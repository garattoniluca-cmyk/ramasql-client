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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ciò che la pipeline «anteprima SQL» mostra e poi esegue (ARCHITECTURE.md §4): un titolo, un'origine e
 * le istruzioni in ordine. È immutabile: l'anteprima mostra esattamente ciò che {@link SqlExecutor} eseguirà.
 *
 * @param title      titolo dell'operazione, per l'anteprima: «Elimina la tabella «libri»»
 * @param origin     origine per il registro («Navigatore», «Editor SQL»…): vale per le istruzioni che non ne hanno una
 * @param statements istruzioni, in ordine
 */
public record SqlScript(String title, String origin, List<SqlStatement> statements) {

    public SqlScript {
        title = title == null ? "" : title;
        origin = origin == null ? "" : origin;
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }

    /** Script dai testi di un generatore; rischio calcolato per ciascuno. */
    public static SqlScript of(String title, String origin, List<String> sql) {
        return new SqlScript(title, origin, SqlStatement.listOf(sql, origin));
    }

    public static SqlScript of(String title, String origin, String... sql) {
        return of(title, origin, Arrays.asList(sql));
    }

    /** Script dal testo scritto dall'utente, separato con {@link StatementSplitter} ({@code DELIMITER} compreso). */
    public static SqlScript fromText(String title, String origin, String scriptText) {
        return of(title, origin, StatementSplitter.split(scriptText == null ? "" : scriptText).stream()
                .map(StatementSplitter.SplitStatement::text).toList());
    }

    /** Rischio complessivo: il più alto delle istruzioni (SAFE per uno script vuoto). */
    public RiskLevel risk() {
        RiskLevel worst = RiskLevel.SAFE;
        for (SqlStatement s : statements) {
            worst = worst.max(s.risk());
        }
        return worst;
    }

    public boolean isEmpty() {
        return statements.isEmpty();
    }

    public int size() {
        return statements.size();
    }

    /** Origine effettiva di un'istruzione: la sua, oppure quella dello script. */
    public String originOf(SqlStatement statement) {
        return statement.origin().isEmpty() ? origin : statement.origin();
    }

    /**
     * Testo da mostrare nell'anteprima (e da copiare nell'editor): istruzioni terminate da {@code ;} e separate da
     * una riga vuota; chi contiene {@code ;} al suo interno (corpo di una routine) è racchiuso tra
     * {@code DELIMITER $$ … $$}. Rieseguibile così com'è.
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (SqlStatement s : statements) {
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            out.append(ScriptText.terminated(s.text()));
        }
        return out.toString();
    }
}
