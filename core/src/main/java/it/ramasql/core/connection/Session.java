/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import it.ramasql.core.CoreMessages;

/**
 * Sessione di lavoro su un server: la <strong>connessione principale</strong>, su cui gira l'SQL dell'utente, e
 * una <strong>connessione di servizio</strong> distinta, per {@code KILL QUERY} e per la lettura dei metadati.
 * <p><strong>Sempre in autocommit, nessuna gestione delle transazioni</strong> (regola 5 di {@code CLAUDE.md}):
 * qui non si disattiva mai l'autocommit e non si emettono mai istruzioni di transazione.
 * <p><strong>Password:</strong> arriva come {@code char[]} e questa classe non la conserva in nessun campo proprio:
 * non finisce su disco, nei log, né in {@link #toString()}. Il driver JDBC però la vuole come {@code String} nelle
 * sue proprietà e <strong>la tiene nella propria configurazione per tutta la vita della connessione</strong>: resta
 * quindi in memoria, dentro il driver, finché le due connessioni non vengono chiuse e raccolte. Solo in memoria:
 * non viene mai scritta altrove.
 */
public final class Session implements AutoCloseable {

    /** Tempo massimo, in millisecondi, per aprire UNA connessione verso il server. */
    static final int CONNECT_TIMEOUT_MS = 8000;

    /** Sotto questo residuo non si tenta nemmeno il passo successivo: sarebbe comunque fuori tempo. */
    static final int MIN_REMAINING_MS = 50;

    private static final String DRIVER_CLASS = "org.mariadb.jdbc.Driver";

    private final ConnectionProfile profile;
    private final Connection main;
    private final Connection service;
    private final ServerInfo serverInfo;
    private final long mainConnectionId;
    private final String openedCatalog;
    private volatile boolean closed;

    private Session(ConnectionProfile profile, Connection main, Connection service, ServerInfo serverInfo,
            long mainConnectionId, String openedCatalog) {
        this.profile = profile;
        this.main = main;
        this.service = service;
        this.serverInfo = serverInfo;
        this.mainConnectionId = mainConnectionId;
        this.openedCatalog = openedCatalog;
    }

    /**
     * Sessione senza connessioni, per i test senza server (di {@link ConnectionAttempt}, del controller): si chiude
     * come una vera e non parla con nessuno.
     */
    static Session detached(ConnectionProfile profile, ServerInfo serverInfo, String catalog) {
        return new Session(profile, null, null, serverInfo, 0, catalog);
    }

    /**
     * Apre la sessione con il tempo massimo complessivo di {@link ConnectionAttempt#DEADLINE_SECONDS} secondi.
     *
     * @see #open(ConnectionProfile, char[], long)
     */
    public static Session open(ConnectionProfile profile, char[] password) throws ConnectionFailedException {
        return open(profile, password, System.nanoTime() + TimeUnit.SECONDS.toNanos(ConnectionAttempt.DEADLINE_SECONDS));
    }

    /**
     * Apre la sessione (chiamata bloccante: dalla UI si usa {@link ConnectionAttempt}, che è annullabile). Le due
     * connessioni si aprono in fila <strong>entro la stessa scadenza</strong>: la seconda ha solo il tempo che resta.
     * Anche le letture di servizio (versione, identificativo, catalogo corrente) si fanno qui, entro la scadenza:
     * dopo, chi riceve la sessione non ha bisogno di parlare con il server per mostrarla.
     *
     * @param password      password digitata dall'utente; l'array resta del chiamante, che lo azzera quando vuole
     * @param deadlineNanos scadenza complessiva, nel riferimento di {@link System#nanoTime()}
     * @throws ConnectionFailedException con la diagnosi in italiano e il testo originale del server
     */
    public static Session open(ConnectionProfile profile, char[] password, long deadlineNanos)
            throws ConnectionFailedException {
        Connection main = null;
        Connection service = null;
        try {
            main = connect(profile, password, profile.defaultCatalog(), deadlineNanos);
            if (!main.getAutoCommit()) {
                main.setAutoCommit(true);
            }
            // la connessione di servizio non sceglie un catalogo: legge i metadati di tutti
            service = connect(profile, password, "", deadlineNanos);
            main.setNetworkTimeout(Runnable::run, remainingMillis(deadlineNanos, System.nanoTime()));
            ServerInfo info = ServerInfo.parse(InternalQueries.version(main));
            long id = InternalQueries.connectionId(main);
            String catalog = InternalQueries.currentCatalog(main);
            main.setNetworkTimeout(Runnable::run, 0); // le istruzioni dell'utente non hanno un limite di rete
            return new Session(profile, main, service, info, id, catalog);
        } catch (SQLException | RuntimeException e) {
            closeQuietly(service);
            closeQuietly(main);
            throw new ConnectionFailedException(ConnectionErrorClassifier.classify(e, profile), e);
        } catch (DeadlineExpired e) {
            closeQuietly(service);
            closeQuietly(main);
            throw new ConnectionFailedException(new ConnectionFailure(ConnectionErrorCause.TIMEOUT,
                    ConnectionErrorClassifier.message(ConnectionErrorCause.TIMEOUT, profile), 0, "",
                    CoreMessages.get("connection.error.deadline.original")), e);
        }
    }

