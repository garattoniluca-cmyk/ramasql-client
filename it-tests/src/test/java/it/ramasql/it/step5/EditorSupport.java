/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step5;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.StatementResult;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.it.ItServers;

/**
 * Attrezzi comuni ai test d'integrazione dell'editor di tabelle (step 5 e 6): sessione del client, esecuzione
 * dell'SQL generato con la pipeline vera ({@link SqlExecutor}) e rilettura con un {@link MetadataReader} nuovo.
 */
public final class EditorSupport {

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");

    private EditorSupport() {
    }

    /** Apre una {@link Session} del client posizionata sul catalogo. La password non si stampa mai. */
    public static Session open(ItServers server, String catalog) throws Exception {
        Matcher m = HOST_PORT.matcher(server.url());
        if (!m.matches()) {
            throw new AssertionError("URL del server di test non riconosciuto: " + server.url());
        }
        ConnectionProfile profile = ConnectionProfile.create("Test " + server.label(), m.group(1),
                Integer.parseInt(m.group(2)), server.user(), catalog, "");
        String value = System.getenv("RAMASQL_IT_" + server.name() + "_PASSWORD");
        if (value == null) {
            throw new AssertionError("Manca la variabile d'ambiente RAMASQL_IT_" + server.name()
                    + "_PASSWORD: i test d'integrazione non si saltano.");
        }
        char[] password = value.toCharArray();
        try {
            return Session.open(profile, password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    /**
     * Esegue le istruzioni generate con la pipeline del client (origine «Editor di tabelle»); se il server ne
     * rifiuta una il test fallisce con l'SQL e l'errore del server: è un difetto del generatore.
     */
    public static ScriptResult apply(SqlExecutor exec, String title, List<String> sql) {
        ScriptResult r = exec.run(SqlScript.of(title, SqlOrigin.TABLE_EDITOR.label(), sql));
        if (!r.completed()) {
            StatementResult f = r.failure().orElseThrow();
            throw new AssertionError("Il server ha rifiutato l'SQL generato (" + title + "):\n"
                    + f.statement().text() + "\n→ errore " + (f.error() == null ? "?" : f.error().code() + " "
                    + f.error().message()));
        }
        return r;
    }

    /** Rilettura dal server con un lettore nuovo (nessuna cache): ciò che il server ha davvero. */
    public static TableDef reread(Session session, String catalog, String table) throws SQLException {
        return MetadataReader.of(session).table(catalog, table)
                .orElseThrow(() -> new AssertionError("Tabella non trovata sul server: " + catalog + "." + table));
    }

    /** {@code SHOW CREATE TABLE} letto dal server. */
    public static String showCreate(Session session, String catalog, String table) throws SQLException {
        return MetadataReader.of(session).showCreateTable(catalog, table)
                .orElseThrow(() -> new AssertionError("SHOW CREATE TABLE vuoto: " + table));
    }

    /** Le istruzioni una per blocco, per le evidenze. */
    public static String block(List<String> sql) {
        List<String> out = new ArrayList<>();
        for (String s : sql) {
            out.add("    " + s.replace("\n", "\n    ") + ";");
        }
        return out.isEmpty() ? "    (nessuna istruzione)" : String.join("\n", out);
    }
}
