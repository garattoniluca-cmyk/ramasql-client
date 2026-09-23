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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import it.ramasql.app.Texts;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.FkPrecheck;
import it.ramasql.core.sqlgen.IndexPrecheck;
import it.ramasql.core.sqlgen.PrecheckWarning;
import it.ramasql.core.sqlgen.PrecheckWarning.Code;

/**
 * I controlli fatti <b>prima</b>, solo sul modello e sui metadati già noti (nessun accesso al server):
 * <ul>
 *   <li><b>errori evidenti</b>, che impediscono di applicare (nome vuoto, colonna ripetuta, indice senza colonne,
 *       chiave esterna senza tabella riferita o con colonne non appaiate…);</li>
 *   <li><b>avvisi</b>, che non bloccano: i controlli preventivi di {@link FkPrecheck} e {@link IndexPrecheck} e i
 *       possibili troncamenti quando si restringe il tipo di una colonna esistente (T5.5).</li>
 * </ul>
 */
final class Checks {

    enum Area { OPTIONS, COLUMNS, INDEXES, FOREIGN_KEYS }

    /**
     * @param area    scheda in cui mostrarlo
     * @param item    riga della scheda (colonna, indice o chiave esterna), −1 = la tabella
     * @param message testo in italiano
     * @param code    codice del controllo preventivo di core, {@code null} per gli altri
     * @param isError {@code true} = errore evidente che impedisce di applicare; {@code false} = avviso
     */
    record Problem(Area area, int item, String message, Code code, boolean isError) {
    }

    private static final Set<Code> BLOCKING = Set.of(Code.FK_COLUMN_COUNT_MISMATCH, Code.FK_COLUMN_NOT_FOUND,
            Code.INDEX_COLUMN_NOT_FOUND, Code.INDEX_INVALID_COLUMNS);
    private static final List<String> INTEGER_RANK = List.of("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT");
    /** Cifre del valore più grande di ciascun intero (con segno / senza). */
    private static final Map<String, Integer> INTEGER_DIGITS =
            Map.of("TINYINT", 3, "SMALLINT", 5, "MEDIUMINT", 8, "INT", 10, "BIGINT", 20);

    final List<Problem> errors = new ArrayList<>();
    final List<Problem> warnings = new ArrayList<>();

    private Checks() {
    }

    /**
     * @param original stato sul server ({@code null} = tabella nuova)
     * @param edited   stato voluto
     * @param parentOf la tabella riferita da una FK ({@code null} = non nota)
     */
    static Checks compute(TableDef original, TableDef edited, Function<ForeignKeyDef, TableDef> parentOf) {
        Checks c = new Checks();
        c.table(edited);
        c.columns(edited);
        c.truncation(original, edited);
        c.indexes(edited);
        c.foreignKeys(edited, parentOf);
        return c;
    }

    List<Problem> errors(Area area) {
        return errors.stream().filter(p -> p.area() == area).toList();
    }

    List<Problem> warnings(Area area) {
        return warnings.stream().filter(p -> p.area() == area).toList();
    }

    /** Errori e avvisi di una riga, errori prima. */
    List<Problem> of(Area area, int item) {
        List<Problem> out = new ArrayList<>();
        errors.stream().filter(p -> p.area() == area && p.item() == item).forEach(out::add);
        warnings.stream().filter(p -> p.area() == area && p.item() == item).forEach(out::add);
        return out;
    }

    List<Problem> truncationWarnings() {
        return warnings.stream().filter(p -> p.area() == Area.COLUMNS).toList();
    }

    private void error(Area area, int item, String key, Object... args) {
        errors.add(new Problem(area, item, Texts.get(key, args), null, true));
    }

    private void warning(Area area, int item, String key, Object... args) {
        warnings.add(new Problem(area, item, Texts.get(key, args), null, false));
    }

    // ================================================================ tabella e colonne

    private void table(TableDef t) {
        if (t.name().isBlank()) {
            error(Area.OPTIONS, -1, "tableeditor.error.tableName");
        }
        if (t.columns().isEmpty()) {
            error(Area.COLUMNS, -1, "tableeditor.error.noColumns");
        }
    }

