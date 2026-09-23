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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;

/**
 * Scheda <b>Colonne</b> (DESIGN §3.6, come il Table Editor di Workbench ridotto): una riga per colonna con nome,
 * tipo, lunghezza/valori, PK, NN, UQ, AI, UN, default e commento; aggiungi/togli colonna. Il segno a sinistra della
 * riga dice se c'è un errore (✘) o un avviso (⚠, es. troncamento possibile); sotto la griglia le spiegazioni.
 */
final class ColumnsTab extends JPanel {

    private static final long serialVersionUID = 1L;

    static final int MARK = 0;
    static final int NAME = 1;
    static final int TYPE = 2;
    static final int ARGS = 3;
    static final int PK = 4;
    static final int NN = 5;
    static final int UQ = 6;
    static final int AI = 7;
    static final int UN = 8;
    static final int DEFAULT = 9;
    static final int COMMENT = 10;

    private final TableEditor editor;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final Banner notes = new Banner("columns.notes");

    ColumnsTab(TableEditor editor) {
        super(new BorderLayout(0, Tokens.SPACE_8));
        this.editor = editor;
        setName("tableeditor.columns");
        setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_16, Tokens.SPACE_16,
                Tokens.SPACE_8, Tokens.SPACE_16));
        setBackground(Tokens.BG_SURFACE);
        table.setName("columns.table");
        Ui.styleTable(table);
        Ui.narrow(table.getColumnModel().getColumn(MARK), 24);
        table.getColumnModel().getColumn(MARK).setCellRenderer(new Ui.MarkRenderer());
        for (int c = PK; c <= UN; c++) {
            Ui.narrow(table.getColumnModel().getColumn(c), 44);
        }
        table.getColumnModel().getColumn(NAME).setPreferredWidth(150);
        table.getColumnModel().getColumn(TYPE).setPreferredWidth(120);
        table.getColumnModel().getColumn(ARGS).setPreferredWidth(110);
        table.getColumnModel().getColumn(DEFAULT).setPreferredWidth(120);
        table.getColumnModel().getColumn(COMMENT).setPreferredWidth(180);
        table.getColumnModel().getColumn(TYPE).setCellEditor(new DefaultCellEditor(typeCombo()));

        add(Ui.scroll(table), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, Tokens.SPACE_4));
        south.setOpaque(false);
        south.add(Ui.buttonRow(
                Ui.button("columns.add", Texts.get("tableeditor.columns.add"), this::addColumn),
                Ui.button("columns.remove", Texts.get("tableeditor.columns.remove"), this::removeSelected)),
                BorderLayout.NORTH);
        south.add(notes, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    /** Elenco dei tipi, raggruppati; le intestazioni di gruppo si vedono ma non si scelgono. */
    private static JComboBox<Object> typeCombo() {
        JComboBox<Object> combo = new JComboBox<>(TypeChoices.comboItems());
        combo.setName("columns.typeEditor");
        combo.setEditable(true);
        combo.setMaximumRowCount(20);
        combo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                boolean header = value instanceof TypeChoices.Header;
                super.getListCellRendererComponent(list, value, index, isSelected && !header, cellHasFocus);
                if (header) {
                    setFont(getFont().deriveFont(Font.BOLD));
                    setEnabled(false);
                }
                return this;
            }
        });
        return combo;
    }

    JTable table() {
        return table;
    }

    String notesText() {
        return notes.text();
    }

    void stopEditing() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    // ================================================================ azioni (pulsanti)

    void addColumn() {
        stopEditing();
        editor.update(t -> Edits.addColumn(t, Texts.get("tableeditor.columns.newName")));
        int last = model.getRowCount() - 1;
        table.changeSelection(last, NAME, false, false);
    }

    void removeSelected() {
        stopEditing();
        int row = table.getSelectedRow();
        if (row >= 0 && row < editor.editedTable().columns().size()) {
            editor.update(t -> Edits.removeColumn(t, row));
            int rows = model.getRowCount();
            if (rows > 0) {
                table.changeSelection(Math.min(row, rows - 1), NAME, false, false);
            }
        }
    }

    /** Come se l'utente scrivesse o spuntasse la cella: passa dallo stesso {@code setValueAt} della griglia. */
    void setCell(int row, int column, Object value) {
        stopEditing();
        model.setValueAt(value, row, column);
    }

    Object cell(int row, int column) {
        return model.getValueAt(row, column);
    }

    void refresh() {
        int selected = table.getSelectedRow();
        int oldRows = model.rows;
        model.rows = editor.editedTable().columns().size();
        if (oldRows == model.rows) {
            if (model.rows > 0) {
                model.fireTableRowsUpdated(0, model.rows - 1);
            }
        } else {
            model.fireTableDataChanged();
            if (selected >= 0 && selected < model.rows) {
                table.changeSelection(selected, Math.max(0, table.getSelectedColumn()), false, false);
            }
        }
        Checks checks = editor.checks();
        java.util.List<Checks.Problem> problems = new java.util.ArrayList<>(checks.errors(Checks.Area.COLUMNS));
        problems.addAll(checks.warnings(Checks.Area.COLUMNS));
        notes.setProblems(problems);
    }

    // ================================================================ modello della griglia

    private final class Model extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private int rows;

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount() {
            return 11;
        }

        @Override
        public String getColumnName(int column) {
            return column == MARK ? "" : Texts.get("tableeditor.columns.header." + column);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column >= PK && column <= UN ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            if (column == MARK || row >= editor.editedTable().columns().size()) {
                return false;
            }
            ColumnDef c = editor.editedTable().columns().get(row);
            if (c.generated()) {
                return false;       // elemento avanzato: conservato, non modificabile in v1
            }
            return switch (column) {
                case AI -> SqlTypes.isInteger(c.dataType());
                case UN -> SqlTypes.isNumeric(c.dataType());
                case DEFAULT -> !c.autoIncrement();
                default -> true;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            TableDef t = editor.editedTable();
            if (row >= t.columns().size()) {
                return null;
            }
            ColumnDef c = t.columns().get(row);
            return switch (column) {
                case MARK -> editor.checks().of(Checks.Area.COLUMNS, row);
                case NAME -> c.name();
                case TYPE -> c.dataType();
                case ARGS -> c.typeArgs() == null ? "" : c.typeArgs();
                case PK -> Edits.isPrimaryKey(t, c.name());
                case NN -> !c.nullable();
                case UQ -> Edits.isUniqueColumn(t, c.name());
                case AI -> c.autoIncrement();
                case UN -> c.unsigned();
                case DEFAULT -> DefaultText.format(c.defaultValue());
                case COMMENT -> c.comment();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (row >= editor.editedTable().columns().size() || !isCellEditable(row, column)) {
                return;
            }
            String text = value == null ? "" : value.toString();
            boolean on = Boolean.TRUE.equals(value);
            switch (column) {
                case NAME -> editor.update(t -> Edits.renameColumn(t, row, text));
                case TYPE -> editor.update(t -> Edits.setType(t, row, value));
                case ARGS -> editor.update(t -> Edits.setTypeArgs(t, row, text));
                case PK -> editor.update(t -> Edits.setPrimaryKey(t, row, on));
                case NN -> editor.update(t -> Edits.setNotNull(t, row, on));
                case UQ -> editor.update(t -> Edits.setUnique(t, row, on));
                case AI -> editor.update(t -> Edits.setAutoIncrement(t, row, on));
                case UN -> editor.update(t -> Edits.setUnsigned(t, row, on));
                case DEFAULT -> editor.update(t -> Edits.setDefault(t, row, text));
                case COMMENT -> editor.update(t -> Edits.setComment(t, row, text));
                default -> { }
            }
        }
    }
}
