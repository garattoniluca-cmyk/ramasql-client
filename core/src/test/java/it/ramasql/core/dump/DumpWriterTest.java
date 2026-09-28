/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.dump.DumpLiterals.Kind;
import it.ramasql.core.exec.StatementSplitter;

/** Forma del file di dump: intestazione, cataloghi, INSERT estesi, viste senza DEFINER e senza catalogo. */
@Tag("step10")
class DumpWriterTest {

    private static String dump(DumpOptions options) throws Exception {
        StringWriter out = new StringWriter();
        DumpWriter w = new DumpWriter(out, options);
        w.header(Instant.parse("2026-09-28T10:00:00Z"), "MariaDB 11.5.2", List.of("biblioteca"), false);
        w.catalog("biblioteca", "utf8mb4", "utf8mb4_unicode_ci", false);
        w.tableStructure("editori", "CREATE TABLE `editori` (\n  `id` int NOT NULL,\n  `nome` varchar(80) NOT NULL,\n"
                + "  PRIMARY KEY (`id`)\n) ENGINE=InnoDB");
        DumpWriter.TableData data = w.tableData("editori", List.of("id", "nome"), List.of(Kind.NUMBER, Kind.TEXT));
        for (int i = 1; i <= 5; i++) {
            data.row(new Object[] {String.valueOf(i), "Editore " + i + "'s"});
        }
        data.end();
        w.view("v_editori", "CREATE VIEW `v_editori` AS select `editori`.`nome` AS `nome` from `editori`");
        w.footer(false);
        return out.toString();
    }

    @Test
    void predefinitoSenzaCreateDatabaseNeDrop() throws Exception {
        String sql = dump(DumpOptions.defaults().withRowsPerInsert(2));
        assertTrue(sql.startsWith("-- "), sql);
        assertTrue(sql.contains("SET NAMES utf8mb4;"));
        assertFalse(sql.contains("CREATE DATABASE"));
        assertFalse(sql.contains("USE "));
        assertFalse(sql.contains("DROP "));
        assertEquals(3, sql.split("INSERT INTO `editori` \\(`id`, `nome`\\) VALUES", -1).length - 1,
                "5 righe a 2 per istruzione = 3 INSERT");
        assertTrue(sql.contains("(1, 'Editore 1''s'),\n(2, 'Editore 2''s');"), sql);
    }

    @Test
    void conDropECreateDatabase() throws Exception {
        String sql = dump(DumpOptions.defaults().withDropIfExists(true).withCreateDatabase(true));
        assertTrue(sql.contains("DROP DATABASE IF EXISTS `biblioteca`;\nCREATE DATABASE `biblioteca` CHARACTER SET utf8mb4"
                + " COLLATE utf8mb4_unicode_ci;\nUSE `biblioteca`;"), sql);
        assertTrue(sql.contains("DROP TABLE IF EXISTS `editori`;\nCREATE TABLE `editori`"), sql);
        assertTrue(sql.contains("DROP VIEW IF EXISTS `v_editori`;\nCREATE VIEW"), sql);
    }

    @Test
    void createDatabaseSenzaDropNonSovrascrive() throws Exception {
        String sql = dump(DumpOptions.defaults().withCreateDatabase(true));
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS `biblioteca`"), sql);
    }

    @Test
    void ilFileSiDivideNelleIstruzioniAttese() throws Exception {
        String sql = dump(DumpOptions.defaults().withRowsPerInsert(100));
        List<String> statements = StatementSplitter.split(sql).stream().map(StatementSplitter.SplitStatement::text)
                .toList();
        assertEquals(List.of("SET NAMES utf8mb4", "SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS",
                "SET FOREIGN_KEY_CHECKS = 0", "SET @OLD_SQL_MODE = @@SQL_MODE", "SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO'",
                "SET @OLD_TIME_ZONE = @@TIME_ZONE", "SET TIME_ZONE = '+00:00'"), statements.subList(0, 7));
        assertTrue(statements.get(7).startsWith("CREATE TABLE `editori`"));
        assertTrue(statements.get(8).startsWith("INSERT INTO `editori`"));
        assertTrue(statements.get(9).startsWith("CREATE VIEW"));
        assertEquals(List.of("SET TIME_ZONE = @OLD_TIME_ZONE", "SET SQL_MODE = @OLD_SQL_MODE",
                "SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS"), statements.subList(10, 13));
        assertEquals(13, statements.size());
        assertTrue(sql.stripTrailing().endsWith("-- Fine del dump: 13 istruzioni."), "l'ultima riga dice che è completo");
    }

