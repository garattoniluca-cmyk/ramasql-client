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

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.SwingUtilities;

import it.ramasql.app.editor.PositionedStatement;
import it.ramasql.app.editor.ResultDataFactory;
import it.ramasql.app.editor.SqlError;
import it.ramasql.app.editor.SqlRunner;
import it.ramasql.app.editor.StatementOutcome;
import it.ramasql.app.pipeline.SqlPipeline;
import it.ramasql.core.exec.ExecutionListener;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.exec.StatementResult;

/**
 * Il {@link SqlRunner} del programma: prende le istruzioni che l'editor SQL ha separato e le fa passare per la
 * <b>pipeline «anteprima SQL»</b> ({@link SqlPipeline#propose}), che le mostra, chiede la conferma e solo allora le
 * consegna all'esecutore. L'editor non esegue mai nulla da sé; qui non si esegue nulla che non sia stato mostrato.
 *
 * <p>Gli esiti tornano all'editor uno per uno mentre lo script va avanti (l'esecutore chiama l'ascoltatore dal suo
 * thread; questa classe li riporta sull'EDT). Se l'utente annulla dall'anteprima l'editor riceve solo
 * {@link SqlRunner.Listener#done done(true)}: nessuna istruzione è partita.
 */
public final class PipelineSqlRunner implements SqlRunner {

    private final SqlPipeline pipeline;

    public PipelineSqlRunner(SqlPipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    @Override
    public void run(List<PositionedStatement> statements, String origin, Listener listener) {
        Objects.requireNonNull(listener, "listener");
        List<PositionedStatement> list = List.copyOf(statements);
        if (list.isEmpty()) {
            onEdt(() -> listener.done(false));
            return;
        }
        List<SqlStatement> sql = new ArrayList<>(list.size());
        for (PositionedStatement p : list) {
            SqlStatement s = p.statement();
            sql.add(origin == null || origin.isBlank() ? s : new SqlStatement(s.text(), origin, s.risk()));
        }
        SqlScript script = new SqlScript(title(list), origin, sql);

        ExecutionListener progress = new ExecutionListener() {
            @Override
            public void statementStarted(SqlScript s, int index, SqlStatement statement) {
                onEdt(() -> listener.started(index));
            }

            @Override
            public void statementFinished(SqlScript s, StatementResult result) {
                StatementOutcome outcome = outcomeOf(result);
                onEdt(() -> listener.finished(outcome));
            }
        };

        pipeline.propose(script, progress).whenComplete((result, error) -> {
            boolean cancelled = result == null || result.interrupted();
            onEdt(() -> listener.done(cancelled));
        });
    }

    @Override
    public void cancel() {
        try {
            pipeline.executor().interrupt();
        } catch (SQLException e) {
            // l'interruzione non è arrivata al server: lo script finirà da sé e l'esito lo dirà
        }
    }

    /** Il titolo che compare nell'anteprima: «1 istruzione» / «N istruzioni». */
    private static String title(List<PositionedStatement> statements) {
        return statements.size() == 1
                ? it.ramasql.app.Texts.get("editor.script.one")
                : it.ramasql.app.Texts.get("editor.script.many", statements.size());
    }

    /** Da esito dell'esecutore a esito dell'editor: righe lette, righe interessate, errore, durata, avvisi. */
    static StatementOutcome outcomeOf(StatementResult result) {
        Duration duration = Duration.ofMillis(result.durationMillis());
        List<StatementOutcome.Warning> warnings = new ArrayList<>(result.warnings().size());
        for (StatementResult.Warning w : result.warnings()) {
            warnings.add(new StatementOutcome.Warning(w.code(), w.message()));
        }
        if (!result.isOk()) {
            StatementResult.ServerError e = result.error();
            SqlError error = e == null
                    ? new SqlError(0, "", it.ramasql.app.Texts.get("editor.outcome.noAnswer"))
                    : new SqlError(e.code(), e.sqlState(), e.message());
            return StatementOutcome.failed(result.index(), error, duration);
        }
        if (result.firstResult().isPresent()) {
            return StatementOutcome.rows(result.index(), ResultDataFactory.of(result.firstResult().get()), duration,
                    warnings);
        }
        return StatementOutcome.update(result.index(), result.affectedRows(), duration, warnings);
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** L'esito complessivo dell'ultimo script proposto (per i test). */
    public ScriptResult lastResult() {
        return pipeline.lastProposal().getNow(null);
    }
}
