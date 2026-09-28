/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Le relazioni del diagramma ER girano <b>attorno</b> alle entità (T11.9: il diagramma resta leggibile): tratti solo
 * orizzontali e verticali, uscita orizzontale dalla riga della colonna, nessun passaggio sotto un'altra tabella,
 * corsie diverse per relazioni che percorrerebbero lo stesso corridoio.
 */
@Tag("step11")
class ErRouterTest {

    private static Rectangle2D box(double x, double y) {
        return new Rectangle2D.Double(x, y, 160, 100);
    }

    /** Controlli comuni a ogni percorso; restituisce il numero di curve. */
    static int check(List<Point2D> pts, Rectangle2D child, double cy, Rectangle2D parent, double py,
            List<Rectangle2D> all) {
        assertNotNull(pts);
        assertTrue(pts.size() >= 2);
        Point2D first = pts.get(0);
        Point2D last = pts.get(pts.size() - 1);
        assertEquals(cy, first.getY(), 1e-9, "esce dalla riga della colonna della figlia");
        assertTrue(first.getX() == child.getMinX() || first.getX() == child.getMaxX(), "dal bordo della figlia");
        assertEquals(py, last.getY(), 1e-9, "arriva alla riga della colonna del padre");
        assertTrue(last.getX() == parent.getMinX() || last.getX() == parent.getMaxX(), "al bordo del padre");
        assertEquals(first.getY(), pts.get(1).getY(), 1e-9, "primo tratto orizzontale");
        assertTrue(Math.abs(pts.get(1).getX() - first.getX()) >= ErRouter.STUB - 1e-9, "spazio per la zampa di gallina");
        assertEquals(last.getY(), pts.get(pts.size() - 2).getY(), 1e-9, "ultimo tratto orizzontale");
        for (int i = 1; i < pts.size(); i++) {
            Point2D a = pts.get(i - 1);
            Point2D b = pts.get(i);
            assertTrue(a.getX() == b.getX() || a.getY() == b.getY(), "tratto ortogonale " + a + " → " + b);
            for (Rectangle2D r : all) {
                assertFalse(crossesInterior(r, a, b), "il tratto " + a + " → " + b + " passa sotto " + r);
            }
        }
        return pts.size() - 2;
    }

    static boolean crossesInterior(Rectangle2D r, Point2D a, Point2D b) {
        double x1 = Math.min(a.getX(), b.getX());
        double x2 = Math.max(a.getX(), b.getX());
        double y1 = Math.min(a.getY(), b.getY());
        double y2 = Math.max(a.getY(), b.getY());
        return x1 < r.getMaxX() - 1e-6 && x2 > r.getMinX() + 1e-6 && y1 < r.getMaxY() - 1e-6 && y2 > r.getMinY() + 1e-6;
    }

    @Test
    void affiancateLineaDirettaSenzaCurve() {
        Rectangle2D a = box(0, 0);
        Rectangle2D b = box(300, 0);
        List<Point2D> pts = new ErRouter(List.of(a, b)).route(a, 50, b, 50);
        assertEquals(0, check(pts, a, 50, b, 50, List.of(a, b)));
        assertEquals(List.of(new Point2D.Double(160, 50), new Point2D.Double(300, 50)), pts);
    }

    @Test
    void unaTabellaInMezzoSiAggira() {
        Rectangle2D a = box(0, 0);
        Rectangle2D mid = box(260, 0);
        Rectangle2D b = box(520, 0);
        List<Rectangle2D> all = List.of(a, mid, b);
        List<Point2D> pts = new ErRouter(all).route(a, 50, b, 50);
        int bends = check(pts, a, 50, b, 50, all);
        assertTrue(bends <= 4, "poche curve: " + bends);
        assertTrue(pts.stream().anyMatch(p -> p.getY() <= -ErRouter.CLEAR || p.getY() >= 100 + ErRouter.CLEAR),
                "gira sopra o sotto la tabella di mezzo");
    }

    @Test
    void unaSopraLAltraEsconoDallaStessaParte() {
        Rectangle2D a = box(0, 0);
        Rectangle2D b = box(0, 200);
        List<Point2D> pts = new ErRouter(List.of(a, b)).route(a, 40, b, 230);
        assertEquals(2, check(pts, a, 40, b, 230, List.of(a, b)), "una C: due curve");
    }

