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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.editor.EditorPrompts.SaveChoice;

/** Apri/Salva {@code .sql} in UTF-8 (accenti ed emoji), titolo con «*», domanda alla chiusura. */
@Tag("step4")
@Tag("ui")
class FileOpenSaveCloseTest {

    private static final String EMOJI = new String(Character.toChars(0x1F4DA));   // libri
    static final String CONTENT = "-- Città, perché, àèìòù, «virgolette» " + EMOJI + "\n"
            + "INSERT INTO soci (nome) VALUES ('Niccolò D''Àlvaro " + EMOJI + "');\n";

    @TempDir
    Path dir;

    @BeforeAll
    static void lookAndFeel() {
        EditorTestSupport.setupLookAndFeel();
    }

    @Test
    void salvaERiapreConAccentiEdEmojiInUtf8() throws IOException {
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.fileToSave = dir.resolve("prova accenti");
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        onEdt(() -> editor.setText(CONTENT));
        assertEquals("Query 1 *", fromEdt(editor::title));

        press(editor, SqlEditor.KEY_SAVE);

        Path saved = dir.resolve("prova accenti.sql");
        assertTrue(Files.exists(saved), "estensione .sql aggiunta");
        assertEquals(List.of("Query 1.sql"), prompts.suggestedNames);
        byte[] bytes = Files.readAllBytes(saved);
        assertFalse(bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB, "niente BOM");
        String expectedOnDisk = CONTENT.replace("\n", System.lineSeparator());
        assertArrayEquals(expectedOnDisk.getBytes(StandardCharsets.UTF_8), bytes, "UTF-8 byte per byte");
        assertEquals("prova accenti.sql", fromEdt(editor::title));
        assertFalse(fromEdt(editor::isModified));

        SqlEditor other = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), new FakeEditorPrompts());
        assertTrue(fromEdt(() -> other.open(saved)));
        assertEquals(CONTENT, fromEdt(other::getText));
        assertEquals("prova accenti.sql", fromEdt(other::title));
        assertFalse(fromEdt(other::isModified));

        List<String> ev = new ArrayList<>();
        ev.add("Salvataggio e riapertura in UTF-8 (senza BOM) di: " + CONTENT.lines().findFirst().orElseThrow());
        ev.add("Byte scritti: " + bytes.length + "; testo riletto identico: " + CONTENT.equals(fromEdt(other::getText)));
        ev.add("Titolo dopo il salvataggio: " + fromEdt(editor::title));
        EditorTestSupport.writeText("file-utf8", EditorTestSupport.join(ev));
    }

    @Test
    void apreUnFileConBomEConservaGliACapoWindows() throws IOException {
        Path f = dir.resolve("windows.sql");
        byte[] body = "SELECT 'è';\r\nSELECT 2;\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        Files.write(f, withBom);
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.fileToOpen = f;
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);

        press(editor, SqlEditor.KEY_OPEN);

        assertEquals("SELECT 'è';\nSELECT 2;\n", fromEdt(editor::getText));
        onEdt(() -> editor.textArea().append("SELECT 3;\n"));
        assertEquals("windows.sql *", fromEdt(editor::title));
        assertTrue(fromEdt(editor::save));
        assertEquals("SELECT 'è';\r\nSELECT 2;\r\nSELECT 3;\r\n", Files.readString(f, StandardCharsets.UTF_8));
    }

    @Test
    void fileIllegibileMostraUnErroreELasciaIlTesto() {
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        onEdt(() -> editor.setText("SELECT 1"));
        assertFalse(fromEdt(() -> editor.open(dir.resolve("non-esiste.sql"))));
        assertEquals(1, prompts.errors.size());
        assertTrue(prompts.errors.getFirst().startsWith("File non accessibile: Impossibile leggere «non-esiste.sql»"));
        assertEquals("SELECT 1", fromEdt(editor::getText));
    }

    @Test
    void chiusuraSenzaModificheNonChiedeNulla() {
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        assertTrue(fromEdt(editor::canClose));
        assertTrue(prompts.saveQuestions.isEmpty());
    }

    @Test
    void chiusuraConModificheAnnullaResta() {
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.saveChoice = SaveChoice.CANCEL;
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        onEdt(() -> editor.setText("SELECT 1"));
        assertFalse(fromEdt(editor::canClose));
        assertEquals(List.of("Query 1"), prompts.saveQuestions);
    }

    @Test
    void chiusuraConModificheNonSalvareChiude() {
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.saveChoice = SaveChoice.DISCARD;
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        onEdt(() -> editor.setText("SELECT 1"));
        assertTrue(fromEdt(editor::canClose));
        assertTrue(prompts.suggestedNames.isEmpty(), "nessun salvataggio");
    }

    @Test
    void chiusuraConModificheSalvaScriveIlFileEChiude() throws IOException {
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.saveChoice = SaveChoice.SAVE;
        prompts.fileToSave = dir.resolve("chiusura.sql");
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        onEdt(() -> editor.setText("SELECT 'salvato alla chiusura'"));
        assertTrue(fromEdt(editor::canClose));
        assertEquals("SELECT 'salvato alla chiusura'", Files.readString(prompts.fileToSave));
    }

    @Test
    void chiusuraConSalvaAnnullatoNellaSceltaDelFileResta() {
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.saveChoice = SaveChoice.SAVE;
        prompts.fileToSave = null;
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        onEdt(() -> editor.setText("SELECT 1"));
        assertFalse(fromEdt(editor::canClose));
        assertTrue(fromEdt(editor::isModified));
    }

    @Test
    void apriConModificheChiedePrimaSeSalvare() throws IOException {
        Path f = Files.writeString(dir.resolve("altro.sql"), "SELECT 'altro'");
        FakeEditorPrompts prompts = new FakeEditorPrompts();
        prompts.saveChoice = SaveChoice.CANCEL;
        prompts.fileToOpen = f;
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), prompts);
        onEdt(() -> editor.setText("SELECT 'mio lavoro'"));
        assertFalse(fromEdt(editor::openFile));
        assertEquals("SELECT 'mio lavoro'", fromEdt(editor::getText));
        prompts.saveChoice = SaveChoice.DISCARD;
        assertTrue(fromEdt(editor::openFile));
        assertEquals("SELECT 'altro'", fromEdt(editor::getText));
        assertEquals("altro.sql", fromEdt(editor::title));
    }

    @Test
    void ilTitoloAvvisaDiOgniCambio() {
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), new FakeEditorPrompts());
        List<Object> titles = new ArrayList<>();
        onEdt(() -> editor.addPropertyChangeListener(SqlEditor.PROPERTY_TITLE, e -> titles.add(e.getNewValue())));
        onEdt(() -> editor.textArea().append("S"));
        onEdt(() -> editor.textArea().append("E"));
        assertEquals(List.of("Query 1 *"), titles, "un solo avviso: il titolo cambia una volta");
    }
}
