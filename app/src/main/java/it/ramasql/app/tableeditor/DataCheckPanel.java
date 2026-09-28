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
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;

/**
 * Esito di «Verifica dati» (T6.7, T6.8): la query usata, sempre visibile (lo studente la può copiare e rieseguire),
 * una riga che dice cosa significa il risultato e le righe trovate.
 */
final class DataCheckPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextArea query = new JTextArea();
    private final Banner status;
    private final DefaultTableModel rows = new DefaultTableModel() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(rows);
    private final JScrollPane rowsScroll = new JScrollPane(table);

    DataCheckPanel(String name) {
        super(new BorderLayout(0, Tokens.SPACE_8));
        setName(name);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_8, 0, 0, 0));
        status = new Banner(name + ".status");
        query.setName(name + ".query");
        query.setEditable(false);
        query.setLineWrap(true);
        query.setBackground(Tokens.BG_SUNKEN);
        query.setForeground(Tokens.TEXT_PRIMARY);
        query.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_8, Tokens.SPACE_12,
                Tokens.SPACE_8, Tokens.SPACE_12));
        query.setFont(Tokens.mono(Tokens.SMALL));
        table.setName(name + ".rows");
        Ui.styleTable(table);
        rowsScroll.setBorder(BorderFactory.createLineBorder(Tokens.BORDER_SUBTLE));
        rowsScroll.setPreferredSize(new Dimension(200, 120));
        JPanel top = new JPanel(new BorderLayout(0, Tokens.SPACE_8));
        top.setOpaque(false);
        top.add(query, BorderLayout.NORTH);
        top.add(status, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(rowsScroll, BorderLayout.CENTER);
        clear();
    }

    void clear() {
        query.setText("");
        status.clear();
        rows.setDataVector(new Object[0][0], new Object[0]);
        setVisible(false);
    }

    void running(String sql) {
        query.setText(sql);
        status.set(Banner.Tone.INFO, Texts.get("tableeditor.datacheck.running"));
        rows.setDataVector(new Object[0][0], new Object[0]);
        rowsScroll.setVisible(false);
        setVisible(true);
        revalidate();
    }

    /**
     * @param noneKey  testo quando non ci sono righe
     * @param foundKey testo con il numero di righe trovate ({@code %d})
     */
    void show(String sql, DataCheck.DataCheckResult result, String noneKey, String foundKey) {
        query.setText(sql);
        if (result.error() != null) {
            status.set(Banner.Tone.DANGER, Texts.get("tableeditor.datacheck.failed", result.error()));
            rows.setDataVector(new Object[0][0], new Object[0]);
            rowsScroll.setVisible(false);
        } else {
            List<List<String>> data = result.rows();
            String text = data.isEmpty() ? Texts.get(noneKey) : result.truncated()
                    ? Texts.get(foundKey + ".atLeast", data.size()) + " "
                            + Texts.get("tableeditor.datacheck.truncated", data.size())
                    : Texts.get(foundKey, data.size());
            status.set(data.isEmpty() ? Banner.Tone.SUCCESS : Banner.Tone.WARNING, text);
            Object[][] cells = new Object[data.size()][];
            for (int r = 0; r < data.size(); r++) {
                cells[r] = data.get(r).stream().map(v -> v == null ? "NULL" : v).toArray();
            }
            rows.setDataVector(cells, result.columns().toArray());
            rowsScroll.setVisible(!data.isEmpty());
        }
        setVisible(true);
        revalidate();
        repaint();
    }

    String queryText() {
        return query.getText();
    }

    String statusText() {
        return status.text();
    }

    int rowCount() {
        return rows.getRowCount();
    }

    Object valueAt(int row, int column) {
        return rows.getValueAt(row, column);
    }

    JTable table() {
        return table;
    }
}
