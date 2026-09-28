/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

import java.util.Locale;

import it.ramasql.core.sqlgen.SqlLiterals;

/**
 * I valori letti dal server scritti come letterali SQL nel dump. Il valore arriva dall'esecutore già nella forma del
 * server — testo per numeri, date e testi, byte per i tipi binari — e si scrive senza reinterpretarlo:
 * <ul>
 *   <li>{@code NULL} → {@code NULL};</li>
 *   <li>numeri ({@code INT}, {@code DECIMAL}, {@code FLOAT}, {@code YEAR}…) → senza apici, <b>così come li scrive il
 *       server</b> ({@code 12.50} resta {@code 12.50});</li>
 *   <li>tipi binari ({@code BLOB}, {@code BINARY}, {@code BIT}, geometrie) → esadecimale {@code X'…'};</li>
 *   <li>{@code TIMESTAMP} → letto come {@code UNIX_TIMESTAMP(colonna)} (secondi dal 1970, indipendenti dal fuso della
 *       sessione) e scritto come data e ora <b>UTC</b> tra apici; il dump imposta {@code TIME_ZONE = '+00:00'} per il
 *       ripristino, così l'istante resta lo stesso anche fra PC con fusi diversi e nell'ora ripetuta del cambio d'ora
 *       (come fa {@code mysqldump}); 0 → {@code '0000-00-00 00:00:00'};</li>
 *   <li>{@code FLOAT} → letto come {@code CAST(colonna AS DOUBLE)}: il server scrive i FLOAT con 6 cifre
 *       ({@code 1234567} diventerebbe {@code 1234570}), il DOUBLE è il valore esatto che, riscritto in un FLOAT, torna
 *       identico;</li>
 *   <li>tutto il resto (testi, date anche «zero» {@code 0000-00-00}, ENUM, SET, JSON) → tra apici, con apici raddoppiati
 *       e barre rovesciate, a-capo, NUL e Ctrl+Z come sequenze ({@link SqlLiterals#string}). Stringa vuota → {@code ''}.
 *       Emoji e accenti passano invariati (il dump si scrive in UTF-8 con {@code SET NAMES utf8mb4}).</li>
 * </ul>
 * Il dump imposta all'inizio un {@code sql_mode} senza {@code NO_BACKSLASH_ESCAPES}, che darebbe un altro significato
 * alle barre rovesciate.
 */
public final class DumpLiterals {

    /** Come si scrive il valore di una colonna. */
    public enum Kind {
        /** Numero, senza apici. */
        NUMBER,
        /** Byte, in esadecimale. */
        BINARY,
        /** Testo, date e il resto, tra apici. */
        TEXT,
        /** {@code TIMESTAMP}: secondi dal 1970 ({@code UNIX_TIMESTAMP}), scritti come data e ora UTC. */
        TIMESTAMP_UTC
    }

    private DumpLiterals() {
    }

    /**
     * Il tipo di scrittura dal nome del tipo SQL riportato dal server ({@code INT UNSIGNED}, {@code decimal},
     * {@code BLOB}, {@code BIT}…).
     */
    public static Kind kindOf(String typeName) {
        String t = typeName == null ? "" : typeName.trim().toUpperCase(Locale.ROOT);
        int space = t.indexOf(' ');
        if (space > 0) {
            t = t.substring(0, space);
        }
        int paren = t.indexOf('(');
        if (paren > 0) {
            t = t.substring(0, paren);
        }
        return switch (t) {
            case "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "DEC",
                "FLOAT", "DOUBLE", "REAL", "YEAR", "BOOLEAN", "BOOL" -> Kind.NUMBER;
            case "BINARY", "VARBINARY", "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB", "BIT", "GEOMETRY", "POINT",
                "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION",
                "GEOMCOLLECTION" -> Kind.BINARY;
            default -> Kind.TEXT;
        };
    }

    /** Il tipo di scrittura di una colonna, dal tipo SQL ({@code TIMESTAMP} ha la sua). */
    public static Kind kindOfColumn(String typeName) {
        return baseType(typeName).equals("TIMESTAMP") ? Kind.TIMESTAMP_UTC : kindOf(typeName);
    }

    /** Come si legge la colonna nella {@code SELECT} del dump (vedi la documentazione della classe). */
    public static String selectExpression(String quotedColumn, String typeName) {
        return switch (baseType(typeName)) {
            case "TIMESTAMP" -> "UNIX_TIMESTAMP(" + quotedColumn + ")";
            case "FLOAT" -> "CAST(" + quotedColumn + " AS DOUBLE)";
            default -> quotedColumn;
        };
    }

    private static String baseType(String typeName) {
        String t = typeName == null ? "" : typeName.trim().toUpperCase(Locale.ROOT);
        int cut = t.length();
        for (char c : new char[] {' ', '('}) {
            int i = t.indexOf(c);
            if (i > 0) {
                cut = Math.min(cut, i);
            }
        }
        return t.substring(0, cut);
    }

    /** Secondi dal 1970 (con decimali) → {@code 'aaaa-mm-gg hh:mm:ss[.ffffff]'} in UTC; 0 → la data «zero». */
    static String timestampUtc(String epoch) {
        String s = epoch.trim();
        java.math.BigDecimal v = new java.math.BigDecimal(s);
        if (v.signum() == 0) {
            return SqlLiterals.string("0000-00-00 00:00:00");
        }
        int dot = s.indexOf('.');
        String fraction = dot < 0 ? "" : s.substring(dot + 1);
        long seconds = v.setScale(0, java.math.RoundingMode.FLOOR).longValueExact();
        String base = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochSecond(seconds));
        return SqlLiterals.string(fraction.isEmpty() ? base : base + "." + fraction);
    }

    /**
     * @param value {@code null}, {@link String} (forma del server) o {@code byte[]}
     */
    public static String literal(Object value, Kind kind) {
        if (value == null) {
            return SqlLiterals.NULL;
        }
        if (value instanceof byte[] bytes) {
            return kind == Kind.TEXT ? SqlLiterals.string(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                    : SqlLiterals.hex(bytes);
        }
        String s = value.toString();
        return switch (kind) {
            case NUMBER -> SqlLiterals.isNumber(s) ? s.trim() : SqlLiterals.string(s);
            case BINARY -> SqlLiterals.hex(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            case TEXT -> SqlLiterals.string(s);
            case TIMESTAMP_UTC -> SqlLiterals.isNumber(s) ? timestampUtc(s) : SqlLiterals.string(s);
        };
    }
}
