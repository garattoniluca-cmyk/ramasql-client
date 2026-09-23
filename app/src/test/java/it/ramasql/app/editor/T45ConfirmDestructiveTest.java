/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import static it.ramasql.app.editor.EditorTestSupport.fromEdt;
import static it.ramasql.app.editor.EditorTestSupport.newEditor;
import static it.ramasql.app.editor.EditorTestSupport.press;
import static it.ramasql.app.editor.EditorTestSupport.setText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.core.CoreMessages;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;

/**
 * T4.5 lato componente: {@code UPDATE soci SET nome='x'} senza WHERE, DROP e TRUNCATE chiedono una conferma esplicita
 * prima di partire; se l'utente rifiuta l'esecutore non riceve nulla. Le istruzioni innocue non chiedono nulla. La
 * conferma è la <b>conferma rafforzata del navigatore</b> ({@link ConfirmationPolicy}, {@link PreviewDialog}): si
 * riscrive il nome dell'oggetto, o la parola di conferma se sono più d'uno; con un testo sbagliato non parte nulla.
 */
@Tag("step4")
@Tag("ui")
class T45ConfirmDestructiveTest {

    private static final List<String> EVIDENCE = new ArrayList<>();

    @BeforeAll
    static void lookAndFeel() {
        EditorTestSupport.setupLookAndFeel();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE soci SET nome='x'",
        "DELETE FROM prestiti",
        "DROP TABLE prestiti",
        "DROP DATABASE ramasql_test_prova",
        "TRUNCATE TABLE soci",
        "ALTER TABLE soci DROP COLUMN email"})
    void istruzionePericolosaRifiutataNonArrivaAllEsecutore(String sql) {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.confirmAnswer = false;
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), prompts);
        setText(editor, sql + ";", 2);

        press(editor, SqlEditor.KEY_RUN_CURRENT);

        assertEquals(List.of(List.of(sql)), prompts.confirmations, "conferma chiesta con il testo dell'istruzione");
        assertEquals(0, runner.runCount(), "rifiutata: niente al server");
        assertEquals("Esecuzione annullata: nessuna istruzione è stata inviata al server.",
                fromEdt(() -> editor.results().statusText()));
        synchronized (EVIDENCE) {
            EVIDENCE.add(sql + "  -> conferma chiesta; rifiutata; istruzioni consegnate: " + runner.runCount());
            EditorTestSupport.writeText("T4.5-conferme", EditorTestSupport.join(EVIDENCE));
        }
    }

    @Test
    void updateSenzaWhereConfermatoParte() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.confirmAnswer = true;
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), prompts);
        setText(editor, "UPDATE soci SET nome='x';", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        assertEquals(1, prompts.confirmations.size());
        assertEquals(List.of("UPDATE soci SET nome='x'"), runner.lastTexts());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE soci SET nome='x' WHERE id = 3",
        "DELETE FROM prestiti WHERE data_reso IS NOT NULL",
        "SELECT * FROM soci WHERE nome = 'DROP TABLE soci'",
        "INSERT INTO soci (nome) VALUES ('TRUNCATE')",
        "CREATE TABLE prova (id INT)"})
    void istruzioniNonDistruttiveNonChiedonoConferma(String sql) {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), prompts);
        setText(editor, sql, 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        assertTrue(prompts.confirmations.isEmpty());
        assertEquals(List.of(sql), runner.lastTexts());
    }

    @Test
    void scriptMistoElencaSoloLeIstruzioniPericoloseESeRifiutatoNonParteNulla() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), prompts);
        setText(editor, "SELECT * FROM soci;\nUPDATE soci SET nome='x';\nINSERT INTO soci (nome) VALUES ('a');\n"
                + "DROP TABLE vecchia;", 0);
        press(editor, SqlEditor.KEY_RUN_ALL);
        assertEquals(List.of(List.of("UPDATE soci SET nome='x'", "DROP TABLE vecchia")), prompts.confirmations);
        assertEquals(0, runner.runCount(), "neppure le istruzioni innocue dello script partono");
    }

    // ---------------------------------------------------------------- conferma rafforzata uguale a quella del navigatore

    /** La stessa politica del navigatore: si riscrive il nome dell'oggetto colpito. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "UPDATE soci SET nome='x'                      | soci",
        "DELETE FROM prestiti                          | prestiti",
        "DROP TABLE prestiti                           | prestiti",
        "DROP DATABASE ramasql_test_prova              | ramasql_test_prova",
        "TRUNCATE TABLE soci                           | soci",
        "ALTER TABLE soci DROP COLUMN email            | soci",
        "SET STATEMENT max_statement_time=1 FOR DELETE FROM prestiti | prestiti"})
    void siRiscriveIlNomeDellOggettoComeNelNavigatore(String sql, String name) {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.confirmAnswer = true;
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), prompts);
        setText(editor, sql + ";", 2);
        press(editor, SqlEditor.KEY_RUN_CURRENT);

        assertEquals(1, prompts.confirmationRequests.size());
        ConfirmationPolicy.Confirmation asked = prompts.confirmationRequests.get(0);
        assertEquals(ConfirmationPolicy.Level.STRONG, asked.level());
        assertEquals(name, asked.typeToConfirm());
        // identica a quella che il navigatore chiederebbe per lo stesso SQL
        assertEquals(ConfirmationPolicy.evaluate(SqlScript.of("t", "o", sql)), asked);
        assertEquals(List.of(sql), runner.lastTexts(), "con il nome riscritto l'istruzione parte");
    }

    /** Un testo sbagliato (anche solo nelle maiuscole, o la parola generica) vale come Annulla: non parte nulla. */
    @ParameterizedTest
    @ValueSource(strings = {"", "prestit", "PRESTITI", "prestiti x", "CONFERMO", "si"})
    void conUnTestoSbagliatoNonParteNulla(String typed) {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.typedAnswer = typed;
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), prompts);
        setText(editor, "DROP TABLE prestiti;", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        assertEquals(1, prompts.confirmations.size());
        assertEquals(0, runner.runCount(), "testo «" + typed + "»: niente al server");
        assertEquals("Esecuzione annullata: nessuna istruzione è stata inviata al server.",
                fromEdt(() -> editor.results().statusText()));
    }

    /** Più oggetti diversi: si riscrive la parola di conferma, come nel navigatore. */
    @Test
    void piuOggettiChiedonoLaParolaDiConferma() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.typedAnswer = "vecchia";
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), prompts);
        setText(editor, "UPDATE soci SET nome='x';\nDROP TABLE vecchia;", 0);
        press(editor, SqlEditor.KEY_RUN_ALL);
        assertEquals(CoreMessages.get("confirm.word"), prompts.confirmationRequests.get(0).typeToConfirm());
        assertEquals(0, runner.runCount(), "il nome di uno solo dei due oggetti non basta");
        prompts.typedAnswer = CoreMessages.get("confirm.word");
        press(editor, SqlEditor.KEY_RUN_ALL);
        assertEquals(List.of("UPDATE soci SET nome='x'", "DROP TABLE vecchia"), runner.lastTexts());
    }

    /**
     * La finestra vera ({@link SwingEditorPrompts}) è la {@link PreviewDialog} del navigatore con la fascia della conferma
     * rafforzata: Esegui disabilitato e non predefinito finché il nome non è riscritto esatto; «Copia nell'editor»
     * nascosto; restituisce il testo solo se confermato.
     */
    @Test
    void laFinestraVeraEQuellaDelNavigatore() {
        List<String> seen = new ArrayList<>();
        SwingEditorPrompts real = fromEdt(() -> new SwingEditorPrompts(() -> null, () -> "."));
        SqlScript dangerous = SqlScript.of("t", "Editor SQL", "DROP TABLE prestiti");
        ConfirmationPolicy.Confirmation c = ConfirmationPolicy.evaluate(dangerous);

        real.presenter = d -> {
            assertTrue(d.requiresTypedConfirmation());
            assertEquals("DROP TABLE prestiti;", d.sqlText());
            assertFalse(d.copyButton().isVisible(), "l'SQL è già nell'editor: niente «Copia nell'editor»");
            assertFalse(d.executeButton().isEnabled(), "Esegui disabilitato a campo vuoto");
            assertNotSame(d.executeButton(), d.getRootPane().getDefaultButton(), "Invio non conferma");
            for (String wrong : List.of("prestit", "PRESTITI", "CONFERMO")) {
                d.confirmationField().setText(wrong);
                assertFalse(d.executeButton().isEnabled(), wrong);
                d.executeButton().doClick();
                assertEquals(PreviewDialog.Decision.CANCEL, d.decision(), "clic su Esegui disabilitato ignorato");
            }
            seen.add("sbagliati rifiutati");
            d.cancelButton().doClick();
            return d.decision();
        };
        assertEquals(null, fromEdt(() -> real.confirmDestructive(c, dangerous)), "annullato: nessun testo");

        real.presenter = d -> {
            d.confirmationField().setText("prestiti");
            assertTrue(d.executeButton().isEnabled());
            EditorTestSupport.writeText("T4.5-conferma-rafforzata", "finestra: " + d.getTitle() + "\nSQL: " + d.sqlText()
                    + "\nda riscrivere: " + d.confirmation().typeToConfirm() + "\nEsegui con «prestiti»: abilitato\n");
            d.executeButton().doClick();
            seen.add("confermato");
            return d.decision();
        };
        assertEquals("prestiti", fromEdt(() -> real.confirmDestructive(c, dangerous)));
        assertEquals(List.of("sbagliati rifiutati", "confermato"), seen);

        // e dentro l'editor: con la finestra vera annullata, al server non arriva nulla
        FakeSqlRunner runner = FakeSqlRunner.manual();
        real.presenter = d -> {
            d.cancelButton().doClick();
            return d.decision();
        };
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), real);
        setText(editor, "TRUNCATE TABLE soci;", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        assertEquals(0, runner.runCount());
    }
}
