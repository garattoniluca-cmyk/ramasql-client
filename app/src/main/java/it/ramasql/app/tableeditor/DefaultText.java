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

import java.util.regex.Pattern;

import it.ramasql.core.metadata.ColumnDefault;

/**
 * Il valore predefinito come si scrive nella cella «Default», in modo semplice per gli studenti:
 * <ul>
 *   <li>cella vuota = nessun default; {@code NULL} = {@code DEFAULT NULL};</li>
 *   <li>{@code CURRENT_TIMESTAMP}, {@code NOW()}, {@code CURRENT_DATE}… = espressione; un testo tra parentesi,
 *       es. {@code (uuid())}, = espressione;</li>
 *   <li>tutto il resto è un valore letterale, scritto senza apici ({@code 0}, {@code italiana}); tra apici solo se
 *       serve distinguerlo ({@code 'NULL'} è la parola, {@code ''} la stringa vuota).</li>
 * </ul>
 */
final class DefaultText {

    private static final Pattern NOW = Pattern.compile(
            "(?i)(CURRENT_TIMESTAMP|LOCALTIME|LOCALTIMESTAMP)(\\s*\\(\\s*\\d*\\s*\\))?"
                    + "|(?i)(NOW|CURRENT_DATE|CURDATE|CURRENT_TIME|CURTIME|UTC_TIMESTAMP)\\s*\\(\\s*\\d*\\s*\\)"
                    + "|(?i)CURRENT_DATE|CURRENT_TIME");

    private DefaultText() {
    }

    static ColumnDefault parse(String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) {
            return ColumnDefault.NONE;
        }
        if (t.equalsIgnoreCase("NULL")) {
            return ColumnDefault.NULL_VALUE;
        }
        if (NOW.matcher(t).matches()) {
            return ColumnDefault.expression(t);
        }
        if (t.length() >= 2 && t.startsWith("(") && t.endsWith(")")) {
            return ColumnDefault.expression(t.substring(1, t.length() - 1).strip());
        }
        if (t.length() >= 2 && t.startsWith("'") && t.endsWith("'")) {
            return ColumnDefault.literal(t.substring(1, t.length() - 1).replace("''", "'"));
        }
        return ColumnDefault.literal(t);
    }

    static String format(ColumnDefault d) {
        return switch (d.kind()) {
            case NONE -> "";
            case NULL -> "NULL";
            case EXPRESSION -> NOW.matcher(d.value()).matches() ? d.value() : "(" + d.value() + ")";
            case LITERAL -> {
                String v = d.value();
                yield parse(v).equals(d) ? v : "'" + v.replace("'", "''") + "'";
            }
        };
    }
}
