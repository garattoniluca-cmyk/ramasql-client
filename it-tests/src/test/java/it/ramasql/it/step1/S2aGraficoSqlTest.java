/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatLightLaf;
import com.sqleo.querybuilder.DiagramLoader;
import com.sqleo.querybuilder.QbAccessoDiProva;
import com.sqleo.querybuilder.QueryActions;
import com.sqleo.querybuilder.QueryBuilder;
import com.sqleo.querybuilder.QueryModel;
import com.sqleo.querybuilder.syntax.QueryTokens;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Spike S2a — «Grafico → SQL»: il query builder VERO, collegato al server con MariaDB Connector/J, costruisce
 * dal diagramma (non dal testo) una query a 4 tabelle con join proposti dalle FK reali, WHERE, GROUP BY con COUNT
 * e ORDER BY. L'SQL generato gira su MariaDB e su MySQL con lo stesso risultato, uguale a quello di una query di
 * riferimento scritta a mano. Conferma anche la mappatura catalogo/schema dei metadati JDBC su server veri.
 *
 * <p>Tutto in-process sull'EDT, senza mostrare finestre: l'immagine è {@code paint} su {@link BufferedImage}.
 * Evidenze: {@code test-results/step1/S2a-<server>.txt} e {@code S2a-qb-<server>.png}.
 */
@Tag("step1")
@Tag("it")
class S2aGraficoSqlTest {

    /** Query di riferimento scritta a mano: l'SQL del diagramma deve dare le stesse righe nello stesso ordine. */
    static final String RIFERIMENTO =
            "SELECT l.titolo, e.nome, COUNT(a.id) AS n_autori"
                    + " FROM libri l"
                    + " JOIN editori e ON l.id_editore = e.id"
                    + " JOIN libri_autori la ON la.id_libro = l.id"
                    + " JOIN autori a ON la.id_autore = a.id"
                    + " WHERE l.anno > 1960"
                    + " GROUP BY l.titolo, e.nome"
                    + " ORDER BY e.nome DESC, l.titolo ASC";

    private static final List<String> TABELLE = List.of("libri", "editori", "libri_autori", "autori");

