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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;

/** Finestre finte: rispondono da sole e registrano le domande. */
final class FakeEditorPrompts implements EditorPrompts {

    /** {@code true}: l'utente riscrive esattamente il testo richiesto; {@code false}: annulla. */
    boolean confirmAnswer;
    /** Se non {@code null}: l'utente scrive questo testo (anche sbagliato) e preme Esegui. */
    String typedAnswer;
    /** Le conferme chieste (livello, testo da riscrivere), nell'ordine. */
    final List<ConfirmationPolicy.Confirmation> confirmationRequests = new ArrayList<>();
    SaveChoice saveChoice = SaveChoice.CANCEL;
    Path fileToOpen;
    Path fileToSave;
    final List<List<String>> confirmations = new ArrayList<>();
    final List<String> saveQuestions = new ArrayList<>();
    final List<String> suggestedNames = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    @Override
    public String confirmDestructive(ConfirmationPolicy.Confirmation confirmation, SqlScript dangerous) {
        confirmations.add(dangerous.statements().stream().map(SqlStatement::text).toList());
        confirmationRequests.add(confirmation);
        if (typedAnswer != null) {
            return typedAnswer;
        }
        return confirmAnswer ? confirmation.typeToConfirm() : null;
    }

    @Override
    public SaveChoice askSaveChanges(String documentName) {
        saveQuestions.add(documentName);
        return saveChoice;
    }

    @Override
    public Path chooseSqlFileToOpen() {
        return fileToOpen;
    }

    @Override
    public Path chooseSqlFileToSave(String suggestedName) {
        suggestedNames.add(suggestedName);
        return fileToSave;
    }

    @Override
    public void showError(String title, String message) {
        errors.add(title + ": " + message);
    }
}
