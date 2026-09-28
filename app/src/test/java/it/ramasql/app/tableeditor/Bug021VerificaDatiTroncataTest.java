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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code BUG-021} (lato interfaccia) — quando «Verifica dati» mostra solo le prime righe, l'esito lo dice: «Trovate
 * <b>almeno</b> 200 righe orfane… Qui sotto ci sono solo le prime 200: sul server ce ne sono altre»; senza
 * troncamento il testo resta quello di prima.
 */
@Tag("step12")
@Tag("ui")
class Bug021VerificaDatiTroncataTest {

    private static final String CATALOG = "ramasql_test_b21";
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

    private static List<List<String>> righe(int n) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(List.of(String.valueOf(1000 + i), "7", String.valueOf(9000 + i), "2026-03-02", "2026-03-20"));
        }
        return out;
    }

    @Test
    void righeOrfaneTroncateLoDice() {
        h = open(prestitiNuda(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_prestiti_soci");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "soci");
        });
        h.dataCheck.answer = new DataCheck.DataCheckResult(
                List.of("id", "id_libro", "id_socio", "data_prestito", "data_reso"), righe(200), null, true);
        onEdt(() -> h.editor.foreignKeysTab().verifySelectedData());
        DataCheckPanel panel = fromEdt(() -> h.editor.foreignKeysTab().dataCheck());
        assertEquals(200, (int) fromEdt(panel::rowCount));
        String status = fromEdt(panel::statusText);
        assertTrue(status.startsWith("⚠ Trovate almeno 200 righe orfane"), status);
        assertTrue(status.contains("errore 1452"), status);
        assertTrue(status.contains("Qui sotto ci sono solo le prime 200: sul server ce ne sono altre."), status);
        h.tab(TableEditor.TAB_FOREIGN_KEYS);
        h.screenshot("step12", "BUG-021-orfane-troncate");

        // lo stesso controllo con meno righe del limite: il testo di sempre, senza «almeno»
        h.dataCheck.answer = new DataCheck.DataCheckResult(
                List.of("id", "id_libro", "id_socio", "data_prestito", "data_reso"), righe(3), null);
        onEdt(() -> h.editor.foreignKeysTab().verifySelectedData());
        String completo = fromEdt(panel::statusText);
        assertTrue(completo.startsWith("⚠ Trovate 3 righe orfane"), completo);
        assertFalse(completo.contains("sul server ce ne sono altre"), completo);
    }

    @Test
    void duplicatiTroncatiLoDice() {
        h = open(soci(CATALOG), CATALOG, MYSQL, biblioteca(CATALOG));
        onEdt(() -> h.editor.columnsTab().setCell(4, ColumnsTab.UQ, true));
        int row = fromEdt(() -> h.editor.editedTable().indexes().size() - 1);
        onEdt(() -> h.editor.indexesTab().select(row));
        List<List<String>> doppioni = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            doppioni.add(List.of("utente" + i + "@example.org", "2"));
        }
        // qui il troncamento viene dal limite di righe dell'esecutore (150), prima dei 200 della verifica
        h.dataCheck.answer = new DataCheck.DataCheckResult(List.of("email", "occorrenze"), doppioni, null, true);
        onEdt(() -> h.editor.indexesTab().verifySelectedData());
        DataCheckPanel panel = fromEdt(() -> h.editor.indexesTab().dataCheck());
        String status = fromEdt(panel::statusText);
        assertTrue(status.startsWith("⚠ Trovati almeno 150 valori ripetuti"), status);
        assertTrue(status.contains("errore 1062"), status);
        assertTrue(status.contains("solo le prime 150"), status);
    }
}
