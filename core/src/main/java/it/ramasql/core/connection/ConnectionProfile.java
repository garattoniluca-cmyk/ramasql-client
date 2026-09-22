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

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Profilo di connessione salvato. <strong>Non ha, e non deve mai avere, un campo per la password</strong>:
 * la password si chiede a ogni connessione e vive solo in memoria ({@link Session}).
 *
 * @param id                identificativo stabile (UUID), generato alla creazione
 * @param name              nome mostrato sulla tessera
 * @param port              porta TCP (3306 se non indicata)
 * @param defaultCatalog    catalogo predefinito, facoltativo (vuoto = nessuno)
 * @param lastServerKind    tipo di server visto all'ultima connessione riuscita ({@code null} = mai connesso)
 * @param lastServerVersion testo di {@code SELECT VERSION()} dell'ultima connessione riuscita
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectionProfile(
        String id,
        String name,
        String host,
        int port,
        String user,
        String defaultCatalog,
        String note,
        ServerInfo.ServerKind lastServerKind,
        String lastServerVersion) {

    public static final int DEFAULT_PORT = 3306;

    public ConnectionProfile {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        name = clean(name);
        host = clean(host);
        port = port <= 0 ? DEFAULT_PORT : port;
        user = clean(user);
        defaultCatalog = clean(defaultCatalog);
        note = note == null ? "" : note.strip();
        lastServerVersion = lastServerVersion == null || lastServerVersion.isBlank() ? null : lastServerVersion;
        // il tipo si ricava sempre dal testo della versione: le due voci non possono contraddirsi
        lastServerKind = lastServerVersion == null ? null : ServerInfo.parse(lastServerVersion).kind();
    }

    /** Nuovo profilo, mai connesso. */
    public static ConnectionProfile create(String name, String host, int port, String user, String defaultCatalog,
            String note) {
        return new ConnectionProfile(null, name, host, port, user, defaultCatalog, note, null, null);
    }

    /** Copia con i dati modificabili dall'utente cambiati; identificativo e ultimo server visto restano. */
    public ConnectionProfile withDetails(String newName, String newHost, int newPort, String newUser,
            String newCatalog, String newNote) {
        return new ConnectionProfile(id, newName, newHost, newPort, newUser, newCatalog, newNote, lastServerKind,
                lastServerVersion);
    }

    /** Copia che ricorda il server visto all'ultima connessione riuscita. */
    public ConnectionProfile withLastServer(ServerInfo info) {
        return new ConnectionProfile(id, name, host, port, user, defaultCatalog, note, info.kind(),
                info.versionText());
    }

    /** Copia con un nuovo identificativo e un altro nome (comando «Duplica»). */
    public ConnectionProfile duplicate(String newName) {
        return new ConnectionProfile(null, newName, host, port, user, defaultCatalog, note, null, null);
    }

    /** Server dell'ultima connessione riuscita, oppure {@code null}. */
    public ServerInfo lastServer() {
        return lastServerVersion == null ? null : ServerInfo.parse(lastServerVersion);
    }

    /** «utente@host:porta», come sulla tessera. */
    public String address() {
        return user + "@" + host + ":" + port;
    }

    public boolean hasDefaultCatalog() {
        return !defaultCatalog.isEmpty();
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
