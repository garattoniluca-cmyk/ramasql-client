/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

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
public final class FakeWorkspacePrompts implements WorkspacePrompts {

    // ---------------------------------------------------------------- finestre modali delle schede

    /** Risposta alle conferme locali della griglia («Scartare le modifiche?»); predefinito: no. */
    public boolean gridConfirmAnswer;
    /** File scelto per «Esporta CSV…»; {@code null} = l'utente annulla. */
    public Path csvFile;
    /** Testo restituito da «Modifica in una finestra…»; {@code null} = l'utente annulla. */
    public String longText;
    /** Gli errori mostrati dalle schede, come «titolo: messaggio». */
    public final List<String> errors = new ArrayList<>();
    /** I messaggi mostrati dall'editor di tabelle. */
    public final List<String> messages = new ArrayList<>();
    /** Risposta alle conferme dell'editor di tabelle; predefinito: sì (i controlli sono già stati fatti). */
    public boolean tableEditorConfirmAnswer = true;
    /** Nome digitato nella conferma rafforzata dell'editor SQL; {@code null} = l'utente annulla. */
    public Function<ConfirmationPolicy.Confirmation, String> onConfirmDestructive = c -> null;
    /** Scelta alla chiusura di un editor SQL con file non salvato; predefinito: scarta (non scrive su disco). */
    public it.ramasql.app.editor.EditorPrompts.SaveChoice saveChoice =
            it.ramasql.app.editor.EditorPrompts.SaveChoice.DISCARD;
    /** File .sql da aprire e da salvare; {@code null} = l'utente annulla. */
    public Path sqlFileToOpen;
    public Path sqlFileToSave;

    @Override
    public it.ramasql.app.grid.GridPrompts gridPrompts() {
        return new it.ramasql.app.grid.GridPrompts() {
            @Override
            public boolean confirm(String title, String message, String confirmLabel) {
                return gridConfirmAnswer;
            }

            @Override
            public String editLongText(String title, String initialText) {
                return longText;
            }

            @Override
            public Path chooseCsvFile(String suggestedName) {
                return csvFile;
            }

            @Override
            public void showError(String title, String message) {
                errors.add(title + ": " + message);
            }
        };
    }

    @Override
    public it.ramasql.app.editor.EditorPrompts editorPrompts() {
        return new it.ramasql.app.editor.EditorPrompts() {
            @Override
            public String confirmDestructive(ConfirmationPolicy.Confirmation confirmation, SqlScript dangerous) {
                return onConfirmDestructive.apply(confirmation);
            }

            @Override
            public SaveChoice askSaveChanges(String documentName) {
                return saveChoice;
            }

            @Override
            public Path chooseSqlFileToOpen() {
                return sqlFileToOpen;
            }

            @Override
            public Path chooseSqlFileToSave(String suggestedName) {
                return sqlFileToSave;
            }

            @Override
            public void showError(String title, String message) {
                errors.add(title + ": " + message);
            }
        };
    }

    @Override
    public it.ramasql.app.tableeditor.TableEditorPrompts tableEditorPrompts() {
        return new it.ramasql.app.tableeditor.TableEditorPrompts() {
            @Override
            public boolean confirm(String title, String message, String confirmLabel) {
                messages.add(title + ": " + message);
                return tableEditorConfirmAnswer;
            }

            @Override
            public void showError(String title, String message) {
                errors.add(title + ": " + message);
            }

            @Override
            public void showMessage(String title, String message) {
                messages.add(title + ": " + message);
            }
        };
    }

    /** Le domande «Conferma, scarta o resta?» ricevute alla chiusura di una scheda, in ordine. */
    public final List<String> pendingQuestions = new ArrayList<>();
    /** Cosa risponde alla domanda di chiusura; predefinito: Resta (non si perde e non si scrive nulla). */
    public java.util.function.Function<String, PendingChoice> onPendingOnClose = q -> PendingChoice.STAY;

    @Override
    public PendingChoice askPendingOnClose(String question) {
        pendingQuestions.add(question);
        return onPendingOnClose.apply(question);
    }

    /** Un'anteprima mostrata: lo script, la conferma e la scelta fatta nella finestra. */
    public record Shown(SqlScript script, ConfirmationPolicy.Confirmation confirmation, String sqlInDialog,
            PreviewDialog.Decision decision) {
    }

    /** Un «Mostra SQL di creazione». */
    public record CreateSql(String title, String statement, String sql) {
    }

    /** Cosa fare con la finestra d'anteprima (sull'EDT); predefinito: Annulla. */
    public Consumer<PreviewDialog> onPreview = d -> d.cancelButton().doClick();
    /** Cosa fare con la finestra «Nuovo catalogo»; predefinito: Annulla. */
    public Consumer<CreateCatalogDialog> onNewCatalog = d -> d.buttons().cancelButton().doClick();
    public Function<String, String> onRename = current -> null;
    public Consumer<ShowCreateDialog> onShowCreate = d -> { };
    public LogExport nextExport;

    public final List<Shown> previews = new ArrayList<>();
    public final List<CreateSql> createSql = new ArrayList<>();
    public final List<String> clipboard = new ArrayList<>();
    public final List<String> exportRequests = new ArrayList<>();

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

    public static LogExport export(Path file, String unqualify) {
        return new LogExport(file, unqualify);
    }
}
