/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app;

import java.util.ResourceBundle;

/**
 * Testi dell'interfaccia: stanno tutti in {@code messages.properties} (italiano), predisposti per la traduzione.
 * Nessuna stringa visibile all'utente va scritta nel codice. I segnaposto sono quelli di {@link String#format}.
 */
public final class Texts {

    private static final ResourceBundle TEXTS = ResourceBundle.getBundle("it.ramasql.app.messages");

    private Texts() {
    }

    /** C'è un testo con questa chiave. */
    public static boolean has(String key) {
        return TEXTS.containsKey(key);
    }

    public static String get(String key, Object... args) {
        String pattern = TEXTS.getString(key);
        return args.length == 0 ? pattern : String.format(pattern, args);
    }
}
