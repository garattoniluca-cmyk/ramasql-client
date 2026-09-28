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

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/**
 * Le istruzioni di uno script {@code .sql} lette <b>una alla volta</b> da un file (il ripristino di un dump, Step 10):
 * la memoria usata è quella dell'istruzione più lunga, non del file. Stesse regole di {@link StatementSplitter}
 * (stringhe, identificatori tra backtick, commenti {@code --} {@code #} {@code /* *}{@code /}, commenti eseguibili
 * {@code /*!} e {@code /*M!} che restano codice, {@code DELIMITER} a inizio istruzione), così uno script diviso da
 * qui e dall'editor SQL dà le stesse istruzioni ({@code ScriptReaderTest} lo controlla).
 */
public final class ScriptReader implements AutoCloseable {

    /**
     * Un'istruzione dello script.
     *
     * @param text  testo senza separatore finale e senza spazi attorno
     * @param line  riga (da 1) in cui comincia
     * @param index posizione nello script (da 0)
     */
    public record Statement(String text, long line, long index) {
    }

    private static final String DELIMITER_KEYWORD = "DELIMITER";
    private static final int CHUNK = 64 * 1024;

    private final Reader in;
    private final char[] chunk = new char[CHUNK];
    private final StringBuilder buf = new StringBuilder();
    private int pos;
    private boolean eof;
    /** Riga del carattere in {@code pos}. */
    private long line = 1;
    private long consumed;
    private long count;
    private String delimiter = ";";

    public ScriptReader(Reader in) {
        this.in = Objects.requireNonNull(in, "in");
    }

    /** Caratteri già superati (per l'avanzamento, rispetto alla lunghezza del file). */
    public long charsConsumed() {
        return consumed + pos;
    }

    /** L'istruzione successiva; {@code null} a fine script. */
    public Statement next() throws IOException {
        int start = -1;
        int codeEnd = -1;
        long startLine = 0;
        while (has(pos)) {
            char ch = buf.charAt(pos);
            if (start < 0 && Character.isWhitespace(ch)) {
                advance(1);
                compact();
                continue;
            }
            if (start < 0 && isDelimiterCommand(pos)) {
                int eol = endOfLine(pos);
                String wanted = buf.substring(pos + DELIMITER_KEYWORD.length(), eol).trim();
                if (!wanted.isEmpty()) {
                    delimiter = wanted;
                }
                advanceTo(eol);
                continue;
            }
            if (ch == '#' || isDashComment(pos)) {
                advanceTo(endOfLine(pos));
                continue;
            }
            if (ch == '/' && has(pos + 1) && buf.charAt(pos + 1) == '*') {
                if (startsWith("/*!", pos) || startsWith("/*M!", pos)) {
                    if (start < 0) {
                        start = pos;
                        startLine = line;
                    }
                    advance(3);
                    codeEnd = pos;
                    continue;
                }
                int close = indexOf("*/", pos + 2);
                advanceTo(close < 0 ? buf.length() : close + 2);
                continue;
            }
            if (startsWith(delimiter, pos)) {
                if (start >= 0) {
                    Statement s = new Statement(buf.substring(start, codeEnd), startLine, count++);
                    advance(delimiter.length());
                    compact();
                    return s;
                }
                advance(delimiter.length());
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                if (start < 0) {
                    start = pos;
                    startLine = line;
                }
                advanceTo(skipQuoted(pos, ch));
                codeEnd = pos;
                continue;
            }
            if (start < 0) {
                start = pos;
                startLine = line;
            }
            advance(1);
            if (!Character.isWhitespace(ch)) {
                codeEnd = pos;
            }
        }
        if (start >= 0) {
            Statement s = new Statement(buf.substring(start, codeEnd), startLine, count++);
            compact();
            return s;
        }
        return null;
    }

    // ---------------------------------------------------------------- buffer

    /** Il carattere {@code i} esiste (leggendo dal file se serve). */
    private boolean has(int i) throws IOException {
        while (i >= buf.length()) {
            if (!fill()) {
                return false;
            }
        }
        return true;
    }

    private boolean fill() throws IOException {
        if (eof) {
            return false;
        }
        int n = in.read(chunk, 0, chunk.length);
        if (n < 0) {
            eof = true;
            return false;
        }
        buf.append(chunk, 0, n);
        return true;
    }

    private void advance(int n) throws IOException {
        advanceTo(pos + n);
    }

    private void advanceTo(int target) throws IOException {
        has(target - 1);
        int end = Math.min(target, buf.length());
        for (int k = pos; k < end; k++) {
            if (buf.charAt(k) == '\n') {
                line++;
            }
        }
        pos = end;
    }

    /** Fra un'istruzione e l'altra il testo già letto si butta: la memoria non cresce con il file. */
    private void compact() {
        if (pos > CHUNK) {
            buf.delete(0, pos);
            consumed += pos;
            pos = 0;
        }
    }

    private boolean startsWith(String s, int i) throws IOException {
        if (!has(i + s.length() - 1)) {
            return false;
        }
        for (int k = 0; k < s.length(); k++) {
            if (buf.charAt(i + k) != s.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private int indexOf(String s, int from) throws IOException {
        int i = from;
        while (true) {
            int found = buf.indexOf(s, i);
            if (found >= 0) {
                return found;
            }
            i = Math.max(from, buf.length() - s.length() + 1);
            if (!fill()) {
                return -1;
            }
        }
    }

    private int endOfLine(int i) throws IOException {
        while (has(i) && buf.charAt(i) != '\n' && buf.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    private boolean isDelimiterCommand(int i) throws IOException {
        int after = i + DELIMITER_KEYWORD.length();
        if (!has(after)) {
            return false;
        }
        return buf.substring(i, after).equalsIgnoreCase(DELIMITER_KEYWORD)
                && (buf.charAt(after) == ' ' || buf.charAt(after) == '\t');
    }

    private boolean isDashComment(int i) throws IOException {
        if (buf.charAt(i) != '-' || !has(i + 1) || buf.charAt(i + 1) != '-') {
            return false;
        }
        return !has(i + 2) || buf.charAt(i + 2) <= ' ';
    }

    private int skipQuoted(int i, char quote) throws IOException {
        i++;
        while (has(i)) {
            char ch = buf.charAt(i);
            if (ch == '\\' && quote != '`') {
                i += 2;
            } else if (ch == quote) {
                if (has(i + 1) && buf.charAt(i + 1) == quote) {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        return buf.length();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
