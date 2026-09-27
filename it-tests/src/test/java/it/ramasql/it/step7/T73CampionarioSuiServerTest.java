/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.it.step7.Campionario.Caso;
import it.ramasql.qb.QbSql;

/**
 * T7.3 (I, docs/ROADMAP.md Step 7): per ogni query rappresentabile del campionario di T7.2 ({@link Campionario}) si
 * eseguono l'SQL <b>originale</b> e quello <b>rigenerato</b> dal modello del query builder ({@link QbSql#check}) su
 * MariaDB e su MySQL, sulla fixture {@code biblioteca} ({@code it-tests/fixtures/biblioteca.sql}), e si confrontano:
 * stesse intestazioni di colonna e stesse righe — nello stesso ordine se la query ha {@code ORDER BY}, altrimenti come
 * multinsieme. Un test per ogni coppia (query, server).
 *
 * <p>Un catalogo {@code ramasql_test_t73_*} per server, creato una volta e distrutto alla fine anche se qualcosa
 * fallisce. Le query con nomi qualificati {@code biblioteca.tabella} si adattano al nome del catalogo di test
 * (sostituzione del prefisso) prima dell'esecuzione, sia l'originale sia la rigenerata.
 *
 * <p>Un risultato vuoto non prova nulla: ogni query del campionario deve restituire almeno una riga.
 *
 * <p>Evidenza: {@code test-results/step7/T7.3-mariadb.md} e {@code T7.3-mysql.md} (righe lette per query, esito).
 */
@Tag("step7")
@Tag("it")
class T73CampionarioSuiServerTest {

    private static final String NULLO = "∅";
    private static final Pattern PREFISSO_BACKTICK = Pattern.compile("`biblioteca`\\.");
    private static final Pattern PREFISSO_SEMPLICE = Pattern.compile("(?<![\\w`$@.])biblioteca\\.", Pattern.CASE_INSENSITIVE);

    private static final Map<ItServers, TestCatalog> CATALOGHI = new EnumMap<>(ItServers.class);
    private static final Map<ItServers, String> ERRORI_DI_PREPARAZIONE = new EnumMap<>(ItServers.class);
    /** server → numero della query → riga della tabella degli esiti. */
    private static final Map<ItServers, Map<Integer, Esito>> ESITI = new EnumMap<>(ItServers.class);

    private record Esito(Caso caso, int righeOriginale, int righeRigenerata, String confronto, boolean superato,
                         String dettaglio) {
    }

    static Stream<Arguments> coppie() {
        return Stream.of(ItServers.values()).flatMap(s -> Campionario.CASI.stream()
                .filter(Caso::rappresentabile)
                .map(c -> Arguments.of(s, c)));
    }

    @BeforeAll
    static void creaCataloghi() {
        for (ItServers s : ItServers.values()) {
            ESITI.put(s, new TreeMap<>());
            TestCatalog c = null;
            try {
                c = TestCatalog.create(s, "t73");
                CATALOGHI.put(s, c);
                c.runScript("/fixtures/biblioteca.sql");
            } catch (SQLException | RuntimeException | Error e) {
                // il catalogo (se nato) resta in CATALOGHI e lo distrugge @AfterAll; i test di questo server falliscono
                ERRORI_DI_PREPARAZIONE.put(s, String.valueOf(e.getMessage()));
            }
        }
    }

