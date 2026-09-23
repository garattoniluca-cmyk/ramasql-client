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

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Indice che v1 <b>non modifica</b> (FULLTEXT, SPATIAL, su prefisso, su espressione, con colonne in ordine
 * {@code DESC}): resta fra gli {@link TableDef#advancedElements() elementi avanzati}, intatto per i generatori, ma si
 * mostra nel navigatore, in sola lettura, con un'etichetta. Si ricava dalla riga di {@code SHOW CREATE TABLE}
 * conservata ({@link #fromCreateLine}).
 *
 * @param name       nome ({@code PRIMARY} per la chiave primaria)
 * @param unique     PRIMARY o UNIQUE
 * @param features   perché è in sola lettura (almeno una)
 * @param columns    le colonne com'è scritte nel server, senza backtick: {@code titolo(10)}, {@code anno DESC},
 *                   {@code (lower(titolo))}
 * @param definition la riga di {@code SHOW CREATE TABLE}, così com'è
 */
public record ReadOnlyIndex(String name, boolean unique, Set<Feature> features, String columns, String definition) {

    /** Che cosa lo rende non modificabile in v1. */
    public enum Feature { FULLTEXT, SPATIAL, PREFIX, EXPRESSION, DESCENDING }

    private static final Pattern PREFIX = Pattern.compile("`(?:[^`]|``)*`\\s*\\(\\s*\\d+\\s*\\)");
    private static final Pattern DESC = Pattern.compile("(?i)\\bDESC\\b");

    public ReadOnlyIndex {
        Objects.requireNonNull(name, "name");
        features = Set.copyOf(features);
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(definition, "definition");
    }

    /**
     * Dalla riga di {@code SHOW CREATE TABLE} di un indice ({@code KEY}, {@code UNIQUE KEY}, {@code FULLTEXT KEY},
     * {@code SPATIAL KEY}, {@code PRIMARY KEY}); vuoto per qualunque altra riga (colonne, CHECK, partizioni, FK) o per
     * un indice senza nessuna delle caratteristiche di {@link Feature}.
     */
    public static Optional<ReadOnlyIndex> fromCreateLine(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String l = line.strip();
        if (l.endsWith(",")) {
            l = l.substring(0, l.length() - 1);
        }
        String upper = l.toUpperCase(Locale.ROOT);
        EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
        boolean unique;
        int rest;
        if (upper.startsWith("PRIMARY KEY")) {
            unique = true;
            rest = "PRIMARY KEY".length();
        } else if (upper.startsWith("UNIQUE KEY") || upper.startsWith("UNIQUE INDEX")) {
            unique = true;
            rest = upper.indexOf(' ', 7) + 1;
        } else if (upper.startsWith("FULLTEXT KEY") || upper.startsWith("FULLTEXT INDEX")) {
            unique = false;
            features.add(Feature.FULLTEXT);
            rest = upper.indexOf(' ', 9) + 1;
        } else if (upper.startsWith("SPATIAL KEY") || upper.startsWith("SPATIAL INDEX")) {
            unique = false;
            features.add(Feature.SPATIAL);
            rest = upper.indexOf(' ', 8) + 1;
        } else if (upper.startsWith("KEY ") || upper.startsWith("INDEX ")) {
            unique = false;
            rest = upper.indexOf(' ') + 1;
        } else {
            return Optional.empty();
        }
        String tail = l.substring(rest).strip();
        String name = "PRIMARY";
        if (tail.startsWith("`")) {
            int end = closingTick(tail);
            name = tail.substring(1, end).replace("``", "`");
            tail = tail.substring(end + 1).strip();
        }
        String columns = "";
        if (tail.startsWith("(")) {
            int close = closingParen(tail);
            columns = tail.substring(1, close < 0 ? tail.length() : close).strip();
        }
        String code = outsideTicks(columns);
        if (code.startsWith("(") || code.contains(",(") || code.contains(", (")) {
            features.add(Feature.EXPRESSION);
        }
        if (PREFIX.matcher(columns).find()) {
            features.add(Feature.PREFIX);
        }
        if (DESC.matcher(code).find()) {
            features.add(Feature.DESCENDING);
        }
        if (features.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReadOnlyIndex(name, unique, features, columns.replace("`", ""), line.strip()));
    }

    private static int closingTick(String s) {
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) == '`') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '`') {
                    i++;
                } else {
                    return i;
                }
            }
        }
        return s.length() - 1;
    }

    private static int closingParen(String s) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '`' || ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** I nomi tra backtick sostituiti da {@code x}: per cercare parole chiave senza farsi ingannare dai nomi. */
    private static String outsideTicks(String s) {
        StringBuilder out = new StringBuilder();
        boolean in = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '`') {
                if (in && i + 1 < s.length() && s.charAt(i + 1) == '`') {
                    i++;
                    continue;
                }
                in = !in;
                if (!in) {
                    out.append('x');
                }
            } else if (!in) {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
