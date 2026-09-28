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

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.connection.ConnectionProfile;

/** Richiesta della password: si digita a ogni connessione e non viene salvata da nessuna parte. */
public final class PasswordDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JPasswordField password = new JPasswordField(22);
    private transient char[] result;

    public PasswordDialog(Window owner, ConnectionProfile profile) {
        super(owner, Texts.get("password.title"), ModalityType.APPLICATION_MODAL);
        JLabel heading = new JLabel(Texts.get("password.heading", profile.name().isEmpty() ? profile.host() : profile.name()));
        Styles.text(heading, "heading", Tokens.TEXT_PRIMARY);
        heading.setIcon(AppIcons.get(AppIcons.CONNECT, 24));
        heading.setIconTextGap(Tokens.px(Tokens.SPACE_12));
        JLabel address = new JLabel(profile.address());
        address.setForeground(Tokens.TEXT_SECONDARY);
        address.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(24 + Tokens.SPACE_12), 0, 0));
        JLabel hint = new JLabel(Texts.get("password.hint"));
        hint.setForeground(Tokens.TEXT_SECONDARY);
        JLabel label = new JLabel(Texts.get("password.label", profile.user()));
        label.setLabelFor(password);
        password.setName("password.field");
        password.putClientProperty("JPasswordField.showRevealButton", true);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));
        for (Component c : new Component[] {heading, Box.createVerticalStrut(Tokens.px(2)), address,
                Box.createVerticalStrut(Tokens.px(Tokens.SPACE_24)), label, Box.createVerticalStrut(Tokens.px(6)),
                password, Box.createVerticalStrut(Tokens.px(Tokens.SPACE_8)), hint}) {
            if (c instanceof javax.swing.JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            body.add(c);
        }
        add(body, BorderLayout.CENTER);
        DialogButtons buttons = new DialogButtons(this, Texts.get("password.connect"), null, this::confirm);
        buttons.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24)));
        add(buttons, BorderLayout.SOUTH);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
        it.ramasql.app.theme.Screens.fit(this);   // dentro lo schermo anche a 1024x768 (T12.6)
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
