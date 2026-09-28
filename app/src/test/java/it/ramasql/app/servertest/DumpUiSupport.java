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

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static it.ramasql.app.servertest.Probe.waitUntil;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JMenuItem;

import it.ramasql.app.dump.DumpWizard;
import it.ramasql.app.dump.ScriptRunTab;
import it.ramasql.core.dump.DumpWriter;

/** Esportazione e «Esegui script SQL» dai punti dell'interfaccia; copie e confronti con la connessione del test. */
final class DumpUiSupport {

    private DumpUiSupport() {
    }

    /** Il pulsante «Esporta/Dump» della barra (con il catalogo scelto nel navigatore, se c'è). */
    static DumpWizard fromToolbar(ClientApp a) {
        waitUntil("«Esporta/Dump» abilitato", ClientApp.TIMEOUT, () -> a.frame().button("export").isEnabled());
        onEdt(() -> a.frame().button("export").doClick());
        DumpWizard w = fromEdt(() -> a.frame().tabs().selected() instanceof DumpWizard d ? d : null);
        assertTrue(w != null, "la scheda «Esporta / Dump» è davanti");
        waitUntil("cataloghi letti", ClientApp.TIMEOUT, () -> !w.isLoading());
        return w;
    }

    /** «Importa» → «Esegui script SQL…». */
    static ScriptRunTab scriptTab(ClientApp a) {
        waitUntil("«Importa» abilitato", ClientApp.TIMEOUT, () -> a.frame().button("import").isEnabled());
        onEdt(() -> {
            JMenuItem item = ClientApp.menuItem(a.frame().importMenu(), "import.menu.script");
            item.doClick();
        });
        ScriptRunTab t = fromEdt(() -> a.frame().tabs().selected() instanceof ScriptRunTab s ? s : null);
        assertTrue(t != null, "la scheda «Esegui script» è davanti");
        return t;
    }

    /** Aspetta la fine della lettura di prova del file scelto. */
    static void awaitScan(ScriptRunTab t) {
        waitUntil("file letto", 120_000, () -> !t.isScanning() && t.preview() != null);
    }

