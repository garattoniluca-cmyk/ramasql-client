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

import static it.ramasql.app.grid.GridTestSupport.fromEdt;
import static it.ramasql.app.grid.GridTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.PendingChanges.PasteResult;
import it.ramasql.core.data.RowChange;
import it.ramasql.core.sqlgen.DmlGenerator;

/**
 * T4.18 (parte componente): blocco 2×2 incollato <b>sopra</b> celle esistenti → modifiche in sospeso (→ UPDATE); un
 * valore su una selezione 5×1 → 5 celle riempite; blocco più largo della tabella → eccedenza scartata con avviso;
 * colonna AUTO_INCREMENT saltata con avviso.
 */
@Tag("step4")
@Tag("ui")
class T418PasteOverExistingTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    private static PasteResult paste(DataGrid grid, Clipboard clipboard, String text) {
        clipboard.setContents(new StringSelection(text), null);
        return fromEdt(grid::paste);
    }

    @Test
    void incollaSopraRiempieEScartaLEccedenza() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> {
            grid.setClipboard(clipboard);
            grid.setOnConfirm(confermate::add);
        });
        JFrame frame = GridTestSupport.host(grid, 1100, 480);
        StringBuilder log = new StringBuilder();

        // (1) 2×2 sopra nome/email delle righe 2 e 3
        onEdt(() -> grid.selectBlock(1, 2, 1, 2));
        PasteResult r1 = paste(grid, clipboard, "Bruno B.\tbruno@esempio.it\r\nCarla V.\tcarla@esempio.it\r\n");
        assertEquals(new PasteResult(4, 0, 0, 0), r1);
        assertEquals(List.of(List.of("Bruno B.", "bruno@esempio.it"), List.of("Carla V.", "carla@esempio.it")),
                GridTestSupport.cells(grid, 1, 2, 2, 3));
        assertEquals(2, (int) fromEdt(() -> grid.model().pending().updateCount()));
        assertEquals(0, (int) fromEdt(() -> grid.model().pending().insertCount()), "sopra righe esistenti: nessuna riga nuova");
        log.append("(1) 2×2 sopra righe 2-3 → ").append(r1).append(" · ").append(fromEdt(grid::counterText)).append('\n');

        // (2) un valore solo su una selezione 5×1 (colonna punti, righe 7-11)
        onEdt(() -> grid.selectBlock(6, 6, 10, 6));
        PasteResult r2 = paste(grid, clipboard, "99\r\n");
        assertEquals(5, r2.cellsWritten());
        assertEquals(List.of(List.of("99"), List.of("99"), List.of("99"), List.of("99"), List.of("99")),
                GridTestSupport.cells(grid, 6, 10, 6, 6));
        assertEquals("110", GridTestSupport.cells(grid, 11, 11, 6, 6).get(0).get(0), "la riga 12 non è toccata");
        log.append("(2) valore «99» su selezione 5×1 → ").append(r2).append('\n');

        // (3) blocco di 4 colonne a partire da «quota» (penultima): 2 colonne scartate con avviso
        onEdt(() -> grid.selectBlock(0, 5, 0, 5));
        PasteResult r3 = paste(grid, clipboard, "1.00\t5\tx\ty\r\n");
        assertEquals(new PasteResult(2, 0, 2, 0), r3);
        assertEquals(List.of(List.of("1.00", "5")), GridTestSupport.cells(grid, 0, 0, 5, 6));
        assertTrue(fromEdt(grid::notice).contains("2 colonne in più scartate"), fromEdt(grid::notice));
        log.append("(3) blocco 1×4 da «quota» → ").append(r3).append(" · avviso: ").append(fromEdt(grid::notice)).append('\n');

        // (4) blocco che parte dalla colonna AUTO_INCREMENT: «id» saltata con avviso, «tessera» scritta
        onEdt(() -> grid.selectBlock(11, 0, 11, 0));
        PasteResult r4 = paste(grid, clipboard, "999\tT999\r\n");
        assertEquals(new PasteResult(1, 0, 0, 1), r4);
        assertEquals(List.of(List.of("12", "T999")), GridTestSupport.cells(grid, 11, 11, 0, 1));
        assertTrue(fromEdt(grid::notice).contains("Colonne saltate perché le calcola il server: id"), fromEdt(grid::notice));
        log.append("(4) blocco da «id» (AUTO_INCREMENT) → ").append(r4).append(" · avviso: ")
                .append(fromEdt(grid::notice)).append('\n');
        GridTestSupport.screenshot(frame, "T4.18-incolla-sopra.png");

        // Conferma → solo UPDATE, con le sole colonne cambiate e il WHERE sulla chiave primaria
        onEdt(grid::confirm);
        assertEquals(1, confermate.size());
        List<String> sql = DmlGenerator.generate(Fixtures.soci(), confermate.get(0));
        assertEquals(9, sql.size(), "righe 1, 2, 3, 7, 8, 9, 10, 11, 12 modificate");
        assertTrue(sql.stream().allMatch(s -> s.startsWith("UPDATE `biblioteca`.`soci` SET ")), sql.toString());
        assertTrue(sql.contains("UPDATE `biblioteca`.`soci` SET `nome` = 'Bruno B.', `email` = 'bruno@esempio.it' WHERE `id` = 2"),
                sql.toString());
        log.append("\nSQL generato alla Conferma (").append(sql.size()).append(" UPDATE):\n")
                .append(String.join(";\n", sql)).append(";\n");
        GridTestSupport.writeText("T4.18-incolla-sopra.txt", log.toString());
    }
}
