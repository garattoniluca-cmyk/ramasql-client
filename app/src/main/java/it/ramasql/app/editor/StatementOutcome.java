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

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Esito di un'istruzione eseguita dall'editor: <b>uno</b> tra risultato (righe), conteggio (righe interessate) ed
 * errore; sempre la durata e gli avvisi del server ({@code SHOW WARNINGS}).
 *
 * @param index        posizione dell'istruzione nella lista passata a {@link SqlRunner#run}
 * @param result       righe restituite, {@code null} se l'istruzione non ne restituisce
 * @param affectedRows righe interessate (INSERT, UPDATE, DELETE…); {@code -1} se non ha senso (SELECT, errore)
 * @param duration     tempo di esecuzione misurato
 * @param warnings     avvisi del server, vuota se nessuno
 * @param error        errore del server, {@code null} se riuscita
 */
public record StatementOutcome(int index, ResultData result, long affectedRows, Duration duration,
        List<Warning> warnings, SqlError error) {

    /** Un avviso del server (una riga di {@code SHOW WARNINGS}). */
    public record Warning(int code, String message) {
        public Warning {
            Objects.requireNonNull(message, "message");
        }
    }

    public StatementOutcome {
        Objects.requireNonNull(duration, "duration");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (error != null && result != null) {
            throw new IllegalArgumentException("Un esito in errore non ha righe");
        }
    }

    /** Istruzione che ha restituito righe. */
    public static StatementOutcome rows(int index, ResultData result, Duration duration, List<Warning> warnings) {
        return new StatementOutcome(index, Objects.requireNonNull(result, "result"), -1, duration, warnings, null);
    }

    /** Istruzione senza righe (DML, DDL): quante righe ha toccato. */
    public static StatementOutcome update(int index, long affectedRows, Duration duration, List<Warning> warnings) {
        return new StatementOutcome(index, null, affectedRows, duration, warnings, null);
    }

    /** Istruzione rifiutata dal server. */
    public static StatementOutcome failed(int index, SqlError error, Duration duration) {
        return new StatementOutcome(index, null, -1, duration, List.of(), Objects.requireNonNull(error, "error"));
    }

    public boolean isError() {
        return error != null;
    }

    public boolean hasResult() {
        return result != null;
    }
}
