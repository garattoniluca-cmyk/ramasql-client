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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.FkPrecheck;
import it.ramasql.core.sqlgen.PrecheckWarning;
import it.ramasql.core.sqlgen.PrecheckWarning.Code;

/**
 * T6.3 (lato interfaccia): ogni caso dei controlli preventivi di {@link FkPrecheck} compare come avviso accanto alla
 * riga della chiave esterna (segno ⚠/✘ con il suo codice) e nel riquadro sotto, <b>senza contattare il server</b>
 * (le finte di esecuzione e controllo dati restano intatte). La FK proposta è {@code prestiti.id_socio → soci.id},
 * con {@code soci} alterata di volta in volta.
 */
@Tag("step6")
@Tag("ui")
class T63PrecheckWarningsTest {

    private static final String CATALOG = "ramasql_test_t63";
    private Harness h;
    private final StringBuilder evidence = new StringBuilder();

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

    /** Apre {@code prestiti} (modificata da {@code child}) con {@code soci} (modificata da {@code parent}) e crea la FK. */
    private List<Checks.Problem> propose(UnaryOperator<TableDef> child, UnaryOperator<TableDef> parent,
            String onDelete, String screenshot) {
        if (h != null) {
            h.dispose();
        }
        TableDef soci = parent.apply(soci(CATALOG));
        h = open(child.apply(prestitiNuda(CATALOG)), CATALOG, MARIADB, biblioteca(CATALOG).with(soci));
        onEdt(() -> {
            ForeignKeysTab fks = h.editor.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_prestiti_soci");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "soci");       // proposta: id_socio → id
            if (onDelete != null) {
                fks.setCell(0, ForeignKeysTab.ON_DELETE, onDelete);
            }
        });
        @SuppressWarnings("unchecked")
        List<Checks.Problem> mark = (List<Checks.Problem>) fromEdt(
                () -> h.editor.foreignKeysTab().table().getModel().getValueAt(0, ForeignKeysTab.MARK));
        String notes = fromEdt(() -> h.editor.foreignKeysTab().notesText());
        for (Checks.Problem p : mark) {
            assertTrue(notes.contains((p.isError() ? "✘ " : "⚠ ") + p.message()), "visibile nel riquadro: " + notes);
        }
        assertTrue(h.applier.requests.isEmpty() && h.dataCheck.queries.isEmpty(), "nessun contatto col server");
        if (screenshot != null) {
            h.tab(TableEditor.TAB_FOREIGN_KEYS);
            h.screenshot("step6", screenshot);
        }
        evidence.append("== ").append(screenshot == null ? "" : screenshot).append('\n').append(notes).append("\n\n");
        return mark;
    }

    private static List<Code> codes(List<Checks.Problem> problems) {
        List<Code> out = new ArrayList<>();
        problems.forEach(p -> out.add(p.code()));
        return out;
    }

    /** Lo stesso avviso che core calcola sui metadati: l'editor lo mostra tale e quale. */
    private static void sameAsCore(List<Checks.Problem> shown, TableDef child, TableDef parent) {
        TableDef c = child;
        List<PrecheckWarning> core = FkPrecheck.check(c, c.foreignKeys().get(0), parent);
        assertEquals(core.stream().map(PrecheckWarning::message).toList(),
                shown.stream().map(Checks.Problem::message).toList());
    }

