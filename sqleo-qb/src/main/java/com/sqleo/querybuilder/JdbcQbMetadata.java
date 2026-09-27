/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import it.ramasql.qb.QbHost;
import it.ramasql.qb.QbMetadata;

/**
 * Codice NOSTRO: la lettura dei metadati con {@code java.sql.DatabaseMetaData} che il query builder di SQLeo faceva in
 * {@code DiagramLoader}, raccolta qui (Step 7, {@code BUG-016}). È il ripiego di {@link QbHost#metadata()} quando la
 * facciata ha solo una connessione JDBC (prove del modulo); sta in questo pacchetto perché è la sola lettura di
 * {@code DatabaseMetaData} ammessa (test d'architettura T3.9). Il programma non la usa: la sua facciata non passa
 * connessioni e i metadati arrivano dal canale del client.
 */
public final class JdbcQbMetadata implements QbMetadata {

    private final Connection connection;
    private final String defaultCatalog;

    /** @param defaultCatalog catalogo usato quando la richiesta non ne indica uno; {@code null} = quello della connessione */
    public JdbcQbMetadata(Connection connection, String defaultCatalog) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.defaultCatalog = defaultCatalog;
    }

    private String catalog(String catalog) throws SQLException {
        if (catalog != null) {
            return catalog;
        }
        return defaultCatalog != null ? defaultCatalog : connection.getCatalog();
    }

    @Override
    public List<String> tables(String catalog) throws SQLException {
        return objects(catalog, "TABLE");
    }

    @Override
    public List<String> views(String catalog) throws SQLException {
        return objects(catalog, "VIEW");
    }

    private List<String> objects(String catalog, String type) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getTables(catalog(catalog), null, "%", new String[] {type})) {
            while (rs.next()) {
                out.add(rs.getString(3).trim());
            }
        }
        return out;
    }

    @Override
    public String find(String catalog, String table) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        String c = catalog(catalog);
        try (ResultSet rs = md.getTables(c, null, table, null)) {
            if (rs.next()) {
                return rs.getString(3).trim();
            }
        }
        // stessa tabella scritta con maiuscole diverse (lower_case_table_names)
        for (String candidate : new String[] {table.toLowerCase(), table.toUpperCase()}) {
            try (ResultSet rs = md.getTables(c, null, candidate, null)) {
                if (rs.next()) {
                    return rs.getString(3).trim();
                }
            }
        }
        return null;
    }

    @Override
    public List<Column> columns(String catalog, String table) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        String c = catalog(catalog);
        Set<String> primary = new HashSet<>();
        try (ResultSet rs = md.getPrimaryKeys(c, null, table)) {
            while (rs.next()) {
                primary.add(rs.getString(4).trim().toLowerCase());
            }
        }
        List<Column> out = new ArrayList<>();
        try (ResultSet rs = md.getColumns(c, null, table, "%")) {
            while (rs.next()) {
                String name = rs.getString(4).trim();
                String type = rs.getString(6) + "(" + rs.getInt(7) + ")";
                out.add(new Column(name, type, primary.contains(name.toLowerCase())));
            }
        }
        return out;
    }

    @Override
    public List<ForeignKey> importedKeys(String catalog, String table) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getImportedKeys(catalog(catalog), null, table)) {
            return keys(rs);
        }
    }

    @Override
    public List<ForeignKey> exportedKeys(String catalog, String table) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getExportedKeys(catalog(catalog), null, table)) {
            return keys(rs);
        }
    }

    private static List<ForeignKey> keys(ResultSet rs) throws SQLException {
        List<ForeignKey> out = new ArrayList<>();
        while (rs.next()) {
            out.add(new ForeignKey(rs.getString(12), rs.getString(3).trim(), rs.getString(4).trim(),
                    rs.getString(7).trim(), rs.getString(8).trim()));
        }
        return out;
    }
}
