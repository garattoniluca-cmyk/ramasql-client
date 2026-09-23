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

import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.sqlgen.PrecheckWarning.Code;

/**
 * Scheda Indici (Step 6): crea INDEX e UNIQUE multi-colonna con l'ordine scelto (su/giù), modifica delle colonne di un
 * indice esistente (= DROP + ADD nello stesso ALTER in anteprima), eliminazione, avviso di indice duplicato accanto alla
 * riga, una sola chiave primaria, indice senza colonne bloccato.
 */
@Tag("step6")
@Tag("ui")
class IndexesTabTest {

    private static final String CATALOG = "ramasql_test_idx";
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
    void indiceMultiColonnaConOrdineEUnique() {
        h = open(libriNuda(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            IndexesTab idx = h.editor.indexesTab();
            idx.addIndex();
            idx.setCell(1, IndexesTab.NAME, "ix_libri_titolo_anno");
            idx.addColumn("anno");
            idx.addColumn("titolo");
            idx.moveColumn(1, -1);                                   // titolo sale al primo posto
            idx.addIndex();
            idx.setCell(2, IndexesTab.NAME, "uq_libri_isbn");
            idx.setCell(2, IndexesTab.KIND, IndexKind.UNIQUE);
            idx.addColumn("isbn");
        });
        assertEquals(List.of("titolo", "anno"),
                fromEdt(() -> h.editor.editedTable().index("ix_libri_titolo_anno").orElseThrow().columns()));
        assertEquals(List.of("ALTER TABLE `ramasql_test_idx`.`libri`\n"
                + "  ADD INDEX `ix_libri_titolo_anno` (`titolo`, `anno`),\n"
                + "  ADD UNIQUE INDEX `uq_libri_isbn` (`isbn`)"), fromEdt(() -> h.editor.previewStatements()));
        onEdt(() -> h.editor.indexesTab().select(1));
        h.tab(TableEditor.TAB_INDEXES);
        h.screenshot("step6", "indici-multicolonna");

        onEdt(() -> h.editor.apply());
        assertTrue(fromEdt(() -> h.editor.outcomeText()).contains("✔ verificato sul server"));
    }

    @Test
    void modificaColonneDiUnIndiceEsistenteDropPiuAddNelloStessoAlter() {
        h = open(autori(CATALOG), CATALOG, MYSQL, biblioteca(CATALOG));
        onEdt(() -> {
            IndexesTab idx = h.editor.indexesTab();
            idx.select(1);                                           // ix_autori_cognome (cognome, nome)
            idx.moveColumn(1, -1);                                   // → (nome, cognome)
        });
        List<String> preview = fromEdt(() -> h.editor.previewStatements());
        assertEquals(List.of("ALTER TABLE `ramasql_test_idx`.`autori`\n"
                + "  DROP INDEX `ix_autori_cognome`,\n"
                + "  ADD INDEX `ix_autori_cognome` (`nome`, `cognome`)"), preview);
        h.tab(TableEditor.TAB_INDEXES);
        h.screenshot("step6", "indici-modifica");

        onEdt(() -> {
            h.editor.revert();
            h.editor.indexesTab().select(1);
            h.editor.indexesTab().removeColumn(1);                   // → (cognome)
        });
        assertEquals(List.of("ALTER TABLE `ramasql_test_idx`.`autori`\n"
                + "  DROP INDEX `ix_autori_cognome`,\n"
                + "  ADD INDEX `ix_autori_cognome` (`cognome`)"), fromEdt(() -> h.editor.previewStatements()));

        onEdt(() -> {
            h.editor.revert();
            h.editor.indexesTab().select(1);
            h.editor.indexesTab().removeSelected();
        });
        assertEquals(List.of("ALTER TABLE `ramasql_test_idx`.`autori` DROP INDEX `ix_autori_cognome`"),
                fromEdt(() -> h.editor.previewStatements()));
        writeText("step6", "indici-modifica", "ORDINE CAMBIATO:\n" + String.join("\n", preview) + "\n");
    }

    @Test
    void indiceDuplicatoAvvisatoAccantoAllaRiga() {
        h = open(libri(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            IndexesTab idx = h.editor.indexesTab();
            idx.addIndex();
            idx.setCell(3, IndexesTab.NAME, "ix_isbn");
            idx.addColumn("isbn");
        });
        @SuppressWarnings("unchecked")
        List<Checks.Problem> mark = (List<Checks.Problem>) fromEdt(
                () -> h.editor.indexesTab().table().getModel().getValueAt(3, IndexesTab.MARK));
        assertEquals(Code.INDEX_DUPLICATE, mark.get(0).code());
        String notes = fromEdt(() -> h.editor.indexesTab().notesText());
        assertTrue(notes.startsWith("⚠ ") && notes.contains("uq_libri_isbn"), notes);
        assertTrue(fromEdt(() -> h.editor.tabs().getTitleAt(TableEditor.TAB_INDEXES)).endsWith("⚠"));
        h.tab(TableEditor.TAB_INDEXES);
        h.screenshot("step6", "indici-duplicato");
        assertTrue(h.dataCheck.queries.isEmpty(), "l'avviso nasce dai metadati, senza server");
        onEdt(() -> h.editor.apply());
        assertEquals(1, h.applier.requests.size(), "un avviso non blocca");
    }

    @Test
    void unaSolaChiavePrimariaEIndiceVuotoBloccato() {
        h = open(libriNuda(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            h.editor.indexesTab().addIndex();
            h.editor.indexesTab().setCell(1, IndexesTab.KIND, IndexKind.PRIMARY);
        });
        assertTrue(fromEdt(() -> h.editor.noticeText()).contains("ha già una chiave primaria"));
        assertEquals(IndexKind.INDEX, fromEdt(() -> h.editor.editedTable().indexes().get(1).kind()));
        String notes = fromEdt(() -> h.editor.indexesTab().notesText());
        assertTrue(notes.contains("✘ L'indice «idx_1» non ha colonne"), notes);
        onEdt(() -> h.editor.apply());
        assertEquals(1, h.prompts.errors.size());
        assertTrue(h.applier.requests.isEmpty());
    }
}
