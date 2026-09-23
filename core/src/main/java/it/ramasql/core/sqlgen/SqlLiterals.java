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

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Letterali SQL. Le stringhe usano l'apice raddoppiato ({@code ''}) e il backslash raddoppiato ({@code \\}):
 * forma corretta con l'{@code sql_mode} predefinito dei due server (senza {@code NO_BACKSLASH_ESCAPES}).
 * A-capo e NUL sono scritti come sequenze ({@code \n}, {@code \r}, {@code \0}) per tenere ogni istruzione
 * leggibile nel registro; tutto il resto — accenti ed emoji compresi — passa invariato.
 */
public final class SqlLiterals {

    public static final String NULL = "NULL";

    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final java.util.regex.Pattern HEX = java.util.regex.Pattern.compile("[0-9a-fA-F]+");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private SqlLiterals() {
    }

    /** {@code l'ora} → {@code 'l''ora'}; {@code null} → {@code NULL}; stringa vuota → {@code ''}. */
    public static String string(String value) {
        if (value == null) {
            return NULL;
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('\'');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\'' -> out.append("''");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\0' -> out.append("\\0");
                case '' -> out.append("\\Z");
                default -> out.append(ch);
            }
        }
        return out.append('\'').toString();
    }

    /** Numero senza apici; i {@link BigDecimal} in forma piana ({@code 1E+3} → {@code 1000}). */
    public static String number(Number value) {
        if (value == null) {
            return NULL;
        }
        return value instanceof BigDecimal d ? d.toPlainString() : value.toString();
    }

    /** DECIMAL senza apici e senza notazione esponenziale, con la scala conservata: {@code 12.50}. */
    public static String decimal(BigDecimal value) {
        return number(value);
    }

    public static String bool(boolean value) {
        return value ? "1" : "0";
    }

    /** {@code '2026-09-21'}. */
    public static String date(LocalDate value) {
        return value == null ? NULL : "'" + DATE.format(value) + "'";
    }

    /** {@code '2026-09-21 08:30:00'}, con i microsecondi solo se presenti. */
    public static String dateTime(LocalDateTime value) {
        if (value == null) {
            return NULL;
        }
        return "'" + DATE.format(value) + " " + timeText(value.toLocalTime()) + "'";
    }

    /** {@code '08:30:00'}. */
    public static String time(LocalTime value) {
        return value == null ? NULL : "'" + timeText(value) + "'";
    }

    private static String timeText(LocalTime t) {
        String base = TIME.format(t);
        int micros = t.getNano() / 1000;
        return micros == 0 ? base : base + "." + String.format(Locale.ROOT, "%06d", micros);
    }

    /** BLOB esadecimale: {@code X'CAFE00'}; vettore vuoto → {@code X''}. */
    public static String hex(byte[] value) {
        return value == null ? NULL : "X'" + HexFormat.of().withUpperCase().formatHex(value) + "'";
    }

    /** Sceglie il letterale dal tipo Java del valore. */
    public static String of(Object value) {
        return switch (value) {
            case null -> NULL;
            case String s -> string(s);
            case Boolean b -> bool(b);
            case Number nr -> number(nr);
            case LocalDate d -> date(d);
            case LocalDateTime dt -> dateTime(dt);
            case LocalTime t -> time(t);
            case byte[] bytes -> hex(bytes);
            default -> string(value.toString());
        };
    }

    /**
     * Dal testo di una cella della griglia al letterale adatto alla colonna: {@code null} → {@code NULL};
     * colonne numeriche con un numero valido → senza apici; colonne booleane {@code true/false} → {@code 1/0};
     * tutto il resto (testo, date, ENUM…) → stringa tra apici.
     */
    public static String forColumn(String text, ColumnDef column) {
        if (text == null) {
            return NULL;
        }
        if (column != null && SqlTypes.isBoolean(column)) {
            if (text.equalsIgnoreCase("true")) {
                return "1";
            }
            if (text.equalsIgnoreCase("false")) {
                return "0";
            }
        }
        if (column != null && SqlTypes.isNumeric(column.dataType()) && isNumber(text)) {
            return text.trim();
        }
        if (column != null && SqlTypes.isBinary(column.dataType())) {
            return hexLiteral(text);
        }
        return string(text);
    }

    /**
     * Il testo di una cella binaria — come lo scrive la griglia, {@code 0x48656C6C6F} — torna al letterale
     * {@code X'48656C6C6F'}. Un testo che non è esadecimale valido si scrive fra apici: sarà il server a rifiutarlo,
     * con il suo messaggio, invece di finire scritto come byte sbagliati.
     */
    static String hexLiteral(String text) {
        String t = text.trim();
        if (t.regionMatches(true, 0, "0x", 0, 2)) {
            t = t.substring(2);
        } else if (t.regionMatches(true, 0, "X'", 0, 2) && t.endsWith("'")) {
            t = t.substring(2, t.length() - 1);
        }
        if (t.isEmpty()) {
            return "X''";
        }
        return t.length() % 2 == 0 && HEX.matcher(t).matches() ? "X'" + t.toUpperCase(java.util.Locale.ROOT) + "'"
                : string(text);
    }

    /** Vero se il testo è un numero SQL valido (intero, decimale con il punto, esponenziale). */
    public static boolean isNumber(String text) {
        return text != null && NUMBER.matcher(text.trim()).matches();
    }
}
