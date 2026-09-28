/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

/** Che cosa si scrive di una tabella: la struttura, i dati o tutti e due (le viste hanno solo la struttura). */
public enum DumpContent {
    /** Solo {@code CREATE TABLE}. */
    STRUCTURE,
    /** Solo {@code INSERT}. */
    DATA,
    /** {@code CREATE TABLE} e {@code INSERT}. */
    BOTH;

    public boolean structure() {
        return this != DATA;
    }

    public boolean data() {
        return this != STRUCTURE;
    }
}
