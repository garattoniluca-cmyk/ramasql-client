/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Una modifica pendente del data-entry, pronta per diventare un'istruzione DML.
 * I valori sono il testo delle celle; {@code null} è il NULL di SQL. Le mappe conservano l'ordine delle colonne.
 *
 * @param rowId          identificativo stabile della riga in {@link PendingChanges} (per marcarla salvata o in errore)
 * @param kind           INSERT / UPDATE / DELETE
 * @param originalValues UPDATE e DELETE: la riga com'è sul server (tutte le colonne); INSERT: vuota
 * @param newValues      INSERT: solo le colonne impostate (le altre useranno DEFAULT / AUTO_INCREMENT);
 *                       UPDATE: <b>solo le colonne cambiate</b>; DELETE: vuota
 */
public record RowChange(long rowId, Kind kind, Map<String, String> originalValues, Map<String, String> newValues) {

    public enum Kind { INSERT, UPDATE, DELETE }

    public RowChange {
        Objects.requireNonNull(kind, "kind");
        originalValues = freeze(originalValues);
        newValues = freeze(newValues);
    }

    private static Map<String, String> freeze(Map<String, String> map) {
        return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    public static RowChange insert(long rowId, Map<String, String> setValues) {
        return new RowChange(rowId, Kind.INSERT, null, setValues);
    }

    public static RowChange update(long rowId, Map<String, String> originalRow, Map<String, String> changedValues) {
        return new RowChange(rowId, Kind.UPDATE, originalRow, changedValues);
    }

    public static RowChange delete(long rowId, Map<String, String> originalRow) {
        return new RowChange(rowId, Kind.DELETE, originalRow, null);
    }
}
