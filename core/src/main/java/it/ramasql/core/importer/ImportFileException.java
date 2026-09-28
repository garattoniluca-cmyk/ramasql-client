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

import it.ramasql.core.CoreMessages;

/**
 * Il file da importare non si può leggere: non è un CSV o un JSON valido, oppure la lettura dal disco non è riuscita.
 * Il messaggio è in italiano e dice <b>dove</b> (riga e, se si sa, colonna) e <b>perché</b>.
 */
public final class ImportFileException extends Exception {

    private static final long serialVersionUID = 1L;

    private final long line;
    private final long column;

    /**
     * @param line   riga del file (da 1), 0 se non si sa
     * @param column colonna (carattere, da 1), 0 se non si sa
     */
    public ImportFileException(String message, long line, long column, Throwable cause) {
        super(message, cause);
        this.line = line;
        this.column = column;
    }

    /** Messaggio dai testi del modulo. */
    public static ImportFileException of(long line, long column, String key, Object... args) {
        return new ImportFileException(CoreMessages.get(key, args), line, column, null);
    }

    public long line() {
        return line;
    }

    public long column() {
        return column;
    }
}