    /**
     * Tempo concesso al prossimo passo: il residuo fino alla scadenza, al massimo {@link #CONNECT_TIMEOUT_MS}.
     *
     * @throws DeadlineExpired se il residuo è troppo piccolo per tentare
     */
    static int remainingMillis(long deadlineNanos, long nowNanos) throws DeadlineExpired {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - nowNanos);
        if (remaining < MIN_REMAINING_MS) {
            throw new DeadlineExpired();
        }
        return (int) Math.min(remaining, CONNECT_TIMEOUT_MS);
    }

    /** La scadenza complessiva è passata prima di poter tentare il passo successivo. */
    static final class DeadlineExpired extends Exception {
        private static final long serialVersionUID = 1L;

        DeadlineExpired() {
            super("scadenza complessiva raggiunta", null, false, false);
        }
    }

    public ConnectionProfile profile() {
        return profile;
    }

    public ServerInfo serverInfo() {
        return serverInfo;
    }

    /** Connessione principale: la usa solo la pipeline SQL ({@code SqlExecutor}). */
    public Connection mainConnection() {
        return main;
    }

    /** Connessione di servizio: metadati e {@code KILL QUERY}. */
    public Connection serviceConnection() {
        return service;
    }

    /**
     * Catalogo corrente letto all'apertura, entro la scadenza ({@code null} = nessuno). Non parla con il server:
     * è ciò che la barra di stato mostra appena connessi.
     */
    public String openedCatalog() {
        return openedCatalog;
    }

    /** Catalogo corrente della connessione principale, chiesto ora al server; {@code null} se non ne è scelto nessuno. */
    public String currentCatalog() throws SQLException {
        return InternalQueries.currentCatalog(main);
    }

    /** Interrompe l'istruzione in corso sulla connessione principale, passando dalla connessione di servizio. */
    public void interruptRunningStatement() throws SQLException {
        InternalQueries.killQuery(service, mainConnectionId);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        closeQuietly(service);
        closeQuietly(main);
    }

    /** Mai dati riservati: solo nome del profilo, indirizzo e server. */
    @Override
    public String toString() {
        return "Session[" + profile.name() + ", " + profile.address() + ", " + serverInfo.displayName()
                + (closed ? ", chiusa" : "") + "]";
    }

    /** Indirizzo JDBC del profilo; mai utente o password nell'indirizzo. */
    static String jdbcUrl(ConnectionProfile profile, String catalog) {
        String host = profile.host().contains(":") && !profile.host().startsWith("[")
                ? "[" + profile.host() + "]" : profile.host();
        return "jdbc:mariadb://" + host + ":" + profile.port() + "/" + catalog;
    }

    /**
     * Parametri del driver (MariaDB Connector/J, unico per i due server). Il charset non si imposta: il
     * Connector/J 3.x parla sempre {@code utf8mb4}.
     */
    static Properties driverProperties(ConnectionProfile profile, char[] password) {
        return driverProperties(profile, password, CONNECT_TIMEOUT_MS);
    }

    static Properties driverProperties(ConnectionProfile profile, char[] password, int connectTimeoutMillis) {
        Properties p = new Properties();
        p.setProperty("user", profile.user());
        p.setProperty("password", new String(password));
        p.setProperty("connectTimeout", String.valueOf(connectTimeoutMillis));
        // nessuna gestione delle transazioni: ogni istruzione è subito definitiva
        p.setProperty("autocommit", "true");
        // MySQL 8 (caching_sha2_password) senza TLS: il driver deve poter chiedere la chiave pubblica al server
        p.setProperty("allowPublicKeyRetrieval", "true");
        // TINYINT(1) è un numero (0, 1, ma anche 2…): nel data-entry si deve vedere com'è, non true/false
        p.setProperty("tinyInt1isBit", "false");
        // riconoscibile in SHOW PROCESSLIST / performance_schema
        p.setProperty("connectionAttributes", "program_name:RamaSQL Client");
        return p;
    }

    private static Connection connect(ConnectionProfile profile, char[] password, String catalog, long deadlineNanos)
            throws SQLException, DeadlineExpired {
        String url = jdbcUrl(profile, catalog);
        int timeout = remainingMillis(deadlineNanos, System.nanoTime());
        Connection c = driver().connect(url, driverProperties(profile, password, timeout));
        if (c == null) {
            throw new SQLException(CoreMessages.get("connection.internal.urlRejected", url));
        }
        return c;
    }

    /** Il driver si istanzia direttamente: niente dipendenza dal caricatore di classi di {@code DriverManager}. */
    private static Driver driver() throws SQLException {
        try {
            return (Driver) Class.forName(DRIVER_CLASS).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new SQLException(CoreMessages.get("connection.internal.driverMissing", DRIVER_CLASS), e);
        }
    }

    private static void closeQuietly(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // si sta chiudendo: non c'è altro da fare
            }
        }
    }
}
