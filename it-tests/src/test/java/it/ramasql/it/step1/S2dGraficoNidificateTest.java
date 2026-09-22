/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatLightLaf;
import com.sqleo.querybuilder.QbAccessoDiProva;
import com.sqleo.querybuilder.QbAccessoDiProva.NodoDiQuery;
import com.sqleo.querybuilder.QueryActions;
import com.sqleo.querybuilder.QueryBuilder;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.qb.BasicQbHost;
import it.ramasql.qb.QbHost;
import it.ramasql.qb.QbRuntime;
import it.ramasql.qb.QbSql;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Spike S2d, parte GRAFICA (docs/ROADMAP.md, Step 1): per ogni costrutto nidificato rappresentabile, il modello si
 * carica nel pannello {@link QueryBuilder} vero ({@code setQueryModel}), collegato al server con i metadati reali:
 * <ol>
 * <li>nell'albero a sinistra compaiono i nodi della sottoquery, al livello giusto;</li>
 * <li>selezionando il nodo, il diagramma mostra le tabelle DELLA SOTTOQUERY (e tornando alla radice quelle esterne);</li>
 * <li>la sottoquery si MODIFICA dal diagramma (condizione aggiunta sul campo, come «add where condition» del menu
 *     del campo);</li>
 * <li>si rilegge {@code getQueryModel().toString(true)}: l'SQL esterno contiene la modifica dentro la sottoquery,
 *     equivale a un riferimento scritto a mano e, eseguito su MariaDB e su MySQL, dà le righe del riferimento
 *     (diverse da quelle della query di partenza: la modifica conta davvero).</li>
 * </ol>
 * Il pannello si costruisce una volta per server (metadati letti da ciascun server). Evidenze:
 * {@code test-results/step1/S2d-grafico-<costrutto>.png} (costruzione su MariaDB, diagramma della sottoquery dopo la
 * modifica) e {@code S2d-grafico-esiti.md}. La CTE non è qui: il parser la dichiara «solo testo» (S2d parte U).
 */
@Tag("step1")
@Tag("ui")
@Tag("it")
class S2dGraficoNidificateTest {

    /**
     * @param nodo        indice (in profondità) del nodo di query da selezionare nell'albero; 0 = query principale
     * @param livello     livello atteso di quel nodo (1 = sottoquery della principale, 2 = sottoquery della sottoquery)
     * @param tipoNodo    classe attesa del nodo nell'albero
     * @param esterne     entità del diagramma della query principale
     * @param interne     entità del diagramma della sottoquery selezionata
     * @param tabella     tabella e colonna su cui si aggiunge la condizione, nel diagramma della sottoquery
     * @param riferimento la query modificata scritta a mano
     */
    record Caso(String slug, String costrutto, String sql, int nodiAttesi, int nodo, int livello, String tipoNodo,
                List<String> esterne, List<String> interne, String tabella, String colonna, String operatore,
                String valore, String riferimento) {
        @Override
        public String toString() {
            return costrutto;
        }
    }

