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
 * Chiave esterna.
 *
 * @param name       nome del vincolo; {@code null} = lo assegna il server (solo per FK nuove)
 * @param columns    colonne della tabella figlia, in ordine
 * @param refCatalog catalogo della tabella riferita; {@code null} = lo stesso della tabella figlia
 * @param refTable   tabella riferita
 * @param refColumns colonne riferite, nello stesso ordine di {@code columns}
 * @param onDelete   azione {@code ON DELETE}
 * @param onUpdate   azione {@code ON UPDATE}
 */
public record ForeignKeyDef(
        String name,
        List<String> columns,
        String refCatalog,
        String refTable,
        List<String> refColumns,
        FkAction onDelete,
        FkAction onUpdate) {

    public ForeignKeyDef {
        name = name == null || name.isBlank() ? null : name;
        columns = List.copyOf(columns);
        refCatalog = refCatalog == null || refCatalog.isBlank() ? null : refCatalog;
        Objects.requireNonNull(refTable, "refTable");
        refColumns = List.copyOf(refColumns);
        onDelete = onDelete == null ? FkAction.RESTRICT : onDelete;
        onUpdate = onUpdate == null ? FkAction.RESTRICT : onUpdate;
    }

    /** FK su una colonna, stesso catalogo, azioni RESTRICT/RESTRICT. */
    public static ForeignKeyDef of(String name, String column, String refTable, String refColumn) {
        return new ForeignKeyDef(name, List.of(column), null, refTable, List.of(refColumn), FkAction.RESTRICT,
                FkAction.RESTRICT);
    }

    /** FK composta, stesso catalogo, azioni RESTRICT/RESTRICT. */
    public static ForeignKeyDef of(String name, List<String> columns, String refTable, List<String> refColumns) {
        return new ForeignKeyDef(name, columns, null, refTable, refColumns, FkAction.RESTRICT, FkAction.RESTRICT);
    }

    public ForeignKeyDef withName(String v) {
        return new ForeignKeyDef(v, columns, refCatalog, refTable, refColumns, onDelete, onUpdate);
    }

    public ForeignKeyDef withActions(FkAction newOnDelete, FkAction newOnUpdate) {
        return new ForeignKeyDef(name, columns, refCatalog, refTable, refColumns, newOnDelete, newOnUpdate);
    }

    public ForeignKeyDef withOnDelete(FkAction v) {
        return withActions(v, onUpdate);
    }

    public ForeignKeyDef withOnUpdate(FkAction v) {
        return withActions(onDelete, v);
    }

    public ForeignKeyDef withReference(String newRefCatalog, String newRefTable, List<String> newRefColumns) {
        return new ForeignKeyDef(name, columns, newRefCatalog, newRefTable, newRefColumns, onDelete, onUpdate);
    }

    public ForeignKeyDef withColumns(List<String> newColumns, List<String> newRefColumns) {
        return new ForeignKeyDef(name, newColumns, refCatalog, refTable, newRefColumns, onDelete, onUpdate);
    }
}
