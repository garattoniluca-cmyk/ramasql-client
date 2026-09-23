/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import static it.ramasql.app.tableeditor.TableEditorTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.TableDef;

/**
 * T6.9 (lato interfaccia): su una tabella di {@code biblioteca_myisam} la scheda Chiavi esterne è disattivata, con la
 * spiegazione e la proposta «Converti in InnoDB», che imposta l'engine nella scheda Opzioni e riattiva la scheda.
 */
@Tag("step6")
@Tag("ui")
class T69MyIsamTest {

    private static final String CATALOG = "ramasql_test_biblioteca_myisam";
    private Harness h;

    @BeforeAll
    static void laf() {
        setupLookAndFeel();
    }

    @AfterEach
    void close() {
        if (h != null) {
            h.dispose();
        }
    }

    @Test
    void schedaDisattivataConSpiegazioneEProposta() {
        TableDef libriMyIsam = libriNuda(CATALOG).withEngine("MyISAM");
        h = open(libriMyIsam, CATALOG, MARIADB,
                new FakeTables().with(editori(CATALOG).withEngine("MyISAM"), libriMyIsam));

        ForeignKeysTab fks = fromEdt(() -> h.editor.foreignKeysTab());
        assertEquals(ForeignKeysTab.CARD_MYISAM, fromEdt(fks::visibleCard));
        String text = fromEdt(fks::myisamText);
        assertTrue(text.contains("La tabella «libri» usa l'engine MyISAM, che non supporta le chiavi esterne"), text);
        assertTrue(text.contains("converti la tabella in InnoDB"), text);
        assertTrue(fromEdt(() -> fks.convertButton().isShowing() || fks.convertButton().isVisible()));
        assertEquals(it.ramasql.app.theme.Tokens.TEXT_TERTIARY,
                fromEdt(() -> h.editor.tabs().getForegroundAt(TableEditor.TAB_FOREIGN_KEYS)), "titolo in grigio");
        onEdt(fks::addForeignKey);
        assertTrue(fromEdt(() -> h.editor.editedTable().foreignKeys().isEmpty()), "niente FK su MyISAM");
        h.tab(TableEditor.TAB_FOREIGN_KEYS);
        h.screenshot("step6", "T6.9-myisam");

        onEdt(() -> fks.convertButton().doClick());
        assertEquals("InnoDB", fromEdt(() -> h.editor.editedTable().engine()));
        assertEquals("InnoDB", fromEdt(() -> h.editor.optionsTab().selectedEngine()), "engine impostato in Opzioni");
        assertEquals(List.of("ALTER TABLE `ramasql_test_biblioteca_myisam`.`libri` ENGINE=InnoDB"),
                fromEdt(() -> h.editor.previewStatements()));
        assertEquals(ForeignKeysTab.CARD_EDITOR, fromEdt(fks::visibleCard), "scheda di nuovo modificabile");
        h.screenshot("step6", "T6.9-convertita");

        // ora la FK verso editori (ancora MyISAM) si può scrivere, ma l'avviso dice che l'altra tabella è MyISAM
        onEdt(() -> {
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "editori");
        });
        String notes = fromEdt(fks::notesText);
        assertTrue(notes.startsWith("⚠ ") && notes.contains("editori"), notes);
        writeText("step6", "T6.9", "SPIEGAZIONE:\n" + text + "\n\nDOPO «Converti in InnoDB»:\n"
                + String.join("\n", fromEdt(() -> h.editor.previewStatements())) + "\n\nAVVISO FK:\n" + notes + "\n");
    }
}
