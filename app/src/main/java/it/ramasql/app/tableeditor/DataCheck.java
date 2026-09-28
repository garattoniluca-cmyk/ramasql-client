/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Il controllo <b>sui dati</b> chiesto dall'utente con «Verifica dati» (righe orfane di una chiave esterna,
 * duplicati di un UNIQUE): l'editor prepara la query e la mostra, chi implementa questa interfaccia la esegue sul
 * server (dalla pipeline, così finisce nel registro) e restituisce le righe trovate.
 */
public interface DataCheck {

    /** Esegue la {@code SELECT} di controllo e passa il risultato a {@code done} <b>sull'EDT</b>. */
    void run(String sql, Consumer<DataCheckResult> done);

    /**
     * @param columns   intestazioni delle colonne
     * @param rows      righe trovate, valori già resi come testo ({@code null} = NULL)
     * @param error     messaggio d'errore del server; {@code null} = eseguita
     * @param truncated le righe mostrate sono solo le prime: sul server ce ne sono altre ({@code BUG-021})
     */
    record DataCheckResult(List<String> columns, List<List<String>> rows, String error, boolean truncated) {

        public DataCheckResult {
            columns = List.copyOf(columns);
            rows = rows.stream().map(r -> Collections.unmodifiableList(new ArrayList<>(r))).toList();
        }

        /** Risultato completo (non troncato). */
        public DataCheckResult(List<String> columns, List<List<String>> rows, String error) {
            this(columns, rows, error, false);
        }

        public static DataCheckResult failed(String error) {
            return new DataCheckResult(List.of(), List.of(), error);
        }
    }
}
