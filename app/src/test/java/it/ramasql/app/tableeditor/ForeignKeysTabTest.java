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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.FkAction;

/**
 * Scheda Chiavi esterne (Step 6): creazione con la coppia di colonne proposta, le 4 azioni per ON DELETE e ON UPDATE
 * (16 combinazioni), modifica = due istruzioni «prima DROP, poi ADD» in anteprima, eliminazione, FK composta,
 * autoreferenziale.
 */
@Tag("step6")
@Tag("ui")
class ForeignKeysTabTest {

    private static final String CATALOG = "ramasql_test_fk";
    private static final String LIBRI = "`ramasql_test_fk`.`libri`";
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
    void nuovaChiaveEsternaConLeQuattroAzioni() {
        h = open(libriNuda(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_libri_editori");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "editori");     // coppia proposta: id_editore → id
        });
        assertEquals(List.of("ALTER TABLE " + LIBRI + " ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY "
                + "(`id_editore`) REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT"),
                fromEdt(() -> h.editor.previewStatements()));
        assertEquals("", fromEdt(() -> h.editor.foreignKeysTab().notesText()), "tipi e indici compatibili");
        h.tab(TableEditor.TAB_FOREIGN_KEYS);
        h.screenshot("step6", "fk-nuova");

        List<String> seen = new ArrayList<>();
        for (FkAction onDelete : FkAction.values()) {
            for (FkAction onUpdate : FkAction.values()) {
                onEdt(() -> {
                    h.editor.foreignKeysTab().setCell(0, ForeignKeysTab.ON_DELETE, onDelete.sql());
                    h.editor.foreignKeysTab().setCell(0, ForeignKeysTab.ON_UPDATE, onUpdate.sql());
                });
                String sql = fromEdt(() -> h.editor.previewStatements().get(0));
                assertTrue(sql.endsWith(" ON DELETE " + onDelete.sql() + " ON UPDATE " + onUpdate.sql()), sql);
                seen.add(sql);
            }
        }
        assertEquals(16, seen.stream().distinct().count(), "16 combinazioni distinte");
        writeText("step6", "fk-azioni", String.join("\n", seen) + "\n");
    }

    @Test
    void modificaEDueIstruzioniPrimaDropPoiAdd() {
        h = open(libri(CATALOG), CATALOG, MYSQL, biblioteca(CATALOG));
        onEdt(() -> {
            h.editor.foreignKeysTab().select(0);
            h.editor.foreignKeysTab().setCell(0, ForeignKeysTab.ON_DELETE, "SET NULL");
        });
        List<String> preview = fromEdt(() -> h.editor.previewStatements());
        assertEquals(List.of("ALTER TABLE " + LIBRI + " DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE " + LIBRI + " ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) REFERENCES "
                        + "`editori` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT"), preview);
        h.tab(TableEditor.TAB_SQL);
        h.screenshot("step6", "fk-modifica-sql");

        onEdt(() -> {
            h.editor.revert();
            h.editor.foreignKeysTab().select(0);
            h.editor.foreignKeysTab().removeSelected();
        });
        assertEquals(List.of("ALTER TABLE " + LIBRI + " DROP FOREIGN KEY `fk_libri_editori`"),
                fromEdt(() -> h.editor.previewStatements()));
        writeText("step6", "fk-modifica", String.join(";\n", preview) + ";\n");
    }

    @Test
    void coppieDiColonneEFkAutoreferenziale() {
        h = open(soci(CATALOG).addColumn(it.ramasql.core.metadata.ColumnDef.of("presentato_da", "INT")
                .withUnsigned(true).asNew()), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "soci");         // autoreferenziale: nessuna proposta ovvia
            fks.addPair("presentato_da", "id");
        });
        assertEquals(List.of("presentato_da"), fromEdt(() -> h.editor.editedTable().foreignKeys().get(0).columns()));
        String sql = fromEdt(() -> h.editor.previewStatements().get(0));
        assertTrue(sql.contains("ADD CONSTRAINT `fk_soci_1` FOREIGN KEY (`presentato_da`) REFERENCES `soci` (`id`)"),
                sql);
        onEdt(() -> h.editor.foreignKeysTab().setPair(0, null, "tessera"));
        String notes = fromEdt(() -> h.editor.foreignKeysTab().notesText());
        assertTrue(notes.contains("⚠"), "INT UNSIGNED contro CHAR(8): avviso di tipo — " + notes);
        onEdt(() -> h.editor.foreignKeysTab().removePair(0));
        assertTrue(fromEdt(() -> h.editor.foreignKeysTab().notesText()).contains("✘"), "senza coppie: errore");
    }
}
