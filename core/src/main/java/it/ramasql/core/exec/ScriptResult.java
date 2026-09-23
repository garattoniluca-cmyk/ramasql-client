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
 * Esito di uno script: le istruzioni eseguite in ordine, fino alla prima non riuscita (compresa). Senza
 * transazioni (ADR-010): ciò che è in {@link #applied()} resta sul server anche se una istruzione successiva fallisce.
 *
 * @param script         lo script
 * @param results        esiti delle istruzioni tentate, in ordine
 * @param durationMillis durata complessiva
 * @param stopRequested  l'utente ha chiesto di interrompere durante l'esecuzione
 */
public record ScriptResult(SqlScript script, List<StatementResult> results, long durationMillis,
        boolean stopRequested) {

    public ScriptResult {
        Objects.requireNonNull(script, "script");
        results = List.copyOf(results);
    }

    /** Tutte le istruzioni sono state eseguite con successo. */
    public boolean completed() {
        return results.size() == script.size() && results.stream().allMatch(StatementResult::isOk);
    }

    /** Istruzioni applicate (esito OK), in ordine. */
    public List<SqlStatement> applied() {
        return results.stream().filter(StatementResult::isOk).map(StatementResult::statement).toList();
    }

    /** La prima istruzione non riuscita o interrotta, se c'è: lo script si è fermato lì. */
    public Optional<StatementResult> failure() {
        return results.stream().filter(r -> !r.isOk()).findFirst();
    }

    /**
     * Lo script è stato interrotto dall'utente: un'istruzione risulta interrotta, oppure l'interruzione è arrivata tra
     * un'istruzione e l'altra e le successive non sono state tentate.
     */
    public boolean interrupted() {
        return failure().map(r -> r.status() == StatementResult.Status.INTERRUPTED)
                .orElse(stopRequested && !completed());
    }

    /** Istruzioni non tentate (dopo quella che ha fermato lo script, o dopo l'interruzione). */
    public List<SqlStatement> notExecuted() {
        return script.statements().subList(results.size(), script.size());
    }
}
