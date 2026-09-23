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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.CoreMessages;

/** {@link SqlLog}: righe, ascoltatori, esportazione come script rieseguibile (lato core di T3.8). */
@Tag("step3")
class SqlLogTest {

    private static SqlLog sample() {
        SqlLog log = new SqlLog();
        String con = "Lab (MariaDB 11.5.2)";
        log.add(con, "Navigatore", "CREATE TABLE `cat`.`a` (id INT)", SqlLog.Outcome.OK, 0, "", "", 12, 0);
        log.add(con, "Editor SQL", "INSERT INTO `cat`.`a` VALUES (1),(2)", SqlLog.Outcome.OK, 0, "", "", 3, 2);
        log.add(con, "Editor SQL", "INSERT INTO a VALUES ('x')", SqlLog.Outcome.ERROR, 1366, "22007",
                "Incorrect integer value: 'x'\nfor column", 1, 0);
        log.add(con, "Editor SQL", "USE cat", SqlLog.Outcome.OK, 0, "", "", 0, 0);
        log.add(con, "Editor SQL", "CREATE PROCEDURE p() BEGIN SELECT 1; END", SqlLog.Outcome.OK, 0, "", "", 2, 0);
        log.add(con, "Editor SQL", "SELECT SLEEP(30)", SqlLog.Outcome.INTERRUPTED, 1317, "70100",
                "Query execution was interrupted", 200, 0);
        return log;
    }

    private static List<String> statements(String script) {
        return StatementSplitter.split(script).stream().map(StatementSplitter.SplitStatement::text).toList();
    }

    @Test
    void righeNumerateEAscoltatori() {
        SqlLog log = new SqlLog();
        List<SqlLog.Entry> seen = new ArrayList<>();
        int[] cleared = {0};
        log.addListener(new SqlLog.Listener() {
            @Override
            public void entryAdded(SqlLog.Entry entry) {
                seen.add(entry);
            }

            @Override
            public void cleared() {
                cleared[0]++;
            }
        });
        log.add("c", "o", "SELECT 1", SqlLog.Outcome.OK, 0, null, null, 1, 1);
        log.add("c", "o", "SELECT 2", SqlLog.Outcome.OK, 0, null, null, 1, 1);
        assertEquals(2, log.size());
        assertEquals(List.of(1L, 2L), log.entries().stream().map(SqlLog.Entry::sequence).toList());
        assertEquals(log.entries(), seen);
        log.clear();
        assertEquals(0, log.size());
        assertEquals(1, cleared[0]);
    }

    @Test
    void esportazioneConFalliteCommentate() {
        String script = SqlLog.exportScript(sample().entries(), new SqlLog.ExportOptions(null, Instant.EPOCH));
        assertTrue(script.startsWith("-- RamaSQL Client - registro SQL esportato il "), script);
        assertTrue(script.contains("-- Connessione: Lab (MariaDB 11.5.2)\n"), script);
        assertTrue(script.contains("-- Istruzioni: 4 riuscite; 2 non riuscite o interrotte"), script);
        assertTrue(script.contains("\nCREATE TABLE `cat`.`a` (id INT);\n"), script);
        assertTrue(script.contains("ERRORE 1366 (22007): Incorrect integer value: 'x' for column\n"
                + "-- INSERT INTO a VALUES ('x');\n"), script);
        assertTrue(script.contains("\nDELIMITER $$\nCREATE PROCEDURE p() BEGIN SELECT 1; END\n$$\nDELIMITER ;\n"),
                script);
        assertTrue(script.contains("INTERROTTA dall'utente\n-- SELECT SLEEP(30);\n"), script);
        // rieseguendolo si ritrovano esattamente le istruzioni riuscite, nello stesso ordine
        assertEquals(List.of("CREATE TABLE `cat`.`a` (id INT)", "INSERT INTO `cat`.`a` VALUES (1),(2)", "USE cat",
                "CREATE PROCEDURE p() BEGIN SELECT 1; END"), statements(script));
    }

