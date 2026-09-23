/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.JsonNode;

import it.ramasql.core.connection.Session;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableKind;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.core.sqlgen.TableDiff;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * T3.1 — lettura dei metadati di {@code biblioteca} e {@code biblioteca_myisam} su entrambi i server, confrontata con
 * i JSON di riferimento ({@code it-tests/fixtures/*.expected.json}: tabelle, colonne con tipo/NULL/default/AI,
 * indici, FK, engine, viste); poi lo stesso {@link TableDef} letto dai due server.
 */
@Tag("step3")
@Tag("it")
class T31MetadatiBibliotecaTest {

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t31_metadatiUgualiAlRiferimento(ItServers server) throws Exception {
        List<TestCatalog> catalogs = new ArrayList<>();
        StringBuilder evidence = new StringBuilder("T3.1 — metadati letti da " + server.label() + "\n");
        try {
            TestCatalog inno = TestCatalog.create(server, "t31_biblioteca");
            catalogs.add(inno);
            inno.runScript(Step3Support.FIXTURE_INNODB);
            TestCatalog myisam = TestCatalog.create(server, "t31_biblioteca_myisam");
            catalogs.add(myisam);
            myisam.runScript(Step3Support.FIXTURE_MYISAM);

            try (Session session = Step3Support.open(server, "")) {
                evidence.append("server: ").append(session.serverInfo().versionText()).append("\n\n");
                MetadataReader reader = MetadataReader.of(session);
                check(reader, inno.name(), "/fixtures/biblioteca.expected.json", session, evidence);
                check(reader, myisam.name(), "/fixtures/biblioteca_myisam.expected.json", session, evidence);

                // l'elenco veloce distingue tabelle e viste, con l'engine per le icone del navigatore
                List<TableSummary> list = reader.tables(inno.name());
                assertEquals(List.of("autori", "editori", "libri", "libri_autori", "prestiti", "soci",
                        "v_libri_editori", "v_prestiti_aperti"), list.stream().map(TableSummary::name).toList());
                assertEquals(2, list.stream().filter(t -> t.kind() == TableKind.VIEW).count());
                assertTrue(reader.tables(myisam.name()).stream().filter(t -> !t.isView())
                        .allMatch(TableSummary::isMyIsam));
                assertTrue(reader.catalog(inno.name()).isPresent());
            }
        } finally {
            TestResults.write("step3", "T3.1-" + server.name().toLowerCase() + ".txt", evidence.toString());
            Step3Support.closeAll(catalogs);
        }
    }

    private static void check(MetadataReader reader, String catalog, String expectedResource, Session session,
            StringBuilder evidence) throws Exception {
        JsonNode expected = Step3Support.expected(expectedResource);
        JsonNode actual = Step3Support.snapshot(reader, catalog);
        evidence.append("== ").append(expectedResource).append(" (catalogo ").append(catalog).append(")\n")
                .append(Step3Support.pretty(actual)).append('\n');
        assertEquals(Step3Support.pretty(expected), Step3Support.pretty(actual),
                "metadati letti diversi dal riferimento " + expectedResource);
        evidence.append("esito: UGUALE al riferimento\n");
        // lo stato letto è il punto di partenza dell'editor di tabelle: nessuna differenza con sé stesso
        for (TableDef t : reader.allTables(catalog)) {
            assertEquals(List.of(), TableDiff.diff(t, t, session.serverInfo()), t.name());
        }
        evidence.append("TableDiff.diff(letto, letto): 0 istruzioni per ciascuna tabella\n\n");
    }

    /** Stessa fixture, due server: gli stessi {@link TableDef} (a parte il nome del catalogo, che è casuale). */
    @Test
    void t31_stessiTableDefSuiDueServer() throws Exception {
        Map<ItServers, List<TableDef>> read = new EnumMap<>(ItServers.class);
        StringBuilder evidence = new StringBuilder("T3.1 — confronto dei TableDef letti da MariaDB e MySQL\n");
        for (String fixture : List.of(Step3Support.FIXTURE_INNODB, Step3Support.FIXTURE_MYISAM)) {
            for (ItServers server : ItServers.values()) {
                List<TestCatalog> catalogs = new ArrayList<>();
                try {
                    TestCatalog c = TestCatalog.create(server, "t31_confronto");
                    catalogs.add(c);
                    c.runScript(fixture);
                    try (Session session = Step3Support.open(server, "")) {
                        read.put(server, MetadataReader.of(session).allTables(c.name()).stream()
                                .map(t -> t.withCatalog(null)).toList());
                    }
                } finally {
                    Step3Support.closeAll(catalogs);
                }
            }
            List<TableDef> maria = read.get(ItServers.MARIADB);
            List<TableDef> mysql = read.get(ItServers.MYSQL);
            assertEquals(6, maria.size());
            for (int i = 0; i < maria.size(); i++) {
                assertEquals(maria.get(i), mysql.get(i), "tabella " + maria.get(i).name() + " di " + fixture);
                assertEquals(List.of(), TableDiff.diff(maria.get(i), mysql.get(i),
                        it.ramasql.core.connection.ServerInfo.parse("8.0.40")), maria.get(i).name());
            }
            evidence.append(fixture).append(": ").append(maria.size())
                    .append(" tabelle, TableDef identici (equals) sui due server\n");
        }
        TestResults.write("step3", "T3.1-confronto-server.txt", evidence.toString());
    }
}
