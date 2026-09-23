/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Quali cataloghi invalidare dopo un'istruzione (cache dei metadati, T3.6 «albero aggiornato da solo»). */
@Tag("step3")
class DdlTargetsTest {

    @Test
    void leIstruzioniNonDdlNonInvalidanoNulla() {
        for (String sql : new String[] {"SELECT * FROM a.b", "INSERT INTO a.b VALUES (1)", "UPDATE t SET x = 1",
                "USE x", "SHOW CREATE TABLE a.b", "  "}) {
            assertFalse(DdlTargets.of(sql).ddl(), sql);
        }
    }

    @Test
    void ddlSuOggettiQualificatiENon() {
        DdlTargets t = DdlTargets.of("RENAME TABLE `cat1`.`a` TO `cat2`.`b`");
        assertTrue(t.ddl());
        assertFalse(t.catalogList());
        assertEquals(Set.of("cat1", "cat2"), t.catalogs());
        assertTrue(t.currentCatalog());
        DdlTargets plain = DdlTargets.of("create table libri (id int)");
        assertEquals(Set.of(), plain.catalogs());
        assertTrue(plain.currentCatalog());
        assertEquals(Set.of("c"), DdlTargets.of("TRUNCATE TABLE c.prestiti").catalogs());
        assertEquals(Set.of("x"), DdlTargets.of("ALTER TABLE `x`.`t` ADD COLUMN c INT COMMENT 'y.z'").catalogs());
    }

    @Test
    void creazioneEdEliminazioneDiCataloghiCambianoLElenco() {
        DdlTargets create = DdlTargets.of("CREATE DATABASE IF NOT EXISTS `ramasql_test_a` CHARACTER SET utf8mb4");
        assertTrue(create.catalogList());
        assertEquals(Set.of("ramasql_test_a"), create.catalogs());
        assertFalse(create.currentCatalog());
        DdlTargets drop = DdlTargets.of("DROP SCHEMA ramasql_test_b");
        assertTrue(drop.catalogList());
        assertEquals(Set.of("ramasql_test_b"), drop.catalogs());
        assertTrue(DdlTargets.of("ALTER DATABASE CHARACTER SET latin1").currentCatalog());
    }

    @Test
    void ddlTargetsVedeLIstruzioneAvvolta() {
        DdlTargets drop = DdlTargets.of("SET STATEMENT max_statement_time=1 FOR DROP TABLE `c1`.`t`");
        assertTrue(drop.ddl());
        assertEquals(Set.of("c1"), drop.catalogs());
        DdlTargets db = DdlTargets.of("SET STATEMENT max_statement_time=5 FOR DROP DATABASE ramasql_test_z");
        assertTrue(db.catalogList());
        assertEquals(Set.of("ramasql_test_z"), db.catalogs());
        assertTrue(DdlTargets.of("SET STATEMENT lock_wait_timeout=2 FOR ALTER TABLE t ADD c INT").currentCatalog());
        assertFalse(DdlTargets.of("SET STATEMENT max_statement_time=1 FOR DELETE FROM t").ddl());
        assertFalse(DdlTargets.of("ANALYZE DELETE FROM t").ddl());
    }
}
