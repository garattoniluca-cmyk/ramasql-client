/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.Session;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.RoutineInfo;
import it.ramasql.core.metadata.RoutineKind;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * {@link MetadataReader} contro i due server: normalizzazione delle differenze (tipi, default, JSON, ON UPDATE),
 * elementi avanzati conservati, routine/trigger/eventi in sola lettura (ADR-017), caricamento pigro e cache.
 */
@Tag("step3")
@Tag("it")
class MetadataReaderServerTest {

    /** Tabella «difficile»: le stesse istruzioni sui due server (la colonna generata non usa l'AUTO_INCREMENT). */
    private static final String[] VARIETY = {
        "CREATE TABLE varieta (id INT UNSIGNED NOT NULL AUTO_INCREMENT, a VARCHAR(10) NOT NULL DEFAULT 'IT',"
            + " b VARCHAR(10) NULL, c VARCHAR(10) NOT NULL,"
            + " d TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + " e DECIMAL(6,2) NOT NULL DEFAULT 0.00, f DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3),"
            + " g ENUM('a','b''c') NOT NULL DEFAULT 'a', h TINYINT(1) NOT NULL DEFAULT 1,"
            + " i INT GENERATED ALWAYS AS (h * 2) VIRTUAL, j VARCHAR(5) NOT NULL DEFAULT '', k TEXT,"
            + " l BIGINT NULL DEFAULT NULL, m CHAR(3) CHARACTER SET latin1 NULL COMMENT 'commento l''x',"
            + " n JSON NULL, o VARCHAR(20) DEFAULT 'NULL', p DATE DEFAULT '2020-01-01', q INT DEFAULT (1 + 1),"
            + " r YEAR NULL, s BIT(1) NOT NULL DEFAULT b'1',"
            + " PRIMARY KEY (id), UNIQUE KEY uq (a, b), KEY ix (c), CONSTRAINT chk CHECK (e >= 0))"
            + " ENGINE=InnoDB AUTO_INCREMENT=5 COMMENT='prova'",
        "CREATE TABLE partizionata (id INT NOT NULL, PRIMARY KEY (id)) PARTITION BY RANGE (id)"
            + " (PARTITION p0 VALUES LESS THAN (10), PARTITION p1 VALUES LESS THAN MAXVALUE)",
        "CREATE TABLE testi (id INT PRIMARY KEY, testo TEXT, titolo VARCHAR(200), FULLTEXT KEY ft_testo (testo),"
            + " KEY ix_pref (titolo(10))) ENGINE=InnoDB"
    };

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void normalizzazioneEdElementiAvanzati(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_varieta"); Session session = Step3Support.open(server, "")) {
            cat.execute(VARIETY);
            MetadataReader reader = MetadataReader.of(session);
            TableDef t = reader.table(cat.name(), "varieta").orElseThrow();

            assertEquals("InnoDB", t.engine());
            assertEquals("prova", t.comment());
            assertEquals(5L, t.autoIncrementStart());
            ColumnDef id = t.column("id").orElseThrow();
            assertEquals("INT", id.dataType());
            assertNull(id.typeArgs());
            assertTrue(id.unsigned() && id.autoIncrement());
            assertEquals(ColumnDefault.literal("IT"), t.column("a").orElseThrow().defaultValue());
            assertEquals(ColumnDefault.NULL_VALUE, t.column("b").orElseThrow().defaultValue());
            assertEquals(ColumnDefault.NONE, t.column("c").orElseThrow().defaultValue());
            assertEquals(ColumnDefault.CURRENT_TIMESTAMP, t.column("d").orElseThrow().defaultValue());
            assertEquals("CURRENT_TIMESTAMP", t.column("d").orElseThrow().onUpdate());
            assertEquals(ColumnDefault.literal("0.00"), t.column("e").orElseThrow().defaultValue());
            assertEquals(ColumnDefault.expression("CURRENT_TIMESTAMP(3)"), t.column("f").orElseThrow().defaultValue());
            assertEquals("'a','b''c'", t.column("g").orElseThrow().typeArgs());
            assertEquals(ColumnDefault.literal("a"), t.column("g").orElseThrow().defaultValue());
            assertEquals("1", t.column("h").orElseThrow().typeArgs());
            assertTrue(t.column("i").orElseThrow().generated());
            assertEquals(ColumnDefault.literal(""), t.column("j").orElseThrow().defaultValue());
            assertEquals(ColumnDefault.NULL_VALUE, t.column("k").orElseThrow().defaultValue());
            assertNull(t.column("l").orElseThrow().typeArgs());
            assertEquals("latin1", t.column("m").orElseThrow().charset());
            assertEquals("commento l'x", t.column("m").orElseThrow().comment());
            assertEquals("JSON", t.column("n").orElseThrow().dataType());
            assertEquals(ColumnDefault.literal("NULL"), t.column("o").orElseThrow().defaultValue());
            assertEquals(ColumnDefault.literal("2020-01-01"), t.column("p").orElseThrow().defaultValue());
            assertEquals(ColumnDefault.expression("1 + 1"), t.column("q").orElseThrow().defaultValue());
            assertEquals("YEAR", t.column("r").orElseThrow().dataType());
            assertNull(t.column("r").orElseThrow().typeArgs());
            assertEquals(ColumnDefault.literal("b'1'"), t.column("s").orElseThrow().defaultValue());
            // elementi avanzati: colonna generata e CHECK di tabella (non il CHECK automatico del JSON di MariaDB)
            assertEquals(2, t.advancedElements().size(), t.advancedElements().toString());
            assertTrue(t.advancedElements().get(0).startsWith("`i` int"), t.advancedElements().toString());
            assertTrue(t.advancedElements().get(1).startsWith("CONSTRAINT `chk` CHECK"), t.advancedElements().toString());

            TableDef p = reader.table(cat.name(), "partizionata").orElseThrow();
            assertEquals(1, p.advancedElements().size());
            assertTrue(p.advancedElements().get(0).contains("PARTITION BY RANGE"), p.advancedElements().toString());
            TableDef tx = reader.table(cat.name(), "testi").orElseThrow();
            assertEquals(List.of("PRIMARY"), tx.indexes().stream().map(i -> i.name()).toList());
            assertEquals(2, tx.advancedElements().size(), tx.advancedElements().toString());

            TestResults.write("step3", "metadati-varieta-" + server.name().toLowerCase() + ".txt",
                    Step3Support.pretty(Step3Support.table(t.withCatalog(null))) + "\n"
                    + Step3Support.pretty(Step3Support.table(p.withCatalog(null))) + "\n"
                    + Step3Support.pretty(Step3Support.table(tx.withCatalog(null))) + "\n");
        }
    }

    /** La tabella «difficile» letta dai due server: identica a parte il testo degli elementi avanzati. */
    @Test
    void normalizzazioneUgualeSuiDueServer() throws Exception {
        Map<ItServers, TableDef> read = new EnumMap<>(ItServers.class);
        for (ItServers server : ItServers.values()) {
            try (TestCatalog cat = TestCatalog.create(server, "t3_varieta2");
                    Session session = Step3Support.open(server, "")) {
                cat.execute(VARIETY[0]);
                read.put(server, MetadataReader.of(session).table(cat.name(), "varieta").orElseThrow()
                        .withCatalog(null).withAdvancedElements(List.of()));
            }
        }
        assertEquals(read.get(ItServers.MARIADB), read.get(ItServers.MYSQL));
    }

    /** ADR-017: routine, trigger ed eventi elencati e mostrati in sola lettura. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void routineTriggerEdEventiInSolaLettura(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_routine"); Session session = Step3Support.open(server, "")) {
            cat.execute("CREATE TABLE t (id INT PRIMARY KEY, n INT)",
                    "CREATE PROCEDURE p_conta() BEGIN SELECT COUNT(*) FROM t; END",
                    "CREATE EVENT ev_pulizia ON SCHEDULE EVERY 1 DAY DISABLE DO DELETE FROM t WHERE id < 0");
            List<String> expected = new ArrayList<>(List.of("PROCEDURE p_conta"));
            if (server.isMariaDb()) {
                // su MySQL 8 con il log binario attivo l'utente di test non può creare funzioni e trigger (serve SUPER)
                cat.execute("CREATE FUNCTION f_doppio(x INT) RETURNS INT DETERMINISTIC NO SQL RETURN x * 2",
                        "CREATE TRIGGER tr_t BEFORE INSERT ON t FOR EACH ROW SET NEW.n = NEW.n + 1");
                expected.add("FUNCTION f_doppio");
                expected.add("TRIGGER tr_t");
            }
            expected.add("EVENT ev_pulizia");
            MetadataReader reader = MetadataReader.of(session);
            List<RoutineInfo> routines = reader.routines(cat.name());
            assertEquals(expected, routines.stream().map(r -> r.kind() + " " + r.name()).toList());
            StringBuilder evidence = new StringBuilder("ADR-017 — oggetti programmabili in sola lettura su "
                    + server.label() + "\n");
            for (RoutineInfo r : routines) {
                String text = reader.showCreate(r).orElseThrow();
                assertTrue(text.contains(r.name()), text);
                evidence.append("-- ").append(r.kind()).append(' ').append(r.name()).append(" (")
                        .append(r.detail()).append(")\n").append(text).append("\n\n");
            }
            if (server.isMariaDb()) {
                RoutineInfo trigger = routines.stream().filter(r -> r.kind() == RoutineKind.TRIGGER).findFirst()
                        .orElseThrow();
                assertEquals("t", trigger.table());
                assertEquals("BEFORE INSERT", trigger.detail());
            }
            assertTrue(reader.showCreate(new RoutineInfo(cat.name(), "inesistente", RoutineKind.PROCEDURE, null, ""))
                    .isEmpty());
            TestResults.write("step3", "ADR-017-routine-" + server.name().toLowerCase() + ".txt", evidence.toString());
        }
    }

    /** Caricamento pigro, cache e invalidazione esplicita con avviso agli ascoltatori. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void caricamentoPigroCacheEInvalidazione(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_cache"); Session session = Step3Support.open(server, "")) {
            cat.execute("CREATE TABLE a (id INT PRIMARY KEY)", "CREATE TABLE b (id INT PRIMARY KEY)",
                    "CREATE VIEW v AS SELECT id FROM a");
            MetadataReader reader = MetadataReader.of(session);
            List<String> events = new ArrayList<>();
            reader.addListener((c, t) -> events.add(c + "/" + t));

            CatalogInfo info = reader.catalog(cat.name()).orElseThrow();
            assertEquals("utf8mb4", info.charset());
            assertEquals("utf8mb4_unicode_ci", info.collation());
            assertFalse(info.system());
            assertTrue(reader.catalogs().stream().filter(CatalogInfo::system).map(CatalogInfo::name).toList()
                    .contains("information_schema"));

            long before = reader.queryCount();
            List<TableSummary> list = reader.tables(cat.name());
            assertEquals(before + 1, reader.queryCount(), "l'elenco è una sola lettura");
            assertEquals(List.of("a", "b", "v"), list.stream().map(TableSummary::name).toList());
            assertFalse(reader.isCached(cat.name(), "a"), "le colonne non si leggono con l'elenco");
            reader.tables(cat.name());
            assertEquals(before + 1, reader.queryCount(), "seconda volta dalla cache");

            TableDef a = reader.table(cat.name(), "a").orElseThrow();
            assertTrue(reader.isCached(cat.name(), "a"));
            assertEquals(a, reader.table(cat.name(), "a").orElseThrow());
            assertTrue(reader.table(cat.name(), "v").isEmpty(), "una vista non è una tabella");
            assertTrue(reader.table(cat.name(), "manca").isEmpty());
            assertTrue(reader.showCreateTable(cat.name(), "a").orElseThrow().startsWith("CREATE TABLE `a`"));
            // dalla connessione di servizio (senza catalogo corrente) il server qualifica i nomi: VIEW `catalogo`.`v`
            assertTrue(reader.showCreateView(cat.name(), "v").orElseThrow().contains("`v` AS select"));
            assertTrue(reader.showCreateTable(cat.name(), "manca").isEmpty());

            // una modifica fatta fuori dal lettore non si vede finché non si invalida
            cat.execute("CREATE TABLE c (id INT PRIMARY KEY)", "ALTER TABLE a ADD COLUMN x INT");
            assertEquals(3, reader.tables(cat.name()).size());
            reader.invalidate(cat.name(), "a");
            assertEquals(List.of(cat.name() + "/a"), events);
            assertFalse(reader.isCached(cat.name(), "a"));
            assertEquals(2, reader.table(cat.name(), "a").orElseThrow().columns().size());
            assertEquals(List.of("a", "b", "c", "v"),
                    reader.tables(cat.name()).stream().map(TableSummary::name).toList());
            reader.invalidate(cat.name());
            assertFalse(reader.isCached(cat.name()));
            reader.invalidateAll();
            assertEquals(List.of(cat.name() + "/a", cat.name() + "/null", "null/null"), events);
        }
    }
}
