/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import it.ramasql.app.Prompts;
import it.ramasql.app.connection.ConnectionController;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;

/**
 * {@link Prompts} finto: al posto delle finestre modali (che nei test bloccherebbero) risponde con ciò che il test
 * ha preparato e <strong>registra tutto ciò che sarebbe stato mostrato</strong>. Le password non si registrano mai.
 */
final class FakePrompts implements Prompts {

    /** Un errore di connessione mostrato all'utente. */
    record ShownConnectionError(ConnectionProfile profile, ConnectionFailure failure) {
    }

    /** Risposta alla richiesta di password ({@code null} = l'utente annulla). */
    Function<ConnectionProfile, char[]> password = p -> null;
    boolean confirmAnswer = true;
    ConnectionProfile nextProfile;
    AppSettings nextSettings;
    Path nextOpenFile;
    Path nextSaveFile;

    final List<String> passwordRequests = new ArrayList<>();
    final List<String> confirmations = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final List<ShownConnectionError> connectionErrors = new ArrayList<>();

    @Override
    public char[] askPassword(ConnectionProfile profile) {
        passwordRequests.add(profile.name());
        return password.apply(profile);
    }

    @Override
    public boolean confirm(String title, String message, String confirmLabel) {
        confirmations.add(title + " | " + message + " | [" + confirmLabel + "]");
        return confirmAnswer;
    }

    @Override
    public void showInfo(String title, String message) {
        infos.add(title + " | " + message);
    }

    @Override
    public void showError(String title, String message) {
        errors.add(title + " | " + message);
    }

    @Override
    public void showConnectionError(ConnectionProfile profile, ConnectionFailure failure) {
        connectionErrors.add(new ShownConnectionError(profile, failure));
    }

    @Override
    public ConnectionProfile editProfile(ConnectionProfile initial, ConnectionController controller) {
        return nextProfile;
    }

    @Override
    public AppSettings editSettings(AppSettings current) {
        return nextSettings;
    }

    @Override
    public Path chooseFileToOpen(String title) {
        return nextOpenFile;
    }

    @Override
    public Path chooseFileToSave(String title, String suggestedName) {
        return nextSaveFile;
    }
}
