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
import java.util.Objects;
import java.util.Optional;

/**
 * Esito di una istruzione eseguita da {@link SqlExecutor}.
 *
 * @param index          posizione nello script (da 0)
 * @param statement      l'istruzione
 * @param status         esito
 * @param affectedRows   righe interessate ({@code INSERT/UPDATE/DELETE}) o righe lette (SELECT, entro il limite);
 *                       0 per un DDL o in caso d'errore
 * @param durationMillis durata in millisecondi, lettura dei risultati compresa
 * @param warnings       avvisi del server ({@code SHOW WARNINGS}), vuoto se non ce ne sono
 * @param resultSets     risultati tabellari, in ordine (di solito zero o uno; più d'uno per un {@code CALL})
 * @param error          errore del server; {@code null} se l'esito è OK
 */
public record StatementResult(
        int index,
        SqlStatement statement,
        Status status,
        long affectedRows,
        long durationMillis,
        List<Warning> warnings,
        List<ResultTable> resultSets,
        ServerError error) {

    public enum Status {
        /** Eseguita: i suoi effetti sono sul server. */
        OK,
        /**
         * Non riuscita: rifiutata dal server, o guasto del client (codice 0). <b>Può essere stata applicata in parte</b>
         * (MariaDB {@code DROP TABLE a, b} con {@code b} inesistente elimina {@code a}; un INSERT di più righe su MyISAM
         * lascia le righe prima del duplicato): vedi {@link #error()}.
         */
        FAILED,
        /** Interrotta dall'utente mentre girava. */
        INTERRUPTED
    }

    /**
     * Avviso del server.
     *
     * @param code    codice, es. 1051
     * @param message testo del server
     */
    public record Warning(int code, String message) {
    }

    /**
     * Errore del server.
     *
     * @param code     codice, es. 1062
     * @param sqlState SQLSTATE, es. {@code 23000}
     * @param message  testo del server
     */
    public record ServerError(int code, String sqlState, String message) {
        public ServerError {
            sqlState = sqlState == null ? "" : sqlState;
            message = message == null ? "" : message;
        }
    }

    public StatementResult {
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(status, "status");
        warnings = List.copyOf(warnings);
        resultSets = List.copyOf(resultSets);
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    /** Il primo risultato tabellare, se c'è. */
    public Optional<ResultTable> firstResult() {
        return resultSets.isEmpty() ? Optional.empty() : Optional.of(resultSets.get(0));
    }
}
