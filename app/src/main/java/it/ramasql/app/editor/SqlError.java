/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.util.Objects;

/**
 * Errore del server su un'istruzione, com'è arrivato: codice, SQLSTATE e testo originale. La posizione (per 1064
 * «near '…' at line N») si ricava dal testo con {@link ErrorExplainer#locate}.
 *
 * @param code     codice di errore del server (1064, 1146…); 0 se sconosciuto
 * @param sqlState SQLSTATE a cinque caratteri, {@code ""} se assente
 * @param message  messaggio originale del server, senza traduzioni
 */
public record SqlError(int code, String sqlState, String message) {

    public SqlError {
        sqlState = sqlState == null ? "" : sqlState;
        Objects.requireNonNull(message, "message");
    }
}
