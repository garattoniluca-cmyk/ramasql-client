/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.connection;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

import it.ramasql.app.Texts;
import it.ramasql.core.connection.ConnectionProfile;

/**
 * I campi di un profilo (nome, host, porta, utente, catalogo facoltativo, nota) e il pulsante
 * <em>Prova connessione</em>. <strong>Nessun campo password</strong>: si chiede a ogni connessione.
 */
public final class ProfileForm extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient ConnectionController controller;
    private final transient ConnectionProfile initial;

    private final JTextField name = new JTextField(24);
    private final JTextField host = new JTextField(24);
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(ConnectionProfile.DEFAULT_PORT, 1, 65535, 1));
    private final JTextField user = new JTextField(24);
    private final JTextField catalog = new JTextField(24);
    private final JTextArea note = new JTextArea(3, 24);
    private final JButton test = new JButton(Texts.get("profile.test"));
    private final JLabel testStatus = new JLabel(" ");
    private transient ConnectionController.TestHandle runningTest;

    public ProfileForm(ConnectionProfile initial, ConnectionController controller) {
        super(new GridBagLayout());
        this.initial = initial;
        this.controller = controller;
        port.setEditor(new JSpinner.NumberEditor(port, "#"));
        catalog.putClientProperty("JTextField.placeholderText", Texts.get("profile.catalog.placeholder"));
        host.putClientProperty("JTextField.placeholderText", Texts.get("profile.host.placeholder"));
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        if (initial != null) {
            name.setText(initial.name());
            host.setText(initial.host());
            port.setValue(initial.port());
            user.setText(initial.user());
            catalog.setText(initial.defaultCatalog());
            note.setText(initial.note());
        }
        name.setName("profile.name");
        host.setName("profile.host");
        port.setName("profile.port");
        user.setName("profile.user");
        catalog.setName("profile.catalog");
        note.setName("profile.note");
        test.setName("profile.test");
        testStatus.setName("profile.testStatus");

        int row = 0;
        row = addRow(row, "profile.name", name);
        row = addRow(row, "profile.host", host);
        row = addRow(row, "profile.port", port);
        row = addRow(row, "profile.user", user);
        row = addRow(row, "profile.catalog", catalog);
        row = addRow(row, "profile.note", new JScrollPane(note));

        JLabel passwordHint = new JLabel(Texts.get("profile.passwordHint"));
        passwordHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(2, 0, 14, 0);
        add(passwordHint, c);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row++;
        c.anchor = GridBagConstraints.LINE_START;
        add(test, c);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 0, 0);
        add(testStatus, c);

        test.addActionListener(e -> toggleTest());
        setBorder(BorderFactory.createEmptyBorder(24, 28, 12, 28));
    }

    private int addRow(int row, String labelKey, JComponent field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = field instanceof JScrollPane ? GridBagConstraints.FIRST_LINE_END : GridBagConstraints.LINE_END;
        l.insets = new Insets(field instanceof JScrollPane ? 10 : 6, 0, 6, 12);
        add(new JLabel(Texts.get(labelKey)), l);

        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.weightx = 1;
        f.fill = field instanceof JSpinner ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        f.anchor = GridBagConstraints.LINE_START;
        f.insets = new Insets(6, 0, 6, 0);
        add(field, f);
        return row + 1;
    }

    /** Che cosa manca o è sbagliato, in italiano; {@code null} se il profilo si può salvare. */
    public String validationError() {
        if (name.getText().isBlank()) {
            return Texts.get("profile.error.name");
        }
        if (controller.store().nameTakenByOther(name.getText().strip(), initial == null ? "" : initial.id())) {
            return Texts.get("profile.error.nameTaken", name.getText().strip());
        }
        return connectionFieldsError();
    }

    private String connectionFieldsError() {
        if (host.getText().isBlank() || host.getText().strip().matches(".*[\\s/?#@].*")) {
            return Texts.get("profile.error.host");
        }
        if (user.getText().isBlank()) {
            return Texts.get("profile.error.user");
        }
        if (catalog.getText().strip().matches(".*[\\s/?#&].*")) {
            return Texts.get("profile.error.catalog");
        }
        return null;
    }

    /** Il profilo con i valori scritti nei campi (conserva identificativo e ultimo server del profilo iniziale). */
    public ConnectionProfile profile() {
        int portValue = ((Number) port.getValue()).intValue();
        if (initial == null) {
            return ConnectionProfile.create(name.getText(), host.getText(), portValue, user.getText(),
                    catalog.getText(), note.getText());
        }
        return initial.withDetails(name.getText(), host.getText(), portValue, user.getText(), catalog.getText(),
                note.getText());
    }

    /** Un solo pulsante: «Prova connessione», che durante la prova diventa «Annulla prova». */
    private void toggleTest() {
        if (runningTest != null) {
            runningTest.cancel();
            return;
        }
        String error = connectionFieldsError();
        if (error != null) {
            showStatus(error, false);
            return;
        }
        ConnectionController.TestHandle started = controller.testConnection(profile(), result -> {
            runningTest = null;
            test.setText(Texts.get("profile.test"));
            showStatus(result.text(), result.success());
        });
        // l'esito arriva sempre più tardi, sull'EDT: qui la prova è appena partita (o la password non è stata data)
        if (started != null) {
            runningTest = started;
            test.setText(Texts.get("profile.test.cancel"));
            showStatus(Texts.get("profile.test.running"), true);
        }
    }

    /**
     * La finestra si chiude: la prova in corso si annulla e il suo esito non si mostra più (nessuna finestra
     * d'errore che compare «dal nulla» dopo la chiusura).
     */
    public void abandonTest() {
        ConnectionController.TestHandle running = runningTest;
        runningTest = null;
        if (running != null) {
            running.abandon();
        }
    }

    /** Vero mentre una prova è in corso. */
    public boolean isTesting() {
        return runningTest != null;
    }

    private void showStatus(String text, boolean ok) {
        testStatus.setText(text);
        testStatus.setForeground(ok ? UIManager.getColor("Label.foreground") : UIManager.getColor("Component.error.focusedBorderColor"));
    }

    public JTextField nameField() {
        return name;
    }

    public JTextField hostField() {
        return host;
    }

    public JSpinner portField() {
        return port;
    }

    public JTextField userField() {
        return user;
    }

    public JTextField catalogField() {
        return catalog;
    }

    public JButton testButton() {
        return test;
    }

    public JLabel testStatusLabel() {
        return testStatus;
    }
}
