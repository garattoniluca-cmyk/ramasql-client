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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.dump.DumpWizard;
import it.ramasql.app.dump.ScriptRunTab;
import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.ScriptPreview;

/**
 * Dump e ripristino sul <b>programma vero</b>, contro MariaDB e MySQL, ricontrollati con la connessione del test:
 * <ul>
 *   <li><b>T10.7</b>: procedura guidata «Esporta / Dump» con più cataloghi, {@code DROP IF EXISTS} e
 *       {@code CREATE DATABASE}, un solo file. I cataloghi dell'utente non si toccano: se ne fanno <b>copie</b>
 *       {@code ramasql_test_*}, si esportano, si eliminano («server vuoto» per quei cataloghi) e si ripristinano con
 *       «Esegui script SQL…» (solo dopo aver controllato che il file nomini soltanto cataloghi di test); le copie
 *       ripristinate sono uguali agli originali. In più il dump totale <b>reale</b> di tutti i cataloghi (sola lettura),
 *       che deve contenerli tutti;</li>
 *   <li><b>T10.8</b>: ripristino di un dump prodotto da {@code mariadb-dump} e da {@code mysqldump}, senza errori e con
 *       l'avanzamento; la metà Navicat usa un file in {@code it-tests/fixtures/navicat/} se l'utente l'ha messo;</li>
 *   <li><b>T10.9</b>: script con un errore a metà, con «fermati» e con «continua»: comportamento dichiarato e rapporto con
 *       l'istruzione e la riga dell'errore.</li>
 * </ul>
 */
