/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.util.List;
import java.util.Optional;

import it.ramasql.core.metadata.TableDef;

/**
 * Le altre tabelle del catalogo, come le vede l'editor: servono a scegliere la tabella riferita da una chiave esterna
 * (e a controllarne tipi, indici ed engine <b>senza contattare il server</b>) e a sapere chi riferisce la tabella in
 * modifica (blocco della conversione a MyISAM). Nel programma la implementa chi conosce i metadati già letti; nei
 * test una finta in memoria.
 */
public interface CatalogTables {

    /** Una chiave esterna di un'altra tabella che punta alla tabella in modifica. */
    record IncomingReference(String table, String constraint) {
    }

    /** Nomi delle tabelle del catalogo (senza viste), in ordine alfabetico. */
    List<String> tableNames();

    /** Definizione di una tabella del catalogo; vuoto se non esiste o non è leggibile. */
    Optional<TableDef> table(String name);

    /** Le chiavi esterne di <i>altre</i> tabelle che riferiscono {@code tableName}. */
    List<IncomingReference> referencing(String tableName);

    /** Catalogo senza altre tabelle (utile per una tabella nuova in un catalogo vuoto). */
    static CatalogTables empty() {
        return new CatalogTables() {
            @Override
            public List<String> tableNames() {
                return List.of();
            }

            @Override
            public Optional<TableDef> table(String name) {
                return Optional.empty();
            }

            @Override
            public List<IncomingReference> referencing(String tableName) {
                return List.of();
            }
        };
    }
}
