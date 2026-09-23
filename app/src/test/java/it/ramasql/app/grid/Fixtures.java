/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/** Tabelle d'esempio (dalla biblioteca del corso) e righe in memoria: niente database. */
final class Fixtures {

    private Fixtures() {
    }

    static ColumnDef col(String name, String type, String args, boolean nullable, boolean autoIncrement,
            ColumnDefault def, int position) {
        return new ColumnDef(name, type, args, false, nullable, def, autoIncrement, "", null, null, position, null,
                false);
    }

    /**
     * soci: id AI PK · tessera VARCHAR(10) NOT NULL · nome VARCHAR(50) NOT NULL · email NULL · nato_il DATE NULL ·
     * quota DECIMAL(6,2) NULL · punti INT NOT NULL DEFAULT 0.
     */
    static TableDef soci() {
        List<ColumnDef> c = List.of(
                col("id", "INT", null, false, true, null, 1),
                col("tessera", "VARCHAR", "10", false, false, null, 2),
                col("nome", "VARCHAR", "50", false, false, null, 3),
                col("email", "VARCHAR", "100", true, false, null, 4),
                col("nato_il", "DATE", null, true, false, null, 5),
                col("quota", "DECIMAL", "6,2", true, false, null, 6),
                col("punti", "INT", null, false, false, ColumnDefault.literal("0"), 7));
        return new TableDef("biblioteca", "soci", "InnoDB", null, null, "", null, c, List.of(IndexDef.primary("id"),
                IndexDef.unique("uq_tessera", "tessera")), List.of(), List.of());
    }

    /** 12 soci; alcuni valori difficili: tabulazione, a-capo, virgolette, NULL, stringa vuota, accenti ed emoji. */
    static List<List<String>> sociRows() {
        List<List<String>> rows = new ArrayList<>();
        String[] nomi = {"Anna Rossi", "Bruno Bianchi", "Carla Verdi", "Dario Neri", "Elena Galli", "Fabio Conti",
            "Giulia Testa", "Luca Moro", "Marta Riva", "Nicola Fonti", "Olga Sala", "Paolo Bassi"};
        for (int i = 0; i < nomi.length; i++) {
            rows.add(new ArrayList<>(Arrays.asList(String.valueOf(i + 1), "T" + (100 + i + 1), nomi[i],
                    nomi[i].toLowerCase(Locale.ROOT).replace(' ', '.') + "@scuola.it",
                    "2008-0" + (1 + i % 9) + "-1" + (i % 9), (10 + i) + ".50", String.valueOf(i * 10))));
        }
        rows.get(2).set(2, "Carla\tVerdi");            // tabulazione in cella
        rows.get(3).set(3, "riga uno\nriga due");      // a-capo in cella
        rows.get(4).set(2, "Elena \"Leni\" Galli");    // virgolette
        rows.get(4).set(3, null);                      // NULL
        rows.get(5).set(3, "");                        // stringa vuota
        rows.get(3).set(4, null);
        rows.get(6).set(2, "Giulia Testà 😀");          // accento ed emoji
        return rows;
    }

    /** autori: id AI PK · nome NOT NULL · cognome NOT NULL · nazione NULL. */
    static TableDef autori() {
        List<ColumnDef> c = List.of(
                col("id", "INT", null, false, true, null, 1),
                col("nome", "VARCHAR", "50", false, false, null, 2),
                col("cognome", "VARCHAR", "50", false, false, null, 3),
                col("nazione", "VARCHAR", "40", true, false, null, 4));
        return new TableDef("biblioteca", "autori", "InnoDB", null, null, "", null, c,
                List.of(IndexDef.primary("id")), List.of(), List.of());
    }

    static List<List<String>> autoriRows() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of("1", "Italo", "Calvino", "Italia")));
        rows.add(new ArrayList<>(List.of("2", "Elsa", "Morante", "Italia")));
        rows.add(new ArrayList<>(List.of("3", "Primo", "Levi", "Italia")));
        return rows;
    }

    /** Una tabella senza PK né UNIQUE: sola lettura. */
    static TableDef registro() {
        List<ColumnDef> c = List.of(
                col("quando", "DATETIME", null, true, false, null, 1),
                col("evento", "VARCHAR", "100", true, false, null, 2));
        return new TableDef("biblioteca", "registro", "InnoDB", null, null, "", null, c, List.of(), List.of(),
                List.of());
    }

    static List<List<String>> registroRows() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of("2026-09-01 08:00:00", "apertura")));
        rows.add(new ArrayList<>(List.of("2026-09-01 13:00:00", "chiusura")));
        return rows;
    }

    static DataGrid grid(TableDef table, List<List<String>> rows, int pageSize, FakeGridPrompts prompts) {
        return GridTestSupport.fromEdt(() -> DataGrid.forTable(table,
                new InMemoryGridDataSource(table.columns(), rows), pageSize, prompts));
    }
}
