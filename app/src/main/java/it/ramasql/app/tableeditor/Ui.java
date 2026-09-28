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
public final class Ui {

    private Ui() {
    }

    public static JPanel buttonRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_8, Tokens.SPACE_8));
        row.setOpaque(false);
        for (JButton b : buttons) {
            row.add(b);
        }
        return row;
    }

    public static JButton button(String name, String text, Runnable action) {
        // misura calcolata ogni volta: se lo stile cambia il carattere (Styles.outline), il testo non si taglia
        JButton b = new JButton(text) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                if (isPreferredSizeSet()) {
                    return super.getPreferredSize();
                }
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.max(d.width, Tokens.px(32)), Tokens.px(Tokens.CONTROL_HEIGHT));
            }
        };
        b.setName(name);
        b.addActionListener(e -> action.run());
        b.putClientProperty("JButton.buttonType", "roundRect");
        return b;
    }

    /** Pulsante primario: pieno d'accento, testo bianco (uno solo per schermata). */
    public static JButton primary(String name, String text, Runnable action) {
        JButton b = button(name, text, action);
        Styles.primary(b, Tokens.ACCENT);            // pieno d'accento con passaggio e pressione, testo bianco
        b.setPreferredSize(null);                       // misura ricalcolata con il grassetto
        Dimension d = b.getPreferredSize();
        b.setPreferredSize(new Dimension(d.width + Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.CONTROL_HEIGHT)));
        return b;
    }

    /** Titolo di sezione: {@code emphasis}, colore del testo principale. */
    public static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        Styles.text(label, "emphasis");
        label.setForeground(Tokens.TEXT_PRIMARY);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, Tokens.SPACE_4, 0));
        return label;
    }

    /** Griglia ariosa: righe 28, divisori {@code border.subtle}, intestazione {@code bg.sunken}. */
    public static void styleTable(JTable table) {
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
        comboCellKeys(table);
    }

    /**
     * Le liste nelle celle da tastiera (T12.4): <b>Alt+Giù</b> (o <b>F4</b>) sulla cella apre la sua lista, con le voci
     * e la spiegazione accanto; le frecce scelgono, Invio conferma, Esc lascia com'era. Senza, in Swing la lista della
     * cella si chiude appena si comincia a modificarla.
     */
    private static final String PENDING_OPEN = "ramasql.openCellList.pending";

    static void comboCellKeys(JTable table) {
        javax.swing.Action open = new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = table.getSelectionModel().getLeadSelectionIndex();
                int col = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
                if (row < 0 || col < 0 || row >= table.getRowCount() || col >= table.getColumnCount()
                        || !(table.getCellEditor(row, col) instanceof javax.swing.DefaultCellEditor ed)
                        || !(ed.getComponent() instanceof javax.swing.JComboBox<?> combo)) {
                    return;
                }
                if (!(table.isEditing() && table.getEditingRow() == row && table.getEditingColumn() == col)
                        && !table.editCellAt(row, col)) {
                    return;
                }
                // la lista si apre quando il fuoco è arrivato (in una lista modificabile, nel suo campo): se si
                // aprisse prima, il passaggio del fuoco la richiuderebbe
                java.awt.Component target = combo.isEditable() ? combo.getEditor().getEditorComponent() : combo;
                Runnable show = () -> javax.swing.SwingUtilities.invokeLater(() -> {
                    if (combo.isShowing()) {
                        combo.showPopup();
                    }
                });
                if (target.isFocusOwner()) {
                    show.run();
                    return;
                }
                // un solo ascoltatore in attesa per lista (Alt+Giù premuto due volte non ne lascia due), e se il
                // fuoco non arriva la richiesta non resta in sospeso fino al prossimo clic
                if (target instanceof JComponent j
                        && j.getClientProperty(PENDING_OPEN) instanceof java.awt.event.FocusListener old) {
                    target.removeFocusListener(old);
                }
                java.awt.event.FocusListener once = new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusGained(java.awt.event.FocusEvent fe) {
                        target.removeFocusListener(this);
                        show.run();
                    }
                };
                target.addFocusListener(once);
                if (target instanceof JComponent j) {
                    j.putClientProperty(PENDING_OPEN, once);
                }
                if (!target.requestFocusInWindow()) {
                    target.removeFocusListener(once);
                }
            }
        };
        table.getActionMap().put("ramasql.openCellList", open);
        for (javax.swing.KeyStroke ks : java.util.List.of(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, 0))) {
            table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, "ramasql.openCellList");
        }
    }

    /**
     * Un passo di una procedura guidata che scorre in verticale solo se non ci sta (schermo basso, carattere grande:
     * revisione T12.7); quando lo spazio c'è, occupa tutta l'altezza come prima.
     */
    public static JScrollPane stepScroll(JComponent step) {
        class Step extends javax.swing.JPanel implements javax.swing.Scrollable {
            private static final long serialVersionUID = 1L;

            Step() {
                super(new java.awt.BorderLayout());
                setOpaque(false);
                add(step, java.awt.BorderLayout.CENTER);
            }

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return getPreferredSize();
            }

            @Override
            public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction) {
                return Tokens.px(24);
            }

            @Override
            public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction) {
                return Math.max(Tokens.px(24), r.height - Tokens.px(24));
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }

            @Override
            public boolean getScrollableTracksViewportHeight() {
                return getParent() instanceof javax.swing.JViewport v && v.getHeight() >= getPreferredSize().height;
            }
        }
        JScrollPane s = new JScrollPane(new Step());
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return s;
    }

    /** Pannello di dettaglio che scorre invece di schiacciarsi quando lo spazio è poco. */
    public static JScrollPane plainScroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.getVerticalScrollBar().setUnitIncrement(Tokens.px(Tokens.ROW_HEIGHT));
        return s;
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(Tokens.BORDER_SUBTLE));
        s.getViewport().setBackground(Tokens.BG_SURFACE);
        return s;
    }

    public static void narrow(TableColumn column, int width) {
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
                StringBuilder tip = new StringBuilder();
                for (Object o : problems) {
                    tip.append(((Checks.Problem) o).message()).append('\n');   // un paragrafo per problema, a capo
                }
                setToolTipText(tip.toString().strip());
            }
            return this;
        }
    }
}
