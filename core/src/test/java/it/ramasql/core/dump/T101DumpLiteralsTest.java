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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.dump.DumpLiterals.Kind;

/**
 * T10.1 — scrittura dei valori nel dump: NULL, apostrofi, backslash, a-capo, emoji, BLOB (esadecimale), DECIMAL,
 * date zero, BIT, stringa vuota → letterali SQL corretti.
 */
@Tag("step10")
class T101DumpLiteralsTest {

    @Test
    void nullSempreNull() {
        assertEquals("NULL", DumpLiterals.literal(null, Kind.TEXT));
        assertEquals("NULL", DumpLiterals.literal(null, Kind.NUMBER));
        assertEquals("NULL", DumpLiterals.literal(null, Kind.BINARY));
    }

    @Test
    void apostrofiRaddoppiati() {
        assertEquals("'l''ora d''aria'", DumpLiterals.literal("l'ora d'aria", Kind.TEXT));
    }

    @Test
    void backslashRaddoppiato() {
        assertEquals("'C:\\\\dati\\\\file.csv'", DumpLiterals.literal("C:\\dati\\file.csv", Kind.TEXT));
    }

    @Test
    void aCapoRitornoNulECtrlZComeSequenze() {
        assertEquals("'riga1\\nriga2\\r\\n\\0fine\\Z'", DumpLiterals.literal("riga1\nriga2\r\n\0fine\u001A", Kind.TEXT));
    }

    @Test
    void emojiEAccentiInvariati() {
        assertEquals("'Forlì 😀 perché'", DumpLiterals.literal("Forlì 😀 perché", Kind.TEXT));
    }

    @Test
    void stringaVuotaNonENull() {
        assertEquals("''", DumpLiterals.literal("", Kind.TEXT));
    }

    @Test
    void blobInEsadecimale() {
        assertEquals("X'00FF7F80'", DumpLiterals.literal(new byte[] {0, (byte) 0xFF, 0x7F, (byte) 0x80}, Kind.BINARY));
        assertEquals("X''", DumpLiterals.literal(new byte[0], Kind.BINARY), "BLOB vuoto");
        assertEquals("X'48656C6C6F'", DumpLiterals.literal("Hello".getBytes(StandardCharsets.UTF_8), Kind.BINARY));
    }

    @Test
    void decimalComeLoScriveIlServer() {
        assertEquals("12.50", DumpLiterals.literal("12.50", Kind.NUMBER), "la scala resta");
        assertEquals("-0.001", DumpLiterals.literal("-0.001", Kind.NUMBER));
        assertEquals("12345678901234567890.1234567890", DumpLiterals.literal("12345678901234567890.1234567890",
                Kind.NUMBER));
        assertEquals("1.5e-7", DumpLiterals.literal("1.5e-7", Kind.NUMBER), "FLOAT/DOUBLE in notazione esponenziale");
    }

    @Test
    void dateZeroTraApici() {
        assertEquals("'0000-00-00'", DumpLiterals.literal("0000-00-00", Kind.TEXT));
        assertEquals("'0000-00-00 00:00:00'", DumpLiterals.literal("0000-00-00 00:00:00", Kind.TEXT));
        assertEquals("'2026-09-28 08:30:00.123456'", DumpLiterals.literal("2026-09-28 08:30:00.123456", Kind.TEXT));
    }

    @Test
    void bitComeByte() {
        assertEquals("X'05'", DumpLiterals.literal(new byte[] {5}, Kind.BINARY));
        assertEquals("X'0101'", DumpLiterals.literal(new byte[] {1, 1}, Kind.BINARY), "BIT(9) su due byte");
    }

    @Test
    void numeroNonNumericoFiniscetraApici() {
        // un tipo numerico che il server riportasse in una forma strana non diventa SQL rotto
        assertEquals("'NaN'", DumpLiterals.literal("NaN", Kind.NUMBER));
    }

    @Test
    void tipoDaNomeDelServer() {
        assertEquals(Kind.NUMBER, DumpLiterals.kindOf("INT UNSIGNED"));
        assertEquals(Kind.NUMBER, DumpLiterals.kindOf("decimal(6,2)"));
        assertEquals(Kind.NUMBER, DumpLiterals.kindOf("YEAR"));
        assertEquals(Kind.BINARY, DumpLiterals.kindOf("LONGBLOB"));
        assertEquals(Kind.BINARY, DumpLiterals.kindOf("BIT"));
        assertEquals(Kind.BINARY, DumpLiterals.kindOf("VARBINARY"));
        assertEquals(Kind.BINARY, DumpLiterals.kindOf("GEOMETRY"));
        assertEquals(Kind.TEXT, DumpLiterals.kindOf("VARCHAR"));
        assertEquals(Kind.TEXT, DumpLiterals.kindOf("DATE"));
        assertEquals(Kind.TEXT, DumpLiterals.kindOf("ENUM"));
        assertEquals(Kind.TEXT, DumpLiterals.kindOf("JSON"));
    }

    @Test
    void enumESetTraApici() {
        assertEquals("'rosso,verde'", DumpLiterals.literal("rosso,verde", Kind.TEXT));
    }
}
