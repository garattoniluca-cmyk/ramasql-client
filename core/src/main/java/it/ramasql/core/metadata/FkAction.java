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

import java.util.Locale;

/** Azione referenziale di una chiave esterna ({@code ON DELETE} / {@code ON UPDATE}). */
public enum FkAction {
    RESTRICT("RESTRICT"),
    CASCADE("CASCADE"),
    SET_NULL("SET NULL"),
    NO_ACTION("NO ACTION");

    private final String sql;

    FkAction(String sql) {
        this.sql = sql;
    }

    /** Testo SQL dell'azione, es. {@code SET NULL}. */
    public String sql() {
        return sql;
    }

    /** Dal testo di {@code information_schema.REFERENTIAL_CONSTRAINTS}; vuoto o nullo vale RESTRICT (default del server). */
    public static FkAction fromSql(String text) {
        if (text == null || text.isBlank()) {
            return RESTRICT;
        }
        String wanted = text.trim().replace('_', ' ').replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        for (FkAction a : values()) {
            if (a.sql.equals(wanted)) {
                return a;
            }
        }
        throw new IllegalArgumentException("Azione referenziale non gestita: " + text);
    }
}
