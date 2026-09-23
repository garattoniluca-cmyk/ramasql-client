/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/** Piccoli pezzi d'interfaccia comuni alle schede, con le misure di DESIGN-SYSTEM §1.3. */
final class Ui {

    private Ui() {
    }

    static JPanel buttonRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_8, Tokens.SPACE_8));
        row.setOpaque(false);
        for (JButton b : buttons) {
            row.add(b);
        }
        return row;
    }

    static JButton button(String name, String text, Runnable action) {
        JButton b = new JButton(text);
        b.setName(name);
        b.addActionListener(e -> action.run());
        b.putClientProperty("JButton.buttonType", "roundRect");
        Dimension d = b.getPreferredSize();
        b.setPreferredSize(new Dimension(Math.max(d.width, Tokens.px(32)), Tokens.px(Tokens.CONTROL_HEIGHT)));
        return b;
    }

    /** Pulsante primario: pieno d'accento, testo bianco (uno solo per schermata). */
    static JButton primary(String name, String text, Runnable action) {
        JButton b = button(name, text, action);
        Styles.primary(b, Tokens.ACCENT);            // pieno d'accento con passaggio e pressione, testo bianco
        b.setPreferredSize(null);                       // misura ricalcolata con il grassetto
        Dimension d = b.getPreferredSize();
        b.setPreferredSize(new Dimension(d.width + Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.CONTROL_HEIGHT)));
        return b;
    }

    /** Titolo di sezione: {@code emphasis}, colore del testo principale. */
    static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        Styles.text(label, "emphasis");
        label.setForeground(Tokens.TEXT_PRIMARY);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, Tokens.SPACE_4, 0));
        return label;
    }

    /** Griglia ariosa: righe 28, divisori {@code border.subtle}, intestazione {@code bg.sunken}. */
    static void styleTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Tokens.px(Tokens.ROW_HEIGHT));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(Tokens.BORDER_SUBTLE);
        table.setBackground(Tokens.BG_SURFACE);
        table.setForeground(Tokens.TEXT_PRIMARY);
        table.setSelectionBackground(Tokens.ACCENT_TINT);
        table.setSelectionForeground(Tokens.TEXT_PRIMARY);
        table.setFillsViewportHeight(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(Tokens.BG_SUNKEN);
        table.getTableHeader().setForeground(Tokens.TEXT_SECONDARY);
        // intestazioni allineate come i dati (a sinistra), non centrate
        if (table.getTableHeader().getDefaultRenderer() instanceof DefaultTableCellRenderer header) {
            header.setHorizontalAlignment(SwingConstants.LEADING);
        }
        table.getTableHeader().setPreferredSize(new Dimension(0, Tokens.px(Tokens.CONTROL_HEIGHT)));
    }

    /** Pannello di dettaglio che scorre invece di schiacciarsi quando lo spazio è poco. */
    static JScrollPane plainScroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.getVerticalScrollBar().setUnitIncrement(Tokens.px(Tokens.ROW_HEIGHT));
        return s;
    }

    static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(Tokens.BORDER_SUBTLE));
        s.getViewport().setBackground(Tokens.BG_SURFACE);
        return s;
    }

    static void narrow(TableColumn column, int width) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
        column.setPreferredWidth(width);
    }

    /**
     * Colonna del segno accanto alla riga: {@code ✘} ({@code danger}) se c'è un errore, {@code ⚠} ({@code warning})
     * se c'è un avviso, con la spiegazione nel suggerimento. Il valore della cella è la lista dei problemi della riga.
     */
    static final class MarkRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        MarkRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            setBackground(null);        // il renderer ricorda i colori: si azzerano a ogni cella
            setForeground(null);
            super.getTableCellRendererComponent(table, "", isSelected, false, row, column);
            setToolTipText(null);
            if (value instanceof List<?> problems && !problems.isEmpty()) {
                boolean error = problems.stream().anyMatch(p -> p instanceof Checks.Problem x && x.isError());
                Banner.Tone tone = error ? Banner.Tone.DANGER : Banner.Tone.WARNING;
                setText(tone.glyph);
                setForeground(tone.color);
                if (!isSelected) {
                    setBackground(tone.tint);
                }
                StringBuilder tip = new StringBuilder("<html>");
                for (Object o : problems) {
                    tip.append(escape(((Checks.Problem) o).message())).append("<br>");
                }
                setToolTipText(tip.append("</html>").toString());
            }
            return this;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
