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

import static it.ramasql.core.data.ClipboardBlock.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T4.15 — appunti a blocchi: blocco → testo tabulato → blocco, nella convenzione di Excel. */
@Tag("step4")
class ClipboardBlockTest {

    static Stream<Arguments> andataERitorno() {
        return Stream.of(
                arguments("blocco semplice 2×2", ClipboardBlock.of(row("a", "b"), row("c", "d")), "a\tb\r\nc\td\r\n"),
                arguments("una sola cella", ClipboardBlock.of(row("solo")), "solo\r\n"),
                arguments("tabulazione dentro la cella", ClipboardBlock.of(row("a\tb", "c")), "\"a\tb\"\tc\r\n"),
                arguments("a-capo dentro la cella", ClipboardBlock.of(row("riga1\nriga2", "x"), row("y", "z")),
                        "\"riga1\nriga2\"\tx\r\ny\tz\r\n"),
                arguments("a-capo di Windows dentro la cella", ClipboardBlock.of(row("uno\r\ndue")),
                        "\"uno\r\ndue\"\r\n"),
                arguments("virgolette dentro la cella", ClipboardBlock.of(row("dice \"ciao\"", "5\" di pollice")),
                        "\"dice \"\"ciao\"\"\"\t\"5\"\" di pollice\"\r\n"),
                arguments("cella che è solo una virgoletta", ClipboardBlock.of(row("\"")), "\"\"\"\"\r\n"),
                arguments("NULL → cella vuota", ClipboardBlock.of(row("a", null, "c")), "a\t\tc\r\n"),
                arguments("stringa vuota → \"\"", ClipboardBlock.of(row("a", "", "c")), "a\t\"\"\tc\r\n"),
                arguments("NULL e stringa vuota restano distinti", ClipboardBlock.of(row(null, ""), row("", null)),
                        "\t\"\"\r\n\"\"\t\r\n"),
                arguments("una sola cella NULL", ClipboardBlock.of(row((String) null)), "\r\n"),
                arguments("riga tutta NULL in mezzo", ClipboardBlock.of(row("a"), row((String) null), row("b")),
                        "a\r\n\r\nb\r\n"),
                arguments("apostrofi, accenti, emoji e spazi ai bordi",
                        ClipboardBlock.of(row(" l'ora ", "però", "😀")), " l'ora \tperò\t😀\r\n"),
                arguments("blocco vuoto", ClipboardBlock.of(), ""));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("andataERitorno")
    void bloccoTestoBlocco(String nome, ClipboardBlock blocco, String testoAtteso) {
        assertEquals(testoAtteso, blocco.toText());
        assertEquals(blocco, ClipboardBlock.parse(blocco.toText()), "andata e ritorno senza alterazioni");
    }

    @Test
    void leggeGliACapoDiUnixEIgnoraLaRigaFinaleVuota() {
        ClipboardBlock atteso = ClipboardBlock.of(row("a", "b"), row("c", "d"));
        assertEquals(atteso, ClipboardBlock.parse("a\tb\nc\td\n"));
        assertEquals(atteso, ClipboardBlock.parse("a\tb\nc\td"));
        assertEquals(atteso, ClipboardBlock.parse("a\tb\r\nc\td\r\n"));
        assertEquals(atteso, ClipboardBlock.parse("a\tb\rc\td\r"));
        assertEquals(0, ClipboardBlock.parse("").rowCount());
        assertEquals(0, ClipboardBlock.parse(null).rowCount());
    }

    @Test
    void unaCellaVuotaDaExcelArrivaComeValoreAssente() {
        ClipboardBlock b = ClipboardBlock.parse("Rossi\t\t1990\r\n");
        assertEquals(3, b.columnCount());
        assertNull(b.cell(0, 1));
        assertEquals("1990", b.cell(0, 2));
    }

    @Test
    void leVirgoletteContanoSoloAInizioCellaESeBenFormate() {
        assertEquals(ClipboardBlock.of(row("5\" di pollice", "x")), ClipboardBlock.parse("5\" di pollice\tx\n"));
        assertEquals(ClipboardBlock.of(row("\"aperta e mai chiusa")), ClipboardBlock.parse("\"aperta e mai chiusa"));
        assertEquals(ClipboardBlock.of(row("\"a\"b", "c")), ClipboardBlock.parse("\"a\"b\tc"));
    }

    @Test
    void leRigheDiLunghezzaDiversaDiventanoUnRettangolo() {
        ClipboardBlock b = ClipboardBlock.parse("a\tb\tc\nd\n");
        assertEquals(2, b.rowCount());
        assertEquals(3, b.columnCount());
        assertEquals(row("d", null, null), b.rows().get(1));
    }

    @Test
    void conStringheVuoteNonMarcateIlTestoEPiuPulitoMaIlRitornoDaNull() {
        ClipboardBlock b = ClipboardBlock.of(row("a", ""));
        assertEquals("a\t\r\n", b.toText(false));
        assertNull(ClipboardBlock.parse(b.toText(false)).cell(0, 1));
        assertTrue(ClipboardBlock.of(row("x")).isSingleCell());
    }
}
