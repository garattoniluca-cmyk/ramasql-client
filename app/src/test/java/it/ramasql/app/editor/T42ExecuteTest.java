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
import static it.ramasql.app.editor.EditorTestSupport.onEdt;
import static it.ramasql.app.editor.EditorTestSupport.press;
import static it.ramasql.app.editor.EditorTestSupport.select;
import static it.ramasql.app.editor.EditorTestSupport.setText;
import static it.ramasql.app.editor.EditorTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.app.grid.DataGrid;

/**
 * T4.2 lato componente: Esegui (Ctrl+Invio) al cursore e sulla selezione, Esegui tutto (Ctrl+Maiusc+Invio) su uno
 * script di 50 istruzioni miste con DELIMITER; l'esecutore finto riceve esattamente le istruzioni attese, in ordine;
 * risultati in sotto-schede, righe interessate e durata per ciascuna.
 */
@Tag("step4")
@Tag("ui")
class T42ExecuteTest {

    @BeforeAll
    static void lookAndFeel() {
        EditorTestSupport.setupLookAndFeel();
    }

    @Test
    void ctrlInvioConIlCursoreNellaProceduraConsegnaEsattamenteLaProcedura() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        String script = StatementLocatorTest.PROCEDURE_SCRIPT;
        setText(editor, script, script.indexOf("'fatto; davvero'") + 3);

        press(editor, SqlEditor.KEY_RUN_CURRENT);

