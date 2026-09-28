/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import it.ramasql.qb.QbRuntime;

/**
 * Codice NOSTRO (non derivato da SQLeo), nel pacchetto del query builder per raggiungerne i membri «di pacchetto».
 * Disposizione delle tabelle del diagramma che evita che un join passi sotto un'altra tabella ({@code BUG-023}).
 *
 * <p><b>Disposizione per collegamenti</b> ({@link #layered}): le tabelle collegate da join formano gruppi. In ogni
 * gruppo la tabella con più join (a parità, la prima aggiunta) sta al centro; le altre si dispongono per «distanza» da
 * lei (visita in ampiezza) in colonne, a destra e a sinistra: colonna ±1 quelle unite a lei, ±2 quelle unite a queste, e
 * così via. Ogni ramo che parte dal centro sta tutto da una parte (due rami uniti fra loro da un join stanno dalla
 * stessa parte); i rami si dividono fra destra e sinistra in modo da pareggiare il numero di tabelle, così il diagramma
 * resta basso e largo come l'area che lo mostra. In una visita in ampiezza ogni join unisce due tabelle a distanza
 * uguale o vicina: quindi, con i rami dalla stessa parte, sempre due colonne vicine o due tabelle della stessa colonna,
 * mai colonne lontane. Le tabelle di una colonna sono impilate e <b>larghe uguali</b>; tra una colonna e l'altra c'è un
 * corridoio vuoto. Così, qualunque sia il modo di disegnare le linee (archi o linee spezzate,
 * {@code QbOption.RELATION_ARCS}):
 * <ul>
 *   <li>un join tra colonne vicine parte dal bordo destro della colonna di sinistra e arriva al bordo sinistro di quella
 *       di destra: linea e nodo stanno tutti nel corridoio;</li>
 *   <li>un join tra due tabelle della stessa colonna gira a destra della colonna (a 15 o 30 px dal bordo, che è lo
 *       stesso per tutte perché le larghezze sono uguali): di nuovo nel corridoio, più largo di quella curva.</li>
 * </ul>
 * I gruppi stanno uno accanto all'altro. Dentro una colonna l'ordine segue la posizione media delle tabelle collegate
 * nella colonna più vicina al centro (meno incroci tra le linee), a parità l'ordine di aggiunta.
 *
 * <p><b>Posto per una tabella nuova</b> ({@link #place}): quando l'utente ha già sistemato il diagramma a mano non si
 * sposta nulla di suo; si cerca un posto per la sola tabella nuova, accanto alle tabelle a cui si unisce, in cui né lei
 * copre linee o nodi di altri join, né i suoi join passano sotto altre tabelle ({@link #conflicts}).
 */
final class DiagramArrange {

    private DiagramArrange() {
    }

    /** Margine del diagramma. */
    static int margin() {
        return QbRuntime.scale(12);
    }

    /** Corridoio tra due colonne: più largo della curva di un join nella stessa colonna (30 px) e di due nodi. */
    static int columnGap() {
        return Math.max(QbRuntime.scale(80), 30 + 4 * QbRuntime.scale(14));
    }

    /** Spazio tra due tabelle della stessa colonna. */
    static int rowGap() {
        return QbRuntime.scale(24);
    }

    // ---------------------------------------------------------------- disposizione per collegamenti

    /**
     * Dispone tutte le entità per collegamenti (vedi la descrizione della classe).
     *
     * @param entities  le entità nell'ordine in cui sono state aggiunte
     * @param relations i join del diagramma
     */
    static void layered(List<DiagramAbstractEntity> entities, DiagramRelation[] relations) {
        Map<DiagramAbstractEntity, Integer> order = new IdentityHashMap<>();
        for (DiagramAbstractEntity e : entities) {
            order.put(e, order.size());
        }
        Map<DiagramAbstractEntity, Set<DiagramAbstractEntity>> links = links(entities, relations, order);

        int x = margin();
        Set<DiagramAbstractEntity> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (DiagramAbstractEntity first : entities) {
            if (seen.contains(first)) {
                continue;
            }
            List<DiagramAbstractEntity> group = group(first, links, seen);
            List<List<DiagramAbstractEntity>> columns = columns(group, links, order);
            x = placeColumns(columns, x) + columnGap();
        }
    }

