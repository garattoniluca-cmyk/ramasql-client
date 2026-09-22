/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.ConnectionAttempt;
import it.ramasql.core.connection.ConnectionAttempt.Outcome;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ConnectionFailedException;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.connection.Session;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/** T2.1 e dintorni — {@link Session} contro MariaDB e MySQL veri. Se un server non risponde il test fallisce. */
@Tag("step2")
@Tag("it")
class SessionServerTest {

    /** T2.1 — tipo e versione del server, confrontati con ciò che il test legge per conto suo. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t21_laSessioneRiconosceTipoEVersioneDelServer(ItServers server) throws Exception {
        String version;
        String comment;
        try (Connection c = server.connect(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT VERSION(), @@version_comment")) {
            rs.next();
            version = rs.getString(1);
            comment = rs.getString(2);
        }

        char[] password = Step2Servers.password(server);
        try (Session session = Session.open(Step2Servers.profile(server, ""), password)) {
            ServerInfo info = session.serverInfo();

            assertEquals(version, info.versionText());
            assertEquals(server.isMariaDb(), info.isMariaDb());
            assertEquals(server.isMariaDb() ? ServerInfo.ServerKind.MARIADB : ServerInfo.ServerKind.MYSQL, info.kind());
            assertTrue(comment.toLowerCase(Locale.ROOT).contains(server.isMariaDb() ? "mariadb" : "mysql"),
                    "@@version_comment = " + comment);
            String[] numbers = version.split("[.-]");
            assertEquals(Integer.parseInt(numbers[0]), info.major());
            assertEquals(Integer.parseInt(numbers[1]), info.minor());
            assertEquals(Integer.parseInt(numbers[2]), info.patch());
            assertEquals((server.isMariaDb() ? "MariaDB " : "MySQL ") + numbers[0] + "." + numbers[1] + "." + numbers[2],
                    info.displayName());

            // sempre autocommit, su tutte e due le connessioni, anche visto dal server
            assertTrue(session.mainConnection().getAutoCommit());
            assertTrue(session.serviceConnection().getAutoCommit());
            assertEquals("1", single(session.mainConnection(), "SELECT @@autocommit"));
            assertEquals("1", single(session.serviceConnection(), "SELECT @@autocommit"));

            // la connessione di servizio è un'altra connessione
            assertNotSame(session.mainConnection(), session.serviceConnection());
            String mainId = single(session.mainConnection(), "SELECT CONNECTION_ID()");
            String serviceId = single(session.serviceConnection(), "SELECT CONNECTION_ID()");
            assertNotEquals(mainId, serviceId);
            assertNull(session.currentCatalog(), "profilo senza catalogo: nessun catalogo scelto");
            assertNull(session.openedCatalog(), "e così lo ha letto l'apertura");

            TestResults.write("step2", "T2.1-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                    "server di test = " + server.label() + "\n"
                            + "SELECT VERSION() letto dal test = " + version + "\n"
                            + "@@version_comment letto dal test = " + comment + "\n"
                            + "Session.serverInfo() = " + info + "\n"
                            + "barra di stato = " + info.displayName() + "\n"
                            + "autocommit (principale, servizio) = " + session.mainConnection().getAutoCommit() + ", "
                            + session.serviceConnection().getAutoCommit() + "\n"
                            + "CONNECTION_ID principale = " + mainId + ", di servizio = " + serviceId + "\n"
                            + "Session.toString() = " + session + "\n");
        }
    }

    /**
     * T2.1 (aggiuntivo, solo MySQL: l'utente esiste solo lì) — con il driver unico (ADR-015) ci si connette anche a un
     * utente {@code caching_sha2_password}, senza TLS.
     */
    @org.junit.jupiter.api.Test
    void t21_mysqlConUtenteCachingSha2PasswordSenzaTls() throws Exception {
        ItServers server = ItServers.MYSQL;
        ConnectionProfile base = Step2Servers.profile(server, "");
        ConnectionProfile sha2 = ConnectionProfile.create("Test sha2", base.host(), base.port(), "ramasql_test_sha2", "", "");
        try (Session session = Session.open(sha2, Step2Servers.password(server))) {
            assertEquals(ServerInfo.ServerKind.MYSQL, session.serverInfo().kind());
            String createUser = single(session.mainConnection(), "SHOW CREATE USER CURRENT_USER()");
            assertTrue(createUser.contains("caching_sha2_password"), "plugin dell'utente: " + createUser.split(" AS ")[0]);
            String cipher;
            try (Statement st = session.mainConnection().createStatement();
                    ResultSet rs = st.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                cipher = rs.next() ? rs.getString(2) : "";
            }
            assertEquals("", cipher, "connessione senza TLS");
            assertTrue(session.mainConnection().getAutoCommit());
            TestResults.write("step2", "T2.1-mysql-caching-sha2.txt",
                    "utente = ramasql_test_sha2 (plugin caching_sha2_password, verificato con SHOW CREATE USER)\n"
                            + "Session.serverInfo() = " + session.serverInfo() + "\n"
                            + "Ssl_cipher = «" + cipher + "» (vuoto: nessun TLS)\n"
                            + "autocommit = " + session.mainConnection().getAutoCommit() + "\n"
                            + "parametro decisivo: allowPublicKeyRetrieval=true\n");
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void ilCatalogoPredefinitoDelProfiloDiventaIlCatalogoCorrente(ItServers server) throws Exception {
        try (TestCatalog catalog = TestCatalog.create(server, "step2_sessione");
                Session session = Session.open(Step2Servers.profile(server, catalog.name()), Step2Servers.password(server))) {
            assertEquals(catalog.name(), session.currentCatalog());
            assertEquals(catalog.name(), session.openedCatalog(), "letto dentro Session.open");
            assertNull(single(session.serviceConnection(), "SELECT DATABASE()"),
                    "la connessione di servizio non sceglie un catalogo");
            session.close();
            assertEquals(catalog.name(), session.openedCatalog(), "a sessione chiusa si legge ancora: nessuna chiamata di rete");
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void passwordErrata_1045(ItServers server) {
        ConnectionFailure f = failure(Step2Servers.profile(server, ""), "password-sbagliata-di-prova".toCharArray());
        assertEquals(ConnectionErrorCause.ACCESS_DENIED, f.cause());
        assertEquals(1045, f.errorCode());
        assertTrue(f.originalDetail().contains("1045") && f.originalDetail().contains("Access denied"), f.originalDetail());
        assertFalse(f.toString().contains("password-sbagliata-di-prova"), "la password non compare mai nella diagnosi");
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void catalogoSenzaPermesso_1044(ItServers server) {
        ConnectionFailure f = failure(Step2Servers.profile(server, "mysql"), Step2Servers.password(server));
        assertEquals(ConnectionErrorCause.CATALOG_ACCESS_DENIED, f.cause());
        assertEquals(1044, f.errorCode());
        assertTrue(f.message().contains("«mysql»"), f.message());
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void catalogoInesistente_1049(ItServers server) {
        ConnectionFailure f = failure(Step2Servers.profile(server, "ramasql_test_non_esiste"), Step2Servers.password(server));
        assertEquals(ConnectionErrorCause.UNKNOWN_CATALOG, f.cause());
        assertEquals(1049, f.errorCode());
        assertTrue(f.message().contains("«ramasql_test_non_esiste»"), f.message());
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void laProvaDiConnessioneAsincronaRiconosceIlServerERichiudeTutto(ItServers server) throws Exception {
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        ConnectionAttempt attempt = ConnectionAttempt.test(Step2Servers.profile(server, ""), Step2Servers.password(server),
                result::complete);

        Outcome outcome = result.get(15, TimeUnit.SECONDS);

        Outcome.Tested tested = assertInstanceOf(Outcome.Tested.class, outcome);
        assertEquals(server.isMariaDb(), tested.serverInfo().isMariaDb());
        assertTrue(attempt.isDone());
        attempt.cancel(); // annullare dopo l'esito non fa nulla
        assertEquals(outcome, result.get());
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void lApertureAsincronaConsegnaUnaSessioneEChiuderlaChiudeLeDueConnessioni(ItServers server) throws Exception {
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        ConnectionAttempt.open(Step2Servers.profile(server, ""), Step2Servers.password(server), result::complete);

        Outcome.Connected connected = assertInstanceOf(Outcome.Connected.class, result.get(15, TimeUnit.SECONDS));
        Session session = connected.session();
        String text = session.toString();
        session.close();

        assertTrue(session.isClosed());
        assertTrue(session.mainConnection().isClosed());
        assertTrue(session.serviceConnection().isClosed());
        assertTrue(text.contains(server.user()) && text.contains(server.isMariaDb() ? "MariaDB" : "MySQL"), text);
        assertFalse(text.contains(new String(Step2Servers.password(server))), "mai la password in toString()");
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void laConnessioneDiServizioInterrompeUnIstruzioneInCorso(ItServers server) throws Exception {
        try (Session session = Session.open(Step2Servers.profile(server, ""), Step2Servers.password(server))) {
            CompletableFuture<Long> running = CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                try (Statement st = session.mainConnection().createStatement()) {
                    st.execute("SELECT SLEEP(20)");
                } catch (SQLException interrupted) {
                    // MariaDB segnala l'interruzione con un errore, MySQL fa tornare SLEEP con 1: va bene in entrambi i casi
                }
                return (System.nanoTime() - start) / 1_000_000;
            });
            Thread.sleep(700);

            session.interruptRunningStatement();

            long millis = running.get(10, TimeUnit.SECONDS);
            assertTrue(millis < 5000, "SLEEP(20) interrotto dopo " + millis + " ms");
            assertEquals("1", single(session.mainConnection(), "SELECT 1"), "la connessione principale resta utilizzabile");
        }
    }

    private static ConnectionFailure failure(ConnectionProfile profile, char[] password) {
        ConnectionFailedException e = assertThrows(ConnectionFailedException.class, () -> Session.open(profile, password));
        return e.failure();
    }

    private static String single(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
