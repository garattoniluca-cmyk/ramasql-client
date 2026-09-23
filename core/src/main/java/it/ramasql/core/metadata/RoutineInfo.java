/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import java.util.Objects;

/**
 * Procedura, funzione, trigger o evento, elencato in sola lettura (ADR-017): nome e tipo; il testo si legge a
 * richiesta con {@link MetadataReader#showCreate(RoutineInfo)}.
 *
 * @param catalog catalogo
 * @param name    nome
 * @param kind    tipo di oggetto
 * @param table   per i trigger, la tabella a cui sono legati; {@code null} per gli altri
 * @param detail  descrizione breve: per i trigger {@code BEFORE INSERT}, per le funzioni il tipo restituito,
 *                per gli eventi lo stato; {@code ""} se non c'è
 */
public record RoutineInfo(String catalog, String name, RoutineKind kind, String table, String detail) {

    public RoutineInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        table = table == null || table.isBlank() ? null : table;
        detail = detail == null ? "" : detail;
    }
}