    private void columns(TableDef t) {
        Set<String> seen = new HashSet<>();
        int autoIncrement = 0;
        for (int i = 0; i < t.columns().size(); i++) {
            ColumnDef c = t.columns().get(i);
            if (c.name().isBlank()) {
                error(Area.COLUMNS, i, "tableeditor.error.columnName", i + 1);
            } else if (!seen.add(Edits.lower(c.name()))) {
                error(Area.COLUMNS, i, "tableeditor.error.columnDuplicate", c.name());
            }
            String type = SqlTypes.canonical(c.dataType());
            if ((type.equals("VARCHAR") || type.equals("VARBINARY")) && c.typeArgs() == null) {
                error(Area.COLUMNS, i, "tableeditor.error.length", c.name(), type);
            }
            if ((type.equals("ENUM") || type.equals("SET")) && c.typeArgs() == null) {
                error(Area.COLUMNS, i, "tableeditor.error.values", c.name(), type);
            }
            if (c.autoIncrement()) {
                autoIncrement++;
                boolean inKey = t.indexes().stream().anyMatch(x -> !x.columns().isEmpty()
                        && x.columns().get(0).equalsIgnoreCase(c.name()));
                if (!inKey) {
                    error(Area.COLUMNS, i, "tableeditor.error.autoIncrementKey", c.name());
                }
            }
        }
        if (autoIncrement > 1) {
            error(Area.COLUMNS, -1, "tableeditor.error.autoIncrementOne");
        }
    }

    // ================================================================ troncamento (T5.5)

    /** Colonne esistenti ristrette: abbinate come fa {@code TableDiff} (prima per posizione, poi per nome). */
    private void truncation(TableDef original, TableDef edited) {
        if (original == null) {
            return;
        }
        for (int e = 0; e < edited.columns().size(); e++) {
            ColumnDef now = edited.columns().get(e);
            ColumnDef before = match(original, now);
            if (before == null || before.generated() || now.generated()) {
                continue;
            }
            String message = narrowing(before, now);
            if (message != null) {
                warnings.add(new Problem(Area.COLUMNS, e, message, null, false));
            }
            if (before.nullable() && !now.nullable() && !now.autoIncrement()) {
                warning(Area.COLUMNS, e, "tableeditor.warn.notNull", now.name());
            }
        }
    }

    private static ColumnDef match(TableDef original, ColumnDef c) {
        if (c.ordinalPosition() > 0) {
            for (ColumnDef o : original.columns()) {
                if (o.ordinalPosition() == c.ordinalPosition()) {
                    return o;
                }
            }
            return null;
        }
        return null;       // colonna nuova: non ha dati da troncare
    }

    private static String narrowing(ColumnDef before, ColumnDef now) {
        String from = SqlTypes.canonical(before.dataType());
        String to = SqlTypes.canonical(now.dataType());
        String fromText = describe(before);
        String toText = describe(now);
        if (fromText.equals(toText)) {
            return null;
        }
        long oldCapacity = textCapacity(from, before.typeArgs());
        long newCapacity = textCapacity(to, now.typeArgs());
        if (oldCapacity >= 0 && newCapacity >= 0) {
            return newCapacity < oldCapacity
                    ? Texts.get("tableeditor.warn.truncateText", now.name(), fromText, toText, newCapacity) : null;
        }
        if (SqlTypes.isNumeric(from) && SqlTypes.isNumeric(to)) {
            return numericNarrowing(before, now) ? Texts.get("tableeditor.warn.truncateNumber", now.name(), fromText,
                    toText) : null;
        }
        if (SqlTypes.isNumeric(from) && newCapacity >= 0) {
            return newCapacity < 21 ? Texts.get("tableeditor.warn.truncateText", now.name(), fromText, toText,
                    newCapacity) : null;
        }
        if (isTemporal(from) && newCapacity >= 0) {
            return newCapacity < 26 ? Texts.get("tableeditor.warn.truncateText", now.name(), fromText, toText,
                    newCapacity) : null;
        }
        if (from.equals(to) && (isTemporal(from) || from.equals("ENUM") || from.equals("SET"))) {
            return null;    // precisione dei secondi, elenco di valori: nessun troncamento da spiegare qui
        }
        return Texts.get("tableeditor.warn.convert", now.name(), fromText, toText);
    }

    private static String describe(ColumnDef c) {
        return c.fullType() + (c.unsigned() ? " UNSIGNED" : "");
    }

    private static boolean isTemporal(String type) {
        return Set.of("DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR").contains(type);
    }

