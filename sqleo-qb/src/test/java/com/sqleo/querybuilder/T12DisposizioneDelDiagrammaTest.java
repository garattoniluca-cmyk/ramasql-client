/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.ramasql.qb.QbHost;
import it.ramasql.qb.QbRuntime;
import it.ramasql.qb.QbSql;

/**
 * {@code BUG-023} (Step 12): con più tabelle collegate nessuna linea di join e nessun nodo di join sta dentro il
 * rettangolo di un'entità che non è un suo capo (il nodo: di nessuna entità), e le entità non si sovrappongono.
 *
 * <p>Il controllo è geometrico e indipendente dal codice della disposizione: ogni join si <b>disegna davvero</b> (lo
 * stesso {@code paintChildren} che usa lo schermo) su un'immagine trasparente grande quanto il suo componente, e ogni
 * pixel disegnato si riporta nelle coordinate del diagramma e si confronta con i rettangoli delle entità (ristretti di
 * 1 px, per l'antialiasing sul bordo dei due capi). Il nodo è il suo riquadro. Si prova con i join ad archi (il modo
 * del programma) e a linee spezzate, con tabelle aggiunte una alla volta in due ordini diversi (fra cui quello della
 * scena di {@code T7.9-100-*.png}), con un modello caricato da SQL, con un ciclo di join (due tabelle della stessa
 * colonna unite fra loro), e con una tabella aggiunta dopo che l'utente ha sistemato il diagramma da sé. Un controllo
 * preliminare rimette le tre tabelle di {@code T7.9} in fila come faceva il codice ereditato e verifica che il
 * controllo veda il difetto. La disposizione non cambia l'SQL: lo si confronta prima e dopo aver ridisposto.
 */
@Tag("step12")
@Tag("ui")
class T12DisposizioneDelDiagrammaTest {

    private QbHost prima;
    private BibliotecaFinta.Host host;
    private JFrame frame;
    private QueryBuilder qb;
    private final StringBuilder evidenza = new StringBuilder();

    @BeforeEach
    void apri() {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");
        prima = QbRuntime.processHost();
    }

