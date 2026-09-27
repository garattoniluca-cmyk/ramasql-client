/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import it.ramasql.app.editor.PositionedStatement;
import it.ramasql.app.editor.SqlRunner;
import it.ramasql.app.editor.StatementOutcome;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.sqlgen.SqlIdentifiers;

/**
 * Esegue la query visiva <b>nel catalogo della scheda</b>. Il query builder scrive i nomi delle tabelle senza catalogo
 * ({@code FROM libri}), come li scrive uno studente; la sessione però può trovarsi in un altro catalogo o in nessuno.
 * Per questo davanti alla query si esegue {@code USE `catalogo`}: è un'istruzione vera, passa dalla pipeline come le
 * altre e compare nel registro (niente SQL nascosto, regola 3 di {@code CLAUDE.md}). All'editor torna solo l'esito
 * della query: gli indici si spostano di uno; se il {@code USE} fallisce (catalogo eliminato) l'errore si riporta sulla
 * query, che non è partita.
 */
final class CatalogScopedRunner implements SqlRunner {

    private final SqlRunner delegate;
    private final String catalog;

    CatalogScopedRunner(SqlRunner delegate, String catalog) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.catalog = catalog;
    }

    /** L'istruzione che fissa il catalogo: {@code USE `c`}. */
    static String useStatement(String catalog) {
        return "USE " + SqlIdentifiers.quote(catalog);
    }

    @Override
    public void run(List<PositionedStatement> statements, String origin, Listener listener) {
        String visualOrigin = SqlOrigin.QUERY_BUILDER.label();
        if (catalog == null || catalog.isBlank() || statements.isEmpty()) {
            delegate.run(statements, visualOrigin, listener);
            return;
        }
        List<PositionedStatement> all = new ArrayList<>(statements.size() + 1);
        all.add(new PositionedStatement(new SqlStatement(useStatement(catalog), visualOrigin, RiskLevel.SAFE), 0, 0, 1));
        all.addAll(statements);
        delegate.run(all, visualOrigin, new Listener() {
            @Override
            public void started(int index) {
                if (index > 0) {
                    listener.started(index - 1);
                }
            }

            @Override
            public void finished(StatementOutcome outcome) {
                if (outcome.index() == 0) {
                    if (outcome.isError()) {   // la query non è partita: l'errore del USE si mostra su di lei
                        listener.finished(StatementOutcome.failed(0, outcome.error(), outcome.duration()));
                    }
                    return;
                }
                listener.finished(new StatementOutcome(outcome.index() - 1, outcome.result(), outcome.affectedRows(),
                        outcome.duration(), outcome.warnings(), outcome.error()));
            }

            @Override
            public void done(boolean cancelled) {
                listener.done(cancelled);
            }
        });
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }
}
