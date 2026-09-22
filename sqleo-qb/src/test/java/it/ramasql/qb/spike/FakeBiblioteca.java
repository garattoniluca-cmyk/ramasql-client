/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadati JDBC finti dello schema canonico «biblioteca» (docs/ROADMAP.md), senza alcun database:
 * {@link Connection} e {@link DatabaseMetaData} sono proxy che rispondono solo alle chiamate che il
 * query builder fa davvero (tabelle, colonne, chiavi primarie, chiavi esterne).
 */
final class FakeBiblioteca {

    static final String CATALOGO = "biblioteca";

    /** tabella → colonne (nome, tipo, dimensione); la chiave primaria è in {@link #PK}. */
    private static final Map<String, String[][]> TABELLE = new LinkedHashMap<>();
    private static final Map<String, List<String>> PK = new LinkedHashMap<>();
    /** FK: nome, tabella primaria, colonna primaria, tabella esterna, colonna esterna. */
    private static final String[][] FK = {
            {"fk_libri_editori", "editori", "id", "libri", "id_editore"},
            {"fk_la_libri", "libri", "id", "libri_autori", "id_libro"},
            {"fk_la_autori", "autori", "id", "libri_autori", "id_autore"},
            {"fk_prestiti_libri", "libri", "id", "prestiti", "id_libro"},
            {"fk_prestiti_soci", "soci", "id", "prestiti", "id_socio"},
    };

    static {
        TABELLE.put("autori", new String[][] {{"id", "INT", "10"}, {"cognome", "VARCHAR", "60"}, {"nome", "VARCHAR", "60"},
                {"nazionalita", "VARCHAR", "40"}});
        TABELLE.put("editori", new String[][] {{"id", "INT", "10"}, {"nome", "VARCHAR", "80"}, {"citta", "VARCHAR", "60"}});
        TABELLE.put("libri", new String[][] {{"id", "INT", "10"}, {"titolo", "VARCHAR", "200"}, {"isbn", "CHAR", "13"},
                {"anno", "SMALLINT", "5"}, {"prezzo", "DECIMAL", "8"}, {"id_editore", "INT", "10"}});
        TABELLE.put("libri_autori", new String[][] {{"id_libro", "INT", "10"}, {"id_autore", "INT", "10"}});
        TABELLE.put("soci", new String[][] {{"id", "INT", "10"}, {"tessera", "CHAR", "8"}, {"cognome", "VARCHAR", "60"},
                {"nome", "VARCHAR", "60"}, {"email", "VARCHAR", "120"}, {"nato_il", "DATE", "10"}});
        TABELLE.put("prestiti", new String[][] {{"id", "INT", "10"}, {"id_libro", "INT", "10"}, {"id_socio", "INT", "10"},
                {"data_prestito", "DATE", "10"}, {"data_reso", "DATE", "10"}});
        PK.put("autori", List.of("id"));
        PK.put("editori", List.of("id"));
        PK.put("libri", List.of("id"));
        PK.put("libri_autori", List.of("id_libro", "id_autore"));
        PK.put("soci", List.of("id"));
        PK.put("prestiti", List.of("id"));
    }

    private FakeBiblioteca() {
    }

