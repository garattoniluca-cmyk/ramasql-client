/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.pipeline;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import javax.swing.SwingUtilities;

import it.ramasql.app.Texts;
import it.ramasql.app.workspace.WorkspacePrompts;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.ExecutionListener;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlScript;

/**
 * La pipeline «anteprima SQL» lato interfaccia (regola 3 di {@code CLAUDE.md}, ARCHITECTURE §4): ogni operazione
 * grafica consegna qui il suo {@link SqlScript}; la pipeline lo mostra nella scheda Anteprima, apre la finestra
 * «SQL che verrà eseguito» con la conferma decisa da {@link ConfirmationPolicy} e, solo se l'utente sceglie
 * <em>Esegui</em>, lo passa a {@link SqlExecutor} (che lo esegue fuori dall'EDT e alimenta il registro). L'esito torna
 * sull'EDT nella scheda Messaggi. Non esiste un metodo che esegua senza anteprima.
 */
public final class SqlPipeline {

    private final SqlExecutor executor;
    private final WorkspacePrompts prompts;
    private final PipelineView view;
    private CompletableFuture<ScriptResult> last = CompletableFuture.completedFuture(null);
    private int running;

    public SqlPipeline(SqlExecutor executor, WorkspacePrompts prompts, PipelineView view) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.view = Objects.requireNonNull(view, "view");
    }

    /**
     * Propone lo script: anteprima, decisione dell'utente, eventuale esecuzione. Va chiamato sull'EDT; ritorna appena
     * la finestra d'anteprima si chiude.
     *
     * @return l'esito dell'esecuzione; completato con {@code null} se l'utente ha annullato o copiato
     */
    public CompletableFuture<ScriptResult> propose(SqlScript script) {
        return propose(script, null);
    }

    /**
     * Come {@link #propose(SqlScript)}, seguendo l'esecuzione istruzione per istruzione: serve all'editor SQL, che
     * mostra l'esito di ciascuna mentre lo script va avanti. L'ascoltatore viene chiamato <b>sul thread
     * dell'esecutore</b>, non sull'EDT.
     *
     * @param listener avanzamento; {@code null} = nessuno
     */
    public CompletableFuture<ScriptResult> propose(SqlScript script, ExecutionListener listener) {
        Objects.requireNonNull(script, "script");
        view.scriptProposed(script);
        ConfirmationPolicy.Confirmation confirmation = ConfirmationPolicy.evaluate(script);
        PreviewDialog.Decision decision = prompts.preview(script, confirmation);
        CompletableFuture<ScriptResult> outcome;
        switch (decision == null ? PreviewDialog.Decision.CANCEL : decision) {
            case EXECUTE -> outcome = submit(script, listener);
            case COPY -> {
                prompts.copyToClipboard(script.text());
                view.message(PipelineView.MessageKind.INFO, Texts.get("pipeline.copied", script.title()));
                outcome = CompletableFuture.completedFuture(null);
            }
            default -> {
                view.message(PipelineView.MessageKind.INFO, Texts.get("pipeline.cancelled", script.title()));
                outcome = CompletableFuture.completedFuture(null);
            }
        }
        last = outcome;
        return outcome;
    }

    /**
     * Come {@link #propose(SqlScript)} per un'importazione: l'anteprima mostra lo script (svuota o crea la tabella, poi
     * l'{@code INSERT} preparata con il numero di lotti nel titolo); con <em>Esegui</em> l'esecutore lo esegue e poi
     * inserisce le righe di {@code source} a lotti. L'ascoltatore è chiamato sul thread dell'esecutore.
     *
     * @return l'esito (sull'EDT, dopo il messaggio); completato con {@code null} se l'utente ha annullato o copiato
     */
    public CompletableFuture<it.ramasql.core.exec.BatchResult> proposeBatchInsert(SqlScript script,
            it.ramasql.core.exec.BatchInsert insert, it.ramasql.core.exec.BatchSource source,
            it.ramasql.core.exec.BatchListener listener) {
        Objects.requireNonNull(script, "script");
        view.scriptProposed(script);
        ConfirmationPolicy.Confirmation confirmation = ConfirmationPolicy.evaluate(script);
        PreviewDialog.Decision decision = prompts.preview(script, confirmation);
        if (decision == PreviewDialog.Decision.COPY) {
            prompts.copyToClipboard(script.text());
            view.message(PipelineView.MessageKind.INFO, Texts.get("pipeline.copied", script.title()));
        }
        if (decision != PreviewDialog.Decision.EXECUTE) {
            if (decision != PreviewDialog.Decision.COPY) {
                view.message(PipelineView.MessageKind.INFO, Texts.get("pipeline.cancelled", script.title()));
            }
            CompletableFuture<ScriptResult> none = CompletableFuture.completedFuture(null);
            last = none;
            return CompletableFuture.completedFuture(null);
        }
        running++;
        CompletableFuture<it.ramasql.core.exec.BatchResult> future;
        try {
            future = executor.submitBatchInsert(script, insert, source, listener);
        } catch (RejectedExecutionException e) {
            running--;
            view.message(PipelineView.MessageKind.ERROR, Texts.get("pipeline.closed"));
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<it.ramasql.core.exec.BatchResult> shown = new CompletableFuture<>();
        CompletableFuture<ScriptResult> marker = new CompletableFuture<>();
        last = marker;
        future.whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            running--;
            if (error != null) {
                view.message(PipelineView.MessageKind.ERROR, Texts.get("pipeline.failedToRun", script.title(),
                        String.valueOf(error.getMessage())));
                shown.completeExceptionally(error);
                marker.completeExceptionally(error);
            } else {
                if (!result.started() || !result.before().script().isEmpty()) {
                    view.scriptFinished(result.before());   // svuota / crea tabella: esito come ogni script
                }
                if (result.started()) {
                    batchMessage(script, result);
                }
                shown.complete(result);
                marker.complete(result.before());
            }
        }));
        return shown;
    }

    /** L'esito dell'inserimento a lotti nella scheda Messaggi. */
    private void batchMessage(SqlScript script, it.ramasql.core.exec.BatchResult r) {
        long rejected = r.rejectedCount();
        String counts = Texts.get("pipeline.batch.counts", r.inserted(), r.batches(), rejected, r.duplicatesIgnored(),
                r.durationMillis());
        if (r.fatal() != null) {
            StringBuilder sb = new StringBuilder(Texts.get("pipeline.batch.failed", script.title(), counts));
            sb.append('\n').append(Texts.get("panel.messages.serverError", r.fatal().code(),
                    r.fatal().sqlState().isEmpty() ? "-" : r.fatal().sqlState(), r.fatal().message()));
            it.ramasql.app.editor.ErrorExplainer.explain(r.fatal().code())
                    .ifPresent(x -> sb.append('\n').append(Texts.get("panel.messages.explanation", x)));
            view.message(PipelineView.MessageKind.ERROR, sb.toString());
        } else if (r.sourceFailure() != null) {
            view.message(PipelineView.MessageKind.ERROR,
                    Texts.get("pipeline.batch.sourceFailed", script.title(), counts, r.sourceFailure()));
        } else if (r.interrupted()) {
            view.message(PipelineView.MessageKind.WARNING, Texts.get("pipeline.batch.interrupted", script.title(), counts));
        } else {
            view.message(rejected > 0 ? PipelineView.MessageKind.WARNING : PipelineView.MessageKind.SUCCESS,
                    Texts.get("pipeline.batch.done", script.title(), counts));
        }
    }

    private CompletableFuture<ScriptResult> submit(SqlScript script, ExecutionListener listener) {
        running++;
        CompletableFuture<ScriptResult> future;
        try {
            future = executor.submit(script, listener);
        } catch (RejectedExecutionException e) {
            running--;
            view.message(PipelineView.MessageKind.ERROR, Texts.get("pipeline.closed"));
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<ScriptResult> shown = new CompletableFuture<>();
        future.whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            running--;
            if (error != null) {
                view.message(PipelineView.MessageKind.ERROR, Texts.get("pipeline.failedToRun", script.title(),
                        String.valueOf(error.getMessage())));
                // fallito non è annullato: chi ha proposto lo script deve poterli distinguere, altrimenti direbbe
                // all'utente «operazione annullata» quando invece qualcosa si è rotto
                shown.completeExceptionally(error);
            } else {
                view.scriptFinished(result);
                shown.complete(result);
            }
        }));
        return shown;
    }

    /** Uno script mandato in esecuzione non ha ancora mostrato il suo esito (sull'EDT). */
    public boolean isBusy() {
        return running > 0;
    }

    /** L'ultima proposta: il suo esito si completa dopo che la scheda Messaggi l'ha mostrato. */
    public CompletableFuture<ScriptResult> lastProposal() {
        return last;
    }

    public SqlExecutor executor() {
        return executor;
    }
}
