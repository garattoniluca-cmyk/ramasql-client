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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import it.ramasql.core.metadata.ColumnDef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Letterali e identificatori: la base di tutto l'SQL generato (Step 4 e 5). */
@Tag("step4")
class SqlLiteralsTest {

    static Stream<Arguments> letterali() {
        return Stream.of(
                arguments("stringa semplice", "Anna", "'Anna'"),
                arguments("stringa vuota", "", "''"),
                arguments("NULL", null, "NULL"),
                arguments("apostrofo raddoppiato", "l'ora d'aria", "'l''ora d''aria'"),
                arguments("backslash raddoppiato", "c:\\dati\\", "'c:\\\\dati\\\\'"),
                arguments("a-capo e tabulazione", "uno\r\ndue\ttre", "'uno\\r\\ndue\ttre'"),
                arguments("emoji e accenti invariati", "però 😀 ñ", "'però 😀 ñ'"),
                arguments("tentativo di iniezione", "x'; DROP TABLE soci; --", "'x''; DROP TABLE soci; --'"),
                arguments("intero", 42, "42"),
                arguments("long negativo", -9_000_000_000L, "-9000000000"),
                arguments("DECIMAL con la scala conservata", new BigDecimal("12.50"), "12.50"),
                arguments("DECIMAL senza notazione esponenziale", new BigDecimal("1E+3"), "1000"),
                arguments("booleano", true, "1"),
                arguments("DATE", LocalDate.of(2026, 9, 21), "'2026-09-21'"),
                arguments("DATETIME", LocalDateTime.of(2026, 9, 21, 8, 5, 0), "'2026-09-21 08:05:00'"),
                arguments("DATETIME con microsecondi", LocalDateTime.of(2026, 9, 21, 8, 5, 0, 120_000),
                        "'2026-09-21 08:05:00.000120'"),
                arguments("TIME", LocalTime.of(23, 59, 59), "'23:59:59'"),
                arguments("BLOB esadecimale", new byte[] {(byte) 0xCA, (byte) 0xFE, 0x00, 0x7F}, "X'CAFE007F'"),
                arguments("BLOB vuoto", new byte[0], "X''"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("letterali")
    void letterale(String nome, Object valore, String atteso) {
        assertEquals(atteso, SqlLiterals.of(valore));
    }

    @Test
    void dalTestoDellaCellaSecondoIlTipoDiColonna() {
        assertEquals("12.50", SqlLiterals.forColumn("12.50", ColumnDef.of("p", "DECIMAL", "8,2")));
        assertEquals("-3", SqlLiterals.forColumn(" -3 ", ColumnDef.of("n", "INT")));
        assertEquals("'abc'", SqlLiterals.forColumn("abc", ColumnDef.of("n", "INT")), "non numerico: lo rifiuterà il server");
        assertEquals("'007'", SqlLiterals.forColumn("007", ColumnDef.of("cap", "CHAR", "5")), "testo resta testo");
        assertEquals("'2026-09-21'", SqlLiterals.forColumn("2026-09-21", ColumnDef.of("d", "DATE")));
        assertEquals("2026", SqlLiterals.forColumn("2026", ColumnDef.of("y", "YEAR")));
        assertEquals("0", SqlLiterals.forColumn("FALSE", ColumnDef.of("b", "BOOLEAN")));
        assertEquals("NULL", SqlLiterals.forColumn(null, ColumnDef.of("n", "INT")));
        assertEquals("'x'", SqlLiterals.forColumn("x", null));
    }

    @Test
    void identificatoriSempreTraBacktick() {
        assertEquals("`soci`", SqlIdentifiers.quote("soci"));
        assertEquals("`ordine dettagli`", SqlIdentifiers.quote("ordine dettagli"));
        assertEquals("`order`", SqlIdentifiers.quote("order"));
        assertEquals("`strano``nome`", SqlIdentifiers.quote("strano`nome"));
        assertEquals("`città 😀`", SqlIdentifiers.quote("città 😀"));
        assertEquals("`bib`.`soci`", SqlIdentifiers.qualified("bib", "soci"));
        assertEquals("`soci`", SqlIdentifiers.qualified(null, "soci"));
        assertEquals("(`a`, `b c`)", SqlIdentifiers.columnList(List.of("a", "b c")));
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifiers.quote(""));
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifiers.quote(null));
    }
}
