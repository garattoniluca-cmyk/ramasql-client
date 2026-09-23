/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.workspace;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import it.ramasql.app.editor.CompletionSource;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableSummary;

/**
 * Il completamento dell'editor SQL alimentato dai metadati già letti ({@link MetadataReader}, con la sua cache): nomi
 * di catalogo, di tabella e di colonna. Non esegue SQL dell'utente e non passa dal registro: è lo stesso canale
 * interno che riempie il navigatore.
 *
 * <p>Se il server non risponde il completamento resta vuoto: si continua a scrivere a mano, senza errori in faccia.
 */
public final class MetadataCompletionSource implements CompletionSource {

    private final MetadataReader reader;
    private final Supplier<String> currentCatalog;

    /**
     * @param currentCatalog il catalogo su cui si è posizionati (dal navigatore o dalla connessione); può dare
     *                       {@code null}
     */
    public MetadataCompletionSource(MetadataReader reader, Supplier<String> currentCatalog) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.currentCatalog = Objects.requireNonNull(currentCatalog, "currentCatalog");
    }

    @Override
    public String currentCatalog() {
        String c = currentCatalog.get();
        return c == null ? "" : c;
    }

    @Override
    public List<String> catalogs() {
        try {
            List<String> names = new ArrayList<>();
            for (CatalogInfo c : reader.catalogs()) {
                if (!c.system()) {
                    names.add(c.name());
                }
            }
            return List.copyOf(names);
        } catch (SQLException e) {
            return List.of();
        }
    }

    @Override
    public List<String> tables(String catalog) {
        if (catalog == null || catalog.isBlank()) {
            return List.of();
        }
        try {
            List<String> names = new ArrayList<>();
            for (TableSummary t : reader.tables(catalog)) {
                names.add(t.name());   // tabelle e viste: in una SELECT valgono entrambe
            }
            return List.copyOf(names);
        } catch (SQLException e) {
            return List.of();
        }
    }

    @Override
    public List<String> columns(String catalog, String table) {
        if (catalog == null || catalog.isBlank() || table == null || table.isBlank()) {
            return List.of();
        }
        try {
            List<ColumnDef> columns = reader.table(catalog, table)
                    .map(t -> t.columns())
                    .orElseGet(() -> reader(catalog, table));
            List<String> names = new ArrayList<>(columns.size());
            for (ColumnDef c : columns) {
                names.add(c.name());
            }
            return List.copyOf(names);
        } catch (SQLException e) {
            return List.of();
        }
    }

    /** Non è una tabella: può essere una vista, le cui colonne si leggono a parte. */
    private List<ColumnDef> reader(String catalog, String view) {
        try {
            return reader.viewColumns(catalog, view);
        } catch (SQLException e) {
            return List.of();
        }
    }
}
