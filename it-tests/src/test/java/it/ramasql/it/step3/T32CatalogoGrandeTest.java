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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.Session;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/** T3.2 — catalogo generato con 500 tabelle: la lettura dell'elenco (navigatore) dura meno di 2 secondi. */
@Tag("step3")
@Tag("it")
class T32CatalogoGrandeTest {

    private static final int TABLES = 500;
    private static final long LIMIT_MS = 2000;

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t32_elencoDi500TabelleInMenoDi2Secondi(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t32_grande")) {
            // generazione: 50 istruzioni per viaggio (allowMultiQueries solo su questa connessione di test)
            long genStart = System.nanoTime();
            Properties multi = new Properties();
            multi.setProperty("allowMultiQueries", "true");
            try (Statement st = cat.newConnection(multi).createStatement()) {
                StringBuilder batch = new StringBuilder();
                for (int i = 1; i <= TABLES; i++) {
                    batch.append(String.format(Locale.ROOT,
                            "CREATE TABLE t%03d (id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                            + " nome VARCHAR(60) NOT NULL, creato_il DATETIME NULL, KEY ix_nome (nome));", i));
                    if (i % 50 == 0) {
                        st.execute(batch.toString());
                        while (st.getMoreResults() || st.getUpdateCount() != -1) {
                            // consuma i risultati delle istruzioni multiple
                        }
                        batch.setLength(0);
                    }
                }
            }
            long genMs = (System.nanoTime() - genStart) / 1_000_000;

            try (Session session = Step3Support.open(server, "")) {
                MetadataReader reader = MetadataReader.of(session);   // lettore nuovo: niente cache
                long start = System.nanoTime();
                List<TableSummary> list = reader.tables(cat.name());
                long ms = (System.nanoTime() - start) / 1_000_000;

                assertEquals(TABLES, list.size());
                assertEquals("t001", list.get(0).name());
                assertEquals("t500", list.get(TABLES - 1).name());
                assertTrue(list.stream().allMatch(t -> "InnoDB".equals(t.engine())));
                assertFalse(reader.isCached(cat.name(), "t001"), "l'elenco non legge le colonne");
                TestResults.write("step3", "T3.2-" + server.name().toLowerCase() + ".txt",
                        "T3.2 — " + server.label() + " (" + session.serverInfo().versionText() + ")\n"
                        + "catalogo " + cat.name() + " con " + TABLES + " tabelle (generate in " + genMs + " ms)\n"
                        + "MetadataReader.tables(): " + list.size() + " righe in " + ms + " ms"
                        + " (1 lettura di information_schema.TABLES; limite " + LIMIT_MS + " ms)\n"
                        + "esito: " + (ms < LIMIT_MS ? "SUPERATO" : "NON SUPERATO") + "\n");
                assertTrue(ms < LIMIT_MS, "elenco letto in " + ms + " ms");
            }
        }
    }
}
