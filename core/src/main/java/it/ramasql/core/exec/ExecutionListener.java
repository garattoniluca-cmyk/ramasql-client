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

/**
 * Avanzamento di uno script in {@link SqlExecutor}. Le chiamate arrivano sul <b>thread dell'esecutore</b>, mai
 * sull'EDT: un'interfaccia Swing le riporta sull'EDT con {@code SwingUtilities.invokeLater}. Non chiamare di qui
 * {@link SqlExecutor#run} (si bloccherebbe): {@link SqlExecutor#submit} va bene.
 */
public interface ExecutionListener {

    default void scriptStarted(SqlScript script) {
    }

    default void statementStarted(SqlScript script, int index, SqlStatement statement) {
    }

    default void statementFinished(SqlScript script, StatementResult result) {
    }

    default void scriptFinished(ScriptResult result) {
    }
}
