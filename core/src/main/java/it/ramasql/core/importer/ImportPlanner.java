/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.importer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import it.ramasql.core.exec.BatchListener;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/**
 * Le proposte della procedura guidata: <b>abbinamento automatico per nome</b> fra colonne del file e della tabella
 * (passo 3, tabella esistente) e <b>nuova tabella</b> con i tipi dedotti (passo 3, tabella nuova).
 */
public final class ImportPlanner {

    /** Nome della chiave primaria aggiunta a una tabella nuova che non ne ha una (o {@code id_riga} se «id» è preso). */
    public static final String ADDED_KEY = "id";

    private ImportPlanner() {
    }

    /**
     * Nome ridotto per l'abbinamento: minuscole, senza accenti, senza spazi né simboli ({@code "Città"} →
     * {@code "citta"}, {@code "Data di nascita"} → {@code "datadinascita"}).
     */
    public static String normalize(String name) {
        String n = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Per ogni colonna del file, la colonna della tabella con lo stesso nome (ridotto con {@link #normalize}); nessuna
     * colonna della tabella si usa due volte.
     *
     * @return indice della colonna del file → colonna della tabella (solo quelle abbinate)
     */
    public static Map<Integer, ColumnDef> autoMapping(List<String> fileColumns, List<ColumnDef> tableColumns) {
        Map<Integer, ColumnDef> out = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < fileColumns.size(); i++) {
            String key = normalize(fileColumns.get(i));
            for (ColumnDef c : tableColumns) {
                if (!c.generated() && normalize(c.name()).equals(key) && used.add(c.name().toLowerCase(Locale.ROOT))) {
                    out.put(i, c);
                    break;
                }
            }
        }
        return out;
    }

    /** Le righe del piano per un abbinamento dato (tabella esistente o nuova). */
    public static List<ImportPlan.Mapping> mappings(FileAnalyzer.Analysis analysis, Map<Integer, ColumnDef> chosen) {
        List<ImportPlan.Mapping> out = new ArrayList<>();
        chosen.forEach((index, column) -> {
            TypeInference.Inferred inf = analysis.inferred().get(index);
            out.add(new ImportPlan.Mapping(index, analysis.columns().get(index), column, inf.decimals(), inf.dates()));
        });
        return out;
    }

    /**
     * La nuova tabella proposta: una colonna per colonna del file, con il tipo dedotto. La chiave primaria: la colonna
     * {@code id} del file se ha interi tutti diversi (diventa {@code AUTO_INCREMENT}, così i prossimi inserimenti
     * nel data-entry la riempiono da sé); altrimenti, se {@code addKey}, una colonna nuova {@code id}
     * {@code INT UNSIGNED AUTO_INCREMENT} in testa — senza chiave primaria la tabella non si potrebbe modificare nel
     * data-entry.
     */
    public static TableDef newTable(String catalog, String name, FileAnalyzer.Analysis analysis, boolean addKey) {
        List<ColumnDef> columns = new ArrayList<>();
        String pk = null;
        for (int i = 0; i < analysis.columns().size(); i++) {
            ColumnDef c = analysis.inferred().get(i).column();
            if (pk == null && analysis.profiles().get(i).uniqueIntegers()) {
                c = c.withNullable(false).withAutoIncrement(true);
                pk = c.name();
            }
            columns.add(c);
        }
        if (pk == null && addKey) {
            boolean taken = columns.stream().anyMatch(c -> c.name().equalsIgnoreCase(ADDED_KEY));
            String key = taken ? ADDED_KEY + "_riga" : ADDED_KEY;
            columns.add(0, ColumnDef.of(key, "INT").withUnsigned(true).withNullable(false).withAutoIncrement(true));
            pk = key;
        }
        // InnoDB esplicito: l'importazione conta su istruzioni che, se falliscono, non lasciano righe (ADR-025)
        TableDef t = TableDef.of(catalog, name).withEngine("InnoDB").withColumns(columns);
        if (pk != null) {
            t = t.withIndexes(List.of(IndexDef.primary(pk)));
        }
        return t;
    }

    /** Abbinamento di tutte le colonne del file alle colonne omonime della nuova tabella. */
    public static Map<Integer, ColumnDef> mappingForNewTable(FileAnalyzer.Analysis analysis, TableDef table) {
        Map<Integer, ColumnDef> out = new LinkedHashMap<>();
        for (int i = 0; i < analysis.columns().size(); i++) {
            Optional<ColumnDef> c = table.column(analysis.columns().get(i));
            if (c.isPresent()) {
                out.put(i, c.get());
            }
        }
        return out;
    }

    /**
     * Esegue il piano senza anteprima: solo per i test d'integrazione e per chi ha già mostrato lo script (la
     * procedura guidata passa dalla pipeline, che mostra {@link ImportPlan#script()} e poi esegue questo stesso
     * inserimento).
     */
    public static CompletableFuture<ImportReport> run(SqlExecutor executor, ImportPlan plan, BatchListener listener) {
        ImportRows rows = plan.rows();
        return executor.submitBatchInsert(plan.script(), plan.insert(), rows, listener).handle((result, error) -> {
            try {
                rows.close();
            } catch (java.io.IOException ignored) {
                // il file era già chiuso o illeggibile: l'esito lo dice
            }
            if (error != null) {
                throw error instanceof RuntimeException re ? re : new java.util.concurrent.CompletionException(error);
            }
            return ImportReport.of(plan, rows, result);
        });
    }
}
