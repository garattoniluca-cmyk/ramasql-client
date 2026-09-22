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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Spike S2c — «viste rilette dal server» (docs/FEASIBILITY.md F-06, rischio R-02): il server non restituisce il testo
 * scritto dall'utente ma una sua riscrittura. Si verifica che la strategia a tre livelli sia praticabile:
 * <ol>
 * <li><b>livello 1</b> — sorgente locale (la SELECT scritta in questo client) riaperta dal parser del query builder;</li>
 * <li><b>livello 2</b> — definizione riletta da {@code information_schema.VIEWS}, passata dal
 *     {@link ViewDefinitionNormalizer} e poi dal parser;</li>
 * <li><b>livello 3</b> — ripiego: il testo del server si apre nell'editor SQL così com'è (deve essere eseguibile
 *     e dare le righe della vista).</li>
 * </ol>
 * Campione: 11 viste sulla fixture «biblioteca-mini» sui due server; su MariaDB le due viste REALI di
 * {@code bibliotecasoft} copiate in un catalogo di test (sola lettura sull'originale); su MySQL lo schema reale
 * {@code scuola} copiato, con due viste scritte qui sopra le sue tabelle ({@code scuola} non ha viste proprie).
 *
 * <p>Il test NON pretende che ogni vista si riapra graficamente: pretende nessuna eccezione, nessuna perdita
 * (se non è rappresentabile il parser lo dice, e il livello 3 funziona), che ogni SQL rigenerato dia le righe
 * della vista, e una soglia minima di viste riaperte al livello 2 fissata su quanto osservato nello spike.
 *
 * <p>Evidenze: {@code test-results/step1/S2c-esiti.md}, {@code S2c-definizioni-<server>.md} (campionario per T8.2).
 */
@Tag("step1")
@Tag("it")
class S2cVisteRiletteTest {

    /** Viste sulla fixture: nome → SELECT come la scriverebbe l'utente (sorgente locale del livello 1). */
    static final Map<String, String> VISTE_FIXTURE = new LinkedHashMap<>();

    static {
        VISTE_FIXTURE.put("v01_semplice",
                "SELECT titolo, anno, prezzo FROM libri WHERE anno >= 1980");
        VISTE_FIXTURE.put("v02_join2",
                "SELECT l.titolo, e.nome AS editore FROM libri l INNER JOIN editori e ON l.id_editore = e.id");
        VISTE_FIXTURE.put("v03_join3",
                "SELECT a.cognome, a.nome, l.titolo FROM autori a INNER JOIN libri_autori la ON la.id_autore = a.id"
                        + " INNER JOIN libri l ON la.id_libro = l.id");
        VISTE_FIXTURE.put("v04_alias",
                "SELECT s.id AS codice, s.cognome AS cognome_socio, s.email AS posta FROM soci s");
        VISTE_FIXTURE.put("v05_aggregata",
                "SELECT e.nome AS editore, COUNT(l.id) AS n_libri, AVG(l.prezzo) AS prezzo_medio FROM editori e"
                        + " INNER JOIN libri l ON l.id_editore = e.id GROUP BY e.nome HAVING COUNT(l.id) >= 2");
        VISTE_FIXTURE.put("v06_funzioni",
                "SELECT CONCAT(s.nome, ' ', s.cognome) AS socio, YEAR(s.data_iscrizione) AS anno_iscrizione,"
                        + " IFNULL(s.email, 'nessuna') AS email FROM soci s");
        VISTE_FIXTURE.put("v07_left_join_is_null",
                "SELECT a.cognome, a.nome FROM autori a LEFT JOIN libri_autori la ON la.id_autore = a.id"
                        + " WHERE la.id_libro IS NULL");
        VISTE_FIXTURE.put("v08_sottoquery",
                "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo > (SELECT AVG(l2.prezzo) FROM libri l2)");
        VISTE_FIXTURE.put("v09_vista_su_vista",
                "SELECT v.editore, v.titolo FROM v02_join2 v WHERE v.editore LIKE 'E%'");
        VISTE_FIXTURE.put("v10_order_limit",
                "SELECT l.titolo, l.anno FROM libri l ORDER BY l.anno DESC, l.titolo LIMIT 5");
        VISTE_FIXTURE.put("v11_distinct",
                "SELECT DISTINCT a.nazionalita FROM autori a WHERE a.nazionalita IS NOT NULL");
    }

    /** Viste scritte qui sopra lo schema reale {@code scuola} (MySQL), che non ne ha di proprie. */
    static final Map<String, String> VISTE_SCUOLA = new LinkedHashMap<>();

    static {
        VISTE_SCUOLA.put("v_alunni_classi",
                "SELECT a.cognome, a.nome, c.nome AS classe, c.aula FROM alunni a LEFT JOIN classi c ON a.classe_id = c.id");
        VISTE_SCUOLA.put("v_corsi_per_classe",
                "SELECT c.nome AS classe, COUNT(cc.corso_id) AS n_corsi, SUM(k.ore_settimanali) AS ore FROM classi c"
                        + " INNER JOIN corsi_classi cc ON cc.classe_id = c.id INNER JOIN corsi k ON cc.corso_id = k.id"
                        + " GROUP BY c.nome");
    }

    /**
     * Soglie minime di viste riaperte graficamente al LIVELLO 2 (definizione del server + normalizzatore), fissate su
     * quanto osservato nello spike del 2026-09-21 (11 su 11 su entrambi i server, dopo la correzione del verso dei join
     * nel parser; prima erano 8 su 11): una vista di margine, per accorgersi delle regressioni senza legarsi alla
     * versione del server. Per le viste sugli schemi reali: osservate 1 su 2 (MariaDB, viste reali) e 2 su 2 (MySQL).
     */
    static final int SOGLIA_FIXTURE_LIVELLO_2 = 10;
    static final int SOGLIA_FIXTURE_LIVELLO_1 = 10;
    static final int SOGLIA_REALI_LIVELLO_2 = 1;

    /**
     * Esito ATTESO per vista e per server (oltre alle soglie), fissato su quanto osservato nello spike del 2026-09-21:
     * livello 1 ({@code null} = sorgente non disponibile), livello 2 e, se il livello 2 è «no», un frammento del
     * motivo atteso. Un cambiamento in qualunque direzione fa fallire il test: se una vista smette di riaprirsi è una
     * regressione; se comincia a riaprirsi, l'attesa va aggiornata consapevolmente (non per caso).
     */
    record Atteso(Boolean livello1, boolean livello2, String motivo) { }

    static final Map<String, Atteso> ATTESI = new LinkedHashMap<>();

    static {
        for (ItServers s : ItServers.values()) {
            for (String vista : VISTE_FIXTURE.keySet()) {
                ATTESI.put(s.name() + ":" + vista, new Atteso(true, true, null));
            }
        }
        // viste REALI di bibliotecasoft (MariaDB): la sorgente scritta dall'autore non è disponibile (livello 1 n.d.)
        ATTESI.put("MARIADB:v_statistiche_libri", new Atteso(null, true, null));
        // v_prestiti_dettaglio (JOIN amministratori e LEFT JOIN generi dopo JOIN libri): il modello rigenera i join in
        // un ordine diverso; la riscrittura è equivalente (stesse righe) ma il testo cambia → «non rappresentabile»,
        // si apre nell'editor SQL (livello 3), senza perdita.
        ATTESI.put("MARIADB:v_prestiti_dettaglio", new Atteso(null, false, "l'SQL rigenerato differisce dall'originale"));
        // viste scritte sopra lo schema reale scuola (MySQL)
        for (String vista : VISTE_SCUOLA.keySet()) {
            ATTESI.put("MYSQL:" + vista, new Atteso(true, true, null));
        }
    }

    /** Confronta ogni esito con quello atteso per quella vista su quel server; restituisce le differenze. */
    static List<String> differenzeDagliAttesi(List<Esito> esiti) {
        List<String> diff = new ArrayList<>();
        for (Esito e : esiti) {
            String chiave = e.server().name() + ":" + e.vista();
            Atteso a = ATTESI.get(chiave);
            if (a == null) {
                diff.add(chiave + ": nessun esito atteso fissato");
                continue;
            }
            if (!java.util.Objects.equals(a.livello1(), e.livello1())) {
                diff.add(chiave + ": livello 1 = " + e.livello1() + ", atteso " + a.livello1());
            }
            if (a.livello2() != e.livello2()) {
                diff.add(chiave + ": livello 2 = " + e.livello2() + ", atteso " + a.livello2()
                        + (e.motivo() == null ? "" : " (motivo: " + e.motivo() + ")"));
            }
            if (!a.livello2() && (e.motivo() == null || !e.motivo().contains(a.motivo()))) {
                diff.add(chiave + ": motivo «" + e.motivo() + "», atteso che contenga «" + a.motivo() + "»");
            }
            if (!a.livello2() && (e.riscrittura() == null || !e.riscrittura().startsWith("eseguita"))) {
                diff.add(chiave + ": la riscrittura del modello doveva essere equivalente; è: " + e.riscrittura());
            }
        }
        return diff;
    }

    /** Esito di una vista su un server. */
    record Esito(String gruppo, String vista, ItServers server, String sorgente, String definizione, String normalizzata,
                 Boolean livello1, boolean livello2, boolean livello2SenzaNormalizzatore, String motivo,
                 String riscrittura, boolean livello3, int righe) {
        int livello() {
            return Boolean.TRUE.equals(livello1) ? 1 : livello2 ? 2 : 3;
        }
    }

    private static final Map<ItServers, TestCatalog> FIXTURE = new EnumMap<>(ItServers.class);
    private static final Map<ItServers, TestCatalog> REALE = new EnumMap<>(ItServers.class);
    private static final List<Esito> ESITI = new ArrayList<>();

    @BeforeAll
    static void preparaCataloghi() throws SQLException {
        try {
            for (ItServers s : ItServers.values()) {
                TestCatalog c = TestCatalog.create(s, "s2c");
                FIXTURE.put(s, c);
                c.runScript(Righe.FIXTURE);
                for (Map.Entry<String, String> v : VISTE_FIXTURE.entrySet()) {
                    c.execute("CREATE VIEW `" + v.getKey() + "` AS " + v.getValue());
                }
            }
            TestCatalog biblio = TestCatalog.create(ItServers.MARIADB, "s2c_bibliotecasoft");
            REALE.put(ItServers.MARIADB, biblio);
            copiaBibliotecasoft(biblio);
            TestCatalog scuola = TestCatalog.create(ItServers.MYSQL, "s2c_scuola");
            REALE.put(ItServers.MYSQL, scuola);
            copiaScuola(scuola);
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
    static void scriviEsitiEDistruggi() {
        try {
            scriviEsiti();
        } finally {
            distruggiCataloghi();
        }
    }

    /** Ogni catalogo si distrugge anche se la distruzione di un altro fallisce; gli errori si riportano tutti. */
    private static void distruggiCataloghi() {
        List<TestCatalog> tutti = new ArrayList<>(FIXTURE.values());
        tutti.addAll(REALE.values());
        FIXTURE.clear();
        REALE.clear();
        Righe.chiudiTutti(tutti);
    }

    // ------------------------------------------------------------------ copia degli schemi reali (sola lettura sull'origine)

    /**
     * Copia in {@code dest} la struttura di tutte le tabelle di {@code bibliotecasoft} e le sue DUE viste reali,
     * lette con SHOW CREATE. Dati: si copiano solo {@code generi} e {@code libri} (catalogo dei libri, nessun dato
     * personale); utenti, operatori e prestiti sono righe INVENTATE, così nessun dato personale né hash esce
     * dal catalogo d'origine.
     */
    private static void copiaBibliotecasoft(TestCatalog dest) throws SQLException {
        String origine = "bibliotecasoft";
        copiaTabelle(dest, origine, List.of("generi", "libri"));
        dest.execute(
                "INSERT INTO amministratori (id, nome, cognome, email, password_hash, ruolo)"
                        + " VALUES (1, 'Ada', 'Di Prova', 'ada.diprova@example.org', 'non-usato', 'operatore')",
                "INSERT INTO utenti (id, nome, cognome, email, codice_fiscale) VALUES"
                        + " (1, 'Mario', 'D''Esempio', 'mario.desempio@example.org', 'DSMMRA80A01H501X'),"
                        + " (2, 'Lucia', 'Finta', NULL, NULL)",
                "INSERT INTO prestiti (utente_id, libro_id, admin_id, data_prestito, data_restituzione_prevista,"
                        + " data_restituzione_effettiva, stato, note)"
                        + " SELECT 1, id, 1, '2025-01-10', '2025-02-10', '2025-02-20', 'restituito', 'reso in ritardo'"
                        + " FROM libri ORDER BY id LIMIT 3",
                "INSERT INTO prestiti (utente_id, libro_id, admin_id, data_prestito, data_restituzione_prevista, stato)"
                        + " SELECT 2, id, 1, '2025-03-01', '2025-04-01', 'attivo' FROM libri ORDER BY id DESC LIMIT 2");
        for (String vista : List.of("v_prestiti_dettaglio", "v_statistiche_libri")) {
            String select = selectDiShowCreateView(dest.connection(), origine, vista);
            dest.execute("CREATE VIEW `" + vista + "` AS " + ViewDefinitionNormalizer.stripCatalog(select, origine));
        }
    }

    /** Copia struttura e dati dello schema reale {@code scuola} e ci crea sopra due viste di prova. */
    private static void copiaScuola(TestCatalog dest) throws SQLException {
        copiaTabelle(dest, "scuola", null);
        for (Map.Entry<String, String> v : VISTE_SCUOLA.entrySet()) {
            dest.execute("CREATE VIEW `" + v.getKey() + "` AS " + v.getValue());
        }
    }

    /** @param conDati tabelle di cui copiare anche le righe; {@code null} = tutte */
    private static void copiaTabelle(TestCatalog dest, String origine, List<String> conDati) throws SQLException {
        TestCatalog.requireTestName(dest.name());
        List<String> tabelle = new ArrayList<>();
        try (Statement st = dest.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = '"
                     + origine + "' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")) {
            while (rs.next()) {
                tabelle.add(rs.getString(1));
            }
        }
        assertTrue(!tabelle.isEmpty(), "nessuna tabella leggibile in " + origine + " (permessi di ramasql_test?)");
        dest.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String t : tabelle) {
                String ddl;
                try (Statement st = dest.connection().createStatement();
                     ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + origine + "`.`" + t + "`")) {
                    rs.next();
                    ddl = rs.getString(2);
                }
                dest.execute(ddl); // il DDL non è qualificato: nasce nel catalogo corrente, che è quello di test
                if (conDati == null || conDati.contains(t)) {
                    dest.execute("INSERT INTO `" + dest.name() + "`.`" + t + "` SELECT * FROM `" + origine + "`.`" + t + "`");
                }
            }
        } finally {
            dest.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private static final Pattern CREATE_VIEW_AS = Pattern.compile("(?is)^CREATE\\s.*?\\sVIEW\\s+(?:`[^`]+`\\.)?`[^`]+`\\s+AS\\s+(.*)$");

    /** La SELECT dentro {@code SHOW CREATE VIEW} (senza ALGORITHM/DEFINER/SQL SECURITY, che richiederebbero privilegi). */
    private static String selectDiShowCreateView(Connection con, String catalogo, String vista) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE VIEW `" + catalogo + "`.`" + vista + "`")) {
            assertTrue(rs.next(), "SHOW CREATE VIEW senza righe per " + vista);
            Matcher m = CREATE_VIEW_AS.matcher(rs.getString(2));
            assertTrue(m.matches(), "SHOW CREATE VIEW in forma inattesa per " + vista);
            return m.group(1);
        }
    }

    // ------------------------------------------------------------------ test

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void visteDellaFixtureRiletteDalServer(ItServers server) throws SQLException {
        TestCatalog c = FIXTURE.get(server);
        List<String> difetti = new ArrayList<>();
        List<Esito> esiti = new ArrayList<>();
        for (Map.Entry<String, String> v : VISTE_FIXTURE.entrySet()) {
            esiti.add(valuta("fixture", c, v.getKey(), v.getValue(), difetti));
        }
        registra(esiti);

        // stesse righe sui due server, vista per vista (la fixture è identica)
        TestCatalog altro = FIXTURE.get(server == ItServers.MARIADB ? ItServers.MYSQL : ItServers.MARIADB);
        for (String vista : VISTE_FIXTURE.keySet()) {
            String q = "SELECT * FROM `" + vista + "`";
            if (!Righe.ordinate(Righe.leggi(c.connection(), q)).equals(Righe.ordinate(Righe.leggi(altro.connection(), q)))) {
                difetti.add(vista + ": righe diverse tra MariaDB e MySQL");
            }
        }

        assertTrue(difetti.isEmpty(), "difetti su " + server.label() + ":\n" + String.join("\n", difetti));
        assertEquals(VISTE_FIXTURE.size(), esiti.size());
        List<String> diff = differenzeDagliAttesi(esiti);
        assertTrue(diff.isEmpty(), "esiti diversi da quelli attesi su " + server.label() + ":\n" + String.join("\n", diff));
        long livello1 = esiti.stream().filter(e -> Boolean.TRUE.equals(e.livello1())).count();
        long livello2 = esiti.stream().filter(Esito::livello2).count();
        assertTrue(livello2 >= SOGLIA_FIXTURE_LIVELLO_2, "solo " + livello2 + " viste su " + esiti.size()
                + " riaperte dalla definizione del server su " + server.label() + " (soglia " + SOGLIA_FIXTURE_LIVELLO_2 + ")");
        // la strategia a tre livelli copre tutto: chi non passa da 1 o 2 ha comunque il livello 3
        assertTrue(esiti.stream().allMatch(Esito::livello3), "livello 3 non praticabile per qualche vista");
        assertTrue(livello1 >= SOGLIA_FIXTURE_LIVELLO_1, "solo " + livello1 + " sorgenti locali su " + esiti.size()
                + " riaperte dal parser (soglia " + SOGLIA_FIXTURE_LIVELLO_1 + ")");
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void visteSulloSchemaRealeRiletteDalServer(ItServers server) throws SQLException {
        TestCatalog c = REALE.get(server);
        List<String> difetti = new ArrayList<>();
        List<Esito> esiti = new ArrayList<>();
        if (server == ItServers.MARIADB) {
            // viste REALI: la sorgente scritta dall'autore non esiste più → il livello 1 non è disponibile
            esiti.add(valuta("bibliotecasoft (vista reale)", c, "v_prestiti_dettaglio", null, difetti));
            esiti.add(valuta("bibliotecasoft (vista reale)", c, "v_statistiche_libri", null, difetti));
        } else {
            for (Map.Entry<String, String> v : VISTE_SCUOLA.entrySet()) {
                esiti.add(valuta("scuola (schema reale, vista di prova)", c, v.getKey(), v.getValue(), difetti));
            }
        }
        registra(esiti);
        assertTrue(difetti.isEmpty(), "difetti su " + server.label() + ":\n" + String.join("\n", difetti));
        assertEquals(2, esiti.size());
        assertTrue(esiti.stream().allMatch(e -> e.righe() > 0), "viste vuote: il confronto non prova nulla");
        List<String> diff = differenzeDagliAttesi(esiti);
        assertTrue(diff.isEmpty(), "esiti diversi da quelli attesi su " + server.label() + ":\n" + String.join("\n", diff));
        long livello2 = esiti.stream().filter(Esito::livello2).count();
        assertTrue(livello2 >= SOGLIA_REALI_LIVELLO_2, "nessuna vista dello schema reale riaperta dalla definizione del server su "
                + server.label());
        assertTrue(esiti.stream().allMatch(Esito::livello3), "livello 3 non praticabile per qualche vista");
    }

    /**
     * Valuta una vista. Aggiunge a {@code difetti} solo ciò che è un difetto vero: eccezioni, testo del server non
     * eseguibile, normalizzatore che cambia il significato, SQL rigenerato che dà righe diverse dalla vista.
     */
    private static Esito valuta(String gruppo, TestCatalog c, String vista, String sorgente, List<String> difetti)
            throws SQLException {
        Connection con = c.connection();
        List<List<String>> righeVista = Righe.ordinate(Righe.leggi(con, "SELECT * FROM `" + vista + "`"));
        String definizione = S2dNidificateSuiServerTest.definizione(c, vista);

        // livello 3: il testo del server, così com'è, è una query eseguibile che dà le righe della vista
        boolean livello3 = stesseRighe(con, definizione, righeVista, vista + ": definizione del server", difetti);

        // il normalizzatore non deve cambiare il significato
        String normalizzata = ViewDefinitionNormalizer.normalize(definizione, c.name());
        stesseRighe(con, normalizzata, righeVista, vista + ": definizione NORMALIZZATA", difetti);
        if (normalizzata.toLowerCase(Locale.ROOT).contains(c.name())) {
            difetti.add(vista + ": il normalizzatore ha lasciato il nome del catalogo: " + normalizzata);
        }

        // livello 2: parser sulla definizione normalizzata
        QbSql.Result r2 = QbSql.check(normalizzata);
        boolean livello2 = r2.representable()
                && stesseRighe(con, r2.regenerated(), righeVista, vista + ": SQL rigenerato (livello 2)", difetti);
        // informativo: quando il parser dichiara «non rappresentabile», la riscrittura del suo modello è almeno equivalente?
        String riscrittura = null;
        if (!r2.representable() && r2.regenerated() != null) {
            List<String> scartati = new ArrayList<>();
            riscrittura = stesseRighe(con, r2.regenerated(), righeVista, "riscrittura", scartati)
                    ? "eseguita: stesse righe della vista (equivalente, ma il testo cambia)"
                    : "NON equivalente: " + String.join("; ", scartati);
        }
        // informativo: e senza normalizzatore?
        QbSql.Result grezzo = QbSql.check(definizione);
        boolean senzaNormalizzatore = grezzo.representable();

        // livello 1: sorgente locale
        Boolean livello1 = null;
        if (sorgente != null) {
            QbSql.Result r1 = QbSql.check(sorgente);
            livello1 = r1.representable()
                    && stesseRighe(con, r1.regenerated(), righeVista, vista + ": SQL rigenerato (livello 1)", difetti);
        }
        return new Esito(gruppo, vista, c.server(), sorgente, definizione, normalizzata, livello1, livello2,
                senzaNormalizzatore, r2.representable() ? null : r2.reason(), riscrittura, livello3, righeVista.size());
    }

    private static boolean stesseRighe(Connection con, String sql, List<List<String>> attese, String cosa, List<String> difetti) {
        try {
            if (Righe.ordinate(Righe.leggi(con, sql)).equals(attese)) {
                return true;
            }
            difetti.add(cosa + " dà righe diverse dalla vista — " + sql);
        } catch (SQLException e) {
            difetti.add(cosa + " non eseguibile (" + e.getMessage() + ") — " + sql);
        }
        return false;
    }

    // ------------------------------------------------------------------ evidenze

    private static synchronized void registra(List<Esito> esiti) {
        ESITI.addAll(esiti);
    }

    private static synchronized void scriviEsiti() {
        StringBuilder md = new StringBuilder();
        md.append("# S2c — viste rilette dal server e riaperte nel query builder\n\n");
        md.append("Generato da `").append(S2cVisteRiletteTest.class.getName()).append("`.\n\n");
        md.append("- **L1** sorgente locale riaperta dal parser (n.d. = vista nata altrove, sorgente non disponibile);\n");
        md.append("- **L2** definizione di `information_schema.VIEWS` → `ViewDefinitionNormalizer` → parser; «sì» vuol dire anche che\n");
        md.append("  l'SQL rigenerato dal modello, eseguito, dà le stesse righe della vista;\n");
        md.append("- **L2 senza norm.** lo stesso senza normalizzatore (per misurare quanto serve);\n");
        md.append("- **L3** il testo del server è eseguibile così com'è e dà le righe della vista (ripiego nell'editor SQL);\n");
        md.append("- **Apertura**: il livello più alto che la strategia a tre livelli userebbe.\n\n");
        md.append("| Gruppo | Vista | Server | Righe | L1 | L2 | L2 senza norm. | L3 | Apertura | Motivo se L2 = no | Riscrittura del modello |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Esito e : ESITI) {
            md.append("| ").append(e.gruppo()).append(" | `").append(e.vista()).append("` | ").append(e.server().label())
                    .append(" | ").append(e.righe())
                    .append(" | ").append(e.livello1() == null ? "n.d." : siNo(e.livello1()))
                    .append(" | ").append(siNo(e.livello2()))
                    .append(" | ").append(siNo(e.livello2SenzaNormalizzatore()))
                    .append(" | ").append(siNo(e.livello3()))
                    .append(" | ").append(e.livello() == 3 ? "editor SQL (L3)" : "grafica (L" + e.livello() + ")")
                    .append(" | ").append(e.motivo() == null ? "" : e.motivo().replace("|", "\\|").replace("\n", " "))
                    .append(" | ").append(e.riscrittura() == null ? "" : e.riscrittura().replace("|", "\\|").replace("\n", " "))
                    .append(" |\n");
        }
        md.append('\n');
        for (ItServers s : ItServers.values()) {
            long tot = ESITI.stream().filter(e -> e.server() == s).count();
            long l2 = ESITI.stream().filter(e -> e.server() == s && e.livello2()).count();
            long grezzo = ESITI.stream().filter(e -> e.server() == s && e.livello2SenzaNormalizzatore()).count();
            long grafica = ESITI.stream().filter(e -> e.server() == s && e.livello() < 3).count();
            md.append("**").append(s.label()).append(":** ").append(l2).append(" viste su ").append(tot)
                    .append(" riaperte dalla definizione del server (senza normalizzatore: ").append(grezzo)
                    .append("); con la sorgente locale l'apertura grafica copre ").append(grafica).append(" su ").append(tot)
                    .append("; le altre vanno nell'editor SQL senza perdita.\n\n");
        }
        md.append("Esito atteso fissato nel test PER VISTA e PER SERVER (`ATTESI`, oltre alle soglie): viste della fixture\n")
                .append("L1 = L2 = sì su entrambi i server; `v_statistiche_libri` (MariaDB, reale) L2 = sì; `v_prestiti_dettaglio`\n")
                .append("(MariaDB, reale) L2 = no, motivo «l'SQL rigenerato differisce dall'originale»: il modello rigenera i tre\n")
                .append("join in un ordine diverso, la riscrittura dà le stesse righe ma il testo cambia, quindi la vista si apre\n")
                .append("nell'editor SQL (L3); viste su `scuola` (MySQL) L1 = L2 = sì. Differenze dall'atteso in questa esecuzione: ")
                .append(differenzeDagliAttesi(ESITI).size()).append(".\n\n");
        md.append("Soglie fissate nel test (per server): viste della fixture al livello 2 ≥ ").append(SOGLIA_FIXTURE_LIVELLO_2)
                .append(" su ").append(VISTE_FIXTURE.size()).append(", al livello 1 ≥ ").append(SOGLIA_FIXTURE_LIVELLO_1)
                .append("; viste sugli schemi reali al livello 2 ≥ ").append(SOGLIA_REALI_LIVELLO_2).append(" su 2.\n");
        TestResults.write("step1", "S2c-esiti.md", md.toString());

        for (ItServers s : ItServers.values()) {
            StringBuilder d = new StringBuilder();
            d.append("# S2c — definizioni di vista rilette da ").append(s.label()).append(" (campionario per T8.2)\n\n");
            d.append("`VIEW_DEFINITION` di `information_schema.VIEWS`, testo esatto del server. Il nome del catalogo di test\n");
            d.append("(`ramasql_test_…`) è casuale a ogni esecuzione.\n\n");
            for (Esito e : ESITI) {
                if (e.server() != s) {
                    continue;
                }
                d.append("## ").append(e.vista()).append(" — ").append(e.gruppo()).append("\n\n");
                d.append("Sorgente scritta dall'utente:\n\n```sql\n")
                        .append(e.sorgente() == null ? "-- non disponibile (vista nata altrove)" : e.sorgente()).append("\n```\n\n");
                d.append("Definizione riletta dal server:\n\n```sql\n").append(e.definizione()).append("\n```\n\n");
                d.append("Dopo `ViewDefinitionNormalizer` (bozza):\n\n```sql\n").append(e.normalizzata()).append("\n```\n\n");
                d.append("Parser del query builder sulla normalizzata: ")
                        .append(e.livello2() ? "rappresentabile" : "NON rappresentabile — " + e.motivo()).append("\n\n");
            }
            TestResults.write("step1", "S2c-definizioni-" + s.name().toLowerCase(Locale.ROOT) + ".md", d.toString());
        }
    }

    private static String siNo(boolean b) {
        return b ? "sì" : "no";
    }
}
