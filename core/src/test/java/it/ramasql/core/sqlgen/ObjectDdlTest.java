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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.MetadataReader;

/** Generatori delle operazioni del navigatore (Step 3): SQL atteso, rischio e conferma. */
@Tag("step3")
class ObjectDdlTest {

    @Test
    void creaEdEliminaCatalogo() {
        assertEquals("CREATE DATABASE `ramasql_test_a` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                ObjectDdl.createCatalog("ramasql_test_a", "utf8mb4", "utf8mb4_unicode_ci"));
        assertEquals("CREATE DATABASE `scuola 2`", ObjectDdl.createCatalog("scuola 2", null, " "));
        assertEquals("CREATE DATABASE `x` COLLATE latin1_swedish_ci",
                ObjectDdl.createCatalog("x", null, "latin1_swedish_ci"));
        assertEquals("DROP DATABASE `a``b`", ObjectDdl.dropCatalog("a`b"));
        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class,
                () -> ObjectDdl.createCatalog("x", "utf8mb4; DROP", null));
        // il messaggio viene dai file di risorse (CoreMessages), non è scritto nel codice
        assertEquals(it.ramasql.core.CoreMessages.get("sqlgen.charsetName.invalid", "utf8mb4; DROP"), bad.getMessage());
        assertEquals("Nome di charset o collation non valido: utf8mb4; DROP", bad.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ObjectDdl.createCatalog("", null, null));
    }

    @Test
    void operazioniSulleTabelle() {
        assertEquals("RENAME TABLE `biblioteca`.`soci` TO `biblioteca`.`iscritti`",
                ObjectDdl.renameTable("biblioteca", "soci", "iscritti"));
        assertEquals("TRUNCATE TABLE `biblioteca`.`prestiti`", ObjectDdl.truncateTable("biblioteca", "prestiti"));
        assertEquals("DROP TABLE `biblioteca`.`ordine dettagli`",
                ObjectDdl.dropTable("biblioteca", "ordine dettagli"));
        assertEquals("DROP VIEW `biblioteca`.`v_aperti`", ObjectDdl.dropView("biblioteca", "v_aperti"));
        // «Mostra SQL di creazione»: una sola implementazione, quella del canale dei metadati che la esegue
        assertEquals("SHOW CREATE TABLE `b`.`order`", MetadataReader.showCreateStatement("TABLE", "b", "order"));
        assertEquals("SHOW CREATE VIEW `b`.`v`", MetadataReader.showCreateStatement("VIEW", "b", "v"));
        assertEquals("SHOW CREATE TABLE `b`.`a``b`", MetadataReader.showCreateStatement("TABLE", "b", "a`b"));
        assertEquals("DROP TABLE `t`", ObjectDdl.dropTable(null, "t"));
    }

    @Test
    void scriptDelNavigatoreConTitoloOrigineERischio() {
        SqlScript drop = TreeScripts.dropTable("biblioteca", "libri");
        assertEquals("Elimina la tabella «libri»", drop.title());
        assertEquals("Navigatore", drop.origin());
        assertEquals(List.of(new SqlStatement("DROP TABLE `biblioteca`.`libri`", "Navigatore",
                RiskLevel.DESTRUCTIVE)), drop.statements());
        assertEquals("libri", ConfirmationPolicy.evaluate(drop).typeToConfirm());

        SqlScript truncate = TreeScripts.truncateTable("b", "prestiti");
        assertEquals(RiskLevel.DESTRUCTIVE, truncate.risk());
        assertEquals("prestiti", ConfirmationPolicy.evaluate(truncate).typeToConfirm());
        assertEquals(RiskLevel.DESTRUCTIVE, TreeScripts.dropCatalog("ramasql_test_x").risk());
        assertEquals(RiskLevel.DESTRUCTIVE, TreeScripts.dropView("b", "v").risk());
        assertEquals(RiskLevel.MODIFIES, TreeScripts.createCatalog("x", "utf8mb4", null).risk());
        assertEquals(ConfirmationPolicy.Level.CONFIRM,
                ConfirmationPolicy.evaluate(TreeScripts.renameTable("b", "a", "c")).level());
        assertEquals("Rinomina la tabella «a» in «c»", TreeScripts.renameTable("b", "a", "c").title());
        assertEquals("Crea il catalogo «x»", TreeScripts.createCatalog("x", null, null).title());
    }
}
