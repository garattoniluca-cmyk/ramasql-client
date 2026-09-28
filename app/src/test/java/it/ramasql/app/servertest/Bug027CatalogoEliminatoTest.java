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
 * <b>BUG-027</b>: eliminato dal navigatore un catalogo intero ({@code DROP DATABASE}), i sorgenti grafici delle sue
 * viste escono da {@code viste.json}; restano quelli degli altri cataloghi e degli altri server. Se l'anteprima si
 * annulla, o se il server rifiuta l'istruzione, l'archivio non si tocca.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class Bug027CatalogoEliminatoTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void eliminareUnCatalogoToglieDallArchivioLeSueViste(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("b27");
        String other = DbServer.newCatalogName("b27b");
        StringBuilder ev = new StringBuilder("BUG-027 — archivio delle viste dopo DROP DATABASE, su " + server.label()
                + "\n");
        try {
            server.createCatalog(catalog);
            server.createCatalog(other);
            server.run("CREATE TABLE `" + catalog + "`.`libri` (id INT NOT NULL PRIMARY KEY, titolo VARCHAR(80))");
            server.run("CREATE VIEW `" + catalog + "`.`v_uno` AS SELECT id, titolo FROM `" + catalog + "`.`libri`");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                String address = server.profile().address();
                ViewSourceStore store = a.frame().viewSources();
                store.save(new ViewSourceStore.Entry(address, catalog, "v_uno", "SELECT id, titolo FROM libri",
                        "select id, titolo from libri"));
                store.save(new ViewSourceStore.Entry(address, catalog, "v_due", "SELECT id FROM libri",
                        "select id from libri"));
                store.save(new ViewSourceStore.Entry(address, other, "v_uno", "SELECT 1", "select 1"));
                store.save(new ViewSourceStore.Entry("altro-server:3306", catalog, "v_uno", "SELECT 1", "select 1"));

                // 1. anteprima annullata: il catalogo resta, e l'archivio non cambia
                a.ws.onPreview = d -> d.cancelButton().doClick();
                a.menu(NavNode.Kind.CATALOG, catalog, catalog, "nav.menu.dropCatalog");
                assertNull(a.awaitLastProposal(), "anteprima annullata");
                Probe.onEdt(() -> { });
                assertTrue(server.catalogExists(catalog));
                assertTrue(store.find(address, catalog, "v_uno").isPresent(), "annullato: i sorgenti restano");
                ev.append("1. Anteprima annullata: catalogo e sorgenti al loro posto\n");

                a.ws.onPreview = d -> {
                    if (d.requiresTypedConfirmation()) {
                        d.confirmationField().setText(d.confirmation().typeToConfirm());
                    }
                    d.executeButton().doClick();
                };
                // 2. il server rifiuta (il catalogo l'ha già eliminato qualcun altro): l'archivio non si tocca
                server.run("DROP DATABASE `" + other + "`");
                a.menu(NavNode.Kind.CATALOG, other, other, "nav.menu.dropCatalog");
                ScriptResult rifiutato = a.awaitLastProposal();
                assertFalse(rifiutato.completed(), "il server ha rifiutato il DROP DATABASE");
                Probe.onEdt(() -> { });
                assertTrue(store.find(address, other, "v_uno").isPresent(),
                        "DROP DATABASE non riuscito: i sorgenti restano");
                ev.append("2. DROP DATABASE rifiutato dal server (").append(rifiutato.failure().orElseThrow().error())
                        .append("): i sorgenti di quel catalogo restano nell'archivio\n");

                // 3. DROP DATABASE riuscito, con la conferma scritta
                a.menu(NavNode.Kind.CATALOG, catalog, catalog, "nav.menu.dropCatalog");
                ScriptResult r = a.awaitLastProposal();
                assertTrue(r.completed(), "DROP DATABASE riuscito");
                assertFalse(server.catalogExists(catalog), "il catalogo non c'è più sul server");
                Probe.waitUntil("sorgenti del catalogo tolti", ClientApp.TIMEOUT,
                        () -> store.find(address, catalog, "v_uno").isEmpty()
                                && store.find(address, catalog, "v_due").isEmpty());
                ViewSourceStore riletto = new ViewSourceStore(dataDir);
                assertTrue(riletto.find(address, catalog, "v_uno").isEmpty(), "anche nel file viste.json");
                assertTrue(riletto.find(address, catalog, "v_due").isEmpty(), "tutte le viste del catalogo");
                assertTrue(riletto.find(address, other, "v_uno").isPresent(),
                        "le voci del catalogo del DROP rifiutato restano");
                assertTrue(riletto.find("altro-server:3306", catalog, "v_uno").isPresent(),
                        "lo stesso catalogo di un altro server resta");
                ev.append("3. DROP DATABASE riuscito: tolte da viste.json v_uno e v_due del catalogo; restano la vista "
                        + "del catalogo il cui DROP è stato rifiutato e quella dello stesso catalogo su un altro server\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step12", "BUG-027-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
            server.dropQuietly(other);
        }
    }
}
