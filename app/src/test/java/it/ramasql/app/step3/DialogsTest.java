/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step3;

import static it.ramasql.app.step3.Step3Ui.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.List;

import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.app.navigator.CreateCatalogDialog;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.CollationInfo;
import it.ramasql.core.sqlgen.TreeScripts;

/**
 * Le finestre dello Step 3 senza server: anteprima con conferma normale e rafforzata (Esegui abilitato solo con il
 * nome esatto, Invio non conferma una distruzione, chiudere = Annulla), «Nuovo catalogo» (predefiniti giusti,
 * collation che segue il charset, validazione del nome).
 */
@Tag("step3")
@Tag("ui")
class DialogsTest {

    @BeforeAll
    static void setup() {
        Step3Ui.setup();
    }

    private static PreviewDialog preview(SqlScript script) {
        return new PreviewDialog(null, script, ConfirmationPolicy.evaluate(script));
    }

    @Test
    void anteprimaConfermaRafforzataEsegueSoloConIlNomeEsatto() {
        onEdt(() -> {
            PreviewDialog d = preview(TreeScripts.dropTable("scuola", "libri"));
            try {
                assertTrue(d.requiresTypedConfirmation());
                assertEquals("libri", d.confirmation().typeToConfirm());
                assertEquals("DROP TABLE `scuola`.`libri`;", d.sqlText());
                assertEquals("Elimina", d.executeButton().getText(), "verbo d'azione sul pulsante");
                assertTrue(labels(d).contains("Distruttiva"), "pillola del rischio");
                assertFalse(d.executeButton().isEnabled());
                assertNull(d.getRootPane().getDefaultButton(), "Invio non conferma un'operazione distruttiva");
                for (String wrong : List.of("", "libr", "Libri", "libri2", "CONFERMO")) {
                    d.confirmationField().setText(wrong);
                    assertFalse(d.executeButton().isEnabled(), wrong);
                    d.executeButton().doClick();
                    assertEquals(PreviewDialog.Decision.CANCEL, d.decision());
                }
                d.confirmationField().setText("  libri ");
                assertTrue(d.executeButton().isEnabled(), "spazi ai lati ignorati");
                d.executeButton().doClick();
                assertEquals(PreviewDialog.Decision.EXECUTE, d.decision());
                assertFalse(d.isDisplayable(), "la finestra si chiude");
            } finally {
                d.dispose();
            }
            PreviewDialog truncate = preview(TreeScripts.truncateTable("scuola", "prestiti"));
            try {
                assertEquals("Svuota", truncate.executeButton().getText());
                assertEquals("prestiti", truncate.confirmation().typeToConfirm());
            } finally {
                truncate.dispose();
            }
        });
    }

    @Test
    void anteprimaNormaleCopiaAnnullaEChiusura() {
        onEdt(() -> {
            SqlScript rename = TreeScripts.renameTable("scuola", "libri", "volumi");
            PreviewDialog d = preview(rename);
            try {
                assertFalse(d.requiresTypedConfirmation());
                assertTrue(d.executeButton().isEnabled());
                assertSame(d.executeButton(), d.getRootPane().getDefaultButton());
                assertEquals("Esegui", d.executeButton().getText());
                assertTrue(labels(d).contains("Modifica"), "pillola del rischio");
                d.copyButton().doClick();
                assertEquals(PreviewDialog.Decision.COPY, d.decision());
            } finally {
                d.dispose();
            }
            PreviewDialog closed = preview(rename);
            closed.dispose();   // chiusa con la X o con Esc senza scegliere
            assertEquals(PreviewDialog.Decision.CANCEL, closed.decision());
            PreviewDialog cancelled = preview(rename);
            cancelled.cancelButton().doClick();
            assertEquals(PreviewDialog.Decision.CANCEL, cancelled.decision());
        });
    }

