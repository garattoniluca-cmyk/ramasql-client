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

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.RowChange;
import it.ramasql.core.sqlgen.DmlGenerator;

/**
 * T4.21 (parte componente): scheda record sullo stesso modello della griglia — inserire un socio completo e
 * modificarne un altro dalla scheda → stesse modifiche in sospeso della griglia, stesso contatore, stessa Conferma
 * (Ctrl+S) con le stesse istruzioni; una cella non valida blocca la Conferma anche qui.
 */
@Tag("step4")
@Tag("ui")
class T421RecordFormTest {

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    private static java.awt.Component find(java.awt.Container c, String name) {
        for (java.awt.Component child : c.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof java.awt.Container inner && find(inner, name) != null) {
                return find(inner, name);
            }
        }
        return null;
    }

    @Test
    void schedaRecordCondivideLeModificheConLaGriglia() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        List<List<RowChange>> confermate = new ArrayList<>();
        onEdt(() -> grid.setOnConfirm(confermate::add));
        JFrame frame = GridTestSupport.host(grid, 900, 520);
        RecordForm form = grid.recordForm();

        onEdt(() -> grid.selectBlock(2, 2, 2, 2));
        onEdt(() -> grid.showRecordForm(true));
        assertTrue(fromEdt(grid::isRecordFormShown));
        assertEquals("Record 3 di 12", fromEdt(form::positionText));
        assertEquals("T103", fromEdt(() -> form.field(1).getText()));
        JLabel label = fromEdt(() -> (JLabel) find(form, "recordForm.label.quota"));
        assertTrue(label.getText().contains("<b>quota</b>") && label.getText().contains("DECIMAL(6,2)"), label.getText());
        assertFalse(fromEdt(() -> form.field(0).isEditable()), "AUTO_INCREMENT: non si scrive");

        // modifica di un socio esistente dalla scheda
        onEdt(() -> form.field(3).setText("carla@esempio.it"));
        assertEquals("carla@esempio.it", GridTestSupport.cells(grid, 2, 2, 3, 3).get(0).get(0), "la griglia la vede");
        assertEquals("Riga modificata, in sospeso: si scrive con Conferma.", fromEdt(form::rowStateText));

        // inserimento di un socio completo: «Nuovo» → la riga d'inserimento
        onEdt(form::newRecord);
        assertEquals("Record nuovo", fromEdt(form::positionText));
        onEdt(() -> {
            form.field(1).setText("T400");
            form.field(2).setText("Sara Nuova");
            form.field(3).setText("sara@esempio.it");
            form.field(4).setText("2009-09-09");
            form.field(5).setText("abc");   // quota non valida
            form.field(6).setText("5");
        });
        assertEquals("Record 13 di 13", fromEdt(form::positionText), "il primo carattere ha creato la riga");
        assertEquals("1 inserimento · 1 modifica · 0 eliminazioni in sospeso", fromEdt(grid::counterText));
        assertEquals("error", fromEdt(() -> form.field(5).getClientProperty("JComponent.outline")));
        assertEquals("Valore non valido: serve un numero (con il punto come separatore decimale).",
                fromEdt(() -> form.field(5).getToolTipText()));
        assertFalse(fromEdt(grid::isConfirmEnabled), "anche dalla scheda, la cella non valida blocca la Conferma");
        onEdt(() -> form.field(5).setText("12.00"));
        assertTrue(fromEdt(grid::isConfirmEnabled));
        GridTestSupport.screenshot(frame, "T4.21-scheda-record.png");

        // navigazione e ritorno alla griglia: la riga nuova è in griglia, selezionata
        onEdt(form::previous);
        assertEquals("Record 12 di 13", fromEdt(form::positionText));
        onEdt(form::next);
        onEdt(() -> grid.showRecordForm(false));
        assertEquals(12, (int) fromEdt(() -> grid.table().getSelectedRow()));
        assertEquals(List.of("T400", "Sara Nuova", "sara@esempio.it", "2009-09-09", "12.00", "5"),
                GridTestSupport.cells(grid, 12, 12, 1, 6).get(0));
        GridTestSupport.screenshot(frame, "T4.21-griglia-dopo-scheda.png");

        // Ctrl+S (dal pannello, vale anche dalla scheda) → stesse istruzioni della griglia
        onEdt(() -> grid.showRecordForm(true));
        onEdt(() -> grid.getActionMap().get(DataGrid.ACTION_CONFIRM).actionPerformed(null));
        assertEquals(1, confermate.size());
        List<String> sql = DmlGenerator.generate(Fixtures.soci(), confermate.get(0));
        assertEquals(List.of("UPDATE `biblioteca`.`soci` SET `email` = 'carla@esempio.it' WHERE `id` = 3",
                "INSERT INTO `biblioteca`.`soci` (`tessera`, `nome`, `email`, `nato_il`, `quota`, `punti`) VALUES "
                        + "('T400', 'Sara Nuova', 'sara@esempio.it', '2009-09-09', 12.00, 5)"), sql);

        // Elimina dalla scheda: la riga resta visibile, marcata, e i campi non si modificano più
        onEdt(() -> form.showRow(0));
        onEdt(form::deleteRecord);
        assertEquals("Riga da eliminare alla Conferma.", fromEdt(form::rowStateText));
        assertFalse(fromEdt(() -> form.field(2).isEditable()));
        assertEquals("1 inserimento · 1 modifica · 1 eliminazione in sospeso", fromEdt(grid::counterText));
        GridTestSupport.writeText("T4.21-scheda-record.txt", "Scheda aperta sulla riga 3 («Record 3 di 12»); email "
                + "modificata → visibile in griglia.\n«Nuovo» → «Record nuovo»; 6 campi scritti → «Record 13 di 13»; "
                + "quota «abc» → contorno rosso, Conferma disabilitata; corretta in 12.00 → abilitata.\n"
                + "Ritorno alla griglia: riga 13 selezionata con i valori della scheda.\nCtrl+S dalla scheda → "
                + "onConfirm → " + String.join(" ; ", sql) + "\nElimina dalla scheda sulla riga 1 → «Riga da eliminare "
                + "alla Conferma.», contatore " + fromEdt(grid::counterText) + "\n");
    }
}
