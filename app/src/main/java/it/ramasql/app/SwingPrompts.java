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

import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import it.ramasql.app.connection.ConnectionController;
import it.ramasql.app.connection.ConnectionErrorDialog;
import it.ramasql.app.connection.PasswordDialog;
import it.ramasql.app.connection.ProfileDialog;
import it.ramasql.app.settings.SettingsDialog;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;

/** Le finestre modali vere del programma. */
public final class SwingPrompts implements Prompts {

    private final Supplier<Window> owner;
    private final Supplier<String> workDirectory;

    /**
     * @param owner         finestra sopra cui aprire i dialoghi
     * @param workDirectory cartella di lavoro corrente (impostazioni), proposta per aprire e salvare file
     */
    public SwingPrompts(Supplier<Window> owner, Supplier<String> workDirectory) {
        this.owner = owner;
        this.workDirectory = workDirectory;
    }

    @Override
    public char[] askPassword(ConnectionProfile profile) {
        return new PasswordDialog(owner.get(), profile).showModal();
    }

    @Override
    public boolean confirm(String title, String message, String confirmLabel) {
        Object[] options = {confirmLabel, Texts.get("dialog.cancel")};
        int choice = JOptionPane.showOptionDialog(owner.get(), message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    @Override
    public void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(owner.get(), message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(owner.get(), message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showConnectionError(ConnectionProfile profile, ConnectionFailure failure) {
        new ConnectionErrorDialog(owner.get(), profile, failure).setVisible(true);
    }

    @Override
    public ConnectionProfile editProfile(ConnectionProfile initial, ConnectionController controller) {
        return new ProfileDialog(owner.get(), initial, controller).showModal();
    }

    @Override
    public AppSettings editSettings(AppSettings current) {
        return new SettingsDialog(owner.get(), current).showModal();
    }

    @Override
    public Path chooseFileToOpen(String title) {
        JFileChooser chooser = jsonChooser(title);
        return chooser.showOpenDialog(owner.get()) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }

    @Override
    public Path chooseFileToSave(String title, String suggestedName) {
        JFileChooser chooser = jsonChooser(title);
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        if (chooser.showSaveDialog(owner.get()) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path chosen = chooser.getSelectedFile().toPath();
        if (!chosen.getFileName().toString().contains(".")) {
            chosen = chosen.resolveSibling(chosen.getFileName() + ".json");
        }
        if (Files.exists(chosen) && !confirm(title, Texts.get("file.overwrite.message", chosen.getFileName()),
                Texts.get("file.overwrite.confirm"))) {
            return null;
        }
        return chosen;
    }

    private JFileChooser jsonChooser(String title) {
        JFileChooser chooser = new JFileChooser(workDirectory.get());
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(Texts.get("file.json.filter"), "json"));
        return chooser;
    }
}
