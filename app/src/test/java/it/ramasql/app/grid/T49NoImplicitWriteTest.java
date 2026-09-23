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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.RowChange;
import it.ramasql.core.metadata.TableDef;

/**
 * T4.9 (parte componente) — <b>nessuna scrittura implicita</b>: 3 righe inserite scrivendo nella riga d'inserimento,
 * poi cambio di riga, ordinamento e cambio pagina senza Conferma → {@code onConfirm} mai chiamato, il fornitore di dati
 * non è interrogato (le modifiche non si perdono), contatore «3 inserimenti…». T4.12 (parte locale) — Scarta
 * ripristina i dati e la domanda alla chiusura; dopo, pagine e ordinamento tornano a funzionare.
 */
@Tag("step4")
@Tag("ui")
class T49NoImplicitWriteTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    private static JButton button(DataGrid grid, String name) {
        return fromEdt(() -> findButton(grid, name));
    }

    private static JButton findButton(java.awt.Container c, String name) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JButton b && name.equals(b.getName())) {
                return b;
            }
            if (child instanceof java.awt.Container inner) {
                JButton found = findButton(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void cambiareRigaOrdinareECambiarePaginaNonScrivonoNulla() throws Exception {
        TableDef soci = Fixtures.soci();
        InMemoryGridDataSource source = new InMemoryGridDataSource(soci.columns(), Fixtures.sociRows());
        FakeGridPrompts prompts = new FakeGridPrompts();
        DataGrid grid = fromEdt(() -> DataGrid.forTable(soci, source, 5, prompts));
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> grid.setOnConfirm(confermate::add));
        JFrame frame = GridTestSupport.host(grid, 1100, 380);
        assertEquals(1, source.loadCount());
        assertTrue(fromEdt(grid::hasNextPage), "12 righe, pagine da 5");

        // tre righe nuove scritte nella riga d'inserimento (indice 5, poi 6, poi 7)
        for (int i = 0; i < 3; i++) {
            int row = 5 + i;
            GridTestSupport.type(grid, row, 1, "N" + i);
            GridTestSupport.type(grid, row, 2, "Nuovo socio " + i);
        }
        assertEquals("3 inserimenti · 0 modifiche · 0 eliminazioni in sospeso", fromEdt(grid::counterText));

        // cambiare riga
        onEdt(() -> grid.table().changeSelection(0, 2, false, false));
        onEdt(() -> grid.table().changeSelection(7, 1, false, false));
        // ordinare
        assertFalse(fromEdt(() -> grid.sortBy(new GridDataSource.SortOrder("nome", true))));
        assertEquals("Prima conferma o scarta le modifiche in sospeso: cambiare pagina o ordinare non salva nulla.",
                fromEdt(grid::notice));
        // cambiare pagina
        assertFalse(fromEdt(() -> button(grid, "dataGrid.page.next").isEnabled()), "pulsante disabilitato");
        assertEquals("Prima conferma o scarta le modifiche in sospeso: cambiare pagina o ordinare non salva nulla.",
                fromEdt(() -> button(grid, "dataGrid.page.next").getToolTipText()));
        assertFalse(fromEdt(grid::nextPage), "e se richiesto lo stesso, rifiutato");
        assertFalse(fromEdt(grid::reload));

        assertTrue(confermate.isEmpty(), "nessuna Conferma implicita");
        assertEquals(1, source.loadCount(), "nessuna nuova lettura: le righe in sospeso non si perdono");
        assertEquals(0, (int) fromEdt(grid::pageIndex));
        assertEquals("3 inserimenti · 0 modifiche · 0 eliminazioni in sospeso", fromEdt(grid::counterText));
        assertEquals(List.of(List.of("N0", "Nuovo socio 0"), List.of("N1", "Nuovo socio 1"),
                List.of("N2", "Nuovo socio 2")), GridTestSupport.cells(grid, 5, 7, 1, 2));
        assertTrue(fromEdt(grid::isConfirmEnabled));
        assertTrue(fromEdt(grid::isDiscardEnabled));
        GridTestSupport.screenshot(frame, "T4.9-tre-inserimenti-in-sospeso.png");

        // T4.12: domanda alla chiusura, poi Scarta
        assertTrue(fromEdt(grid::hasPending));
        String domanda = fromEdt(grid::closeQuestion);
        assertEquals("Ci sono modifiche in sospeso (3 inserimenti · 0 modifiche · 0 eliminazioni).\n"
                + "Conferma, scarta o resta?", domanda);
        assertEquals(List.of("Conferma", "Scarta", "Resta"), DataGrid.closeQuestionOptions());
        prompts.confirmAnswer = false;   // «Annulla» nella domanda di Scarta: nulla cambia
        assertFalse(fromEdt(grid::discard));
        assertTrue(fromEdt(grid::hasPending));
        prompts.confirmAnswer = true;
        assertTrue(fromEdt(grid::discard));
        assertTrue(prompts.shown.get(1).startsWith("confirm: Scartare le modifiche? | Le modifiche in sospeso "
                + "(3 inserimenti · 0 modifiche · 0 eliminazioni) andranno perse."), prompts.shown.toString());
        assertFalse(fromEdt(grid::hasPending));
        assertEquals("Nessuna modifica in sospeso", fromEdt(grid::counterText));
        assertEquals(6, (int) fromEdt(() -> grid.table().getRowCount()), "5 righe lette + riga d'inserimento");
        assertEquals(Fixtures.sociRows().subList(0, 5), GridTestSupport.cells(grid, 0, 4, 0, 6));
        assertTrue(confermate.isEmpty());

        // senza modifiche in sospeso: pagina successiva e ordinamento chiedono al fornitore
        assertTrue(fromEdt(grid::nextPage));
        assertEquals(2, source.loadCount());
        assertEquals(1, (int) fromEdt(grid::pageIndex));
        assertEquals("6", GridTestSupport.cells(grid, 0, 0, 0, 0).get(0).get(0));
        assertTrue(fromEdt(() -> grid.sortBy(new GridDataSource.SortOrder("nome", false))));
        assertEquals(3, source.loadCount());
        assertEquals(0, (int) fromEdt(grid::pageIndex), "ordinare riparte dalla prima pagina");
        assertEquals("Paolo Bassi", GridTestSupport.cells(grid, 0, 0, 2, 2).get(0).get(0));
        assertEquals("nome ▼", fromEdt(() -> grid.table().getColumnModel().getColumn(2).getHeaderValue()));
        GridTestSupport.screenshot(frame, "T4.9-dopo-scarta-ordinato.png");

        GridTestSupport.writeText("T4.9-nessuna-scrittura-implicita.txt", "3 righe scritte nella riga d'inserimento; "
                + "poi cambio riga (0 → 7), ordinamento per nome, pagina successiva, rilettura.\n"
                + "onConfirm chiamato: 0 volte. Letture dal fornitore: 1 (solo l'apertura).\n"
                + "Ordinamento/pagina rifiutati con avviso: «Prima conferma o scarta le modifiche in sospeso: cambiare "
                + "pagina o ordinare non salva nulla.»\nContatore: 3 inserimenti · 0 modifiche · 0 eliminazioni in sospeso\n"
                + "T4.12 — domanda alla chiusura: «" + domanda.replace('\n', ' ')
                + "»\nScarta (dopo conferma della domanda): righe come lette, contatore "
                + "«Nessuna modifica in sospeso».\nPoi pagina 2 (letture: 2) e ordinamento nome Z→A (letture: 3).\n");
    }
}
