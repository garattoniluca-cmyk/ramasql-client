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
 * Esito di {@link SqlExecutor#submitBatchInsert}: le istruzioni che precedono l'inserimento (svuota, crea tabella) e
 * l'inserimento a lotti. Senza transazioni: le righe contate in {@link #inserted()} sono sul server anche se
 * l'inserimento si è fermato prima della fine.
 *
 * @param before            esito delle istruzioni prima dell'inserimento (lo script senza l'ultima istruzione)
 * @param started           l'inserimento è cominciato (le istruzioni prima sono riuscite)
 * @param batches           lotti mandati
 * @param inserted          righe inserite
 * @param duplicatesIgnored righe saltate perché la chiave c'era già (con l'opzione «ignora duplicati»)
 * @param rejected          righe rifiutate dal server, in ordine (al più {@link #MAX_DETAILS})
 * @param rejectedCount     righe rifiutate dal server in tutto
 * @param interrupted       fermato dall'utente
 * @param fatal             errore che ha fermato l'inserimento (connessione caduta…), {@code null} se nessuno
 * @param sourceFailure     il file non si è più potuto leggere: il messaggio, {@code null} se nessuno
 * @param durationMillis    durata complessiva
 */
public record BatchResult(ScriptResult before, boolean started, long batches, long inserted, long duplicatesIgnored,
        List<RowError> rejected, long rejectedCount, boolean interrupted, StatementResult.ServerError fatal,
        String sourceFailure, long durationMillis) {

    /** Righe rifiutate ricordate una per una (le altre si contano soltanto). */
    public static final int MAX_DETAILS = 1000;

    /**
     * Una riga rifiutata dal server.
     *
     * @param line     riga del file
     * @param code     codice d'errore del server
     * @param sqlState SQLSTATE
     * @param message  messaggio del server
     */
    public record RowError(long line, int code, String sqlState, String message) {
    }

    public BatchResult {
        rejected = List.copyOf(rejected);
    }

    /** Tutto il file è stato letto e ogni riga ha avuto il suo esito. */
    public boolean completed() {
        return started && !interrupted && fatal == null && sourceFailure == null;
    }
}
