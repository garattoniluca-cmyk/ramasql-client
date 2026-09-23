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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.data.PendingChanges.RowKind;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;

/**
 * <b>Scheda record</b> (il «Form Editor» di Workbench): un record per volta, campi incolonnati con etichetta = nome
 * della colonna + tipo; Precedente / Successivo / Nuovo / Elimina. Lavora sullo <b>stesso</b> {@link DataGridModel}
 * della griglia: stesse modifiche in sospeso, stessi Conferma/Scarta (nella barra della griglia).
 *
 * <ul>
 *   <li>Ogni carattere scritto è subito una modifica in sospeso della cella (come digitarlo nella griglia); nulla va sul
 *       server. Su un campo NULL il testo vuoto lascia NULL; «Imposta NULL» è nel menu del campo.</li>
 *   <li>Il «record nuovo» è la riga d'inserimento della griglia: il primo carattere scritto crea la riga.</li>
 *   <li>Colonne di testo lungo ({@code TEXT}…) in un'area su più righe; un valore con a-capo in una colonna corta non si
 *       modifica qui (si userebbe «Modifica in una finestra…» della griglia) per non perdere gli a-capo.</li>
 * </ul>
 */
public final class RecordForm extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DataGrid grid;
    private final DataGridModel model;
    private final List<JTextComponent> fields = new ArrayList<>();
    private final JLabel position = new JLabel();
    private final JLabel rowState = new JLabel(" ");
    private final JButton previous = new JButton(Texts.get("form.previous"));
    private final JButton next = new JButton(Texts.get("form.next"));
    private final JButton newRecord = new JButton(Texts.get("form.new"));
    private final JButton delete = new JButton(Texts.get("form.delete"));
    private int current;
    private boolean updating;

    RecordForm(DataGrid grid) {
        super(new BorderLayout());
        this.grid = grid;
        this.model = grid.model();
        setName("recordForm");

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_8)));
        nav.setBackground(Tokens.BG_WINDOW);
        previous.setIcon(AppIcons.small(AppIcons.PAGE_PREVIOUS));
        next.setIcon(AppIcons.small(AppIcons.PAGE_NEXT));
        next.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        newRecord.setIcon(AppIcons.small(AppIcons.PLUS));
        delete.setIcon(AppIcons.tinted(AppIcons.small(AppIcons.STATUS_ERROR), Tokens.DANGER));
        position.setForeground(Tokens.TEXT_SECONDARY);
        rowState.setIconTextGap(Tokens.px(6));
        previous.setName("recordForm.previous");
        next.setName("recordForm.next");
        newRecord.setName("recordForm.new");
        delete.setName("recordForm.delete");
        previous.addActionListener(e -> previous());
        next.addActionListener(e -> next());
        newRecord.addActionListener(e -> newRecord());
        delete.addActionListener(e -> deleteRecord());
        nav.add(previous);
        nav.add(position);
        nav.add(next);
        if (model.isEditable()) {
            nav.add(newRecord);
            nav.add(delete);
        }
        rowState.setName("recordForm.state");
        rowState.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_8),
                Tokens.px(Tokens.SPACE_12)));
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Tokens.BG_WINDOW);
        top.add(nav, BorderLayout.NORTH);
        top.add(rowState, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_16),
                Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_16)));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_4),
                Tokens.px(Tokens.SPACE_8));
        for (int c = 0; c < model.getColumnCount(); c++) {
            ColumnDef def = model.column(c);
            gc.gridy = c;
            gc.gridx = 0;
            gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.NORTHEAST;
            body.add(label(def), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.anchor = GridBagConstraints.NORTHWEST;
            JTextComponent field = field(def, c);
            fields.add(field);
            body.add(field instanceof JTextArea ? new JScrollPane(field) : field, gc);
        }
        gc.gridy = model.getColumnCount();
        gc.weighty = 1;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        body.add(filler, gc);
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.BORDER_SUBTLE));
        scroll.getViewport().setBackground(Tokens.BG_SURFACE);
        body.setBackground(Tokens.BG_SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        model.addTableModelListener(e -> {
            if (!updating) {
                refresh();
            }
        });
        showRow(0);
    }

    private static JLabel label(ColumnDef def) {
        String type = def.fullType() + (def.unsigned() ? " UNSIGNED" : "");
        JLabel label = new JLabel(Texts.get("form.label", escape(def.name()), Tokens.hex(Tokens.TEXT_SECONDARY),
                escape(type)));
        label.setName("recordForm.label." + def.name());
        return label;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JTextComponent field(ColumnDef def, int column) {
        String type = SqlTypes.canonical(def.dataType());
        boolean longText = type.endsWith("TEXT");
        JTextComponent field;
        if (longText) {
            JTextArea area = new JTextArea(3, 30);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            field = area;
        } else {
            field = new JTextField(30);
        }
        field.setName("recordForm.field." + def.name());
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                apply(column);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                apply(column);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        JPopupMenu menu = new JPopupMenu();
        JMenuItem setNull = new JMenuItem(Texts.get("grid.menu.setNull"));
        setNull.addActionListener(e -> setNull(column));
        setNull.setEnabled(def.nullable());
        menu.add(setNull);
        if (model.isEditable()) {
            field.setComponentPopupMenu(menu);
        }
        return field;
    }

    // ---------------------------------------------------------------- navigazione

    /** Riga mostrata (indice della griglia; la riga d'inserimento = record nuovo). */
    public int currentRow() {
        return current;
    }

    /** Mostra la riga indicata (limitata alle righe esistenti). */
    public void showRow(int row) {
        current = Math.max(0, Math.min(row, model.getRowCount() - 1));
        refresh();
    }

    public boolean previous() {
        if (current <= 0) {
            return false;
        }
        showRow(current - 1);
        return true;
    }

    public boolean next() {
        if (current >= model.getRowCount() - 1) {
            return false;
        }
        showRow(current + 1);
        return true;
    }

    /** Va al record nuovo (la riga d'inserimento): il primo carattere scritto crea la riga in sospeso. */
    public boolean newRecord() {
        if (!model.isEditable()) {
            return false;
        }
        showRow(model.dataRowCount());
        if (!fields.isEmpty()) {
            firstEditableField().requestFocusInWindow();
        }
        return true;
    }

    /** Marca da eliminare il record mostrato (un record nuovo sparisce). Nulla va sul server. */
    public boolean deleteRecord() {
        if (!model.isEditable() || current >= model.dataRowCount()) {
            return false;
        }
        model.pending().deleteRow(current);
        model.fireTableDataChanged();
        showRow(current);
        return true;
    }

    /** Il campo della colonna (per i test e per l'accessibilità). */
    public JTextComponent field(int column) {
        return fields.get(column);
    }

    /** «Record 3 di 25» / «Record nuovo». */
    public String positionText() {
        return position.getText();
    }

    public String rowStateText() {
        return rowState.getText();
    }

    void stopEditing() {
        // i campi scrivono a ogni carattere: non c'è nulla da chiudere
    }

    private JTextComponent firstEditableField() {
        for (int c = 0; c < fields.size(); c++) {
            if (fields.get(c).isEditable()) {
                return fields.get(c);
            }
        }
        return fields.get(0);
    }

    // ---------------------------------------------------------------- dal modello ai campi

    /** Ridisegna il record corrente dal modello (dopo incolla, Scarta, esito della Conferma…). */
    void refresh() {
        if (current > model.getRowCount() - 1) {
            current = Math.max(0, model.getRowCount() - 1);
        }
        updating = true;
        try {
            boolean insertRow = model.isInsertRow(current) || model.getRowCount() == 0;
            PendingChanges pending = model.pending();
            boolean deleted = !insertRow && pending.kind(current) == RowKind.DELETED;
            for (int c = 0; c < fields.size(); c++) {
                JTextComponent field = fields.get(c);
                ColumnDef def = model.column(c);
                String value = insertRow ? null : (String) model.getValueAt(current, c);
                boolean set = !insertRow && model.isSet(current, c);
                if (!field.getText().equals(value == null ? "" : value)) {
                    field.setText(value == null ? "" : value);
                    field.setCaretPosition(0);
                }
                boolean hasLineBreak = field instanceof JTextField && value != null
                        && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0);
                field.setEditable(model.isEditable() && !deleted && !model.isReadOnlyColumn(c) && !hasLineBreak);
                field.putClientProperty("JTextField.placeholderText", placeholder(def, value, set));
                field.setFont(field.getFont().deriveFont(value == null && set ? Font.ITALIC : Font.PLAIN));
                Optional<String> invalid = insertRow ? Optional.empty() : pending.validationError(current, c);
                field.putClientProperty("JComponent.outline", invalid.isPresent() ? "error" : null);
                field.setToolTipText(invalid.orElse(hasLineBreak ? Texts.get("form.lineBreak") : null));
                field.setBackground(background(insertRow, c));
                field.repaint();
            }
            if (insertRow) {
                position.setText(Texts.get("form.position.new"));
            } else {
                position.setText(Texts.get("form.position", current + 1, model.dataRowCount()));
            }
            rowState.setForeground(Tokens.TEXT_SECONDARY);
            rowState.setIcon(null);
            String state = " ";
            if (!insertRow) {
                Optional<String> error = pending.errorMessage(current);
                if (error.isPresent()) {
                    state = Texts.get("grid.row.error.tooltip", error.get());
                    rowState.setForeground(Tokens.DANGER);
                    rowState.setIcon(AppIcons.small(AppIcons.STATUS_ERROR));
                } else {
                    state = switch (pending.kind(current)) {
                        case INSERTED -> Texts.get("form.state.inserted");
                        case MODIFIED -> Texts.get("form.state.modified");
                        case DELETED -> Texts.get("form.state.deleted");
                        case UNCHANGED -> " ";
                    };
                }
            }
            rowState.setText(state);
            previous.setEnabled(current > 0);
            next.setEnabled(current < model.getRowCount() - 1);
            delete.setEnabled(model.isEditable() && !insertRow && !deleted);
            newRecord.setEnabled(model.isEditable() && !insertRow);
        } finally {
            updating = false;
        }
    }

    private Color background(boolean insertRow, int column) {
        Color normal = Tokens.BG_SURFACE;
        if (insertRow) {
            return normal;
        }
        PendingChanges pending = model.pending();
        return switch (pending.kind(current)) {
            case INSERTED -> GridCellRenderer.INSERTED;
            case MODIFIED -> pending.isCellModified(current, column) ? GridCellRenderer.MODIFIED_CELL : normal;
            case DELETED -> GridCellRenderer.DELETED;
            case UNCHANGED -> normal;
        };
    }

    private static String placeholder(ColumnDef def, String value, boolean set) {
        if (value != null) {
            return null;
        }
        if (set) {
            return Texts.get("grid.null");
        }
        if (def.autoIncrement()) {
            return Texts.get("form.placeholder.auto");
        }
        if (!def.defaultValue().isNone()) {
            return Texts.get("form.placeholder.default");
        }
        return def.nullable() ? Texts.get("grid.null") : "";
    }

    // ---------------------------------------------------------------- dai campi al modello

    private void apply(int column) {
        if (updating) {
            return;
        }
        String text = fields.get(column).getText();
        updating = true;
        try {
            boolean wasInsertRow = model.isInsertRow(current);
            model.setValueAt(text, current, column);
            if (wasInsertRow && !model.isInsertRow(current)) {
                // la riga è stata creata: resta sul record, che ora è una riga in sospeso
                current = Math.min(current, model.dataRowCount() - 1);
            }
        } finally {
            updating = false;
        }
        refreshStateOnly();
    }

    /** Dopo una digitazione: aggiorna colori, stato e contatori senza toccare il testo (il cursore resta dov'è). */
    private void refreshStateOnly() {
        javax.swing.SwingUtilities.invokeLater(this::refresh);
    }

    private void setNull(int column) {
        if (model.isInsertRow(current) || !model.column(column).nullable()) {
            return;
        }
        model.pending().setValue(current, column, null);
        model.fireTableRowsUpdated(current, current);
    }
}
