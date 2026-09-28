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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Propone il formato di un CSV guardandone l'inizio: codifica ({@link EncodingDetector}), separatore e riga
 * d'intestazione. È un'ipotesi: la procedura guidata la mostra e l'utente la corregge.
 *
 * <p><b>Separatore</b>: per ogni candidato ({@code ;} {@code ,} tabulazione {@code |}) si leggono i primi record
 * rispettando le virgolette; vince quello con cui il maggior numero di record ha lo <b>stesso</b> numero di campi (più
 * di uno). A parità, l'ordine di {@link CsvFormat#SEPARATORS}: prima il punto e virgola di Excel italiano.
 *
 * <p><b>Intestazione</b>: la prima riga lo è se i suoi campi sono tutti testo non vuoto, diversi fra loro e non
 * numeri né date, e se almeno una colonna sotto contiene numeri o date, oppure se il file ha una sola riga di testo.
 */
public final class CsvSniffer {

    /** Record guardati per decidere. */
    static final int SAMPLE_RECORDS = 50;

    private CsvSniffer() {
    }

    /**
     * @param head   primi byte del file
     * @param length byte validi
     * @param whole  {@code head} è il file intero
     */
    public static CsvFormat sniff(byte[] head, int length, boolean whole) {
        EncodingDetector.Detected enc = EncodingDetector.detect(head, length, whole);
        String text = new String(head, enc.bomLength(), length - enc.bomLength(), enc.charset());
        return sniffText(text, enc.charset(), whole);
    }

    /** Come {@link #sniff}, sul testo già decodificato. */
    public static CsvFormat sniffText(String text, Charset charset, boolean whole) {
        char best = CsvFormat.SEPARATORS[0];
        int bestScore = -1;
        List<List<String>> bestRecords = List.of();
        for (char candidate : CsvFormat.SEPARATORS) {
            List<List<String>> records = sample(text, candidate, whole);
            int score = consistency(records);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                bestRecords = records;
            }
        }
        return new CsvFormat(charset, best, '"', looksLikeHeader(bestRecords));
    }

    private static List<List<String>> sample(String text, char separator, boolean whole) {
        List<List<String>> out = new ArrayList<>();
        CsvParser parser = new CsvParser(new StringReader(text), separator, '"');
        try {
            CsvParser.Record r;
            while (out.size() < SAMPLE_RECORDS && (r = parser.next()) != null) {
                out.add(r.fields());
            }
        } catch (ImportFileException e) {
            // virgolette non chiuse: se il testo è solo l'inizio del file, il record tagliato non conta
            if (whole) {
                return out;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);   // StringReader non fallisce
        }
        if (!whole && out.size() > 1) {
            out.remove(out.size() - 1);   // l'ultimo record può essere tagliato dal limite della lettura
        }
        return out;
    }

    /** Record che hanno il numero di campi più frequente (se è almeno 2); 0 se un campo solo. */
    private static int consistency(List<List<String>> records) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (List<String> r : records) {
            counts.merge(r.size(), 1, Integer::sum);
        }
        int bestFields = 0;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() > bestFields)) {
                bestFields = e.getKey();
                bestCount = e.getValue();
            }
        }
        return bestFields < 2 ? 0 : bestCount * 1000 + bestFields;
    }

    static boolean looksLikeHeader(List<List<String>> records) {
        if (records.isEmpty()) {
            return false;
        }
        List<String> first = records.get(0);
        Set<String> seen = new HashSet<>();
        for (String f : first) {
            String t = f.trim();
            if (t.isEmpty() || !seen.add(t.toLowerCase(java.util.Locale.ROOT)) || ValueParsing.isNumberLike(t)
                    || ValueParsing.isDateLike(t)) {
                return false;
            }
        }
        if (records.size() == 1) {
            return true;
        }
        // almeno una colonna in cui la prima riga è testo e sotto ci sono numeri o date
        for (int c = 0; c < first.size(); c++) {
            for (int r = 1; r < records.size(); r++) {
                List<String> row = records.get(r);
                if (c < row.size()) {
                    String v = row.get(c).trim();
                    if (ValueParsing.isNumberLike(v) || ValueParsing.isDateLike(v)) {
                        return true;
                    }
                }
            }
        }
        // tutto testo: intestazione se la prima riga è diversa da ogni altra e le altre non si ripetono uguali a lei
        for (int r = 1; r < records.size(); r++) {
            if (records.get(r).equals(first)) {
                return false;
            }
        }
        return true;
    }
}
