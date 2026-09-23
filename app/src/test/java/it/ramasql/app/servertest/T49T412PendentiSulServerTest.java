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

import static it.ramasql.app.grid.GridTestSupport.type;
import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import it.ramasql.app.workspace.WorkspacePrompts;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T4.9</b> (nessuna scrittura implicita) e <b>T4.12</b> (Scarta e chiusura della scheda) <b>contro i server
 * veri</b>: qui la griglia è quella del programma, collegata alla tabella {@code soci} della fixture
 * {@code biblioteca}, e ogni affermazione si controlla sul server con la connessione separata del test.
 *
 * <p>T4.9: si inseriscono 3 righe, si modifica una riga esistente, si ordina e si cambia pagina <b>senza</b> premere
 * Conferma → sul server non deve cambiare niente e il registro non deve avere nessuna istruzione di scrittura.
 * <p>T4.12: <em>Scarta</em> riporta i dati a quelli del server (riletti davvero); poi, chiudendo la scheda con
 * modifiche in sospeso, arriva la domanda «Conferma, scarta o resta?» e «Resta» lascia la scheda aperta.
 */
@Tag("step4")
@Tag("ui")
@Tag("it")
class T49T412PendentiSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t49_t412_senzaConfermaIlServerNonCambiaEScartaRileggeDalServer(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("pendenti");
        StringBuilder ev = new StringBuilder("T4.9 e T4.12 — data-entry senza Conferma, su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            long righePrima = server.rowCount(catalog, "soci");
            List<List<String>> socio1Prima = server.rows(
                    "SELECT tessera, cognome, nome, email FROM `" + catalog + "`.`soci` ORDER BY id LIMIT 1");
            ev.append("Catalogo di test: ").append(catalog).append(" (fixture biblioteca.sql)\n")
                    .append("soci sul server prima: ").append(righePrima).append(" righe; prima riga ")
                    .append(socio1Prima.get(0)).append('\n');

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                TableDef soci = a.workspace().reader().table(catalog, "soci").orElseThrow();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, soci));
                a.waitIdle();
                int scritture = scrittureNelRegistro(a);
                assertEquals(0, scritture, "aprire la tabella non scrive niente");

                // ---------------------------------------------------------------- T4.9
                int nuova = fromEdt(() -> grid.model().pending().rowCount());   // la riga d'inserimento
                type(grid, nuova, 1, "T900001");
                type(grid, nuova, 2, "Rossi");
                type(grid, nuova, 3, "Anna");
                type(grid, nuova + 1, 1, "T900002");
                type(grid, nuova + 1, 2, "Bianchi");
                type(grid, nuova + 1, 3, "Bruno");
                type(grid, nuova + 2, 1, "T900003");
                type(grid, nuova + 2, 2, "Verdi");
                type(grid, nuova + 2, 3, "Carla");
                type(grid, 0, 4, "cambiata@esempio.it");          // modifica di una riga esistente
                String contatore = fromEdt(grid::counterText);
                assertTrue(contatore.contains("3"), "il contatore nomina i 3 inserimenti: " + contatore);

                // ordinare e cambiare pagina con modifiche in sospeso: rifiutato, con avviso
                assertFalse(fromEdt(() -> grid.sortBy(new GridDataSource.SortOrder("cognome", true))),
                        "ordinare con modifiche in sospeso è rifiutato");
                assertFalse(fromEdt(grid::nextPage), "cambiare pagina con modifiche in sospeso è rifiutato");
                String avviso = fromEdt(grid::notice);
                assertTrue(avviso.contains("conferma o scarta"), avviso);

                assertEquals(0, scrittureNelRegistro(a), "registro: nessuna istruzione di scrittura");
                assertEquals(righePrima, server.rowCount(catalog, "soci"), "sul server non è comparsa nessuna riga");
                assertEquals(socio1Prima, server.rows("SELECT tessera, cognome, nome, email FROM `" + catalog
                        + "`.`soci` ORDER BY id LIMIT 1"), "sul server la riga esistente è intatta");
                Probe.paintWindow("step4", a.frame(), "T4.9-server-" + server.id() + ".png");
                ev.append("T4.9 — contatore: «").append(contatore).append("»; avviso su ordina/cambia pagina: «")
                        .append(avviso).append("»\n")
                        .append("  registro: ").append(scrittureNelRegistro(a))
                        .append(" istruzioni di scrittura; soci sul server: ")
                        .append(server.rowCount(catalog, "soci")).append(" righe (prima ").append(righePrima)
                        .append("), prima riga invariata\n");

                // ---------------------------------------------------------------- T4.12 — Scarta
                a.ws.gridConfirmAnswer = true;   // la domanda «Scartare le modifiche?»
                assertTrue(fromEdt(grid::discard), "Scarta eseguito");
                assertFalse(fromEdt(grid::hasPending), "nessuna modifica in sospeso dopo Scarta");
                assertEquals(0, scrittureNelRegistro(a), "Scarta è locale: non scrive niente");
                // i dati mostrati sono quelli del server: si rilegge la pagina e si confronta cella per cella
                assertTrue(fromEdt(grid::reload), "dopo Scarta la pagina si rilegge");
                a.waitIdle();
                List<List<String>> inGriglia =
                        fromEdt(() -> it.ramasql.app.grid.GridTestSupport.cells(grid, 0, 0, 1, 4));
                assertEquals(socio1Prima.get(0), inGriglia.get(0), "la prima riga è quella del server");
                ev.append("T4.12 — Scarta: nessuna modifica in sospeso, 0 istruzioni; prima riga riletta ")
                        .append(inGriglia.get(0)).append(" = server ").append(socio1Prima.get(0)).append('\n');

                // ---------------------------------------------------------------- T4.12 — chiusura della scheda
                type(grid, 0, 3, "NomeNonSalvato");
                assertTrue(fromEdt(grid::hasPending), "c'è una modifica in sospeso");
                a.ws.onPendingOnClose = q -> WorkspacePrompts.PendingChoice.STAY;
                assertFalse(fromEdt(() -> a.frame().tabs().close(grid)), "«Resta» lascia la scheda aperta");
                assertEquals(1, fromEdt(() -> a.frame().tabs().count()), "la scheda è ancora lì");
                String domanda = a.ws.pendingQuestions.getLast();
                assertTrue(domanda.contains("Conferma") && domanda.contains("scarta") && domanda.contains("resta"),
                        "la domanda offre le tre strade: " + domanda);
                assertTrue(fromEdt(grid::hasPending), "«Resta» non ha buttato la modifica");

                a.ws.onPendingOnClose = q -> WorkspacePrompts.PendingChoice.DISCARD;
                assertTrue(fromEdt(() -> a.frame().tabs().close(grid)), "«Scarta» chiude la scheda");
                assertEquals(0, fromEdt(() -> a.frame().tabs().count()), "nessuna scheda aperta");
                assertEquals(0, scrittureNelRegistro(a), "chiudere non ha scritto niente");
                assertEquals(righePrima, server.rowCount(catalog, "soci"), "sul server nulla, dall'inizio alla fine");
                ev.append("T4.12 — chiusura: domanda «").append(domanda.replace('\n', ' '))
                        .append("»; «Resta» → scheda aperta e modifica conservata; «Scarta» → scheda chiusa, ")
                        .append(scrittureNelRegistro(a)).append(" istruzioni nel registro, ")
                        .append(server.rowCount(catalog, "soci")).append(" righe sul server\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            // un test fallito non deve lasciare un file di evidenza che sembra valido
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step4", "T4.9-T4.12-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    /** Istruzioni del registro che non sono letture: qualunque cosa cambi i dati sarebbe una scrittura implicita. */
    private static int scrittureNelRegistro(ClientApp a) throws Exception {
        return fromEdt(() -> (int) a.log().entries().stream()
                .filter(e -> !e.sql().stripLeading().regionMatches(true, 0, "SELECT", 0, 6))
                .filter(e -> !e.sql().stripLeading().regionMatches(true, 0, "SHOW", 0, 4))
                .count());
    }
}
