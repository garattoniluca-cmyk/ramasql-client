/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.SqlIdentifiers;

/**
 * Il dump ({@code DESIGN.md} §3.10): per gli oggetti scelti legge la struttura dal canale dei metadati
 * ({@code SHOW CREATE TABLE/VIEW}) e le righe dall'esecutore ({@link RowSource}: una {@code SELECT} per tabella, letta
 * a flusso e registrata), le ordina per dipendenze ({@link DumpOrder}) e le scrive con {@link DumpWriter}. Si chiama
 * fuori dall'EDT; si può interrompere (il file dice che è incompleto).
 *
 * <p>Routine, trigger ed eventi non entrano nel dump v1 ({@code ADR-026}): se un catalogo ne ha, il risultato lo dice.
 */
public final class Dumper {

    /**
     * Un oggetto scelto nella procedura guidata.
     *
     * @param catalog catalogo
     * @param name    tabella o vista
     * @param view    è una vista (solo struttura)
     * @param content struttura, dati o entrambi (per le viste conta solo la struttura)
     */
    public record Item(String catalog, String name, boolean view, DumpContent content) {
        public Item {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(name, "name");
            content = view ? DumpContent.STRUCTURE : Objects.requireNonNull(content, "content");
        }
    }

    /** Le righe di una tabella, lette a flusso dall'esecutore. */
    public interface RowSource {
        /**
         * @param selectList l'elenco della {@code SELECT}, già scritto ({@code `id`, UNIX_TIMESTAMP(`creato`), …}:
         *                   vedi {@link DumpLiterals#selectExpression})
         * @param orderBy    colonne della chiave primaria (vuoto = nessun ordine)
         * @param sink       riceve le righe: valori {@code null}, {@link String} o {@code byte[]}
         * @return righe lette
         */
        long read(String catalog, String table, String selectList, List<String> orderBy, RowSink sink)
                throws Exception;
    }

    /**
     * Le righe dall'esecutore della sessione, a flusso: la {@code SELECT} (con l'ordine della chiave primaria) finisce
     * nel registro con l'origine data.
     */
    public static RowSource fromExecutor(it.ramasql.core.exec.SqlExecutor executor, String origin) {
        return (catalog, table, selectList, orderBy, sink) -> {
            StringBuilder sql = new StringBuilder("SELECT ").append(selectList).append(" FROM ")
                    .append(SqlIdentifiers.qualified(catalog, table));
            if (!orderBy.isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", orderBy.stream().map(SqlIdentifiers::quote).toList()));
            }
            try {
                return executor.submitStreamRead(it.ramasql.core.exec.SqlStatement.of(sql.toString(), origin),
                        sink::row).join();
            } catch (java.util.concurrent.CompletionException e) {
                throw e.getCause() instanceof Exception x ? x : e;
            }
        };
    }

    /** Oltre questi caratteri una riga sola supera il {@code max_allowed_packet} predefinito (16 MB) al ripristino. */
    static final long BIG_ROW_CHARS = 8_000_000;

    /** Chi riceve le righe; un'eccezione ferma la lettura. */
    public interface RowSink {
        void row(Object[] values) throws Exception;
    }

    /** Avanzamento, sul thread che fa il dump. */
    public interface Progress {
        default void object(String catalog, String name, int index, int total) {
        }

        default void rows(String table, long rows) {
        }
    }

    /**
     * Esito.
     *
     * @param statements  istruzioni scritte
     * @param tables      tabelle scritte (struttura o dati)
     * @param views       viste scritte
     * @param rows        righe scritte
     * @param interrupted fermato dall'utente: il file è incompleto
     * @param warnings    cose da sapere (routine e trigger non inclusi, chiavi circolari…), in italiano
     */
    public record Result(long statements, int tables, int views, long rows, boolean interrupted,
            List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    private final MetadataReader reader;
    private final RowSource rows;
    private final String server;

    public Dumper(MetadataReader reader, RowSource rows, String server) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.rows = Objects.requireNonNull(rows, "rows");
        this.server = server == null ? "" : server;
    }

