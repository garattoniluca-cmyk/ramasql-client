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

/**
 * Separatore di istruzioni di uno script SQL, con le regole del client {@code mysql}.
 *
 * <ul>
 *   <li>il separatore ({@code ;} o quello scelto con {@code DELIMITER}) non vale dentro stringhe
 *       ({@code '…'}, {@code "…"}, con {@code \'} e apici raddoppiati), identificatori tra backtick e commenti;</li>
 *   <li>commenti: {@code -- } (con spazio o fine riga dopo i due trattini), {@code #}, {@code /* … *}{@code /};
 *       i commenti eseguibili {@code /*! … *}{@code /} e {@code /*M! … *}{@code /} sono codice e fanno parte dell'istruzione;</li>
 *   <li>{@code DELIMITER xx} è riconosciuto solo a inizio istruzione, vale fino a fine riga e non è un'istruzione;</li>
 *   <li>i commenti prima di un'istruzione e dopo la sua fine non ne fanno parte; quelli in mezzo sì;</li>
 *   <li>script vuoto o di soli commenti → nessuna istruzione; l'ultima istruzione può non avere il separatore.</li>
 * </ul>
 */
public final class StatementSplitter {

    /**
     * Un'istruzione trovata nello script.
     *
     * @param text        testo senza separatore finale e senza spazi attorno
     * @param startOffset posizione del primo carattere nello script
     * @param endOffset   posizione dopo l'ultimo carattere (esclusa)
     * @param line        riga (da 1) in cui l'istruzione comincia
     */
    public record SplitStatement(String text, int startOffset, int endOffset, int line) {
    }

    private static final String DELIMITER_KEYWORD = "DELIMITER";

    private StatementSplitter() {
    }

    public static List<SplitStatement> split(String script) {
        List<SplitStatement> out = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return out;
        }
        final int n = script.length();
        String delimiter = ";";
        int start = -1;      // inizio dell'istruzione in corso, -1 = nessuna
        int codeEnd = -1;    // fine dell'ultimo carattere di codice dell'istruzione in corso
        int i = 0;
        while (i < n) {
            char ch = script.charAt(i);

            if (start < 0 && Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            // DELIMITER: solo a inizio istruzione
            if (start < 0 && isDelimiterCommand(script, i)) {
                int eol = endOfLine(script, i);
                String wanted = script.substring(i + DELIMITER_KEYWORD.length(), eol).trim();
                if (!wanted.isEmpty()) {
                    delimiter = wanted;
                }
                i = eol;
                continue;
            }
            // commenti
            if (ch == '#' || isDashComment(script, i)) {
                i = endOfLine(script, i);
                continue;
            }
            if (ch == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                if (isExecutableComment(script, i)) {
                    if (start < 0) {
                        start = i;
                    }
                    i += 3;          // il contenuto è codice: si continua la scansione normale
                    codeEnd = i;
                    continue;
                }
                int close = script.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
                continue;
            }
            // separatore
            if (script.startsWith(delimiter, i)) {
                if (start >= 0) {
                    out.add(statement(script, start, codeEnd));
                    start = -1;
                }
                i += delimiter.length();
                continue;
            }
            // stringhe e identificatori tra backtick
            if (ch == '\'' || ch == '"' || ch == '`') {
                if (start < 0) {
                    start = i;
                }
                i = skipQuoted(script, i, ch);
                codeEnd = i;
                continue;
            }
            // codice normale
            if (start < 0) {
                start = i;
            }
            i++;
            if (!Character.isWhitespace(ch)) {
                codeEnd = i;
            }
        }
        if (start >= 0) {
            out.add(statement(script, start, codeEnd));
        }
        return out;
    }

    private static SplitStatement statement(String script, int start, int end) {
        int line = 1;
        for (int k = 0; k < start; k++) {
            if (script.charAt(k) == '\n') {
                line++;
            }
        }
        return new SplitStatement(script.substring(start, end), start, end, line);
    }

    private static boolean isDelimiterCommand(String s, int i) {
        int after = i + DELIMITER_KEYWORD.length();
        return s.regionMatches(true, i, DELIMITER_KEYWORD, 0, DELIMITER_KEYWORD.length())
                && after < s.length() && (s.charAt(after) == ' ' || s.charAt(after) == '\t');
    }

    /** {@code --} è commento solo se seguito da spazio, controllo o fine del testo ({@code 5--3} è aritmetica). */
    static boolean isDashComment(String s, int i) {
        if (s.charAt(i) != '-' || i + 1 >= s.length() || s.charAt(i + 1) != '-') {
            return false;
        }
        return i + 2 >= s.length() || s.charAt(i + 2) <= ' ';
    }

    /** {@code /*!} (MySQL e MariaDB) e {@code /*M!} (solo MariaDB). */
    static boolean isExecutableComment(String s, int i) {
        return s.startsWith("/*!", i) || s.startsWith("/*M!", i);
    }

    static int endOfLine(String s, int i) {
        int n = s.length();
        while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    /** Dalla virgoletta d'apertura in {@code i} alla posizione dopo quella di chiusura (o alla fine del testo). */
    static int skipQuoted(String s, int i, char quote) {
        int n = s.length();
        i++;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '\\' && quote != '`') {
                i += 2;
            } else if (ch == quote) {
                if (i + 1 < n && s.charAt(i + 1) == quote) {
                    i += 2;      // virgoletta raddoppiata
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        return n;
    }
}
