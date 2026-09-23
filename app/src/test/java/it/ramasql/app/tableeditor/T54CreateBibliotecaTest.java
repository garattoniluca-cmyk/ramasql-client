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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * T5.4 (lato componente): {@code editori} e {@code libri} della «biblioteca» creati da zero con l'editor, come farebbe
 * lo studente. L'anteprima viva coincide <b>carattere per carattere</b> con {@code TableDiff.diff(null, …)} della
 * tabella attesa, scritta a mano con l'API di core, e l'SQL passato all'esecuzione è <b>identico</b> all'anteprima.
 */
@Tag("step5")
@Tag("ui")
class T54CreateBibliotecaTest {

    private static final String CATALOG = "ramasql_test_t54";
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
    void editoriDaZero() {
        h = open(null, CATALOG, MARIADB, new FakeTables());
        onEdt(() -> {
            OptionsTab options = h.editor.optionsTab();
            options.setTableName("editori");
            ColumnsTab cols = h.editor.columnsTab();
            cols.setCell(0, ColumnsTab.UN, true);            // id INT UNSIGNED (PK, NN, AI già proposti)
            cols.addColumn();
            cols.setCell(1, ColumnsTab.NAME, "nome");
            cols.setCell(1, ColumnsTab.TYPE, "VARCHAR");
            cols.setCell(1, ColumnsTab.ARGS, "80");
            cols.setCell(1, ColumnsTab.NN, true);
            cols.addColumn();
            cols.setCell(2, ColumnsTab.NAME, "citta");
            cols.setCell(2, ColumnsTab.TYPE, "VARCHAR(60)");  // tipo e lunghezza scritti insieme
        });

        TableDef expected = new TableDef(CATALOG, "editori", "InnoDB", null, null, "", null,
                List.of(idColumn(), ColumnDef.of("nome", "VARCHAR", "80").notNull(),
                        ColumnDef.of("citta", "VARCHAR", "60")),
                List.of(IndexDef.primary("id")), List.of(), List.of());
        List<String> expectedSql = TableDiff.diff(null, expected, MARIADB);
        String literal = """
                CREATE TABLE `ramasql_test_t54`.`editori` (
                  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
                  `nome` VARCHAR(80) NOT NULL,
                  `citta` VARCHAR(60) NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB""";

        List<String> preview = fromEdt(() -> h.editor.previewStatements());
        assertEquals(List.of(literal), preview, "anteprima = SQL atteso scritto a mano");
        assertEquals(expectedSql, preview, "anteprima = TableDiff.diff(null, tabella attesa)");
        assertEquals(literal + ";", fromEdt(() -> h.editor.previewText()), "testo della scheda SQL");
        assertEquals(literal + ";", fromEdt(() -> h.editor.sqlTab().shownText()));

        h.tab(TableEditor.TAB_COLUMNS);
        h.screenshot("step5", "T5.4-editori-colonne");
        h.tab(TableEditor.TAB_SQL);
        h.screenshot("step5", "T5.4-editori-sql");

        onEdt(() -> h.editor.apply());
        assertEquals(1, h.applier.requests.size());
        List<String> executed = h.applier.requests.get(0).statements();
        assertEquals(preview, executed, "SQL passato all'esecuzione IDENTICO all'anteprima");
        String outcome = fromEdt(() -> h.editor.outcomeText());
        assertTrue(outcome.contains("✔ verificato sul server"), outcome);
        assertTrue(fromEdt(() -> h.editor.previewStatements().isEmpty()), "dopo l'applicazione: nessuna modifica");
        h.screenshot("step5", "T5.4-editori-applicata");
        writeText("step5", "T5.4-editori", "ANTEPRIMA (scheda SQL):\n" + String.join(";\n\n", preview)
                + ";\n\nESEGUITO (passato a TableApplier):\n" + String.join(";\n\n", executed)
                + ";\n\nIDENTICI: " + preview.equals(executed) + "\n\nESITO:\n" + outcome + "\n");
    }