    /** Le entità collegate (anche indirettamente) a {@code first}. */
    private static List<DiagramAbstractEntity> group(DiagramAbstractEntity first,
            Map<DiagramAbstractEntity, Set<DiagramAbstractEntity>> links, Set<DiagramAbstractEntity> seen) {
        List<DiagramAbstractEntity> group = new ArrayList<>();
        ArrayDeque<DiagramAbstractEntity> queue = new ArrayDeque<>();
        queue.add(first);
        seen.add(first);
        while (!queue.isEmpty()) {
            DiagramAbstractEntity e = queue.poll();
            group.add(e);
            for (DiagramAbstractEntity n : links.get(e)) {
                if (seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return group;
    }

    /** Collegamenti (senza verso, senza doppioni) tra entità del diagramma, nell'ordine di aggiunta. */
    private static Map<DiagramAbstractEntity, Set<DiagramAbstractEntity>> links(List<DiagramAbstractEntity> entities,
            DiagramRelation[] relations, Map<DiagramAbstractEntity, Integer> order) {
        Map<DiagramAbstractEntity, Set<DiagramAbstractEntity>> links = new IdentityHashMap<>();
        for (DiagramAbstractEntity e : entities) {
            links.put(e, new LinkedHashSet<>());
        }
        for (DiagramRelation r : relations) {
            DiagramAbstractEntity a = r.primaryEntity;
            DiagramAbstractEntity b = r.foreignEntity;
            if (a == null || b == null || a == b || !order.containsKey(a) || !order.containsKey(b)) {
                continue;
            }
            links.get(a).add(b);
            links.get(b).add(a);
        }
        return links;
    }

    /** Colonne del gruppo, da sinistra a destra (vedi la descrizione della classe). */
    private static List<List<DiagramAbstractEntity>> columns(List<DiagramAbstractEntity> group,
            Map<DiagramAbstractEntity, Set<DiagramAbstractEntity>> links, Map<DiagramAbstractEntity, Integer> order) {
        // il centro: la tabella con più join, a parità la prima aggiunta
        DiagramAbstractEntity center = group.get(0);
        for (DiagramAbstractEntity e : group) {
            int d = links.get(e).size() - links.get(center).size();
            if (d > 0 || (d == 0 && order.get(e) < order.get(center))) {
                center = e;
            }
        }
        // visita in ampiezza dal centro: distanza e ramo (la tabella a distanza 1 da cui si discende)
        Map<DiagramAbstractEntity, Integer> depth = new IdentityHashMap<>();
        Map<DiagramAbstractEntity, DiagramAbstractEntity> branch = new IdentityHashMap<>();
        List<DiagramAbstractEntity> visit = new ArrayList<>();
        ArrayDeque<DiagramAbstractEntity> queue = new ArrayDeque<>();
        queue.add(center);
        depth.put(center, 0);
        while (!queue.isEmpty()) {
            DiagramAbstractEntity e = queue.poll();
            visit.add(e);
            List<DiagramAbstractEntity> next = new ArrayList<>(links.get(e));
            next.sort(Comparator.comparingInt(order::get));
            for (DiagramAbstractEntity n : next) {
                if (!depth.containsKey(n)) {
                    depth.put(n, depth.get(e) + 1);
                    branch.put(n, e == center ? n : branch.get(e));
                    queue.add(n);
                }
            }
        }
        // rami uniti da un join che non passa dal centro: stessa parte
        Map<DiagramAbstractEntity, DiagramAbstractEntity> joined = new IdentityHashMap<>();
        for (DiagramAbstractEntity e : visit) {
            if (e != center) {
                for (DiagramAbstractEntity n : links.get(e)) {
                    if (n != center) {
                        union(joined, branch.get(e), branch.get(n));
                    }
                }
            }
        }
        // i blocchi di rami, dal più grande (a parità, quello con la tabella aggiunta prima), alla parte con meno tabelle
        Map<DiagramAbstractEntity, List<DiagramAbstractEntity>> blocks = new LinkedHashMap<>();
        for (DiagramAbstractEntity e : visit) {
            if (e != center) {
                blocks.computeIfAbsent(find(joined, branch.get(e)), k -> new ArrayList<>()).add(e);
            }
        }
        List<List<DiagramAbstractEntity>> sorted = new ArrayList<>(blocks.values());
        sorted.sort(Comparator.<List<DiagramAbstractEntity>>comparingInt(b -> -b.size())
                .thenComparingInt(b -> b.stream().mapToInt(order::get).min().orElse(0)));
        Map<DiagramAbstractEntity, Integer> side = new IdentityHashMap<>();
        int right = 0;
        int left = 0;
        for (List<DiagramAbstractEntity> b : sorted) {
            int s = right <= left ? 1 : -1;
            for (DiagramAbstractEntity e : b) {
                side.put(e, s);
            }
            if (s > 0) {
                right += b.size();
            } else {
                left += b.size();
            }
        }
        // colonne: indice = parte × distanza (0 = il centro)
        int reach = 0;
        Map<Integer, List<DiagramAbstractEntity>> byIndex = new TreeMap<>();
        for (DiagramAbstractEntity e : visit) {
            int index = e == center ? 0 : side.get(e) * depth.get(e);
            byIndex.computeIfAbsent(index, k -> new ArrayList<>()).add(e);
            reach = Math.max(reach, Math.abs(index));
        }
        // ordine dentro la colonna, dal centro verso l'esterno: posizione media delle collegate nella colonna più interna
        for (int step = 1; step <= reach; step++) {
            for (int s : new int[] {1, -1}) {
                List<DiagramAbstractEntity> column = byIndex.get(s * step);
                List<DiagramAbstractEntity> inner = byIndex.get(s * (step - 1));
                if (column == null || inner == null) {
                    continue;
                }
                Map<DiagramAbstractEntity, Double> weight = new IdentityHashMap<>();
                for (DiagramAbstractEntity e : column) {
                    double sum = 0;
                    int count = 0;
                    for (DiagramAbstractEntity n : links.get(e)) {
                        int i = indexOf(inner, n);
                        if (i >= 0) {
                            sum += i;
                            count++;
                        }
                    }
                    weight.put(e, count == 0 ? Double.MAX_VALUE : sum / count);
                }
                column.sort(Comparator.<DiagramAbstractEntity>comparingDouble(weight::get).thenComparingInt(order::get));
            }
        }
        return new ArrayList<>(byIndex.values());
    }

    private static DiagramAbstractEntity find(Map<DiagramAbstractEntity, DiagramAbstractEntity> parent,
            DiagramAbstractEntity e) {
        DiagramAbstractEntity p = parent.getOrDefault(e, e);
        if (p == e) {
            return e;
        }
        DiagramAbstractEntity root = find(parent, p);
        parent.put(e, root);
        return root;
    }

    private static void union(Map<DiagramAbstractEntity, DiagramAbstractEntity> parent, DiagramAbstractEntity a,
            DiagramAbstractEntity b) {
        DiagramAbstractEntity ra = find(parent, a);
        DiagramAbstractEntity rb = find(parent, b);
        if (ra != rb) {
            parent.put(rb, ra);
        }
    }

    private static int indexOf(List<DiagramAbstractEntity> list, DiagramAbstractEntity e) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == e) {
                return i;
            }
        }
        return -1;
    }

