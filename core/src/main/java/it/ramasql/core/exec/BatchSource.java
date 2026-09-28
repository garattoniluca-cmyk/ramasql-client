/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import java.util.List;

/**
 * Le righe di un {@link BatchInsert}, un lotto alla volta: si leggono dal file mentre si inseriscono, così la memoria
 * non cresce con il file. Chiamata sul thread dell'esecutore.
 */
public interface BatchSource {

    /**
     * Una riga da inserire.
     *
     * @param line   riga del file da cui viene (per il rapporto)
     * @param params valori, uno per colonna del {@link BatchInsert}; {@code null} = NULL; testo, numeri o altro che
     *               il driver sa inviare
     */
    record Row(long line, Object[] params) {
    }

    /**
     * Il lotto successivo, al massimo {@code maxRows} righe; vuoto a fine dati.
     *
     * @throws Exception il file non si legge più: l'inserimento si ferma e l'errore finisce nell'esito
     */
    List<Row> nextBatch(int maxRows) throws Exception;
}
