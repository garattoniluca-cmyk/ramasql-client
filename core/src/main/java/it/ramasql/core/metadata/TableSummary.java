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
 * Riga dell'elenco veloce delle tabelle e delle viste di un catalogo (senza colonne): è ciò che il navigatore
 * mostra aprendo un catalogo. I dettagli si chiedono dopo, a richiesta, con {@link MetadataReader#table}.
 *
 * @param catalog catalogo
 * @param name    nome
 * @param kind    tabella o vista
 * @param engine  {@code InnoDB}, {@code MyISAM}…; {@code null} per le viste
 * @param comment commento della tabella ({@code ""} se assente, sempre {@code ""} per le viste)
 */
public record TableSummary(String catalog, String name, TableKind kind, String engine, String comment) {

    public TableSummary {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        engine = engine == null || engine.isBlank() ? null : engine;
        comment = comment == null ? "" : comment;
    }

    public boolean isView() {
        return kind == TableKind.VIEW;
    }

    /** Vero per le tabelle MyISAM (icona diversa nel navigatore, niente chiavi esterne). */
    public boolean isMyIsam() {
        return "MyISAM".equalsIgnoreCase(engine);
    }
}
