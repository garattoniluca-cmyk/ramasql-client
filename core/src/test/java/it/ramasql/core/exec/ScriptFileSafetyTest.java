/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.core.dump.DumpOptions;
import it.ramasql.core.dump.DumpWriter;
import it.ramasql.core.metadata.CollationInfo;

/**
 * Ciò che rende sicuro eseguire uno script da file (revisione dello Step 10): cataloghi nascosti nei commenti
 * eseguibili e nei nomi qualificati, {@code DROP DATABASE} lontani in fondo al file, impostazioni della sessione
 * cambiate, codifica diversa da UTF-8, dump incompleti, collation sconosciute al server, registro esportato che non
 * ripete le istruzioni di un file.
 */
@Tag("step10")
class ScriptFileSafetyTest {

    @TempDir
    Path dir;

    private Path write(String text) throws Exception {
        Path f = dir.resolve("s.sql");
        Files.writeString(f, text, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void cataloghiNeiCommentiEseguibiliEQualificati() throws Exception {
        ScriptPreview p = ScriptPreview.scan(write("/*!40000 DROP DATABASE IF EXISTS `scuola`*/;\n"
                + "DROP TABLE ramasql_test_a.x;\nINSERT INTO `bibliotecasoft`.`libri` VALUES (1);\n"
                + "/* DROP DATABASE commento_normale */ SELECT 1;\n"), "x", null);
        assertEquals(Set.of("scuola"), p.catalogs(), "il commento eseguibile conta, quello normale no");
        assertEquals(Set.of("ramasql_test_a", "bibliotecasoft"), p.qualifiedCatalogs());
        assertFalse(p.onlyCatalogsStartingWith("ramasql_test_"));
        assertTrue(p.createsCatalog());
        ScriptPreview ok = ScriptPreview.scan(write("DROP TABLE ramasql_test_a.x;\nSELECT a.b FROM t a;\n"), "x",
                null);
        assertTrue(ok.onlyCatalogsStartingWith("ramasql_test_"), ok.qualifiedCatalogs().toString());
    }

    @Test
    void dropDatabaseLontanoSempreNellAnteprimaEParolaGenerica() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("DROP TABLE IF EXISTS t").append(i).append(";\n");
        }
        sb.append("DROP DATABASE ramasql_test_z;\n");
        ScriptPreview p = ScriptPreview.scan(write(sb.toString()), "x", null);
        assertEquals(301, p.destructiveCount(), "contate tutte, senza tetto");
        assertTrue(p.later().stream().anyMatch(s -> s.text().startsWith("DROP DATABASE")),
                "il DROP DATABASE dopo 300 distruttive entra comunque nell'anteprima");
        SqlScript shown = p.previewScript("x", List.of());
        assertEquals(ConfirmationPolicy.Action.DROP_CATALOG, ConfirmationPolicy.mostSevereAction(shown));
        ConfirmationPolicy.Confirmation c = p.confirmation("x", List.of());
        assertEquals(ConfirmationPolicy.Level.STRONG, c.level());
        assertEquals("CONFERMO", c.typeToConfirm(), "più oggetti: la parola generica");
    }

    @Test
    void istruzioniLunghissimeAbbreviateNellAnteprima() throws Exception {
        String big = "INSERT INTO t VALUES ('" + "x".repeat(50_000) + "')";
        ScriptPreview p = ScriptPreview.scan(write(big + ";\n"), "x", null);
        assertTrue(p.first().get(0).text().length() <= ScriptPreview.PREVIEW_TEXT_LIMIT + 2);
        assertTrue(p.first().get(0).text().endsWith(" …"));
    }