    @Test
    void esportazioneSenzaCatalogoPerRieseguireAltrove() {
        String script = sample().exportScript(SqlLog.ExportOptions.withoutCatalog("cat"));
        assertEquals(List.of("CREATE TABLE `a` (id INT)", "INSERT INTO `a` VALUES (1),(2)",
                "CREATE PROCEDURE p() BEGIN SELECT 1; END"), statements(script));
        assertTrue(script.contains("-- USE cat;\n-- (USE del catalogo d'origine omesso)"), script);
    }

    @Test
    void togliereIlCatalogoNonToccaStringheNeAltriNomi() {
        assertEquals("SELECT 'cat.x', `catx`.y, t.cat FROM `t`",
                SqlLog.unqualify("SELECT 'cat.x', `catx`.y, t.cat FROM cat.`t`", "cat"));
        assertEquals("DROP TABLE `a`", SqlLog.unqualify("DROP TABLE `CAT`.`a`", "cat"));
        assertNull(SqlLog.unqualify("use `cat`", "cat"));
        assertEquals("USE altro", SqlLog.unqualify("USE altro", "cat"));
    }

    // ---------------------------------------------------------------- revisione Step 3: fallite, catalogo d'origine

    @Test
    void leFalliteSonoSegnalateComePossibilmenteApplicateInParte() {
        SqlLog log = new SqlLog();
        log.add("c", "Editor SQL", "DROP TABLE a, b", SqlLog.Outcome.ERROR, 1051, "42S02", "Unknown table 'b'", 3, 0);
        log.add("c", "Editor SQL", "UPDATE t SET x = SLEEP(9)", SqlLog.Outcome.INTERRUPTED, 1317, "70100", "", 3, 0);
        String script = log.exportScript(new SqlLog.ExportOptions(null, Instant.EPOCH));
        String partial = "-- " + CoreMessages.get("log.export.partial") + "\n";
        assertTrue(script.contains("-- DROP TABLE a, b;\n" + partial), script);
        assertTrue(script.contains("-- UPDATE t SET x = SLEEP(9);\n" + partial), script);
        assertTrue(partial.contains("può essere stata applicata in parte: verifica"), partial);
        assertFalse(script.contains("nessun effetto"), "mai promettere che una fallita non abbia avuto effetto");
        assertFalse(script.contains("non hanno avuto effetto"), script);
        assertEquals(List.of(), statements(script), "le fallite non si rieseguono");
    }

