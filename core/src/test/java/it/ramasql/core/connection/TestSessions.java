/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

/** Solo per i test di core: una {@link Session} senza connessioni ({@link Session#detached}), per provare senza server. */
public final class TestSessions {

    private TestSessions() {
    }

    /** Sessione «staccata»: nessuna connessione (la principale è {@code null}). */
    public static Session detached() {
        return Session.detached(ConnectionProfile.create("Prova", "127.0.0.1", 3306, "u", "", ""),
                ServerInfo.parse("11.5.2-MariaDB"), null);
    }
}