    @Test
    void impostazioniDellaSessioneCambiate() throws Exception {
        ScriptPreview p = ScriptPreview.scan(write("/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;\n"
                + "/*!40103 SET TIME_ZONE='+00:00' */;\nSET NAMES latin1;\nLOCK TABLES `t` WRITE;\nBEGIN;\n"
                + "SET UNIQUE_CHECKS=0;\n"), "x", null);
        assertEquals(Set.of(ScriptPreview.SessionChange.SQL_MODE, ScriptPreview.SessionChange.TIME_ZONE,
                ScriptPreview.SessionChange.NAMES, ScriptPreview.SessionChange.LOCK_TABLES,
                ScriptPreview.SessionChange.TRANSACTION, ScriptPreview.SessionChange.UNIQUE_CHECKS), p.sessionChanges());
        assertFalse(p.touchesForeignKeyChecks());
    }

    @Test
    void fileCambiatoDopoLaLetturaDiProva() throws Exception {
        Path f = write("SELECT 1;\n");
        ScriptPreview p = ScriptPreview.scan(f, "x", null);
        assertFalse(p.changedOnDisk());
        Files.writeString(f, "DROP DATABASE ramasql_test_q;\nSELECT 1;\n", StandardCharsets.UTF_8);
        assertTrue(p.changedOnDisk(), "prima di eseguire si rilegge: la conferma vale per il file visto");
    }

    @Test
    void fileNonUtf8LettoComeWindows1252() throws Exception {
        Path f = dir.resolve("latin.sql");
        Files.write(f, "INSERT INTO t VALUES ('perché città');\n".getBytes("windows-1252"));
        ScriptPreview p = ScriptPreview.scan(f, "x", null);
        assertTrue(p.notUtf8());
        assertEquals("INSERT INTO t VALUES ('perché città')", p.first().get(0).text(), "accenti giusti");
        try (ScriptReader r = p.open()) {
            assertEquals("INSERT INTO t VALUES ('perché città')", r.next().text());
        }
        ScriptPreview utf = ScriptPreview.scan(write("SELECT 'perché';"), "x", null);
        assertFalse(utf.notUtf8());
    }

    @Test
    void dumpDiRamaSqlIncompleto() throws Exception {
        java.io.StringWriter out = new java.io.StringWriter();
        DumpWriter w = new DumpWriter(out, DumpOptions.defaults());
        w.header(Instant.EPOCH, "MariaDB", List.of("c"), false);
        w.tableStructure("t", "CREATE TABLE t (a INT)");
        String partial = out.toString();
        w.footer(false);
        assertFalse(ScriptPreview.scan(write(out.toString()), "x", null).incompleteDump(), "completo");
        assertTrue(ScriptPreview.scan(write(partial), "x", null).incompleteDump(), "troncato");
        java.io.StringWriter out2 = new java.io.StringWriter();
        DumpWriter w2 = new DumpWriter(out2, DumpOptions.defaults());
        w2.header(Instant.EPOCH, "MariaDB", List.of("c"), false);
        w2.footer(true);
        assertTrue(ScriptPreview.scan(write(out2.toString()), "x", null).incompleteDump(), "interrotto");
        assertFalse(ScriptPreview.scan(write("SELECT 1;"), "x", null).incompleteDump(), "non è un nostro dump");
    }

