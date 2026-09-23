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
import java.util.function.Consumer;
import java.util.function.Function;

import it.ramasql.app.navigator.CreateCatalogDialog;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.app.pipeline.ShowCreateDialog;
import it.ramasql.app.workspace.WorkspacePrompts;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.CollationInfo;

/**
 * {@link WorkspacePrompts} dei test: costruisce le <b>finestre vere</b> (anteprima, nuovo catalogo, SQL di
 * creazione) senza mostrarle, le passa al test che le pilota via API (scrive, preme i pulsanti, le disegna) e
 * restituisce ciò che la finestra ha deciso. Registra tutto ciò che è stato mostrato.
 */
final class FakeWorkspacePrompts implements WorkspacePrompts {

    /** Un'anteprima mostrata: lo script, la conferma e la scelta fatta nella finestra. */
    record Shown(SqlScript script, ConfirmationPolicy.Confirmation confirmation, String sqlInDialog,
            PreviewDialog.Decision decision) {
    }

    /** Un «Mostra SQL di creazione». */
    record CreateSql(String title, String statement, String sql) {
    }

    /** Cosa fare con la finestra d'anteprima (sull'EDT); predefinito: Annulla. */
    Consumer<PreviewDialog> onPreview = d -> d.cancelButton().doClick();
    /** Cosa fare con la finestra «Nuovo catalogo»; predefinito: Annulla. */
    Consumer<CreateCatalogDialog> onNewCatalog = d -> d.buttons().cancelButton().doClick();
    Function<String, String> onRename = current -> null;
    Consumer<ShowCreateDialog> onShowCreate = d -> { };
    LogExport nextExport;

    final List<Shown> previews = new ArrayList<>();
    final List<CreateSql> createSql = new ArrayList<>();
    final List<String> clipboard = new ArrayList<>();
    final List<String> exportRequests = new ArrayList<>();

    @Override
    public PreviewDialog.Decision preview(SqlScript script, ConfirmationPolicy.Confirmation confirmation) {
        PreviewDialog dialog = new PreviewDialog(null, script, confirmation);
        try {
            onPreview.accept(dialog);
            previews.add(new Shown(script, confirmation, dialog.sqlText(), dialog.decision()));
            return dialog.decision();
        } finally {
            dialog.dispose();
        }
    }

    @Override
    public CreateCatalogDialog.Choice askNewCatalog(List<CollationInfo> collations, String charset, String collation) {
        CreateCatalogDialog dialog = new CreateCatalogDialog(null, collations, charset, collation);
        try {
            onNewCatalog.accept(dialog);
            return dialog.result();
        } finally {
            dialog.dispose();
        }
    }

    @Override
    public String askNewTableName(String catalog, String currentName) {
        return onRename.apply(currentName);
    }

    @Override
    public void showCreateSql(String title, String statement, String sql) {
        createSql.add(new CreateSql(title, statement, sql));
        ShowCreateDialog dialog = new ShowCreateDialog(null, title, statement, sql, clipboard::add);
        try {
            onShowCreate.accept(dialog);
        } finally {
            dialog.dispose();
        }
    }

    @Override
    public LogExport chooseLogExport(String suggestedName, String candidateCatalog) {
        exportRequests.add(suggestedName + " | " + candidateCatalog);
        return nextExport;
    }

    @Override
    public void copyToClipboard(String text) {
        clipboard.add(text);
    }

    static LogExport export(Path file, String unqualify) {
        return new LogExport(file, unqualify);
    }
}
