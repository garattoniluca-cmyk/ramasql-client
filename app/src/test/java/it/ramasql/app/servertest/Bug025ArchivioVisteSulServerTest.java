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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.navigator.NavNode;
import it.ramasql.core.connection.ViewSourceStore;
import it.ramasql.core.exec.ScriptResult;

/**
 * {@code BUG-025} — «Elimina vista» dal navigatore toglie il sorgente archiviato da {@code viste.json}, ma solo
 * <b>dopo</b> un {@code DROP VIEW} riuscito: con l'anteprima annullata o con il server che rifiuta l'istruzione la
 * voce resta; le voci delle altre viste (e degli altri server) non si toccano.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class Bug025ArchivioVisteSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void eliminareUnaVistaTogliIlSuoSorgenteSoloSeIlDropRiesce(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("b25");
        StringBuilder ev = new StringBuilder("BUG-025 — archivio delle viste ripulito dopo DROP VIEW, su "
                + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.run("CREATE TABLE `" + catalog + "`.`libri` (id INT NOT NULL PRIMARY KEY, titolo VARCHAR(80))");
            server.run("CREATE VIEW `" + catalog + "`.`v_uno` AS SELECT id, titolo FROM `" + catalog + "`.`libri`");
            server.run("CREATE VIEW `" + catalog + "`.`v_due` AS SELECT id FROM `" + catalog + "`.`libri`");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                String address = server.profile().address();
                ViewSourceStore store = a.frame().viewSources();
                store.save(new ViewSourceStore.Entry(address, catalog, "v_uno", "SELECT id, titolo FROM libri",
                        "select id, titolo from libri"));
                store.save(new ViewSourceStore.Entry(address, catalog, "v_due", "SELECT id FROM libri",
                        "select id from libri"));
                store.save(new ViewSourceStore.Entry("altro-server:3306", catalog, "v_uno", "SELECT 1", "select 1"));
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                a.expand(NavNode.Kind.VIEWS, catalog, null);

                // 1. anteprima annullata: niente DROP, la voce resta
                a.ws.onPreview = d -> d.cancelButton().doClick();
                a.menu(NavNode.Kind.VIEW, catalog, "v_uno", "nav.menu.dropView");
                assertNull(a.awaitLastProposal(), "anteprima annullata");
                Probe.onEdt(() -> { });
                assertTrue(store.find(address, catalog, "v_uno").isPresent(), "annullato: il sorgente resta");
                assertTrue(server.tableExists(catalog, "v_uno"));
                ev.append("1. Anteprima annullata: v_uno ancora sul server e nell'archivio\n");

                // 2. il server rifiuta (la vista l'ha già eliminata qualcun altro): la voce resta
                server.run("DROP VIEW `" + catalog + "`.`v_due`");
                a.ws.onPreview = d -> {
                    if (d.requiresTypedConfirmation()) {
                        d.confirmationField().setText(d.confirmation().typeToConfirm());
                    }
                    d.executeButton().doClick();
                };
                a.menu(NavNode.Kind.VIEW, catalog, "v_due", "nav.menu.dropView");
                ScriptResult rifiutato = a.awaitLastProposal();
                assertFalse(rifiutato.completed(), "il server ha rifiutato il DROP VIEW");
                Probe.onEdt(() -> { });
                assertTrue(store.find(address, catalog, "v_due").isPresent(),
                        "DROP VIEW non riuscito: il sorgente non si tocca");
                ev.append("2. DROP VIEW rifiutato dal server (").append(rifiutato.failure().orElseThrow().error())
                        .append("): v_due resta nell'archivio\n");

                // 3. DROP VIEW riuscito: la voce di v_uno sparisce, le altre restano
                a.menu(NavNode.Kind.VIEW, catalog, "v_uno", "nav.menu.dropView");
                ScriptResult eliminata = a.awaitLastProposal();
                assertTrue(eliminata.completed(), "DROP VIEW riuscito");
                assertEquals("DROP VIEW `" + catalog + "`.`v_uno`", eliminata.script().statements().get(0).text());
                assertFalse(server.tableExists(catalog, "v_uno"), "la vista non c'è più sul server");
                Probe.waitUntil("sorgente di v_uno tolto dall'archivio", ClientApp.TIMEOUT,
                        () -> store.find(address, catalog, "v_uno").isEmpty());
                ViewSourceStore riletto = new ViewSourceStore(dataDir);
                assertTrue(riletto.find(address, catalog, "v_uno").isEmpty(), "anche nel file viste.json");
                assertTrue(riletto.find(address, catalog, "v_due").isPresent(), "le altre viste restano");
                assertTrue(riletto.find("altro-server:3306", catalog, "v_uno").isPresent(),
                        "la stessa vista di un altro server resta");
                ev.append("3. DROP VIEW riuscito: v_uno tolta da viste.json; restano v_due e la voce dell'altro server\n")
                        .append("Esito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step12", "BUG-025-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }
}