    @Test
    void collationSconosciuteSostituite() throws Exception {
        List<CollationInfo> mysql = List.of(new CollationInfo("utf8mb4_0900_ai_ci", "utf8mb4", true),
                new CollationInfo("utf8mb4_unicode_ci", "utf8mb4", false),
                new CollationInfo("utf8mb3_general_ci", "utf8mb3", true));
        String create = "CREATE TABLE `t` (`a` varchar(10) COLLATE utf8mb4_uca1400_ai_ci, `b` text COLLATE "
                + "utf8mb4_unicode_ci) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci";
        ScriptPreview p = ScriptPreview.scan(write(create + ";\n/*!40101 SET collation_connection = utf8_uca1400_ai_ci */;\n"),
                "x", null);
        assertEquals(Set.of("utf8mb4_uca1400_ai_ci", "utf8mb4_unicode_ci", "utf8_uca1400_ai_ci"), p.collations());
        CollationCompat c = CollationCompat.of(p.collations(), mysql);
        assertEquals(Map.of("utf8mb4_uca1400_ai_ci", "utf8mb4_0900_ai_ci", "utf8_uca1400_ai_ci", "utf8mb3_general_ci"),
                c.replacements());
        assertEquals("CREATE TABLE `t` (`a` varchar(10) COLLATE utf8mb4_0900_ai_ci, `b` text COLLATE utf8mb4_unicode_ci)"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", c.apply(create));
        assertEquals("SELECT 'COLLATE utf8mb4_uca1400_ai_ci'".length(), c.apply("SELECT 'COLLATE utf8mb4_uca1400_ai_ci'")
                .length());
        List<CollationInfo> mariadb = List.of(new CollationInfo("utf8mb4_uca1400_ai_ci", "utf8mb4", true));
        CollationCompat back = CollationCompat.of(Set.of("utf8mb4_0900_ai_ci"), mariadb);
        assertEquals("CREATE TABLE t (a INT) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci",
                back.apply("CREATE TABLE t (a INT) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"));
        assertTrue(CollationCompat.of(Set.of("utf8mb4_uca1400_ai_ci"), mariadb).isEmpty(), "già conosciuta");
        assertEquals("x", CollationCompat.none().apply("x"));
    }

    @Test
    void ripristinoDellaSessione() {
        List<String> before = List.of("STRICT_TRANS_TABLES", "SYSTEM", "1", "1", "utf8mb4", "utf8mb4_uca1400_ai_ci");
        assertEquals(List.of(), SqlExecutor.restoreStatements(before, before, false));
        List<String> after = List.of("NO_AUTO_VALUE_ON_ZERO", "+00:00", "0", "0", "latin1", "latin1_swedish_ci");
        assertEquals(List.of("UNLOCK TABLES", "SET SESSION sql_mode = 'STRICT_TRANS_TABLES'",
                "SET SESSION time_zone = 'SYSTEM'", "SET FOREIGN_KEY_CHECKS = 1", "SET UNIQUE_CHECKS = 1",
                "SET NAMES utf8mb4 COLLATE utf8mb4_uca1400_ai_ci"), SqlExecutor.restoreStatements(before, after, true));
    }

    @Test
    void registroEsportatoRimandaAlFile() {
        SqlLog log = new SqlLog();
        log.add("c", "Editor SQL", "CREATE TABLE a (id INT)", SqlLog.Outcome.OK, 0, "", "", 1, 0);
        log.add("c", "Script da file", "DROP TABLE IF EXISTS libri", SqlLog.Outcome.OK, 0, "", "", 1, 0, "riga 3", false,
                "biblioteca.sql");
        log.add("c", "Script da file", "CREATE TABLE libri (id INT, titolo VARCHAR(100), … lunghissima …",
                SqlLog.Outcome.OK, 0, "", "", 1, 0, "abbreviata", false, "biblioteca.sql");
        log.add("c", "Script da file", "-- file «biblioteca.sql»: 3 istruzioni", SqlLog.Outcome.OK, 0, "", "", 1, 0,
                "riassunto", false, "biblioteca.sql");
        log.add("c", "Editor SQL", "SELECT 1", SqlLog.Outcome.OK, 0, "", "", 1, 1);
        String script = log.exportScript();
        assertTrue(script.contains("CREATE TABLE a (id INT);"));
        assertTrue(script.contains("SELECT 1;"));
        assertFalse(script.lines().anyMatch(l -> l.startsWith("DROP TABLE")), "il DROP del file non si riesegue da solo");
        assertFalse(script.contains("CREATE TABLE libri"));
        assertEquals(1, script.lines().filter(l -> l.contains("eseguito il file «biblioteca.sql» (3 istruzioni")).count(),
                script);
    }
}
