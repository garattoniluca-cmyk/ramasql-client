/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * Normalizzazione dei metadati: righe di {@code information_schema} <b>copiate da MariaDB 11.5 e MySQL 8.0</b>
 * (sonda sui server di sviluppo, 2026-09-22) per la stessa tabella → stesso {@link TableDef}.
 */
@Tag("step3")
class MetadataNormalizerTest {

    private static final String[] TABLE_ROW_MARIADB = {"t", "BASE TABLE", "InnoDB", "utf8mb4_unicode_ci", "tab"};
    private static final String[] TABLE_ROW_MYSQL = {"t", "BASE TABLE", "InnoDB", "utf8mb4_unicode_ci", "tab"};

    private static String[] c(String... v) {
        return v;
    }

    // COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARSET, COLLATION, COMMENT, POSITION, GENERATION
    private static final List<String[]> COLUMNS_MARIADB = List.of(
            c("id", "int(10) unsigned", "NO", null, "auto_increment", null, null, "", "1", null),
            c("a", "varchar(10)", "NO", "'IT'", "", "utf8mb4", "utf8mb4_unicode_ci", "", "2", null),
            c("b", "varchar(10)", "YES", "NULL", "", "utf8mb4", "utf8mb4_unicode_ci", "", "3", null),
            c("c", "varchar(10)", "NO", null, "", "utf8mb4", "utf8mb4_unicode_ci", "", "4", null),
            c("d", "timestamp", "NO", "current_timestamp()", "on update current_timestamp()", null, null, "", "5", null),
            c("e", "decimal(6,2)", "NO", "0.00", "", null, null, "", "6", null),
            c("f", "datetime(3)", "YES", "current_timestamp(3)", "", null, null, "", "7", null),
            c("g", "enum('a','b''c')", "NO", "'a'", "", "utf8mb4", "utf8mb4_unicode_ci", "", "8", null),
            c("h", "tinyint(1)", "NO", "1", "", null, null, "", "9", null),
            c("i", "int(11)", "YES", "NULL", "VIRTUAL GENERATED", null, null, "", "10", "`h` * 2"),
            c("j", "varchar(5)", "NO", "''", "", "utf8mb4", "utf8mb4_unicode_ci", "", "11", null),
            c("k", "text", "YES", "NULL", "", "utf8mb4", "utf8mb4_unicode_ci", "", "12", null),
            c("l", "bigint(20)", "YES", "NULL", "", null, null, "", "13", null),
            c("m", "char(3)", "YES", "NULL", "", "latin1", "latin1_swedish_ci", "commento l'x", "14", null),
            c("n", "longtext", "YES", "NULL", "", "utf8mb4", "utf8mb4_bin", "", "15", null),
            c("o", "varchar(20)", "YES", "'NULL'", "", "utf8mb4", "utf8mb4_unicode_ci", "", "16", null),
            c("p", "date", "YES", "'2020-01-01'", "", null, null, "", "17", null),
            c("q", "int(11)", "YES", "(1 + 1)", "", null, null, "", "18", null));

    private static final List<String[]> COLUMNS_MYSQL = List.of(
            c("id", "int unsigned", "NO", null, "auto_increment", null, null, "", "1", ""),
            c("a", "varchar(10)", "NO", "IT", "", "utf8mb4", "utf8mb4_unicode_ci", "", "2", ""),
            c("b", "varchar(10)", "YES", null, "", "utf8mb4", "utf8mb4_unicode_ci", "", "3", ""),
            c("c", "varchar(10)", "NO", null, "", "utf8mb4", "utf8mb4_unicode_ci", "", "4", ""),
            c("d", "timestamp", "NO", "CURRENT_TIMESTAMP", "DEFAULT_GENERATED on update CURRENT_TIMESTAMP", null, null,
                    "", "5", ""),
            c("e", "decimal(6,2)", "NO", "0.00", "", null, null, "", "6", ""),
            c("f", "datetime(3)", "YES", "CURRENT_TIMESTAMP(3)", "DEFAULT_GENERATED", null, null, "", "7", ""),
            c("g", "enum('a','b''c')", "NO", "a", "", "utf8mb4", "utf8mb4_unicode_ci", "", "8", ""),
            c("h", "tinyint(1)", "NO", "1", "", null, null, "", "9", ""),
            c("i", "int", "YES", null, "VIRTUAL GENERATED", null, null, "", "10", "(`h` * 2)"),
            c("j", "varchar(5)", "NO", "", "", "utf8mb4", "utf8mb4_unicode_ci", "", "11", ""),
            c("k", "text", "YES", null, "", "utf8mb4", "utf8mb4_unicode_ci", "", "12", ""),
            c("l", "bigint", "YES", null, "", null, null, "", "13", ""),
            c("m", "char(3)", "YES", null, "", "latin1", "latin1_swedish_ci", "commento l'x", "14", ""),
            c("n", "json", "YES", null, "", null, null, "", "15", ""),
            c("o", "varchar(20)", "YES", "NULL", "", "utf8mb4", "utf8mb4_unicode_ci", "", "16", ""),
            c("p", "date", "YES", "2020-01-01", "", null, null, "", "17", ""),
            c("q", "int", "YES", "(1 + 1)", "DEFAULT_GENERATED", null, null, "", "18", ""));

    // INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, INDEX_TYPE (stesse righe sui due server)
    private static final List<String[]> INDEXES = List.of(
            c("ix", "1", "1", "c", null, "BTREE"),
            c("PRIMARY", "0", "1", "id", null, "BTREE"),
            c("uq", "0", "2", "b", null, "BTREE"),
            c("uq", "0", "1", "a", null, "BTREE"));

    private static final String CREATE_MARIADB = """
            CREATE TABLE `t` (
              `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
              `a` varchar(10) NOT NULL DEFAULT 'IT',
              `b` varchar(10) DEFAULT NULL,
              `c` varchar(10) NOT NULL,
              `d` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
              `e` decimal(6,2) NOT NULL DEFAULT 0.00,
              `f` datetime(3) DEFAULT current_timestamp(3),
              `g` enum('a','b''c') NOT NULL DEFAULT 'a',
              `h` tinyint(1) NOT NULL DEFAULT 1,
              `i` int(11) GENERATED ALWAYS AS (`h` * 2) VIRTUAL,
              `j` varchar(5) NOT NULL DEFAULT '',
              `k` text DEFAULT NULL,
              `l` bigint(20) DEFAULT NULL,
              `m` char(3) CHARACTER SET latin1 COLLATE latin1_swedish_ci DEFAULT NULL COMMENT 'commento l''x',
              `n` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`n`)),
              `o` varchar(20) DEFAULT 'NULL',
              `p` date DEFAULT '2020-01-01',
              `q` int(11) DEFAULT (1 + 1),
              PRIMARY KEY (`id`),
              UNIQUE KEY `uq` (`a`,`b`),
              KEY `ix` (`c`),
              CONSTRAINT `chk` CHECK (`e` >= 0)
            ) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='tab'""";

    private static final String CREATE_MYSQL = """
            CREATE TABLE `t` (
              `id` int unsigned NOT NULL AUTO_INCREMENT,
              `a` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'IT',
              `b` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
              `c` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
              `d` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              `e` decimal(6,2) NOT NULL DEFAULT '0.00',
              `f` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
              `g` enum('a','b''c') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'a',
              `h` tinyint(1) NOT NULL DEFAULT '1',
              `i` int GENERATED ALWAYS AS ((`h` * 2)) VIRTUAL,
              `j` varchar(5) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
              `k` text COLLATE utf8mb4_unicode_ci,
              `l` bigint DEFAULT NULL,
              `m` char(3) CHARACTER SET latin1 COLLATE latin1_swedish_ci DEFAULT NULL COMMENT 'commento l''x',
              `n` json DEFAULT NULL,
              `o` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'NULL',
              `p` date DEFAULT '2020-01-01',
              `q` int DEFAULT ((1 + 1)),
              PRIMARY KEY (`id`),
              UNIQUE KEY `uq` (`a`,`b`),
              KEY `ix` (`c`),
              CONSTRAINT `chk` CHECK ((`e` >= 0))
            ) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='tab'""";

    private static TableDef mariadb() {
        return MetadataNormalizer.table("cat", TABLE_ROW_MARIADB, COLUMNS_MARIADB, INDEXES, List.of(), CREATE_MARIADB,
                true);
    }

    private static TableDef mysql() {
        return MetadataNormalizer.table("cat", TABLE_ROW_MYSQL, COLUMNS_MYSQL, INDEXES, List.of(), CREATE_MYSQL, false);
    }

    @Test
    void laStessaTabellaLettaDaiDueServerDaLoStessoModello() {
        TableDef m = mariadb();
        TableDef y = mysql();
        // gli elementi avanzati restano nel testo di ciascun server: si confrontano a parte
        assertEquals(m.withAdvancedElements(List.of()), y.withAdvancedElements(List.of()));
        assertEquals(2, m.advancedElements().size(), m.advancedElements().toString());
        assertEquals(2, y.advancedElements().size(), y.advancedElements().toString());
        assertTrue(m.advancedElements().get(0).startsWith("`i` int(11) GENERATED ALWAYS AS"));
        assertTrue(y.advancedElements().get(1).startsWith("CONSTRAINT `chk` CHECK"));
    }

