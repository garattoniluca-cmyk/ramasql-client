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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Legge <b>tutto</b> il file una volta, in streaming, prima dell'importazione: colonne (per un JSON l'unione delle
 * chiavi, nell'ordine in cui compaiono), numero di righe, prime righe per l'anteprima e il {@link ColumnProfile} di
 * ogni colonna, da cui {@link TypeInference} deduce i tipi. Guardare tutte le righe, e non un campione, evita di
 * proporre un {@code VARCHAR(20)} per una colonna che alla riga 50 000 ha un testo più lungo.
 * Gira fuori dall'EDT; si può interrompere.
 */
public final class FileAnalyzer {

    /** Righe tenute per l'anteprima. */
    public static final int PREVIEW_ROWS = 20;

    /**
     * Il risultato.
     *
     * @param file          il file, con le colonne JSON raccolte
     * @param columns       nomi delle colonne
     * @param preview       prime righe
     * @param rows          righe con almeno un valore
     * @param skippedEmpty  righe vuote saltate
     * @param extraValues   righe con più valori delle colonne (non si importeranno)
     * @param inferred      tipo dedotto per ogni colonna, nello stesso ordine
     * @param profiles      profili delle colonne
     * @param shortRows     righe con meno valori delle colonne (i mancanti valgono come vuoti)
     * @param replacements  valori con lettere illeggibili (�): la codifica non è quella del file
     * @param encodingFixed la codifica proposta (UTF-8) era sbagliata e l'analisi l'ha corretta in Windows-1252
     */
    public record Analysis(ImportFile file, List<String> columns, List<SourceRow> preview, long rows,
            long skippedEmpty, long extraValues, List<TypeInference.Inferred> inferred,
            List<ColumnProfile> profiles, long shortRows, long replacements, boolean encodingFixed) {

        Analysis withEncodingFixed() {
            return new Analysis(file, columns, preview, rows, skippedEmpty, extraValues, inferred, profiles, shortRows,
                    replacements, true);
        }
    }

    private FileAnalyzer() {
    }

    /**
     * @param cancelled chiesto di tanto in tanto: {@code true} ferma la lettura ({@link CancellationException})
     * @param progress  righe lette finora, ogni 10 000 (può essere {@code null})
     */
    public static Analysis analyze(ImportFile file, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException, ImportFileException {
        Analysis a = file.kind() == ImportFile.Kind.CSV ? csv(file, cancelled, progress) : json(file, cancelled, progress);
        if (a.replacements() > 0 && file.charset().equals(java.nio.charset.StandardCharsets.UTF_8) && !hasBom(file)) {
            // la codifica si decide sui primi 64 kB: se più avanti i byte non sono UTF-8 (un CSV di Excel con gli
            // accenti solo in fondo) si rilegge tutto in Windows-1252, e se così è pulito si tiene quella
            ImportFile cp1252 = file.withFormat(file.csv().withCharset(EncodingDetector.WINDOWS_1252));
            Analysis b = cp1252.kind() == ImportFile.Kind.CSV ? csv(cp1252, cancelled, progress)
                    : json(cp1252, cancelled, progress);
            if (b.replacements() == 0) {
                return b.withEncodingFixed();
            }
        }
        return a;
    }

    private static boolean hasBom(ImportFile file) throws IOException {
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(file.path())) {
            byte[] head = in.readNBytes(3);
            return EncodingDetector.bomLength(head, head.length) > 0;
        }
    }

    private static long replacements(List<ColumnProfile> profiles, List<String> columns) {
        long n = profiles.stream().mapToLong(ColumnProfile::replacementCount).sum();
        return n + columns.stream().filter(c -> c.indexOf('�') >= 0).count();
    }

    private static Analysis csv(ImportFile file, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException, ImportFileException {
        try (ImportSource source = file.open()) {
            List<String> columns = source.columns();
            List<ColumnProfile> profiles = new ArrayList<>();
            for (String c : columns) {
                profiles.add(new ColumnProfile(c));
            }
            List<SourceRow> preview = new ArrayList<>();
            long rows = 0;
            long extra = 0;
            long shortRows = 0;
            SourceRow r;
            while ((r = source.next()) != null) {
                rows++;
                if (preview.size() < PREVIEW_ROWS) {
                    preview.add(r);
                }
                if (r.missingValues() > 0) {
                    shortRows++;
                }
                if (r.extraValues() > 0) {
                    extra++;
                } else {
                    for (int i = 0; i < columns.size(); i++) {
                        profiles.get(i).add(r.values().get(i));
                    }
                }
                tick(rows, cancelled, progress);
            }
            return new Analysis(file, columns, preview, rows, source.skippedEmptyRows(), extra, infer(profiles),
                    profiles, shortRows, replacements(profiles, columns), false);
        }
    }

    private static Analysis json(ImportFile file, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException, ImportFileException {
        Map<String, ColumnProfile> profiles = new LinkedHashMap<>();
        List<JsonArrayReader.JsonRecord> firstRecords = new ArrayList<>();
        long rows = 0;
        try (BufferedReader reader = file.reader(); JsonArrayReader json = new JsonArrayReader(reader)) {
            JsonArrayReader.JsonRecord r;
            while ((r = json.next()) != null) {
                for (String key : r.values().keySet()) {
                    if (!profiles.containsKey(key)) {
                        ColumnProfile p = new ColumnProfile(key);
                        p.addMissing(rows);   // nelle righe prima di questa la chiave non c'era
                        profiles.put(key, p);
                    }
                }
                for (Map.Entry<String, ColumnProfile> e : profiles.entrySet()) {
                    e.getValue().add(r.values().get(e.getKey()));
                }
                rows++;
                if (firstRecords.size() < PREVIEW_ROWS) {
                    firstRecords.add(r);
                }
                tick(rows, cancelled, progress);
            }
        }
        List<String> columns = List.copyOf(profiles.keySet());
        List<SourceRow> preview = new ArrayList<>();
        for (JsonArrayReader.JsonRecord r : firstRecords) {
            List<Object> values = new ArrayList<>();
            for (String c : columns) {
                values.add(r.values().get(c));
            }
            preview.add(new SourceRow(r.line(), values, 0, 0));
        }
        List<ColumnProfile> list = List.copyOf(profiles.values());
        return new Analysis(file.withJsonColumns(columns), columns, preview, rows, 0, 0, infer(list), list, 0,
                replacements(list, columns), false);
    }

    private static List<TypeInference.Inferred> infer(List<ColumnProfile> profiles) {
        return profiles.stream().map(TypeInference::infer).toList();
    }

    private static void tick(long rows, BooleanSupplier cancelled, LongConsumer progress) {
        if (rows % 10_000 == 0) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            if (progress != null) {
                progress.accept(rows);
            }
        }
    }
}
