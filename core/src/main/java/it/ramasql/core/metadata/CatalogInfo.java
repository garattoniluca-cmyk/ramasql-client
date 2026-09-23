/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Catalogo (database) del server, come lo mostra il navigatore.
 *
 * @param name      nome
 * @param charset   charset predefinito ({@code DEFAULT_CHARACTER_SET_NAME})
 * @param collation collation predefinita ({@code DEFAULT_COLLATION_NAME})
 * @param system    catalogo di sistema ({@code information_schema}, {@code mysql}, {@code performance_schema},
 *                  {@code sys}): nascosto per default nel navigatore
 */
public record CatalogInfo(String name, String charset, String collation, boolean system) {

    /** Cataloghi di sistema dei due server. */
    public static final Set<String> SYSTEM_CATALOGS = Set.of("information_schema", "mysql", "performance_schema", "sys");

    public CatalogInfo {
        Objects.requireNonNull(name, "name");
    }

    public static boolean isSystemCatalog(String name) {
        return name != null && SYSTEM_CATALOGS.contains(name.toLowerCase(Locale.ROOT));
    }
}
