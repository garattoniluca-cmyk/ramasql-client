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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.pipeline.PipelineView;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.app.sqlpanel.SqlPanel;
import it.ramasql.core.exec.ConfirmationPolicy;

/**
 * <b>T3.5</b> — <em>Elimina tabella</em> dal navigatore → finestra d'anteprima (catturata) → <b>Annulla</b>: la
 * tabella esiste ancora sul server (connessione separata del test) e il registro ha zero istruzioni.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class T35CancelTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t35_eliminaTabellaPoiAnnullaNonToccaIlServer(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("annulla");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            long rowsBefore = server.rowCount(catalog, "libri");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                a.expand(NavNode.Kind.TABLES, catalog, null);
                a.ws.onPreview = d -> {
                    Probe.paintWindow("step3", d, "T3.5-" + server.id() + ".png");
                    if (server == DbServer.MARIADB) {
                        Probe.paintWindow("step3", d, "anteprima.png");
                    }
                    d.cancelButton().doClick();
                };
                a.menu(NavNode.Kind.TABLE, catalog, "libri", "nav.menu.dropTable");
                assertNull(a.awaitLastProposal(), "annullato: nessuna esecuzione");

                assertEquals(1, a.ws.previews.size(), "l'anteprima è stata mostrata");
                FakeWorkspacePrompts.Shown shown = a.ws.previews.get(0);
                String expectedSql = "DROP TABLE `" + catalog + "`.`libri`;";
                assertEquals(expectedSql, shown.sqlInDialog(), "l'anteprima mostra l'SQL esatto");
                assertEquals(ConfirmationPolicy.Level.STRONG, shown.confirmation().level());
                assertEquals(PreviewDialog.Decision.CANCEL, shown.decision());

                // sul server la tabella c'è ancora, con le stesse righe (connessione separata del test)
                assertTrue(server.tableExists(catalog, "libri"), "la tabella deve esistere ancora");
                assertEquals(rowsBefore, server.rowCount(catalog, "libri"));
                // registro: zero istruzioni
                assertEquals(0, fromEdt(() -> a.log().size()), "il registro non deve avere istruzioni");
                assertEquals(0, fromEdt(() -> a.panel().logTable().getRowCount()));
                // l'albero mostra ancora la tabella; la scheda Anteprima mostra l'SQL; Messaggi dice «annullato»
                assertNotNull(fromEdt(() -> a.nav().find(NavNode.Kind.TABLE, catalog, "libri")));
                assertEquals(expectedSql, fromEdt(() -> a.panel().previewText()));
                SqlPanel.Message last = fromEdt(() -> a.panel().messages().get(a.panel().messages().size() - 1));
                assertEquals(PipelineView.MessageKind.INFO, last.kind());
                assertTrue(last.text().contains("annullato, nulla è stato eseguito"), last.text());

                Probe.writeText("step3", "T3.5-" + server.id() + ".txt", "T3.5 — Elimina tabella → Annulla, su " + server.label()
                        + "\nCatalogo di test: " + catalog + " (fixture biblioteca.sql)\n"
                        + "Anteprima mostrata (T3.5-" + server.id() + ".png): «" + shown.sqlInDialog() + "», conferma "
                        + shown.confirmation().level() + " (riscrivere «" + shown.confirmation().typeToConfirm() + "»)\n"
                        + "Scelta: " + shown.decision() + "\n"
                        + "Server (connessione separata): tabella libri presente = "
                        + server.tableExists(catalog, "libri") + ", righe " + server.rowCount(catalog, "libri")
                        + " (prima " + rowsBefore + ")\n"
                        + "Registro: " + fromEdt(() -> a.log().size()) + " istruzioni\n"
                        + "Messaggi: " + last.text() + "\n");
            }
        } finally {
            server.dropQuietly(catalog);
        }
    }
}
