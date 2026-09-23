/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.pipeline;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;

/**
 * «Mostra SQL di creazione»: il testo di {@code SHOW CREATE …} come lo restituisce il server, in sola lettura, con
 * il pulsante <em>Copia</em>. Sotto il titolo si legge l'istruzione usata per leggerlo («come l'ho letto»). Non è
 * modale: si può tenere aperta mentre si lavora.
 */
public final class ShowCreateDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final RSyntaxTextArea sql;
    private final JButton copy = new JButton(Texts.get("showCreate.copy"));
    private final JButton close = new JButton(Texts.get("showCreate.close"));

    /**
     * @param statement l'istruzione con cui il testo è stato letto ({@code SHOW CREATE TABLE `c`.`t`})
     * @param text      il testo restituito dal server
     * @param copier    chi mette il testo negli appunti
     */
    public ShowCreateDialog(Window owner, String title, String statement, String text, Consumer<String> copier) {
        super(owner, title, ModalityType.MODELESS);
        setName("showCreate.dialog");
        JLabel heading = new JLabel(title);
        heading.setFont(Tokens.font(Tokens.HEADING, java.awt.Font.BOLD));
        heading.setForeground(Tokens.TEXT_PRIMARY);
        JLabel how = new JLabel(Texts.get("showCreate.readWith", statement));
        how.setName("showCreate.statement");
        how.setForeground(Tokens.TEXT_SECONDARY);
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        int pad = Tokens.px(Tokens.SPACE_24);
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(pad, pad, Tokens.px(Tokens.SPACE_16), pad));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        how.setAlignmentX(LEFT_ALIGNMENT);
        head.add(heading);
        head.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)));
        head.add(how);

        sql = SqlText.readOnly(text, 16, 80);
        sql.setName("showCreate.sql");
        JScrollPane scroll = new JScrollPane(sql);
        scroll.setPreferredSize(new Dimension(Tokens.px(720), Tokens.px(320)));
        scroll.setBorder(BorderFactory.createLineBorder(Tokens.BORDER_SUBTLE));
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, pad, 0, pad));
        center.add(scroll);

        copy.setName("showCreate.copy");
        close.setName("showCreate.close");
        copy.addActionListener(e -> copier.accept(sql.getText()));
        close.addActionListener(e -> dispose());
        Styles.primary(close, Tokens.ACCENT);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.px(Tokens.SPACE_8), 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
        buttons.add(copy);
        buttons.add(close);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Tokens.BG_SURFACE);
        content.add(head, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    public String sqlText() {
        return sql.getText();
    }

    public JButton copyButton() {
        return copy;
    }

    public JButton closeButton() {
        return close;
    }
}
