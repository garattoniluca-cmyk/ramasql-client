/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import it.ramasql.app.theme.Tokens;

/**
 * «Aiuto → Guida rapida» ({@code DESIGN.md} §3.12): una pagina breve in italiano, dentro il programma (niente
 * browser, niente rete), con i passi dell'esercitazione tipo e le scorciatoie. Il testo è nel file di risorse
 * {@code guida-rapida.html}; la dimensione del carattere segue le impostazioni.
 */
public final class GuideDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private final JEditorPane page = new JEditorPane();

    public GuideDialog(Window owner) {
        super(owner, Texts.get("guide.title"), ModalityType.MODELESS);
        setName("guide.dialog");
        page.setName("guide.page");
        page.setContentType("text/html");
        page.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        page.setFont(javax.swing.UIManager.getFont("Label.font"));
        page.setEditable(false);
        page.setText(html());
        page.setCaretPosition(0);
        page.setToolTipText(Texts.get("guide.page.tooltip"));
        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(Tokens.px(640), Tokens.px(560)));
        DialogButtons buttons = new DialogButtons(this, Texts.get("guide.close"), null, null, this::dispose);
        buttons.confirmButton().setName("guide.close");
        buttons.confirmButton().setToolTipText(Texts.get("guide.close.tooltip"));
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Tokens.BG_SURFACE);
        root.add(scroll, BorderLayout.CENTER);
        buttons.setOpaque(false);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Il testo della guida, dal file di risorse. */
    static String html() {
        try (InputStream in = GuideDialog.class.getResourceAsStream("/it/ramasql/app/guida-rapida.html")) {
            if (in == null) {
                return Texts.get("guide.missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Texts.get("guide.missing");
        }
    }

    /** Il testo mostrato, senza marcatori (per i test). */
    public String plainText() {
        try {
            return page.getDocument().getText(0, page.getDocument().getLength());
        } catch (javax.swing.text.BadLocationException e) {
            return "";
        }
    }
}
