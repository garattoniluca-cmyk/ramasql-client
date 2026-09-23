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
import static it.ramasql.app.editor.EditorTestSupport.setText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T4.4 lato componente: Ctrl+Spazio è collegato al completamento; {@code SELECT * FROM li} propone {@code libri},
 * {@code libri_autori}; {@code libri.} propone le colonne di {@code libri}. Le proposte sono quelle che il fornitore
 * installato nell'editor restituisce per l'area di testo (cioè ciò che la finestrella mostra).
 */
@Tag("step4")
@Tag("ui")
class T44CompletionTest {

    @BeforeAll
    static void lookAndFeel() {
        EditorTestSupport.setupLookAndFeel();
    }

    private static SqlEditor editor() {
        return newEditor(FakeSqlRunner.manual(), InMemoryCompletionSource.biblioteca(), new FakeEditorPrompts());
    }

    private static List<String> proposalsAt(SqlEditor editor, String text) {
        setText(editor, text, text.length());
        return fromEdt(() -> editor.autoCompletion().getCompletionProvider()
                .getCompletions(editor.textArea()).stream().map(Completion::getReplacementText).toList());
    }

    private static List<String> descriptionsAt(SqlEditor editor, String text) {
        setText(editor, text, text.length());
        return fromEdt(() -> editor.autoCompletion().getCompletionProvider()
                .getCompletions(editor.textArea()).stream()
                .map(c -> c.getReplacementText() + " — " + ((BasicCompletion) c).getShortDescription()).toList());
    }

    @Test
    void ctrlSpazioEAgganciatoAlCompletamento() {
        SqlEditor editor = editor();
        Object name = fromEdt(() -> editor.textArea().getInputMap(JComponent.WHEN_FOCUSED).get(SqlEditor.KEY_COMPLETE));
        assertNotNull(name, "Ctrl+Spazio nell'InputMap");
        assertNotNull(fromEdt(() -> editor.textArea().getActionMap().get(name)), "azione di completamento");
        assertEquals(SqlEditor.KEY_COMPLETE, fromEdt(() -> editor.autoCompletion().getTriggerKey()));
    }

    @Test
    void fromLiProponeLibriELibriAutoriPerPrimi() {
        SqlEditor editor = editor();
        List<String> p = proposalsAt(editor, "SELECT * FROM li");
        assertEquals(List.of("libri", "libri_autori"), p.subList(0, 2));
        assertTrue(p.contains("LIKE") && p.contains("LIMIT"), "poi le parole chiave: " + p);
        assertTrue(p.stream().allMatch(s -> s.toLowerCase().startsWith("li")), p.toString());
        showProposals(editor, "T4.4-li", descriptionsAt(editor, "SELECT * FROM li"));
    }

    @Test
    void dopoLibriPuntoProponeLeColonneDiLibri() {
        SqlEditor editor = editor();
        List<String> p = proposalsAt(editor, "SELECT libri.");
        assertEquals(List.of("anno", "id", "id_editore", "prezzo", "titolo"), p);
        showProposals(editor, "T4.4-libri-punto", descriptionsAt(editor, "SELECT libri."));
    }

    @Test
    void dopoLibriPuntoConPrefissoFiltraLeColonne() {
        assertEquals(List.of("id", "id_editore"), proposalsAt(editor(), "SELECT libri.i"));
    }

    @Test
    void aliasDellaTabellaNelFromPortaAlleSueColonne() {
        SqlEditor editor = editor();
        String text = "SELECT l. FROM libri l JOIN editori AS e ON e.id = l.id_editore";
        setText(editor, text, "SELECT l.".length());
        List<String> p = fromEdt(() -> editor.autoCompletion().getCompletionProvider()
                .getCompletions(editor.textArea()).stream().map(Completion::getReplacementText).toList());
        assertEquals(List.of("anno", "id", "id_editore", "prezzo", "titolo"), p);
        setText(editor, text, text.indexOf("e.id") + 2);
        List<String> e = fromEdt(() -> editor.autoCompletion().getCompletionProvider()
                .getCompletions(editor.textArea()).stream().map(Completion::getReplacementText).toList());
        assertEquals(List.of("citta", "id", "nome"), e);
    }

    @Test
    void catalogoPuntoProponeLeSueTabelleECatalogoTabellaPuntoLeColonne() {
        SqlEditor editor = editor();
        assertEquals(List.of("classi", "studenti"), proposalsAt(editor, "SELECT * FROM scuola."));
        assertEquals(List.of("classe", "matricola", "nome"), proposalsAt(editor, "SELECT scuola.studenti."));
        assertEquals(List.of("anno", "id", "id_editore", "prezzo", "titolo"),
                proposalsAt(editor, "SELECT `biblioteca`.`libri`."));
    }

    @Test
    void senzaQualificatorePrimaLeColonneDelleTabelleNominate() {
        SqlEditor editor = editor();
        String text = "SELECT ti FROM libri";
        setText(editor, text, "SELECT ti".length());
        List<String> p = fromEdt(() -> editor.autoCompletion().getCompletionProvider()
                .getCompletions(editor.textArea()).stream().map(Completion::getReplacementText).toList());
        assertEquals("titolo", p.getFirst());
    }

    @Test
    void cataloghiEParoleChiaveSenzaTabelleCorrispondenti() {
        List<String> p = proposalsAt(editor(), "SEL");
        assertEquals(List.of("SELECT"), p);
        assertEquals(List.of("scuola", "SCHEMA"), proposalsAt(editor(), "USE sc"));
    }

    @Test
    void senzaConnessioneRestanoLeParoleChiave() {
        SqlEditor editor = newEditor(FakeSqlRunner.manual(), CompletionSource.empty(), new FakeEditorPrompts());
        assertEquals(List.of("WHEN", "WHERE", "WHILE"), proposalsAt(editor, "SELECT * FROM x WH"));
    }

    @Test
    void nomiNonSempliciSiInserisconoTraBacktick() {
        assertEquals("libri", SqlCompletionProvider.insertionText("libri"));
        assertEquals("`ordine dettagli`", SqlCompletionProvider.insertionText("ordine dettagli"));
        assertEquals("`order`", SqlCompletionProvider.insertionText("order"));
        assertEquals("`città`", SqlCompletionProvider.insertionText("città"));
    }

    /** Evidenza: l'editor con il testo digitato e, accanto, l'elenco delle proposte com'è restituito. */
    private static void showProposals(SqlEditor editor, String name, List<String> proposals) {
        onEdt(() -> {
            JList<String> list = new JList<>(proposals.toArray(String[]::new));
            list.setSelectedIndex(0);
            JPanel side = new JPanel(new BorderLayout());
            side.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            side.add(new JLabel("Ctrl+Spazio →"), BorderLayout.NORTH);
            side.add(new JScrollPane(list), BorderLayout.CENTER);
            JPanel all = new JPanel(new BorderLayout());
            all.add(editor, BorderLayout.CENTER);
            all.add(side, BorderLayout.EAST);
            EditorTestSupport.screenshot(EditorTestSupport.host(all, 1100, 500), name);
        });
        List<String> lines = new ArrayList<>();
        lines.add("T4.4 — testo: " + fromEdt(editor::getText) + "  (cursore in fondo) + Ctrl+Spazio");
        lines.addAll(proposals);
        EditorTestSupport.writeText(name, EditorTestSupport.join(lines));
    }
}
