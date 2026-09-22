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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T2.2 — classificazione degli errori di connessione da eccezioni simulate (nessun server). */
@Tag("step2")
class ConnectionErrorClassifierTest {

    private static final ConnectionProfile PROFILE =
            ConnectionProfile.create("Aula 3A", "db.scuola.example", 3307, "studente", "biblioteca", "");

    /** Il driver avvolge gli errori di rete in una SQLException senza codice, con la causa vera in fondo. */
    private static SQLException network(Throwable cause) {
        return new SQLNonTransientConnectionException(
                "Socket fail to connect to host:address=(host=x)(port=1)(type=primary). " + cause.getMessage(),
                "08000", cause);
    }

    static Stream<Arguments> simulatedErrors() {
        return Stream.of(
                Arguments.of("host sconosciuto", network(new UnknownHostException("db.scuola.example")),
                        ConnectionErrorCause.UNKNOWN_HOST, 0),
                Arguments.of("porta chiusa", network(new ConnectException("Connection refused: connect")),
                        ConnectionErrorCause.PORT_CLOSED, 0),
                Arguments.of("tempo scaduto (socket)", network(new SocketTimeoutException("Connect timed out")),
                        ConnectionErrorCause.TIMEOUT, 0),
                Arguments.of("tempo scaduto (sistema operativo)",
                        network(new ConnectException("Connection timed out: connect")),
                        ConnectionErrorCause.TIMEOUT, 0),
                Arguments.of("rete non raggiungibile", network(new NoRouteToHostException("No route to host")),
                        ConnectionErrorCause.TIMEOUT, 0),
                Arguments.of("accesso negato",
                        new SQLException("Access denied for user 'studente'@'localhost' (using password: YES)", "28000", 1045),
                        ConnectionErrorCause.ACCESS_DENIED, 1045),
                Arguments.of("accesso al catalogo negato",
                        new SQLException("Access denied for user 'studente'@'%' to database 'mysql'", "42000", 1044),
                        ConnectionErrorCause.CATALOG_ACCESS_DENIED, 1044),
                Arguments.of("catalogo inesistente",
                        new SQLException("Unknown database 'bibliotca'", "42000", 1049),
                        ConnectionErrorCause.UNKNOWN_CATALOG, 1049),
                Arguments.of("troppe connessioni", new SQLException("Too many connections", "08004", 1040),
                        ConnectionErrorCause.TOO_MANY_CONNECTIONS, 1040),
                Arguments.of("computer non autorizzato",
                        new SQLException("Host '10.0.0.7' is not allowed to connect to this MariaDB server", "HY000", 1130),
                        ConnectionErrorCause.HOST_NOT_ALLOWED, 1130),
                Arguments.of("SSL richiesto dal server",
                        new SQLException("Connections using insecure transport are prohibited while --require_secure_transport=ON.",
                                "HY000", 3159),
                        ConnectionErrorCause.SSL, 3159),
                Arguments.of("stretta di mano SSL fallita", network(new SSLHandshakeException("No appropriate protocol")),
                        ConnectionErrorCause.SSL, 0),
                Arguments.of("plugin di autenticazione (codice)",
                        new SQLException("Plugin 'auth_xyz' is not loaded", "HY000", 1524),
                        ConnectionErrorCause.AUTH_PLUGIN, 1524),
                Arguments.of("plugin di autenticazione (testo del driver)",
                        new SQLNonTransientConnectionException(
                                "RSA public key is not available client side (option serverRsaPublicKeyFile not set)", "S1009"),
                        ConnectionErrorCause.AUTH_PLUGIN, 0),
                Arguments.of("errore avvolto in un'altra eccezione",
                        new RuntimeException("wrapper", new SQLException("Unknown database 'x'", "42000", 1049)),
                        ConnectionErrorCause.UNKNOWN_CATALOG, 1049),
                Arguments.of("altro", new SQLException("Qualcosa di imprevisto", "HY000", 1105),
                        ConnectionErrorCause.OTHER, 1105));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("simulatedErrors")
    void ogniErroreSimulatoHaLaSuaCausa(String label, Throwable error, ConnectionErrorCause expected, int expectedCode) {
        ConnectionFailure failure = ConnectionErrorClassifier.classify(error, PROFILE);

        assertEquals(expected, failure.cause(), label);
        assertEquals(expectedCode, failure.errorCode(), "codice originale del server");
        assertFalse(failure.message().isBlank(), "serve il messaggio in italiano");
        assertFalse(failure.originalText().isBlank(), "il testo originale non si perde mai");
        if (expectedCode != 0) {
            assertTrue(failure.originalDetail().contains(String.valueOf(expectedCode)),
                    "il dettaglio mostra il codice: " + failure.originalDetail());
        }
    }

    @Test
    void leCauseRiconosciuteSonoAlmenoCinqueEOgnunaHaUnMessaggioDiverso() {
        Map<ConnectionErrorCause, String> messages = new EnumMap<>(ConnectionErrorCause.class);
        simulatedErrors().forEach(a -> {
            ConnectionFailure f = ConnectionErrorClassifier.classify((Throwable) a.get()[1], PROFILE);
            messages.put(f.cause(), f.message());
        });
        messages.remove(ConnectionErrorCause.OTHER);

        assertTrue(messages.size() >= 5, "cause distinte riconosciute: " + messages.keySet());
        Set<String> distinct = new HashSet<>(messages.values());
        assertEquals(messages.size(), distinct.size(), "ogni causa ha un messaggio suo");
    }

    @Test
    void ogniCausaHaIlSuoTestoNelleRisorse() {
        for (ConnectionErrorCause cause : ConnectionErrorCause.values()) {
            String message = ConnectionErrorClassifier.message(cause, PROFILE);
            assertFalse(message.isBlank(), cause.name());
            assertFalse(message.contains("%s"), "segnaposto non sostituito in " + cause + ": " + message);
        }
    }

    @Test
    void ilMessaggioNominaCioCheVaCorretto() {
        assertTrue(ConnectionErrorClassifier.message(ConnectionErrorCause.UNKNOWN_HOST, PROFILE).contains("db.scuola.example"));
        assertTrue(ConnectionErrorClassifier.message(ConnectionErrorCause.PORT_CLOSED, PROFILE).contains("3307"));
        assertTrue(ConnectionErrorClassifier.message(ConnectionErrorCause.ACCESS_DENIED, PROFILE).contains("studente"));
        assertTrue(ConnectionErrorClassifier.message(ConnectionErrorCause.UNKNOWN_CATALOG, PROFILE).contains("biblioteca"));
        assertTrue(ConnectionErrorClassifier.message(ConnectionErrorCause.CATALOG_ACCESS_DENIED, PROFILE).contains("biblioteca"));
    }

    @Test
    void ilTempoScadutoDelClientEUnTimeout() {
        ConnectionFailure failure = ConnectionErrorClassifier.timeout(PROFILE);
        assertEquals(ConnectionErrorCause.TIMEOUT, failure.cause());
        assertTrue(failure.originalDetail().contains(String.valueOf(ConnectionAttempt.DEADLINE_SECONDS)));
    }

    @Test
    void ilMessaggioDiTempoScadutoNonDichiaraUnNumeroDiSecondiCheNonESempreVero() {
        // lo stesso messaggio vale per la scadenza del client (10 s), del driver (8 s) e del sistema operativo (~21 s)
        ConnectionFailure byClient = ConnectionErrorClassifier.timeout(PROFILE);
        ConnectionFailure byDriver = ConnectionErrorClassifier.classify(network(new SocketTimeoutException("Connect timed out")), PROFILE);
        ConnectionFailure byOs = ConnectionErrorClassifier.classify(network(new ConnectException("Connection timed out: connect")), PROFILE);
        for (ConnectionFailure f : List.of(byClient, byDriver, byOs)) {
            assertEquals(ConnectionErrorCause.TIMEOUT, f.cause());
            assertFalse(f.message().matches("(?s).*\\d+\\s*second.*"), "nessun numero di secondi: " + f.message());
            assertTrue(f.message().contains("db.scuola.example") && f.message().contains("3307"), f.message());
        }
    }

    @Test
    void portaChiusa_ilSuggerimentoValeAncheSeIlServerNonESulla3306() {
        String m = ConnectionErrorClassifier.message(ConnectionErrorCause.PORT_CLOSED,
                ConnectionProfile.create("MySQL", "127.0.0.1", 3307, "ramasql_test", "", ""));
        assertTrue(m.contains("porta 3307"), m);
        assertTrue(m.contains("controlla quella indicata da chi gestisce il server"), m);
    }

    /** Il nome dell'host dentro il testo del driver non deve far pensare a SSL/TLS («classlab» contiene «ssl»). */
    static Stream<Arguments> hostNamesContainingSslOrTls() {
        return Stream.of(Arguments.of("classlab"), Arguments.of("atlas"), Arguments.of("tlsserver"), Arguments.of("ssl-aula"));
    }

    @ParameterizedTest(name = "host {0}")
    @MethodSource("hostNamesContainingSslOrTls")
    void unHostConSslOTlsNelNomeNonEUnErroreSsl(String host) {
        ConnectionProfile profile = ConnectionProfile.create("Aula", host, 3306, "studente", "", "");
        SQLException error = new SQLNonTransientConnectionException(
                "Could not connect to address=(host=" + host + ")(port=3306)(type=primary) : unexpected end of stream, read 0 bytes from 4 (socket was closed by server)",
                "08000");

        ConnectionFailure failure = ConnectionErrorClassifier.classify(error, profile);

        assertEquals(ConnectionErrorCause.OTHER, failure.cause(), failure.originalText());
        assertTrue(failure.originalText().contains(host), "il testo originale resta intero: " + failure.originalText());
    }

    @Test
    void sslETlsComeParoleInteriSiRiconosconoAncoraAncheConLHostClasslab() {
        ConnectionProfile profile = ConnectionProfile.create("Aula", "classlab", 3306, "studente", "", "");
        SQLException tls = new SQLNonTransientConnectionException(
                "Could not connect to address=(host=classlab)(port=3306)(type=primary) : TLS protocol version not supported", "08000");
        SQLException ssl = new SQLNonTransientConnectionException("Trying to connect with ssl, but ssl not enabled in the server", "08000");
        assertEquals(ConnectionErrorCause.SSL, ConnectionErrorClassifier.classify(tls, profile).cause());
        assertEquals(ConnectionErrorCause.SSL, ConnectionErrorClassifier.classify(ssl, profile).cause());
    }

    @Test
    void ilCodice1043NonESempreSsl() {
        ConnectionProfile profile = ConnectionProfile.create("Aula", "classlab", 3306, "studente", "", "");
        ConnectionFailure badHandshake = ConnectionErrorClassifier.classify(
                new SQLException("Bad handshake", "08S01", 1043), profile);
        ConnectionFailure sslHandshake = ConnectionErrorClassifier.classify(
                new SQLException("Bad handshake: SSL connection error: protocol version mismatch", "08S01", 1043), profile);

        assertEquals(ConnectionErrorCause.OTHER, badHandshake.cause());
        assertEquals(1043, badHandshake.errorCode(), "il codice originale si mostra");
        assertEquals(ConnectionErrorCause.SSL, sslHandshake.cause());
        assertEquals(1043, sslHandshake.errorCode(), "anche quando la causa si riconosce dal testo il codice resta");
    }
}
