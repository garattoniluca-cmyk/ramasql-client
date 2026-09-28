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

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.dump.DumpContent;
import it.ramasql.core.dump.DumpOptions;
import it.ramasql.core.dump.Dumper;
import it.ramasql.core.exec.ScriptFileListener;
import it.ramasql.core.exec.ScriptFileResult;
import it.ramasql.core.exec.ScriptPreview;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * Le prove chieste dalla revisione indipendente dello Step 10, contro i server veri e con il codice del client:
 * <ul>
 *   <li><b>T10.5, memoria di picco</b>: dump e ripristino di 1 000 000 di righe e del BLOB da 5 MB in un processo con
 *       l'heap limitato ({@code -Xmx}): se il client tenesse il risultato o il file in memoria, finirebbe senza;</li>
 *   <li><b>T10.5, un INSERT per riga</b>: il registro non cresce con le istruzioni del file (una riga riassuntiva);</li>
 *   <li><b>T10.5, tipi difficili</b>: date «zero», TIME negativi e oltre le 24 ore, TIMESTAMP (anche nell'ora
 *       ripetuta, con fusi diversi fra dump e ripristino), JSON, geometrie, colonne generate, ENUM/SET, FLOAT, BIT,
 *       vista che usa un'altra vista, chiavi circolari, tabella che si riferisce a sé stessa;</li>
 *   <li><b>T10.6, collation predefinite</b>: tabelle e catalogo senza collation dichiarata (MariaDB scrive
 *       {@code utf8mb4_uca1400_ai_ci}, MySQL {@code utf8mb4_0900_ai_ci}) da un server all'altro, dump con
 *       {@code CREATE DATABASE};</li>
 *   <li><b>T10.9, sessione</b>: un ripristino interrotto lascia la sessione cambiata e il client propone come rimetterla;
 *       {@code LOCK TABLES} rimasto aperto; transazione aperta dal file; {@code USE} non riuscito con «continua»;
 *       connessione caduta; interruzione di un dump mentre il server prepara le righe.</li>
 * </ul>
 */
@Tag("step10")
@Tag("it")
class T10RevisioneSulServerTest {

    @TempDir
    Path dir;

