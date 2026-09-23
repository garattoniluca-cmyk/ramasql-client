/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import it.ramasql.app.Texts;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.grid.GridPrompts;
import it.ramasql.app.grid.InMemoryGridDataSource;
import it.ramasql.core.metadata.ColumnDef;

/**
 * La parte bassa dell'editor SQL: una scheda «Esito» con una riga per istruzione eseguita (esito, righe interessate,
 * durata, avvisi) e il riquadro dell'errore, più una sotto-scheda «Risultato N» per ogni istruzione che ha restituito
 * righe, con la griglia in sola lettura ({@link DataGrid#readOnly}). Si svuota a ogni nuova esecuzione.
 */
public final class ResultsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final GridPrompts gridPrompts;
    private final int rowLimit;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final DefaultTableModel outcomes;
    private final JTable outcomeTable;
    private final JTextArea errorArea = new JTextArea();
    private final JScrollPane errorScroll;
    private final JLabel status = new JLabel(Texts.get("editor.status.ready"));
    private final List<DataGrid> grids = new ArrayList<>();

    ResultsPanel(GridPrompts gridPrompts, int rowLimit) {
        super(new BorderLayout());
        this.gridPrompts = gridPrompts;
        this.rowLimit = rowLimit;
        setName("sqlEditor.results");
        outcomes = new DefaultTableModel(new Object[] {
            Texts.get("editor.results.col.number"), Texts.get("editor.results.col.statement"),
            Texts.get("editor.results.col.outcome"), Texts.get("editor.results.col.duration"),
            Texts.get("editor.results.col.warnings")}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        outcomeTable = new JTable(outcomes);
        outcomeTable.setName("sqlEditor.outcomes");
        outcomeTable.setFillsViewportHeight(true);
        outcomeTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        outcomeTable.getColumnModel().getColumn(0).setMaxWidth(60);
        outcomeTable.getColumnModel().getColumn(1).setPreferredWidth(360);
        outcomeTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        outcomeTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        outcomeTable.getColumnModel().getColumn(4).setPreferredWidth(220);

        errorArea.setName("sqlEditor.error");
        errorArea.setEditable(false);
        errorArea.setLineWrap(true);
        errorArea.setWrapStyleWord(true);
        errorArea.setRows(4);
        errorArea.setForeground(new Color(0xB00020));
        errorScroll = new JScrollPane(errorArea);
        errorScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")));
        errorScroll.setVisible(false);

        JPanel outcomePanel = new JPanel(new BorderLayout());
        outcomePanel.add(new JScrollPane(outcomeTable), BorderLayout.CENTER);
        outcomePanel.add(errorScroll, BorderLayout.SOUTH);
        tabs.addTab(Texts.get("editor.results.outcome"), outcomePanel);

        status.setName("sqlEditor.status");
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    /** Nuova esecuzione: toglie risultati, esiti ed errore precedenti. */
    void clear() {
        while (tabs.getTabCount() > 1) {
            tabs.removeTabAt(1);
        }
        grids.clear();
        outcomes.setRowCount(0);
        errorArea.setText("");
        errorScroll.setVisible(false);
        tabs.setSelectedIndex(0);
        revalidate();
    }

    void setStatus(String text) {
        status.setText(text);
    }

    /** Aggiunge l'esito di un'istruzione (e la sua sotto-scheda, se ha righe). */
    void addOutcome(int number, PositionedStatement statement, StatementOutcome outcome) {
        String outcomeText;
        if (outcome.isError()) {
            outcomeText = Texts.get("editor.outcome.error", outcome.error().code());
        } else if (outcome.hasResult()) {
            ResultData r = outcome.result();
            outcomeText = r.truncated() ? Texts.get("editor.outcome.rowsTruncated", r.rowCount())
                    : r.rowCount() == 1 ? Texts.get("editor.outcome.rows.one")
                    : Texts.get("editor.outcome.rows", r.rowCount());
            addResultTab(r);
        } else if (outcome.affectedRows() >= 0) {
            outcomeText = outcome.affectedRows() == 1 ? Texts.get("editor.outcome.affected.one")
                    : Texts.get("editor.outcome.affected", outcome.affectedRows());
        } else {
            outcomeText = Texts.get("editor.outcome.ok");
        }
        outcomes.addRow(new Object[] {number, abbreviate(statement.text()), outcomeText,
            formatDuration(outcome.duration()), warningsText(outcome.warnings())});
    }

    /** Mostra l'errore (messaggio del server + spiegazione) e porta in primo piano la scheda Esito. */
    void showError(String text) {
        errorArea.setText(text);
        errorArea.setCaretPosition(0);
        errorScroll.setVisible(true);
        tabs.setSelectedIndex(0);
        revalidate();
    }

    private void addResultTab(ResultData r) {
        List<ColumnDef> columns = new ArrayList<>();
        for (ResultData.Column c : r.columns()) {
            columns.add(ColumnDef.of(c.name(), baseType(c.typeName())));
        }
        String explanation = r.truncated() ? Texts.get("editor.result.truncated", r.rowCount()) : null;
        DataGrid grid = DataGrid.readOnly(columns, new InMemoryGridDataSource(columns, r.rows()),
                Math.max(1, rowLimit), explanation, gridPrompts);
        grid.setName("sqlEditor.result." + (grids.size() + 1));
        grids.add(grid);
        tabs.addTab(Texts.get("editor.results.result", grids.size()), grid);
        if (grids.size() == 1) {
            tabs.setSelectedIndex(1);   // il primo risultato si vede subito
        }
    }

    private static String baseType(String typeName) {
        String t = typeName.trim();
        int cut = 0;
        while (cut < t.length() && (Character.isLetter(t.charAt(cut)) || t.charAt(cut) == '_')) {
            cut++;
        }
        return cut == 0 ? "VARCHAR" : t.substring(0, cut);
    }

    static String abbreviate(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 119) + "…";
    }

    static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return Texts.get("editor.duration", String.format(Locale.ITALIAN, "%.3f", seconds));
    }

    private static String warningsText(List<StatementOutcome.Warning> warnings) {
        if (warnings.isEmpty()) {
            return "";
        }
        StatementOutcome.Warning first = warnings.getFirst();
        String head = warnings.size() == 1 ? Texts.get("editor.outcome.warnings.one")
                : Texts.get("editor.outcome.warnings", warnings.size());
        return head + ": " + first.code() + " " + first.message();
    }

    // ---------------------------------------------------------------- lettura (per i test e l'integrazione)

    public JTabbedPane tabs() {
        return tabs;
    }

    public JTable outcomeTable() {
        return outcomeTable;
    }

    /** Quante sotto-schede «Risultato N» ci sono. */
    public int resultCount() {
        return grids.size();
    }

    /** La griglia della sotto-scheda «Risultato {@code n}» (da 1). */
    public DataGrid resultGrid(int n) {
        return grids.get(n - 1);
    }

    /** Le righe della scheda Esito: numero, istruzione, esito, durata, avvisi. */
    public List<List<String>> outcomeRows() {
        List<List<String>> out = new ArrayList<>();
        for (int r = 0; r < outcomes.getRowCount(); r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < outcomes.getColumnCount(); c++) {
                row.add(String.valueOf(outcomes.getValueAt(r, c)));
            }
            out.add(row);
        }
        return out;
    }

    /** Il testo dell'errore mostrato, {@code ""} se nessuno. */
    public String errorText() {
        return errorScroll.isVisible() ? errorArea.getText() : "";
    }

    public String statusText() {
        return status.getText();
    }
}
