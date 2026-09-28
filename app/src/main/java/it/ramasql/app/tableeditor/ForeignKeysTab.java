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
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.FkPrecheck;

/**
 * Scheda <b>Chiavi esterne</b>: elenco (nome, tabella riferita, colonne, ON DELETE, ON UPDATE) e, per quella scelta,
 * le coppie colonna → colonna riferita. Avvisi di {@link FkPrecheck} accanto alla riga e sotto, calcolati sui
 * metadati già noti (T6.3, <b>senza contattare il server</b>); «Verifica dati» mostra la query delle righe orfane e
 * le righe trovate (T6.7). Su una tabella MyISAM la scheda è disattivata, con la spiegazione e la proposta di
 * convertire in InnoDB (T6.9).
 */
final class ForeignKeysTab extends JPanel {

    private static final long serialVersionUID = 1L;

    static final int MARK = 0;
    static final int NAME = 1;
    static final int REF_TABLE = 2;
    static final int COLUMNS = 3;
    static final int ON_DELETE = 4;
    static final int ON_UPDATE = 5;

    static final String CARD_EDITOR = "editor";
    static final String CARD_MYISAM = "myisam";

    private final TableEditor editor;
    private final CardLayout cards = new CardLayout();
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final PairsModel pairsModel = new PairsModel();
    private final JTable pairs = new JTable(pairsModel);
    private final JComboBox<String> refTables = new JComboBox<>();
    private final JComboBox<String> childColumns = new JComboBox<>();
    private final JComboBox<String> parentColumns = new JComboBox<>();
    private final JLabel detailTitle = new JLabel();
    private final Banner notes = new Banner("fks.notes");
    private final Banner myisamText = new Banner("fks.myisam.text");
    private final JButton verifyData;
    private final JButton convert;
    private final DataCheckPanel dataCheck = new DataCheckPanel("fks.datacheck");
    private String visibleCard = CARD_EDITOR;
    private boolean refreshing;

