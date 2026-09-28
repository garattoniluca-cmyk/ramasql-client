/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T9.3 — lettore JSON: elenco di oggetti piatti; chiavi mancanti in alcuni oggetti; valori {@code null}; oggetto
 * annidato (→ testo JSON); file non valido (errore chiaro, con la posizione).
 */
@Tag("step9")
class T93JsonTest {

    @TempDir
    Path dir;

    private static List<JsonArrayReader.JsonRecord> read(String json) throws Exception {
        List<JsonArrayReader.JsonRecord> out = new ArrayList<>();
        try (JsonArrayReader r = new JsonArrayReader(new StringReader(json))) {
            JsonArrayReader.JsonRecord rec;
            while ((rec = r.next()) != null) {
                out.add(rec);
            }
        }
        return out;
    }

    private FileAnalyzer.Analysis analyze(String json) throws Exception {
        Path f = dir.resolve("dati.json");
        Files.writeString(f, json, StandardCharsets.UTF_8);
        return FileAnalyzer.analyze(ImportFile.detect(f), null, null);
    }

    @Test
    void elencoDiOggettiPiatti() throws Exception {
        List<JsonArrayReader.JsonRecord> r = read("[\n {\"titolo\": \"Il nome della rosa\", \"anno\": 1980, \"prezzo\": 12.50},\n"
                + " {\"titolo\": \"Q\", \"anno\": 1999, \"prezzo\": 9.9}\n]");
        assertEquals(2, r.size());
        assertEquals("Il nome della rosa", r.get(0).values().get("titolo"));
        assertEquals(1980L, r.get(0).values().get("anno"));
        assertEquals(new BigDecimal("12.50"), r.get(0).values().get("prezzo"), "nessuna cifra persa");
        assertEquals(2, r.get(0).line());
        assertEquals(3, r.get(1).line());
    }

    @Test
    void valoriNullEValoriLogici() throws Exception {
        List<JsonArrayReader.JsonRecord> r = read("[{\"a\": null, \"b\": true, \"c\": false}]");
        assertTrue(r.get(0).values().containsKey("a"));
        assertNull(r.get(0).values().get("a"));
        assertEquals(Boolean.TRUE, r.get(0).values().get("b"));
    }

    @Test
    void oggettoEElencoAnnidatiDiventanoTestoJson() throws Exception {
        List<JsonArrayReader.JsonRecord> r = read("[{\"id\": 1, \"editore\": {\"nome\": \"Adelphi\", \"citta\": \"Milano\"},"
                + " \"tag\": [\"a\", \"b\"]}]");
        assertEquals(new SourceRow.JsonText("{\"nome\":\"Adelphi\",\"citta\":\"Milano\"}"), r.get(0).values().get("editore"));
        assertEquals(new SourceRow.JsonText("[\"a\",\"b\"]"), r.get(0).values().get("tag"));
        assertEquals(1L, r.get(0).values().get("id"), "dopo l'annidato la lettura prosegue");
    }

    @Test
    void chiaviMancantiInAlcuniOggetti() throws Exception {
        FileAnalyzer.Analysis a = analyze("[{\"a\": 1, \"b\": \"x\"}, {\"a\": 2}, {\"a\": 3, \"c\": \"nuova\"}]");
        assertEquals(List.of("a", "b", "c"), a.columns(), "unione delle chiavi, nell'ordine in cui compaiono");
        assertEquals(3, a.rows());
        assertEquals(Arrays.asList(2L, null, null), a.preview().get(1).values());
        assertTrue(a.inferred().get(1).column().nullable(), "b manca nella seconda riga");
        assertTrue(a.inferred().get(2).column().nullable(), "c compare solo alla terza riga");
        assertEquals("INT", a.inferred().get(0).column().fullType());
        try (ImportSource s = a.file().open()) {
            assertEquals(List.of("a", "b", "c"), s.columns());
            assertEquals(Arrays.asList(1L, "x", null), s.next().values());
        }
    }

    @Test
    void fileNonValidoErroreConPosizione() {
        ImportFileException e = assertThrows(ImportFileException.class,
                () -> read("[\n  {\"a\": 1},\n  {\"a\": 2,, \"b\": 3}\n]"));
        assertEquals(3, e.line());
        assertTrue(e.column() > 0);
        assertTrue(e.getMessage().startsWith("Il file JSON non è valido alla riga 3, colonna"), e.getMessage());
        assertTrue(e.getMessage().contains("virgola"), "spiegazione in italiano: " + e.getMessage());
        assertTrue(!e.getMessage().contains("Unexpected") && !e.getMessage().contains("expecting"),
                "niente testo inglese del lettore: " + e.getMessage());
    }

    @Test
    void radiceCheNonEUnElenco() {
        ImportFileException e = assertThrows(ImportFileException.class, () -> read("{\"a\": 1}"));
        assertTrue(e.getMessage().contains("elenco di oggetti"), e.getMessage());
        assertEquals(1, e.line());
    }

    @Test
    void elementoCheNonEUnOggetto() {
        ImportFileException e = assertThrows(ImportFileException.class, () -> read("[{\"a\": 1},\n 42]"));
        assertEquals(2, e.line());
        assertTrue(e.getMessage().contains("non è un oggetto"), e.getMessage());
    }

    @Test
    void fileTroncato() {
        ImportFileException e = assertThrows(ImportFileException.class, () -> read("[{\"a\": 1}, {\"a\": 2"));
        assertTrue(e.getMessage().contains("riga 1"), e.getMessage());
    }

    @Test
    void fileVuotoETestoDopoLaFine() {
        assertThrows(ImportFileException.class, () -> read(""));
        ImportFileException e = assertThrows(ImportFileException.class, () -> read("[{\"a\":1}] x"));
        assertTrue(e.getMessage().contains("riga 1, colonna 11") && e.getMessage().contains("«x»"), e.getMessage());
        ImportFileException e2 = assertThrows(ImportFileException.class, () -> read("[{\"a\":1}] [2]"));
        assertTrue(e2.getMessage().contains("dopo la parentesi"), e2.getMessage());
    }

    @Test
    void elencoVuotoNessunaRiga() throws Exception {
        assertEquals(0, read("[ ]").size());
    }

    @Test
    void numeriEnormiNonSiPerdono() throws Exception {
        Object v = read("[{\"n\": 123456789012345678901234567890}]").get(0).values().get("n");
        assertInstanceOf(BigDecimal.class, v);
        assertEquals("123456789012345678901234567890", ((BigDecimal) v).toPlainString());
    }
}