    static final List<Caso> CASI = List.of(
            new Caso("in", "WHERE … IN (SELECT)",
                    "SELECT s.cognome, s.nome FROM soci s WHERE s.id IN"
                            + " (SELECT p.id_socio FROM prestiti p WHERE p.data_restituzione IS NULL)",
                    2, 1, 1, "ConditionQueryTreeItem", List.of("soci s"), List.of("prestiti p"),
                    "prestiti", "id_libro", ">", "10",
                    "SELECT s.cognome, s.nome FROM soci s WHERE s.id IN"
                            + " (SELECT p.id_socio FROM prestiti p WHERE p.data_restituzione IS NULL AND p.id_libro > 10)"),
            new Caso("not-in", "WHERE … NOT IN (SELECT)",
                    "SELECT l.titolo FROM libri l WHERE l.id NOT IN (SELECT p.id_libro FROM prestiti p)",
                    2, 1, 1, "ConditionQueryTreeItem", List.of("libri l"), List.of("prestiti p"),
                    "prestiti", "id_socio", "=", "1",
                    "SELECT l.titolo FROM libri l WHERE l.id NOT IN (SELECT p.id_libro FROM prestiti p WHERE p.id_socio = 1)"),
            new Caso("exists", "WHERE EXISTS (SELECT correlata)",
                    "SELECT a.cognome FROM autori a WHERE EXISTS"
                            + " (SELECT la.id_libro FROM libri_autori la WHERE la.id_autore = a.id)",
                    2, 1, 1, "ConditionQueryTreeItem", List.of("autori a"), List.of("libri_autori la"),
                    "libri_autori", "id_libro", ">", "15",
                    "SELECT a.cognome FROM autori a WHERE EXISTS"
                            + " (SELECT la.id_libro FROM libri_autori la WHERE la.id_autore = a.id AND la.id_libro > 15)"),
            new Caso("max", "confronto con (SELECT MAX…)",
                    "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo = (SELECT MAX(l2.prezzo) FROM libri l2)",
                    2, 1, 1, "ConditionQueryTreeItem", List.of("libri l"), List.of("libri l2"),
                    "libri", "anno", "<", "1960",
                    "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo ="
                            + " (SELECT MAX(l2.prezzo) FROM libri l2 WHERE l2.anno < 1960)"),
            new Caso("select", "sottoquery nella lista SELECT",
                    "SELECT e.nome, (SELECT COUNT(l.id) FROM libri l WHERE l.id_editore = e.id) AS quanti_libri"
                            + " FROM editori e",
                    2, 1, 1, "QueryTreeItem", List.of("editori e"), List.of("libri l"),
                    "libri", "anno", ">", "1980",
                    "SELECT e.nome, (SELECT COUNT(l.id) FROM libri l WHERE l.id_editore = e.id AND l.anno > 1980)"
                            + " AS quanti_libri FROM editori e"),
            new Caso("derivata", "tabella derivata in FROM",
                    "SELECT t.id_editore, t.prezzo_medio FROM (SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio"
                            + " FROM libri l GROUP BY l.id_editore) t WHERE t.prezzo_medio > 12",
                    2, 1, 1, "DiagramQueryTreeItem", List.of("(derivata) t"), List.of("libri l"),
                    "libri", "anno", ">", "1980",
                    "SELECT t.id_editore, t.prezzo_medio FROM (SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio"
                            + " FROM libri l WHERE l.anno > 1980 GROUP BY l.id_editore) t WHERE t.prezzo_medio > 12"),
            new Caso("due-livelli", "due livelli di annidamento (modifica al secondo livello)",
                    "SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.id_libro IN"
                            + " (SELECT l.id FROM libri l WHERE l.anno < 1960))",
                    3, 2, 2, "ConditionQueryTreeItem", List.of("soci s"), List.of("libri l"),
                    "libri", "prezzo", ">", "11",
                    "SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.id_libro IN"
                            + " (SELECT l.id FROM libri l WHERE l.anno < 1960 AND l.prezzo > 11))"));

    static Stream<Caso> casi() {
        return CASI.stream();
    }

    /** Ciò che il pannello ha mostrato e prodotto per un costrutto, costruito con i metadati di un server. */
    record Costruzione(List<NodoDiQuery> nodi, List<String> diagrammaEsterno, List<String> diagrammaInterno,
                       String sqlDelNodo, List<String> diagrammaEsternoDopo, String sqlSuPiuRighe, String sqlSuUnaRiga,
                       List<String> avvisi) {
    }

    private static final Map<ItServers, TestCatalog> CATALOGHI = new EnumMap<>(ItServers.class);
    private static final Map<String, String> ESITI = new LinkedHashMap<>();

    @BeforeAll
    static void creaCataloghi() throws Exception {
        try {
            for (ItServers s : ItServers.values()) {
                TestCatalog c = TestCatalog.create(s, "s2dg");
                CATALOGHI.put(s, c);
                c.runScript(Righe.FIXTURE);
            }
        } catch (Exception | Error e) {
            try {
                distruggi();
            } catch (RuntimeException | Error pulizia) {
                e.addSuppressed(pulizia);
            }
            throw e;
        }
    }

    @AfterAll
    static void scriviEsitiEDistruggi() {
        try {
            StringBuilder md = new StringBuilder();
            md.append("# S2d (parte grafica) — query nidificate nel pannello del query builder\n\n");
            md.append("Generato da `").append(S2dGraficoNidificateTest.class.getName()).append("`. Per ogni costrutto, ")
                    .append("su ciascun server: modello caricato con `setQueryModel`, nodi della sottoquery nell'albero, ")
                    .append("diagramma della sottoquery selezionata, condizione aggiunta dal diagramma, SQL riletto con ")
                    .append("`toString(true)` ed eseguito sui due server.\n\n");
            md.append("| Costrutto | Server dei metadati | Esito |\n|---|---|---|\n");
            ESITI.forEach((k, v) -> md.append("| ").append(k.replace("|", "\\|").replace(" @ ", " | ")).append(" | ")
                    .append(v.replace("|", "\\|").replace("\n", " ")).append(" |\n"));
            TestResults.write("step1", "S2d-grafico-esiti.md", md.toString());
        } finally {
            distruggi();
        }
    }

