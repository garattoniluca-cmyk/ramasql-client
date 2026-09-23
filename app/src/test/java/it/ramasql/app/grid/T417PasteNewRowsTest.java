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
import java.util.Locale;

import javax.swing.JFrame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.RowChange;
import it.ramasql.core.sqlgen.DmlGenerator;

/**
 * T4.17 (parte componente): un blocco 20×3 «copiato da Excel» (testo tabulato con CR+LF, come lo scrive Excel)
 * incollato con la cella attiva sulla riga d'inserimento di {@code autori} → 20 righe in sospeso → Conferma → 20
 * {@code INSERT} dal {@link DmlGenerator}, senza istruzioni di transazione. Nulla è eseguito: la griglia non scrive.
 */
@Tag("step4")
@Tag("ui")
class T417PasteNewRowsTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    static String excelVentiPerTre() {
        StringBuilder sb = new StringBuilder();
        String[][] autori = {{"Umberto", "Eco", "Italia"}, {"Natalia", "Ginzburg", "Italia"},
            {"Gabriel", "García Márquez", "Colombia"}, {"José", "Saramago", "Portogallo"}, {"Wisława", "Szymborska", ""}};
        for (int i = 0; i < 20; i++) {
            String[] a = autori[i % autori.length];
            sb.append(a[0]).append(' ').append(i + 1).append('\t').append(a[1]).append('\t').append(a[2]).append("\r\n");
        }
        return sb.toString();
    }

    @Test
    void ventiPerTreSullaRigaDInserimentoDiventaVentiInsert() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.autori(), Fixtures.autoriRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> {
            grid.setClipboard(clipboard);
            grid.setOnConfirm(confermate::add);
        });
        JFrame frame = GridTestSupport.host(grid, 900, 820);
        clipboard.setContents(new StringSelection(excelVentiPerTre()), null);

        onEdt(() -> grid.selectBlock(3, 1, 3, 1));   // riga d'inserimento, colonna «nome»
        assertTrue(fromEdt(() -> grid.model().isInsertRow(3)));
        action(grid, DataGrid.ACTION_PASTE);

        assertEquals(23, (int) fromEdt(() -> grid.model().dataRowCount()), "3 righe lette + 20 nuove");
        assertEquals(24, (int) fromEdt(() -> grid.table().getRowCount()), "e la riga d'inserimento resta in fondo");
        assertEquals(20, (int) fromEdt(() -> grid.model().pending().insertCount()));
        assertEquals("20 inserimenti · 0 modifiche · 0 eliminazioni in sospeso", fromEdt(grid::counterText));
        assertEquals(List.of("Gabriel 3", "García Márquez", "Colombia"), GridTestSupport.cells(grid, 5, 5, 1, 3).get(0));
        assertEquals(new DataGrid.CellRange(3, 22, 1, 3), fromEdt(grid::selection), "il blocco incollato resta selezionato");
        assertTrue(fromEdt(grid::notice).contains("20 righe nuove"), fromEdt(grid::notice));
        assertTrue(fromEdt(grid::isConfirmEnabled));
        assertTrue(confermate.isEmpty(), "incollare non conferma nulla");
        GridTestSupport.screenshot(frame, "T4.17-incolla-20x3.png");

        onEdt(() -> grid.getActionMap().get(DataGrid.ACTION_CONFIRM).actionPerformed(null));   // Ctrl+S
        assertEquals(1, confermate.size());
        List<RowChange> changes = confermate.get(0);
        assertEquals(20, changes.size());
        assertTrue(changes.stream().allMatch(c -> c.kind() == RowChange.Kind.INSERT));
        List<String> sql = DmlGenerator.generate(Fixtures.autori(), changes);
        assertEquals(20, sql.size());
        assertEquals("INSERT INTO `biblioteca`.`autori` (`nome`, `cognome`, `nazione`) VALUES ('Umberto 1', 'Eco', 'Italia')",
                sql.get(0));
        assertEquals("INSERT INTO `biblioteca`.`autori` (`nome`, `cognome`) VALUES ('Wisława 5', 'Szymborska')", sql.get(4),
                "cella vuota da Excel su riga nuova = colonna omessa (DEFAULT)");
        for (String s : sql) {
            String u = s.toUpperCase(Locale.ROOT);
            assertTrue(u.startsWith("INSERT INTO "), s);
            assertFalse(u.contains("TRANSACTION") || u.contains("COMMIT") || u.contains("ROLLBACK") || u.contains("BEGIN"), s);
            assertFalse(s.contains("`id`"), "AUTO_INCREMENT omesso: " + s);
        }
        assertEquals(20, (int) fromEdt(() -> grid.model().pending().insertCount()),
                "la griglia non esegue: finché l'integrazione non riporta l'esito, le righe restano in sospeso");
        GridTestSupport.writeText("T4.17-incolla-20x3.txt", "Testo «da Excel» (20×3, CR+LF) incollato sulla riga "
                + "d'inserimento, colonna nome.\nRighe in sospeso: 20; contatore: " + fromEdt(grid::counterText)
                + "\nAvviso: " + fromEdt(grid::notice) + "\nonConfirm chiamato 1 volta con " + changes.size()
                + " RowChange INSERT.\nSQL da DmlGenerator (" + sql.size() + " istruzioni, nessuna di transazione):\n"
                + String.join(";\n", sql) + ";\n");
    }
}
