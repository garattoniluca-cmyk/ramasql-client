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