    private static void distruggi() {
        List<TestCatalog> tutti = new ArrayList<>(CATALOGHI.values());
        CATALOGHI.clear();
        Righe.chiudiTutti(tutti);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casi")
    void costruitoEModificatoNelPannello(Caso caso) throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");
        TestCatalog mariadb = CATALOGHI.get(ItServers.MARIADB);
        TestCatalog mysql = CATALOGHI.get(ItServers.MYSQL);

        // la query di partenza e il riferimento, sui due server: la modifica deve cambiare davvero il risultato
        Map<ItServers, List<List<String>>> partenza = new EnumMap<>(ItServers.class);
        Map<ItServers, List<List<String>>> attese = new EnumMap<>(ItServers.class);
        for (ItServers s : ItServers.values()) {
            Connection con = CATALOGHI.get(s).connection();
            partenza.put(s, Righe.ordinate(Righe.leggi(con, caso.sql())));
            attese.put(s, Righe.ordinate(Righe.leggi(con, caso.riferimento())));
        }
        assertEquals(attese.get(ItServers.MARIADB), attese.get(ItServers.MYSQL), "riferimento diverso tra i due server");
        assertFalse(attese.get(ItServers.MARIADB).isEmpty(), "riferimento vuoto: il confronto non proverebbe nulla");
        assertNotEquals(partenza.get(ItServers.MARIADB), attese.get(ItServers.MARIADB),
                "la modifica scelta non cambia il risultato: il test non proverebbe nulla");

        for (ItServers metadati : ItServers.values()) {
            String chiave = caso.costrutto() + " @ " + metadati.label();
            ESITI.put(chiave, "FALLITO (vedi il rapporto di surefire)");
            Path png = metadati == ItServers.MARIADB
                    ? TestResults.dir("step1").resolve("S2d-grafico-" + caso.slug() + ".png") : null;
            if (png != null) {
                Files.deleteIfExists(png);
            }
            Costruzione c = costruisci(CATALOGHI.get(metadati), caso, png);
            String dove = " (metadati da " + metadati.label() + ")";

            // 1) nodi della sottoquery nell'albero
            assertEquals(caso.nodiAttesi(), c.nodi().size(), "nodi di query nell'albero" + dove + ": " + c.nodi());
            NodoDiQuery nodo = c.nodi().get(caso.nodo());
            assertEquals(caso.livello(), nodo.livello(), "livello del nodo della sottoquery" + dove + ": " + nodo);
            assertEquals(caso.tipoNodo(), nodo.tipo(), "tipo del nodo della sottoquery" + dove + ": " + nodo);
            // 2) diagramma: prima le tabelle esterne, selezionando il nodo quelle della sottoquery, poi di nuovo le esterne
            assertEquals(Set.copyOf(caso.esterne()), new HashSet<>(c.diagrammaEsterno()), "diagramma della query principale" + dove);
            assertEquals(Set.copyOf(caso.interne()), new HashSet<>(c.diagrammaInterno()),
                    "diagramma dopo aver selezionato il nodo della sottoquery" + dove);
            assertEquals(Set.copyOf(caso.esterne()), new HashSet<>(c.diagrammaEsternoDopo()),
                    "diagramma tornando alla query principale" + dove);
            assertTrue(c.avvisi().isEmpty(), "avvisi del query builder" + dove + ": " + c.avvisi());
            // 3)+4) la modifica fatta nel diagramma della sottoquery arriva nell'SQL esterno
            assertTrue(QbSql.normalize(c.sqlDelNodo()).contains(QbSql.normalize(caso.valore())),
                    "la condizione non è nella sottoquery selezionata" + dove + ": " + c.sqlDelNodo());
            assertEquals(QbSql.normalize(caso.riferimento()), QbSql.normalize(c.sqlSuUnaRiga()),
                    "SQL esterno dopo la modifica ≠ riferimento" + dove + ":\n" + c.sqlSuPiuRighe());
            assertEquals(QbSql.normalize(c.sqlSuUnaRiga()), QbSql.normalize(c.sqlSuPiuRighe()),
                    "toString(true) e toString(false) non equivalenti" + dove);
            QbSql.Result riletto = QbSql.check(c.sqlSuPiuRighe());
            assertTrue(riletto.representable(), "l'SQL modificato non si riapre nel query builder" + dove + ": " + riletto.reason());
            for (ItServers s : ItServers.values()) {
                List<List<String>> righe = Righe.ordinate(Righe.leggi(CATALOGHI.get(s).connection(), c.sqlSuPiuRighe()));
                assertEquals(attese.get(s), righe, "SQL modificato" + dove + " eseguito su " + s.label() + " ≠ riferimento");
            }
            if (png != null) {
                assertTrue(Files.size(png) > 5_000, "immagine troppo piccola: " + png);
            }
            ESITI.put(chiave, "ok: nodi " + c.nodi().stream().map(n -> n.livello() + ":" + n.etichetta()).toList()
                    + "; diagramma esterno " + c.diagrammaEsterno() + ", della sottoquery " + c.diagrammaInterno()
                    + "; condizione aggiunta dal diagramma `" + caso.tabella() + "." + caso.colonna() + " "
                    + caso.operatore() + " " + caso.valore() + "`; SQL riletto `" + c.sqlSuUnaRiga()
                    + "` = riferimento, stesse righe su MariaDB e MySQL (" + attese.get(ItServers.MARIADB).size()
                    + " righe; la query di partenza ne dava " + partenza.get(ItServers.MARIADB).size() + ")"
                    + (png != null ? "; immagine `" + png.getFileName() + "`" : ""));
        }
    }

