/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.settings;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.connection.AppSettings;

/**
 * Impostazioni: <strong>esattamente quattro voci</strong> ({@code DESIGN.md} §3.12) — lingua, dimensione del
 * carattere, limite righe, cartella di lavoro. Una quinta voce non si aggiunge: si annota in {@code BUGS.md}.
 */
public final class SettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Una lingua offerta: codice ISO e nome mostrato. */
    private record Language(String code, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final JComboBox<Language> language = new JComboBox<>();
    private final JSpinner fontSize;
    private final JSpinner rowLimit;
    private final JTextField workDirectory = new JTextField(26);
    private final JLabel error = DialogButtons.errorLabel();
    private final JPanel fields = new JPanel(new GridBagLayout());
    private final List<JComponent> settingEditors = new ArrayList<>();
    private transient AppSettings result;

    public SettingsDialog(Window owner, AppSettings current) {
        super(owner, Texts.get("settings.title"), ModalityType.APPLICATION_MODAL);
        setName("settings.dialog");
        for (Language l : availableLanguages()) {
            language.addItem(l);
            if (l.code().equals(current.language())) {
                language.setSelectedItem(l);
            }
        }
        fontSize = new JSpinner(new SpinnerNumberModel(current.fontSize(), AppSettings.MIN_FONT_SIZE,
                AppSettings.MAX_FONT_SIZE, 1));
        rowLimit = new JSpinner(new SpinnerNumberModel(current.rowLimit(), 1, AppSettings.MAX_ROW_LIMIT, 100));
        rowLimit.setEditor(new JSpinner.NumberEditor(rowLimit, "#"));
        workDirectory.setText(current.workDirectory());
        JButton browse = new JButton(Texts.get("settings.workDirectory.browse"));
        browse.setName("settings.browse");
        browse.addActionListener(e -> browse());
        JPanel directoryRow = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_8), 0));
        directoryRow.setOpaque(false);
        directoryRow.add(workDirectory, BorderLayout.CENTER);
        directoryRow.add(browse, BorderLayout.EAST);

        language.setName("settings.language");
        it.ramasql.app.theme.ComboTips.install(language, l -> it.ramasql.app.theme.Tips.item("settings.language", l.code(), l.label()));
        fontSize.setName("settings.fontSize");
        rowLimit.setName("settings.rowLimit");
        workDirectory.setName("settings.workDirectory");

        addSetting("settings.language", language, false);
        addSetting("settings.fontSize", fontSize, false);
        addSetting("settings.rowLimit", rowLimit, false);
        addSetting("settings.workDirectory", directoryRow, true);
        // una riga di spiegazione sotto ogni voce (dopo le etichette: l'ordine di lettura resta voce → valore)
        String[] hints = {"settings.language.hint", "settings.fontSize.hint", "settings.rowLimit.hint",
                "settings.workDirectory.hint"};
        for (int i = 0; i < hints.length; i++) {
            addHint(i, Texts.get(hints[i]));
        }
        fields.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));

        add(fields, BorderLayout.CENTER);
        DialogButtons buttons = new DialogButtons(this, Texts.get("settings.save"), error, this::save);
        buttons.confirmButton().setToolTipText(Texts.get("settings.save.tooltip"));
        buttons.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24)));
        add(buttons, BorderLayout.SOUTH);
        setResizable(false);
        it.ramasql.app.theme.Tips.fromNames(getRootPane());   // suggerimenti <nome>.tooltip (ADR-020)
        pack();
        setLocationRelativeTo(owner);
        it.ramasql.app.theme.Screens.fit(this);   // dentro lo schermo anche a 1024x768 (T12.6)
    }

    /** Quante voci ha la finestra (T12.7: le impostazioni sono 4). */
    public int settingCount() {
        return settingEditors.size();
    }

    /** Una voce: etichetta a destra nella prima colonna, controllo nella seconda (righe 0, 2, 4, 6). */

    private void addSetting(String labelKey, JComponent editor, boolean fill) {
        int row = settingEditors.size() * 2;
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.LINE_END;
        l.insets = new Insets(Tokens.px(Tokens.SPACE_8), 0, Tokens.px(2), Tokens.px(Tokens.SPACE_12));
        JLabel label = Styles.text(new JLabel(Texts.get(labelKey)), "emphasis", Tokens.TEXT_PRIMARY);
        label.setLabelFor(editor);
        fields.add(label, l);

        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.weightx = 1;
        f.anchor = GridBagConstraints.LINE_START;
        f.fill = fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        f.insets = new Insets(Tokens.px(Tokens.SPACE_8), 0, Tokens.px(2), 0);
        fields.add(editor, f);
        settingEditors.add(editor);
    }

    /** La spiegazione della voce {@code index}, in {@code text.secondary} sotto il controllo. */
    private void addHint(int index, String text) {
        GridBagConstraints h = new GridBagConstraints();
        h.gridx = 1;
        h.gridy = index * 2 + 1;
        h.anchor = GridBagConstraints.LINE_START;
        h.insets = new Insets(0, Tokens.px(2), Tokens.px(Tokens.SPACE_8), 0);
        JLabel hint = Styles.text(new JLabel(text), "smallText", Tokens.TEXT_SECONDARY);
        fields.add(hint, h);
    }

    /** Italiano sempre; le altre lingue compaiono solo se esiste il loro file di testi. */
    private static List<Language> availableLanguages() {
        List<Language> list = new ArrayList<>();
        list.add(new Language("it", Texts.get("settings.language.it")));
        if (SettingsDialog.class.getResource("/it/ramasql/app/messages_en.properties") != null) {
            list.add(new Language("en", Texts.get("settings.language.en")));
        }
        return list;
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(workDirectory.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(Texts.get("settings.workDirectory"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            workDirectory.setText(chosen.getAbsolutePath());
        }
    }

    private void save() {
        String directory = workDirectory.getText().strip();
        if (directory.isEmpty() || !Files.isDirectory(Path.of(directory))) {
            error.setText(Texts.get("settings.error.workDirectory"));
            return;
        }
        result = settings();
        dispose();
    }

    /** Le impostazioni con i valori scritti nella finestra. */
    public AppSettings settings() {
        Language chosen = (Language) language.getSelectedItem();
        return new AppSettings(chosen == null ? Locale.ITALIAN.getLanguage() : chosen.code(),
                ((Number) fontSize.getValue()).intValue(), ((Number) rowLimit.getValue()).intValue(),
                workDirectory.getText().strip());
    }

    /** Mostra la finestra e aspetta. {@code null} = annullato. */
    public AppSettings showModal() {
        setVisible(true);
        return result;
    }

    /** I controlli delle voci, uno per impostazione (i test verificano che siano quattro). */
    public List<JComponent> settingEditors() {
        return List.copyOf(settingEditors);
    }

    public JSpinner fontSizeField() {
        return fontSize;
    }
}
