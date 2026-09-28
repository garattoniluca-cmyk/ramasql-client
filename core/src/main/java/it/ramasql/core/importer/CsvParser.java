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
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lettore CSV <b>in streaming</b>: un record per volta, senza tenere il file in memoria (un CSV da un milione di righe
 * si legge con memoria costante). Segue le convenzioni di Excel e della RFC 4180:
 * <ul>
 *   <li>campi separati da un carattere scelto ({@code ;} per Excel italiano, {@code ,}, tabulazione, {@code |});</li>
 *   <li>un campo che <b>comincia</b> con le virgolette finisce alle virgolette di chiusura e può contenere separatori e
 *       a-capo; dentro, le virgolette si scrivono raddoppiate ({@code ""});</li>
 *   <li>fine riga {@code \r\n}, {@code \n} o {@code \r}; una riga vuota (anche l'ultima) non è un record;</li>
 *   <li>virgolette in mezzo a un campo non racchiuso restano come sono; testo dopo le virgolette di chiusura si
 *       aggiunge al campo (tolleranza per file scritti a mano).</li>
 * </ul>
 * Virgolette aperte e mai chiuse fino alla fine del file: {@link ImportFileException} con la riga in cui si sono aperte.
 */
public final class CsvParser {

    /**
     * Un record letto.
     *
     * @param line   riga del file (da 1) in cui comincia il record
     * @param fields campi, in ordine, mai {@code null} (un campo vuoto è {@code ""})
     */
    public record Record(long line, List<String> fields) {
    }

    private final Reader in;
    private final char separator;
    private final char quote;
    private final char[] buffer = new char[64 * 1024];
    private int pos;
    private int limit;
    private long line = 1;
    private boolean eof;

    public CsvParser(Reader in, char separator, char quote) {
        this.in = Objects.requireNonNull(in, "in");
        this.separator = separator;
        this.quote = quote;
    }

    public CsvParser(Reader in, CsvFormat format) {
        this(in, format.separator(), format.quote());
    }

    /** Il record successivo; {@code null} a fine file. */
    public Record next() throws IOException, ImportFileException {
        while (true) {
            int c = peek();
            if (c < 0) {
                return null;
            }
            if (c == '\n' || c == '\r') {   // riga vuota: non è un record
                consumeNewline();
                continue;
            }
            return readRecord();
        }
    }

    private Record readRecord() throws IOException, ImportFileException {
        long start = line;
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        while (true) {
            int c = peek();
            if (c < 0) {
                fields.add(field.toString());
                return new Record(start, fields);
            }
            if (c == quote && field.isEmpty()) {
                read();
                readQuoted(field, start);
                continue;   // dopo la chiusura: separatore, fine riga o testo in più
            }
            read();
            if (c == separator) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && peek() == '\n') {
                    read();
                }
                line++;
                fields.add(field.toString());
                return new Record(start, fields);
            } else {
                field.append((char) c);
            }
        }
    }

    /** Dentro le virgolette: fino a quelle di chiusura; {@code ""} vale una virgoletta; gli a-capo restano. */
    private void readQuoted(StringBuilder field, long start) throws IOException, ImportFileException {
        long opened = line;
        while (true) {
            int c = read();
            if (c < 0) {
                throw ImportFileException.of(opened, 0, "import.csv.unclosedQuote", opened, start);
            }
            if (c == quote) {
                if (peek() == quote) {
                    read();
                    field.append(quote);
                    continue;
                }
                return;
            }
            if (c == '\r') {
                if (peek() == '\n') {
                    read();
                }
                line++;
                field.append('\n');   // un a-capo dentro la cella, comunque fosse scritto
                continue;
            }
            if (c == '\n') {
                line++;
            }
            field.append((char) c);
        }
    }

    private void consumeNewline() throws IOException {
        int c = read();
        if (c == '\r' && peek() == '\n') {
            read();
        }
        line++;
    }

    private int peek() throws IOException {
        if (pos >= limit && !fill()) {
            return -1;
        }
        return buffer[pos];
    }

    private int read() throws IOException {
        if (pos >= limit && !fill()) {
            return -1;
        }
        return buffer[pos++];
    }

    private boolean fill() throws IOException {
        if (eof) {
            return false;
        }
        int n = in.read(buffer, 0, buffer.length);
        while (n == 0) {
            n = in.read(buffer, 0, buffer.length);
        }
        if (n < 0) {
            eof = true;
            return false;
        }
        pos = 0;
        limit = n;
        return true;
    }
}
