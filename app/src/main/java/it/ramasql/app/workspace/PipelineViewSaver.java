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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import it.ramasql.app.Texts;
import it.ramasql.app.pipeline.PipelineView;
import it.ramasql.app.pipeline.SqlPipeline;
import it.ramasql.app.visual.ViewSaver;
import it.ramasql.core.connection.ViewSourceStore;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.ViewDef;
import it.ramasql.core.sqlgen.ViewDdl;

/**
 * Il {@link ViewSaver} del programma (Step 8): {@code CREATE [OR REPLACE] VIEW} generato da {@link ViewDdl} passa
 * dalla pipeline «anteprima SQL» come ogni altra modifica. Se il server accetta, si rilegge la definizione della vista
 * ({@code VIEW_DEFINITION}) e il <b>sorgente originale</b> va nell'archivio delle viste insieme a quella definizione:
 * riaprendo la vista, finché sul server resta quella, si riapre il testo scritto dall'utente (livello 1).
 */
public final class PipelineViewSaver implements ViewSaver {

    private final SqlPipeline pipeline;
    private final MetadataReader reader;
    private final ViewSourceStore store;
    private final String server;
    private final PipelineView view;

    /**
     * @param store  archivio dei sorgenti; {@code null} = non si archivia (si riaprirà dal livello 2)
     * @param server indirizzo del profilo di connessione ({@code utente@host:porta}), chiave dell'archivio
     */
    public PipelineViewSaver(SqlPipeline pipeline, MetadataReader reader, ViewSourceStore store, String server,
            PipelineView view) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.store = store;
        this.server = Objects.requireNonNull(server, "server");
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    public void save(String catalog, String name, String select, boolean orReplace, Consumer<Boolean> done) {
        SqlScript script;
        String body;
        try {
            script = ViewDdl.createScript(catalog, name, select, orReplace);
            body = ViewDdl.selectBody(select);
        } catch (IllegalArgumentException e) {
            view.message(PipelineView.MessageKind.ERROR, e.getMessage());
            done.accept(false);
            return;
        }
        pipeline.propose(script).whenComplete((ScriptResult result, Throwable error) -> {
            boolean ok = error == null && result != null && result.completed();
            if (!ok) {
                SwingUtilities.invokeLater(() -> done.accept(false));
                return;
            }
            // rilettura della definizione e scrittura dell'archivio fuori dall'EDT; l'esito torna sull'EDT
            Thread t = new Thread(() -> {
                String problem = archive(catalog, name, body);
                SwingUtilities.invokeLater(() -> {
                    view.message(PipelineView.MessageKind.SUCCESS, Texts.get("visual.view.saved", name));
                    if (problem != null) {
                        view.message(PipelineView.MessageKind.WARNING, Texts.get("visual.view.notArchived", name,
                                problem));
                    }
                    done.accept(true);
                });
            }, "RamaSQL - archivio delle viste");
            t.setDaemon(true);
            t.start();
        });
    }

    /**
     * Rilegge la definizione dal server e archivia il sorgente. Se non riesce la vista resta (manca solo il livello 1
     * della riapertura): restituisce il motivo da dire all'utente, {@code null} se è andato tutto bene.
     */
    private String archive(String catalog, String name, String body) {
        if (store == null) {
            return null;
        }
        try {
            reader.invalidate(catalog, name);
            Optional<ViewDef> def = reader.view(catalog, name);
            if (def.isEmpty() || def.get().selectSql().isBlank()) {
                // senza la definizione (vista sparita, o utente senza SHOW VIEW) il confronto del livello 1 non varrebbe
                return Texts.get("visual.view.notReread");
            }
            store.save(new ViewSourceStore.Entry(server, catalog, name, body, def.get().selectSql()));
            return null;
        } catch (SQLException | IOException | RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
    }
}
