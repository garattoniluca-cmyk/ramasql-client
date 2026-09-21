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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Conoscenza dei tipi di colonna di MariaDB/MySQL condivisa da generatori, controlli e validazione. */
public final class SqlTypes {

    private static final Set<String> INTEGER = Set.of("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT");
    private static final Set<String> APPROXIMATE = Set.of("FLOAT", "DOUBLE");
    private static final Set<String> TEXT =
            Set.of("CHAR", "VARCHAR", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT", "ENUM", "SET");

    private SqlTypes() {
    }

    /** Nome canonico del tipo: sinonimi ricondotti a quello che il server riporta (INTEGER→INT, BOOLEAN→TINYINT, NUMERIC→DECIMAL…). */
    public static String canonical(String dataType) {
        String t = dataType == null ? "" : dataType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (t) {
            case "INTEGER" -> "INT";
            case "BOOL", "BOOLEAN" -> "TINYINT";
            case "DEC", "NUMERIC", "FIXED" -> "DECIMAL";
            case "REAL", "DOUBLE PRECISION" -> "DOUBLE";
            case "CHARACTER" -> "CHAR";
            default -> t;
        };
    }

    public static boolean isInteger(String dataType) {
        return INTEGER.contains(canonical(dataType));
    }

    public static boolean isDecimal(String dataType) {
        return canonical(dataType).equals("DECIMAL");
    }

    public static boolean isApproximate(String dataType) {
        return APPROXIMATE.contains(canonical(dataType));
    }

    /** Interi, DECIMAL, FLOAT/DOUBLE, YEAR: i valori si scrivono senza apici. */
    public static boolean isNumeric(String dataType) {
        String t = canonical(dataType);
        return INTEGER.contains(t) || APPROXIMATE.contains(t) || t.equals("DECIMAL") || t.equals("YEAR");
    }

    /** Tipi con charset e collation. */
    public static boolean isText(String dataType) {
        return TEXT.contains(canonical(dataType));
    }

    /** BOOL/BOOLEAN oppure TINYINT(1), la forma in cui il server li conserva. */
    public static boolean isBoolean(ColumnDef column) {
        String raw = column.dataType();
        return raw.equals("BOOL") || raw.equals("BOOLEAN")
                || (raw.equals("TINYINT") && "1".equals(column.typeArgs()));
    }

    /** Argomenti canonici: spazi tolti fuori dagli apici; {@code "1"} per BOOLEAN; {@code null} se assenti. */
    public static String canonicalArgs(ColumnDef column) {
        if (column.dataType().equals("BOOL") || column.dataType().equals("BOOLEAN")) {
            return "1";
        }
        String args = column.typeArgs();
        if (args == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < args.length(); i++) {
            char ch = args.charAt(i);
            if (ch == '\'') {
                quoted = !quoted;
            }
            if (quoted || !Character.isWhitespace(ch)) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /**
     * Stesso tipo agli occhi del server: sinonimi equivalenti, UNSIGNED uguale e — per gli interi — larghezza
     * di visualizzazione ignorata se uno dei due non la dichiara (MySQL ≥ 8.0.19 riporta {@code int}, MariaDB {@code int(11)}).
     */
    public static boolean sameType(ColumnDef a, ColumnDef b) {
        if (!canonical(a.dataType()).equals(canonical(b.dataType())) || a.unsigned() != b.unsigned()) {
            return false;
        }
        String argsA = canonicalArgs(a);
        String argsB = canonicalArgs(b);
        if (isInteger(a.dataType()) && (argsA == null || argsB == null)) {
            return true;
        }
        return Objects.equals(argsA, argsB);
    }

    /** Valori di un ENUM/SET dagli argomenti {@code 'a','b','l''altro'} (apici raddoppiati e backslash risolti). */
    public static List<String> parseQuotedList(String typeArgs) {
        List<String> values = new ArrayList<>();
        if (typeArgs == null) {
            return values;
        }
        int i = 0;
        int n = typeArgs.length();
        while (i < n) {
            if (typeArgs.charAt(i) != '\'') {
                i++;
                continue;
            }
            StringBuilder value = new StringBuilder();
            i++;
            while (i < n) {
                char ch = typeArgs.charAt(i);
                if (ch == '\'' && i + 1 < n && typeArgs.charAt(i + 1) == '\'') {
                    value.append('\'');
                    i += 2;
                } else if (ch == '\\' && i + 1 < n) {
                    value.append(typeArgs.charAt(i + 1));
                    i += 2;
                } else if (ch == '\'') {
                    i++;
                    break;
                } else {
                    value.append(ch);
                    i++;
                }
            }
            values.add(value.toString());
        }
        return values;
    }
}
