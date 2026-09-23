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

/**
 * Analisi lessicale minima di un'istruzione, condivisa da {@link DdlTargets}, {@link ConfirmationPolicy} e
 * dall'esportazione del registro: parole, identificatori tra backtick, punti, stringhe; i commenti sono saltati
 * (quelli eseguibili {@code /*! … *}{@code /} valgono come codice). Ogni token ricorda dove sta nel testo.
 */
final class SqlLexer {

    enum Type { WORD, QUOTED_IDENT, STRING, DOT, OTHER }

    /**
     * @param type  tipo
     * @param text  per WORD il testo com'è; per QUOTED_IDENT il nome senza backtick; per STRING e OTHER il testo
     * @param start posizione del primo carattere nel testo originale
     * @param end   posizione dopo l'ultimo carattere
     */
    record Token(Type type, String text, int start, int end) {

        boolean isWord(String upperWord) {
            return type == Type.WORD && text.equalsIgnoreCase(upperWord);
        }

        /** Parola o identificatore tra backtick: può essere un nome. */
        boolean isName() {
            return type == Type.WORD || type == Type.QUOTED_IDENT;
        }

        String upper() {
            return text.toUpperCase(Locale.ROOT);
        }
    }

    private SqlLexer() {
    }

    static List<Token> tokenize(String sql) {
        List<Token> out = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char ch = sql.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
            } else if (ch == '#' || StatementSplitter.isDashComment(sql, i)) {
                i = StatementSplitter.endOfLine(sql, i);
            } else if (ch == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                if (StatementSplitter.isExecutableComment(sql, i)) {
                    i += sql.charAt(i + 2) == 'M' ? 4 : 3;
                    while (i < n && Character.isDigit(sql.charAt(i))) {
                        i++;
                    }
                } else {
                    int close = sql.indexOf("*/", i + 2);
                    i = close < 0 ? n : close + 2;
                }
            } else if (ch == '*' && i + 1 < n && sql.charAt(i + 1) == '/') {
                i += 2;                                   // chiusura di un commento eseguibile
            } else if (ch == '\'' || ch == '"') {
                int end = StatementSplitter.skipQuoted(sql, i, ch);
                out.add(new Token(Type.STRING, sql.substring(i, end), i, end));
                i = end;
            } else if (ch == '`') {
                int end = StatementSplitter.skipQuoted(sql, i, ch);
                String body = sql.substring(i + 1, Math.max(i + 1, end - 1)).replace("``", "`");
                out.add(new Token(Type.QUOTED_IDENT, body, i, end));
                i = end;
            } else if (ch == '.') {
                out.add(new Token(Type.DOT, ".", i, i + 1));
                i++;
            } else if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_'
                        || sql.charAt(i) == '$')) {
                    i++;
                }
                out.add(new Token(Type.WORD, sql.substring(start, i), start, i));
            } else {
                out.add(new Token(Type.OTHER, String.valueOf(ch), i, i + 1));
                i++;
            }
        }
        return out;
    }

    /** Indice del primo token che non è una parentesi aperta ({@code (SELECT …)}); {@code -1} se non ce n'è. */
    static int firstMeaningful(List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (!(t.type() == Type.OTHER && t.text().equals("("))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Nome eventualmente qualificato che comincia in {@code i}: {@code [catalogo, oggetto]} (catalogo {@code null}
     * se assente); {@code null} se in {@code i} non c'è un nome.
     */
    static String[] qualifiedName(List<Token> tokens, int i) {
        if (i < 0 || i >= tokens.size() || !tokens.get(i).isName()) {
            return null;
        }
        String first = tokens.get(i).text();
        if (i + 2 < tokens.size() && tokens.get(i + 1).type() == Type.DOT && tokens.get(i + 2).isName()) {
            return new String[] {first, tokens.get(i + 2).text()};
        }
        return new String[] {null, first};
    }

    /**
     * L'istruzione che il server esegue davvero quando {@code sql} è solo un involucro: MariaDB
     * {@code SET STATEMENT var=valore[, …] FOR <istr>}, MariaDB {@code ANALYZE [FORMAT=JSON] <DML>} (esegue il DML),
     * MySQL {@code EXPLAIN|DESCRIBE [FORMAT=…] ANALYZE [FORMAT=…] <istr>} (la esegue). Gli involucri annidati si
     * tolgono tutti. Senza involucro restituisce {@code sql} così com'è. {@code ANALYZE TABLE} e {@code EXPLAIN} senza
     * {@code ANALYZE} non sono involucri: non eseguono l'istruzione.
     */
    static String innermost(String sql) {
        String current = sql;
        for (int guard = 0; guard < 16; guard++) {
            int start = wrappedStart(tokenize(current));
            if (start < 0) {
                return current;
            }
            current = current.substring(start);
        }
        return current;
    }

    private static final java.util.Set<String> ANALYZABLE =
            java.util.Set.of("SELECT", "UPDATE", "DELETE", "INSERT", "REPLACE", "WITH", "VALUES");

    /** Posizione, nel testo, dell'istruzione avvolta; {@code -1} se non c'è un involucro. */
    private static int wrappedStart(List<Token> t) {
        int i = firstMeaningful(t);
        if (i < 0) {
            return -1;
        }
        Token first = t.get(i);
        if (first.isWord("SET") && i + 1 < t.size() && t.get(i + 1).isWord("STATEMENT")) {
            int depth = 0;
            for (int k = i + 2; k < t.size(); k++) {
                Token x = t.get(k);
                if (x.type() == Type.OTHER && x.text().equals("(")) {
                    depth++;
                } else if (x.type() == Type.OTHER && x.text().equals(")")) {
                    depth = Math.max(0, depth - 1);
                } else if (depth == 0 && x.isWord("FOR")) {
                    return k + 1 < t.size() ? t.get(k + 1).start() : -1;
                }
            }
            return -1;
        }
        if (first.isWord("ANALYZE")) {
            int j = skipFormat(t, i + 1);
            if (j < t.size() && (ANALYZABLE.contains(t.get(j).upper()) && t.get(j).type() == Type.WORD
                    || t.get(j).type() == Type.OTHER && t.get(j).text().equals("("))) {
                return t.get(j).start();
            }
            return -1;
        }
        if (first.isWord("EXPLAIN") || first.isWord("DESCRIBE") || first.isWord("DESC")) {
            int j = skipFormat(t, i + 1);
            if (j < t.size() && t.get(j).isWord("ANALYZE")) {
                j = skipFormat(t, j + 1);
                return j < t.size() ? t.get(j).start() : -1;
            }
        }
        return -1;
    }

    /** Salta {@code FORMAT = valore}. */
    private static int skipFormat(List<Token> t, int j) {
        if (j + 2 < t.size() && t.get(j).isWord("FORMAT") && t.get(j + 1).text().equals("=")) {
            return j + 3;
        }
        return j;
    }

    /** Salta {@code IF EXISTS} / {@code IF NOT EXISTS} a partire da {@code i}. */
    static int skipIfExists(List<Token> tokens, int i) {
        if (i < tokens.size() && tokens.get(i).isWord("IF")) {
            i++;
            if (i < tokens.size() && tokens.get(i).isWord("NOT")) {
                i++;
            }
            if (i < tokens.size() && tokens.get(i).isWord("EXISTS")) {
                i++;
            }
        }
        return i;
    }
}
