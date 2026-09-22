/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.it.ItServers;

/** Dai server dei test d'integrazione ai profili di connessione del client (la password non è mai nel profilo). */
final class Step2Servers {

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");

    private Step2Servers() {
    }

    static ConnectionProfile profile(ItServers server, String catalog) {
        Matcher m = HOST_PORT.matcher(server.url());
        if (!m.matches()) {
            throw new AssertionError("URL del server di test non riconosciuto: " + server.url());
        }
        return ConnectionProfile.create("Test " + server.label(), m.group(1), Integer.parseInt(m.group(2)),
                server.user(), catalog, "");
    }

    /** Password dell'utente di test: dalla variabile d'ambiente, mai stampata; se manca il test fallisce. */
    static char[] password(ItServers server) {
        String value = System.getenv("RAMASQL_IT_" + server.name() + "_PASSWORD");
        if (value == null) {
            throw new AssertionError("Manca la variabile d'ambiente RAMASQL_IT_" + server.name()
                    + "_PASSWORD: i test d'integrazione non si saltano.");
        }
        return value.toCharArray();
    }
}
