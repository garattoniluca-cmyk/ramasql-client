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

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.FkPrecheck;
import it.ramasql.core.sqlgen.IndexPrecheck;

/**
 * T6.7 e T6.8 (lato interfaccia): «Verifica dati» su una chiave esterna mostra la query delle righe orfane e le righe
 * che il {@link DataCheck} (qui finto, poi il server) restituisce; su un UNIQUE la query dei duplicati. Se si
 * procede comunque, l'errore del server (1452, 1062) arriva spiegato; corretti i dati, la FK si crea e risulta
 * verificata.
 */
@Tag("step6")
@Tag("ui")
class T67T68DataCheckTest {

    private static final String CATALOG = "ramasql_test_t67";
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
    void t67RigheOrfaneDiPrestitiVersoSoci() {
        h = open(prestitiNuda(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_prestiti_soci");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "soci");
        });
        TableDef edited = fromEdt(() -> h.editor.editedTable());
        String expectedQuery = FkPrecheck.orphanRowsQuery(prestitiNuda(CATALOG), edited.foreignKeys().get(0));
        assertEquals("SELECT figlia.* FROM `ramasql_test_t67`.`prestiti` AS figlia LEFT JOIN `ramasql_test_t67`.`soci` "
                + "AS riferita ON riferita.`id` = figlia.`id_socio` WHERE figlia.`id_socio` IS NOT NULL AND "
                + "riferita.`id` IS NULL", expectedQuery);
        assertTrue(fromEdt(() -> h.editor.foreignKeysTab().verifyDataButton().isEnabled()));

        h.dataCheck.answer = new DataCheck.DataCheckResult(
                List.of("id", "id_libro", "id_socio", "data_prestito", "data_reso"),
                List.of(List.of("41", "7", "901", "2026-03-02", "2026-03-20"),
                        java.util.Arrays.asList("42", "8", "902", "2026-03-05", null),
                        java.util.Arrays.asList("43", "9", "903", "2026-04-11", null)), null);
        onEdt(() -> h.editor.foreignKeysTab().verifySelectedData());

        assertEquals(List.of(expectedQuery), h.dataCheck.queries, "la query mostrata è quella eseguita");
        DataCheckPanel panel = fromEdt(() -> h.editor.foreignKeysTab().dataCheck());
        assertEquals(expectedQuery, fromEdt(panel::queryText));
        assertEquals(3, (int) fromEdt(panel::rowCount), "le 3 righe orfane sono elencate");
        assertEquals("NULL", fromEdt(() -> panel.valueAt(1, 4)));
        String status = fromEdt(panel::statusText);
        assertTrue(status.startsWith("⚠ Trovate 3 righe orfane"), status);
        assertTrue(status.contains("errore 1452"), status);
        h.tab(TableEditor.TAB_FOREIGN_KEYS);
        h.screenshot("step6", "T6.7-righe-orfane");

        // si esegue comunque: il server rifiuta con 1452, spiegato; la tabella riletta è quella di prima
        h.applier.answer = r -> TableApplier.ApplyOutcome.failure(List.of(), r.statements(),
                new TableApplier.ApplyError(1452, "Cannot add or update a child row: a foreign key constraint fails",
                        "Ci sono righe che riferiscono valori che non esistono nella tabella riferita."),
                prestitiNuda(CATALOG));
        onEdt(() -> h.editor.apply());
        String failed = fromEdt(() -> h.editor.outcomeText());
        assertTrue(failed.contains("Errore 1452: Cannot add or update a child row"), failed);
        assertTrue(failed.contains("non esistono nella tabella riferita"), failed);
        assertTrue(fromEdt(() -> h.editor.editedTable().foreignKeys().isEmpty()), "ricaricata: la FK non c'è");
        h.screenshot("step6", "T6.7-errore-1452");

        // corrette le righe: la FK si crea e risulta verificata sul server
        h.applier.answer = r -> TableApplier.ApplyOutcome.success(r.statements(), r.edited().withOrdinalPositions());
        onEdt(() -> {
            h.editor.foreignKeysTab().addForeignKey();
            h.editor.foreignKeysTab().setCell(0, ForeignKeysTab.NAME, "fk_prestiti_soci");
            h.editor.foreignKeysTab().setCell(0, ForeignKeysTab.REF_TABLE, "soci");
        });
        h.dataCheck.answer = new DataCheck.DataCheckResult(List.of("id"), List.of(), null);
        onEdt(() -> h.editor.foreignKeysTab().verifySelectedData());
        assertTrue(fromEdt(panel::statusText).startsWith("✔ Nessuna riga orfana"));
        onEdt(() -> h.editor.apply());
        String ok = fromEdt(() -> h.editor.outcomeText());
        assertTrue(ok.contains("✔ verificato sul server"), ok);
        writeText("step6", "T6.7", "QUERY:\n" + expectedQuery + "\n\nESITO VERIFICA:\n" + status
                + "\n\nESECUZIONE COMUNQUE:\n" + failed + "\n\nDOPO LA CORREZIONE:\n" + ok + "\n");
    }

