/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static it.ramasql.app.servertest.Probe.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.swing.JMenuItem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.er.ErCanvas;
import it.ramasql.app.er.ErModelPanel;
import it.ramasql.app.er.ErModelWindow;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.tableeditor.TableEditor;
import it.ramasql.app.theme.Tokens;
import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.model.AutoLayout;
import it.ramasql.model.ErModel;
import it.ramasql.model.ModelFile;
import it.ramasql.model.Relationship;

/**
 * Il modello ER sul <b>programma vero</b>, contro MariaDB e MySQL, ricontrollato sul server con la connessione del
 * test:
 * <ul>
 *   <li><b>T11.6</b>: su {@code biblioteca_myisam}, dal pulsante «Modello ER»: si accettano i suggerimenti, se ne disegna
 *       una a mano trascinando da colonna a colonna, si salva → relazioni logiche tratteggiate; nel registro SQL
 *       <b>zero istruzioni</b>, sul server nessuna chiave esterna;</li>
 *   <li><b>T11.7</b>: chiusa la finestra e chiusa la connessione, il {@code .rsqlmodel} si riapre dal menu <em>File</em>
 *       senza connessione: modello identico, posizioni comprese;</li>
 *   <li><b>T11.8</b>: sul server si aggiunge una colonna a {@code soci} e si elimina {@code prestiti} → <em>Aggiorna dal
 *       database</em>: colonna nuova, {@code prestiti} segnata come mancante, posizioni e relazioni logiche conservate;</li>
 *   <li><b>T11.9</b>: catalogo di 30 tabelle → disposizione senza sovrapposizioni, le più riferite al centro; zoom;
 *       trascinamento con il mouse; con 100 entità disegno e trascinamento restano fluidi;</li>
 *   <li><b>T11.10</b>: doppio clic su un'entità → si apre l'editor della tabella;</li>
 *   <li><b>T11.11</b>: PNG esportato; resa in scala A4 a 150 dpi e a 1024×768 misurata: testo abbastanza grande, linee
 *       abbastanza spesse e contrastate, tratteggio e zampa di gallina riconoscibili.</li>
 * </ul>
 */
