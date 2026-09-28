/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.it.TestResults;

/**
 * I file di prova dell'importazione nel repository sono quelli del generatore, e {@code soci-excel.csv} è davvero
 * nel formato di Excel italiano: punto e virgola, Windows-1252 (senza BOM), date gg/mm/aaaa, virgola decimale, accenti.
 */
@Tag("step9")
class ImportFixturesTest {

    private static Path file(String name) {
        return TestResults.projectRoot().resolve("it-tests/fixtures").resolve(ImportFixtures.DIR).resolve(name);
    }

    @Test
    void fileGeneratiUgualiAQuelliNelRepository() throws Exception {
        assertEquals(ImportFixtures.soci(), Files.readString(file(ImportFixtures.SOCI), StandardCharsets.UTF_8),
                "rigenerare con ImportFixtures.main");
        assertEquals(ImportFixtures.libri(), Files.readString(file(ImportFixtures.LIBRI), StandardCharsets.UTF_8));
        assertEquals(ImportFixtures.prestitiConErrori(),
                Files.readString(file(ImportFixtures.PRESTITI_ERRORI), StandardCharsets.UTF_8));
        assertEquals(101, ImportFixtures.soci().lines().count());
        assertEquals(101, ImportFixtures.prestitiConErrori().lines().count());
    }

    @Test
    void sociExcelNelFormatoDiExcelItaliano() throws Exception {
        byte[] b = Files.readAllBytes(file("soci-excel.csv"));
        assertTrue(!(b[0] == (byte) 0xEF && b[1] == (byte) 0xBB), "nessun BOM");
        String text = new String(b, Charset.forName("windows-1252"));
        List<String> lines = text.lines().toList();
        assertEquals("Tessera;Cognome;Nome;Email;Nato il;Quota", lines.get(0));
        assertEquals(101, lines.size());
        assertTrue(lines.get(1).matches("S0000001;[^;]+;[^;]+;[^;]*;\\d{2}/\\d{2}/\\d{4};\\d+(,\\d+)?"), lines.get(1));
        assertTrue(text.contains("Niccolò") || text.contains("Nicolò"), "accenti in Windows-1252");
        assertTrue(text.contains(",25") || text.contains(",5"), "virgola decimale");
        boolean anyHighByte = false;
        for (byte x : b) {
            anyHighByte |= (x & 0xFF) > 127;
        }
        assertTrue(anyHighByte);
        assertTrue(new String(b, StandardCharsets.UTF_8).contains("�"), "non è UTF-8");
    }
}