    @Test
    void t68DuplicatiPerUniqueSuSociEmail() {
        h = open(soci(CATALOG), CATALOG, MYSQL, biblioteca(CATALOG));
        onEdt(() -> h.editor.columnsTab().setCell(4, ColumnsTab.UQ, true));       // email → UNIQUE email_UNIQUE
        int row = fromEdt(() -> h.editor.editedTable().indexes().size() - 1);
        onEdt(() -> h.editor.indexesTab().select(row));
        assertTrue(fromEdt(() -> h.editor.indexesTab().verifyDataButton().isEnabled()));
        String expectedQuery = IndexPrecheck.duplicatesQuery(soci(CATALOG), List.of("email"));
        assertEquals("SELECT `email`, COUNT(*) AS `occorrenze` FROM `ramasql_test_t67`.`soci` WHERE `email` IS NOT "
                + "NULL GROUP BY `email` HAVING COUNT(*) > 1", expectedQuery);

        h.dataCheck.answer = new DataCheck.DataCheckResult(List.of("email", "occorrenze"),
                List.of(List.of("rossi@example.org", "2"), List.of("bianchi@example.org", "3")), null);
        onEdt(() -> h.editor.indexesTab().verifySelectedData());
        assertEquals(List.of(expectedQuery), h.dataCheck.queries);
        DataCheckPanel panel = fromEdt(() -> h.editor.indexesTab().dataCheck());
        assertEquals(expectedQuery, fromEdt(panel::queryText));
        assertEquals(2, (int) fromEdt(panel::rowCount));
        String status = fromEdt(panel::statusText);
        assertTrue(status.startsWith("⚠ Trovati 2 valori ripetuti"), status);
        assertTrue(status.contains("errore 1062"), status);
        h.tab(TableEditor.TAB_INDEXES);
        h.screenshot("step6", "T6.8-duplicati");

        h.applier.answer = r -> TableApplier.ApplyOutcome.failure(List.of(), r.statements(),
                new TableApplier.ApplyError(1062, "Duplicate entry 'rossi@example.org' for key 'email_UNIQUE'",
                        "Un valore è ripetuto dove non può esserlo (chiave primaria o UNIQUE)."), soci(CATALOG));
        onEdt(() -> h.editor.apply());
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertTrue(outcome.contains("Errore 1062: Duplicate entry"), outcome);
        assertTrue(outcome.contains("è ripetuto dove non può esserlo"), outcome);
        writeText("step6", "T6.8", "QUERY:\n" + expectedQuery + "\n\nESITO VERIFICA:\n" + status
                + "\n\nESECUZIONE COMUNQUE:\n" + outcome + "\n");
    }

    @Test
    void suUnaTabellaNuovaLaVerificaDatiNonServe() {
        h = open(null, CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> h.editor.columnsTab().setCell(0, ColumnsTab.UQ, true));
        onEdt(() -> h.editor.indexesTab().select(1));
        assertFalse(fromEdt(() -> h.editor.indexesTab().verifyDataButton().isEnabled()));
        onEdt(() -> h.editor.indexesTab().verifySelectedData());
        assertTrue(h.dataCheck.queries.isEmpty());
    }
}
