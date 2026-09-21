/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core;

/** Nome e versione del prodotto: unica fonte per titolo della finestra, «Informazioni su» e installer. */
public final class ProductInfo {

    public static final String NAME = "RamaSQL Client";

    private ProductInfo() {
    }

    /** Versione letta dal manifest del jar; in sviluppo (classi non impacchettate) vale "dev". */
    public static String version() {
        String v = ProductInfo.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    public static String title() {
        return NAME + " " + version();
    }
}
