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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Il file da importare e come leggerlo: percorso, tipo (CSV o JSON), formato. È ciò che l'utente decide al primo passo
 * della procedura guidata; {@link #detect(Path)} ne propone uno.
 *
 * @param path        file
 * @param kind        CSV o JSON
 * @param csv         formato del CSV (per un JSON conta solo la codifica)
 * @param jsonColumns chiavi del JSON in ordine di comparsa (le raccoglie {@link FileAnalyzer}); vuoto per un CSV
 */
public record ImportFile(Path path, Kind kind, CsvFormat csv, List<String> jsonColumns) {

    /** Tipo di file. */
    public enum Kind { CSV, JSON }

    /** Byte letti per riconoscere formato e codifica. */
    static final int HEAD_BYTES = 64 * 1024;

    public ImportFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(csv, "csv");
        jsonColumns = jsonColumns == null ? List.of() : List.copyOf(jsonColumns);
    }

    /**
     * Propone tipo e formato guardando l'inizio del file: JSON se l'estensione è {@code .json} o il primo carattere
     * significativo è {@code [}; altrimenti CSV con separatore, codifica e intestazione dedotti.
     */
    public static ImportFile detect(Path path) throws IOException {
        byte[] head = new byte[HEAD_BYTES];
        int n;
        boolean whole;
        try (InputStream in = Files.newInputStream(path)) {
            n = in.readNBytes(head, 0, head.length);
            whole = in.read() < 0;
        }
        CsvFormat format = CsvSniffer.sniff(head, n, whole);
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String start = new String(head, EncodingDetector.bomLength(head, n), Math.max(0, n - EncodingDetector.bomLength(head, n)),
                format.charset()).stripLeading();
        boolean json = name.endsWith(".json") || (!name.endsWith(".csv") && !name.endsWith(".txt") && start.startsWith("["));
        return new ImportFile(path, json ? Kind.JSON : Kind.CSV, format, List.of());
    }

    public Charset charset() {
        return csv.charset();
    }

    public ImportFile withFormat(CsvFormat v) {
        return new ImportFile(path, kind, v, jsonColumns);
    }

    public ImportFile withKind(Kind v) {
        return new ImportFile(path, v, csv, jsonColumns);
    }

    public ImportFile withJsonColumns(List<String> v) {
        return new ImportFile(path, kind, csv, v);
    }

    /** Il nome del file, per i titoli e il rapporto. */
    public String fileName() {
        return path.getFileName().toString();
    }

    /**
     * Apre il file per leggerne le righe una alla volta. Per un JSON servono le colonne ({@link #jsonColumns()},
     * dall'analisi): le chiavi che non ci sono si ignorano.
     */
    public ImportSource open() throws IOException, ImportFileException {
        BufferedReader reader = reader();
        try {
            return kind == Kind.CSV ? new CsvSource(reader, csv) : new JsonSource(reader, jsonColumns);
        } catch (IOException | ImportFileException | RuntimeException e) {
            reader.close();
            throw e;
        }
    }

    /**
     * Lettore del testo, BOM saltato. I byte che non appartengono alla codifica scelta diventano il carattere
     * sostitutivo (�), visibile nell'anteprima: segno che la codifica è sbagliata.
     */
    BufferedReader reader() throws IOException {
        InputStream in = Files.newInputStream(path);
        try {
            byte[] head = in.readNBytes(3);
            int bom = EncodingDetector.bomLength(head, head.length, csv.charset());
            InputStream rest = new java.io.SequenceInputStream(
                    new java.io.ByteArrayInputStream(head, bom, head.length - bom), in);
            var decoder = csv.charset().newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            return new BufferedReader(new InputStreamReader(rest, decoder), 64 * 1024);
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    // ================================================================ sorgenti

    /** Righe di un CSV: intestazione (o colonne numerate), righe vuote saltate, valori in eccesso contati. */
    static final class CsvSource implements ImportSource {
        private final BufferedReader reader;
        private final CsvParser parser;
        private final List<String> columns;
        private CsvParser.Record pending;
        private long skipped;

        CsvSource(BufferedReader reader, CsvFormat format) throws IOException, ImportFileException {
            this.reader = reader;
            this.parser = new CsvParser(reader, format);
            CsvParser.Record first = nextNonEmpty();
            if (first == null) {
                columns = List.of();
            } else if (format.header()) {
                columns = columnNames(first.fields());
            } else {
                columns = numbered(first.fields().size());
                pending = first;
            }
        }

        private CsvParser.Record nextNonEmpty() throws IOException, ImportFileException {
            CsvParser.Record r;
            while ((r = parser.next()) != null) {
                if (r.fields().stream().allMatch(f -> f.isBlank())) {
                    skipped++;
                    continue;
                }
                return r;
            }
            return null;
        }

        @Override
        public List<String> columns() {
            return columns;
        }

        @Override
        public SourceRow next() throws IOException, ImportFileException {
            CsvParser.Record r = pending != null ? pending : nextNonEmpty();
            pending = null;
            if (r == null) {
                return null;
            }
            List<Object> values = new ArrayList<>(columns.size());
            List<String> fields = r.fields();
            for (int i = 0; i < columns.size(); i++) {
                values.add(i < fields.size() ? fields.get(i) : null);
            }
            int extra = 0;
            for (int i = columns.size(); i < fields.size(); i++) {
                if (!fields.get(i).isBlank()) {
                    extra++;
                }
            }
            return new SourceRow(r.line(), values, extra, Math.max(0, columns.size() - fields.size()));
        }

        @Override
        public long skippedEmptyRows() {
            return skipped;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    /** Righe di un JSON allineate alle colonne scelte. */
    static final class JsonSource implements ImportSource {
        private final BufferedReader reader;
        private final JsonArrayReader json;
        private final List<String> columns;

        JsonSource(BufferedReader reader, List<String> columns) throws IOException {
            this.reader = reader;
            this.json = new JsonArrayReader(reader);
            this.columns = List.copyOf(columns);
        }

        @Override
        public List<String> columns() {
            return columns;
        }

        @Override
        public SourceRow next() throws IOException, ImportFileException {
            JsonArrayReader.JsonRecord r = json.next();
            if (r == null) {
                return null;
            }
            Map<String, Object> map = r.values();
            List<Object> values = new ArrayList<>(columns.size());
            for (String c : columns) {
                values.add(map.get(c));
            }
            return new SourceRow(r.line(), values, 0, 0);
        }

        @Override
        public long skippedEmptyRows() {
            return 0;
        }

        @Override
        public void close() throws IOException {
            json.close();
            reader.close();
        }
    }

    /** Nomi dall'intestazione: spazi tolti ai lati; vuoti → «colonna_N»; ripetuti → «nome_2». */
    static List<String> columnNames(List<String> header) {
        List<String> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).strip();
            if (name.isEmpty()) {
                name = "colonna_" + (i + 1);
            }
            String unique = name;
            for (int k = 2; !used.add(unique.toLowerCase(Locale.ROOT)); k++) {
                unique = name + "_" + k;
            }
            out.add(unique);
        }
        return out;
    }

    static List<String> numbered(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add("colonna_" + i);
        }
        return out;
    }
}
