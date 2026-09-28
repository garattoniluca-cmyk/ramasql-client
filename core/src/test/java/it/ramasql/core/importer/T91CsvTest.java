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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T9.1 — lettore CSV: separatori {@code ;} {@code ,} tabulazione; virgolette con a-capo e virgolette raddoppiate;
 * UTF-8 con e senza BOM; Windows-1252; riga finale vuota; righe con colonne in più e in meno. I record letti sono
 * quelli attesi; codifica e separatore sono rilevati.
 */
@Tag("step9")
class T91CsvTest {

    @TempDir
    Path dir;

    private static List<List<String>> parse(String text, char sep) throws Exception {
        CsvParser p = new CsvParser(new StringReader(text), sep, '"');
        List<List<String>> out = new ArrayList<>();
        CsvParser.Record r;
        while ((r = p.next()) != null) {
            out.add(r.fields());
        }
        return out;
    }

    private Path write(String name, byte[] bytes) throws IOException {
        Path f = dir.resolve(name);
        Files.write(f, bytes);
        return f;
    }

    private static byte[] bytes(String s, Charset cs, boolean bom) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (bom) {
            out.writeBytes(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        out.writeBytes(s.getBytes(cs));
        return out.toByteArray();
    }

    private static List<List<Object>> rows(ImportFile f) throws Exception {
        List<List<Object>> out = new ArrayList<>();
        try (ImportSource s = f.open()) {
            SourceRow r;
            while ((r = s.next()) != null) {
                out.add(r.values());
            }
        }
        return out;
    }

    @Test
    void separatorePuntoEVirgola() throws Exception {
        assertEquals(List.of(List.of("a", "b", "c"), List.of("1", "2", "3")), parse("a;b;c\n1;2;3\n", ';'));
    }

    @Test
    void separatoreVirgola() throws Exception {
        assertEquals(List.of(List.of("a", "b"), List.of("x y", "2")), parse("a,b\r\nx y,2\r\n", ','));
    }

    @Test
    void separatoreTabulazione() throws Exception {
        assertEquals(List.of(List.of("a", "b;c", "d,e")), parse("a\tb;c\td,e", '\t'));
    }

    @Test
    void virgoletteConSeparatoreEACapoDentro() throws Exception {
        List<List<String>> r = parse("titolo;nota\n\"Uno; due\";\"prima riga\nseconda riga\"\nfine;x\n", ';');
        assertEquals(3, r.size());
        assertEquals(List.of("Uno; due", "prima riga\nseconda riga"), r.get(1));
        assertEquals(List.of("fine", "x"), r.get(2));
    }

    @Test
    void aCapoWindowsDentroLeVirgoletteDiventaUnSoloACapo() throws Exception {
        assertEquals(List.of(List.of("a\nb", "c")), parse("\"a\r\nb\";c\r\n", ';'));
    }

    @Test
    void virgoletteRaddoppiate() throws Exception {
        assertEquals(List.of(List.of("Il \"nome\" della rosa", "Eco")),
                parse("\"Il \"\"nome\"\" della rosa\";Eco\n", ';'));
    }

    @Test
    void campoTraVirgoletteVuoto() throws Exception {
        assertEquals(List.of(List.of("", "b", "")), parse("\"\";b;\"\"", ';'));
    }

    @Test
    void virgolettaInMezzoAlCampoRestaComE() throws Exception {
        assertEquals(List.of(List.of("pollici 5\"", "x")), parse("pollici 5\";x\n", ';'));
    }

    @Test
    void rigaFinaleVuotaERigheVuoteInMezzoNonSonoRecord() throws Exception {
        assertEquals(List.of(List.of("a", "b"), List.of("1", "2")), parse("a;b\n\n1;2\n\n", ';'));
        assertEquals(List.of(List.of("a", "b")), parse("a;b\r\n\r\n", ';'));
    }

    @Test
    void soloRitornoCarrelloComeFineRiga() throws Exception {
        assertEquals(List.of(List.of("a", "b"), List.of("1", "2")), parse("a;b\r1;2\r", ';'));
    }

    @Test
    void numeroDiRigaDelRecordContaGliACapoDentroLeVirgolette() throws Exception {
        CsvParser p = new CsvParser(new StringReader("a;b\n\"x\ny\";1\nz;2\n"), ';', '"');
        assertEquals(1, p.next().line());
        assertEquals(2, p.next().line());
        assertEquals(4, p.next().line());
        assertNull(p.next());
    }

    @Test
    void virgoletteMaiChiuseErroreConLaRiga() {
        ImportFileException e = assertThrows(ImportFileException.class,
                () -> parse("a;b\n1;2\n3;\"aperte\n4;5\n", ';'));
        assertEquals(3, e.line());
        assertTrue(e.getMessage().contains("riga 3"), e.getMessage());
        assertTrue(e.getMessage().contains("virgolette"), e.getMessage());
    }

    @Test
    void utf8SenzaBomRilevato() throws Exception {
        Path f = write("soci.csv", bytes("cognome;città\nRossi;Forlì\n", StandardCharsets.UTF_8, false));
        ImportFile file = ImportFile.detect(f);
        assertEquals(StandardCharsets.UTF_8, file.charset());
        assertEquals(';', file.csv().separator());
        assertEquals(List.of("cognome", "città"), file.open().columns());
        assertEquals(List.of(List.of("Rossi", "Forlì")), rows(file));
    }

    @Test
    void utf8ConBomRilevatoEBomSaltato() throws Exception {
        Path f = write("soci.csv", bytes("cognome;nome\nD'Annunzio;Gabriele\n", StandardCharsets.UTF_8, true));
        ImportFile file = ImportFile.detect(f);
        assertEquals(StandardCharsets.UTF_8, file.charset());
        assertEquals(List.of("cognome", "nome"), file.open().columns(), "il BOM non finisce nel nome della colonna");
        assertEquals(List.of(List.of("D'Annunzio", "Gabriele")), rows(file));
    }

    @Test
    void windows1252Rilevato() throws Exception {
        Path f = write("excel.csv", bytes("cognome;città\nNiccolò;Cesenatico\nSforzà;Forlì\n",
                EncodingDetector.WINDOWS_1252, false));
        ImportFile file = ImportFile.detect(f);
        assertEquals(EncodingDetector.WINDOWS_1252, file.charset());
        assertEquals(List.of(List.of("Niccolò", "Cesenatico"), List.of("Sforzà", "Forlì")), rows(file));
    }

    @Test
    void soloAsciiEUtf8() {
        byte[] b = "a;b\n1;2\n".getBytes(StandardCharsets.US_ASCII);
        assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(b, b.length, true).charset());
    }

