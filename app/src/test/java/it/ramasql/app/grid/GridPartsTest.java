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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;

/** I pezzi della griglia presi da soli: fornitore in memoria, CSV, Transferable, casi limite degli appunti. */
@Tag("step4")
@Tag("ui")
class GridPartsTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    @Test
    void fornitoreInMemoriaPaginaEOrdinaComeIlServer() {
        List<ColumnDef> cols = List.of(Fixtures.col("n", "INT", null, true, false, null, 1),
                Fixtures.col("t", "VARCHAR", "10", true, false, null, 2));
        InMemoryGridDataSource source = new InMemoryGridDataSource(cols, List.of(Arrays.asList("10", "b"),
                Arrays.asList("9", "a"), Arrays.asList(null, "c"), Arrays.asList("100", null)));
        GridDataSource.Page first = source.load(0, 3, new GridDataSource.SortOrder("n", true));
        assertEquals(List.of(Arrays.asList(null, "c"), Arrays.asList("9", "a"), Arrays.asList("10", "b")), first.rows(),
                "NULL per primi, numeri per valore (9 < 10 < 100), non come testo");
        assertTrue(first.hasMore());
        GridDataSource.Page second = source.load(1, 3, new GridDataSource.SortOrder("n", true));
        assertEquals(List.of(Arrays.asList("100", null)), second.rows());
        assertFalse(second.hasMore());
        assertEquals("c", source.load(0, 10, new GridDataSource.SortOrder("T", false)).rows().get(0).get(1));
        assertEquals(3, source.loadCount());
    }

    @Test
    void campiCsv() {
        ColumnDef decimal = Fixtures.col("q", "DECIMAL", "6,2", true, false, null, 1);
        ColumnDef integer = Fixtures.col("i", "INT", null, true, false, null, 1);
        ColumnDef text = Fixtures.col("t", "VARCHAR", "20", true, false, null, 1);
        assertEquals("12,50", CsvExporter.field("12.50", decimal));
        assertEquals("-0,5", CsvExporter.field("-0.5", decimal));
        assertEquals("12", CsvExporter.field("12", integer));
        assertEquals("12.50", CsvExporter.field("12.50", text), "il testo non si tocca");
        assertEquals("", CsvExporter.field(null, text));
        assertEquals("\"\"", CsvExporter.field("", text));
        assertEquals("\" spazio\"", CsvExporter.field(" spazio", text));
        assertEquals("\"a;b\"", CsvExporter.field("a;b", text));
    }

    @Test
    void ilTransferableDichiaraIlFlavorGrezzoMaSiLeggeComeTesto() throws Exception {
        BlockTransferable t = new BlockTransferable("a\t\"x\ny\"\r\n");
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        assertArrayEquals(new DataFlavor[] {windows ? BlockTransferable.WINDOWS_UNICODE_TEXT : DataFlavor.stringFlavor},
                t.getTransferDataFlavors(), "nessun flavor text/plain dichiarato (riporterebbe il CR+LF)");
        assertTrue(t.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertEquals("a\t\"x\ny\"\r\n", t.getTransferData(DataFlavor.stringFlavor));
        try (InputStream in = (InputStream) t.getTransferData(BlockTransferable.WINDOWS_UNICODE_TEXT)) {
            assertEquals("a\t\"x\ny\"\r\n\0", new String(in.readAllBytes(), StandardCharsets.UTF_16LE),
                    "byte UTF-16LE così come sono, con il terminatore");
        }
    }

    @Test
    void casiLimiteDegliAppunti() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.autori(), Fixtures.autoriRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        onEdt(() -> grid.setClipboard(clipboard));

        // copiare la sola riga d'inserimento non mette nulla negli appunti
        onEdt(() -> grid.selectBlock(3, 0, 3, 3));
        assertNull(fromEdt(() -> grid.copySelection(false)));
        assertNull(clipboard.getContents(null));

        // appunti vuoti: Ctrl+V non fa nulla e lo dice
        action(grid, DataGrid.ACTION_PASTE);
        assertEquals("Negli appunti non c'è testo da incollare.", fromEdt(grid::notice));
        assertFalse(fromEdt(grid::hasPending));

        // Ctrl+Z senza un incolla da annullare
        action(grid, DataGrid.ACTION_UNDO_PASTE);
        assertEquals("Non c'è un incolla da annullare.", fromEdt(grid::notice));

        // un valore solo su più celle che comprendono la riga d'inserimento: riempie solo le righe esistenti
        clipboard.setContents(new java.awt.datatransfer.StringSelection("Francia"), null);
        onEdt(() -> grid.selectBlock(1, 3, 3, 3));
        action(grid, DataGrid.ACTION_PASTE);
        assertEquals(List.of(List.of("Francia"), List.of("Francia")), GridTestSupport.cells(grid, 1, 2, 3, 3));
        assertEquals(3, (int) fromEdt(() -> grid.model().dataRowCount()), "nessuna riga nuova");

        // restituire una riga eliminata; una riga nuova eliminata sparisce
        onEdt(() -> grid.selectRows(0, 0));
        action(grid, DataGrid.ACTION_DELETE_ROWS);
        assertEquals(1, (int) fromEdt(() -> grid.model().pending().deleteCount()));
        action(grid, DataGrid.ACTION_RESTORE_ROWS);
        assertEquals(0, (int) fromEdt(() -> grid.model().pending().deleteCount()));
        GridTestSupport.type(grid, 3, 1, "Nuovo");
        assertEquals(4, (int) fromEdt(() -> grid.model().dataRowCount()));
        onEdt(() -> grid.selectRows(3, 3));
        action(grid, DataGrid.ACTION_DELETE_ROWS);
        assertEquals(3, (int) fromEdt(() -> grid.model().dataRowCount()));
        assertEquals(0, (int) fromEdt(() -> grid.model().pending().insertCount()));
    }
}