    /** Un catalogo pronto da scrivere: oggetti in ordine, strutture e colonne lette. */
    private record CatalogPlan(CatalogInfo info, DumpOrder order, Map<String, Item> items, Map<String, TableDef> tables,
            Map<String, String> creates) {
    }

    public Result write(List<Item> selection, DumpOptions options, Writer out, BooleanSupplier cancelled,
            Progress progress) throws IOException, SQLException {
        Progress p = progress == null ? new Progress() { } : progress;
        BooleanSupplier stop = cancelled == null ? () -> false : cancelled;
        List<String> warnings = new ArrayList<>();
        // 1) metadati di tutti i cataloghi, prima di scrivere: servono l'ordine e le chiavi circolari
        Map<String, List<Item>> byCatalog = new LinkedHashMap<>();
        for (Item i : selection) {
            byCatalog.computeIfAbsent(i.catalog(), k -> new ArrayList<>()).add(i);
        }
        List<CatalogPlan> plans = new ArrayList<>();
        boolean circular = false;
        for (Map.Entry<String, List<Item>> e : byCatalog.entrySet()) {
            String catalog = e.getKey();
            CatalogInfo info = reader.catalog(catalog).orElse(new CatalogInfo(catalog, null, null, false));
            Map<String, Item> items = new LinkedHashMap<>();
            Map<String, TableDef> tables = new LinkedHashMap<>();
            Map<String, String> creates = new LinkedHashMap<>();
            Map<String, List<String>> refs = new LinkedHashMap<>();
            Map<String, String> viewDefs = new LinkedHashMap<>();
            for (Item i : e.getValue()) {
                items.put(i.name(), i);
                if (i.view()) {
                    String create = reader.showCreateView(catalog, i.name()).orElseThrow(
                            () -> new SQLException(CoreMessages.get("dump.error.missing", catalog, i.name())));
                    String clean = DumpWriter.viewForDump(create, catalog);
                    List<String> others = DumpWriter.otherCatalogs(clean);
                    if (!others.isEmpty()) {
                        warnings.add(CoreMessages.get("dump.warning.otherCatalog", catalog, i.name(),
                                String.join(", ", others)));
                    }
                    creates.put(i.name(), clean);
                    viewDefs.put(i.name(), clean);
                } else {
                    TableDef t = reader.table(catalog, i.name()).orElseThrow(
                            () -> new SQLException(CoreMessages.get("dump.error.missing", catalog, i.name())));
                    tables.put(i.name(), t);
                    if (i.content().structure()) {
                        creates.put(i.name(), reader.showCreateTable(catalog, i.name()).orElseThrow(
                                () -> new SQLException(CoreMessages.get("dump.error.missing", catalog, i.name()))));
                    }
                    List<String> r = new ArrayList<>();
                    t.foreignKeys().forEach(fk -> {
                        if (fk.refCatalog() == null || fk.refCatalog().equalsIgnoreCase(catalog)) {
                            r.add(fk.refTable());
                        }
                    });
                    refs.put(i.name(), r);
                }
            }
            DumpOrder order = DumpOrder.of(refs, viewDefs);
            if (order.circular()) {
                warnings.add(CoreMessages.get("dump.warning.circular", catalog, String.join(", ", order.circularKeys())));
            }
            if (!order.selfReferences().isEmpty()) {
                warnings.add(CoreMessages.get("dump.warning.selfReference", catalog,
                        String.join(", ", order.selfReferences())));
            }
            circular |= order.needsForeignKeysOff();
            if (!reader.routines(catalog).isEmpty()) {
                warnings.add(CoreMessages.get("dump.warning.routines", catalog));
            }
            plans.add(new CatalogPlan(info, order, items, tables, creates));
        }
        // 2) scrittura
        DumpWriter w = new DumpWriter(out, options);
        List<String> names = plans.stream().map(c -> c.info().name()).toList();
        w.header(Instant.now(), server, names, circular);
        int total = selection.size();
        int index = 0;
        int tablesWritten = 0;
        int viewsWritten = 0;
        long[] rowsWritten = {0};
        boolean interrupted = false;
        boolean several = plans.size() > 1;
        try {
            for (CatalogPlan c : plans) {
                w.catalog(c.info().name(), c.info().charset(), c.info().collation(), several);
                for (String table : c.order().tables()) {
                    check(stop);
                    Item item = c.items().get(table);
                    p.object(c.info().name(), table, ++index, total);
                    if (item.content().structure()) {
                        w.tableStructure(table, c.creates().get(table));
                    }
                    if (item.content().data()) {
                        data(w, c.info().name(), c.tables().get(table), stop, p, warnings, rowsWritten);
                    }
                    tablesWritten++;
                }
                for (String view : c.order().views()) {
                    check(stop);
                    p.object(c.info().name(), view, ++index, total);
                    w.view(view, c.creates().get(view));
                    viewsWritten++;
                }
            }
        } catch (CancellationException e) {
            interrupted = true;
        }
        w.footer(interrupted);
        return new Result(w.statements(), tablesWritten, viewsWritten, rowsWritten[0], interrupted, warnings);
    }