    @Test
    void figliaADestraDelPadre() {
        Rectangle2D child = box(400, 150);
        Rectangle2D parent = box(0, 0);
        List<Point2D> pts = new ErRouter(List.of(child, parent)).route(child, 180, parent, 40);
        check(pts, child, 180, parent, 40, List.of(child, parent));
        assertEquals(400, pts.get(0).getX(), 1e-9, "esce dal lato che guarda il padre");
        assertEquals(160, pts.get(pts.size() - 1).getX(), 1e-9, "entra dal lato che guarda la figlia");
    }

    @Test
    void dueRelazioniNelloStessoCorridoioUsanoCorsieDiverse() {
        Rectangle2D a1 = box(0, 0);
        Rectangle2D a2 = box(0, 150);
        Rectangle2D b1 = box(400, 300);
        Rectangle2D b2 = box(400, 450);
        List<Rectangle2D> all = List.of(a1, a2, b1, b2);
        ErRouter router = new ErRouter(all);
        List<Point2D> r1 = router.route(a1, 50, b1, 330);
        List<Point2D> r2 = router.route(a2, 200, b2, 480);
        check(r1, a1, 50, b1, 330, all);
        check(r2, a2, 200, b2, 480, all);
        assertEquals(0, overlap(r1, r2), 1e-9, "nessun tratto in comune: " + r1 + " / " + r2);
    }

