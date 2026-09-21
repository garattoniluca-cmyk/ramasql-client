/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

/**
 * Avviso di un controllo preventivo (fatto sui metadati, senza contattare il server).
 *
 * @param code    codice tipizzato, per i test e per scegliere icona e aiuto
 * @param message spiegazione in italiano da mostrare accanto alla riga dell'editor
 */
public record PrecheckWarning(Code code, String message) {

    public enum Code {
        /** La tabella figlia non è InnoDB: MyISAM ignora le chiavi esterne. */
        FK_CHILD_NOT_INNODB,
        /** La tabella riferita non è InnoDB. */
        FK_PARENT_NOT_INNODB,
        /** Numero di colonne diverso tra i due lati, o nessuna colonna. */
        FK_COLUMN_COUNT_MISMATCH,
        /** Una colonna nominata non esiste nella tabella figlia o in quella riferita. */
        FK_COLUMN_NOT_FOUND,
        /** Tipi diversi (es. INT e BIGINT, DECIMAL con precisione diversa). */
        FK_TYPE_MISMATCH,
        /** Stesso tipo intero ma uno è UNSIGNED e l'altro no. */
        FK_SIGN_MISMATCH,
        /** Colonne di testo con charset o collation diverse. */
        FK_COLLATION_MISMATCH,
        /** Le colonne riferite non sono il prefisso sinistro di un indice o della chiave primaria. */
        FK_REFERENCED_NOT_INDEXED,
        /** {@code SET NULL} su una colonna NOT NULL. */
        FK_SET_NULL_ON_NOT_NULL,
        /** Esiste già un indice con le stesse colonne nello stesso ordine. */
        INDEX_DUPLICATE,
        /** L'indice proposto è il prefisso sinistro di un indice esistente: è superfluo. */
        INDEX_REDUNDANT_PREFIX,
        /** Una colonna dell'indice non esiste nella tabella. */
        INDEX_COLUMN_NOT_FOUND,
        /** Indice senza colonne, o con la stessa colonna ripetuta. */
        INDEX_INVALID_COLUMNS
    }
}
