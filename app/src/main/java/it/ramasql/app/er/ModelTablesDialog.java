/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.app.tableeditor.Ui;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;

/**
 * «Nuovo modello dal catalogo…» ({@code DESIGN.md} §3.11): quali tabelle mettere nel modello. Tutte spuntate in
 * partenza, con <em>Tutte</em> e <em>Nessuna</em>; almeno una per creare il modello. Le viste non ci sono (non sono
 * entità del modello).
 */
public final class ModelTablesDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final List<String> tables;
    private final boolean[] checked;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JLabel error = DialogButtons.errorLabel();
    private final DialogButtons buttons;
    private transient List<String> result;

    public ModelTablesDialog(Window owner, String catalog, List<String> tables) {
        super(owner, Texts.get("er.tables.title", catalog), ModalityType.APPLICATION_MODAL);
        this.tables = List.copyOf(tables);
        this.checked = new boolean[tables.size()];
        java.util.Arrays.fill(checked, true);
        setName("er.tables.dialog");

        JPanel form = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_12)));
        form.setOpaque(false);
        int pad = Tokens.px(Tokens.SPACE_24);
        form.setBorder(BorderFactory.createEmptyBorder(pad, pad, Tokens.px(Tokens.SPACE_8), pad));
        JLabel heading = Styles.text(new JLabel(Texts.get("er.tables.title", catalog)), "heading", Tokens.TEXT_PRIMARY);
        JLabel hint = new JLabel("<html>" + Texts.get("er.tables.hint") + "</html>");
        hint.setForeground(Tokens.TEXT_SECONDARY);
        JPanel top = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_8)));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(hint, BorderLayout.CENTER);
        form.add(top, BorderLayout.NORTH);

        table.setName("er.tables.table");
        table.setToolTipText(Texts.get("er.tables.table.tooltip"));
        Ui.styleTable(table);
        Ui.narrow(table.getColumnModel().getColumn(0), Tokens.px(36));
        table.setTableHeader(null);
        JScrollPane scroll = Ui.scroll(table);
        scroll.setPreferredSize(new Dimension(Tokens.px(360), Tokens.px(Tokens.ROW_HEIGHT) * Math.min(12,
                Math.max(4, tables.size())) + 4));
        form.add(scroll, BorderLayout.CENTER);

        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        quick.setOpaque(false);
        quick.add(Ui.button("er.tables.all", Texts.get("er.tables.all"), () -> setAll(true)));
        quick.add(javax.swing.Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
        quick.add(Ui.button("er.tables.none", Texts.get("er.tables.none"), () -> setAll(false)));
        form.add(quick, BorderLayout.SOUTH);
        for (java.awt.Component c : quick.getComponents()) {
            if (c instanceof javax.swing.JButton b) {
                b.setToolTipText(Texts.get(b.getName() + ".tooltip"));
            }
        }

        buttons = new DialogButtons(this, Texts.get("er.tables.confirm"), error, this::confirm);
        Styles.primary(buttons.confirmButton(), Tokens.ACCENT);
        buttons.confirmButton().setName("er.tables.confirm");
        buttons.confirmButton().setToolTipText(Texts.get("er.tables.confirm.tooltip"));
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Tokens.BG_SURFACE);
        buttons.setOpaque(false);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        it.ramasql.app.theme.Tips.fromNames(getRootPane());   // suggerimenti <nome>.tooltip (ADR-020)
        pack();
        setLocationRelativeTo(owner);
        it.ramasql.app.theme.Screens.fit(this);   // dentro lo schermo anche a 1024x768 (T12.6)
    }

    /** Spunta o toglie tutte. */
    public void setAll(boolean value) {
        java.util.Arrays.fill(checked, value);
        model.fireTableDataChanged();
        error.setText(" ");
    }

    /** Spunta o toglie una tabella (per la tastiera e per i test). */
    public void setChecked(String name, boolean value) {
        int i = tables.indexOf(name);
        if (i >= 0) {
            checked[i] = value;
            model.fireTableRowsUpdated(i, i);
        }
    }

    /** Le tabelle spuntate, nell'ordine dell'elenco. */
    public List<String> selection() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            if (checked[i]) {
                out.add(tables.get(i));
            }
        }
        return out;
    }

    /** «Crea il modello»: almeno una tabella. */
    public void confirm() {
        List<String> s = selection();
        if (s.isEmpty()) {
            error.setText(Texts.get("er.tables.error.none"));
            return;
        }
        result = s;
        dispose();
    }

    public DialogButtons buttons() {
        return buttons;
    }

    public String errorText() {
        return error.getText();
    }

    /** Mostra la finestra (modale); {@code null} = annullata. */
    public List<String> showModal() {
        result = null;
        setVisible(true);
        return result;
    }

    private final class Model extends AbstractTableModel {
        private static final long serialVersionUID = 1L;

        @Override
        public int getRowCount() {
            return tables.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            return column == 0 ? checked[row] : tables.get(row);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            checked[row] = Boolean.TRUE.equals(value);
            fireTableRowsUpdated(row, row);
            error.setText(" ");
        }
    }
}
