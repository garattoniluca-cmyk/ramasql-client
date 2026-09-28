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
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;

/**
 * Connessione non riuscita ({@code DESIGN-SYSTEM.md} §3.7): un titolo umano («Non riesco a connettermi a …») con
 * l'icona d'errore, <em>che cosa correggere</em> in italiano e, sotto, il <strong>messaggio originale</strong> del
 * server con il suo codice, in un riquadro monospaziato selezionabile (per copiarlo) che si può ripiegare.
 * Aperto all'inizio: il messaggio del server si mostra sempre.
 */
public final class ConnectionErrorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextArea explanation;
    private final JTextArea original;
    private final JPanel originalBox;
    private final JButton toggle;

    public ConnectionErrorDialog(Window owner, ConnectionProfile profile, ConnectionFailure failure) {
        super(owner, Texts.get("connect.error.title"), ModalityType.APPLICATION_MODAL);
        int width = Tokens.px(440);

        JLabel heading = Styles.text(new JLabel(Texts.get("connect.error.heading", profile.name())), "heading",
                Tokens.TEXT_PRIMARY);
        heading.setIcon(AppIcons.get(AppIcons.STATUS_ERROR, 24));
        heading.setIconTextGap(Tokens.px(Tokens.SPACE_12));
        explanation = wrapped(failure.message(), width, false);
        explanation.setName("connect.error.explanation");

        toggle = new JButton(Texts.get("connect.error.original"), AppIcons.small(AppIcons.CHEVRON_DOWN));
        toggle.setName("connect.error.toggle");
        toggle.setToolTipText(Texts.get("connect.error.original.hide"));
        Styles.toolbarButton(toggle);
        toggle.setFocusable(true);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setForeground(Tokens.TEXT_SECONDARY);
        Styles.text(toggle, "emphasis");
        original = wrapped(failure.originalDetail(), width - 2 * Tokens.px(Tokens.SPACE_12), true);
        original.setName("connect.error.original");
        originalBox = new JPanel(new BorderLayout());
        originalBox.setBackground(Tokens.BG_SUNKEN);
        originalBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tokens.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12),
                        Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12))));
        originalBox.add(original, BorderLayout.CENTER);
        toggle.addActionListener(e -> setOriginalShown(!originalBox.isVisible()));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));
        for (Component c : new Component[] {heading, Box.createVerticalStrut(Tokens.px(Tokens.SPACE_12)), explanation,
                Box.createVerticalStrut(Tokens.px(Tokens.SPACE_16)), toggle,
                Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)), originalBox}) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            body.add(c);
        }
        add(body, BorderLayout.CENTER);
        DialogButtons buttons = new DialogButtons(this, Texts.get("dialog.ok"), null, null, this::dispose);
        buttons.confirmButton().setToolTipText(Texts.get("connection.error.ok.tooltip"));
        buttons.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24)));
        add(buttons, BorderLayout.SOUTH);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
        it.ramasql.app.theme.Screens.fit(this);   // dentro lo schermo anche a 1024x768 (T12.6)
    }

    /** Apre o ripiega il riquadro del messaggio originale (la freccia gira di conseguenza). */
    public void setOriginalShown(boolean shown) {
        originalBox.setVisible(shown);
        toggle.setIcon(AppIcons.small(shown ? AppIcons.CHEVRON_DOWN : AppIcons.PAGE_NEXT));
        toggle.setToolTipText(Texts.get(shown ? "connect.error.original.hide" : "connect.error.original.show"));
        pack();
        it.ramasql.app.theme.Screens.fit(this);
    }

    public boolean isOriginalShown() {
        return originalBox.isVisible();
    }

    /** Testo su più righe, selezionabile, largo {@code width} pixel e alto quanto serve. */
    private static JTextArea wrapped(String text, int width, boolean mono) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(!mono);
        area.setOpaque(false);
        area.setBorder(null);
        area.setForeground(Tokens.TEXT_PRIMARY);
        area.setFont(mono ? Tokens.mono(Tokens.SMALL) : Tokens.font(Tokens.BODY, java.awt.Font.PLAIN));
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
