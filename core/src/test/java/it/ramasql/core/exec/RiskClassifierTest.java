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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T3.4 — classificazione di rischio di 30 istruzioni. */
@Tag("step3")
class RiskClassifierTest {

    static Stream<Arguments> istruzioni() {
        return Stream.of(
                // ---- SAFE
                arguments("SELECT * FROM soci", RiskLevel.SAFE),
                arguments("  select nome\nfrom soci\nwhere id = 3", RiskLevel.SAFE),
                arguments("SHOW CREATE TABLE `libri`", RiskLevel.SAFE),
                arguments("DESCRIBE soci", RiskLevel.SAFE),
                arguments("EXPLAIN SELECT * FROM libri WHERE id = 1", RiskLevel.SAFE),
                arguments("USE biblioteca", RiskLevel.SAFE),
                arguments("SET NAMES utf8mb4", RiskLevel.SAFE),
                arguments("-- elenco dei soci\n/* commento */ # altro\nSELECT 1", RiskLevel.SAFE),
                arguments("(SELECT id FROM soci) UNION (SELECT id FROM autori)", RiskLevel.SAFE),
                arguments("WITH recenti AS (SELECT * FROM prestiti WHERE anno = 2026) SELECT * FROM recenti",
                        RiskLevel.SAFE),
                arguments("SELECT 'DROP TABLE soci' AS scherzo", RiskLevel.SAFE),
                // ---- MODIFIES
                arguments("INSERT INTO soci (nome) VALUES ('Anna')", RiskLevel.MODIFIES),
                arguments("REPLACE INTO soci (id, nome) VALUES (1, 'Anna')", RiskLevel.MODIFIES),
                arguments("UPDATE soci SET nome = 'x' WHERE id = 1", RiskLevel.MODIFIES),
                arguments("update soci set nome = 'x'\n  where id = 1", RiskLevel.MODIFIES),
                arguments("DELETE FROM soci WHERE id = 1", RiskLevel.MODIFIES),
                arguments("CREATE TABLE t (id INT)", RiskLevel.MODIFIES),
                arguments("CREATE OR REPLACE VIEW v AS SELECT 1", RiskLevel.MODIFIES),
                arguments("ALTER TABLE soci ADD COLUMN eta INT NULL", RiskLevel.MODIFIES),
                arguments("ALTER TABLE soci DROP INDEX `ix_nome`, DROP FOREIGN KEY `fk_a`, ALTER COLUMN eta DROP DEFAULT",
                        RiskLevel.MODIFIES),
                arguments("RENAME TABLE soci TO iscritti", RiskLevel.MODIFIES),
                // ---- DESTRUCTIVE
                arguments("DROP TABLE soci", RiskLevel.DESTRUCTIVE),
                arguments("/* pulizia */ drop database if exists ramasql_test_x", RiskLevel.DESTRUCTIVE),
                arguments("DROP VIEW v", RiskLevel.DESTRUCTIVE),
                arguments("TRUNCATE TABLE soci", RiskLevel.DESTRUCTIVE),
                arguments("UPDATE soci SET nome = 'x'", RiskLevel.DESTRUCTIVE),
                arguments("DELETE FROM soci", RiskLevel.DESTRUCTIVE),
                arguments("UPDATE soci SET nota = 'where id = 1' -- where id = 1\n/* WHERE 1=1 */",
                        RiskLevel.DESTRUCTIVE),
                arguments("DELETE FROM soci ORDER BY (SELECT MAX(id) FROM prestiti WHERE id_socio = 1) LIMIT 5",
                        RiskLevel.DESTRUCTIVE),
                arguments("ALTER TABLE soci DROP COLUMN `eta`", RiskLevel.DESTRUCTIVE));
    }

    @ParameterizedTest(name = "[{index}] {1}: {0}")
    @MethodSource("istruzioni")
    void classifica(String sql, RiskLevel atteso) {
        assertEquals(atteso, RiskClassifier.classify(sql));
    }

    @Test
    void sonoEsattamenteTrentaIstruzioni() {
        assertEquals(30, istruzioni().count(), "T3.4 chiede 30 istruzioni");
    }

    @Test
    void unTestoConPiuIstruzioniPrendeIlRischioPiuAlto() {
        assertEquals(RiskLevel.DESTRUCTIVE, RiskClassifier.classify("SELECT 1; DROP TABLE soci; SELECT 2"));
        assertEquals(RiskLevel.SAFE, RiskClassifier.classify("   -- solo un commento"));
    }

    @Test
    void casiParticolari() {
        assertEquals(RiskLevel.DESTRUCTIVE, RiskClassifier.classify("ALTER TABLE soci DROP eta"));
        assertEquals(RiskLevel.DESTRUCTIVE, RiskClassifier.classify("ALTER TABLE vendite DROP PARTITION p2019"));
        assertEquals(RiskLevel.DESTRUCTIVE, RiskClassifier.classify("CREATE OR REPLACE TABLE t (id INT)"));
        assertEquals(RiskLevel.DESTRUCTIVE,
                RiskClassifier.classify("WITH vecchi AS (SELECT id FROM soci WHERE anno < 2000) DELETE FROM soci"));
        assertEquals(RiskLevel.MODIFIES, RiskClassifier.classify("UPDATE soci SET n = @where WHERE id = 2"));
        assertEquals(RiskLevel.DESTRUCTIVE, RiskClassifier.classify("UPDATE soci SET n = @where"));
        assertEquals(RiskLevel.MODIFIES, RiskClassifier.classify("/*!40101 SET NAMES utf8 */; INSERT INTO t VALUES (1)"));
        assertEquals(RiskLevel.MODIFIES, RiskClassifier.classify("CALL ricalcola()"));
    }
}
