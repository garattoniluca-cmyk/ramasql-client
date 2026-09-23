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
import static it.ramasql.app.editor.EditorTestSupport.setText;
import static it.ramasql.app.editor.EditorTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T4.3 lato componente: con {@code SELECT SLEEP(30)} in corso, il pulsante Interrompi (o Esc) chiama
 * {@link SqlRunner#cancel()}; quando l'esecutore conferma la fine, Esegui torna disponibile e l'editor si può riusare.
 * Il tempo reale di interruzione (&lt; 2 s, {@code KILL QUERY}) è dell'integrazione con il server.
 */
@Tag("step4")
@Tag("ui")
class T43CancelTest {

    @BeforeAll
    static void lookAndFeel() {
        EditorTestSupport.setupLookAndFeel();
    }

    static JButton button(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && name.equals(b.getName())) {
                return b;
            }
            if (c instanceof Container inner) {
                JButton found = button(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void pulsanteInterrompiChiamaCancelEPoiEseguiTornaDisponibile() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        JFrame frame = EditorTestSupport.host(editor, 1000, 600);
        setText(editor, "SELECT SLEEP(30);", 0);
        JButton cancel = fromEdt(() -> button(editor, "sqlEditor.cancel"));
        JButton run = fromEdt(() -> button(editor, "sqlEditor.runCurrent"));
        assertFalse(fromEdt(cancel::isEnabled), "Interrompi spento quando non si esegue nulla");

        onEdt(run::doClick);
        runner.listener().started(0);
        waitUntil(() -> editor.results().statusText().startsWith("In esecuzione: istruzione 1 di 1"), "stato");
        assertTrue(fromEdt(cancel::isEnabled));
        assertFalse(fromEdt(run::isEnabled));
        EditorTestSupport.screenshot(frame, "T4.3-in-esecuzione");

        onEdt(cancel::doClick);
        assertEquals(1, runner.cancelCount());
        assertEquals("Interruzione in corso…", fromEdt(() -> editor.results().statusText()));

        // l'esecutore riporta l'istruzione uccisa (1317) e la fine per interruzione
        runner.listener().finished(StatementOutcome.failed(0,
                new SqlError(1317, "70100", "Query execution was interrupted"), Duration.ofMillis(850)));
        runner.listener().done(true);
        waitUntil(() -> !editor.isRunning(), "fine dopo l'interruzione");
        assertTrue(fromEdt(run::isEnabled), "Esegui torna disponibile");
        assertFalse(fromEdt(cancel::isEnabled));
        assertEquals("Interrotto: eseguite 1 istruzioni su 1.", fromEdt(() -> editor.results().statusText()));
        assertTrue(fromEdt(() -> editor.results().errorText()).contains("interrotta su richiesta"));
        EditorTestSupport.screenshot(frame, "T4.3-interrotto");

        // la sessione resta utilizzabile: una nuova esecuzione parte
        setText(editor, "SELECT 1;", 0);
        onEdt(run::doClick);
        assertEquals(2, runner.runCount());
        assertEquals(List.of("SELECT 1"), runner.lastTexts());
        EditorTestSupport.writeText("T4.3-interrompi", EditorTestSupport.join(List.of(
                "T4.3 (lato componente): SELECT SLEEP(30) -> Interrompi",
                "cancel() ricevuti dall'esecutore: " + runner.cancelCount(),
                "stato dopo la fine: Interrotto: eseguite 1 istruzioni su 1.",
                "nuova esecuzione accettata dopo l'interruzione: " + runner.lastTexts())));
    }

    @Test
    void escMentreEsegueInterrompe() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT SLEEP(30)", 2);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        press(editor, SqlEditor.KEY_ESCAPE);
        assertEquals(1, runner.cancelCount());
        runner.listener().done(true);
        waitUntil(() -> !editor.isRunning(), "fine");
        press(editor, SqlEditor.KEY_ESCAPE);
        assertEquals(1, runner.cancelCount(), "Esc da fermi non chiama cancel");
    }

    @Test
    void escConIlFuocoSuiRisultatiInterrompeLoStesso() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT SLEEP(30)", 2);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        onEdt(() -> {
            Object name = editor.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(SqlEditor.KEY_ESCAPE);
            editor.getActionMap().get(name).actionPerformed(null);
        });
        assertEquals(1, runner.cancelCount());
    }

    @Test
    void interruzioneAMetaScriptDiceQuanteIstruzioniSonoStateEseguite() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT 1;\nSELECT SLEEP(30);\nSELECT 3;", 0);
        press(editor, SqlEditor.KEY_RUN_ALL);
        runner.listener().started(0);
        runner.listener().finished(FakeSqlRunner.defaultOutcome(0, runner.lastRun().getFirst()));
        runner.listener().started(1);
        onEdt(editor::cancelRun);
        runner.listener().done(true);
        waitUntil(() -> !editor.isRunning(), "fine");
        assertEquals("Interrotto: eseguite 1 istruzioni su 3.", fromEdt(() -> editor.results().statusText()));
        assertEquals(1, (int) fromEdt(() -> editor.results().resultCount()));
    }

    @Test
    void chiusuraDellaSchedaDuranteLEsecuzioneInterrompe() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT SLEEP(30)", 2);
        press(editor, SqlEditor.KEY_RUN_CURRENT);
        onEdt(editor::dispose);
        assertEquals(1, runner.cancelCount());
    }
}
