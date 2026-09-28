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
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Un modello ER ({@code DESIGN.md} §3.11, {@code ARCHITECTURE.md} §6): le entità (un'istantanea delle tabelle, così il
 * modello si apre anche senza connessione), le relazioni <b>fisiche</b> (le chiavi esterne del server) e <b>logiche</b>
 * (disegnate a mano o accettate dai suggerimenti: non toccano il database) e le posizioni del diagramma. Immutabile: le
 * modifiche producono un modello nuovo, così annullare e confrontare sono semplici.
 *
 * @param name          nome del modello (di solito il catalogo)
 * @param catalog       catalogo da cui viene
 * @param wholeCatalog  il modello comprende tutte le tabelle del catalogo: «Aggiorna dal database» aggiunge anche le
 *                      tabelle nuove
 * @param entities      entità
 * @param relationships relazioni
 * @param server        il server da cui viene ({@code host:porta} del profilo; {@code ""} se non si sa): *Aggiorna dal
 *                      database* e l'apertura delle tabelle si rifiutano su un altro server, che può avere un catalogo
 *                      con lo stesso nome ma tabelle diverse
 */
public record ErModel(String name, String catalog, boolean wholeCatalog, List<Entity> entities,
        List<Relationship> relationships, String server) {

    public ErModel {
        name = name == null ? "" : name;
        entities = List.copyOf(entities);
        relationships = List.copyOf(relationships);
        server = server == null ? "" : server;
    }

    /** Un modello di cui non si sa il server. */
    public ErModel(String name, String catalog, boolean wholeCatalog, List<Entity> entities,
            List<Relationship> relationships) {
        this(name, catalog, wholeCatalog, entities, relationships, "");
    }

    public ErModel withServer(String v) {
        return new ErModel(name, catalog, wholeCatalog, entities, relationships, v);
    }

    /**
     * Una colonna dell'entità.
     *
     * @param name       nome
     * @param type       tipo completo, come lo mostra il server ({@code VARCHAR(60)}, {@code INT UNSIGNED})
     * @param nullable   ammette NULL
     * @param primaryKey fa parte della chiave primaria
     * @param unique     ha un indice UNIQUE di una sola colonna
     */
    public record Attribute(String name, String type, boolean nullable, boolean primaryKey, boolean unique) {
        public Attribute {
            Objects.requireNonNull(name, "name");
            type = type == null ? "" : type;
        }
    }

    /**
     * Un'entità del diagramma: l'istantanea di una tabella.
     *
     * @param table   nome della tabella
     * @param engine  engine ({@code InnoDB}, {@code MyISAM}; {@code null} se non noto)
     * @param columns colonne, in ordine
     * @param uniques gruppi di colonne con un vincolo di unicità (chiave primaria e indici UNIQUE)
     * @param x       posizione nel diagramma (in unità di disegno a scala 100%)
     * @param y       posizione nel diagramma
     * @param missing la tabella non c'è più nel database («Aggiorna dal database»): resta, segnata
     */
    public record Entity(String table, String engine, List<Attribute> columns, List<List<String>> uniques, double x,
            double y, boolean missing) {

        public Entity {
            Objects.requireNonNull(table, "table");
            columns = List.copyOf(columns);
            List<List<String>> u = new ArrayList<>();
            for (List<String> g : uniques == null ? List.<List<String>>of() : uniques) {
                u.add(List.copyOf(g));
            }
            uniques = List.copyOf(u);
        }

        public Optional<Attribute> column(String columnName) {
            return columns.stream().filter(c -> c.name().equalsIgnoreCase(columnName)).findFirst();
        }

        /** Colonne della chiave primaria, in ordine. */
        public List<String> primaryKey() {
            return columns.stream().filter(Attribute::primaryKey).map(Attribute::name).toList();
        }

        public Entity at(double newX, double newY) {
            return new Entity(table, engine, columns, uniques, newX, newY, missing);
        }

        public Entity withMissing(boolean v) {
            return new Entity(table, engine, columns, uniques, x, y, v);
        }

        /** Le colonne sono esattamente un gruppo unico (chiave primaria o UNIQUE). */
        public boolean isUnique(List<String> cols) {
            for (List<String> g : uniques) {
                if (g.size() == cols.size() && g.stream().allMatch(c -> cols.stream().anyMatch(c::equalsIgnoreCase))) {
                    return true;
                }
            }
            return false;
        }
    }

    public Optional<Entity> entity(String table) {
        return entities.stream().filter(e -> e.table().equalsIgnoreCase(table)).findFirst();
    }

    public ErModel withEntities(List<Entity> v) {
        return new ErModel(name, catalog, wholeCatalog, v, relationships, server);
    }

    public ErModel withRelationships(List<Relationship> v) {
        return new ErModel(name, catalog, wholeCatalog, entities, v, server);
    }

    /** L'entità cambiata (per nome della tabella). */
    public ErModel changeEntity(String table, UnaryOperator<Entity> change) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : entities) {
            out.add(e.table().equalsIgnoreCase(table) ? change.apply(e) : e);
        }
        return withEntities(out);
    }

    /** Aggiunge una relazione logica (le fisiche vengono solo dal database). */
    public ErModel addLogical(Relationship r) {
        if (r.kind() != Relationship.Kind.LOGICAL) {
            throw new IllegalArgumentException("solo relazioni logiche");
        }
        List<Relationship> out = new ArrayList<>(relationships);
        out.add(r);
        return withRelationships(out);
    }

    public ErModel removeRelationship(String id) {
        return withRelationships(relationships.stream().filter(r -> !r.id().equals(id)).toList());
    }

    public List<Relationship> physical() {
        return relationships.stream().filter(r -> r.kind() == Relationship.Kind.PHYSICAL).toList();
    }

    public List<Relationship> logical() {
        return relationships.stream().filter(r -> r.kind() == Relationship.Kind.LOGICAL).toList();
    }

    /** Esiste già una relazione (di qualunque tipo) fra quelle colonne. */
    public boolean hasRelationship(String fromTable, List<String> fromColumns, String toTable) {
        return relationships.stream().anyMatch(r -> r.fromTable().equalsIgnoreCase(fromTable)
                && r.toTable().equalsIgnoreCase(toTable) && sameColumns(r.fromColumns(), fromColumns));
    }

    static boolean sameColumns(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).toLowerCase(Locale.ROOT).equals(b.get(i).toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}
