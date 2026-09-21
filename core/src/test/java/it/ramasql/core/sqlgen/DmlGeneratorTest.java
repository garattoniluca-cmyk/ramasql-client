/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import it.ramasql.core.data.RowChange;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T4.8 — generatore DML dalle modifiche pendenti. */
@Tag("step4")
class DmlGeneratorTest {

    /** soci: PK semplice AUTO_INCREMENT, colonne di vari tipi. */
    static final TableDef SOCI = TableDef.of(null, "soci")
            .addColumn(ColumnDef.of("id", "INT").withUnsigned(true).notNull().withAutoIncrement(true))
            .addColumn(ColumnDef.of("tessera", "VARCHAR", "10").notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "100").notNull())
            .addColumn(ColumnDef.of("nato_il", "DATE"))
            .addColumn(ColumnDef.of("quota", "DECIMAL", "8,2").notNull().withDefault(ColumnDefault.literal("0.00")))
            .addColumn(ColumnDef.of("attivo", "TINYINT", "1").notNull().withDefault(ColumnDefault.literal("1")))
            .addColumn(ColumnDef.of("note", "TEXT"))
            .withPrimaryKey("id")
            .addIndex(IndexDef.unique("uq_tessera", "tessera"));

    /** libri_autori: PK composta. */
    static final TableDef LIBRI_AUTORI = TableDef.of("biblioteca", "libri_autori")
            .addColumn(ColumnDef.of("id_libro", "INT").notNull())
            .addColumn(ColumnDef.of("id_autore", "INT").notNull())
            .addColumn(ColumnDef.of("ruolo", "VARCHAR", "30"))
            .withPrimaryKey("id_libro", "id_autore");

    /** Senza PK ma con UNIQUE non nullo (più un UNIQUE annullabile, che va scartato). */
    static final TableDef SCAFFALI = TableDef.of(null, "scaffali")
            .addColumn(ColumnDef.of("sigla", "CHAR", "3"))
            .addColumn(ColumnDef.of("codice", "VARCHAR", "10").notNull())
            .addColumn(ColumnDef.of("piano", "INT"))
            .addIndex(IndexDef.unique("uq_sigla", "sigla"))
            .addIndex(IndexDef.unique("uq_codice", "codice"));

    /** Senza alcuna chiave: sola lettura. */
    static final TableDef APPUNTI = TableDef.of(null, "appunti").addColumn(ColumnDef.of("testo", "TEXT"))
            .addIndex(IndexDef.index("ix", "testo"));

    static Map<String, String> riga(String... coppie) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < coppie.length; i += 2) {
            m.put(coppie[i], coppie[i + 1]);
        }
        return m;
    }

    static final Map<String, String> ANNA =
            riga("id", "7", "tessera", "T007", "nome", "Anna", "nato_il", "1990-05-01", "quota", "10.00", "attivo",
                    "1", "note", null);

    static Stream<Arguments> casi() {
        return Stream.of(
                arguments("INSERT: DEFAULT e AUTO_INCREMENT omessi", SOCI,
                        RowChange.insert(1, riga("tessera", "T001", "nome", "Anna")),
                        "INSERT INTO `soci` (`tessera`, `nome`) VALUES ('T001', 'Anna')"),
                arguments("INSERT: NULL esplicito", SOCI,
                        RowChange.insert(1, riga("tessera", "T002", "nome", "Ugo", "nato_il", null)),
                        "INSERT INTO `soci` (`tessera`, `nome`, `nato_il`) VALUES ('T002', 'Ugo', NULL)"),
                arguments("INSERT: apostrofi e backslash", SOCI,
                        RowChange.insert(1, riga("tessera", "T003", "nome", "D'Angelo", "note", "c:\\dati l'ora")),
                        "INSERT INTO `soci` (`tessera`, `nome`, `note`) VALUES ('T003', 'D''Angelo', 'c:\\\\dati l''ora')"),
                arguments("INSERT: emoji e accenti", SOCI,
                        RowChange.insert(1, riga("tessera", "T004", "nome", "Zoë 😀 però")),
                        "INSERT INTO `soci` (`tessera`, `nome`) VALUES ('T004', 'Zoë 😀 però')"),
                arguments("INSERT: DECIMAL e intero senza apici, DATE tra apici", SOCI,
                        RowChange.insert(1, riga("tessera", "T005", "nome", "Lia", "nato_il", "2001-12-31", "quota",
                                "12.50", "attivo", "0")),
                        "INSERT INTO `soci` (`tessera`, `nome`, `nato_il`, `quota`, `attivo`) "
                                + "VALUES ('T005', 'Lia', '2001-12-31', 12.50, 0)"),
                arguments("INSERT: stringa vuota diversa da NULL", SOCI,
                        RowChange.insert(1, riga("tessera", "T006", "nome", "", "note", "")),
                        "INSERT INTO `soci` (`tessera`, `nome`, `note`) VALUES ('T006', '', '')"),
                arguments("INSERT: nessuna colonna impostata", SOCI, RowChange.insert(1, riga()),
                        "INSERT INTO `soci` () VALUES ()"),
                arguments("INSERT: booleano scritto true", SOCI,
                        RowChange.insert(1, riga("tessera", "T008", "nome", "Eva", "attivo", "true")),
                        "INSERT INTO `soci` (`tessera`, `nome`, `attivo`) VALUES ('T008', 'Eva', 1)"),
                arguments("UPDATE: solo la colonna cambiata, WHERE su PK semplice", SOCI,
                        RowChange.update(1, ANNA, riga("nome", "Anna Maria")),
                        "UPDATE `soci` SET `nome` = 'Anna Maria' WHERE `id` = 7"),
                arguments("UPDATE: più colonne, una portata a NULL", SOCI,
                        RowChange.update(1, ANNA, riga("nato_il", null, "quota", "15")),
                        "UPDATE `soci` SET `nato_il` = NULL, `quota` = 15 WHERE `id` = 7"),
                arguments("UPDATE: cambia la PK, il WHERE usa il valore originale", SOCI,
                        RowChange.update(1, ANNA, riga("id", "70")),
                        "UPDATE `soci` SET `id` = 70 WHERE `id` = 7"),
                arguments("UPDATE: WHERE su PK composta, nome qualificato", LIBRI_AUTORI,
                        RowChange.update(1, riga("id_libro", "3", "id_autore", "9", "ruolo", null),
                                riga("ruolo", "curatore")),
                        "UPDATE `biblioteca`.`libri_autori` SET `ruolo` = 'curatore' WHERE `id_libro` = 3 AND `id_autore` = 9"),
                arguments("UPDATE: WHERE su UNIQUE non nullo in assenza di PK", SCAFFALI,
                        RowChange.update(1, riga("sigla", null, "codice", "A'1", "piano", "2"), riga("piano", "3")),
                        "UPDATE `scaffali` SET `piano` = 3 WHERE `codice` = 'A''1'"),
                arguments("DELETE: PK semplice", SOCI, RowChange.delete(1, ANNA), "DELETE FROM `soci` WHERE `id` = 7"),
                arguments("DELETE: PK composta", LIBRI_AUTORI,
                        RowChange.delete(1, riga("id_libro", "3", "id_autore", "9", "ruolo", "autore")),
                        "DELETE FROM `biblioteca`.`libri_autori` WHERE `id_libro` = 3 AND `id_autore` = 9"),
                arguments("DELETE: UNIQUE in assenza di PK", SCAFFALI,
                        RowChange.delete(1, riga("sigla", "AB", "codice", "B2", "piano", null)),
                        "DELETE FROM `scaffali` WHERE `codice` = 'B2'"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("casi")
    void genera(String nome, TableDef tabella, RowChange modifica, String atteso) {
        assertEquals(List.of(atteso), DmlGenerator.generate(tabella, List.of(modifica)));
    }

    @Test
    void nessunaIstruzioneDiTransazioneEOrdineDeleteUpdateInsert() {
        List<RowChange> modifiche = new ArrayList<>();
        modifiche.add(RowChange.insert(1, riga("tessera", "T100", "nome", "Nuovo")));
        modifiche.add(RowChange.update(2, ANNA, riga("nome", "Anna B.")));
        modifiche.add(RowChange.delete(3, riga("id", "9", "tessera", "T009", "nome", "Via", "nato_il", null, "quota",
                "0.00", "attivo", "1", "note", null)));
        modifiche.add(RowChange.update(4, ANNA, riga("note", "seconda")));
        modifiche.add(RowChange.insert(5, riga("tessera", "T101", "nome", "Altro")));

        List<String> sql = DmlGenerator.generate(SOCI, modifiche);

        assertEquals(List.of(
                "DELETE FROM `soci` WHERE `id` = 9",
                "UPDATE `soci` SET `nome` = 'Anna B.' WHERE `id` = 7",
                "UPDATE `soci` SET `note` = 'seconda' WHERE `id` = 7",
                "INSERT INTO `soci` (`tessera`, `nome`) VALUES ('T100', 'Nuovo')",
                "INSERT INTO `soci` (`tessera`, `nome`) VALUES ('T101', 'Altro')"), sql);
        for (String s : sql) {
            String inizio = s.toUpperCase(Locale.ROOT);
            assertFalse(inizio.startsWith("START TRANSACTION") || inizio.startsWith("BEGIN")
                    || inizio.startsWith("COMMIT") || inizio.startsWith("ROLLBACK")
                    || inizio.contains("AUTOCOMMIT"), s);
            assertTrue(inizio.startsWith("INSERT ") || inizio.startsWith("UPDATE ") || inizio.startsWith("DELETE "), s);
        }
        List<Long> righe = DmlGenerator.generateWithRows(SOCI, modifiche).stream().map(r -> r.change().rowId()).toList();
        assertEquals(List.of(3L, 2L, 4L, 1L, 5L), righe);
    }

    @Test
    void updateSenzaColonneCambiateNonGeneraNulla() {
        assertEquals(List.of(), DmlGenerator.generate(SOCI, List.of(RowChange.update(1, ANNA, riga()))));
    }

    @Test
    void sceltaDellaChiave() {
        assertEquals(Optional.of(List.of("id")), DmlGenerator.keyColumns(SOCI));
        assertEquals(Optional.of(List.of("id_libro", "id_autore")), DmlGenerator.keyColumns(LIBRI_AUTORI));
        assertEquals(Optional.of(List.of("codice")), DmlGenerator.keyColumns(SCAFFALI), "il UNIQUE annullabile non vale");
        assertEquals(Optional.empty(), DmlGenerator.keyColumns(APPUNTI));
        assertFalse(DmlGenerator.isEditable(APPUNTI));
        assertThrows(IllegalStateException.class,
                () -> DmlGenerator.generate(APPUNTI, List.of(RowChange.delete(1, riga("testo", "x")))));
    }

    @Test
    void nelWhereUnValoreNulloDiventaIsNullMaiUgualeNull() {
        String where = DmlGenerator.where(SCAFFALI, List.of("sigla", "codice"), riga("sigla", null, "codice", "C3"));
        assertEquals("`sigla` IS NULL AND `codice` = 'C3'", where);
        assertFalse(where.contains("= NULL"));
        assertThrows(IllegalArgumentException.class, () -> DmlGenerator.where(SOCI, riga("nome", "senza id")));
    }

    @Test
    void leIstruzioniPerLaPipelineHannoOrigineERischio() {
        List<SqlStatement> s = DmlGenerator.statements(SOCI, List.of(RowChange.delete(1, ANNA)), "Data-entry");
        assertEquals(1, s.size());
        assertEquals("Data-entry", s.get(0).origin());
        assertEquals(RiskLevel.MODIFIES, s.get(0).risk(), "DELETE con WHERE");
    }
}
