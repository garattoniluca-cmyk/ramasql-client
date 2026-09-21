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
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T6.2 — {@code TableDiff} sulle chiavi esterne: stesso SQL atteso su MariaDB 11 e MySQL 8.0.
 * Tabelle riferite nei casi: {@code editori(id INT UNSIGNED PK)}, {@code libri_autori(id_libro, id_autore PK)}.
 */
@Tag("step6")
class TableDiffForeignKeyTest {

    static final ServerInfo MARIADB = ServerInfo.parse("11.5.2-MariaDB");
    static final ServerInfo MYSQL = ServerInfo.parse("8.0.39");

    /** La colonna della FK è annullabile: così anche SET NULL è eseguibile davvero. */
    static final TableDef LIBRI = TableDef.of(null, "libri")
            .addColumn(ColumnDef.of("id", "INT").withUnsigned(true).notNull().withAutoIncrement(true))
            .addColumn(ColumnDef.of("titolo", "VARCHAR", "100").notNull())
            .addColumn(ColumnDef.of("id_editore", "INT").withUnsigned(true))
            .withPrimaryKey("id")
            .withEngine("InnoDB")
            .withOrdinalPositions();

    static final ForeignKeyDef FK_EDITORI = ForeignKeyDef.of("fk_libri_editori", "id_editore", "editori", "id");
    static final TableDef LIBRI_CON_FK = LIBRI.addForeignKey(FK_EDITORI);

    static final TableDef CITAZIONI = TableDef.of(null, "citazioni")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("id_libro", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("id_autore", "INT").withUnsigned(true).notNull())
            .withPrimaryKey("id").withEngine("InnoDB").withOrdinalPositions();

