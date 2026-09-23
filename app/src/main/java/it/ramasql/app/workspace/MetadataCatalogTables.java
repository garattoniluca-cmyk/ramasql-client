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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import it.ramasql.app.tableeditor.CatalogTables;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableKind;
import it.ramasql.core.metadata.TableSummary;

/**
 * Le altre tabelle del catalogo viste dall'editor, lette dal canale dei metadati (con la sua cache): l'editor può così
 * controllare tipi, indici ed engine della tabella riferita da una chiave esterna <b>senza eseguire SQL</b> e sapere
 * chi riferisce la tabella in modifica (blocco della conversione a MyISAM, {@code T5.7}).
 *
 * <p>Le viste non compaiono: una chiave esterna non può puntare a una vista. Se il server non risponde l'editor si
 * comporta come in un catalogo vuoto (nessun suggerimento) invece di bloccare l'utente.
 */
public final class MetadataCatalogTables implements CatalogTables {

    private final MetadataReader reader;
    private final String catalog;
    /** Una lettura dei metadati non è riuscita: da qui in poi l'elenco non è più affidabile. */
    private volatile boolean incomplete;

    public MetadataCatalogTables(MetadataReader reader, String catalog) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public boolean isComplete() {
        return !incomplete;
    }

    @Override
    public List<String> tableNames() {
        try {
            List<String> names = new ArrayList<>();
            for (TableSummary t : reader.tables(catalog)) {
                if (t.kind() == TableKind.TABLE) {
                    names.add(t.name());
                }
            }
            names.sort(Comparator.naturalOrder());
            return List.copyOf(names);
        } catch (SQLException e) {
            incomplete = true;
            return List.of();
        }
    }

    @Override
    public Optional<TableDef> table(String name) {
        try {
            return reader.table(catalog, name);
        } catch (SQLException e) {
            incomplete = true;
            return Optional.empty();
        }
    }

    @Override
    public List<IncomingReference> referencing(String tableName) {
        try {
            List<IncomingReference> found = new ArrayList<>();
            for (TableDef t : reader.allTables(catalog)) {
                if (t.name().equalsIgnoreCase(tableName)) {
                    continue;   // le chiavi esterne della tabella stessa non la «riferiscono» dall'esterno
                }
                for (ForeignKeyDef fk : t.foreignKeys()) {
                    if (fk.refTable().equalsIgnoreCase(tableName)) {
                        found.add(new IncomingReference(t.name(), fk.name()));
                    }
                }
            }
            return List.copyOf(found);
        } catch (SQLException e) {
            incomplete = true;
            return List.of();
        }
    }
}
