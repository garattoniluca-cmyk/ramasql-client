/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import static it.ramasql.app.grid.GridTestSupport.action;
import static it.ramasql.app.grid.GridTestSupport.fromEdt;
import static it.ramasql.app.grid.GridTestSupport.onEdt;
import static it.ramasql.app.grid.GridTestSupport.visible;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.app.grid.DataGrid.CellRange;
import it.ramasql.core.data.ClipboardBlock;

/**
 * T4.16 (parte componente): selezione a blocco 3×4 in mezzo alla griglia e Ctrl+C, con e senza intestazioni, nella
 * convenzione di Excel; selezione di colonna (clic sull'intestazione), di riga (clic sul numero) e Ctrl+A.
 * Gli appunti sono PRIVATI ({@code new Clipboard("test")}): quelli dell'utente non si toccano.
 */
@Tag("step4")
@Tag("ui")
class T416BlockSelectionCopyTest {

    private static final String ATTESO = "T103\t\"Carla\tVerdi\"\tcarla.verdi@scuola.it\t2008-03-12\r\n"
            + "T104\tDario Neri\t\"riga uno\nriga due\"\t\r\n"
            + "T105\t\"Elena \"\"Leni\"\" Galli\"\t\t2008-05-14\r\n";

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    @Test
    void bloccoTrePerQuattroCopiatoComeTestoTabulatoConESenzaIntestazioni() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        onEdt(() -> grid.setClipboard(clipboard));
        JFrame frame = GridTestSupport.host(grid, 1100, 460);

        onEdt(() -> grid.selectBlock(2, 1, 4, 4));
        assertEquals(new CellRange(2, 4, 1, 4), fromEdt(grid::selection));
        action(grid, DataGrid.ACTION_COPY);
        String copiato = (String) clipboard.getData(DataFlavor.stringFlavor);
        assertEquals(ATTESO, copiato, "Ctrl+C: 3 righe × 4 colonne, convenzione Excel");
        GridTestSupport.screenshot(frame, "T4.16-blocco-3x4.png");

        // andata e ritorno: il testo riletto è esattamente il blocco della griglia (NULL resta NULL)
        List<List<String>> blocco = GridTestSupport.cells(grid, 2, 4, 1, 4);
        assertEquals(blocco, ClipboardBlock.parse(copiato).rows());
        assertNull(blocco.get(1).get(3), "NULL copiato come cella vuota e riletto come NULL");

        action(grid, DataGrid.ACTION_COPY_WITH_HEADERS);
        String conIntestazioni = (String) clipboard.getData(DataFlavor.stringFlavor);
        assertEquals("tessera\tnome\temail\tnato_il\r\n" + ATTESO, conIntestazioni, "Copia con intestazioni: 4×4");

        GridTestSupport.writeText("T4.16-copia.txt", "Blocco selezionato: righe 3-5, colonne tessera..nato_il (3×4)\n"
                + "Ctrl+C (<TAB> <CR> <LF> resi visibili):\n" + visible(copiato)
                + "\nCopia con intestazioni:\n" + visible(conIntestazioni)
                + "\nRiletto con ClipboardBlock.parse: " + ClipboardBlock.parse(copiato).rows()
                + "\nAppunti usati: privati (new Clipboard(\"test\")), non quelli di sistema.\n"
                + "Verifica in Excel e Calc reali: vedi Bug002SystemClipboardTest e il controllo manuale T4.16.\n");
    }

    @Test
    void clicSullIntestazioneSulNumeroDiRigaECtrlASelezionano() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        onEdt(() -> grid.setClipboard(clipboard));
        JFrame frame = GridTestSupport.host(grid, 1100, 460);

        // clic sull'intestazione della colonna «nome»: la colonna intera (le 12 righe, non la riga d'inserimento)
        onEdt(() -> {
            JComponent header = grid.table().getTableHeader();
            Rectangle r = grid.table().getTableHeader().getHeaderRect(2);
            press(header, new Point(r.x + r.width / 2, r.y + r.height / 2), 0);
        });
        assertEquals(new CellRange(0, 11, 2, 2), fromEdt(grid::selection));
        action(grid, DataGrid.ACTION_COPY);
        assertEquals(12, ClipboardBlock.parse((String) clipboard.getData(DataFlavor.stringFlavor)).rowCount());

        // Maiusc+clic su «email»: si estende a due colonne
        onEdt(() -> {
            Rectangle r = grid.table().getTableHeader().getHeaderRect(3);
            press(grid.table().getTableHeader(), new Point(r.x + 5, r.y + 5), InputEvent.SHIFT_DOWN_MASK);
        });
        assertEquals(new CellRange(0, 11, 2, 3), fromEdt(grid::selection));

        // clic sul numero della riga 5: la riga intera
        onEdt(() -> {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, grid.table());
            JComponent rowHeader = (JComponent) scroll.getRowHeader().getView();
            int y = grid.table().getCellRect(4, 0, true).y + 3;
            press(rowHeader, new Point(10, y), 0);
        });
        assertEquals(new CellRange(4, 4, 0, 6), fromEdt(grid::selection));
        GridTestSupport.screenshot(frame, "T4.16-riga-intera.png");

        // Ctrl+A: tutto (la copia esclude la riga d'inserimento vuota)
        action(grid, DataGrid.ACTION_SELECT_ALL);
        assertEquals(new CellRange(0, 12, 0, 6), fromEdt(grid::selection));
        action(grid, DataGrid.ACTION_COPY);
        ClipboardBlock tutto = ClipboardBlock.parse((String) clipboard.getData(DataFlavor.stringFlavor));
        assertEquals(12, tutto.rowCount());
        assertEquals(7, tutto.columnCount());
        assertEquals(Arrays.asList("7", "T107", "Giulia Testà 😀", "giulia.testa@scuola.it", "2008-07-16", "16.50",
                "60"), tutto.rows().get(6));
        GridTestSupport.writeText("T4.16-selezione.txt", "Clic intestazione «nome» → righe 1-12 × colonna nome;\n"
                + "Maiusc+clic «email» → colonne nome..email;\nclic numero riga 5 → riga intera (7 colonne);\n"
                + "Ctrl+A → tutto; copiate 12 righe × 7 colonne (riga d'inserimento esclusa).\n");
    }

    private static void press(JComponent target, Point p, int modifiers) {
        long now = System.currentTimeMillis();
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, now, modifiers | InputEvent.BUTTON1_DOWN_MASK,
                p.x, p.y, 1, false, MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, now, modifiers, p.x, p.y, 1, false,
                MouseEvent.BUTTON1));
    }
}
