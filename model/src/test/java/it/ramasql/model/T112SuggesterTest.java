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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/**
 * T11.2 — suggeritore: {@code id_editore}→{@code editori.id}, {@code editore_id}, {@code editoreId},
 * {@code id_libro}/{@code id_autore} in tabella ponte, singolare/plurale ({@code socio}/{@code soci},
 * {@code libro}/{@code libri}, {@code author}/{@code authors}), tipi incompatibili, falsi amici ({@code id_esterno}
 * senza tabella): proposte attese con punteggio, nessuna proposta per i falsi amici.
 */
@Tag("step11")
class T112SuggesterTest {

    private static TableDef table(String name, List<ColumnDef> columns) {
        return TableDef.of("x", name).withColumns(columns).withIndexes(List.of(IndexDef.primary(columns.get(0).name())));
    }

    private static ColumnDef intCol(String name) {
        return ColumnDef.of(name, "INT").withUnsigned(true).withNullable(false);
    }

    private static List<String> suggestions(List<TableDef> tables) {
        List<String> out = new ArrayList<>();
        for (RelationshipSuggester.Suggestion s : RelationshipSuggester.suggest(ReverseEngineer.build("x", tables, true))) {
            out.add(s.relationship().describe());
        }
        return out;
    }

    private static final TableDef EDITORI = table("editori", List.of(intCol("id"), ColumnDef.of("nome", "VARCHAR", "80")));

    @Test
    void idPrefisso() {
        assertEquals(List.of("libri.id_editore → editori.id"),
                suggestions(List.of(EDITORI, table("libri", List.of(intCol("id"), intCol("id_editore"))))));
    }

    @Test
    void idSuffisso() {
        assertEquals(List.of("libri.editore_id → editori.id"),
                suggestions(List.of(EDITORI, table("libri", List.of(intCol("id"), intCol("editore_id"))))));
    }

    @Test
    void camelCase() {
        assertEquals(List.of("libri.editoreId → editori.id"),
                suggestions(List.of(EDITORI, table("libri", List.of(intCol("id"), intCol("editoreId"))))));
        assertEquals(List.of("libri.idEditore → editori.id"),
                suggestions(List.of(EDITORI, table("libri", List.of(intCol("id"), intCol("idEditore"))))));
    }

    @Test
    void tabellaPonte() {
        TableDef libri = table("libri", List.of(intCol("id")));
        TableDef autori = table("autori", List.of(intCol("id")));
        TableDef ponte = TableDef.of("x", "libri_autori").withColumns(List.of(intCol("id_libro"), intCol("id_autore")))
                .withIndexes(List.of(IndexDef.primary("id_libro", "id_autore")));
        List<String> s = suggestions(List.of(libri, autori, ponte));
        assertEquals(Set.of("libri_autori.id_libro → libri.id", "libri_autori.id_autore → autori.id"), Set.copyOf(s));
        // accettate le due proposte, il modello riconosce la tabella ponte (N:M indicativa)
        ErModel m = ReverseEngineer.build("x", List.of(libri, autori, ponte), true);
        for (RelationshipSuggester.Suggestion x : RelationshipSuggester.suggest(m)) {
            m = m.addLogical(x.relationship());
        }
        assertEquals(1, ReverseEngineer.bridges(m).size());
        assertEquals("libri_autori", ReverseEngineer.bridges(m).get(0).table());
    }

    @Test
    void singolarePlurale() {
        TableDef soci = table("soci", List.of(intCol("id")));
        TableDef libri = table("libri", List.of(intCol("id")));
        TableDef prestiti = table("prestiti", List.of(intCol("id"), intCol("id_socio"), intCol("id_libro")));
        assertEquals(Set.of("prestiti.id_socio → soci.id", "prestiti.id_libro → libri.id"),
                Set.copyOf(suggestions(List.of(soci, libri, prestiti))));
        TableDef authors = table("authors", List.of(intCol("id")));
        TableDef books = table("books", List.of(intCol("id"), intCol("author_id")));
        assertEquals(List.of("books.author_id → authors.id"), suggestions(List.of(authors, books)));
        TableDef categories = table("categories", List.of(intCol("id")));
        TableDef items = table("items", List.of(intCol("id"), intCol("category_id")));
        assertEquals(List.of("items.category_id → categories.id"), suggestions(List.of(categories, items)));
        TableDef tessere = table("tessere", List.of(intCol("id")));
        TableDef iscr = table("iscrizioni", List.of(intCol("id"), intCol("id_tessera")));
        assertEquals(List.of("iscrizioni.id_tessera → tessere.id"), suggestions(List.of(tessere, iscr)));
    }

