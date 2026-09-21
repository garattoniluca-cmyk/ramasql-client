/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it;

import java.util.Optional;

/**
 * Connessioni per i test d'integrazione, lette da variabili d'ambiente (mai da file in git).
 * Se mancano, i test d'integrazione si saltano invece di fallire.
 */
record ItConfig(String url, String user, String password) {

    static final String TEST_CATALOG_PREFIX = "ramasql_test_";

    static Optional<ItConfig> mariadb() {
        return fromEnv("RAMASQL_IT_MARIADB");
    }

    static Optional<ItConfig> mysql() {
        return fromEnv("RAMASQL_IT_MYSQL");
    }

    private static Optional<ItConfig> fromEnv(String prefix) {
        String url = System.getenv(prefix + "_URL");
        String user = System.getenv(prefix + "_USER");
        String password = System.getenv(prefix + "_PASSWORD");
        if (url == null || user == null || password == null) {
            return Optional.empty();
        }
        return Optional.of(new ItConfig(url, user, password));
    }
}
