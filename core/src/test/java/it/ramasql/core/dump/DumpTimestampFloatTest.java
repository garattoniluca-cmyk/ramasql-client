/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.dump.DumpLiterals.Kind;

/**
 * I {@code TIMESTAMP} nel dump sono istanti, non ore locali: si leggono come secondi dal 1970 e si scrivono in UTC
 * (il file imposta {@code TIME_ZONE = '+00:00'}); i {@code FLOAT} si leggono come DOUBLE per non perdere cifre.
 */
@Tag("step10")
class DumpTimestampFloatTest {

    @Test
    void timestampLettoComeSecondiEScrittoInUtc() {
        assertEquals("UNIX_TIMESTAMP(`creato`)", DumpLiterals.selectExpression("`creato`", "TIMESTAMP"));
        assertEquals("UNIX_TIMESTAMP(`t`)", DumpLiterals.selectExpression("`t`", "timestamp(3)"));
        assertEquals(Kind.TIMESTAMP_UTC, DumpLiterals.kindOfColumn("TIMESTAMP"));
        assertEquals(Kind.TEXT, DumpLiterals.kindOfColumn("DATETIME"), "DATETIME non ha fuso: resta com'è");
        assertEquals("`d`", DumpLiterals.selectExpression("`d`", "DATETIME"));
        // 1774746000 = 2026-03-29 01:00:00 UTC (le 3:00 a Roma, subito dopo il cambio all'ora legale)
        assertEquals("'2026-03-29 01:00:00.125'", DumpLiterals.literal("1774746000.125", Kind.TIMESTAMP_UTC));
        assertEquals("'2026-03-29 01:00:00'", DumpLiterals.literal("1774746000", Kind.TIMESTAMP_UTC));
        // 1792891800 = 2026-10-25 01:30:00 UTC: a Roma è le 2:30 dell'ora ripetuta, in UTC nessuna ambiguità
        assertEquals("'2026-10-25 01:30:00'", DumpLiterals.literal("1792891800", Kind.TIMESTAMP_UTC));
        assertEquals("'1970-01-01 00:00:01.000000'", DumpLiterals.literal("1.000000", Kind.TIMESTAMP_UTC));
        assertEquals("'0000-00-00 00:00:00'", DumpLiterals.literal("0", Kind.TIMESTAMP_UTC), "la data «zero»");
        assertEquals("'0000-00-00 00:00:00'", DumpLiterals.literal("0.000", Kind.TIMESTAMP_UTC));
        assertEquals("NULL", DumpLiterals.literal(null, Kind.TIMESTAMP_UTC));
    }

    @Test
    void floatLettoComeDoubleEsatto() {
        assertEquals("CAST(`f` AS DOUBLE)", DumpLiterals.selectExpression("`f`", "FLOAT"));
        assertEquals("CAST(`f` AS DOUBLE)", DumpLiterals.selectExpression("`f`", "float unsigned"));
        assertEquals("`d`", DumpLiterals.selectExpression("`d`", "DOUBLE"));
        assertEquals("`n`", DumpLiterals.selectExpression("`n`", "DECIMAL(6,2)"));
        assertEquals(Kind.NUMBER, DumpLiterals.kindOfColumn("FLOAT"));
        assertEquals("1234567", DumpLiterals.literal("1234567", Kind.NUMBER));
        assertEquals("0.10000000149011612", DumpLiterals.literal("0.10000000149011612", Kind.NUMBER));
    }
}
