/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.navigator;

import java.util.Locale;
import java.util.Objects;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.RoutineInfo;
import it.ramasql.core.metadata.TableSummary;

/**
 * Un nodo dell'albero del navigatore (oggetto utente dei {@code DefaultMutableTreeNode}).
 *
 * @param kind    tipo di nodo
 * @param catalog catalogo ({@code null} per il server)
 * @param name    nome dell'oggetto (tabella, colonna, indice…) o testo del nodo di servizio
 * @param data    dati letti dal server: {@link TableSummary}, {@link ColumnDef}, {@link IndexDef} (o
 *                {@link it.ramasql.core.metadata.ReadOnlyIndex} per gli indici in sola lettura),
 *                {@link ForeignKeyDef}, {@link RoutineInfo}, {@link it.ramasql.core.metadata.CatalogInfo};
 *                {@code null} per cartelle e nodi di servizio
 * @param key     chiave stabile (per ricordare espansione e selezione quando l'albero si ricostruisce)
 * @param count   numero di elementi (cartelle); -1 se non serve
 */
public record NavNode(Kind kind, String catalog, String name, Object data, String key, int count) {

    public enum Kind {
        SERVER, CATALOG, TABLES, VIEWS, ROUTINES, TABLE, VIEW, ROUTINE, COLUMNS, INDEXES, FOREIGN_KEYS,
        COLUMN, INDEX, FOREIGN_KEY, LOADING, MESSAGE
    }

    public NavNode {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
    }

    /** Chiave di un catalogo. */
    static String catalogKey(String catalog) {
        return "C:" + lower(catalog);
    }

    /** Chiave di una tabella o vista. */
    static String tableKey(String catalog, String table) {
        return "T:" + lower(catalog) + "." + lower(table);
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    public boolean isTable() {
        return kind == Kind.TABLE;
    }

    /** La tabella (o vista) dei nodi TABLE/VIEW. */
    public TableSummary table() {
        return data instanceof TableSummary t ? t : null;
    }

    public RoutineInfo routine() {
        return data instanceof RoutineInfo r ? r : null;
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