    @AfterAll
    static void scriviEvidenzeEDistruggiCataloghi() {
        try {
            for (ItServers s : ItServers.values()) {
                scriviEvidenza(s);
            }
        } finally {
            List<Throwable> errori = new ArrayList<>();
            for (TestCatalog c : CATALOGHI.values()) {
                try {
                    c.close();
                } catch (RuntimeException | Error e) {
                    errori.add(e);
                }
            }
            CATALOGHI.clear();
            if (!errori.isEmpty()) {
                AssertionError tutti = new AssertionError("cataloghi di test non distrutti");
                errori.forEach(tutti::addSuppressed);
                throw tutti;
            }
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("coppie")
    void originaleERigenerataDannoLeStesseRighe(ItServers server, Caso caso) throws SQLException {
        String confronto = "-";
        int nOriginale = -1;
        int nRigenerata = -1;
        try {
            assertFalse(ERRORI_DI_PREPARAZIONE.containsKey(server),
                    "catalogo di test non preparato su " + server.label() + ": " + ERRORI_DI_PREPARAZIONE.get(server));
            TestCatalog catalogo = CATALOGHI.get(server);
            assertNotNull(catalogo, "catalogo di test assente su " + server.label());

            QbSql.Result r = QbSql.check(caso.sql());
            assertTrue(r.representable(), "la query non è più rappresentabile: " + r.reason());
            boolean ordinata = r.model().getOrderByClause().length > 0;
            confronto = ordinata ? "stesso ordine (ORDER BY)" : "multinsieme";

            String originale = adatta(caso.sql(), catalogo.name());
            String rigenerata = adatta(r.regenerated(), catalogo.name());
            Risultato a = leggi(catalogo.connection(), originale);
            Risultato b = leggi(catalogo.connection(), rigenerata);
            nOriginale = a.righe().size();
            nRigenerata = b.righe().size();

            assertTrue(nOriginale > 0, "l'originale non restituisce righe: il confronto non proverebbe nulla");
            assertEquals(a.intestazioni(), b.intestazioni(), "intestazioni di colonna diverse; rigenerata: " + rigenerata);
            if (ordinata) {
                assertEquals(a.righe(), b.righe(), "righe diverse o in ordine diverso; rigenerata: " + rigenerata);
            } else {
                assertEquals(ordinate(a.righe()), ordinate(b.righe()), "righe diverse; rigenerata: " + rigenerata);
            }
            registra(server, new Esito(caso, nOriginale, nRigenerata, confronto, true, ""));
        } catch (AssertionError | SQLException | RuntimeException e) {
            registra(server, new Esito(caso, nOriginale, nRigenerata, confronto, false, String.valueOf(e.getMessage())));
            throw e;
        }
    }

    /** Sostituisce il catalogo {@code biblioteca} dei nomi qualificati con quello di test. */
    static String adatta(String sql, String catalogo) {
        String conBacktick = PREFISSO_BACKTICK.matcher(sql).replaceAll(Matcher.quoteReplacement("`" + catalogo + "`."));
        return PREFISSO_SEMPLICE.matcher(conBacktick).replaceAll(Matcher.quoteReplacement("`" + catalogo + "`."));
    }

    private record Risultato(List<String> intestazioni, List<List<String>> righe) {
    }

    private static Risultato leggi(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            List<String> intestazioni = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) {
                intestazioni.add(md.getColumnLabel(i));
            }
            List<List<String>> righe = new ArrayList<>();
            while (rs.next()) {
                List<String> riga = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    riga.add(canonico(rs.getObject(i), rs, i));
                }
                righe.add(riga);
            }
            return new Risultato(intestazioni, righe);
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

    private static List<List<String>> ordinate(List<List<String>> righe) {
        List<List<String>> copia = new ArrayList<>(righe);
        copia.sort(Comparator.comparing(r -> String.join("\u0001", r)));
        return copia;
    }

    private static synchronized void registra(ItServers server, Esito esito) {
        ESITI.computeIfAbsent(server, k -> new TreeMap<>()).put(esito.caso().n(), esito);
    }

    private static synchronized void scriviEvidenza(ItServers server) {
        long attese = Campionario.CASI.stream().filter(Caso::rappresentabile).count();
        Map<Integer, Esito> esiti = ESITI.getOrDefault(server, Map.of());
        long superati = esiti.values().stream().filter(Esito::superato).count();
        boolean ok = !ERRORI_DI_PREPARAZIONE.containsKey(server) && superati == attese && esiti.size() == attese;

        StringBuilder md = new StringBuilder();
        md.append("# T7.3 — campionario di T7.2 eseguito su ").append(server.label()).append("\n\n");
        md.append("Generato da `").append(T73CampionarioSuiServerTest.class.getName()).append("`. Fixture: `it-tests/fixtures/biblioteca.sql` ")
                .append("in un catalogo `ramasql_test_t73_*` creato per la prova e poi distrutto.\n\n");
        md.append("Per ogni query rappresentabile: SQL originale e SQL rigenerato dal modello eseguiti sullo stesso catalogo; ")
                .append("stesse intestazioni e stesse righe (nello stesso ordine se c'è ORDER BY, altrimenti come multinsieme). ")
                .append("I nomi `biblioteca.tabella` sono adattati al catalogo di test.\n\n");
        if (ERRORI_DI_PREPARAZIONE.containsKey(server)) {
            md.append("**Preparazione fallita:** ").append(celle(ERRORI_DI_PREPARAZIONE.get(server))).append("\n\n");
        }
        md.append("| # | Costrutto | Righe originale | Righe rigenerata | Confronto | Esito | Dettaglio |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (Esito e : esiti.values()) {
            md.append("| ").append(e.caso().n())
                    .append(" | ").append(celle(e.caso().costrutto()))
                    .append(" | ").append(e.righeOriginale() < 0 ? "-" : String.valueOf(e.righeOriginale()))
                    .append(" | ").append(e.righeRigenerata() < 0 ? "-" : String.valueOf(e.righeRigenerata()))
                    .append(" | ").append(e.confronto())
                    .append(" | ").append(e.superato() ? "ok" : "**FALLITO**")
                    .append(" | ").append(celle(e.dettaglio()))
                    .append(" |\n");
        }
        md.append("\nQuery rappresentabili nel campionario: ").append(attese).append("; eseguite: ").append(esiti.size())
                .append("; superate: ").append(superati).append(".\n\n");
        md.append(ok ? "**Esito: SUPERATO**\n" : "**Esito: FALLITO**\n");
        TestResults.write("step7", "T7.3-" + server.name().toLowerCase(java.util.Locale.ROOT) + ".md", md.toString());
    }

    private static String celle(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\r", "").replace("\n", " ");
    }
}
