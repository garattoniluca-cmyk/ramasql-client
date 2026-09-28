/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.model;

import java.util.ResourceBundle;

/** Testi del modulo {@code model} rivolti all'utente, in {@code it/ramasql/model/messages.properties}. */
public final class ModelMessages {

    private static final ResourceBundle TEXTS = ResourceBundle.getBundle("it.ramasql.model.messages");

    private ModelMessages() {
    }

    public static String get(String key, Object... args) {
        String pattern = TEXTS.getString(key);
        return args.length == 0 ? pattern : String.format(pattern, args);
    }
}
