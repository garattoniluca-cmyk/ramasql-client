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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;

/**
 * Retroingegneria ({@code DESIGN.md} §3.11, come «Reverse Engineer» di Workbench): dalle tabelle del catalogo alle
 * entità del modello, dalle chiavi esterne alle relazioni <b>fisiche</b>, con la cardinalità dedotta dai metadati
 * ({@link #cardinality}): colonne della figlia uniche → 1:1, altrimenti 1:N; obbligatoria se le colonne sono NOT NULL.
 * Le viste non sono entità del modello.
 */
public final class ReverseEngineer {

    private ReverseEngineer() {
    }

    /**
     * Legge dal server (canale dei metadati, fuori dall'EDT) le tabelle scelte, o tutte ({@code tables} vuoto).
     */
    public static ErModel fromCatalog(MetadataReader reader, String catalog, List<String> tables) throws SQLException {
        List<TableDef> defs = new ArrayList<>();
        boolean whole = tables == null || tables.isEmpty();
        if (whole) {
            for (TableSummary s : reader.tables(catalog)) {
                if (!s.isView()) {
                    reader.table(catalog, s.name()).ifPresent(defs::add);
                }
            }
        } else {
            for (String t : tables) {
                reader.table(catalog, t).ifPresent(defs::add);
            }
        }
        return build(catalog, defs, whole);
    }

    /** Il modello dalle definizioni delle tabelle (senza server: per i test e per «Aggiorna dal database»). */
    public static ErModel build(String catalog, List<TableDef> tables, boolean wholeCatalog) {
        List<ErModel.Entity> entities = new ArrayList<>();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (TableDef t : tables) {
            entities.add(entity(t));
            names.add(t.name());
        }
        List<Relationship> relationships = new ArrayList<>();
        for (TableDef t : tables) {
            for (ForeignKeyDef fk : t.foreignKeys()) {
                boolean sameCatalog = fk.refCatalog() == null || fk.refCatalog().equalsIgnoreCase(catalog)
                        || fk.refCatalog().equalsIgnoreCase(t.catalog());
                if (!sameCatalog || !names.contains(fk.refTable())) {
                    continue;   // riferisce una tabella fuori dal modello
                }
                String label = fk.name() == null ? "" : fk.name();
                relationships.add(new Relationship(physicalId(t.name(), fk), Relationship.Kind.PHYSICAL, t.name(),
                        fk.columns(), fk.refTable(), fk.refColumns(), cardinality(t, fk.columns()),
                        mandatory(t, fk.columns()), label));
            }
        }
        return new ErModel(catalog, catalog, wholeCatalog, entities, relationships);
    }

    static String physicalId(String table, ForeignKeyDef fk) {
        return "fk:" + table.toLowerCase(Locale.ROOT) + "." + (fk.name() == null
                ? String.join(",", fk.columns()) : fk.name()).toLowerCase(Locale.ROOT);
    }

    /** L'istantanea di una tabella. */
    public static ErModel.Entity entity(TableDef t) {
        List<String> pk = t.primaryKey().map(IndexDef::columns).orElse(List.of());
        List<List<String>> uniques = new ArrayList<>();
        Set<String> singleUnique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (IndexDef i : t.indexes()) {
            if (i.isUnique()) {
                uniques.add(i.columns());
                if (i.columns().size() == 1) {
                    singleUnique.add(i.columns().get(0));
                }
            }
        }
        List<ErModel.Attribute> columns = new ArrayList<>();
        for (ColumnDef c : t.columns()) {
            boolean inPk = pk.stream().anyMatch(c.name()::equalsIgnoreCase);
            String type = c.fullType() + (c.unsigned() ? " UNSIGNED" : "");
            columns.add(new ErModel.Attribute(c.name(), type, c.nullable(), inPk,
                    !inPk && singleUnique.contains(c.name())));
        }
        return new ErModel.Entity(t.name(), t.engine(), columns, uniques, 0, 0, false);
    }

    /**
     * T11.3 — cardinalità dai metadati: se le colonne della chiave esterna sono esattamente la chiave primaria o un
     * indice UNIQUE della figlia, a ogni padre corrisponde al più una figlia (1:1); altrimenti 1:N.
     */
    public static Relationship.Cardinality cardinality(TableDef child, List<String> fkColumns) {
        for (IndexDef i : child.indexes()) {
            if (i.isUnique() && i.columns().size() == fkColumns.size()
                    && i.columns().stream().allMatch(c -> fkColumns.stream().anyMatch(c::equalsIgnoreCase))) {
                return Relationship.Cardinality.ONE_TO_ONE;
            }
        }
        return Relationship.Cardinality.ONE_TO_MANY;
    }

    /** Obbligatoria: tutte le colonne della chiave esterna sono NOT NULL (ogni figlia ha il suo padre). */
    public static boolean mandatory(TableDef child, List<String> fkColumns) {
        return fkColumns.stream().allMatch(c -> child.column(c).map(col -> !col.nullable()).orElse(false));
    }

    /**
     * Una tabella ponte: la sua chiave primaria è fatta esattamente delle colonne di due relazioni verso due padri
     * ({@code libri_autori}: libri N:M autori). La relazione N:M è indicativa: nel database sono due 1:N.
     *
     * @param table la tabella ponte
     * @param left  un padre
     * @param right l'altro padre
     */
    public record Bridge(String table, String left, String right) {
    }

    /** Le tabelle ponte del modello (con relazioni fisiche o logiche). */
    public static List<Bridge> bridges(ErModel model) {
        List<Bridge> out = new ArrayList<>();
        for (ErModel.Entity e : model.entities()) {
            List<String> pk = e.primaryKey();
            if (pk.size() < 2) {
                continue;
            }
            List<Relationship> inPk = model.relationships().stream()
                    .filter(r -> r.fromTable().equalsIgnoreCase(e.table())
                            && r.fromColumns().stream().allMatch(c -> pk.stream().anyMatch(c::equalsIgnoreCase)))
                    .toList();
            if (inPk.size() != 2) {
                continue;
            }
            Set<String> covered = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            inPk.forEach(r -> covered.addAll(r.fromColumns()));
            if (covered.size() == pk.size()) {
                out.add(new Bridge(e.table(), inPk.get(0).toTable(), inPk.get(1).toTable()));
            }
        }
        return out;
    }
}