    @Test
    void ogniCasoDiFkPrecheckHaIlSuoAvviso() {
        // nessun problema: tipi uguali, id indicizzato
        assertEquals(List.of(), propose(t -> t, t -> t, null, null));

        // INT UNSIGNED (figlia) contro INT (riferita)
        List<Checks.Problem> sign = propose(t -> t, t -> t.changeColumn("id", c -> c.withUnsigned(false)), null,
                "T6.3-int-unsigned");
        assertEquals(List.of(Code.FK_SIGN_MISMATCH), codes(sign));
        assertFalse(sign.get(0).isError(), "avviso, non errore: il server deciderà");
        sameAsCore(sign, fromEdt(() -> h.editor.editedTable()), h.tables.table("soci").orElseThrow());

        // INT contro BIGINT
        List<Checks.Problem> type = propose(t -> t, t -> t.changeColumn("id", c -> c.withType("BIGINT", null)), null,
                "T6.3-int-bigint");
        assertEquals(List.of(Code.FK_TYPE_MISMATCH), codes(type));

        // VARCHAR con collation diverse (FK sulla tessera)
        List<Checks.Problem> collation = propose(
                t -> t.addColumn(ColumnDef.of("tessera", "CHAR", "8").withCharset("utf8mb4", "utf8mb4_bin").asNew()),
                t -> t, null, null);
        assertEquals(List.of(), collation, "la FK proposta è ancora su id_socio");
        onEdt(() -> {
            h.editor.foreignKeysTab().setPair(0, "tessera", "tessera");
        });
        @SuppressWarnings("unchecked")
        List<Checks.Problem> coll = (List<Checks.Problem>) fromEdt(
                () -> h.editor.foreignKeysTab().table().getModel().getValueAt(0, ForeignKeysTab.MARK));
        assertEquals(List.of(Code.FK_COLLATION_MISMATCH), codes(coll));
        assertTrue(fromEdt(() -> h.editor.foreignKeysTab().notesText()).contains(coll.get(0).message()));
        h.tab(TableEditor.TAB_FOREIGN_KEYS);
        h.screenshot("step6", "T6.3-collation");
        evidence.append("== T6.3-collation\n").append(fromEdt(() -> h.editor.foreignKeysTab().notesText()))
                .append("\n\n");

        // colonna riferita senza indice (soci.email)
        List<Checks.Problem> notIndexed = propose(
                t -> t.addColumn(ColumnDef.of("email_socio", "VARCHAR", "120").asNew()), t -> t, null, null);
        onEdt(() -> h.editor.foreignKeysTab().setPair(0, "email_socio", "email"));
        @SuppressWarnings("unchecked")
        List<Checks.Problem> ni = (List<Checks.Problem>) fromEdt(
                () -> h.editor.foreignKeysTab().table().getModel().getValueAt(0, ForeignKeysTab.MARK));
        assertEquals(List.of(), notIndexed);
        assertEquals(List.of(Code.FK_REFERENCED_NOT_INDEXED), codes(ni));
        h.tab(TableEditor.TAB_FOREIGN_KEYS);
        h.screenshot("step6", "T6.3-senza-indice");
        evidence.append("== T6.3-senza-indice\n").append(fromEdt(() -> h.editor.foreignKeysTab().notesText()))
                .append("\n\n");

        // SET NULL su colonna NOT NULL (prestiti.id_socio è NOT NULL)
        List<Checks.Problem> setNull = propose(t -> t, t -> t, "SET NULL", "T6.3-set-null");
        assertEquals(List.of(Code.FK_SET_NULL_ON_NOT_NULL), codes(setNull));

        // tabella riferita MyISAM
        List<Checks.Problem> myisam = propose(t -> t, t -> t.withEngine("MyISAM"), null, "T6.3-riferita-myisam");
        assertEquals(List.of(Code.FK_PARENT_NOT_INNODB), codes(myisam));

        // colonna inesistente / numero di colonne diverso: errori evidenti (✘) che bloccano «Applica»
        propose(t -> t, t -> t, null, null);
        onEdt(() -> h.editor.foreignKeysTab().setPair(0, null, "non_esiste"));
        @SuppressWarnings("unchecked")
        List<Checks.Problem> missing = (List<Checks.Problem>) fromEdt(
                () -> h.editor.foreignKeysTab().table().getModel().getValueAt(0, ForeignKeysTab.MARK));
        assertEquals(List.of(Code.FK_COLUMN_NOT_FOUND), codes(missing));
        assertTrue(missing.get(0).isError());
        onEdt(() -> h.editor.foreignKeysTab().removePair(0));
        @SuppressWarnings("unchecked")
        List<Checks.Problem> count = (List<Checks.Problem>) fromEdt(
                () -> h.editor.foreignKeysTab().table().getModel().getValueAt(0, ForeignKeysTab.MARK));
        assertEquals(List.of(Code.FK_COLUMN_COUNT_MISMATCH), codes(count));
        onEdt(() -> h.editor.apply());
        assertEquals(1, h.prompts.errors.size(), "errore evidente: «Applica» si ferma");
        assertTrue(h.applier.requests.isEmpty());

        writeText("step6", "T6.3", evidence.toString());
    }

    @Test
    void tabellaFigliaMyIsamLaSchedaLoSpiega() {
        // FK_CHILD_NOT_INNODB: su una tabella MyISAM la scheda non propone nemmeno la FK, e dice perché (T6.9)
        TableDef child = prestitiNuda(CATALOG).withEngine("MyISAM");
        h = open(child, CATALOG, MYSQL, biblioteca(CATALOG));
        assertEquals(ForeignKeysTab.CARD_MYISAM, fromEdt(() -> h.editor.foreignKeysTab().visibleCard()));
        assertTrue(fromEdt(() -> h.editor.foreignKeysTab().myisamText()).contains("non supporta le chiavi esterne"));
        TableDef parent = soci(CATALOG);
        List<PrecheckWarning> core = FkPrecheck.check(child,
                it.ramasql.core.metadata.ForeignKeyDef.of("fk", "id_socio", "soci", "id"), parent);
        assertEquals(Code.FK_CHILD_NOT_INNODB, core.get(0).code(), "lo stesso caso visto da core");
        assertTrue(fromEdt(() -> h.editor.foreignKeysTab().myisamText()).contains("MyISAM"));
        assertEquals(List.of(IndexDef.primary("id")), fromEdt(() -> h.editor.editedTable().indexes()));
    }
}
