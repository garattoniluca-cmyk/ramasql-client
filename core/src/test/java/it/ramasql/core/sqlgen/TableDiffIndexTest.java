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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T6.1 — {@code TableDiff} sugli indici: stesso SQL atteso su MariaDB 11 e MySQL 8.0. */
@Tag("step6")
class TableDiffIndexTest {

    static final ServerInfo MARIADB = ServerInfo.parse("11.5.2-MariaDB");
    static final ServerInfo MYSQL = ServerInfo.parse("8.0.39");

    static final TableDef SOCI = TableDef.of(null, "soci")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("tessera", "VARCHAR", "10").notNull())
            .addColumn(ColumnDef.of("cognome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("email", "VARCHAR", "100"))
            .withEngine("InnoDB")
            .withOrdinalPositions();

    static final TableDef CON_INDICI = SOCI.withPrimaryKey("id")
            .addIndex(IndexDef.unique("uq_tessera", "tessera"))
            .addIndex(IndexDef.index("ix_nome", "cognome", "nome"));

    private static Arguments caso(String nome, TableDef originale, TableDef modificata, String... attese) {
        return arguments(nome, originale, modificata, List.of(attese));
    }

    static Stream<Arguments> casi() {
        return Stream.of(
                caso("crea INDEX", SOCI, SOCI.addIndex(IndexDef.index("ix_cognome", "cognome")),
                        "ALTER TABLE `soci` ADD INDEX `ix_cognome` (`cognome`)"),
                caso("crea UNIQUE", SOCI, SOCI.addIndex(IndexDef.unique("uq_email", "email")),
                        "ALTER TABLE `soci` ADD UNIQUE INDEX `uq_email` (`email`)"),
                caso("crea PRIMARY", SOCI, SOCI.withPrimaryKey("id"), "ALTER TABLE `soci` ADD PRIMARY KEY (`id`)"),
                caso("crea INDEX multi-colonna: l'ordine è quello dato", SOCI,
                        SOCI.addIndex(IndexDef.index("ix_nome", "nome", "cognome")),
                        "ALTER TABLE `soci` ADD INDEX `ix_nome` (`nome`, `cognome`)"),
                caso("crea più indici in un solo ALTER", SOCI,
                        SOCI.withPrimaryKey("id").addIndex(IndexDef.unique("uq_tessera", "tessera"))
                                .addIndex(IndexDef.index("order by", "cognome", "nome")), """
                        ALTER TABLE `soci`
                          ADD PRIMARY KEY (`id`),
                          ADD UNIQUE INDEX `uq_tessera` (`tessera`),
                          ADD INDEX `order by` (`cognome`, `nome`)"""),
                caso("elimina INDEX", CON_INDICI, CON_INDICI.removeIndex("ix_nome"),
                        "ALTER TABLE `soci` DROP INDEX `ix_nome`"),
                caso("elimina UNIQUE", CON_INDICI, CON_INDICI.removeIndex("uq_tessera"),
                        "ALTER TABLE `soci` DROP INDEX `uq_tessera`"),
                caso("elimina PRIMARY", CON_INDICI, CON_INDICI.withPrimaryKey(), "ALTER TABLE `soci` DROP PRIMARY KEY"),
                caso("rinomina INDEX", CON_INDICI, CON_INDICI.changeIndex("ix_nome", i -> i.withName("ix_anagrafica")),
                        "ALTER TABLE `soci` RENAME INDEX `ix_nome` TO `ix_anagrafica`"),
                caso("rinomina UNIQUE", CON_INDICI, CON_INDICI.changeIndex("uq_tessera", i -> i.withName("tessera unica")),
                        "ALTER TABLE `soci` RENAME INDEX `uq_tessera` TO `tessera unica`"),
                caso("modifica colonne di un indice: DROP + ADD nello stesso ALTER", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withColumns("cognome", "nome", "email")), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD INDEX `ix_nome` (`cognome`, `nome`, `email`)"""),
                caso("cambia solo l'ordine delle colonne: è una modifica", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withColumns("nome", "cognome")), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD INDEX `ix_nome` (`nome`, `cognome`)"""),
                caso("da INDEX a UNIQUE: DROP + ADD nello stesso ALTER", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withKind(IndexKind.UNIQUE)), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD UNIQUE INDEX `ix_nome` (`cognome`, `nome`)"""),
                caso("rinomina e modifica insieme: non è una rinomina", CON_INDICI,
                        CON_INDICI.removeIndex("ix_nome").addIndex(IndexDef.index("ix_cognome", "cognome")), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD INDEX `ix_cognome` (`cognome`)"""),
                caso("cambia la PRIMARY", CON_INDICI, CON_INDICI.withPrimaryKey("id", "tessera"), """
                        ALTER TABLE `soci`
                          DROP PRIMARY KEY,
                          ADD PRIMARY KEY (`id`, `tessera`)"""),
                caso("rinomina di colonna indicizzata: l'indice non si tocca", CON_INDICI,
                        CON_INDICI.changeColumn("cognome", c -> c.withName("famiglia"))
                                .changeIndex("ix_nome", i -> i.withColumns("famiglia", "nome")),
                        "ALTER TABLE `soci` CHANGE COLUMN `cognome` `famiglia` VARCHAR(50) NOT NULL"),
                caso("nuova colonna con il suo indice: ADD COLUMN prima di ADD INDEX", CON_INDICI,
                        CON_INDICI.addColumn(ColumnDef.of("citta", "VARCHAR", "40"))
                                .addIndex(IndexDef.index("ix_citta", "citta")), """
                        ALTER TABLE `soci`
                          ADD COLUMN `citta` VARCHAR(40) NULL,
                          ADD INDEX `ix_citta` (`citta`)"""),
                caso("elimina colonna e il suo indice: DROP INDEX prima di DROP COLUMN", CON_INDICI,
                        CON_INDICI.removeIndex("uq_tessera").removeColumn("tessera"), """
                        ALTER TABLE `soci`
                          DROP INDEX `uq_tessera`,
                          DROP COLUMN `tessera`"""),
                caso("nessuna modifica: nomi di colonna con maiuscole diverse", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withColumns("COGNOME", "Nome"))),
                caso("tabella nuova con indici", null, CON_INDICI, """
                        CREATE TABLE `soci` (
                          `id` INT NOT NULL,
                          `tessera` VARCHAR(10) NOT NULL,
                          `cognome` VARCHAR(50) NOT NULL,
                          `nome` VARCHAR(50) NOT NULL,
                          `email` VARCHAR(100) NULL,
                          PRIMARY KEY (`id`),
                          UNIQUE INDEX `uq_tessera` (`tessera`),
                          INDEX `ix_nome` (`cognome`, `nome`)
                        ) ENGINE=InnoDB"""));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("casi")
    void diff(String nome, TableDef originale, TableDef modificata, List<String> attese) {
        assertEquals(attese, TableDiff.diff(originale, modificata, MARIADB), "MariaDB");
        assertEquals(attese, TableDiff.diff(originale, modificata, MYSQL), "MySQL");
    }

    @Test
    void doveRenameIndexNonEsisteLaRinominaDiventaDropPiuAdd() {
        ServerInfo mariaDbVecchio = ServerInfo.parse("10.4.32-MariaDB");
        assertFalse(TableDiff.supportsRenameIndex(mariaDbVecchio));
        assertTrue(TableDiff.supportsRenameIndex(MARIADB));
        assertTrue(TableDiff.supportsRenameIndex(MYSQL));
        assertTrue(TableDiff.supportsRenameIndex(ServerInfo.parse("5.7.44")));
        assertEquals(List.of("""
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD INDEX `ix_anagrafica` (`cognome`, `nome`)"""),
                TableDiff.diff(CON_INDICI, CON_INDICI.changeIndex("ix_nome", i -> i.withName("ix_anagrafica")),
                        mariaDbVecchio));
    }
}
