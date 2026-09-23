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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;

/**
 * Intestazione della griglia su <b>due righe</b> ({@code DESIGN-SYSTEM.md} §3.4): il nome della colonna in
 * {@code emphasis} (con la freccia dell'ordinamento, se c'è) e sotto il glifo del tipo — chiave oro per la chiave
 * primaria, «#» per i numeri, «Aa» per i testi, calendario per date e ore — con il tipo in {@code caption}
 * {@code text.secondary} (es. {@code VARCHAR(50) · NN}). Fondo {@code bg.sunken}, separatori {@code border.subtle}.
 */
final class GridHeaderRenderer extends JComponent implements TableCellRenderer {

    private static final long serialVersionUID = 1L;

    private final transient List<ColumnDef> columns;
    private final transient Set<String> primaryKey;
    private String name = "";
    private String type = "";
    private transient Icon glyphIcon;
    private String glyphText;
    private boolean selected;

    GridHeaderRenderer(List<ColumnDef> columns, Set<String> primaryKey) {
        this.columns = columns;
        this.primaryKey = primaryKey;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        name = value == null ? "" : value.toString();
        int modelColumn = table == null ? column : table.convertColumnIndexToModel(column);
        ColumnDef def = modelColumn >= 0 && modelColumn < columns.size() ? columns.get(modelColumn) : null;
        type = def == null ? "" : typeText(def);
        glyphIcon = null;
        glyphText = null;
        if (def != null) {
            if (primaryKey.contains(def.name())) {
                glyphIcon = AppIcons.get(AppIcons.TREE_PRIMARY_KEY, 12);
            } else if (GridCellRenderer.isTemporal(def.dataType())) {
                glyphIcon = AppIcons.get(AppIcons.TYPE_DATE, 12);
            } else if (SqlTypes.isNumeric(def.dataType())) {
                glyphText = "#";
            } else if (SqlTypes.isText(def.dataType())) {
                glyphText = "Aa";
            }
        }
        // tutta la colonna selezionata (clic sull'intestazione): l'intestazione lo mostra con la tinta d'accento
        selected = table != null && table.getSelectedRowCount() == table.getRowCount() && table.getRowCount() > 0
                && table.isColumnSelected(column);
        setToolTipText(def == null ? null : name + " — " + type);
        return this;
    }

    /** {@code VARCHAR(50) · NN · AI}: il tipo come lo scrive il server, poi i vincoli in sigla. */
    static String typeText(ColumnDef def) {
        StringBuilder sb = new StringBuilder(def.fullType());
        if (def.unsigned()) {
            sb.append(" UNSIGNED");
        }
        if (!def.nullable()) {
            sb.append(" · NN");
        }
        if (def.autoIncrement()) {
            sb.append(" · AI");
        }
        return sb.toString();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Tokens.px(80), Tokens.px(Tokens.GRID_HEADER_HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(selected ? Tokens.ACCENT_TINT : Tokens.BG_SUNKEN);
            g2.fillRect(0, 0, w, h);
            g2.setColor(Tokens.BORDER_SUBTLE);
            g2.fillRect(w - 1, 0, 1, h);
            g2.setColor(Tokens.BORDER_DEFAULT);
            g2.fillRect(0, h - 1, w, 1);
            int pad = Tokens.px(Tokens.SPACE_8);
            int clip = w - 2 * pad;

            Font nameFont = Tokens.semibold(Tokens.EMPHASIS);
            Font typeFont = Tokens.font(Tokens.CAPTION, Font.PLAIN);
            FontMetrics nm = g2.getFontMetrics(nameFont);
            FontMetrics tm = g2.getFontMetrics(typeFont);
            int gap = Tokens.px(2);
            int block = nm.getHeight() + gap + tm.getHeight();
            int top = (h - block) / 2;

            g2.setFont(nameFont);
            g2.setColor(Tokens.TEXT_PRIMARY);
            g2.drawString(fit(name, nm, clip), pad, top + nm.getAscent());

            int y2 = top + nm.getHeight() + gap;
            int x = pad;
            if (glyphIcon != null) {
                glyphIcon.paintIcon(this, g2, x, y2 + (tm.getHeight() - glyphIcon.getIconHeight()) / 2);
                x += glyphIcon.getIconWidth() + Tokens.px(4);
            } else if (glyphText != null) {
                g2.setFont(Tokens.semibold(Tokens.CAPTION));
                g2.setColor(Tokens.TEXT_SECONDARY);
                g2.drawString(glyphText, x, y2 + tm.getAscent());
                x += g2.getFontMetrics().stringWidth(glyphText) + Tokens.px(4);
            }
            g2.setFont(typeFont);
            g2.setColor(Tokens.TEXT_SECONDARY);
            g2.drawString(fit(type, tm, w - pad - x), x, y2 + tm.getAscent());
        } finally {
            g2.dispose();
        }
    }

    /** Il testo accorciato con «…» se non sta nella larghezza. */
    private static String fit(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String dots = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width) {
            end--;
        }
        return end == 0 ? "" : text.substring(0, end) + dots;
    }
}
