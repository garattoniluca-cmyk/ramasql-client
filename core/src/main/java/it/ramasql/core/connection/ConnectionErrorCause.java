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

/**
 * Le cause di una connessione non riuscita che il client sa distinguere. Ognuna ha un messaggio italiano che
 * dice <em>cosa correggere</em> (chiave {@code connection.error.<NOME>} nei testi di core).
 */
public enum ConnectionErrorCause {
    /** Il nome dell'host non si risolve (DNS). */
    UNKNOWN_HOST,
    /** L'host risponde ma la porta rifiuta la connessione. */
    PORT_CLOSED,
    /** Nessuna risposta entro il tempo massimo: host spento, indirizzo non raggiungibile, firewall. */
    TIMEOUT,
    /** Utente o password errati (errore 1045, 1698). */
    ACCESS_DENIED,
    /** L'utente non ha permessi sul catalogo predefinito (errore 1044). */
    CATALOG_ACCESS_DENIED,
    /** Il catalogo predefinito non esiste (errore 1049). */
    UNKNOWN_CATALOG,
    /** Il server ha esaurito le connessioni (errore 1040, 1203). */
    TOO_MANY_CONNECTIONS,
    /** Il server non accetta connessioni da questo computer (errore 1130). */
    HOST_NOT_ALLOWED,
    /** Il server pretende, o non riesce a stabilire, un canale cifrato SSL/TLS. */
    SSL,
    /** Il metodo di autenticazione dell'utente non è gestibile dal driver. */
    AUTH_PLUGIN,
    /** Tutto il resto: vale il messaggio originale. */
    OTHER
}
