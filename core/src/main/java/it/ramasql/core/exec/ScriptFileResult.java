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
 * Esito di uno script eseguito da un file ({@link SqlExecutor#submitScriptFile}), senza transazioni generate dal client:
 * ciò che è riuscito resta sul server anche se lo script si è fermato — salvo che lo script stesso abbia aperto una
 * transazione con {@code BEGIN} e non l'abbia chiusa ({@link #transactionOpen()}).
 *
 * @param executed        istruzioni del file eseguite (riuscite o no)
 * @param succeeded       istruzioni riuscite
 * @param failures        istruzioni non riuscite, in ordine (al più {@link BatchResult#MAX_DETAILS})
 * @param failureCount    istruzioni non riuscite in tutto
 * @param interrupted     fermato dall'utente
 * @param stop            perché si è fermato prima della fine ({@link Stop#NONE} se no)
 * @param readError       il file non si è più potuto leggere: il messaggio, {@code null} se nessuno
 * @param durationMillis  durata
 * @param warningCount    avvisi del server in tutto (in modalità non rigorosa i troncamenti sono avvisi, non errori)
 * @param warnings        i primi avvisi (al più {@link #MAX_WARNINGS})
 * @param restore         istruzioni che rimettono la sessione com'era prima del file (vuoto se è già così)
 * @param transactionOpen il file ha aperto una transazione ({@code BEGIN}) e non l'ha chiusa
 */
public record ScriptFileResult(long executed, long succeeded, List<Failure> failures, long failureCount,
        boolean interrupted, Stop stop, String readError, long durationMillis, long warningCount,
        List<Warning> warnings, List<String> restore, boolean transactionOpen) {

    public static final int MAX_WARNINGS = 20;

    /** Perché l'esecuzione si è fermata prima della fine del file. */
    public enum Stop {
        /** Non si è fermata (o l'ha fermata l'utente, o il file non si leggeva più). */
        NONE,
        /** Primo errore, con l'opzione «fermati». */
        ERROR,
        /** Un {@code USE} o un {@code CREATE DATABASE} non riuscito: andando avanti si scriverebbe nel catalogo sbagliato. */
        CATALOG,
        /** La connessione è caduta: inutile provare le istruzioni seguenti. */
        CONNECTION
    }

    /**
     * Un'istruzione non riuscita.
     *
     * @param line     riga del file in cui comincia (0 = istruzione aggiunta prima del file, il {@code USE})
     * @param text     l'istruzione (abbreviata se lunghissima)
     * @param code     codice d'errore del server
     * @param sqlState SQLSTATE
     * @param message  messaggio del server
     */
    public record Failure(long line, String text, int code, String sqlState, String message) {
    }

    /** Un avviso del server, con la riga del file. */
    public record Warning(long line, int code, String message) {
    }

    public ScriptFileResult {
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
        restore = List.copyOf(restore);
        stop = stop == null ? Stop.NONE : stop;
    }

    /** Fermato prima della fine per un errore (opzione «fermati», catalogo, connessione). */
    public boolean stoppedOnError() {
        return stop != Stop.NONE;
    }

    /** Tutto il file è stato eseguito senza errori. */
    public boolean completed() {
        return !interrupted && stop == Stop.NONE && readError == null && failureCount == 0;
    }
}
