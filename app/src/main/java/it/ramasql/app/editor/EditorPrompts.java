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

import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;

/**
 * Le finestre modali dell'editor SQL (come {@link it.ramasql.app.Prompts} per il resto del programma): l'editor non
 * apre mai un dialogo per conto suo. Nel programma c'è {@link SwingEditorPrompts}; nei test una finta che risponde da
 * sola e registra le domande.
 */
public interface EditorPrompts {

    /** Risposta alla domanda «salvare prima di chiudere?». */
    enum SaveChoice { SAVE, DISCARD, CANCEL }

    /**
     * <b>Conferma rafforzata</b> per istruzioni che possono cancellare dati (DROP, TRUNCATE, UPDATE/DELETE senza
     * WHERE…): la stessa politica e la stessa finestra del navigatore ({@link ConfirmationPolicy}): l'utente riscrive il
     * nome dell'oggetto, oppure la parola di conferma se gli oggetti sono più d'uno. L'editor controlla comunque il
     * testo restituito con {@link ConfirmationPolicy.Confirmation#accepts}: senza il testo giusto non parte nulla.
     *
     * @param confirmation la conferma da chiedere (livello {@code STRONG}, testo da riscrivere, spiegazione)
     * @param dangerous    le sole istruzioni pericolose, nell'ordine, come script (per mostrarle)
     * @return il testo che l'utente ha scritto confermando; {@code null} se ha annullato
     */
    String confirmDestructive(ConfirmationPolicy.Confirmation confirmation, SqlScript dangerous);

    /** Lo script ha modifiche non salvate: salvare, non salvare o restare? */
    SaveChoice askSaveChanges(String documentName);

    /** File {@code .sql} da aprire; {@code null} = annullato. */
    Path chooseSqlFileToOpen();

    /** File {@code .sql} da scrivere, con un nome proposto; {@code null} = annullato. */
    Path chooseSqlFileToSave(String suggestedName);

    void showError(String title, String message);
}
