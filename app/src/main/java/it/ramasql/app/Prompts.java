/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app;

import java.nio.file.Path;

import it.ramasql.app.connection.ConnectionController;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;

/**
 * Tutte le domande e gli avvisi in finestra modale passano da qui: la logica (i controller) non apre mai una
 * finestra per conto suo. Nel programma l'implementazione è {@link SwingPrompts}; nei test è una finta che
 * risponde da sola e registra ciò che sarebbe stato mostrato.
 */
public interface Prompts {

    /** Chiede la password per il profilo. {@code null} = l'utente ha annullato. L'array va azzerato da chi lo riceve. */
    char[] askPassword(ConnectionProfile profile);

    /** Domanda sì/no; {@code confirmLabel} è il testo del pulsante che conferma (es. «Elimina»). */
    boolean confirm(String title, String message, String confirmLabel);

    void showInfo(String title, String message);

    void showError(String title, String message);

    /** Connessione non riuscita: spiegazione in italiano + dettaglio originale del server. */
    void showConnectionError(ConnectionProfile profile, ConnectionFailure failure);

    /** Finestra del profilo (nuovo se {@code initial} è {@code null}). {@code null} = annullato. */
    ConnectionProfile editProfile(ConnectionProfile initial, ConnectionController controller);

    /** Finestra delle impostazioni. {@code null} = annullato. */
    AppSettings editSettings(AppSettings current);

    /** File JSON da aprire; {@code null} = annullato. */
    Path chooseFileToOpen(String title);

    /** File JSON da salvare, con un nome proposto; {@code null} = annullato. */
    Path chooseFileToSave(String title, String suggestedName);
}
