/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.connection;

import java.util.List;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;

/** Ciò che il {@link ConnectionController} chiede alla finestra principale. Sempre chiamato sull'EDT. */
public interface ShellView {

    /** Nessuna connessione: schermata iniziale con le tessere dei profili; le schede della connessione precedente si chiudono. */
    void showHome(List<ConnectionProfile> profiles);

    /** Tentativo in corso: indicatore di attesa e pulsante Annulla; le schede della connessione precedente si chiudono. */
    void showConnecting(ConnectionProfile profile);

    /** Connessione aperta: area di lavoro e barra di stato aggiornata. {@code catalog} può essere {@code null}. */
    void showConnected(Session session, String catalog);

    /** Quante schede sono aperte nell'area di lavoro (chiuderle chiede conferma). */
    int openTabCount();
}
