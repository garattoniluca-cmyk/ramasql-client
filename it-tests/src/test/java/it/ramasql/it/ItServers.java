/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * I due server dei test d'integrazione, per i test parametrici:
 * {@code @ParameterizedTest @EnumSource(ItServers.class)}.
 *
 * <p>Se le variabili d'ambiente {@code RAMASQL_IT_*} mancano o il server non risponde il test
 * <b>fallisce</b>: non si salta mai (goal.md, vincolo 4).
 */
public enum ItServers {

    MARIADB("MariaDB", "RAMASQL_IT_MARIADB", ItConfig::mariadb),
    MYSQL("MySQL", "RAMASQL_IT_MYSQL", ItConfig::mysql);

    /** Millisecondi concessi al server per rispondere prima di dichiarare fallito il test. */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String label;
    private final String envPrefix;
    private final Supplier<Optional<ItConfig>> config;

    ItServers(String label, String envPrefix, Supplier<Optional<ItConfig>> config) {
        this.label = label;
        this.envPrefix = envPrefix;
        this.config = config;
    }

    /** Nome leggibile del server ("MariaDB", "MySQL"). */
    public String label() {
        return label;
    }

    /** Vero per il server MariaDB, falso per MySQL. */
    public boolean isMariaDb() {
        return this == MARIADB;
    }

    /** URL JDBC del server (senza catalogo); fallisce se le variabili d'ambiente mancano. */
    public String url() {
        return config().url();
    }

    /** Utente dei test; fallisce se le variabili d'ambiente mancano. */
    public String user() {
        return config().user();
    }

    /** Apre una connessione al server senza catalogo predefinito (autocommit, come il client). */
    public Connection connect() {
        return connect(null, new Properties());
    }

    /** Apre una connessione posizionata sul catalogo indicato ({@code null} = nessuno). */
    public Connection connect(String catalog) {
        return connect(catalog, new Properties());
    }

    /**
     * Apre una connessione con proprietà aggiuntive del driver (utente e password li mette questo metodo).
     *
     * @throws AssertionError se mancano le variabili d'ambiente o il server non risponde
     */
    public Connection connect(String catalog, Properties extra) {
        ItConfig c = config();
        Properties p = new Properties();
        p.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        p.putAll(extra);
        p.setProperty("user", c.user());
        p.setProperty("password", c.password());
        try {
            Connection con = DriverManager.getConnection(c.url(), p);
            if (catalog != null) {
                con.setCatalog(catalog);
            }
            return con;
        } catch (SQLException e) {
            throw new AssertionError("Il server " + label + " (" + c.url() + ") non risponde o rifiuta l'utente "
                    + c.user() + ": [" + e.getErrorCode() + "] " + e.getMessage(), e);
        }
    }

    private ItConfig config() {
        return config.get().orElseThrow(() -> new AssertionError(
                "Mancano le variabili d'ambiente " + envPrefix + "_URL / _USER / _PASSWORD: "
                        + "i test d'integrazione non si saltano (usare scripts\\verify.ps1, che le carica)."));
    }
}