    @Test
    void relazioniParalleleBenSeparateENonSuiSimboliAltrui() {
        // quattro relazioni dalla colonna di sinistra a quella di destra, che passano tutte per lo stesso corridoio
        List<Rectangle2D> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            all.add(box(0, i * 150.0));
            all.add(box(400, 600 + i * 150.0));
        }
        ErRouter router = new ErRouter(all);
        List<List<Point2D>> routes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Rectangle2D a = all.get(2 * i);
            Rectangle2D b = all.get(2 * i + 1);
            List<Point2D> pts = router.route(a, a.getY() + 40, b, b.getY() + 40);
            check(pts, a, a.getY() + 40, b, b.getY() + 40, all);
            routes.add(pts);
        }
        for (int i = 0; i < routes.size(); i++) {
            for (int k = i + 1; k < routes.size(); k++) {
                double d = minParallelDistance(routes.get(i), routes.get(k));
                assertTrue(d >= ErRouter.SEPARATION, "relazioni " + i + " e " + k + " parallele a " + d
                        + " unità: sembrerebbero una linea sola");
                assertFalse(throughStub(routes.get(i), routes.get(k)), "la " + i + " passa sui simboli della " + k);
                assertFalse(throughStub(routes.get(k), routes.get(i)), "la " + k + " passa sui simboli della " + i);
            }
        }
    }

    /** Distanza minima fra tratti paralleli che si affiancano (0 se si sovrappongono); infinita se nessuno. */
    static double minParallelDistance(List<Point2D> p, List<Point2D> q) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 1; i < p.size(); i++) {
            for (int k = 1; k < q.size(); k++) {
                Point2D a = p.get(i - 1);
                Point2D b = p.get(i);
                Point2D c = q.get(k - 1);
                Point2D d = q.get(k);
                if (a.getY() == b.getY() && c.getY() == d.getY()) {
                    double lo = Math.max(Math.min(a.getX(), b.getX()), Math.min(c.getX(), d.getX()));
                    double hi = Math.min(Math.max(a.getX(), b.getX()), Math.max(c.getX(), d.getX()));
                    if (hi - lo > 1) {
                        min = Math.min(min, Math.abs(a.getY() - c.getY()));
                    }
                } else if (a.getX() == b.getX() && c.getX() == d.getX()) {
                    double lo = Math.max(Math.min(a.getY(), b.getY()), Math.min(c.getY(), d.getY()));
                    double hi = Math.min(Math.max(a.getY(), b.getY()), Math.max(c.getY(), d.getY()));
                    if (hi - lo > 1) {
                        min = Math.min(min, Math.abs(a.getX() - c.getX()));
                    }
                }
            }
        }
        return min;
    }

    /** Un tratto di {@code p} attraversa la zona dei simboli d'estremità di {@code q}. */
    static boolean throughStub(List<Point2D> p, List<Point2D> q) {
        List<Rectangle2D> zones = new ArrayList<>();
        for (Point2D[] end : new Point2D[][] {{q.get(0), q.get(1)}, {q.get(q.size() - 1), q.get(q.size() - 2)}}) {
            double len = Math.min(ErRouter.STUB - 2, Math.abs(end[1].getX() - end[0].getX()));
            double x = end[1].getX() > end[0].getX() ? end[0].getX() : end[0].getX() - len;
            zones.add(new Rectangle2D.Double(x + 1, end[0].getY() - ErRouter.MARKER + 1, len - 2,
                    2 * ErRouter.MARKER - 2));
        }
        for (int i = 1; i < p.size(); i++) {
            for (Rectangle2D z : zones) {
                if (z.intersectsLine(p.get(i - 1).getX(), p.get(i - 1).getY(), p.get(i).getX(), p.get(i).getY())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void entitaSovrappostePercorsoAssente() {
        Rectangle2D a = box(0, 0);
        Rectangle2D b = box(100, 20);   // si sovrappongono: nessun passaggio libero dal lato vicino
        Rectangle2D wall = new Rectangle2D.Double(-400, -400, 1200, 50);
        List<Point2D> pts = new ErRouter(List.of(a, b, wall)).route(a, 50, b, 60);
        if (pts != null) {   // se c'è, rispetta comunque le regole
            check(pts, a, 50, b, 60, List.of(wall));
        }
        Rectangle2D child = box(100, 100);
        Rectangle2D big = new Rectangle2D.Double(0, 0, 600, 400);   // la figlia sta tutta dentro un'altra entità
        Rectangle2D parent = box(900, 100);
        assertNull(new ErRouter(List.of(child, big, parent)).route(child, 150, parent, 150),
                "nessuna uscita libera: chi chiama ripiega sul percorso semplice");
    }

    @Test
    void centoEntitaEcentocinquantaRelazioniInPocoTempo() {
        List<Rectangle2D> all = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            all.add(box((i % 10) * 240.0, (i / 10) * 170.0));
        }
        ErRouter router = new ErRouter(all);
        long t0 = System.nanoTime();
        int routed = 0;
        for (int i = 1; i < 100; i++) {
            int parent = (i - 1) / 3;
            List<Point2D> pts = router.route(all.get(i), all.get(i).getY() + 40, all.get(parent),
                    all.get(parent).getY() + 40);
            check(pts, all.get(i), all.get(i).getY() + 40, all.get(parent), all.get(parent).getY() + 40, all);
            routed++;
        }
        for (int i = 0; i < 50; i++) {
            int a = (i * 7) % 100;
            int b = (i * 13 + 5) % 100;
            if (a == b) {
                continue;
            }
            List<Point2D> pts = router.route(all.get(a), all.get(a).getY() + 62, all.get(b), all.get(b).getY() + 62);
            check(pts, all.get(a), all.get(a).getY() + 62, all.get(b), all.get(b).getY() + 62, all);
            routed++;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(routed >= 140);
        assertTrue(ms < 3000, routed + " percorsi in " + ms + " ms");
    }

    @Test
    void semplificaIPuntiAllineati() {
        List<Point2D> pts = ErRouter.simplify(List.of(new Point2D.Double(0, 0), new Point2D.Double(10, 0),
                new Point2D.Double(20, 0), new Point2D.Double(20, 0), new Point2D.Double(20, 30)));
        assertEquals(List.of(new Point2D.Double(0, 0), new Point2D.Double(20, 0), new Point2D.Double(20, 30)), pts);
    }

    /** Lunghezza dei tratti collineari in comune fra due percorsi. */
    static double overlap(List<Point2D> p, List<Point2D> q) {
        double total = 0;
        for (int i = 1; i < p.size(); i++) {
            for (int k = 1; k < q.size(); k++) {
                Point2D a = p.get(i - 1);
                Point2D b = p.get(i);
                Point2D c = q.get(k - 1);
                Point2D d = q.get(k);
                if (a.getY() == b.getY() && c.getY() == d.getY() && a.getY() == c.getY()) {
                    total += Math.max(0, Math.min(Math.max(a.getX(), b.getX()), Math.max(c.getX(), d.getX()))
                            - Math.max(Math.min(a.getX(), b.getX()), Math.min(c.getX(), d.getX())));
                } else if (a.getX() == b.getX() && c.getX() == d.getX() && a.getX() == c.getX()) {
                    total += Math.max(0, Math.min(Math.max(a.getY(), b.getY()), Math.max(c.getY(), d.getY()))
                            - Math.max(Math.min(a.getY(), b.getY()), Math.min(c.getY(), d.getY())));
                }
            }
        }
        return total;
    }
}