    /** Le righe di una tabella; {@code total[0]} cresce anche se la lettura si interrompe a metà. */
    private void data(DumpWriter w, String catalog, TableDef t, BooleanSupplier stop, Progress p,
            List<String> warnings, long[] total) throws IOException {
        List<ColumnDef> columns = t.columns().stream().filter(c -> !c.generated()).toList();
        List<String> names = columns.stream().map(ColumnDef::name).toList();
        List<DumpLiterals.Kind> kinds = columns.stream().map(c -> DumpLiterals.kindOfColumn(c.dataType())).toList();
        String selectList = String.join(", ", columns.stream().map(c -> DumpLiterals.selectExpression(
                SqlIdentifiers.quote(c.name()), c.dataType())).toList());
        List<String> orderBy = t.primaryKey().map(pk -> pk.columns()).orElse(List.of());
        DumpWriter.TableData data = w.tableData(t.name(), names, kinds);
        long before = total[0];
        try {
            rows.read(catalog, t.name(), selectList, orderBy, values -> {
                if (stop.getAsBoolean()) {
                    throw new CancellationException();
                }
                data.row(values);
                total[0] = before + data.rows();
                if (data.rows() % 10_000 == 0) {
                    p.rows(t.name(), data.rows());
                }
            });
        } catch (CancellationException e) {
            data.end();
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof CancellationException c) {
                data.end();
                throw c;
            }
            if (stop.getAsBoolean()) {
                // «Interrompi» mentre il server preparava le righe: il KILL QUERY fa fallire la lettura, ma è
                // un'interruzione, non un errore
                data.end();
                throw new CancellationException();
            }
            throw new IOException(CoreMessages.get("dump.error.read", t.name(), e.getMessage()), e);
        }
        data.end();
        if (stop.getAsBoolean()) {
            // «Interrompi»: l'esecutore può chiudere la lettura senza errore (si ferma fra una riga e l'altra), ma le
            // righe non sono tutte: il dump è interrotto, non finito
            throw new CancellationException();
        }
        if (data.largestRow() > BIG_ROW_CHARS) {
            warnings.add(CoreMessages.get("dump.warning.bigRow", t.name(), data.largestRow() / 1_000_000));
        }
        p.rows(t.name(), data.rows());
    }

    private static void check(BooleanSupplier stop) {
        if (stop.getAsBoolean()) {
            throw new CancellationException();
        }
    }

    /** Nome del file proposto: {@code catalogo-aaaa-mm-gg.sql} (o {@code dump-…} per più cataloghi). */
    public static String suggestedFileName(List<String> catalogs, java.time.LocalDate day) {
        String base = catalogs.size() == 1 ? catalogs.get(0) : "dump";
        return base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_") + "-" + day + ".sql";
    }
}
