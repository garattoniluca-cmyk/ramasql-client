/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;

/**
 * «Esporta CSV» pensato per l'<b>Excel italiano</b>, che apre il file con un doppio clic senza la procedura
 * d'importazione:
 * <ul>
 *   <li><b>UTF-8 con BOM</b>: senza il BOM Excel legge il file come Windows-1252 e rovina gli accenti;</li>
 *   <li>separatore <b>{@code ;}</b> (con le impostazioni internazionali italiane la virgola è il separatore decimale,
 *       e il separatore di elenco è il punto e virgola);</li>
 *   <li>decimali delle colonne DECIMAL/FLOAT/DOUBLE con la <b>virgola</b> ({@code 12.50} → {@code 12,50}), perché
 *       l'Excel italiano leggerebbe {@code 12.50} come testo o come data; gli interi e le date ({@code AAAA-MM-GG})
 *       restano come sono;</li>
 *   <li>prima riga con i nomi delle colonne; righe chiuse da CR+LF;</li>
 *   <li>virgolette doppie solo dove servono (separatore, virgolette, a-capo, tabulazioni, spazi iniziali o finali), con le
 *       virgolette interne raddoppiate;</li>
 *   <li>NULL → campo vuoto; stringa vuota → {@code ""} (Excel mostra vuote entrambe, ma il file le distingue).</li>
 * </ul>
 */
public final class CsvExporter {

    public static final char SEPARATOR = ';';

    private CsvExporter() {
    }

    /** Scrive intestazioni e righe (i valori sono il testo delle celle, {@code null} = NULL). */
    public static void write(Path file, List<ColumnDef> columns, List<List<String>> rows) throws IOException {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write('﻿');
            for (int c = 0; c < columns.size(); c++) {
                if (c > 0) {
                    out.write(SEPARATOR);
                }
                out.write(quote(columns.get(c).name()));
            }
            out.write("\r\n");
            for (List<String> row : rows) {
                for (int c = 0; c < columns.size(); c++) {
                    if (c > 0) {
                        out.write(SEPARATOR);
                    }
                    out.write(field(row.get(c), columns.get(c)));
                }
                out.write("\r\n");
            }
        }
    }

    static String field(String value, ColumnDef column) {
        if (value == null) {
            return "";
        }
        if (value.isEmpty()) {
            return "\"\"";
        }
        String type = SqlTypes.canonical(column.dataType());
        boolean decimal = type.equals("DECIMAL") || SqlTypes.isApproximate(type);
        return quote(decimal && value.matches("[+-]?\\d*\\.\\d+") ? value.replace('.', ',') : value);
    }

    private static String quote(String value) {
        boolean needs = value.indexOf(SEPARATOR) >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\t') >= 0 || !value.equals(value.strip());
        return needs ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
