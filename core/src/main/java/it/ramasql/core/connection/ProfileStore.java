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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JacksonException;

import it.ramasql.core.CoreMessages;

/**
 * Archivio dei profili di connessione: {@code connessioni.json} nella cartella dei dati dell'utente.
 * Lo stesso formato serve per l'esportazione ({@code connessioni-3A.json}) che il docente distribuisce.
 * Il file <strong>non contiene mai password</strong>: {@link ConnectionProfile} non ha un campo per tenerla,
 * e in lettura un eventuale campo in più viene ignorato e non riscritto.
 * <p>Ogni modifica si prepara su una <strong>copia</strong> dell'elenco, si salva, e solo se il salvataggio riesce
 * diventa l'elenco in memoria: memoria e disco non divergono mai.
 */
public final class ProfileStore {

    public static final String FILE_NAME = "connessioni.json";
    public static final int FORMAT_VERSION = 1;

    /** Contenuto del file. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfilesFile(int formatVersion, List<ConnectionProfile> profiles) {
    }

    /** Esito di un'importazione: profili aggiunti e profili aggiornati (stesso nome). */
    public record ImportResult(int added, int updated) {
        public int total() {
            return added + updated;
        }
    }

    /**
     * Esito dell'apertura all'avvio.
     *
     * @param store        l'archivio da usare (sempre presente)
     * @param problem      perché il file non si è potuto leggere, in italiano; {@code null} = tutto bene
     * @param setAsideCopy dove è stato messo da parte il file illeggibile; {@code null} se non è stato possibile
     *                     spostarlo (allora il file resta dov'è e l'elenco vuoto vive solo in memoria finché
     *                     l'utente non salva un profilo)
     */
    public record Opening(ProfileStore store, String problem, Path setAsideCopy) {
        public boolean hasProblem() {
            return problem != null;
        }
    }

    private final Path file;
    private final List<ConnectionProfile> profiles = new ArrayList<>();

    /** Apre l'archivio nella cartella indicata e carica i profili presenti (nessun file = nessun profilo). */
    public ProfileStore(Path dataDirectory) throws IOException {
        this.file = dataDirectory.resolve(FILE_NAME);
        if (Files.exists(file)) {
            profiles.addAll(readFile(file));
        }
    }

    /** Archivio vuoto che non legge il file: lo riscrive solo al primo salvataggio. */
    private ProfileStore(Path dataDirectory, boolean ignored) {
        this.file = dataDirectory.resolve(FILE_NAME);
    }

    /**
     * Apertura all'avvio, che <strong>non fallisce mai</strong>: se il file non si legge lo si mette da parte con un
     * nome univoco e si riparte da un elenco vuoto; se nemmeno spostarlo è possibile, l'elenco vuoto resta solo in
     * memoria e il file su disco non si tocca finché l'utente non salva.
     */
    public static Opening openRecovering(Path dataDirectory) {
        try {
            return new Opening(new ProfileStore(dataDirectory), null, null);
        } catch (IOException unreadable) {
            String problem = unreadable.getMessage();
            Path aside;
            try {
                aside = JsonFiles.setAside(dataDirectory.resolve(FILE_NAME));
            } catch (IOException cannotMove) {
                aside = null;
            }
            return new Opening(new ProfileStore(dataDirectory, true), problem, aside);
        }
    }

    public Path file() {
        return file;
    }

    public synchronized List<ConnectionProfile> profiles() {
        return List.copyOf(profiles);
    }

