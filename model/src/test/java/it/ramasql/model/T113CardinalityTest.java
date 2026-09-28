/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/**
 * T11.3 — cardinalità dai metadati: FK su colonna UNIQUE → 1:1; FK NOT NULL → obbligatoria; tabella ponte → N:M
 * indicata. Più la retroingegneria della biblioteca senza server (entità, relazioni fisiche, colonne).
 */
@Tag("step11")
class T113CardinalityTest {

    private static final ErModel BIB = ReverseEngineer.build("bib", ModelFixtures.biblioteca(true), true);

    private static Relationship rel(ErModel m, String from, String col) {
        return m.relationships().stream()
                .filter(r -> r.fromTable().equals(from) && r.fromColumns().equals(List.of(col))).findFirst().orElseThrow();
    }

    @Test
    void fkSuColonnaUniqueEUnoAUno() {
        TableDef persone = TableDef.of("x", "persone").withColumns(List.of(ColumnDef.of("id", "INT").withNullable(false)))
                .withIndexes(List.of(IndexDef.primary("id")));
        TableDef passaporti = TableDef.of("x", "passaporti").withColumns(List.of(
                ColumnDef.of("numero", "CHAR", "9").withNullable(false), ColumnDef.of("id_persona", "INT").withNullable(false)))
                .withIndexes(List.of(IndexDef.primary("numero"), IndexDef.unique("uq_persona", "id_persona")))
                .withForeignKeys(List.of(ForeignKeyDef.of("fk_pass_persone", "id_persona", "persone", "id")));
        ErModel m = ReverseEngineer.build("x", List.of(persone, passaporti), true);
        assertEquals(Relationship.Cardinality.ONE_TO_ONE, m.relationships().get(0).cardinality());
        assertTrue(m.relationships().get(0).mandatory());
    }

    @Test
    void fkSenzaUniqueEUnoAMolti() {
        assertEquals(Relationship.Cardinality.ONE_TO_MANY, rel(BIB, "prestiti", "id_socio").cardinality());
    }

    @Test
    void fkNotNullObbligatoriaAnnullabileFacoltativa() {
        assertTrue(rel(BIB, "prestiti", "id_socio").mandatory(), "id_socio NOT NULL");
        assertFalse(rel(BIB, "libri", "id_editore").mandatory(), "un libro può non avere editore");
    }

    @Test
    void tabellaPonteNM() {
        List<ReverseEngineer.Bridge> b = ReverseEngineer.bridges(BIB);
        assertEquals(1, b.size());
        assertEquals("libri_autori", b.get(0).table());
        assertEquals(java.util.Set.of("libri", "autori"), java.util.Set.of(b.get(0).left(), b.get(0).right()));
    }

    @Test
    void prestitiNonEPonte() {
        // prestiti riferisce libri e soci, ma la sua chiave è id: non è una tabella ponte
        assertTrue(ReverseEngineer.bridges(BIB).stream().noneMatch(x -> x.table().equals("prestiti")));
    }

    @Test
    void retroingegneriaBiblioteca() {
        assertEquals(6, BIB.entities().size());
        assertEquals(5, BIB.physical().size());
        assertEquals(0, BIB.logical().size());
        ErModel.Entity libri = BIB.entity("libri").orElseThrow();
        assertEquals(List.of("id"), libri.primaryKey());
        assertEquals("INT UNSIGNED", libri.column("id").orElseThrow().type());
        assertTrue(libri.column("isbn").orElseThrow().unique());
        assertEquals("fk_libri_editori", rel(BIB, "libri", "id_editore").label());
        assertEquals("InnoDB", libri.engine());
    }

    @Test
    void senzaChiaviEsterneNessunaRelazioneFisica() {
        ErModel m = ReverseEngineer.build("bib", ModelFixtures.biblioteca(false), true);
        assertEquals(6, m.entities().size());
        assertEquals(0, m.physical().size());
        assertEquals(List.of(), ReverseEngineer.bridges(m), "senza relazioni non si riconosce il ponte");
    }

    @Test
    void chiaveVersoTabellaFuoriDalModelloIgnorata() {
        List<TableDef> soloPrestiti = List.of(ModelFixtures.biblioteca(true).get(5));
        ErModel m = ReverseEngineer.build("bib", soloPrestiti, false);
        assertEquals(0, m.relationships().size());
    }
}
