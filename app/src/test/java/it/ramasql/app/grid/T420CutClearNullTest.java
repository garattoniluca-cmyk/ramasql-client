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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.ClipboardBlock;
import it.ramasql.core.data.RowChange;
import it.ramasql.core.sqlgen.DmlGenerator;

/**
 * T4.20 (parte componente): Ctrl+X e Canc su un blocco con colonne NULL e NOT NULL → NULL dove ammesso, stringa vuota
 * nelle colonne di testo NOT NULL, cella invariata con avviso nelle altre NOT NULL; sulle righe nuove la cella torna
 * «non impostata» (DEFAULT). Più: NULL mostrato in grigio corsivo, «Imposta NULL», «Modifica in una finestra…».
 */
@Tag("step4")
@Tag("ui")
class T420CutClearNullTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    @Test
    void tagliaECancSuColonneNullENotNull() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> {
            grid.setClipboard(clipboard);
            grid.setOnConfirm(confermate::add);
        });
        JFrame frame = GridTestSupport.host(grid, 1100, 480);
        List<List<String>> prima = GridTestSupport.cells(grid, 0, 1, 2, 6);

        // Ctrl+X su righe 1-2 × nome (NOT NULL testo), email, nato_il, quota (NULL), punti (NOT NULL INT)
        onEdt(() -> grid.selectBlock(0, 2, 1, 6));
        action(grid, DataGrid.ACTION_CUT);
        assertEquals(prima, ClipboardBlock.parse((String) clipboard.getData(DataFlavor.stringFlavor)).rows(),
                "Ctrl+X copia prima di svuotare");
        List<List<String>> dopo = GridTestSupport.cells(grid, 0, 1, 2, 6);
        assertEquals(Arrays.asList("", null, null, null, "0"), dopo.get(0));
        assertEquals(Arrays.asList("", null, null, null, "10"), dopo.get(1));
        assertEquals("Celle svuotate; le colonne NOT NULL punti non ammettono NULL né testo vuoto e sono rimaste com'erano.",
                fromEdt(grid::notice));
        GridTestSupport.screenshot(frame, "T4.20-taglia.png");
        List<String> sql = DmlGenerator.generate(Fixtures.soci(), fromEdt(() -> grid.model().pending().toRowChanges()));
        assertEquals("UPDATE `biblioteca`.`soci` SET `nome` = '', `email` = NULL, `nato_il` = NULL, `quota` = NULL WHERE `id` = 1",
                sql.get(0));

        // Ctrl+Z ritira lo svuotamento
        action(grid, DataGrid.ACTION_UNDO_PASTE);
        assertEquals(prima, GridTestSupport.cells(grid, 0, 1, 2, 6));
        assertFalse(fromEdt(grid::hasPending));

        // Canc su righe NUOVE: le celle tornano «non impostate» → omesse dall'INSERT (DEFAULT)
        clipboard.setContents(new StringSelection("T500\tNuovo Socio\tn@s.it\t2010-10-10\t5.00\t3\r\n"), null);
        onEdt(() -> grid.selectBlock(12, 1, 12, 1));
        action(grid, DataGrid.ACTION_PASTE);
        onEdt(() -> grid.selectBlock(12, 3, 12, 6));
        action(grid, DataGrid.ACTION_CLEAR);   // tasto Canc
        assertEquals(Arrays.asList(null, null, null, null), GridTestSupport.cells(grid, 12, 12, 3, 6).get(0));
        assertFalse(fromEdt(() -> grid.model().isSet(12, 6)), "punti non impostato: userà il DEFAULT 0");
        List<String> insert = DmlGenerator.generate(Fixtures.soci(), fromEdt(() -> grid.model().pending().toRowChanges()));
        assertEquals(List.of("INSERT INTO `biblioteca`.`soci` (`tessera`, `nome`) VALUES ('T500', 'Nuovo Socio')"), insert);

        GridTestSupport.writeText("T4.20-taglia-canc.txt", "Ctrl+X su righe 1-2 × nome..punti:\n  appunti = blocco "
                + "originale " + prima + "\n  dopo = " + dopo + "\n  avviso: " + fromEdt(grid::notice)
                + "\n  SQL (prima riga): " + sql.get(0) + "\nCtrl+Z: blocco ripristinato, nessuna modifica in sospeso.\n"
                + "Canc su riga nuova (email..punti): celle non impostate → " + insert.get(0) + "\n");
    }

    @Test
    void nullGrigioCorsivoImpostaNullEModificaInUnaFinestra() throws Exception {
        FakeGridPrompts prompts = new FakeGridPrompts();
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, prompts);
        onEdt(() -> grid.setClipboard(new Clipboard("test")));
        JFrame frame = GridTestSupport.host(grid, 1100, 480);

        // NULL (riga 5, email) disegnato come «NULL» grigio corsivo, diverso dalla stringa vuota (riga 6)
        JLabel nullCell = fromEdt(() -> (JLabel) grid.table().prepareRenderer(grid.table().getCellRenderer(4, 3), 4, 3));
        String nullText = nullCell.getText();
        assertEquals("NULL", nullText);
        assertTrue(nullCell.getFont().isItalic());
        assertEquals(GridCellRenderer.NULL_TEXT, nullCell.getForeground());
        JLabel emptyCell = fromEdt(() -> (JLabel) grid.table().prepareRenderer(grid.table().getCellRenderer(5, 3), 5, 3));
        String emptyText = emptyCell.getText();
        assertEquals("", emptyText);

        // «Imposta NULL» su email (ammesso) e nome (NOT NULL: rifiutato con avviso)
        onEdt(() -> grid.selectBlock(0, 2, 0, 3));
        action(grid, DataGrid.ACTION_SET_NULL);
        assertEquals(Arrays.asList("Anna Rossi", null), GridTestSupport.cells(grid, 0, 0, 2, 3).get(0));
        assertTrue(fromEdt(() -> grid.model().isSet(0, 3)), "NULL esplicito");
        assertEquals("Le colonne nome non ammettono NULL: rimaste com'erano.", fromEdt(grid::notice));

        // «Modifica in una finestra…»: testo lungo con a-capo
        prompts.longTextAnswer = "Prima riga\nSeconda riga\n\tcon rientro";
        onEdt(() -> grid.selectBlock(1, 3, 1, 3));
        action(grid, DataGrid.ACTION_EDIT_IN_WINDOW);
        assertEquals("longText: email — riga 2 | bruno.bianchi@scuola.it", prompts.shown.get(0));
        assertEquals("Prima riga\nSeconda riga\n\tcon rientro", GridTestSupport.cells(grid, 1, 1, 3, 3).get(0).get(0));
        prompts.longTextAnswer = null;   // Annulla: nulla cambia
        action(grid, DataGrid.ACTION_EDIT_IN_WINDOW);
        assertEquals("Prima riga\nSeconda riga\n\tcon rientro", GridTestSupport.cells(grid, 1, 1, 3, 3).get(0).get(0));
        JLabel longCell = fromEdt(() -> (JLabel) grid.table().prepareRenderer(grid.table().getCellRenderer(1, 3), 1, 3));
        String longText = longCell.getText();
        assertEquals("Prima riga↵Seconda riga↵→con rientro", longText);
        GridTestSupport.screenshot(frame, "T4.20-null-e-testo-lungo.png");

        // un editor aperto su un NULL e chiuso senza scrivere non lo trasforma in stringa vuota
        GridTestSupport.type(grid, 4, 3, "");
        assertNull(GridTestSupport.cells(grid, 4, 4, 3, 3).get(0).get(0));
        GridTestSupport.writeText("T4.20-null.txt", "NULL: «" + nullText + "» corsivo grigio; stringa vuota: «"
                + emptyText + "».\nImposta NULL su nome+email riga 1 → email NULL, nome invariato; avviso: "
                + "«Le colonne nome non ammettono NULL…».\nModifica in una finestra: testo con a-capo e tab salvato; "
                + "in cella si vede «" + longText + "».\nEditor aperto su NULL e chiuso vuoto: resta NULL.\n");
    }
}
