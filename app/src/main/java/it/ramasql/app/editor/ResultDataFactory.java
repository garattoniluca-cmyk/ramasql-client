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

import java.util.ArrayList;
import java.util.List;

import it.ramasql.app.grid.ResultCells;
import it.ramasql.core.exec.ResultTable;

/**
 * Da {@link ResultTable} (quello che l'esecutore ha letto dal server) a {@link ResultData} (quello che l'editor SQL
 * mostra). La conversione dei valori in testo è la stessa della griglia di data-entry ({@link ResultCells}): una sola
 * regola per tutto il programma.
 */
public final class ResultDataFactory {

    private ResultDataFactory() {
    }

    public static ResultData of(ResultTable table) {
        List<ResultData.Column> columns = new ArrayList<>(table.columnCount());
        for (ResultTable.Column c : table.columns()) {
            columns.add(new ResultData.Column(c.label(), c.typeName()));
        }
        return new ResultData(columns, ResultCells.rows(table), table.truncated());
    }
}
