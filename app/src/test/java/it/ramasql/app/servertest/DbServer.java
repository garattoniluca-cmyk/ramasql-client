/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.exec.StatementSplitter;

/**
 * I due server veri dei test d'interfaccia dello Step 3, con la <b>connessione di servizio del test</b> (JDBC
 * diretto, fuori dal client) per preparare i cataloghi {@code ramasql_test_*} e verificare sul server ciò che
 * l'interfaccia dichiara. Credenziali dalle variabili {@code RAMASQL_IT_*} (le carica {@code scripts\verify.ps1}):
 * se mancano il test <b>fallisce</b>. La password non si stampa e non si scrive mai.
 */
public enum DbServer {

    MARIADB("MariaDB"),
    MYSQL("MySQL");

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String label;

    DbServer(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Profilo del client verso questo server, senza catalogo predefinito. */
    public ConnectionProfile profile() {
        Matcher m = HOST_PORT.matcher(env("URL"));
        if (!m.matches()) {
            throw new AssertionError("URL del server di test non riconosciuto: " + env("URL"));
        }
        return ConnectionProfile.create(label + " locale", m.group(1), Integer.parseInt(m.group(2)), env("USER"),
                "", "");
    }

    public char[] password() {
        return env("PASSWORD").toCharArray();
    }

    /** Connessione di servizio del test (autocommit), senza catalogo. */
    public Connection connect() {
        Properties p = new Properties();
        p.setProperty("user", env("USER"));
        p.setProperty("password", env("PASSWORD"));
        p.setProperty("connectTimeout", "5000");
        try {
            return DriverManager.getConnection(env("URL"), p);
        } catch (SQLException e) {
            throw new AssertionError("Il server " + label + " non risponde: [" + e.getErrorCode() + "] "
                    + e.getMessage(), e);
        }
    }

    /** Un nome di catalogo di test nuovo: {@code ramasql_test_ui_<scopo>_<casuale>}. */
    public static String newCatalogName(String purpose) {
        StringBuilder sb = new StringBuilder("ramasql_test_ui_").append(purpose).append('_');
        for (int i = 0; i < 6; i++) {
            sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(RANDOM.nextInt(36)));
        }
        return sb.toString();
    }

    public static String requireTestName(String name) {
        if (!name.startsWith("ramasql_test_") || !name.matches("[a-z0-9_]+")) {
            throw new AssertionError("Solo cataloghi ramasql_test_*: " + name);
        }
        return name;
    }

    public void createCatalog(String name) throws SQLException {
        run("CREATE DATABASE `" + requireTestName(name) + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    /** {@code DROP DATABASE IF EXISTS} (solo cataloghi di test); non fallisce mai, per i blocchi finally. */
    public void dropQuietly(String name) {
        if (name == null) {
            return;
        }
        try {
            run("DROP DATABASE IF EXISTS `" + requireTestName(name) + "`");
        } catch (SQLException e) {
            System.err.println("Catalogo di test non eliminato: " + name + " [" + e.getErrorCode() + "]");
        }
    }

    public void run(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * Carica una fixture di {@code it-tests/fixtures/} nel catalogo (con la connessione del test, fuori dal client:
     * nel registro del client non finisce nulla).
     */
    public void loadFixture(String catalog, String fileName) throws SQLException {
        Path file = Probe.projectRoot().resolve("it-tests").resolve("fixtures").resolve(fileName);
        String script;
        try {
            script = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("USE `" + requireTestName(catalog) + "`");
            for (StatementSplitter.SplitStatement s : StatementSplitter.split(script)) {
                st.execute(s.text());
            }
        }
    }

    /** Un valore letto con la connessione del test ({@code null} se nessuna riga). */
    public String scalar(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Le righe di una query, valori come testo ({@code null} = NULL), con la connessione del test. */
    public java.util.List<java.util.List<String>> rows(String sql) throws SQLException {
        java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                java.util.List<String> row = new java.util.ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    row.add(rs.getString(i));
                }
                out.add(row);
            }
        }
        return out;
    }

    public boolean tableExists(String catalog, String table) throws SQLException {
        return "1".equals(scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + catalog
                + "' AND TABLE_NAME = '" + table + "'"));
    }

    public boolean catalogExists(String catalog) throws SQLException {
        return "1".equals(scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '"
                + catalog + "'"));
    }

    public long rowCount(String catalog, String table) throws SQLException {
        return Long.parseLong(scalar("SELECT COUNT(*) FROM `" + catalog + "`.`" + table + "`"));
    }

    private String env(String what) {
        String name = "RAMASQL_IT_" + name() + "_" + what;
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new AssertionError("Manca la variabile d'ambiente " + name
                    + ": i test contro i server veri non si saltano (usare scripts\\verify.ps1, che le carica).");
        }
        return value;
    }
}
