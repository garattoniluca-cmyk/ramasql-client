/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntFunction;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.data.PendingChanges.RowKind;

/**
 * Intestazione di riga: numero della riga (contando le pagine precedenti), «+» per le righe nuove, «*» per la riga
 * d'inserimento, «!» per le righe in errore (con il messaggio del server come suggerimento). Un clic seleziona la
 * riga intera; trascinare o Maiusc+clic seleziona più righe.
 */
final class RowHeader extends JList<String> {

    private static final long serialVersionUID = 1L;

    private final JTable table;
    private final IntFunction<Integer> absoluteNumber;

    RowHeader(JTable table, IntFunction<Integer> absoluteNumber) {
        this.table = table;
        this.absoluteNumber = absoluteNumber;
        HeaderListModel listModel = new HeaderListModel();
        setModel(listModel);
        table.getModel().addTableModelListener(e -> listModel.changed(e));
        setFixedCellHeight(table.getRowHeight());
        setFixedCellWidth(Tokens.px(Tokens.GUTTER_WIDTH));
        setFocusable(false);
        setBackground(Tokens.BG_WINDOW);
        setCellRenderer(new Renderer());
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter mouse = new MouseAdapter() {
            private int anchor = -1;

            @Override
            public void mousePressed(MouseEvent e) {
                int row = locationToIndex(e.getPoint());
                if (row < 0 || table.getColumnCount() == 0) {
                    return;
                }
                if (table.isEditing()) {
                    table.getCellEditor().stopCellEditing();
                }
                anchor = e.isShiftDown() && anchor >= 0 ? anchor : row;
                selectRows(anchor, row);
                table.requestFocusInWindow();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int row = locationToIndex(e.getPoint());
                if (row >= 0 && anchor >= 0) {
                    selectRows(anchor, row);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Seleziona le righe intere da {@code from} a {@code to}, lasciando la cella attiva sulla prima colonna. */
    void selectRows(int from, int to) {
        table.setRowSelectionInterval(from, to);
        table.setColumnSelectionInterval(0, table.getColumnCount() - 1);
        table.scrollRectToVisible(table.getCellRect(to, 0, true));
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int row = locationToIndex(event.getPoint());
        DataGridModel model = (DataGridModel) table.getModel();
        if (row < 0 || model.isInsertRow(row)) {
            return row < 0 ? null : Texts.get("grid.row.insert.tooltip");
        }
        return model.pending().errorMessage(row).map(m -> Texts.get("grid.row.error.tooltip", m)).orElse(null);
    }

    private final class HeaderListModel extends AbstractListModel<String> {
        private static final long serialVersionUID = 1L;
        private int size;

        @Override
        public int getSize() {
            size = table.getRowCount();
            return size;
        }

        @Override
        public String getElementAt(int index) {
            DataGridModel model = (DataGridModel) table.getModel();
            if (model.isInsertRow(index)) {
                return "*";
            }
            PendingChanges pending = model.pending();
            String mark = pending.errorMessage(index).isPresent() ? "! " : "";
            return pending.kind(index) == RowKind.INSERTED ? mark + "+" : mark + absoluteNumber.apply(index);
        }

        void changed(TableModelEvent e) {
            int old = size;
            int now = table.getRowCount();
            if (now > old) {
                fireIntervalAdded(this, old, now - 1);
            } else if (now < old) {
                fireIntervalRemoved(this, now, old - 1);
            }
            size = now;
            fireContentsChanged(this, 0, Math.max(0, now - 1));
        }
    }

    /**
     * Gutter dei numeri di riga (§3.4): numero in {@code caption} allineato a destra; gli stati hanno una barra
     * sinistra di 3 px e un glifo — «+» riga nuova ({@code success}), «−» da eliminare ({@code danger}), «!» rifiutata
     * dal server ({@code danger}), numero con barra {@code warning} se modificata — così non dipendono dal colore.
     */
    private final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private Color bar;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, false, false);
            setHorizontalAlignment(SwingConstants.RIGHT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 1, Tokens.BORDER_SUBTLE),
                    BorderFactory.createEmptyBorder(0, Tokens.px(4), 0, Tokens.px(Tokens.SPACE_8))));
            DataGridModel model = (DataGridModel) table.getModel();
            Color background = Tokens.BG_WINDOW;
            Color foreground = Tokens.TEXT_TERTIARY;
            Font font = Tokens.font(Tokens.CAPTION, Font.PLAIN);
            bar = null;
            String text = String.valueOf(value);
            if (!model.isInsertRow(index) && index < model.dataRowCount()) {
                PendingChanges pending = model.pending();
                switch (pending.kind(index)) {
                    case INSERTED -> {
                        background = GridCellRenderer.INSERTED;
                        bar = Tokens.strong(Tokens.SUCCESS);
                        foreground = Tokens.strong(Tokens.SUCCESS);
                        text = "+";
                        font = Tokens.semibold(Tokens.BODY);
                    }
                    case MODIFIED -> {
                        background = GridCellRenderer.MODIFIED_ROW;
                        bar = Tokens.strong(Tokens.WARNING);
                        foreground = Tokens.TEXT_SECONDARY;
                    }
                    case DELETED -> {
                        background = GridCellRenderer.DELETED;
                        bar = Tokens.strong(Tokens.DANGER);
                        foreground = Tokens.strong(Tokens.DANGER);
                        text = "−";
                        font = Tokens.semibold(Tokens.BODY);
                    }
                    case UNCHANGED -> {
                        // numero semplice
                    }
                }
                if (pending.errorMessage(index).isPresent()) {
                    background = GridCellRenderer.ERROR_ROW;
                    bar = Tokens.strong(Tokens.DANGER);
                    foreground = Tokens.strong(Tokens.DANGER);
                    text = "! " + text.replace("! ", "");
                    font = Tokens.semibold(Tokens.CAPTION);
                }
            }
            if (table.isRowSelected(index)) {
                background = Tokens.mix(background, Tokens.ACCENT_TINT, 0.7);
                if (bar == null) {
                    foreground = Tokens.ACCENT_PRESSED;
                    font = Tokens.semibold(Tokens.CAPTION);
                }
            }
            setText(text);
            setFont(font);
            setBackground(background);
            setForeground(foreground);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (bar != null) {
                g.setColor(bar);
                g.fillRect(0, 0, Tokens.px(3), getHeight());
            }
        }
    }
}
