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
import static it.ramasql.app.editor.EditorTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import javax.swing.JFrame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T4.6 lato componente: errore di sintassi 1064 simulato con «near '…' at line 3» sulla seconda istruzione dello
 * script → evidenziata la riga giusta del documento e il testo citato; messaggio originale + spiegazione in italiano.
 */
@Tag("step4")
@Tag("ui")
class T46ErrorTest {

    static final String SCRIPT = String.join("\n",
            "SELECT * FROM soci;",
            "",
            "SELECT titolo,",
            "       anno",
            "FORM libri",
            "WHERE anno > 2000;",
            "SELECT 'mai eseguita';");

    static final String MARIADB_1064 = "You have an error in your SQL syntax; check the manual that corresponds to your "
            + "MariaDB server version for the right syntax to use near 'FORM libri\nWHERE anno > 2000' at line 3";

    @BeforeAll
    static void lookAndFeel() {
        EditorTestSupport.setupLookAndFeel();
    }

    private static FakeSqlRunner failingAt(int failing, SqlError error) {
        return FakeSqlRunner.automatic().respondingWith((i, st) -> i == failing
                ? StatementOutcome.failed(i, error, Duration.ofMillis(3))
                : FakeSqlRunner.defaultOutcome(i, st));
    }

    @Test
    void errore1064AllaRiga3DellIstruzioneEvidenziaLaRiga5DelDocumento() {
        FakeSqlRunner runner = failingAt(1, new SqlError(1064, "42000", MARIADB_1064));
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, SCRIPT, 0);
        JFrame frame = EditorTestSupport.host(editor, 1100, 700);

        press(editor, SqlEditor.KEY_RUN_ALL);
        waitUntil(() -> !editor.isRunning(), "fine");

        assertEquals(3, runner.lastRun().size());
        assertEquals(5, (int) fromEdt(editor::errorLine), "riga 3 dell'istruzione che comincia alla riga 3");
        assertEquals("FORM libri", fromEdt(editor::errorHighlightedText));
        assertEquals(SCRIPT.indexOf("FORM libri"), (int) fromEdt(() -> editor.textArea().getCaretPosition()));
        String error = fromEdt(() -> editor.results().errorText());
        assertTrue(error.startsWith("Istruzione 2 — errore 1064 (42000): You have an error in your SQL syntax"), error);
        assertTrue(error.contains("In parole semplici: L'SQL non è scritto correttamente (errore di sintassi)."), error);
        assertTrue(error.contains("alla riga 5"), error);
        assertEquals(2, fromEdt(() -> editor.results().outcomeRows()).size(), "la terza non è stata eseguita");
        assertEquals("Errore 1064", fromEdt(() -> editor.results().outcomeRows()).get(1).get(2));
        assertEquals("Errore nell'istruzione 2: eseguite 2 istruzioni su 3.",
                fromEdt(() -> editor.results().statusText()));
        assertEquals(0, (int) fromEdt(() -> editor.results().tabs().getSelectedIndex()), "scheda Esito in primo piano");

        EditorTestSupport.screenshot(frame, "T4.6-errore-1064");
        EditorTestSupport.writeText("T4.6-errore-1064", EditorTestSupport.join(List.of(
                "T4.6 (lato componente): errore 1064 simulato sulla 2a istruzione dello script",
                "Messaggio del server: " + MARIADB_1064.replace("\n", "\\n"),
                "Riga evidenziata nel documento: " + fromEdt(editor::errorLine),
                "Testo evidenziato: " + fromEdt(editor::errorHighlightedText),
                "Riquadro errore:", error)));
    }

    @Test
    void erroreMySqlSuUnaSolaRigaEvidenziaIlTestoCitato() {
        String msg = "You have an error in your SQL syntax; check the manual that corresponds to your MySQL server "
                + "version for the right syntax to use near 'FRM soci' at line 1";
        FakeSqlRunner runner = failingAt(0, new SqlError(1064, "42000", msg));
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "-- prova\n\nSELECT * FRM soci", 12);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        waitUntil(() -> !editor.isRunning(), "fine");
        assertEquals(3, (int) fromEdt(editor::errorLine));
        assertEquals("FRM soci", fromEdt(editor::errorHighlightedText));
    }

    @Test
    void erroreAllaFineDellIstruzioneEvidenziaLUltimoCarattere() {
        String msg = "You have an error in your SQL syntax; check the manual that corresponds to your MariaDB server "
                + "version for the right syntax to use near '' at line 2";
        FakeSqlRunner runner = failingAt(0, new SqlError(1064, "42000", msg));
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT *\nFROM", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        waitUntil(() -> !editor.isRunning(), "fine");
        assertEquals(2, (int) fromEdt(editor::errorLine));
        assertEquals("M", fromEdt(editor::errorHighlightedText));
    }

    @Test
    void erroreSenzaPosizioneEvidenziaLaPrimaRigaDellIstruzioneESpiega() {
        FakeSqlRunner runner = failingAt(1, new SqlError(1146, "42S02", "Table 'biblioteca.libro' doesn't exist"));
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT 1;\n\n\nSELECT *\nFROM libro;", 0);
        press(editor, SqlEditor.KEY_RUN_ALL);
        waitUntil(() -> !editor.isRunning(), "fine");
        assertEquals(4, (int) fromEdt(editor::errorLine));
        assertNull(fromEdt(editor::errorHighlightedText));
        String error = fromEdt(() -> editor.results().errorText());
        assertTrue(error.contains("errore 1146 (42S02): Table 'biblioteca.libro' doesn't exist"), error);
        assertTrue(error.contains("La tabella indicata non esiste"), error);
    }

    @Test
    void codiceSenzaSpiegazioneMostraSoloIlMessaggioOriginale() {
        FakeSqlRunner runner = failingAt(0, new SqlError(1227, "42000",
                "Access denied; you need (at least one of) the SUPER privilege(s) for this operation"));
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SET GLOBAL max_connections = 10", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        waitUntil(() -> !editor.isRunning(), "fine");
        String error = fromEdt(() -> editor.results().errorText());
        assertTrue(error.contains("SUPER privilege"), error);
        assertFalse(error.contains("In parole semplici"), error);
    }

    @Test
    void unaNuovaEsecuzioneTogliLEvidenziazioneDellErrore() {
        FakeSqlRunner runner = FakeSqlRunner.automatic().respondingWith((i, st) -> st.text().contains("FRM")
                ? StatementOutcome.failed(i, new SqlError(1064, "42000",
                        "... right syntax to use near 'FRM soci' at line 1"), Duration.ofMillis(1))
                : FakeSqlRunner.defaultOutcome(i, st));
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT * FRM soci", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        waitUntil(() -> !editor.isRunning(), "prima");
        assertEquals(1, (int) fromEdt(editor::errorLine));
        setText(editor, "SELECT * FROM soci", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        waitUntil(() -> !editor.isRunning(), "seconda");
        assertEquals(0, (int) fromEdt(editor::errorLine));
        assertEquals("", fromEdt(() -> editor.results().errorText()));
    }
}
