/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Validazione per tipo dei valori del data-entry (Step 4). */
@Tag("step4")
class ValueValidatorTest {

    static final ColumnDef INT = ColumnDef.of("n", "INT");
    static final ColumnDef INT_UNSIGNED = ColumnDef.of("n", "INT").withUnsigned(true);
    static final ColumnDef TINYINT = ColumnDef.of("n", "TINYINT");
    static final ColumnDef TINYINT_UNSIGNED = ColumnDef.of("n", "TINYINT", "3").withUnsigned(true);
    static final ColumnDef SMALLINT = ColumnDef.of("n", "SMALLINT");
    static final ColumnDef BIGINT = ColumnDef.of("n", "BIGINT");
    static final ColumnDef BIGINT_UNSIGNED = ColumnDef.of("n", "BIGINT").withUnsigned(true);
    static final ColumnDef DECIMAL = ColumnDef.of("p", "DECIMAL", "6,2");
    static final ColumnDef DECIMAL_UNSIGNED = ColumnDef.of("p", "DECIMAL", "6,2").withUnsigned(true);
    static final ColumnDef DOUBLE = ColumnDef.of("d", "DOUBLE");
    static final ColumnDef DATE = ColumnDef.of("d", "DATE");
    static final ColumnDef DATETIME = ColumnDef.of("d", "DATETIME");
    static final ColumnDef TIME = ColumnDef.of("t", "TIME");
    static final ColumnDef YEAR = ColumnDef.of("y", "YEAR");
    static final ColumnDef VARCHAR5 = ColumnDef.of("v", "VARCHAR", "5");
    static final ColumnDef ENUM = ColumnDef.of("e", "ENUM", "'nuovo','usato','d''epoca'");
    static final ColumnDef SET = ColumnDef.of("s", "SET", "'a','b','c'");
    static final ColumnDef BOOLEAN = ColumnDef.of("b", "BOOLEAN");
    static final ColumnDef TINYINT1 = ColumnDef.of("b", "TINYINT", "1");
    static final ColumnDef NOT_NULL = ColumnDef.of("x", "VARCHAR", "10").notNull();

    static Stream<Arguments> validi() {
        return Stream.of(
                arguments(INT, "0"), arguments(INT, "-2147483648"), arguments(INT, "2147483647"), arguments(INT, "+5"),
                arguments(INT_UNSIGNED, "4294967295"),
                arguments(TINYINT, "-128"), arguments(TINYINT_UNSIGNED, "255"),
                arguments(SMALLINT, "32767"),
                arguments(BIGINT, "-9223372036854775808"), arguments(BIGINT_UNSIGNED, "18446744073709551615"),
                arguments(DECIMAL, "9999.99"), arguments(DECIMAL, "-0.5"), arguments(DECIMAL, ".5"),
                arguments(DECIMAL, "12"), arguments(DECIMAL, "12.500"), arguments(DECIMAL, "0012.5"),
                arguments(DOUBLE, "1.5e-3"),
                arguments(DATE, "2024-02-29"), arguments(DATE, "1000-01-01"),
                arguments(DATETIME, "2026-09-21 08:30:00"), arguments(DATETIME, "2026-09-21 08:30"),
                arguments(DATETIME, "2026-09-21"), arguments(DATETIME, "2026-09-21 23:59:59.123456"),
                arguments(TIME, "08:30:00"), arguments(TIME, "-838:59:59"), arguments(TIME, "8:05"),
                arguments(YEAR, "1901"), arguments(YEAR, "2155"), arguments(YEAR, "0000"),
                arguments(VARCHAR5, "cinqu"), arguments(VARCHAR5, ""), arguments(VARCHAR5, "😀😀😀😀😀"),
                arguments(ENUM, "usato"), arguments(ENUM, "d'epoca"), arguments(ENUM, "NUOVO"),
                arguments(SET, "a,c"), arguments(SET, ""),
                arguments(BOOLEAN, "0"), arguments(BOOLEAN, "1"), arguments(BOOLEAN, "TRUE"), arguments(TINYINT1, "false"),
                arguments(INT, null), arguments(ColumnDef.of("j", "JSON"), "{qualsiasi cosa"));
    }