    ForeignKeysTab(TableEditor editor) {
        this.editor = editor;
        setLayout(cards);
        setName("tableeditor.fks");
        setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_16, Tokens.SPACE_16,
                Tokens.SPACE_8, Tokens.SPACE_16));
        setBackground(Tokens.BG_SURFACE);

        table.setName("fks.table");
        it.ramasql.app.theme.Tips.headers(table);
        Ui.styleTable(table);
        table.setPreferredScrollableViewportSize(new Dimension(600, 5 * Tokens.ROW_HEIGHT));
        Ui.narrow(table.getColumnModel().getColumn(MARK), 24);
        table.getColumnModel().getColumn(MARK).setCellRenderer(new Ui.MarkRenderer());
        table.getColumnModel().getColumn(NAME).setPreferredWidth(170);
        table.getColumnModel().getColumn(REF_TABLE).setPreferredWidth(120);
        table.getColumnModel().getColumn(COLUMNS).setPreferredWidth(220);
        refTables.setEditable(true);
        refTables.setName("fks.refTable");
        childColumns.setName("fks.childColumn");
        parentColumns.setName("fks.parentColumn");
        it.ramasql.app.theme.ComboTips.install(refTables, this::refTableTip);
        it.ramasql.app.theme.ComboTips.install(childColumns, n -> columnTip(editor.editedTable(), n));
        it.ramasql.app.theme.ComboTips.install(parentColumns, n -> {
            int i = selected();
            TableDef parent = i < 0 ? null : editor.parentOf(editor.editedTable().foreignKeys().get(i));
            return parent == null ? null : columnTip(parent, n);
        });
        table.getColumnModel().getColumn(REF_TABLE).setCellEditor(new DefaultCellEditor(refTables));
        table.getColumnModel().getColumn(ON_DELETE).setCellEditor(new DefaultCellEditor(actionCombo()));
        table.getColumnModel().getColumn(ON_UPDATE).setCellEditor(new DefaultCellEditor(actionCombo()));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing) {
                dataCheck.clear();
                refreshDetail();
            }
        });

        pairs.setName("fks.pairs");
        it.ramasql.app.theme.Tips.headers(pairs);
        Ui.styleTable(pairs);
        pairs.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(childColumns));
        pairs.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(parentColumns));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Ui.scroll(table), BorderLayout.CENTER);
        top.add(Ui.buttonRow(
                Ui.button("fks.add", Texts.get("tableeditor.fks.add"), this::addForeignKey),
                Ui.button("fks.remove", Texts.get("tableeditor.fks.remove"), this::removeSelected)),
                BorderLayout.SOUTH);

        JScrollPane pairsScroll = Ui.scroll(pairs);
        pairsScroll.setPreferredSize(new Dimension(320, 110));
        JPanel pairsBox = new JPanel(new BorderLayout());
        pairsBox.setOpaque(false);
        pairsBox.add(pairsScroll, BorderLayout.CENTER);
        pairsBox.add(Ui.buttonRow(
                Ui.button("fks.addPair", Texts.get("tableeditor.fks.addPair"), () -> addPair(null, null)),
                Ui.button("fks.removePair", Texts.get("tableeditor.fks.removePair"),
                        () -> removePair(pairs.getSelectedRow()))), BorderLayout.SOUTH);

        verifyData = Ui.button("fks.verifyData", Texts.get("tableeditor.verifyData"), this::verifySelectedData);
        JPanel checks = new JPanel(new BorderLayout(0, Tokens.SPACE_4));
        checks.setOpaque(false);
        checks.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_8, 0, 0, 0));
        checks.add(notes, BorderLayout.NORTH);
        checks.add(Ui.buttonRow(verifyData), BorderLayout.CENTER);
        checks.add(dataCheck, BorderLayout.SOUTH);

        JPanel detail = new JPanel(new BorderLayout(0, Tokens.SPACE_4));
        detail.setOpaque(false);
        detail.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_16, 0, 0, 0));
        detailTitle.setFont(detailTitle.getFont().deriveFont(java.awt.Font.BOLD));
        detailTitle.setForeground(Tokens.TEXT_PRIMARY);
        detailTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, Tokens.SPACE_8, 0));
        detail.add(detailTitle, BorderLayout.NORTH);
        detail.add(pairsBox, BorderLayout.CENTER);
        detail.add(checks, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, Ui.plainScroll(detail));
        split.setResizeWeight(0.4);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, CARD_EDITOR);

        // ---- MyISAM: niente chiavi esterne, con la spiegazione e la proposta (T6.9)
        convert = Ui.primary("fks.convert", Texts.get("tableeditor.fks.convert"), this::convertToInnoDb);
        JPanel myisam = new JPanel(new BorderLayout(0, Tokens.SPACE_12));
        myisam.setName("fks.myisam");
        myisam.setOpaque(false);
        myisam.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_24, Tokens.SPACE_24,
                Tokens.SPACE_24, Tokens.SPACE_24));
        JLabel title = new JLabel(Texts.get("tableeditor.fks.myisam.title"));
        title.setFont(Tokens.semibold(Tokens.TITLE));
        title.setForeground(Tokens.TEXT_PRIMARY);
        myisam.add(title, BorderLayout.NORTH);
        myisam.add(myisamText, BorderLayout.CENTER);
        JPanel convertRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        convertRow.setOpaque(false);
        convertRow.add(convert);
        myisam.add(convertRow, BorderLayout.SOUTH);
        JPanel myisamHolder = new JPanel(new BorderLayout());
        myisamHolder.setOpaque(false);
        myisamHolder.add(myisam, BorderLayout.NORTH);
        add(myisamHolder, CARD_MYISAM);
    }

    private static JComboBox<String> actionCombo() {
        JComboBox<String> combo = new JComboBox<>();
        for (FkAction a : FkAction.values()) {
            combo.addItem(a.sql());
        }
        combo.setName("fks.action");
        it.ramasql.app.theme.ComboTips.install(combo, it.ramasql.app.theme.Tips.of("tableeditor.fk.action"));
        return combo;
    }

    /** La tabella riferita: la sua chiave primaria, dai metadati già letti. */
    private String refTableTip(String name) {
        TableDef t = editor.tableNamed(name);
        String key = t == null ? Texts.get("tableeditor.refTable.noKey") : t.primaryKey()
                .map(pk -> String.join(", ", pk.columns())).orElse(Texts.get("tableeditor.refTable.noKey"));
        return it.ramasql.app.theme.Tips.titled(name, Texts.get("tableeditor.refTable.tooltip", name, key));
    }

    private static String columnTip(TableDef t, String name) {
        return t.columns().stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst()
                .map(c -> it.ramasql.app.theme.Tips.column(c, t.primaryKey().map(pk -> pk.columns().stream()
                        .anyMatch(name::equalsIgnoreCase)).orElse(false)))
                .orElse(null);
    }

    JTable table() {
        return table;
    }

    JTable pairs() {
        return pairs;
    }

    int selected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < editor.editedTable().foreignKeys().size() ? row : -1;
    }

    void select(int row) {
        table.changeSelection(row, NAME, false, false);
    }

    String notesText() {
        return notes.text();
    }

    String visibleCard() {
        return visibleCard;
    }

    String myisamText() {
        return myisamText.text();
    }

    JButton convertButton() {
        return convert;
    }

    JButton verifyDataButton() {
        return verifyData;
    }

    DataCheckPanel dataCheck() {
        return dataCheck;
    }

    /** Il modulo di modifica si usa solo su InnoDB. */
    boolean editable() {
        return CARD_EDITOR.equals(visibleCard) && table.isEnabled();
    }

    void stopEditing() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        if (pairs.isEditing()) {
            pairs.getCellEditor().stopCellEditing();
        }
    }

    // ================================================================ azioni

    void addForeignKey() {
        if (!editable()) {
            return;
        }
        stopEditing();
        editor.update(Edits::addForeignKey);
        select(model.getRowCount() - 1);
    }

    void removeSelected() {
        stopEditing();
        int i = selected();
        if (i >= 0 && editable()) {
            editor.update(t -> Edits.removeForeignKeyAt(t, i));
            if (model.getRowCount() > 0) {
                select(Math.min(i, model.getRowCount() - 1));
            }
        }
    }

    void setCell(int row, int column, Object value) {
        stopEditing();
        model.setValueAt(value, row, column);
    }

    void addPair(String column, String refColumn) {
        int i = selected();
        if (i >= 0 && editable()) {
            editor.update(t -> Edits.addForeignKeyPair(t, i, column, refColumn));
        }
    }

    void setPair(int pair, String column, String refColumn) {
        int i = selected();
        if (i >= 0 && editable() && pair >= 0 && pair < pairsModel.getRowCount()) {
            editor.update(t -> Edits.setForeignKeyPair(t, i, pair, column, refColumn));
        }
    }

    void removePair(int pair) {
        int i = selected();
        if (i >= 0 && editable() && pair >= 0 && pair < pairsModel.getRowCount()) {
            editor.update(t -> Edits.removeForeignKeyPair(t, i, pair));
        }
    }

    void convertToInnoDb() {
        editor.requestEngine("InnoDB");
    }

    /** «Verifica dati»: le righe della tabella che non trovano corrispondenza nella tabella riferita (T6.7). */
    void verifySelectedData() {
        int i = selected();
        if (i < 0 || !canVerify(editor.editedTable().foreignKeys().get(i))) {
            return;
        }
        ForeignKeyDef fk = editor.editedTable().foreignKeys().get(i);
        String sql = FkPrecheck.orphanRowsQuery(editor.serverTable(), fk);
        dataCheck.running(sql);
        editor.dataCheck().run(sql, result -> dataCheck.show(sql, result, "tableeditor.datacheck.noOrphans",
                "tableeditor.datacheck.orphans"));
    }

    private boolean canVerify(ForeignKeyDef fk) {
        return editor.originalTable() != null && !fk.refTable().isBlank() && !fk.columns().isEmpty()
                && fk.columns().size() == fk.refColumns().size()
                && fk.columns().stream().noneMatch(String::isBlank)
                && fk.refColumns().stream().noneMatch(String::isBlank);
    }

    // ================================================================ aggiornamento

    void refresh() {
        TableDef t = editor.editedTable();
        boolean innoDb = t.isInnoDb();
        visibleCard = innoDb ? CARD_EDITOR : CARD_MYISAM;
        cards.show(this, visibleCard);
        myisamText.set(Banner.Tone.INFO, Texts.get("tableeditor.fks.myisam.text", t.name(), t.engine()));
        table.setEnabled(innoDb);

        refreshing = true;
        try {
            int selected = table.getSelectedRow();
            int oldRows = model.rows;
            model.rows = t.foreignKeys().size();
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
            refTables.removeAllItems();
            List<String> names = new ArrayList<>(editor.tables().tableNames());
            if (names.stream().noneMatch(n -> n.equalsIgnoreCase(t.name()))) {
                names.add(t.name());            // chiave esterna autoreferenziale
            }
            names.forEach(refTables::addItem);
            childColumns.removeAllItems();
            for (ColumnDef c : t.columns()) {
                childColumns.addItem(c.name());
            }
        } finally {
            refreshing = false;
        }
        refreshDetail();
    }

    private void refreshDetail() {
        int i = selected();
        Checks checks = editor.checks();
        parentColumns.removeAllItems();
        int oldPairs = pairsModel.rows;
        pairsModel.rows = i < 0 ? 0 : editor.editedTable().foreignKeys().get(i).columns().size();
        if (oldPairs == pairsModel.rows && pairsModel.rows > 0) {
            pairsModel.fireTableRowsUpdated(0, pairsModel.rows - 1);
        } else {
            pairsModel.fireTableDataChanged();
        }
        if (i < 0) {
            detailTitle.setText(Texts.get("tableeditor.fks.none"));
            List<Checks.Problem> all = new ArrayList<>(checks.errors(Checks.Area.FOREIGN_KEYS));
            all.addAll(checks.warnings(Checks.Area.FOREIGN_KEYS));
            notes.setProblems(all);
            verifyData.setEnabled(false);
            return;
        }
        ForeignKeyDef fk = editor.editedTable().foreignKeys().get(i);
        detailTitle.setText(Texts.get("tableeditor.fks.detail", fk.name() == null ? "" : fk.name(),
                fk.refTable().isBlank() ? "…" : fk.refTable()));
        TableDef parent = editor.parentOf(fk);
        if (parent != null) {
            parent.columns().forEach(c -> parentColumns.addItem(c.name()));
        }
        List<Checks.Problem> problems = checks.of(Checks.Area.FOREIGN_KEYS, i);
        notes.setProblems(problems);
        verifyData.setEnabled(canVerify(fk));
        verifyData.setToolTipText(editor.originalTable() == null ? Texts.get("tableeditor.verifyData.newTable")
                : null);
    }

    // ================================================================ modelli

    private final class Model extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private int rows;

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return column == MARK ? "" : Texts.get("tableeditor.fks.header." + column);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return row < editor.editedTable().foreignKeys().size() && column != MARK && column != COLUMNS
                    && editor.editedTable().isInnoDb();
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (row >= editor.editedTable().foreignKeys().size()) {
                return null;
            }
            ForeignKeyDef f = editor.editedTable().foreignKeys().get(row);
            return switch (column) {
                case MARK -> editor.checks().of(Checks.Area.FOREIGN_KEYS, row);
                case NAME -> f.name() == null ? "" : f.name();
                case REF_TABLE -> f.refTable();
                case COLUMNS -> pairsText(f);
                case ON_DELETE -> f.onDelete().sql();
                case ON_UPDATE -> f.onUpdate().sql();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (!isCellEditable(row, column)) {
                return;
            }
            String text = value == null ? "" : value.toString().strip();
            switch (column) {
                case NAME -> editor.update(t -> Edits.changeForeignKeyAt(t, row, f -> f.withName(text)));
                case REF_TABLE -> {
                    TableDef parent = editor.tableNamed(text);
                    editor.update(t -> Edits.setReferencedTable(t, row, text, parent));
                }
                case ON_DELETE -> editor.update(t -> Edits.changeForeignKeyAt(t, row,
                        f -> f.withOnDelete(FkAction.fromSql(text))));
                case ON_UPDATE -> editor.update(t -> Edits.changeForeignKeyAt(t, row,
                        f -> f.withOnUpdate(FkAction.fromSql(text))));
                default -> { }
            }
        }
    }

    static String pairsText(ForeignKeyDef f) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < f.columns().size(); i++) {
            String ref = i < f.refColumns().size() ? f.refColumns().get(i) : "";
            parts.add(f.columns().get(i) + " → " + (ref.isBlank() ? "…" : ref));
        }
        return String.join(", ", parts);
    }

    private final class PairsModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private int rows;

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return Texts.get(column == 0 ? "tableeditor.fks.pair.column" : "tableeditor.fks.pair.refColumn");
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return editable();
        }

        @Override
        public Object getValueAt(int row, int column) {
            int i = selected();
            if (i < 0) {
                return null;
            }
            ForeignKeyDef f = editor.editedTable().foreignKeys().get(i);
            List<String> list = column == 0 ? f.columns() : f.refColumns();
            return row < list.size() ? list.get(row) : "";
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            String text = value == null ? "" : value.toString();
            setPair(row, column == 0 ? text : null, column == 1 ? text : null);
        }
    }
}
