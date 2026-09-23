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
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/**
 * T5.7 (lato componente): conversione InnoDB→MyISAM di {@code editori} (riferita dalla FK di {@code libri})
 * <b>bloccata con spiegazione</b>; bloccata anche per una tabella che ha chiavi esterne sue; libera per una tabella
 * senza legami, e conversione inversa, con la spiegazione delle conseguenze.
 */
@Tag("step5")
@Tag("ui")
class T57EngineTest {

    private static final String CATALOG = "ramasql_test_t57";
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

    private static TableDef note() {
        return new TableDef(CATALOG, "note", "InnoDB", CHARSET, COLLATION, "", null,
                List.of(idColumn(), ColumnDef.of("testo", "VARCHAR", "200")), List.of(IndexDef.primary("id")),
                List.of(), List.of()).withOrdinalPositions();
    }

    @Test
    void editoriRiferitaDaLibriNonPassaAMyIsam() {
        h = open(editori(CATALOG), CATALOG, MARIADB, biblioteca(CATALOG));
        onEdt(() -> h.editor.optionsTab().setEngine("MyISAM"));

        String blocked = fromEdt(() -> h.editor.optionsTab().engineBlockedText());
        assertTrue(blocked.contains("«editori» non può passare a MyISAM: MyISAM non supporta le chiavi esterne."),
                blocked);
        assertTrue(blocked.contains("libri.fk_libri_editori"), blocked);
        assertEquals("InnoDB", fromEdt(() -> h.editor.editedTable().engine()), "engine invariato");
        assertEquals("InnoDB", fromEdt(() -> h.editor.optionsTab().selectedEngine()), "la scelta torna a InnoDB");
        assertTrue(fromEdt(() -> h.editor.previewStatements().isEmpty()), "nessun ALTER");
        h.tab(TableEditor.TAB_OPTIONS);
        h.screenshot("step5", "T5.7-editori-bloccata");
        writeText("step5", "T5.7-editori-bloccata", blocked + "\n");
    }

    @Test
    void tabellaConChiaviEsterneSueNonPassaAMyIsam() {
        h = open(libri(CATALOG), CATALOG, MYSQL, biblioteca(CATALOG));
        onEdt(() -> h.editor.optionsTab().setEngine("MyISAM"));
        String blocked = fromEdt(() -> h.editor.optionsTab().engineBlockedText());
        assertTrue(blocked.contains("Ha le sue chiavi esterne: fk_libri_editori"), blocked);
        assertEquals("InnoDB", fromEdt(() -> h.editor.editedTable().engine()));
    }

    @Test
    void tabellaLiberaAndataERitorno() {
        h = open(note(), CATALOG, MARIADB, biblioteca(CATALOG).with(note()));
        onEdt(() -> h.editor.optionsTab().setEngine("MyISAM"));
        assertEquals("", fromEdt(() -> h.editor.optionsTab().engineBlockedText()));
        assertEquals(List.of("ALTER TABLE `ramasql_test_t57`.`note` ENGINE=MyISAM"),
                fromEdt(() -> h.editor.previewStatements()));
        String notes = fromEdt(() -> h.editor.optionsTab().engineNotesText());
        assertTrue(notes.contains("MyISAM: niente chiavi esterne né transazioni"), notes);
        assertTrue(notes.contains("Passando da InnoDB a MyISAM il server ricostruisce la tabella"), notes);
        h.tab(TableEditor.TAB_OPTIONS);
        h.screenshot("step5", "T5.7-note-myisam");

        onEdt(() -> h.editor.apply());
        assertEquals(List.of("ALTER TABLE `ramasql_test_t57`.`note` ENGINE=MyISAM"),
                h.applier.requests.get(0).statements());
        assertEquals("MyISAM", fromEdt(() -> h.editor.originalTable().engine()), "ricaricata MyISAM");

        onEdt(() -> h.editor.optionsTab().setEngine("InnoDB"));        // conversione inversa
        assertEquals(List.of("ALTER TABLE `ramasql_test_t57`.`note` ENGINE=InnoDB"),
                fromEdt(() -> h.editor.previewStatements()));
        onEdt(() -> h.editor.apply());
        assertEquals("InnoDB", fromEdt(() -> h.editor.originalTable().engine()));
        writeText("step5", "T5.7-note", "ANDATA: " + h.applier.requests.get(0).statements() + "\nRITORNO: "
                + h.applier.requests.get(1).statements() + "\nSPIEGAZIONE:\n" + notes + "\n");
    }

    @Test
    void tabellaNuovaMyIsam() {
        h = open(null, CATALOG, MYSQL, new FakeTables());
        onEdt(() -> {
            h.editor.optionsTab().setTableName("registro");
            h.editor.optionsTab().setEngine("MyISAM");
        });
        List<String> preview = fromEdt(() -> h.editor.previewStatements());
        assertEquals(1, preview.size());
        assertTrue(preview.get(0).endsWith(") ENGINE=MyISAM"), preview.get(0));
        assertEquals(ForeignKeysTab.CARD_MYISAM, fromEdt(() -> h.editor.foreignKeysTab().visibleCard()));
    }
}
