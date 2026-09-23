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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.app.Texts;

/**
 * I tipi proposti nella colonna «Tipo» (DESIGN §3.6): i più comuni, raggruppati in numerici, testo, data/ora e altri.
 * Il testo libero resta ammesso ({@code VARCHAR(45)} scritto a mano imposta anche la lunghezza).
 */
final class TypeChoices {

    /** Intestazione di un gruppo nell'elenco: non è un tipo e non si può scegliere. */
    record Header(String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Tipo letto dal testo della cella: nome in maiuscolo e argomenti tra parentesi ({@code null} se assenti). */
    record Parsed(String type, String args) {
    }

    private static final List<List<String>> GROUPS = List.of(
            List.of("INT", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT", "DECIMAL", "FLOAT", "DOUBLE", "BIT"),
            List.of("VARCHAR", "CHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT", "ENUM", "SET"),
            List.of("DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR"),
            List.of("BOOLEAN", "BLOB", "MEDIUMBLOB", "LONGBLOB", "BINARY", "VARBINARY", "JSON"));
    private static final List<String> GROUP_KEYS = List.of("tableeditor.types.numeric", "tableeditor.types.text",
            "tableeditor.types.datetime", "tableeditor.types.other");

    /** Tipi che non prendono argomenti: passando a uno di questi la lunghezza si svuota. */
    private static final Set<String> NO_ARGS = Set.of("TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT", "TINYBLOB", "BLOB",
            "MEDIUMBLOB", "LONGBLOB", "DATE", "YEAR", "JSON", "BOOLEAN", "BOOL");
    private static final Pattern TYPE = Pattern.compile("([A-Za-z][A-Za-z0-9_ ]*?)\\s*(?:\\((.*)\\))?");

    private TypeChoices() {
    }

    /** Le voci dell'elenco a discesa: intestazioni di gruppo seguite dai loro tipi. */
    static Object[] comboItems() {
        List<Object> items = new ArrayList<>();
        for (int g = 0; g < GROUPS.size(); g++) {
            items.add(new Header(Texts.get(GROUP_KEYS.get(g))));
            items.addAll(GROUPS.get(g));
        }
        return items.toArray();
    }

    /** Il tipo scritto o scelto; {@code null} se il testo non è un tipo (vuoto, intestazione, sintassi errata). */
    static Parsed parse(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        Matcher m = TYPE.matcher(text.strip());
        if (!m.matches()) {
            return null;
        }
        String type = m.group(1).strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        String args = m.group(2) == null || m.group(2).isBlank() ? null : m.group(2).strip();
        return new Parsed(type, args);
    }

    static boolean takesNoArgs(String type) {
        return NO_ARGS.contains(type);
    }

    /** Argomenti proposti quando si passa a un tipo che li richiede (come Workbench: VARCHAR(45)). */
    static String defaultArgs(String type) {
        return switch (type) {
            case "VARCHAR", "VARBINARY" -> "45";
            case "DECIMAL" -> "10,2";
            default -> null;
        };
    }

    /** Famiglia di tipi i cui argomenti hanno lo stesso significato: passando dall'uno all'altro si conservano. */
    static String family(String type) {
        return switch (type) {
            case "CHAR", "VARCHAR", "BINARY", "VARBINARY" -> "length";
            case "DECIMAL", "NUMERIC", "DEC" -> "decimal";
            case "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT" -> "integer";
            case "FLOAT", "DOUBLE" -> "approximate";
            case "DATETIME", "TIMESTAMP", "TIME" -> "fsp";
            case "ENUM", "SET" -> "values";
            default -> type;
        };
    }
}
