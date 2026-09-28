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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.sqlgen.IndexPrecheck;

/**
 * Scheda <b>Indici</b>: elenco degli indici (nome, tipo PRIMARY/UNIQUE/INDEX, colonne) e, per quello scelto, le sue
 * colonne <b>in ordine</b> con i pulsanti per aggiungerle, toglierle e spostarle. Gli avvisi di {@link IndexPrecheck}
 * stanno accanto alla riga e sotto; «Verifica dati» su un UNIQUE mostra la query dei duplicati e le righe trovate.
 */
final class IndexesTab extends JPanel {

    private static final long serialVersionUID = 1L;

    static final int MARK = 0;
    static final int NAME = 1;
    static final int KIND = 2;
    static final int COLUMNS = 3;

    private final TableEditor editor;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final DefaultListModel<String> indexColumns = new DefaultListModel<>();
    private final JList<String> columnList = new JList<>(indexColumns);
    private final JComboBox<String> columnChoice = new JComboBox<>();
    private final JLabel detailTitle = new JLabel();
    private final Banner notes = new Banner("indexes.notes");
    private final JButton verifyData;
    private final DataCheckPanel dataCheck = new DataCheckPanel("indexes.datacheck");
    private final JPanel detail = new JPanel(new BorderLayout(0, 4));
    private boolean refreshing;

