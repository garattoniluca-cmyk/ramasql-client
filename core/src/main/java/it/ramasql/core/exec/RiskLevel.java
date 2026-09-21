/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

/** Classe di rischio di un'istruzione, in ordine crescente: guida le conferme della pipeline SQL. */
public enum RiskLevel {
    /** Non modifica nulla: SELECT, SHOW, DESCRIBE, EXPLAIN, USE, SET. */
    SAFE,
    /** Modifica dati o struttura in modo circoscritto: INSERT, UPDATE/DELETE con WHERE, CREATE, ALTER, RENAME. */
    MODIFIES,
    /** Può distruggere dati: DROP, TRUNCATE, UPDATE/DELETE senza WHERE, ALTER … DROP COLUMN. Conferma rafforzata. */
    DESTRUCTIVE;

    /** Il più grave dei due. */
    public RiskLevel max(RiskLevel other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
