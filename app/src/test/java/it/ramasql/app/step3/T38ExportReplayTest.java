/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step3;

import static it.ramasql.app.step3.Step3Ui.fromEdt;
import static it.ramasql.app.step3.Step3Ui.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.navigator.NavNode;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;

/**
 * <b>T3.8</b> — una serie di operazioni fatte dal client (caricamento della {@code biblioteca} con l'esecutore del
 * client, poi dal navigatore: rinomina, svuota, elimina e un'eliminazione rifiutata dal server) → <b>Esporta…</b>
 * del Registro come {@code .sql} (senza il nome del catalogo d'origine) → lo script si riesegue con l'esecutore su un
 * catalogo vuoto → stesso risultato finale: {@code TableDef} riletti uguali (compreso AUTO_INCREMENT), stesse viste,
 * stesso {@code CHECKSUM TABLE} per ogni tabella.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class T38ExportReplayTest {

    private static final String TEST_ORIGIN = "Test (fixture)";

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Step3Ui.setup();
    }

    @ParameterizedTest
    @EnumSource(Step3Server.class)
    void t38_registroEsportatoERieseguitoDaLoStessoRisultato(Step3Server server) throws Exception {
        String source = Step3Server.newCatalogName("reg_a");
        String copy = Step3Server.newCatalogName("reg_b");
        StringBuilder ev = new StringBuilder("T3.8 — registro esportato e rieseguito, su " + server.label() + "\n");
        try {
            server.createCatalog(source);
            server.createCatalog(copy);
            ev.append("Origine: ").append(source).append(" · copia (vuota): ").append(copy).append("\n\n");
            try (Step3App a = Step3App.connect(server, dataDir)) {
                SqlExecutor executor = a.workspace().executor();
                // 1) la biblioteca caricata dall'esecutore del client: finisce nel registro come tutto il resto
                String fixture = Files.readString(Step3Ui.projectRoot().resolve("it-tests/fixtures/biblioteca.sql"),
                        StandardCharsets.UTF_8);
                assertTrue(executor.run(SqlScript.of("USE", TEST_ORIGIN, "USE `" + source + "`")).completed());
                ScriptResult load = executor.run(SqlScript.fromText("Carica biblioteca", TEST_ORIGIN, fixture));
                assertTrue(load.completed(), () -> "fixture: " + load.failure());
                int afterFixture = fromEdt(() -> a.log().size());
                ev.append("1) fixture biblioteca.sql caricata con l'esecutore del client: ").append(afterFixture)
                        .append(" istruzioni nel registro\n");

                // 2) operazioni dal navigatore, con Esegui nell'anteprima
                a.ws.onPreview = d -> {
                    if (d.requiresTypedConfirmation()) {
                        d.confirmationField().setText(d.confirmation().typeToConfirm());
                    }
                    d.executeButton().doClick();
                };
                a.expand(NavNode.Kind.CATALOG, source, source);
                a.expand(NavNode.Kind.TABLES, source, null);
                a.ws.onRename = current -> "scrittori";
                a.menu(NavNode.Kind.TABLE, source, "autori", "nav.menu.rename");
                assertTrue(a.awaitLastProposal().completed());
                a.menu(NavNode.Kind.TABLE, source, "prestiti", "nav.menu.truncate");
                assertTrue(a.awaitLastProposal().completed());
                a.menu(NavNode.Kind.TABLE, source, "libri_autori", "nav.menu.dropTable");
                assertTrue(a.awaitLastProposal().completed());
                a.menu(NavNode.Kind.TABLE, source, "editori", "nav.menu.dropTable");
                assertFalse(a.awaitLastProposal().completed(), "editori è riferita da libri: il server rifiuta");
                List<SqlLog.Entry> entries = fromEdt(() -> a.log().entries());
                ev.append("2) dal navigatore: rinomina autori→scrittori, svuota prestiti, elimina libri_autori (OK), "
                        + "elimina editori (rifiutata: ERRORE ").append(entries.get(entries.size() - 1).errorCode())
                        .append(")\n");

                // 3) Esporta… del Registro, senza il nome del catalogo d'origine
                Path file = Step3Ui.resultsDir().resolve("T3.8-registro-interfaccia-" + server.id() + ".sql");
                a.ws.nextExport = FakeWorkspacePrompts.export(file, source);
                onEdt(() -> a.nav().tree().setSelectionPath(a.nav().find(NavNode.Kind.CATALOG, source, source)));
                onEdt(() -> a.panel().exportButton().doClick());
                assertEquals(1, a.ws.exportRequests.size());
                assertTrue(a.ws.exportRequests.get(0).endsWith("| " + source),
                        "si propone di togliere il catalogo selezionato: " + a.ws.exportRequests.get(0));
                String script = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(script.contains("`" + source + "`."), "nessun riferimento al catalogo d'origine");
                assertTrue(script.contains("-- DROP TABLE `editori`;"), "l'istruzione rifiutata resta commentata");
                assertTrue(script.contains("RENAME TABLE `autori` TO `scrittori`;"), script);
                ev.append("3) Esporta… → ").append(file.getFileName()).append(" (").append(script.lines().count())
                        .append(" righe; USE e DROP TABLE editori commentati)\n");

                // 4) riesecuzione sul catalogo vuoto
                assertTrue(executor.run(SqlScript.of("USE", TEST_ORIGIN, "USE `" + copy + "`")).completed());
                ScriptResult replay = executor.run(SqlScript.fromText("Riesecuzione del registro", TEST_ORIGIN, script));
                assertTrue(replay.completed(), () -> "riesecuzione: " + replay.failure());
                ev.append("4) rieseguito con l'esecutore su ").append(copy).append(": ").append(replay.results().size())
                        .append(" istruzioni, tutte OK\n\n");

                // 5) confronto: metadati (TableDef) e dati (CHECKSUM TABLE)
                MetadataReader reader = a.workspace().reader();
                reader.invalidateAll();
                Map<String, TableDef> tablesA = normalized(reader, source);
                Map<String, TableDef> tablesB = normalized(reader, copy);
                assertEquals(List.of("editori", "libri", "prestiti", "scrittori", "soci"), List.copyOf(tablesA.keySet()));
                assertEquals(tablesA.keySet(), tablesB.keySet());
                for (String t : tablesA.keySet()) {
                    assertEquals(tablesA.get(t), tablesB.get(t), "TableDef di " + t);
                    ev.append("   ").append(t).append(": TableDef uguali (colonne ").append(tablesA.get(t).columns().size())
                            .append(", indici ").append(tablesA.get(t).indexes().size()).append(", FK ")
                            .append(tablesA.get(t).foreignKeys().size()).append(", AUTO_INCREMENT ")
                            .append(tablesA.get(t).autoIncrementStart()).append(")\n");
                }
                List<String> viewsA = reader.tables(source).stream().filter(TableSummary::isView).map(TableSummary::name).toList();
                List<String> viewsB = reader.tables(copy).stream().filter(TableSummary::isView).map(TableSummary::name).toList();
                assertEquals(viewsA, viewsB);
                Map<String, Long> sumA = checksums(server, source, tablesA.keySet());
                Map<String, Long> sumB = checksums(server, copy, tablesB.keySet());
                assertEquals(sumA, sumB, "CHECKSUM TABLE");
                assertEquals(0L, server.rowCount(copy, "prestiti"));
                ev.append("   viste: ").append(viewsA).append(" in entrambi\n");
                ev.append("   CHECKSUM TABLE origine: ").append(sumA).append('\n');
                ev.append("   CHECKSUM TABLE copia:   ").append(sumB).append('\n');
                ev.append("Esito: stesso risultato finale (nessuna differenza, AUTO_INCREMENT compreso)\n");
            }
        } finally {
            server.dropQuietly(source);
            server.dropQuietly(copy);
        }
        Step3Ui.writeText("T3.8-interfaccia-" + server.id() + ".txt", ev.toString());
    }

    /** Le tabelle del catalogo, per nome, senza il nome del catalogo (per confrontarle con quelle della copia). */
    private static Map<String, TableDef> normalized(MetadataReader reader, String catalog) throws Exception {
        Map<String, TableDef> out = new LinkedHashMap<>();
        for (TableDef t : reader.allTables(catalog)) {
            List<ForeignKeyDef> fks = new ArrayList<>();
            for (ForeignKeyDef fk : t.foreignKeys()) {
                fks.add(fk.withReference(catalog.equalsIgnoreCase(fk.refCatalog()) ? null : fk.refCatalog(),
                        fk.refTable(), fk.refColumns()));
            }
            out.put(t.name(), t.withCatalog("-").withForeignKeys(fks));
        }
        return out;
    }

    private static Map<String, Long> checksums(Step3Server server, String catalog, Iterable<String> tables)
            throws Exception {
        Map<String, Long> out = new LinkedHashMap<>();
        try (Connection c = server.connect(); Statement st = c.createStatement()) {
            for (String t : tables) {
                try (ResultSet rs = st.executeQuery("CHECKSUM TABLE `" + catalog + "`.`" + t + "`")) {
                    rs.next();
                    out.put(t, rs.getLong(2));
                }
            }
        }
        return out;
    }
}
