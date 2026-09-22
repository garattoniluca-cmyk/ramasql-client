/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.connection.ConnectionProfile;

/**
 * I due server veri usati dai test d'interfaccia: le stesse variabili d'ambiente {@code RAMASQL_IT_*} dei test
 * d'integrazione (le carica {@code scripts\verify.ps1}). Se mancano il test <strong>fallisce</strong>: non si salta.
 */
enum TestServer {

    MARIADB("MariaDB"),
    MYSQL("MySQL");

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");

    private final String label;

    TestServer(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    boolean isMariaDb() {
        return this == MARIADB;
    }

    /** Profilo verso questo server ({@code catalog} vuoto = nessun catalogo predefinito). */
    ConnectionProfile profile(String name, String catalog) {
        Matcher m = HOST_PORT.matcher(env("URL"));
        if (!m.matches()) {
            throw new AssertionError("URL del server di test non riconosciuto: " + env("URL"));
        }
        return ConnectionProfile.create(name, m.group(1), Integer.parseInt(m.group(2)), env("USER"), catalog, "");
    }

    /** Password dell'utente di test: mai stampata, mai scritta nelle evidenze. */
    char[] password() {
        return env("PASSWORD").toCharArray();
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
