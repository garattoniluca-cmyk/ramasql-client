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
import java.sql.PreparedStatement;
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
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

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

    /**
     * Esegue lo script e poi un <b>inserimento a lotti</b> (l'importazione, {@code ADR-025}). L'ultima istruzione dello
     * script deve essere {@link BatchInsert#statement()}: è ciò che l'anteprima ha mostrato. Le istruzioni prima (svuota
     * la tabella, crea la tabella) si eseguono come in {@link #submit}; se una non riesce l'inserimento non comincia.
     * Poi le righe arrivano da {@code source} un lotto alla volta, in autocommit, senza transazioni.
     * {@link #interrupt()} ferma l'istruzione in corso ({@code KILL QUERY}) e l'inserimento; le righe già inserite
     * restano e l'esito le conta. Nel registro: le istruzioni prima, una per una, e l'inserimento come <b>una</b> riga
     * (l'istruzione preparata, i lotti, le righe inserite e scartate).
     */
    public CompletableFuture<BatchResult> submitBatchInsert(SqlScript script, BatchInsert insert, BatchSource source,
            BatchListener listener) {
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(insert, "insert");
        Objects.requireNonNull(source, "source");
        if (script.isEmpty() || !script.statements().get(script.size() - 1).text().equals(insert.statement().text())) {
            throw new IllegalArgumentException("l'ultima istruzione dello script deve essere l'INSERT preparata");
        }
        BatchListener l = listener == null ? new BatchListener() { } : listener;
        if (closed) {
            throw new RejectedExecutionException(CoreMessages.get("exec.closed"));
        }
        CompletableFuture<BatchResult> future = new CompletableFuture<>();
        CompletableFuture<ScriptResult> marker = new CompletableFuture<>();
        queued.add(marker);
        try {
            thread.execute(() -> {
                if (!queued.remove(marker)) {
                    future.completeExceptionally(new IllegalStateException(CoreMessages.get("exec.closed")));
                    return;
                }
                try {
                    future.complete(runBatchNow(script, insert, source, l));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            queued.remove(marker);
            throw e;
        }
        marker.whenComplete((r, e) -> {
            if (e != null) {
                future.completeExceptionally(e);   // chiuso prima di partire
            }
        });
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
        try {
            return runStatements(script, listener, rowsToRead, run);
        } finally {
            synchronized (lock) {
                if (current == run) {
                    current = null;
                }
            }
        }
    }

    /** Le istruzioni dello script, con l'interruzione di {@code run} (che il chiamante ha reso quella corrente). */
    private ScriptResult runStatements(SqlScript script, ExecutionListener listener, int rowsToRead, Run run) {
        long start = System.nanoTime();
        List<StatementResult> results = new ArrayList<>();
        {
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
        }
        ScriptResult outcome = new ScriptResult(script, results, millisSince(start), run.cancel);
        quietly(() -> listener.scriptFinished(outcome));
        return outcome;
    }

    // ================================================================ inserimento a lotti (ADR-025)

    private BatchResult runBatchNow(SqlScript script, BatchInsert insert, BatchSource source, BatchListener l) {
        long start = System.nanoTime();
        SqlScript before = new SqlScript(script.title(), script.origin(),
                script.statements().subList(0, script.size() - 1));
        // una sola esecuzione «corrente» per tutta l'importazione: Interrompi vale in ogni momento
        Run run = new Run();
        synchronized (lock) {
            current = run;
        }
        ScriptResult beforeResult;
        try {
            beforeResult = runStatements(before, l, rowLimit, run);
        } catch (RuntimeException e) {
            clear(run);
            throw e;
        }
        if (!beforeResult.completed()) {
            clear(run);
            return new BatchResult(beforeResult, false, 0, 0, 0, List.of(), 0, beforeResult.interrupted(), null, null,
                    millisSince(start));
        }
        BatchRun b = new BatchRun(insert, l, run);
        try {
            Connection con = session.mainConnection();
            ensureAutocommit(con);
            int max = insert.rowsPerBatch();
            while (!run.cancel && b.fatal == null) {
                List<BatchSource.Row> rows;
                try {
                    rows = source.nextBatch(max);
                } catch (Exception e) {
                    b.sourceFailure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    break;
                }
                if (rows == null || rows.isEmpty()) {
                    break;
                }
                b.batches++;
                if (insert.atomic()) {
                    b.multiRow(con, rows);
                } else {
                    b.oneByOne(con, rows);
                }
                long batches = b.batches;
                quietly(() -> l.batchFinished(batches, b.inserted, b.rejectedCount, b.duplicates));
            }
        } catch (RuntimeException e) {
            b.fatal = new StatementResult.ServerError(0, "", CoreMessages.get("exec.internalError",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            clear(run);
            b.close();
        }
        boolean interrupted = run.cancel;
        long duration = millisSince(start);
        SqlLog.Outcome outcome = b.fatal != null ? SqlLog.Outcome.ERROR
                : interrupted ? SqlLog.Outcome.INTERRUPTED : SqlLog.Outcome.OK;
        String note = CoreMessages.get("log.batch.note", b.batches, b.inserted, b.rejectedCount, b.duplicates)
                + (b.sourceFailure != null ? " · " + CoreMessages.get("log.batch.sourceFailure", b.sourceFailure) : "");
        log.add(connectionLabel, script.originOf(insert.statement()), insert.statement().text(), outcome,
                b.fatal == null ? 0 : b.fatal.code(), b.fatal == null ? "" : b.fatal.sqlState(),
                b.fatal == null ? (interrupted ? CoreMessages.get("exec.interrupted") : "") : b.fatal.message(),
                duration, b.inserted, note, true);
        return new BatchResult(beforeResult, true, b.batches, b.inserted, b.duplicates, b.errors, b.rejectedCount,
                interrupted, b.fatal, b.sourceFailure, duration);
    }

    private void clear(Run run) {
        synchronized (lock) {
            if (current == run) {
                current = null;
            }
        }
    }

    /** Stato di un inserimento a lotti in corso. */
    private final class BatchRun {
        final BatchInsert insert;
        final BatchListener listener;
        final Run run;
        long batches;
        long inserted;
        long duplicates;
        long rejectedCount;
        final List<BatchResult.RowError> errors = new ArrayList<>();
        StatementResult.ServerError fatal;
        String sourceFailure;
        private PreparedStatement full;
        private PreparedStatement single;

        BatchRun(BatchInsert insert, BatchListener listener, Run run) {
            this.insert = insert;
            this.listener = listener;
            this.run = run;
        }

        /**
         * InnoDB: il lotto in una sola INSERT. Se il server la rifiuta (e non per un'interruzione) non ha lasciato
         * righe: si divide a metà e si riprova ciascuna metà, fino alla singola riga, che dà il motivo del rifiuto.
         * Così un duplicato fra mille righe costa una decina di istruzioni, non mille commit.
         */
        void multiRow(Connection con, List<BatchSource.Row> rows) {
            if (rows.isEmpty() || run.cancel || fatal != null) {
                return;
            }
            if (rows.size() == 1) {
                one(con, rows.get(0));
                return;
            }
            try {
                PreparedStatement ps;
                if (rows.size() == insert.rowsPerBatch()) {
                    if (full == null) {
                        full = con.prepareStatement(insert.multiRow(rows.size()));
                    }
                    ps = full;
                } else {
                    ps = con.prepareStatement(insert.multiRow(rows.size()));
                }
                try {
                    int k = 1;
                    for (BatchSource.Row r : rows) {
                        for (Object v : r.params()) {
                            ps.setObject(k++, v);
                        }
                    }
                    inserted += ps.executeLargeUpdate();
                } finally {
                    if (ps != full) {
                        ps.close();
                    }
                }
            } catch (SQLException e) {
                if (run.cancel || isConnectionError(e)) {
                    stop(e);
                    return;
                }
                int half = rows.size() / 2;
                multiRow(con, rows.subList(0, half));
                multiRow(con, rows.subList(half, rows.size()));
            }
        }

        /** MyISAM e simili: righe una per volta, in un lotto JDBC (ogni riga ha il suo esito). */
        void oneByOne(Connection con, List<BatchSource.Row> rows) {
            int[] counts;
            try {
                PreparedStatement ps = single(con);
                for (BatchSource.Row r : rows) {
                    bind(ps, r);
                    ps.addBatch();
                }
                counts = ps.executeBatch();
            } catch (java.sql.BatchUpdateException e) {
                counts = e.getUpdateCounts();
                if (run.cancel || isConnectionError(e)) {
                    countDone(counts);
                    stop(e);
                    return;
                }
            } catch (SQLException e) {
                // senza gli esiti riga per riga non si sa che cosa è entrato (MyISAM non annulla): ci si ferma
                // invece di ritentare, che potrebbe inserire due volte le stesse righe
                stop(e);
                if (fatal == null && !run.cancel) {
                    fatal = new StatementResult.ServerError(e.getErrorCode(), e.getSQLState(), e.getMessage());
                }
                return;
            }
            for (int i = 0; i < rows.size(); i++) {
                int c = i < counts.length ? counts[i] : Statement.EXECUTE_FAILED;
                if (c >= 0 || c == Statement.SUCCESS_NO_INFO) {
                    inserted += Math.max(1, c);
                } else {
                    if (run.cancel || fatal != null) {
                        return;
                    }
                    one(con, rows.get(i));   // per sapere perché (o per tentarla, se il lotto si è fermato prima)
                }
            }
        }

        private void countDone(int[] counts) {
            if (counts == null) {
                return;
            }
            for (int c : counts) {
                if (c >= 0 || c == Statement.SUCCESS_NO_INFO) {
                    inserted += Math.max(1, c);
                }
            }
        }

        /** Una riga da sola: inserita, duplicato ignorato o rifiutata con il motivo del server. */
        private void one(Connection con, BatchSource.Row r) {
            try {
                PreparedStatement ps = single(con);
                ps.clearBatch();
                bind(ps, r);
                inserted += Math.max(0, ps.executeLargeUpdate());
            } catch (SQLException e) {
                if (run.cancel || isConnectionError(e)) {
                    stop(e);
                    return;
                }
                if (insert.ignoreDuplicates() && e.getErrorCode() == 1062) {
                    duplicates++;
                    return;
                }
                rejectedCount++;
                BatchResult.RowError err = new BatchResult.RowError(r.line(), e.getErrorCode(), e.getSQLState(),
                        e.getMessage());
                if (errors.size() < BatchResult.MAX_DETAILS) {
                    errors.add(err);
                }
                quietly(() -> listener.rowRejected(err));
            }
        }

        private PreparedStatement single(Connection con) throws SQLException {
            if (single == null) {
                single = con.prepareStatement(insert.statement().text());
            }
            return single;
        }

        private void bind(PreparedStatement ps, BatchSource.Row r) throws SQLException {
            Object[] p = r.params();
            for (int i = 0; i < p.length; i++) {
                ps.setObject(i + 1, p[i]);
            }
        }

        private void stop(SQLException e) {
            if (!run.cancel) {
                fatal = new StatementResult.ServerError(e.getErrorCode(), e.getSQLState(), e.getMessage());
            }
        }

        void close() {
            for (PreparedStatement ps : new PreparedStatement[] {full, single}) {
                if (ps != null) {
                    try {
                        ps.close();
                    } catch (SQLException ignored) {
                        // la connessione può essere già caduta
                    }
                }
            }
        }
    }

    // ================================================================ lettura a flusso (dump, Step 10)

    /**
     * Legge le righe di una {@code SELECT} <b>a flusso</b> (il driver non le tiene tutte in memoria) e le passa una alla
     * volta a {@code sink}: serve al dump, che le scrive nel file man mano. I valori arrivano come li scrive il server:
     * {@code byte[]} per i tipi binari (BLOB, BINARY, BIT, geometrie), {@link String} per tutto il resto, {@code null}
     * per NULL. Nel registro: la {@code SELECT} con il numero di righe lette. {@link #interrupt()} la ferma.
     *
     * @return righe lette
     */
    public CompletableFuture<Long> submitStreamRead(SqlStatement select, RowSink sink) {
        Objects.requireNonNull(select, "select");
        Objects.requireNonNull(sink, "sink");
        if (closed) {
            throw new RejectedExecutionException(CoreMessages.get("exec.closed"));
        }
        CompletableFuture<Long> future = new CompletableFuture<>();
        CompletableFuture<ScriptResult> marker = new CompletableFuture<>();
        queued.add(marker);
        try {
            thread.execute(() -> {
                if (!queued.remove(marker)) {
                    future.completeExceptionally(new IllegalStateException(CoreMessages.get("exec.closed")));
                    return;
                }
                try {
                    future.complete(streamNow(select, sink));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            queued.remove(marker);
            throw e;
        }
        marker.whenComplete((r, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** Chi riceve le righe lette a flusso; un'eccezione ferma la lettura (e finisce nell'esito). */
    public interface RowSink {
        void row(Object[] values) throws Exception;
    }

    private long streamNow(SqlStatement select, RowSink sink) throws Exception {
        Run run = new Run();
        synchronized (lock) {
            current = run;
        }
        long start = System.nanoTime();
        long rows = 0;
        SqlLog.Outcome outcome = SqlLog.Outcome.OK;
        StatementResult.ServerError error = null;
        try {
            Connection con = session.mainConnection();
            ensureAutocommit(con);
            try (Statement st = con.createStatement()) {
                st.setFetchSize(1000);   // a flusso: il driver legge a blocchi, non tutto il risultato
                try (ResultSet rs = st.executeQuery(select.text())) {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    boolean[] binary = new boolean[n + 1];
                    for (int c = 1; c <= n; c++) {
                        binary[c] = isBinary(md.getColumnType(c), md.getColumnTypeName(c));
                    }
                    while (rs.next()) {
                        if (run.cancel) {
                            break;
                        }
                        Object[] values = new Object[n];
                        for (int c = 1; c <= n; c++) {
                            values[c - 1] = binary[c] ? rs.getBytes(c) : rs.getString(c);
                        }
                        sink.row(values);
                        rows++;
                    }
                }
            }
            if (run.cancel) {
                outcome = SqlLog.Outcome.INTERRUPTED;
            }
            return rows;
        } catch (SQLException e) {
            outcome = run.cancel ? SqlLog.Outcome.INTERRUPTED : SqlLog.Outcome.ERROR;
            error = new StatementResult.ServerError(e.getErrorCode(), e.getSQLState(), e.getMessage());
            throw e;
        } catch (Exception e) {
            outcome = SqlLog.Outcome.ERROR;
            error = new StatementResult.ServerError(0, "", e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage());
            throw e;
        } finally {
            clear(run);
            log.add(connectionLabel, select.origin(), select.text(), outcome, error == null ? 0 : error.code(),
                    error == null ? "" : error.sqlState(), error == null ? "" : error.message(),
                    millisSince(start), rows);
        }
    }

    /** Tipi il cui valore è una sequenza di byte. */
    private static boolean isBinary(int jdbcType, String typeName) {
        return switch (jdbcType) {
            case java.sql.Types.BINARY, java.sql.Types.VARBINARY, java.sql.Types.LONGVARBINARY, java.sql.Types.BLOB,
                java.sql.Types.BIT -> true;
            default -> typeName != null && typeName.toUpperCase(java.util.Locale.ROOT).matches(
                    "GEOMETRY|POINT|LINESTRING|POLYGON|MULTIPOINT|MULTILINESTRING|MULTIPOLYGON|GEOMETRYCOLLECTION|GEOMCOLLECTION");
        };
    }

    // ================================================================ script da file (ripristino, Step 10)

    /** Oltre questa lunghezza un'istruzione si registra abbreviata (i dump hanno INSERT di centinaia di kB). */
    static final int LOG_TEXT_LIMIT = 300;

    /**
     * Esegue uno script {@code .sql} letto <b>a flusso</b> da un file (il ripristino di un dump): un'istruzione alla
     * volta, in autocommit, come {@link #submit}, ma senza tenere lo script in memoria. Con {@code continueOnError} un
     * errore si annota e si va avanti; altrimenti ci si ferma — e ci si ferma comunque se non riesce un {@code USE} o un
     * {@code CREATE DATABASE} (le istruzioni seguenti finirebbero nel catalogo sbagliato) o se cade la connessione.
     * Nel registro finiscono, una per una (le lunghissime abbreviate, con la riga del file), le istruzioni che cambiano
     * struttura, sessione o transazione, le distruttive e quelle non riuscite, al più {@link #MAX_FILE_LOG}; in fondo
     * una riga riassuntiva conta tutte le altre (gli INSERT dei dati: un dump con un INSERT per riga ne ha milioni, che
     * nel registro sommergerebbero memoria e interfaccia). Prima e dopo si leggono le impostazioni della sessione: se il
     * file le ha lasciate cambiate, l'esito propone le istruzioni per rimetterle ({@link ScriptFileResult#restore()}).
     * {@link #interrupt()} ferma l'istruzione in corso e lo script.
     *
     * @param before   istruzioni da eseguire prima del file (il {@code USE} del catalogo scelto), mostrate
     *                 nell'anteprima; vuoto = nessuna
     * @param reader   il file, già aperto (lo chiude l'esecutore)
     * @param fileName per il registro
     */
    public CompletableFuture<ScriptFileResult> submitScriptFile(List<SqlStatement> before, ScriptReader reader,
            String fileName, boolean continueOnError, String origin, ScriptFileListener listener) {
        return submitScriptFile(before, reader, fileName, continueOnError, origin, UnaryOperator.identity(), listener);
    }

    /**
     * Come {@link #submitScriptFile(List, ScriptReader, String, boolean, String, ScriptFileListener)}, con una
     * sostituzione applicata a ogni istruzione prima di eseguirla (le collation che il server non conosce,
     * {@link CollationCompat}): nel registro finisce il testo eseguito.
     */
    public CompletableFuture<ScriptFileResult> submitScriptFile(List<SqlStatement> before, ScriptReader reader,
            String fileName, boolean continueOnError, String origin, UnaryOperator<String> rewrite,
            ScriptFileListener listener) {
        Objects.requireNonNull(reader, "reader");
        UnaryOperator<String> rw = rewrite == null ? UnaryOperator.identity() : rewrite;
        ScriptFileListener l = listener == null ? new ScriptFileListener() { } : listener;
        if (closed) {
            throw new RejectedExecutionException(CoreMessages.get("exec.closed"));
        }
        CompletableFuture<ScriptFileResult> future = new CompletableFuture<>();
        CompletableFuture<ScriptResult> marker = new CompletableFuture<>();
        queued.add(marker);
        try {
            thread.execute(() -> {
                if (!queued.remove(marker)) {
                    future.completeExceptionally(new IllegalStateException(CoreMessages.get("exec.closed")));
                    return;
                }
                try {
                    future.complete(runFileNow(before, reader, fileName, continueOnError, origin, rw, l));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                } finally {
                    try {
                        reader.close();
                    } catch (java.io.IOException ignored) {
                        // il file si chiude comunque
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            queued.remove(marker);
            throw e;
        }
        marker.whenComplete((r, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private ScriptFileResult runFileNow(List<SqlStatement> before, ScriptReader reader, String fileName,
            boolean continueOnError, String origin, UnaryOperator<String> rewrite, ScriptFileListener l) {
        long start = System.nanoTime();
        Run run = new Run();
        synchronized (lock) {
            current = run;
        }
        FileRun f = new FileRun(fileName, origin);
        String readError = null;
        Connection con = null;
        List<String> sessionBefore = null;
        try {
            con = session.mainConnection();
            ensureAutocommit(con);
            try {
                sessionBefore = sessionState(con);
            } catch (SQLException e) {
                sessionBefore = null;   // senza lo stato di partenza non si propone il ripristino
            }
            SqlScript label = new SqlScript(fileName, origin, List.of());
            int index = 0;
            for (SqlStatement s : before) {
                StatementResult r = runOne(con, index++, s, run, 1);
                record(label, r);
                quietly(() -> invalidateMetadata(s.text()));
                if (!r.isOk()) {
                    f.failureCount++;
                    f.failures.add(new ScriptFileResult.Failure(0, s.text(), r.error() == null ? 0 : r.error().code(),
                            r.error() == null ? "" : r.error().sqlState(), r.error() == null ? "" : r.error().message()));
                    f.stop = r.status() == StatementResult.Status.INTERRUPTED ? ScriptFileResult.Stop.NONE
                            : ScriptFileResult.Stop.CATALOG;
                    return f.result(run.cancel, null, millisSince(start), List.of());
                }
            }
            while (!run.cancel) {
                ScriptReader.Statement st;
                try {
                    st = reader.next();
                } catch (java.io.IOException e) {
                    readError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    break;
                }
                if (st == null) {
                    break;
                }
                String text = rewrite.apply(st.text());
                SqlStatement statement = SqlStatement.of(text, origin);
                StatementResult r;
                try {
                    r = runOne(con, index++, statement, run, 1);
                } catch (RuntimeException e) {
                    r = internalError(index, statement, e, System.nanoTime());
                }
                String code = ScriptPreview.executable(text.length() > 400 ? text.substring(0, 400) : text).strip();
                ScriptFileResult.Failure failure = f.executed(st, text, !text.equals(st.text()), code, statement, r);
                quietly(() -> invalidateMetadata(text));
                if (!r.isOk()) {
                    if (r.status() == StatementResult.Status.INTERRUPTED) {
                        break;
                    }
                    quietly(() -> l.statementFailed(failure));
                    if (isConnectionLost(con, r)) {
                        f.stop = ScriptFileResult.Stop.CONNECTION;
                        break;
                    }
                    if (CATALOG_CHOICE.matcher(code).matches()) {
                        // senza il catalogo giusto le istruzioni seguenti finirebbero in quello corrente
                        f.stop = ScriptFileResult.Stop.CATALOG;
                        break;
                    }
                    if (!continueOnError) {
                        f.stop = ScriptFileResult.Stop.ERROR;
                        break;
                    }
                }
                long done = f.executed;
                long chars = reader.charsConsumed();
                quietly(() -> l.progress(done, chars));
            }
        } finally {
            clear(run);
        }
        List<String> restore = List.of();
        if (sessionBefore != null && f.stop != ScriptFileResult.Stop.CONNECTION) {
            try {
                restore = restoreStatements(sessionBefore, sessionState(con), f.locked && !f.transactionOpen());
            } catch (SQLException | RuntimeException ignored) {
                // senza lo stato della sessione non si propone nulla
            }
        }
        ScriptFileResult result = f.result(run.cancel, readError, millisSince(start), restore);
        f.summary(result);
        return result;
    }

    /** {@code USE} o {@code CREATE DATABASE}: se non riescono, ci si ferma anche con «continua». */
    private static final Pattern CATALOG_CHOICE = Pattern.compile(
            "(?is)^(?:USE\\b|CREATE\\s+(?:DATABASE|SCHEMA)\\b).*");
    /** Istruzioni registrate una per una: cambiano struttura, sessione o transazione. */
    private static final Pattern STRUCTURAL = Pattern.compile(
            "(?is)^(?:CREATE|ALTER|DROP|TRUNCATE|RENAME|USE|SET|LOCK|UNLOCK|BEGIN|START|COMMIT|ROLLBACK|SAVEPOINT"
                    + "|RELEASE|GRANT|REVOKE|FLUSH|CALL|XA)\\b.*");
    private static final Pattern IMPLICIT_COMMIT = Pattern.compile(
            "(?is)^(?:ALTER|DROP|RENAME|TRUNCATE|LOCK|GRANT|REVOKE|CREATE(?!\\s+TEMPORARY))\\b.*"
                    + "|^SET\\b.*\\bAUTOCOMMIT\\s*=\\s*(?:1|ON)\\b.*");
    /**
     * Avvisi che non dicono nulla sui dati e non si contano: le note di {@code IF [NOT] EXISTS} (1007 catalogo già
     * presente, 1008 catalogo assente, 1050 tabella già presente, 1051 tabella assente, 1305 routine assente, 1360
     * trigger assente, 4092 vista assente in MariaDB) e le sintassi deprecate (1287, 1681).
     */
    private static final Set<Integer> NOT_ABOUT_DATA = Set.of(1007, 1008, 1050, 1051, 1305, 1360, 4092, 1287, 1681);

    /** Istruzioni di un file registrate una per una, al più; le altre si contano. */
    static final int MAX_FILE_LOG = 5000;

    /** Lo stato di un'esecuzione da file: conteggi, errori, avvisi, transazioni e tabelle bloccate dal file. */
    private final class FileRun {
        final String fileName;
        final String origin;
        long executed;
        long ok;
        long failureCount;
        long warningCount;
        long rows;
        long logged;
        long firstLine = -1;
        long lastLine;
        boolean locked;
        boolean txOpen;
        boolean autocommitOff;
        ScriptFileResult.Stop stop = ScriptFileResult.Stop.NONE;
        final List<ScriptFileResult.Failure> failures = new ArrayList<>();
        final List<ScriptFileResult.Warning> warnings = new ArrayList<>();

        FileRun(String fileName, String origin) {
            this.fileName = fileName;
            this.origin = origin;
        }

        boolean transactionOpen() {
            return txOpen || autocommitOff;
        }

        ScriptFileResult.Failure executed(ScriptReader.Statement st, String text, boolean rewritten, String code,
                SqlStatement statement, StatementResult r) {
            ScriptFileResult.Failure failure = null;
            executed++;
            if (firstLine < 0) {
                firstLine = st.line();
            }
            lastLine = st.line();
            if (r.isOk()) {
                ok++;
                rows += Math.max(0, r.affectedRows());
                track(code.toUpperCase(java.util.Locale.ROOT));
            } else if (r.status() != StatementResult.Status.INTERRUPTED) {
                failureCount++;
                failure = new ScriptFileResult.Failure(st.line(), abbreviate(text, LOG_TEXT_LIMIT),
                        r.error() == null ? 0 : r.error().code(), r.error() == null ? "" : r.error().sqlState(),
                        r.error() == null ? "" : r.error().message());
                if (failures.size() < BatchResult.MAX_DETAILS) {
                    failures.add(failure);
                }
            }
            for (StatementResult.Warning w : r.warnings()) {
                if (NOT_ABOUT_DATA.contains(w.code())) {
                    continue;
                }
                warningCount++;
                if (warnings.size() < ScriptFileResult.MAX_WARNINGS) {
                    warnings.add(new ScriptFileResult.Warning(st.line(), w.code(), w.message()));
                }
            }
            boolean individually = !r.isOk() || STRUCTURAL.matcher(code).matches()
                    || statement.risk() == RiskLevel.DESTRUCTIVE;
            if (individually && logged < MAX_FILE_LOG) {
                logged++;
                recordFileStatement(origin, fileName, st, text, rewritten, r);
            }
            return failure;
        }

        /** Transazioni e blocchi aperti dal file, dal testo delle istruzioni riuscite. */
        private void track(String code) {
            if (code.matches("(?s)^LOCK\\s+TABLES?\\b.*")) {
                locked = true;
            } else if (code.matches("(?s)^UNLOCK\\s+TABLES?\\b.*")) {
                locked = false;
                txOpen = false;
            }
            if (IMPLICIT_COMMIT.matcher(code).matches()) {
                txOpen = false;
            }
            if (code.matches("(?s)^(?:BEGIN(?:\\s+WORK)?|START\\s+TRANSACTION\\b.*)\\s*$")) {
                txOpen = true;
            } else if (code.matches("(?s)^(?:COMMIT|ROLLBACK)(?!\\s+(?:WORK\\s+)?TO\\b).*")) {
                txOpen = false;
            }
            if (code.matches("(?s)^SET\\b.*\\bAUTOCOMMIT\\s*=\\s*(?:0|OFF)\\b.*")) {
                autocommitOff = true;
            } else if (code.matches("(?s)^SET\\b.*\\bAUTOCOMMIT\\s*=\\s*(?:1|ON)\\b.*")) {
                autocommitOff = false;
            }
        }

        ScriptFileResult result(boolean interrupted, String readError, long millis, List<String> restore) {
            return new ScriptFileResult(executed, ok, failures, failureCount, interrupted, stop, readError, millis,
                    warningCount, warnings, restore, transactionOpen());
        }

        /** In fondo al registro: una riga riassuntiva del file, con i conteggi di ciò che non è registrato a parte. */
        void summary(ScriptFileResult r) {
            if (executed == 0) {
                return;
            }
            String text = CoreMessages.get("log.file.summary", fileName, executed, Math.max(firstLine, 0), lastLine, ok,
                    failureCount, warningCount);
            String note = CoreMessages.get("log.file.summaryNote", logged);
            if (logged >= MAX_FILE_LOG) {
                note += "; " + CoreMessages.get("log.file.summaryCapped", MAX_FILE_LOG);
            }
            SqlLog.Outcome outcome = r.interrupted() ? SqlLog.Outcome.INTERRUPTED
                    : failureCount > 0 || r.readError() != null ? SqlLog.Outcome.ERROR : SqlLog.Outcome.OK;
            ScriptFileResult.Failure firstFailure = failures.isEmpty() ? null : failures.get(0);
            log.add(connectionLabel, origin, text, outcome, firstFailure == null ? 0 : firstFailure.code(),
                    firstFailure == null ? "" : firstFailure.sqlState(), firstFailure == null
                            ? (r.readError() == null ? "" : r.readError()) : firstFailure.message(),
                    r.durationMillis(), rows, note, false, fileName);
        }
    }

    /** La connessione è caduta (o il server è sparito): inutile andare avanti. */
    private static boolean isConnectionLost(Connection con, StatementResult r) {
        StatementResult.ServerError e = r.error();
        if (e != null && (e.sqlState().startsWith("08") || Set.of(2002, 2003, 2006, 2013, 1927, 4031).contains(e.code()))) {
            return true;
        }
        try {
            return con.isClosed();
        } catch (SQLException ex) {
            return true;
        }
    }

    /** Le impostazioni della sessione che uno script può cambiare (lettura interna, non registrata). */
    private static List<String> sessionState(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT @@SESSION.sql_mode, @@SESSION.time_zone, @@SESSION.foreign_key_checks,"
                        + " @@SESSION.unique_checks, @@SESSION.character_set_client, @@SESSION.collation_connection")) {
            rs.next();
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                String v = rs.getString(i);
                out.add(v == null ? "" : v);
            }
            return out;
        }
    }

    /** Le istruzioni che rimettono la sessione com'era prima del file (vuoto se non è cambiato nulla). */
    static List<String> restoreStatements(List<String> before, List<String> after, boolean unlock) {
        List<String> out = new ArrayList<>();
        if (unlock) {
            out.add("UNLOCK TABLES");
        }
        if (!before.get(0).equals(after.get(0))) {
            out.add("SET SESSION sql_mode = " + it.ramasql.core.sqlgen.SqlLiterals.string(before.get(0)));
        }
        if (!before.get(1).equals(after.get(1))) {
            out.add("SET SESSION time_zone = " + it.ramasql.core.sqlgen.SqlLiterals.string(before.get(1)));
        }
        if (!before.get(2).equals(after.get(2))) {
            out.add("SET FOREIGN_KEY_CHECKS = " + (isOn(before.get(2)) ? 1 : 0));
        }
        if (!before.get(3).equals(after.get(3))) {
            out.add("SET UNIQUE_CHECKS = " + (isOn(before.get(3)) ? 1 : 0));
        }
        if (!before.get(4).equals(after.get(4)) || !before.get(5).equals(after.get(5))) {
            String charset = before.get(4);
            String collation = before.get(5);
            if (charset.matches("\\w+")) {
                out.add("SET NAMES " + charset + (collation.matches("\\w+") && collation.toLowerCase(java.util.Locale.ROOT)
                        .startsWith(charset.toLowerCase(java.util.Locale.ROOT) + "_") ? " COLLATE " + collation : ""));
            }
        }
        return out;
    }

    private static boolean isOn(String v) {
        return v.equals("1") || v.equalsIgnoreCase("ON");
    }

    /** Nel registro: l'istruzione, abbreviata se lunghissima (con la riga del file per ritrovarla). */
    private void recordFileStatement(String origin, String fileName, ScriptReader.Statement st, String text,
            boolean rewritten, StatementResult r) {
        SqlLog.Outcome outcome = switch (r.status()) {
            case OK -> SqlLog.Outcome.OK;
            case FAILED -> SqlLog.Outcome.ERROR;
            case INTERRUPTED -> SqlLog.Outcome.INTERRUPTED;
        };
        StatementResult.ServerError e = r.error();
        boolean longText = text.length() > LOG_TEXT_LIMIT;
        String shown = longText ? abbreviate(text, LOG_TEXT_LIMIT) : text;
        String note = longText ? CoreMessages.get("log.file.abbreviated", text.length(), st.line(), fileName)
                : CoreMessages.get("log.file.line", st.line(), fileName);
        if (rewritten) {
            note += " · " + CoreMessages.get("log.file.rewritten");
        }
        log.add(connectionLabel, origin, shown, outcome, e == null ? 0 : e.code(), e == null ? "" : e.sqlState(),
                e == null ? "" : e.message(), r.durationMillis(), r.affectedRows(), note, false, fileName);
    }

    static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + " …";
    }

    /** Connessione caduta o server sparito: inutile ritentare le righe. */
    private static boolean isConnectionError(SQLException e) {
        String state = e.getSQLState();
        return (state != null && state.startsWith("08")) || e instanceof java.sql.SQLNonTransientConnectionException;
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
