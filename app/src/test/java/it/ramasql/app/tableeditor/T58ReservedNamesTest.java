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

import static it.ramasql.app.tableeditor.TableEditorTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T5.8 (lato componente): tabella {@code ordine dettagli} (con lo spazio) e colonna {@code order} (parola
 * riservata): l'anteprima e l'SQL eseguito li mettono tra backtick; poi una rinomina di colonna esistente con
 * parola riservata diventa {@code CHANGE COLUMN}.
 */
@Tag("step5")
@Tag("ui")
class T58ReservedNamesTest {

    private static final String CATALOG = "ramasql_test_t58";
    private Harness h;

    @BeforeAll
    static void laf() {
        setupLookAndFeel();
    }

    @AfterEach
    void close() {
        if (h != null) {
            h.dispose();
        }
    }

    @Test
    void ordineDettagliConColonnaOrder() {
        h = open(null, CATALOG, MARIADB, new FakeTables());
        onEdt(() -> {
            h.editor.optionsTab().setTableName("ordine dettagli");
            ColumnsTab cols = h.editor.columnsTab();
            cols.addColumn();
            cols.setCell(1, ColumnsTab.NAME, "order");
            cols.setCell(1, ColumnsTab.TYPE, "INT");
            cols.setCell(1, ColumnsTab.NN, true);
            cols.addColumn();
            cols.setCell(2, ColumnsTab.NAME, "select");
            cols.setCell(2, ColumnsTab.DEFAULT, "nuovo");
        });
        String expected = """
                CREATE TABLE `ramasql_test_t58`.`ordine dettagli` (
                  `id` INT NOT NULL AUTO_INCREMENT,
                  `order` INT NOT NULL,
                  `select` VARCHAR(45) NULL DEFAULT 'nuovo',
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB""";
        assertEquals(List.of(expected), fromEdt(() -> h.editor.previewStatements()));
        h.tab(TableEditor.TAB_SQL);
        h.screenshot("step5", "T5.8-ordine-dettagli");

        onEdt(() -> h.editor.apply());
        assertEquals(List.of(expected), h.applier.requests.get(0).statements());

        // tabella ora «sul server»: rinomina di una colonna con parola riservata
        onEdt(() -> h.editor.columnsTab().setCell(1, ColumnsTab.NAME, "group"));
        assertEquals(List.of("ALTER TABLE `ramasql_test_t58`.`ordine dettagli` CHANGE COLUMN `order` `group` INT "
                + "NOT NULL"), fromEdt(() -> h.editor.previewStatements()));
        writeText("step5", "T5.8", "ESEGUITO:\n" + expected + ";\n\nRINOMINA SUCCESSIVA:\n"
                + String.join("\n", fromEdt(() -> h.editor.previewStatements())) + ";\n");
    }
}