    @Test
    void tipiNormalizzati() {
        TableDef t = mariadb();
        ColumnDef id = t.column("id").orElseThrow();
        assertEquals("INT", id.dataType());
        assertNull(id.typeArgs());
        assertTrue(id.unsigned());
        assertTrue(id.autoIncrement());
        assertEquals(ColumnDefault.NONE, id.defaultValue());
        assertEquals("TINYINT", t.column("h").orElseThrow().dataType());
        assertEquals("1", t.column("h").orElseThrow().typeArgs());
        assertNull(t.column("l").orElseThrow().typeArgs());
        assertEquals("'a','b''c'", t.column("g").orElseThrow().typeArgs());
        assertEquals("6,2", t.column("e").orElseThrow().typeArgs());
        assertEquals("3", t.column("f").orElseThrow().typeArgs());
        ColumnDef json = t.column("n").orElseThrow();
        assertEquals("JSON", json.dataType());
        assertNull(json.charset());
        assertNull(json.collation());
        assertTrue(t.column("i").orElseThrow().generated());
    }

    @Test
    void defaultNeiQuattroCasi() {
        TableDef t = mysql();
        assertEquals(ColumnDefault.literal("IT"), t.column("a").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.NULL_VALUE, t.column("b").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.NONE, t.column("c").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.CURRENT_TIMESTAMP, t.column("d").orElseThrow().defaultValue());
        assertEquals("CURRENT_TIMESTAMP", t.column("d").orElseThrow().onUpdate());
        assertEquals(ColumnDefault.literal("0.00"), t.column("e").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.expression("CURRENT_TIMESTAMP(3)"), t.column("f").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.literal(""), t.column("j").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.literal("NULL"), t.column("o").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.literal("2020-01-01"), t.column("p").orElseThrow().defaultValue());
        assertEquals(ColumnDefault.expression("1 + 1"), t.column("q").orElseThrow().defaultValue());
    }

    @Test
    void charsetDiColonnaUgualeAllaTabellaDiventaEreditato() {
        TableDef t = mariadb();
        assertEquals("utf8mb4", t.charset());
        assertEquals("utf8mb4_unicode_ci", t.collation());
        assertNull(t.column("a").orElseThrow().charset());
        assertNull(t.column("a").orElseThrow().collation());
        assertEquals("latin1", t.column("m").orElseThrow().charset());
        assertEquals("latin1_swedish_ci", t.column("m").orElseThrow().collation());
        assertEquals("commento l'x", t.column("m").orElseThrow().comment());
    }

    @Test
    void opzioniIndiciEAutoIncrement() {
        TableDef t = mariadb();
        assertEquals("InnoDB", t.engine());
        assertEquals("tab", t.comment());
        assertEquals(5L, t.autoIncrementStart());
        assertEquals(List.of(IndexDef.primary("id"), IndexDef.index("ix", "c"), IndexDef.unique("uq", "a", "b")),
                t.indexes());
        assertEquals(18, t.columns().size());
        assertEquals(1, t.columns().get(0).ordinalPosition());
        assertEquals(18, t.columns().get(17).ordinalPosition());
    }

    @Test
    void tableDiffTraLettoELettoENullaAncheTraIDueServer() {
        ServerInfo maria = ServerInfo.parse("11.5.2-MariaDB");
        ServerInfo my = ServerInfo.parse("8.0.40");
        assertEquals(List.of(), TableDiff.diff(mariadb(), mariadb(), maria));
        assertEquals(List.of(), TableDiff.diff(mysql(), mysql(), my));
        assertEquals(List.of(), TableDiff.diff(mariadb(), mysql(), maria));
    }