    /** «Esegui» e attesa della fine. */
    /** Il catalogo è fra quelli che si possono scegliere. */
    static boolean hasTarget(ScriptRunTab t, String catalog) {
        for (int i = 0; i < t.targetCombo().getItemCount(); i++) {
            if (catalog.equals(t.targetCombo().getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    static void runAndWait(ClientApp a, ScriptRunTab t) {
        onEdt(() -> t.runButton().doClick());
        waitUntil("script finito", 600_000, () -> !t.isRunning());
        a.waitIdle();
    }

    /** I cataloghi dell'utente: non di sistema e non di test. */
    static List<String> userCatalogs(DbServer server) throws SQLException {
        List<String> out = new ArrayList<>();
        for (List<String> r : server.rows("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME")) {
            String n = r.get(0);
            String l = n.toLowerCase(Locale.ROOT);
            if (!l.startsWith("ramasql_test") && !List.of("information_schema", "mysql", "performance_schema", "sys")
                    .contains(l)) {
                out.add(n);
            }
        }
        return out;
    }

    /**
     * Copia un catalogo (dell'utente) in un catalogo di test: struttura da {@code SHOW CREATE TABLE}, righe con
     * {@code INSERT … SELECT} (colonne generate escluse), viste da {@code SHOW CREATE VIEW} senza definer né catalogo.
     * Sull'originale solo letture.
     */
    static void copy(DbServer server, String source, String dest) throws Exception {
        DbServer.requireTestName(dest);
        try (Connection c = server.connect(); Statement st = c.createStatement()) {
            st.execute("USE `" + dest + "`");
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                List<String> tables = new ArrayList<>();
                List<String> views = new ArrayList<>();
                try (ResultSet rs = st.executeQuery("SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = '" + source.replace("'", "''") + "' ORDER BY TABLE_NAME")) {
                    while (rs.next()) {
                        (rs.getString(2).equals("VIEW") ? views : tables).add(rs.getString(1));
                    }
                }
                for (String t : tables) {
                    String ddl;
                    try (Statement s2 = c.createStatement();
                         ResultSet rs = s2.executeQuery("SHOW CREATE TABLE `" + source + "`.`" + t + "`")) {
                        rs.next();
                        ddl = rs.getString(2);
                    }
                    st.execute(ddl);
                    List<String> cols = new ArrayList<>();
                    try (Statement s2 = c.createStatement(); ResultSet rs = s2.executeQuery("SELECT COLUMN_NAME FROM"
                            + " information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + source.replace("'", "''")
                            + "' AND TABLE_NAME = '" + t.replace("'", "''") + "' AND EXTRA NOT LIKE '%VIRTUAL GENERATED%'"
                            + " AND EXTRA NOT LIKE '%STORED GENERATED%'"
                            + " ORDER BY ORDINAL_POSITION")) {
                        while (rs.next()) {
                            cols.add("`" + rs.getString(1).replace("`", "``") + "`");
                        }
                    }
                    String list = String.join(", ", cols);
                    st.execute("INSERT INTO `" + dest + "`.`" + t + "` (" + list + ") SELECT " + list + " FROM `" + source
                            + "`.`" + t + "`");
                }
                List<String> pending = new ArrayList<>(views);
                for (int round = 0; round < 5 && !pending.isEmpty(); round++) {
                    for (String v : List.copyOf(pending)) {
                        String ddl;
                        try (Statement s2 = c.createStatement();
                             ResultSet rs = s2.executeQuery("SHOW CREATE VIEW `" + source + "`.`" + v + "`")) {
                            rs.next();
                            ddl = rs.getString(2);
                        }
                        try {
                            st.execute(DumpWriter.viewForDump(ddl, source));
                            pending.remove(v);
                        } catch (SQLException e) {
                            // una vista che usa un'altra vista non ancora copiata: si riprova al giro dopo
                        }
                    }
                }
                if (!pending.isEmpty()) {
                    throw new AssertionError("viste non copiate da " + source + ": " + pending);
                }
            } finally {
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    /** L'impronta senza il testo di SHOW CREATE (per confrontare lo stesso catalogo su server diversi). */
    static Map<String, String> dataOnly(Map<String, String> fingerprint, boolean strip) {
        if (!strip) {
            return fingerprint;
        }
        Map<String, String> out = new TreeMap<>();
        fingerprint.forEach((k, v) -> out.put(k, v.substring(0, v.lastIndexOf(" · "))));
        return out;
    }

    /**
     * La struttura di una tabella letta da {@code information_schema} (colonne con tipo, NULL, predefinito, extra,
     * charset e collation; indici; chiavi esterne con le azioni; engine e collation della tabella): confrontabile fra
     * cataloghi senza dipendere dalla forma in cui {@code SHOW CREATE TABLE} scrive le stesse cose.
     */
    static String structure(Connection c, String catalog, String table) throws SQLException {
        String cat = catalog.replace("'", "''");
        String t = table.replace("'", "''");
        StringBuilder sb = new StringBuilder();
        for (String sql : List.of(
                "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME"
                        + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + cat + "' AND TABLE_NAME = '" + t
                        + "' ORDER BY ORDINAL_POSITION",
                "SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = '" + cat + "' AND TABLE_NAME = '" + t + "' ORDER BY INDEX_NAME, SEQ_IN_INDEX",
                "SELECT r.CONSTRAINT_NAME, r.REFERENCED_TABLE_NAME, r.UPDATE_RULE, r.DELETE_RULE, k.COLUMN_NAME,"
                        + " k.REFERENCED_COLUMN_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS r"
                        + " JOIN information_schema.KEY_COLUMN_USAGE k ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA"
                        + " AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME AND k.TABLE_NAME = r.TABLE_NAME"
                        + " WHERE r.CONSTRAINT_SCHEMA = '" + cat + "' AND r.TABLE_NAME = '" + t
                        + "' ORDER BY r.CONSTRAINT_NAME, k.ORDINAL_POSITION",
                "SELECT ENGINE, TABLE_COLLATION, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = '"
                        + cat + "' AND TABLE_NAME = '" + t + "'")) {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                int n = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    for (int i = 1; i <= n; i++) {
                        sb.append(rs.getString(i)).append('|');
                    }
                    sb.append('\n');
                }
            }
            sb.append("--\n");
        }
        return sb.toString();
    }

    /** La definizione di una vista senza il nome del catalogo (e senza definer). */
    static String viewDefinition(Connection c, String catalog, String view) throws SQLException {
        try (Statement s2 = c.createStatement();
             ResultSet rs = s2.executeQuery("SHOW CREATE VIEW `" + catalog + "`.`" + view + "`")) {
            rs.next();
            return DumpWriter.viewForDump(rs.getString(2), catalog);
        }
    }

    /** Impronta di ogni tabella di un catalogo (righe in ordine, come testo o byte) e definizione delle viste. */
    static Map<String, String> fingerprint(DbServer server, String catalog) throws Exception {
        Map<String, String> out = new TreeMap<>();
        try (Connection c = server.connect(); Statement st = c.createStatement()) {
            List<String[]> objects = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES"
                    + " WHERE TABLE_SCHEMA = '" + catalog + "' ORDER BY TABLE_NAME")) {
                while (rs.next()) {
                    objects.add(new String[] {rs.getString(1), rs.getString(2)});
                }
            }
            for (String[] o : objects) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long n = 0;
                try (Statement s2 = c.createStatement(); ResultSet rs = s2.executeQuery("SELECT * FROM `" + catalog + "`.`"
                        + o[0] + "`")) {
                    int cols = rs.getMetaData().getColumnCount();
                    List<String> rows = new ArrayList<>();
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i <= cols; i++) {
                            int type = rs.getMetaData().getColumnType(i);
                            boolean bin = type == java.sql.Types.BINARY || type == java.sql.Types.VARBINARY
                                    || type == java.sql.Types.LONGVARBINARY || type == java.sql.Types.BLOB
                                    || type == java.sql.Types.BIT;
                            String v = bin ? (rs.getBytes(i) == null ? null : HexFormat.of().formatHex(rs.getBytes(i)))
                                    : rs.getString(i);
                            sb.append(v == null ? "\u0000" : v).append('\u001F');
                        }
                        rows.add(sb.toString());
                        n++;
                    }
                    rows.sort(null);   // indipendente dall'ordine fisico
                    for (String r : rows) {
                        md.update(r.getBytes(StandardCharsets.UTF_8));
                        md.update((byte) '\n');
                    }
                }
                String ddl = o[1].equals("VIEW") ? viewDefinition(c, catalog, o[0]) : structure(c, catalog, o[0]);
                out.put(o[0], o[1] + " · " + n + " righe · " + HexFormat.of().formatHex(md.digest()).substring(0, 16)
                        + " · " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(ddl.getBytes(StandardCharsets.UTF_8))).substring(0, 12));
            }
        }
        return out;
    }
}
