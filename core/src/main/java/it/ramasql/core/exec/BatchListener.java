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

/**
 * Avanzamento di un {@link BatchInsert}, sul thread dell'esecutore (come {@link ExecutionListener}).
 */
public interface BatchListener extends ExecutionListener {

    /**
     * Un lotto è finito.
     *
     * @param batches           lotti mandati finora
     * @param inserted          righe inserite finora
     * @param rejected          righe rifiutate dal server finora
     * @param duplicatesIgnored duplicati ignorati finora
     */
    default void batchFinished(long batches, long inserted, long rejected, long duplicatesIgnored) {
    }

    /** Una riga è stata rifiutata dal server. */
    default void rowRejected(BatchResult.RowError error) {
    }
}
