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

/**
 * T5.5 (lato componente): restringere {@code libri.titolo} da VARCHAR(100) a VARCHAR(20) produce l'avviso di possibile
 * troncamento accanto alla riga e una conferma esplicita <b>prima</b> di applicare; se il server rifiuta, l'errore
 * arriva spiegato nell'esito.
 */
@Tag("step5")
@Tag("ui")
class T55TruncationTest {

    private static final String CATALOG = "ramasql_test_t55";
    private static final String WARNING =
            "titolo: passando da VARCHAR(100) a VARCHAR(20) i valori più lunghi di 20 caratteri potrebbero essere "
                    + "troncati o rifiutati dal server.";
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

    /** {@code libri} come nella traccia di T5.5: titolo VARCHAR(100). */
    private static TableDef libri100() {
        return libri(CATALOG).changeColumn("titolo", c -> c.withTypeArgs("100"));
    }

    @Test
    void avvisoAccantoAllaRigaEConfermaPrimaDiApplicare() {
        h = open(libri100(), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> h.editor.columnsTab().setCell(1, ColumnsTab.ARGS, "20"));

        String notes = fromEdt(() -> h.editor.columnsTab().notesText());
        assertTrue(notes.contains("⚠ " + WARNING), notes);
        @SuppressWarnings("unchecked")
        List<Checks.Problem> mark = (List<Checks.Problem>) fromEdt(
                () -> h.editor.columnsTab().cell(1, ColumnsTab.MARK));
        assertEquals(1, mark.size(), "segno ⚠ accanto alla riga di titolo");
        assertFalse(mark.get(0).isError(), "è un avviso, non blocca");
        assertTrue(fromEdt(() -> h.editor.tabs().getTitleAt(TableEditor.TAB_COLUMNS)).endsWith("⚠"));
        assertEquals(List.of("ALTER TABLE `ramasql_test_t55`.`libri` MODIFY COLUMN `titolo` VARCHAR(20) NOT NULL"),
                fromEdt(() -> h.editor.previewStatements()));
        h.tab(TableEditor.TAB_COLUMNS);
        h.screenshot("step5", "T5.5-avviso-troncamento");

        // l'utente rinuncia: nulla viene eseguito
        h.prompts.confirmAnswer = false;
        onEdt(() -> h.editor.apply());
        assertEquals(1, h.prompts.confirms.size());
        assertTrue(h.prompts.confirms.get(0).contains(WARNING), h.prompts.confirms.get(0));
        assertTrue(h.applier.requests.isEmpty(), "senza conferma non si esegue nulla");

        // l'utente conferma: il server rifiuta (dati più lunghi, modalità rigorosa) e l'errore è spiegato
        h.prompts.confirmAnswer = true;
        h.applier.answer = r -> TableApplier.ApplyOutcome.failure(List.of(), r.statements(),
                new TableApplier.ApplyError(1265, "Data truncated for column 'titolo' at row 3",
                        "Un valore non entra nella colonna: è troppo lungo per il nuovo tipo."),
                libri100());
        onEdt(() -> h.editor.apply());
        assertEquals(2, h.prompts.confirms.size());
        assertEquals(1, h.applier.requests.size());
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertTrue(outcome.contains("Errore 1265: Data truncated for column 'titolo' at row 3"), outcome);
        assertTrue(outcome.contains("troppo lungo per il nuovo tipo"), outcome);
        assertEquals("100", fromEdt(() -> h.editor.editedTable().column("titolo").orElseThrow().typeArgs()),
                "ricaricata dal server: titolo è rimasto VARCHAR(100)");
        h.screenshot("step5", "T5.5-errore-spiegato");
        writeText("step5", "T5.5", "AVVISO NELLA SCHEDA COLONNE:\n" + notes + "\n\nCONFERMA CHIESTA:\n"
                + h.prompts.confirms.get(0) + "\n\nSQL PASSATO ALL'ESECUZIONE:\n"
                + String.join("\n", h.applier.requests.get(0).statements()) + "\n\nESITO:\n" + outcome + "\n");
    }

    @Test
    void restringereUnNumeroAvvisaAllargareNo() {
        h = open(libri100(), CATALOG, MYSQL, biblioteca(CATALOG));
        onEdt(() -> {
            ColumnsTab cols = h.editor.columnsTab();
            cols.setCell(3, ColumnsTab.TYPE, "TINYINT");          // anno SMALLINT UNSIGNED → TINYINT UNSIGNED
            cols.setCell(4, ColumnsTab.ARGS, "5,1");              // prezzo DECIMAL(6,2) → DECIMAL(5,1)
        });
        String notes = fromEdt(() -> h.editor.columnsTab().notesText());
        assertTrue(notes.contains("anno: passando da SMALLINT UNSIGNED a TINYINT UNSIGNED"), notes);
        assertTrue(notes.contains("prezzo: passando da DECIMAL(6,2) a DECIMAL(5,1)"), notes);

        onEdt(() -> h.editor.revert());
        onEdt(() -> {
            ColumnsTab cols = h.editor.columnsTab();
            cols.setCell(1, ColumnsTab.ARGS, "200");              // VARCHAR(100) → VARCHAR(200): più spazio
            cols.setCell(3, ColumnsTab.TYPE, "INT");              // SMALLINT → INT: più spazio
            cols.setCell(4, ColumnsTab.ARGS, "10,2");
        });
        assertEquals("", fromEdt(() -> h.editor.columnsTab().notesText()), "allargare non dà avvisi");
        onEdt(() -> h.editor.apply());
        assertTrue(h.prompts.confirms.isEmpty(), "nessuna conferma se non ci sono dati a rischio");
        assertEquals(1, h.applier.requests.size());
    }

    @Test
    void unaTabellaNuovaNonHaDatiDaTroncare() {
        h = open(null, CATALOG, MARIADB, new FakeTables());
        onEdt(() -> {
            h.editor.columnsTab().addColumn();
            h.editor.columnsTab().setCell(1, ColumnsTab.ARGS, "10");
        });
        assertEquals("", fromEdt(() -> h.editor.columnsTab().notesText()));
    }
}
