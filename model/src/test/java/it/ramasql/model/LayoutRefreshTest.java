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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/** Disposizione automatica (entità non sovrapposte, le più collegate al centro) e «Aggiorna dal database». */
@Tag("step11")
class LayoutRefreshTest {

    /** Un catalogo di {@code n} tabelle con chiavi esterne a stella e a catena. */
    static List<TableDef> generated(int n) {
        List<TableDef> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<ColumnDef> cols = new ArrayList<>(List.of(ColumnDef.of("id", "INT").withNullable(false),
                    ColumnDef.of("descrizione_della_riga", "VARCHAR", "100")));
            List<ForeignKeyDef> fks = new ArrayList<>();
            if (i > 0) {
                cols.add(ColumnDef.of("id_centro", "INT"));
                fks.add(ForeignKeyDef.of("fk_" + i + "_centro", "id_centro", "t00", "id"));
            }
            if (i > 1) {
                cols.add(ColumnDef.of("id_prec", "INT"));
                fks.add(ForeignKeyDef.of("fk_" + i + "_prec", "id_prec", String.format("t%02d", i - 1), "id"));
            }
            out.add(TableDef.of("x", String.format("t%02d", i)).withColumns(cols)
                    .withIndexes(List.of(IndexDef.primary("id"))).withForeignKeys(fks));
        }
        return out;
    }

    @Test
    void trentaTabelleNonSovrapposteELaPiuRiferitaAlCentro() {
        ErModel m = AutoLayout.layout(ReverseEngineer.build("x", generated(30), true), AutoLayout.ESTIMATE);
        assertFalse(AutoLayout.overlaps(m, AutoLayout.ESTIMATE));
        double cx = m.entities().stream().mapToDouble(ErModel.Entity::x).average().orElseThrow();
        double cy = m.entities().stream().mapToDouble(ErModel.Entity::y).average().orElseThrow();
        ErModel.Entity centro = m.entity("t00").orElseThrow();
        double dCentro = Math.hypot(centro.x() - cx, centro.y() - cy);
        long closer = m.entities().stream().filter(e -> Math.hypot(e.x() - cx, e.y() - cy) < dCentro - 1).count();
        assertTrue(closer <= 3, "la tabella riferita da tutte sta fra le più centrali (più vicine: " + closer + ")");
    }

    @Test
    void centoEntitaNonSovrapposte() {
        ErModel m = AutoLayout.layout(ReverseEngineer.build("x", generated(100), true), AutoLayout.ESTIMATE);
        assertFalse(AutoLayout.overlaps(m, AutoLayout.ESTIMATE));
        assertTrue(m.entities().stream().allMatch(e -> e.x() >= 0 && e.y() >= 0));
    }

    @Test
    void aggiornaConservaPosizioniERelazioniLogicheESegnaLeMancanti() {
        ErModel m = AutoLayout.layout(ReverseEngineer.build("bib", ModelFixtures.biblioteca(false), true),
                AutoLayout.ESTIMATE);
        for (RelationshipSuggester.Suggestion s : RelationshipSuggester.suggest(m)) {
            m = m.addLogical(s.relationship());
        }
        m = m.changeEntity("soci", e -> e.at(900, 700));
        assertEquals(5, m.logical().size());
        // sul server: soci ha una colonna in più, prestiti non c'è più
        List<TableDef> server = new ArrayList<>(ModelFixtures.biblioteca(false));
        server.removeIf(t -> t.name().equals("prestiti"));
        server.replaceAll(t -> t.name().equals("soci") ? t.addColumn(ColumnDef.of("telefono", "VARCHAR", "20")) : t);
        server.add(TableDef.of("bib", "collane").withColumns(List.of(ColumnDef.of("id", "INT").withNullable(false)))
                .withIndexes(List.of(IndexDef.primary("id"))));
        ModelRefresh.Result r = ModelRefresh.refresh(m, ReverseEngineer.build("bib", server, true), AutoLayout.ESTIMATE);
        ErModel after = r.model();
        assertEquals(List.of("prestiti"), r.missing());
        assertEquals(List.of("collane"), r.added());
        assertEquals(List.of("soci"), r.changed());
        assertTrue(after.entity("soci").orElseThrow().column("telefono").isPresent());
        assertEquals(900, after.entity("soci").orElseThrow().x(), "posizione conservata");
        assertTrue(after.entity("prestiti").orElseThrow().missing());
        assertEquals(m.entity("prestiti").orElseThrow().x(), after.entity("prestiti").orElseThrow().x());
        assertEquals(5, after.logical().size(), "relazioni logiche conservate, anche quelle verso prestiti");
        assertFalse(AutoLayout.overlaps(after, AutoLayout.ESTIMATE), "la tabella nuova sta in uno spazio libero");
    }

    @Test
    void aggiornaModelloParzialeNonAggiungeTabelle() {
        ErModel m = ReverseEngineer.build("bib", ModelFixtures.biblioteca(true).subList(0, 2), false);
        ModelRefresh.Result r = ModelRefresh.refresh(m, ReverseEngineer.build("bib", ModelFixtures.biblioteca(true), true),
                AutoLayout.ESTIMATE);
        assertEquals(List.of(), r.added());
        assertEquals(2, r.model().entities().size());
    }

    @Test
    void relazioniFisicheRifatteDalServer() {
        ErModel m = ReverseEngineer.build("bib", ModelFixtures.biblioteca(true), true);
        List<TableDef> senzaUna = new ArrayList<>(ModelFixtures.biblioteca(true));
        senzaUna.replaceAll(t -> t.name().equals("libri") ? t.withForeignKeys(List.of()) : t);
        ModelRefresh.Result r = ModelRefresh.refresh(m, ReverseEngineer.build("bib", senzaUna, true), AutoLayout.ESTIMATE);
        assertEquals(4, r.model().physical().size(), "la chiave esterna tolta sul server sparisce dal modello");
    }
}
