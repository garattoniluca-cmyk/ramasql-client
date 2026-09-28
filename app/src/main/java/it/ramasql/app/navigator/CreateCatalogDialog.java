/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.navigator;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.CollationInfo;

/**
 * «Nuovo catalogo…» (come <em>Create Schema</em> di Workbench, ridotto): nome, charset e collation, con valori
 * predefiniti giusti ({@code utf8mb4} e la sua collation predefinita sul server). Il pulsante porta
 * all'anteprima dell'SQL: qui non si esegue nulla.
 */
public final class CreateCatalogDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Lunghezza massima di un nome di catalogo sui due server. */
    public static final int MAX_NAME_LENGTH = 64;

    /** Cosa ha scelto l'utente. */
    public record Choice(String name, String charset, String collation) {
    }

    private final transient List<CollationInfo> collations;
    private final JTextField name = new JTextField(24);
    private final JComboBox<String> charset = new JComboBox<>();
    private final JComboBox<String> collation = new JComboBox<>();
    private final JLabel error = DialogButtons.errorLabel();
    private final DialogButtons buttons;
    private Choice result;

    public CreateCatalogDialog(Window owner, List<CollationInfo> collations, String defaultCharset,
            String defaultCollation) {
        super(owner, Texts.get("catalog.create.title"), ModalityType.APPLICATION_MODAL);
        this.collations = List.copyOf(collations);
        setName("catalog.create.dialog");
        name.setName("catalog.create.name");
        charset.setName("catalog.create.charset");
        collation.setName("catalog.create.collation");
        it.ramasql.app.theme.ComboTips.install(charset, it.ramasql.app.theme.Tips::charset);
        it.ramasql.app.theme.ComboTips.install(collation, it.ramasql.app.theme.Tips::collation);
        name.putClientProperty("JTextField.placeholderText", Texts.get("catalog.create.name.placeholder"));

        TreeSet<String> charsets = new TreeSet<>();
        collations.forEach(c -> charsets.add(c.charset()));
        charsets.forEach(charset::addItem);
        if (defaultCharset != null && charsets.contains(defaultCharset)) {
            charset.setSelectedItem(defaultCharset);
        }
        fillCollations(defaultCollation);
        charset.addActionListener(e -> fillCollations(null));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int pad = Tokens.px(Tokens.SPACE_24);
        form.setBorder(BorderFactory.createEmptyBorder(pad, pad + Tokens.px(4), Tokens.px(Tokens.SPACE_8), pad + Tokens.px(4)));
        JLabel heading = new JLabel(Texts.get("catalog.create.title"));
        heading.setFont(Tokens.font(Tokens.HEADING, java.awt.Font.BOLD));
        heading.setForeground(Tokens.TEXT_PRIMARY);
        GridBagConstraints t = new GridBagConstraints();
        t.gridx = 0;
        t.gridy = 0;
        t.gridwidth = 2;
        t.anchor = GridBagConstraints.WEST;
        t.insets = new Insets(0, 0, Tokens.px(Tokens.SPACE_16), 0);
        form.add(heading, t);
        row(form, 1, "catalog.create.name.label", name);
        row(form, 2, "catalog.create.charset.label", charset);
        row(form, 3, "catalog.create.collation.label", collation);
        JLabel hint = new JLabel(Texts.get("catalog.create.hint"));
        hint.setForeground(Tokens.TEXT_TERTIARY);
        GridBagConstraints h = new GridBagConstraints();
        h.gridx = 1;
        h.gridy = 4;
        h.anchor = GridBagConstraints.WEST;
        h.insets = new Insets(6, 0, 0, 0);
        form.add(hint, h);

        buttons = new DialogButtons(this, Texts.get("catalog.create.confirm"), error, this::confirm);
        buttons.confirmButton().setToolTipText(Texts.get("catalog.create.confirm.tooltip"));
        Styles.primary(buttons.confirmButton(), Tokens.ACCENT);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Tokens.BG_SURFACE);
        buttons.setOpaque(false);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        it.ramasql.app.theme.Tips.fromNames(getRootPane());   // suggerimenti <nome>.tooltip (ADR-020)
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        it.ramasql.app.theme.Screens.fit(this);   // dentro lo schermo anche a 1024x768 (T12.6)
    }

    private static void row(JPanel form, int y, String labelKey, java.awt.Component field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = y;
        l.anchor = GridBagConstraints.WEST;
        l.insets = new Insets(4, 0, 4, 12);
        JLabel label = new JLabel(Texts.get(labelKey));
        form.add(label, l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = y;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.weightx = 1;
        f.insets = new Insets(4, 0, 4, 0);
        form.add(field, f);
    }

    /** Collation del charset scelto; selezionata quella indicata, altrimenti la predefinita del charset. */
    private void fillCollations(String wanted) {
        String cs = (String) charset.getSelectedItem();
        collation.removeAllItems();
        List<String> names = new ArrayList<>();
        String preferred = null;
        for (CollationInfo c : collations) {
            if (c.charset().equals(cs)) {
                names.add(c.name());
                if (c.isDefault()) {
                    preferred = c.name();
                }
            }
        }
        names.sort(null);
        names.forEach(collation::addItem);
        if (wanted != null && names.contains(wanted)) {
            collation.setSelectedItem(wanted);
        } else if (preferred != null) {
            collation.setSelectedItem(preferred);
        }
    }

    private void confirm() {
        String n = name.getText().strip();
        if (n.isEmpty()) {
            error.setText(Texts.get("catalog.create.error.empty"));
            return;
        }
        if (n.length() > MAX_NAME_LENGTH) {
            error.setText(Texts.get("catalog.create.error.long", MAX_NAME_LENGTH));
            return;
        }
        if (n.toLowerCase(Locale.ROOT).equals("information_schema")) {
            error.setText(Texts.get("catalog.create.error.reserved"));
            return;
        }
        result = new Choice(n, (String) charset.getSelectedItem(), (String) collation.getSelectedItem());
        dispose();
    }

    /** Mostra la finestra (modale); {@code null} = annullata. */
    public Choice showModal() {
        result = null;
        setVisible(true);
        return result;
    }

    /** La scelta confermata ({@code null} finché non si preme il pulsante con dati validi). */
    public Choice result() {
        return result;
    }

    // ------------------------------------------------------------------ accesso per i test

    public JTextField nameField() {
        return name;
    }

    public JComboBox<String> charsetBox() {
        return charset;
    }

    public JComboBox<String> collationBox() {
        return collation;
    }

    public DialogButtons buttons() {
        return buttons;
    }

    public JLabel errorLabel() {
        return error;
    }
}