    @Test
    void unNomeConUnACapoNonDiventaSqlNeiCommenti() throws Exception {
        StringWriter out = new StringWriter();
        DumpWriter w = new DumpWriter(out, DumpOptions.defaults());
        w.tableStructure("x\nDROP DATABASE scuola;--", "CREATE TABLE `x\nDROP DATABASE scuola;--` (a INT)");
        String sql = out.toString();
        List<String> statements = StatementSplitter.split(sql).stream().map(StatementSplitter.SplitStatement::text)
                .toList();
        assertEquals(1, statements.size(), "solo il CREATE TABLE: " + statements);
        assertTrue(sql.contains("-- Tabella x DROP DATABASE scuola;--: struttura"), sql);
    }

    @Test
    void catalogoTabellaEColonnaConLoStessoNome() {
        String show = "CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`%` SQL SECURITY DEFINER VIEW `v` AS select "
                + "`scuola`.`scuola`.`id` AS `id` from `scuola`.`scuola`";
        String clean = DumpWriter.viewForDump(show, "scuola");
        assertEquals("CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v` AS select `scuola`.`id` AS `id` from"
                + " `scuola`", clean);
        assertEquals(List.of(), DumpWriter.otherCatalogs(clean));
        String other = DumpWriter.viewForDump("CREATE VIEW `v` AS select `altro`.`t`.`a` AS `a` from `altro`.`t`",
                "scuola");
        assertEquals(List.of("altro"), DumpWriter.otherCatalogs(other), "vista che legge un altro catalogo");
    }

    @Test
    void rigaPiuLungaMisurata() throws Exception {
        StringWriter out = new StringWriter();
        DumpWriter w = new DumpWriter(out, DumpOptions.defaults());
        DumpWriter.TableData d = w.tableData("t", List.of("a", "b"), List.of(Kind.NUMBER, Kind.TEXT));
        d.row(new Object[] {"1", "x"});
        d.row(new Object[] {"2", "abcdefghij"});
        d.end();
        assertEquals("(2, 'abcdefghij')".length(), d.largestRow());
    }

    @Test
    void tabellaVuotaSoloUnCommento() throws Exception {
        StringWriter out = new StringWriter();
        DumpWriter w = new DumpWriter(out, DumpOptions.defaults());
        DumpWriter.TableData d = w.tableData("vuota", List.of("a"), List.of(Kind.TEXT));
        d.end();
        assertFalse(out.toString().contains("INSERT"));
        assertEquals(0, d.rows());
    }

    @Test
    void istruzioneTroppoLungaSiChiudePrima() throws Exception {
        StringWriter out = new StringWriter();
        DumpWriter w = new DumpWriter(out, DumpOptions.defaults().withRowsPerInsert(1000));
        DumpWriter.TableData d = w.tableData("blob", List.of("b"), List.of(Kind.BINARY));
        byte[] big = new byte[300_000];
        for (int i = 0; i < 5; i++) {
            d.row(new Object[] {big});
        }
        d.end();
        long inserts = out.toString().lines().filter(l -> l.startsWith("INSERT INTO")).count();
        assertTrue(inserts >= 3, "righe da 600 kB l'una: più istruzioni (" + inserts + ")");
    }

    @Test
    void vistaSenzaDefinerESenzaCatalogo() {
        String show = "CREATE ALGORITHM=UNDEFINED DEFINER=`ramasql_test`@`%` SQL SECURITY DEFINER VIEW `v` AS "
                + "select `bib`.`p`.`id` AS `id`,'`bib`.nel testo' AS `t` from (`bib`.`prestiti` `p` join `bib`.`soci` `s`"
                + " on(`s`.`id` = `p`.`id_socio`))";
        String out = DumpWriter.viewForDump(show, "bib");
        assertEquals("CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v` AS select `p`.`id` AS `id`,"
                + "'`bib`.nel testo' AS `t` from (`prestiti` `p` join `soci` `s` on(`s`.`id` = `p`.`id_socio`))", out);
    }

    @Test
    void definerConApiciEHostQualsiasi() {
        String show = "CREATE ALGORITHM=UNDEFINED DEFINER='root'@'localhost' SQL SECURITY INVOKER VIEW `v` AS select 1";
        assertEquals("CREATE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW `v` AS select 1",
                DumpWriter.viewForDump(show, "x"));
    }

    @Test
    void catalogoAltroNonToccato() {
        assertEquals("select `altro`.`t`.`a` from `altro`.`t`", DumpWriter.unqualify("select `altro`.`t`.`a` from `altro`.`t`",
                "bib"));
    }
}
