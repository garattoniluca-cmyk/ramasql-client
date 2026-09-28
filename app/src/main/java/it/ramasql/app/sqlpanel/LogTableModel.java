/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.sqlpanel;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.table.AbstractTableModel;

import it.ramasql.app.Texts;
import it.ramasql.core.exec.SqlLog;

/** Le righe del Registro: una per istruzione eseguita (ora, origine, SQL, esito, durata, righe). Solo sull'EDT. */
public final class LogTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    public static final int COL_NUMBER = 0;
    public static final int COL_TIME = 1;
    public static final int COL_ORIGIN = 2;
    public static final int COL_SQL = 3;
    public static final int COL_OUTCOME = 4;
    public static final int COL_DURATION = 5;
    public static final int COL_ROWS = 6;

    private static final String[] HEADERS = {"panel.log.col.number", "panel.log.col.time", "panel.log.col.origin",
        "panel.log.col.sql", "panel.log.col.outcome", "panel.log.col.duration", "panel.log.col.rows"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final transient List<SqlLog.Entry> entries = new ArrayList<>();

    void add(SqlLog.Entry e) {
        entries.add(e);
        fireTableRowsInserted(entries.size() - 1, entries.size() - 1);
    }

    /** Più righe insieme, con un solo evento. */
    void addAll(java.util.List<SqlLog.Entry> list) {
        if (list.isEmpty()) {
            return;
        }
        int from = entries.size();
        entries.addAll(list);
        fireTableRowsInserted(from, entries.size() - 1);
    }

    void clear() {
        entries.clear();
        fireTableDataChanged();
    }

    public SqlLog.Entry entry(int modelRow) {
        return entries.get(modelRow);
    }

    public List<SqlLog.Entry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return HEADERS.length;
    }

    @Override
    public String getColumnName(int column) {
        return Texts.get(HEADERS[column]);
    }

    @Override
    public Object getValueAt(int row, int column) {
        SqlLog.Entry e = entries.get(row);
        return switch (column) {
            case COL_NUMBER -> e.sequence();
            case COL_TIME -> TIME.format(e.time());
            case COL_ORIGIN -> e.origin();
            case COL_SQL -> oneLine(e.sql());
            case COL_OUTCOME -> outcome(e);
            case COL_DURATION -> Texts.get("panel.log.duration", e.durationMillis());
            case COL_ROWS -> e.isOk() ? String.valueOf(e.rows()) : "";
            default -> "";
        };
    }

    /** «OK», «Errore 1146», «Interrotta». */
    static String outcome(SqlLog.Entry e) {
        return switch (e.outcome()) {
            case OK -> Texts.get("panel.log.outcome.ok");
            case ERROR -> Texts.get("panel.log.outcome.error", e.errorCode());
            case INTERRUPTED -> Texts.get("panel.log.outcome.interrupted");
        };
    }

    static String oneLine(String sql) {
        return sql.replaceAll("\\s+", " ").strip();
    }
}