@Tag("step10")
@Tag("ui")
@Tag("it")
class T107T109DumpSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    private static void executeWithConfirmation(ClientApp a, List<String> seen) {
        a.ws.onPreview = d -> {
            if (seen != null) {
                seen.add(d.confirmation().level() + " · " + d.sqlText().lines().limit(3).toList());
            }
            if (d.requiresTypedConfirmation()) {
                d.confirmationField().setText(d.confirmation().typeToConfirm());
            }
            d.executeButton().doClick();
        };
    }

    // ================================================================ T10.7

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t107_dumpTotaleERipristinoSuCopie(DbServer server) throws Exception {
        List<String> users = DumpUiSupport.userCatalogs(server);
        StringBuilder ev = new StringBuilder("T10.7 — dump totale e ripristino su server «vuoto» (" + server.label()
                + ")\nCataloghi dell'utente (letti soltanto): " + users + "\n");
        Map<String, String> copies = new LinkedHashMap<>();
        try {
            assertFalse(users.isEmpty(), "sul server ci sono cataloghi dell'utente");
            int k = 0;
            for (String u : users) {
                String copy = DbServer.newCatalogName("t107c" + (k++));
                server.createCatalog(copy);
                DumpUiSupport.copy(server, u, copy);
                copies.put(u, copy);
            }
            Map<String, Map<String, String>> originals = new LinkedHashMap<>();
            for (String u : users) {
                originals.put(u, DumpUiSupport.fingerprint(server, u));
            }
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                // 1) il dump delle copie, dalla procedura guidata: DROP IF EXISTS e CREATE DATABASE, un solo file
                DumpWizard w = DumpUiSupport.fromToolbar(a);
                onEdt(w::clearSelection);
                for (String copy : copies.values()) {
                    onEdt(() -> {
                        for (int i = 0; i < w.catalogTable().getRowCount(); i++) {
                            if (copy.equals(w.catalogTable().getValueAt(i, 1))) {
                                w.catalogTable().setValueAt(Boolean.TRUE, i, 0);   // la casella del catalogo
                            }
                        }
                    });
                }
                waitUntil("oggetti letti", ClientApp.TIMEOUT, () -> !w.isLoading()
                        && w.selection().stream().map(i -> i.catalog()).distinct().count() == copies.size());
                onEdt(() -> w.nextButton().doClick());   // → opzioni
                if (copies.size() > 1) {
                    assertTrue(fromEdt(() -> w.createDatabaseCheck().isSelected()), "più cataloghi: CREATE DATABASE");
                    assertFalse(fromEdt(() -> w.createDatabaseCheck().isEnabled()), "e non si può togliere");
                } else {
                    onEdt(() -> w.createDatabaseCheck().doClick());   // un catalogo solo: si sceglie a mano
                }
                onEdt(() -> w.dropCheck().doClick());
                assertTrue(fromEdt(() -> w.createDatabaseCheck().isSelected() && w.dropCheck().isSelected()));
                Path file = dataDir.resolve("totale-copie.sql");
                a.ws.filesToSave.put(FilePrompts.Purpose.DUMP, file);
                onEdt(w::chooseFile);
                onEdt(() -> w.nextButton().doClick());   // → esporta
                onEdt(() -> w.nextButton().doClick());   // «Esporta»
                waitUntil("dump finito", 300_000, () -> !w.isRunning() && w.result() != null);
                assertFalse(w.result().interrupted());
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String copy : copies.values()) {
                    assertTrue(text.contains("DROP DATABASE IF EXISTS `" + copy + "`;"), copy);
                    assertTrue(text.contains("CREATE DATABASE `" + copy + "`"), copy);
                    assertTrue(text.contains("USE `" + copy + "`;"), copy);
                }
                ev.append("Dump delle copie ").append(copies.values()).append(": ").append(fromEdt(
                        () -> w.resultBanner().text())).append('\n');
                PaintSupport.paint(w, "step10", "T10.7-dump-" + server.id() + ".png");

                // 2) «server vuoto»: le copie si eliminano
                ScriptPreview check = ScriptPreview.scan(file, "Esportazione", null);
                assertTrue(check.onlyCatalogsStartingWith("ramasql_test_"),
                        "il file nomina solo cataloghi di test: " + check.catalogs());
                for (String copy : copies.values()) {
                    server.dropQuietly(copy);
                    assertFalse(server.catalogExists(copy));
                }
                onEdt(() -> a.nav().refreshAll());
                a.waitIdle();

                // 3) ripristino con «Esegui script SQL…»: ricrea tutto
                List<String> confirmations = new ArrayList<>();
                executeWithConfirmation(a, confirmations);
                ScriptRunTab t = DumpUiSupport.scriptTab(a);
                a.ws.filesToOpen.put(FilePrompts.Purpose.RUN_SCRIPT, file);
                onEdt(t::chooseFile);
                DumpUiSupport.awaitScan(t);
                assertEquals("", fromEdt(() -> t.targetCombo().getSelectedItem()), "decide lo script (USE)");
                DumpUiSupport.runAndWait(a, t);
                assertNotNull(t.result());
                assertTrue(t.result().completed(), fromEdt(() -> t.resultBanner().text()));
                assertEquals(ConfirmationPolicy.Level.STRONG.name(), confirmations.get(0).split(" ")[0],
                        "DROP DATABASE nel file: conferma rafforzata");
                for (Map.Entry<String, String> e : copies.entrySet()) {
                    assertTrue(server.catalogExists(e.getValue()), "ricreato " + e.getValue());
                    Map<String, String> restored = DumpUiSupport.fingerprint(server, e.getValue());
                    assertEquals(originals.get(e.getKey()), restored, "copia di " + e.getKey()
                            + " ripristinata uguale all'originale (tabelle, righe, struttura, viste)");
                    ev.append("  ").append(e.getKey()).append(" → ").append(e.getValue()).append(": ")
                            .append(restored.size()).append(" oggetti uguali all'originale ").append(restored.values()
                                    .stream().map(v -> v.substring(0, v.indexOf(" · ", v.indexOf(" · ") + 3))).toList())
                            .append('\n');
                }
                ev.append("Ripristino: ").append(fromEdt(() -> t.resultBanner().text())).append("\nConferma: ")
                        .append(confirmations.get(0)).append('\n');
                PaintSupport.paint(t, "step10", "T10.7-ripristino-" + server.id() + ".png");

                // 4) il dump totale reale di tutti i cataloghi dell'utente (sola lettura, mai ripristinato)
                DumpWizard all = DumpUiSupport.fromToolbar(a);
                onEdt(all::chooseAll);
                waitUntil("oggetti di tutti i cataloghi", ClientApp.TIMEOUT, () -> !all.isLoading()
                        && all.selection().stream().map(i -> i.catalog()).distinct().count() >= users.size());
                onEdt(() -> all.nextButton().doClick());
                onEdt(() -> all.dropCheck().setSelected(true));
                Path real = dataDir.resolve("totale-reale.sql");
                a.ws.filesToSave.put(FilePrompts.Purpose.DUMP, real);
                onEdt(all::chooseFile);
                onEdt(() -> all.nextButton().doClick());
                onEdt(() -> all.nextButton().doClick());
                waitUntil("dump totale finito", 300_000, () -> !all.isRunning() && all.result() != null);
                String realText = Files.readString(real, StandardCharsets.UTF_8);
                for (String u : users) {
                    assertTrue(realText.contains("CREATE DATABASE `" + u + "`"), "il dump totale contiene " + u);
                }
                ev.append("Dump totale reale (solo lettura, non ripristinato): ").append(Files.size(real))
                        .append(" byte, contiene CREATE DATABASE per ").append(users).append('\n');
                ev.append("Esito: OK\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step10", "T10.7-" + server.id() + ".txt", ev.toString());
            copies.values().forEach(server::dropQuietly);
        }
    }

    // ================================================================ T10.8

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t108_dumpDiMysqldumpEMariadbDump(DbServer server) throws Exception {
        String srcMaria = DbServer.newCatalogName("t108mdb");
        String srcMy = DbServer.newCatalogName("t108my");
        List<String> targets = new ArrayList<>();
        StringBuilder ev = new StringBuilder("T10.8 — ripristino di dump prodotti da altri programmi (" + server.label()
                + ")\n");
        try {
            // mariadb-dump (MariaDB 11.5; il suo mysqldump.exe è lo stesso programma, byte per byte) dal server MariaDB,
            // con un INSERT per riga per una tabella da 20 000 righe: un file lungo, per vedere l'avanzamento
            DbServer.MARIADB.createCatalog(srcMaria);
            DbServer.MARIADB.loadFixture(srcMaria, "biblioteca.sql");
            DbServer.MARIADB.run("CREATE TABLE `" + srcMaria + "`.registro (id INT PRIMARY KEY, nota VARCHAR(40))");
            DbServer.MARIADB.run("INSERT INTO `" + srcMaria + "`.registro WITH RECURSIVE n(k) AS (SELECT 1 UNION ALL"
                    + " SELECT k + 1 FROM n WHERE k < 200) SELECT (a.k - 1) * 200 + b.k, CONCAT('nota ', a.k, '-', b.k)"
                    + " FROM n a, n b WHERE (a.k - 1) * 200 + b.k <= 20000");
            // il mysqldump vero di Oracle (con MySQL Workbench 8.0) dal server MySQL
            DbServer.MYSQL.createCatalog(srcMy);
            DbServer.MYSQL.loadFixture(srcMy, "biblioteca.sql");
            record Source(Path file, DbServer from, String catalog, Map<String, String> original) {
            }
            List<Source> dumps = new ArrayList<>();
            Path mdb = dataDir.resolve("mariadb-dump.sql");
            ExternalDump.runWith(DbServer.MARIADB, "mariadb-dump.exe", srcMaria, List.of("--skip-extended-insert"), mdb);
            dumps.add(new Source(mdb, DbServer.MARIADB, srcMaria, DumpUiSupport.fingerprint(DbServer.MARIADB, srcMaria)));
            Path oracle = dataDir.resolve("mysqldump-oracle.sql");
            ExternalDump.runOracle(DbServer.MYSQL, srcMy, oracle);
            dumps.add(new Source(oracle, DbServer.MYSQL, srcMy, DumpUiSupport.fingerprint(DbServer.MYSQL, srcMy)));
            Path navicat = Probe.projectRoot().resolve("it-tests/fixtures/navicat");
            boolean hasNavicat = Files.isDirectory(navicat);
            if (hasNavicat) {
                // un dump di Navicat della biblioteca messo lì dall'utente: si ripristina come gli altri
                try (var files = Files.list(navicat)) {
                    for (Path f : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                        String probe = DbServer.newCatalogName("t108nav");
                        DbServer.MARIADB.createCatalog(probe);
                        DbServer.MARIADB.loadFixture(probe, "biblioteca.sql");
                        dumps.add(new Source(f, null, null, DumpUiSupport.fingerprint(DbServer.MARIADB, probe)));
                        DbServer.MARIADB.dropQuietly(probe);
                    }
                }
            }
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                executeWithConfirmation(a, null);
                for (Source s : dumps) {
                    Path dump = s.file();
                    // regola di sicurezza dei test: il file non deve nominare cataloghi che non siano di test
                    ScriptPreview check = ScriptPreview.scan(dump, "x", null);
                    assertTrue(check.onlyCatalogsStartingWith("ramasql_test_") && !check.hasUse()
                            && !check.createsCatalog(), dump.getFileName() + " nomina cataloghi: " + check.catalogs()
                            + " " + check.qualifiedCatalogs());
                    String target = DbServer.newCatalogName("t108dst");
                    server.createCatalog(target);
                    targets.add(target);
                    onEdt(() -> a.nav().refreshAll());
                    a.waitIdle();
                    ScriptRunTab t = DumpUiSupport.scriptTab(a);
                    a.ws.filesToOpen.put(FilePrompts.Purpose.RUN_SCRIPT, dump);
                    onEdt(t::chooseFile);
                    DumpUiSupport.awaitScan(t);
                    waitUntil("catalogo nell'elenco", ClientApp.TIMEOUT, () -> DumpUiSupport.hasTarget(t, target));
                    assertEquals(ScriptRunTab.NONE, fromEdt(() -> t.targetCombo().getSelectedItem()),
                            "nessun catalogo scelto al posto dell'utente");
                    assertFalse(fromEdt(() -> t.runButton().isEnabled()), "«Esegui» spento finché non si sceglie");
                    onEdt(() -> t.targetCombo().setSelectedItem(target));
                    String summary = fromEdt(() -> t.fileSummary().getText());
                    // si esegue, e intanto si guarda l'avanzamento
                    List<String> seen = new ArrayList<>();
                    onEdt(() -> t.runButton().doClick());
                    long t0 = System.currentTimeMillis();
                    while (fromEdt(t::isRunning) && System.currentTimeMillis() - t0 < 600_000) {
                        String label = fromEdt(() -> t.progressLabel().getText() + " · " + t.progress().getValue());
                        if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(label)) {
                            seen.add(label);
                        }
                        Thread.sleep(40);
                    }
                    a.waitIdle();
                    assertNotNull(t.result());
                    assertTrue(t.result().completed(), dump.getFileName() + ": " + fromEdt(() -> t.resultBanner().text())
                            + " " + t.result().failures());
                    String progress = fromEdt(() -> t.progressLabel().getText());
                    assertTrue(progress.contains(t.result().executed() + " istruzioni eseguite"), progress);
                    List<String> during = seen.stream().filter(x -> x.matches("\\d+ istruzioni eseguite su \\d+ · \\d+")
                            && !x.startsWith(t.result().executed() + " ")).toList();
                    if (t.result().executed() > 5000) {
                        assertTrue(during.size() >= 2, "avanzamento visto durante l'esecuzione: " + seen);
                    }
                    Map<String, String> restored = DumpUiSupport.fingerprint(server, target);
                    Map<String, String> original = new java.util.TreeMap<>(s.original());
                    restored.keySet().retainAll(original.keySet());
                    boolean otherServer = s.from() != server;
                    assertEquals(DumpUiSupport.dataOnly(original, otherServer),
                            DumpUiSupport.dataOnly(restored, otherServer),
                            "biblioteca ripristinata uguale (da un altro server: stesse righe e viste)");
                    ev.append(dump.getFileName()).append(" (").append(Files.size(dump)).append(" byte, da ")
                            .append(s.from() == null ? "Navicat" : s.from().label()).append("): ")
                            .append(t.result().executed()).append(" istruzioni, 0 errori; riepilogo del file «")
                            .append(summary.replace('\n', ' ')).append("»; avanzamento visto durante l'esecuzione: ")
                            .append(during.size() > 6 ? during.subList(0, 6) + "…" : during).append("; alla fine «")
                            .append(progress).append("»; ").append(original.size())
                            .append(" oggetti ripristinati uguali all'originale\n");
                    PaintSupport.paint(t, "step10", "T10.8-" + dump.getFileName().toString().replace(".sql", "")
                            + "-" + server.id() + ".png");
                }
                ev.append(hasNavicat ? "Navicat: file presente in it-tests/fixtures/navicat (vedi sopra)\n"
                        : "Navicat: nessun file in it-tests/fixtures/navicat — la metà Navicat resta all'utente "
                                + "(procedura in test-results/step10/T10.8-navicat-procedura.md)\n");
                ev.append("Esito: OK\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step10", "T10.8-" + server.id() + ".txt", ev.toString());
            DbServer.MARIADB.dropQuietly(srcMaria);
            DbServer.MYSQL.dropQuietly(srcMy);
            targets.forEach(server::dropQuietly);
        }
    }

    // ================================================================ T10.9

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t109_erroreAMetaFermatiOContinua(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t109");
        StringBuilder ev = new StringBuilder("T10.9 — script con un errore a metà (" + server.label() + ")\n");
        String script = "-- prova: la terza istruzione sbaglia\n"
                + "CREATE TABLE prima (id INT PRIMARY KEY);\n"
                + "INSERT INTO prima VALUES (1), (2);\n"
                + "\n"
                + "INSERT INTO tabella_che_non_esiste VALUES (3);\n"
                + "CREATE TABLE dopo (id INT PRIMARY KEY);\n"
                + "INSERT INTO dopo VALUES (9);\n";
        Path file = dataDir.resolve("errore.sql");
        Files.writeString(file, script, StandardCharsets.UTF_8);
        try {
            server.createCatalog(catalog);
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                executeWithConfirmation(a, null);
                // «fermati»
                ScriptRunTab t = DumpUiSupport.scriptTab(a);
                a.ws.filesToOpen.put(FilePrompts.Purpose.RUN_SCRIPT, file);
                onEdt(t::chooseFile);
                DumpUiSupport.awaitScan(t);
                waitUntil("catalogo nell'elenco", ClientApp.TIMEOUT, () -> DumpUiSupport.hasTarget(t, catalog));
                assertEquals(ScriptRunTab.NONE, fromEdt(() -> t.targetCombo().getSelectedItem()), "nessuna scelta"
                        + " predefinita: il file non ha USE e nessun catalogo è selezionato nel navigatore");
                assertFalse(fromEdt(() -> t.runButton().isEnabled()));
                onEdt(() -> t.targetCombo().setSelectedItem(catalog));
                assertTrue(fromEdt(() -> t.stopRadio().isSelected()), "predefinito: fermati");
                DumpUiSupport.runAndWait(a, t);
                assertTrue(t.result().stoppedOnError());
                assertEquals(3, t.result().executed());
                assertEquals(1, t.result().failureCount());
                assertEquals(5, t.result().failures().get(0).line(), "riga dell'errore nel file");
                String row = fromEdt(() -> t.failureTable().getValueAt(0, 0) + " | " + t.failureTable().getValueAt(0, 1)
                        + " | " + t.failureTable().getValueAt(0, 2));
                assertTrue(row.startsWith("5 | INSERT INTO tabella_che_non_esiste") && row.contains("[1146]")
                        && row.contains("non esiste"), row);
                assertTrue(server.tableExists(catalog, "prima"));
                assertFalse(server.tableExists(catalog, "dopo"), "dopo l'errore non si va avanti");
                assertEquals(2, server.rowCount(catalog, "prima"));
                ev.append("Fermati: ").append(fromEdt(() -> t.resultBanner().text())).append("\n  rapporto: ").append(row)
                        .append("\n  sul server: «prima» con 2 righe, «dopo» non c'è\n");
                PaintSupport.paint(t, "step10", "T10.9-fermati-" + server.id() + ".png");

                // «continua», su un catalogo ripulito
                server.run("DROP TABLE IF EXISTS `" + catalog + "`.prima");
                ScriptRunTab t2 = DumpUiSupport.scriptTab(a);
                a.ws.filesToOpen.put(FilePrompts.Purpose.RUN_SCRIPT, file);
                onEdt(t2::chooseFile);
                DumpUiSupport.awaitScan(t2);
                waitUntil("catalogo nell'elenco", ClientApp.TIMEOUT, () -> DumpUiSupport.hasTarget(t2, catalog));
                onEdt(() -> t2.targetCombo().setSelectedItem(catalog));
                onEdt(() -> t2.continueRadio().doClick());
                DumpUiSupport.runAndWait(a, t2);
                assertFalse(t2.result().stoppedOnError());
                assertEquals(5, t2.result().executed());
                assertEquals(4, t2.result().succeeded());
                assertEquals(1, t2.result().failureCount());
                assertTrue(server.tableExists(catalog, "dopo"), "si è andati avanti");
                assertEquals(1, server.rowCount(catalog, "dopo"));
                ev.append("Continua: ").append(fromEdt(() -> t2.resultBanner().text()))
                        .append("\n  sul server: «prima» e «dopo» ci sono\n");
                PaintSupport.paint(t2, "step10", "T10.9-continua-" + server.id() + ".png");
                // nel registro l'istruzione che ha sbagliato, con la riga del file, e il riassunto del file
                assertTrue(a.log().entries().stream().anyMatch(e -> !e.isOk() && e.sql().contains("tabella_che_non_esiste")
                        && e.note().contains("riga 5")));
                assertTrue(a.log().entries().stream().anyMatch(e -> e.sql().startsWith("-- file «errore.sql»: 5 istruzioni")
                        && e.origin().equals("Script da file")), "riga riassuntiva del file");
                ev.append("Esito: OK\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step10", "T10.9-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }


    // ================================================================ T10.5 / T10.9: interruzioni dall'interfaccia

    /**
     * «Interrompi» dal programma vero: un dump interrotto non scrive nessun file e lascia intatto quello che c'era con lo
     * stesso nome; un ripristino interrotto lascia la sessione cambiata dal file (modalità SQL, fuso, controlli delle
     * chiavi) e la scheda propone, dalla pipeline, le istruzioni che la rimettono com'era.
     */
    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t109_interruzioniDallInterfaccia(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t109int");
        String target = DbServer.newCatalogName("t109intdst");
        StringBuilder ev = new StringBuilder("T10.5/T10.9 — «Interrompi» nel dump e nel ripristino (" + server.label()
                + ")\n");
        try {
            server.createCatalog(catalog);
            server.createCatalog(target);
            server.run("CREATE TABLE `" + catalog + "`.grande (id INT PRIMARY KEY, v VARCHAR(60)) ENGINE=InnoDB");
            server.run("INSERT INTO `" + catalog + "`.grande WITH RECURSIVE n(k) AS (SELECT 1 UNION ALL SELECT k + 1"
                    + " FROM n WHERE k < 1000) SELECT (a.k - 1) * 1000 + b.k, CONCAT('valore numero ', a.k, '/', b.k)"
                    + " FROM n a, n b WHERE (a.k - 1) * 1000 + b.k <= 300000");
            Path previous = dataDir.resolve("precedente.sql");
            Files.writeString(previous, "-- un dump buono di ieri\nSELECT 1;\n", StandardCharsets.UTF_8);
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                executeWithConfirmation(a, null);
                // 1) dump interrotto sullo stesso nome di un file che c'è già
                DumpWizard w = DumpUiSupport.fromToolbar(a);
                onEdt(w::clearSelection);
                onEdt(() -> {
                    for (int i = 0; i < w.catalogTable().getRowCount(); i++) {
                        if (catalog.equals(w.catalogTable().getValueAt(i, 1))) {
                            w.catalogTable().setValueAt(Boolean.TRUE, i, 0);
                        }
                    }
                });
                waitUntil("oggetti letti", ClientApp.TIMEOUT, () -> !w.isLoading() && !w.selection().isEmpty());
                onEdt(() -> w.nextButton().doClick());
                onEdt(() -> w.rowsSpinner().setValue(1));   // un INSERT per riga: un file lungo
                a.ws.filesToSave.put(FilePrompts.Purpose.DUMP, previous);
                onEdt(w::chooseFile);
                onEdt(() -> w.nextButton().doClick());
                onEdt(() -> w.nextButton().doClick());   // «Esporta»
                waitUntil("righe in scrittura", 120_000, () -> w.progressLabel().getText().contains("righe scritte"));
                onEdt(w::interrupt);
                waitUntil("dump fermato", 120_000, () -> !w.isRunning() && w.result() != null);
                assertTrue(w.result().interrupted());
                assertEquals("-- un dump buono di ieri\nSELECT 1;\n", Files.readString(previous, StandardCharsets.UTF_8),
                        "il file che c'era resta com'era");
                assertFalse(Files.exists(dataDir.resolve("precedente.sql.part")), "nessun file a metà");
                String banner = fromEdt(() -> w.resultBanner().text());
                assertTrue(banner.contains("nessun file scritto"), banner);
                ev.append("Dump interrotto dopo ").append(w.result().rows()).append(" righe: «").append(banner)
                        .append("»; «precedente.sql» identico, nessun .part\n");
                PaintSupport.paint(w, "step10", "T10.5-dump-interrotto-" + server.id() + ".png");

                // 2) il dump completo, poi il ripristino interrotto
                Path file = dataDir.resolve("completo.sql");
                DumpWizard w2 = DumpUiSupport.fromToolbar(a);
                onEdt(w2::clearSelection);
                onEdt(() -> {
                    for (int i = 0; i < w2.catalogTable().getRowCount(); i++) {
                        if (catalog.equals(w2.catalogTable().getValueAt(i, 1))) {
                            w2.catalogTable().setValueAt(Boolean.TRUE, i, 0);
                        }
                    }
                });
                waitUntil("oggetti letti", ClientApp.TIMEOUT, () -> !w2.isLoading() && !w2.selection().isEmpty());
                onEdt(() -> w2.nextButton().doClick());
                onEdt(() -> w2.rowsSpinner().setValue(1));
                a.ws.filesToSave.put(FilePrompts.Purpose.DUMP, file);
                onEdt(w2::chooseFile);
                onEdt(() -> w2.nextButton().doClick());
                onEdt(() -> w2.nextButton().doClick());
                waitUntil("dump finito", 600_000, () -> !w2.isRunning() && w2.result() != null);
                assertFalse(w2.result().interrupted());
                onEdt(() -> a.nav().refreshAll());
                a.waitIdle();
                ScriptRunTab t = DumpUiSupport.scriptTab(a);
                a.ws.filesToOpen.put(FilePrompts.Purpose.RUN_SCRIPT, file);
                onEdt(t::chooseFile);
                DumpUiSupport.awaitScan(t);
                waitUntil("catalogo nell'elenco", ClientApp.TIMEOUT, () -> DumpUiSupport.hasTarget(t, target));
                String summary = fromEdt(() -> t.fileSummary().getText());
                assertTrue(summary.contains("SQL_MODE") && summary.contains("TIME_ZONE"), summary);
                onEdt(() -> t.targetCombo().setSelectedItem(target));
                int logBefore = a.log().size();
                onEdt(() -> t.runButton().doClick());
                waitUntil("ripristino avviato", 120_000, () -> t.progress().getValue() > 50);
                onEdt(t::interrupt);
                waitUntil("ripristino fermato", 120_000, () -> !t.isRunning());
                a.waitIdle();
                assertTrue(t.result().interrupted());
                assertTrue(fromEdt(() -> t.sessionButton().isVisible()), "pulsante per rimettere la sessione");
                String result = fromEdt(() -> t.resultBanner().text());
                assertTrue(result.contains("impostazioni della sessione"), result);
                assertTrue(a.log().size() - logBefore < 30, "registro: una riga per istruzione strutturale, non per"
                        + " INSERT (" + (a.log().size() - logBefore) + ")");
                PaintSupport.paint(t, "step10", "T10.9-ripristino-interrotto-" + server.id() + ".png");
                onEdt(() -> t.sessionButton().doClick());
                ScriptResultCheck.completed(a);
                List<String> restoreSql = a.log().entries().subList(a.log().size() - t.result().restore().size(),
                        a.log().size()).stream().map(e -> e.sql()).toList();
                assertEquals(t.result().restore(), restoreSql, "eseguite dalla pipeline, nel registro");
                assertFalse(fromEdt(() -> t.sessionButton().isVisible()));
                ev.append("Ripristino interrotto dopo ").append(t.result().executed()).append(" istruzioni: «")
                        .append(result.replace('\n', ' ')).append("»\nRimesse dalla pipeline: ").append(restoreSql)
                        .append('\n');
            }
            ev.append("Esito: OK\n");
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step10", "T10.9-interruzioni-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
            server.dropQuietly(target);
        }
    }

    /** L'ultima proposta della pipeline è stata eseguita tutta. */
    private static final class ScriptResultCheck {
        private ScriptResultCheck() {
        }

        static void completed(ClientApp a) throws Exception {
            var r = a.awaitLastProposal();
            assertNotNull(r, "proposta eseguita");
            assertTrue(r.completed(), String.valueOf(r.failure()));
        }
    }
}
