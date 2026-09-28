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
import java.util.List;

/**
 * Le righe di un file da importare, <b>una alla volta</b> (streaming: la memoria non cresce con il file).
 * Si apre con {@link ImportFile#open()}.
 */
public interface ImportSource extends AutoCloseable {

    /** Nomi delle colonne, nell'ordine del file. */
    List<String> columns();

    /** La riga successiva; {@code null} a fine file. */
    SourceRow next() throws IOException, ImportFileException;

    /** Righe saltate perché vuote (un CSV con {@code ;;;} a fine elenco). */
    long skippedEmptyRows();

    @Override
    void close() throws IOException;
}
