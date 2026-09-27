/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import it.ramasql.core.metadata.ViewDefinitionNormalizer;
import it.ramasql.core.sqlgen.ViewDdl;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.qb.QbSql;

/**
 * T8.3 — dieci viste create su MariaDB e su MySQL con il generatore del client ({@link ViewDdl}), rilette da
 * {@code information_schema.VIEWS} e riaperte al <b>livello 2</b>: definizione del server → normalizzatore →
 * parser del query builder. Per le viste rappresentabili, l'SQL <b>rigenerato dal modello</b> si esegue sul server e
 * deve dare le stesse righe della vista (confronto come multinsieme, oppure in ordine se la vista ha ORDER BY).
 * Per ogni vista è fissato l'esito atteso (rappresentabile sì/no): due sono volutamente non rappresentabili (funzione
 * finestra, UNION ALL) e devono restare testo. Tabella degli esiti in {@code test-results/step8/T8.3-esiti.md}.
 */
@Tag("step8")
@Tag("it")
class T83VisteSulServerTest {

    record Vista(String nome, String select, boolean rappresentabile, boolean ordinata) {
        @Override
        public String toString() {
            return nome;
        }
    }

    static final List<Vista> VISTE = List.of(
            new Vista("v01_semplice", "SELECT titolo, anno, prezzo FROM libri WHERE anno >= 1980", true, false),
            new Vista("v02_join", "SELECT l.titolo, e.nome AS editore FROM libri l INNER JOIN editori e"
                    + " ON l.id_editore = e.id", true, false),
            new Vista("v03_prestiti_aperti", "SELECT p.id, l.titolo, s.cognome, p.data_prestito FROM prestiti p"
                    + " INNER JOIN libri l ON p.id_libro = l.id INNER JOIN soci s ON p.id_socio = s.id"
                    + " WHERE p.data_reso IS NULL", true, false),
            new Vista("v04_aggregata", "SELECT e.nome AS editore, COUNT(l.id) AS n_libri, AVG(l.prezzo) AS media"
                    + " FROM editori e INNER JOIN libri l ON l.id_editore = e.id GROUP BY e.nome HAVING COUNT(l.id) >= 2",
                    true, false),
            // sulla fixture ogni autore ha almeno un libro: il LEFT JOIN si prova su libri → editori (anche i NULL)
            new Vista("v05_left", "SELECT l.titolo, e.nome AS editore FROM libri l LEFT JOIN editori e"
                    + " ON l.id_editore = e.id", true, false),
            new Vista("v06_sopra_media", "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo >"
                    + " (SELECT AVG(l2.prezzo) FROM libri l2)", true, false),
            new Vista("v07_funzioni", "SELECT CONCAT(s.nome, ' ', s.cognome) AS socio, YEAR(s.nato_il) AS anno"
                    + " FROM soci s", true, false),
            new Vista("v08_ordinata", "SELECT l.titolo, l.anno FROM libri l ORDER BY l.anno DESC, l.titolo LIMIT 5",
                    true, true),
            new Vista("v09_finestra", "SELECT titolo, ROW_NUMBER() OVER (ORDER BY prezzo DESC, id) AS posizione"
                    + " FROM libri", false, false),
            new Vista("v10_union_all", "SELECT cognome FROM autori UNION ALL SELECT cognome FROM soci", false, false));

    private static final Map<ItServers, TestCatalog> CATALOGHI = new EnumMap<>(ItServers.class);
    private static final Map<String, String> ESITI = new LinkedHashMap<>();

    @BeforeAll
    static void creaCataloghiEViste() throws Exception {
        try {
            for (ItServers s : ItServers.values()) {
                TestCatalog c = TestCatalog.create(s, "t83");
                CATALOGHI.put(s, c);
                c.runScript("/fixtures/biblioteca.sql");
                for (Vista v : VISTE) {
                    c.execute(ViewDdl.createView(c.name(), v.nome(), v.select(), false));
                }
            }
        } catch (Exception | Error e) {
            chiudi();
            throw e;
        }
    }

