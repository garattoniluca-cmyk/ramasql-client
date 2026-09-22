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
import java.awt.Dimension;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;

/**
 * Connessione non riuscita: in alto la spiegazione in italiano (<em>che cosa correggere</em>), sotto — sempre —
 * il messaggio originale del server con il suo codice, selezionabile per copiarlo.
 */
public final class ConnectionErrorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextArea explanation;
    private final JTextArea original;

    public ConnectionErrorDialog(Window owner, ConnectionProfile profile, ConnectionFailure failure) {
        super(owner, Texts.get("connect.error.title"), ModalityType.APPLICATION_MODAL);
        int em = getFontMetrics(UIManager.getFont("Label.font")).getHeight();
        int width = em * 30;

        JLabel heading = new JLabel(Texts.get("connect.error.heading", profile.name()));
        heading.putClientProperty("FlatLaf.styleClass", "h3");
        explanation = wrapped(failure.message(), width);
        explanation.setName("connect.error.explanation");
        JLabel originalTitle = new JLabel(Texts.get("connect.error.original"));
        originalTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        original = wrapped(failure.originalDetail(), width);
        original.setName("connect.error.original");

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(24, 28, 16, 28));
        for (Component c : new Component[] {heading, Box.createVerticalStrut(12), explanation, Box.createVerticalStrut(20),
                originalTitle, Box.createVerticalStrut(6), original}) {
            if (c instanceof javax.swing.JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            body.add(c);
        }
        add(body, BorderLayout.CENTER);
        add(new DialogButtons(this, Texts.get("dialog.ok"), null, null, this::dispose), BorderLayout.SOUTH);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Testo su più righe, selezionabile, largo {@code width} pixel e alto quanto serve. */
    private static JTextArea wrapped(String text, int width) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(UIManager.getFont("Label.font"));
        area.setSize(width, Short.MAX_VALUE);
        area.setPreferredSize(new Dimension(width, area.getPreferredSize().height));
        return area;
    }

    public String explanationText() {
        return explanation.getText();
    }

    public String originalText() {
        return original.getText();
    }
}
