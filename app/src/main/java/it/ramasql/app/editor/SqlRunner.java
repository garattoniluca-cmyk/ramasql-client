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

import java.util.List;

/**
 * Chi esegue davvero l'SQL dell'editor. L'editor non tocca mai il database: separa le istruzioni, chiede le conferme e
 * consegna la lista qui. Nel programma l'implementazione collega la pipeline di {@code core} (esecutore, registro SQL,
 * {@code KILL QUERY} da una connessione di servizio); nei test è una finta che registra ciò che riceve.
 *
 * <h2>Contratto</h2>
 * <ul>
 *   <li>{@link #run} è <b>asincrona</b>: ritorna subito, lavora fuori dall'EDT e riporta l'avanzamento al
 *       {@link Listener}, da qualunque thread (l'editor si riporta da solo sull'EDT).</li>
 *   <li>Le istruzioni si eseguono <b>nell'ordine</b> dato, una per volta, in autocommit (nessuna transazione aggiunta).
 *       Al primo errore ci si ferma: l'esito con l'errore è l'ultimo riportato.</li>
 *   <li>Per ogni istruzione eseguita arriva esattamente un {@link Listener#finished}; alla fine, sempre, un solo
 *       {@link Listener#done} (anche dopo un errore o un'interruzione).</li>
 *   <li>{@link #cancel} interrompe l'istruzione in corso e non avvia le successive; si può chiamare in ogni momento,
 *       anche dall'EDT, e non deve bloccare.</li>
 * </ul>
 */
public interface SqlRunner {

    /** Avanzamento dell'esecuzione. */
    interface Listener {

        /** L'istruzione di posizione {@code index} (nella lista data a {@link #run}) sta per partire. */
        void started(int index);

        /** Esito di un'istruzione eseguita (anche in errore). */
        void finished(StatementOutcome outcome);

        /** Fine: tutte eseguite, fermate da un errore, o interrotte ({@code cancelled}). */
        void done(boolean cancelled);
    }

    /**
     * Esegue le istruzioni, in ordine.
     *
     * @param statements istruzioni già separate, con la loro posizione nel documento
     * @param origin     origine per il registro SQL («Editor SQL»)
     * @param listener   chi riceve l'avanzamento
     */
    void run(List<PositionedStatement> statements, String origin, Listener listener);

    /** Interrompe l'esecuzione in corso (nel programma: {@code KILL QUERY} dell'istruzione attiva). */
    void cancel();
}
