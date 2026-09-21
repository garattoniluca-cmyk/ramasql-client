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

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.PrecheckWarning.Code;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T6.3 — controlli preventivi di FK e indici (solo metadati, nessun server) e query di orfani e duplicati. */
@Tag("step6")
class FkPrecheckTest {

    static final TableDef SOCI = TableDef.of(null, "soci")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("tessera", "VARCHAR", "10").notNull())
            .addColumn(ColumnDef.of("email", "VARCHAR", "100"))
            .addColumn(ColumnDef.of("cognome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "50").notNull())
            .withPrimaryKey("id")
            .addIndex(IndexDef.unique("uq_tessera", "tessera"))
            .addIndex(IndexDef.index("ix_nome", "cognome", "nome"))
            .withEngine("InnoDB").withCharset("utf8mb4", "utf8mb4_general_ci");

    static final TableDef PRESTITI = TableDef.of(null, "prestiti")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("id_socio", "INT").notNull())
            .addColumn(ColumnDef.of("id_socio_null", "INT"))
            .addColumn(ColumnDef.of("tessera", "VARCHAR", "20").notNull())
            .withPrimaryKey("id")
            .withEngine("InnoDB").withCharset("utf8mb4", "utf8mb4_general_ci");

    static final ForeignKeyDef FK = ForeignKeyDef.of("fk_prestiti_soci", "id_socio", "soci", "id");

    static Stream<Arguments> casi() {
        return Stream.of(
                arguments("tutto in ordine: nessun avviso", PRESTITI, FK, SOCI, List.of()),
                arguments("INT contro INT UNSIGNED", PRESTITI.changeColumn("id_socio", c -> c.withUnsigned(true)), FK, SOCI,
                        List.of(Code.FK_SIGN_MISMATCH)),
                arguments("INT contro BIGINT", PRESTITI, FK, SOCI.changeColumn("id", c -> c.withType("BIGINT", null)),
                        List.of(Code.FK_TYPE_MISMATCH)),
                arguments("INT(11) contro INT: la larghezza non conta", PRESTITI.changeColumn("id_socio", c -> c.withTypeArgs("11")),
                        FK, SOCI, List.of()),
                arguments("VARCHAR con collation diverse",
                        PRESTITI.changeColumn("tessera", c -> c.withCharset("utf8mb4", "utf8mb4_bin")),
                        ForeignKeyDef.of("fk_tessera", "tessera", "soci", "tessera"), SOCI,
                        List.of(Code.FK_COLLATION_MISMATCH)),
                arguments("VARCHAR con collation di tabella diverse", PRESTITI.withCharset("latin1", "latin1_swedish_ci"),
                        ForeignKeyDef.of("fk_tessera", "tessera", "soci", "tessera"), SOCI,
                        List.of(Code.FK_COLLATION_MISMATCH)),
                arguments("VARCHAR di lunghezza diversa, stessa collation: ammesso", PRESTITI,
                        ForeignKeyDef.of("fk_tessera", "tessera", "soci", "tessera"), SOCI, List.of()),
                arguments("colonna riferita senza indice", PRESTITI,
                        ForeignKeyDef.of("fk_email", "tessera", "soci", "email"), SOCI,
                        List.of(Code.FK_REFERENCED_NOT_INDEXED)),
                arguments("colonna riferita seconda in un indice: non è prefisso sinistro", PRESTITI,
                        ForeignKeyDef.of("fk_nome", "tessera", "soci", "nome"), SOCI,
                        List.of(Code.FK_REFERENCED_NOT_INDEXED)),
                arguments("colonna riferita prima di un indice composto: va bene", PRESTITI,
                        ForeignKeyDef.of("fk_cognome", "tessera", "soci", "cognome"), SOCI, List.of()),
                arguments("SET NULL su colonna NOT NULL", PRESTITI, FK.withOnDelete(FkAction.SET_NULL), SOCI,
                        List.of(Code.FK_SET_NULL_ON_NOT_NULL)),
                arguments("ON UPDATE SET NULL su colonna NOT NULL", PRESTITI, FK.withOnUpdate(FkAction.SET_NULL), SOCI,
                        List.of(Code.FK_SET_NULL_ON_NOT_NULL)),
                arguments("SET NULL su colonna annullabile: va bene", PRESTITI,
                        ForeignKeyDef.of("fk", "id_socio_null", "soci", "id").withOnDelete(FkAction.SET_NULL), SOCI,
                        List.of()),
                arguments("tabella figlia MyISAM", PRESTITI.withEngine("MyISAM"), FK, SOCI,
                        List.of(Code.FK_CHILD_NOT_INNODB)),
                arguments("tabella riferita MyISAM", PRESTITI, FK, SOCI.withEngine("MyISAM"),
                        List.of(Code.FK_PARENT_NOT_INNODB)),
                arguments("colonna inesistente", PRESTITI, ForeignKeyDef.of("fk", "id_socio", "soci", "codice"), SOCI,
                        List.of(Code.FK_COLUMN_NOT_FOUND)),
                arguments("numero di colonne diverso", PRESTITI,
                        ForeignKeyDef.of("fk", List.of("id_socio", "tessera"), "soci", List.of("id")), SOCI,
                        List.of(Code.FK_COLUMN_COUNT_MISMATCH)),
                arguments("più problemi insieme: ciascuno il suo avviso",
                        PRESTITI.withEngine("MyISAM").changeColumn("id_socio", c -> c.withUnsigned(true)),
                        FK.withOnDelete(FkAction.SET_NULL), SOCI,
                        List.of(Code.FK_CHILD_NOT_INNODB, Code.FK_SIGN_MISMATCH, Code.FK_SET_NULL_ON_NOT_NULL)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("casi")
    void controlla(String nome, TableDef figlia, ForeignKeyDef fk, TableDef riferita, List<Code> attesi) {
        List<PrecheckWarning> avvisi = FkPrecheck.check(figlia, fk, riferita);
        assertEquals(attesi, avvisi.stream().map(PrecheckWarning::code).toList());
        for (PrecheckWarning a : avvisi) {
            assertFalse(a.message().isBlank(), "ogni avviso ha la sua spiegazione");
            assertFalse(a.message().contains("%s"), "segnaposto risolti: " + a.message());
        }
    }

    @Test
    void iMessaggiSonoInItalianoENominanoLeColonne() {
        String segno = FkPrecheck.check(PRESTITI.changeColumn("id_socio", c -> c.withUnsigned(true)), FK, SOCI)
                .get(0).message();
        assertEquals("Segno diverso: «id_socio» è INT UNSIGNED ma la colonna riferita «id» è INT. "
                + "Devono essere entrambe UNSIGNED o entrambe con segno.", segno);
        String myisam = FkPrecheck.check(PRESTITI.withEngine("MyISAM"), FK, SOCI).get(0).message();
        assertTrue(myisam.contains("«prestiti»") && myisam.contains("MyISAM") && myisam.contains("InnoDB"), myisam);
    }

    @Test
    void fkAutoreferenziale() {
        TableDef categorie = TableDef.of(null, "categorie")
                .addColumn(ColumnDef.of("id", "INT").notNull())
                .addColumn(ColumnDef.of("id_padre", "INT"))
                .withPrimaryKey("id").withEngine("InnoDB");
        ForeignKeyDef fk = ForeignKeyDef.of("fk_padre", "id_padre", "categorie", "id").withOnDelete(FkAction.SET_NULL);
        assertEquals(List.of(), FkPrecheck.check(categorie, fk, categorie));
        assertEquals("SELECT figlia.* FROM `categorie` AS figlia LEFT JOIN `categorie` AS riferita "
                + "ON riferita.`id` = figlia.`id_padre` WHERE figlia.`id_padre` IS NOT NULL AND riferita.`id` IS NULL",
                FkPrecheck.orphanRowsQuery(categorie, fk));
    }

    @Test
    void queryDelleRigheOrfane() {
        assertEquals("SELECT figlia.* FROM `prestiti` AS figlia LEFT JOIN `soci` AS riferita "
                + "ON riferita.`id` = figlia.`id_socio` WHERE figlia.`id_socio` IS NOT NULL AND riferita.`id` IS NULL",
                FkPrecheck.orphanRowsQuery(PRESTITI, FK));
        ForeignKeyDef composta = ForeignKeyDef.of("fk", List.of("id_libro", "id_autore"), "libri autori",
                List.of("libro", "autore")).withReference("altro", "libri autori", List.of("libro", "autore"));
        assertEquals("SELECT figlia.* FROM `bib`.`citazioni` AS figlia LEFT JOIN `altro`.`libri autori` AS riferita "
                + "ON riferita.`libro` = figlia.`id_libro` AND riferita.`autore` = figlia.`id_autore` "
                + "WHERE figlia.`id_libro` IS NOT NULL AND figlia.`id_autore` IS NOT NULL AND riferita.`libro` IS NULL",
                FkPrecheck.orphanRowsQuery(TableDef.of("bib", "citazioni"), composta));
    }

    // ---------------------------------------------------------------- indici

    static Stream<Arguments> casiIndice() {
        return Stream.of(
                arguments("indice nuovo su colonna libera: nessun avviso", IndexDef.index("ix_email", "email"), List.of()),
                arguments("doppione di un UNIQUE esistente", IndexDef.index("ix_tessera", "tessera"),
                        List.of(Code.INDEX_DUPLICATE)),
                arguments("doppione della chiave primaria", IndexDef.unique("uq_id", "id"), List.of(Code.INDEX_DUPLICATE)),
                arguments("doppione multi-colonna", IndexDef.index("ix_anagrafica", "cognome", "nome"),
                        List.of(Code.INDEX_DUPLICATE)),
                arguments("stesse colonne in ordine diverso: non è un doppione", IndexDef.index("ix_inverso", "nome", "cognome"),
                        List.of()),
                arguments("prefisso sinistro di un indice esistente: superfluo", IndexDef.index("ix_cognome", "cognome"),
                        List.of(Code.INDEX_REDUNDANT_PREFIX)),
                arguments("UNIQUE sul prefisso: ha un senso, nessun avviso", IndexDef.unique("uq_cognome", "cognome"),
                        List.of()),
                arguments("modifica di un indice esistente: non è doppione di se stesso",
                        IndexDef.index("ix_nome", "cognome", "nome"), List.of()),
                arguments("colonna inesistente", IndexDef.index("ix_x", "telefono"), List.of(Code.INDEX_COLUMN_NOT_FOUND)),
                arguments("colonna ripetuta", IndexDef.index("ix_x", "email", "EMAIL"), List.of(Code.INDEX_INVALID_COLUMNS)),
                arguments("senza colonne", IndexDef.index("ix_x"), List.of(Code.INDEX_INVALID_COLUMNS)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("casiIndice")
    void controllaIndice(String nome, IndexDef proposto, List<Code> attesi) {
        List<PrecheckWarning> avvisi = IndexPrecheck.check(SOCI, proposto);
        assertEquals(attesi, avvisi.stream().map(PrecheckWarning::code).toList());
        for (PrecheckWarning a : avvisi) {
            assertFalse(a.message().isBlank() || a.message().contains("%s"), a.message());
        }
    }

    @Test
    void queryDeiDuplicati() {
        assertEquals("SELECT `email`, COUNT(*) AS `occorrenze` FROM `soci` WHERE `email` IS NOT NULL "
                + "GROUP BY `email` HAVING COUNT(*) > 1", IndexPrecheck.duplicatesQuery(SOCI, List.of("email")));
        assertEquals("SELECT `cognome`, `nome`, COUNT(*) AS `occorrenze` FROM `bib`.`soci` "
                + "WHERE `cognome` IS NOT NULL AND `nome` IS NOT NULL GROUP BY `cognome`, `nome` HAVING COUNT(*) > 1",
                IndexPrecheck.duplicatesQuery(SOCI.withCatalog("bib"), List.of("cognome", "nome")));
    }
}
