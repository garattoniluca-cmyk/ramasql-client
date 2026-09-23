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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import it.ramasql.app.editor.ErrorExplainer;
import it.ramasql.app.tableeditor.TableApplier;
import it.ramasql.app.pipeline.SqlPipeline;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.exec.StatementResult;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;

/**
 * L'applicatore dell'editor di tabelle nel programma: manda le istruzioni dell'editor alla pipeline «anteprima SQL»
 * ({@link SqlPipeline#propose}) e, appena l'esecuzione finisce, <b>rilegge la tabella dal server</b> con il
 * {@link MetadataReader}, anche quando qualcosa è andato storto: così l'editor può mostrare lo stato reale invece di
 * quello che credeva di avere ({@code ADR-011}, «verifica dopo»).
 *
 * <p>Le istruzioni si eseguono una per volta, nell'ordine, fermandosi alla prima che il server rifiuta: l'esito dice
 * quali sono state applicate, quale è fallita (con codice, messaggio del server e spiegazione in italiano) e quali non
 * sono nemmeno partite.
 */
public final class PipelineTableApplier implements TableApplier {

    private final SqlPipeline pipeline;
    private final MetadataReader reader;

    public PipelineTableApplier(SqlPipeline pipeline, MetadataReader reader) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public void apply(ApplyRequest request, Consumer<ApplyOutcome> done) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(done, "done");
        List<String> texts = request.statements();
        if (texts.isEmpty()) {
            done.accept(ApplyOutcome.success(List.of(), reload(request)));
            return;
        }
        String origin = SqlOrigin.TABLE_EDITOR.label();
        List<SqlStatement> statements = new ArrayList<>(texts.size());
        for (String text : texts) {
            statements.add(SqlStatement.of(text, origin));
        }
        String title = request.original() == null
                ? it.ramasql.app.Texts.get("tableeditor.script.create", request.edited().name())
                : it.ramasql.app.Texts.get("tableeditor.script.alter", request.edited().name());
        SqlScript script = new SqlScript(title, origin, statements);
        pipeline.propose(script).whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                // non annullato: fallito. L'errore l'ha già mostrato la pipeline; qui si dice che nulla è partito
                done.accept(ApplyOutcome.failure(List.of(), texts, new ApplyError(0,
                        it.ramasql.app.Texts.get("pipeline.failedShort"), ""), reload(request)));
            } else {
                done.accept(outcome(request, texts, result));
            }
        }));
    }

    /** Da esito dello script a esito per l'editor, con la tabella riletta dal server. */
    private ApplyOutcome outcome(ApplyRequest request, List<String> texts, ScriptResult result) {
        if (result == null) {
            return ApplyOutcome.cancelled(texts);   // annullato dall'anteprima: nulla è stato eseguito
        }
        List<String> applied = new ArrayList<>();
        ApplyError failure = null;
        int failedIndex = -1;
        for (StatementResult r : result.results()) {
            if (r.index() < 0 || r.index() >= texts.size()) {
                continue;   // indice fuori posto: non si inventa nulla
            }
            if (r.isOk()) {
                applied.add(texts.get(r.index()));
            } else {
                failedIndex = r.index();
                failure = error(r);
                break;
            }
        }
        TableDef reloaded = reload(request);
        if (failure == null && result.interrupted() && applied.size() < texts.size()) {
            // interrotta fra un'istruzione e la successiva: le rimanenti non sono partite, e dire «applicate tutte»
            // sarebbe falso
            failedIndex = applied.size();
            failure = new ApplyError(0, it.ramasql.app.Texts.get("tableeditor.outcome.interrupted"), "");
        }
        if (failure == null) {
            return ApplyOutcome.success(applied, reloaded);
        }
        List<String> notApplied = new ArrayList<>(texts.subList(failedIndex, texts.size()));
        return ApplyOutcome.failure(applied, notApplied, failure, reloaded);
    }

    private static ApplyError error(StatementResult result) {
        StatementResult.ServerError e = result.error();
        if (e == null) {
            return new ApplyError(0, it.ramasql.app.Texts.get("editor.outcome.noAnswer"), "");
        }
        return new ApplyError(e.code(), e.message(), ErrorExplainer.explain(e.code()).orElse(""));
    }

    /**
     * Rilegge dal server la tabella su cui si è lavorato: prima si butta la copia in cache (il DDL l'ha resa vecchia),
     * poi si legge. {@code null} se la tabella non c'è (un {@code CREATE} fallito) o se la lettura non riesce.
     */
    private TableDef reload(ApplyRequest request) {
        TableDef edited = request.edited();
        String catalog = edited.catalog();
        try {
            reader.invalidate(catalog);
            TableDef byNewName = reader.table(catalog, edited.name()).orElse(null);
            if (byNewName != null) {
                return byNewName;
            }
            // rinomina fallita: la tabella è ancora con il nome di prima
            TableDef original = request.original();
            if (original != null && !original.name().equals(edited.name())) {
                return reader.table(catalog, original.name()).orElse(null);
            }
            return null;
        } catch (SQLException e) {
            return null;   // l'editor lo dice all'utente: «stato reale non riletto»
        }
    }
}
