/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.dump.DumpContent;
import it.ramasql.core.dump.DumpOptions;
import it.ramasql.core.dump.Dumper;
import it.ramasql.core.exec.ScriptFileResult;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * Dump e ripristino contro i server veri, con il codice del client:
 * <ul>
 *   <li><b>T10.3</b>: round-trip totale di {@code biblioteca} in un catalogo nuovo: stessi oggetti, metadati identici
 *       (colonne, indici, chiavi esterne con le stesse azioni, engine, charset), {@code CHECKSUM TABLE} identico per
 *       ogni tabella, viste funzionanti;</li>
 *   <li><b>T10.4</b>: dump selettivo (solo struttura di {@code autori}, solo dati di {@code editori}, entrambi di
 *       {@code libri}): il file contiene esattamente quello e nient'altro;</li>
 *   <li><b>T10.5</b>: casi difficili — {@code biblioteca_myisam}, tabella con spazi nel nome, BLOB da 5 MB, tabella da un
 *       milione di righe — round-trip riuscito con memoria stabile;</li>
 *   <li><b>T10.6</b>: dump da MariaDB ripristinato su MySQL e viceversa: struttura e dati uguali (collation esplicite
 *       nella biblioteca; differenze documentate nel diario).</li>
 * </ul>
 */
@Tag("step10")
@Tag("it")
class T103T106DumpSulServerTest {

    @TempDir
    Path dir;

