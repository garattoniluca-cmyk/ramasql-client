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

import it.ramasql.core.CoreMessages;

/**
 * Origini note delle istruzioni, con l'etichetta mostrata nel registro (testi in {@code messages.properties}).
 * {@link SqlStatement#origin()} resta un testo libero: queste sono solo le etichette comuni.
 */
public enum SqlOrigin {
    EDITOR("origin.editor"),
    NAVIGATOR("origin.navigator"),
    GRID("origin.grid"),
    RECORD_FORM("origin.recordForm"),
    TABLE_EDITOR("origin.tableEditor"),
    QUERY_BUILDER("origin.queryBuilder"),
    IMPORT("origin.import"),
    DUMP("origin.dump"),
    SCRIPT_FILE("origin.scriptFile"),
    ER_MODEL("origin.erModel");

    private final String key;

    SqlOrigin(String key) {
        this.key = key;
    }

    /** Etichetta per il registro, es. «Navigatore». */
    public String label() {
        return CoreMessages.get(key);
    }
}
