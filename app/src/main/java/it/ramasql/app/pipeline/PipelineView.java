/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.pipeline;

import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlScript;

/**
 * Ciò che la pipeline racconta al pannello SQL (schede Anteprima e Messaggi). Sempre chiamato sull'EDT.
 * Il Registro non passa di qui: lo alimenta {@code SqlLog}, che riceve le istruzioni da {@code SqlExecutor}.
 */
public interface PipelineView {

    /** Tipo di messaggio (colore nella scheda Messaggi). */
    enum MessageKind { SUCCESS, INFO, WARNING, ERROR }

    /** Uno script sta per essere mostrato in anteprima: la scheda Anteprima lo mostra. */
    void scriptProposed(SqlScript script);

    /** L'esecuzione è finita (bene o male): esito per la scheda Messaggi. */
    void scriptFinished(ScriptResult result);

    /** Un messaggio libero (annullato, copiato, errore di lettura…). */
    void message(MessageKind kind, String text);
}
