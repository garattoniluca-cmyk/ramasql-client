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

import java.util.List;
import java.util.Objects;

/**
 * Una relazione fra due entità, dalla tabella «figlia» (dove stanno le colonne che riferiscono: il lato «molti») alla
 * tabella «padre» (il lato «uno»).
 *
 * @param id          identificatore nel modello
 * @param kind        fisica (una chiave esterna del server) o logica (solo nel modello)
 * @param fromTable   tabella figlia
 * @param fromColumns colonne della figlia
 * @param toTable     tabella padre
 * @param toColumns   colonne riferite del padre
 * @param cardinality 1:1, 1:N o N:M indicativa
 * @param mandatory   ogni riga della figlia ha il suo padre (colonne NOT NULL)
 * @param label       etichetta ({@code ""} = nessuna); per le fisiche il nome del vincolo
 */
public record Relationship(String id, Kind kind, String fromTable, List<String> fromColumns, String toTable,
        List<String> toColumns, Cardinality cardinality, boolean mandatory, String label) {

    /** Fisica (FK del server) o logica (disegnata o accettata, non tocca il database). */
    public enum Kind { PHYSICAL, LOGICAL }

    /** Cardinalità dal lato padre al lato figlio. */
    public enum Cardinality {
        /** Uno a uno: le colonne della figlia sono uniche. */
        ONE_TO_ONE,
        /** Uno a molti: il caso comune. */
        ONE_TO_MANY,
        /** Molti a molti, indicativa: una tabella ponte fra due padri. */
        MANY_TO_MANY
    }

    public Relationship {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(fromTable, "fromTable");
        Objects.requireNonNull(toTable, "toTable");
        fromColumns = List.copyOf(fromColumns);
        toColumns = List.copyOf(toColumns);
        cardinality = cardinality == null ? Cardinality.ONE_TO_MANY : cardinality;
        label = label == null ? "" : label;
    }

    public Relationship withCardinality(Cardinality v) {
        return new Relationship(id, kind, fromTable, fromColumns, toTable, toColumns, v, mandatory, label);
    }

    public Relationship withLabel(String v) {
        return new Relationship(id, kind, fromTable, fromColumns, toTable, toColumns, cardinality, mandatory, v);
    }

    /** Descrizione breve: {@code libri.id_editore → editori.id}. */
    public String describe() {
        return fromTable + "." + String.join(", ", fromColumns) + " → " + toTable + "." + String.join(", ", toColumns);
    }
}
