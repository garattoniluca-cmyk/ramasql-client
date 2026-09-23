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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import it.ramasql.core.CoreMessages;

/**
 * La conferma rafforzata non si aggira con un involucro: MariaDB {@code SET STATEMENT … FOR <istr>} e
 * {@code ANALYZE <DML>}, MySQL {@code EXPLAIN ANALYZE <istr>} eseguono l'istruzione interna, quindi valgono quanto lei
 * per il rischio ({@link RiskClassifier}), per l'invalidazione dei metadati ({@link DdlTargets}), per il nome da
 * riscrivere ({@link ConfirmationPolicy}) e per la frase della conferma ({@link ConfirmationPolicy#actionOf}).
 * I casi di rischio sono in {@link RiskClassifierTest#involucri} (i 30 di T3.4 restano invariati), quelli dei
 * metadati in {@link DdlTargetsTest#ddlTargetsVedeLIstruzioneAvvolta}.
 */
@Tag("step3")
class WrappedStatementsTest {

    @Test
    void innermostTogliTuttiGliInvolucri() {
        assertEquals("DELETE FROM t", SqlLexer.innermost("SET STATEMENT max_statement_time=1 FOR DELETE FROM t"));
        assertEquals("DROP TABLE t", SqlLexer.innermost("SET STATEMENT a=1 FOR SET STATEMENT b=2 FOR DROP TABLE t"));
        assertEquals("DELETE FROM t", SqlLexer.innermost("ANALYZE FORMAT=JSON DELETE FROM t"));
        assertEquals("UPDATE t SET a = 1", SqlLexer.innermost("EXPLAIN ANALYZE UPDATE t SET a = 1"));
        assertEquals("SELECT 1", SqlLexer.innermost("SELECT 1"));
        assertEquals("ANALYZE TABLE t", SqlLexer.innermost("ANALYZE TABLE t"));
        assertEquals("SET STATEMENT a='for' FOR", SqlLexer.innermost("SET STATEMENT a='for' FOR"),
                "FOR senza istruzione dopo: nessun involucro riconosciuto");
    }

    @Test
    void laConfermaRafforzataChiedeIlNomeDellOggettoAvvolto() {
        ConfirmationPolicy.Confirmation c = ConfirmationPolicy.evaluate(
                SqlScript.of("t", "o", "SET STATEMENT max_statement_time=1 FOR DELETE FROM `c`.`prestiti`"));
        assertEquals(ConfirmationPolicy.Level.STRONG, c.level());
        assertEquals("prestiti", c.typeToConfirm());
        assertEquals("soci", ConfirmationPolicy.evaluate(SqlScript.of("t", "o", "ANALYZE DELETE FROM soci"))
                .typeToConfirm());
        assertEquals("soci", ConfirmationPolicy.evaluate(SqlScript.of("t", "o", "EXPLAIN ANALYZE UPDATE soci SET a=1"))
                .typeToConfirm());
    }

    @Test
    void piuOggettiNellaStessaIstruzioneChiedonoLaParolaDiConferma() {
        // DROP TABLE a, b elimina due tabelle: riscrivere solo «a» non basta a capire che cosa si perde
        assertEquals(CoreMessages.get("confirm.word"),
                ConfirmationPolicy.evaluate(SqlScript.of("t", "o", "DROP TABLE a, b")).typeToConfirm());
        assertEquals(CoreMessages.get("confirm.word"),
                ConfirmationPolicy.evaluate(SqlScript.of("t", "o", "DROP TABLE `c`.`a`, `c`.`b`")).typeToConfirm());
        assertEquals(CoreMessages.get("confirm.word"),
                ConfirmationPolicy.evaluate(SqlScript.of("t", "o", "DELETE a, b FROM a JOIN b ON a.id = b.id"))
                        .typeToConfirm());
        assertEquals("a", ConfirmationPolicy.evaluate(SqlScript.of("t", "o", "DROP TABLE a")).typeToConfirm());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "DROP TABLE `c`.`libri`                                        | DROP_TABLE",
        "-- elimina\\nDROP TABLE libri                                 | DROP_TABLE",
        "/* pulizia */ drop temporary table if exists tmp              | DROP_TABLE",
        "-- a\\n# commento\\n  DROP   TEMPORARY TABLE tmp              | DROP_TABLE",
        "DROP VIEW v                                                   | DROP_VIEW",
        "/* x */ DROP SCHEMA ramasql_test_x                            | DROP_CATALOG",
        "DROP DATABASE ramasql_test_x                                  | DROP_CATALOG",
        "-- svuota\\nTRUNCATE TABLE prestiti                         | TRUNCATE",
        "SET STATEMENT max_statement_time=1 FOR TRUNCATE TABLE t       | TRUNCATE",
        "SET STATEMENT max_statement_time=1 FOR DROP TABLE t           | DROP_TABLE",
        "DELETE FROM soci                                              | OTHER",
        "DROP INDEX ix ON t                                            | OTHER"})
    void azioneDellaFraseDiConferma(String sql, ConfirmationPolicy.Action expected) {
        String text = sql.replace("\\n", "\n");
        assertEquals(expected, ConfirmationPolicy.actionOf(text));
        assertEquals(expected, ConfirmationPolicy.actionOf(SqlScript.of("t", "o", "SELECT 1", text)));
    }
}
