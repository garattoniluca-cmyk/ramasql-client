/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import java.util.Objects;

/**
 * Valore predefinito di una colonna, con i quattro casi tenuti distinti: nessun default,
 * {@code DEFAULT NULL}, espressione (es. {@code CURRENT_TIMESTAMP}), letterale (anche stringa vuota).
 *
 * @param kind  il caso
 * @param value testo dell'espressione o del letterale (senza apici); {@code null} per NONE e NULL
 */
public record ColumnDefault(Kind kind, String value) {

    public enum Kind { NONE, NULL, EXPRESSION, LITERAL }

    /** Nessun default dichiarato. */
    public static final ColumnDefault NONE = new ColumnDefault(Kind.NONE, null);
    /** {@code DEFAULT NULL}. */
    public static final ColumnDefault NULL_VALUE = new ColumnDefault(Kind.NULL, null);
    /** {@code DEFAULT CURRENT_TIMESTAMP}. */
    public static final ColumnDefault CURRENT_TIMESTAMP = new ColumnDefault(Kind.EXPRESSION, "CURRENT_TIMESTAMP");

    public ColumnDefault {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.NONE || kind == Kind.NULL) {
            value = null;
        } else {
            Objects.requireNonNull(value, "value");
        }
    }

    /** Letterale: {@code literal("")} è la stringa vuota, {@code literal("0")} lo zero. */
    public static ColumnDefault literal(String value) {
        return new ColumnDefault(Kind.LITERAL, value);
    }

    /** Espressione scritta com'è nell'SQL: {@code CURRENT_TIMESTAMP}, {@code uuid()}… */
    public static ColumnDefault expression(String expression) {
        return new ColumnDefault(Kind.EXPRESSION, expression.trim());
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }
}