    private static String id(ItServers s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    private static ScriptResult run(DumpSupport.Client c, String... sql) throws Exception {
        return c.executor().submit(SqlScript.of("prova", "Editor SQL", sql), null).get(2, TimeUnit.MINUTES);
    }

    /** Un valore letto dalla sessione del client (la sua connessione, non quella del test). */
    private static String value(DumpSupport.Client c, String expression) throws Exception {
        ScriptResult r = run(c, "SELECT " + expression);
        Object v = r.results().get(0).resultSets().get(0).rows().get(0).get(0);
        return v == null ? null : v.toString();
    }

    /**
     * {@code SELECT <espressioni> FROM} i numeri da 1 a {@code count} (al più 1 000 000), come prodotto di due CTE da
     * 1000: la ricorsione dei server si ferma a 1000 passi.
     */
    private static String numbers(int count, String expressions) {
        return "WITH RECURSIVE n(k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM n WHERE k < 1000),"
                + " m(i) AS (SELECT (a.k - 1) * 1000 + b.k FROM n a, n b) SELECT " + expressions
                + " FROM m WHERE i <= " + count;
    }

    private static String writeFile(Path file, String text) throws Exception {
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return text;
    }

    // ================================================================ T10.5: memoria di picco in un altro processo

    private static String child(String mode, ItServers server, String catalog, Path file, String xmx, int rowsPerInsert)
            throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = List.of(java, "-Xmx" + xmx, "-cp", System.getProperty("java.class.path"),
                DumpChild.class.getName(), mode, server.name(), catalog, file.toString(), String.valueOf(rowsPerInsert));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(20, TimeUnit.MINUTES), mode + " non finito");
        String ok = out.lines().filter(l -> l.startsWith("OK ")).findFirst().orElse("");
        assertEquals(0, p.exitValue(), mode + " con -Xmx" + xmx + ": " + out.lines().filter(l -> l.contains("Error")
                || l.contains("Exception")).limit(5).toList());
        assertFalse(ok.isEmpty(), out);
        return ok;
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t105_piccoDiMemoriaConHeapLimitato(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t105_picco"); TestCatalog dst = TestCatalog.create(server,
                "t105_picco_dst"); TestCatalog blobSrc = TestCatalog.create(server, "t105_blob");
                TestCatalog blobDst = TestCatalog.create(server, "t105_blob_dst")) {
            src.execute("CREATE TABLE grande (id INT NOT NULL PRIMARY KEY, nome VARCHAR(40) NOT NULL, valore DECIMAL(8,2))"
                    + " ENGINE=InnoDB");
            src.execute("INSERT INTO grande WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1000)"
                    + " SELECT a.i * 1000 + b.i - 1000, CONCAT('riga numero ', a.i * 1000 + b.i - 1000), (a.i + b.i) / 7"
                    + " FROM n a, n b");
            blobSrc.execute("CREATE TABLE b (id INT PRIMARY KEY, dati LONGBLOB) ENGINE=InnoDB");
            try (var ps = blobSrc.connection().prepareStatement("INSERT INTO b VALUES (?, ?)")) {
                byte[] blob = new byte[5 * 1024 * 1024];
                new java.util.Random(7).nextBytes(blob);
                for (int i = 1; i <= 3; i++) {
                    ps.setInt(1, i);
                    ps.setBytes(2, blob);
                    ps.executeUpdate();
                }
            }
            Path file = dir.resolve("grande.sql");
            Path blobFile = dir.resolve("blob.sql");
            // 1 000 000 di righe (~40 MB di file, più di 100 MB di oggetti se si tenessero in memoria) con 64 MB di heap
            String dump = child("dump", server, src.name(), file, "64m", 100);
            String restore = child("restore", server, dst.name(), file, "64m", 100);
            assertEquals(DumpSupport.checksums(src.connection(), null, src.name(), List.of("grande")),
                    DumpSupport.checksums(src.connection(), null, dst.name(), List.of("grande")));
            // tre BLOB da 5 MB: un INSERT da ~10 MB di testo alla volta, con 96 MB di heap
            String blobDump = child("dump", server, blobSrc.name(), blobFile, "96m", 1);
            String blobRestore = child("restore", server, blobDst.name(), blobFile, "96m", 1);
            assertEquals(DumpSupport.checksums(src.connection(), null, blobSrc.name(), List.of("b")),
                    DumpSupport.checksums(src.connection(), null, blobDst.name(), List.of("b")));
            TestResults.write("step10", "T10.5-memoria-" + id(server) + ".txt", "T10.5 — memoria di picco misurata in"
                    + " un processo con l'heap limitato (" + server.label() + ")\n1 000 000 di righe, file di "
                    + Files.size(file) / (1024 * 1024) + " MB, -Xmx64m:\n  dump:       " + dump + "\n  ripristino: "
                    + restore + "\n3 BLOB da 5 MB (un INSERT per riga), file di " + Files.size(blobFile) / (1024 * 1024)
                    + " MB, -Xmx96m:\n  dump:       " + blobDump + "\n  ripristino: " + blobRestore
                    + "\nCHECKSUM TABLE uguale in entrambi i casi. peakMb = picco dell'heap usato nel processo;"
                    + " log = righe del registro del client (la riga riassuntiva del file più le sole istruzioni"
                    + " strutturali: non una per INSERT)\nEsito: OK\n");
        }
    }

    // ================================================================ T10.5: un INSERT per riga

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t105_unInsertPerRigaIlRegistroNonCresce(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t105_uno"); TestCatalog dst = TestCatalog.create(server,
                "t105_uno_dst"); DumpSupport.Client c = DumpSupport.Client.of(server)) {
            src.execute("CREATE TABLE r (id INT PRIMARY KEY, v VARCHAR(20)) ENGINE=InnoDB");
            src.execute("INSERT INTO r " + numbers(20_000, "i, CONCAT('v', i)"));
            Path file = dir.resolve("uno-per-riga.sql");
            c.dump(c.whole(src.name()), DumpOptions.defaults().withRowsPerInsert(1), file);
            int before = c.log().size();
            ScriptFileResult r = c.restore(file, dst.name());
            assertTrue(r.completed(), String.valueOf(r.failures()));
            assertTrue(r.executed() > 20_000);
            List<SqlLog.Entry> added = c.log().entries().subList(before, c.log().size());
            assertTrue(added.size() < 30, "registro: " + added.size() + " righe per " + r.executed() + " istruzioni");
            assertTrue(added.stream().noneMatch(e -> e.sql().startsWith("INSERT")), "gli INSERT sono solo contati");
            SqlLog.Entry summary = added.get(added.size() - 1);
            assertTrue(summary.sql().startsWith("-- file «uno-per-riga.sql»: " + r.executed() + " istruzioni"),
                    summary.sql());
            assertEquals(20_000, summary.rows(), "righe inserite in tutto");
            assertEquals(DumpSupport.checksums(src.connection(), null, src.name(), List.of("r")),
                    DumpSupport.checksums(src.connection(), null, dst.name(), List.of("r")));
            String export = c.log().exportScript();
            assertFalse(export.lines().anyMatch(l -> l.startsWith("DROP ") || l.startsWith("CREATE TABLE")),
                    "l'esportazione rimanda al file, non ne ripete le istruzioni");
            TestResults.write("step10", "T10.5-uno-per-riga-" + id(server) + ".txt", "T10.5 — dump con «Righe per"
                    + " INSERT = 1» (20 000 righe) ripristinato (" + server.label() + ")\nIstruzioni eseguite: "
                    + r.executed() + " in " + r.durationMillis() / 1000.0 + " s\nRighe aggiunte al registro: "
                    + added.size() + " (le strutturali e il riassunto):\n  " + String.join("\n  ", added.stream()
                            .map(e -> e.sql().lines().findFirst().orElse("")).toList())
                    + "\nNota del riassunto: " + summary.note() + "\nCHECKSUM TABLE uguale; l'esportazione del registro"
                    + " rimanda al file con un commento\nEsito: OK\n");
        }
    }

    // ================================================================ T10.5: tipi difficili

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t105_tipiDifficiliEFusiDiversi(ItServers server) throws Exception {
        boolean maria = server == ItServers.MARIADB;
        try (TestCatalog src = TestCatalog.create(server, "t105_tipi"); TestCatalog dst = TestCatalog.create(server,
                "t105_tipi_dst"); DumpSupport.Client dumpClient = DumpSupport.Client.of(server);
                DumpSupport.Client restoreClient = DumpSupport.Client.of(server)) {
            src.execute("SET SESSION sql_mode = ''", "SET SESSION time_zone = '+00:00'",
                    "CREATE TABLE tipi (id INT PRIMARY KEY, d DATE, dt DATETIME(3), t TIME, t2 TIME(2), ts TIMESTAMP(6) NULL"
                            + " DEFAULT NULL, ts0 TIMESTAMP NULL DEFAULT NULL, y YEAR, e ENUM('a','b c','d''e'),"
                            + " s SET('x','y','z'), f FLOAT, fu FLOAT UNSIGNED, db DOUBLE, j JSON, g POINT, b BIT(5),"
                            + " u BIGINT UNSIGNED, n DECIMAL(20,6), gen_v INT AS (id * 2) VIRTUAL,"
                            + " gen_s VARCHAR(20) AS (CONCAT('n', id)) STORED) ENGINE=InnoDB",
                    "INSERT INTO tipi (id, d, dt, t, t2, ts, ts0, y, e, s, f, fu, db, j, g, b, u, n) VALUES"
                            + " (1, '0000-00-00', '0000-00-00 00:00:00.000', '-838:59:59', '100:00:00.25',"
                            + " '2026-10-25 00:30:00.123456', '0000-00-00 00:00:00', 2026, 'b c', 'x,z', 1234567, 0.1,"
                            + " 0.1, '{\"a\": [1, 2, \"è\"], \"b\": null}', ST_GeomFromText('POINT(1 2)'), b'10101',"
                            + " 18446744073709551615, -12345678901234.123456),"
                            + " (2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,"
                            + " NULL, NULL),"
                            + " (3, '2024-02-29', '1999-12-31 23:59:59.999', '00:00:00', '-00:00:01.50',"
                            + " '2026-03-29 01:00:00', '2038-01-19 03:14:07', 1901, 'd''e', '', 3.4028235e38, 1.17549435e-38,"
                            + " -1.7976931348623157e308, '[]', ST_GeomFromText('POINT(-0.5 1e-3)'), b'0', 0, 0)",
                    "CREATE TABLE a_uno (id INT PRIMARY KEY, id_due INT) ENGINE=InnoDB",
                    "CREATE TABLE b_due (id INT PRIMARY KEY, id_uno INT) ENGINE=InnoDB",
                    "SET FOREIGN_KEY_CHECKS = 0",
                    "INSERT INTO a_uno VALUES (1, 10), (2, 20)", "INSERT INTO b_due VALUES (10, 2), (20, 1)",
                    "ALTER TABLE a_uno ADD CONSTRAINT fk_a_b FOREIGN KEY (id_due) REFERENCES b_due (id)",
                    "ALTER TABLE b_due ADD CONSTRAINT fk_b_a FOREIGN KEY (id_uno) REFERENCES a_uno (id)",
                    "CREATE TABLE dipendenti (id INT PRIMARY KEY, nome VARCHAR(20), responsabile INT,"
                            + " CONSTRAINT fk_resp FOREIGN KEY (responsabile) REFERENCES dipendenti (id)) ENGINE=InnoDB",
                    "INSERT INTO dipendenti VALUES (1, 'Anna', 3), (2, 'Bruno', 3), (3, 'Carla', NULL)",
                    "SET FOREIGN_KEY_CHECKS = 1",
                    "CREATE VIEW z_base AS SELECT id, e, s FROM tipi",
                    "CREATE VIEW a_sopra AS SELECT id, e FROM z_base WHERE id > 1");
            // il dump si fa con la sessione a +02:00, il ripristino con una sessione a -03:00
            run(dumpClient, "SET time_zone = '+02:00'");
            run(restoreClient, "SET time_zone = '-03:00'");
            Path file = dir.resolve("tipi.sql");
            Dumper.Result d = dumpClient.dump(dumpClient.whole(src.name()), DumpOptions.defaults()
                    .withDisableForeignKeys(false), file);
            assertTrue(d.warnings().stream().anyMatch(w -> w.contains("a_uno") && w.contains("ciclo")), d.warnings()
                    .toString());
            assertTrue(d.warnings().stream().anyMatch(w -> w.contains("dipendenti") && w.contains("sé stesse")),
                    d.warnings().toString());
            ScriptFileResult r = restoreClient.restore(file, dst.name());
            assertTrue(r.completed(), r.failures().toString());
            // nessun avviso sui dati (1681, FLOAT UNSIGNED deprecato su MySQL, riguarda la definizione e non si conta)
            assertEquals(0, r.warningCount(), "nessun avviso sui dati: " + r.warnings());
            assertEquals(List.of(), r.restore(), "il file rimette da sé la sessione com'era");
            assertEquals("-03:00", value(restoreClient, "@@SESSION.time_zone"));
            List<String> tables = List.of("tipi", "a_uno", "b_due", "dipendenti");
            assertEquals(DumpSupport.checksums(src.connection(), null, src.name(), tables),
                    DumpSupport.checksums(src.connection(), null, dst.name(), tables));
            String q = "SELECT id, UNIX_TIMESTAMP(ts), UNIX_TIMESTAMP(ts0), CAST(f AS DOUBLE), CAST(fu AS DOUBLE),"
                    + " HEX(ST_AsBinary(g)), CAST(j AS CHAR), d, dt, t, t2, gen_v, gen_s FROM ";
            List<List<String>> original = DumpSupport.rows(src.connection(), q + "`" + src.name() + "`.tipi ORDER BY id");
            List<List<String>> restored = DumpSupport.rows(src.connection(), q + "`" + dst.name() + "`.tipi ORDER BY id");
            assertEquals(original, restored);
            assertEquals(DumpSupport.rows(src.connection(), "SELECT * FROM `" + src.name() + "`.a_sopra ORDER BY id"),
                    DumpSupport.rows(src.connection(), "SELECT * FROM `" + dst.name() + "`.a_sopra ORDER BY id"),
                    "la vista che usa un'altra vista funziona");
            TestResults.write("step10", "T10.5-tipi-" + id(server) + ".txt", "T10.5 — tipi difficili (" + server.label()
                    + ")\nDump con la sessione a +02:00, ripristino con una sessione a -03:00\nAvvisi del dump: "
                    + d.warnings() + "\nRipristino: " + r.executed() + " istruzioni, 0 errori, avvisi del server: " + r.warnings()
                    + " (nessuno sui dati);"
                    + " sessione rimessa com'era dal file (time_zone -03:00)\nCHECKSUM TABLE uguale per " + tables
                    + "\nValori confrontati uno per uno (id, UNIX_TIMESTAMP dei TIMESTAMP, FLOAT come DOUBLE, geometrie,"
                    + " JSON, date zero, TIME negativi e oltre 24 h, colonne generate):\n  " + String.join("\n  ",
                            restored.stream().map(List::toString).toList())
                    + "\nVista a_sopra (usa z_base, scritta dopo di lei) funzionante; chiavi circolari a_uno ⇄ b_due e"
                    + " dipendenti.responsabile → dipendenti.id (Anna → Carla, id più alto) ripristinate"
                    + (maria ? "" : "") + "\nEsito: OK\n");
        }
    }

    // ================================================================ T10.6: collation predefinite, CREATE DATABASE

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t106_collationPredefiniteDaUnServerAllAltro(ItServers from) throws Exception {
        ItServers to = from == ItServers.MARIADB ? ItServers.MYSQL : ItServers.MARIADB;
        try (TestCatalog src = TestCatalog.create(from, "t106_coll"); DumpSupport.Client a = DumpSupport.Client.of(from);
                DumpSupport.Client b = DumpSupport.Client.of(to)) {
            String defaultCollation = DumpSupport.rows(src.connection(), "SELECT @@collation_server").get(0).get(0);
            src.execute("ALTER DATABASE `" + src.name() + "` CHARACTER SET utf8mb4 COLLATE " + defaultCollation,
                    "CREATE TABLE persone (id INT PRIMARY KEY, nome VARCHAR(40) NOT NULL, note TEXT) DEFAULT CHARSET=utf8mb4",
                    "INSERT INTO persone VALUES (1, 'Élodie', 'perché'), (2, 'Zoë', NULL)",
                    "CREATE VIEW v_persone AS SELECT id, UPPER(nome) AS nome FROM persone WHERE nome <> 'x'");
            Path file = dir.resolve("coll-" + id(from) + ".sql");
            a.dump(a.whole(src.name()), DumpOptions.defaults().withCreateDatabase(true).withDropIfExists(true), file);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(text.contains(defaultCollation), "il dump porta la collation predefinita di " + from.label());
            ScriptPreview preview = ScriptPreview.scan(file, "Script da file", null);
            var compat = b.compatFor(preview);
            assertFalse(compat.isEmpty(), "su " + to.label() + " la collation " + defaultCollation + " non c'è");
            try {
                ScriptFileResult r = b.restore(file, null);
                assertTrue(r.completed(), r.failures().toString());
                try (Connection c = to.connect(); Statement st = c.createStatement()) {
                    List<List<String>> rows = DumpSupport.rows(c, "SELECT id, nome, note FROM `" + src.name()
                            + "`.persone ORDER BY id");
                    assertEquals(List.of(List.of("1", "Élodie", "perché"), Arrays.asList("2", "Zoë", null)), rows);
                    assertEquals(List.of(List.of("1", "ÉLODIE"), List.of("2", "ZOË")), DumpSupport.rows(c,
                            "SELECT * FROM `" + src.name() + "`.v_persone ORDER BY id"));
                    String used = DumpSupport.rows(c, "SELECT TABLE_COLLATION FROM information_schema.TABLES WHERE"
                            + " TABLE_SCHEMA = '" + src.name() + "' AND TABLE_NAME = 'persone'").get(0).get(0);
                    String target = DumpSupport.rows(c, "SELECT @@collation_server").get(0).get(0);
                    TestResults.write("step10", "T10.6-collation-da-" + id(from) + "-a-" + id(to) + ".txt", "T10.6 —"
                            + " catalogo e tabella con la collation predefinita di " + from.label() + " («"
                            + defaultCollation + "», non dichiarata), dump con DROP/CREATE DATABASE, ripristinato su "
                            + to.label() + "\nSostituzioni mostrate nel riepilogo e applicate: " + compat.replacements()
                            + "\nRipristino: " + r.executed() + " istruzioni, 0 errori\nSu " + to.label()
                            + ": tabella «persone» con collation " + used + " (la predefinita del server è " + target
                            + "), righe e accenti uguali, vista funzionante\nEsito: OK\n");
                    assertEquals(target, used);
                }
            } finally {
                try (Connection c = to.connect(); Statement st = c.createStatement()) {
                    st.execute("DROP DATABASE IF EXISTS `" + TestCatalog.requireTestName(src.name()) + "`");
                }
            }
        }
    }

    // ================================================================ T10.9: sessione, transazioni, catalogo, connessione

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t109_ripristinoInterrottoLaSessioneSiRimette(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t109_sess"); TestCatalog dst = TestCatalog.create(server,
                "t109_sess_dst"); DumpSupport.Client c = DumpSupport.Client.of(server)) {
            src.execute("CREATE TABLE r (id INT PRIMARY KEY, v VARCHAR(20)) ENGINE=InnoDB");
            src.execute("INSERT INTO r " + numbers(30_000, "i, CONCAT('v', i)"));
            Path file = dir.resolve("da-interrompere.sql");
            c.dump(c.whole(src.name()), DumpOptions.defaults().withRowsPerInsert(1), file);
            String mode = value(c, "@@SESSION.sql_mode");
            String zone = value(c, "@@SESSION.time_zone");
            ScriptPreview preview = ScriptPreview.scan(file, "Script da file", null);
            AtomicLong done = new AtomicLong();
            CompletableFuture<ScriptFileResult> f = c.executor().submitScriptFile(
                    List.of(SqlStatement.of("USE `" + dst.name() + "`", "Script da file")), preview.open(),
                    file.getFileName().toString(), false, "Script da file", new ScriptFileListener() {
                        @Override
                        public void progress(long statements, long chars) {
                            done.set(statements);
                        }
                    });
            long t0 = System.currentTimeMillis();
            while (done.get() < 500 && System.currentTimeMillis() - t0 < 60_000) {
                Thread.sleep(20);
            }
            c.executor().interrupt();
            ScriptFileResult r = f.get(2, TimeUnit.MINUTES);
            assertTrue(r.interrupted());
            assertTrue(r.executed() < preview.statements());
            assertTrue(r.restore().contains("SET SESSION sql_mode = '" + mode.replace("'", "''") + "'"), r.restore()
                    .toString());
            assertTrue(r.restore().contains("SET SESSION time_zone = '" + zone + "'"), r.restore().toString());
            assertTrue(r.restore().contains("SET FOREIGN_KEY_CHECKS = 1"), r.restore().toString());
            assertEquals("NO_AUTO_VALUE_ON_ZERO", value(c, "@@SESSION.sql_mode"), "la sessione è rimasta cambiata");
            ScriptResult fixed = run(c, r.restore().toArray(String[]::new));
            assertTrue(fixed.completed());
            assertEquals(mode, value(c, "@@SESSION.sql_mode"));
            assertEquals(zone, value(c, "@@SESSION.time_zone"));
            assertEquals("1", value(c, "@@SESSION.foreign_key_checks"));
            TestResults.write("step10", "T10.9-sessione-" + id(server) + ".txt", "T10.9 — ripristino interrotto dopo "
                    + r.executed() + " istruzioni su " + preview.statements() + " (" + server.label() + ")\nSessione"
                    + " rimasta cambiata dal file: sql_mode = NO_AUTO_VALUE_ON_ZERO, time_zone = +00:00, controlli delle"
                    + " chiavi spenti\nIstruzioni proposte per rimetterla:\n  " + String.join("\n  ", r.restore())
                    + "\nEseguite: sql_mode = «" + mode + "», time_zone = " + zone + ", foreign_key_checks = 1 come prima"
                    + "\nEsito: OK\n");
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t109_lockTransazioneUseEConnessione(ItServers server) throws Exception {
        StringBuilder ev = new StringBuilder("T10.9 — file che bloccano tabelle, aprono transazioni, sbagliano il"
                + " catalogo, perdono la connessione (" + server.label() + ")\n");
        try (TestCatalog dst = TestCatalog.create(server, "t109_casi")) {
            dst.execute("CREATE TABLE t (id INT PRIMARY KEY) ENGINE=InnoDB", "CREATE TABLE altra (id INT) ENGINE=InnoDB");
            List<SqlStatement> use = List.of(SqlStatement.of("USE `" + dst.name() + "`", "Script da file"));
            // LOCK TABLES rimasto aperto: il ripristino propone UNLOCK TABLES
            try (DumpSupport.Client c = DumpSupport.Client.of(server)) {
                Path f1 = dir.resolve("lock.sql");
                writeFile(f1, "LOCK TABLES `t` WRITE;\nINSERT INTO `t` VALUES (1);\nINSERT INTO non_esiste VALUES (1);\n"
                        + "UNLOCK TABLES;\n");
                ScriptFileResult r = c.executor().submitScriptFile(use, ScriptPreview.open(f1), "lock.sql", false,
                        "Script da file", null).get(1, TimeUnit.MINUTES);
                assertEquals(ScriptFileResult.Stop.ERROR, r.stop());
                assertEquals(List.of("UNLOCK TABLES"), r.restore());
                assertFalse(run(c, "SELECT COUNT(*) FROM `" + dst.name() + "`.altra").completed(),
                        "con LOCK TABLES aperto le altre tabelle non si leggono");
                assertTrue(run(c, r.restore().toArray(String[]::new)).completed());
                assertTrue(run(c, "SELECT COUNT(*) FROM `" + dst.name() + "`.altra").completed());
                ev.append("LOCK TABLES + errore: proposto ").append(r.restore()).append("; prima «altra» non si leggeva,"
                        + " dopo sì\n");
            }
            // transazione aperta dal file
            try (DumpSupport.Client c = DumpSupport.Client.of(server)) {
                Path f2 = dir.resolve("begin.sql");
                writeFile(f2, "BEGIN;\nINSERT INTO `t` VALUES (2);\nINSERT INTO non_esiste VALUES (1);\nCOMMIT;\n");
                ScriptFileResult r = c.executor().submitScriptFile(use, ScriptPreview.open(f2), "begin.sql", false,
                        "Script da file", null).get(1, TimeUnit.MINUTES);
                assertTrue(r.transactionOpen(), "BEGIN senza COMMIT");
                try (Connection other = server.connect()) {
                    assertEquals(List.of(List.of("0")), DumpSupport.rows(other, "SELECT COUNT(*) FROM `" + dst.name()
                            + "`.t WHERE id = 2"), "la riga non è confermata: da un'altra connessione non si vede");
                }
                ev.append("BEGIN + errore: transactionOpen = true; la riga inserita dopo BEGIN non si vede da un'altra"
                        + " connessione (non confermata); il client lo dice e non genera COMMIT/ROLLBACK\n");
            }
            // USE non riuscito con «continua»: ci si ferma comunque
            try (DumpSupport.Client c = DumpSupport.Client.of(server)) {
                Path f3 = dir.resolve("use.sql");
                writeFile(f3, "USE `ramasql_test_non_esiste_proprio`;\nCREATE TABLE finita_qui (a INT);\n");
                ScriptFileResult r = c.executor().submitScriptFile(use, ScriptPreview.open(f3), "use.sql", true,
                        "Script da file", null).get(1, TimeUnit.MINUTES);
                assertEquals(ScriptFileResult.Stop.CATALOG, r.stop());
                assertEquals(1, r.executed());
                try (Connection other = server.connect()) {
                    assertEquals(List.of(List.of("0")), DumpSupport.rows(other, "SELECT COUNT(*) FROM"
                            + " information_schema.TABLES WHERE TABLE_NAME = 'finita_qui'"));
                }
                ev.append("USE fallito con «continua»: fermato (Stop.CATALOG), «finita_qui» non creata da nessuna parte\n");
            }
            // connessione caduta: ci si ferma, non si prova il resto del file
            try (DumpSupport.Client c = DumpSupport.Client.of(server)) {
                String connectionId = value(c, "CONNECTION_ID()");
                Path f4 = dir.resolve("caduta.sql");
                StringBuilder sb = new StringBuilder("SELECT SLEEP(3);\n");
                for (int i = 10; i < 60; i++) {
                    sb.append("INSERT INTO `t` VALUES (").append(i).append(");\n");
                }
                writeFile(f4, sb.toString());
                CompletableFuture<ScriptFileResult> f = c.executor().submitScriptFile(use, ScriptPreview.open(f4),
                        "caduta.sql", true, "Script da file", null);
                Thread.sleep(1000);
                try (Connection other = server.connect(); Statement st = other.createStatement()) {
                    st.execute("KILL " + Long.parseLong(connectionId));
                }
                ScriptFileResult r = f.get(1, TimeUnit.MINUTES);
                assertEquals(ScriptFileResult.Stop.CONNECTION, r.stop(), r.failures().toString());
                assertTrue(r.executed() <= 2, "non si provano le altre 50: " + r.executed());
                ev.append("connessione chiusa dal server durante SLEEP con «continua»: fermato (Stop.CONNECTION) dopo ")
                        .append(r.executed()).append(" istruzioni: ").append(r.failures().get(0).message()).append('\n');
            }
            ev.append("Esito: OK\n");
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            TestResults.write("step10", "T10.9-casi-" + id(server) + ".txt", ev.toString());
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t105_dumpInterrottoMentreIlServerPreparaLeRighe(ItServers server) throws Exception {
        try (TestCatalog src = TestCatalog.create(server, "t105_stop"); DumpSupport.Client c = DumpSupport.Client.of(
                server)) {
            // MyISAM senza chiave primaria ordinata dall'indice: ORDER BY con filesort, il server prepara tutto prima
            src.execute("CREATE TABLE lenta (id INT NOT NULL, v VARCHAR(200), PRIMARY KEY (id)) ENGINE=MyISAM");
            src.execute("INSERT INTO lenta " + numbers(400_000, "400001 - i, REPEAT('x', 150)"));
            Path file = dir.resolve("interrotto.sql");
            AtomicBoolean stop = new AtomicBoolean();
            AtomicLong rows = new AtomicLong();
            Dumper d = new Dumper(c.reader(), Dumper.fromExecutor(c.executor(), "Esportazione"), "prova");
            CompletableFuture<Dumper.Result> f = CompletableFuture.supplyAsync(() -> {
                try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    return d.write(List.of(new Dumper.Item(src.name(), "lenta", false, DumpContent.BOTH)),
                            DumpOptions.defaults(), out, stop::get, new Dumper.Progress() {
                                @Override
                                public void rows(String table, long n) {
                                    rows.set(n);
                                }
                            });
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            });
            long t0 = System.currentTimeMillis();
            while (rows.get() < 10_000 && System.currentTimeMillis() - t0 < 60_000 && !f.isDone()) {
                Thread.sleep(5);
            }
            stop.set(true);
            c.executor().interrupt();
            Dumper.Result r = f.get(2, TimeUnit.MINUTES);
            assertTrue(r.interrupted(), "interruzione, non errore");
            assertTrue(r.rows() < 400_000);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(text.contains("DUMP INTERROTTO"), "il file dice che è incompleto");
            assertTrue(ScriptPreview.scan(file, "x", null).incompleteDump(), "e la lettura di prova lo riconosce");
            TestResults.write("step10", "T10.5-dump-interrotto-" + id(server) + ".txt", "T10.5 — dump interrotto"
                    + " durante la lettura di 400 000 righe (" + server.label() + ")\nRighe scritte prima"
                    + " dell'interruzione: " + r.rows() + "\nEsito del client: interrotto (non «errore»); il file finisce"
                    + " con «DUMP INTERROTTO» e la lettura di prova di «Esegui script SQL» lo segnala come dump"
                    + " incompleto\nEsito: OK\n");
        }
    }

    private static final class Arrays {
        private Arrays() {
        }

        @SafeVarargs
        static <T> List<T> asList(T... values) {
            return new ArrayList<>(java.util.Arrays.asList(values));
        }
    }
}
