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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.TableDef;

/**
 * T5.6 (lato componente): una modifica che richiede 3 istruzioni ({@code RENAME TABLE}, {@code ALTER … DROP FOREIGN
 * KEY}, {@code ALTER …} con il resto) e un applicatore finto che fallisce alla 2ª: l'editor dice cosa è stato
 * applicato e cosa no, con l'errore, e si ricarica con la tabella riletta dal server (lo stato reale).
 */
@Tag("step5")
@Tag("ui")
class T56PartialApplyTest {

    private static final String CATALOG = "ramasql_test_t56";
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
    void siFermaAllaSecondaEMostraLoStatoReale() {
        h = open(libri(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> {
            h.editor.optionsTab().setTableName("volumi");                 // 1) RENAME TABLE
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.select(0);
            fks.setCell(0, ForeignKeysTab.ON_DELETE, "CASCADE");          // 2) DROP FK  3) ADD FK …
            h.editor.columnsTab().setCell(1, ColumnsTab.ARGS, "200");     //    … e MODIFY titolo
        });
        List<String> statements = fromEdt(() -> h.editor.previewStatements());
        assertEquals(3, statements.size(), String.join("\n", statements));
        assertEquals("RENAME TABLE `ramasql_test_t56`.`libri` TO `ramasql_test_t56`.`volumi`", statements.get(0));
        assertEquals("ALTER TABLE `ramasql_test_t56`.`volumi` DROP FOREIGN KEY `fk_libri_editori`", statements.get(1));
        assertTrue(statements.get(2).startsWith("ALTER TABLE `ramasql_test_t56`.`volumi`\n"), statements.get(2));

        // il server esegue la 1ª, rifiuta la 2ª: la tabella riletta è rinominata, il resto è com'era
        TableDef afterFirst = libri(CATALOG).withName("volumi");
        h.applier.answer = r -> TableApplier.ApplyOutcome.failure(r.statements().subList(0, 1),
                r.statements().subList(1, 3),
                new TableApplier.ApplyError(1142, "ALTER command denied to user 'studente'@'localhost' for table "
                        + "`ramasql_test_t56`.`volumi`", "Il tuo utente non ha il permesso di modificare la tabella."),
                afterFirst);
        onEdt(() -> h.editor.apply());

        assertEquals(statements, h.applier.requests.get(0).statements());
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertTrue(outcome.contains("✘ Istruzioni applicate: 1 su 3; il server si è fermato alla n. 2."), outcome);
        assertTrue(outcome.contains("✘ Errore 1142: ALTER command denied"), outcome);
        assertTrue(outcome.contains("Il tuo utente non ha il permesso"), outcome);
        assertTrue(outcome.contains("Applicata: RENAME TABLE `ramasql_test_t56`.`libri` TO"), outcome);
        assertTrue(outcome.contains("Non riuscita: ALTER TABLE `ramasql_test_t56`.`volumi` DROP FOREIGN KEY"),
                outcome);
        assertTrue(outcome.contains("Non eseguita: ALTER TABLE `ramasql_test_t56`.`volumi` "), outcome);
        assertTrue(outcome.contains("com'è davvero sul server"), outcome);
        assertNull(fromEdt(() -> h.editor.lastVerification()), "nessuna verifica dopo un errore");

        // l'editor mostra lo stato reale: nome nuovo, FK ancora RESTRICT, titolo ancora VARCHAR(150)
        TableDef shown = fromEdt(() -> h.editor.editedTable());
        assertEquals(afterFirst.withOrdinalPositions(), shown);
        assertEquals(shown, fromEdt(() -> h.editor.originalTable()));
        assertEquals(FkAction.RESTRICT, shown.foreignKeys().get(0).onDelete());
        assertEquals("150", shown.column("titolo").orElseThrow().typeArgs());
        assertEquals("volumi", fromEdt(() -> h.editor.optionsTab().tableNameText()), "campo Nome aggiornato");
        assertTrue(fromEdt(() -> h.editor.previewStatements().isEmpty()), "ricaricato: nessuna modifica pendente");
        h.tab(TableEditor.TAB_FOREIGN_KEYS);
        h.screenshot("step5", "T5.6-applicata-in-parte");
        writeText("step5", "T5.6", "ISTRUZIONI:\n" + String.join(";\n", statements) + ";\n\nESITO MOSTRATO:\n"
                + outcome + "\n\nSTATO RICARICATO: nome=" + shown.name() + ", ON DELETE="
                + shown.foreignKeys().get(0).onDelete() + ", titolo=" + shown.column("titolo").orElseThrow().fullType()
                + "\n");
    }

    @Test
    void createFallitoLasciaLeModificheNellEditor() {
        h = open(null, CATALOG, MYSQL, new FakeTables());
        onEdt(() -> h.editor.optionsTab().setTableName("prova"));
        TableDef requested = fromEdt(() -> h.editor.editedTable());
        h.applier.answer = r -> TableApplier.ApplyOutcome.failure(List.of(), r.statements(),
                new TableApplier.ApplyError(1050, "Table 'prova' already exists", "Esiste già una tabella con "
                        + "questo nome."), null);
        onEdt(() -> h.editor.apply());
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertTrue(outcome.contains("Errore 1050"), outcome);
        assertTrue(outcome.contains("le tue modifiche sono ancora nell'editor"), outcome);
        assertEquals(requested, fromEdt(() -> h.editor.editedTable()));
        assertTrue(fromEdt(() -> h.editor.isNewTable()));
    }
}
