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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Le <strong>query interne</strong> della sessione: le poche letture di servizio che il client fa per conto
 * proprio (versione del server, identificativo della connessione, catalogo corrente) e {@code KILL QUERY}.
 * <p>È l'unico punto di {@code core.connection} che esegue SQL: tutto ciò che l'utente chiede passa invece da
 * {@code SqlExecutor} ({@code ARCHITECTURE.md} §4). Sono tutte istruzioni fisse, senza testo dell'utente, e
 * nessuna modifica dati o apre transazioni.
 */
final class InternalQueries {

    static final String SELECT_VERSION = "SELECT VERSION()";
    static final String SELECT_CONNECTION_ID = "SELECT CONNECTION_ID()";
    static final String SELECT_DATABASE = "SELECT DATABASE()";
    static final String KILL_QUERY = "KILL QUERY ";

    private InternalQueries() {
    }

    static String version(Connection connection) throws SQLException {
        return singleValue(connection, SELECT_VERSION);
    }

    static long connectionId(Connection connection) throws SQLException {
        return Long.parseLong(singleValue(connection, SELECT_CONNECTION_ID));
    }

    /** Catalogo corrente, oppure {@code null} se la connessione non ne ha scelto uno. */
    static String currentCatalog(Connection connection) throws SQLException {
        return singleValue(connection, SELECT_DATABASE);
    }

    /** Interrompe l'istruzione in corso sulla connessione indicata (l'identificativo è un numero: niente testo libero). */
    static void killQuery(Connection serviceConnection, long connectionId) throws SQLException {
        try (Statement st = serviceConnection.createStatement()) {
            st.execute(KILL_QUERY + connectionId);
        }
    }

    private static String singleValue(Connection connection, String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
