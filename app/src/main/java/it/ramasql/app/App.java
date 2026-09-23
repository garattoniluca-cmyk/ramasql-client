/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app;

import java.nio.file.Path;

import it.ramasql.app.connection.ConnectionController;
import it.ramasql.app.settings.SettingsController;
import it.ramasql.app.workspace.WorkspacePrompts;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ProfileStore;

/**
 * Montaggio del programma: archivio dei profili, impostazioni, controller e finestra principale, a partire da una
 * cartella dei dati e da un {@link Prompts}. Lo usano {@link Main} (con le finestre vere) e i test (con una
 * cartella temporanea e un {@code Prompts} finto). Va chiamato sull'EDT.
 * <p>L'avvio <strong>non fallisce mai</strong> per un file dell'utente rovinato (profili o impostazioni): il file si
 * mette da parte con un nome univoco, si riparte dai valori vuoti/predefiniti e lo si dice in italiano.
 */
public final class App {

    private final MainFrame frame;
    private final ConnectionController connections;
    private final SettingsController settings;

    private App(MainFrame frame, ConnectionController connections, SettingsController settings) {
        this.frame = frame;
        this.connections = connections;
        this.settings = settings;
    }

    /** Con le finestre vere dell'area di lavoro (navigatore, anteprima, pannello SQL). */
    public static App create(Path dataDirectory, Prompts prompts) {
        return create(dataDirectory, prompts, null);
    }

    /**
     * Con le finestre dell'area di lavoro date (i test ne passano una finta).
     *
     * @param workspacePrompts {@code null} = quelle vere ({@code SwingWorkspacePrompts})
     */
    public static App create(Path dataDirectory, Prompts prompts, WorkspacePrompts workspacePrompts) {
        SettingsController settings = new SettingsController(dataDirectory, prompts);
        settings.applyFont();
        ProfileStore.Opening opening = ProfileStore.openRecovering(dataDirectory);
        ConnectionController connections = new ConnectionController(opening.store(), prompts);
        MainFrame frame = new MainFrame(connections, settings, prompts, workspacePrompts);
        connections.attach(frame);
        AppSettings.Loading loading = settings.loading();
        if (loading.hasProblem()) {
            prompts.showError(Texts.get("settings.load.error.title"), loading.setAsideCopy() != null
                    ? Texts.get("settings.load.error.setAside", loading.problem(), loading.setAsideCopy().getFileName())
                    : Texts.get("settings.load.error.kept", loading.problem()));
        }
        if (opening.hasProblem()) {
            prompts.showError(Texts.get("profiles.load.error.title"), opening.setAsideCopy() != null
                    ? Texts.get("profiles.load.error.setAside", opening.problem(), opening.setAsideCopy().getFileName())
                    : Texts.get("profiles.load.error.kept", opening.problem()));
        }
        return new App(frame, connections, settings);
    }

    public MainFrame frame() {
        return frame;
    }

    public ConnectionController connections() {
        return connections;
    }

    public SettingsController settings() {
        return settings;
    }
}