    /** Ciò che il query builder ha caricato e prodotto. */
    private record Costruita(List<String> tabelle, List<String> colonneLette, List<String> join, String sql,
                             String sqlSuPiuRighe, List<String> avvisi) {
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void queryAQuattroTabelleDalDiagramma(ItServers server) throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");
        ItServers altro = server == ItServers.MARIADB ? ItServers.MYSQL : ItServers.MARIADB;
        String etichetta = server.name().toLowerCase(Locale.ROOT);

        try (TestCatalog qui = TestCatalog.create(server, "s2a"); TestCatalog la = TestCatalog.create(altro, "s2a")) {
            qui.runScript(Righe.FIXTURE);
            la.runScript(Righe.FIXTURE);

            Path png = TestResults.dir("step1").resolve("S2a-qb-" + etichetta + ".png");
            Files.deleteIfExists(png);
            Costruita c = costruisci(qui, png);

            StringBuilder rapporto = new StringBuilder();
            rapporto.append("S2a — query costruita dal diagramma del query builder collegato a ").append(server.label())
                    .append(" (").append(versione(qui.connection())).append(")\n\n");
            rapporto.append("Tabelle nel diagramma: ").append(c.tabelle()).append('\n');
            rapporto.append("Colonne lette dai metadati JDBC:\n");
            c.colonneLette().forEach(r -> rapporto.append("  ").append(r).append('\n'));
            rapporto.append("Join proposti dalle chiavi esterne del server:\n");
            c.join().forEach(r -> rapporto.append("  ").append(r).append('\n'));
            rapporto.append("Avvisi del query builder: ").append(c.avvisi()).append("\n\n");
            rapporto.append("SQL generato (toString(true)):\n").append(c.sqlSuPiuRighe()).append("\n\n");
            rapporto.append("SQL di riferimento scritto a mano:\n").append(RIFERIMENTO).append("\n\n");
            TestResults.write("step1", "S2a-" + etichetta + ".txt", rapporto.toString());

            // --- metadati: tabelle, colonne e FK devono arrivare dal server vero
            assertEquals(new HashSet<>(TABELLE), new HashSet<>(c.tabelle()), "tabelle nel diagramma");
            for (String t : TABELLE) {
                List<String> attese = colonneDalServer(qui, t);
                assertTrue(c.colonneLette().contains(t + ": " + attese),
                        "colonne di " + t + " lette dal QB diverse da information_schema: attese " + attese
                                + ", lette " + c.colonneLette());
            }
            assertEquals(3, c.join().size(), "join proposti dalle FK: " + c.join());
            Set<String> nomiFk = new HashSet<>();
            c.join().forEach(j -> nomiFk.add(j.substring(0, j.indexOf(':'))));
            assertEquals(Set.of("fk_libri_editori", "fk_la_libri", "fk_la_autori"), nomiFk, "nomi delle FK usate per i join");
            assertTrue(c.avvisi().isEmpty(), "avvisi inattesi del query builder: " + c.avvisi());

            // --- forma dell'SQL generato
            String norm = QbSql.normalize(c.sql());
            assertTrue(norm.contains(" join "), "mancano i JOIN: " + c.sql());
            assertTrue(norm.contains(" where "), "manca WHERE: " + c.sql());
            assertTrue(norm.contains("group by"), "manca GROUP BY: " + c.sql());
            assertTrue(norm.contains("order by"), "manca ORDER BY: " + c.sql());
            assertTrue(norm.contains("count("), "manca COUNT: " + c.sql());
            QbSql.Result riletto = QbSql.check(c.sql());
            assertTrue(riletto.representable(), "l'SQL generato dal QB non si riapre nel QB: " + riletto.reason());

            // --- esecuzione sui due server: stesse righe, stesso ordine, uguali al riferimento
            List<List<String>> generatoQui = Righe.leggi(qui.connection(), c.sql());
            List<List<String>> generatoLa = Righe.leggi(la.connection(), c.sql());
            List<List<String>> riferimentoQui = Righe.leggi(qui.connection(), RIFERIMENTO);
            List<List<String>> riferimentoLa = Righe.leggi(la.connection(), RIFERIMENTO);

            rapporto.append("Risultato dell'SQL generato su ").append(server.label()).append(":\n")
                    .append(Righe.testo(Righe.intestazioni(qui.connection(), c.sql()), generatoQui)).append('\n');
            rapporto.append("Risultato dell'SQL generato su ").append(altro.label()).append(":\n")
                    .append(Righe.testo(Righe.intestazioni(la.connection(), c.sql()), generatoLa)).append('\n');
            rapporto.append("Risultato del riferimento su ").append(server.label()).append(": ")
                    .append(riferimentoQui.size()).append(" righe; su ").append(altro.label()).append(": ")
                    .append(riferimentoLa.size()).append(" righe\n");
            rapporto.append("Generato = riferimento su ").append(server.label()).append(": ")
                    .append(generatoQui.equals(riferimentoQui)).append('\n');
            rapporto.append("Generato su ").append(server.label()).append(" = generato su ").append(altro.label())
                    .append(": ").append(generatoQui.equals(generatoLa)).append('\n');
            TestResults.write("step1", "S2a-" + etichetta + ".txt", rapporto.toString());

            assertTrue(generatoQui.size() >= 10, "risultato troppo piccolo per essere significativo: " + generatoQui.size());
            assertEquals(riferimentoQui, generatoQui, "SQL generato ≠ riferimento su " + server.label());
            assertEquals(riferimentoLa, generatoLa, "SQL generato ≠ riferimento su " + altro.label());
            assertEquals(generatoQui, generatoLa, "risultati diversi tra " + server.label() + " e " + altro.label());
            assertEquals(Righe.ordinate(generatoQui), Righe.ordinate(generatoLa), "confronto ordinato tra i due server");

            assertImmagineNonVuota(png);
        }
    }

    // ------------------------------------------------------------------ costruzione sull'EDT

