/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.connection.Session;
import it.ramasql.core.metadata.MetadataReader;

/**
 * L'<b>unico</b> punto del prodotto che esegue SQL per conto dell'utente (regola 3 di {@code CLAUDE.md},
 * {@code ARCHITECTURE.md} §4, test d'architettura T3.9).
 *
 * <ul>
 *   <li>Esegue gli {@link SqlScript} <b>una istruzione alla volta</b> sulla connessione principale della
 *       {@link Session}, su un <b>thread dedicato</b> (mai l'EDT): {@link #submit} restituisce subito,
 *       {@link #run} aspetta.</li>
 *   <li><b>Mai transazioni</b> (ADR-010): la connessione è in autocommit (se qualcuno l'ha tolto, qui si rimette) e
 *       l'esecutore non emette mai {@code START TRANSACTION}, {@code COMMIT} o {@code ROLLBACK}. Ogni istruzione
 *       riuscita è subito definitiva.</li>
 *   <li>Si <b>ferma alla prima istruzione non riuscita</b>: {@link ScriptResult} dice che cosa è stato applicato,
 *       che cosa è fallito (codice, SQLSTATE, messaggio) e che cosa non è stato tentato.</li>
 *   <li>Per ogni istruzione: righe interessate, durata, avvisi del server ({@code SHOW WARNINGS}, letti dal driver),
 *       risultati tabellari fino a {@link #rowLimit()} righe (le altre non si leggono: {@link ResultTable#truncated()}).</li>
 *   <li>Registra ogni istruzione tentata nel {@link SqlLog} (origine, esito, durata, righe).</li>
 *   <li><b>Interruzione</b>: {@link #interrupt()} manda {@code KILL QUERY} dalla connessione di servizio; l'istruzione
 *       in corso risulta {@code INTERRUPTED}, le successive non si tentano, la sessione resta utilizzabile.</li>
 *   <li>Dopo ogni DDL, <b>anche non riuscito o interrotto</b> (può essere stato applicato in parte), invalida la cache del {@link MetadataReader} per il catalogo toccato (e l'elenco dei
 *       cataloghi dopo {@code CREATE/DROP DATABASE}): il navigatore, che ascolta il lettore, si aggiorna da solo.</li>
 * </ul>
 */
public final class SqlExecutor implements AutoCloseable {

    /** Righe lette al massimo da un risultato tabellare (l'editor SQL); la griglia pagina per conto suo. */
    public static final int DEFAULT_ROW_LIMIT = 1000;

    private final Session session;
    private final SqlLog log;
    private final MetadataReader metadata;
    private final String connectionLabel;
    private final ExecutorService thread;
    private volatile int rowLimit = DEFAULT_ROW_LIMIT;
    /** Guarda {@link #current}: l'inizio e la fine di uno script e il {@code KILL QUERY} non si sovrappongono mai. */
    private final Object lock = new Object();
    /** Lo script in esecuzione ({@code null} = nessuno); protetto da {@link #lock}. */
    private Run current;
    /** Script in coda, non ancora partiti: {@link #close()} li chiude con un errore invece di lasciarli appesi. */
    private final Set<CompletableFuture<ScriptResult>> queued = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /** Un'esecuzione: la richiesta d'interruzione vale solo per lei, mai per lo script successivo. */
    private static final class Run {
        volatile boolean cancel;
    }

