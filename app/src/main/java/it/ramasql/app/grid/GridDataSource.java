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

import java.util.List;
import java.util.Objects;

/**
 * Il «fornitore di dati» della griglia: legge <b>una pagina</b> di righe, nell'ordine richiesto. È l'unico punto da cui
 * la griglia riceve dati; non c'è il verso opposto (la griglia non scrive mai: le modifiche escono solo da
 * {@link DataGrid#setConfirmHandler}). Nel programma lo implementerà l'integrazione con il server
 * ({@code SELECT … ORDER BY … LIMIT pageSize+1 OFFSET …}); per i test e per i risultati già in memoria c'è
 * {@link InMemoryGridDataSource}.
 *
 * <p>Si chiama sull'EDT: un'implementazione lenta blocca l'interfaccia (vedi i limiti in {@link DataGrid}).
 */
public interface GridDataSource {

    /**
     * Ordinamento su una colonna.
     *
     * @param column    nome della colonna (come in {@link it.ramasql.core.metadata.ColumnDef#name()})
     * @param ascending crescente ({@code ASC}) o decrescente ({@code DESC})
     */
    record SortOrder(String column, boolean ascending) {
        public SortOrder {
            Objects.requireNonNull(column, "column");
        }
    }

    /**
     * Una pagina letta.
     *
     * @param rows    righe (una lista di celle per riga, nell'ordine delle colonne della griglia; {@code null} = NULL)
     * @param hasMore c'è almeno un'altra riga dopo questa pagina (chi legge dal server chiede {@code pageSize + 1} righe)
     */
    record Page(List<List<String>> rows, boolean hasMore) {
        public Page {
            rows = List.copyOf(rows.stream().map(r -> java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<>(r))).toList());
        }
    }

    /**
     * @param pageIndex pagina richiesta, da 0
     * @param pageSize  righe per pagina (il «limite righe» delle impostazioni, 1000 di norma)
     * @param orderBy   ordinamento, {@code null} = quello naturale del server
     */
    Page load(int pageIndex, int pageSize, SortOrder orderBy);
}
