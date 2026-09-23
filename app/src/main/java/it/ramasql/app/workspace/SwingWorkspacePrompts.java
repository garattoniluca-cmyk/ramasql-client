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

import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import it.ramasql.app.Texts;
import it.ramasql.app.navigator.CreateCatalogDialog;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.app.pipeline.ShowCreateDialog;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.CollationInfo;

/** Le finestre vere dell'area di lavoro. */
public final class SwingWorkspacePrompts implements WorkspacePrompts {

    private final Supplier<Window> owner;
    private final Supplier<String> workDirectory;

    public SwingWorkspacePrompts(Supplier<Window> owner, Supplier<String> workDirectory) {
        this.owner = owner;
        this.workDirectory = workDirectory;
    }

    @Override
    public PreviewDialog.Decision preview(SqlScript script, ConfirmationPolicy.Confirmation confirmation) {
        return new PreviewDialog(owner.get(), script, confirmation).showModal();
    }

    @Override
    public CreateCatalogDialog.Choice askNewCatalog(List<CollationInfo> collations, String charset, String collation) {
        return new CreateCatalogDialog(owner.get(), collations, charset, collation).showModal();
    }

    @Override
    public it.ramasql.app.grid.GridPrompts gridPrompts() {
        return new it.ramasql.app.grid.SwingGridPrompts(owner::get, workDirectory);
    }

    @Override
    public it.ramasql.app.editor.EditorPrompts editorPrompts() {
        return new it.ramasql.app.editor.SwingEditorPrompts(owner::get, workDirectory);
    }

    @Override
    public it.ramasql.app.tableeditor.TableEditorPrompts tableEditorPrompts() {
        return new it.ramasql.app.tableeditor.SwingTableEditorPrompts(owner::get);
    }

    @Override
    public PendingChoice askPendingOnClose(String question) {
        String[] options = {Texts.get("grid.close.confirm"), Texts.get("grid.close.discard"),
            Texts.get("grid.close.stay")};
        int choice = JOptionPane.showOptionDialog(owner.get(), question, Texts.get("grid.close.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
        return switch (choice) {
            case 0 -> PendingChoice.CONFIRM;
            case 1 -> PendingChoice.DISCARD;
            default -> PendingChoice.STAY;   // anche la X della finestra: non si perde nulla
        };
    }

    @Override
    public String askNewTableName(String catalog, String currentName) {
        Object answer = JOptionPane.showInputDialog(owner.get(), Texts.get("nav.rename.message", currentName),
                Texts.get("nav.rename.title"), JOptionPane.PLAIN_MESSAGE, null, null, currentName);
        return answer == null ? null : answer.toString();
    }

    @Override
    public void showCreateSql(String title, String statement, String sql) {
        new ShowCreateDialog(owner.get(), title, statement, sql, this::copyToClipboard).setVisible(true);
    }

    @Override
    public LogExport chooseLogExport(String suggestedName, String candidateCatalog) {
        JFileChooser chooser = new JFileChooser(workDirectory.get());
        chooser.setDialogTitle(Texts.get("panel.log.export.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(Texts.get("panel.log.export.filter"), "sql"));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        JCheckBox unqualify = null;
        if (candidateCatalog != null) {
            unqualify = new JCheckBox(Texts.get("panel.log.export.unqualify", candidateCatalog));
            unqualify.setToolTipText(Texts.get("panel.log.export.unqualify.tooltip"));
            chooser.setAccessory(unqualify);
        }
        if (chooser.showSaveDialog(owner.get()) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path chosen = chooser.getSelectedFile().toPath();
        if (!chosen.getFileName().toString().contains(".")) {
            chosen = chosen.resolveSibling(chosen.getFileName() + ".sql");
        }
        if (Files.exists(chosen)) {
            Object[] options = {Texts.get("file.overwrite.confirm"), Texts.get("dialog.cancel")};
            int choice = JOptionPane.showOptionDialog(owner.get(),
                    Texts.get("file.overwrite.message", chosen.getFileName()), Texts.get("panel.log.export.title"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
            if (choice != 0) {
                return null;
            }
        }
        return new LogExport(chosen, unqualify != null && unqualify.isSelected() ? candidateCatalog : null);
    }

    @Override
    public void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }
}