    /**
     * @param session  sessione: si usa la sua connessione principale
     * @param log      registro da alimentare
     * @param metadata lettore da invalidare dopo i DDL; {@code null} = nessuno
     */
    public SqlExecutor(Session session, SqlLog log, MetadataReader metadata) {
        this.session = Objects.requireNonNull(session, "session");
        this.log = Objects.requireNonNull(log, "log");
        this.metadata = metadata;
        this.connectionLabel = session.profile().name() + " (" + session.serverInfo().displayName() + ")";
        this.thread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RamaSQL - esecuzione SQL");
            t.setDaemon(true);
            return t;
        });
    }

    public SqlLog log() {
        return log;
    }

    public int rowLimit() {
        return rowLimit;
    }

    /** Righe lette al massimo da ogni risultato tabellare (almeno 1). */
    public void setRowLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limite di righe non valido: " + limit);
        }
        this.rowLimit = limit;
    }

    /** Uno script è in esecuzione. */
    public boolean isRunning() {
        synchronized (lock) {
            return current != null;
        }
    }

    /**
     * Mette in coda lo script sul thread dell'esecutore e restituisce subito. Gli script si eseguono uno dopo
     * l'altro, mai insieme. Dopo {@link #close()} non accetta più nulla ({@code RejectedExecutionException}); gli script
     * rimasti in coda si chiudono con un'eccezione, mai appesi.
     *
     * @param listener avanzamento (sul thread dell'esecutore); {@code null} = nessuno
     */
    public CompletableFuture<ScriptResult> submit(SqlScript script, ExecutionListener listener) {
        return submit(script, listener, rowLimit);
    }

    /**
     * Come {@link #submit(SqlScript, ExecutionListener)}, ma leggendo al massimo {@code rowsToRead} righe da ogni
     * risultato invece del {@link #rowLimit()} dell'esecutore: serve a chi pagina per conto suo (la griglia di
     * data-entry chiede {@code righePerPagina + 1} per sapere se c'è un'altra pagina). Non cambia il limite generale.
     *
     * @param rowsToRead righe da leggere al massimo (almeno 1)
     */
    public CompletableFuture<ScriptResult> submit(SqlScript script, ExecutionListener listener, int rowsToRead) {
        Objects.requireNonNull(script, "script");
        if (rowsToRead < 1) {
            throw new IllegalArgumentException("limite di righe non valido: " + rowsToRead);
        }
        ExecutionListener l = listener == null ? new ExecutionListener() { } : listener;
        if (closed) {
            throw new RejectedExecutionException(CoreMessages.get("exec.closed"));
        }
        CompletableFuture<ScriptResult> future = new CompletableFuture<>();
        queued.add(future);
        try {
            thread.execute(() -> {
                if (!queued.remove(future)) {
                    return;   // chiuso da close() prima di partire
                }
                try {
                    future.complete(runNow(script, l, rowsToRead));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            queued.remove(future);
            throw e;
        }
        if (closed && queued.remove(future)) {   // close() arrivato mentre si accodava
            future.completeExceptionally(new IllegalStateException(CoreMessages.get("exec.closed")));
        }
        return future;
    }

    /** Esegue lo script e aspetta la fine (per i test e per chi è già fuori dall'EDT). */
    public ScriptResult run(SqlScript script) {
        return run(script, null);
    }

    public ScriptResult run(SqlScript script, ExecutionListener listener) {
        return run(script, listener, rowLimit);
    }

    /** Come {@link #run(SqlScript, ExecutionListener)}, con il limite di righe di questa sola lettura. */
    public ScriptResult run(SqlScript script, ExecutionListener listener, int rowsToRead) {
        try {
            return submit(script, listener, rowsToRead).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    /**
     * Interrompe lo script in corso: l'istruzione che sta girando viene fermata con {@code KILL QUERY} (dalla
     * connessione di servizio) e le successive non si eseguono.
     *
     * @return {@code false} se non c'era nulla in esecuzione
     */
    public boolean interrupt() throws SQLException {
        // sotto il lucchetto: finché il KILL QUERY non è tornato, lo script non può finire né il successivo
        // cominciare, quindi il KILL colpisce solo lo script che si voleva fermare
        synchronized (lock) {
            Run run = current;
            if (run == null) {
                return false;
            }
            run.cancel = true;
            session.interruptRunningStatement();
            return true;
        }
    }

    /**
     * Ferma il thread dell'esecutore (non chiude la sessione). Gli script in coda mai partiti non restano appesi: i loro
     * future si completano con un'eccezione ({@code IllegalStateException}, «esecutore chiuso»).
     */
    @Override
    public void close() {
        closed = true;
        for (CompletableFuture<ScriptResult> f : List.copyOf(queued)) {
            if (queued.remove(f)) {
                f.completeExceptionally(new IllegalStateException(CoreMessages.get("exec.closed")));
            }
        }
        thread.shutdownNow();
        try {
            thread.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ================================================================ esecuzione

    private ScriptResult runNow(SqlScript script, ExecutionListener listener, int rowsToRead) {
        Run run = new Run();
        synchronized (lock) {
            current = run;
        }
        long start = System.nanoTime();
        List<StatementResult> results = new ArrayList<>();
        try {
            quietly(() -> listener.scriptStarted(script));
            Connection con = session.mainConnection();
            ensureAutocommit(con);
            for (int i = 0; i < script.size(); i++) {
                if (run.cancel) {
                    break;
                }
                SqlStatement statement = script.statements().get(i);
                long statementStart = System.nanoTime();
                StatementResult result;
                try {
                    listener.statementStarted(script, i, statement);
                    result = runOne(con, i, statement, run, rowsToRead);
                } catch (RuntimeException e) {
                    // qualunque guasto (del driver, della connessione, di un ascoltatore) lascia traccia nel registro
                    result = internalError(i, statement, e, statementStart);
                }
                results.add(result);
                record(script, result);
                // anche dopo un errore: un DDL non riuscito può essere stato applicato in parte (MariaDB DROP TABLE a, b)
                quietly(() -> invalidateMetadata(statement.text()));
                StatementResult finished = result;
                quietly(() -> listener.statementFinished(script, finished));
                if (!result.isOk()) {
                    break;
                }
            }
        } finally {
            synchronized (lock) {
                if (current == run) {
                    current = null;
                }
            }
        }
        ScriptResult outcome = new ScriptResult(script, results, millisSince(start), run.cancel);
        quietly(() -> listener.scriptFinished(outcome));
        return outcome;
    }

    /** Un ascoltatore che si guasta non ferma l'esecuzione né il registro. */
    private static void quietly(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // l'esito resta quello dell'esecuzione: l'ascoltatore è solo avvisato
        }
    }

    /** Un guasto dentro il client (non un errore del server): l'istruzione risulta non riuscita, con la spiegazione. */
    private static StatementResult internalError(int index, SqlStatement statement, RuntimeException e,
            long startNanos) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new StatementResult(index, statement, StatementResult.Status.FAILED, 0, millisSince(startNanos),
                List.of(), List.of(), new StatementResult.ServerError(0, "",
                        CoreMessages.get("exec.internalError", detail)));
    }

    /** L'autocommit non si tocca mai, tranne per rimetterlo se qualcuno l'ha tolto (ADR-010). */
    private static void ensureAutocommit(Connection con) {
        try {
            if (!con.getAutoCommit()) {
                con.setAutoCommit(true);
            }
        } catch (SQLException | RuntimeException e) {
            // la connessione è rotta (o manca): lo dirà la prima istruzione, con il messaggio del server
        }
    }

    private StatementResult runOne(Connection con, int index, SqlStatement statement, Run run, int rowsToRead) {
        long start = System.nanoTime();
        List<ResultTable> tables = new ArrayList<>();
        List<StatementResult.Warning> warnings = new ArrayList<>();
        long affected = 0;
        try (Statement st = con.createStatement()) {
            // lettura a flusso: oltre il limite le righe non si tengono in memoria
            st.setFetchSize(Math.min(rowsToRead, 999) + 1);   // niente overflow con limiti molto grandi
            boolean isResultSet = st.execute(statement.text());
            while (true) {
                if (isResultSet) {
                    try (ResultSet rs = st.getResultSet()) {
                        ResultTable table = read(rs, rowsToRead);
                        tables.add(table);
                        affected += table.rowCount();
                    }
                } else {
                    long count = st.getLargeUpdateCount();
                    if (count == -1) {
                        break;
                    }
                    affected += count;
                }
                isResultSet = st.getMoreResults();
            }
            for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning()) {
                warnings.add(new StatementResult.Warning(w.getErrorCode(), w.getMessage()));
            }
        } catch (RuntimeException e) {
            return internalError(index, statement, e, start);
        } catch (SQLException e) {
            StatementResult.Status status = run.cancel
                    ? StatementResult.Status.INTERRUPTED : StatementResult.Status.FAILED;
            return new StatementResult(index, statement, status, 0, millisSince(start), warnings, List.of(),
                    new StatementResult.ServerError(e.getErrorCode(), e.getSQLState(), e.getMessage()));
        }
        if (run.cancel && statement.risk() == RiskLevel.SAFE) {
            // MySQL interrompe SELECT SLEEP() senza errore: il risultato non vale, l'istruzione è interrotta
            return new StatementResult(index, statement, StatementResult.Status.INTERRUPTED, 0, millisSince(start),
                    warnings, tables, new StatementResult.ServerError(0, "", CoreMessages.get("exec.interrupted")));
        }
        return new StatementResult(index, statement, StatementResult.Status.OK, affected, millisSince(start),
                warnings, tables, null);
    }

    private ResultTable read(ResultSet rs, int rowsToRead) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<ResultTable.Column> columns = new ArrayList<>(n);
        for (int c = 1; c <= n; c++) {
            columns.add(new ResultTable.Column(md.getColumnLabel(c), md.getColumnTypeName(c), md.getColumnType(c)));
        }
        int limit = rowsToRead;
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= limit) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(n);
            for (int c = 1; c <= n; c++) {
                row.add(value(rs.getObject(c)));
            }
            rows.add(row);
        }
        return new ResultTable(columns, rows, truncated);
    }

    /** I LOB si leggono subito: il risultato non deve tenere risorse del driver. */
    private static Object value(Object v) throws SQLException {
        if (v instanceof Blob b) {
            return b.getBytes(1, (int) b.length());
        }
        if (v instanceof Clob c) {
            return c.getSubString(1, (int) c.length());
        }
        return v;
    }

    private void record(SqlScript script, StatementResult r) {
        SqlLog.Outcome outcome = switch (r.status()) {
            case OK -> SqlLog.Outcome.OK;
            case FAILED -> SqlLog.Outcome.ERROR;
            case INTERRUPTED -> SqlLog.Outcome.INTERRUPTED;
        };
        StatementResult.ServerError e = r.error();
        log.add(connectionLabel, script.originOf(r.statement()), r.statement().text(), outcome,
                e == null ? 0 : e.code(), e == null ? "" : e.sqlState(), e == null ? "" : e.message(),
                r.durationMillis(), r.affectedRows());
    }

    private void invalidateMetadata(String sql) {
        if (metadata == null) {
            return;
        }
        DdlTargets targets = DdlTargets.of(sql);
        if (!targets.ddl()) {
            return;
        }
        if (targets.catalogList()) {
            metadata.invalidateCatalogs();
        }
        for (String catalog : targets.catalogs()) {
            metadata.invalidate(catalog);
        }
        if (targets.currentCatalog()) {
            try {
                String current = session.currentCatalog();
                if (current != null) {
                    metadata.invalidate(current);
                }
            } catch (SQLException e) {
                metadata.invalidateAll();   // non si sa dove si è: meglio rileggere tutto
            }
        }
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
