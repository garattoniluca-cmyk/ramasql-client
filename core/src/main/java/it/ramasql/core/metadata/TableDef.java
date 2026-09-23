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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Tabella (record immutabile): è sia ciò che si legge dal server, sia ciò che l'editor di tabelle modifica.
 *
 * @param catalog            catalogo (database); {@code null} = nome non qualificato nell'SQL generato
 * @param name               nome
 * @param engine             {@code InnoDB} / {@code MyISAM}; {@code null} = non specificato
 * @param charset            charset predefinito; {@code null} = non specificato
 * @param collation          collation predefinita; {@code null} = non specificata
 * @param comment            commento ({@code ""} se assente)
 * @param autoIncrementStart valore di {@code AUTO_INCREMENT=}; {@code null} = non specificato
 * @param columns            colonne, in ordine
 * @param indexes            indici, inclusa la chiave primaria
 * @param foreignKeys        chiavi esterne
 * @param advancedElements   elementi avanzati non editabili in v1 (CHECK, colonne generate, partizioni), come frammenti
 *                           di testo di {@code SHOW CREATE TABLE}: conservati per mostrarli, <b>mai toccati</b> dai generatori
 */
public record TableDef(
        String catalog,
        String name,
        String engine,
        String charset,
        String collation,
        String comment,
        Long autoIncrementStart,
        List<ColumnDef> columns,
        List<IndexDef> indexes,
        List<ForeignKeyDef> foreignKeys,
        List<String> advancedElements) {

    public TableDef {
        Objects.requireNonNull(name, "name");
        catalog = blankToNull(catalog);
        engine = blankToNull(engine);
        charset = blankToNull(charset);
        collation = blankToNull(collation);
        comment = comment == null ? "" : comment;
        columns = columns == null ? List.of() : List.copyOf(columns);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        foreignKeys = foreignKeys == null ? List.of() : List.copyOf(foreignKeys);
        advancedElements = advancedElements == null ? List.of() : List.copyOf(advancedElements);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Tabella vuota: solo catalogo (anche {@code null}) e nome. */
    public static TableDef of(String catalog, String name) {
        return new TableDef(catalog, name, null, null, null, "", null, List.of(), List.of(), List.of(), List.of());
    }

    // ---------------------------------------------------------------- ricerca

    /** Colonna per nome (senza distinzione tra maiuscole e minuscole, come sul server). */
    public Optional<ColumnDef> column(String columnName) {
        return columns.stream().filter(c -> c.name().equalsIgnoreCase(columnName)).findFirst();
    }

    public Optional<IndexDef> index(String indexName) {
        return indexes.stream().filter(i -> i.name().equalsIgnoreCase(indexName)).findFirst();
    }

    public Optional<IndexDef> primaryKey() {
        return indexes.stream().filter(IndexDef::isPrimary).findFirst();
    }

    public Optional<ForeignKeyDef> foreignKey(String fkName) {
        return foreignKeys.stream().filter(f -> f.name() != null && f.name().equalsIgnoreCase(fkName)).findFirst();
    }

    /**
     * Gli indici che v1 non modifica (FULLTEXT, SPATIAL, su prefisso, su espressione, DESC), ricavati dagli
     * {@link #advancedElements()}: da mostrare in sola lettura accanto a {@link #indexes()}. I generatori non li
     * toccano (restano elementi avanzati).
     */
    public List<ReadOnlyIndex> readOnlyIndexes() {
        return advancedElements.stream().map(ReadOnlyIndex::fromCreateLine).flatMap(Optional::stream).toList();
    }

    public boolean isInnoDb() {
        return engine == null || engine.equalsIgnoreCase("InnoDB");
    }

    // ---------------------------------------------------------------- «with»

    public TableDef withCatalog(String v) {
        return new TableDef(v, name, engine, charset, collation, comment, autoIncrementStart, columns, indexes,
                foreignKeys, advancedElements);
    }

    public TableDef withName(String v) {
        return new TableDef(catalog, v, engine, charset, collation, comment, autoIncrementStart, columns, indexes,
                foreignKeys, advancedElements);
    }

    public TableDef withEngine(String v) {
        return new TableDef(catalog, name, v, charset, collation, comment, autoIncrementStart, columns, indexes,
                foreignKeys, advancedElements);
    }

    public TableDef withCharset(String newCharset, String newCollation) {
        return new TableDef(catalog, name, engine, newCharset, newCollation, comment, autoIncrementStart, columns,
                indexes, foreignKeys, advancedElements);
    }

    public TableDef withComment(String v) {
        return new TableDef(catalog, name, engine, charset, collation, v, autoIncrementStart, columns, indexes,
                foreignKeys, advancedElements);
    }

    public TableDef withAutoIncrementStart(Long v) {
        return new TableDef(catalog, name, engine, charset, collation, comment, v, columns, indexes, foreignKeys,
                advancedElements);
    }

    public TableDef withColumns(List<ColumnDef> v) {
        return new TableDef(catalog, name, engine, charset, collation, comment, autoIncrementStart, v, indexes,
                foreignKeys, advancedElements);
    }

    public TableDef withIndexes(List<IndexDef> v) {
        return new TableDef(catalog, name, engine, charset, collation, comment, autoIncrementStart, columns, v,
                foreignKeys, advancedElements);
    }

    public TableDef withForeignKeys(List<ForeignKeyDef> v) {
        return new TableDef(catalog, name, engine, charset, collation, comment, autoIncrementStart, columns, indexes,
                v, advancedElements);
    }

    public TableDef withAdvancedElements(List<String> v) {
        return new TableDef(catalog, name, engine, charset, collation, comment, autoIncrementStart, columns, indexes,
                foreignKeys, v);
    }

    // ---------------------------------------------------------------- colonne

    /** Numera le colonne 1…n, come le riporta il server: lo stato «appena letto» da cui l'editor parte. */
    public TableDef withOrdinalPositions() {
        List<ColumnDef> list = new ArrayList<>();
        for (ColumnDef c : columns) {
            list.add(c.withOrdinalPosition(list.size() + 1));
        }
        return withColumns(list);
    }

    /** Aggiunge in fondo. */
    public TableDef addColumn(ColumnDef column) {
        List<ColumnDef> list = new ArrayList<>(columns);
        list.add(column);
        return withColumns(list);
    }

    /** Inserisce alla posizione data (0 = prima colonna). */
    public TableDef addColumn(int listIndex, ColumnDef column) {
        List<ColumnDef> list = new ArrayList<>(columns);
        list.add(listIndex, column);
        return withColumns(list);
    }

    public TableDef removeColumn(String columnName) {
        return withColumns(columns.stream().filter(c -> !c.name().equalsIgnoreCase(columnName)).toList());
    }

    /** Sostituisce la colonna di nome dato con la sua versione modificata: {@code t.changeColumn("a", c -> c.notNull())}. */
    public TableDef changeColumn(String columnName, UnaryOperator<ColumnDef> change) {
        if (column(columnName).isEmpty()) {
            throw new IllegalArgumentException("Colonna inesistente: " + columnName);
        }
        return withColumns(columns.stream()
                .map(c -> c.name().equalsIgnoreCase(columnName) ? change.apply(c) : c).toList());
    }

    // ---------------------------------------------------------------- indici

    public TableDef addIndex(IndexDef index) {
        List<IndexDef> list = new ArrayList<>(indexes);
        list.add(index);
        return withIndexes(list);
    }

    public TableDef removeIndex(String indexName) {
        return withIndexes(indexes.stream().filter(i -> !i.name().equalsIgnoreCase(indexName)).toList());
    }

    public TableDef changeIndex(String indexName, UnaryOperator<IndexDef> change) {
        if (index(indexName).isEmpty()) {
            throw new IllegalArgumentException("Indice inesistente: " + indexName);
        }
        return withIndexes(indexes.stream()
                .map(i -> i.name().equalsIgnoreCase(indexName) ? change.apply(i) : i).toList());
    }

    /** Imposta (o sostituisce) la chiave primaria; senza colonne la toglie. */
    public TableDef withPrimaryKey(String... pkColumns) {
        TableDef without = withIndexes(indexes.stream().filter(i -> !i.isPrimary()).toList());
        if (pkColumns.length == 0) {
            return without;
        }
        List<IndexDef> list = new ArrayList<>();
        list.add(IndexDef.primary(pkColumns));
        list.addAll(without.indexes());
        return without.withIndexes(list);
    }

    // ---------------------------------------------------------------- chiavi esterne

    public TableDef addForeignKey(ForeignKeyDef fk) {
        List<ForeignKeyDef> list = new ArrayList<>(foreignKeys);
        list.add(fk);
        return withForeignKeys(list);
    }

    public TableDef removeForeignKey(String fkName) {
        return withForeignKeys(foreignKeys.stream()
                .filter(f -> f.name() == null || !f.name().equalsIgnoreCase(fkName)).toList());
    }

    public TableDef changeForeignKey(String fkName, UnaryOperator<ForeignKeyDef> change) {
        if (foreignKey(fkName).isEmpty()) {
            throw new IllegalArgumentException("Chiave esterna inesistente: " + fkName);
        }
        return withForeignKeys(foreignKeys.stream()
                .map(f -> f.name() != null && f.name().equalsIgnoreCase(fkName) ? change.apply(f) : f).toList());
    }
}
