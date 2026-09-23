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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * Il normalizzatore non deve nascondere differenze: {@code ZEROFILL} si legge e si conserva, gli indici che v1 non
 * modifica (FULLTEXT, prefisso, espressione, DESC) restano elementi avanzati ma si vedono ({@link TableDef#readOnlyIndexes}),
 * una chiave esterna {@code SET DEFAULT} non fa fallire la lettura della tabella.
 */
@Tag("step3")
class MetadataSpecialCasesTest {

    private static String[] c(String... v) {
        return v;
    }

    private static final String[] TABLE = {"s", "BASE TABLE", "InnoDB", "utf8mb4_unicode_ci", ""};

    @Test
    void zerofillSiLeggeSiConfrontaESiConserva() {
        List<String[]> cols = List.of(
                c("id", "int(11)", "NO", null, "", null, null, "", "1", null),
                c("codice", "int(6) unsigned zerofill", "NO", "0", "", null, null, "", "2", null));
        TableDef t = MetadataNormalizer.table("cat", TABLE, cols, List.of(), List.of(), null, true);
        ColumnDef codice = t.column("codice").orElseThrow();
        assertTrue(codice.zerofill());
        assertTrue(codice.unsigned());
        assertEquals("INT(6)", codice.fullType(), "la larghezza di ZEROFILL resta");
        assertFalse(t.column("id").orElseThrow().zerofill());
        // letto = letto: nessuna istruzione; un MODIFY di un'altra proprietà conserva ZEROFILL
        ServerInfo maria = ServerInfo.parse("11.5.2-MariaDB");
        assertEquals(List.of(), TableDiff.diff(t, t, maria));
        TableDef edited = t.changeColumn("codice", x -> x.withComment("c"));
        assertEquals(List.of("ALTER TABLE `cat`.`s` MODIFY COLUMN `codice` INT(6) UNSIGNED ZEROFILL NOT NULL DEFAULT 0"
                + " COMMENT 'c'"), TableDiff.diff(t, edited, maria));
        // e la differenza di ZEROFILL non si perde nel confronto
        assertEquals(1, TableDiff.diff(t, t.changeColumn("codice", x -> x.withZerofill(false)), maria).size());
    }

    @Test
    void indiceDescEUnElementoAvanzatoMaSiVede() {
        String ddl = """
                CREATE TABLE `s` (
                  `id` int NOT NULL,
                  `anno` int DEFAULT NULL,
                  `titolo` varchar(200) DEFAULT NULL,
                  `trama` text,
                  PRIMARY KEY (`id`),
                  KEY `ix_anno` (`anno` DESC),
                  KEY `ix_pref` (`titolo`(10)),
                  KEY `ix_expr` ((lower(`titolo`))),
                  FULLTEXT KEY `ft` (`titolo`,`trama`),
                  KEY `ix_ok` (`anno`,`id`)
                ) ENGINE=InnoDB""";
        List<String[]> idx = List.of(
                c("PRIMARY", "0", "1", "id", null, "BTREE", "A"),
                c("ix_anno", "1", "1", "anno", null, "BTREE", "D"),
                c("ix_pref", "1", "1", "titolo", "10", "BTREE", "A"),
                c("ix_expr", "1", "1", null, null, "BTREE", "A"),
                c("ft", "1", "1", "titolo", null, "FULLTEXT", null),
                c("ft", "1", "2", "trama", null, "FULLTEXT", null),
                c("ix_ok", "1", "1", "anno", null, "BTREE", "A"),
                c("ix_ok", "1", "2", "id", null, "BTREE", "A"));
        TableDef t = MetadataNormalizer.table("cat", TABLE, List.of(), idx, List.of(), ddl, false);
        // il generatore vede solo gli indici che sa riscrivere identici
        assertEquals(List.of(IndexDef.primary("id"), IndexDef.index("ix_ok", "anno", "id")), t.indexes());
        assertTrue(t.advancedElements().contains("KEY `ix_anno` (`anno` DESC)"), t.advancedElements().toString());
        // il navigatore li mostra tutti, in sola lettura, con il motivo
        List<ReadOnlyIndex> ro = t.readOnlyIndexes();
        assertEquals(List.of("ix_anno", "ix_pref", "ix_expr", "ft"), ro.stream().map(ReadOnlyIndex::name).toList());
        assertEquals(Set.of(ReadOnlyIndex.Feature.DESCENDING), ro.get(0).features());
        assertEquals("anno DESC", ro.get(0).columns());
        assertEquals(Set.of(ReadOnlyIndex.Feature.PREFIX), ro.get(1).features());
        assertEquals("titolo(10)", ro.get(1).columns());
        assertEquals(Set.of(ReadOnlyIndex.Feature.EXPRESSION), ro.get(2).features());
        assertEquals(Set.of(ReadOnlyIndex.Feature.FULLTEXT), ro.get(3).features());
        assertEquals("titolo,trama", ro.get(3).columns());
        // una riga DESC senza COLLATION (server vecchio o simulato) resta come prima: indice normale
        TableDef old = MetadataNormalizer.table("cat", TABLE, List.of(),
                List.<String[]>of(c("ix", "1", "1", "anno", null, "BTREE")), List.of(), null, false);
        assertEquals(List.of(IndexDef.index("ix", "anno")), old.indexes());
    }

    @Test
    void righeDiIndiceRiconosciute() {
        assertEquals(Optional.empty(), ReadOnlyIndex.fromCreateLine("KEY `ix` (`a`,`b`)"));
        assertEquals(Optional.empty(), ReadOnlyIndex.fromCreateLine("`a` int DEFAULT NULL"));
        assertEquals(Optional.empty(), ReadOnlyIndex.fromCreateLine("CONSTRAINT `chk` CHECK (`a` > 0)"));
        assertEquals(Optional.empty(), ReadOnlyIndex.fromCreateLine("PARTITION BY HASH (`id`) PARTITIONS 4"));
        ReadOnlyIndex u = ReadOnlyIndex.fromCreateLine("UNIQUE KEY `uq desc` (`email`(20)),").orElseThrow();
        assertEquals("uq desc", u.name(), "DESC dentro un nome non conta");
        assertTrue(u.unique());
        assertEquals(Set.of(ReadOnlyIndex.Feature.PREFIX), u.features());
        ReadOnlyIndex pk = ReadOnlyIndex.fromCreateLine("PRIMARY KEY (`codice`(8))").orElseThrow();
        assertEquals("PRIMARY", pk.name());
        ReadOnlyIndex sp = ReadOnlyIndex.fromCreateLine("SPATIAL KEY `g` (`posizione`)").orElseThrow();
        assertEquals(Set.of(ReadOnlyIndex.Feature.SPATIAL), sp.features());
        ReadOnlyIndex both = ReadOnlyIndex.fromCreateLine("KEY `m` (`a`(5),`b` DESC)").orElseThrow();
        assertEquals(Set.of(ReadOnlyIndex.Feature.PREFIX, ReadOnlyIndex.Feature.DESCENDING), both.features());
    }

    /**
     * {@code SET DEFAULT}: InnoDB lo rifiuta su entrambi i server, quindi il client non lo genera (non è una costante di
     * {@link FkAction}); ma se il server lo riporta la tabella si legge lo stesso e la chiave resta, com'è scritta nel
     * server, fra gli elementi avanzati (mai riscritta dai generatori).
     */
    @Test
    void chiaveEsternaSetDefaultNonFaFallireLaLettura() {
        String ddl = """
                CREATE TABLE `s` (
                  `id` int NOT NULL,
                  `p` int DEFAULT NULL,
                  `q` int DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  CONSTRAINT `fk_sd` FOREIGN KEY (`p`) REFERENCES `padre` (`id`) ON DELETE SET DEFAULT,
                  CONSTRAINT `fk_ok` FOREIGN KEY (`q`) REFERENCES `padre` (`id`) ON DELETE CASCADE
                ) ENGINE=NDB""";
        List<String[]> fk = List.of(
                c("fk_sd", "p", "cat", "padre", "id", "RESTRICT", "SET DEFAULT"),
                c("fk_ok", "q", "cat", "padre", "id", "RESTRICT", "CASCADE"));
        TableDef t = MetadataNormalizer.table("cat", TABLE, List.of(), List.of(), fk, ddl, false);
        assertEquals(List.of("fk_ok"), t.foreignKeys().stream().map(ForeignKeyDef::name).toList());
        assertEquals(List.of("CONSTRAINT `fk_sd` FOREIGN KEY (`p`) REFERENCES `padre` (`id`) ON DELETE SET DEFAULT"),
                t.advancedElements());
        assertEquals(Optional.empty(), FkAction.parse("SET DEFAULT"));
        assertEquals(Optional.empty(), FkAction.parse("set_default"));
        assertEquals(Optional.of(FkAction.RESTRICT), FkAction.parse(null));
        assertEquals(Optional.of(FkAction.NO_ACTION), FkAction.parse("NO ACTION"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FkAction.fromSql("SET DEFAULT"));
        assertTrue(e.getMessage().contains("SET DEFAULT"), e.getMessage());
        assertEquals(List.of(FkAction.RESTRICT, FkAction.CASCADE, FkAction.SET_NULL, FkAction.NO_ACTION),
                List.of(FkAction.values()), "l'editor propone solo le quattro azioni che InnoDB accetta");
        // senza SHOW CREATE la chiave si conserva comunque, descritta dalle righe lette
        TableDef bare = MetadataNormalizer.table("cat", TABLE, List.of(), List.of(), fk, null, false);
        assertEquals(1, bare.foreignKeys().size());
        assertTrue(bare.advancedElements().get(0).contains("SET DEFAULT"), bare.advancedElements().toString());
    }
}
