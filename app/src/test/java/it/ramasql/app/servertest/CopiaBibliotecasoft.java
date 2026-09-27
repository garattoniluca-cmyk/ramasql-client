/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.metadata.ViewDefinitionNormalizer;

/**
 * Copia il catalogo <b>reale</b> {@code bibliotecasoft} dell'utente in un catalogo {@code ramasql_test_*} (Step 8,
 * T8.7b): sull'originale solo letture. Struttura di tutte le tabelle (da {@code SHOW CREATE TABLE}), righe dei soli
 * {@code generi} e {@code libri} (nessun dato personale); utenti, operatori e prestiti sono righe <b>inventate</b>, così
 * nessun dato personale né hash esce dal catalogo d'origine; le due viste reali ricreate dalla loro SELECT
 * ({@code SHOW CREATE VIEW}) senza il nome del catalogo d'origine. Stesso metodo dello spike S2c.
 */
final class CopiaBibliotecasoft {

    static final String ORIGINE = "bibliotecasoft";
    static final List<String> VISTE = List.of("v_prestiti_dettaglio", "v_statistiche_libri");
    private static final Pattern CREATE_VIEW_AS =
            Pattern.compile("(?is)^CREATE\\s.*?\\sVIEW\\s+(?:`[^`]+`\\.)?`[^`]+`\\s+AS\\s+(.*)$");

    private CopiaBibliotecasoft() {
    }

    /** {@code true} se sul server c'è il catalogo reale da copiare. */
    static boolean disponibile(DbServer server) throws SQLException {
        return server.catalogExists(ORIGINE);
    }

    static void copia(DbServer server, String dest) throws SQLException {
        DbServer.requireTestName(dest);
        try (Connection c = server.connect(); Statement st = c.createStatement()) {
            st.execute("USE `" + dest + "`");
            List<String> tabelle = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = '"
                    + ORIGINE + "' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")) {
                while (rs.next()) {
                    tabelle.add(rs.getString(1));
                }
            }
            if (tabelle.isEmpty()) {
                throw new AssertionError("nessuna tabella leggibile in " + ORIGINE);
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String t : tabelle) {
                    String ddl;
                    try (Statement s2 = c.createStatement();
                         ResultSet rs = s2.executeQuery("SHOW CREATE TABLE `" + ORIGINE + "`.`" + t + "`")) {
                        rs.next();
                        ddl = rs.getString(2);
                    }
                    st.execute(ddl);   // non qualificato: nasce nel catalogo corrente, quello di test
                    if (t.equals("generi") || t.equals("libri")) {
                        st.execute("INSERT INTO `" + dest + "`.`" + t + "` SELECT * FROM `" + ORIGINE + "`.`" + t + "`");
                    }
                }
            } finally {
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            st.execute("INSERT INTO amministratori (id, nome, cognome, email, password_hash, ruolo)"
                    + " VALUES (1, 'Ada', 'Di Prova', 'ada.diprova@example.org', 'non-usato', 'operatore')");
            st.execute("INSERT INTO utenti (id, nome, cognome, email, codice_fiscale) VALUES"
                    + " (1, 'Mario', 'D''Esempio', 'mario.desempio@example.org', 'DSMMRA80A01H501X'),"
                    + " (2, 'Lucia', 'Finta', NULL, NULL)");
            st.execute("INSERT INTO prestiti (utente_id, libro_id, admin_id, data_prestito, data_restituzione_prevista,"
                    + " data_restituzione_effettiva, stato, note)"
                    + " SELECT 1, id, 1, '2025-01-10', '2025-02-10', '2025-02-20', 'restituito', 'reso in ritardo'"
                    + " FROM libri ORDER BY id LIMIT 3");
            st.execute("INSERT INTO prestiti (utente_id, libro_id, admin_id, data_prestito, data_restituzione_prevista,"
                    + " stato) SELECT 2, id, 1, '2025-03-01', '2025-04-01', 'attivo' FROM libri ORDER BY id DESC LIMIT 2");
            for (String vista : VISTE) {
                String select;
                try (Statement s2 = c.createStatement();
                     ResultSet rs = s2.executeQuery("SHOW CREATE VIEW `" + ORIGINE + "`.`" + vista + "`")) {
                    if (!rs.next()) {
                        throw new AssertionError("SHOW CREATE VIEW senza righe per " + vista);
                    }
                    Matcher m = CREATE_VIEW_AS.matcher(rs.getString(2));
                    if (!m.matches()) {
                        throw new AssertionError("SHOW CREATE VIEW in forma inattesa per " + vista);
                    }
                    select = m.group(1);
                }
                st.execute("CREATE VIEW `" + vista + "` AS " + ViewDefinitionNormalizer.stripCatalog(select, ORIGINE));
            }
        }
    }
}
