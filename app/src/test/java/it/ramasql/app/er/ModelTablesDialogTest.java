/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.app.theme.RamaSqlLaf;

/**
 * La scelta delle tabelle di «Nuovo modello dal catalogo…» ({@code DESIGN.md} §3.11): tutte spuntate in partenza,
 * <em>Tutte</em>/<em>Nessuna</em>, almeno una per creare il modello, ogni comando con la sua spiegazione.
 */
@Tag("step11")
class ModelTablesDialogTest {

    @Test
    void tutteInPartenzaAlmenoUnaPerCreare() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RamaSqlLaf.setup();
            ModelTablesDialog d = new ModelTablesDialog(null, "biblioteca", List.of("autori", "editori", "libri"));
            try {
                assertEquals(List.of("autori", "editori", "libri"), d.selection(), "tutte spuntate");
                d.setAll(false);
                assertEquals(List.of(), d.selection());
                d.confirm();
                assertEquals("Scegli almeno una tabella.", d.errorText());
                assertTrue(d.isDisplayable(), "la finestra resta aperta");
                d.setChecked("libri", true);
                d.setChecked("autori", true);
                assertEquals(List.of("autori", "libri"), d.selection(), "nell'ordine dell'elenco");
                d.setAll(true);
                d.setChecked("editori", false);
                assertEquals(List.of("autori", "libri"), d.selection());
                assertFalse(d.buttons().confirmButton().getToolTipText().isBlank());
                assertEquals("Crea il modello", d.buttons().confirmButton().getText());
            } finally {
                d.dispose();
            }
        });
    }
}