    @Test
    void libriDaZero() {
        h = open(null, CATALOG, MYSQL, new FakeTables().with(editori(CATALOG)));
        onEdt(() -> {
            OptionsTab options = h.editor.optionsTab();
            options.setTableName("libri");
            options.setCharset(CHARSET, COLLATION);
            options.setComment("Catalogo dei libri");
            ColumnsTab cols = h.editor.columnsTab();
            cols.setCell(0, ColumnsTab.UN, true);
            cols.addColumn();
            cols.setCell(1, ColumnsTab.NAME, "titolo");
            cols.setCell(1, ColumnsTab.ARGS, "150");
            cols.setCell(1, ColumnsTab.NN, true);
            cols.addColumn();
            cols.setCell(2, ColumnsTab.NAME, "isbn");
            cols.setCell(2, ColumnsTab.TYPE, "CHAR");
            cols.setCell(2, ColumnsTab.ARGS, "13");
            cols.setCell(2, ColumnsTab.UQ, true);
            cols.addColumn();
            cols.setCell(3, ColumnsTab.NAME, "anno");
            cols.setCell(3, ColumnsTab.TYPE, "SMALLINT");
            cols.setCell(3, ColumnsTab.UN, true);
            cols.addColumn();
            cols.setCell(4, ColumnsTab.NAME, "prezzo");
            cols.setCell(4, ColumnsTab.TYPE, "DECIMAL");
            cols.setCell(4, ColumnsTab.ARGS, "6,2");
            cols.setCell(4, ColumnsTab.NN, true);
            cols.setCell(4, ColumnsTab.DEFAULT, "0.00");
            cols.addColumn();
            cols.setCell(5, ColumnsTab.NAME, "id_editore");
            cols.setCell(5, ColumnsTab.TYPE, "INT");
            cols.setCell(5, ColumnsTab.UN, true);
        });

        TableDef expected = new TableDef(CATALOG, "libri", "InnoDB", CHARSET, COLLATION, "Catalogo dei libri", null,
                List.of(idColumn(),
                        ColumnDef.of("titolo", "VARCHAR", "150").notNull(),
                        ColumnDef.of("isbn", "CHAR", "13"),
                        ColumnDef.of("anno", "SMALLINT").withUnsigned(true),
                        ColumnDef.of("prezzo", "DECIMAL", "6,2").notNull().withDefault(ColumnDefault.literal("0.00")),
                        ColumnDef.of("id_editore", "INT").withUnsigned(true)),
                List.of(IndexDef.primary("id"), IndexDef.unique("isbn_UNIQUE", "isbn")), List.of(), List.of());
        List<String> preview = fromEdt(() -> h.editor.previewStatements());
        assertEquals(TableDiff.diff(null, expected, MYSQL), preview, "anteprima = TableDiff.diff(null, attesa)");
        assertEquals(1, preview.size());
        assertTrue(preview.get(0).contains("`prezzo` DECIMAL(6,2) NOT NULL DEFAULT 0.00"), preview.get(0));
        assertTrue(preview.get(0).contains("UNIQUE INDEX `isbn_UNIQUE` (`isbn`)"), preview.get(0));
        assertTrue(preview.get(0).endsWith(
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Catalogo dei libri'"),
                preview.get(0));

        h.tab(TableEditor.TAB_COLUMNS);
        h.screenshot("step5", "T5.4-libri-colonne");
        h.tab(TableEditor.TAB_OPTIONS);
        h.screenshot("step5", "T5.4-libri-opzioni");
        h.tab(TableEditor.TAB_SQL);
        h.screenshot("step5", "T5.4-libri-sql");

        onEdt(() -> h.editor.apply());
        List<String> executed = h.applier.requests.get(0).statements();
        assertEquals(preview, executed, "SQL passato all'esecuzione IDENTICO all'anteprima");
        assertEquals(expected.withOrdinalPositions(), fromEdt(() -> h.editor.originalTable()),
                "l'editor si ricarica con la tabella riletta");
        writeText("step5", "T5.4-libri", "ANTEPRIMA:\n" + String.join(";\n\n", preview)
                + ";\n\nESEGUITO:\n" + String.join(";\n\n", executed) + ";\n\nIDENTICI: " + preview.equals(executed)
                + "\n\nESITO:\n" + fromEdt(() -> h.editor.outcomeText()) + "\n");
    }
}
