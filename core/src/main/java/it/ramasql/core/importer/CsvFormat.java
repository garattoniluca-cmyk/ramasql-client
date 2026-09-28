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

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Come è scritto un file CSV: codifica, separatore dei campi, carattere delle virgolette, prima riga d'intestazione.
 * Lo propone {@link CsvSniffer}; l'utente lo corregge al primo passo della procedura guidata.
 *
 * @param charset   codifica del testo
 * @param separator separatore dei campi: {@code ;} (Excel italiano), {@code ,}, tabulazione, {@code |}
 * @param quote     carattere che racchiude i campi con separatori o a-capo (di solito {@code "})
 * @param header    la prima riga contiene i nomi delle colonne
 */
public record CsvFormat(Charset charset, char separator, char quote, boolean header) {

    /** I separatori proposti, nell'ordine in cui si preferiscono a parità di indizi. */
    public static final char[] SEPARATORS = {';', ',', '\t', '|'};

    public CsvFormat {
        Objects.requireNonNull(charset, "charset");
        if (separator == quote) {
            throw new IllegalArgumentException("separatore e virgolette coincidono: " + separator);
        }
        if (separator == '\n' || separator == '\r' || quote == '\n' || quote == '\r') {
            throw new IllegalArgumentException("a-capo non ammesso come separatore o virgolette");
        }
    }

    public CsvFormat withCharset(Charset v) {
        return new CsvFormat(v, separator, quote, header);
    }

    public CsvFormat withSeparator(char v) {
        return new CsvFormat(charset, v, quote, header);
    }

    public CsvFormat withHeader(boolean v) {
        return new CsvFormat(charset, separator, quote, v);
    }
}
