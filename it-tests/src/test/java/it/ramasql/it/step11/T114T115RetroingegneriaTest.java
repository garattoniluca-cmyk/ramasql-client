/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.model.ErModel;
import it.ramasql.model.Relationship;
import it.ramasql.model.RelationshipSuggester;
import it.ramasql.model.ReverseEngineer;

/**
 * Retroingegneria contro i server veri, con il lettore dei metadati del client:
 * <ul>
 *   <li><b>T11.4</b>: {@code biblioteca} → 6 entità e le 5 relazioni fisiche corrette (colonne, tabelle, cardinalità,
 *       obbligatorietà), la tabella ponte riconosciuta;</li>
 *   <li><b>T11.5</b>: {@code biblioteca_myisam} (nessuna chiave esterna) → 0 relazioni fisiche; il suggeritore propone
 *       <b>le 5 relazioni attese</b> e nessuna sbagliata.</li>
 * </ul>
 */
@Tag("step11")
@Tag("it")
class T114T115RetroingegneriaTest {

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");

    /** Le 5 relazioni della biblioteca. */
    static final Set<String> EXPECTED = Set.of("libri.id_editore → editori.id", "libri_autori.id_libro → libri.id",
            "libri_autori.id_autore → autori.id", "prestiti.id_libro → libri.id", "prestiti.id_socio → soci.id");

    static Session open(ItServers server) throws Exception {
        Matcher m = HOST_PORT.matcher(server.url());
        assertTrue(m.matches(), server.url());
        String value = System.getenv("RAMASQL_IT_" + server.name() + "_PASSWORD");
        if (value == null) {
            throw new AssertionError("Manca RAMASQL_IT_" + server.name() + "_PASSWORD: i test non si saltano.");
        }
        return Session.open(ConnectionProfile.create("Test " + server.label(), m.group(1), Integer.parseInt(m.group(2)),
                server.user(), "", ""), value.toCharArray());
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t114_bibliotecaSeiEntitaCinqueRelazioniFisiche(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t114"); Session s = open(server)) {
            cat.runScript("/fixtures/biblioteca.sql");
            ErModel m = ReverseEngineer.fromCatalog(MetadataReader.of(s), cat.name(), List.of());
            assertEquals(6, m.entities().size(), "le viste non sono entità");
            assertEquals(Set.of("autori", "editori", "libri", "libri_autori", "prestiti", "soci"),
                    new TreeSet<>(m.entities().stream().map(ErModel.Entity::table).toList()));
            Set<String> physical = new TreeSet<>(m.physical().stream().map(Relationship::describe).toList());
            assertEquals(EXPECTED, physical);
            assertEquals(0, m.logical().size());
            Relationship editore = m.physical().stream().filter(r -> r.fromTable().equals("libri")).findFirst()
                    .orElseThrow();
            assertEquals(Relationship.Cardinality.ONE_TO_MANY, editore.cardinality());
            assertEquals(false, editore.mandatory(), "id_editore annullabile: facoltativa");
            assertEquals("fk_libri_editori", editore.label());
            assertTrue(m.physical().stream().filter(r -> r.fromTable().equals("prestiti")).allMatch(Relationship::mandatory));
            assertEquals(List.of("libri_autori"), ReverseEngineer.bridges(m).stream().map(ReverseEngineer.Bridge::table)
                    .toList());
            assertEquals(List.of("id"), m.entity("soci").orElseThrow().primaryKey());
            assertTrue(m.entity("soci").orElseThrow().column("tessera").orElseThrow().unique());
            TestResults.write("step11", "T11.4-" + server.name().toLowerCase(Locale.ROOT) + ".txt", "T11.4 —"
                    + " retroingegneria di biblioteca (" + server.label() + ")\nEntità: " + m.entities().stream()
                            .map(e -> e.table() + "(" + e.columns().size() + " colonne, " + e.engine() + ")").toList()
                    + "\nRelazioni fisiche:\n  " + String.join("\n  ", m.physical().stream().map(r -> r.describe() + " · "
                            + r.cardinality() + " · " + (r.mandatory() ? "obbligatoria" : "facoltativa") + " · "
                            + r.label()).toList())
                    + "\nTabella ponte (N:M indicativa): libri_autori\nEsito: OK\n");
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t115_myisamSenzaChiaviEsterneMaConISuggerimenti(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t115"); Session s = open(server)) {
            cat.runScript("/fixtures/biblioteca_myisam.sql");
            ErModel m = ReverseEngineer.fromCatalog(MetadataReader.of(s), cat.name(), List.of());
            assertEquals(6, m.entities().size());
            assertEquals(0, m.physical().size(), "MyISAM: nessuna chiave esterna");
            assertTrue(m.entities().stream().allMatch(e -> "MyISAM".equalsIgnoreCase(e.engine())));
            List<RelationshipSuggester.Suggestion> suggestions = RelationshipSuggester.suggest(m);
            Set<String> proposed = new TreeSet<>(suggestions.stream().map(x -> x.relationship().describe()).toList());
            assertEquals(EXPECTED, proposed, "5/5 relazioni attese, 0 proposte errate");
            assertTrue(suggestions.stream().allMatch(x -> x.relationship().kind() == Relationship.Kind.LOGICAL));
            assertTrue(suggestions.stream().allMatch(x -> x.score() >= 80));
            TestResults.write("step11", "T11.5-" + server.name().toLowerCase(Locale.ROOT) + ".txt", "T11.5 —"
                    + " biblioteca_myisam (" + server.label() + "): 6 entità MyISAM, 0 relazioni fisiche\nProposte del"
                    + " suggeritore (5/5 attese, 0 errate):\n  " + String.join("\n  ", suggestions.stream()
                            .map(x -> x.relationship().describe() + " · " + x.score() + "% · " + x.reason()).toList())
                    + "\nEsito: OK\n");
        }
    }
}
