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

import java.nio.file.Path;
import java.util.List;

import it.ramasql.app.navigator.CreateCatalogDialog;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.CollationInfo;

/**
 * Le finestre dell'area di lavoro (navigatore, anteprima, pannello SQL), come {@link it.ramasql.app.Prompts} per le
 * connessioni: la logica non apre mai una finestra per conto suo. Nel programma l'implementazione è
 * {@link SwingWorkspacePrompts}; nei test una finta che pilota le stesse finestre vere senza mostrarle.
 * Tutto si chiama sull'EDT.
 */
public interface WorkspacePrompts {

    /**
     * Scelta per «Esporta registro…».
     *
     * @param file             file {@code .sql} da scrivere
     * @param unqualifyCatalog catalogo da togliere dai nomi qualificati, per rieseguire lo script altrove
     *                         ({@code null} = lo script resta com'è)
     */
    record LogExport(Path file, String unqualifyCatalog) {
    }

    /** Le finestre modali della griglia di data-entry (conferme locali, testo lungo, esporta CSV). */
    it.ramasql.app.grid.GridPrompts gridPrompts();

    /** Le finestre modali dell'editor SQL (apri/salva .sql, conferma rafforzata, errori). */
    it.ramasql.app.editor.EditorPrompts editorPrompts();

    /** Scelta dei file (importazione, dump, script, modello ER) e conferme delle schede che li usano. */
    FilePrompts files();

    /** Le finestre modali dell'editor di tabelle. */
    it.ramasql.app.tableeditor.TableEditorPrompts tableEditorPrompts();

    /** Cosa fare chiudendo una scheda di data-entry che ha modifiche in sospeso (T4.12). */
    enum PendingChoice { CONFIRM, DISCARD, STAY }

    /**
     * Domanda alla chiusura di una scheda con modifiche in sospeso: «Conferma, scarta o resta?». Chiudere una scheda
     * non deve mai far perdere lavoro in silenzio né scrivere sul server senza che l'utente lo chieda.
     *
     * @param question testo già pronto della domanda ({@code DataGrid.closeQuestion()})
     */
    PendingChoice askPendingOnClose(String question);

    /** Finestra «SQL che verrà eseguito»: restituisce la scelta dell'utente. */
    PreviewDialog.Decision preview(SqlScript script, ConfirmationPolicy.Confirmation confirmation);

    /**
     * Finestra «Nuovo catalogo»: nome, charset e collation.
     *
     * @return la scelta, {@code null} = annullato
     */
    CreateCatalogDialog.Choice askNewCatalog(List<CollationInfo> collations, String charset, String collation);

    /** Nuovo nome per «Rinomina…»; {@code null} = annullato. */
    String askNewTableName(String catalog, String currentName);

    /** «Mostra SQL di creazione»: testo di sola lettura con Copia. */
    void showCreateSql(String title, String statement, String sql);

    /**
     * «Esporta…» del registro.
     *
     * @param candidateCatalog catalogo che si può proporre di togliere dai nomi ({@code null} = nessuno)
     * @return la scelta, {@code null} = annullato
     */
    LogExport chooseLogExport(String suggestedName, String candidateCatalog);

    /** Mette il testo negli appunti di sistema. */
    void copyToClipboard(String text);

    /**
     * «Nuovo modello dal catalogo…»: le tabelle da mettere nel modello ER (in partenza tutte);
     * {@code null} = annullato.
     */
    List<String> chooseModelTables(String catalog, List<String> tables);

    /** L'etichetta di una relazione logica del modello ER; {@code null} = annullato. */
    String askRelationshipLabel(String relationship, String current);
}