    private static Costruita costruisci(TestCatalog catalogo, Path png) throws Exception {
        AtomicReference<Costruita> esito = new AtomicReference<>();
        AtomicReference<Throwable> errore = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                esito.set(costruisciSullEdt(catalogo, png));
            } catch (Throwable t) {
                errore.set(t);
            }
        });
        if (errore.get() != null) {
            throw new IllegalStateException("errore nel query builder", errore.get());
        }
        return esito.get();
    }

    private static Costruita costruisciSullEdt(TestCatalog catalogo, Path png) throws Exception {
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
        QueryBuilder.selectAllColumns = false; // in SQLeo era una preferenza: qui i campi si scelgono uno per uno
        JFrame frame = new JFrame("RamaSQL - spike S2a");
        try {
            QueryBuilder qb = new QueryBuilder(host);
            frame.getContentPane().add(qb);
            frame.setSize(new Dimension(host.scale(1280), host.scale(760)));
            frame.addNotify(); // visualizzabile (peer e layout) senza mostrarlo sul desktop
            frame.validate();
            qb.setQueryModel(new QueryModel());

            // 1) le tabelle si aggiungono al diagramma come fa il doppio clic nell'elenco degli oggetti:
            //    il loader legge colonne e PK dai metadati JDBC e propone i join dalle FK (autojoin = true)
            for (String t : TABELLE) {
                DiagramLoader.run(DiagramLoader.DEFAULT, qb, new QueryTokens.Table(null, t), true);
            }
            // 2) campi spuntati, espressione di aggregazione, condizione, raggruppamento, ordinamento
            QbAccessoDiProva.seleziona(qb, "libri", "titolo");
            QbAccessoDiProva.seleziona(qb, "editori", "nome");
            QbAccessoDiProva.aggiungiEspressione(qb, "COUNT", "autori", "id", "n_autori");
            QbAccessoDiProva.aggiungiWhere(qb, "libri", "anno", ">", "1960");
            QbAccessoDiProva.aggiungiGroupBy(qb, "libri", "titolo");
            QbAccessoDiProva.aggiungiGroupBy(qb, "editori", "nome");
            QbAccessoDiProva.aggiungiOrderBy(qb, "editori", "nome", false);
            QbAccessoDiProva.aggiungiOrderBy(qb, "libri", "titolo", true);

            qb.getActionMap().get(QueryActions.ENTITIES_ARRANGE_GRID).actionPerformed(new ActionEvent(qb, 0, "disponi"));
            frame.validate();

            Container content = frame.getContentPane();
            BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            content.paint(g);
            g.dispose();
            ImageIO.write(image, "png", png.toFile());

            List<String> colonne = new ArrayList<>();
            for (String t : TABELLE) {
                assertNotNull(QbAccessoDiProva.entita(qb, t), "tabella non caricata nel diagramma: " + t);
                colonne.add(t + ": " + QbAccessoDiProva.colonne(qb, t));
            }
            QueryModel modello = qb.getQueryModel();
            return new Costruita(QbAccessoDiProva.tabelleNelDiagramma(qb), colonne, QbAccessoDiProva.join(qb),
                    modello.toString(false), modello.toString(true), avvisi);
        } finally {
            frame.dispose();
            QueryBuilder.selectAllColumns = tutteLeColonne;
            QbRuntime.setHost(hostPrecedente);
        }
    }

    // ------------------------------------------------------------------ utilità

    private static List<String> colonneDalServer(TestCatalog catalogo, String tabella) throws SQLException {
        List<String> nomi = new ArrayList<>();
        try (Statement st = catalogo.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                     + catalogo.name() + "' AND TABLE_NAME = '" + tabella + "' ORDER BY ORDINAL_POSITION")) {
            while (rs.next()) {
                nomi.add(rs.getString(1));
            }
        }
        return nomi;
    }

    static String versione(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION()")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** L'immagine esiste e non è una tinta unita (almeno 50 colori diversi su una griglia di campioni). */
    private static void assertImmagineNonVuota(Path png) throws Exception {
        assertTrue(Files.size(png) > 10_000, "immagine troppo piccola: " + png);
        BufferedImage img = ImageIO.read(png.toFile());
        Set<Integer> colori = new HashSet<>();
        for (int x = 0; x < img.getWidth(); x += 7) {
            for (int y = 0; y < img.getHeight(); y += 7) {
                colori.add(img.getRGB(x, y));
            }
        }
        assertTrue(colori.size() >= 50, "immagine quasi vuota: solo " + colori.size() + " colori");
    }
}
