/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Catalogo usa-e-getta per un test d'integrazione: nasce con un nome univoco
 * {@code ramasql_test_<etichetta>_<suffisso casuale>} e viene distrutto da {@link #close()},
 * anche se il test fallisce (usarlo in un {@code try-with-resources}).
 *
 * <p>Rifiuta qualunque nome che non inizi con {@code ramasql_test_} (regola 9 di CLAUDE.md).
 */
public final class TestCatalog implements AutoCloseable {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** MySQL e MariaDB accettano al massimo 64 caratteri per il nome di un catalogo. */
    private static final int MAX_NAME_LENGTH = 64;

    private final ItServers server;
    private final String name;
    private final Connection connection;
    private final List<Connection> extraConnections = new ArrayList<>();

    private TestCatalog(ItServers server, String name, Connection connection) {
        this.server = server;
        this.name = name;
        this.connection = connection;
    }

    /**
     * Crea il catalogo (utf8mb4) sul server e apre una connessione già posizionata su di esso.
     *
     * @param label etichetta del test: lettere, cifre e «_» (il resto diventa «_»)
     */
    public static TestCatalog create(ItServers server, String label) {
        String clean = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        String suffix = Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString(RANDOM.nextInt(36 * 36 * 36 * 36), 36);
        String prefix = ItConfig.TEST_CATALOG_PREFIX + clean;
        int room = MAX_NAME_LENGTH - suffix.length() - 1;
        if (prefix.length() > room) {
            prefix = prefix.substring(0, room);
        }
        String name = requireTestName(prefix + "_" + suffix);
        Connection con = server.connect();
        try {
            try (Statement st = con.createStatement()) {
                st.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            con.setCatalog(name);
            return new TestCatalog(server, name, con);
        } catch (SQLException e) {
            try {
                con.close();
            } catch (SQLException ignored) {
                // si riporta l'errore originale
            }
            throw new AssertionError("Impossibile creare il catalogo di test " + name + " su " + server.label()
                    + ": " + e.getMessage(), e);
        }
    }

    /** Controllo di sicurezza: solo nomi {@code ramasql_test_*}, fatti di lettere minuscole, cifre e «_». */
    public static String requireTestName(String name) {
        if (name == null || !name.startsWith(ItConfig.TEST_CATALOG_PREFIX)
                || name.length() <= ItConfig.TEST_CATALOG_PREFIX.length()
                || name.length() > MAX_NAME_LENGTH
                || !name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Nome di catalogo non ammesso nei test: «" + name
                    + "» (deve iniziare con " + ItConfig.TEST_CATALOG_PREFIX + ")");
        }
        return name;
    }

    /** Server su cui vive il catalogo. */
    public ItServers server() {
        return server;
    }

    /** Nome del catalogo (inizia sempre con {@code ramasql_test_}). */
    public String name() {
        return name;
    }

    /** Connessione principale, già posizionata sul catalogo; la chiude {@link #close()}. */
    public Connection connection() {
        return connection;
    }

    /** Apre un'altra connessione sul catalogo (es. per KILL QUERY); la chiude {@link #close()}. */
    public Connection newConnection() {
        return newConnection(new Properties());
    }

    /** Come {@link #newConnection()}, con proprietà aggiuntive del driver. */
    public Connection newConnection(Properties extra) {
        Connection c = server.connect(name, extra);
        synchronized (extraConnections) {
            extraConnections.add(c);
        }
        return c;
    }

    /** Esegue una o più istruzioni SQL sulla connessione principale. */
    public void execute(String... statements) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    /**
     * Esegue uno script SQL preso dalle risorse di test (UTF-8). Le istruzioni sono separate da «;» a
     * fine riga; le righe che iniziano con «--» sono commenti. Niente DELIMITER: per le fixture basta.
     *
     * @param resourcePath percorso assoluto nel classpath, es. {@code /it/ramasql/it/step1/s4-fixture.sql}
     */
    public void runScript(String resourcePath) throws SQLException {
        String text;
        try (InputStream in = TestCatalog.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new AssertionError("Script SQL non trovato nelle risorse di test: " + resourcePath);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Script SQL illeggibile: " + resourcePath, e);
        }
        for (String sql : splitStatements(text)) {
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            } catch (SQLException e) {
                throw new SQLException("Errore nello script " + resourcePath + " sull'istruzione «"
                        + sql.lines().findFirst().orElse("") + "…»: " + e.getMessage(), e.getSQLState(),
                        e.getErrorCode(), e);
            }
        }
    }

    /** Divide lo script in istruzioni: «;» a fine riga chiude l'istruzione, «--» a inizio riga è un commento. */
    static List<String> splitStatements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.replace("\r\n", "\n").split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.endsWith(";")) {
                current.append(line, 0, line.lastIndexOf(';'));
                String sql = current.toString().strip();
                if (!sql.isEmpty()) {
                    result.add(sql);
                }
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        String tail = current.toString().strip();
        if (!tail.isEmpty()) {
            result.add(tail);
        }
        return result;
    }

    /** Distrugge il catalogo e chiude tutte le connessioni; se la principale è rotta ne apre una nuova. */
    @Override
    public void close() {
        synchronized (extraConnections) {
            for (Connection c : extraConnections) {
                closeQuietly(c);
            }
            extraConnections.clear();
        }
        String drop = "DROP DATABASE IF EXISTS `" + requireTestName(name) + "`";
        try (Statement st = connection.createStatement()) {
            st.execute(drop);
        } catch (SQLException first) {
            try (Connection fresh = server.connect(); Statement st = fresh.createStatement()) {
                st.execute(drop);
            } catch (SQLException second) {
                second.addSuppressed(first);
                throw new AssertionError("Catalogo di test non distrutto: " + name + " su " + server.label()
                        + " (" + second.getMessage() + ")", second);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
            // la connessione può essere già chiusa o interrotta dal test
        }
    }
}
