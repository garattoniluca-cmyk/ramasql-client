/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Classifica il rischio di un'istruzione SQL guardandone solo il testo (nessun accesso al server).
 *
 * <p>Regole: SELECT/SHOW/DESCRIBE/EXPLAIN/USE/SET → {@link RiskLevel#SAFE}; INSERT/REPLACE, UPDATE e DELETE
 * <b>con</b> WHERE, CREATE, ALTER non distruttivo, RENAME → {@link RiskLevel#MODIFIES}; DROP, TRUNCATE,
 * UPDATE/DELETE <b>senza</b> WHERE, {@code ALTER … DROP COLUMN/PARTITION}, {@code CREATE OR REPLACE TABLE}
 * → {@link RiskLevel#DESTRUCTIVE}. Stringhe, identificatori tra backtick e commenti sono ignorati: un
 * {@code WHERE} scritto lì dentro, o dentro una sottoquery, non conta. Un'istruzione non riconosciuta vale
 * MODIFIES (prudenza); un testo con più istruzioni prende il rischio più alto.
 */
public final class RiskClassifier {

    private static final Set<String> SAFE_VERBS = Set.of(
            "SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "USE", "SET", "HELP", "CHECKSUM", "VALUES", "TABLE");
    private static final Set<String> MAIN_VERBS = Set.of("SELECT", "UPDATE", "DELETE", "INSERT", "REPLACE");
    /** Ciò che dopo {@code DROP}, dentro un ALTER, non fa perdere dati. */
    private static final Set<String> HARMLESS_DROPS =
            Set.of("DEFAULT", "INDEX", "KEY", "PRIMARY", "FOREIGN", "CONSTRAINT", "CHECK");

    private RiskClassifier() {
    }

    public static RiskLevel classify(String sql) {
        List<StatementSplitter.SplitStatement> parts = StatementSplitter.split(sql);
        RiskLevel worst = RiskLevel.SAFE;
        for (StatementSplitter.SplitStatement part : parts) {
            worst = worst.max(classifyOne(part.text()));
        }
        return worst;
    }

    private static RiskLevel classifyOne(String statement) {
        List<Token> tokens = tokenize(statement);
        int first = 0;
        while (first < tokens.size() && tokens.get(first).isOpenParen()) {
            first++;
        }
        if (first >= tokens.size()) {
            return RiskLevel.SAFE;
        }
        String verb = tokens.get(first).text;
        if (verb.equals("WITH")) {
            verb = mainVerbAfterWith(tokens, first);
        }
        if (SAFE_VERBS.contains(verb)) {
            return RiskLevel.SAFE;
        }
        return switch (verb) {
            case "UPDATE", "DELETE" -> hasTopLevelWhere(tokens, first) ? RiskLevel.MODIFIES : RiskLevel.DESTRUCTIVE;
            case "DROP", "TRUNCATE" -> RiskLevel.DESTRUCTIVE;
            case "ALTER" -> alterRisk(tokens);
            case "CREATE" -> createRisk(tokens, first);
            default -> RiskLevel.MODIFIES;
        };
    }

    /** Dopo le CTE, il primo verbo a profondità zero decide ({@code WITH … DELETE} esiste). */
    private static String mainVerbAfterWith(List<Token> tokens, int withIndex) {
        int base = tokens.get(withIndex).depth;
        for (int i = withIndex + 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.depth == base && MAIN_VERBS.contains(t.text)) {
                return t.text;
            }
        }
        return "SELECT";
    }

    private static boolean hasTopLevelWhere(List<Token> tokens, int verbIndex) {
        int base = tokens.get(verbIndex).depth;
        for (int i = verbIndex + 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.depth == base && t.text.equals("WHERE")) {
                return true;
            }
        }
        return false;
    }

    private static RiskLevel alterRisk(List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i).text;
            if (t.equals("TRUNCATE") || t.equals("DISCARD")) {
                return RiskLevel.DESTRUCTIVE;
            }
            if (t.equals("DROP")) {
                String next = i + 1 < tokens.size() ? tokens.get(i + 1).text : "";
                if (!HARMLESS_DROPS.contains(next)) {
                    return RiskLevel.DESTRUCTIVE;   // DROP COLUMN, DROP PARTITION, DROP `colonna`
                }
            }
        }
        return RiskLevel.MODIFIES;
    }

    private static RiskLevel createRisk(List<Token> tokens, int first) {
        boolean orReplace = first + 2 < tokens.size()
                && tokens.get(first + 1).text.equals("OR") && tokens.get(first + 2).text.equals("REPLACE");
        if (orReplace) {
            for (int i = first + 3; i < Math.min(tokens.size(), first + 5); i++) {
                if (tokens.get(i).text.equals("TABLE")) {
                    return RiskLevel.DESTRUCTIVE;   // MariaDB: sostituisce la tabella con i suoi dati
                }
            }
        }
        return RiskLevel.MODIFIES;
    }

    // ---------------------------------------------------------------- analisi lessicale minima

    private record Token(String text, int depth) {
        boolean isOpenParen() {
            return text.equals("(");
        }
    }

    /** Parole (in maiuscolo) e parentesi aperte, con la profondità di parentesi; il resto non serve. */
    private static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int n = sql.length();
        int depth = 0;
        int i = 0;
        while (i < n) {
            char ch = sql.charAt(i);
            if (ch == '#' || StatementSplitter.isDashComment(sql, i)) {
                i = StatementSplitter.endOfLine(sql, i);
            } else if (ch == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                if (StatementSplitter.isExecutableComment(sql, i)) {
                    i += sql.charAt(i + 2) == 'M' ? 4 : 3;
                    while (i < n && Character.isDigit(sql.charAt(i))) {
                        i++;                         // numero di versione: /*!50003
                    }
                } else {
                    int close = sql.indexOf("*/", i + 2);
                    i = close < 0 ? n : close + 2;
                }
            } else if (ch == '\'' || ch == '"') {
                i = StatementSplitter.skipQuoted(sql, i, ch);
            } else if (ch == '`') {
                i = StatementSplitter.skipQuoted(sql, i, ch);
                tokens.add(new Token("`", depth));   // segnaposto di un identificatore
            } else if (ch == '(') {
                tokens.add(new Token("(", depth));
                depth++;
                i++;
            } else if (ch == ')') {
                depth = Math.max(0, depth - 1);
                i++;
            } else if (Character.isLetter(ch) || ch == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_'
                        || sql.charAt(i) == '$')) {
                    i++;
                }
                boolean variable = start > 0 && sql.charAt(start - 1) == '@';
                if (!variable) {
                    tokens.add(new Token(sql.substring(start, i).toUpperCase(Locale.ROOT), depth));
                }
            } else {
                i++;
            }
        }
        return tokens;
    }
}