    @Test
    void chiaviEsterneRaggruppateConAzioniECatalogoRiferito() {
        List<String[]> fkRows = List.of(
                c("fk_b", "x", "cat", "padre", "id", "CASCADE", "SET NULL"),
                c("fk_a", "y1", "altro", "p2", "k1", "RESTRICT", "NO ACTION"),
                c("fk_a", "y2", "altro", "p2", "k2", "RESTRICT", "NO ACTION"));
        TableDef t = MetadataNormalizer.table("cat", TABLE_ROW_MYSQL, List.of(), List.of(), fkRows, null, false);
        assertEquals(2, t.foreignKeys().size());
        ForeignKeyDef a = t.foreignKeys().get(0);
        assertEquals("fk_a", a.name());
        assertEquals(List.of("y1", "y2"), a.columns());
        assertEquals("altro", a.refCatalog());
        assertEquals(List.of("k1", "k2"), a.refColumns());
        assertEquals(FkAction.NO_ACTION, a.onDelete());
        assertEquals(FkAction.RESTRICT, a.onUpdate());
        ForeignKeyDef b = t.foreignKeys().get(1);
        assertNull(b.refCatalog());
        assertEquals(FkAction.SET_NULL, b.onDelete());
        assertEquals(FkAction.CASCADE, b.onUpdate());
    }

    @Test
    void partizioniEIndiciSpecialiSonoElementiAvanzati() {
        String ddl = """
                CREATE TABLE `p` (
                  `id` int NOT NULL,
                  `testo` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  KEY `ix_pref` (`testo`(10))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                /*!50100 PARTITION BY RANGE (`id`)
                (PARTITION p0 VALUES LESS THAN (10) ENGINE = InnoDB,
                 PARTITION p1 VALUES LESS THAN MAXVALUE ENGINE = InnoDB) */""";
        List<String[]> idx = List.of(c("PRIMARY", "0", "1", "id", null, "BTREE"),
                c("ix_pref", "1", "1", "testo", "10", "BTREE"));
        TableDef t = MetadataNormalizer.table("cat", c("p", "BASE TABLE", "InnoDB", "utf8mb4_unicode_ci", ""),
                List.of(), idx, List.of(), ddl, false);
        assertEquals(List.of(IndexDef.primary("id")), t.indexes());
        assertEquals("KEY `ix_pref` (`testo`(10))", t.advancedElements().get(0));
        assertTrue(t.advancedElements().get(1).startsWith("/*!50100 PARTITION BY RANGE (`id`)"));
        assertNull(t.autoIncrementStart());
    }

    @Test
    void funzioniDiSupporto() {
        assertEquals("CURRENT_TIMESTAMP", MetadataNormalizer.normalizeExpression("current_timestamp()"));
        assertEquals("CURRENT_TIMESTAMP", MetadataNormalizer.normalizeExpression("now()"));
        assertEquals("CURRENT_TIMESTAMP", MetadataNormalizer.normalizeExpression("(CURRENT_TIMESTAMP(0))"));
        assertEquals("CURRENT_TIMESTAMP(6)", MetadataNormalizer.normalizeExpression("current_timestamp(6)"));
        assertEquals("uuid()", MetadataNormalizer.normalizeExpression("(uuid())"));
        assertEquals("CURRENT_TIMESTAMP(3)", MetadataNormalizer.onUpdate("on update current_timestamp(3)"));
        assertNull(MetadataNormalizer.onUpdate("DEFAULT_GENERATED"));
        assertTrue(MetadataNormalizer.isGenerated("STORED GENERATED"));
        assertTrue(MetadataNormalizer.isGenerated("PERSISTENT"));
        assertFalse(MetadataNormalizer.isGenerated("DEFAULT_GENERATED"));
        assertEquals("utf8mb3", MetadataNormalizer.charsetOf("utf8mb3_general_ci"));
        assertEquals("binary", MetadataNormalizer.charsetOf("binary"));
        assertEquals("l'ora \\ fine", MetadataNormalizer.unquote("'l''ora \\\\ fine'"));
        assertEquals(new MetadataNormalizer.ParsedType("YEAR", null, false, false),
                MetadataNormalizer.parseColumnType("year(4)"));
        assertEquals(new MetadataNormalizer.ParsedType("INT", "5", true, true),
                MetadataNormalizer.parseColumnType("int(5) unsigned zerofill"));
        assertEquals(new MetadataNormalizer.ParsedType("SET", "'x)','y'", false, false),
                MetadataNormalizer.parseColumnType("set('x)','y')"));
        // MariaDB: stringa letterale «NULL» ≠ DEFAULT NULL; MySQL: nessun default su NOT NULL
        assertEquals(ColumnDefault.literal("NULL"),
                MetadataNormalizer.defaultValue("'NULL'", "", true, true, "VARCHAR"));
        assertEquals(ColumnDefault.NONE, MetadataNormalizer.defaultValue(null, "", false, false, "INT"));
        assertEquals(ColumnDefault.literal("b'1'"), MetadataNormalizer.defaultValue("b'1'", "", false, true, "BIT"));
    }
}
