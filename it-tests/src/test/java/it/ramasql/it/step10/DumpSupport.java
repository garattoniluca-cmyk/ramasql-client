/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;
import it.ramasql.core.dump.DumpContent;
import it.ramasql.core.dump.DumpOptions;
import it.ramasql.core.dump.Dumper;
import it.ramasql.core.exec.CollationCompat;
import it.ramasql.core.exec.ScriptFileResult;
import it.ramasql.core.exec.ScriptPreview;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.core.sqlgen.SqlIdentifiers;
import it.ramasql.it.ItServers;

/**
 * Dump e ripristino con il codice del client (lettore dei metadati, esecutore, {@link Dumper}, script da file), e i
 * confronti fatti con una connessione separata del test.
 */
final class DumpSupport {

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");

    private DumpSupport() {
    }

    /** Una sessione del client (le stesse due connessioni del prodotto), senza catalogo. */
    static Session open(ItServers server) throws Exception {
        Matcher m = HOST_PORT.matcher(server.url());
        assertTrue(m.matches(), server.url());
        ConnectionProfile p = ConnectionProfile.create("Test " + server.label(), m.group(1),
                Integer.parseInt(m.group(2)), server.user(), "", "");
        String value = System.getenv("RAMASQL_IT_" + server.name() + "_PASSWORD");
        if (value == null) {
            throw new AssertionError("Manca RAMASQL_IT_" + server.name() + "_PASSWORD: i test non si saltano.");
        }
        return Session.open(p, value.toCharArray());
    }

    /** Il client al lavoro: sessione, lettore dei metadati, esecutore, registro. */
    record Client(Session session, MetadataReader reader, SqlExecutor executor, SqlLog log) implements AutoCloseable {
        static Client of(ItServers server) throws Exception {
            Session s = open(server);
            SqlLog log = new SqlLog();
            MetadataReader r = MetadataReader.of(s);
            return new Client(s, r, new SqlExecutor(s, log, r), log);
        }

        @Override
        public void close() {
            executor.close();
            session.close();
        }

        /** Tutte le tabelle e viste del catalogo, struttura e dati. */
        List<Dumper.Item> whole(String catalog) throws Exception {
            List<Dumper.Item> out = new ArrayList<>();
            for (TableSummary t : reader.tables(catalog)) {
                out.add(new Dumper.Item(catalog, t.name(), t.isView(), DumpContent.BOTH));
            }
            return out;
        }

        Dumper.Result dump(List<Dumper.Item> items, DumpOptions options, Path file) throws Exception {
            Dumper d = new Dumper(reader, Dumper.fromExecutor(executor, "Esportazione"),
                    session.serverInfo().displayName());
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                return d.write(items, options, out, null, null);
            }
        }

        /**
         * Esegue il file dentro il catalogo dato ({@code null} = lascia fare ai suoi USE), come il client: letto nella
         * codifica trovata dalla lettura di prova, con le collation che questo server non conosce sostituite.
         */
        ScriptFileResult restore(Path file, String catalog) throws Exception {
            List<SqlStatement> before = catalog == null ? List.of()
                    : List.of(SqlStatement.of("USE " + SqlIdentifiers.quote(catalog), "Script da file"));
            ScriptPreview preview = ScriptPreview.scan(file, "Script da file", null);
            return executor.submitScriptFile(before, preview.open(), file.getFileName().toString(), false,
                    "Script da file", compatFor(preview), null).get(10, TimeUnit.MINUTES);
        }