    /** Posiziona le colonne a partire da {@code x}; restituisce il bordo destro del gruppo. */
    private static int placeColumns(List<List<DiagramAbstractEntity>> columns, int x) {
        int right = x;
        for (List<DiagramAbstractEntity> column : columns) {
            int width = 0;
            for (DiagramAbstractEntity e : column) {
                width = Math.max(width, e.getPreferredSize().width);
            }
            int y = margin();
            for (DiagramAbstractEntity e : column) {
                Dimension pref = e.getPreferredSize();
                int height = e.getHeight() > 0 ? e.getHeight() : pref.height;
                e.setSize(width, height);
                e.validate();
                e.setLocation(x, y);
                y += height + rowGap();
            }
            right = x + width;
            x = right + columnGap();
        }
        return right;
    }

    // ---------------------------------------------------------------- posto per una tabella nuova

    /**
     * Cerca un posto per {@code item} senza spostare le altre entità: accanto a ciascuna tabella a cui si unisce (a
     * destra, poi a sinistra, a varie altezze), poi a destra di tutto e sotto tutto. Prende il primo posto senza
     * conflitti; se non ce n'è uno, quello con meno conflitti (e, a parità, lascia dov'è).
     */
    static void place(DiagramAbstractEntity item, DiagramAbstractEntity[] entities, DiagramRelation[] relations) {
        List<DiagramAbstractEntity> neighbours = new ArrayList<>();
        List<DiagramRelation> own = new ArrayList<>();
        for (DiagramRelation r : relations) {
            if (r.primaryEntity == item || r.foreignEntity == item) {
                own.add(r);
                DiagramAbstractEntity other = r.primaryEntity == item ? r.foreignEntity : r.primaryEntity;
                if (other != null && other != item && !neighbours.contains(other)) {
                    neighbours.add(other);
                }
            }
        }
        int w = item.getWidth() > 0 ? item.getWidth() : item.getPreferredSize().width;
        int h = item.getHeight() > 0 ? item.getHeight() : item.getPreferredSize().height;
        int gap = columnGap();
        int step = rowGap();
        int maxRight = margin();
        int maxBottom = margin();
        for (DiagramAbstractEntity e : entities) {
            if (e != item) {
                maxRight = Math.max(maxRight, e.getX() + e.getWidth());
                maxBottom = Math.max(maxBottom, e.getY() + e.getHeight());
            }
        }

        List<Point> candidates = new ArrayList<>();
        for (DiagramAbstractEntity n : neighbours) {
            for (int k = 0; k <= 30; k++) {
                for (int sign : k == 0 ? new int[] {1} : new int[] {1, -1}) {
                    int y = n.getY() + sign * k * step;
                    if (y < margin()) {
                        continue;
                    }
                    candidates.add(new Point(n.getX() + n.getWidth() + gap, y));
                    if (n.getX() - gap - w >= margin()) {
                        candidates.add(new Point(n.getX() - gap - w, y));
                    }
                }
            }
        }
        for (int k = 0; k <= 60; k++) {
            candidates.add(new Point(maxRight + gap, margin() + k * step));
        }
        for (int k = 0; k <= 60; k++) {
            candidates.add(new Point(margin() + k * step, maxBottom + step));
        }

        Point original = item.getLocation();
        Point best = original;
        int bestScore = score(item, own, entities, relations);
        for (Point p : candidates) {
            if (bestScore == 0) {
                break;
            }
            move(item, own, p);
            int s = score(item, own, entities, relations);
            if (s < bestScore) {
                bestScore = s;
                best = p;
            }
        }
        move(item, own, best);
    }

