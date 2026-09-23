/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Risultato tabellare di un'istruzione (SELECT, SHOW…), letto fino al limite di righe dell'esecutore.
 *
 * @param columns   colonne, in ordine
 * @param rows      righe; ogni valore è quello di {@code ResultSet.getObject} ({@code null} = NULL), con i LOB
 *                  già letti ({@code byte[]} / {@code String})
 * @param truncated il server aveva altre righe oltre il limite: non sono state lette
 */
public record ResultTable(List<Column> columns, List<List<Object>> rows, boolean truncated) {

    /**
     * Colonna del risultato.
     *
     * @param label    etichetta (alias o nome)
     * @param typeName tipo come lo riporta il driver, es. {@code VARCHAR}
     * @param jdbcType tipo {@link java.sql.Types}
     */
    public record Column(String label, String typeName, int jdbcType) {
        public Column {
            Objects.requireNonNull(label, "label");
            typeName = typeName == null ? "" : typeName;
        }
    }

    public ResultTable {
        columns = List.copyOf(columns);
        List<List<Object>> copy = new ArrayList<>(rows.size());
        for (List<Object> r : rows) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(r)));   // i NULL sono ammessi
        }
        rows = Collections.unmodifiableList(copy);
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return columns.size();
    }

    /** Valore alla riga e colonna date (da 0). */
    public Object value(int row, int column) {
        return rows.get(row).get(column);
    }
}
