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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import it.ramasql.core.CoreMessages;

/** Politica di conferma (lato core di T3.7): DROP/TRUNCATE/DELETE senza WHERE → conferma rafforzata con il nome. */
@Tag("step3")
class ConfirmationPolicyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "DROP TABLE `cat`.`libri`                 | libri",
        "drop table if exists soci                | soci",
        "DROP TEMPORARY TABLE tmp                 | tmp",
        "DROP VIEW `c`.`v_prestiti`               | v_prestiti",
        "DROP DATABASE `ramasql_test_x`           | ramasql_test_x",
        "TRUNCATE TABLE `c`.`prestiti`            | prestiti",
        "TRUNCATE autori                          | autori",
        "DELETE FROM soci                         | soci",
        "DELETE LOW_PRIORITY FROM c.soci          | soci",
        "UPDATE soci SET nome = 'x'               | soci",
        "ALTER TABLE `libri` DROP COLUMN `anno`   | libri",
        "CREATE OR REPLACE TABLE t (id INT)       | t",
        "/* pulizia */ DROP INDEX ix ON libri     | ix"})
    void confermaRafforzataConIlNomeDellOggetto(String sql, String name) {
        ConfirmationPolicy.Confirmation c = ConfirmationPolicy.evaluate(SqlScript.of("t", "o", sql));
        assertEquals(ConfirmationPolicy.Level.STRONG, c.level());
        assertEquals(name, c.typeToConfirm());
        assertEquals(CoreMessages.get("confirm.strong", name), c.message());
        assertTrue(c.accepts(" " + name + " "));
        assertFalse(c.accepts(name + "x"));
        assertFalse(c.accepts(null));
    }

    @Test
    void leggereNonChiedeConfermaModificareChiedeEsegui() {
        ConfirmationPolicy.Confirmation read = ConfirmationPolicy.evaluate(SqlScript.of("t", "o", "SELECT * FROM x"));
        assertEquals(ConfirmationPolicy.Level.NONE, read.level());
        assertNull(read.typeToConfirm());
        assertTrue(read.accepts(null));
        ConfirmationPolicy.Confirmation write = ConfirmationPolicy.evaluate(
                SqlScript.of("t", "o", "INSERT INTO x VALUES (1)", "UPDATE x SET a = 1 WHERE id = 2"));
        assertEquals(ConfirmationPolicy.Level.CONFIRM, write.level());
        assertTrue(write.accepts(""));
    }

    @Test
    void piuOggettiDistruttiDiversiChiedonoLaParolaDiConferma() {
        ConfirmationPolicy.Confirmation c = ConfirmationPolicy.evaluate(
                SqlScript.of("t", "o", "DROP TABLE a", "INSERT INTO b VALUES (1)", "TRUNCATE TABLE c"));
        assertEquals(ConfirmationPolicy.Level.STRONG, c.level());
        assertEquals(CoreMessages.get("confirm.word"), c.typeToConfirm());
        ConfirmationPolicy.Confirmation same = ConfirmationPolicy.evaluate(
                SqlScript.of("t", "o", "DELETE FROM soci", "DROP TABLE soci"));
        assertEquals("soci", same.typeToConfirm());
    }
}
