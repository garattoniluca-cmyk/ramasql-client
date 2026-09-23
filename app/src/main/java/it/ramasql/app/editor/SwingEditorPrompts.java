/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.awt.Component;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import it.ramasql.app.Texts;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;

/** Le finestre modali vere dell'editor SQL. */
public final class SwingEditorPrompts implements EditorPrompts {

    private final Supplier<Component> owner;
    private final Supplier<String> workDirectory;
    /** Mostra la finestra di conferma e ne restituisce la scelta; i test la pilotano senza mostrarla. */
    Function<PreviewDialog, PreviewDialog.Decision> presenter = PreviewDialog::showModal;

    /**
     * @param owner         componente sopra cui aprire i dialoghi
     * @param workDirectory cartella di lavoro (impostazioni), proposta per aprire e salvare
     */
    public SwingEditorPrompts(Supplier<Component> owner, Supplier<String> workDirectory) {
        this.owner = owner;
        this.workDirectory = workDirectory;
    }

    /**
     * La stessa finestra di conferma rafforzata del navigatore ({@link PreviewDialog}, DESIGN-SYSTEM §3.7): l'SQL delle
     * istruzioni pericolose, la fascia rossa «Stai per …» e il campo in cui riscrivere il nome dell'oggetto (o la parola
     * di conferma); il pulsante resta disabilitato finché il testo non coincide, e Invio non conferma. «Copia
     * nell'editor» non serve (l'SQL è già nell'editor): vale come Annulla.
     */
    @Override
    public String confirmDestructive(ConfirmationPolicy.Confirmation confirmation, SqlScript dangerous) {
        Component parent = owner.get();
        Window window = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        PreviewDialog dialog = new PreviewDialog(window, dangerous, confirmation);
        dialog.copyButton().setVisible(false);
        PreviewDialog.Decision decision = presenter.apply(dialog);
        String typed = dialog.confirmationField().getText();
        return decision == PreviewDialog.Decision.EXECUTE && confirmation.accepts(typed) ? typed : null;
    }

    @Override
    public SaveChoice askSaveChanges(String documentName) {
        Object[] options = {Texts.get("editor.close.save"), Texts.get("editor.close.discard"),
            Texts.get("dialog.cancel")};
        int choice = JOptionPane.showOptionDialog(owner.get(), Texts.get("editor.close.message", documentName),
                Texts.get("editor.close.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                options, options[0]);
        return switch (choice) {
            case 0 -> SaveChoice.SAVE;
            case 1 -> SaveChoice.DISCARD;
            default -> SaveChoice.CANCEL;
        };
    }

    @Override
    public Path chooseSqlFileToOpen() {
        JFileChooser chooser = chooser(Texts.get("editor.file.open.title"));
        return chooser.showOpenDialog(owner.get()) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }

    @Override
    public Path chooseSqlFileToSave(String suggestedName) {
        JFileChooser chooser = chooser(Texts.get("editor.file.save.title"));
        chooser.setSelectedFile(Path.of(workDirectory.get() == null ? "." : workDirectory.get())
                .resolve(suggestedName).toFile());
        if (chooser.showSaveDialog(owner.get()) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path chosen = SqlEditor.withSqlExtension(chooser.getSelectedFile().toPath());
        if (Files.exists(chosen)) {
            Object[] options = {Texts.get("file.overwrite.confirm"), Texts.get("dialog.cancel")};
            int choice = JOptionPane.showOptionDialog(owner.get(),
                    Texts.get("file.overwrite.message", chosen.getFileName()), Texts.get("editor.file.save.title"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
            if (choice != 0) {
                return null;
            }
        }
        return chosen;
    }

    @Override
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(owner.get(), message, title, JOptionPane.ERROR_MESSAGE);
    }

    private JFileChooser chooser(String title) {
        JFileChooser chooser = new JFileChooser(workDirectory.get());
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(Texts.get("editor.file.filter"), "sql"));
        return chooser;
    }
}