    @Test
    void utf8TagliatoDalLimiteDiLetturaNonDiventaWindows1252() {
        byte[] full = "àèìòù".repeat(10).getBytes(StandardCharsets.UTF_8);
        int cut = full.length - 1;   // l'ultima «ù» spezzata a metà
        assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(full, cut, false).charset());
        assertEquals(EncodingDetector.WINDOWS_1252, EncodingDetector.detect(full, cut, true).charset(),
                "se il file finisce davvero lì, non è UTF-8 valido");
    }

    @Test
    void separatoreRilevatoPuntoEVirgolaVirgolaTab() {
        String[] texts = {"a;b;c\n1;2,5;3\n4;5,5;6\n", "a,b,c\n1,\"2;5\",3\n4,5,6\n", "a\tb\tc\n1\t2,5\t3\n"};
        char[] expected = {';', ',', '\t'};
        for (int i = 0; i < texts.length; i++) {
            CsvFormat f = CsvSniffer.sniffText(texts[i], StandardCharsets.UTF_8, true);
            assertEquals(expected[i], f.separator(), texts[i]);
        }
    }

    @Test
    void separatoreConVirgolaDecimaleItalianaResta() {
        // le virgole decimali non devono far scegliere la virgola come separatore
        CsvFormat f = CsvSniffer.sniffText("titolo;prezzo\nUno;12,50\nDue;8,00\nTre;9,90\n", StandardCharsets.UTF_8, true);
        assertEquals(';', f.separator());
        assertTrue(f.header());
    }

    @Test
    void intestazioneRilevataOppureNo() {
        assertTrue(CsvSniffer.sniffText("id;cognome\n1;Rossi\n2;Bianchi\n", StandardCharsets.UTF_8, true).header());
        assertFalse(CsvSniffer.sniffText("1;Rossi\n2;Bianchi\n", StandardCharsets.UTF_8, true).header());
        assertFalse(CsvSniffer.sniffText("12/03/2001;Rossi\n", StandardCharsets.UTF_8, true).header());
    }

    @Test
    void senzaIntestazioneColonneNumerate() throws Exception {
        Path f = write("n.csv", bytes("1;Rossi\n2;Bianchi\n", StandardCharsets.UTF_8, false));
        ImportFile file = ImportFile.detect(f);
        assertFalse(file.csv().header());
        assertEquals(List.of("colonna_1", "colonna_2"), file.open().columns());
        assertEquals(2, rows(file).size());
    }

    @Test
    void righeConColonneInMenoOInPiu() throws Exception {
        Path f = write("r.csv", bytes("a;b;c\n1;2;3\n4;5\n6;7;8;9\n10;11;12;\n", StandardCharsets.UTF_8, false));
        ImportFile file = ImportFile.detect(f);
        try (ImportSource s = file.open()) {
            SourceRow full = s.next();
            assertEquals(List.of("1", "2", "3"), full.values());
            SourceRow shortRow = s.next();
            assertEquals(java.util.Arrays.asList("4", "5", null), shortRow.values(), "valore mancante = assente");
            assertEquals(0, shortRow.extraValues());
            SourceRow longRow = s.next();
            assertEquals(1, longRow.extraValues(), "un valore in più");
            assertEquals(4, longRow.line());
            SourceRow trailing = s.next();
            assertEquals(0, trailing.extraValues(), "un separatore finale senza valore non è un valore in più");
            assertNull(s.next());
        }
    }

    @Test
    void righeDiSoliSeparatoriSaltateEContate() throws Exception {
        Path f = write("v.csv", bytes("a;b\n1;2\n;\n;;\n3;4\n", StandardCharsets.UTF_8, false));
        ImportFile file = ImportFile.detect(f);
        try (ImportSource s = file.open()) {
            assertEquals(List.of("1", "2"), s.next().values());
            assertEquals(List.of("3", "4"), s.next().values());
            assertNull(s.next());
            assertEquals(2, s.skippedEmptyRows());
        }
    }

    @Test
    void intestazioneConNomiVuotiORipetuti() {
        assertEquals(List.of("nome", "colonna_2", "Nome_2"), ImportFile.columnNames(List.of(" nome ", "", "Nome")));
    }

    @Test
    void jsonRiconosciutoDallEstensioneEDalContenuto() throws Exception {
        assertEquals(ImportFile.Kind.JSON,
                ImportFile.detect(write("libri.json", bytes("[]", StandardCharsets.UTF_8, false))).kind());
        assertEquals(ImportFile.Kind.JSON,
                ImportFile.detect(write("dati", bytes(" [ {\"a\":1} ]", StandardCharsets.UTF_8, false))).kind());
        assertEquals(ImportFile.Kind.CSV,
                ImportFile.detect(write("dati.csv", bytes("a;b\n", StandardCharsets.UTF_8, false))).kind());
    }

    @Test
    void accentiDiWindows1252OltreIPrimi64kBCorrettiDallAnalisi() throws Exception {
        StringBuilder sb = new StringBuilder("cognome;citta\n");
        while (sb.length() < 70_000) {
            sb.append("Rossi;Bologna\n");   // solo ASCII nei primi 64 kB: sembra UTF-8
        }
        sb.append("Niccolò;Forlì\n");
        Path f = write("lungo.csv", bytes(sb.toString(), EncodingDetector.WINDOWS_1252, false));
        ImportFile detected = ImportFile.detect(f);
        assertEquals(StandardCharsets.UTF_8, detected.charset(), "dai primi 64 kB sembra UTF-8");
        FileAnalyzer.Analysis a = FileAnalyzer.analyze(detected, null, null);
        assertTrue(a.encodingFixed(), "l'analisi di tutto il file si accorge dei byte non UTF-8");
        assertEquals(EncodingDetector.WINDOWS_1252, a.file().charset());
        assertEquals(0, a.replacements());
        List<List<Object>> all = rows(a.file());
        assertEquals(List.of("Niccolò", "Forlì"), all.get(all.size() - 1), "gli accenti arrivano giusti");
    }

    @Test
    void utf8ConBomNonSiCambiaMaLeLettereIllegibiliSiContano() throws Exception {
        byte[] head = bytes("a;b\nx;", StandardCharsets.UTF_8, true);
        byte[] tail = "Forlì\n".getBytes(EncodingDetector.WINDOWS_1252);
        byte[] all = java.util.Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        Path f = write("bom.csv", all);
        FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(f), null, null);
        assertFalse(a.encodingFixed(), "con il BOM il file dichiara UTF-8: non si cambia da soli");
        assertEquals(1, a.replacements(), "ma il problema si conta, e la procedura guidata lo dice");
    }

    @Test
    void righeCorteContate() throws Exception {
        Path f = write("corte.csv", bytes("a;b;c\n1;2;3\n4;5\n", StandardCharsets.UTF_8, false));
        FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(f), null, null);
        assertEquals(1, a.shortRows());
    }

    @Test
    void codificaSbagliataSiVedeComeCarattereSostitutivo() throws Exception {
        Path f = write("w.csv", bytes("a;b\nForlì;x\n", EncodingDetector.WINDOWS_1252, false));
        ImportFile utf8 = ImportFile.detect(f).withFormat(ImportFile.detect(f).csv().withCharset(StandardCharsets.UTF_8));
        assertEquals("Forl�", rows(utf8).get(0).get(0), "nessuna eccezione: l'anteprima mostra il problema");
    }
}
