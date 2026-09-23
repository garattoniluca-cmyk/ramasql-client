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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** L'area di testo: evidenziazione, numeri di riga, parentesi, scorciatoie, trova/sostituisci, commenti, carattere. */
@Tag("step4")
@Tag("ui")
class EditorBasicsTest {

    @BeforeAll
    static void lookAndFeel() {
        EditorTestSupport.setupLookAndFeel();
    }

    private static SqlEditor editor() {
        return newEditor(FakeSqlRunner.manual(), InMemoryCompletionSource.biblioteca(), new FakeEditorPrompts());
    }

    @Test
    void evidenziazioneSqlNumeriDiRigaEParentesiAbbinate() {
        SqlEditor editor = editor();
        assertEquals(SyntaxConstants.SYNTAX_STYLE_SQL, fromEdt(() -> editor.textArea().getSyntaxEditingStyle()));
        assertTrue(fromEdt(() -> editor.scrollPane().getLineNumbersEnabled()));
        assertTrue(fromEdt(() -> editor.textArea().isBracketMatchingEnabled()));
        assertTrue(fromEdt(() -> editor.textArea().getPaintMatchedBracketPair()));
        JFrame frame = EditorTestSupport.host(editor, 1000, 520);
        setText(editor, String.join("\n",
                "-- Libri pubblicati dopo il 2000, con l'editore",
                "SELECT l.titolo, l.anno, e.nome AS editore",
                "FROM libri l",
                "JOIN editori e ON (e.id = l.id_editore)",
                "WHERE l.anno > 2000 AND l.prezzo < 25.50",
                "ORDER BY l.anno DESC;"), 0);
        onEdt(() -> editor.textArea().setCaretPosition(editor.getText().indexOf("(e.id")));
        EditorTestSupport.screenshot(frame, "aspetto");
    }

    @Test
    void tutteLeScorciatoieSonoNellInputMap() {
        SqlEditor editor = editor();
        for (KeyStroke k : List.of(SqlEditor.KEY_RUN_CURRENT, SqlEditor.KEY_RUN_ALL, SqlEditor.KEY_ESCAPE,
                SqlEditor.KEY_FIND, SqlEditor.KEY_TOGGLE_COMMENT, SqlEditor.KEY_COMPLETE, SqlEditor.KEY_OPEN,
                SqlEditor.KEY_SAVE)) {
            Object name = fromEdt(() -> editor.textArea().getInputMap(JComponent.WHEN_FOCUSED).get(k));
            assertNotNull(name, "manca " + k);
            assertNotNull(fromEdt(() -> editor.textArea().getActionMap().get(name)), "azione per " + k);
        }
        assertEquals(SqlEditor.ACTION_RUN_CURRENT,
                fromEdt(() -> editor.textArea().getInputMap().get(SqlEditor.KEY_RUN_CURRENT)));
        assertEquals(SqlEditor.ACTION_RUN_ALL, fromEdt(() -> editor.textArea().getInputMap().get(SqlEditor.KEY_RUN_ALL)));
    }

    @Test
    void ctrlBarraCommentaEDecommentaLeRigheSelezionate() {
        SqlEditor editor = editor();
        setText(editor, "SELECT 1;\n  SELECT 2;\n\nSELECT 3;", 0);
        select(editor, 0, "SELECT 1;\n  SELECT 2;\n\nSEL".length());
        press(editor, SqlEditor.KEY_TOGGLE_COMMENT);
        assertEquals("-- SELECT 1;\n--   SELECT 2;\n\n-- SELECT 3;", fromEdt(editor::getText));
        select(editor, 0, fromEdt(editor::getText).length());
        press(editor, SqlEditor.KEY_TOGGLE_COMMENT);
        assertEquals("SELECT 1;\n  SELECT 2;\n\nSELECT 3;", fromEdt(editor::getText));
    }

    @Test
    void ctrlBarraSenzaSelezioneCommentaLaRigaDelCursore() {
        SqlEditor editor = editor();
        setText(editor, "SELECT 1;\nSELECT 2;", "SELECT 1;\nSEL".length());
        press(editor, SqlEditor.KEY_TOGGLE_COMMENT);
        assertEquals("SELECT 1;\n-- SELECT 2;", fromEdt(editor::getText));
        onEdt(() -> editor.textArea().undoLastAction());
        assertEquals("SELECT 1;\nSELECT 2;", fromEdt(editor::getText), "un solo Ctrl+Z annulla il commento");
    }

    @Test
    void righeCommentateNonVengonoEseguite() {
        FakeSqlRunner runner = FakeSqlRunner.manual();
        SqlEditor editor = newEditor(runner, CompletionSource.empty(), new FakeEditorPrompts());
        setText(editor, "SELECT 1;\nSELECT 2;\nSELECT 3;", 0);
        select(editor, "SELECT 1;\n".length(), "SELECT 1;\nSELECT 2;".length());
        press(editor, SqlEditor.KEY_TOGGLE_COMMENT);
        press(editor, SqlEditor.KEY_RUN_ALL);
        assertEquals(List.of("SELECT 1", "SELECT 3"), runner.lastTexts());
    }

    @Test
    void ctrlFApreLaBarraTrovaESostituisci() {
        SqlEditor editor = editor();
        setText(editor, "SELECT nome FROM soci WHERE nome LIKE 'A%';\nSELECT Nome FROM autori;", 0);
        assertFalse(fromEdt(editor::isFindBarVisible));
        select(editor, 7, 11);
        press(editor, SqlEditor.KEY_FIND);
        assertTrue(fromEdt(editor::isFindBarVisible));
        assertTrue(fromEdt(() -> editor.findBar().findNext()));
        assertEquals("nome", fromEdt(() -> editor.textArea().getSelectedText()));
        onEdt(() -> editor.findBar().setReplaceText("cognome"));
        int replaced = fromEdt(() -> editor.findBar().replaceAll());
        assertEquals(3, replaced, "senza distinguere maiuscole e minuscole");
        assertEquals("SELECT cognome FROM soci WHERE cognome LIKE 'A%';\nSELECT cognome FROM autori;",
                fromEdt(editor::getText));
        assertEquals("Sostituiti: 3.", fromEdt(() -> editor.findBar().messageText()));
        onEdt(() -> editor.findBar().setFindText("inesistente"));
        assertFalse(fromEdt(() -> editor.findBar().findNext()));
        assertEquals("Non trovato.", fromEdt(() -> editor.findBar().messageText()));
        JFrame frame = EditorTestSupport.host(editor, 1100, 400);
        EditorTestSupport.screenshot(frame, "trova-sostituisci");
        press(editor, SqlEditor.KEY_ESCAPE);
        assertFalse(fromEdt(editor::isFindBarVisible), "Esc chiude la barra");
    }

    @Test
    void ilCarattereSegueLeImpostazioni() {
        SqlEditor editor = editor();
        Font original = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont")
                : new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        try {
            onEdt(() -> {
                UIManager.put("defaultFont", original.deriveFont(19f));
                SwingUtilities.updateComponentTreeUI(editor);   // ciò che fa FlatLaf.updateUI()
            });
            assertEquals(19, (int) fromEdt(editor::fontSize));
            assertEquals(19, (int) fromEdt(() -> editor.scrollPane().getGutter().getLineNumberFont().getSize()));
            onEdt(() -> editor.setFontSize(24));
            assertEquals(24, (int) fromEdt(editor::fontSize));
        } finally {
            onEdt(() -> UIManager.put("defaultFont", original));
        }
    }
}
