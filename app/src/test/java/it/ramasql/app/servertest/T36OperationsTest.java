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
import static it.ramasql.app.servertest.Probe.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

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
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlOrigin;

/**
 * <b>T3.6</b> — dal navigatore: <em>Nuovo catalogo</em> (latin1 / latin1_swedish_ci), <em>Rinomina</em>,
 * <em>Svuota</em>, <em>Elimina</em> tabella → <b>Esegui</b> nell'anteprima. Per ogni operazione: anteprima mostrata
 * (catturata), riga nel registro con origine «Navigatore», esito e durata, <b>albero aggiornato da solo</b> (nessun
 * aggiornamento chiesto dal test), verifica sul server con una connessione separata. In più: un'operazione rifiutata
 * dal server (tabella riferita da una chiave esterna) mostra l'errore con il codice originale e cosa non è stato
 * applicato; alla fine il catalogo si elimina dal navigatore.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class T36OperationsTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t36_creaRinominaSvuotaEliminaConAnteprimaRegistroEAlberoAggiornato(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("ops");
        StringBuilder ev = new StringBuilder("T3.6 — operazioni del navigatore con Esegui, su " + server.label() + "\n");
        try (ClientApp a = ClientApp.connect(server, dataDir)) {
            String nav = SqlOrigin.NAVIGATOR.label();
            a.ws.onPreview = d -> {
                Probe.paintWindow("step3", d, "T3.6-" + (a.ws.previews.size() + 1) + "-" + server.id() + ".png");
                if (d.requiresTypedConfirmation()) {
                    d.confirmationField().setText(d.confirmation().typeToConfirm());
                }
                d.executeButton().doClick();
            };

            // ---------- 1. Nuovo catalogo con charset e collation scelti
            a.ws.onNewCatalog = d -> {
                assertEquals("utf8mb4", d.charsetBox().getSelectedItem(), "predefinito: utf8mb4");
                assertNotNull(d.collationBox().getSelectedItem(), "predefinita: la collation predefinita del server");
                d.nameField().setText(catalog);
                d.charsetBox().setSelectedItem("latin1");
                assertEquals("latin1_swedish_ci", d.collationBox().getSelectedItem(),
                        "cambiando charset si propone la sua collation predefinita");
                d.collationBox().setSelectedItem("latin1_swedish_ci");
                if (server == DbServer.MARIADB) {
                    Probe.paintWindow("step3", d, "nuovo-catalogo.png");
                }
                d.buttons().confirmButton().doClick();
            };
            a.menu(NavNode.Kind.SERVER, null, null, "nav.menu.newCatalog");
            waitUntil("anteprima di CREATE DATABASE", ClientApp.TIMEOUT, () -> a.ws.previews.size() == 1);
            ScriptResult created = a.awaitLastProposal();
            step(ev, a, 1, created, "CREATE DATABASE `" + catalog + "` CHARACTER SET latin1 COLLATE latin1_swedish_ci");
            waitUntil("il nuovo catalogo compare da solo nel navigatore", ClientApp.TIMEOUT,
                    () -> a.nav().find(NavNode.Kind.CATALOG, catalog, catalog) != null);
            assertEquals("latin1", server.scalar("SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA"
                    + " WHERE SCHEMA_NAME='" + catalog + "'"));
            assertEquals("latin1_swedish_ci", server.scalar("SELECT DEFAULT_COLLATION_NAME FROM"
                    + " information_schema.SCHEMATA WHERE SCHEMA_NAME='" + catalog + "'"));
            ev.append("   server: SCHEMATA → latin1 / latin1_swedish_ci; albero: catalogo comparso senza aggiornare\n");

            // tabelle di prova caricate dal test (connessione separata), poi «Aggiorna» sul catalogo
            server.loadFixture(catalog, "biblioteca.sql");
            a.menu(NavNode.Kind.CATALOG, catalog, catalog, "nav.menu.refresh");
            a.expand(NavNode.Kind.CATALOG, catalog, catalog);
            a.expand(NavNode.Kind.TABLES, catalog, null);
            a.node(NavNode.Kind.TABLE, catalog, "autori");

            // ---------- 2. Rinomina autori → scrittori
            a.ws.onRename = current -> "scrittori";
            a.menu(NavNode.Kind.TABLE, catalog, "autori", "nav.menu.rename");
            ScriptResult renamed = a.awaitLastProposal();
            step(ev, a, 2, renamed, "RENAME TABLE `" + catalog + "`.`autori` TO `" + catalog + "`.`scrittori`");
            waitUntil("albero: «scrittori» al posto di «autori»", ClientApp.TIMEOUT,
                    () -> a.nav().find(NavNode.Kind.TABLE, catalog, "scrittori") != null
                            && a.nav().find(NavNode.Kind.TABLE, catalog, "autori") == null);
            assertTrue(server.tableExists(catalog, "scrittori"));
            assertFalse(server.tableExists(catalog, "autori"));
            ev.append("   server: scrittori presente, autori assente; albero aggiornato da solo\n");

            // ---------- 3. Svuota prestiti (conferma rafforzata)
            assertTrue(server.rowCount(catalog, "prestiti") > 0);
            a.menu(NavNode.Kind.TABLE, catalog, "prestiti", "nav.menu.truncate");
            ScriptResult truncated = a.awaitLastProposal();
            step(ev, a, 3, truncated, "TRUNCATE TABLE `" + catalog + "`.`prestiti`");
            assertEquals(0, server.rowCount(catalog, "prestiti"));
            ev.append("   server: COUNT(*) di prestiti = 0\n");

            // ---------- 4. Elimina prestiti (conferma rafforzata)
            a.menu(NavNode.Kind.TABLE, catalog, "prestiti", "nav.menu.dropTable");
            ScriptResult dropped = a.awaitLastProposal();
            step(ev, a, 4, dropped, "DROP TABLE `" + catalog + "`.`prestiti`");
            waitUntil("albero: «prestiti» sparita", ClientApp.TIMEOUT,
                    () -> a.nav().find(NavNode.Kind.TABLE, catalog, "prestiti") == null
                            && a.nav().find(NavNode.Kind.TABLE, catalog, "soci") != null);
            assertFalse(server.tableExists(catalog, "prestiti"));
            ev.append("   server: prestiti assente; albero aggiornato da solo\n");

            for (FakeWorkspacePrompts.Shown shown : a.ws.previews) {
                assertEquals(PreviewDialog.Decision.EXECUTE, shown.decision());
            }
            assertEquals(ConfirmationPolicy.Level.CONFIRM, a.ws.previews.get(0).confirmation().level());
            assertEquals(ConfirmationPolicy.Level.CONFIRM, a.ws.previews.get(1).confirmation().level());
            assertEquals(ConfirmationPolicy.Level.STRONG, a.ws.previews.get(2).confirmation().level());
            assertEquals(ConfirmationPolicy.Level.STRONG, a.ws.previews.get(3).confirmation().level());

            // ---------- 5. Operazione rifiutata dal server: editori è riferita da libri (chiave esterna)
            a.menu(NavNode.Kind.TABLE, catalog, "editori", "nav.menu.dropTable");
            ScriptResult refused = a.awaitLastProposal();
            assertFalse(refused.completed());
            SqlLog.Entry err = fromEdt(() -> a.log().entries().get(a.log().size() - 1));
            assertEquals(SqlLog.Outcome.ERROR, err.outcome());
            assertEquals(nav, err.origin());
            assertTrue(server.tableExists(catalog, "editori"), "rifiutata: la tabella resta");
            SqlPanel.Message msg = fromEdt(() -> a.panel().messages().get(a.panel().messages().size() - 1));
            assertEquals(PipelineView.MessageKind.ERROR, msg.kind());
            assertTrue(msg.text().contains("Errore " + err.errorCode()) && msg.text().contains(err.message())
                    && msg.text().contains("Istruzioni applicate: 0 su 1"), msg.text());
            assertEquals(2, fromEdt(() -> a.panel().getSelectedIndex()), "dopo un errore si vede la scheda Messaggi");
            onEdt(() -> a.panel().setSelectedIndex(0));
            Probe.paintWindow("step3", a.frame(), "T3.6-registro-" + server.id() + ".png");
            if (server == DbServer.MARIADB) {
                Probe.paintWindow("step3", a.frame(), "registro.png");
            }
            int errorRow = fromEdt(() -> a.panel().logTable().getRowCount() - 1);
            java.awt.Color errorBackground = fromEdt(() -> a.panel().logTable()
                    .prepareRenderer(a.panel().logTable().getCellRenderer(errorRow, 3), errorRow, 3).getBackground());
            assertEquals(SqlPanel.errorBackground(), errorBackground, "la riga in errore è evidenziata");
            ev.append("5) Elimina editori (riferita da libri): rifiutata dal server — registro: ERRORE ")
                    .append(err.errorCode()).append(" (").append(err.sqlState()).append("), riga evidenziata;\n")
                    .append("   Messaggi: ").append(msg.text().replace("\n", "\n             ")).append('\n');

            // ---------- 6. Elimina catalogo dal navigatore
            a.menu(NavNode.Kind.CATALOG, catalog, catalog, "nav.menu.dropCatalog");
            ScriptResult droppedCatalog = a.awaitLastProposal();
            step(ev, a, 6, droppedCatalog, "DROP DATABASE `" + catalog + "`");
            waitUntil("albero: catalogo sparito", ClientApp.TIMEOUT,
                    () -> a.nav().find(NavNode.Kind.CATALOG, catalog, catalog) == null);
            assertFalse(server.catalogExists(catalog));
            ev.append("   server: catalogo assente; albero aggiornato da solo\n");

            // riepilogo del registro
            List<SqlLog.Entry> entries = fromEdt(() -> a.log().entries());
            assertEquals(6, entries.size());
            ev.append("\nRegistro (").append(entries.size()).append(" righe):\n");
            for (SqlLog.Entry e : entries) {
                ev.append(String.format("  #%d %-10s %-6s %4d ms  %s%n", e.sequence(), e.origin(), e.outcome(),
                        e.durationMillis(), e.sql()));
            }
            ev.append("Schermate: T3.6-1…6-").append(server.id()).append(".png (anteprime), T3.6-registro-")
                    .append(server.id()).append(".png").append(server == DbServer.MARIADB
                            ? ", nuovo-catalogo.png, registro.png" : "").append('\n');
            assertTrue(a.prompts.errors.isEmpty(), a.prompts.errors.toString());
        } finally {
            server.dropQuietly(catalog);
        }
        Probe.writeText("step3", "T3.6-interfaccia-" + server.id() + ".txt", ev.toString());
    }

    /** Un'operazione eseguita: anteprima con l'SQL atteso, riga del registro «Navigatore» OK con durata. */
    private static void step(StringBuilder ev, ClientApp a, int n, ScriptResult result, String expectedSql) {
        assertNotNull(result, "eseguito");
        assertTrue(result.completed(), "operazione " + n + " non riuscita: " + result.failure());
        FakeWorkspacePrompts.Shown shown = a.ws.previews.get(a.ws.previews.size() - 1);
        assertEquals(expectedSql + ";", shown.sqlInDialog(), "anteprima dell'operazione " + n);
        SqlLog.Entry e = fromEdt(() -> a.log().entries().get(a.log().size() - 1));
        assertEquals(expectedSql, e.sql(), "l'SQL eseguito è quello mostrato");
        assertEquals(SqlOrigin.NAVIGATOR.label(), e.origin());
        assertEquals(SqlLog.Outcome.OK, e.outcome());
        assertTrue(e.durationMillis() >= 0);
        int rows = fromEdt(() -> a.panel().logTable().getRowCount());
        assertEquals(fromEdt(() -> a.log().size()), rows, "il Registro mostra ogni istruzione");
        String outcome = fromEdt(() -> String.valueOf(a.panel().logTable().getValueAt(rows - 1, 4)));
        String origin = fromEdt(() -> String.valueOf(a.panel().logTable().getValueAt(rows - 1, 2)));
        String duration = fromEdt(() -> String.valueOf(a.panel().logTable().getValueAt(rows - 1, 5)));
        assertEquals("OK", outcome);
        assertEquals("Navigatore", origin);
        assertTrue(duration.endsWith(" ms"), duration);
        SqlPanel.Message msg = fromEdt(() -> a.panel().messages().get(a.panel().messages().size() - 1));
        assertEquals(PipelineView.MessageKind.SUCCESS, msg.kind(), msg.text());
        ev.append(n).append(") ").append(result.script().title()).append(" — anteprima ")
                .append(shown.confirmation().level()).append(": «").append(shown.sqlInDialog()).append("»\n")
                .append("   registro: #").append(e.sequence()).append(' ').append(origin).append(' ').append(outcome)
                .append(' ').append(duration).append("; messaggio: ").append(msg.text()).append('\n');
    }
}