    private static void move(DiagramAbstractEntity item, List<DiagramRelation> own, Point p) {
        item.setLocation(p);
        for (DiagramRelation r : own) {
            r.doResize();
        }
    }

    /** Conflitti che riguardano {@code item}: sovrapposizioni con altre entità, linee e nodi sotto entità. */
    private static int score(DiagramAbstractEntity item, List<DiagramRelation> own, DiagramAbstractEntity[] entities,
            DiagramRelation[] relations) {
        int conflicts = 0;
        Rectangle area = item.getBounds();
        area.grow(rowGap() / 2, rowGap() / 2);
        for (DiagramAbstractEntity e : entities) {
            if (e != item && area.intersects(e.getBounds())) {
                conflicts += 2;
            }
        }
        for (DiagramRelation r : relations) {
            if (own.contains(r)) {
                conflicts += conflicts(r, entities);
            } else if (r.primaryEntity != item && r.foreignEntity != item) {
                conflicts += conflicts(r, new DiagramAbstractEntity[] {item});
            }
        }
        return conflicts;
    }

    /**
     * Quante volte la linea o il nodo del join {@code r} stanno dentro un'entità di {@code entities} che non è un suo
     * capo (il nodo: dentro qualunque entità). La linea si approssima per eccesso con i rettangoli dei suoi tratti
     * ({@link DiagramRelation#pathBoxes}): se non tocca i rettangoli, non tocca l'entità.
     */
    static int conflicts(DiagramRelation r, DiagramAbstractEntity[] entities) {
        int n = 0;
        List<Rectangle> boxes = r.pathBoxes();
        Rectangle anchor = r.anchorBounds();
        for (DiagramAbstractEntity e : entities) {
            Rectangle inner = e.getBounds();
            inner.grow(-1, -1);
            if (anchor.intersects(inner)) {
                n++;
            }
            if (e == r.primaryEntity || e == r.foreignEntity) {
                continue;
            }
            for (Rectangle b : boxes) {
                if (b.intersects(inner)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }
}
