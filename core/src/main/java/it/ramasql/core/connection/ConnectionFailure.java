/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

import it.ramasql.core.CoreMessages;

/**
 * Diagnosi di una connessione non riuscita: la causa riconosciuta, la spiegazione in italiano e,
 * <strong>sempre</strong>, ciò che ha detto il server o il sistema (codice e testo originali).
 *
 * @param cause        causa riconosciuta
 * @param message      spiegazione in italiano: che cosa correggere
 * @param errorCode    codice d'errore del server (0 se l'errore non viene dal server, es. rete)
 * @param sqlState     SQLSTATE, se disponibile (altrimenti vuoto)
 * @param originalText testo originale dell'errore (del server, oppure dell'eccezione di rete)
 */
public record ConnectionFailure(ConnectionErrorCause cause, String message, int errorCode, String sqlState,
        String originalText) {

    /** Riga con il dettaglio originale, es. «Errore 1045 (SQLSTATE 28000): Access denied for user…». */
    public String originalDetail() {
        if (errorCode != 0) {
            return CoreMessages.get("connection.error.detail.server", errorCode, sqlState, originalText);
        }
        return CoreMessages.get("connection.error.detail.network", originalText);
    }
}
