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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.UIManager;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.core.connection.ConnectionProfile;

/** Richiesta della password: si digita a ogni connessione e non viene salvata da nessuna parte. */
public final class PasswordDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JPasswordField password = new JPasswordField(22);
    private transient char[] result;

    public PasswordDialog(Window owner, ConnectionProfile profile) {
        super(owner, Texts.get("password.title"), ModalityType.APPLICATION_MODAL);
        JLabel heading = new JLabel(Texts.get("password.heading", profile.name().isEmpty() ? profile.host() : profile.name()));
        heading.putClientProperty("FlatLaf.styleClass", "h3");
        JLabel address = new JLabel(profile.address());
        address.setForeground(UIManager.getColor("Label.disabledForeground"));
        JLabel label = new JLabel(Texts.get("password.label", profile.user()));
        label.setLabelFor(password);
        password.setName("password.field");
        password.putClientProperty("JPasswordField.showRevealButton", true);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(24, 28, 16, 28));
        for (Component c : new Component[] {heading, Box.createVerticalStrut(4), address, Box.createVerticalStrut(20), label,
                Box.createVerticalStrut(6), password}) {
            if (c instanceof javax.swing.JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            body.add(c);
        }
        add(body, BorderLayout.CENTER);
        add(new DialogButtons(this, Texts.get("password.connect"), null, this::confirm), BorderLayout.SOUTH);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    private void confirm() {
        result = password.getPassword();
        dispose();
    }

    /** Mostra la finestra e aspetta. {@code null} = annullato. Il campo viene svuotato in ogni caso. */
    public char[] showModal() {
        setVisible(true);
        password.setText("");
        return result;
    }

    public JPasswordField passwordField() {
        return password;
    }
}
