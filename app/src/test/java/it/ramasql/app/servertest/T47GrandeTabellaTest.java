/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.grid.GridDataSource;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T4.7</b> — tabella {@code grande} da <b>1 000 000 di righe</b> sul server vero: apertura in data-entry dal
 * navigatore, pagina 2, ordinamento. Si misurano il tempo della <b>prima pagina</b> (limite della roadmap: 1 s) e la
 * memoria usata (limite: 300 MB), e si controlla che la griglia legga davvero solo una pagina per volta: le righe in
 * memoria sono al massimo quelle della pagina, non il milione.
 *
 * <p>L'ordinamento su una colonna <b>senza indice</b> si misura e si riporta senza soglia: la roadmap non ne chiede
 * una, e il tempo dipende dall'ordinamento di un milione di righe sul server (vedi {@code BUG-017}).
 */
@Tag("step4")
@Tag("ui")
@Tag("it")
class T47GrandeTabellaTest {

    /** Righe della tabella di prova: quelle chieste dalla roadmap. */
    private static final int ROWS = 1_000_000;
    /** Righe per pagina: il limite predefinito delle impostazioni. */
    private static final int PAGE = 1000;
    private static final long FIRST_PAGE_LIMIT_MS = 1000;
    private static final long MEMORY_LIMIT_BYTES = 300L * 1024 * 1024;

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t47_unMilioneDiRigheSiApreEPaginaSenzaCaricareTutto(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("grande");
        StringBuilder evidence = new StringBuilder("T4.7 — tabella da " + ROWS + " righe su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            long popolamento = System.currentTimeMillis();
            popola(server, catalog);
            popolamento = System.currentTimeMillis() - popolamento;
            assertEquals(ROWS, server.rowCount(catalog, "grande"), "righe preparate sul server");
            evidence.append("Catalogo di test: ").append(catalog).append(" (creato e popolato in ")
                    .append(popolamento).append(" ms con INSERT … SELECT, fuori dal client)\n");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                TableDef table = a.workspace().reader().table(catalog, "grande").orElseThrow();

                // --- prima pagina: la apre il navigatore, come farebbe l'utente
                long start = System.nanoTime();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, table));
                long firstPageMs = (System.nanoTime() - start) / 1_000_000;
                assertNotNull(grid, "la scheda di data-entry si è aperta");
                a.waitIdle();
                assertEquals(PAGE, fromEdt(() -> grid.model().pending().rowCount()), "in griglia c'è una pagina");
                assertTrue(fromEdt(grid::hasNextPage), "ci sono altre pagine");
                String sqlPrimaPagina = a.log().entries().getLast().sql();
                assertTrue(sqlPrimaPagina.contains("LIMIT " + (PAGE + 1)),
                        "la griglia chiede una riga in più per sapere se c'è un'altra pagina: " + sqlPrimaPagina);
                evidence.append("Prima pagina: ").append(firstPageMs).append(" ms (limite ")
                        .append(FIRST_PAGE_LIMIT_MS).append(" ms) — SQL nel registro: ").append(sqlPrimaPagina)
                        .append('\n');

                // --- pagina 2
                start = System.nanoTime();
                assertTrue(fromEdt(grid::nextPage), "pagina 2 letta");
                long page2Ms = (System.nanoTime() - start) / 1_000_000;
                List<List<String>> prime = fromEdt(() -> it.ramasql.app.grid.GridTestSupport.cells(grid, 0, 0, 0, 2));
                assertTrue(Integer.parseInt(prime.get(0).get(0)) > PAGE,
                        "la pagina 2 comincia oltre la prima pagina, id=" + prime.get(0).get(0));
                evidence.append("Pagina 2: ").append(page2Ms).append(" ms, prima riga id=")
                        .append(prime.get(0).get(0)).append(" — ").append(a.log().entries().getLast().sql())
                        .append('\n');

                // --- ordinamento su colonna senza indice (dalla prima pagina)
                start = System.nanoTime();
                assertTrue(fromEdt(() -> grid.sortBy(new GridDataSource.SortOrder("numero", false))), "ordinato");
                long sortMs = (System.nanoTime() - start) / 1_000_000;
                List<List<String>> ordinate =
                        fromEdt(() -> it.ramasql.app.grid.GridTestSupport.cells(grid, 0, 1, 2, 2));
                assertEquals(String.valueOf(ROWS), ordinate.get(0).get(0), "il primo valore è il più grande");
                assertTrue(Integer.parseInt(ordinate.get(0).get(0)) > Integer.parseInt(ordinate.get(1).get(0)),
                        "ordine decrescente");
                evidence.append("Ordinamento su «numero» (colonna senza indice), decrescente: ").append(sortMs)
                        .append(" ms — ").append(a.log().entries().getLast().sql()).append('\n');

                // --- memoria: la griglia tiene una pagina, non il milione di righe
                for (int i = 0; i < 3; i++) {
                    System.gc();
                    Thread.sleep(80);
                }
                long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                evidence.append("Memoria usata dopo le tre letture: ").append(used / (1024 * 1024))
                        .append(" MB (limite 300 MB)\n");
                evidence.append("Righe tenute in memoria dalla griglia: ")
                        .append(fromEdt(() -> grid.model().pending().rowCount())).append(" (di ").append(ROWS)
                        .append(" sul server)\n");

                Probe.paintWindow("step4", a.frame(), "T4.7-" + server.id() + ".png");
                onEdt(() -> a.frame().tabs().close(grid));

                assertTrue(firstPageMs < FIRST_PAGE_LIMIT_MS,
                        "prima pagina in " + firstPageMs + " ms, limite " + FIRST_PAGE_LIMIT_MS);
                assertTrue(used < MEMORY_LIMIT_BYTES, "memoria usata " + (used / (1024 * 1024)) + " MB, limite 300 MB");
                assertTrue(fromEdt(() -> grid.model().pending().rowCount()) <= PAGE,
                        "in memoria non deve esserci più di una pagina");
                evidence.append("Esito: SUPERATO\n");
            }
        } finally {
            Probe.writeText("step4", "T4.7-" + server.id() + ".txt", evidence.toString());
            server.dropQuietly(catalog);
        }
    }

    /**
     * Un milione di righe in dieci passi da centomila, con {@code INSERT … SELECT} su un incrocio di cifre: veloce e
     * uguale sui due server, e non tiene niente in memoria nel client.
     */
    private static void popola(DbServer server, String catalog) throws Exception {
        server.run("CREATE TABLE `" + catalog + "`.`grande` ("
                + "id INT UNSIGNED NOT NULL AUTO_INCREMENT, "
                + "testo VARCHAR(60) NOT NULL, "
                + "numero INT NOT NULL, "
                + "PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        server.run("CREATE TABLE `" + catalog + "`.`cifre` (i INT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        server.run("INSERT INTO `" + catalog + "`.`cifre` (i) VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9)");
        for (int blocco = 0; blocco < 10; blocco++) {
            int base = blocco * 100_000;
            server.run("INSERT INTO `" + catalog + "`.`grande` (testo, numero) "
                    + "SELECT CONCAT('riga ', n), n FROM (SELECT " + base
                    + " + 1 + a.i + 10*b.i + 100*c.i + 1000*d.i + 10000*e.i AS n"
                    + " FROM `" + catalog + "`.`cifre` a, `" + catalog + "`.`cifre` b, `" + catalog + "`.`cifre` c,"
                    + " `" + catalog + "`.`cifre` d, `" + catalog + "`.`cifre` e) AS numeri");
        }
        server.run("DROP TABLE `" + catalog + "`.`cifre`");
    }
}
