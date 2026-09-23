/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;

/**
 * Fornitore di dati in memoria: righe già lette (risultati di una query) o preparate da un test. Ordina come farebbe
 * il server: NULL per primi in ordine crescente, colonne numeriche per valore, le altre come testo.
 */
public final class InMemoryGridDataSource implements GridDataSource {

    private final List<ColumnDef> columns;
    private final List<List<String>> rows;
    private int loadCount;

    public InMemoryGridDataSource(List<ColumnDef> columns, List<? extends List<String>> rows) {
        this.columns = List.copyOf(columns);
        this.rows = new ArrayList<>();
        for (List<String> r : rows) {
            this.rows.add(Collections.unmodifiableList(new ArrayList<>(r)));
        }
    }

    @Override
    public Page load(int pageIndex, int pageSize, SortOrder orderBy) {
        loadCount++;
        List<List<String>> sorted = new ArrayList<>(rows);
        if (orderBy != null) {
            int c = columnIndex(orderBy.column());
            boolean numeric = SqlTypes.isNumeric(columns.get(c).dataType());
            Comparator<List<String>> cmp = Comparator.comparing(r -> r.get(c),
                    Comparator.nullsFirst(numeric ? InMemoryGridDataSource::compareNumbers : String::compareTo));
            sorted.sort(orderBy.ascending() ? cmp : cmp.reversed());
        }
        int from = Math.min(sorted.size(), pageIndex * pageSize);
        int to = Math.min(sorted.size(), from + pageSize);
        return new Page(sorted.subList(from, to), to < sorted.size());
    }

    /** Quante volte la griglia ha chiesto una pagina (per i test). */
    public int loadCount() {
        return loadCount;
    }

    private int columnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Colonna sconosciuta: " + name);
    }

    private static int compareNumbers(String a, String b) {
        try {
            return new BigDecimal(a.trim()).compareTo(new BigDecimal(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }
}
