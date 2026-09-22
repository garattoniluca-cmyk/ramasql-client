/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.qb.QbSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Spike S2d, parte I (docs/ROADMAP.md Step 1, docs/FEASIBILITY.md F-05bis): query nidificate sui due server.
 * Per ogni costrutto si eseguono l'SQL ORIGINALE e quello RIGENERATO dal parser del query builder
 * ({@link QbSql#check}): stesse righe tra originale e rigenerato, e tra MariaDB e MySQL. Per i costrutti
 * «solo testo» il parser deve dirlo (non rappresentabile) e l'originale deve girare comunque. In più due viste
 * create sul server e rilette da {@code information_schema}: una vista su un'altra vista e una vista con sottoquery.
 *
 * <p>Evidenza: {@code test-results/step1/S2d-server-esiti.md}.
 */
@Tag("step1")
@Tag("it")
class S2dNidificateSuiServerTest {

    /** {@code rappresentabile}: esito di {@code QbSql.check} osservato nello spike e fissato contro le regressioni. */
    record Caso(String costrutto, String sql, boolean rappresentabile) {
    }

    static final List<Caso> CASI = List.of(
            new Caso("WHERE … IN (SELECT)",
                    "SELECT s.cognome, s.nome FROM soci s WHERE s.id IN"
                            + " (SELECT p.id_socio FROM prestiti p WHERE p.data_restituzione IS NULL)", true),
            new Caso("WHERE … NOT IN (SELECT)",
                    "SELECT l.titolo FROM libri l WHERE l.id NOT IN (SELECT p.id_libro FROM prestiti p)", true),
            new Caso("WHERE EXISTS (SELECT correlata)",
                    "SELECT a.cognome FROM autori a WHERE EXISTS"
                            + " (SELECT la.id_libro FROM libri_autori la WHERE la.id_autore = a.id)", true),
            new Caso("confronto con (SELECT MAX…)",
                    "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo = (SELECT MAX(l2.prezzo) FROM libri l2)", true),
            new Caso("confronto con (SELECT AVG…)",
                    "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo > (SELECT AVG(l2.prezzo) FROM libri l2)", true),
            new Caso("sottoquery nella lista SELECT",
                    "SELECT e.nome, (SELECT COUNT(l.id) FROM libri l WHERE l.id_editore = e.id) AS quanti_libri"
                            + " FROM editori e", true),
            new Caso("tabella derivata in FROM",
                    "SELECT t.id_editore, t.prezzo_medio FROM (SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio"
                            + " FROM libri l GROUP BY l.id_editore) t WHERE t.prezzo_medio > 12", true),
            new Caso("CTE WITH (riferita con alias)",
                    "WITH recenti AS (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 1990)"
                            + " SELECT r.titolo FROM recenti r ORDER BY r.titolo", false),
            new Caso("CTE WITH (riferita senza alias)",
                    "WITH recenti AS (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 1990)"
                            + " SELECT recenti.titolo FROM recenti ORDER BY recenti.titolo", false),
            new Caso("due livelli di annidamento",
                    "SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.id_libro IN"
                            + " (SELECT l.id FROM libri l WHERE l.anno < 1960))", true));

    /** Viste nidificate: nome → SELECT scritto «dall'utente» (la sorgente locale del livello 1). */
    static final Map<String, String> VISTE = new LinkedHashMap<>();

    static {
        VISTE.put("v_libri_editori",
                "SELECT l.titolo, l.anno, e.nome AS editore FROM libri l INNER JOIN editori e ON l.id_editore = e.id");
        VISTE.put("v_su_vista",
                "SELECT v.titolo, v.editore FROM v_libri_editori v WHERE v.anno > 1970");
        VISTE.put("v_con_sottoquery",
                "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo > (SELECT AVG(l2.prezzo) FROM libri l2)");
    }

    private static final Map<ItServers, TestCatalog> CATALOGHI = new EnumMap<>(ItServers.class);
    /** chiave «costrutto» → server → celle della tabella degli esiti. */
    private static final Map<String, Map<ItServers, String>> ESITI = new LinkedHashMap<>();

    @BeforeAll
    static void creaCataloghi() throws SQLException {
        try {
            for (ItServers s : ItServers.values()) {
                TestCatalog c = TestCatalog.create(s, "s2d");
                CATALOGHI.put(s, c);
                c.runScript(Righe.FIXTURE);
                for (Map.Entry<String, String> v : VISTE.entrySet()) {
                    c.execute("CREATE VIEW `" + v.getKey() + "` AS " + v.getValue());
                }
            }
        } catch (SQLException | RuntimeException | Error e) {
            try {
                distruggiCataloghi();
            } catch (RuntimeException | Error pulizia) {
                e.addSuppressed(pulizia);
            }
            throw e;
        }
    }

    @AfterAll
    static void distruggiCataloghiEScriviEsiti() {
        try {
            scriviEsiti();
        } finally {
            distruggiCataloghi();
        }
    }

    /** Ogni catalogo si distrugge anche se la distruzione di un altro fallisce; gli errori si riportano tutti. */
    private static void distruggiCataloghi() {
        List<TestCatalog> tutti = new ArrayList<>(CATALOGHI.values());
        CATALOGHI.clear();
        Righe.chiudiTutti(tutti);
    }

    // ------------------------------------------------------------------ costrutti

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void originaleERigeneratoDannoLeStesseRighe(ItServers server) {
        TestCatalog qui = CATALOGHI.get(server);
        TestCatalog la = CATALOGHI.get(altro(server));
        List<String> difetti = new ArrayList<>();
        int rigeneratiUguali = 0;

        for (Caso caso : CASI) {
            StringBuilder cella = new StringBuilder();
            try {
                List<List<String>> originale = Righe.ordinate(Righe.leggi(qui.connection(), caso.sql()));
                List<List<String>> originaleAltrove = Righe.ordinate(Righe.leggi(la.connection(), caso.sql()));
                cella.append("originale: ").append(originale.size()).append(" righe");
                if (originale.isEmpty()) {
                    difetti.add(caso.costrutto() + ": il risultato è vuoto, il confronto non prova nulla");
                }
                if (!originale.equals(originaleAltrove)) {
                    difetti.add(caso.costrutto() + ": righe diverse tra " + server.label() + " e " + altro(server).label());
                    cella.append("; ≠ ").append(altro(server).label());
                } else {
                    cella.append("; = ").append(altro(server).label());
                }

                QbSql.Result r = QbSql.check(caso.sql());
                if (r.representable() != caso.rappresentabile()) {
                    difetti.add(caso.costrutto() + ": rappresentabile=" + r.representable() + " ma atteso "
                            + caso.rappresentabile() + " (" + r.reason() + ")");
                }
                if (r.representable()) {
                    List<List<String>> rigenerato = Righe.ordinate(Righe.leggi(qui.connection(), r.regenerated()));
                    if (rigenerato.equals(originale)) {
                        rigeneratiUguali++;
                        cella.append("; **grafico**: rigenerato = originale");
                    } else {
                        difetti.add(caso.costrutto() + ": il rigenerato dà righe diverse dall'originale su " + server.label());
                        cella.append("; rigenerato ≠ originale");
                    }
                } else {
                    cella.append("; **solo testo** (il parser lo dichiara: ").append(r.reason()).append(')');
                    if (r.regenerated() != null) {
                        // informativo: la riscrittura del modello è comunque una query valida ed equivalente?
                        cella.append("; riscrittura del modello: ").append(provaRiscrittura(qui, r.regenerated(), originale));
                    }
                }
            } catch (SQLException | RuntimeException e) {
                difetti.add(caso.costrutto() + " su " + server.label() + ": " + e);
                cella.append("; ERRORE: ").append(e.getMessage());
            }
            registra(caso.costrutto(), server, cella.toString());
        }

        assertTrue(difetti.isEmpty(), "difetti su " + server.label() + ":\n" + String.join("\n", difetti));
        long attesiGrafici = CASI.stream().filter(Caso::rappresentabile).count();
        assertEquals(attesiGrafici, rigeneratiUguali, "costrutti il cui SQL rigenerato dà le stesse righe dell'originale");
    }

    private static String provaRiscrittura(TestCatalog c, String sql, List<List<String>> originale) {
        try {
            return Righe.ordinate(Righe.leggi(c.connection(), sql)).equals(originale)
                    ? "eseguibile, stesse righe" : "eseguibile ma righe DIVERSE";
        } catch (SQLException e) {
            return "non eseguibile (" + e.getMessage() + ")";
        }
    }

    // ------------------------------------------------------------------ viste nidificate

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void visteNidificateRiletteDalServer(ItServers server) throws SQLException {
        TestCatalog qui = CATALOGHI.get(server);
        TestCatalog la = CATALOGHI.get(altro(server));
        List<String> difetti = new ArrayList<>();

        for (String vista : List.of("v_su_vista", "v_con_sottoquery")) {
            StringBuilder cella = new StringBuilder();
            List<List<String>> righeVista = Righe.ordinate(Righe.leggi(qui.connection(), "SELECT * FROM `" + vista + "`"));
            List<List<String>> righeAltrove = Righe.ordinate(Righe.leggi(la.connection(), "SELECT * FROM `" + vista + "`"));
            cella.append("vista: ").append(righeVista.size()).append(" righe");
            if (righeVista.isEmpty()) {
                difetti.add(vista + ": vista vuota, il confronto non prova nulla");
            }
            if (!righeVista.equals(righeAltrove)) {
                difetti.add(vista + ": righe diverse tra i due server");
            } else {
                cella.append("; = ").append(altro(server).label());
            }

            // livello 1: la sorgente scritta dall'utente si riapre nel query builder
            QbSql.Result sorgente = QbSql.check(VISTE.get(vista));
            if (!sorgente.representable()) {
                difetti.add(vista + ": la sorgente locale non è rappresentabile: " + sorgente.reason());
            }
            // livello 2: definizione riletta dal server, normalizzata, data al parser
            String definizione = definizione(qui, vista);
            String normalizzata = ViewDefinitionNormalizer.normalize(definizione, qui.name());
            QbSql.Result r = QbSql.check(normalizzata);
            if (r.representable()) {
                List<List<String>> rigenerato = Righe.ordinate(Righe.leggi(qui.connection(), r.regenerated()));
                if (rigenerato.equals(righeVista)) {
                    cella.append("; **grafico** dalla definizione del server: rigenerato = vista");
                } else {
                    difetti.add(vista + ": SQL rigenerato dalla definizione del server ≠ vista");
                    cella.append("; rigenerato ≠ vista");
                }
            } else {
                difetti.add(vista + ": definizione del server non riaperta graficamente: " + r.reason()
                        + " — normalizzata: " + normalizzata);
                cella.append("; **solo testo**: ").append(r.reason());
            }
            cella.append("; definizione del server: `").append(definizione.replace("|", "\\|")).append('`');
            registra("VISTA " + vista + " — `" + VISTE.get(vista) + "`", server, cella.toString());
        }
        assertTrue(difetti.isEmpty(), "difetti su " + server.label() + ":\n" + String.join("\n", difetti));
    }

    static String definizione(TestCatalog c, String vista) throws SQLException {
        try (Statement st = c.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = '"
                     + c.name() + "' AND TABLE_NAME = '" + vista + "'")) {
            assertTrue(rs.next(), "vista non trovata in information_schema: " + vista);
            String def = rs.getString(1);
            assertTrue(def != null && !def.isBlank(), "definizione vuota per " + vista);
            return def;
        }
    }

    // ------------------------------------------------------------------ tabella degli esiti

    private static ItServers altro(ItServers s) {
        return s == ItServers.MARIADB ? ItServers.MYSQL : ItServers.MARIADB;
    }

    private static synchronized void registra(String riga, ItServers server, String cella) {
        ESITI.computeIfAbsent(riga, k -> new EnumMap<>(ItServers.class)).put(server, cella);
    }

    private static synchronized void scriviEsiti() {
        StringBuilder md = new StringBuilder();
        md.append("# S2d (parte I) — query nidificate eseguite su MariaDB e MySQL\n\n");
        md.append("Generato da `").append(S2dNidificateSuiServerTest.class.getName()).append("`. Fixture: `biblioteca-mini.sql`.\n\n");
        md.append("Per ogni costrutto: l'SQL originale gira sui due server con le stesse righe; se il parser lo dichiara\n");
        md.append("rappresentabile (**grafico**), l'SQL rigenerato dal modello dà le stesse righe dell'originale;\n");
        md.append("altrimenti (**solo testo**) il parser lo dice e l'originale resta eseguibile: nessuna perdita.\n\n");
        md.append("| Costrutto / vista | SQL | MariaDB | MySQL |\n|---|---|---|---|\n");
        Map<String, String> sqlPerCostrutto = new LinkedHashMap<>();
        CASI.forEach(c -> sqlPerCostrutto.put(c.costrutto(), c.sql()));
        for (Map.Entry<String, Map<ItServers, String>> e : ESITI.entrySet()) {
            String sql = sqlPerCostrutto.get(e.getKey());
            md.append("| ").append(e.getKey().replace("|", "\\|"))
                    .append(" | ").append(sql == null ? "" : "`" + sql.replace("|", "\\|") + "`")
                    .append(" | ").append(e.getValue().getOrDefault(ItServers.MARIADB, "—").replace("\n", " "))
                    .append(" | ").append(e.getValue().getOrDefault(ItServers.MYSQL, "—").replace("\n", " "))
                    .append(" |\n");
        }
        TestResults.write("step1", "S2d-server-esiti.md", md.toString());
    }
}
