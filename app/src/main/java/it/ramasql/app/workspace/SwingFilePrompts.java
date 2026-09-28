/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.workspace;

import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import it.ramasql.app.Texts;

/** Le finestre vere per scegliere i file, nella cartella di lavoro delle impostazioni. */
public final class SwingFilePrompts implements FilePrompts {

    private final Supplier<Window> owner;
    private final Supplier<String> workDirectory;

    public SwingFilePrompts(Supplier<Window> owner, Supplier<String> workDirectory) {
        this.owner = owner;
        this.workDirectory = workDirectory;
    }

    private JFileChooser chooser(Purpose purpose) {
        JFileChooser chooser = new JFileChooser(workDirectory.get());
        chooser.setDialogTitle(Texts.get(purpose.key() + ".title"));
        chooser.setFileFilter(new FileNameExtensionFilter(Texts.get(purpose.key() + ".filter"), purpose.extensions()));
        return chooser;
    }

    @Override
    public Path chooseToOpen(Purpose purpose) {
        JFileChooser chooser = chooser(purpose);
        if (chooser.showOpenDialog(owner.get()) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    @Override
    public Path chooseToSave(Purpose purpose, String suggestedName) {
        JFileChooser chooser = chooser(purpose);
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        if (chooser.showSaveDialog(owner.get()) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path chosen = chooser.getSelectedFile().toPath();
        if (!chosen.getFileName().toString().contains(".")) {
            chosen = chosen.resolveSibling(chosen.getFileName() + "." + purpose.extensions()[0]);
        }
        if (Files.exists(chosen) && !confirm(Texts.get(purpose.key() + ".title"),
                Texts.get("file.overwrite.message", chosen.getFileName()), Texts.get("file.overwrite.confirm"))) {
            return null;
        }
        return chosen;
    }

    @Override
    public boolean confirm(String title, String message, String confirmLabel) {
        Object[] options = {confirmLabel, Texts.get("dialog.cancel")};
        int choice = JOptionPane.showOptionDialog(owner.get(), message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    @Override
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(owner.get(), message, title, JOptionPane.ERROR_MESSAGE);
    }
}
