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

import it.ramasql.core.CoreMessages;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;

/**
 * Le operazioni del navigatore già pronte per la pipeline «anteprima SQL»: {@link SqlScript} con titolo (per la
 * finestra di anteprima) e origine «Navigatore» (per il registro). L'SQL viene da {@link ObjectDdl}.
 */
public final class TreeScripts {

    private TreeScripts() {
    }

    public static SqlScript createCatalog(String name, String charset, String collation) {
        return script(CoreMessages.get("script.createCatalog", name), ObjectDdl.createCatalog(name, charset, collation));
    }

    public static SqlScript dropCatalog(String name) {
        return script(CoreMessages.get("script.dropCatalog", name), ObjectDdl.dropCatalog(name));
    }

    public static SqlScript renameTable(String catalog, String oldName, String newName) {
        return script(CoreMessages.get("script.renameTable", oldName, newName),
                ObjectDdl.renameTable(catalog, oldName, newName));
    }

    public static SqlScript truncateTable(String catalog, String table) {
        return script(CoreMessages.get("script.truncateTable", table), ObjectDdl.truncateTable(catalog, table));
    }

    public static SqlScript dropTable(String catalog, String table) {
        return script(CoreMessages.get("script.dropTable", table), ObjectDdl.dropTable(catalog, table));
    }

    public static SqlScript dropView(String catalog, String view) {
        return script(CoreMessages.get("script.dropView", view), ObjectDdl.dropView(catalog, view));
    }

    private static SqlScript script(String title, String sql) {
        return SqlScript.of(title, SqlOrigin.NAVIGATOR.label(), sql);
    }
}
