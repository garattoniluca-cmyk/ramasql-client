/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import it.ramasql.app.grid.ResultCells;
import it.ramasql.app.pipeline.SqlPipeline;
import it.ramasql.app.tableeditor.DataCheck;
import it.ramasql.core.exec.ResultTable;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.StatementResult;

/**
 * «Verifica dati» dell'editor di tabelle nel programma: la {@code SELECT} che cerca le righe orfane di una chiave
 * esterna o i duplicati di un UNIQUE passa dalla pipeline come ogni altra istruzione, quindi finisce nell'anteprima e
 * nel registro. È una lettura: nessuna conferma rafforzata, nessuna scrittura.
 */
public final class PipelineDataCheck implements DataCheck {

    /** Righe di controllo mostrate al massimo: all'utente servono gli esempi, non l'elenco completo. */
    static final int MAX_ROWS = 200;

    private final SqlPipeline pipeline;

    public PipelineDataCheck(SqlPipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    @Override
    public void run(String sql, Consumer<DataCheckResult> done) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(done, "done");
        SqlScript script = SqlScript.of(it.ramasql.app.Texts.get("tableeditor.dataCheck.title"),
                SqlOrigin.TABLE_EDITOR.label(), sql);
        pipeline.propose(script).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> done.accept(convert(result))));
    }

    private static DataCheckResult convert(ScriptResult result) {
        if (result == null) {
            return DataCheckResult.failed(it.ramasql.app.Texts.get("tableeditor.dataCheck.cancelled"));
        }
        StatementResult only = result.results().isEmpty() ? null : result.results().get(0);
        if (only == null) {
            return DataCheckResult.failed(it.ramasql.app.Texts.get("editor.outcome.noAnswer"));
        }
        if (!only.isOk()) {
            StatementResult.ServerError e = only.error();
            return DataCheckResult.failed(e == null ? it.ramasql.app.Texts.get("editor.outcome.noAnswer")
                    : "[" + e.code() + "] " + e.message());
        }
        ResultTable table = only.firstResult().orElse(null);
        if (table == null) {
            return new DataCheckResult(List.of(), List.of(), null);
        }
        List<String> columns = new ArrayList<>(table.columnCount());
        for (ResultTable.Column c : table.columns()) {
            columns.add(c.label());
        }
        List<List<String>> rows = ResultCells.rows(table);
        if (rows.size() > MAX_ROWS) {
            rows = List.copyOf(rows.subList(0, MAX_ROWS));
        }
        return new DataCheckResult(columns, rows, null);
    }
}
