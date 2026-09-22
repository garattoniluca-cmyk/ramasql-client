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

import java.nio.file.Path;

/**
 * Cartella dei dati dell'utente: {@code %APPDATA%\RamaSQL}. La proprietà di sistema {@code ramasql.appdata}
 * la sostituisce (la usano i test, che non devono mai scrivere nella cartella vera dell'utente).
 */
public final class AppData {

    /** Proprietà di sistema che sostituisce la cartella dei dati. */
    public static final String OVERRIDE_PROPERTY = "ramasql.appdata";

    private AppData() {
    }

    public static Path directory() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank() ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("RamaSQL");
    }
}
