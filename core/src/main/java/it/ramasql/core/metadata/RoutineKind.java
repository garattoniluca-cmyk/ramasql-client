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

/** Oggetti programmabili del catalogo: in v1 solo in <b>sola lettura</b> (ADR-017). */
public enum RoutineKind {
    PROCEDURE("PROCEDURE"),
    FUNCTION("FUNCTION"),
    TRIGGER("TRIGGER"),
    EVENT("EVENT");

    private final String sql;

    RoutineKind(String sql) {
        this.sql = sql;
    }

    /** Parola SQL: {@code SHOW CREATE <sql>}. */
    public String sql() {
        return sql;
    }
}
