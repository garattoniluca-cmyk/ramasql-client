/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.verify;

import it.ramasql.core.CoreMessages;
import java.util.List;

/**
 * Esito della verifica dopo l'applicazione (ADR-011, DESIGN §3.6): l'elenco delle differenze tra indici e chiavi
 * esterne chiesti e quelli riletti dal server.
 *
 * @param table  nome della tabella verificata
 * @param issues differenze, bloccanti e informative, nell'ordine: indici, poi chiavi esterne
 */
public record Verification(String table, List<VerificationIssue> issues) {

    public Verification {
        issues = List.copyOf(issues);
    }

    /** «Conforme»: nessuna differenza bloccante (le informazioni possono esserci). */
    public boolean conforming() {
        return issues.stream().noneMatch(VerificationIssue::isBlocking);
    }

    public List<VerificationIssue> blocking() {
        return issues.stream().filter(VerificationIssue::isBlocking).toList();
    }

    public List<VerificationIssue> informational() {
        return issues.stream().filter(i -> !i.isBlocking()).toList();
    }

    /** Vero se c'è almeno una differenza di questo tipo. */
    public boolean has(VerificationIssue.Kind kind) {
        return issues.stream().anyMatch(i -> i.kind() == kind);
    }

    /**
     * Testo da mostrare all'utente: «✔ verificato sul server» (più le informazioni) oppure l'elenco delle
     * differenze, una per riga.
     */
    public String summary() {
        StringBuilder out = new StringBuilder(conforming()
                ? CoreMessages.get("verify.ok", table)
                : CoreMessages.get("verify.differences", table, blocking().size()));
        for (VerificationIssue i : issues) {
            out.append('\n').append(i.isBlocking() ? "✘ " : "ℹ ").append(i.message());
        }
        return out.toString();
    }
}