    @Test
    void nuovoCatalogoPredefinitiCollationEValidazione() {
        List<CollationInfo> collations = List.of(
                new CollationInfo("utf8mb4_unicode_ci", "utf8mb4", false),
                new CollationInfo("utf8mb4_general_ci", "utf8mb4", true),
                new CollationInfo("latin1_bin", "latin1", false),
                new CollationInfo("latin1_swedish_ci", "latin1", true));
        onEdt(() -> {
            CreateCatalogDialog d = new CreateCatalogDialog(null, collations, "utf8mb4", "utf8mb4_general_ci");
            try {
                assertEquals("utf8mb4", d.charsetBox().getSelectedItem());
                assertEquals("utf8mb4_general_ci", d.collationBox().getSelectedItem());
                assertEquals(2, d.collationBox().getItemCount(), "solo le collation del charset scelto");
                d.charsetBox().setSelectedItem("latin1");
                assertEquals("latin1_swedish_ci", d.collationBox().getSelectedItem(), "predefinita del charset");
                d.buttons().confirmButton().doClick();
                assertNull(d.result(), "nome vuoto: non si prosegue");
                assertFalse(d.errorLabel().getText().isBlank());
                d.nameField().setText("x".repeat(CreateCatalogDialog.MAX_NAME_LENGTH + 1));
                d.buttons().confirmButton().doClick();
                assertNull(d.result(), "nome troppo lungo");
                d.nameField().setText("  compiti_3a ");
                d.collationBox().setSelectedItem("latin1_bin");
                d.buttons().confirmButton().doClick();
                assertEquals(new CreateCatalogDialog.Choice("compiti_3a", "latin1", "latin1_bin"), d.result());
            } finally {
                d.dispose();
            }
            assertEquals("CREATE DATABASE `compiti_3a` CHARACTER SET latin1 COLLATE latin1_bin;",
                    TreeScripts.createCatalog("compiti_3a", "latin1", "latin1_bin").text());
        });
    }

    /**
     * La frase «Stai per …» e il verbo del pulsante si riconoscono anche con commenti iniziali, con
     * {@code DROP TEMPORARY TABLE} e dentro {@code SET STATEMENT … FOR} (li decide il core, {@code ConfirmationPolicy}).
     */
    @Test
    void laFraseDellaConfermaRiconosceCommentiETemporary() {
        onEdt(() -> {
            String[][] cases = {
                {"-- pulizia\nDROP TEMPORARY TABLE tmp", "eliminare la tabella", "Elimina", "tmp"},
                {"/* vecchia */ drop table if exists `c`.`libri`", "eliminare la tabella", "Elimina", "libri"},
                {"# via\nDROP VIEW v_aperti", "eliminare la vista", "Elimina", "v_aperti"},
                {"-- a\n/* b */ DROP SCHEMA ramasql_test_x", "eliminare il catalogo", "Elimina", "ramasql_test_x"},
                {"/* svuota */ TRUNCATE TABLE prestiti", "svuotare", "Svuota", "prestiti"},
                {"SET STATEMENT max_statement_time=1 FOR DROP TABLE soci", "eliminare la tabella", "Elimina", "soci"},
                {"-- tutto\nDELETE FROM soci", "cancellare dati in modo definitivo", "Esegui", "soci"}};
            for (String[] c : cases) {
                PreviewDialog d = preview(SqlScript.of("t", "Editor SQL", c[0]));
                try {
                    assertTrue(d.requiresTypedConfirmation(), c[0]);
                    assertEquals(c[3], d.confirmation().typeToConfirm(), c[0]);
                    String sentence = labels(d).stream().filter(t -> t.contains("Stai per")).findFirst().orElse("");
                    assertTrue(sentence.contains(c[1]), c[0] + " → " + sentence);
                    assertEquals(c[2], d.executeButton().getText(), c[0]);
                } finally {
                    d.dispose();
                }
            }
        });
    }

    private static List<String> labels(Container c) {
        List<String> out = new java.util.ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof JLabel l && l.getText() != null) {
                out.add(l.getText());
            }
            if (child instanceof Container sub) {
                out.addAll(labels(sub));
            }
        }
        return out;
    }
}