    /** Caratteri (o byte) che il tipo può contenere; −1 se non è un tipo di testo/binario. */
    private static long textCapacity(String type, String args) {
        return switch (type) {
            case "CHAR", "BINARY" -> args == null ? 1 : parseLong(args);
            case "VARCHAR", "VARBINARY" -> args == null ? -1 : parseLong(args);
            case "TINYTEXT", "TINYBLOB" -> 255;
            case "TEXT", "BLOB" -> 65_535;
            case "MEDIUMTEXT", "MEDIUMBLOB" -> 16_777_215;
            case "LONGTEXT", "LONGBLOB", "JSON" -> 4_294_967_295L;
            default -> -1;
        };
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean numericNarrowing(ColumnDef before, ColumnDef now) {
        String from = SqlTypes.canonical(before.dataType());
        String to = SqlTypes.canonical(now.dataType());
        if (to.equals("DOUBLE")) {
            return false;
        }
        if (SqlTypes.isInteger(from) && SqlTypes.isInteger(to)) {
            int a = INTEGER_RANK.indexOf(from);
            int b = INTEGER_RANK.indexOf(to);
            if (!before.unsigned() && now.unsigned()) {
                return true;                         // i negativi non entrano più
            }
            if (before.unsigned() && !now.unsigned()) {
                return b <= a;                       // serve un tipo più grande per i valori alti
            }
            return b < a;
        }
        if (SqlTypes.isDecimal(from) && SqlTypes.isDecimal(to)) {
            int[] a = precision(before.typeArgs());
            int[] b = precision(now.typeArgs());
            return b[0] - b[1] < a[0] - a[1] || b[1] < a[1] || (!before.unsigned() && now.unsigned());
        }
        if (SqlTypes.isInteger(from) && SqlTypes.isDecimal(to)) {
            int[] b = precision(now.typeArgs());
            return b[0] - b[1] < INTEGER_DIGITS.get(from) || (!before.unsigned() && now.unsigned());
        }
        return !(from.equals("FLOAT") && to.equals("FLOAT"));
    }

    /** {@code DECIMAL(p,s)}: senza argomenti il server usa (10,0). */
    private static int[] precision(String args) {
        if (args == null) {
            return new int[] {10, 0};
        }
        String[] parts = args.split(",");
        int p = (int) Math.max(0, parseLong(parts[0]));
        int s = parts.length > 1 ? (int) Math.max(0, parseLong(parts[1])) : 0;
        return new int[] {p, s};
    }

    // ================================================================ indici

    private void indexes(TableDef t) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < t.indexes().size(); i++) {
            IndexDef index = t.indexes().get(i);
            if (index.name().isBlank()) {
                error(Area.INDEXES, i, "tableeditor.error.indexName", i + 1);
            } else if (!seen.add(Edits.lower(index.name()))) {
                error(Area.INDEXES, i, "tableeditor.error.indexDuplicate", index.name());
            }
            if (index.columns().isEmpty()) {
                error(Area.INDEXES, i, "tableeditor.error.indexColumns", index.name());
                continue;
            }
            for (PrecheckWarning w : IndexPrecheck.check(t, index)) {
                add(Area.INDEXES, i, w);
            }
        }
    }

    // ================================================================ chiavi esterne

    private void foreignKeys(TableDef t, Function<ForeignKeyDef, TableDef> parentOf) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < t.foreignKeys().size(); i++) {
            ForeignKeyDef fk = t.foreignKeys().get(i);
            String name = fk.name() == null ? "" : fk.name();
            if (name.isBlank()) {
                error(Area.FOREIGN_KEYS, i, "tableeditor.error.fkName", i + 1);
            } else if (!seen.add(Edits.lower(name))) {
                error(Area.FOREIGN_KEYS, i, "tableeditor.error.fkDuplicate", name);
            }
            if (fk.refTable().isBlank()) {
                error(Area.FOREIGN_KEYS, i, "tableeditor.error.fkTable", name);
                continue;
            }
            if (fk.columns().stream().anyMatch(String::isBlank) || fk.refColumns().stream().anyMatch(String::isBlank)) {
                error(Area.FOREIGN_KEYS, i, "tableeditor.error.fkPair", name);
                continue;
            }
            TableDef parent = parentOf.apply(fk);
            if (parent == null) {
                warning(Area.FOREIGN_KEYS, i, "tableeditor.warn.fkParentUnknown", fk.refTable());
                continue;
            }
            for (PrecheckWarning w : FkPrecheck.check(t, fk, parent)) {
                add(Area.FOREIGN_KEYS, i, w);
            }
        }
    }

    private void add(Area area, int item, PrecheckWarning w) {
        boolean blocking = BLOCKING.contains(w.code());
        (blocking ? errors : warnings).add(new Problem(area, item, w.message(), w.code(), blocking));
    }
}