    static final TableDef CATEGORIE = TableDef.of(null, "categorie")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("id_padre", "INT"))
            .withPrimaryKey("id").withEngine("InnoDB").withOrdinalPositions();

    static final ForeignKeyDef FK_PADRE =
            ForeignKeyDef.of("fk_categorie_padre", "id_padre", "categorie", "id").withOnDelete(FkAction.CASCADE);

    private static Arguments caso(String nome, TableDef originale, TableDef modificata, String... attese) {
        return arguments(nome, originale, modificata, List.of(attese));
    }

    static Stream<Arguments> casi() {
        List<Arguments> casi = new ArrayList<>();
        // ---- le 16 combinazioni ON DELETE × ON UPDATE
        for (FkAction onDelete : FkAction.values()) {
            for (FkAction onUpdate : FkAction.values()) {
                casi.add(caso("crea FK: ON DELETE " + onDelete.sql() + " ON UPDATE " + onUpdate.sql(), LIBRI,
                        LIBRI.addForeignKey(FK_EDITORI.withActions(onDelete, onUpdate)),
                        "ALTER TABLE `libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                                + "REFERENCES `editori` (`id`) ON DELETE " + onDelete.sql() + " ON UPDATE "
                                + onUpdate.sql()));
            }
        }
        casi.add(caso("FK composta", CITAZIONI,
                CITAZIONI.addForeignKey(ForeignKeyDef.of("fk_citazioni_libri_autori", List.of("id_libro", "id_autore"),
                        "libri_autori", List.of("id_libro", "id_autore")).withOnDelete(FkAction.CASCADE)),
                "ALTER TABLE `citazioni` ADD CONSTRAINT `fk_citazioni_libri_autori` FOREIGN KEY (`id_libro`, `id_autore`) "
                        + "REFERENCES `libri_autori` (`id_libro`, `id_autore`) ON DELETE CASCADE ON UPDATE RESTRICT"));
        casi.add(caso("elimina FK", LIBRI_CON_FK, LIBRI, "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`"));
        casi.add(caso("modifica azione: prima DROP FOREIGN KEY, poi ADD CONSTRAINT, in due istruzioni", LIBRI_CON_FK,
                LIBRI_CON_FK.changeForeignKey("fk_libri_editori", f -> f.withOnDelete(FkAction.SET_NULL)),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `editori` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT"));
        casi.add(caso("modifica tabella riferita: DROP poi ADD", LIBRI_CON_FK,
                LIBRI_CON_FK.changeForeignKey("fk_libri_editori", f -> f.withReference(null, "case_editrici", List.of("codice"))),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `case_editrici` (`codice`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("rinomina FK: DROP poi ADD con il nome nuovo", LIBRI_CON_FK,
                LIBRI_CON_FK.changeForeignKey("fk_libri_editori", f -> f.withName("fk editore")),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk editore` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("FK autoreferenziale", CATEGORIE, CATEGORIE.addForeignKey(FK_PADRE),
                "ALTER TABLE `categorie` ADD CONSTRAINT `fk_categorie_padre` FOREIGN KEY (`id_padre`) "
                        + "REFERENCES `categorie` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT"));
        casi.add(caso("FK autoreferenziale già nel CREATE TABLE", null, CATEGORIE.addForeignKey(FK_PADRE), """
                CREATE TABLE `categorie` (
                  `id` INT NOT NULL,
                  `nome` VARCHAR(50) NOT NULL,
                  `id_padre` INT NULL,
                  PRIMARY KEY (`id`),
                  CONSTRAINT `fk_categorie_padre` FOREIGN KEY (`id_padre`) REFERENCES `categorie` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
                ) ENGINE=InnoDB"""));
        casi.add(caso("FK autoreferenziale + rinomina della tabella: basta il RENAME",
                CATEGORIE.addForeignKey(FK_PADRE), CATEGORIE.addForeignKey(FK_PADRE).withName("generi"),
                "RENAME TABLE `categorie` TO `generi`"));
        casi.add(caso("nuova colonna, indice e FK in un solo ALTER, nell'ordine giusto", CITAZIONI,
                CITAZIONI.addColumn(ColumnDef.of("id_editore", "INT").withUnsigned(true))
                        .addIndex(IndexDef.index("ix_editore", "id_editore"))
                        .addForeignKey(FK_EDITORI.withName("fk_citazioni_editori")), """
                ALTER TABLE `citazioni`
                  ADD COLUMN `id_editore` INT UNSIGNED NULL,
                  ADD INDEX `ix_editore` (`id_editore`),
                  ADD CONSTRAINT `fk_citazioni_editori` FOREIGN KEY (`id_editore`) REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT"""));
        casi.add(caso("elimina FK e la sua colonna: prima l'ALTER della FK, poi quello della colonna", LIBRI_CON_FK,
                LIBRI.removeColumn("id_editore"),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` DROP COLUMN `id_editore`"));
        casi.add(caso("due FK eliminate e una creata", LIBRI_CON_FK.addForeignKey(FK_EDITORI.withName("fk_doppia")),
                LIBRI.addForeignKey(FK_EDITORI.withName("fk_nuova").withOnUpdate(FkAction.CASCADE)), """
                ALTER TABLE `libri`
                  DROP FOREIGN KEY `fk_libri_editori`,
                  DROP FOREIGN KEY `fk_doppia`""",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk_nuova` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE"));
        casi.add(caso("FK senza nome: lo assegna il server", LIBRI, LIBRI.addForeignKey(FK_EDITORI.withName(null)),
                "ALTER TABLE `libri` ADD FOREIGN KEY (`id_editore`) REFERENCES `editori` (`id`) "
                        + "ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("FK verso un altro catalogo: tabella riferita qualificata", LIBRI.withCatalog("ramasql_test_a"),
                LIBRI.withCatalog("ramasql_test_a").addForeignKey(
                        FK_EDITORI.withReference("ramasql_test_b", "editori", List.of("id"))),
                "ALTER TABLE `ramasql_test_a`.`libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `ramasql_test_b`.`editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("nomi con spazi e parole riservate",
                TableDef.of(null, "ordine dettagli").addColumn(ColumnDef.of("order", "INT")).withOrdinalPositions(),
                TableDef.of(null, "ordine dettagli").addColumn(ColumnDef.of("order", "INT")).withOrdinalPositions()
                        .addForeignKey(ForeignKeyDef.of("fk order", "order", "order", "primary key")),
                "ALTER TABLE `ordine dettagli` ADD CONSTRAINT `fk order` FOREIGN KEY (`order`) "
                        + "REFERENCES `order` (`primary key`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("nessuna modifica → zero istruzioni", LIBRI_CON_FK, LIBRI_CON_FK));
        casi.add(caso("nessuna modifica: catalogo riferito esplicito ma uguale", LIBRI_CON_FK.withCatalog("c"),
                LIBRI.withCatalog("c").addForeignKey(FK_EDITORI.withReference("c", "editori", List.of("id")))));
        casi.add(caso("rinomina della colonna della FK: la FK non si tocca", LIBRI_CON_FK,
                LIBRI.changeColumn("id_editore", c -> c.withName("editore"))
                        .addForeignKey(FK_EDITORI.withColumns(List.of("editore"), List.of("id"))),
                "ALTER TABLE `libri` CHANGE COLUMN `id_editore` `editore` INT UNSIGNED NULL"));
        return casi.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("casi")
    void diff(String nome, TableDef originale, TableDef modificata, List<String> attese) {
        assertEquals(attese, TableDiff.diff(originale, modificata, MARIADB), "MariaDB");
        assertEquals(attese, TableDiff.diff(originale, modificata, MYSQL), "MySQL");
    }

    @Test
    void leCombinazioniDiAzioniSonoSedici() {
        long combinazioni = casi().filter(a -> ((String) a.get()[0]).startsWith("crea FK: ON DELETE")).count();
        assertEquals(16, combinazioni);
        assertTrue(casi().count() >= 20, "T6.2 chiede almeno 20 casi");
    }

    @Test
    void nellaModificaIlDropPrecedeSempreLAdd() {
        for (FkAction azione : List.of(FkAction.CASCADE, FkAction.SET_NULL, FkAction.NO_ACTION)) {
            List<String> sql = TableDiff.diff(LIBRI_CON_FK,
                    LIBRI_CON_FK.changeForeignKey("fk_libri_editori", f -> f.withOnUpdate(azione)), MYSQL);
            assertEquals(2, sql.size(), "due istruzioni distinte");
            assertTrue(sql.get(0).contains("DROP FOREIGN KEY `fk_libri_editori`"));
            assertTrue(sql.get(1).contains("ADD CONSTRAINT `fk_libri_editori`"));
            assertTrue(sql.get(1).endsWith("ON UPDATE " + azione.sql()));
        }
    }

    @Test
    void leAzioniSiLeggonoDalTestoDiInformationSchema() {
        assertEquals(FkAction.SET_NULL, FkAction.fromSql("SET NULL"));
        assertEquals(FkAction.NO_ACTION, FkAction.fromSql("no action"));
        assertEquals(FkAction.RESTRICT, FkAction.fromSql(null));
        assertEquals(FkAction.CASCADE, FkAction.fromSql(" CASCADE "));
    }
}
