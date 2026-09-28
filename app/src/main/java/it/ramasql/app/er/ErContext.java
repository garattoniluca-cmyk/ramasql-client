/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.core.metadata.MetadataReader;

/**
 * Ciò che il modello ER chiede alla finestra principale. Il modello vive <b>senza</b> connessione (si apre, si
 * modifica, si salva, si esporta); la connessione serve solo ad «Aggiorna dal database» e ad aprire l'editor di una
 * tabella.
 */
public interface ErContext {

    /** Il lettore dei metadati della connessione aperta, se ce n'è una; {@code null} senza connessione. */
    MetadataReader reader();

    /**
     * Il server collegato ({@code host:porta} del profilo), o {@code null} senza connessione: un modello si aggiorna e
     * apre le tabelle solo sul server da cui viene.
     */
    String serverAddress();

    /** Chiede l'etichetta di una relazione logica; {@code null} = annullato. */
    String askLabel(String relationship, String current);

    /** Apre l'editor della tabella nella finestra principale (se connessi). */
    void openTableEditor(String catalog, String table);

    /** Scelta dei file (salva, apri, PNG) e conferme. */
    FilePrompts files();

    /** Domanda alla chiusura con modifiche non salvate: salva, scarta o resta. */
    enum CloseChoice { SAVE, DISCARD, STAY }

    CloseChoice askSaveOnClose(String modelName);
}