    @Test
    void punteggioNomeUgualeSopraPlurale() {
        TableDef editore = table("editore", List.of(intCol("id")));
        TableDef libri = table("libri", List.of(intCol("id"), intCol("id_editore")));
        RelationshipSuggester.Suggestion s = RelationshipSuggester.suggest(ReverseEngineer.build("x",
                List.of(editore, libri), true)).get(0);
        assertEquals(100, s.score(), "stesso nome e stesso tipo");
        TableDef editori = table("editori", List.of(intCol("id")));
        RelationshipSuggester.Suggestion p = RelationshipSuggester.suggest(ReverseEngineer.build("x",
                List.of(editori, libri), true)).get(0);
        assertEquals(90, p.score(), "plurale e stesso tipo");
        assertTrue(p.reason().contains("id_editore") && p.reason().contains("editori"), p.reason());
    }

    @Test
    void tipiIncompatibiliNessunaProposta() {
        TableDef libri = table("libri", List.of(intCol("id"), ColumnDef.of("id_editore", "VARCHAR", "20")));
        assertEquals(List.of(), suggestions(List.of(EDITORI, libri)));
    }

    @Test
    void interiDiSegnoDiversoCompatibiliConPunteggioMinore() {
        TableDef libri = table("libri", List.of(intCol("id"), ColumnDef.of("id_editore", "BIGINT")));
        List<RelationshipSuggester.Suggestion> s = RelationshipSuggester.suggest(
                ReverseEngineer.build("x", List.of(EDITORI, libri), true));
        assertEquals(1, s.size());
        assertEquals(80, s.get(0).score());
    }

    @Test
    void falsiAmiciNessunaProposta() {
        TableDef ordini = table("ordini", List.of(intCol("id"), intCol("id_esterno"), intCol("codice_id"),
                intCol("idea"), intCol("valid")));
        assertEquals(List.of(), suggestions(List.of(ordini, EDITORI)));
    }

    @Test
    void chiavePrimariaComposteNonSiPropone() {
        TableDef ponte = TableDef.of("x", "iscrizioni").withColumns(List.of(intCol("a"), intCol("b")))
                .withIndexes(List.of(IndexDef.primary("a", "b")));
        TableDef figli = table("figli", List.of(intCol("id"), intCol("id_iscrizione")));
        assertEquals(List.of(), suggestions(List.of(ponte, figli)), "una chiave di due colonne non si indovina");
    }

    @Test
    void stessoNomeDellaChiaveAltrui() {
        TableDef studenti = TableDef.of("x", "studenti").withColumns(List.of(ColumnDef.of("codice_fiscale", "CHAR", "16")
                .withNullable(false), ColumnDef.of("nome", "VARCHAR", "40")))
                .withIndexes(List.of(IndexDef.primary("codice_fiscale")));
        TableDef iscr = table("iscrizioni", List.of(intCol("id"), ColumnDef.of("codice_fiscale", "CHAR", "16")));
        List<RelationshipSuggester.Suggestion> s = RelationshipSuggester.suggest(
                ReverseEngineer.build("x", List.of(studenti, iscr), true));
        assertEquals(1, s.size());
        assertEquals("iscrizioni.codice_fiscale → studenti.codice_fiscale", s.get(0).relationship().describe());
        assertEquals(70, s.get(0).score());
    }

    @Test
    void giaInRelazioneNonSiRipropone() {
        List<TableDef> t = ModelFixtures.biblioteca(true);
        assertEquals(List.of(), suggestions(t), "con le chiavi esterne tutte le relazioni ci sono già");
    }

    @Test
    void proposteLogicheNonObbligatorieSeAnnullabili() {
        TableDef libri = table("libri", List.of(intCol("id"), ColumnDef.of("id_editore", "INT").withUnsigned(true)));
        Relationship r = RelationshipSuggester.suggest(ReverseEngineer.build("x", List.of(EDITORI, libri), true))
                .get(0).relationship();
        assertEquals(Relationship.Kind.LOGICAL, r.kind());
        assertEquals(false, r.mandatory());
    }

    @Test
    void formePlurali() {
        Set<String> f = new TreeSet<>(RelationshipSuggester.forms("socio"));
        assertTrue(f.contains("soci"));
        assertTrue(RelationshipSuggester.forms("libro").contains("libri"));
        assertTrue(RelationshipSuggester.forms("editori").contains("editore"));
        assertTrue(RelationshipSuggester.forms("biblioteca").contains("biblioteche"));
        assertEquals(0, RelationshipSuggester.nameMatch("esterno", "editori"));
    }
}
