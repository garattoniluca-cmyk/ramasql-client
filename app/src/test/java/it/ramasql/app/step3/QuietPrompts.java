/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import it.ramasql.app.Prompts;
import it.ramasql.app.connection.ConnectionController;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;

/** {@link Prompts} dei test dello Step 3: dà la password del server di test e registra avvisi ed errori. */
final class QuietPrompts implements Prompts {

    private final Step3Server server;
    AppSettings nextSettings;
    final List<String> errors = new ArrayList<>();

    QuietPrompts(Step3Server server) {
        this.server = server;
    }

    @Override
    public char[] askPassword(ConnectionProfile profile) {
        return server.password();
    }

    @Override
    public boolean confirm(String title, String message, String confirmLabel) {
        return true;
    }

    @Override
    public void showInfo(String title, String message) {
        // niente
    }

    @Override
    public void showError(String title, String message) {
        errors.add(title + " | " + message);
    }

    @Override
    public void showConnectionError(ConnectionProfile profile, ConnectionFailure failure) {
        errors.add("connessione: " + failure);
    }

    @Override
    public ConnectionProfile editProfile(ConnectionProfile initial, ConnectionController controller) {
        return null;
    }

    @Override
    public AppSettings editSettings(AppSettings current) {
        return nextSettings;
    }

    @Override
    public Path chooseFileToOpen(String title) {
        return null;
    }

    @Override
    public Path chooseFileToSave(String title, String suggestedName) {
        return null;
    }
}
