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

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.JTable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.pipeline.PipelineView;
import it.ramasql.app.sqlpanel.SqlPanel;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlScript;

/**
 * Pannello SQL alimentato dall'esecutore vero: uno script di tre istruzioni con un errore a metà passa dalla pipeline
 * (anteprima → Esegui) → <b>Registro</b> con due righe (la seconda in errore, evidenziata), <b>Messaggi</b> con codice
 * originale del server e ciò che è stato applicato e ciò che no, <b>Anteprima</b> con lo script; <b>filtro</b> per
 * testo ed esito; doppio clic → SQL negli appunti; <b>Esporta…</b> delle righe filtrate.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class SqlPanelTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void registroMessaggiAnteprimaFiltroCopiaEdEsportazione(DbServer server) throws Exception {
        try (ClientApp a = ClientApp.connect(server, dataDir)) {
            SqlScript script = SqlScript.of("Prova del registro", "Test", "SELECT 1",
                    "SELECT * FROM `ramasql_test_s3_inesistente`.`t`", "SELECT 2");
            a.ws.onPreview = d -> d.executeButton().doClick();
            ScriptResult result = fromEdt(() -> a.workspace().pipeline().propose(script))
                    .get(ClientApp.TIMEOUT, TimeUnit.MILLISECONDS);
            a.waitIdle();
            assertFalse(result.completed());
            waitUntil("due righe nel Registro", ClientApp.TIMEOUT, () -> a.panel().logTable().getRowCount() == 2);

            // ---------- Registro
            List<SqlLog.Entry> entries = fromEdt(() -> a.log().entries());
            assertEquals(2, entries.size(), "la terza istruzione non è stata tentata");
            SqlLog.Entry ok = entries.get(0);
            SqlLog.Entry err = entries.get(1);
            assertEquals(SqlLog.Outcome.OK, ok.outcome());
            assertEquals(SqlLog.Outcome.ERROR, err.outcome());
            assertTrue(err.errorCode() == 1049 || err.errorCode() == 1146, "codice: " + err.errorCode());
            JTable table = a.panel().logTable();
            onEdt(() -> {
                assertEquals("Test", table.getValueAt(0, 2));
                assertEquals("SELECT 1", table.getValueAt(0, 3));
                assertEquals("OK", table.getValueAt(0, 4));
                assertEquals("Errore " + err.errorCode(), table.getValueAt(1, 4));
                assertEquals(SqlPanel.errorBackground(), table.prepareRenderer(table.getCellRenderer(1, 3), 1, 3)
                        .getBackground(), "riga in errore evidenziata");
                assertFalse(SqlPanel.errorBackground().equals(table.prepareRenderer(table.getCellRenderer(0, 3), 0, 3)
                        .getBackground()), "riga riuscita non evidenziata");
            });

            // ---------- Messaggi: codice originale, spiegazione se nota, cosa applicato e cosa no
            SqlPanel.Message msg = fromEdt(() -> a.panel().messages().get(a.panel().messages().size() - 1));
            assertEquals(PipelineView.MessageKind.ERROR, msg.kind());
            assertTrue(msg.text().contains("Errore " + err.errorCode()) && msg.text().contains(err.message()), msg.text());
            assertTrue(msg.text().contains("Istruzioni applicate: 1 su 3"), msg.text());
            assertTrue(msg.text().contains("applicata: SELECT 1"), msg.text());
            assertTrue(msg.text().contains("non riuscita: SELECT * FROM"), msg.text());
            assertTrue(msg.text().contains("non eseguita: SELECT 2"), msg.text());
            assertTrue(msg.text().contains("Cosa significa:"), "spiegazione in italiano per " + err.errorCode());
            assertTrue(fromEdt(() -> a.panel().messagesPane().getText()).contains("Errore " + err.errorCode()));
            assertEquals(2, fromEdt(() -> a.panel().getSelectedIndex()), "dopo un errore si vede la scheda Messaggi");
            Probe.paintWindow("step3", a.frame(), "messaggi-" + server.id() + ".png");

            // ---------- Anteprima: l'ultimo script proposto
            assertEquals(script, fromEdt(() -> a.panel().previewedScript()));
            assertEquals(script.text(), fromEdt(() -> a.panel().previewText()));

            // ---------- filtro per testo ed esito
            onEdt(() -> a.panel().logFilterField().setText("select 1"));
            assertEquals(List.of(ok), fromEdt(() -> a.panel().visibleEntries()));
            onEdt(() -> a.panel().logFilterField().setText("errore"));
            assertEquals(List.of(err), fromEdt(() -> a.panel().visibleEntries()));
            onEdt(() -> a.panel().logFilterField().setText("nessuna_corrispondenza"));
            assertEquals(0, fromEdt(() -> table.getRowCount()));
            onEdt(() -> a.panel().logFilterField().setText(""));
            assertEquals(2, fromEdt(() -> table.getRowCount()));

            // ---------- doppio clic → SQL negli appunti
            onEdt(() -> {
                a.panel().setSelectedIndex(0);
                a.frame().validate();
                Rectangle cell = table.getCellRect(0, 3, true);
                table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                        InputEvent.BUTTON1_DOWN_MASK, cell.x + 5, cell.y + 5, 2, false, MouseEvent.BUTTON1));
            });
            assertEquals("SELECT 1", a.ws.clipboard.get(a.ws.clipboard.size() - 1));

            // ---------- Esporta… le righe filtrate
            Path file = dataDir.resolve("registro-filtrato.sql");
            a.ws.nextExport = FakeWorkspacePrompts.export(file, null);
            onEdt(() -> a.panel().logFilterField().setText("select 1"));
            onEdt(() -> a.panel().exportButton().doClick());
            String exported = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(exported.contains("\nSELECT 1;\n"), exported);
            assertFalse(exported.contains("inesistente"), "solo le righe visibili");
            SqlPanel.Message done = fromEdt(() -> a.panel().messages().get(a.panel().messages().size() - 1));
            assertEquals(PipelineView.MessageKind.SUCCESS, done.kind());
            assertTrue(done.text().contains(": 1 istruzioni"), done.text());
            onEdt(() -> a.panel().logFilterField().setText(""));
            a.ws.nextExport = FakeWorkspacePrompts.export(dataDir.resolve("registro.sql"), null);
            onEdt(() -> a.panel().exportButton().doClick());
            String all = Files.readString(dataDir.resolve("registro.sql"), StandardCharsets.UTF_8);
            assertTrue(all.contains("-- SELECT * FROM `ramasql_test_s3_inesistente`.`t`;"), "istruzione fallita commentata");
            assertNotNull(a.ws.exportRequests.get(0));

            Probe.writeText("step3", "pannello-sql-" + server.id() + ".txt", "Pannello SQL su " + server.label() + "\n"
                    + "Script: SELECT 1; SELECT da catalogo inesistente; SELECT 2 → Registro: 2 righe (OK, Errore "
                    + err.errorCode() + " evidenziata), terza non tentata\n"
                    + "Messaggi:\n" + msg.text() + "\n"
                    + "Filtro «select 1» → 1 riga; «errore» → 1 riga; doppio clic → appunti «SELECT 1»\n"
                    + "Esporta… con filtro → solo SELECT 1; senza filtro → istruzione fallita commentata\n");
        }
    }

    /**
     * «Esporta…» scrive le istruzioni nell'<b>ordine di esecuzione</b> anche con la tabella del registro ordinata
     * diversamente (clic su un'intestazione) e con un filtro attivo: uno script rieseguito in un altro ordine non
     * riprodurrebbe lo stato del server.
     */
    @ParameterizedTest
    @EnumSource(DbServer.class)
    void esportaSempreNellOrdineDiEsecuzione(DbServer server) throws Exception {
        try (ClientApp a = ClientApp.connect(server, dataDir)) {
            SqlScript script = SqlScript.of("Ordine", "Test", "SELECT 'c3 si'", "SELECT 'a1 si'", "SELECT 'x9 no'",
                    "SELECT 'b2 si'");
            a.ws.onPreview = d -> d.executeButton().doClick();
            ScriptResult result = fromEdt(() -> a.workspace().pipeline().propose(script))
                    .get(ClientApp.TIMEOUT, TimeUnit.MILLISECONDS);
            a.waitIdle();
            assertTrue(result.completed());
            waitUntil("quattro righe nel Registro", ClientApp.TIMEOUT, () -> a.panel().logTable().getRowCount() == 4);
            JTable table = a.panel().logTable();
            // ordinamento per testo SQL, crescente (come un clic sull'intestazione)
            onEdt(() -> table.getRowSorter().setSortKeys(
                    List.of(new javax.swing.RowSorter.SortKey(3, javax.swing.SortOrder.ASCENDING))));
            assertEquals(List.of("SELECT 'a1 si'", "SELECT 'b2 si'", "SELECT 'c3 si'", "SELECT 'x9 no'"),
                    fromEdt(() -> a.panel().visibleEntries().stream().map(SqlLog.Entry::sql).toList()),
                    "la tabella è davvero ordinata in un altro modo");

            Path file = dataDir.resolve("ordinato.sql");
            a.ws.nextExport = FakeWorkspacePrompts.export(file, null);
            onEdt(() -> a.panel().exportButton().doClick());
            assertEquals(List.of("SELECT 'c3 si'", "SELECT 'a1 si'", "SELECT 'x9 no'", "SELECT 'b2 si'"),
                    statements(Files.readString(file, StandardCharsets.UTF_8)), "tutto il registro: ordine di esecuzione");

            // filtro attivo e ordinamento decrescente
            onEdt(() -> table.getRowSorter().setSortKeys(
                    List.of(new javax.swing.RowSorter.SortKey(3, javax.swing.SortOrder.DESCENDING))));
            onEdt(() -> a.panel().logFilterField().setText(" si'"));
            assertEquals(List.of("SELECT 'c3 si'", "SELECT 'b2 si'", "SELECT 'a1 si'"),
                    fromEdt(() -> a.panel().visibleEntries().stream().map(SqlLog.Entry::sql).toList()));
            List<String> expected = List.of("SELECT 'c3 si'", "SELECT 'a1 si'", "SELECT 'b2 si'");
            Path filtered = dataDir.resolve("filtrato.sql");
            a.ws.nextExport = FakeWorkspacePrompts.export(filtered, null);
            onEdt(() -> a.panel().exportButton().doClick());
            assertEquals(expected, statements(Files.readString(filtered, StandardCharsets.UTF_8)),
                    "solo le righe filtrate, ma nell'ordine di esecuzione");
            assertEquals(expected, fromEdt(() -> a.panel().exportedEntries().stream().map(SqlLog.Entry::sql)
                    .toList()));
            Probe.writeText("step3", "pannello-sql-ordine-" + server.id() + ".txt", "Esporta… con il registro ordinato per SQL"
                    + " (crescente; poi decrescente con il filtro « si'»): istruzioni esportate nell'ordine di"
                    + " esecuzione " + expected + "\n");
        }
    }

    private static List<String> statements(String script) {
        return it.ramasql.core.exec.StatementSplitter.split(script).stream()
                .map(it.ramasql.core.exec.StatementSplitter.SplitStatement::text).toList();
    }
}