    private static String id(ItServers s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    // ================================================================ T10.3

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t103_roundTripTotaleDellaBiblioteca(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t103_src"); TestCatalog dst = TestCatalog.create(server,
                "t103_dst"); DumpSupport.Client c = DumpSupport.Client.of(server)) {
            src.runScript("/fixtures/biblioteca.sql");
            Path file = dir.resolve("biblioteca.sql");
            Dumper.Result r = c.dump(c.whole(src.name()), DumpOptions.defaults(), file);
            assertFalse(r.interrupted());
            assertEquals(6, r.tables());
            assertEquals(2, r.views());
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(text.lines().filter(l -> !l.startsWith("--")).noneMatch(l -> l.contains(src.name())),
                    "le istruzioni non nominano il catalogo d'origine (solo i commenti): si ripristina altrove");
            ScriptFileResult restored = c.restore(file, dst.name());
            assertTrue(restored.completed(), String.valueOf(restored.failures()));
            c.reader().invalidateAll();
            List<String> structure = DumpSupport.sameStructure(c.reader(), src.name(), c.reader(), dst.name());
            Map<String, String> before = DumpSupport.checksums(src.connection(), c.reader(), src.name());
            Map<String, String> after = DumpSupport.checksums(src.connection(), c.reader(), dst.name());
            assertEquals(before, after, "CHECKSUM TABLE identico per ogni tabella");
            List<String> views = new ArrayList<>();
            for (TableSummary v : c.reader().tables(src.name())) {
                if (v.isView()) {
                    List<List<String>> a = DumpSupport.rows(src.connection(), "SELECT * FROM `" + src.name() + "`.`"
                            + v.name() + "` ORDER BY 1");
                    List<List<String>> b = DumpSupport.rows(src.connection(), "SELECT * FROM `" + dst.name() + "`.`"
                            + v.name() + "` ORDER BY 1");
                    assertEquals(a, b, "la vista " + v.name() + " ripristinata dà le stesse righe");
                    assertTrue(!a.isEmpty());
                    views.add(v.name() + ": " + a.size() + " righe uguali");
                }
            }
            TestResults.write("step10", "T10.3-" + id(server) + ".txt", "T10.3 — round-trip totale di biblioteca ("
                    + server.label() + ")\nDump: " + r.tables() + " tabelle, " + r.views() + " viste, " + r.rows()
                    + " righe, " + r.statements() + " istruzioni (" + Files.size(file) + " byte)\nRipristino: "
                    + restored.executed() + " istruzioni, " + restored.failureCount() + " errori\nStruttura:\n  "
                    + String.join("\n  ", structure) + "\nCHECKSUM TABLE: " + before + " = " + after + "\nViste: "
                    + views + "\nEsito: OK\n");
        }
    }

    // ================================================================ T10.4

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t104_dumpSelettivo(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t104"); DumpSupport.Client c = DumpSupport.Client.of(server)) {
            src.runScript("/fixtures/biblioteca.sql");
            Path file = dir.resolve("selettivo.sql");
            List<Dumper.Item> items = List.of(new Dumper.Item(src.name(), "autori", false, DumpContent.STRUCTURE),
                    new Dumper.Item(src.name(), "editori", false, DumpContent.DATA),
                    new Dumper.Item(src.name(), "libri", false, DumpContent.BOTH));
            c.dump(items, DumpOptions.defaults(), file);
            List<String> statements = it.ramasql.core.exec.StatementSplitter.split(Files.readString(file,
                    StandardCharsets.UTF_8)).stream().map(it.ramasql.core.exec.StatementSplitter.SplitStatement::text)
                    .filter(s -> !s.startsWith("SET ")).toList();
            List<String> creates = statements.stream().filter(s -> s.startsWith("CREATE")).toList();
            assertEquals(2, creates.size(), "solo i CREATE di autori e libri: " + creates);
            assertTrue(creates.stream().anyMatch(s -> s.startsWith("CREATE TABLE `autori`")));
            assertTrue(creates.stream().anyMatch(s -> s.startsWith("CREATE TABLE `libri`")));
            assertTrue(statements.stream().noneMatch(s -> s.startsWith("INSERT INTO `autori`")), "autori senza dati");
            assertTrue(statements.stream().noneMatch(s -> s.startsWith("CREATE TABLE `editori`")),
                    "editori senza struttura");
            long editori = statements.stream().filter(s -> s.startsWith("INSERT INTO `editori`")).count();
            long libri = statements.stream().filter(s -> s.startsWith("INSERT INTO `libri`")).count();
            assertTrue(editori >= 1 && libri >= 1);
            List<String> others = statements.stream().filter(s -> !s.startsWith("CREATE TABLE `autori`")
                    && !s.startsWith("CREATE TABLE `libri`") && !s.startsWith("INSERT INTO `editori`")
                    && !s.startsWith("INSERT INTO `libri`")).toList();
            assertEquals(List.of(), others, "nient'altro nel file");
            // l'ordine: editori (dati) prima di libri, che lo riferisce
            int e = statements.indexOf(statements.stream().filter(s -> s.startsWith("INSERT INTO `editori`")).findFirst()
                    .orElseThrow());
            int l = statements.indexOf(creates.stream().filter(s -> s.startsWith("CREATE TABLE `libri`")).findFirst()
                    .orElseThrow());
            assertTrue(e < l, "editori prima di libri");
            // le righe: 20 editori e 200 libri
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertEquals(20, count(text, "INSERT INTO `editori`"));
            assertEquals(200, count(text, "INSERT INTO `libri`"));
            TestResults.write("step10", "T10.4-" + id(server) + ".txt", "T10.4 — dump selettivo (" + server.label()
                    + ")\nIstruzioni nel file (tolte le SET di inizio e fine):\n" + String.join("\n", statements.stream()
                            .map(s -> "  " + s.lines().findFirst().orElse("") + (s.contains("\n") ? " …" : "")).toList())
                    + "\n1 CREATE per autori senza INSERT, soli INSERT per editori (20 righe), CREATE e INSERT per libri"
                    + " (200 righe), nient'altro\nEsito: OK\n");
        }
    }

    /** Righe di valori nelle INSERT di una tabella (una riga per «(» a inizio riga dopo VALUES). */
    private static int count(String dump, String insertHead) {
        int n = 0;
        for (String stmt : it.ramasql.core.exec.StatementSplitter.split(dump).stream()
                .map(it.ramasql.core.exec.StatementSplitter.SplitStatement::text).toList()) {
            if (stmt.startsWith(insertHead)) {
                n += (int) stmt.lines().filter(line -> line.startsWith("(")).count();
            }
        }
        return n;
    }

    // ================================================================ T10.5

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t105_myisamNomiConSpaziEBlob(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t105_src"); TestCatalog dst = TestCatalog.create(server,
                "t105_dst"); DumpSupport.Client c = DumpSupport.Client.of(server)) {
            src.runScript("/fixtures/biblioteca_myisam.sql");
            src.execute("CREATE TABLE `nota con spazi` (`id numero` INT NOT NULL PRIMARY KEY, `il testo` TEXT,"
                    + " `dati` LONGBLOB, `bit` BIT(9), `quando` DATETIME(3), `v` DECIMAL(12,4)) ENGINE=InnoDB");
            byte[] blob = new byte[5 * 1024 * 1024];
            new Random(42).nextBytes(blob);
            try (PreparedStatement ps = src.connection().prepareStatement("INSERT INTO `nota con spazi` VALUES (?, ?, ?,"
                    + " b'101010101', '2026-09-28 08:30:00.123', -12.5)")) {
                ps.setInt(1, 1);
                ps.setString(2, "l'ora \\ «virgolette» \"doppie\" 😀\nriga due\ttab");
                ps.setBytes(3, blob);
                ps.executeUpdate();
                ps.setInt(1, 2);
                ps.setString(2, "");
                ps.setBytes(3, new byte[0]);
                ps.executeUpdate();
            }
            Path file = dir.resolve("difficili.sql");
            Runtime rt = Runtime.getRuntime();
            System.gc();
            long base = rt.totalMemory() - rt.freeMemory();
            Dumper.Result r = c.dump(c.whole(src.name()), DumpOptions.defaults(), file);
            System.gc();
            long afterDump = rt.totalMemory() - rt.freeMemory();
            ScriptFileResult restored = c.restore(file, dst.name());
            assertTrue(restored.completed(), String.valueOf(restored.failures()));
            c.reader().invalidateAll();
            DumpSupport.sameStructure(c.reader(), src.name(), c.reader(), dst.name());
            assertEquals(DumpSupport.checksums(src.connection(), c.reader(), src.name()),
                    DumpSupport.checksums(src.connection(), c.reader(), dst.name()));
            TableDef nota = c.reader().table(dst.name(), "nota con spazi").orElseThrow();
            assertEquals(DumpSupport.contentHash(src.connection(), src.name(), nota),
                    DumpSupport.contentHash(src.connection(), dst.name(), nota), "BLOB da 5 MB, BIT, emoji identici");
            assertEquals("MyISAM", c.reader().table(dst.name(), "libri").orElseThrow().engine());
            TestResults.write("step10", "T10.5-difficili-" + id(server) + ".txt", "T10.5 — biblioteca_myisam, tabella"
                    + " «nota con spazi» (colonne con spazi, LONGBLOB da 5 MB, BIT(9), DATETIME(3), DECIMAL, emoji,"
                    + " apici, barre, a-capo) (" + server.label() + ")\nDump: " + Files.size(file) + " byte, "
                    + r.statements() + " istruzioni\nRipristino: " + restored.executed() + " istruzioni, 0 errori\n"
                    + "Struttura uguale, CHECKSUM TABLE uguale per ogni tabella, contenuto della tabella con il BLOB"
                    + " identico byte per byte\nMemoria dopo il dump: " + (afterDump - base) / (1024 * 1024) + " MB in più"
                    + "\nEsito: OK\n");
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t105_unMilioneDiRighe(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t105_grande"); TestCatalog dst = TestCatalog.create(server,
                "t105_grande_dst"); DumpSupport.Client c = DumpSupport.Client.of(server)) {
            src.execute("CREATE TABLE grande (id INT NOT NULL PRIMARY KEY, nome VARCHAR(40) NOT NULL, valore DECIMAL(8,2))"
                    + " ENGINE=InnoDB");
            src.execute("INSERT INTO grande WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1000)"
                    + " SELECT a.i * 1000 + b.i - 1000, CONCAT('riga ', a.i * 1000 + b.i - 1000), (a.i + b.i) / 7"
                    + " FROM n a, n b");
            assertEquals("1000000", DumpSupport.rows(src.connection(), "SELECT COUNT(*) FROM grande").get(0).get(0));
            Path file = dir.resolve("grande.sql");
            Runtime rt = Runtime.getRuntime();
            System.gc();
            long base = rt.totalMemory() - rt.freeMemory();
            Dumper.Result r = c.dump(c.whole(src.name()), DumpOptions.defaults(), file);
            System.gc();
            long afterDump = rt.totalMemory() - rt.freeMemory();
            assertEquals(1_000_000, r.rows());
            ScriptFileResult restored = c.restore(file, dst.name());
            System.gc();
            long afterRestore = rt.totalMemory() - rt.freeMemory();
            assertTrue(restored.completed(), String.valueOf(restored.failures()));
            assertEquals(DumpSupport.checksums(src.connection(), c.reader(), src.name()),
                    DumpSupport.checksums(src.connection(), c.reader(), dst.name()));
            long fileMb = Files.size(file) / (1024 * 1024);
            long growDump = Math.max(0, afterDump - base) / (1024 * 1024);
            long growRestore = Math.max(0, afterRestore - base) / (1024 * 1024);
            // il registro tiene solo le istruzioni strutturali e una riga riassuntiva del file, non le 10 000 INSERT
            assertTrue(growDump < 48 && growRestore < 48 && fileMb > 20,
                    "memoria: +" + growDump + " MB dopo il dump, +" + growRestore + " MB dopo il ripristino, file "
                            + fileMb + " MB");
            TestResults.write("step10", "T10.5-milione-" + id(server) + ".txt", "T10.5 — tabella da 1 000 000 di righe ("
                    + server.label() + ")\nDump: " + fileMb + " MB, " + r.statements() + " istruzioni; ripristino: "
                    + restored.executed() + " istruzioni in " + restored.durationMillis() / 1000 + " s, 0 errori\n"
                    + "CHECKSUM TABLE uguale\nMemoria dopo la raccolta: +" + growDump + " MB dopo il dump, +" + growRestore
                    + " MB dopo il ripristino (il file non si tiene in memoria)\nEsito: OK\n");
        }
    }

    // ================================================================ T10.6

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t106_daUnServerAllAltro(ItServers from) throws Exception {
        ItServers to = from == ItServers.MARIADB ? ItServers.MYSQL : ItServers.MARIADB;
        try (TestCatalog src = TestCatalog.create(from, "t106_src"); TestCatalog dst = TestCatalog.create(to, "t106_dst");
                DumpSupport.Client a = DumpSupport.Client.of(from); DumpSupport.Client b = DumpSupport.Client.of(to)) {
            src.runScript("/fixtures/biblioteca.sql");
            Path file = dir.resolve("da-" + id(from) + ".sql");
            a.dump(a.whole(src.name()), DumpOptions.defaults(), file);
            ScriptFileResult restored = b.restore(file, dst.name());
            assertTrue(restored.completed(), String.valueOf(restored.failures()));
            b.reader().invalidateAll();
            // fra i due server l'azione predefinita delle chiavi esterne si rilegge RESTRICT (MariaDB) o NO ACTION
            // (MySQL): in InnoDB sono la stessa regola (BUG-014), quindi il confronto le considera uguali e lo dice
            List<String> structure = DumpSupport.sameStructure(a.reader(), src.name(), b.reader(), dst.name(), true);
            List<String> data = new ArrayList<>();
            for (TableSummary t : a.reader().tables(src.name())) {
                if (!t.isView()) {
                    TableDef def = a.reader().table(src.name(), t.name()).orElseThrow();
                    String h1 = DumpSupport.contentHash(src.connection(), src.name(), def);
                    String h2 = DumpSupport.contentHash(dst.connection(), dst.name(), def);
                    assertEquals(h1, h2, "stesse righe in " + t.name());
                    data.add(t.name() + " " + h1.substring(0, h1.indexOf(':')) + " righe, impronta uguale");
                }
            }
            assertEquals(DumpSupport.rows(src.connection(), "SELECT COUNT(*) FROM v_prestiti_aperti"),
                    DumpSupport.rows(dst.connection(), "SELECT COUNT(*) FROM v_prestiti_aperti"));
            TestResults.write("step10", "T10.6-da-" + id(from) + "-a-" + id(to) + ".txt", "T10.6 — dump da "
                    + from.label() + " ripristinato su " + to.label() + "\nRipristino: " + restored.executed()
                    + " istruzioni, 0 errori\nStruttura (lettore del client):\n  " + String.join("\n  ", structure)
                    + "\nDati: " + data + "\nViste funzionanti.\nDifferenza fra i server, gestita: l'azione delle chiavi"
                    + " esterne scritta RESTRICT si rilegge RESTRICT su MariaDB e NO ACTION su MySQL (BUG-014): in InnoDB"
                    + " sono la stessa regola. Collation: la biblioteca le dichiara (utf8mb4_unicode_ci, presente su"
                    + " entrambi), quindi restano uguali.\nEsito: OK\n");
        }
    }
}
