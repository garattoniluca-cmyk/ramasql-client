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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.data.RowChange;
import it.ramasql.core.sqlgen.DmlGenerator;
import it.ramasql.core.sqlgen.DmlGenerator.RowStatement;

/**
 * T4.10 e T4.11 (parte componente, senza server): cosa la griglia consegna alla Conferma e come mostra l'esito che
 * l'integrazione le riporta. L'esecuzione vera (anteprima, SqlExecutor, errori 1062 del server) è dell'integrazione:
 * qui l'esito è simulato chiamando {@link DataGrid#markSaved} / {@link DataGrid#markError} come farà l'esecutore.
 */
@Tag("step4")
@Tag("ui")
class T410T411ConfirmFlowTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    @Test
    void treModificheUnInserimentoUnEliminazioneDannoCinqueIstruzioni() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> grid.setOnConfirm(confermate::add));
        JFrame frame = GridTestSupport.host(grid, 1100, 480);

        GridTestSupport.type(grid, 0, 2, "Anna Rossi Bianchi");
        GridTestSupport.type(grid, 1, 5, "99.90");
        GridTestSupport.type(grid, 2, 3, "carla@esempio.it");
        GridTestSupport.type(grid, 12, 1, "T200");                 // riga d'inserimento
        GridTestSupport.type(grid, 12, 2, "Zeno Nuovo");
        onEdt(() -> grid.selectRows(8, 8));
        action(grid, DataGrid.ACTION_DELETE_ROWS);                 // riga 9 da eliminare
        assertEquals("1 inserimento · 3 modifiche · 1 eliminazione in sospeso", fromEdt(grid::counterText));
        assertEquals(PendingChanges.RowKind.DELETED, fromEdt(() -> grid.model().pending().kind(8)));
        onEdt(() -> grid.selectBlock(0, 0, 0, 0));
        JLabel deleted = fromEdt(() -> (JLabel) grid.table().prepareRenderer(grid.table().getCellRenderer(8, 2), 8, 2));
        assertEquals(GridCellRenderer.DELETED, deleted.getBackground());
        GridTestSupport.screenshot(frame, "T4.10-cinque-modifiche.png");

        onEdt(grid::confirm);
        assertEquals(1, confermate.size());
        List<RowStatement> statements = DmlGenerator.generateWithRows(Fixtures.soci(), confermate.get(0));
        assertEquals(5, statements.size());
        List<String> sql = statements.stream().map(RowStatement::sql).toList();
        assertEquals("DELETE FROM `biblioteca`.`soci` WHERE `id` = 9", sql.get(0));
        assertEquals("UPDATE `biblioteca`.`soci` SET `nome` = 'Anna Rossi Bianchi' WHERE `id` = 1", sql.get(1));
        assertEquals("INSERT INTO `biblioteca`.`soci` (`tessera`, `nome`) VALUES ('T200', 'Zeno Nuovo')", sql.get(4));
        assertFalse(sql.stream().anyMatch(s -> s.toUpperCase(Locale.ROOT).matches(".*(TRANSACTION|COMMIT|ROLLBACK).*")));

        // esito riportato dall'«esecutore»: tutto riuscito, l'id AUTO_INCREMENT letto dal server compare in griglia
        onEdt(() -> {
            for (RowStatement s : statements) {
                grid.markSaved(s.change().rowId(),
                        s.change().kind() == RowChange.Kind.INSERT ? Map.of("id", "13") : Map.of());
            }
        });
        assertFalse(fromEdt(grid::hasPending));
        assertEquals(12, (int) fromEdt(() -> grid.model().dataRowCount()), "12 - 1 eliminata + 1 inserita");
        assertEquals(List.of("13", "T200", "Zeno Nuovo"), GridTestSupport.cells(grid, 11, 11, 0, 2).get(0));
        assertEquals("Nessuna modifica in sospeso", fromEdt(grid::counterText));
        GridTestSupport.screenshot(frame, "T4.10-dopo-esecuzione.png");
        GridTestSupport.writeText("T4.10-conferma.txt", "3 modifiche + 1 inserimento + 1 eliminazione → onConfirm con "
                + confermate.get(0).size() + " RowChange → " + sql.size() + " istruzioni (nessuna di transazione):\n"
                + String.join(";\n", sql) + ";\nEsito simulato: markSaved su tutte, id 13 alla riga nuova → in griglia "
                + "«13 | T200 | Zeno Nuovo», nessuna modifica in sospeso.\n"
                + "Da fare nell'integrazione: anteprima, esecuzione con SqlExecutor, rilettura dei dati sul server.\n");
    }

    @Test
    void erroreASecondaRigaLaMarcaELaTerzaRestaInSospeso() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> grid.setOnConfirm(confermate::add));
        JFrame frame = GridTestSupport.host(grid, 1100, 480);
        String[] tessere = {"T300", "T101", "T302"};   // la seconda è già usata (duplicato)
        for (int i = 0; i < 3; i++) {
            GridTestSupport.type(grid, 12 + i, 1, tessere[i]);
            GridTestSupport.type(grid, 12 + i, 2, "Socio " + i);
        }
        onEdt(grid::confirm);
        List<RowStatement> statements = DmlGenerator.generateWithRows(Fixtures.soci(), confermate.get(0));
        assertEquals(3, statements.size());

        // l'«esecutore» si ferma al primo errore: 1ª salvata, 2ª in errore, 3ª non eseguita
        String errore = "Duplicate entry 'T101' for key 'uq_tessera' (1062) — esiste già un socio con questa tessera.";
        onEdt(() -> {
            grid.markSaved(statements.get(0).change().rowId(), Map.of("id", "13"));
            grid.markError(statements.get(1).change().rowId(), errore);
        });
        assertEquals(PendingChanges.State.SALVATA, fromEdt(() -> grid.model().pending().state(12)));
        assertEquals("13", GridTestSupport.cells(grid, 12, 12, 0, 0).get(0).get(0));
        assertEquals(PendingChanges.State.IN_ERRORE, fromEdt(() -> grid.model().pending().state(13)));
        assertEquals(PendingChanges.State.PENDENTE, fromEdt(() -> grid.model().pending().state(14)));
        assertEquals("Riga nuova non salvata: " + errore, fromEdt(grid::notice));
        JLabel cell = fromEdt(() -> (JLabel) grid.table().prepareRenderer(grid.table().getCellRenderer(13, 2), 13, 2));
        assertEquals(GridCellRenderer.ERROR_ROW, cell.getBackground());
        assertEquals("Non salvata: " + errore, cell.getToolTipText(), "il messaggio si legge sulla riga");
        assertEquals("2 inserimenti · 0 modifiche · 0 eliminazioni in sospeso", fromEdt(grid::counterText));
        GridTestSupport.screenshot(frame, "T4.11-riga-in-errore.png");

        // corretta la 2ª, la Conferma consegna le due righe restanti
        GridTestSupport.type(grid, 13, 1, "T301");
        assertEquals(PendingChanges.State.PENDENTE, fromEdt(() -> grid.model().pending().state(13)),
                "correggere la riga toglie il segno d'errore");
        onEdt(grid::confirm);
        assertEquals(2, confermate.size());
        List<String> sql = DmlGenerator.generate(Fixtures.soci(), confermate.get(1));
        assertEquals(List.of("INSERT INTO `biblioteca`.`soci` (`tessera`, `nome`) VALUES ('T301', 'Socio 1')",
                "INSERT INTO `biblioteca`.`soci` (`tessera`, `nome`) VALUES ('T302', 'Socio 2')"), sql);
        assertTrue(fromEdt(grid::hasPending), "finché l'esito non torna, restano in sospeso");
        GridTestSupport.writeText("T4.11-errore.txt", "3 inserimenti, il 2º con tessera duplicata.\nEsito simulato: "
                + "1º markSaved (id 13), 2º markError(1062 spiegato), 3º non eseguito.\nStati: SALVATA / IN_ERRORE / "
                + "PENDENTE; avviso: " + "Riga nuova non salvata: " + errore + "\nCorretta la tessera (T301): seconda "
                + "Conferma → " + String.join(" ; ", sql) + "\nDa fare nell'integrazione: esecutore che si ferma al "
                + "primo errore e spiegazione in italiano del codice 1062 (dal classificatore d'errori).\n");
    }
}
