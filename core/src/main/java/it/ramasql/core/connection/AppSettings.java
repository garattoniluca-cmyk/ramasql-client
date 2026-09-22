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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JacksonException;

import it.ramasql.core.CoreMessages;

/**
 * Le impostazioni del programma: <strong>esattamente quattro voci</strong> ({@code DESIGN.md} §3.12), salvate in
 * {@code impostazioni.json} nella cartella dei dati dell'utente.
 *
 * @param language      lingua dell'interfaccia (codice ISO: {@code it}, {@code en})
 * @param fontSize      dimensione del carattere di tutta l'interfaccia, in punti
 * @param rowLimit      numero massimo di righe lette da una query (come «Limit Rows» di Workbench)
 * @param workDirectory cartella proposta per aprire e salvare file
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppSettings(String language, int fontSize, int rowLimit, String workDirectory) {

    public static final String FILE_NAME = "impostazioni.json";
    public static final int DEFAULT_FONT_SIZE = 13;
    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 28;
    public static final int DEFAULT_ROW_LIMIT = 1000;
    public static final int MAX_ROW_LIMIT = 1_000_000;

    public AppSettings {
        language = language == null || language.isBlank() ? "it" : language.strip();
        fontSize = fontSize <= 0 ? DEFAULT_FONT_SIZE : Math.clamp(fontSize, MIN_FONT_SIZE, MAX_FONT_SIZE);
        rowLimit = rowLimit <= 0 ? DEFAULT_ROW_LIMIT : Math.min(rowLimit, MAX_ROW_LIMIT);
        workDirectory = workDirectory == null || workDirectory.isBlank() ? defaultWorkDirectory() : workDirectory.strip();
    }

    public static AppSettings defaults() {
        return new AppSettings(null, 0, 0, null);
    }

    public AppSettings withFontSize(int newSize) {
        return new AppSettings(language, newSize, rowLimit, workDirectory);
    }

    /**
     * Esito della lettura all'avvio.
     *
     * @param settings     le impostazioni da usare (predefinite se il file non si leggeva)
     * @param problem      perché il file non si è potuto leggere; {@code null} = tutto bene
     * @param setAsideCopy dove è stato messo da parte il file illeggibile ({@code null} se non si è potuto spostare)
     */
    public record Loading(AppSettings settings, String problem, Path setAsideCopy) {
        public boolean hasProblem() {
            return problem != null;
        }
    }

    /**
     * Legge le impostazioni dalla cartella indicata, <strong>senza effetti</strong>: file mancante o illeggibile =
     * valori predefiniti. Serve prima di aprire le finestre (per la lingua); all'avvio vero si usa
     * {@link #loadRecovering(Path)}.
     */
    public static AppSettings load(Path dataDirectory) {
        try {
            return readOrDefaults(dataDirectory.resolve(FILE_NAME));
        } catch (IOException e) {
            return defaults();
        }
    }

    /**
     * Lettura all'avvio: se il file è rovinato si usano i valori predefiniti, il file si mette da parte con un nome
     * univoco (così un salvataggio successivo non cancella ciò che conteneva) e si riporta il problema, da mostrare.
     */
    public static Loading loadRecovering(Path dataDirectory) {
        Path file = dataDirectory.resolve(FILE_NAME);
        try {
            return new Loading(readOrDefaults(file), null, null);
        } catch (IOException unreadable) {
            Path aside;
            try {
                aside = JsonFiles.setAside(file);
            } catch (IOException cannotMove) {
                aside = null;
            }
            String problem = unreadable instanceof JacksonException
                    ? CoreMessages.get("settings.file.invalid", FILE_NAME)
                    : CoreMessages.get("settings.file.unreadable", FILE_NAME, unreadable.getClass().getSimpleName());
            return new Loading(defaults(), problem, aside);
        }
    }

    private static AppSettings readOrDefaults(Path file) throws IOException {
        if (!Files.exists(file)) {
            return defaults();
        }
        AppSettings read = JsonFiles.read(file, AppSettings.class);
        return read == null ? defaults() : read;
    }

    public void save(Path dataDirectory) throws IOException {
        JsonFiles.write(dataDirectory.resolve(FILE_NAME), this);
    }

    private static String defaultWorkDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        Path documents = home.resolve("Documents");
        return (Files.isDirectory(documents) ? documents : home).toString();
    }
}