    IndexesTab(TableEditor editor) {
        super(new BorderLayout(0, Tokens.SPACE_8));
        this.editor = editor;
        setName("tableeditor.indexes");
        setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_16, Tokens.SPACE_16,
                Tokens.SPACE_8, Tokens.SPACE_16));
        setBackground(Tokens.BG_SURFACE);
        table.setName("indexes.table");
        it.ramasql.app.theme.Tips.headers(table);
        Ui.styleTable(table);
        table.setPreferredScrollableViewportSize(new Dimension(600, 5 * Tokens.ROW_HEIGHT));
        Ui.narrow(table.getColumnModel().getColumn(MARK), 24);
        table.getColumnModel().getColumn(MARK).setCellRenderer(new Ui.MarkRenderer());
        table.getColumnModel().getColumn(NAME).setPreferredWidth(180);
        table.getColumnModel().getColumn(KIND).setPreferredWidth(90);
        table.getColumnModel().getColumn(COLUMNS).setPreferredWidth(300);
        JComboBox<IndexKind> kinds = new JComboBox<>(IndexKind.values());
        kinds.setName("indexes.kind");
        it.ramasql.app.theme.ComboTips.install(kinds, k -> it.ramasql.app.theme.Tips.item("tableeditor.index.kind", k,
                k.name()));
        table.getColumnModel().getColumn(KIND).setCellEditor(new DefaultCellEditor(kinds));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing) {
                dataCheck.clear();
                refreshDetail();
            }
        });

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Ui.scroll(table), BorderLayout.CENTER);
        top.add(Ui.buttonRow(
                Ui.button("indexes.add", Texts.get("tableeditor.indexes.add"), this::addIndex),
                Ui.button("indexes.remove", Texts.get("tableeditor.indexes.remove"), this::removeSelected)),
                BorderLayout.SOUTH);

        columnList.setName("indexes.columns");
        columnList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        columnChoice.setName("indexes.columnChoice");
        it.ramasql.app.theme.ComboTips.install(columnChoice, this::columnTip);
        columnList.setFixedCellHeight(Tokens.ROW_HEIGHT);
        columnList.setSelectionBackground(Tokens.ACCENT_TINT);
        columnList.setSelectionForeground(Tokens.TEXT_PRIMARY);
        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, Tokens.SPACE_8));
        buttons.setOpaque(false);
        buttons.add(columnChoice);
        buttons.add(Ui.button("indexes.addColumn", Texts.get("tableeditor.indexes.addColumn"),
                () -> addColumn((String) columnChoice.getSelectedItem())));
        buttons.add(Ui.button("indexes.removeColumn", Texts.get("tableeditor.indexes.removeColumn"),
                () -> removeColumn(columnList.getSelectedIndex())));
        buttons.add(Ui.button("indexes.up", Texts.get("tableeditor.indexes.up"),
                () -> moveColumn(columnList.getSelectedIndex(), -1)));
        buttons.add(Ui.button("indexes.down", Texts.get("tableeditor.indexes.down"),
                () -> moveColumn(columnList.getSelectedIndex(), +1)));
        JPanel buttonsHolder = new JPanel(new BorderLayout());
        buttonsHolder.setOpaque(false);
        buttonsHolder.add(buttons, BorderLayout.NORTH);
        JScrollPane listScroll = Ui.scroll(columnList);
        listScroll.setPreferredSize(new Dimension(240, 140));
        JPanel columnsBox = new JPanel(new BorderLayout(Tokens.SPACE_16, 0));
        columnsBox.setOpaque(false);
        columnsBox.add(listScroll, BorderLayout.CENTER);
        columnsBox.add(buttonsHolder, BorderLayout.EAST);

        verifyData = Ui.button("indexes.verifyData", Texts.get("tableeditor.verifyData"), this::verifySelectedData);
        JPanel checks = new JPanel(new BorderLayout(0, Tokens.SPACE_4));
        checks.setOpaque(false);
        checks.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_8, 0, 0, 0));
        checks.add(notes, BorderLayout.NORTH);
        checks.add(Ui.buttonRow(verifyData), BorderLayout.CENTER);
        checks.add(dataCheck, BorderLayout.SOUTH);

        detail.setOpaque(false);
        detail.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_16, 0, 0, 0));
        detailTitle.setFont(detailTitle.getFont().deriveFont(java.awt.Font.BOLD));
        detailTitle.setForeground(Tokens.TEXT_PRIMARY);
        detailTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, Tokens.SPACE_8, 0));
        detail.add(detailTitle, BorderLayout.NORTH);
        detail.add(columnsBox, BorderLayout.CENTER);
        detail.add(checks, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, Ui.plainScroll(detail));
        split.setResizeWeight(0.4);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);
    }

    JTable table() {
        return table;
    }

    int selected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < editor.editedTable().indexes().size() ? row : -1;
    }

    void select(int row) {
        table.changeSelection(row, NAME, false, false);
    }

    List<String> selectedColumns() {
        int i = selected();
        return i < 0 ? List.of() : editor.editedTable().indexes().get(i).columns();
    }

    String notesText() {
        return notes.text();
    }

    DataCheckPanel dataCheck() {
        return dataCheck;
    }

    JButton verifyDataButton() {
        return verifyData;
    }

    void stopEditing() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    // ================================================================ azioni

    void addIndex() {
        stopEditing();
        editor.update(t -> Edits.addIndex(t, Texts.get("tableeditor.indexes.newName")));
        select(model.getRowCount() - 1);
    }

    void removeSelected() {
        stopEditing();
        int i = selected();
        if (i >= 0) {
            editor.update(t -> Edits.removeIndexAt(t, i));
            if (model.getRowCount() > 0) {
                select(Math.min(i, model.getRowCount() - 1));
            }
        }
    }

    void setCell(int row, int column, Object value) {
        stopEditing();
        model.setValueAt(value, row, column);
    }

    void addColumn(String column) {
        int i = selected();
        if (i >= 0 && column != null) {
            editor.update(t -> Edits.addIndexColumn(t, i, column));
            columnList.setSelectedIndex(indexColumns.size() - 1);
        }
    }

    void removeColumn(int position) {
        int i = selected();
        if (i >= 0 && position >= 0 && position < indexColumns.size()) {
            editor.update(t -> Edits.removeIndexColumn(t, i, position));
        }
    }

    void moveColumn(int position, int delta) {
        int i = selected();
        if (i >= 0 && position >= 0) {
            editor.update(t -> Edits.moveIndexColumn(t, i, position, delta));
            int target = position + delta;
            if (target >= 0 && target < indexColumns.size()) {
                columnList.setSelectedIndex(target);
            }
        }
    }

    /** «Verifica dati»: duplicati che impedirebbero l'UNIQUE (o la chiave primaria) sulle colonne scelte (T6.8). */
    void verifySelectedData() {
        int i = selected();
        if (i < 0 || !canVerify(editor.editedTable().indexes().get(i))) {
            return;
        }
        IndexDef index = editor.editedTable().indexes().get(i);
        String sql = IndexPrecheck.duplicatesQuery(editor.serverTable(), index.columns());
        dataCheck.running(sql);
        editor.dataCheck().run(sql, result -> dataCheck.show(sql, result, "tableeditor.datacheck.noDuplicates",
                "tableeditor.datacheck.duplicates"));
    }

    private boolean canVerify(IndexDef index) {
        return editor.originalTable() != null && index.isUnique() && !index.columns().isEmpty();
    }

    // ================================================================ aggiornamento

    /** La spiegazione di una colonna della tabella che si sta modificando. */
    private String columnTip(String name) {
        it.ramasql.core.metadata.TableDef t = editor.editedTable();
        return t.columns().stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst()
                .map(c -> it.ramasql.app.theme.Tips.column(c, t.primaryKey().map(pk -> pk.columns().stream()
                        .anyMatch(name::equalsIgnoreCase)).orElse(false)))
                .orElse(null);
    }

    void refresh() {
        refreshing = true;
        try {
            int selected = table.getSelectedRow();
            int oldRows = model.rows;
            model.rows = editor.editedTable().indexes().size();
            if (oldRows == model.rows) {
                if (model.rows > 0) {
                    model.fireTableRowsUpdated(0, model.rows - 1);
                }
            } else {
                model.fireTableDataChanged();
                if (selected >= 0 && selected < model.rows) {
                    table.changeSelection(selected, NAME, false, false);
                }
            }
            columnChoice.removeAllItems();
            for (ColumnDef c : editor.editedTable().columns()) {
                columnChoice.addItem(c.name());
            }
        } finally {
            refreshing = false;
        }
        refreshDetail();
    }

    private void refreshDetail() {
        int i = selected();
        int keep = columnList.getSelectedIndex();
        indexColumns.clear();
        Checks checks = editor.checks();
        if (i < 0) {
            detailTitle.setText(Texts.get("tableeditor.indexes.none"));
            List<Checks.Problem> all = new java.util.ArrayList<>(checks.errors(Checks.Area.INDEXES));
            all.addAll(checks.warnings(Checks.Area.INDEXES));
            notes.setProblems(all);
            setDetailEnabled(false);
            verifyData.setEnabled(false);
            return;
        }
        IndexDef index = editor.editedTable().indexes().get(i);
        detailTitle.setText(Texts.get("tableeditor.indexes.detail", index.name()));
        index.columns().forEach(indexColumns::addElement);
        if (keep >= 0 && keep < indexColumns.size()) {
            columnList.setSelectedIndex(keep);
        }
        List<Checks.Problem> problems = checks.of(Checks.Area.INDEXES, i);
        notes.setProblems(problems);
        setDetailEnabled(true);
        verifyData.setEnabled(canVerify(index));
        verifyData.setToolTipText(editor.originalTable() == null ? Texts.get("tableeditor.verifyData.newTable")
                : index.isUnique() ? null : Texts.get("tableeditor.indexes.verifyOnlyUnique"));
    }

    private void setDetailEnabled(boolean enabled) {
        columnList.setEnabled(enabled);
        columnChoice.setEnabled(enabled);
    }

    // ================================================================ modello

    private final class Model extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private int rows;

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return column == MARK ? "" : Texts.get("tableeditor.indexes.header." + column);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            if (row >= editor.editedTable().indexes().size()) {
                return false;
            }
            return column == KIND || (column == NAME && !editor.editedTable().indexes().get(row).isPrimary());
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (row >= editor.editedTable().indexes().size()) {
                return null;
            }
            IndexDef i = editor.editedTable().indexes().get(row);
            return switch (column) {
                case MARK -> editor.checks().of(Checks.Area.INDEXES, row);
                case NAME -> i.name();
                case KIND -> i.kind();
                case COLUMNS -> String.join(", ", i.columns());
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (!isCellEditable(row, column)) {
                return;
            }
            if (column == NAME) {
                editor.update(t -> Edits.renameIndexAt(t, row, value == null ? "" : value.toString()));
            } else if (column == KIND) {
                IndexKind kind = value instanceof IndexKind k ? k : parseKind(value);
                if (kind == IndexKind.PRIMARY && editor.editedTable().primaryKey().isPresent()
                        && !editor.editedTable().indexes().get(row).isPrimary()) {
                    editor.notice(Texts.get("tableeditor.indexes.onePrimary"));
                    return;
                }
                editor.update(t -> Edits.setIndexKind(t, row, kind, Texts.get("tableeditor.indexes.newName")));
            }
        }

        private IndexKind parseKind(Object value) {
            try {
                return value == null ? null : IndexKind.valueOf(value.toString().strip().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
