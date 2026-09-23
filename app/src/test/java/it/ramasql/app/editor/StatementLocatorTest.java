/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.exec.RiskLevel;

/** Quale istruzione esegue l'editor: al cursore, nella selezione, tutte (con DELIMITER). */
@Tag("step4")
@Tag("ui")
class StatementLocatorTest {

    static final String PROCEDURE_SCRIPT = String.join("\n",
            "SELECT * FROM soci;",
            "",
            "DELIMITER //",
            "CREATE PROCEDURE conta_prestiti(IN socio INT)",
            "BEGIN",
            "  SELECT COUNT(*) FROM prestiti WHERE id_socio = socio;",
            "  SELECT 'fatto; davvero';",
            "END //",
            "DELIMITER ;",
            "",
            "CALL conta_prestiti(3);");

    static final String PROCEDURE_TEXT = String.join("\n",
            "CREATE PROCEDURE conta_prestiti(IN socio INT)",
            "BEGIN",
            "  SELECT COUNT(*) FROM prestiti WHERE id_socio = socio;",
            "  SELECT 'fatto; davvero';",
            "END");

    @Test
    void cursoreDentroUnaProceduraConDelimiterPrendeTuttaLaProcedura() {
        int caret = PROCEDURE_SCRIPT.indexOf("COUNT(*)");
        PositionedStatement st = StatementLocator.atCaret(PROCEDURE_SCRIPT, caret, "Editor SQL");
        assertEquals(PROCEDURE_TEXT, st.text());
        assertEquals(4, st.line());
        assertEquals(PROCEDURE_SCRIPT.indexOf("CREATE PROCEDURE"), st.startOffset());
        assertEquals("Editor SQL", st.statement().origin());
    }

    @Test
    void cursoreSubitoDopoIlPuntoEVirgolaPrendeLIstruzionePrecedente() {
        String sql = "SELECT 1;   \nSELECT 2;";
        assertEquals("SELECT 1", StatementLocator.atCaret(sql, sql.indexOf(';') + 1, "").text());
        assertEquals("SELECT 1", StatementLocator.atCaret(sql, sql.indexOf(';') + 3, "").text());
    }

    @Test
    void cursoreSuUnaRigaVuotaTraDueIstruzioniPrendeLaSuccessiva() {
        String sql = "SELECT 1;\n\nSELECT 2;";
        assertEquals("SELECT 2", StatementLocator.atCaret(sql, sql.indexOf("\n\n") + 1, "").text());
    }

    @Test
    void cursoreInFondoAlloScriptPrendeLUltima() {
        String sql = "SELECT 1;\nSELECT 2;\n\n\n";
        assertEquals("SELECT 2", StatementLocator.atCaret(sql, sql.length(), "").text());
    }

    @Test
    void scriptVuotoODiSoliCommentiNonHaIstruzioni() {
        assertNull(StatementLocator.atCaret("", 0, ""));
        assertNull(StatementLocator.atCaret("-- solo un commento\n/* e un altro */", 5, ""));
        assertTrue(StatementLocator.all("  \n# niente\n", "").isEmpty());
    }

    @Test
    void selezioneDentroIlCorpoDiUnaProceduraNonVieneSpezzataAiPuntiEVirgola() {
        int from = PROCEDURE_SCRIPT.indexOf("BEGIN");
        int to = PROCEDURE_SCRIPT.indexOf("END //") + 3;
        List<PositionedStatement> sel = StatementLocator.inSelection(PROCEDURE_SCRIPT, from, to, "");
        assertEquals(1, sel.size());
        assertEquals(PROCEDURE_SCRIPT.substring(from, to), sel.getFirst().text());
        assertEquals(5, sel.getFirst().line());
    }

    @Test
    void selezioneSuDueIstruzioniLeSeparaConPosizioniDelDocumento() {
        String sql = "SELECT 0;\nUPDATE soci SET nome = 'a;b' WHERE id = 1;\nSELECT 2;\nSELECT 3;";
        int from = sql.indexOf("UPDATE");
        int to = sql.indexOf("SELECT 3");
        List<PositionedStatement> sel = StatementLocator.inSelection(sql, from, to, "");
        assertEquals(List.of("UPDATE soci SET nome = 'a;b' WHERE id = 1", "SELECT 2"),
                sel.stream().map(PositionedStatement::text).toList());
        assertEquals(from, sel.get(0).startOffset());
        assertEquals(2, sel.get(0).line());
        assertEquals(sql.indexOf("SELECT 2"), sel.get(1).startOffset());
        assertEquals(3, sel.get(1).line());
    }

    @Test
    void tutteLeIstruzioniConRischioCalcolato() {
        List<PositionedStatement> all = StatementLocator.all(PROCEDURE_SCRIPT, "Editor SQL");
        assertEquals(List.of("SELECT * FROM soci", PROCEDURE_TEXT, "CALL conta_prestiti(3)"),
                all.stream().map(PositionedStatement::text).toList());
        assertEquals(List.of(1, 4, 11), all.stream().map(PositionedStatement::line).toList());
        assertEquals(RiskLevel.SAFE, all.get(0).risk());
        assertEquals(RiskLevel.MODIFIES, all.get(1).risk());
    }
}
