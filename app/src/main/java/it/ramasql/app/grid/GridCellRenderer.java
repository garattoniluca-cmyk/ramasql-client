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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.Path2D;
import java.util.Map;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.data.PendingChanges.RowKind;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;

/**
 * Aspetto di una cella ({@code DESIGN-SYSTEM.md} §3.4). Gli stati sono gli stessi in tutta l'app e <b>mai affidati
 * al solo colore</b>: riga nuova su {@code success.tint} (e «＋» nel gutter), cella modificata su {@code warning.tint}
 * con il triangolino {@code warning} nell'angolo, riga da eliminare su {@code danger.tint} con il testo barrato, cella
 * non valida con bordo 1,5 px {@code danger} e icona d'errore (il messaggio è nel suggerimento), riga rifiutata dal
 * server su {@code danger.tint} con «!» nel gutter. NULL è una pillola grigia, distinta dalla stringa vuota. Righe
 * pari zebrate appena; numeri a destra (cifre tabulari di Segoe UI), date al centro, testo a sinistra. La selezione
 * a blocco ha il fondo {@code accent.tint} e il bordo esterno d'accento.
 */
final class GridCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    static final Color INSERTED = Tokens.SUCCESS_TINT;
    static final Color MODIFIED_ROW = Tokens.mix(Tokens.BG_SURFACE, Tokens.WARNING_TINT, 0.4);
    static final Color MODIFIED_CELL = Tokens.WARNING_TINT;
    static final Color DELETED = Tokens.DANGER_TINT;
    static final Color ERROR_ROW = Tokens.DANGER_TINT;
    static final Color INVALID = Tokens.DANGER_TINT;
    static final Color INVALID_BORDER = Tokens.DANGER;
    /** Testo di NULL (sulla pillola {@code bg.sunken}): {@code text.secondary}, leggibile anche al proiettore. */
    static final Color NULL_TEXT = Tokens.TEXT_SECONDARY;
    private static final int TOOLTIP_MAX = 600;

    private boolean nullPill;
    private boolean modifiedMark;
    private boolean invalidMark;
    private boolean deletedRow;
    /** Lati del blocco selezionato da disegnare: alto, sinistra, basso, destra. */
    private final boolean[] blockEdges = new boolean[4];

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        DataGridModel model = (DataGridModel) table.getModel();
        String text = (String) value;
        boolean isNull = text == null && model.isSet(row, column);
        String shown = isNull ? Texts.get("grid.null") : text == null ? "" : oneLine(text);
        super.getTableCellRendererComponent(table, shown, isSelected, hasFocus, row, column);
        Font base = table.getFont();
        setFont(isNull ? Tokens.font(Tokens.CAPTION, Font.ITALIC) : base);
        int modelColumn = table.convertColumnIndexToModel(column);
        // T12.9: anche la cella dice che cosa contiene (la colonna e il suo tipo); errori e testi lunghi lo sostituiscono
        ColumnDef def = model.column(modelColumn);
        setToolTipText(def == null ? null : Texts.get(model.isInsertRow(row) ? "dataGrid.cell.insert.tooltip"
                : "dataGrid.cell.tooltip", def.name(), GridHeaderRenderer.typeText(def)));
        setHorizontalAlignment(alignment(model.column(modelColumn)));
        nullPill = isNull;
        modifiedMark = false;
        invalidMark = false;
        deletedRow = false;

        Color background = row % 2 == 1 ? Tokens.BG_ZEBRA : Tokens.BG_SURFACE;
        Color foreground = isNull ? NULL_TEXT : Tokens.TEXT_PRIMARY;
        Optional<String> invalid = Optional.empty();
        if (!model.isInsertRow(row)) {
            PendingChanges pending = model.pending();
            RowKind kind = pending.kind(row);
            background = switch (kind) {
                case INSERTED -> INSERTED;
                case MODIFIED -> pending.isCellModified(row, modelColumn) ? MODIFIED_CELL : MODIFIED_ROW;
                case DELETED -> DELETED;
                case UNCHANGED -> background;
            };
            modifiedMark = kind == RowKind.MODIFIED && pending.isCellModified(row, modelColumn);
            if (kind == RowKind.DELETED) {
                deletedRow = true;
                foreground = Tokens.strong(Tokens.DANGER);
                setFont(getFont().deriveFont(Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON)));
            }
            Optional<String> rowError = pending.errorMessage(row);
            if (rowError.isPresent()) {
                background = ERROR_ROW;
                setToolTipText(Texts.get("grid.row.error", rowError.get()));
            }
            invalid = pending.validationError(row, modelColumn);
            if (invalid.isPresent()) {
                background = INVALID;
                invalidMark = true;
                setToolTipText(invalid.get());
            } else if (rowError.isEmpty() && text != null && (text.length() > 60 || hasControl(text))) {
                setToolTipText(text.length() > TOOLTIP_MAX ? text.substring(0, TOOLTIP_MAX) + "…" : text);
            }
        } else {
            foreground = Tokens.TEXT_TERTIARY;
        }
        if (isSelected) {
            // selezione a blocco: la tinta d'accento, ma gli stati restano leggibili (colori di testo e segni)
            setBackground(Tokens.mix(background, Tokens.ACCENT_TINT, background == Tokens.BG_SURFACE
                    || background == Tokens.BG_ZEBRA ? 1.0 : 0.5));
            markBlockEdges(table, row, column);
        } else {
            setBackground(background);
            java.util.Arrays.fill(blockEdges, false);
        }
        setForeground(foreground);
        Border cell = BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_8), 0,
                Tokens.px(invalidMark ? Tokens.SPACE_8 + 18 : Tokens.SPACE_8));
        setBorder(hasFocus && getBorder() != null ? BorderFactory.createCompoundBorder(getBorder(), cell) : cell);
        return this;
    }

    /** Quali lati di questa cella sono il bordo esterno del blocco selezionato (selezione a intervallo unico). */
    private void markBlockEdges(JTable table, int row, int column) {
        int[] rows = table.getSelectedRows();
        int[] columns = table.getSelectedColumns();
        if (rows.length * columns.length <= 1) {
            java.util.Arrays.fill(blockEdges, false);
            return;
        }
        blockEdges[0] = row == rows[0];
        blockEdges[1] = column == columns[0];
        blockEdges[2] = row == rows[rows.length - 1];
        blockEdges[3] = column == columns[columns.length - 1];
    }

    /** Numeri a destra, date e ore al centro, il resto a sinistra. */
    private static int alignment(ColumnDef def) {
        if (def == null) {
            return SwingConstants.LEADING;
        }
        if (SqlTypes.isNumeric(def.dataType()) && !SqlTypes.isBoolean(def)) {
            return SwingConstants.RIGHT;
        }
        return isTemporal(def.dataType()) ? SwingConstants.CENTER : SwingConstants.LEADING;
    }

    static boolean isTemporal(String dataType) {
        String t = SqlTypes.canonical(dataType);
        return t.equals("DATE") || t.equals("DATETIME") || t.equals("TIMESTAMP") || t.equals("TIME")
                || t.equals("YEAR");
    }

    // renderer: lo sfondo lo disegniamo noi (sotto la pillola di NULL), il resto lo fa JLabel
    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (nullPill) {
                paintNullPill(g2);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
        Graphics2D o = (Graphics2D) g.create();
        try {
            o.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (modifiedMark) {
                // triangolino nell'angolo in alto a destra
                int s = Tokens.px(7);
                Path2D tri = new Path2D.Float();
                tri.moveTo(w - s, 0);
                tri.lineTo(w, 0);
                tri.lineTo(w, s);
                tri.closePath();
                o.setColor(Tokens.strong(Tokens.WARNING));
                o.fill(tri);
            }
            if (invalidMark) {
                float stroke = Tokens.px(3) / 2f;
                o.setStroke(new BasicStroke(stroke));
                o.setColor(INVALID_BORDER);
                o.drawRect(1, 1, w - 3, h - 3);
                AppIcons.small(AppIcons.STATUS_ERROR).paintIcon(this, o, w - Tokens.px(Tokens.SPACE_4 + 16),
                        (h - Tokens.px(16)) / 2);
            }
            if (blockEdges[0] || blockEdges[1] || blockEdges[2] || blockEdges[3]) {
                int t = Math.max(1, Math.round(Tokens.px(3) / 2f));
                o.setColor(Tokens.ACCENT);
                if (blockEdges[0]) {
                    o.fillRect(0, 0, w, t);
                }
                if (blockEdges[1]) {
                    o.fillRect(0, 0, t, h);
                }
                if (blockEdges[2]) {
                    o.fillRect(0, h - t, w, t);
                }
                if (blockEdges[3]) {
                    o.fillRect(w - t, 0, t, h);
                }
            }
        } finally {
            o.dispose();
        }
    }

    /** La pillola dietro «NULL»: {@code bg.sunken}, raggio pieno, alta quanto il testo più un po' d'aria. */
    private void paintNullPill(Graphics2D g2) {
        FontMetrics fm = getFontMetrics(getFont());
        int tw = fm.stringWidth(getText());
        Insets in = getInsets();
        int padX = Tokens.px(6);
        int ph = fm.getHeight() + Tokens.px(2);
        int pw = tw + 2 * padX;
        int x = switch (getHorizontalAlignment()) {
            case SwingConstants.RIGHT, SwingConstants.TRAILING -> getWidth() - in.right - tw - padX;
            case SwingConstants.CENTER -> (getWidth() - tw) / 2 - padX;
            default -> in.left - padX;
        };
        int y = (getHeight() - ph) / 2;
        g2.setColor(deletedRow ? Tokens.BG_SURFACE : Tokens.BG_SUNKEN);
        g2.fillRoundRect(x, y, pw, ph, ph, ph);
    }

    /**
     * Il testo su una riga: gli a-capo diventano «↵» e le tabulazioni «→» (il testo intero è nel suggerimento e
     * nell'editor a finestra).
     */
    private static String oneLine(String text) {
        if (!hasControl(text)) {
            return text;
        }
        return text.replace("\r\n", "↵").replace('\n', '↵').replace('\r', '↵').replace('\t', '→');
    }

    private static boolean hasControl(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\t') >= 0;
    }
}
