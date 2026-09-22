/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app.spike.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ramasql.app.spike.SpikeFiles;
import it.ramasql.app.spike.er.ErCanvas.Entity;
import it.ramasql.app.spike.er.ErCanvas.Relation;
import it.ramasql.app.spike.er.ErCanvas.Route;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Spike S6 — canvas ER in Java2D con 30 entità e 40 relazioni. Tutto in-process: gli eventi del mouse
 * sono sintetici ({@code dispatchEvent}) e il disegno va su {@link BufferedImage}; lo schermo non si tocca.
 */
@Tag("step1")
@Tag("ui")
class ErCanvasSpikeTest {

    private static final int W = 1600;
    private static final int H = 1000;

    private static ErCanvas canvas() {
        ErCanvas c = ErCanvas.demo(30, 40, 20260921L);
        c.setSize(W, H);
        return c;
    }

    private static BufferedImage paint(ErCanvas c) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            c.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static int distinctColors(BufferedImage img) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                colors.add(img.getRGB(x, y));
            }
        }
        return colors.size();
    }

    private static void mouse(ErCanvas c, int id, Point p, int modifiers) throws Exception {
        SwingUtilities.invokeAndWait(() -> c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(),
                modifiers, p.x, p.y, 1, false, MouseEvent.BUTTON1)));
    }

    /** Pressione, spostamento in 10 passi, rilascio: come farebbe la mano. */
    private static void drag(ErCanvas c, Point from, Point to) throws Exception {
        mouse(c, MouseEvent.MOUSE_PRESSED, from, InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 10; i++) {
            Point p = new Point(from.x + (to.x - from.x) * i / 10, from.y + (to.y - from.y) * i / 10);
            mouse(c, MouseEvent.MOUSE_DRAGGED, p, InputEvent.BUTTON1_DOWN_MASK);
        }
        mouse(c, MouseEvent.MOUSE_RELEASED, to, 0);
    }

    private static boolean onBorder(Rectangle2D r, Point2D p) {
        double eps = 1e-6;
        boolean insideX = p.getX() >= r.getMinX() - eps && p.getX() <= r.getMaxX() + eps;
        boolean insideY = p.getY() >= r.getMinY() - eps && p.getY() <= r.getMaxY() + eps;
        boolean onVertical = Math.abs(p.getX() - r.getMinX()) < eps || Math.abs(p.getX() - r.getMaxX()) < eps;
        boolean onHorizontal = Math.abs(p.getY() - r.getMinY()) < eps || Math.abs(p.getY() - r.getMaxY()) < eps;
        return (onVertical && insideY) || (onHorizontal && insideX);
    }

    // ------------------------------------------------------------------ (1)

    @Test
    void trentaEntitaQuarantaRelazioniSiDisegnano() {
        ErCanvas c = canvas();
        assertEquals(30, c.entities().size());
        assertEquals(40, c.relations().size());
        for (Entity e : c.entities()) {
            assertTrue(e.columns.size() >= 4 && e.columns.size() <= 8, e.name + ": " + e.columns.size() + " colonne");
        }
        for (Relation r : c.relations()) {
            assertNotEquals(r.many(), r.one());
            Route route = c.route(r);
            assertTrue(onBorder(r.many().bounds(), route.points().get(0)), "la zampa di gallina parte dal bordo della figlia");
            assertTrue(onBorder(r.one().bounds(), route.points().get(route.points().size() - 1)));
        }
        BufferedImage img = paint(c);
        assertTrue(distinctColors(img) > 20, "immagine non monocroma");
    }

    // ------------------------------------------------------------------ (2)

    @Test
    void trascinandoUnEntitaLeRelazioniLaSeguono() throws Exception {
        ErCanvas c = canvas();
        Relation rel = c.relations().get(0);
        Entity e = rel.many();
        double x0 = e.x;
        double y0 = e.y;
        Point2D attaccoPrima = c.route(rel).points().get(0);
        BufferedImage prima = paint(c);

        Point from = c.toScreen(e.x + 20, e.y + 10); // dentro l'intestazione
        assertEquals(e, c.entityAt(from));
        drag(c, from, new Point(from.x + 60, from.y + 40));

        assertEquals(x0 + 60, e.x, 1e-9);
        assertEquals(y0 + 40, e.y, 1e-9);
        Point2D attaccoDopo = c.route(rel).points().get(0);
        assertNotEquals(attaccoPrima, attaccoDopo, "il punto d'attacco della relazione deve spostarsi con l'entità");
        assertTrue(onBorder(e.bounds(), attaccoDopo), "la relazione resta attaccata al bordo dell'entità spostata");
        for (Relation r : c.relations()) { // tutte le relazioni restano attaccate
            Route route = c.route(r);
            assertTrue(onBorder(r.many().bounds(), route.points().get(0)));
            assertTrue(onBorder(r.one().bounds(), route.points().get(route.points().size() - 1)));
        }
        BufferedImage dopo = paint(c);
        int diversi = 0;
        for (int y = 0; y < H; y += 2) {
            for (int x = 0; x < W; x += 2) {
                if (prima.getRGB(x, y) != dopo.getRGB(x, y)) {
                    diversi++;
                }
            }
        }
        assertTrue(diversi > 500, "il disegno deve cambiare dopo il trascinamento (pixel diversi: " + diversi + ")");

        // a zoom 200% lo stesso gesto sposta l'entità della metà (coordinate del diagramma)
        c.setView(2.0, 0, 0);
        Point from2 = c.toScreen(e.x + 20, e.y + 10);
        drag(c, from2, new Point(from2.x + 60, from2.y + 40));
        assertEquals(x0 + 60 + 30, e.x, 1e-9);
        assertEquals(y0 + 40 + 20, e.y, 1e-9);
    }

    @Test
    void trascinandoLoSfondoSiSpostaIlFoglio() throws Exception {
        ErCanvas c = canvas();
        Point vuoto = new Point(5, 5);
        assertNull(c.entityAt(vuoto));
        Entity e = c.entities().get(0);
        double x0 = e.x;
        drag(c, vuoto, new Point(105, 55));
        assertEquals(new Point2D.Double(100, 50), c.offset());
        assertEquals(x0, e.x, 1e-9, "il pan non sposta le entità");
        assertEquals(new Point((int) Math.round(x0 + 100), (int) Math.round(e.y + 50)), c.toScreen(e.x, e.y));
    }

    // ------------------------------------------------------------------ (3)

    @Test
    void zoomCinquantaCentoDuecento() throws Exception {
        ErCanvas c = canvas();
        Entity e = c.entities().get(7);
        for (double z : new double[] {0.5, 1.0, 2.0}) {
            c.setView(z, 0, 0);
            Point a = c.toScreen(e.x, e.y);
            Point b = c.toScreen(e.x + e.width(), e.y + e.height());
            assertEquals(e.width() * z, b.x - a.x, 1.0, "larghezza a zoom " + z);
            assertEquals(e.height() * z, b.y - a.y, 1.0);
            assertEquals(e, c.entityAt(new Point((a.x + b.x) / 2, (a.y + b.y) / 2)), "l'entità si prende anche a zoom " + z);
            BufferedImage img = paint(c);
            assertTrue(distinctColors(img) > 20);
            ImageIO.write(img, "png", SpikeFiles.step1Dir().resolve(
                    String.format(Locale.ROOT, "S6-er-canvas-zoom%03d.png", (int) (z * 100))).toFile());
            // l'intestazione blu dell'entità è lì dove il calcolo dice che deve essere
            int rgb = img.getRGB(Math.min(W - 1, (int) (a.x + (e.width() - 10) * z)), Math.min(H - 1, (int) (a.y + 4 * z)));
            assertEquals(0x2F6FDE, rgb & 0xFFFFFF, "colore dell'intestazione a zoom " + z);
        }
        // rotella: tre scatti in avanti sopra un punto, che deve restare fermo sotto il mouse
        c.setView(1.0, 0, 0);
        Point mouse = new Point(800, 500);
        Point2D sotto = c.toWorld(mouse);
        for (int i = 0; i < 3; i++) {
            SwingUtilities.invokeAndWait(() -> c.dispatchEvent(new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(), 0, mouse.x, mouse.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1)));
        }
        assertEquals(1.1 * 1.1 * 1.1, c.zoom(), 1e-9);
        Point2D ancora = c.toWorld(mouse);
        assertEquals(sotto.getX(), ancora.getX(), 1e-6);
        assertEquals(sotto.getY(), ancora.getY(), 1e-6);
    }

    // ------------------------------------------------------------------ (4)

    @Test
    void ridisegnoFluidoSottoSediciMillisecondi() throws Exception {
        ErCanvas c = canvas();
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        StringBuilder report = new StringBuilder("S6 - tempo di paint su BufferedImage " + W + "x" + H
                + " (30 entita', 40 relazioni, antialias attivo), 100 ridisegni dopo 30 di riscaldamento\n");
        double mediaCento = -1;
        for (double z : new double[] {1.0, 0.5, 2.0}) {
            c.setView(z, 0, 0);
            for (int i = 0; i < 30; i++) {
                paintOn(c, img);
            }
            long max = 0;
            long total = 0;
            for (int i = 0; i < 100; i++) {
                // ogni fotogramma sposta un'entità, come durante un trascinamento vero
                c.entities().get(3).x += (i % 2 == 0) ? 3 : -3;
                long t0 = System.nanoTime();
                paintOn(c, img);
                long dt = System.nanoTime() - t0;
                total += dt;
                max = Math.max(max, dt);
            }
            double media = total / 100.0 / 1e6;
            report.append(String.format(Locale.ROOT, "zoom %3d%%: media %.2f ms, massimo %.2f ms%n",
                    (int) (z * 100), media, max / 1e6));
            if (z == 1.0) {
                mediaCento = media;
            }
        }
        report.append("java ").append(System.getProperty("java.version")).append(", ")
                .append(Runtime.getRuntime().availableProcessors()).append(" processori logici\n");
        Files.writeString(SpikeFiles.step1Dir().resolve("S6-misure.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
        assertTrue(mediaCento < 16.0, "paint medio a 100% = " + mediaCento + " ms (soglia: 16 ms, cioè 60 fotogrammi al secondo)");
    }

    private static void paintOn(ErCanvas c, BufferedImage img) {
        Graphics2D g = img.createGraphics();
        try {
            c.paint(g);
        } finally {
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ (5)

    @Test
    void esportazionePng() throws Exception {
        ErCanvas c = canvas();
        Path file = SpikeFiles.step1Dir().resolve("S6-er-canvas.png");
        Files.deleteIfExists(file);
        c.exportPng(file, 1.0);
        assertTrue(Files.size(file) > 20_000, "PNG troppo piccola: " + Files.size(file) + " byte");
        BufferedImage img = ImageIO.read(file.toFile());
        assertNotNull(img);
        Rectangle2D b = c.diagramBounds();
        assertEquals((int) Math.ceil(b.getWidth() + 60), img.getWidth());
        assertEquals((int) Math.ceil(b.getHeight() + 60), img.getHeight());
        assertTrue(distinctColors(img) > 20, "PNG monocroma");
        // l'esportazione contiene TUTTO il diagramma anche se la vista è altrove
        c.setView(3.0, -2000, -2000);
        BufferedImage altra = c.exportImage(1.0);
        assertEquals(img.getWidth(), altra.getWidth());
        assertEquals(img.getRGB(100, 40), altra.getRGB(100, 40));
        // ingrandimento della zona in alto a sinistra, per giudicare a occhio le zampe di gallina
        c.setView(1.0, 0, 0);
        BufferedImage x2 = c.exportImage(2.0).getSubimage(0, 0, 1200, 900);
        ImageIO.write(x2, "png", SpikeFiles.step1Dir().resolve("S6-er-canvas-dettaglio.png").toFile());
    }
}
