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
