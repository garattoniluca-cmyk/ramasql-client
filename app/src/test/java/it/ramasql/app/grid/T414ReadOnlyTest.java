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

import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;

/**
 * T4.14 (parte componente): tabella senza PK né UNIQUE e vista → stessa griglia in sola lettura, con una riga di
 * spiegazione; niente riga d'inserimento, niente modifica/incolla/taglia/Canc, niente Conferma/Scarta; la copia sì.
 */
@Tag("step4")
@Tag("ui")
class T414ReadOnlyTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    private static List<String> visibleNames(Container c) {
        List<String> out = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child.getName() != null && child.isVisible() && child.getParent() != null) {
                out.add(child.getName());
            }
            if (child instanceof Container inner) {
                out.addAll(visibleNames(inner));
            }
        }
        return out;
    }

    private static void assertOnlyCopy(DataGrid grid, Clipboard clipboard) throws Exception {
        assertFalse(fromEdt(grid::isEditable));
        assertEquals(2, (int) fromEdt(() -> grid.table().getRowCount()), "nessuna riga d'inserimento");
        assertFalse(fromEdt(() -> grid.table().isCellEditable(0, 1)));
        assertFalse(fromEdt(() -> grid.table().editCellAt(0, 1)), "non si scrive in cella");
        List<List<String>> prima = GridTestSupport.cells(grid, 0, 1, 0, 1);

        clipboard.setContents(new StringSelection("x\ty\r\nz\tw\r\n"), null);
        onEdt(() -> grid.selectBlock(0, 0, 1, 1));
        action(grid, DataGrid.ACTION_PASTE);
        action(grid, DataGrid.ACTION_CLEAR);
        action(grid, DataGrid.ACTION_SET_NULL);
        action(grid, DataGrid.ACTION_DELETE_ROWS);
        action(grid, DataGrid.ACTION_CUT);
        assertEquals("x\ty\r\nz\tw\r\n", clipboard.getData(DataFlavor.stringFlavor), "Ctrl+X non ha copiato né svuotato");
        assertEquals(prima, GridTestSupport.cells(grid, 0, 1, 0, 1), "nessuna modifica possibile");
        assertFalse(fromEdt(grid::hasPending));

        action(grid, DataGrid.ACTION_COPY);
        assertEquals(prima.get(0).get(0) + "\t" + prima.get(0).get(1) + "\r\n" + prima.get(1).get(0) + "\t"
                + prima.get(1).get(1) + "\r\n", clipboard.getData(DataFlavor.stringFlavor), "la copia funziona");
        List<String> names = fromEdt(() -> visibleNames(grid));
        assertFalse(names.contains("dataGrid.confirm"), names.toString());
        assertFalse(names.contains("dataGrid.discard"), names.toString());
        assertFalse(names.contains("dataGrid.counter"), names.toString());
        assertTrue(names.contains("dataGrid.export"), names.toString());
    }

    @Test
    void tabellaSenzaChiaveInSolaLetturaConSpiegazione() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.registro(), Fixtures.registroRows(), 1000, new FakeGridPrompts());
        Clipboard clipboard = new Clipboard("test");
        onEdt(() -> grid.setClipboard(clipboard));
        JFrame frame = GridTestSupport.host(grid, 900, 260);
        assertEquals("Questa tabella non ha una chiave primaria né un indice UNIQUE: il client non saprebbe quale riga "
                + "modificare. Puoi copiare i dati.", fromEdt(grid::readOnlyExplanation));
        assertTrue(fromEdt(() -> visibleNames(grid)).contains("dataGrid.readOnly"), "spiegazione visibile");
        assertOnlyCopy(grid, clipboard);
        GridTestSupport.screenshot(frame, "T4.14-tabella-senza-chiave.png");
    }

    @Test
    void vistaERisultatiDiQueryInSolaLettura() throws Exception {
        List<ColumnDef> columns = List.of(Fixtures.col("titolo", "VARCHAR", "100", true, false, null, 1),
                Fixtures.col("prestiti", "BIGINT", null, false, false, null, 2));
        List<List<String>> rows = List.of(List.of("Il barone rampante", "4"), List.of("La Storia", "2"));
        Clipboard clipboard = new Clipboard("test");
        DataGrid view = fromEdt(() -> DataGrid.readOnly(columns, new InMemoryGridDataSource(columns, rows), 1000,
                DataGrid.viewExplanation(), new FakeGridPrompts()));
        onEdt(() -> view.setClipboard(clipboard));
        JFrame frame = GridTestSupport.host(view, 900, 260);
        assertEquals("Le viste si leggono soltanto: i dati si modificano nelle tabelle da cui la vista li prende. "
                + "Puoi copiarli.", fromEdt(view::readOnlyExplanation));
        assertOnlyCopy(view, clipboard);
        GridTestSupport.screenshot(frame, "T4.14-vista.png");

        DataGrid results = fromEdt(() -> DataGrid.readOnly(columns, new InMemoryGridDataSource(columns, rows), 1000,
                null, new FakeGridPrompts()));
        onEdt(() -> results.setClipboard(clipboard));
        assertNull(fromEdt(results::readOnlyExplanation));
        assertFalse(fromEdt(() -> visibleNames(results)).contains("dataGrid.readOnly"));
        assertOnlyCopy(results, clipboard);
        JLabel first = fromEdt(() -> (JLabel) results.table().prepareRenderer(results.table().getCellRenderer(0, 0), 0, 0));
        assertEquals("Il barone rampante", first.getText());
        GridTestSupport.writeText("T4.14-sola-lettura.txt", "Tabella «registro» senza PK/UNIQUE, vista e risultati di "
                + "query: nessuna riga d'inserimento, editCellAt rifiutato, Ctrl+V/Canc/Imposta NULL/Elimina righe/"
                + "Ctrl+X senza effetto, Conferma/Scarta/contatore assenti; Ctrl+C copia il blocco.\nSpiegazione "
                + "tabella: " + it.ramasql.app.Texts.get("grid.readOnly.noKey") + "\nSpiegazione vista: " + DataGrid.viewExplanation()
                + "\nRisultati di query: nessuna spiegazione (riga non mostrata).\n");
    }
}