    static Connection connection() {
        Connection[] self = new Connection[1];
        DatabaseMetaData metaData = proxy(DatabaseMetaData.class, (p, m, a) -> metaData(self[0], m, a));
        self[0] = proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
            case "getMetaData" -> metaData;
            case "getCatalog" -> CATALOGO;
            case "isClosed" -> false;
            case "isValid" -> true;
            case "close" -> null;
            case "toString" -> "FakeBiblioteca.connection";
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> throw new UnsupportedOperationException("Connection." + m.getName());
        });
        return self[0];
    }

    private static Object metaData(Connection connection, Method m, Object[] a) {
        switch (m.getName()) {
            case "getConnection":
                return connection;
            case "getIdentifierQuoteString":
                return "`";
            case "getMaxColumnNameLength":
                return 64;
            case "supportsSchemasInTableDefinitions":
            case "storesUpperCaseIdentifiers":
                return false;
            case "storesLowerCaseIdentifiers":
                return true;
            case "getTableTypes":
                return rows(List.of(new Object[] {"TABLE"}, new Object[] {"VIEW"}));
            case "getTables": {
                List<Object[]> r = new ArrayList<>();
                String pattern = (String) a[2];
                for (String t : TABELLE.keySet()) {
                    if (catalogOk(a[0]) && (pattern == null || pattern.equals("%") || pattern.equalsIgnoreCase(t))) {
                        r.add(new Object[] {CATALOGO, null, t, "TABLE"});
                    }
                }
                return rows(r);
            }
            case "getColumns": {
                List<Object[]> r = new ArrayList<>();
                String[][] cols = catalogOk(a[0]) ? TABELLE.get(lower(a[2])) : null;
                if (cols != null) {
                    int pos = 1;
                    for (String[] c : cols) {
                        Object[] row = new Object[24];
                        row[0] = CATALOGO;
                        row[2] = lower(a[2]);
                        row[3] = c[0];
                        row[5] = c[1];
                        row[6] = Integer.valueOf(c[2]);
                        row[16] = pos++;
                        r.add(row);
                    }
                }
                return rows(r);
            }
            case "getPrimaryKeys": {
                List<Object[]> r = new ArrayList<>();
                List<String> pk = catalogOk(a[0]) ? PK.get(lower(a[2])) : null;
                if (pk != null) {
                    short seq = 1;
                    for (String c : pk) {
                        r.add(new Object[] {CATALOGO, null, lower(a[2]), c, seq++, "PRIMARY"});
                    }
                }
                return rows(r);
            }
            case "getImportedKeys":
                return rows(keys(lower(a[2]), false));
            case "getExportedKeys":
                return rows(keys(lower(a[2]), true));
            case "toString":
                return "FakeBiblioteca.metaData";
            case "hashCode":
                return 42;
            default:
                throw new UnsupportedOperationException("DatabaseMetaData." + m.getName());
        }
    }

    private static boolean catalogOk(Object catalog) {
        return catalog == null || CATALOGO.equalsIgnoreCase(catalog.toString());
    }

    private static String lower(Object o) {
        return o == null ? null : o.toString().toLowerCase();
    }

    /** Righe nel formato di getImportedKeys/getExportedKeys (14 colonne). */
    private static List<Object[]> keys(String table, boolean exported) {
        List<Object[]> r = new ArrayList<>();
        for (String[] fk : FK) {
            if ((exported ? fk[1] : fk[3]).equals(table)) {
                Object[] row = new Object[14];
                row[0] = CATALOGO;
                row[2] = fk[1];
                row[3] = fk[2];
                row[4] = CATALOGO;
                row[6] = fk[3];
                row[7] = fk[4];
                row[8] = (short) 1;
                row[11] = fk[0];
                row[12] = "PRIMARY";
                r.add(row);
            }
        }
        return r;
    }

    private static ResultSet rows(List<Object[]> rows) {
        Iterator<Object[]> it = rows.iterator();
        Object[][] current = new Object[1][];
        boolean[] wasNull = new boolean[1];
        return proxy(ResultSet.class, (p, m, a) -> {
            switch (m.getName()) {
                case "next":
                    if (it.hasNext()) {
                        current[0] = it.next();
                        return true;
                    }
                    return false;
                case "close":
                    return null;
                case "wasNull":
                    return wasNull[0];
                case "getString": {
                    Object v = current[0][(Integer) a[0] - 1];
                    wasNull[0] = v == null;
                    return v == null ? null : v.toString();
                }
                case "getInt": {
                    Object v = current[0][(Integer) a[0] - 1];
                    wasNull[0] = v == null;
                    return v == null ? 0 : ((Number) v).intValue();
                }
                default:
                    throw new UnsupportedOperationException("ResultSet." + m.getName());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(FakeBiblioteca.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
