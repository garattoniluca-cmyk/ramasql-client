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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;

/**
 * T6.6 (lato componente): dopo ogni «Applica» l'editor confronta ciò che ha chiesto con la tabella riletta (qui
 * restituita dall'applicatore finto) e mostra «✔ verificato sul server», le informazioni (indice creato dal server per
 * la FK) oppure le differenze (azione diversa, FK assente).
 */
@Tag("step6")
@Tag("ui")
class T66VerificationTest {

    private static final String CATALOG = "ramasql_test_t66";
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

    /** Su {@code libri} nuda: UNIQUE su isbn, indice su id_editore, FK verso editori. */
    private void buildLibriKeys() {
        onEdt(() -> {
            IndexesTab idx = h.editor.indexesTab();
            idx.addIndex();
            idx.setCell(1, IndexesTab.NAME, "uq_libri_isbn");
            idx.setCell(1, IndexesTab.KIND, IndexKind.UNIQUE);
            idx.addColumn("isbn");
            idx.addIndex();
            idx.setCell(2, IndexesTab.NAME, "ix_libri_editore");
            idx.addColumn("id_editore");
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_libri_editori");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "editori");
        });
    }

    @Test
    void verificatoSulServer() {
        h = open(libriNuda(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        buildLibriKeys();
        onEdt(() -> h.editor.apply());
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertTrue(outcome.contains("✔ verificato sul server: indici e chiavi esterne di «libri» sono quelli chiesti"),
                outcome);
        assertTrue(fromEdt(() -> h.editor.lastVerification().conforming()));
        h.tab(TableEditor.TAB_INDEXES);
        h.screenshot("step6", "T6.6-verificato");
        writeText("step6", "T6.6-verificato", "SQL:\n" + String.join(";\n", h.applier.requests.get(0).statements())
                + ";\n\nESITO:\n" + outcome + "\n");
    }

    @Test
    void informazioneIndiceCreatoDalServerPerLaFk() {
        h = open(libriNuda(CATALOG), CATALOG, MYSQL, biblioteca(CATALOG));
        onEdt(() -> {
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_libri_editori");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "editori");
        });
        // il server aggiunge da sé l'indice sulla colonna della FK
        h.applier.answer = r -> TableApplier.ApplyOutcome.success(r.statements(),
                r.edited().addIndex(IndexDef.index("fk_libri_editori", "id_editore")).withOrdinalPositions());
        onEdt(() -> h.editor.apply());
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertTrue(outcome.contains("✔ verificato sul server"), outcome);
        assertTrue(outcome.contains("ℹ Il server ha creato da sé l'indice «fk_libri_editori»"), outcome);
        h.screenshot("step6", "T6.6-informazione");
    }

    @Test
    void differenzeRispettoAlChiesto() {
        h = open(libriNuda(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        buildLibriKeys();
        onEdt(() -> h.editor.foreignKeysTab().setCell(0, ForeignKeysTab.ON_DELETE, "CASCADE"));
        // il server riporta un'altra azione e non ha l'indice UNIQUE
        h.applier.answer = r -> {
            TableDef actual = r.edited()
                    .changeForeignKey("fk_libri_editori", f -> f.withOnDelete(FkAction.SET_NULL))
                    .removeIndex("uq_libri_isbn");
            return TableApplier.ApplyOutcome.success(r.statements(), actual.withOrdinalPositions());
        };
        onEdt(() -> h.editor.apply());
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertFalse(outcome.contains("✔ verificato sul server"), outcome);
        assertTrue(outcome.contains("✘ Il server non ha applicato esattamente ciò che è stato chiesto per «libri»: 2 "
                + "differenze"), outcome);
        assertTrue(outcome.contains("✘ L'indice «uq_libri_isbn» (isbn) non c'è sul server."), outcome);
        assertTrue(outcome.contains("✘ La chiave esterna «fk_libri_editori» ha un'altra azione"), outcome);
        assertFalse(fromEdt(() -> h.editor.lastVerification().conforming()));
        h.screenshot("step6", "T6.6-differenze");
        List<String> lines = new ArrayList<>(outcome.lines().toList());
        writeText("step6", "T6.6-differenze", String.join("\n", lines) + "\n");
    }
}
