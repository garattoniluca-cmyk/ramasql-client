/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.visual;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.qb.QbMetadata;

/**
 * I metadati del diagramma presi dal <b>canale dei metadati del client</b> ({@link MetadataReader}, lo stesso del
 * navigatore, con la sua cache): il query builder non interroga più il server da sé con {@code DatabaseMetaData}
 * ({@code BUG-016}). Le chiavi esterne che puntano a una tabella si ricavano da quelle delle altre tabelle del catalogo.
 */
final class MetadataQbMetadata implements QbMetadata {

    private final MetadataReader reader;
    private final String defaultCatalog;

    MetadataQbMetadata(MetadataReader reader, String defaultCatalog) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.defaultCatalog = defaultCatalog;
    }

    private String cat(String catalog) {
        return catalog != null ? catalog : defaultCatalog;
    }

    @Override
    public List<String> tables(String catalog) throws SQLException {
        return reader.tables(cat(catalog)).stream().filter(s -> !s.isView()).map(TableSummary::name).toList();
    }

    @Override
    public List<String> views(String catalog) throws SQLException {
        return reader.tables(cat(catalog)).stream().filter(TableSummary::isView).map(TableSummary::name).toList();
    }

    @Override
    public String find(String catalog, String table) throws SQLException {
        if (table == null) {
            return null;
        }
        for (TableSummary s : reader.tables(cat(catalog))) {
            if (s.name().equalsIgnoreCase(table)) {
                return s.name();
            }
        }
        return null;
    }

    @Override
    public List<Column> columns(String catalog, String table) throws SQLException {
        String name = find(catalog, table);
        if (name == null) {
            return List.of();
        }
        var def = reader.table(cat(catalog), name);
        List<Column> out = new ArrayList<>();
        if (def.isPresent()) {
            List<String> pk = def.get().primaryKey().map(IndexDef::columns).orElse(List.of());
            for (ColumnDef c : def.get().columns()) {
                out.add(new Column(c.name(), typeOf(c), pk.stream().anyMatch(p -> p.equalsIgnoreCase(c.name()))));
            }
        } else {
            for (ColumnDef c : reader.viewColumns(cat(catalog), name)) {   // una vista si usa come una tabella
                out.add(new Column(c.name(), typeOf(c), false));
            }
        }
        return out;
    }

    @Override
    public List<ForeignKey> importedKeys(String catalog, String table) throws SQLException {
        String name = find(catalog, table);
        if (name == null) {
            return List.of();
        }
        List<ForeignKey> out = new ArrayList<>();
        reader.table(cat(catalog), name).ifPresent(def -> addKeys(def, out));
        return out;
    }

    @Override
    public List<ForeignKey> exportedKeys(String catalog, String table) throws SQLException {
        String name = find(catalog, table);
        if (name == null) {
            return List.of();
        }
        List<ForeignKey> out = new ArrayList<>();
        for (TableDef other : reader.allTables(cat(catalog))) {
            List<ForeignKey> keys = new ArrayList<>();
            addKeys(other, keys);
            keys.stream().filter(k -> k.primaryTable().equalsIgnoreCase(name)).forEach(out::add);
        }
        return out;
    }

    /** Una riga per colonna delle chiavi esterne della tabella (le FK composte danno più righe con lo stesso nome). */
    private static void addKeys(TableDef def, List<ForeignKey> out) {
        for (ForeignKeyDef fk : def.foreignKeys()) {
            if (fk.refCatalog() != null && def.catalog() != null && !fk.refCatalog().equalsIgnoreCase(def.catalog())) {
                continue;   // riferimento a un altro catalogo: il diagramma lavora su un catalogo solo
            }
            for (int i = 0; i < fk.columns().size() && i < fk.refColumns().size(); i++) {
                out.add(new ForeignKey(fk.name(), fk.refTable(), fk.refColumns().get(i), def.name(),
                        fk.columns().get(i)));
            }
        }
    }

    private static String typeOf(ColumnDef c) {
        return c.dataType() + (c.typeArgs() == null ? "" : "(" + c.typeArgs() + ")") + (c.unsigned() ? " UNSIGNED" : "");
    }
}