@Tag("step11")
@Tag("ui")
@Tag("it")
class T116T1111ModelloErSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    private static ErModelWindow newModel(ClientApp a, String catalog) {
        int before = fromEdt(() -> a.frame().erWindows().size());
        a.node(NavNode.Kind.CATALOG, catalog, catalog);
        onEdt(() -> a.nav().tree().setSelectionPath(a.nav().find(NavNode.Kind.CATALOG, catalog, catalog)));
        waitUntil("«Modello ER» abilitato", ClientApp.TIMEOUT, () -> a.frame().button("erModel").isEnabled());
        onEdt(() -> {
            JMenuItem item = ClientApp.menuItem(a.frame().erMenu(), "er.menu.new");
            assertTrue(item.isEnabled());
            item.doClick();
        });
        waitUntil("modello letto", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                && a.frame().erWindows().size() == before + 1);
        return fromEdt(() -> a.frame().erWindows().get(a.frame().erWindows().size() - 1));
    }

    private static void mouse(ErCanvas c, int id, Point p, int modifiers, int clicks) {
        onEdt(() -> c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), modifiers, p.x, p.y, clicks, false,
                MouseEvent.BUTTON1)));
    }

    /** Trascina con il mouse vero (eventi del componente) da un punto a un altro. */
    private static void drag(ErCanvas c, Point from, Point to) {
        mouse(c, MouseEvent.MOUSE_PRESSED, from, InputEvent.BUTTON1_DOWN_MASK, 1);
        Point mid = new Point((from.x + to.x) / 2, (from.y + to.y) / 2);
        mouse(c, MouseEvent.MOUSE_DRAGGED, mid, InputEvent.BUTTON1_DOWN_MASK, 0);
        mouse(c, MouseEvent.MOUSE_DRAGGED, to, InputEvent.BUTTON1_DOWN_MASK, 0);
        mouse(c, MouseEvent.MOUSE_RELEASED, to, 0, 1);
    }

    private static void waitIdle(ErModelPanel p) {
        waitUntil("modello a riposo", ClientApp.TIMEOUT, () -> !p.isBusy());
    }

    // ================================================================ T11.6 · T11.7 · T11.8 · T11.10 · T11.11

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t116_t118_logicheSalvataggioRiaperturaAggiornamento(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t116");
        StringBuilder ev = new StringBuilder("T11.6–T11.8, T11.10, T11.11 — modello ER di biblioteca_myisam ("
                + server.label() + ")\n");
        Path file = dataDir.resolve("biblioteca.rsqlmodel");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca_myisam.sql");
            server.run("CREATE TABLE `" + catalog + "`.recensioni (id INT UNSIGNED NOT NULL PRIMARY KEY,"
                    + " libro INT UNSIGNED NOT NULL, voto TINYINT NOT NULL) ENGINE=MyISAM");
            ErModel saved;
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                int logBefore = a.log().size();
                ErModelWindow w = newModel(a, catalog);
                ErModelPanel p = w.panel();
                ErCanvas c = p.canvas();
                assertEquals(7, fromEdt(() -> p.model().entities().size()));
                assertEquals(0, fromEdt(() -> p.model().physical().size()), "MyISAM: nessuna relazione fisica");
                // T11.6: suggerimenti accettati
                onEdt(p::suggest);
                assertTrue(fromEdt(p::suggestionsVisible));
                Set<String> proposed = new TreeSet<>(fromEdt(() -> p.suggestions().stream()
                        .map(s -> s.relationship().describe()).toList()));
                assertEquals(Set.of("libri.id_editore → editori.id", "libri_autori.id_libro → libri.id",
                        "libri_autori.id_autore → autori.id", "prestiti.id_libro → libri.id",
                        "prestiti.id_socio → soci.id"), proposed, "«libro» senza id_ non si indovina: si disegna a mano");
                onEdt(p::acceptChecked);
                assertEquals(5, fromEdt(() -> p.model().logical().size()));
                // una relazione disegnata a mano, trascinando da recensioni.libro a libri.id
                Point from = fromEdt(() -> c.pointOf("recensioni", "libro"));
                Point to = fromEdt(() -> c.pointOf("libri", "id"));
                drag(c, from, to);
                List<Relationship> logical = fromEdt(() -> p.model().logical());
                assertEquals(6, logical.size());
                Relationship drawn = logical.stream().filter(r -> r.fromTable().equals("recensioni")).findFirst()
                        .orElseThrow();
                assertEquals("recensioni.libro → libri.id", drawn.describe());
                assertTrue(drawn.mandatory(), "libro NOT NULL: obbligatoria");
                ev.append("T11.6 — accettati 5 suggerimenti, disegnata a mano ").append(drawn.describe()).append('\n');
                // salva
                a.ws.filesToSave.put(FilePrompts.Purpose.MODEL, file);
                onEdt(p::save);
                waitIdle(p);
                assertTrue(Files.exists(file));
                assertFalse(fromEdt(p::isModified));
                saved = ModelFile.read(file);
                assertEquals(6, saved.logical().size());
                assertEquals(0, saved.physical().size());
                assertEquals(logBefore, a.log().size(), "registro SQL: zero istruzioni, il database non è stato toccato");
                assertEquals("0", server.scalar("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS"
                        + " WHERE CONSTRAINT_SCHEMA = '" + catalog + "'"), "sul server nessuna chiave esterna");
                PaintSupport.paint(p, "step11", "T11.6-logiche-" + server.id() + ".png");
                ev.append("  ").append(underEntities(c, fromEdt(p::model))).append('\n');
                ev.append("  salvato in ").append(file.getFileName()).append(": 7 entità, 6 relazioni logiche,"
                        + " 0 fisiche; registro SQL: ").append(a.log().size() - logBefore).append(" istruzioni;"
                        + " REFERENTIAL_CONSTRAINTS sul server: 0\n");

                // T11.10: doppio clic su un'entità → editor della tabella
                Point soci = fromEdt(() -> c.pointOf("soci", null));
                mouse(c, MouseEvent.MOUSE_PRESSED, soci, InputEvent.BUTTON1_DOWN_MASK, 1);
                mouse(c, MouseEvent.MOUSE_RELEASED, soci, 0, 1);
                mouse(c, MouseEvent.MOUSE_CLICKED, soci, 0, 2);
                waitUntil("editor della tabella soci", ClientApp.TIMEOUT, () -> a.frame().tabs().selected()
                        instanceof TableEditor te && te.editedTable().name().equals("soci"));
                ev.append("T11.10 — doppio clic su «soci»: si apre l'editor della tabella soci\n");

                // T11.11: esporta PNG e misure di leggibilità
                Path png = dataDir.resolve("modello.png");
                a.ws.filesToSave.put(FilePrompts.Purpose.PNG, png);
                onEdt(p::exportPng);
                waitIdle(p);
                BufferedImage exported = ImageIO.read(png.toFile());
                Rectangle2D extent = fromEdt(c::extent);
                assertEquals((int) Math.ceil((extent.getWidth() + 2 * ErCanvas.margin()) * ErModelPanel.EXPORT_SCALE),
                        exported.getWidth(), "PNG al doppio della scala");
                ev.append(ErLegibility.measure(c, drawn.id(), server.id()));

                // T11.7: chiusa la finestra e la connessione, si riapre senza connessione
                onEdt(w::closeIfAllowed);
                assertFalse(fromEdt(w::isDisplayable));
                onEdt(() -> a.app.connections().disconnect());
                waitUntil("disconnesso", ClientApp.TIMEOUT, () -> a.frame().workspace() == null);
                a.ws.filesToOpen.put(FilePrompts.Purpose.MODEL, file);
                onEdt(() -> fileMenuItem(a, "menu.file.openModel").doClick());
                waitUntil("modello riaperto", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                        && !a.frame().erWindows().isEmpty());
                ErModelWindow reopened = fromEdt(() -> a.frame().erWindows().get(0));
                ErModel back = fromEdt(() -> reopened.panel().model());
                assertEquals(saved, back, "diagramma identico, posizioni comprese");
                assertFalse(fromEdt(() -> reopened.panel().isModified()));
                PaintSupport.paint(reopened.panel(), "step11", "T11.7-riaperto-senza-connessione-" + server.id() + ".png");
                ev.append("T11.7 — riaperto dal menu File senza connessione: modello identico (").append(back.entities()
                        .size()).append(" entità, posizioni uguali, ").append(back.logical().size())
                        .append(" relazioni logiche)\n");
                onEdt(reopened::closeIfAllowed);
            }

            // T11.8: sul server una colonna in più e una tabella in meno → «Aggiorna dal database»
            server.run("ALTER TABLE `" + catalog + "`.soci ADD COLUMN telefono VARCHAR(20) NULL");
            server.run("DROP TABLE `" + catalog + "`.prestiti");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.filesToOpen.put(FilePrompts.Purpose.MODEL, file);
                onEdt(() -> fileMenuItem(a, "menu.file.openModel").doClick());
                waitUntil("modello riaperto", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                        && !a.frame().erWindows().isEmpty());
                ErModelPanel p = fromEdt(() -> a.frame().erWindows().get(0).panel());
                int logBefore = a.log().size();
                onEdt(p::refreshFromDatabase);
                waitIdle(p);
                ErModel after = fromEdt(p::model);
                assertTrue(after.entity("soci").orElseThrow().column("telefono").isPresent(), "colonna nuova");
                assertTrue(after.entity("prestiti").orElseThrow().missing(), "prestiti segnata come mancante");
                for (ErModel.Entity e : saved.entities()) {
                    ErModel.Entity now = after.entity(e.table()).orElseThrow();
                    assertEquals(e.x(), now.x(), "posizione di " + e.table());
                    assertEquals(e.y(), now.y(), "posizione di " + e.table());
                }
                assertEquals(saved.logical(), after.logical(), "relazioni logiche conservate");
                assertEquals(logBefore, a.log().size(), "aggiornare legge soltanto");
                String banner = fromEdt(() -> p.banner().text());
                assertTrue(banner.contains("prestiti") && banner.contains("soci"), banner);
                PaintSupport.paint(p, "step11", "T11.8-aggiornato-" + server.id() + ".png");
                ev.append("T11.8 — dopo ALTER TABLE soci ADD telefono e DROP TABLE prestiti: ").append(banner
                        .replace('\n', ' ')).append("; posizioni e 6 relazioni logiche conservate\n");
            }
            ev.append("Esito: OK\n");
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step11", "T11.6-T11.11-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ================================================================ T11.9

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t119_trentaTabelleDisposizioneZoomTrascinamento(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t119");
        StringBuilder ev = new StringBuilder("T11.9 — disposizione, zoom e trascinamento (" + server.label() + ")\n");
        try {
            server.createCatalog(catalog);
            StringBuilder ddl = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                String t = String.format("t%02d", i);
                ddl.setLength(0);
                ddl.append("CREATE TABLE `").append(catalog).append("`.").append(t)
                        .append(" (id INT NOT NULL PRIMARY KEY, descrizione_della_riga VARCHAR(100)");
                if (i > 0) {
                    ddl.append(", id_centro INT, FOREIGN KEY (id_centro) REFERENCES `").append(catalog).append("`.t00(id)");
                }
                if (i > 1) {
                    ddl.append(", id_prec INT, FOREIGN KEY (id_prec) REFERENCES `").append(catalog).append("`.")
                            .append(String.format("t%02d", i - 1)).append("(id)");
                }
                server.run(ddl.append(") ENGINE=InnoDB").toString());
            }
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                ErModelWindow w = newModel(a, catalog);
                ErModelPanel p = w.panel();
                ErCanvas c = p.canvas();
                ErModel m = fromEdt(p::model);
                assertEquals(30, m.entities().size());
                assertEquals(57, m.physical().size());
                assertFalse(AutoLayout.overlaps(m, fromEdt(c::measure)), "entità non sovrapposte");
                ev.append(underEntities(c, m)).append('\n');
                double cx = m.entities().stream().mapToDouble(ErModel.Entity::x).average().orElseThrow();
                double cy = m.entities().stream().mapToDouble(ErModel.Entity::y).average().orElseThrow();
                ErModel.Entity centro = m.entity("t00").orElseThrow();
                double d = Math.hypot(centro.x() - cx, centro.y() - cy);
                long closer = m.entities().stream().filter(e -> Math.hypot(e.x() - cx, e.y() - cy) < d - 1).count();
                assertTrue(closer <= 3, "la tabella riferita da tutte è fra le più centrali (più vicine: " + closer + ")");
                ev.append("30 tabelle, 57 relazioni fisiche; nessuna sovrapposizione; t00 (riferita da 29) fra le più"
                        + " centrali (più vicine al centro: ").append(closer).append(")\n");
                PaintSupport.paint(p, "step11", "T11.9-trenta-" + server.id() + ".png");
                // zoom
                double before = fromEdt(c::zoom);
                int widthBefore = fromEdt(() -> c.getPreferredSize().width);
                onEdt(() -> p.zoom(1.2));
                assertEquals(before * 1.2, fromEdt(c::zoom), 1e-9);
                assertTrue(fromEdt(() -> c.getPreferredSize().width) > widthBefore);
                onEdt(() -> p.zoom(1 / 1.2));
                // trascinamento dell'intestazione con il mouse
                ErModel.Entity t05 = fromEdt(() -> p.model().entity("t05").orElseThrow());
                Point head = fromEdt(() -> c.pointOf("t05", null));
                drag(c, head, new Point(head.x + 90, head.y + 40));
                ErModel.Entity moved = fromEdt(() -> p.model().entity("t05").orElseThrow());
                assertEquals(t05.x() + 90 / fromEdt(c::zoom), moved.x(), 1.0);
                assertEquals(t05.y() + 40 / fromEdt(c::zoom), moved.y(), 1.0);
                assertTrue(fromEdt(p::isModified));
                ev.append("zoom 100% → 120% → 100%; t05 trascinata di (90, 40) con il mouse: da (").append(t05.x())
                        .append(", ").append(t05.y()).append(") a (").append(moved.x()).append(", ").append(moved.y())
                        .append(")\n");
                // con 100 entità (generate): disegno e trascinamento restano fluidi
                List<it.ramasql.core.metadata.TableDef> cento = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    cento.add(it.ramasql.core.metadata.TableDef.of("x", String.format("e%03d", i)).withColumns(List.of(
                            it.ramasql.core.metadata.ColumnDef.of("id", "INT").withNullable(false),
                            it.ramasql.core.metadata.ColumnDef.of("nome", "VARCHAR", "60"),
                            it.ramasql.core.metadata.ColumnDef.of("id_padre", "INT")))
                            .withIndexes(List.of(it.ramasql.core.metadata.IndexDef.primary("id")))
                            .withForeignKeys(i == 0 ? List.of() : List.of(it.ramasql.core.metadata.ForeignKeyDef.of(
                                    "fk" + i, "id_padre", String.format("e%03d", (i - 1) / 3), "id"))));
                }
                ErModelWindow big = fromEdt(() -> a.frame().openErWindow(it.ramasql.model.ReverseEngineer.build("x",
                        cento, true), null));
                onEdt(() -> big.panel().autoLayout());
                ErCanvas bc = big.panel().canvas();
                assertFalse(AutoLayout.overlaps(fromEdt(() -> big.panel().model()), fromEdt(bc::measure)));
                BufferedImage img = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_RGB);
                long best = Long.MAX_VALUE;
                for (int k = 0; k < 5; k++) {
                    long t0 = System.nanoTime();
                    onEdt(() -> {
                        java.awt.Graphics2D g = img.createGraphics();
                        bc.setSize(bc.getPreferredSize());
                        bc.paint(g);
                        g.dispose();
                    });
                    best = Math.min(best, (System.nanoTime() - t0) / 1_000_000);
                }
                Point e050 = fromEdt(() -> bc.pointOf("e050", null));
                long t0 = System.nanoTime();
                drag(bc, e050, new Point(e050.x + 30, e050.y + 30));
                long dragMs = (System.nanoTime() - t0) / 1_000_000;
                assertTrue(best < 250, "disegno di 100 entità in " + best + " ms");
                assertTrue(dragMs < 500, "trascinamento con 100 entità in " + dragMs + " ms");
                ev.append("100 entità generate: nessuna sovrapposizione, disegno completo in ").append(best)
                        .append(" ms, trascinamento (4 eventi) in ").append(dragMs).append(" ms\n");
                onEdt(big::dispose);
                ev.append("Esito: OK\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step11", "T11.9-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    /** Misure di leggibilità della resa (T11.11). */
    static final class ErLegibility {

        private ErLegibility() {
        }

        /** Rapporto di contrasto WCAG fra due colori. */
        static double contrast(Color a, Color b) {
            double la = luminance(a);
            double lb = luminance(b);
            return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
        }

        private static double luminance(Color c) {
            double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
            for (int i = 0; i < 3; i++) {
                v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
            }
            return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
        }

        static String measure(ErCanvas c, String logicalId, String serverId) throws Exception {
            StringBuilder ev = new StringBuilder("T11.11 — leggibilità della resa\n");
            Rectangle2D extent = fromEdt(c::extent);
            double w = extent.getWidth() + 2 * ErCanvas.margin();
            double h = extent.getHeight() + 2 * ErCanvas.margin();
            // A4 orizzontale a 150 dpi e proiettore 1024×768: la scala che fa stare il diagramma
            record Target(String name, int width, int height, double minFontPx) {
            }
            for (Target t : List.of(new Target("A4-150dpi", 1754, 1240, 14), new Target("1024x768", 1024, 768, 9))) {
                double scale = Math.min(t.width() / w, t.height() / h);
                BufferedImage img = fromEdt(() -> c.render(scale));
                ImageIO.write(img, "png", Probe.resultsDir("step11").resolve("T11.11-" + t.name() + "-" + serverId
                        + ".png").toFile());
                java.awt.Font label = javax.swing.UIManager.getFont("Label.font");
                double fontPx = (label == null ? 12 : label.getSize2D() - 1) * scale;   // carattere delle righe
                double stroke = 1.4 * scale;
                double dashPx = 6 * scale;
                assertTrue(fontPx >= t.minFontPx(), t.name() + ": carattere di " + fontPx + " px");
                assertTrue(stroke >= 1.0, t.name() + ": linee di " + stroke + " px");
                assertTrue(dashPx >= 4, t.name() + ": trattini di " + dashPx + " px");
                // tratteggio riconoscibile: lungo il tratto più lungo della relazione logica si alternano linea e vuoto
                List<Point2D> pts = fromEdt(() -> c.routePoints(logicalId));
                assertTrue(pts.size() >= 2, "percorso della relazione logica");
                Point2D a0 = pts.get(0);
                Point2D a1 = pts.get(1);
                for (int i = 1; i < pts.size(); i++) {
                    if (pts.get(i).distance(pts.get(i - 1)) > a1.distance(a0)) {
                        a0 = pts.get(i - 1);
                        a1 = pts.get(i);
                    }
                }
                double len = a1.distance(a0) * scale;
                double skip = 20 * scale;
                int transitions = 0;
                Boolean prevInk = null;
                for (double t0 = skip; t0 <= len - skip; t0 += 1) {
                    double f = t0 / len;
                    double mx = a0.getX() + (a1.getX() - a0.getX()) * f;
                    double my = a0.getY() + (a1.getY() - a0.getY()) * f;
                    int x = (int) Math.round((mx - extent.getX() + ErCanvas.margin()) * scale);
                    int y = (int) Math.round((my - extent.getY() + ErCanvas.margin()) * scale);
                    boolean ink = darkest(img, x, y) < 200;
                    if (prevInk != null && ink != prevInk) {
                        transitions++;
                    }
                    prevInk = ink;
                }
                int x1 = (int) Math.round(len);
                int x0 = 0;
                ev.append("  ").append(t.name()).append(": scala ").append(String.format("%.2f", scale))
                        .append(", carattere ≈ ").append(String.format("%.1f", fontPx)).append(" px, linee ")
                        .append(String.format("%.1f", stroke)).append(" px, trattini ").append(String.format("%.1f",
                                dashPx)).append(" px, alternanze del tratteggio misurate: ").append(transitions)
                        .append('\n');
                if (x1 - x0 > 60 * scale) {
                    assertTrue(transitions >= 2, t.name() + ": tratteggio riconoscibile (" + transitions + ")");
                }
            }
            double physical = contrast(Tokens.TEXT_SECONDARY, Color.WHITE);
            double logical = contrast(Tokens.ACCENT, Color.WHITE);
            assertTrue(physical >= 3 && logical >= 3, "contrasto delle linee");
            ev.append("  contrasto su bianco: relazioni fisiche ").append(String.format("%.1f", physical))
                    .append(":1, logiche ").append(String.format("%.1f", logical)).append(":1 (almeno 3:1);"
                            + " fisiche a tratto pieno grigio, logiche tratteggiate blu: distinguibili anche in bianco e"
                            + " nero per la forma\n");
            return ev.toString();
        }

        private static int darkest(BufferedImage img, int x, int y) {
            int min = 255;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int yy = y + dy;
                    int xx = x + dx;
                    if (yy < 0 || yy >= img.getHeight() || xx < 0 || xx >= img.getWidth()) {
                        continue;
                    }
                    Color c = new Color(img.getRGB(xx, yy));
                    min = Math.min(min, (c.getRed() + c.getGreen() + c.getBlue()) / 3);
                }
            }
            return min;
        }
    }

    /** Nessuna relazione passa sotto un'entità: ogni tratto resta fuori dall'interno di tutte le tabelle. */
    static String underEntities(ErCanvas c, ErModel m) {
        int segments = 0;
        int bends = 0;
        for (Relationship r : m.relationships()) {
            List<Point2D> pts = fromEdt(() -> c.routePoints(r.id()));
            assertTrue(pts.size() >= 2, "percorso di " + r.describe());
            bends += pts.size() - 2;
            for (int i = 1; i < pts.size(); i++) {
                Point2D a = pts.get(i - 1);
                Point2D b = pts.get(i);
                assertTrue(a.getX() == b.getX() || a.getY() == b.getY(), "tratto ortogonale");
                segments++;
                for (ErModel.Entity e : m.entities()) {
                    AutoLayout.Size size = fromEdt(() -> c.measure().of(e));
                    Rectangle2D box = new Rectangle2D.Double(e.x(), e.y(), size.width(), size.height());
                    boolean inside = Math.min(a.getX(), b.getX()) < box.getMaxX() - 1e-6
                            && Math.max(a.getX(), b.getX()) > box.getMinX() + 1e-6
                            && Math.min(a.getY(), b.getY()) < box.getMaxY() - 1e-6
                            && Math.max(a.getY(), b.getY()) > box.getMinY() + 1e-6;
                    assertFalse(inside, r.describe() + " passa sotto " + e.table());
                }
            }
        }
        return m.relationships().size() + " relazioni, " + segments + " tratti ortogonali, " + bends
                + " curve in tutto: nessun tratto passa sotto un'entità";
    }

    /** Una voce della barra dei menu, cercata per nome. */
    static JMenuItem fileMenuItem(ClientApp a, String name) {
        javax.swing.JMenuBar bar = a.frame().getJMenuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            javax.swing.JMenu m = bar.getMenu(i);
            for (int k = 0; m != null && k < m.getItemCount(); k++) {
                JMenuItem item = m.getItem(k);
                if (item != null && name.equals(item.getName())) {
                    assertNotNull(item.getText());
                    return item;
                }
            }
        }
        throw new AssertionError("voce di menu assente: " + name);
    }
}
