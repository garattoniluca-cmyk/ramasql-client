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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * «Aggiorna dal database» ({@code DESIGN.md} §3.11): confronta il modello con quello appena riletto dal server.
 * <ul>
 *   <li>entità che ci sono ancora: colonne aggiornate, <b>posizione conservata</b>;</li>
 *   <li>entità sparite dal database: restano dove sono, <b>segnate come mancanti</b> (non si perde il lavoro);</li>
 *   <li>tabelle nuove (solo se il modello comprende tutto il catalogo): aggiunte, in una posizione libera;</li>
 *   <li>relazioni fisiche: rifatte dalle chiavi esterne attuali; relazioni <b>logiche conservate</b> tutte.</li>
 * </ul>
 */
public final class ModelRefresh {

    /**
     * Esito.
     *
     * @param model   il modello aggiornato
     * @param added   tabelle aggiunte
     * @param missing tabelle non più nel database
 * @param changed  tabelle con colonne cambiate
     * @param broken   relazioni logiche che nominano colonne che non ci sono più ({@code descrizione})
     * @param promoted relazioni logiche diventate chiavi esterne vere sul server (tolte: ora sono fisiche)
     */
    public record Result(ErModel model, List<String> added, List<String> missing, List<String> changed,
            List<String> broken, List<String> promoted) {
        public Result {
            added = List.copyOf(added);
            missing = List.copyOf(missing);
            changed = List.copyOf(changed);
            broken = List.copyOf(broken);
            promoted = List.copyOf(promoted);
        }
    }

    private ModelRefresh() {
    }

    /**
     * @param current il modello aperto
     * @param fresh   il modello riletto dal server (stesse tabelle, o tutto il catalogo)
     * @param measure per mettere le tabelle nuove in uno spazio libero
     */
    public static Result refresh(ErModel current, ErModel fresh, AutoLayout.Measure measure) {
        List<ErModel.Entity> out = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        Set<String> present = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ErModel.Entity e : current.entities()) {
            var now = fresh.entity(e.table());
            if (now.isPresent()) {
                ErModel.Entity n = now.get();
                if (!n.columns().equals(e.columns()) || !n.uniques().equals(e.uniques())
                        || !java.util.Objects.equals(n.engine(), e.engine())) {
                    changed.add(e.table());
                }
                out.add(new ErModel.Entity(e.table(), n.engine(), n.columns(), n.uniques(), e.x(), e.y(), false));
                present.add(e.table());
            } else {
                out.add(e.withMissing(true));
                missing.add(e.table());
            }
        }
        double maxX = 0;
        double maxY = 0;
        for (ErModel.Entity e : out) {
            AutoLayout.Size s = measure.of(e);
            maxX = Math.max(maxX, e.x() + s.width());
            maxY = Math.max(maxY, e.y());
        }
        if (current.wholeCatalog()) {
            double x = maxX + AutoLayout.GAP;
            double y = 40;
            for (ErModel.Entity n : fresh.entities()) {
                if (current.entity(n.table()).isEmpty()) {
                    out.add(n.at(x, y));   // in una colonna nuova a destra: niente si sovrappone
                    y += measure.of(n).height() + AutoLayout.GAP;
                    added.add(n.table());
                    present.add(n.table());
                }
            }
        }
        List<Relationship> relationships = new ArrayList<>();
        for (Relationship r : fresh.physical()) {
            if (present.contains(r.fromTable()) && present.contains(r.toTable())) {
                relationships.add(r);
            }
        }
        List<String> promoted = new ArrayList<>();
        List<Relationship> logical = new ArrayList<>();
        for (Relationship l : current.logical()) {
            boolean nowPhysical = relationships.stream().anyMatch(p -> p.fromTable().equalsIgnoreCase(l.fromTable())
                    && p.toTable().equalsIgnoreCase(l.toTable()) && sameColumns(p.fromColumns(), l.fromColumns()));
            if (nowPhysical) {
                promoted.add(l.describe());   // due linee sovrapposte non servono: vale quella del server
            } else {
                logical.add(l);
            }
        }
        relationships.addAll(logical);
        ErModel model = new ErModel(current.name(), current.catalog(), current.wholeCatalog(), out, relationships,
                current.server());
        List<String> broken = new ArrayList<>();
        for (Relationship l : logical) {
            if (!brokenColumns(model, l).isEmpty()) {
                broken.add(l.describe());
            }
        }
        return new Result(model, added, missing, changed, broken, promoted);
    }

    private static boolean sameColumns(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equalsIgnoreCase(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Colonne di una relazione logica che non ci sono più (per segnalarla), in minuscolo. */
    public static List<String> brokenColumns(ErModel model, Relationship r) {
        List<String> out = new ArrayList<>();
        model.entity(r.fromTable()).ifPresent(e -> r.fromColumns().stream()
                .filter(c -> e.column(c).isEmpty()).forEach(c -> out.add(r.fromTable() + "." + c.toLowerCase(Locale.ROOT))));
        model.entity(r.toTable()).ifPresent(e -> r.toColumns().stream()
                .filter(c -> e.column(c).isEmpty()).forEach(c -> out.add(r.toTable() + "." + c.toLowerCase(Locale.ROOT))));
        return out;
    }
}
