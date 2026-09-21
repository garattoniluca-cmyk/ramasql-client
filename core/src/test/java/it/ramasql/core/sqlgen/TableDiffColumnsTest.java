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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.TableDef;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T5.1 — {@code TableDiff} su colonne, chiave primaria e opzioni di tabella. Ogni caso è verificato con
 * {@link #MARIADB} e con {@link #MYSQL}: per queste operazioni l'SQL scelto è lo stesso sui due server.
 * Gli SQL attesi sono eseguibili in sequenza: {@code diff(null, originale)} crea la tabella di partenza.
 */
@Tag("step5")
class TableDiffColumnsTest {

    static final ServerInfo MARIADB = ServerInfo.parse("11.5.2-MariaDB");
    static final ServerInfo MYSQL = ServerInfo.parse("8.0.39");

    static final TableDef LIBRI = TableDef.of(null, "libri")
            .addColumn(ColumnDef.of("id", "INT").withUnsigned(true).notNull().withAutoIncrement(true))
            .addColumn(ColumnDef.of("titolo", "VARCHAR", "100").notNull())
            .addColumn(ColumnDef.of("prezzo", "DECIMAL", "8,2"))
            .addColumn(ColumnDef.of("stato", "ENUM", "'nuovo','usato'").notNull()
                    .withDefault(ColumnDefault.literal("nuovo")))
            .addColumn(ColumnDef.of("note", "TEXT"))
            .addColumn(ColumnDef.of("creato", "DATETIME").notNull().withDefault(ColumnDefault.CURRENT_TIMESTAMP))
            .withPrimaryKey("id")
            .withEngine("InnoDB")
            .withCharset("utf8mb4", "utf8mb4_general_ci")
            .withOrdinalPositions();

    static final TableDef LIBRI_AUTORI = TableDef.of(null, "libri_autori")
            .addColumn(ColumnDef.of("id_libro", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("id_autore", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("ruolo", "VARCHAR", "30").notNull().withDefault(ColumnDefault.literal("autore")))
            .withPrimaryKey("id_libro", "id_autore")
            .withEngine("InnoDB")
            .withOrdinalPositions();

    static final TableDef SENZA_PK = TableDef.of(null, "t")
            .addColumn(ColumnDef.of("a", "INT").notNull())
            .addColumn(ColumnDef.of("b", "INT").notNull())
            .withOrdinalPositions();

    static final TableDef MYISAM = TableDef.of(null, "registro")
            .addColumn(ColumnDef.of("riga", "VARCHAR", "200"))
            .withEngine("MyISAM").withOrdinalPositions();

    private static Arguments caso(String nome, TableDef originale, TableDef modificata, String... attese) {
        return arguments(nome, originale, modificata, List.of(attese));
    }

    static Stream<Arguments> casi() {
        return Stream.of(
                // ------------------------------------------------------------ tabella nuova
                caso("nuova: tabella completa", null, LIBRI, """
                        CREATE TABLE `libri` (
                          `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
                          `titolo` VARCHAR(100) NOT NULL,
                          `prezzo` DECIMAL(8,2) NULL,
                          `stato` ENUM('nuovo','usato') NOT NULL DEFAULT 'nuovo',
                          `note` TEXT NULL,
                          `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"""),
                caso("nuova: minima, senza opzioni", null,
                        TableDef.of(null, "t").addColumn(ColumnDef.of("a", "INT")), """
                        CREATE TABLE `t` (
                          `a` INT NULL
                        )"""),
                caso("nuova: nomi con spazi e parole riservate", null,
                        TableDef.of(null, "ordine dettagli")
                                .addColumn(ColumnDef.of("order", "INT").notNull())
                                .addColumn(ColumnDef.of("select", "VARCHAR", "10"))
                                .addColumn(ColumnDef.of("group by", "DATE"))
                                .withPrimaryKey("order"), """
                        CREATE TABLE `ordine dettagli` (
                          `order` INT NOT NULL,
                          `select` VARCHAR(10) NULL,
                          `group by` DATE NULL,
                          PRIMARY KEY (`order`)
                        )"""),
                caso("nuova: backtick nel nome", null,
                        TableDef.of(null, "strana`tabella").addColumn(ColumnDef.of("col`onna", "INT")), """
                        CREATE TABLE `strana``tabella` (
                          `col``onna` INT NULL
                        )"""),
                caso("nuova: MyISAM, commenti con apostrofo, AUTO_INCREMENT iniziale", null,
                        TableDef.of(null, "registro")
                                .addColumn(ColumnDef.of("id", "INT").notNull().withAutoIncrement(true)
                                        .withComment("l'identificativo"))
                                .withPrimaryKey("id").withEngine("MyISAM").withAutoIncrementStart(1000L)
                                .withComment("Registro dell'aula"), """
                        CREATE TABLE `registro` (
                          `id` INT NOT NULL AUTO_INCREMENT COMMENT 'l''identificativo',
                          PRIMARY KEY (`id`)
                        ) ENGINE=MyISAM AUTO_INCREMENT=1000 COMMENT='Registro dell''aula'"""),
                caso("nuova: PK composta, nome qualificato dal catalogo", null,
                        LIBRI_AUTORI.withCatalog("ramasql_test_u"), """
                        CREATE TABLE `ramasql_test_u`.`libri_autori` (
                          `id_libro` INT UNSIGNED NOT NULL,
                          `id_autore` INT UNSIGNED NOT NULL,
                          `ruolo` VARCHAR(30) NOT NULL DEFAULT 'autore',
                          PRIMARY KEY (`id_libro`, `id_autore`)
                        ) ENGINE=InnoDB"""),
                caso("nuova: tutti i tipi di default", null,
                        TableDef.of(null, "eventi")
                                .addColumn(ColumnDef.of("id", "CHAR", "36").notNull()
                                        .withDefault(ColumnDefault.expression("uuid()")))
                                .addColumn(ColumnDef.of("titolo", "VARCHAR", "50").notNull()
                                        .withDefault(ColumnDefault.literal("")))
                                .addColumn(ColumnDef.of("posti", "INT").notNull().withDefault(ColumnDefault.literal("0")))
                                .addColumn(ColumnDef.of("sala", "VARCHAR", "20").withDefault(ColumnDefault.NULL_VALUE))
                                .addColumn(ColumnDef.of("creato", "DATETIME").notNull()
                                        .withDefault(ColumnDefault.expression("current_timestamp()")))
                                .addColumn(ColumnDef.of("modificato", "TIMESTAMP").notNull()
                                        .withDefault(ColumnDefault.expression("NOW()"))
                                        .withOnUpdate("current_timestamp()"))
                                .addColumn(ColumnDef.of("preciso", "DATETIME", "6").notNull()
                                        .withDefault(ColumnDefault.expression("current_timestamp(6)")))
                                .withPrimaryKey("id").withEngine("InnoDB"), """
                        CREATE TABLE `eventi` (
                          `id` CHAR(36) NOT NULL DEFAULT (uuid()),
                          `titolo` VARCHAR(50) NOT NULL DEFAULT '',
                          `posti` INT NOT NULL DEFAULT 0,
                          `sala` VARCHAR(20) NULL DEFAULT NULL,
                          `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `modificato` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          `preciso` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                          PRIMARY KEY (`id`)
                        ) ENGINE=InnoDB"""),
                caso("nuova: charset e collation di colonna", null,
                        TableDef.of(null, "codici")
                                .addColumn(ColumnDef.of("codice", "VARCHAR", "10").notNull()
                                        .withCharset("utf8mb4", "utf8mb4_bin"))
                                .addColumn(ColumnDef.of("vecchio", "VARCHAR", "10")
                                        .withCharset("latin1", "latin1_swedish_ci"))
                                .addColumn(ColumnDef.of("normale", "VARCHAR", "10")
                                        .withCharset("utf8mb4", "utf8mb4_general_ci"))
                                .withCharset("utf8mb4", "utf8mb4_general_ci"), """
                        CREATE TABLE `codici` (
                          `codice` VARCHAR(10) COLLATE utf8mb4_bin NOT NULL,
                          `vecchio` VARCHAR(10) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL,
                          `normale` VARCHAR(10) NULL
                        ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"""),

                // ------------------------------------------------------------ nessuna modifica
                caso("nessuna modifica → zero istruzioni", LIBRI, LIBRI),
                caso("nessuna modifica: INT(10) e INT sono lo stesso tipo", LIBRI,
                        LIBRI.changeColumn("id", c -> c.withTypeArgs("10"))),
                caso("nessuna modifica: current_timestamp() è CURRENT_TIMESTAMP", LIBRI,
                        LIBRI.changeColumn("creato", c -> c.withDefault(ColumnDefault.expression("current_timestamp()")))),
                caso("nessuna modifica: nessun default e DEFAULT NULL si equivalgono", LIBRI,
                        LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.NULL_VALUE))),
                caso("nessuna modifica: BOOLEAN è TINYINT(1), INTEGER è INT",
                        SENZA_PK.addColumn(ColumnDef.of("ok", "TINYINT", "1")).withOrdinalPositions(),
                        SENZA_PK.addColumn(ColumnDef.of("ok", "BOOLEAN")).withOrdinalPositions()
                                .changeColumn("a", c -> c.withType("INTEGER", null))),
                caso("nessuna modifica: default numerico 0 e 0.00",
                        LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.literal("0.00"))),
                        LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.literal("0")))),
                caso("nessuna modifica: collation di colonna uguale a quella della tabella",
                        LIBRI.changeColumn("titolo", c -> c.withCharset("utf8mb4", "utf8mb4_general_ci")), LIBRI),
                caso("nessuna modifica: engine scritto in minuscolo, opzioni non specificate",
                        LIBRI, LIBRI.withEngine("innodb").withCharset(null, null)),

                // ------------------------------------------------------------ aggiungi / rimuovi / rinomina
                caso("aggiungi colonna in fondo", LIBRI, LIBRI.addColumn(ColumnDef.of("isbn", "VARCHAR", "13")),
                        "ALTER TABLE `libri` ADD COLUMN `isbn` VARCHAR(13) NULL"),
                caso("aggiungi colonna in testa", LIBRI_AUTORI,
                        LIBRI_AUTORI.addColumn(0, ColumnDef.of("codice", "CHAR", "5").notNull()),
                        "ALTER TABLE `libri_autori` ADD COLUMN `codice` CHAR(5) NOT NULL FIRST"),
                caso("aggiungi colonna in mezzo", LIBRI, LIBRI.addColumn(2, ColumnDef.of("sottotitolo", "VARCHAR", "200")),
                        "ALTER TABLE `libri` ADD COLUMN `sottotitolo` VARCHAR(200) NULL AFTER `titolo`"),
                caso("aggiungi due colonne in fondo", LIBRI,
                        LIBRI.addColumn(ColumnDef.of("isbn", "VARCHAR", "13"))
                                .addColumn(ColumnDef.of("pagine", "SMALLINT").withUnsigned(true)), """
                        ALTER TABLE `libri`
                          ADD COLUMN `isbn` VARCHAR(13) NULL,
                          ADD COLUMN `pagine` SMALLINT UNSIGNED NULL"""),
                caso("aggiungi colonna con default e commento", LIBRI,
                        LIBRI.addColumn(ColumnDef.of("copie", "INT").notNull().withDefault(ColumnDefault.literal("1"))
                                .withComment("copie possedute")),
                        "ALTER TABLE `libri` ADD COLUMN `copie` INT NOT NULL DEFAULT 1 COMMENT 'copie possedute'"),
                caso("aggiungi colonna con nome riservato dopo una con spazi",
                        TableDef.of(null, "ordine dettagli").addColumn(ColumnDef.of("order", "INT").notNull())
                                .addColumn(ColumnDef.of("group by", "DATE")).withOrdinalPositions(),
                        TableDef.of(null, "ordine dettagli").addColumn(ColumnDef.of("order", "INT").notNull())
                                .addColumn(ColumnDef.of("group by", "DATE")).withOrdinalPositions()
                                .addColumn(1, ColumnDef.of("key", "INT")),
                        "ALTER TABLE `ordine dettagli` ADD COLUMN `key` INT NULL AFTER `order`"),
                caso("rimuovi colonna", LIBRI, LIBRI.removeColumn("note"), "ALTER TABLE `libri` DROP COLUMN `note`"),
                caso("rimuovi due colonne", LIBRI, LIBRI.removeColumn("note").removeColumn("prezzo"), """
                        ALTER TABLE `libri`
                          DROP COLUMN `prezzo`,
                          DROP COLUMN `note`"""),
                caso("rinomina colonna: CHANGE COLUMN con la definizione completa", LIBRI,
                        LIBRI.changeColumn("titolo", c -> c.withName("titolo_libro")),
                        "ALTER TABLE `libri` CHANGE COLUMN `titolo` `titolo_libro` VARCHAR(100) NOT NULL"),
                caso("rinomina e cambia tipo insieme", LIBRI,
                        LIBRI.changeColumn("note", c -> c.withName("descrizione").withType("MEDIUMTEXT", null)),
                        "ALTER TABLE `libri` CHANGE COLUMN `note` `descrizione` MEDIUMTEXT NULL"),
                caso("rinomina verso un nome riservato con spazi", LIBRI,
                        LIBRI.changeColumn("stato", c -> c.withName("order by")),
                        "ALTER TABLE `libri` CHANGE COLUMN `stato` `order by` ENUM('nuovo','usato') NOT NULL DEFAULT 'nuovo'"),
                caso("rinomina solo maiuscole/minuscole", LIBRI, LIBRI.changeColumn("titolo", c -> c.withName("Titolo")),
                        "ALTER TABLE `libri` CHANGE COLUMN `titolo` `Titolo` VARCHAR(100) NOT NULL"),
                caso("senza posizioni l'abbinamento è per nome: nome diverso = DROP + ADD",
                        TableDef.of(null, "t").addColumn(ColumnDef.of("a", "INT")).addColumn(ColumnDef.of("b", "INT")),
                        TableDef.of(null, "t").addColumn(ColumnDef.of("a", "BIGINT")).addColumn(ColumnDef.of("c", "INT")), """
                        ALTER TABLE `t`
                          DROP COLUMN `b`,
                          MODIFY COLUMN `a` BIGINT NULL,
                          ADD COLUMN `c` INT NULL"""),

                // ------------------------------------------------------------ tipo, lunghezza, NULL
                caso("cambio tipo INT → BIGINT", LIBRI, LIBRI.changeColumn("id", c -> c.withType("BIGINT", null)),
                        "ALTER TABLE `libri` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT"),
                caso("cambio lunghezza VARCHAR", LIBRI, LIBRI.changeColumn("titolo", c -> c.withTypeArgs("200")),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(200) NOT NULL"),
                caso("cambio precisione DECIMAL", LIBRI, LIBRI.changeColumn("prezzo", c -> c.withTypeArgs("10,3")),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(10,3) NULL"),
                caso("nuovo valore di un ENUM", LIBRI,
                        LIBRI.changeColumn("stato", c -> c.withTypeArgs("'nuovo','usato','d''epoca'")),
                        "ALTER TABLE `libri` MODIFY COLUMN `stato` ENUM('nuovo','usato','d''epoca') NOT NULL DEFAULT 'nuovo'"),
                caso("cambio tipo TEXT → VARCHAR", LIBRI, LIBRI.changeColumn("note", c -> c.withType("VARCHAR", "500")),
                        "ALTER TABLE `libri` MODIFY COLUMN `note` VARCHAR(500) NULL"),
                caso("da NULL a NOT NULL", LIBRI, LIBRI.changeColumn("prezzo", ColumnDef::notNull),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NOT NULL"),
                caso("da NOT NULL a NULL", LIBRI, LIBRI.changeColumn("titolo", c -> c.withNullable(true)),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(100) NULL"),

                // ------------------------------------------------------------ default
                caso("default numerico", LIBRI, LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.literal("0"))),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NULL DEFAULT 0"),
                caso("default stringa vuota", LIBRI, LIBRI.changeColumn("titolo", c -> c.withDefault(ColumnDefault.literal(""))),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(100) NOT NULL DEFAULT ''"),
                caso("default con apostrofo", LIBRI,
                        LIBRI.changeColumn("titolo", c -> c.withDefault(ColumnDefault.literal("senza titolo: l'ignoto"))),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(100) NOT NULL DEFAULT 'senza titolo: l''ignoto'"),
                caso("da default letterale a DEFAULT NULL",
                        LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.literal("9.99"))),
                        LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.NULL_VALUE)),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NULL DEFAULT NULL"),
                caso("rimozione del default", LIBRI, LIBRI.changeColumn("stato", c -> c.withDefault(ColumnDefault.NONE)),
                        "ALTER TABLE `libri` MODIFY COLUMN `stato` ENUM('nuovo','usato') NOT NULL"),
                caso("cambio del default letterale", LIBRI,
                        LIBRI.changeColumn("stato", c -> c.withDefault(ColumnDefault.literal("usato"))),
                        "ALTER TABLE `libri` MODIFY COLUMN `stato` ENUM('nuovo','usato') NOT NULL DEFAULT 'usato'"),
                caso("da letterale a CURRENT_TIMESTAMP",
                        LIBRI.changeColumn("creato", c -> c.withDefault(ColumnDefault.literal("2000-01-01 00:00:00"))), LIBRI,
                        "ALTER TABLE `libri` MODIFY COLUMN `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"),
                caso("da CURRENT_TIMESTAMP a nessun default", LIBRI,
                        LIBRI.changeColumn("creato", c -> c.withDefault(ColumnDefault.NONE)),
                        "ALTER TABLE `libri` MODIFY COLUMN `creato` DATETIME NOT NULL"),
                caso("aggiunta di ON UPDATE CURRENT_TIMESTAMP", LIBRI,
                        LIBRI.changeColumn("creato", c -> c.withOnUpdate("CURRENT_TIMESTAMP")),
                        "ALTER TABLE `libri` MODIFY COLUMN `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"),
                caso("NOT NULL con DEFAULT NULL: il default non si scrive", LIBRI,
                        LIBRI.changeColumn("prezzo", c -> c.notNull().withDefault(ColumnDefault.NULL_VALUE)),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NOT NULL"),

                // ------------------------------------------------------------ AI, UNSIGNED, commento, collation
                caso("togli AUTO_INCREMENT", LIBRI, LIBRI.changeColumn("id", c -> c.withAutoIncrement(false)),
                        "ALTER TABLE `libri` MODIFY COLUMN `id` INT UNSIGNED NOT NULL"),
                caso("metti AUTO_INCREMENT (la colonna è già chiave)",
                        LIBRI.changeColumn("id", c -> c.withAutoIncrement(false)), LIBRI,
                        "ALTER TABLE `libri` MODIFY COLUMN `id` INT UNSIGNED NOT NULL AUTO_INCREMENT"),
                caso("togli UNSIGNED", LIBRI, LIBRI.changeColumn("id", c -> c.withUnsigned(false)),
                        "ALTER TABLE `libri` MODIFY COLUMN `id` INT NOT NULL AUTO_INCREMENT"),
                caso("metti UNSIGNED", SENZA_PK, SENZA_PK.changeColumn("b", c -> c.withUnsigned(true)),
                        "ALTER TABLE `t` MODIFY COLUMN `b` INT UNSIGNED NOT NULL"),
                caso("aggiungi commento di colonna", LIBRI,
                        LIBRI.changeColumn("prezzo", c -> c.withComment("in euro, IVA inclusa: l'importo")),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NULL COMMENT 'in euro, IVA inclusa: l''importo'"),
                caso("togli commento di colonna", LIBRI.changeColumn("prezzo", c -> c.withComment("in euro")), LIBRI,
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NULL"),
                caso("collation di colonna", LIBRI, LIBRI.changeColumn("titolo", c -> c.withCharset("utf8mb4", "utf8mb4_bin")),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(100) COLLATE utf8mb4_bin NOT NULL"),
                caso("charset di colonna", LIBRI,
                        LIBRI.changeColumn("titolo", c -> c.withCharset("latin1", "latin1_swedish_ci")),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(100) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL"),

                // ------------------------------------------------------------ chiave primaria
                caso("PK semplice aggiunta", SENZA_PK, SENZA_PK.withPrimaryKey("a"),
                        "ALTER TABLE `t` ADD PRIMARY KEY (`a`)"),
                caso("PK composta aggiunta", SENZA_PK, SENZA_PK.withPrimaryKey("a", "b"),
                        "ALTER TABLE `t` ADD PRIMARY KEY (`a`, `b`)"),
                caso("PK tolta", LIBRI_AUTORI, LIBRI_AUTORI.withPrimaryKey(),
                        "ALTER TABLE `libri_autori` DROP PRIMARY KEY"),
                caso("PK tolta insieme all'AUTO_INCREMENT che la richiede", LIBRI,
                        LIBRI.withPrimaryKey().changeColumn("id", c -> c.withAutoIncrement(false)), """
                        ALTER TABLE `libri`
                          DROP PRIMARY KEY,
                          MODIFY COLUMN `id` INT UNSIGNED NOT NULL"""),
                caso("PK composta: ordine delle colonne cambiato", LIBRI_AUTORI,
                        LIBRI_AUTORI.withPrimaryKey("id_autore", "id_libro"), """
                        ALTER TABLE `libri_autori`
                          DROP PRIMARY KEY,
                          ADD PRIMARY KEY (`id_autore`, `id_libro`)"""),
                caso("PK da composta a semplice", LIBRI_AUTORI, LIBRI_AUTORI.withPrimaryKey("id_libro"), """
                        ALTER TABLE `libri_autori`
                          DROP PRIMARY KEY,
                          ADD PRIMARY KEY (`id_libro`)"""),
                caso("PK da semplice a composta", SENZA_PK.withPrimaryKey("a"), SENZA_PK.withPrimaryKey("a", "b"), """
                        ALTER TABLE `t`
                          DROP PRIMARY KEY,
                          ADD PRIMARY KEY (`a`, `b`)"""),
                caso("rinomina di una colonna della PK: la PK non si tocca", LIBRI_AUTORI,
                        LIBRI_AUTORI.changeColumn("id_libro", c -> c.withName("libro")).withPrimaryKey("libro", "id_autore"),
                        "ALTER TABLE `libri_autori` CHANGE COLUMN `id_libro` `libro` INT UNSIGNED NOT NULL"),
                caso("nuova colonna AUTO_INCREMENT che diventa PK", SENZA_PK,
                        SENZA_PK.addColumn(0, ColumnDef.of("id", "INT").notNull().withAutoIncrement(true))
                                .withPrimaryKey("id"), """
                        ALTER TABLE `t`
                          ADD COLUMN `id` INT NOT NULL AUTO_INCREMENT FIRST,
                          ADD PRIMARY KEY (`id`)"""),

                // ------------------------------------------------------------ opzioni di tabella
                caso("engine InnoDB → MyISAM", LIBRI, LIBRI.withEngine("MyISAM"), "ALTER TABLE `libri` ENGINE=MyISAM"),
                caso("engine MyISAM → InnoDB", MYISAM, MYISAM.withEngine("InnoDB"), "ALTER TABLE `registro` ENGINE=InnoDB"),
                caso("charset e collation", LIBRI, LIBRI.withCharset("latin1", "latin1_swedish_ci"),
                        "ALTER TABLE `libri` DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci"),
                caso("solo collation", LIBRI, LIBRI.withCharset("utf8mb4", "utf8mb4_unicode_ci"),
                        "ALTER TABLE `libri` COLLATE=utf8mb4_unicode_ci"),
                caso("commento di tabella", LIBRI, LIBRI.withComment("Catalogo dell'istituto"),
                        "ALTER TABLE `libri` COMMENT='Catalogo dell''istituto'"),
                caso("commento di tabella tolto", LIBRI.withComment("Catalogo"), LIBRI, "ALTER TABLE `libri` COMMENT=''"),
                caso("AUTO_INCREMENT iniziale", LIBRI, LIBRI.withAutoIncrementStart(1000L),
                        "ALTER TABLE `libri` AUTO_INCREMENT=1000"),

                // ------------------------------------------------------------ rinomina tabella e combinazioni
                caso("rinomina tabella", LIBRI, LIBRI.withName("volumi"), "RENAME TABLE `libri` TO `volumi`"),
                caso("rinomina tabella: catalogo, spazi e parola riservata",
                        LIBRI.withCatalog("ramasql_test_u"), LIBRI.withCatalog("ramasql_test_u").withName("order by"),
                        "RENAME TABLE `ramasql_test_u`.`libri` TO `ramasql_test_u`.`order by`"),
                caso("rinomina tabella + nuova colonna: prima RENAME, poi ALTER sul nome nuovo", LIBRI,
                        LIBRI.withName("volumi").addColumn(ColumnDef.of("isbn", "CHAR", "13")),
                        "RENAME TABLE `libri` TO `volumi`",
                        "ALTER TABLE `volumi` ADD COLUMN `isbn` CHAR(13) NULL"),
                caso("combinazione: DROP, MODIFY, ADD, engine e commento in un solo ALTER", LIBRI,
                        LIBRI.removeColumn("note").changeColumn("titolo", c -> c.withTypeArgs("200"))
                                .addColumn(2, ColumnDef.of("isbn", "CHAR", "13"))
                                .withEngine("MyISAM").withComment("Catalogo"), """
                        ALTER TABLE `libri`
                          DROP COLUMN `note`,
                          MODIFY COLUMN `titolo` VARCHAR(200) NOT NULL,
                          ADD COLUMN `isbn` CHAR(13) NULL AFTER `titolo`,
                          ENGINE=MyISAM,
                          COMMENT='Catalogo'"""),
                caso("combinazione: rinomina, NULL, default e PK in un solo ALTER", SENZA_PK,
                        SENZA_PK.changeColumn("a", c -> c.withName("codice").withType("VARCHAR", "8"))
                                .changeColumn("b", c -> c.withNullable(true).withDefault(ColumnDefault.literal("5")))
                                .withPrimaryKey("codice"), """
                        ALTER TABLE `t`
                          CHANGE COLUMN `a` `codice` VARCHAR(8) NOT NULL,
                          MODIFY COLUMN `b` INT NULL DEFAULT 5,
                          ADD PRIMARY KEY (`codice`)"""),
                caso("combinazione con catalogo: l'ALTER è qualificato", LIBRI.withCatalog("ramasql_test_u"),
                        LIBRI.withCatalog("ramasql_test_u").removeColumn("note"),
                        "ALTER TABLE `ramasql_test_u`.`libri` DROP COLUMN `note`"),

                // ------------------------------------------------------------ elementi avanzati
                caso("elementi avanzati: CHECK, colonna generata e partizioni non vengono mai toccati",
                        LIBRI.addColumn(ColumnDef.of("prezzo_ivato", "DECIMAL", "10,2").withGenerated(true)
                                        .withOrdinalPosition(7))
                                .withAdvancedElements(List.of(
                                        "CONSTRAINT `chk_prezzo` CHECK (`prezzo` >= 0)",
                                        "`prezzo_ivato` decimal(10,2) GENERATED ALWAYS AS (`prezzo` * 1.22) STORED",
                                        "PARTITION BY HASH (`id`) PARTITIONS 4")),
                        LIBRI.changeColumn("titolo", c -> c.withTypeArgs("150")),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(150) NOT NULL"),
                caso("elementi avanzati: una colonna generata non si aggiunge né si modifica",
                        LIBRI.addColumn(ColumnDef.of("g", "INT").withGenerated(true).withOrdinalPosition(7)),
                        LIBRI.addColumn(ColumnDef.of("g", "BIGINT").withGenerated(true).withOrdinalPosition(7))
                                .addColumn(ColumnDef.of("h", "INT").withGenerated(true))));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("casi")
    void diff(String nome, TableDef originale, TableDef modificata, List<String> attese) {
        assertEquals(attese, TableDiff.diff(originale, modificata, MARIADB), "MariaDB");
        assertEquals(attese, TableDiff.diff(originale, modificata, MYSQL), "MySQL");
        for (String sql : attese) {
            assertTrue(sql.contains("`"), "identificatori sempre tra backtick");
        }
    }

    @Test
    void iCasiSonoAlmenoSessanta() {
        assertTrue(casi().count() >= 60, "T5.1 chiede almeno 60 casi, sono " + casi().count());
    }

    @Test
    void leIstruzioniPerLaPipelineHannoOrigineERischio() {
        List<SqlStatement> s = TableDiff.statements(LIBRI, LIBRI.removeColumn("note").withName("volumi"), MYSQL,
                "Editor di tabelle");
        assertEquals(2, s.size());
        assertEquals("Editor di tabelle", s.get(0).origin());
        assertEquals(RiskLevel.MODIFIES, s.get(0).risk(), "RENAME TABLE");
        assertEquals(RiskLevel.DESTRUCTIVE, s.get(1).risk(), "ALTER … DROP COLUMN");
        assertEquals(RiskLevel.MODIFIES, TableDiff.statements(null, LIBRI, MARIADB, "x").get(0).risk());
    }
}
