/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;

import it.ramasql.app.Texts;

/** Le finestre modali vere della griglia. */
public final class SwingGridPrompts implements GridPrompts {

    private final Supplier<Component> owner;
    private final Supplier<String> workDirectory;

    /**
     * @param owner         componente sopra cui aprire i dialoghi
     * @param workDirectory cartella proposta per salvare i file (la cartella di lavoro delle impostazioni)
     */
    public SwingGridPrompts(Supplier<Component> owner, Supplier<String> workDirectory) {
        this.owner = owner;
        this.workDirectory = workDirectory;
    }

    @Override
    public boolean confirm(String title, String message, String confirmLabel) {
        Object[] options = {confirmLabel, Texts.get("dialog.cancel")};
        int choice = JOptionPane.showOptionDialog(owner.get(), message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    @Override
    public String editLongText(String title, String initialText) {
        JTextArea area = new JTextArea(initialText == null ? "" : initialText);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(560, 320));
        Object[] options = {Texts.get("grid.longText.ok"), Texts.get("dialog.cancel")};
        int choice = JOptionPane.showOptionDialog(owner.get(), scroll, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        return choice == 0 ? area.getText() : null;
    }

    @Override
    public Path chooseCsvFile(String suggestedName) {
        JFileChooser chooser = new JFileChooser(workDirectory.get());
        chooser.setDialogTitle(Texts.get("grid.export.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(Texts.get("grid.export.filter"), "csv"));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        if (chooser.showSaveDialog(owner.get()) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path chosen = chooser.getSelectedFile().toPath();
        if (!chosen.getFileName().toString().contains(".")) {
            chosen = chosen.resolveSibling(chosen.getFileName() + ".csv");
        }
        if (Files.exists(chosen) && !confirm(Texts.get("grid.export.title"),
                Texts.get("file.overwrite.message", chosen.getFileName()), Texts.get("file.overwrite.confirm"))) {
            return null;
        }
        return chosen;
    }

    @Override
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(owner.get(), message, title, JOptionPane.ERROR_MESSAGE);
    }
}
