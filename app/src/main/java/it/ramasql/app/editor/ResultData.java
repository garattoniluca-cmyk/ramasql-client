/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Le righe restituite da un'istruzione (SELECT, SHOW…), già lette e convertite in testo.
 *
 * @param columns   colonne del risultato, nell'ordine
 * @param rows      righe; ogni riga ha una cella per colonna ({@code null} = NULL)
 * @param truncated il server aveva altre righe oltre il limite di lettura (il «limite righe» delle impostazioni)
 */
public record ResultData(List<Column> columns, List<List<String>> rows, boolean truncated) {

    /**
     * Una colonna del risultato.
     *
     * @param name     etichetta (alias compreso)
     * @param typeName tipo SQL come lo dice il driver ({@code INT}, {@code VARCHAR}…); decide l'allineamento
     */
    public record Column(String name, String typeName) {
        public Column {
            Objects.requireNonNull(name, "name");
            typeName = typeName == null || typeName.isBlank() ? "VARCHAR" : typeName;
        }
    }

    public ResultData {
        columns = List.copyOf(columns);
        List<List<String>> copy = new ArrayList<>(rows.size());
        for (List<String> r : rows) {
            if (r.size() != columns.size()) {
                throw new IllegalArgumentException("Riga con " + r.size() + " celle, colonne " + columns.size());
            }
            copy.add(Collections.unmodifiableList(new ArrayList<>(r)));
        }
        rows = Collections.unmodifiableList(copy);
    }

    public int rowCount() {
        return rows.size();
    }
}
