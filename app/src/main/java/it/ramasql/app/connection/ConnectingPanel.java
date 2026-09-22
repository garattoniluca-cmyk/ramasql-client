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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

import it.ramasql.app.Texts;
import it.ramasql.core.connection.ConnectionProfile;

/** Attesa della connessione: a chi ci si sta collegando, un indicatore che si muove e il pulsante <em>Annulla</em>. */
public final class ConnectingPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel title = new JLabel();
    private final JLabel address = new JLabel();
    private final JButton cancel = new JButton(Texts.get("connecting.cancel"));

    public ConnectingPanel(ConnectionController controller) {
        super(new GridBagLayout());
        title.putClientProperty("FlatLaf.styleClass", "h2");
        address.setForeground(UIManager.getColor("Label.disabledForeground"));
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setMaximumSize(new Dimension(320, 6));
        progress.setPreferredSize(new Dimension(320, 6));
        cancel.setName("connecting.cancel");
        cancel.addActionListener(e -> controller.cancelConnecting());

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        for (Component c : new Component[] {title, Box.createVerticalStrut(6), address, Box.createVerticalStrut(24),
                progress, Box.createVerticalStrut(24), cancel}) {
            if (c instanceof javax.swing.JComponent jc) {
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
