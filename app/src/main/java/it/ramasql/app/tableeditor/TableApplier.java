/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import it.ramasql.core.metadata.TableDef;

/**
 * Chi applica davvero le modifiche dell'editor di tabelle: nel programma passa le istruzioni dalla pipeline
 * «anteprima SQL» (finestra di anteprima, poi esecuzione <b>istruzione per istruzione</b>, fermandosi al primo
 * errore) e rilegge la tabella dal server. L'editor non esegue nulla da sé; la <b>verifica dopo</b> (ADR-011) la fa
 * l'editor stesso con {@code SchemaVerifier} confrontando il richiesto con la tabella riletta.
 */
public interface TableApplier {

    /**
     * Applica le istruzioni e comunica l'esito a {@code done} <b>sull'EDT</b> (anche più tardi, dopo un lavoro in
     * sottofondo). Se l'utente annulla dall'anteprima: {@link ApplyOutcome#cancelled(List)}.
     */
    void apply(ApplyRequest request, Consumer<ApplyOutcome> done);

    /**
     * @param original   stato letto dal server prima della modifica; {@code null} = tabella nuova
     * @param edited     stato voluto
     * @param statements le istruzioni, identiche all'anteprima della scheda SQL, nell'ordine di esecuzione
     */
    record ApplyRequest(TableDef original, TableDef edited, List<String> statements) {
        public ApplyRequest {
            Objects.requireNonNull(edited, "edited");
            statements = List.copyOf(statements);
        }
    }

    /**
     * Errore del server sull'istruzione che ha fermato l'esecuzione.
     *
     * @param code        codice del server (1452, 1062…); 0 se sconosciuto
     * @param message     messaggio originale del server
     * @param explanation riga di spiegazione in italiano; vuota se non ce n'è
     */
    record ApplyError(int code, String message, String explanation) {
        public ApplyError {
            Objects.requireNonNull(message, "message");
            explanation = explanation == null ? "" : explanation;
        }
    }

    /**
     * @param cancelled    l'utente ha annullato dall'anteprima: nulla è stato eseguito
     * @param applied      istruzioni eseguite con successo, in ordine
     * @param notApplied   istruzioni non eseguite: la prima è quella fallita (se c'è un errore), poi le successive
     * @param error        errore che ha fermato l'esecuzione; {@code null} = tutte eseguite
     * @param reloaded     la tabella <b>riletta dal server</b> dopo l'esecuzione (anche in caso d'errore);
     *                     {@code null} = non esiste (es. CREATE fallito) o non è stato possibile rileggerla
     */
    record ApplyOutcome(boolean cancelled, List<String> applied, List<String> notApplied, ApplyError error,
            TableDef reloaded) {

        public ApplyOutcome {
            applied = List.copyOf(applied);
            notApplied = List.copyOf(notApplied);
        }

        public static ApplyOutcome cancelled(List<String> statements) {
            return new ApplyOutcome(true, List.of(), statements, null, null);
        }

        public static ApplyOutcome success(List<String> statements, TableDef reloaded) {
            return new ApplyOutcome(false, statements, List.of(), null, reloaded);
        }

        public static ApplyOutcome failure(List<String> applied, List<String> notApplied, ApplyError error,
                TableDef reloaded) {
            return new ApplyOutcome(false, applied, notApplied, Objects.requireNonNull(error, "error"), reloaded);
        }
    }
}
