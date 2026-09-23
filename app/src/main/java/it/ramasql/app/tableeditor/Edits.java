/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;

/**
 * Le modifiche che le schede fanno sul modello, come funzioni pure {@code TableDef → TableDef}: così ogni
 * gesto dell'utente è un passo verificabile e l'anteprima SQL si ricava sempre dallo stesso modello.
 *
 * <p>Regole per non rompere {@code TableDiff}: le colonne esistenti conservano {@code ordinalPosition} (una rinomina
 * diventa {@code CHANGE COLUMN}), le nuove nascono con posizione 0; rinominare o togliere una colonna aggiorna
 * indici e chiavi esterne che la usano.
 */
final class Edits {

    private Edits() {
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /** Il primo nome libero tra {@code base1}, {@code base2}… (senza distinzione tra maiuscole e minuscole). */
    static String freeName(String base, List<String> taken) {
        Set<String> used = new HashSet<>();
        taken.forEach(n -> used.add(lower(n)));
        for (int i = 1;; i++) {
            if (!used.contains(lower(base + i))) {
                return base + i;
            }
        }
    }

    static String freeNameExact(String wanted, List<String> taken) {
        Set<String> used = new HashSet<>();
        taken.forEach(n -> used.add(lower(n)));
        return used.contains(lower(wanted)) ? freeName(wanted + "_", taken) : wanted;
    }

    // ================================================================ colonne

    static TableDef changeColumnAt(TableDef t, int index, UnaryOperator<ColumnDef> change) {
        List<ColumnDef> cols = new ArrayList<>(t.columns());
        cols.set(index, change.apply(cols.get(index)));
        return t.withColumns(cols);
    }

    static TableDef renameColumn(TableDef t, int index, String newName) {
        String name = newName == null ? "" : newName.strip();
        String old = t.columns().get(index).name();
        if (name.equals(old)) {
            return t;
        }
        TableDef out = changeColumnAt(t, index, c -> c.withName(name));
        boolean selfReference;
        List<IndexDef> indexes = new ArrayList<>();
        for (IndexDef i : out.indexes()) {
            indexes.add(new IndexDef(i.name(), i.kind(), replace(i.columns(), old, name)));
        }
        List<ForeignKeyDef> fks = new ArrayList<>();
        for (ForeignKeyDef f : out.foreignKeys()) {
            selfReference = f.refTable().equalsIgnoreCase(t.name()) && f.refCatalog() == null;
            fks.add(f.withColumns(replace(f.columns(), old, name),
                    selfReference ? replace(f.refColumns(), old, name) : f.refColumns()));
        }
        return out.withIndexes(indexes).withForeignKeys(fks);
    }

    private static List<String> replace(List<String> names, String old, String replacement) {
        return names.stream().map(n -> n.equalsIgnoreCase(old) ? replacement : n).toList();
    }

    /** Tipo scritto nella cella: {@code VARCHAR(45)} imposta anche la lunghezza. Testo non valido: nessun cambio. */
    static TableDef setType(TableDef t, int index, Object value) {
        TypeChoices.Parsed parsed = TypeChoices.parse(value);
        if (parsed == null) {
            return t;
        }
        return changeColumnAt(t, index, c -> {
            String type = parsed.type();
            String args;
            if (parsed.args() != null) {
                args = parsed.args();
            } else if (TypeChoices.takesNoArgs(type)) {
                args = null;
            } else if (c.typeArgs() != null && TypeChoices.family(type).equals(TypeChoices.family(c.dataType()))) {
                args = c.typeArgs();
            } else {
                args = TypeChoices.defaultArgs(type);
            }
            ColumnDef out = c.withType(type, args);
            if (!SqlTypes.isNumeric(type)) {
                out = out.withUnsigned(false);
            }
            if (!SqlTypes.isInteger(type)) {
                out = out.withAutoIncrement(false);
            }
            return out;
        });
    }

    static TableDef setTypeArgs(TableDef t, int index, String args) {
        return changeColumnAt(t, index, c -> c.withTypeArgs(args));
    }

    static List<String> primaryKeyColumns(TableDef t) {
        return t.primaryKey().map(IndexDef::columns).orElse(List.of());
    }

    static boolean isPrimaryKey(TableDef t, String column) {
        return primaryKeyColumns(t).stream().anyMatch(c -> c.equalsIgnoreCase(column));
    }

    static TableDef setPrimaryKey(TableDef t, int index, boolean on) {
        String name = t.columns().get(index).name();
        List<String> pk = new ArrayList<>(primaryKeyColumns(t));
        pk.removeIf(c -> c.equalsIgnoreCase(name));
        if (on) {
            pk.add(name);
        }
        TableDef out = t;
        if (on) {
            out = changeColumnAt(out, index, ColumnDef::notNull);
        }
        return withPrimaryKeyKeepingPlace(out, pk);
    }

    /** Come {@link TableDef#withPrimaryKey}, ma la chiave primaria resta nella posizione che aveva tra gli indici. */
    private static TableDef withPrimaryKeyKeepingPlace(TableDef t, List<String> pk) {
        List<IndexDef> indexes = new ArrayList<>(t.indexes());
        int at = -1;
        for (int i = 0; i < indexes.size(); i++) {
            if (indexes.get(i).isPrimary()) {
                at = i;
            }
        }
        if (at >= 0) {
            indexes.remove(at);
        }
        if (!pk.isEmpty()) {
            indexes.add(at >= 0 ? at : 0, new IndexDef(IndexDef.PRIMARY_NAME, IndexKind.PRIMARY, pk));
        }
        return t.withIndexes(indexes);
    }

    static TableDef setNotNull(TableDef t, int index, boolean notNull) {
        ColumnDef c = t.columns().get(index);
        if (!notNull && (isPrimaryKey(t, c.name()) || c.autoIncrement())) {
            return t;        // chiave primaria e AUTO_INCREMENT sono sempre NOT NULL
        }
        return changeColumnAt(t, index, x -> x.withNullable(!notNull));
    }

    /** L'indice UNIQUE di una sola colonna che la casella «UQ» rappresenta. */
    static boolean isUniqueColumn(TableDef t, String column) {
        return t.indexes().stream().anyMatch(i -> i.kind() == IndexKind.UNIQUE && i.columns().size() == 1
                && i.columns().get(0).equalsIgnoreCase(column));
    }

    static TableDef setUnique(TableDef t, int index, boolean on) {
        String name = t.columns().get(index).name();
        if (on == isUniqueColumn(t, name)) {
            return t;
        }
        if (on) {
            List<String> taken = t.indexes().stream().map(IndexDef::name).toList();
            return t.addIndex(IndexDef.unique(freeNameExact(name + "_UNIQUE", taken), name));
        }
        return t.withIndexes(t.indexes().stream().filter(i -> !(i.kind() == IndexKind.UNIQUE
                && i.columns().size() == 1 && i.columns().get(0).equalsIgnoreCase(name))).toList());
    }

    static TableDef setAutoIncrement(TableDef t, int index, boolean on) {
        return changeColumnAt(t, index, c -> {
            if (on && !SqlTypes.isInteger(c.dataType())) {
                return c;
            }
            ColumnDef out = c.withAutoIncrement(on);
            return on ? out.notNull() : out;
        });
    }

    static TableDef setUnsigned(TableDef t, int index, boolean on) {
        return changeColumnAt(t, index, c -> on && !SqlTypes.isNumeric(c.dataType()) ? c : c.withUnsigned(on));
    }

    static TableDef setDefault(TableDef t, int index, String text) {
        return changeColumnAt(t, index, c -> c.withDefault(DefaultText.parse(text)));
    }

    static TableDef setComment(TableDef t, int index, String text) {
        return changeColumnAt(t, index, c -> c.withComment(text == null ? "" : text));
    }

    /** Nuova colonna in fondo: {@code colonnaN VARCHAR(45)} annullabile, posizione 0 (non ancora sul server). */
    static TableDef addColumn(TableDef t, String baseName) {
        List<String> taken = t.columns().stream().map(ColumnDef::name).toList();
        return t.addColumn(ColumnDef.of(freeName(baseName, taken), "VARCHAR", "45").asNew());
    }

    /** Toglie la colonna, la toglie dagli indici (eliminando quelli rimasti vuoti) ed elimina le FK che la usano. */
    static TableDef removeColumn(TableDef t, int index) {
        String name = t.columns().get(index).name();
        List<ColumnDef> cols = new ArrayList<>(t.columns());
        cols.remove(index);
        List<IndexDef> indexes = new ArrayList<>();
        for (IndexDef i : t.indexes()) {
            List<String> rest = i.columns().stream().filter(c -> !c.equalsIgnoreCase(name)).toList();
            if (!rest.isEmpty() || i.columns().isEmpty()) {
                indexes.add(new IndexDef(i.name(), i.kind(), rest));
            }
        }
        List<ForeignKeyDef> fks = t.foreignKeys().stream()
                .filter(f -> f.columns().stream().noneMatch(c -> c.equalsIgnoreCase(name)))
                .filter(f -> !(f.refTable().equalsIgnoreCase(t.name()) && f.refCatalog() == null
                        && f.refColumns().stream().anyMatch(c -> c.equalsIgnoreCase(name))))
                .toList();
        return t.withColumns(cols).withIndexes(indexes).withForeignKeys(fks);
    }

    // ================================================================ indici

    static TableDef changeIndexAt(TableDef t, int index, UnaryOperator<IndexDef> change) {
        List<IndexDef> list = new ArrayList<>(t.indexes());
        list.set(index, change.apply(list.get(index)));
        return t.withIndexes(list);
    }

    static TableDef addIndex(TableDef t, String baseName) {
        List<String> taken = t.indexes().stream().map(IndexDef::name).toList();
        return t.addIndex(new IndexDef(freeName(baseName, taken), IndexKind.INDEX, List.of()));
    }

    static TableDef removeIndexAt(TableDef t, int index) {
        List<IndexDef> list = new ArrayList<>(t.indexes());
        list.remove(index);
        return t.withIndexes(list);
    }

    static TableDef renameIndexAt(TableDef t, int index, String newName) {
        String name = newName == null ? "" : newName.strip();
        IndexDef i = t.indexes().get(index);
        if (i.isPrimary() || name.equals(i.name())) {
            return t;
        }
        return changeIndexAt(t, index, x -> x.withName(name));
    }

    /**
     * Cambio di tipo: PRIMARY solo se non c'è già un'altra chiave primaria; da PRIMARY a UNIQUE/INDEX l'indice
     * prende un nome proprio (il nome {@code PRIMARY} è riservato alla chiave primaria).
     */
    static TableDef setIndexKind(TableDef t, int index, IndexKind kind, String baseName) {
        IndexDef i = t.indexes().get(index);
        if (kind == null || kind == i.kind()) {
            return t;
        }
        if (kind == IndexKind.PRIMARY) {
            if (t.primaryKey().isPresent()) {
                return t;
            }
            TableDef out = changeIndexAt(t, index, x -> x.withKind(IndexKind.PRIMARY));
            for (String c : i.columns()) {
                int ci = columnIndex(out, c);
                if (ci >= 0) {
                    out = changeColumnAt(out, ci, ColumnDef::notNull);
                }
            }
            return out;
        }
        if (i.isPrimary()) {
            List<String> taken = t.indexes().stream().map(IndexDef::name).toList();
            return changeIndexAt(t, index, x -> new IndexDef(freeName(baseName, taken), kind, x.columns()));
        }
        return changeIndexAt(t, index, x -> x.withKind(kind));
    }

    static TableDef addIndexColumn(TableDef t, int index, String column) {
        IndexDef i = t.indexes().get(index);
        if (column == null || i.columns().stream().anyMatch(c -> c.equalsIgnoreCase(column))) {
            return t;
        }
        List<String> cols = new ArrayList<>(i.columns());
        cols.add(column);
        TableDef out = changeIndexAt(t, index, x -> new IndexDef(x.name(), x.kind(), cols));
        if (i.isPrimary()) {
            int ci = columnIndex(out, column);
            out = ci >= 0 ? changeColumnAt(out, ci, ColumnDef::notNull) : out;
        }
        return out;
    }

    static TableDef removeIndexColumn(TableDef t, int index, int position) {
        List<String> cols = new ArrayList<>(t.indexes().get(index).columns());
        cols.remove(position);
        return changeIndexAt(t, index, x -> new IndexDef(x.name(), x.kind(), cols));
    }

    /** Sposta di {@code delta} posti (−1 su, +1 giù) una colonna dell'indice: l'ordine conta. */
    static TableDef moveIndexColumn(TableDef t, int index, int position, int delta) {
        List<String> cols = new ArrayList<>(t.indexes().get(index).columns());
        int target = position + delta;
        if (position < 0 || position >= cols.size() || target < 0 || target >= cols.size()) {
            return t;
        }
        cols.add(target, cols.remove(position));
        return changeIndexAt(t, index, x -> new IndexDef(x.name(), x.kind(), cols));
    }

    static int columnIndex(TableDef t, String column) {
        for (int i = 0; i < t.columns().size(); i++) {
            if (t.columns().get(i).name().equalsIgnoreCase(column)) {
                return i;
            }
        }
        return -1;
    }

    // ================================================================ chiavi esterne

    static TableDef changeForeignKeyAt(TableDef t, int index, UnaryOperator<ForeignKeyDef> change) {
        List<ForeignKeyDef> list = new ArrayList<>(t.foreignKeys());
        list.set(index, change.apply(list.get(index)));
        return t.withForeignKeys(list);
    }

    static TableDef addForeignKey(TableDef t) {
        List<String> taken = t.foreignKeys().stream().map(f -> f.name() == null ? "" : f.name()).toList();
        String name = freeName("fk_" + t.name() + "_", taken);
        return t.addForeignKey(new ForeignKeyDef(name, List.of(), null, "", List.of(), null, null));
    }

    static TableDef removeForeignKeyAt(TableDef t, int index) {
        List<ForeignKeyDef> list = new ArrayList<>(t.foreignKeys());
        list.remove(index);
        return t.withForeignKeys(list);
    }

    /**
     * Cambio della tabella riferita: le colonne riferite si svuotano. Se non c'è ancora nessuna coppia e la tabella
     * riferita ha una chiave primaria di una colonna, propone la coppia quando nella tabella c'è una colonna dal
     * nome evidente ({@code id_<tabella>}, {@code <tabella>_id} o lo stesso nome).
     */
    static TableDef setReferencedTable(TableDef t, int index, String refTable, TableDef parent) {
        ForeignKeyDef f = t.foreignKeys().get(index);
        String table = refTable == null ? "" : refTable.strip();
        if (table.equalsIgnoreCase(f.refTable())) {
            return t;
        }
        List<String> childCols = new ArrayList<>(f.columns());
        List<String> refCols = new ArrayList<>();
        if (childCols.isEmpty() && parent != null && parent.primaryKey().isPresent()
                && parent.primaryKey().get().columns().size() == 1) {
            String pk = parent.primaryKey().get().columns().get(0);
            List<String> candidates = new ArrayList<>(List.of("id_" + table, table + "_id"));
            for (String s : singulars(table)) {
                candidates.add("id_" + s);
                candidates.add(s + "_id");
            }
            candidates.add(pk);
            for (String candidate : candidates) {
                int ci = columnIndex(t, candidate);
                boolean ownKey = isPrimaryKey(t, candidate);    // «id» della tabella stessa: non è la colonna giusta
                if (ci >= 0 && !ownKey) {
                    childCols.add(t.columns().get(ci).name());
                    refCols.add(pk);
                    break;
                }
            }
        } else {
            childCols.forEach(c -> refCols.add(""));
        }
        return changeForeignKeyAt(t, index, x -> new ForeignKeyDef(x.name(), childCols, null, table, refCols,
                x.onDelete(), x.onUpdate()));
    }

    /** Singolari italiani possibili: «editori» → editore, «libri» → libro, «soci» → socio, «categorie» → categoria. */
    private static List<String> singulars(String table) {
        List<String> out = new ArrayList<>();
        if (table.length() > 2 && (table.endsWith("i") || table.endsWith("e"))) {
            String stem = table.substring(0, table.length() - 1);
            for (String end : List.of("o", "e", "io", "a")) {
                out.add(stem + end);
            }
        }
        return out;
    }

    static TableDef addForeignKeyPair(TableDef t, int index, String column, String refColumn) {
        ForeignKeyDef f = t.foreignKeys().get(index);
        List<String> cols = new ArrayList<>(f.columns());
        List<String> refs = new ArrayList<>(f.refColumns());
        cols.add(column == null ? "" : column);
        refs.add(refColumn == null ? "" : refColumn);
        return changeForeignKeyAt(t, index, x -> x.withColumns(cols, refs));
    }

    static TableDef setForeignKeyPair(TableDef t, int index, int pair, String column, String refColumn) {
        ForeignKeyDef f = t.foreignKeys().get(index);
        List<String> cols = new ArrayList<>(f.columns());
        List<String> refs = new ArrayList<>(f.refColumns());
        while (refs.size() < cols.size()) {
            refs.add("");
        }
        if (column != null) {
            cols.set(pair, column);
        }
        if (refColumn != null) {
            refs.set(pair, refColumn);
        }
        return changeForeignKeyAt(t, index, x -> x.withColumns(cols, refs));
    }

    static TableDef removeForeignKeyPair(TableDef t, int index, int pair) {
        ForeignKeyDef f = t.foreignKeys().get(index);
        List<String> cols = new ArrayList<>(f.columns());
        List<String> refs = new ArrayList<>(f.refColumns());
        cols.remove(pair);
        if (pair < refs.size()) {
            refs.remove(pair);
        }
        return changeForeignKeyAt(t, index, x -> x.withColumns(cols, refs));
    }

    // ================================================================ opzioni

    /**
     * Charset/collation di una tabella <b>esistente</b>: il server ({@code DEFAULT CHARSET=… COLLATE=…}) cambia solo
     * il predefinito per le colonne future. Le colonne di testo già presenti che ereditavano il vecchio valore lo
     * ricevono qui in modo esplicito, così il modello dice il vero e dopo la rilettura non restano differenze.
     */
    static TableDef setCharset(TableDef t, TableDef original, String charset, String collation) {
        TableDef out = t.withCharset(charset, collation);
        if (original == null) {
            return out;
        }
        List<ColumnDef> cols = new ArrayList<>();
        for (ColumnDef c : out.columns()) {
            boolean existing = c.ordinalPosition() > 0;
            if (existing && SqlTypes.isText(c.dataType()) && !c.generated()) {
                String cs = c.charset() != null ? c.charset() : original.charset();
                String co = c.collation() != null ? c.collation() : original.collation();
                if (c.collation() == null && c.charset() != null && co != null
                        && !sameCharsetFamily(c.charset(), co)) {
                    co = null;
                }
                c = c.withCharset(cs, co);
            }
            cols.add(c);
        }
        return out.withColumns(cols);
    }

    private static boolean sameCharsetFamily(String charset, String collation) {
        return lower(collation).startsWith(lower(charset) + "_");
    }
}
