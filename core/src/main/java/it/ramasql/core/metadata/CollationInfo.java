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

import java.util.Objects;

/**
 * Collation disponibile sul server (per la finestra «Crea catalogo»).
 *
 * @param name      nome, es. {@code utf8mb4_unicode_ci}
 * @param charset   charset a cui appartiene, es. {@code utf8mb4}
 * @param isDefault è la collation predefinita del suo charset
 */
public record CollationInfo(String name, String charset, boolean isDefault) {

    public CollationInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(charset, "charset");
    }
}
