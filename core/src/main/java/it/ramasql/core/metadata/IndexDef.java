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

import java.util.List;
import java.util.Objects;

/**
 * Indice di una tabella; la chiave primaria è l'indice di tipo {@link IndexKind#PRIMARY} con nome {@code PRIMARY}.
 *
 * @param name    nome (sempre {@code PRIMARY} per la chiave primaria)
 * @param kind    PRIMARY / UNIQUE / INDEX
 * @param columns colonne, nell'ordine dell'indice
 */
public record IndexDef(String name, IndexKind kind, List<String> columns) {

    public static final String PRIMARY_NAME = "PRIMARY";

    public IndexDef {
        Objects.requireNonNull(kind, "kind");
        name = kind == IndexKind.PRIMARY ? PRIMARY_NAME : Objects.requireNonNull(name, "name");
        columns = List.copyOf(columns);
    }

    public static IndexDef primary(String... columns) {
        return new IndexDef(PRIMARY_NAME, IndexKind.PRIMARY, List.of(columns));
    }

    public static IndexDef unique(String name, String... columns) {
        return new IndexDef(name, IndexKind.UNIQUE, List.of(columns));
    }

    public static IndexDef index(String name, String... columns) {
        return new IndexDef(name, IndexKind.INDEX, List.of(columns));
    }

    public boolean isPrimary() {
        return kind == IndexKind.PRIMARY;
    }

    /** Vero per PRIMARY e UNIQUE. */
    public boolean isUnique() {
        return kind != IndexKind.INDEX;
    }

    public IndexDef withName(String v) {
        return new IndexDef(v, kind, columns);
    }

    public IndexDef withKind(IndexKind v) {
        return new IndexDef(name, v, columns);
    }

    public IndexDef withColumns(String... v) {
        return new IndexDef(name, kind, List.of(v));
    }
}