    /**
     * Regola ferrea di «senza catalogo»: lo script rieseguito non crea, non modifica e non elimina mai il catalogo
     * d'origine. Ogni forma che lo nomina ancora si commenta con la spiegazione.
     */
    @Test
    void senzaCatalogoNonToccaMaiIlCatalogoDOrigine() {
        SqlLog log = new SqlLog();
        String nav = SqlOrigin.NAVIGATOR.label();
        String ed = SqlOrigin.EDITOR.label();
        String[][] rows = {
            {nav, "CREATE DATABASE `cat` CHARACTER SET utf8mb4"},
            {ed, "create schema if not exists cat"},
            {ed, "CREATE OR REPLACE DATABASE cat"},
            {ed, "ALTER DATABASE `cat` COLLATE utf8mb4_bin"},
            {ed, "ALTER SCHEMA cat CHARACTER SET latin1"},
            {nav, "DROP DATABASE `cat`"},
            {ed, "drop database if exists `CAT`"},
            {ed, "SET STATEMENT max_statement_time=5 FOR DROP DATABASE cat"},
            {ed, "/*!40000 DROP DATABASE cat */"},
            {ed, "USE `cat`"},
            {ed, "INSERT INTO cat . t VALUES (1)"},
            {ed, "SHOW TABLES FROM cat"},
            {ed, "SELECT * FROM \"cat\".t"},
            {ed, "PREPARE s FROM 'DROP DATABASE cat'"},
            {ed, "CREATE TABLE cat.cat (id INT)"},
            // queste si rieseguono sul catalogo corrente, senza il qualificatore
            {nav, "DROP TABLE `cat`.`vecchia`"},
            {ed, "INSERT INTO `cat`.`t` SELECT * FROM cat.u"},
            {ed, "ALTER DATABASE CHARACTER SET utf8mb4"},
            {ed, "DROP DATABASE `altro`"},
            {ed, "SELECT t.cat FROM t"}};
        for (String[] r : rows) {
            log.add("c", r[0], r[1], SqlLog.Outcome.OK, 0, "", "", 1, 0);
        }
        String script = log.exportScript(SqlLog.ExportOptions.withoutCatalog("cat"));

        assertEquals(List.of("DROP TABLE `vecchia`", "INSERT INTO `t` SELECT * FROM u",
                "ALTER DATABASE CHARACTER SET utf8mb4", "DROP DATABASE `altro`", "SELECT t.cat FROM t"),
                statements(script), script);
        String catalogStatement = "-- " + CoreMessages.get("log.export.catalogStatement", "cat") + "\n";
        for (String sql : new String[] {"CREATE DATABASE `cat` CHARACTER SET utf8mb4", "create schema if not exists cat",
                "CREATE OR REPLACE DATABASE cat", "ALTER DATABASE `cat` COLLATE utf8mb4_bin",
                "ALTER SCHEMA cat CHARACTER SET latin1", "DROP DATABASE `cat`", "drop database if exists `CAT`",
                "SET STATEMENT max_statement_time=5 FOR DROP DATABASE cat", "/*!40000 DROP DATABASE cat */"}) {
            assertTrue(script.contains("-- " + sql + ";\n" + catalogStatement), sql + "\n" + script);
        }
        String residual = "-- " + CoreMessages.get("log.export.residualReference", "cat") + "\n";
        for (String sql : new String[] {"INSERT INTO cat . t VALUES (1)", "SHOW TABLES FROM cat",
                "SELECT * FROM \"cat\".t", "PREPARE s FROM 'DROP DATABASE cat'", "CREATE TABLE cat.cat (id INT)"}) {
            assertTrue(script.contains("-- " + sql + ";\n" + residual), sql + "\n" + script);
        }
        assertTrue(script.contains("-- USE `cat`;\n-- " + CoreMessages.get("log.export.useOmitted")), script);
        // scritta a mano: il qualificatore si toglie, ma lo si dice; generata dal client: in silenzio
        String byHand = "-- " + CoreMessages.get("log.export.strippedByHand", "cat") + "\n";
        assertTrue(script.contains(byHand + "INSERT INTO `t` SELECT * FROM u;\n"), script);
        assertFalse(script.contains(byHand + "DROP TABLE `vecchia`;"), script);
        assertFalse(script.contains(byHand + "SELECT t.cat FROM t;"), "nulla da togliere, nulla da dire");
    }

    @Test
    void formaRieseguibileDiOgniIstruzione() {
        assertEquals(SqlLog.ReplayKind.UNCHANGED, SqlLog.replayForm("SELECT 1", "cat").kind());
        assertEquals(new SqlLog.Replay(SqlLog.ReplayKind.STRIPPED, "DROP TABLE `a`"),
                SqlLog.replayForm("DROP TABLE `cat`.`a`", "cat"));
        assertEquals(SqlLog.ReplayKind.USE_ORIGIN, SqlLog.replayForm("use cat", "cat").kind());
        assertEquals(SqlLog.ReplayKind.CATALOG_STATEMENT, SqlLog.replayForm("DROP SCHEMA cat", "cat").kind());
        assertEquals(SqlLog.ReplayKind.RESIDUAL_REFERENCE, SqlLog.replayForm("SELECT * FROM cat .t", "cat").kind());
        // le stringhe sono dati, tranne nell'SQL dinamico
        assertEquals(SqlLog.ReplayKind.UNCHANGED, SqlLog.replayForm("INSERT INTO t VALUES ('cat')", "cat").kind());
        assertEquals(SqlLog.ReplayKind.RESIDUAL_REFERENCE,
                SqlLog.replayForm("EXECUTE IMMEDIATE 'TRUNCATE cat.t'", "cat").kind());
        // i commenti non si eseguono: nominare lì il catalogo non conta
        assertEquals(SqlLog.ReplayKind.UNCHANGED, SqlLog.replayForm("SELECT 1 /* cat */ -- DROP DATABASE cat", "cat")
                .kind());
    }
}
