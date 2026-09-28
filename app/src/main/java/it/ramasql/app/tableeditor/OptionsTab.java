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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.TableDef;

/**
 * Scheda <b>Opzioni</b>: nome della tabella (rinomina), engine InnoDB/MyISAM con la spiegazione delle conseguenze,
 * charset, collation, AUTO_INCREMENT iniziale, commento. Ogni campo aggiorna subito il modello (e l'anteprima SQL).
 */
final class OptionsTab extends JPanel {

    private static final long serialVersionUID = 1L;

    static final List<String> ENGINES = List.of("InnoDB", "MyISAM");
    private static final List<String> CHARSETS = List.of("", "utf8mb4", "utf8mb3", "latin1", "ascii");
    private static final List<String> COLLATIONS = List.of("", "utf8mb4_unicode_ci", "utf8mb4_general_ci",
            "utf8mb4_bin", "utf8mb4_0900_ai_ci", "utf8mb4_uca1400_ai_ci", "utf8mb3_general_ci", "latin1_swedish_ci",
            "latin1_general_ci", "ascii_general_ci");

    private final TableEditor editor;
    private final JTextField name = new JTextField(30);
    private final JComboBox<String> engine = new JComboBox<>(ENGINES.toArray(String[]::new));
    private final Banner engineNotes = new Banner("options.engineNotes");
    private final Banner engineBlocked = new Banner("options.engineBlocked");
    private final JComboBox<String> charset = new JComboBox<>(CHARSETS.toArray(String[]::new));
    private final JComboBox<String> collation = new JComboBox<>(COLLATIONS.toArray(String[]::new));
    private final Banner charsetNotes = new Banner("options.charsetNotes");
    private final JTextField autoIncrement = new JTextField(12);
    private final JTextField comment = new JTextField(40);
    private final Banner notes = new Banner("options.notes");
    private boolean refreshing;

    OptionsTab(TableEditor editor) {
        super(new BorderLayout());
        this.editor = editor;
        setName("tableeditor.options");
        name.setName("options.name");
        engine.setName("options.engine");
        charset.setName("options.charset");
        collation.setName("options.collation");
        autoIncrement.setName("options.autoIncrement");
        comment.setName("options.comment");
        charset.setEditable(true);
        collation.setEditable(true);
        it.ramasql.app.theme.ComboTips.install(engine, it.ramasql.app.theme.Tips.of("tableeditor.engine"));
        it.ramasql.app.theme.ComboTips.install(charset, it.ramasql.app.theme.Tips::charset);
        it.ramasql.app.theme.ComboTips.install(collation, it.ramasql.app.theme.Tips::collation);
        setBackground(Tokens.BG_SURFACE);
        for (JComponent field : List.of(name, engine, charset, collation, autoIncrement, comment)) {
            Dimension d = field.getPreferredSize();
            field.setPreferredSize(new Dimension(Math.max(d.width, 220), Tokens.CONTROL_HEIGHT));
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_24, Tokens.SPACE_24,
                Tokens.SPACE_24, Tokens.SPACE_24));
        int row = 0;
        row = addRow(form, row, "tableeditor.options.name", name);
        row = addRow(form, row, "tableeditor.options.engine", engine);
        row = addRow(form, row, null, engineNotes);
        row = addRow(form, row, null, engineBlocked);
        row = addRow(form, row, "tableeditor.options.charset", charset);
        row = addRow(form, row, "tableeditor.options.collation", collation);
        row = addRow(form, row, null, charsetNotes);
        row = addRow(form, row, "tableeditor.options.autoIncrement", autoIncrement);
        row = addRow(form, row, "tableeditor.options.comment", comment);
        row = addRow(form, row, null, notes);
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = row;
        filler.weighty = 1;
        JPanel empty = new JPanel();
        empty.setOpaque(false);
        form.add(empty, filler);
        add(form, BorderLayout.CENTER);

