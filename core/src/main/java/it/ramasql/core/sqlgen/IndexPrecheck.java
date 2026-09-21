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
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.PrecheckWarning.Code;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Controlli preventivi di un indice proposto, fatti solo sui metadati, più la query che trova i duplicati. */
public final class IndexPrecheck {

    private IndexPrecheck() {
    }

    /**
     * @param table    la tabella com'è ora; l'indice con lo stesso nome del proposto (quello che si sta
     *                 modificando) non è considerato nel confronto
     * @param proposed l'indice da creare o modificato
     */
    public static List<PrecheckWarning> check(TableDef table, IndexDef proposed) {
        List<PrecheckWarning> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean repeated = false;
        for (String column : proposed.columns()) {
            repeated |= !seen.add(column.toLowerCase(Locale.ROOT));
            if (table.column(column).isEmpty()) {
                out.add(warn(Code.INDEX_COLUMN_NOT_FOUND, "precheck.index.columnNotFound", column, table.name()));
            }
        }
        if (proposed.columns().isEmpty() || repeated) {
            out.add(warn(Code.INDEX_INVALID_COLUMNS, "precheck.index.invalidColumns", proposed.name()));
            return out;
        }
        for (IndexDef existing : table.indexes()) {
            if (existing.name().equalsIgnoreCase(proposed.name())) {
                continue;
            }
            if (sameColumns(existing.columns(), proposed.columns(), proposed.columns().size())
                    && existing.columns().size() == proposed.columns().size()) {
                out.add(warn(Code.INDEX_DUPLICATE, "precheck.index.duplicate", proposed.name(), existing.name(),
                        String.join(", ", existing.columns())));
            } else if (proposed.kind() == IndexKind.INDEX
                    && existing.columns().size() > proposed.columns().size()
                    && sameColumns(existing.columns(), proposed.columns(), proposed.columns().size())) {
                out.add(warn(Code.INDEX_REDUNDANT_PREFIX, "precheck.index.redundantPrefix", proposed.name(),
                        existing.name(), String.join(", ", existing.columns())));
            }
        }
        return out;
    }

    private static boolean sameColumns(List<String> a, List<String> b, int count) {
        if (a.size() < count || b.size() < count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (!a.get(i).equalsIgnoreCase(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Query dei <b>duplicati</b> che farebbero fallire un UNIQUE (o una PRIMARY KEY) sulle colonne date
     * (errore 1062). Le righe con un NULL sono escluse: un UNIQUE ammette più NULL.
     */
    public static String duplicatesQuery(TableDef table, List<String> columns) {
        String cols = columns.stream().map(SqlIdentifiers::quote).collect(Collectors.joining(", "));
        String notNull = columns.stream().map(c -> SqlIdentifiers.quote(c) + " IS NOT NULL")
                .collect(Collectors.joining(" AND "));
        return "SELECT " + cols + ", COUNT(*) AS `occorrenze` FROM "
                + SqlIdentifiers.qualified(table.catalog(), table.name())
                + " WHERE " + notNull + " GROUP BY " + cols + " HAVING COUNT(*) > 1";
    }

    private static PrecheckWarning warn(Code code, String key, Object... args) {
        return new PrecheckWarning(code, CoreMessages.get(key, args));
    }
}
