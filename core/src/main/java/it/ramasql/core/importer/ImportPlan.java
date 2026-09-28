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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.exec.BatchInsert;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.importer.ValueParsing.DateOrder;
import it.ramasql.core.importer.ValueParsing.DecimalStyle;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.ObjectDdl;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * Tutto ciò che l'utente ha deciso nella procedura guidata «Importa dati», e l'SQL che ne segue: è ciò che passa per
 * la pipeline «anteprima SQL» ({@link #script()}): eventuale {@code TRUNCATE TABLE} (opzione «svuota prima»), eventuale
 * {@code CREATE TABLE} (nuova tabella), e l'{@code INSERT} preparata eseguita a lotti ({@link #insert()}).
 *
 * @param file          il file e come leggerlo
 * @param catalog       catalogo di destinazione
 * @param table         tabella esistente di destinazione, oppure la nuova tabella da creare
 * @param newTable      la tabella non esiste ancora: si crea con {@code CREATE TABLE}
 * @param mappings      quale colonna del file va in quale colonna della tabella
 * @param options       opzioni
 * @param expectedRows  righe del file (dall'analisi), per il titolo dell'anteprima
 */
public record ImportPlan(ImportFile file, String catalog, TableDef table, boolean newTable, List<Mapping> mappings,
        Options options, long expectedRows) {

    /**
     * Una colonna del file importata in una colonna della tabella.
     *
     * @param sourceIndex posizione della colonna nel file (da 0)
     * @param sourceName  nome nel file
     * @param target      colonna della tabella
     * @param decimals    stile decimale dei valori nel file
     * @param dates       ordine delle date nel file
     */
    public record Mapping(int sourceIndex, String sourceName, ColumnDef target, DecimalStyle decimals,
            DateOrder dates) {
    }

    /**
     * Opzioni del passo 4.
     *
     * @param truncateFirst    svuota la tabella prima di importare ({@code TRUNCATE TABLE}, conferma rafforzata)
     * @param ignoreDuplicates una riga con una chiave già presente si salta invece di contarla come errore
     * @param emptyIsNull      un valore vuoto vale {@code NULL}
     * @param dateOrder        ordine delle date scelto dall'utente; {@code null} = quello dedotto per ogni colonna
     */
    public record Options(boolean truncateFirst, boolean ignoreDuplicates, boolean emptyIsNull, DateOrder dateOrder) {

        public static Options defaults() {
            return new Options(false, false, true, null);
        }
    }

    public ImportPlan {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(table, "table");
        mappings = List.copyOf(mappings);
        Objects.requireNonNull(options, "options");
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException(CoreMessages.get("import.plan.noColumns"));
        }
    }

    /** L'inserimento a lotti: InnoDB (o tabella nuova, che il server crea InnoDB) = un'istruzione per lotto. */
    public BatchInsert insert() {
        List<String> columns = mappings.stream().map(m -> m.target().name()).toList();
        boolean atomic = table.engine() == null ? newTable : table.engine().equalsIgnoreCase("InnoDB");
        return new BatchInsert(catalog, table.name(), columns, atomic, options.ignoreDuplicates(),
                SqlOrigin.IMPORT.label());
    }

    /** Lotti previsti. */
    public long expectedBatches() {
        int per = insert().rowsPerBatch();
        return (expectedRows + per - 1) / per;
    }

    /** Lo script per la pipeline: titolo con file, righe e lotti; l'ultima istruzione è l'INSERT preparata. */
    public SqlScript script() {
        String origin = SqlOrigin.IMPORT.label();
        List<SqlStatement> statements = new ArrayList<>();
        if (newTable) {
            statements.add(SqlStatement.of(TableDiff.createTable(table.withCatalog(catalog)), origin));
        } else if (options.truncateFirst()) {
            statements.add(SqlStatement.of(ObjectDdl.truncateTable(catalog, table.name()), origin));
        }
        statements.add(insert().statement());
        long batches = expectedBatches();
        String title = CoreMessages.get(batches == 1 ? "import.script.title.one" : "import.script.title", file.fileName(),
                table.name(), expectedRows, batches, insert().rowsPerBatch());
        return new SqlScript(title, origin, statements);
    }

    /** Le righe del file, convertite, a lotti ({@link ImportRows}). */
    public ImportRows rows() {
        return new ImportRows(this);
    }
}
