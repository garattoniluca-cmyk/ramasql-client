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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.RowChange;

/**
 * T4.19 (parte componente): testo in una colonna INT e una data impossibile incollati → celle marcate (con il messaggio
 * del validatore come suggerimento), Conferma disabilitata con la spiegazione e senza effetto se invocata; Ctrl+Z
 * ritira l'intero incolla; corrette le celle, la Conferma torna possibile.
 */
@Tag("step4")
@Tag("ui")
class T419ValidationAndUndoTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    private static String tooltip(DataGrid grid, int row, int column) {
        return fromEdt(() -> {
            var renderer = grid.table().getCellRenderer(row, column);
            JComponent c = (JComponent) grid.table().prepareRenderer(renderer, row, column);
            return c.getToolTipText();
        });
    }

    private static java.awt.Component find(java.awt.Container c, String name) {
        for (java.awt.Component child : c.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            java.awt.Component inner = child instanceof java.awt.Container k ? find(k, name) : null;
            if (inner != null) {
                return inner;
            }
        }
        return null;
    }

    @Test
    void celleNonValideBloccanoLaConfermaECtrlZRitiraLIncolla() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> {
            grid.setClipboard(clipboard);
            grid.setOnConfirm(confermate::add);
        });
        JFrame frame = GridTestSupport.host(grid, 1100, 480);
        List<List<String>> prima = GridTestSupport.cells(grid, 0, 1, 4, 6);

        // riga 1: data impossibile, quota valida, testo in INT; riga 2: tutto valido
        clipboard.setContents(new StringSelection("2026-02-30\t12.50\tabc\r\n2001-01-01\t3.00\t7\r\n"), null);
        onEdt(() -> grid.selectBlock(0, 4, 0, 4));
        action(grid, DataGrid.ACTION_PASTE);

        List<int[]> invalid = fromEdt(() -> grid.model().pending().invalidCells());
        assertEquals(2, invalid.size());
        assertEquals("Data non valida: usa il formato AAAA-MM-GG, con un giorno che esiste.", tooltip(grid, 0, 4));
        assertEquals("Valore non valido: serve un numero intero.", tooltip(grid, 0, 6));
        assertFalse(fromEdt(grid::isConfirmEnabled), "Conferma bloccata");
        assertEquals("2 celle non valide (segnate in rosso): correggile per poter confermare.",
                fromEdt(grid::confirmDisabledReason));
        assertTrue(fromEdt(grid::counterText).startsWith("0 inserimenti · 2 modifiche"), fromEdt(grid::counterText));
        onEdt(() -> grid.getActionMap().get(DataGrid.ACTION_CONFIRM).actionPerformed(null));   // Ctrl+S
        assertTrue(confermate.isEmpty(), "con celle non valide la Conferma non consegna nulla");
        assertEquals("2 celle non valide (segnate in rosso): correggile per poter confermare.",
                fromEdt(() -> ((javax.swing.JLabel) find(grid, "dataGrid.invalid")).getText()), "spiegazione visibile");
        GridTestSupport.screenshot(frame, "T4.19-celle-non-valide.png");
        String dopoIncolla = fromEdt(grid::counterText) + " · Conferma: " + fromEdt(grid::confirmDisabledReason);

        // Ctrl+Z: l'intero incolla (2×3 celle) sparisce in un colpo
        action(grid, DataGrid.ACTION_UNDO_PASTE);
        assertEquals(prima, GridTestSupport.cells(grid, 0, 1, 4, 6));
        assertFalse(fromEdt(grid::hasPending));
        assertEquals("Nessuna modifica in sospeso", fromEdt(grid::counterText));
        assertFalse(fromEdt(grid::isConfirmEnabled));

        // di nuovo, poi si correggono a mano le due celle: la Conferma torna possibile
        action(grid, DataGrid.ACTION_PASTE);
        assertFalse(fromEdt(grid::isConfirmEnabled));
        GridTestSupport.type(grid, 0, 4, "2026-02-28");
        assertFalse(fromEdt(grid::isConfirmEnabled), "manca ancora una cella");
        assertEquals("1 cella non valida (segnata in rosso): correggila per poter confermare.",
                fromEdt(grid::confirmDisabledReason));
        GridTestSupport.type(grid, 0, 6, "42");
        assertTrue(fromEdt(grid::isConfirmEnabled));
        onEdt(grid::confirm);
        assertEquals(1, confermate.size());
        assertEquals(2, confermate.get(0).size(), "2 UPDATE");
        GridTestSupport.screenshot(frame, "T4.19-corrette.png");

        GridTestSupport.writeText("T4.19-validazione.txt", "Incollato 2×3 da nato_il: «2026-02-30», «12.50», «abc» "
                + "/ «2001-01-01», «3.00», «7».\nCelle non valide: 2 → suggerimenti: «" + "Data non valida…» e «Valore "
                + "non valido: serve un numero intero.»\nDopo l'incolla: " + dopoIncolla
                + "\nCtrl+S con celle non valide: onConfirm NON chiamato.\nCtrl+Z: tutte le 6 celle tornano come "
                + "prima, nessuna modifica in sospeso.\nIncollato di nuovo, corrette a mano (2026-02-28, 42): "
                + "Conferma abilitata, onConfirm con " + confermate.get(0).size() + " modifiche.\n");
    }
}