    @AfterAll
    static void esitiEChiusura() {
        try {
            StringBuilder md = new StringBuilder("# T8.3 — dieci viste sui due server, rilette e riaperte (livello 2)\n\n")
                    .append("Generato da `").append(T83VisteSulServerTest.class.getName()).append("`. Per ogni vista: ")
                    .append("`CREATE VIEW` del generatore del client, `VIEW_DEFINITION` riletta, normalizzata, passata al ")
                    .append("parser; per le rappresentabili l'SQL rigenerato è eseguito e confrontato con le righe della vista.\n\n")
                    .append("| Vista @ server | Esito |\n|---|---|\n");
            ESITI.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v.replace("|", "\\|")).append(" |\n"));
            boolean completo = ESITI.size() == VISTE.size() * ItServers.values().length
                    && ESITI.values().stream().noneMatch(v -> v.startsWith("Esito: FALLITO"));
            md.append(completo ? "\nEsito: SUPERATO\n" : "\nEsito: FALLITO — " + ESITI.size() + " casi registrati su "
                    + VISTE.size() * ItServers.values().length + " (preparazione non riuscita o casi falliti)\n");
            TestResults.write("step8", "T8.3-esiti.md", md.toString());
        } finally {
            chiudi();
        }
    }

    private static void chiudi() {
        for (TestCatalog c : new ArrayList<>(CATALOGHI.values())) {
            c.close();
        }
        CATALOGHI.clear();
    }

    static Stream<Arguments> casi() {
        List<Arguments> out = new ArrayList<>();
        for (Vista v : VISTE) {
            for (ItServers s : ItServers.values()) {
                out.add(Arguments.of(v, s));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} su {1}")
    @MethodSource("casi")
    void rilettaERiapertaAlLivello2(Vista v, ItServers server) throws Exception {
        TestCatalog c = CATALOGHI.get(server);
        String chiave = v.nome() + " @ " + server;
        try {
            // la definizione si rilegge con il canale dei metadati del client (lo stesso di «Modifica vista»)
            it.ramasql.core.metadata.MetadataReader reader = new it.ramasql.core.metadata.MetadataReader(c.connection(),
                    it.ramasql.core.connection.ServerInfo.parse(scalar(c, "SELECT VERSION()")));
            String definizione = reader.view(c.name(), v.nome()).map(it.ramasql.core.metadata.ViewDef::selectSql)
                    .orElse(null);
            assertFalse(definizione == null || definizione.isBlank(), "definizione riletta dal server");
            assertEquals(scalar(c, "SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = '"
                    + c.name() + "' AND TABLE_NAME = '" + v.nome() + "'"), definizione,
                    "il lettore del client restituisce proprio VIEW_DEFINITION");
            String normalizzata = ViewDefinitionNormalizer.normalize(definizione, c.name());
            assertFalse(normalizzata.contains(c.name()), "il catalogo è tolto: " + normalizzata);
            QbSql.Result check = QbSql.check(normalizzata);
            assertEquals(v.rappresentabile(), check.representable(), "rappresentabilità attesa per " + v.nome()
                    + " — normalizzata: " + normalizzata + " — motivo: " + check.reason());
            if (!check.representable()) {
                ESITI.put(chiave, "livello 3 (testo), come atteso: " + check.reason());
                return;
            }
            String rigenerata = check.model().toString(false);
            List<List<String>> dallaVista = righe(c, "SELECT * FROM `" + v.nome() + "`");
            List<List<String>> dallaRigenerata = righe(c, rigenerata);
            assertFalse(dallaVista.isEmpty(), "la vista ha righe sulla fixture");
            if (!v.ordinata()) {
                ordina(dallaVista);
                ordina(dallaRigenerata);
            }
            assertEquals(dallaVista, dallaRigenerata, "l'SQL rigenerato dà le righe della vista");
            ESITI.put(chiave, "livello 2: " + dallaVista.size() + " righe uguali — rigenerata: " + rigenerata);
        } catch (Throwable t) {
            ESITI.put(chiave, "Esito: FALLITO — " + t);
            throw t;
        }
    }

    private static String scalar(TestCatalog c, String sql) throws SQLException {
        try (Statement st = c.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static List<List<String>> righe(TestCatalog c, String sql) throws SQLException {
        List<List<String>> out = new ArrayList<>();
        try (Statement st = c.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> r = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    r.add(rs.getString(i));
                }
                out.add(r);
            }
        }
        return out;
    }

    private static void ordina(List<List<String>> righe) {
        righe.sort((a, b) -> String.join("\u0001", a.stream().map(String::valueOf).toList())
                .compareTo(String.join("\u0001", b.stream().map(String::valueOf).toList())));
    }
}
