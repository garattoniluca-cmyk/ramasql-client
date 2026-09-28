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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/**
 * Le correzioni della revisione dello Step 11 nel modello: server di provenienza nel file, relazioni logiche rotte o
 * diventate chiavi esterne vere dopo «Aggiorna dal database», suggeritore (chiave condivisa, autoriferimento, chiavi
 * dal nome generico), celle della disposizione su misura.
 */
@Tag("step11")
class T11RevisioneModelTest {

    @TempDir
    Path dir;

    private static ColumnDef id() {
        return ColumnDef.of("id", "INT").withNullable(false);
    }

    @Test
    void ilServerDiProvenienzaStaNelFile() throws Exception {
        ErModel m = ReverseEngineer.build("biblioteca", ModelFixtures.biblioteca(true), true).withServer("127.0.0.1:3306");
        Path f = dir.resolve("b.rsqlmodel");
        ModelFile.write(f, m);
        ErModel back = ModelFile.read(f);
        assertEquals("127.0.0.1:3306", back.server());
        assertEquals(m, back);
        assertTrue(Files.readString(f).contains("\"server\" : \"127.0.0.1:3306\""));
        assertFalse(Files.exists(dir.resolve("b.rsqlmodel.tmp")), "nessun file temporaneo rimasto");
        // un file senza server (modelli vecchi) si apre, con il server sconosciuto
        String old = Files.readString(f).replace("\"server\" : \"127.0.0.1:3306\",", "");
        assertEquals("", ModelFile.fromJson(old, "vecchio.rsqlmodel").server());
    }

    @Test
    void relazioniLogicheRotteEDiventateChiaviVere() {
        ErModel current = ReverseEngineer.build("bib", ModelFixtures.biblioteca(false), true);
        for (RelationshipSuggester.Suggestion s : RelationshipSuggester.suggest(current)) {
            current = current.addLogical(s.relationship());
        }
        assertEquals(5, current.logical().size());
        // sul server: libri.id_editore sparita; prestiti.id_socio diventata una chiave esterna vera
        List<TableDef> now = new ArrayList<>();
        for (TableDef t : ModelFixtures.biblioteca(false)) {
            if (t.name().equals("libri")) {
                t = t.withColumns(t.columns().stream().filter(c -> !c.name().equals("id_editore")).toList());
            }
            if (t.name().equals("prestiti")) {
                t = t.withForeignKeys(List.of(ForeignKeyDef.of("fk_prestiti_soci", "id_socio", "soci", "id")));
            }
            now.add(t);
        }
        ModelRefresh.Result r = ModelRefresh.refresh(current, ReverseEngineer.build("bib", now, true),
                AutoLayout.ESTIMATE);
        assertEquals(List.of("libri.id_editore → editori.id"), r.broken());
        assertEquals(List.of("prestiti.id_socio → soci.id"), r.promoted());
        assertEquals(4, r.model().logical().size(), "la promossa non resta anche come logica (due linee sovrapposte)");
        assertEquals(1, r.model().physical().size());
        assertEquals(List.of("libri.id_editore"), ModelRefresh.brokenColumns(r.model(), r.model().logical().stream()
                .filter(x -> x.fromTable().equals("libri")).findFirst().orElseThrow()));
    }

    @Test
    void chiaveCondivisaEAutoriferimento() {
        TableDef libri = TableDef.of("x", "libri").withColumns(List.of(id(), ColumnDef.of("titolo", "VARCHAR", "80")))
                .withIndexes(List.of(IndexDef.primary("id")));
        TableDef dettagli = TableDef.of("x", "dettagli_libro").withColumns(List.of(
                ColumnDef.of("id_libro", "INT").withNullable(false), ColumnDef.of("pagine", "INT")))
                .withIndexes(List.of(IndexDef.primary("id_libro")));
        TableDef dipendenti = TableDef.of("x", "dipendenti").withColumns(List.of(id(),
                ColumnDef.of("nome", "VARCHAR", "40"), ColumnDef.of("id_responsabile", "INT")))
                .withIndexes(List.of(IndexDef.primary("id")));
        ErModel m = ReverseEngineer.build("x", List.of(libri, dettagli, dipendenti), true);
        List<RelationshipSuggester.Suggestion> s = RelationshipSuggester.suggest(m);
        Set<String> got = new TreeSet<>(s.stream().map(x -> x.relationship().describe()).toList());
        assertEquals(Set.of("dettagli_libro.id_libro → libri.id", "dipendenti.id_responsabile → dipendenti.id"), got);
        Relationship shared = s.stream().filter(x -> x.relationship().fromTable().equals("dettagli_libro")).findFirst()
                .orElseThrow().relationship();
        assertEquals(Relationship.Cardinality.ONE_TO_ONE, shared.cardinality(), "chiave condivisa: 1:1");
    }

    @Test
    void chiaveDalNomeGenericoInPiuTabelleNessunaProposta() {
        TableDef classi = TableDef.of("x", "classi").withColumns(List.of(ColumnDef.of("codice", "CHAR", "4")
                .withNullable(false))).withIndexes(List.of(IndexDef.primary("codice")));
        TableDef aule = TableDef.of("x", "aule").withColumns(List.of(ColumnDef.of("codice", "CHAR", "4")
                .withNullable(false))).withIndexes(List.of(IndexDef.primary("codice")));
        TableDef orario = TableDef.of("x", "orario").withColumns(List.of(id(), ColumnDef.of("codice", "CHAR", "4")))
                .withIndexes(List.of(IndexDef.primary("id")));
        ErModel m = ReverseEngineer.build("x", List.of(classi, aule, orario), true);
        assertEquals(List.of(), RelationshipSuggester.suggest(m), "«codice» di classi o di aule? Non si indovina");
        ErModel single = ReverseEngineer.build("x", List.of(classi, orario), true);
        assertEquals(List.of("orario.codice → classi.codice"), RelationshipSuggester.suggest(single).stream()
                .map(x -> x.relationship().describe()).toList(), "una sola tabella con quella chiave: sì");
    }

    @Test
    void celleSuMisuraUnaTabellaEnormeNonIngrandisceTutte() {
        List<TableDef> tables = new ArrayList<>(LayoutRefreshTest.generated(12));
        List<ColumnDef> many = new ArrayList<>(List.of(id()));
        for (int i = 0; i < 40; i++) {
            many.add(ColumnDef.of("campo_" + i, "VARCHAR", "20"));
        }
        tables.add(TableDef.of("x", "enorme").withColumns(many).withIndexes(List.of(IndexDef.primary("id"))));
        ErModel m = AutoLayout.layout(ReverseEngineer.build("x", tables, true), AutoLayout.ESTIMATE);
        assertFalse(AutoLayout.overlaps(m, AutoLayout.ESTIMATE));
        double big = AutoLayout.ESTIMATE.of(m.entity("enorme").orElseThrow()).height();
        // con celle tutte uguali ogni riga sarebbe alta quanto la tabella enorme: le righe senza di lei sono basse
        List<Double> ys = m.entities().stream().map(ErModel.Entity::y).distinct().sorted().toList();
        double minStep = Double.MAX_VALUE;
        for (int i = 1; i < ys.size(); i++) {
            minStep = Math.min(minStep, ys.get(i) - ys.get(i - 1));
        }
        assertTrue(ys.size() > 1 && minStep < big, "passo minimo fra le righe " + minStep + " < " + big);
    }
}
