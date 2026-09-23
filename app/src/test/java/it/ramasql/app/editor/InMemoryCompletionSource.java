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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadati in memoria: la {@code biblioteca} del corso più un secondo catalogo. */
final class InMemoryCompletionSource implements CompletionSource {

    private final String current;
    private final Map<String, Map<String, List<String>>> catalogs = new LinkedHashMap<>();

    InMemoryCompletionSource(String current) {
        this.current = current;
    }

    InMemoryCompletionSource table(String catalog, String table, String... columns) {
        catalogs.computeIfAbsent(catalog, c -> new LinkedHashMap<>()).put(table, List.of(columns));
        return this;
    }

    /** La {@code biblioteca} (catalogo in uso) e {@code scuola}. */
    static InMemoryCompletionSource biblioteca() {
        return new InMemoryCompletionSource("biblioteca")
                .table("biblioteca", "autori", "id", "nome", "cognome")
                .table("biblioteca", "editori", "id", "nome", "citta")
                .table("biblioteca", "libri", "id", "titolo", "anno", "prezzo", "id_editore")
                .table("biblioteca", "libri_autori", "id_libro", "id_autore")
                .table("biblioteca", "prestiti", "id", "id_libro", "id_socio", "data_prestito", "data_reso")
                .table("biblioteca", "soci", "id", "tessera", "nome", "email")
                .table("scuola", "studenti", "matricola", "nome", "classe")
                .table("scuola", "classi", "sigla", "aula");
    }

    @Override
    public String currentCatalog() {
        return current;
    }

    @Override
    public List<String> catalogs() {
        return new ArrayList<>(catalogs.keySet());
    }

    @Override
    public List<String> tables(String catalog) {
        Map<String, List<String>> t = catalogs.get(catalog);
        return t == null ? List.of() : new ArrayList<>(t.keySet());
    }

    @Override
    public List<String> columns(String catalog, String table) {
        Map<String, List<String>> t = catalogs.get(catalog);
        return t == null ? List.of() : t.getOrDefault(table, List.of());
    }
}