        /** Le collation del file che questo server non conosce, con la sostituta (come la scheda del client). */
        CollationCompat compatFor(ScriptPreview preview) throws Exception {
            return CollationCompat.of(preview.collations(), reader.collations());
        }
    }

    private static TableDef sameActions(TableDef t) {
        return t.withForeignKeys(t.foreignKeys().stream().map(fk -> fk.withActions(
                fk.onDelete() == it.ramasql.core.metadata.FkAction.NO_ACTION ? it.ramasql.core.metadata.FkAction.RESTRICT
                        : fk.onDelete(),
                fk.onUpdate() == it.ramasql.core.metadata.FkAction.NO_ACTION ? it.ramasql.core.metadata.FkAction.RESTRICT
                        : fk.onUpdate())).toList());
    }

    /** Righe di una query come testo, con la connessione del test. */
    static List<List<String>> rows(Connection c, String sql) throws Exception {
        List<List<String>> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> r = new ArrayList<>();
                for (int i = 1; i <= n; i++) {
                    r.add(rs.getString(i));
                }
                out.add(r);
            }
        }
        return out;
    }

    /** {@code CHECKSUM TABLE} di ogni tabella del catalogo (nome → somma). */
    static Map<String, String> checksums(Connection c, MetadataReader reader, String catalog) throws Exception {
        Map<String, String> out = new TreeMap<>();
        for (TableSummary t : reader.tables(catalog)) {
            if (!t.isView()) {
                out.put(t.name(), rows(c, "CHECKSUM TABLE " + SqlIdentifiers.qualified(catalog, t.name())).get(0).get(1));
            }
        }
        return out;
    }

    /** {@code CHECKSUM TABLE} delle tabelle elencate (nome → somma); il lettore non serve. */
    static Map<String, String> checksums(Connection c, MetadataReader unused, String catalog, List<String> tables)
            throws Exception {
        Map<String, String> out = new TreeMap<>();
        for (String t : tables) {
            out.put(t, rows(c, "CHECKSUM TABLE " + SqlIdentifiers.qualified(catalog, t)).get(0).get(1));
        }
        return out;
    }

    /**
     * Impronta del contenuto di una tabella indipendente dal server (per il confronto fra MariaDB e MySQL, dove
     * {@code CHECKSUM TABLE} non si confronta): le righe in ordine di chiave, come testo, in SHA-256.
     */
    static String contentHash(Connection c, String catalog, TableDef t) throws Exception {
        String order = t.primaryKey().map(pk -> String.join(", ", pk.columns().stream().map(SqlIdentifiers::quote)
                .toList())).orElse("1");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long n = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM "
                + SqlIdentifiers.qualified(catalog, t.name()) + " ORDER BY " + order)) {
            int cols = rs.getMetaData().getColumnCount();
            boolean[] binary = new boolean[cols + 1];
            for (int i = 1; i <= cols; i++) {
                int type = rs.getMetaData().getColumnType(i);
                binary[i] = type == java.sql.Types.BINARY || type == java.sql.Types.VARBINARY
                        || type == java.sql.Types.LONGVARBINARY || type == java.sql.Types.BLOB || type == java.sql.Types.BIT;
            }
            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    byte[] b = binary[i] ? rs.getBytes(i) : rs.getString(i) == null ? null
                            : rs.getString(i).getBytes(StandardCharsets.UTF_8);
                    md.update(b == null ? new byte[] {0} : b);
                    md.update((byte) 0x1F);
                }
                md.update((byte) '\n');
                n++;
            }
        }
        return n + ":" + HexFormat.of().formatHex(md.digest());
    }

    /**
     * Le tabelle di due cataloghi hanno la stessa struttura secondo il lettore del client (colonne, indici, chiavi
     * esterne con le loro azioni, engine, charset, collation): il nome del catalogo a parte.
     */
    static List<String> sameStructure(MetadataReader a, String catA, MetadataReader b, String catB) throws Exception {
        return sameStructure(a, catA, b, catB, false);
    }

    /** Come sopra; con {@code restrictIsNoAction} RESTRICT e NO ACTION contano uguali (InnoDB, fra i due server). */
    static List<String> sameStructure(MetadataReader a, String catA, MetadataReader b, String catB,
            boolean restrictIsNoAction) throws Exception {
        List<String> report = new ArrayList<>();
        List<String> tablesA = a.tables(catA).stream().filter(t -> !t.isView()).map(TableSummary::name).sorted().toList();
        List<String> tablesB = b.tables(catB).stream().filter(t -> !t.isView()).map(TableSummary::name).sorted().toList();
        assertEquals(tablesA, tablesB, "stesse tabelle");
        for (String t : tablesA) {
            TableDef ta = a.table(catA, t).orElseThrow().withCatalog(null);
            TableDef tb = b.table(catB, t).orElseThrow().withCatalog(null);
            if (restrictIsNoAction) {
                ta = sameActions(ta);
                tb = sameActions(tb);
            }
            assertEquals(ta, tb, "struttura di " + t);
            report.add(t + ": " + ta.columns().size() + " colonne, " + ta.indexes().size() + " indici, "
                    + ta.foreignKeys().size() + " chiavi esterne " + ta.foreignKeys().stream()
                            .map(fk -> fk.name() + " ON DELETE " + fk.onDelete() + " ON UPDATE " + fk.onUpdate()).toList()
                    + ", " + ta.engine() + ", " + ta.collation() + " — uguale");
        }
        return report;
    }
}