    // ------------------------------------------------------------------ costruzione sull'EDT

    private static Costruzione costruisci(TestCatalog catalogo, Caso caso, Path png) throws Exception {
        AtomicReference<Costruzione> esito = new AtomicReference<>();
        AtomicReference<Throwable> errore = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                esito.set(costruisciSullEdt(catalogo, caso, png));
            } catch (Throwable t) {
                errore.set(t);
            }
        });
        if (errore.get() != null) {
            throw new IllegalStateException("errore nel query builder (" + caso.costrutto() + ")", errore.get());
        }
        return esito.get();
    }

    private static Costruzione costruisciSullEdt(TestCatalog catalogo, Caso caso, Path png) throws Exception {
        assertTrue(FlatLightLaf.setup(), "FlatLaf chiaro non installabile");
        List<String> avvisi = new ArrayList<>();
        QbHost host = new BasicQbHost() {
            @Override
            public Connection connection() {
                return catalogo.connection();
            }

            @Override
            public String catalog() {
                return catalogo.name();
            }

            @Override
            public void alert(String message) {
                avvisi.add(message);
            }
        };
        QbHost hostPrecedente = QbRuntime.host();
        boolean tutteLeColonne = QueryBuilder.selectAllColumns;
        QbRuntime.setHost(host);
        QueryBuilder.selectAllColumns = false;
        JFrame frame = new JFrame("RamaSQL - spike S2d grafico");
        try {
            QueryBuilder qb = new QueryBuilder(host);
            frame.getContentPane().add(qb);
            Dimension size = new Dimension(host.scale(1280), host.scale(760));
            frame.setSize(size);
            frame.addNotify(); // visualizzabile (peer e layout) senza mostrarlo sul desktop
            frame.getRootPane().setSize(size);
            frame.validate();

            qb.setQueryModel(QbSql.parse(caso.sql()));
            List<NodoDiQuery> nodi = QbAccessoDiProva.nodiDiQuery(qb);
            List<String> esterno = QbAccessoDiProva.entitaNelDiagramma(qb);

            // selezione del nodo della sottoquery: il diagramma passa a quel livello
            QbAccessoDiProva.selezionaNodo(qb, caso.nodo());
            List<String> interno = QbAccessoDiProva.entitaNelDiagramma(qb);
            // modifica dal diagramma della sottoquery: condizione sul campo (menu del campo, «add where condition»)
            QbAccessoDiProva.aggiungiWhere(qb, caso.tabella(), caso.colonna(), caso.operatore(), caso.valore());
            String sqlDelNodo = QbAccessoDiProva.sqlDelNodoSelezionato(qb);

            qb.getActionMap().get(QueryActions.ENTITIES_ARRANGE_GRID).actionPerformed(new ActionEvent(qb, 0, "disponi"));
            frame.getRootPane().setSize(size);
            frame.getRootPane().validate();
            if (png != null) {
                Container content = frame.getContentPane();
                BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                content.paint(g);
                g.dispose();
                Files.createDirectories(png.getParent());
                ImageIO.write(image, "png", png.toFile());
            }

            // ritorno alla query principale
            QbAccessoDiProva.selezionaNodo(qb, 0);
            List<String> esternoDopo = QbAccessoDiProva.entitaNelDiagramma(qb);

            return new Costruzione(nodi, esterno, interno, sqlDelNodo, esternoDopo,
                    qb.getQueryModel().toString(true), qb.getQueryModel().toString(false), avvisi);
        } finally {
            frame.dispose();
            QueryBuilder.selectAllColumns = tutteLeColonne;
            QbRuntime.setHost(hostPrecedente);
        }
    }
}
