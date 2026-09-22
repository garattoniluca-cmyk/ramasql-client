/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app.spike.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * PROTOTIPO USA-E-GETTA dello spike S7: appunti «a blocchi» tra una {@link JTable} con selezione a celle
 * e i fogli di calcolo, secondo la CONVENZIONE DI EXCEL per il testo tabulato:
 * celle separate da tabulazione, righe chiuse da {@code \r\n}; una cella che contiene tabulazione,
 * a-capo o virgolette va tra {@code "} con le virgolette interne raddoppiate ({@code ""}).
 */
final class BlockClipboard {

    private BlockClipboard() {
    }

    // ------------------------------------------------------------------ testo <-> blocco

    /** Blocco → testo tabulato. */
    static String toText(List<List<String>> block) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : block) {
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) {
                    sb.append('\t');
                }
                sb.append(quote(row.get(c)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static String quote(String cell) {
        if (cell == null) {
            return "";
        }
        if (cell.indexOf('\t') < 0 && cell.indexOf('\n') < 0 && cell.indexOf('\r') < 0 && cell.indexOf('"') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }

    /**
     * Testo tabulato → blocco. Una cella è «tra virgolette» solo se INIZIA con {@code "} ed è ben formata
     * (virgolette interne tutte raddoppiate, virgoletta di chiusura seguita da tabulazione, a-capo o fine testo):
     * dentro valgono tabulazioni e a-capo, {@code ""} è una virgoletta. Negli altri casi le virgolette sono
     * caratteri qualunque: Excel VERO scrive senza virgolette né raddoppi le celle che contengono solo virgolette
     * ({@code detto "ciao"}, e perfino {@code "inizia con virgolette}); mette tra virgolette solo le celle con
     * tabulazione o a-capo. Righe chiuse da CR LF, LF o CR; l'a-capo finale non crea una riga vuota.
     *
     * <p>Limite intrinseco del formato testo di Excel (non risolvibile qui): una cella che inizia con {@code "}
     * seguita più avanti da una cella che finisce con {@code "}, senza altre virgolette in mezzo, è ambigua.
     */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean cellStarted = false; // la cella corrente ha già ricevuto qualcosa (anche solo le virgolette)
        boolean afterTab = false;    // dopo una tabulazione la cella successiva esiste sempre, anche vuota
        int i = 0;
        int n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            int quotedEnd = (!cellStarted && ch == '"') ? quotedCellEnd(text, i) : -1;
            if (quotedEnd > 0) { // cella tra virgolette ben formata: il contenuto sta tra i+1 e quotedEnd-1
                cell.append(text.substring(i + 1, quotedEnd - 1).replace("\"\"", "\""));
                cellStarted = true;
                i = quotedEnd;
            } else if (ch == '\t') {
                row.add(cell.toString());
                cell.setLength(0);
                cellStarted = false;
                afterTab = true;
                i++;
            } else if (ch == '\r' || ch == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                cellStarted = false;
                afterTab = false;
                rows.add(row);
                row = new ArrayList<>();
                i += (ch == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') ? 2 : 1;
            } else {
                cell.append(ch);
                cellStarted = true;
                i++;
            }
        }
        if (cellStarted || afterTab) { // ultima riga senza a-capo finale
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Se in {@code start} inizia una cella tra virgolette BEN FORMATA restituisce l'indice subito dopo la
     * virgoletta di chiusura, altrimenti -1 (e la virgoletta iniziale vale come carattere normale).
     */
    private static int quotedCellEnd(String text, int start) {
        int n = text.length();
        int j = start + 1;
        while (j < n) {
            if (text.charAt(j) != '"') {
                j++;
            } else if (j + 1 < n && text.charAt(j + 1) == '"') {
                j += 2;
            } else {
                j++;
                boolean delimiter = j == n || text.charAt(j) == '\t' || text.charAt(j) == '\r' || text.charAt(j) == '\n';
                return delimiter ? j : -1;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ JTable

    /** Blocco rettangolare selezionato nella tabella (indici di vista); vuoto se non c'è selezione. */
    static List<List<String>> selectedBlock(JTable table) {
        List<List<String>> block = new ArrayList<>();
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            return block;
        }
        for (int r = rows[0]; r <= rows[rows.length - 1]; r++) {
            List<String> line = new ArrayList<>();
            for (int c = cols[0]; c <= cols[cols.length - 1]; c++) {
                Object v = table.getValueAt(r, c);
                line.add(v == null ? "" : v.toString());
            }
            block.add(line);
        }
        return block;
    }

    /** Copia il blocco selezionato negli appunti; restituisce il testo messo negli appunti. */
    static String copy(JTable table, Clipboard clipboard) {
        String text = toText(selectedBlock(table));
        setText(clipboard, text);
        return text;
    }

    /**
     * Incolla dagli appunti a partire dalla cella attiva (o da 0,0), creando le righe che mancano.
     * Le colonne oltre l'ultima della tabella si scartano.
     *
     * @return {righe incollate, colonne incollate, righe nuove create, celle scartate}
     */
    static int[] paste(JTable table, Clipboard clipboard) {
        List<List<String>> block = parse(getText(clipboard));
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        int row0 = Math.max(0, table.getSelectionModel().getLeadSelectionIndex());
        int col0 = Math.max(0, table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        int created = 0;
        int dropped = 0;
        int maxCols = 0;
        for (int r = 0; r < block.size(); r++) {
            int viewRow = row0 + r;
            while (viewRow >= table.getRowCount()) {
                model.addRow(new Object[model.getColumnCount()]);
                created++;
            }
            List<String> line = block.get(r);
            for (int c = 0; c < line.size(); c++) {
                int viewCol = col0 + c;
                if (viewCol >= table.getColumnCount()) {
                    dropped++;
                    continue;
                }
                table.setValueAt(line.get(c), viewRow, viewCol);
                maxCols = Math.max(maxCols, c + 1);
            }
        }
        if (!block.isEmpty() && maxCols > 0) { // si lascia selezionato il blocco incollato
            table.changeSelection(row0, col0, false, false);
            table.changeSelection(row0 + block.size() - 1, col0 + maxCols - 1, false, true);
        }
        return new int[] {block.size(), maxCols, created, dropped};
    }

    /** Collega Copia e Incolla della tabella (Ctrl+C / Ctrl+V usano già i nomi «copy» e «paste»). */
    static void install(JTable table) {
        table.setCellSelectionEnabled(true);
        table.getActionMap().put("copy", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                copy(table, Toolkit.getDefaultToolkit().getSystemClipboard());
            }
        });
        table.getActionMap().put("paste", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                paste(table, Toolkit.getDefaultToolkit().getSystemClipboard());
            }
        });
    }

    // ------------------------------------------------------------------ appunti (con nuovi tentativi)

    /** Gli appunti di Windows possono essere occupati da un altro programma per qualche istante: si riprova. */
    static void setText(Clipboard clipboard, String text) {
        IllegalStateException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                clipboard.setContents(new StringSelection(text), null);
                return;
            } catch (IllegalStateException e) {
                last = e;
                pause();
            }
        }
        throw last;
    }

    static String getText(Clipboard clipboard) {
        RuntimeException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return "";
                }
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            } catch (IllegalStateException e) {
                last = e;
                pause();
            } catch (UnsupportedFlavorException | IOException e) {
                last = new IllegalStateException(e);
                pause();
            }
        }
        throw last;
    }

    private static void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
