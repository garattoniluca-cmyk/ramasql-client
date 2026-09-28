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

/** Avanzamento di uno script eseguito da un file, sul thread dell'esecutore. */
public interface ScriptFileListener {

    /**
     * @param statements istruzioni eseguite finora
     * @param chars      caratteri del file letti finora (per la barra rispetto alla lunghezza del file)
     */
    default void progress(long statements, long chars) {
    }

    default void statementFailed(ScriptFileResult.Failure failure) {
    }
}
