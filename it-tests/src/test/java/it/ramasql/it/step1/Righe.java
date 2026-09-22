/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import it.ramasql.it.TestCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Risultato di una SELECT in forma confrontabile tra server diversi: ogni valore è una stringa
 * canonica (numeri decimali senza zeri finali, NULL = «∅»). Usato dagli spike S2a, S2c, S2d.
 */
final class Righe {

    /** Fixture comune ai tre spike. */
    static final String FIXTURE = "/it/ramasql/it/step1/biblioteca-mini.sql";

    static final String NULLO = "∅";

    private Righe() {
    }

    /**
     * Distrugge TUTTI i cataloghi indicati: ognuno si chiude nel suo try/catch, così un errore su uno non lascia
     * orfani gli altri; gli errori si accumulano e alla fine si lancia un unico {@link AssertionError} che li
     * contiene tutti (come soppressi). La collezione si svuota in ogni caso.
     */
    static void chiudiTutti(Collection<TestCatalog> cataloghi) {
        List<Throwable> errori = new ArrayList<>();
        List<String> nomi = new ArrayList<>();
        for (TestCatalog c : cataloghi) {
            try {
                c.close();
            } catch (RuntimeException | Error e) {
                errori.add(e);
                nomi.add(c.name() + " su " + c.server().label());
            }
        }
        cataloghi.clear();
        if (!errori.isEmpty()) {
            AssertionError tutti = new AssertionError("cataloghi di test non distrutti: " + String.join(", ", nomi));
            errori.forEach(tutti::addSuppressed);
            throw tutti;
        }
    }

    /** Esegue la query e restituisce le righe nell'ordine del server (le etichette: {@link #intestazioni}). */
    static List<List<String>> leggi(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            List<List<String>> righe = new ArrayList<>();
            while (rs.next()) {
                List<String> riga = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    riga.add(canonico(rs.getObject(i), rs, i));
                }
                righe.add(riga);
            }
            return righe;
        }
    }

    /** Etichette delle colonne del risultato. */
    static List<String> intestazioni(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            List<String> nomi = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                nomi.add(md.getColumnLabel(i));
            }
            return nomi;
        }
    }

    private static String canonico(Object v, ResultSet rs, int i) throws SQLException {
        if (v == null) {
            return NULLO;
        }
        if (v instanceof BigDecimal d) {
            return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Double || v instanceof Float) {
            BigDecimal d = BigDecimal.valueOf(((Number) v).doubleValue());
            return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Number) {
            return v.toString();
        }
        return rs.getString(i);
    }

    /** Copia ordinata (confronto «a insiemi» tra query senza ORDER BY totale). */
    static List<List<String>> ordinate(List<List<String>> righe) {
        List<List<String>> copia = new ArrayList<>(righe);
        copia.sort(Comparator.comparing(r -> String.join("", r)));
        return copia;
    }

    /** Testo leggibile per i file di evidenza. */
    static String testo(List<String> intestazioni, List<List<String>> righe) {
        StringBuilder sb = new StringBuilder();
        if (intestazioni != null) {
            sb.append(String.join(" | ", intestazioni)).append('\n');
        }
        for (List<String> r : righe) {
            sb.append(String.join(" | ", r)).append('\n');
        }
        sb.append("(").append(righe.size()).append(" righe)\n");
        return sb.toString();
    }
}