    public synchronized ConnectionProfile byId(String id) {
        return profiles.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    public synchronized void add(ConnectionProfile profile) throws IOException {
        List<ConnectionProfile> next = new ArrayList<>(profiles);
        next.add(profile);
        commit(next);
    }

    /** Sostituisce il profilo con lo stesso identificativo. */
    public synchronized void update(ConnectionProfile profile) throws IOException {
        int i = indexOfId(profiles, profile.id());
        if (i < 0) {
            throw new IllegalArgumentException("profilo sconosciuto: " + profile.id());
        }
        List<ConnectionProfile> next = new ArrayList<>(profiles);
        next.set(i, profile);
        commit(next);
    }

    public synchronized void remove(String id) throws IOException {
        int i = indexOfId(profiles, id);
        if (i >= 0) {
            List<ConnectionProfile> next = new ArrayList<>(profiles);
            next.remove(i);
            commit(next);
        }
    }

    /** Un nome non ancora usato: «nome», poi «nome (2)», «nome (3)»… */
    public synchronized String freeName(String wanted) {
        String candidate = wanted;
        for (int n = 2; indexOfName(profiles, candidate) >= 0; n++) {
            candidate = wanted + " (" + n + ")";
        }
        return candidate;
    }

    /** Vero se un ALTRO profilo (identificativo diverso) usa già questo nome. */
    public synchronized boolean nameTakenByOther(String name, String ownId) {
        int i = indexOfName(profiles, name);
        return i >= 0 && !profiles.get(i).id().equals(ownId);
    }

    /** Esporta tutti i profili nel file indicato (l'ultimo server visto non si esporta: vale solo su questo PC). */
    public synchronized void exportTo(Path target) throws IOException {
        List<ConnectionProfile> portable = profiles.stream()
                .map(p -> new ConnectionProfile(p.id(), p.name(), p.host(), p.port(), p.user(), p.defaultCatalog(),
                        p.note(), null, null))
                .toList();
        JsonFiles.write(target, new ProfilesFile(FORMAT_VERSION, portable));
    }

    /**
     * I nomi dei profili di questo archivio che un'importazione dal file indicato <strong>sostituirebbe</strong>
     * (stesso nome, senza badare alle maiuscole), nell'ordine dell'elenco. Serve per chiedere conferma prima.
     */
    public synchronized List<String> namesReplacedBy(Path source) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (ConnectionProfile incoming : readFile(source)) {
            if (importable(incoming)) {
                int i = indexOfName(profiles, incoming.name());
                if (i >= 0) {
                    names.add(profiles.get(i).name());
                }
            }
        }
        return List.copyOf(names);
    }

    /**
     * Importa i profili di un file esportato, <strong>unendoli per nome</strong>: un profilo con un nome già
     * presente aggiorna quello esistente (che conserva identificativo e ultimo server visto), gli altri si aggiungono.
     * Chi chiama chiede prima conferma con {@link #namesReplacedBy(Path)}.
     */
    public synchronized ImportResult importFrom(Path source) throws IOException {
        List<ConnectionProfile> next = new ArrayList<>(profiles);
        int added = 0;
        int updated = 0;
        for (ConnectionProfile incoming : readFile(source)) {
            if (!importable(incoming)) {
                continue;
            }
            int i = indexOfName(next, incoming.name());
            if (i >= 0) {
                ConnectionProfile old = next.get(i);
                next.set(i, old.withDetails(incoming.name(), incoming.host(), incoming.port(), incoming.user(),
                        incoming.defaultCatalog(), incoming.note()));
                updated++;
            } else {
                // identificativo nuovo se quello del file è già usato qui da un profilo con un altro nome
                next.add(indexOfId(next, incoming.id()) >= 0 ? incoming.duplicate(incoming.name()) : incoming);
                added++;
            }
        }
        commit(next);
        return new ImportResult(added, updated);
    }

    private static boolean importable(ConnectionProfile incoming) {
        return !incoming.name().isEmpty() && !incoming.host().isEmpty();
    }

    /** Salva l'elenco nuovo; solo se il salvataggio riesce diventa l'elenco in memoria. */
    private void commit(List<ConnectionProfile> next) throws IOException {
        List<ConnectionProfile> snapshot = List.copyOf(next);
        JsonFiles.write(file, new ProfilesFile(FORMAT_VERSION, snapshot));
        profiles.clear();
        profiles.addAll(snapshot);
    }

    private static List<ConnectionProfile> readFile(Path source) throws IOException {
        ProfilesFile content;
        try {
            content = JsonFiles.read(source, ProfilesFile.class);
        } catch (JacksonException e) {
            throw new IOException(CoreMessages.get("profiles.file.invalid", source.getFileName()), e);
        } catch (IOException e) {
            throw new IOException(CoreMessages.get("profiles.file.unreadable", source.getFileName(),
                    e.getClass().getSimpleName()), e);
        }
        if (content == null || content.profiles() == null) {
            throw new IOException(CoreMessages.get("profiles.file.invalid", source.getFileName()));
        }
        if (content.formatVersion() > FORMAT_VERSION) {
            throw new IOException(CoreMessages.get("profiles.file.newer", source.getFileName()));
        }
        return content.profiles().stream().filter(Objects::nonNull).toList();
    }

    private static int indexOfId(List<ConnectionProfile> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfName(List<ConnectionProfile> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
