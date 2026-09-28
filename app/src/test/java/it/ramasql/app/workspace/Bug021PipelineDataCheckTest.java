/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.app.tableeditor.DataCheck.DataCheckResult;
import it.ramasql.core.exec.ResultTable;

/**
 * {@code BUG-021} — «Verifica dati» mostra al massimo {@value PipelineDataCheck#MAX_ROWS} righe e adesso dice quando
 * ne ha tolte: sia se le toglie lei, sia se l'esecutore si era già fermato al suo limite di righe.
 */
@Tag("step12")
class Bug021PipelineDataCheckTest {

    private static ResultTable tabella(int righe, boolean troncataDallEsecutore) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < righe; i++) {
            rows.add(List.of(i + 1, "valore " + i));
        }
        return new ResultTable(List.of(new ResultTable.Column("id", "INT", Types.INTEGER),
                new ResultTable.Column("nome", "VARCHAR", Types.VARCHAR)), rows, troncataDallEsecutore);
    }

    @Test
    void oltreIlLimiteSiTagliaESiDice() {
        DataCheckResult r = PipelineDataCheck.fromTable(tabella(PipelineDataCheck.MAX_ROWS + 50, false));
        assertEquals(PipelineDataCheck.MAX_ROWS, r.rows().size(), "si mostrano solo le prime");
        assertTrue(r.truncated(), "il troncamento è dichiarato");
        assertEquals(List.of("id", "nome"), r.columns());
        assertEquals(List.of("1", "valore 0"), r.rows().get(0));
    }

    @Test
    void troncataDallEsecutoreSiDiceAnche() {
        DataCheckResult r = PipelineDataCheck.fromTable(tabella(100, true));
        assertEquals(100, r.rows().size());
        assertTrue(r.truncated(), "l'esecutore aveva altre righe: il risultato non è completo");
    }

    @Test
    void entroIlLimiteNonETroncata() {
        DataCheckResult r = PipelineDataCheck.fromTable(tabella(PipelineDataCheck.MAX_ROWS, false));
        assertEquals(PipelineDataCheck.MAX_ROWS, r.rows().size());
        assertFalse(r.truncated());
        assertFalse(new DataCheckResult(List.of(), List.of(), null).truncated(), "il costruttore breve: completo");
        assertFalse(DataCheckResult.failed("errore").truncated());
    }
}
