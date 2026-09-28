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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * L'istruzione preparata dell'importazione: com'è scritta (una riga e più righe di valori), quante righe per lotto, e
 * come compare nel registro esportato (commentata: i valori erano nel file, non nel registro).
 */
@Tag("step9")
class BatchInsertLogTest {

    private final BatchInsert insert = new BatchInsert("c", "t", List.of("a", "b"), true, false, "Importazione");

    @Test
    void istruzionePreparataMostrata() {
        assertEquals("INSERT INTO `c`.`t` (`a`, `b`) VALUES (?, ?)", insert.statement().text());
        assertEquals(RiskLevel.MODIFIES, insert.statement().risk());
        assertEquals("INSERT INTO `c`.`t` (`a`, `b`) VALUES (?, ?), (?, ?), (?, ?)", insert.multiRow(3));
    }

    @Test
    void righePerLottoLimitateDaiSegnaposti() {
        assertEquals(1000, insert.rowsPerBatch());
        List<String> many = java.util.stream.IntStream.range(0, 200).mapToObj(i -> "c" + i).toList();
        assertEquals(65_535 / 200, new BatchInsert("c", "t", many, true, false, "").rowsPerBatch());
    }

    @Test
    void nessunaColonnaNonAmmessa() {
        assertThrows(IllegalArgumentException.class, () -> new BatchInsert("c", "t", List.of(), true, false, ""));
    }

    @Test
    void nelRegistroEsportatoLIstruzionePreparataECommentataConLaNota() {
        SqlLog log = new SqlLog();
        log.add("prova", "Importazione", insert.statement().text(), SqlLog.Outcome.OK, 0, "", "", 12, 95,
                "istruzione preparata, eseguita in 1 lotti: 95 righe inserite", true);
        log.add("prova", "Editor SQL", "SELECT 1", SqlLog.Outcome.OK, 0, "", "", 1, 1);
        String script = log.exportScript();
        assertTrue(script.contains("-- INSERT INTO `c`.`t` (`a`, `b`) VALUES (?, ?);"), script);
        assertTrue(script.contains("95 righe inserite"), script);
        assertTrue(script.contains("non si può rieseguire"), script);
        assertTrue(script.contains("\nSELECT 1;"), script);
        assertTrue(log.entries().get(0).parameterized());
        assertFalse(log.entries().get(1).parameterized());
    }
}
