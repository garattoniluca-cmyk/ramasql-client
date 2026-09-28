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

/**
 * Le opzioni del dump ({@code DESIGN.md} §3.10, passo 3), con i valori predefiniti di Workbench ridotti.
 *
 * @param dropIfExists       prima di ogni {@code CREATE}, {@code DROP … IF EXISTS} (e {@code DROP DATABASE IF EXISTS}
 *                           se si crea anche il catalogo): il ripristino sostituisce ciò che c'è
 * @param createDatabase     {@code CREATE DATABASE} e {@code USE} per ogni catalogo: il file ricrea i cataloghi con il
 *                           loro nome. Senza, il file si ripristina nel catalogo scelto al momento
 * @param rowsPerInsert      righe per istruzione {@code INSERT} («INSERT estesi»); 1 = una riga per istruzione
 * @param disableForeignKeys {@code SET FOREIGN_KEY_CHECKS=0} durante il ripristino: le tabelle si possono ricreare e
 *                           riempire in qualunque ordine
 */
public record DumpOptions(boolean dropIfExists, boolean createDatabase, int rowsPerInsert, boolean disableForeignKeys) {

    public DumpOptions {
        if (rowsPerInsert < 1) {
            throw new IllegalArgumentException(it.ramasql.core.CoreMessages.get("dump.options.rows", rowsPerInsert));
        }
    }

    /** Predefinite: nessun DROP, nessun CREATE DATABASE, 100 righe per INSERT, controlli delle chiavi spenti. */
    public static DumpOptions defaults() {
        return new DumpOptions(false, false, 100, true);
    }

    public DumpOptions withDropIfExists(boolean v) {
        return new DumpOptions(v, createDatabase, rowsPerInsert, disableForeignKeys);
    }

    public DumpOptions withCreateDatabase(boolean v) {
        return new DumpOptions(dropIfExists, v, rowsPerInsert, disableForeignKeys);
    }

    public DumpOptions withRowsPerInsert(int v) {
        return new DumpOptions(dropIfExists, createDatabase, v, disableForeignKeys);
    }

    public DumpOptions withDisableForeignKeys(boolean v) {
        return new DumpOptions(dropIfExists, createDatabase, rowsPerInsert, v);
    }
}
