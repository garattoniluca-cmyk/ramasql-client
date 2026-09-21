/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Blocco rettangolare di celle ⇄ testo tabulato degli appunti, nella <b>convenzione di Excel</b>
 * (la stessa di LibreOffice Calc e Fogli Google).
 *
 * <h2>Scrittura ({@link #toText()})</h2>
 * Celle separate da tabulazione, righe chiuse da {@code \r\n} (anche l'ultima). Una cella che contiene
 * tabulazioni, a-capo o virgolette è racchiusa tra virgolette doppie, con le virgolette interne raddoppiate.
 *
 * <h2>NULL e stringa vuota (andata e ritorno)</h2>
 * Nel modello {@code null} è il NULL di SQL e {@code ""} la stringa vuota. In copia il NULL diventa una
 * <b>cella vuota</b>; la stringa vuota diventa la cella <b>{@code ""}</b> (due virgolette), che i fogli di
 * calcolo leggono comunque come cella vuota. In lettura vale l'inverso: cella vuota → {@code null},
 * cella {@code ""} → stringa vuota. Così blocco → testo → blocco non altera nulla, e una cella vuota
 * che arriva da Excel è «valore assente»: sarà la griglia a decidere (NULL su una riga esistente,
 * DEFAULT su una riga nuova — vedi {@link PendingChanges#paste}). Con {@code toText(false)} anche la
 * stringa vuota esce come cella vuota (testo più pulito, ma al ritorno diventa {@code null}).
 *
 * <h2>Lettura ({@link #parse})</h2>
 * Accetta {@code \r\n}, {@code \n} e {@code \r}; l'a-capo finale non crea una riga in più; le virgolette
 * contano solo a inizio cella ({@code 5" di pollice} è testo normale); righe di lunghezza diversa sono
 * completate con {@code null} perché il blocco sia rettangolare.
 *
 * @param rows righe di celle; ogni riga ha {@link #columnCount()} celle
 */
public record ClipboardBlock(List<List<String>> rows) {

    public ClipboardBlock {
        int width = 0;
        for (List<String> row : rows) {
            width = Math.max(width, row.size());
        }
        List<List<String>> copy = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>(row);
            while (cells.size() < width) {
                cells.add(null);
            }
            copy.add(Collections.unmodifiableList(cells));
        }
        rows = Collections.unmodifiableList(copy);
    }

    /** Blocco da righe di celle: {@code of(row("a", null), row("b", ""))}. */
    @SafeVarargs
    public static ClipboardBlock of(List<String>... rows) {
        return new ClipboardBlock(Arrays.asList(rows));
    }

    /** Una riga di celle; ammette {@code null} (a differenza di {@code List.of}). */
    public static List<String> row(String... cells) {
        return Collections.unmodifiableList(Arrays.asList(cells.clone()));
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return rows.isEmpty() ? 0 : rows.get(0).size();
    }

    public String cell(int row, int column) {
        return rows.get(row).get(column);
    }

    /** Un solo valore: incollato su una selezione di più celle le riempie tutte. */
    public boolean isSingleCell() {
        return rowCount() == 1 && columnCount() == 1;
    }

    // ---------------------------------------------------------------- scrittura

    /** Testo per gli appunti, senza perdita tra NULL e stringa vuota. */
    public String toText() {
        return toText(true);
    }

    /** @param quoteEmptyStrings vero: la stringa vuota esce come {@code ""}; falso: come cella vuota */
    public String toText(boolean quoteEmptyStrings) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) {
                    out.append('\t');
                }
                out.append(encodeCell(row.get(c), quoteEmptyStrings));
            }
            out.append("\r\n");
        }
        return out.toString();
    }

    private static String encodeCell(String value, boolean quoteEmptyStrings) {
        if (value == null) {
            return "";
        }
        if (value.isEmpty()) {
            return quoteEmptyStrings ? "\"\"" : "";
        }
        boolean needsQuotes = value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('"') >= 0;
        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    // ---------------------------------------------------------------- lettura

    public static ClipboardBlock parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return new ClipboardBlock(rows);
        }
        final int n = text.length();
        List<String> row = new ArrayList<>();
        int i = 0;
        boolean rowOpen = false;         // c'è una riga iniziata e non ancora chiusa da un a-capo
        while (i <= n) {
            // --- una cella
            String cell;
            int closing = i < n && text.charAt(i) == '"' ? closingQuote(text, i) : -1;
            if (closing >= 0) {
                cell = text.substring(i + 1, closing).replace("\"\"", "\"");
                i = closing + 1;
            } else {
                int start = i;
                while (i < n && text.charAt(i) != '\t' && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                    i++;
                }
                cell = i == start ? null : text.substring(start, i);
            }
            // --- ciò che la segue
            if (i < n && text.charAt(i) == '\t') {
                row.add(cell);
                rowOpen = true;
                i++;
                continue;
            }
            boolean atEnd = i >= n;
            if (atEnd && !rowOpen && cell == null) {
                break;                   // a-capo finale: nessuna riga in più
            }
            row.add(cell);
            rows.add(row);
            row = new ArrayList<>();
            rowOpen = false;
            if (atEnd) {
                break;
            }
            i += text.charAt(i) == '\r' && i + 1 < n && text.charAt(i + 1) == '\n' ? 2 : 1;
        }
        return new ClipboardBlock(rows);
    }

    /**
     * Posizione della virgoletta che chiude la cella aperta in {@code open}, oppure -1 se la cella non è
     * ben formata (manca la chiusura, o dopo la chiusura non c'è un separatore): allora vale come testo normale.
     */
    private static int closingQuote(String text, int open) {
        int n = text.length();
        int i = open + 1;
        while (i < n) {
            if (text.charAt(i) == '"') {
                if (i + 1 < n && text.charAt(i + 1) == '"') {
                    i += 2;
                    continue;
                }
                boolean separatorFollows = i + 1 >= n || text.charAt(i + 1) == '\t' || text.charAt(i + 1) == '\n'
                        || text.charAt(i + 1) == '\r';
                return separatorFollows ? i : -1;
            }
            i++;
        }
        return -1;
    }
}
