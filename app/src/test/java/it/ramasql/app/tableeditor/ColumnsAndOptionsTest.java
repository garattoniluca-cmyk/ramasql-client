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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * Schede Colonne, Opzioni e SQL (Step 5): rinomina = {@code CHANGE COLUMN} (posizione conservata), aggiungi/togli,
 * PK/NN/UQ/AI/UN, default scritti in modo semplice, tipo con lunghezza, nessuna modifica = nessuna istruzione,
 * rinomina della tabella, commento, AUTO_INCREMENT, charset/collation di una tabella esistente.
 */
@Tag("step5")
@Tag("ui")
class ColumnsAndOptionsTest {

    private static final String CATALOG = "ramasql_test_cols";
    private static final String T = "`ramasql_test_cols`.`editori`";
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

    private void openEditori() {
        h = open(editori(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
    }

    @Test
    void nessunaModificaNessunaIstruzione() {
        openEditori();
        assertTrue(fromEdt(() -> h.editor.previewStatements().isEmpty()));
        assertEquals("-- Nessuna modifica", fromEdt(() -> h.editor.sqlTab().shownText()));
        assertEquals("Nessuna modifica da applicare", fromEdt(() -> h.editor.sqlTab().counterText()));
        assertFalse(fromEdt(() -> h.editor.applyButton().isEnabled()), "Applica disattivato senza modifiche");
        assertFalse(fromEdt(() -> h.editor.isModified()));
        onEdt(() -> h.editor.apply());
        assertEquals(1, h.prompts.messages.size());
        assertTrue(h.applier.requests.isEmpty());
    }

    @Test
    void rinominaDiColonnaEsistenteDiventaChangeColumnEAggiornaGliIndici() {
        openEditori();
        onEdt(() -> h.editor.columnsTab().setCell(1, ColumnsTab.NAME, "denominazione"));
        assertEquals(List.of("ALTER TABLE " + T + " CHANGE COLUMN `nome` `denominazione` VARCHAR(80) NOT NULL"),
                fromEdt(() -> h.editor.previewStatements()), "una sola CHANGE: l'indice segue la colonna");
        assertEquals(List.of("denominazione"),
                fromEdt(() -> h.editor.editedTable().index("uq_editori_nome").orElseThrow().columns()));
        assertEquals(2, fromEdt(() -> h.editor.editedTable().columns().get(1).ordinalPosition()),
                "la colonna rinominata conserva la posizione");
    }

    @Test
    void aggiungiETogliColonne() {
        openEditori();
        onEdt(() -> {
            ColumnsTab cols = h.editor.columnsTab();
            cols.addColumn();
            cols.setCell(3, ColumnsTab.NAME, "telefono");
            cols.setCell(3, ColumnsTab.ARGS, "20");
            cols.table().changeSelection(2, ColumnsTab.NAME, false, false);
            cols.removeSelected();                                          // toglie «citta»
        });
        assertEquals(0, fromEdt(() -> h.editor.editedTable().column("telefono").orElseThrow().ordinalPosition()),
                "colonna nuova: posizione 0");
        assertEquals(List.of("ALTER TABLE " + T + "\n  DROP COLUMN `citta`,\n  ADD COLUMN `telefono` VARCHAR(20) NULL"),
                fromEdt(() -> h.editor.previewStatements()));
        h.tab(TableEditor.TAB_SQL);
        h.screenshot("step5", "colonne-aggiungi-togli");
    }

    @Test
    void casellePkNnUqAiUn() {
        h = open(null, CATALOG, MYSQL, new FakeTables());
        onEdt(() -> {
            ColumnsTab cols = h.editor.columnsTab();
            cols.addColumn();
            cols.setCell(1, ColumnsTab.NAME, "codice");
            cols.setCell(1, ColumnsTab.UQ, true);
        });
        TableDef t = fromEdt(() -> h.editor.editedTable());
        assertEquals(IndexKind.UNIQUE, t.index("codice_UNIQUE").orElseThrow().kind());
        assertEquals(Boolean.TRUE, fromEdt(() -> h.editor.columnsTab().cell(1, ColumnsTab.UQ)));
        onEdt(() -> h.editor.columnsTab().setCell(1, ColumnsTab.UQ, false));
        assertTrue(fromEdt(() -> h.editor.editedTable().index("codice_UNIQUE").isEmpty()), "UQ tolto");

        onEdt(() -> h.editor.columnsTab().setCell(1, ColumnsTab.PK, true));   // PK composta (id, codice)
        t = fromEdt(() -> h.editor.editedTable());
        assertEquals(List.of("id", "codice"), t.primaryKey().orElseThrow().columns());
        assertFalse(t.column("codice").orElseThrow().nullable(), "PK implica NOT NULL");
        onEdt(() -> h.editor.columnsTab().setCell(1, ColumnsTab.NN, false));
        assertFalse(fromEdt(() -> h.editor.editedTable().column("codice").orElseThrow().nullable()),
                "una colonna della chiave primaria resta NOT NULL");

        // AI e UN solo sui tipi che li ammettono
        assertFalse(fromEdt(() -> h.editor.columnsTab().table().getModel().isCellEditable(1, ColumnsTab.AI)),
                "AI non si spunta su VARCHAR");
        assertFalse(fromEdt(() -> h.editor.columnsTab().table().getModel().isCellEditable(1, ColumnsTab.UN)));
        onEdt(() -> h.editor.columnsTab().setCell(0, ColumnsTab.AI, false));
        onEdt(() -> h.editor.columnsTab().setCell(0, ColumnsTab.UN, true));
        ColumnDef id = fromEdt(() -> h.editor.editedTable().column("id").orElseThrow());
        assertFalse(id.autoIncrement());
        assertTrue(id.unsigned());
    }

    @Test
    void autoIncrementFuoriDaUnaChiaveEUnErroreEvidente() {
        h = open(null, CATALOG, MARIADB, new FakeTables());
        onEdt(() -> h.editor.columnsTab().setCell(0, ColumnsTab.PK, false));
        String notes = fromEdt(() -> h.editor.columnsTab().notesText());
        assertTrue(notes.startsWith("✘ La colonna «id» è AUTO_INCREMENT"), notes);
        assertTrue(fromEdt(() -> h.editor.tabs().getTitleAt(TableEditor.TAB_COLUMNS)).endsWith("✘"));
        onEdt(() -> h.editor.apply());
        assertEquals(1, h.prompts.errors.size(), "errore evidente: non si applica");
        assertTrue(h.prompts.errors.get(0).contains("AUTO_INCREMENT"), h.prompts.errors.get(0));
        assertTrue(h.applier.requests.isEmpty());
        h.tab(TableEditor.TAB_COLUMNS);
        h.screenshot("step5", "colonne-errore-evidente");
    }

    @Test
    void defaultScrittiInModoSemplice() {
        h = open(null, CATALOG, MARIADB, new FakeTables());
        onEdt(() -> {
            ColumnsTab cols = h.editor.columnsTab();
            for (int i = 1; i <= 5; i++) {
                cols.addColumn();
            }
            cols.setCell(1, ColumnsTab.DEFAULT, "italiana");
            cols.setCell(2, ColumnsTab.TYPE, "DECIMAL(6,2)");
            cols.setCell(2, ColumnsTab.DEFAULT, "0.00");
            cols.setCell(3, ColumnsTab.TYPE, "TIMESTAMP");
            cols.setCell(3, ColumnsTab.DEFAULT, "CURRENT_TIMESTAMP");
            cols.setCell(4, ColumnsTab.DEFAULT, "NULL");
            cols.setCell(5, ColumnsTab.DEFAULT, "''");
        });
        List<ColumnDef> c = fromEdt(() -> h.editor.editedTable().columns());
        assertEquals(ColumnDefault.literal("italiana"), c.get(1).defaultValue());
        assertEquals(ColumnDefault.literal("0.00"), c.get(2).defaultValue());
        assertEquals("DECIMAL", c.get(2).dataType());
        assertEquals("6,2", c.get(2).typeArgs());
        assertEquals(ColumnDefault.expression("CURRENT_TIMESTAMP"), c.get(3).defaultValue());
        assertNull(c.get(3).typeArgs(), "TIMESTAMP: la lunghezza di VARCHAR non passa al nuovo tipo");
        assertEquals(ColumnDefault.NULL_VALUE, c.get(4).defaultValue());
        assertEquals(ColumnDefault.literal(""), c.get(5).defaultValue());
        assertEquals("''", fromEdt(() -> h.editor.columnsTab().cell(5, ColumnsTab.DEFAULT)), "stringa vuota visibile");
        String sql = fromEdt(() -> h.editor.previewStatements().get(0));
        assertTrue(sql.contains("`colonna1` VARCHAR(45) NULL DEFAULT 'italiana'"), sql);
        assertTrue(sql.contains("`colonna2` DECIMAL(6,2) NULL DEFAULT 0.00"), sql);
        assertTrue(sql.contains("`colonna3` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP"), sql);
        assertTrue(sql.contains("`colonna4` VARCHAR(45) NULL DEFAULT NULL"), sql);
        assertTrue(sql.contains("`colonna5` VARCHAR(45) NULL DEFAULT ''"), sql);
    }

    @Test
    void opzioniRinominaCommentoAutoIncrement() {
        openEditori();
        onEdt(() -> {
            OptionsTab o = h.editor.optionsTab();
            o.setTableName("case editrici");
            o.setComment("Le case editrici");
            o.setAutoIncrement("100");
        });
        assertEquals(List.of("RENAME TABLE " + T + " TO `ramasql_test_cols`.`case editrici`",
                "ALTER TABLE `ramasql_test_cols`.`case editrici`\n  AUTO_INCREMENT=100,\n  COMMENT='Le case editrici'"),
                fromEdt(() -> h.editor.previewStatements()));
        assertEquals("SQL · 2", fromEdt(() -> h.editor.tabs().getTitleAt(TableEditor.TAB_SQL)));
        h.tab(TableEditor.TAB_OPTIONS);
        h.screenshot("step5", "opzioni");

        onEdt(() -> h.editor.optionsTab().setAutoIncrement("dieci"));
        assertTrue(fromEdt(() -> h.editor.optionsTab().notesText()).contains("«dieci» non va bene"));
        onEdt(() -> h.editor.optionsTab().setTableName(""));
        assertEquals("-- L'anteprima comparirà quando avrai completato i campi segnati con ✘.",
                fromEdt(() -> h.editor.sqlTab().shownText()), "nome vuoto: niente SQL, nessuna eccezione");
        assertTrue(fromEdt(() -> h.editor.tabs().getTitleAt(TableEditor.TAB_OPTIONS)).endsWith("✘"));
    }

    /**
     * Charset/collation di una tabella <b>esistente</b>: {@code DEFAULT CHARSET=… COLLATE=…} vale solo per le colonne
     * future, quindi le colonne di testo esistenti ricevono nel modello il charset di prima (esplicito), l'utente è
     * avvisato, e dopo l'applicazione la rilettura non lascia differenze.
     */
    @Test
    void charsetDiTabellaEsistenteNonToccaLeColonne() {
        openEditori();
        onEdt(() -> h.editor.optionsTab().setCharset("latin1", "latin1_swedish_ci"));
        assertEquals(List.of("ALTER TABLE " + T + " DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci"),
                fromEdt(() -> h.editor.previewStatements()), "nessuna MODIFY delle colonne esistenti");
        TableDef edited = fromEdt(() -> h.editor.editedTable());
        for (String name : List.of("nome", "citta")) {
            ColumnDef c = edited.column(name).orElseThrow();
            assertEquals(CHARSET, c.charset(), name + ": charset di prima, ora esplicito");
            assertEquals(COLLATION, c.collation(), name);
        }
        assertNull(edited.column("id").orElseThrow().charset(), "le colonne non di testo non cambiano");
        String notes = fromEdt(() -> h.editor.optionsTab().charsetNotesText());
        assertTrue(notes.contains("valgono per le colonne che aggiungerai: le colonne di testo esistenti mantengono "
                + "il loro charset"), notes);

        // una colonna aggiunta ora eredita il nuovo charset della tabella
        onEdt(() -> {
            h.editor.columnsTab().addColumn();
            h.editor.columnsTab().setCell(3, ColumnsTab.NAME, "sito");
        });
        assertNull(fromEdt(() -> h.editor.editedTable().column("sito").orElseThrow().charset()));
        List<String> preview = fromEdt(() -> h.editor.previewStatements());
        assertEquals(List.of("ALTER TABLE " + T + "\n  ADD COLUMN `sito` VARCHAR(45) NULL,\n"
                + "  DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci"), preview);
        h.tab(TableEditor.TAB_OPTIONS);
        h.screenshot("step5", "opzioni-charset");

        // il server rilegge le colonne con il loro charset esplicito: nessuna differenza residua
        onEdt(() -> h.editor.apply());
        TableDef requested = h.applier.requests.get(0).edited();
        assertEquals(List.of(), TableDiff.diff(requested.withOrdinalPositions(), requested, MARIADB));
        assertTrue(fromEdt(() -> h.editor.previewStatements().isEmpty()));
        assertTrue(fromEdt(() -> h.editor.outcomeText()).contains("✔ verificato sul server"));
        writeText("step5", "opzioni-charset", "SQL:\n" + String.join("\n", preview) + "\n\nAVVISO:\n" + notes + "\n");
    }
}
