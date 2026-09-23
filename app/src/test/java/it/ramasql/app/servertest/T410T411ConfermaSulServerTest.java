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

import static it.ramasql.app.grid.GridTestSupport.action;
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
import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T4.10</b> e <b>T4.11</b> <b>contro i server veri</b>, dalla griglia del programma sulla tabella {@code soci}.
 *
 * <p>T4.10: 3 modifiche + 1 inserimento + 1 eliminazione → <em>Conferma</em> → l'anteprima contiene <b>esattamente 5
 * istruzioni</b> e <b>nessuna</b> istruzione di transazione (ADR-010) → dopo l'esecuzione i dati sul server coincidono
 * con quanto mostrato e l'{@code id} AUTO_INCREMENT della riga nuova compare in griglia.
 * <p>T4.11: 3 inserimenti di cui il secondo con {@code tessera} duplicata → il 1º è scritto e marcato salvato, il 2º
 * porta l'errore <b>1062</b> del server spiegato, il 3º resta in sospeso e non è stato scritto; corretta la tessera del
 * 2º, una nuova Conferma scrive tutto.
 */
@Tag("step4")
@Tag("ui")
@Tag("it")
class T410T411ConfermaSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t410_t411_confermaScriveSulServerEGliErroriRestanoInSospeso(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("conferma");
        StringBuilder ev = new StringBuilder("T4.10 e T4.11 — Conferma sul server " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            // un socio senza prestiti, aggiunto dal test (fuori dal client): prestiti.id_socio è una FK RESTRICT,
            // e nella fixture ogni socio ha almeno un prestito
            server.run("INSERT INTO `" + catalog + "`.`soci` (tessera, cognome, nome) VALUES"
                    + " ('T990099', 'DaEliminare', 'Senza Prestiti')");
            long righePrima = server.rowCount(catalog, "soci");
            ev.append("Catalogo di test: ").append(catalog).append(" — soci: ").append(righePrima).append(" righe\n");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                TableDef soci = a.workspace().reader().table(catalog, "soci").orElseThrow();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, soci));
                a.waitIdle();
                String idDaEliminare = server.scalar("SELECT id FROM `" + catalog
                        + "`.`soci` WHERE tessera = 'T990099'");
                assertTrue(idDaEliminare != null, "il socio senza prestiti è sul server");
                int rigaDaEliminare = rigaConId(grid, idDaEliminare);
                assertTrue(rigaDaEliminare >= 0, "il socio " + idDaEliminare + " è nella prima pagina");

                // ---------------------------------------------------------------- T4.10
                type(grid, 0, 2, "Rossi Cognome Nuovo");
                type(grid, 1, 3, "NomeCambiato");
                type(grid, 2, 4, "nuova.email@esempio.it");
                int nuova = fromEdt(() -> grid.model().pending().rowCount());
                type(grid, nuova, 1, "T500001");
                type(grid, nuova, 2, "Nuovi");
                type(grid, nuova, 3, "Nicola");
                onEdt(() -> grid.selectRows(rigaDaEliminare, rigaDaEliminare));
                action(grid, DataGrid.ACTION_DELETE_ROWS);
                assertEquals(PendingChanges.RowKind.DELETED,
                        fromEdt(() -> grid.model().pending().kind(rigaDaEliminare)));

                a.ws.onPreview = d -> {
                    Probe.paintWindow("step4", d, "T4.10-anteprima-" + server.id() + ".png");
                    d.executeButton().doClick();
                };
                onEdt(grid::confirm);
                var esito = a.awaitLastProposal();
                assertTrue(esito != null && esito.completed(), "le 5 istruzioni sono state eseguite; esito: "
                        + (esito == null ? "annullato dall'anteprima"
                        : esito.failure().map(f -> String.valueOf(f.error())).orElse("?")));

                var mostrata = a.ws.previews.getLast();
                String sql = mostrata.sqlInDialog();
                assertEquals(5, mostrata.script().size(), "l'anteprima ha esattamente 5 istruzioni:\n" + sql);
                assertEquals(1, conta(sql, "DELETE"), "una DELETE");
                assertEquals(3, conta(sql, "UPDATE"), "tre UPDATE");
                assertEquals(1, conta(sql, "INSERT"), "una INSERT");
                for (String vietata : List.of("START TRANSACTION", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT")) {
                    assertFalse(sql.toUpperCase(java.util.Locale.ROOT).contains(vietata),
                            "nessuna istruzione di transazione (" + vietata + "):\n" + sql);
                }
                ev.append("T4.10 — anteprima con ").append(mostrata.script().size())
                        .append(" istruzioni (1 DELETE, 3 UPDATE, 1 INSERT), nessuna transazione:\n")
                        .append(sql.indent(4));

                // sul server: le modifiche ci sono, la riga eliminata no, la nuova sì
                assertEquals(righePrima, server.rowCount(catalog, "soci"), "una eliminata, una aggiunta");
                assertEquals("0", server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.`soci` WHERE id = "
                        + idDaEliminare), "la riga eliminata non c'è più");
                List<List<String>> nuovaSulServer = server.rows("SELECT id, tessera, cognome, nome FROM `" + catalog
                        + "`.`soci` WHERE tessera = 'T500001'");
                assertEquals(1, nuovaSulServer.size(), "la riga nuova è sul server");
                String idAssegnato = nuovaSulServer.get(0).get(0);

                // l'id AUTO_INCREMENT compare in griglia (la pagina si rilegge da sola dopo una Conferma riuscita)
                a.waitIdle();
                Probe.waitUntil("id AUTO_INCREMENT in griglia", 10_000, () -> {
                    try {
                        return fromEdt(() -> it.ramasql.app.grid.GridTestSupport.cells(grid, 0,
                                grid.model().pending().rowCount() - 1, 0, 1)).stream()
                                .anyMatch(r -> idAssegnato.equals(r.get(0)) && "T500001".equals(r.get(1)));
                    } catch (Exception e) {
                        return false;
                    }
                });
                Probe.paintWindow("step4", a.frame(), "T4.10-dopo-" + server.id() + ".png");
                ev.append("  sul server: riga ").append(idDaEliminare).append(" eliminata, riga nuova id=")
                        .append(idAssegnato).append(" (tessera T500001) — l'id AUTO_INCREMENT si vede in griglia\n")
                        .append("  cognome/nome/email modificati riletti dal server: ")
                        .append(server.rows("SELECT cognome FROM `" + catalog + "`.`soci` ORDER BY id LIMIT 1")
                                .get(0)).append('\n');
                assertEquals("Rossi Cognome Nuovo", server.rows("SELECT cognome FROM `" + catalog
                        + "`.`soci` ORDER BY id LIMIT 1").get(0).get(0), "la modifica è sul server");

                // ---------------------------------------------------------------- T4.11
                long righeT411 = server.rowCount(catalog, "soci");
                int base = fromEdt(() -> grid.model().pending().rowCount());
                type(grid, base, 1, "T600001");
                type(grid, base, 2, "Uno");
                type(grid, base, 3, "Primo");
                type(grid, base + 1, 1, "T500001");        // duplicata: esiste già
                type(grid, base + 1, 2, "Due");
                type(grid, base + 1, 3, "Secondo");
                type(grid, base + 2, 1, "T600003");
                type(grid, base + 2, 2, "Tre");
                type(grid, base + 2, 3, "Terzo");
                long idPrima = fromEdt(() -> grid.model().pending().rowId(base));
                long idErrore = fromEdt(() -> grid.model().pending().rowId(base + 1));
                long idTerza = fromEdt(() -> grid.model().pending().rowId(base + 2));

                onEdt(grid::confirm);
                a.awaitLastProposal();
                a.waitIdle();

                assertEquals(PendingChanges.State.SALVATA, statoDi(grid, idPrima), "la 1ª riga è salvata");
                assertEquals(PendingChanges.State.IN_ERRORE, statoDi(grid, idErrore), "la 2ª è in errore");
                assertEquals(PendingChanges.State.PENDENTE, statoDi(grid, idTerza), "la 3ª è ancora in sospeso");
                String errore = fromEdt(() -> grid.model().pending()
                        .errorMessage(grid.model().pending().indexOf(idErrore)).orElse(""));
                assertTrue(errore.contains("1062"), "l'errore del server è il 1062: " + errore);
                // «Duplicate» è il messaggio grezzo del server: la spiegazione in italiano è un'altra cosa
                assertTrue(errore.contains("Valore duplicato"), "con la spiegazione in italiano: " + errore);
                assertEquals(righeT411 + 1, server.rowCount(catalog, "soci"), "sul server è entrata solo la 1ª");
                Probe.paintWindow("step4", a.frame(), "T4.11-errore-" + server.id() + ".png");
                ev.append("T4.11 — 1ª salvata, 2ª in errore «").append(errore).append("», 3ª in sospeso; ")
                        .append("sul server ").append(server.rowCount(catalog, "soci")).append(" righe (prima ")
                        .append(righeT411).append(")\n");

                // corretta la tessera duplicata, la Conferma scrive tutto
                type(grid, fromEdt(() -> grid.model().pending().indexOf(idErrore)), 1, "T600002");
                onEdt(grid::confirm);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals(righeT411 + 3, server.rowCount(catalog, "soci"), "adesso ci sono tutte e tre");
                assertEquals(3, server.rows("SELECT tessera FROM `" + catalog
                        + "`.`soci` WHERE tessera IN ('T600001','T600002','T600003')").size(), "le tre tessere");
                ev.append("  corretta la tessera del 2º → sul server ").append(server.rowCount(catalog, "soci"))
                        .append(" righe, le tre tessere T600001/T600002/T600003 presenti\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            // un test fallito non deve lasciare un file di evidenza che sembra valido
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step4", "T4.10-T4.11-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    /** L'indice della riga della griglia che ha quell'{@code id} nella prima colonna; -1 se non c'è. */
    private static int rigaConId(DataGrid grid, String id) throws Exception {
        return fromEdt(() -> {
            PendingChanges p = grid.model().pending();
            for (int r = 0; r < p.rowCount(); r++) {
                if (id.equals(p.value(r, 0))) {
                    return r;
                }
            }
            return -1;
        });
    }

    private static PendingChanges.State statoDi(DataGrid grid, long rowId) throws Exception {
        return fromEdt(() -> {
            int i = grid.model().pending().indexOf(rowId);
            if (i < 0) {
                throw new AssertionError("la riga " + rowId + " è sparita dalla griglia: non si può dire com'è finita");
            }
            return grid.model().pending().state(i);
        });
    }

    private static int conta(String text, String word) {
        int n = 0;
        for (String line : text.split("\n")) {
            if (line.stripLeading().regionMatches(true, 0, word, 0, word.length())) {
                n++;
            }
        }
        return n;
    }
}
