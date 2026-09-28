/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Disposizione automatica ({@code ARCHITECTURE.md} §6), semplice e senza librerie di grafi: le entità si mettono su
 * una griglia di celle tutte grandi come l'entità più grande (più un margine), <b>a spirale dal centro</b>; le tabelle
 * più collegate (più relazioni) prendono le celle centrali, e ogni tabella cerca la cella libera più vicina alle
 * tabelle a cui è già collegata. Le entità non si sovrappongono mai (una per cella).
 */
public final class AutoLayout {

    /** Dimensione di un'entità disegnata, in unità di disegno. */
    public record Size(double width, double height) {
    }

    /** Come si misura un'entità (il canvas usa i caratteri veri; i test una stima). */
    @FunctionalInterface
    public interface Measure {
        Size of(ErModel.Entity entity);
    }

    /** Stima senza interfaccia: 7,5 unità per carattere, 22 per riga (titolo compreso). */
    public static final Measure ESTIMATE = e -> {
        int chars = e.table().length() + 4;
        for (ErModel.Attribute a : e.columns()) {
            chars = Math.max(chars, a.name().length() + a.type().length() + 6);
        }
        return new Size(chars * 7.5 + 24, (e.columns().size() + 1) * 22.0 + 16);
    };

    /** Spazio fra le celle. */
    public static final double GAP = 60;

    private AutoLayout() {
    }

    /** Il modello con tutte le entità ridisposte. */
    public static ErModel layout(ErModel model, Measure measure) {
        List<ErModel.Entity> all = model.entities();
        if (all.isEmpty()) {
            return model;
        }
        double cellW = 0;
        double cellH = 0;
        for (ErModel.Entity e : all) {
            Size s = measure.of(e);
            cellW = Math.max(cellW, s.width());
            cellH = Math.max(cellH, s.height());
        }
        cellW += GAP;
        cellH += GAP;
        Map<String, Integer> degree = new HashMap<>();
        for (Relationship r : model.relationships()) {
            degree.merge(key(r.fromTable()), 1, Integer::sum);
            degree.merge(key(r.toTable()), 1, Integer::sum);
        }
        List<ErModel.Entity> order = new ArrayList<>(all);
        order.sort(Comparator.<ErModel.Entity>comparingInt(e -> -degree.getOrDefault(key(e.table()), 0))
                .thenComparing(e -> e.table().toLowerCase(Locale.ROOT)));
        List<int[]> cells = spiral(all.size() * 3 + 9);
        Map<String, int[]> placed = new HashMap<>();
        boolean[] used = new boolean[cells.size()];
        for (ErModel.Entity e : order) {
            List<int[]> neighbours = new ArrayList<>();
            for (Relationship r : model.relationships()) {
                String other = r.fromTable().equalsIgnoreCase(e.table()) ? r.toTable()
                        : r.toTable().equalsIgnoreCase(e.table()) ? r.fromTable() : null;
                if (other != null && placed.containsKey(key(other))) {
                    neighbours.add(placed.get(key(other)));
                }
            }
            int best = -1;
            double bestCost = Double.MAX_VALUE;
            for (int i = 0; i < cells.size(); i++) {
                if (used[i]) {
                    continue;
                }
                int[] c = cells.get(i);
                double cost = Math.hypot(c[0], c[1]) * 0.35;   // vicino al centro
                for (int[] n : neighbours) {
                    cost += Math.hypot(c[0] - n[0], c[1] - n[1]);   // vicino alle tabelle collegate
                }
                if (cost < bestCost - 1e-9) {
                    bestCost = cost;
                    best = i;
                }
            }
            used[best] = true;
            placed.put(key(e.table()), cells.get(best));
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int[] c : placed.values()) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
        }
        List<ErModel.Entity> out = new ArrayList<>();
        for (ErModel.Entity e : all) {
            int[] c = placed.get(key(e.table()));
            out.add(e.at(40 + (c[0] - minX) * cellW, 40 + (c[1] - minY) * cellH));
        }
        return model.withEntities(out);
    }

    private static String key(String table) {
        return table.toLowerCase(Locale.ROOT);
    }

    /** Celle della griglia in ordine di distanza dal centro (0,0), a spirale, un po' più larghe che alte. */
    static List<int[]> spiral(int count) {
        List<int[]> out = new ArrayList<>();
        int radius = 0;
        while (out.size() < count) {
            List<int[]> ring = new ArrayList<>();
            for (int x = -radius - radius / 2; x <= radius + radius / 2; x++) {
                for (int y = -radius; y <= radius; y++) {
                    double r = Math.max(Math.abs(x) / 1.5, Math.abs(y));
                    if (Math.ceil(r - 1e-9) == radius || (radius == 0 && x == 0 && y == 0)) {
                        ring.add(new int[] {x, y});
                    }
                }
            }
            ring.sort(Comparator.comparingDouble(c -> Math.hypot(c[0], c[1] * 1.3)));
            out.addAll(ring);
            radius++;
        }
        return out;
    }

    /** Le entità si sovrappongono? (Per i test e per il canvas dopo un trascinamento.) */
    public static boolean overlaps(ErModel model, Measure measure) {
        List<ErModel.Entity> e = model.entities();
        for (int i = 0; i < e.size(); i++) {
            Size a = measure.of(e.get(i));
            for (int j = i + 1; j < e.size(); j++) {
                Size b = measure.of(e.get(j));
                boolean apart = e.get(i).x() + a.width() <= e.get(j).x() || e.get(j).x() + b.width() <= e.get(i).x()
                        || e.get(i).y() + a.height() <= e.get(j).y() || e.get(j).y() + b.height() <= e.get(i).y();
                if (!apart) {
                    return true;
                }
            }
        }
        return false;
    }
}
