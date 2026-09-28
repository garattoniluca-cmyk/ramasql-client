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
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;

import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.ProductInfo;

/**
 * «Aiuto → Informazioni su RamaSQL Client» (T12.8): nome e versione, la licenza (GPL-3.0-or-later) e dove trovare i
 * sorgenti, le attribuzioni del codice derivato da SQLeo e SQLeonardo, e le librerie incluse con le loro licenze. Tutti
 * i testi nel file di risorse.
 */
public final class AboutDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Le librerie incluse nel programma: nome e licenza (chiavi {@code about.lib.<n>}). */
    static final List<String> LIBRARIES = List.of("flatlaf", "rsyntax", "mariadb", "jackson", "java");

    private final JTable libraries;

    public AboutDialog(Window owner) {
        super(owner, Texts.get("about.title"), ModalityType.APPLICATION_MODAL);
        setName("about.dialog");
        JPanel content = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_12)));
        content.setBackground(Tokens.BG_SURFACE);
        int pad = Tokens.px(Tokens.SPACE_24);
        content.setBorder(BorderFactory.createEmptyBorder(pad, pad, Tokens.px(Tokens.SPACE_8), pad));

        JPanel top = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_4)));
        top.setOpaque(false);
        top.add(Styles.text(new JLabel(Texts.get("about.name", ProductInfo.NAME, ProductInfo.version())), "heading",
                Tokens.TEXT_PRIMARY), BorderLayout.NORTH);
        top.add(Styles.text(new JLabel(Texts.get("about.subtitle")), "emphasis", Tokens.TEXT_SECONDARY),
                BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);

        JTextArea text = new JTextArea(String.join("\n\n", Texts.get("about.license"), Texts.get("about.sources"),
                Texts.get("about.sqleo"), Texts.get("about.workbench")));
        text.setName("about.text");
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFocusable(true);
        text.setToolTipText(Texts.get("about.text.tooltip"));
        text.setColumns(52);

        libraries = new JTable(new AbstractTableModel() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getRowCount() {
                return LIBRARIES.size();
            }

            @Override
            public int getColumnCount() {
                return 3;
            }

            @Override
            public String getColumnName(int column) {
                return Texts.get("about.libraries.column." + column);
            }

            @Override
            public Object getValueAt(int row, int column) {
                return Texts.get("about.lib." + LIBRARIES.get(row) + "." + column);
            }
        });
        libraries.setName("about.libraries");
        libraries.setToolTipText(Texts.get("about.libraries.tooltip"));
        libraries.setFillsViewportHeight(true);
        JScrollPane libScroll = new JScrollPane(libraries);
        libScroll.setPreferredSize(new Dimension(Tokens.px(560), Tokens.px(Tokens.ROW_HEIGHT) * (LIBRARIES.size() + 1)
                + Tokens.px(6)));
        JPanel middle = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_12)));
        middle.setOpaque(false);
        middle.add(text, BorderLayout.CENTER);
        JPanel libs = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_4)));
        libs.setOpaque(false);
        libs.add(Styles.text(new JLabel(Texts.get("about.libraries")), "emphasis", Tokens.TEXT_PRIMARY),
                BorderLayout.NORTH);
        libs.add(libScroll, BorderLayout.CENTER);
        middle.add(libs, BorderLayout.SOUTH);
        content.add(middle, BorderLayout.CENTER);

        DialogButtons buttons = new DialogButtons(this, Texts.get("about.close"), null, null, this::dispose);
        buttons.setOpaque(false);
        buttons.confirmButton().setName("about.close");
        buttons.confirmButton().setToolTipText(Texts.get("about.close.tooltip"));
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Tokens.BG_SURFACE);
        root.add(content, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Tutto il testo della finestra (per i test). */
    public String allText() {
        StringBuilder sb = new StringBuilder();
        collect(getContentPane(), sb);
        for (int r = 0; r < libraries.getRowCount(); r++) {
            for (int c = 0; c < libraries.getColumnCount(); c++) {
                sb.append(libraries.getValueAt(r, c)).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void collect(java.awt.Container c, StringBuilder sb) {
        for (java.awt.Component x : c.getComponents()) {
            if (x instanceof JLabel l) {
                sb.append(l.getText()).append('\n');
            } else if (x instanceof JTextArea t) {
                sb.append(t.getText()).append('\n');
            }
            if (x instanceof java.awt.Container k) {
                collect(k, sb);
            }
        }
    }
}
