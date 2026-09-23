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

import java.util.Objects;

import it.ramasql.app.pipeline.PipelineView;
import it.ramasql.app.pipeline.SqlPipeline;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.metadata.MetadataReader;

/**
 * Ciò che vive finché dura una connessione: la {@link Session}, il {@link MetadataReader} (canale interno dei
 * metadati, con la sua cache), il {@link SqlExecutor} (unico esecutore dell'SQL dell'utente, che alimenta il
 * {@link SqlLog} della finestra) e la {@link SqlPipeline} che li collega all'interfaccia. Si crea alla connessione e si
 * chiude alla disconnessione o alla chiusura della finestra; il registro invece appartiene alla finestra e resta.
 */
public final class SessionWorkspace {

    private final Session session;
    private final MetadataReader reader;
    private final SqlExecutor executor;
    private final SqlPipeline pipeline;
    private final WorkspacePrompts prompts;
    private final PipelineView view;
    private volatile boolean closed;
    private volatile Thread closer;

    private SessionWorkspace(Session session, MetadataReader reader, SqlExecutor executor, SqlPipeline pipeline,
            WorkspacePrompts prompts, PipelineView view) {
        this.session = session;
        this.reader = reader;
        this.executor = executor;
        this.pipeline = pipeline;
        this.prompts = prompts;
        this.view = view;
    }

    /**
     * Prepara lettore ed esecutore per una sessione appena aperta. Nessuna chiamata di rete: si può fare sull'EDT.
     *
     * @param log      registro della finestra (unico, sopravvive alle connessioni)
     * @param rowLimit righe lette al massimo da un risultato (impostazioni)
     */
    public static SessionWorkspace open(Session session, SqlLog log, int rowLimit, WorkspacePrompts prompts,
            PipelineView view) {
        Objects.requireNonNull(session, "session");
        MetadataReader reader = MetadataReader.of(session);
        SqlExecutor executor = new SqlExecutor(session, log, reader);
        executor.setRowLimit(Math.max(1, rowLimit));
        return new SessionWorkspace(session, reader, executor, new SqlPipeline(executor, prompts, view), prompts,
                view);
    }

    public Session session() {
        return session;
    }

    public MetadataReader reader() {
        return reader;
    }

    public SqlExecutor executor() {
        return executor;
    }

    public SqlPipeline pipeline() {
        return pipeline;
    }

    public WorkspacePrompts prompts() {
        return prompts;
    }

    public PipelineView view() {
        return view;
    }

    /** Nuovo limite di righe (cambiato nelle impostazioni). */
    public void setRowLimit(int rowLimit) {
        executor.setRowLimit(Math.max(1, rowLimit));
    }

    /**
     * Chiude l'esecutore (il suo thread) senza bloccare chi chiama: la chiusura aspetta al massimo 2 s un'istruzione
     * in corso, quindi si fa su un thread a parte. La sessione la chiude il {@code ConnectionController}.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Thread t = new Thread(executor::close, "RamaSQL - chiusura esecutore");
        t.setDaemon(true);
        closer = t;
        t.start();
    }

    public boolean isClosed() {
        return closed;
    }

    /** Aspetta che la chiusura dell'esecutore sia finita (test, chiusura del programma). */
    public void awaitClosed(long millis) throws InterruptedException {
        Thread t = closer;
        if (t != null) {
            t.join(millis);
        }
    }
}
