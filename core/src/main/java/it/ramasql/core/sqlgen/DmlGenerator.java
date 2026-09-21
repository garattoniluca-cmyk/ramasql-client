/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

import it.ramasql.core.data.RowChange;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Dalle modifiche pendenti del data-entry alle istruzioni DML. Puro: nessun accesso al server.
 *
 * <ul>
 *   <li><b>INSERT</b>: solo le colonne impostate; quelle lasciate vuote (DEFAULT, AUTO_INCREMENT) sono omesse.</li>
 *   <li><b>UPDATE</b>: solo le colonne cambiate; nessuna colonna cambiata → nessuna istruzione.</li>
 *   <li><b>WHERE</b> di UPDATE e DELETE: i valori <i>originali</i> della chiave primaria; senza PK, del primo indice
 *       UNIQUE con tutte le colonne NOT NULL. Senza nessuno dei due la tabella è di sola lettura
 *       ({@link #keyColumns} vuoto) e generare è un errore. Un valore nullo nel WHERE diventa {@code IS NULL},
 *       mai {@code = NULL}.</li>
 *   <li><b>Ordine</b>: prima tutte le DELETE, poi le UPDATE, poi le INSERT; dentro ogni gruppo, l'ordine ricevuto
 *       (quello della griglia). Eliminare per primo libera i valori unici che una riga modificata o nuova può
 *       voler riusare nella stessa conferma.</li>
 *   <li><b>Mai</b> istruzioni di transazione: la sessione è in autocommit (CLAUDE.md, regola 5).</li>
 * </ul>
 */
public final class DmlGenerator {

    /** Un'istruzione con la modifica da cui nasce: serve a marcare la riga salvata o in errore dopo l'esecuzione. */
    public record RowStatement(RowChange change, String sql) {
    }

    private DmlGenerator() {
    }

    /** Colonne che identificano una riga: PK, altrimenti il primo UNIQUE tutto NOT NULL; vuoto = sola lettura. */
    public static Optional<List<String>> keyColumns(TableDef table) {
        Optional<IndexDef> pk = table.primaryKey();
        if (pk.isPresent()) {
            return Optional.of(pk.get().columns());
        }
        return table.indexes().stream()
                .filter(IndexDef::isUnique)
                .filter(i -> i.columns().stream().allMatch(
                        c -> table.column(c).map(col -> !col.nullable()).orElse(false)))
                .map(IndexDef::columns)
                .findFirst();
    }

    /** Vero se le righe della tabella si possono modificare ed eliminare dalla griglia. */
    public static boolean isEditable(TableDef table) {
        return keyColumns(table).isPresent();
    }

    /** Le istruzioni nell'ordine d'esecuzione (DELETE, UPDATE, INSERT), senza {@code ;} finale. */
    public static List<String> generate(TableDef table, List<RowChange> changes) {
        return generateWithRows(table, changes).stream().map(RowStatement::sql).toList();
    }

    /** Come {@link #generate}, con origine e classe di rischio per la pipeline «anteprima SQL». */
    public static List<SqlStatement> statements(TableDef table, List<RowChange> changes, String origin) {
        return SqlStatement.listOf(generate(table, changes), origin);
    }

    /** Come {@link #generate}, ma ogni istruzione resta legata alla sua modifica. */
    public static List<RowStatement> generateWithRows(TableDef table, List<RowChange> changes) {
        List<RowStatement> out = new ArrayList<>();
        for (RowChange change : ordered(changes)) {
            String sql = switch (change.kind()) {
                case INSERT -> insert(table, change);
                case UPDATE -> change.newValues().isEmpty() ? null : update(table, change);
                case DELETE -> delete(table, change);
            };
            if (sql != null) {
                out.add(new RowStatement(change, sql));
            }
        }
        return out;
    }

    /** DELETE, poi UPDATE, poi INSERT; ordinamento stabile. */
    public static List<RowChange> ordered(List<RowChange> changes) {
        return changes.stream().sorted(Comparator.comparingInt(c -> switch (c.kind()) {
            case DELETE -> 0;
            case UPDATE -> 1;
            case INSERT -> 2;
        })).toList();
    }

    public static String insert(TableDef table, RowChange change) {
        Map<String, String> values = change.newValues();
        String columns = values.keySet().stream().map(SqlIdentifiers::quote).collect(Collectors.joining(", "));
        String literals = values.entrySet().stream()
                .map(e -> literal(table, e.getKey(), e.getValue())).collect(Collectors.joining(", "));
        return "INSERT INTO " + tableName(table) + " (" + columns + ") VALUES (" + literals + ")";
    }

    public static String update(TableDef table, RowChange change) {
        String set = change.newValues().entrySet().stream()
                .map(e -> SqlIdentifiers.quote(e.getKey()) + " = " + literal(table, e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
        return "UPDATE " + tableName(table) + " SET " + set + " WHERE " + where(table, change.originalValues());
    }

    public static String delete(TableDef table, RowChange change) {
        return "DELETE FROM " + tableName(table) + " WHERE " + where(table, change.originalValues());
    }

    /** Condizione che identifica la riga dai suoi valori originali, sulle colonne di {@link #keyColumns}. */
    public static String where(TableDef table, Map<String, String> originalRow) {
        List<String> key = keyColumns(table).orElseThrow(() -> new IllegalStateException(
                "La tabella " + table.name() + " non ha chiave primaria né UNIQUE non nullo: sola lettura"));
        return where(table, key, originalRow);
    }

    /** Condizione sulle colonne date; un valore nullo diventa {@code IS NULL}. */
    public static String where(TableDef table, List<String> columns, Map<String, String> row) {
        List<String> parts = new ArrayList<>();
        for (String column : columns) {
            String key = row.keySet().stream().filter(k -> k.equalsIgnoreCase(column)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Manca il valore originale di " + column));
            String value = row.get(key);
            parts.add(SqlIdentifiers.quote(column)
                    + (value == null ? " IS NULL" : " = " + literal(table, column, value)));
        }
        return String.join(" AND ", parts);
    }

    private static String literal(TableDef table, String column, String value) {
        ColumnDef def = table.column(column).orElse(null);
        return SqlLiterals.forColumn(value, def);
    }

    private static String tableName(TableDef table) {
        return SqlIdentifiers.qualified(table.catalog(), table.name());
    }
}
