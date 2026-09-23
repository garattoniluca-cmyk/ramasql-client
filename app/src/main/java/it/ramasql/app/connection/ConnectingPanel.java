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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.connection.ConnectionProfile;

/**
 * Attesa della connessione: il cilindro del database, a chi ci si sta collegando, un indicatore sottile che si muove
 * e il pulsante <em>Annulla</em>. Tutto al centro, su {@code bg.window}.
 */
public final class ConnectingPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel title = Styles.text(new JLabel(), "heading", Tokens.TEXT_PRIMARY);
    private final JLabel address = new JLabel();
    private final JButton cancel = new JButton(Texts.get("connecting.cancel"));

    public ConnectingPanel(ConnectionController controller) {
        super(new GridBagLayout());
        setBackground(Tokens.BG_WINDOW);
        address.setForeground(Tokens.TEXT_SECONDARY);
        JLabel image = new JLabel(AppIcons.get(AppIcons.TREE_CATALOG, 48));
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        Dimension bar = new Dimension(Tokens.px(280), Tokens.px(6));
        progress.setMaximumSize(bar);
        progress.setPreferredSize(bar);
        cancel.setName("connecting.cancel");
        cancel.addActionListener(e -> controller.cancelConnecting());

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        for (Component c : new Component[] {image, Box.createVerticalStrut(Tokens.px(Tokens.SPACE_16)), title,
                Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)), address,
                Box.createVerticalStrut(Tokens.px(Tokens.SPACE_24)), progress,
                Box.createVerticalStrut(Tokens.px(Tokens.SPACE_24)), cancel}) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
            box.add(c);
        }
        add(box);
    }

    public void setProfile(ConnectionProfile profile) {
        title.setText(Texts.get("connecting.title", profile.name()));
        address.setText(profile.address());
    }

    public JButton cancelButton() {
        return cancel;
    }
}
