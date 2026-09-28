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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
 * Instradamento ortogonale delle relazioni <b>attorno</b> alle entità: una linea non passa mai sotto una tabella.
 * <p>
 * Si costruisce una griglia sparsa con le coordinate utili (i bordi delle entità allargati di {@link #CLEAR}, le
 * uscite dalle righe delle colonne e alcune corsie nei corridoi fra le entità) e si cerca il percorso più corto con
 * A*, pagando ogni curva ({@link #BEND}), i tratti già percorsi da un'altra relazione ({@link #SHARED}) e quelli che
 * le corrono accanto a meno di {@link #SEPARATION} ({@link #NEAR}): così le linee restano poche curve e scelgono
 * corsie ben distinte invece di sovrapporsi o sfiorarsi. Ogni relazione esce dalla riga della sua colonna con un tratto
 * orizzontale ({@link #STUB}) che lascia spazio alla zampa di gallina; i tratti d'uscita già disegnati (dove stanno i
 * simboli) sono ostacoli per le altre relazioni, tranne per quelle che escono dallo stesso punto.
 * Si cerca prima in una regione attorno alle due entità, poi, se serve, in tutto il diagramma.
 */
final class ErRouter {

    /** Distanza minima fra una linea e un'entità. */
    static final double CLEAR = 12;
    /** Tratto orizzontale all'uscita dalla riga, per la zampa di gallina e le barrette. */
    static final double STUB = 26;
    /** Costo di una curva, in unità di lunghezza. */
    static final double BEND = 36;
    /** Sovrapprezzo per unità di lunghezza sui tratti già usati da un'altra relazione. */
    static final double SHARED = 4;
    /** Distanza fra corsie parallele in un corridoio. */
    static final double LANE = 12;
    /** Sotto questa distanza due tratti paralleli di relazioni diverse sembrano una linea sola. */
    static final double SEPARATION = 10;
    /** Sovrapprezzo per unità di lunghezza accanto a un tratto di un'altra relazione. */
    static final double NEAR = 1.5;
    /** Mezza altezza della zona dei simboli d'estremità (zampa di gallina, barrette, cerchio). */
    static final double MARKER = 8;
    /** Margine della prima regione di ricerca attorno alle due entità. */
    static final double REGION = 160;

    private static final int E = 0;
    private static final int W = 1;
    private static final int N = 2;
    private static final int S = 3;

    private final List<Rectangle2D> entities;
    private final Map<Long, List<double[]>> horizontal = new HashMap<>();
    private final Map<Long, List<double[]>> vertical = new HashMap<>();
    /** Zone dei simboli delle relazioni già instradate, con il punto sul bordo da cui escono. */
    private final List<Marker> markers = new ArrayList<>();

    private record Marker(Rectangle2D area, Point2D edge) {
    }

    ErRouter(List<Rectangle2D> entities) {
        this.entities = List.copyOf(entities);
    }

    /** Segna come occupato un percorso già deciso (per le relazioni che non si ricalcolano). */
    void occupy(List<Point2D> pts) {
        if (pts.size() >= 2) {
            marker(pts.get(0), pts.get(1));
            marker(pts.get(pts.size() - 1), pts.get(pts.size() - 2));
        }
        for (int i = 1; i < pts.size(); i++) {
            Point2D a = pts.get(i - 1);
            Point2D b = pts.get(i);
            if (Math.abs(a.getY() - b.getY()) < 0.5) {
                horizontal.computeIfAbsent(key(a.getY()), k -> new ArrayList<>())
                        .add(new double[] {Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX())});
            } else if (Math.abs(a.getX() - b.getX()) < 0.5) {
                vertical.computeIfAbsent(key(a.getX()), k -> new ArrayList<>())
                        .add(new double[] {Math.min(a.getY(), b.getY()), Math.max(a.getY(), b.getY())});
            }
        }
    }

    private void marker(Point2D edge, Point2D next) {
        if (Math.abs(edge.getY() - next.getY()) > 0.5) {
            return;
        }
        // il simbolo (fino a STUB) più lo spazio per non sembrare la continuazione di un'altra linea
        double len = STUB + SEPARATION;
        double x = next.getX() > edge.getX() ? edge.getX() : edge.getX() - len;
        markers.add(new Marker(new Rectangle2D.Double(x, edge.getY() - MARKER, len, 2 * MARKER), edge));
    }

    private static long key(double v) {
        return Math.round(v * 2);
    }

    /**
     * Il percorso dalla riga {@code cy} dell'entità figlia alla riga {@code py} dell'entità padre: il primo punto sta
     * sul bordo della figlia, l'ultimo sul bordo del padre, i tratti sono orizzontali o verticali. {@code null} se non
     * c'è un passaggio libero (entità sovrapposte): chi chiama ripiega su un percorso semplice.
     */
    List<Point2D> route(Rectangle2D child, double cy, Rectangle2D parent, double py) {
        Rectangle2D region = child.createUnion(parent);
        region = new Rectangle2D.Double(region.getX() - REGION, region.getY() - REGION,
                region.getWidth() + 2 * REGION, region.getHeight() + 2 * REGION);
        List<Point2D> found = search(child, cy, parent, py, region, true);
        if (found == null) {
            Rectangle2D all = region;
            for (Rectangle2D r : entities) {
                all = all.createUnion(r);
            }
            all = new Rectangle2D.Double(all.getX() - 3 * STUB, all.getY() - 3 * STUB, all.getWidth() + 6 * STUB,
                    all.getHeight() + 6 * STUB);
            found = search(child, cy, parent, py, all, true);
            if (found == null) {
                found = search(child, cy, parent, py, all, false);   // senza evitare i simboli, piuttosto che niente
            }
        }
        if (found != null) {
            occupy(found);
        }
        return found;
    }

    private record Port(double x, double y, double edgeX, int outward) {
    }

    private List<Point2D> search(Rectangle2D child, double cy, Rectangle2D parent, double py, Rectangle2D region,
            boolean avoidMarkers) {
        List<Rectangle2D> obstacles = new ArrayList<>();
        for (Rectangle2D r : entities) {
            Rectangle2D o = inflate(r);
            if (o.intersects(region)) {
                obstacles.add(o);
            }
        }
        if (avoidMarkers) {
            for (Marker m : markers) {
                // i simboli di chi esce dallo stesso punto non sono un ostacolo: le relazioni lì si uniscono
                boolean samePort = near(m.edge(), child, cy) || near(m.edge(), parent, py);
                if (!samePort && m.area().intersects(region)) {
                    obstacles.add(m.area());
                }
            }
        }
        List<Port> starts = ports(child, cy, obstacles);
        List<Port> ends = ports(parent, py, obstacles);
        if (starts.isEmpty() || ends.isEmpty()) {
            return null;
        }
        // le coordinate della griglia
        TreeSet<Double> bx = new TreeSet<>();
        TreeSet<Double> by = new TreeSet<>();
        bx.add(region.getMinX());
        bx.add(region.getMaxX());
        by.add(region.getMinY());
        by.add(region.getMaxY());
        for (Rectangle2D o : obstacles) {
            bx.add(o.getMinX());
            bx.add(o.getMaxX());
            by.add(o.getMinY());
            by.add(o.getMaxY());
        }
        for (Port p : starts) {
            bx.add(p.x());
            by.add(p.y());
        }
        for (Port p : ends) {
            bx.add(p.x());
            by.add(p.y());
        }
        double[] xs = withLanes(bx, region.getMinX(), region.getMaxX());
        double[] ys = withLanes(by, region.getMinY(), region.getMaxY());
        int nx = xs.length;
        int ny = ys.length;
        boolean[] free = new boolean[nx * ny];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                free[i * ny + j] = !inside(obstacles, xs[i], ys[j]);
            }
        }
        // A* sugli stati (nodo, direzione di marcia)
        double[] best = new double[nx * ny * 4];
        int[] from = new int[nx * ny * 4];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(from, -1);
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        Map<Integer, Port> startAt = new HashMap<>();
        for (Port p : starts) {
            int node = index(xs, p.x()) * ny + index(ys, p.y());
            int state = node * 4 + (p.outward() > 0 ? E : W);
            best[state] = 0;
            startAt.put(state, p);
            queue.add(new double[] {heuristic(xs[node / ny], ys[node % ny], ends), 0, state});
        }
        Map<Integer, Port> endAt = new HashMap<>();
        for (Port p : ends) {
            endAt.put(index(xs, p.x()) * ny + index(ys, p.y()), p);
        }
        double bestTotal = Double.POSITIVE_INFINITY;
        int bestState = -1;
        while (!queue.isEmpty()) {
            double[] q = queue.poll();
            int state = (int) q[2];
            double cost = q[1];
            if (cost > best[state] || q[0] >= bestTotal) {
                if (q[0] >= bestTotal) {
                    break;
                }
                continue;
            }
            int node = state / 4;
            int dir = state % 4;
            Port end = endAt.get(node);
            if (end != null) {
                // l'ultimo tratto entra nel padre in orizzontale, verso il bordo
                int into = end.outward() > 0 ? W : E;
                if (dir != opposite(into)) {
                    double total = cost + (dir == into ? 0 : BEND);
                    if (total < bestTotal) {
                        bestTotal = total;
                        bestState = state;
                    }
                }
            }
            int i = node / ny;
            int j = node % ny;
            for (int d = 0; d < 4; d++) {
                if (d == opposite(dir)) {
                    continue;
                }
                int ni = i + (d == E ? 1 : d == W ? -1 : 0);
                int nj = j + (d == S ? 1 : d == N ? -1 : 0);
                if (ni < 0 || nj < 0 || ni >= nx || nj >= ny || !free[ni * ny + nj]) {
                    continue;
                }
                double x1 = xs[i];
                double y1 = ys[j];
                double x2 = xs[ni];
                double y2 = ys[nj];
                if (blocked(obstacles, x1, y1, x2, y2)) {
                    continue;
                }
                double len = Math.abs(x2 - x1) + Math.abs(y2 - y1);
                double step = len + SHARED * shared(x1, y1, x2, y2) + NEAR * alongside(x1, y1, x2, y2)
                        + (d == dir ? 0 : BEND);
                int next = (ni * ny + nj) * 4 + d;
                double c = cost + step;
                if (c < best[next]) {
                    best[next] = c;
                    from[next] = state;
                    queue.add(new double[] {c + heuristic(x2, y2, ends), c, next});
                }
            }
        }
        if (bestState < 0) {
            return null;
        }
        List<Point2D> pts = new ArrayList<>();
        int s = bestState;
        while (s >= 0) {
            int node = s / 4;
            pts.add(0, new Point2D.Double(xs[node / ny], ys[node % ny]));
            if (startAt.containsKey(s)) {
                break;
            }
            s = from[s];
        }
        Port start = startAt.get(s);
        Port end = endAt.get(bestState / 4);
        pts.add(0, new Point2D.Double(start.edgeX(), start.y()));
        pts.add(new Point2D.Double(end.edgeX(), end.y()));
        return simplify(pts);
    }

    /** Il punto è sul bordo sinistro o destro dell'entità, alla riga data. */
    private static boolean near(Point2D edge, Rectangle2D entity, double y) {
        return Math.abs(edge.getY() - y) < 0.5 && (Math.abs(edge.getX() - entity.getMinX()) < 0.5
                || Math.abs(edge.getX() - entity.getMaxX()) < 0.5);
    }

    /** Le uscite possibili da una riga: a destra e a sinistra, se il tratto orizzontale è libero. */
    private static List<Port> ports(Rectangle2D entity, double y, List<Rectangle2D> obstacles) {
        List<Port> out = new ArrayList<>();
        for (int side : new int[] {1, -1}) {
            double edge = side > 0 ? entity.getMaxX() : entity.getMinX();
            double x = edge + side * STUB;
            boolean ok = true;
            for (Rectangle2D o : obstacles) {
                if (!o.equals(inflate(entity)) && (strictlyInside(o, x, y) || crosses(o, Math.min(edge, x), y, Math.max(edge, x), y))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(new Port(x, y, edge, side));
            }
        }
        return out;
    }

    private static Rectangle2D inflate(Rectangle2D r) {
        return new Rectangle2D.Double(r.getX() - CLEAR, r.getY() - CLEAR, r.getWidth() + 2 * CLEAR,
                r.getHeight() + 2 * CLEAR);
    }

    private static double[] withLanes(TreeSet<Double> base, double min, double max) {
        TreeSet<Double> all = new TreeSet<>();
        Double prev = null;
        for (double v : base) {
            if (v < min || v > max) {
                continue;
            }
            all.add(v);
            if (prev != null && v - prev > 2 * LANE) {
                double mid = (prev + v) / 2;
                all.add(mid);
                for (int k = 1; k <= 2; k++) {
                    for (double lane : new double[] {mid - k * LANE, mid + k * LANE}) {
                        if (lane > prev + LANE / 2 && lane < v - LANE / 2) {
                            all.add(lane);
                        }
                    }
                }
            }
            prev = v;
        }
        double[] out = new double[all.size()];
        int i = 0;
        for (double v : all) {
            out[i++] = v;
        }
        return out;
    }

    private static int index(double[] values, double v) {
        int i = Arrays.binarySearch(values, v);
        if (i < 0) {
            throw new IllegalStateException("coordinata assente: " + v);
        }
        return i;
    }

    private static double heuristic(double x, double y, List<Port> ends) {
        double h = Double.POSITIVE_INFINITY;
        for (Port p : ends) {
            h = Math.min(h, Math.abs(p.x() - x) + Math.abs(p.y() - y));
        }
        return h;
    }

    private static int opposite(int d) {
        return switch (d) {
            case E -> W;
            case W -> E;
            case N -> S;
            default -> N;
        };
    }

    private static boolean inside(List<Rectangle2D> obstacles, double x, double y) {
        for (Rectangle2D o : obstacles) {
            if (strictlyInside(o, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean strictlyInside(Rectangle2D o, double x, double y) {
        return x > o.getMinX() + 1e-6 && x < o.getMaxX() - 1e-6 && y > o.getMinY() + 1e-6 && y < o.getMaxY() - 1e-6;
    }

    private static boolean blocked(List<Rectangle2D> obstacles, double x1, double y1, double x2, double y2) {
        for (Rectangle2D o : obstacles) {
            if (crosses(o, Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2))) {
                return true;
            }
        }
        return false;
    }

    /** Un tratto orizzontale o verticale che passa per l'interno del rettangolo (i bordi si possono percorrere). */
    private static boolean crosses(Rectangle2D o, double x1, double y1, double x2, double y2) {
        if (y1 == y2) {
            return y1 > o.getMinY() + 1e-6 && y1 < o.getMaxY() - 1e-6 && x1 < o.getMaxX() - 1e-6
                    && x2 > o.getMinX() + 1e-6;
        }
        return x1 > o.getMinX() + 1e-6 && x1 < o.getMaxX() - 1e-6 && y1 < o.getMaxY() - 1e-6 && y2 > o.getMinY() + 1e-6;
    }

    /** Quanta parte del tratto è già percorsa da un'altra relazione. */
    private double shared(double x1, double y1, double x2, double y2) {
        List<double[]> used;
        double a;
        double b;
        if (y1 == y2) {
            used = horizontal.get(key(y1));
            a = Math.min(x1, x2);
            b = Math.max(x1, x2);
        } else {
            used = vertical.get(key(x1));
            a = Math.min(y1, y2);
            b = Math.max(y1, y2);
        }
        if (used == null) {
            return 0;
        }
        double total = 0;
        for (double[] u : used) {
            total += Math.max(0, Math.min(b, u[1]) - Math.max(a, u[0]));
        }
        return Math.min(total, b - a);
    }

    /** Quanta parte del tratto corre accanto (a meno di {@link #SEPARATION}, non sopra) a un'altra relazione. */
    private double alongside(double x1, double y1, double x2, double y2) {
        boolean horizontalSegment = y1 == y2;
        double at = horizontalSegment ? y1 : x1;
        double a = horizontalSegment ? Math.min(x1, x2) : Math.min(y1, y2);
        double b = horizontalSegment ? Math.max(x1, x2) : Math.max(y1, y2);
        Map<Long, List<double[]>> lines = horizontalSegment ? horizontal : vertical;
        double total = 0;
        for (long k = key(at - SEPARATION) + 1; k < key(at + SEPARATION); k++) {
            if (k == key(at)) {
                continue;
            }
            List<double[]> used = lines.get(k);
            if (used != null) {
                for (double[] u : used) {
                    total += Math.max(0, Math.min(b, u[1]) - Math.max(a, u[0]));
                }
            }
        }
        return Math.min(total, b - a);
    }

    /** Toglie i punti intermedi allineati. */
    static List<Point2D> simplify(List<Point2D> pts) {
        List<Point2D> out = new ArrayList<>();
        for (Point2D p : pts) {
            if (!out.isEmpty() && out.get(out.size() - 1).distance(p) < 1e-6) {
                continue;
            }
            if (out.size() >= 2) {
                Point2D a = out.get(out.size() - 2);
                Point2D b = out.get(out.size() - 1);
                boolean sameX = Math.abs(a.getX() - b.getX()) < 1e-6 && Math.abs(b.getX() - p.getX()) < 1e-6;
                boolean sameY = Math.abs(a.getY() - b.getY()) < 1e-6 && Math.abs(b.getY() - p.getY()) < 1e-6;
                if (sameX || sameY) {
                    out.set(out.size() - 1, p);
                    continue;
                }
            }
            out.add(p);
        }
        return out;
    }
}
