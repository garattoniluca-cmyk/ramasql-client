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
 * Vista.
 *
 * @param catalog      catalogo
 * @param name         nome
 * @param selectSql    la SELECT che definisce la vista ({@code VIEW_DEFINITION})
 * @param checkOption  {@code NONE}, {@code CASCADED} o {@code LOCAL}
 * @param updatable    la vista è aggiornabile secondo il server
 * @param definer      definer, es. {@code root@localhost}; {@code null} se non letto
 * @param securityType {@code DEFINER} o {@code INVOKER}; {@code null} se non letto
 */
public record ViewDef(
        String catalog,
        String name,
        String selectSql,
        String checkOption,
        boolean updatable,
        String definer,
        String securityType) {

    public ViewDef {
        Objects.requireNonNull(name, "name");
        selectSql = selectSql == null ? "" : selectSql;
        checkOption = checkOption == null || checkOption.isBlank() ? "NONE" : checkOption;
    }

    public static ViewDef of(String catalog, String name, String selectSql) {
        return new ViewDef(catalog, name, selectSql, "NONE", false, null, null);
    }

    public ViewDef withName(String v) {
        return new ViewDef(catalog, v, selectSql, checkOption, updatable, definer, securityType);
    }

    public ViewDef withSelectSql(String v) {
        return new ViewDef(catalog, name, v, checkOption, updatable, definer, securityType);
    }
}
