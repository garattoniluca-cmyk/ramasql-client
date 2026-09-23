/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.util.List;

/**
 * Da dove il completamento (Ctrl+Spazio) prende i nomi di cataloghi, tabelle e colonne. Nel programma lo implementa
 * l'integrazione con i metadati (con una cache: si chiama sull'EDT a ogni Ctrl+Spazio); nei test è in memoria.
 * I nomi si restituiscono come sono sul server; un elenco sconosciuto è una lista vuota, mai {@code null}.
 */
public interface CompletionSource {

    /** Catalogo in uso ({@code USE}), {@code null} se nessuno. */
    String currentCatalog();

    /** Cataloghi visibili all'utente. */
    List<String> catalogs();

    /** Tabelle e viste di un catalogo. */
    List<String> tables(String catalog);

    /** Colonne di una tabella o vista, nell'ordine della tabella. */
    List<String> columns(String catalog, String table);

    /** Nessun metadato (editor senza connessione): restano le parole chiave. */
    static CompletionSource empty() {
        return new CompletionSource() {
            @Override
            public String currentCatalog() {
                return null;
            }

            @Override
            public List<String> catalogs() {
                return List.of();
            }

            @Override
            public List<String> tables(String catalog) {
                return List.of();
            }

            @Override
            public List<String> columns(String catalog, String table) {
                return List.of();
            }
        };
    }
}
