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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Il <strong>canale interno dei metadati</strong>: le sole letture che il client fa per conto proprio su
 * {@code information_schema} e con {@code SHOW CREATE}, sulla connessione di servizio della sessione.
 * <p>Insieme a {@code InternalQueries} (sessione) e {@code SqlExecutor} (SQL dell'utente) è l'unico punto del
 * prodotto che esegue SQL (test d'architettura T3.9). Tutte le istruzioni sono fisse: i nomi arrivano come
 * parametri {@code ?} oppure, per {@code SHOW CREATE}, tra backtick con il backtick interno raddoppiato.
 * Nessuna modifica dati, nessuna transazione. Queste letture non finiscono nel registro dell'utente.
 */
final class MetadataQueries {

    static final String CATALOGS =
            "SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME"
            + " FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME";

    static final String TABLES_OF_CATALOG =
            "SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COMMENT"
            + " FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";

    static final String TABLE =
            "SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COLLATION, TABLE_COMMENT"
            + " FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";

    static final String COLUMNS =
            "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME,"
            + " COLLATION_NAME, COLUMN_COMMENT, ORDINAL_POSITION, GENERATION_EXPRESSION"
            + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";

    static final String INDEXES =
            "SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, INDEX_TYPE, COLLATION"
            + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?"
            + " ORDER BY INDEX_NAME, SEQ_IN_INDEX";

    static final String FOREIGN_KEYS =
            "SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME,"
            + " k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE"
            + " FROM information_schema.KEY_COLUMN_USAGE k"
            + " JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA"
            + " AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME AND r.TABLE_NAME = k.TABLE_NAME"
            + " WHERE k.TABLE_SCHEMA = ? AND k.TABLE_NAME = ? AND k.REFERENCED_TABLE_NAME IS NOT NULL"
            + " ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION";

    static final String VIEWS =
            "SELECT TABLE_NAME, VIEW_DEFINITION, CHECK_OPTION, IS_UPDATABLE, DEFINER, SECURITY_TYPE"
            + " FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";

    static final String ROUTINES =
            "SELECT ROUTINE_NAME, ROUTINE_TYPE, DTD_IDENTIFIER"
            + " FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = ? ORDER BY ROUTINE_TYPE, ROUTINE_NAME";

    static final String TRIGGERS =
            "SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_TIMING, EVENT_MANIPULATION"
            + " FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ? ORDER BY TRIGGER_NAME";

    static final String EVENTS =
            "SELECT EVENT_NAME, STATUS FROM information_schema.EVENTS WHERE EVENT_SCHEMA = ? ORDER BY EVENT_NAME";

    static final String COLLATIONS =
            "SELECT COLLATION_NAME, CHARACTER_SET_NAME, IS_DEFAULT"
            + " FROM information_schema.COLLATIONS ORDER BY CHARACTER_SET_NAME, COLLATION_NAME";

    /**
     * MariaDB 10.10 e successivi: le collation UCA 14 ({@code utf8mb4_uca1400_ai_ci}, la predefinita di utf8mb4 in
     * 11.x) valgono per più set di caratteri e in {@code COLLATIONS} compaiono con il nome corto e senza set; il nome
     * completo e la predefinita di ogni set stanno qui.
     */
    static final String COLLATIONS_MARIADB =
            "SELECT FULL_COLLATION_NAME, CHARACTER_SET_NAME, IS_DEFAULT"
            + " FROM information_schema.COLLATION_CHARACTER_SET_APPLICABILITY ORDER BY CHARACTER_SET_NAME,"
            + " FULL_COLLATION_NAME";

    private MetadataQueries() {
    }

    /** Esegue una lettura con parametri e restituisce le righe come testi ({@code null} = NULL). */
    static List<String[]> rows(Connection connection, String sql, String... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                int n = rs.getMetaData().getColumnCount();
                List<String[]> out = new ArrayList<>();
                while (rs.next()) {
                    String[] row = new String[n];
                    for (int c = 0; c < n; c++) {
                        row[c] = rs.getString(c + 1);
                    }
                    out.add(row);
                }
                return out;
            }
        }
    }

    /**
     * Testo di {@code SHOW CREATE <kind> `catalogo`.`nome`}: la colonna «Create …» (o «SQL Original Statement»
     * per i trigger). {@code null} se l'oggetto non c'è o se l'utente non ha il permesso di leggerne il testo.
     *
     * @param kind {@code TABLE}, {@code VIEW}, {@code PROCEDURE}, {@code FUNCTION}, {@code TRIGGER}, {@code EVENT}
     */
    static String showCreate(Connection connection, String kind, String catalog, String name) throws SQLException {
        String sql = "SHOW CREATE " + kind + " " + quote(catalog) + "." + quote(name);
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            ResultSetMetaData md = rs.getMetaData();
            for (int c = 1; c <= md.getColumnCount(); c++) {
                String label = md.getColumnLabel(c);
                if (label.startsWith("Create ") || label.equals("SQL Original Statement")) {
                    return rs.getString(c);
                }
            }
            return null;
        }
    }

    /** Testo esatto dell'istruzione {@code SHOW CREATE} usata da {@link #showCreate}: per mostrarla a richiesta. */
    static String showCreateText(String kind, String catalog, String name) {
        return "SHOW CREATE " + kind + " " + quote(catalog) + "." + quote(name);
    }

    private static String quote(String name) {
        return "`" + name.replace("`", "``") + "`";
    }
}
