/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step3;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.core.metadata.ViewDef;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;

/** Attrezzi comuni dei test dello Step 3: sessioni del client, fixture, istantanea JSON dei metadati. */
final class Step3Support {

    static final String FIXTURE_INNODB = "/fixtures/biblioteca.sql";
    static final String FIXTURE_MYISAM = "/fixtures/biblioteca_myisam.sql";

    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");

    private Step3Support() {
    }

    /** Profilo del client verso il server di test, posizionato sul catalogo indicato ({@code ""} = nessuno). */
    static ConnectionProfile profile(ItServers server, String catalog) {
        Matcher m = HOST_PORT.matcher(server.url());
        if (!m.matches()) {
            throw new AssertionError("URL del server di test non riconosciuto: " + server.url());
        }
        return ConnectionProfile.create("Test " + server.label(), m.group(1), Integer.parseInt(m.group(2)),
                server.user(), catalog, "");
    }

    /** Apre una {@link Session} del client (le stesse due connessioni del prodotto). Password mai stampata. */
    static Session open(ItServers server, String catalog) throws Exception {
        String value = System.getenv("RAMASQL_IT_" + server.name() + "_PASSWORD");
        if (value == null) {
            throw new AssertionError("Manca la variabile d'ambiente RAMASQL_IT_" + server.name()
                    + "_PASSWORD: i test d'integrazione non si saltano.");
        }
        char[] password = value.toCharArray();
        try {
            return Session.open(profile(server, catalog), password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    /** Distrugge tutti i cataloghi, anche se uno fallisce; gli errori si sommano in un unico AssertionError. */
    static void closeAll(Collection<TestCatalog> catalogs) {
        List<Throwable> errors = new ArrayList<>();
        for (TestCatalog c : catalogs) {
            try {
                c.close();
            } catch (RuntimeException | Error e) {
                errors.add(e);
            }
        }
        catalogs.clear();
        if (!errors.isEmpty()) {
            AssertionError all = new AssertionError("cataloghi di test non distrutti: " + errors.size());
            errors.forEach(all::addSuppressed);
            throw all;
        }
    }

    /** {@code DROP DATABASE IF EXISTS} di un catalogo di test creato dal client (solo nomi {@code ramasql_test_*}). */
    static void dropTestCatalog(ItServers server, String name) throws SQLException {
        String safe = TestCatalog.requireTestName(name);
        try (Connection c = server.connect(); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + safe + "`");
        }
    }

    // ================================================================ istantanea JSON dei metadati

    /**
     * I metadati di un catalogo in forma JSON confrontabile con i file {@code *.expected.json}: senza il nome del
     * catalogo (che è casuale) e senza il testo delle viste (che i server riscrivono ciascuno a modo suo).
     */
    static ObjectNode snapshot(MetadataReader reader, String catalog) throws SQLException {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode tables = root.putArray("tables");
        List<TableDef> defs = new ArrayList<>(reader.allTables(catalog));
        defs.sort(Comparator.comparing(TableDef::name));
        for (TableDef t : defs) {
            tables.add(table(t));
        }
        ArrayNode views = root.putArray("views");
        List<ViewDef> viewDefs = new ArrayList<>(reader.views(catalog));
        viewDefs.sort(Comparator.comparing(ViewDef::name));
        for (ViewDef v : viewDefs) {
            ObjectNode n = views.addObject();
            n.put("name", v.name());
            n.put("checkOption", v.checkOption());
            ArrayNode cols = n.putArray("columns");
            reader.viewColumns(catalog, v.name()).forEach(c -> cols.add(c.name()));
        }
        return root;
    }

    static ObjectNode table(TableDef t) {
        ObjectNode n = JSON.createObjectNode();
        n.put("name", t.name());
        n.put("engine", t.engine());
        n.put("charset", t.charset());
        n.put("collation", t.collation());
        n.put("comment", t.comment());
        if (t.autoIncrementStart() == null) {
            n.putNull("autoIncrement");
        } else {
            n.put("autoIncrement", t.autoIncrementStart());
        }
        ArrayNode cols = n.putArray("columns");
        for (ColumnDef c : t.columns()) {
            ObjectNode cn = cols.addObject();
            cn.put("name", c.name());
            cn.put("type", c.fullType() + (c.unsigned() ? " UNSIGNED" : ""));
            cn.put("nullable", c.nullable());
            cn.put("default", switch (c.defaultValue().kind()) {
                case NONE -> "NONE";
                case NULL -> "NULL";
                case LITERAL -> "LITERAL:" + c.defaultValue().value();
                case EXPRESSION -> "EXPRESSION:" + c.defaultValue().value();
            });
            cn.put("autoIncrement", c.autoIncrement());
            cn.put("comment", c.comment());
            if (c.charset() != null) {
                cn.put("charset", c.charset());
            }
            if (c.collation() != null) {
                cn.put("collation", c.collation());
            }
            if (c.onUpdate() != null) {
                cn.put("onUpdate", c.onUpdate());
            }
            if (c.generated()) {
                cn.put("generated", true);
            }
        }
        ArrayNode idx = n.putArray("indexes");
        for (IndexDef i : t.indexes()) {
            ObjectNode in = idx.addObject();
            in.put("name", i.name());
            in.put("kind", i.kind().name());
            ArrayNode ic = in.putArray("columns");
            i.columns().forEach(ic::add);
        }
        ArrayNode fks = n.putArray("foreignKeys");
        for (ForeignKeyDef f : t.foreignKeys()) {
            ObjectNode fn = fks.addObject();
            fn.put("name", f.name());
            ArrayNode fc = fn.putArray("columns");
            f.columns().forEach(fc::add);
            if (f.refCatalog() != null) {
                fn.put("refCatalog", f.refCatalog());
            }
            fn.put("refTable", f.refTable());
            ArrayNode rc = fn.putArray("refColumns");
            f.refColumns().forEach(rc::add);
            fn.put("onDelete", f.onDelete().name());
            fn.put("onUpdate", f.onUpdate().name());
        }
        ArrayNode adv = n.putArray("advanced");
        t.advancedElements().forEach(adv::add);
        return n;
    }

    static JsonNode expected(String resource) throws Exception {
        try (var in = Step3Support.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError("JSON di riferimento non trovato: " + resource);
            }
            return JSON.readTree(in);
        }
    }

    static String pretty(JsonNode node) throws Exception {
        return JSON.writeValueAsString(node);
    }

    // ================================================================ confronto dei dati

    /** {@code CHECKSUM TABLE} di ogni tabella (non vista) del catalogo, per nome. */
    static Map<String, Long> checksums(Connection con, MetadataReader reader, String catalog) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        for (TableSummary t : reader.tables(catalog)) {
            if (t.isView()) {
                continue;
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(
                    "CHECKSUM TABLE `" + catalog + "`.`" + t.name() + "`")) {
                rs.next();
                out.put(t.name(), rs.getLong(2));
            }
        }
        return out;
    }

    /** Un solo valore letto con una connessione di test. */
    static String scalar(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