        assertEquals(1, runner.runCount());
        assertEquals(List.of(StatementLocatorTest.PROCEDURE_TEXT), runner.lastTexts());
        assertEquals(4, runner.lastRun().getFirst().line());
        assertEquals("Editor SQL", runner.lastOrigin());
        assertTrue(fromEdt(editor::isRunning));
    }

    @Test
    void ctrlInvioDopoLaProceduraConsegnaLaCall() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        String script = StatementLocatorTest.PROCEDURE_SCRIPT;
        setText(editor, script, script.length());
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        assertEquals(List.of("CALL conta_prestiti(3)"), runner.lastTexts());
        assertEquals(11, runner.lastRun().getFirst().line());
    }

    @Test
    void conUnaSelezioneEsegueSoloIlTestoSelezionato() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        String script = "SELECT * FROM soci;\nSELECT titolo, anno FROM libri WHERE anno > 2000;\nSELECT 3;";
        setText(editor, script, 0);
        int from = script.indexOf("titolo");
        select(editor, script.indexOf("SELECT titolo"), script.indexOf(" WHERE"));

        press(editor, SqlEditor.KEY_RUN_CURRENT);

        assertEquals(List.of("SELECT titolo, anno FROM libri"), runner.lastTexts());
        assertEquals(from - "SELECT ".length(), runner.lastRun().getFirst().startOffset());
        assertEquals(2, runner.lastRun().getFirst().line());
    }

    @Test
    void scriptVuotoNonChiamaLEsecutore() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "-- solo commenti\n\n", 0);
        press(editor, SqlEditor.KEY_RUN_ALL);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        assertEquals(0, runner.runCount());
        assertEquals("Qui non c'è nessuna istruzione da eseguire.", fromEdt(() -> editor.results().statusText()));
    }

    @Test
    void mentreEsegueIPulsantiEseguiSonoSpentiEUnSecondoAvvioNonParte() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT 1;", 0);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        assertFalse(fromEdt(() -> editor.runCurrentAction().isEnabled()));
        assertFalse(fromEdt(() -> editor.runAllAction().isEnabled()));
        assertTrue(fromEdt(() -> editor.cancelAction().isEnabled()));
        press(editor, SqlEditor.KEY_RUN_ALL);
        assertEquals(1, runner.runCount());

        runner.listener().started(0);
        runner.listener().finished(FakeSqlRunner.defaultOutcome(0, runner.lastRun().getFirst()));
        runner.listener().done(false);
        waitUntil(() -> !editor.isRunning(), "fine esecuzione");
        assertTrue(fromEdt(() -> editor.runCurrentAction().isEnabled()));
        assertFalse(fromEdt(() -> editor.cancelAction().isEnabled()));
        assertEquals("Fatto: 1 istruzione in ", fromEdt(() -> editor.results().statusText()).substring(0, 23));
    }

    /** Lo script di 50 istruzioni miste (DDL, DML, SELECT, una procedura con DELIMITER e la sua CALL). */
    static List<String> fiftyStatements() {
        List<String> out = new ArrayList<>();
        out.add("CREATE TABLE ramasql_prova (id INT AUTO_INCREMENT PRIMARY KEY, nome VARCHAR(50), n INT)");
        out.add("ALTER TABLE ramasql_prova ADD COLUMN nota VARCHAR(100)");
        out.add("CREATE PROCEDURE somma_prova()\nBEGIN\n  SELECT SUM(n) FROM ramasql_prova;\nEND");
        for (int i = 4; i <= 48; i++) {
            out.add(switch (i % 5) {
                case 0 -> "SELECT id, nome FROM ramasql_prova WHERE n >= " + i;
                case 1 -> "INSERT INTO ramasql_prova (nome, n) VALUES ('riga; " + i + "', " + i + ")";
                case 2 -> "UPDATE ramasql_prova SET nota = 'aggiornata' WHERE n = " + i;
                case 3 -> "DELETE FROM ramasql_prova WHERE n = " + (i * 100);
                default -> "SHOW COLUMNS FROM ramasql_prova";
            });
        }
        out.add("CALL somma_prova()");
        out.add("SELECT COUNT(*) FROM ramasql_prova");
        return out;
    }

    static String fiftyScript(List<String> statements) {
        StringBuilder sb = new StringBuilder("-- Script di prova T4.2: 50 istruzioni\n");
        for (String s : statements) {
            if (s.startsWith("CREATE PROCEDURE")) {
                sb.append("DELIMITER //\n").append(s).append(" //\nDELIMITER ;\n");
            } else {
                sb.append(s).append(";\n");
            }
        }
        return sb.toString();
    }

    @Test
    void eseguiTuttoCinquantaIstruzioniInOrdineConRisultatiInSottoSchede() {
        FakeSqlRunner runner = FakeSqlRunner.automatic();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        List<String> expected = fiftyStatements();
        assertEquals(50, expected.size());
        setText(editor, fiftyScript(expected), 0);
        JFrame frame = EditorTestSupport.host(editor, 1100, 800);

        press(editor, SqlEditor.KEY_RUN_ALL);
        waitUntil(() -> !editor.isRunning(), "fine dello script");

        assertEquals(1, runner.runCount());
        assertEquals(expected, runner.lastTexts(), "istruzioni consegnate in ordine, esattamente");
        List<List<String>> rows = fromEdt(() -> editor.results().outcomeRows());
        assertEquals(50, rows.size());
        int withResult = 0;
        for (int i = 0; i < 50; i++) {
            List<String> row = rows.get(i);
            assertEquals(String.valueOf(i + 1), row.get(0));
            String verb = expected.get(i).split("\\s+")[0];
            boolean hasRows = verb.equals("SELECT") || verb.equals("SHOW") || verb.equals("CALL");
            String outcome = hasRows ? (i == 0 ? "1 riga" : (i + 1) + " righe")
                    : (i == 0 ? "1 riga interessata" : (i + 1) + " righe interessate");
            assertEquals(outcome, row.get(2), "esito dell'istruzione " + (i + 1));
            assertEquals(String.format(java.util.Locale.ITALIAN, "%.3f s", (i + 1) / 1000.0), row.get(3));
            withResult += hasRows ? 1 : 0;
        }
        int results = fromEdt(() -> editor.results().resultCount());
        assertEquals(withResult, results);
        assertEquals(results + 1, (int) fromEdt(() -> editor.results().tabs().getTabCount()));
        assertEquals("Risultato 1", fromEdt(() -> editor.results().tabs().getTitleAt(1)));
        DataGrid last = fromEdt(() -> editor.results().resultGrid(results));
        assertEquals(50, (int) fromEdt(() -> last.model().getRowCount()), "l'ultima SELECT ha 50 righe");
        assertFalse(fromEdt(last::isEditable), "griglia dei risultati in sola lettura");
        assertTrue(fromEdt(() -> editor.results().statusText()).startsWith("Fatto: 50 istruzioni in "));

        onEdt(() -> editor.results().tabs().setSelectedIndex(0));
        EditorTestSupport.screenshot(frame, "T4.2-script50-esito");
        onEdt(() -> editor.results().tabs().setSelectedIndex(1));
        EditorTestSupport.screenshot(frame, "T4.2-script50-risultato1");
        List<String> evidence = new ArrayList<>();
        evidence.add("T4.2 (lato componente) — Esegui tutto su 50 istruzioni; esecutore finto su un altro thread");
        evidence.add("Sotto-schede Risultato: " + results + "; righe nella scheda Esito: " + rows.size());
        for (int i = 0; i < 50; i++) {
            evidence.add(String.join(" | ", rows.get(i)));
        }
        EditorTestSupport.writeText("T4.2-script50", EditorTestSupport.join(evidence));
        assertTrue(Files.exists(EditorTestSupport.resultsDir().resolve("editor-T4.2-script50-esito.png")));
    }

    @Test
    void avvisiDelServerCompaionoNellaRigaDiEsito() {
        FakeSqlRunner runner = FakeSqlRunner.automatic().respondingWith((i, st) -> StatementOutcome.update(i, 1,
                java.time.Duration.ofMillis(12), List.of(new StatementOutcome.Warning(1265,
                        "Data truncated for column 'nome' at row 1"))));
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "INSERT INTO soci (nome) VALUES ('un nome troppo lungo')", 3);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        waitUntil(() -> !editor.isRunning(), "fine");
        List<String> row = fromEdt(() -> editor.results().outcomeRows()).getFirst();
        assertEquals("1 riga interessata", row.get(2));
        assertEquals("0,012 s", row.get(3));
        assertEquals("1 avviso: 1265 Data truncated for column 'nome' at row 1", row.get(4));
    }

    @Test
    void unaNuovaEsecuzioneSvuotaIRisultatiPrecedenti() {
        FakeSqlRunner runner = FakeSqlRunner.automatic();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT 1;\nSELECT 2;\nSELECT 3;", 0);
        press(editor, SqlEditor.KEY_RUN_ALL);
        waitUntil(() -> !editor.isRunning(), "prima esecuzione");
        assertEquals(3, (int) fromEdt(() -> editor.results().resultCount()));
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        waitUntil(() -> !editor.isRunning(), "seconda esecuzione");
        assertEquals(1, (int) fromEdt(() -> editor.results().resultCount()));
        assertEquals(1, fromEdt(() -> editor.results().outcomeRows()).size());
    }
}
