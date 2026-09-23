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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@link SqlScript}: rischio complessivo, testo dell'anteprima rieseguibile, origine. */
@Tag("step3")
class SqlScriptTest {

    @Test
    void rischioComplessivoEIlMassimo() {
        assertEquals(RiskLevel.SAFE, SqlScript.of("t", "o").risk());
        assertEquals(RiskLevel.SAFE, SqlScript.of("t", "o", "SELECT 1", "SHOW TABLES").risk());
        assertEquals(RiskLevel.MODIFIES, SqlScript.of("t", "o", "SELECT 1", "INSERT INTO a VALUES (1)").risk());
        assertEquals(RiskLevel.DESTRUCTIVE,
                SqlScript.of("t", "o", "INSERT INTO a VALUES (1)", "DROP TABLE a", "SELECT 1").risk());
    }

    @Test
    void dalTestoDellUtenteConDelimiter() {
        SqlScript s = SqlScript.fromText("Editor", "Editor SQL", """
                SELECT 1;
                -- commento
                DELIMITER //
                CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END //
                DELIMITER ;
                UPDATE t SET x = 1""");
        assertEquals(3, s.size());
        assertEquals("CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END", s.statements().get(1).text());
        assertEquals(RiskLevel.DESTRUCTIVE, s.risk());
        assertEquals("Editor SQL", s.originOf(s.statements().get(0)));
    }

    @Test
    void testoDellAnteprimaERieseguibile() {
        SqlScript s = SqlScript.of("t", "o", "CREATE TABLE a (id INT)",
                "CREATE PROCEDURE p() BEGIN SELECT 1; END", "SELECT 1 -- nota");
        String text = s.text();
        assertEquals("""
                CREATE TABLE a (id INT);

                DELIMITER $$
                CREATE PROCEDURE p() BEGIN SELECT 1; END
                $$
                DELIMITER ;

                SELECT 1 -- nota
                ;""", text);
        // il testo si risepara nelle stesse istruzioni (il commento in coda, come sempre, non fa parte dell'istruzione)
        List<String> again = StatementSplitter.split(text).stream().map(StatementSplitter.SplitStatement::text)
                .toList();
        assertEquals(List.of("CREATE TABLE a (id INT)", "CREATE PROCEDURE p() BEGIN SELECT 1; END", "SELECT 1"),
                again);
    }

    @Test
    void originePropriaDellIstruzioneVinceSuQuellaDelloScript() {
        SqlStatement mine = SqlStatement.of("SELECT 1", "Griglia");
        SqlScript s = new SqlScript("t", "Navigatore", List.of(mine, SqlStatement.of("SELECT 2", "")));
        assertEquals("Griglia", s.originOf(mine));
        assertEquals("Navigatore", s.originOf(s.statements().get(1)));
        assertTrue(new SqlScript(null, null, List.of()).isEmpty());
    }
}