    @AfterEach
    void chiudi() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
        QbRuntime.setHost(prima);
    }

    private void costruisci(boolean archi) throws Exception {
        host = new BibliotecaFinta.Host(archi);
        QbRuntime.setHost(host);
        onEdt(() -> {
            qb = new QueryBuilder(host);
            frame = new JFrame("RamaSQL - T12 disposizione del diagramma");
            frame.getContentPane().add(qb);
            Dimension size = new Dimension(1280, 760);
            frame.setSize(size);
            frame.addNotify(); // visualizzabile (peer e layout) senza mostrarlo sul desktop
            frame.getRootPane().setSize(size);
            frame.getRootPane().validate();
        });
    }

    @ParameterizedTest(name = "join ad archi: {0}")
    @ValueSource(booleans = {true, false})
    void nessunJoinSottoUnAltraEntita(boolean archi) throws Exception {
        String modo = archi ? "archi" : "linee spezzate";
        evidenza.append("BUG-023 — disposizione del diagramma, join disegnati a ").append(modo).append('\n');
        try {
            costruisci(archi);

            // 0. il controllo vede il difetto: le tre tabelle di T7.9 in fila, come le metteva il codice ereditato
            onEdt(() -> {
                for (String t : List.of("prestiti", "soci", "libri")) {
                    assertTrue(QbOperations.addTable(qb, t), "tabella aggiunta: " + t);
                }
                int x = 10;
                for (String t : List.of("prestiti", "soci", "libri")) {
                    DiagramAbstractEntity e = entita(t);
                    e.setSize(e.getPreferredSize());
                    e.setLocation(x, 10);
                    x += e.getWidth() + 50;
                }
                qb.diagram.doResize();
            });
            List<String> vecchia = fromEdt(this::difetti);
            evidenza.append("0. T7.9 in fila (disposizione ereditata): ").append(vecchia).append('\n');
            assertTrue(vecchia.stream().anyMatch(d -> d.contains("soci")),
                    "il controllo deve vedere il join prestiti–libri sotto soci: " + vecchia);

            // 1. stesse tre tabelle, poi le altre, aggiunte una alla volta come l'utente (doppio clic o trascinamento)
            scena(List.of("prestiti", "soci", "libri", "editori", "libri_autori", "autori"), 5, "1. prestiti → autori");
            if (archi) {
                salvaImmagine("T12-BUG023-scena-T7.9.png");
            }

            // 2. un altro ordine
            scena(List.of("libri", "editori", "libri_autori", "autori", "prestiti", "soci"), 5, "2. libri → soci");

            // 3. un modello caricato dal testo SQL (vista SQL → Grafica, riapertura di una vista)
            String query = "SELECT a.cognome, l.titolo, e.nome, p.data_prestito FROM autori a"
                    + " INNER JOIN libri_autori la ON a.id = la.id_autore INNER JOIN libri l ON la.id_libro = l.id"
                    + " INNER JOIN editori e ON l.id_editore = e.id LEFT JOIN prestiti p ON l.id = p.id_libro"
                    + " WHERE l.anno > 2000 ORDER BY l.titolo";
            onEdt(() -> qb.setQueryModel(QbSql.parse(query)));
            controlla("3. modello a 5 tabelle caricato dall'SQL", 5, 4);
            assertEquals(QbSql.normalize(query), QbSql.normalize(fromEdt(() -> qb.getQueryModel().toString(false))),
                    "il modello caricato rigenera la stessa query");

            // 4. un ciclo: soci e libri, nella stessa colonna, uniti anche fra loro
            String ciclo = "SELECT p.id FROM prestiti p, soci s, libri l, editori e"
                    + " WHERE p.id_socio = s.id AND p.id_libro = l.id AND s.id = l.id AND l.id_editore = e.id";
            onEdt(() -> qb.setQueryModel(QbSql.parse(ciclo)));
            controlla("4. ciclo prestiti–soci–libri (+ editori)", 4, 4);

            // 5. l'utente sistema il diagramma da sé: le tabelle aggiunte dopo cercano un posto libero, le sue restano
            onEdt(() -> {
                QbOperations.clear(qb);
                QbOperations.addTable(qb, "prestiti");
                QbOperations.addTable(qb, "soci");
                qb.getActionMap().get(QueryActions.ENTITIES_ARRANGE_GRID)
                        .actionPerformed(new java.awt.event.ActionEvent(qb, 0, "griglia"));
            });
            Map<String, Point> sistemate = fromEdt(this::posizioni);
            onEdt(() -> {
                QbOperations.addTable(qb, "libri");
                QbOperations.addTable(qb, "editori");
            });
            Map<String, Point> dopo = fromEdt(this::posizioni);
            for (Map.Entry<String, Point> e : sistemate.entrySet()) {
                assertEquals(e.getValue(), dopo.get(e.getKey()), "l'entità sistemata dall'utente non si sposta: " + e.getKey());
            }
            List<String> d5 = fromEdt(this::difetti);
            evidenza.append("5. griglia dell'utente, poi libri ed editori al primo posto libero: ").append(fromEdt(this::riquadri))
                    .append(", difetti ").append(d5).append('\n');
            assertEquals(List.of(), d5, "tabelle aggiunte dopo la griglia dell'utente: join sotto altre entità");
            controlla("5. (ridisposto per collegamenti)", 4, 3);
            assertTrue(host.avvisi.isEmpty(), "avvisi inattesi: " + host.avvisi);
            evidenza.append("Esito: SUPERATO\n");
        } catch (Throwable t) {
            evidenza.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Step12Files.write("T12-BUG023-disposizione-" + (archi ? "archi" : "linee") + ".txt", evidenza.toString());
        }
    }

    /** Svuota, aggiunge {@code tabelle} una alla volta e controlla dopo ogni aggiunta; poi ridispone e confronta l'SQL. */
    private void scena(List<String> tabelle, int joinAttesi, String titolo) throws Exception {
        onEdt(() -> QbOperations.clear(qb));
        for (int i = 0; i < tabelle.size(); i++) {
            String t = tabelle.get(i);
            onEdt(() -> assertTrue(QbOperations.addTable(qb, t), "tabella aggiunta: " + t));
            List<String> d = fromEdt(this::difetti);
            assertEquals(List.of(), d, titolo + ", dopo " + t + ": join sotto altre entità");
        }
        controlla(titolo, tabelle.size(), joinAttesi);
    }

    /** Numero di entità e join, nessun difetto; l'SQL non cambia ridisponendo. */
    private void controlla(String titolo, int entitaAttese, int joinAttesi) throws Exception {
        assertEquals(entitaAttese, (int) fromEdt(() -> qb.diagram.getEntities().length), titolo + ": entità");
        assertEquals(joinAttesi, (int) fromEdt(() -> qb.diagram.getRelations().length), titolo + ": join");
        List<String> d = fromEdt(this::difetti);
        String sqlPrima = fromEdt(() -> QbOperations.sql(qb));
        onEdt(() -> qb.diagram.doArrangeEntitiesLayered());
        String sqlDopo = fromEdt(() -> QbOperations.sql(qb));
        evidenza.append(titolo).append(": ").append(entitaAttese).append(" entità, ").append(joinAttesi)
                .append(" join, pixel controllati ").append(pixelControllati).append(", difetti ").append(d).append('\n')
                .append("   posizioni ").append(fromEdt(this::riquadri)).append('\n');
        assertEquals(List.of(), d, titolo + ": join sotto altre entità");
        assertEquals(sqlPrima, sqlDopo, titolo + ": la disposizione non cambia l'SQL");
    }

    private int pixelControllati;

    /**
     * I difetti di disposizione del diagramma visibile: entità sovrapposte, nodi di join dentro un'entità, pixel di una
     * linea di join dentro un'entità che non è uno dei suoi due capi.
     */
    private List<String> difetti() {
        List<String> out = new ArrayList<>();
        DiagramAbstractEntity[] es = qb.diagram.getEntities();
        for (int i = 0; i < es.length; i++) {
            for (int j = i + 1; j < es.length; j++) {
                if (es[i].getBounds().intersects(es[j].getBounds())) {
                    out.add("entità sovrapposte: " + nome(es[i]) + " e " + nome(es[j]));
                }
            }
        }
        pixelControllati = 0;
        for (DiagramRelation r : qb.diagram.getRelations()) {
            String join = nome(r.primaryEntity) + "–" + nome(r.foreignEntity);
            Rectangle nodo = r.anchorBounds();
            for (DiagramAbstractEntity e : es) {
                if (nodo.intersects(e.getBounds())) {
                    out.add("nodo del join " + join + " dentro " + nome(e));
                }
            }
            int w = Math.max(1, r.getWidth());
            int h = Math.max(1, r.getHeight());
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            r.paintChildren(g);
            g.dispose();
            int disegnati = 0;
            Map<DiagramAbstractEntity, Integer> sotto = new LinkedHashMap<>();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((img.getRGB(x, y) >>> 24) == 0) {
                        continue;
                    }
                    disegnati++;
                    int dx = x + r.getX();
                    int dy = y + r.getY();
                    for (DiagramAbstractEntity e : es) {
                        if (e == r.primaryEntity || e == r.foreignEntity) {
                            continue;
                        }
                        Rectangle dentro = e.getBounds();
                        dentro.grow(-1, -1);
                        if (dentro.contains(dx, dy)) {
                            sotto.merge(e, 1, Integer::sum);
                        }
                    }
                }
            }
            pixelControllati += disegnati;
            if (disegnati == 0) {
                out.add("join " + join + " non disegnato");
            }
            sotto.forEach((e, n) -> out.add("linea del join " + join + " sotto " + nome(e) + " (" + n + " px)"));
        }
        return out;
    }

    private DiagramAbstractEntity entita(String tabella) {
        for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
            if (e.getQueryToken().getName().equalsIgnoreCase(tabella)) {
                return e;
            }
        }
        throw new AssertionError("entità assente: " + tabella);
    }

    private static String nome(DiagramAbstractEntity e) {
        return e == null ? "?" : e.getQueryToken().getName();
    }

    private Map<String, Point> posizioni() {
        Map<String, Point> out = new LinkedHashMap<>();
        for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
            out.put(nome(e), e.getLocation());
        }
        return out;
    }

    private String riquadri() {
        StringBuilder sb = new StringBuilder();
        for (DiagramAbstractEntity e : qb.diagram.getEntitiesInOrder()) {
            Rectangle b = e.getBounds();
            sb.append(nome(e)).append('(').append(b.x).append(',').append(b.y).append(' ').append(b.width).append('x')
                    .append(b.height).append(") ");
        }
        return sb.toString().trim();
    }

    private void salvaImmagine(String file) throws Exception {
        onEdt(() -> {
            try {
                javax.swing.JComponent d = QbOperations.diagram(qb);
                d.setSize(d.getPreferredSize().width, d.getPreferredSize().height);
                d.validate();
                BufferedImage img = new BufferedImage(Math.max(1, d.getWidth()), Math.max(1, d.getHeight()),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                d.paint(g);
                g.dispose();
                ImageIO.write(img, "png", Step12Files.dir().resolve(file).toFile());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    // ---------------------------------------------------------------- EDT

    private interface Azione {
        void run() throws Exception;
    }

    private static void onEdt(Azione a) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                a.run();
            } catch (Throwable t) {
                err.set(t);
            }
        });
        rilancia(err.get());
    }

    private static <T> T fromEdt(java.util.concurrent.Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        onEdt(() -> out.set(c.call()));
        return out.get();
    }

    private static void rilancia(Throwable t) throws Exception {
        if (t instanceof Exception e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
    }
}
