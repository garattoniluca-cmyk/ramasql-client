/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.verify;

import java.util.Objects;

/**
 * Una differenza tra ciò che era stato chiesto (indice o chiave esterna) e ciò che il server riporta davvero.
 *
 * @param severity {@link Severity#BLOCKING} = il server non ha ciò che è stato chiesto; {@link Severity#INFO} =
 *                 il server ha fatto qualcosa in più o in forma equivalente, da far sapere ma non un errore
 * @param kind     tipo della differenza (per i test, le icone e l'aiuto)
 * @param object   nome dell'indice o della chiave esterna interessati (come chiesto; se senza nome, come sul server)
 * @param message  spiegazione in italiano, da mostrare all'utente
 */
public record VerificationIssue(Severity severity, Kind kind, String object, String message) {

    public enum Severity {
        /** Il server non corrisponde al richiesto: l'esito non è «conforme». */
        BLOCKING,
        /** Informazione: il server ha aggiunto o scelto qualcosa da sé, senza contraddire il richiesto. */
        INFO
    }

    public enum Kind {
        /** La tabella non c'è sul server. */
        TABLE_MISSING,
        /** Indice (o chiave primaria) chiesto e assente. */
        INDEX_MISSING,
        /** Indice presente con la struttura chiesta ma con un altro nome. */
        INDEX_NAME_DIFFERENT,
        /** Stesse colonne dell'indice chiesto, in un altro ordine. */
        INDEX_COLUMN_ORDER,
        /** Colonne dell'indice diverse da quelle chieste. */
        INDEX_COLUMNS,
        /** UNIQUE chiesto e INDEX sul server, o viceversa. */
        INDEX_UNIQUENESS,
        /** Indice (o chiave primaria) sul server che non era stato chiesto. */
        INDEX_UNEXPECTED,
        /** Indice creato dal server da sé per servire una chiave esterna (informazione). */
        INDEX_IMPLICIT_FOR_FK,
        /** Indice creato a suo tempo per una chiave esterna ora eliminata: il server lo conserva (informazione). */
        INDEX_LEFT_BY_DROPPED_FK,
        /** Chiave esterna chiesta e assente (es. ignorata in silenzio da MyISAM). */
        FK_MISSING,
        /** Chiave esterna presente con la struttura chiesta ma con un altro nome. */
        FK_NAME_DIFFERENT,
        /** Chiave esterna chiesta senza nome: il nome lo ha scelto il server (informazione). */
        FK_NAME_ASSIGNED,
        /** Stesse colonne della FK chiesta, in un altro ordine. */
        FK_COLUMN_ORDER,
        /** Colonne della tabella figlia diverse. */
        FK_COLUMNS,
        /** Tabella (o catalogo) riferita diversa. */
        FK_REFERENCED_TABLE,
        /** Colonne riferite diverse. */
        FK_REFERENCED_COLUMNS,
        /** Azione ON DELETE diversa. */
        FK_ON_DELETE,
        /** Azione ON UPDATE diversa. */
        FK_ON_UPDATE,
        /** RESTRICT chiesto e NO ACTION riportato (o viceversa): su InnoDB sono la stessa regola (informazione). */
        FK_ACTION_EQUIVALENT,
        /** Chiave esterna sul server che non era stata chiesta. */
        FK_UNEXPECTED
    }

    public VerificationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
    }

    public boolean isBlocking() {
        return severity == Severity.BLOCKING;
    }
}
