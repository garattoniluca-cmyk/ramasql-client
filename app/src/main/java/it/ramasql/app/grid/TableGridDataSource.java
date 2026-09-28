/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import it.ramasql.app.Texts;
import it.ramasql.core.exec.ResultTable;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.StatementResult;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.sqlgen.SqlIdentifiers;

/**
 * Il fornitore di dati della griglia che legge le righe <b>dal server</b>, una pagina per volta, passando dal
 * {@link SqlExecutor}: la {@code SELECT} finisce nel registro come tutte le altre istruzioni (regola 3 di
 * {@code CLAUDE.md}), così in aula si vede sempre l'SQL che il client ha eseguito per riempire la griglia.
 *
 * <p>La pagina si chiede con {@code LIMIT righePerPagina + 1}: la riga in più non si mostra, dice solo che esiste
 * un'altra pagina ({@link Page#hasMore()}). Il limite di righe generale dell'esecutore non c'entra e non viene
 * toccato: si usa {@link SqlExecutor#run(SqlScript, it.ramasql.core.exec.ExecutionListener, int)}.
 *
 * <p>Le colonne lette sono quelle della tabella, nominate una per una e nell'ordine della griglia: mai
 * {@code SELECT *}, così una colonna aggiunta sul server da qualcun altro non sfasa le celle.
 *
 * <p><b>Thread:</b> {@link #load} è sincrona (aspetta l'esecutore) e la {@link DataGrid} la chiama <b>fuori
 * dall'EDT</b>, con l'indicatore di lettura visibile ({@code BUG-017}): una pagina lenta non blocca l'interfaccia.
 */
public final class TableGridDataSource implements GridDataSource {

    private final SqlExecutor executor;
    private final String catalog;
    private final String table;
    private final List<String> columns;
    private final String origin;

    /**
     * @param executor esecutore della sessione (unico punto che esegue SQL)
     * @param table    tabella da leggere: catalogo, nome e colonne nell'ordine della griglia
     */
    public TableGridDataSource(SqlExecutor executor, it.ramasql.core.metadata.TableDef table) {
        this(executor, table.catalog(), table.name(), table.columns().stream().map(ColumnDef::name).toList(),
                SqlOrigin.GRID.label());
    }

    /** Come sopra, indicando a mano catalogo, tabella, colonne e origine per il registro. */
    public TableGridDataSource(SqlExecutor executor, String catalog, String table, List<String> columns,
            String origin) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.catalog = catalog;
        this.table = Objects.requireNonNull(table, "table");
        this.columns = List.copyOf(columns);
        if (this.columns.isEmpty()) {
            throw new IllegalArgumentException("Nessuna colonna da leggere per " + table);
        }
        this.origin = origin;
    }

    /**
     * L'SQL della pagina, esattamente quello che verrà eseguito e registrato.
     *
     * @param pageIndex pagina, da 0
     * @param pageSize  righe per pagina (si chiede una riga in più)
     * @param orderBy   ordinamento, {@code null} = ordine naturale del server
     */
    public String pageSql(int pageIndex, int pageSize, SortOrder orderBy) {
        if (pageIndex < 0 || pageSize < 1) {
            throw new IllegalArgumentException("Pagina " + pageIndex + ", righe per pagina " + pageSize);
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            sql.append(i == 0 ? "" : ", ").append(SqlIdentifiers.quote(columns.get(i)));
        }
        sql.append(" FROM ").append(SqlIdentifiers.qualified(catalog, table));
        if (orderBy != null) {
            sql.append(" ORDER BY ").append(SqlIdentifiers.quote(orderBy.column()))
                    .append(orderBy.ascending() ? " ASC" : " DESC");
        }
        sql.append(" LIMIT ").append(pageSize + 1);
        long offset = (long) pageIndex * pageSize;
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
        return sql.toString();
    }

    @Override
    public Page load(int pageIndex, int pageSize, SortOrder orderBy) {
        String sql = pageSql(pageIndex, pageSize, orderBy);
        SqlScript script = SqlScript.of(Texts.get("grid.read.title", table), origin, sql);
        ScriptResult result = executor.run(script, null, pageSize + 1);
        StatementResult only = result.results().isEmpty() ? null : result.results().get(0);
        if (only == null || !only.isOk()) {
            throw new ReadFailed(Texts.get("grid.read.failed", table, describe(only)), only);
        }
        ResultTable rows = only.firstResult()
                .orElseThrow(() -> new ReadFailed(Texts.get("grid.read.noResult", table), only));
        List<List<String>> cells = new ArrayList<>(Math.min(rows.rowCount(), pageSize));
        boolean hasMore = false;
        for (int r = 0; r < rows.rowCount(); r++) {
            if (cells.size() >= pageSize) {
                hasMore = true;   // la riga in più: esiste un'altra pagina
                break;
            }
            List<String> row = new ArrayList<>(columns.size());
            for (int c = 0; c < columns.size(); c++) {
                row.add(ResultCells.text(rows.value(r, c)));
            }
            cells.add(row);
        }
        return new Page(cells, hasMore || rows.truncated());
    }

    private static String describe(StatementResult result) {
        if (result == null || result.error() == null) {
            return Texts.get("grid.read.noAnswer");
        }
        StatementResult.ServerError e = result.error();
        String original = e.code() == 0 ? e.message() : "[" + e.code() + "] " + e.message();
        // la spiegazione in italiano, quando c'è (es. 1356: vista non più valida, Step 8), poi il messaggio del server
        return it.ramasql.app.editor.ErrorExplainer.explain(e.code())
                .map(x -> Texts.get("grid.read.errorExplained", x, original)).orElse(original);
    }

    /** La lettura di una pagina non è riuscita: chi apre la griglia mostra il messaggio del server. */
    public static final class ReadFailed extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient StatementResult result;

        ReadFailed(String message, StatementResult result) {
            super(message);
            this.result = result;
        }

        /** L'esito dell'istruzione, con l'errore del server; {@code null} se non è arrivata risposta. */
        public StatementResult result() {
            return result;
        }
    }
}