    static Stream<Arguments> nonValidi() {
        return Stream.of(
                arguments(INT, "abc", "numero intero"), arguments(INT, "", "numero intero"),
                arguments(INT, "1.5", "numero intero"), arguments(INT, "2147483648", "Fuori intervallo"),
                arguments(INT, "-2147483649", "Fuori intervallo"),
                arguments(INT_UNSIGNED, "-1", "Fuori intervallo"), arguments(INT_UNSIGNED, "4294967296", "Fuori intervallo"),
                arguments(TINYINT, "128", "Fuori intervallo"), arguments(TINYINT_UNSIGNED, "256", "Fuori intervallo"),
                arguments(SMALLINT, "40000", "Fuori intervallo"),
                arguments(BIGINT, "9223372036854775808", "Fuori intervallo"),
                arguments(BIGINT_UNSIGNED, "18446744073709551616", "Fuori intervallo"),
                arguments(DECIMAL, "10000", "troppo grande"), arguments(DECIMAL, "1.234", "cifre decimali"),
                arguments(DECIMAL, "12,5", "serve un numero"), arguments(DECIMAL, "-", "serve un numero"),
                arguments(DECIMAL_UNSIGNED, "-1", "UNSIGNED"),
                arguments(DOUBLE, "uno", "serve un numero"),
                arguments(DATE, "2023-02-29", "Data non valida"), arguments(DATE, "21/09/2026", "Data non valida"),
                arguments(DATE, "2026-13-01", "Data non valida"), arguments(DATE, "0999-01-01", "Data non valida"),
                arguments(DATETIME, "2026-09-21 25:00:00", "Data e ora"), arguments(DATETIME, "ieri", "Data e ora"),
                arguments(DATETIME, "2026-02-30 10:00:00", "Data e ora"),
                arguments(TIME, "12:60:00", "Ora non valida"), arguments(TIME, "839:00:00", "Ora non valida"),
                arguments(YEAR, "1900", "Anno non valido"), arguments(YEAR, "26", "Anno non valido"),
                arguments(VARCHAR5, "sei ca", "al massimo 5 caratteri, qui sono 6"),
                arguments(ENUM, "rotto", "nuovo, usato, d'epoca"), arguments(ENUM, "", "Valore non ammesso"),
                arguments(SET, "a,z", "Valore non ammesso"),
                arguments(BOOLEAN, "2", "0 o 1"), arguments(TINYINT1, "sì", "0 o 1"),
                arguments(NOT_NULL, null, "«x» non ammette NULL"));
    }

    @ParameterizedTest(name = "[{index}] {0} accetta «{1}»")
    @MethodSource("validi")
    void accetta(ColumnDef colonna, String testo) {
        ValueValidator.Result r = ValueValidator.validate(testo, colonna);
        assertTrue(r.valid(), r.message());
        assertEquals("", r.message());
    }

    @ParameterizedTest(name = "[{index}] {0} rifiuta «{1}»")
    @MethodSource("nonValidi")
    void rifiuta(ColumnDef colonna, String testo, String parteDelMessaggio) {
        ValueValidator.Result r = ValueValidator.validate(testo, colonna);
        assertFalse(r.valid());
        assertTrue(r.message().contains(parteDelMessaggio), "messaggio: " + r.message());
    }

    @Test
    void nullSuColonnaNotNullPassaSeCiPensaIlServer() {
        assertTrue(ValueValidator.validate(null, NOT_NULL.withDefault(ColumnDefault.literal(""))).valid());
        assertTrue(ValueValidator.validate(null, ColumnDef.of("id", "INT").notNull().withAutoIncrement(true)).valid());
    }

    @Test
    void iLimitiInByteDeiTesti() {
        assertTrue(ValueValidator.validate("a".repeat(255), ColumnDef.of("t", "TINYTEXT")).valid());
        assertFalse(ValueValidator.validate("è".repeat(128), ColumnDef.of("t", "TINYTEXT")).valid(), "256 byte in UTF-8");
        assertFalse(ValueValidator.validate("a".repeat(65_536), ColumnDef.of("t", "TEXT")).valid());
    }
}
