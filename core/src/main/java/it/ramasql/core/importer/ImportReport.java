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
import java.util.Comparator;
import java.util.List;

import it.ramasql.core.exec.BatchResult;

/**
 * Il rapporto finale di un'importazione ({@code DESIGN.md} §3.9, passo 5): righe lette, inserite, scartate con riga e
 * motivo, duplicati ignorati, e se l'importazione si è fermata prima della fine (interrotta, errore, file illeggibile).
 *
 * @param fileName          nome del file
 * @param table             tabella di destinazione
 * @param read              righe lette dal file (non vuote)
 * @param inserted          righe inserite sul server
 * @param duplicatesIgnored righe saltate perché già presenti (opzione «ignora duplicati»)
 * @param rejectedCount     righe scartate in tutto (dal client e dal server)
 * @param rejected          righe scartate, in ordine di riga (al più {@link BatchResult#MAX_DETAILS} per origine)
 * @param skippedEmpty      righe vuote saltate
 * @param batches           lotti mandati
 * @param result            esito dell'esecutore
 */
public record ImportReport(String fileName, String table, long read, long inserted, long duplicatesIgnored,
        long rejectedCount, List<Rejection> rejected, long skippedEmpty, long batches, BatchResult result) {

    /**
     * Una riga non importata.
     *
     * @param line       riga del file
     * @param reason     motivo in italiano (per il client) o messaggio del server
     * @param serverCode codice d'errore del server, {@code 0} se l'ha scartata il client
     */
    public record Rejection(long line, String reason, int serverCode) {
    }

    public ImportReport {
        rejected = List.copyOf(rejected);
    }

    /** Unisce ciò che hanno scartato il client ({@link ImportRows}) e il server ({@link BatchResult}). */
    public static ImportReport of(ImportPlan plan, ImportRows rows, BatchResult result) {
        List<Rejection> all = new ArrayList<>();
        for (ImportRows.Skipped s : rows.rejected()) {
            all.add(new Rejection(s.line(), s.reason(), 0));
        }
        for (BatchResult.RowError e : result.rejected()) {
            all.add(new Rejection(e.line(), e.message(), e.code()));
        }
        all.sort(Comparator.comparingLong(Rejection::line));
        return new ImportReport(plan.file().fileName(), plan.table().name(), rows.read(), result.inserted(),
                result.duplicatesIgnored(), rows.rejectedCount() + result.rejectedCount(), all,
                rows.skippedEmptyRows(), result.batches(), result);
    }

    /** L'importazione è arrivata alla fine del file. */
    public boolean completed() {
        return result.completed();
    }

    public boolean interrupted() {
        return result.interrupted();
    }
}
