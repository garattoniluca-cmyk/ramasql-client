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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Archivio dei <b>sorgenti originali</b> delle viste create con il client: livello 1 della riapertura di una vista
 * ({@code FEASIBILITY.md} F-06, {@code DESIGN.md} §3.8). Il server non conserva il testo scritto dall'utente ma una sua
 * riscrittura; qui si tiene la SELECT com'era, insieme alla definizione che il server ha restituito subito dopo il
 * salvataggio. Riaprendo la vista, il sorgente vale <b>solo se la definizione sul server è ancora quella</b>: se
 * qualcuno l'ha cambiata da un altro programma, il sorgente è vecchio e non si usa (si passa al livello 2).
 *
 * <p>File {@code viste.json} nella cartella dei dati dell'utente ({@link AppData}), con {@code formatVersion}.
 * Chiave: indirizzo del profilo ({@code utente@host:porta}, come nel profilo), catalogo e nome della vista, senza distinzione tra maiuscole e minuscole.
 * Un file illeggibile si mette da parte e si riparte vuoti: perdere l'archivio fa solo ripiegare sul livello 2.
 */
public final class ViewSourceStore {

    public static final String FILE_NAME = "viste.json";
    public static final int FORMAT_VERSION = 1;

    /**
     * Una vista salvata dal client.
     *
     * @param server           indirizzo del profilo di connessione ({@code utente@host:porta})
     * @param catalog          catalogo della vista
     * @param view             nome della vista
     * @param source           la SELECT scritta dall'utente (o prodotta dal query builder)
     * @param serverDefinition {@code VIEW_DEFINITION} riletta dal server subito dopo il salvataggio
     */
    public record Entry(String server, String catalog, String view, String source, String serverDefinition) {
        public Entry {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(view, "view");
            source = source == null ? "" : source;
            serverDefinition = serverDefinition == null ? "" : serverDefinition;
        }

        boolean sameKey(String otherServer, String otherCatalog, String otherView) {
            return server.equalsIgnoreCase(otherServer) && Objects.equals(lower(catalog), lower(otherCatalog))
                    && view.equalsIgnoreCase(otherView);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SourcesFile(int formatVersion, List<Entry> views) {
    }

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    /** Apre l'archivio nella cartella indicata; un file illeggibile viene messo da parte (mai eccezioni per questo). */
    public ViewSourceStore(Path dataDirectory) {
        this.file = dataDirectory.resolve(FILE_NAME);
        if (Files.exists(file)) {
            try {
                SourcesFile read = JsonFiles.read(file, SourcesFile.class);
                if (read != null && read.views() != null) {
                    for (Entry e : read.views()) {
                        if (e != null) {
                            entries.add(e);
                        }
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                try {
                    JsonFiles.setAside(file);
                } catch (IOException ignored) {
                    // resta dov'è: verrà riscritto al primo salvataggio
                }
            }
        }
    }

    public Path file() {
        return file;
    }

    /** Registra (o sostituisce) il sorgente di una vista e riscrive il file. */
    public synchronized void save(Entry entry) throws IOException {
        entries.removeIf(e -> e.sameKey(entry.server(), entry.catalog(), entry.view()));
        entries.add(entry);
        write();
    }

    /** Dimentica una vista (eliminata dal client). */
    public synchronized void remove(String server, String catalog, String view) throws IOException {
        if (entries.removeIf(e -> e.sameKey(server, catalog, view))) {
            write();
        }
    }

    /** Dimentica tutte le viste di un catalogo (eliminato dal client con {@code DROP DATABASE}, {@code BUG-027}). */
    public synchronized void removeCatalog(String server, String catalog) throws IOException {
        if (entries.removeIf(e -> e.server().equalsIgnoreCase(server)
                && Objects.equals(lower(e.catalog()), lower(catalog)))) {
            write();
        }
    }

    public synchronized Optional<Entry> find(String server, String catalog, String view) {
        return entries.stream().filter(e -> e.sameKey(server, catalog, view)).findFirst();
    }

    /**
     * Il sorgente da usare per riaprire la vista, <b>solo se</b> la definizione attuale sul server coincide con quella
     * registrata al salvataggio (a meno degli spazi ai bordi); altrimenti vuoto.
     */
    public synchronized Optional<String> sourceFor(String server, String catalog, String view,
            String currentServerDefinition) {
        String current = currentServerDefinition == null ? "" : currentServerDefinition.strip();
        if (current.isEmpty()) {
            return Optional.empty();   // senza definizione (es. utente senza SHOW VIEW) non si può confrontare
        }
        return find(server, catalog, view).filter(e -> e.serverDefinition().strip().equals(current))
                .map(Entry::source);
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    private void write() throws IOException {
        JsonFiles.write(file, new SourcesFile(FORMAT_VERSION, List.copyOf(entries)));
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
