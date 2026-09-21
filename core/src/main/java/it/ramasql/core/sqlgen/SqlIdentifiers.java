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

import java.util.List;
import java.util.stream.Collectors;

/** Identificatori SQL: sempre tra backtick, così nomi con spazi e parole riservate funzionano senza eccezioni. */
public final class SqlIdentifiers {

    private SqlIdentifiers() {
    }

    /** {@code ordine dettagli} → {@code `ordine dettagli`}; un backtick interno si raddoppia. */
    public static String quote(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Identificatore vuoto");
        }
        return "`" + name.replace("`", "``") + "`";
    }

    /** {@code `catalogo`.`nome`}, oppure solo {@code `nome`} se il catalogo è nullo o vuoto. */
    public static String qualified(String catalog, String name) {
        return catalog == null || catalog.isBlank() ? quote(name) : quote(catalog) + "." + quote(name);
    }

    /** Elenco tra parentesi: {@code (`a`, `b`)}. */
    public static String columnList(List<String> names) {
        return names.stream().map(SqlIdentifiers::quote).collect(Collectors.joining(", ", "(", ")"));
    }
}