        onText(name, text -> editor.update(t -> t.withName(text)));
        onText(comment, text -> editor.update(t -> t.withComment(text)));
        onText(autoIncrement, this::autoIncrementChanged);
        engine.addActionListener(e -> {
            if (!refreshing) {
                editor.requestEngine((String) engine.getSelectedItem());
            }
        });
        charset.addActionListener(e -> {
            if (!refreshing) {
                charsetChanged(text(charset), null);
            }
        });
        collation.addActionListener(e -> {
            if (!refreshing) {
                charsetChanged(text(charset), text(collation));
            }
        });
    }

    private static int addRow(JPanel form, int row, String labelKey, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(Tokens.SPACE_4, 0, Tokens.SPACE_4, Tokens.SPACE_16);
        c.anchor = GridBagConstraints.WEST;
        if (labelKey != null) {
            c.gridx = 0;
            JLabel label = new JLabel(Texts.get(labelKey));
            label.setForeground(Tokens.TEXT_SECONDARY);
            form.add(label, c);
        }
        c.gridx = 1;
        c.weightx = 1;
        c.fill = field instanceof Banner ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        form.add(field, c);
        return row + 1;
    }

    private void onText(JTextField field, Consumer<String> change) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fire();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fire();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fire();
            }

            private void fire() {
                if (!refreshing) {
                    change.accept(field.getText());
                }
            }
        });
    }

    private static String text(JComboBox<String> combo) {
        Object v = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        Object selected = combo.getSelectedItem();
        String s = selected != null ? selected.toString() : v == null ? "" : v.toString();
        return s.strip();
    }

    // ================================================================ azioni (come l'utente)

    void setTableName(String text) {
        name.setText(text);
    }

    void setEngine(String value) {
        engine.setSelectedItem(value);
    }

    void setCharset(String cs, String co) {
        refreshing = true;
        try {
            charset.setSelectedItem(cs);
            collation.setSelectedItem(co);
        } finally {
            refreshing = false;
        }
        charsetChanged(cs, co);
    }

    void setAutoIncrement(String text) {
        autoIncrement.setText(text);
    }

    void setComment(String text) {
        comment.setText(text);
    }

    String tableNameText() {
        return name.getText();
    }

    String engineNotesText() {
        return engineNotes.text();
    }

    String engineBlockedText() {
        return engineBlocked.text();
    }

    String charsetNotesText() {
        return charsetNotes.text();
    }

    String notesText() {
        return notes.text();
    }

    Object selectedEngine() {
        return engine.getSelectedItem();
    }

    private void charsetChanged(String cs, String co) {
        String newCharset = cs == null || cs.isBlank() ? null : cs;
        String newCollation = co == null || co.isBlank() ? null : co;
        if (newCollation != null && newCharset != null
                && !newCollation.toLowerCase(Locale.ROOT).startsWith(newCharset.toLowerCase(Locale.ROOT) + "_")) {
            newCollation = null;             // collation di un altro charset: la sceglie il server
        }
        String collationValue = newCollation;
        editor.update(t -> Edits.setCharset(t, editor.originalTable(), newCharset, collationValue));
    }

    private void autoIncrementChanged(String text) {
        String s = text.strip();
        if (s.isEmpty()) {
            editor.update(t -> t.withAutoIncrementStart(null));
            return;
        }
        try {
            long v = Long.parseLong(s);
            if (v > 0) {
                editor.update(t -> t.withAutoIncrementStart(v));
                return;
            }
        } catch (NumberFormatException e) {
            // sotto: spiegazione
        }
        notes.set(Banner.Tone.DANGER, Texts.get("tableeditor.error.autoIncrementValue", s));
    }

    /** Messaggio del blocco del cambio di engine (vuoto = nessun blocco). */
    void showEngineBlocked(String message) {
        engineBlocked.set(Banner.Tone.DANGER, message);
    }

    // ================================================================ aggiornamento

    void refresh() {
        TableDef t = editor.editedTable();
        TableDef o = editor.originalTable();
        refreshing = true;
        try {
            if (!name.getText().equals(t.name())) {
                name.setText(t.name());
            }
            String e = t.engine() == null ? "InnoDB" : canonicalEngine(t.engine());
            if (!e.equals(engine.getSelectedItem())) {
                engine.setSelectedItem(e);
            }
            String cs = t.charset() == null ? "" : t.charset();
            if (!cs.equals(text(charset))) {
                charset.setSelectedItem(cs);
            }
            String co = t.collation() == null ? "" : t.collation();
            if (!co.equals(text(collation))) {
                collation.setSelectedItem(co);
            }
            String ai = t.autoIncrementStart() == null ? "" : t.autoIncrementStart().toString();
            if (!ai.equals(autoIncrement.getText().strip()) && !autoIncrementInvalid()) {
                autoIncrement.setText(ai);
            }
            if (!comment.getText().equals(t.comment())) {
                comment.setText(t.comment());
            }
        } finally {
            refreshing = false;
        }
        engineNotes.set(Banner.Tone.INFO, engineExplanation(o, t));
        boolean charsetChanged = o != null && (!same(o.charset(), t.charset()) || !same(o.collation(), t.collation()));
        charsetNotes.set(Banner.Tone.INFO, charsetChanged ? Texts.get("tableeditor.options.charsetExisting") : "");
        Checks checks = editor.checks();
        if (!autoIncrementInvalid()) {
            List<Checks.Problem> all = new java.util.ArrayList<>(checks.errors(Checks.Area.OPTIONS));
            all.addAll(checks.warnings(Checks.Area.OPTIONS));
            notes.setProblems(all);
        }
    }

    private boolean autoIncrementInvalid() {
        String s = autoIncrement.getText().strip();
        if (s.isEmpty()) {
            return false;
        }
        try {
            return Long.parseLong(s) <= 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b == null ? "" : b);
    }

    static String canonicalEngine(String engine) {
        for (String e : ENGINES) {
            if (e.equalsIgnoreCase(engine)) {
                return e;
            }
        }
        return engine;
    }

    /** Conseguenze dell'engine scelto e, se cambia su una tabella esistente, di cosa fa il server. */
    private static String engineExplanation(TableDef original, TableDef edited) {
        String key = edited.isInnoDb() ? "tableeditor.options.engine.innodb" : "tableeditor.options.engine.myisam";
        StringBuilder sb = new StringBuilder(Texts.get(key));
        if (original != null && !canonicalEngine(original.engine() == null ? "InnoDB" : original.engine())
                .equals(canonicalEngine(edited.engine() == null ? "InnoDB" : edited.engine()))) {
            sb.append('\n').append(Texts.get("tableeditor.options.engine.rebuild",
                    original.engine() == null ? "InnoDB" : original.engine(), edited.engine()));
        }
        return sb.toString();
    }
}
