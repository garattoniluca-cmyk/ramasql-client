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

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@link ScriptResult}: che cosa è applicato, che cosa ha fermato lo script, che cosa non è stato tentato. */
@Tag("step3")
class ScriptResultTest {

    private static final SqlScript SCRIPT = SqlScript.of("t", "o", "INSERT INTO a VALUES (1)",
            "INSERT INTO a VALUES (1)", "INSERT INTO a VALUES (2)");

    private static StatementResult result(int i, StatementResult.Status status) {
        return new StatementResult(i, SCRIPT.statements().get(i), status, status == StatementResult.Status.OK ? 1 : 0,
                1, List.of(), List.of(), status == StatementResult.Status.OK ? null
                        : new StatementResult.ServerError(1062, "23000", "Duplicate entry"));
    }

    @Test
    void completato() {
        ScriptResult r = new ScriptResult(SCRIPT, List.of(result(0, StatementResult.Status.OK),
                result(1, StatementResult.Status.OK), result(2, StatementResult.Status.OK)), 3, false);
        assertTrue(r.completed());
        assertEquals(SCRIPT.statements(), r.applied());
        assertTrue(r.failure().isEmpty());
        assertEquals(List.of(), r.notExecuted());
        assertFalse(r.interrupted());
    }

    @Test
    void fermatoAlSecondo() {
        ScriptResult r = new ScriptResult(SCRIPT, List.of(result(0, StatementResult.Status.OK),
                result(1, StatementResult.Status.FAILED)), 2, false);
        assertFalse(r.completed());
        assertEquals(SCRIPT.statements().subList(0, 1), r.applied());
        assertEquals(1, r.failure().orElseThrow().index());
        assertEquals(SCRIPT.statements().subList(2, 3), r.notExecuted());
        assertFalse(r.interrupted());
    }

    @Test
    void interrottoDurante() {
        ScriptResult r = new ScriptResult(SCRIPT, List.of(result(0, StatementResult.Status.INTERRUPTED)), 2, true);
        assertTrue(r.interrupted());
        assertEquals(List.of(), r.applied());
        assertEquals(SCRIPT.statements().subList(1, 3), r.notExecuted());
    }

    @Test
    void interrottoTraUnaIstruzioneELAltra() {
        ScriptResult r = new ScriptResult(SCRIPT, List.of(result(0, StatementResult.Status.OK)), 2, true);
        assertTrue(r.interrupted());
        assertTrue(r.failure().isEmpty());
        assertEquals(SCRIPT.statements().subList(1, 3), r.notExecuted());
        ScriptResult late = new ScriptResult(SCRIPT, List.of(result(0, StatementResult.Status.OK),
                result(1, StatementResult.Status.OK), result(2, StatementResult.Status.OK)), 3, true);
        assertFalse(late.interrupted(), "fermato quando tutto era già finito");
    }
}
