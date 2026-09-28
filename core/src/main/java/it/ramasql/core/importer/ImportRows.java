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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.exec.BatchSource;
import it.ramasql.core.importer.ValueParsing.DateOrder;

/**
 * Le righe di un {@link ImportPlan} pronte per l'esecutore: il file letto in streaming, ogni valore convertito con
 * {@link ValueConverter}. Le righe che non si possono importare (valore non valido, valori in più delle colonne) non
 * arrivano al server: restano qui, con la riga del file e il motivo in italiano, per il rapporto finale.
 */
public final class ImportRows implements BatchSource, AutoCloseable {

    /**
     * Una riga scartata prima di arrivare al server.
     *
     * @param line   riga del file
     * @param reason perché, in italiano
     */
    public record Skipped(long line, String reason) {
    }

    /** Oltre questa dimensione stimata dei valori il lotto si chiude prima delle sue righe (limite del pacchetto). */
    static final long MAX_BATCH_BYTES = 4L * 1024 * 1024;

    private final ImportPlan plan;
    private ImportSource source;
    private long read;
    private long rejectedCount;
    private final List<Skipped> rejected = new ArrayList<>();
    private boolean finished;

    ImportRows(ImportPlan plan) {
        this.plan = plan;
    }

    @Override
    public List<Row> nextBatch(int maxRows) throws IOException, ImportFileException {
        if (finished) {
            return List.of();
        }
        if (source == null) {
            source = plan.file().open();
        }
        List<Row> batch = new ArrayList<>(Math.min(maxRows, 1024));
        long bytes = 0;
        while (batch.size() < maxRows && bytes < MAX_BATCH_BYTES) {
            SourceRow r = source.next();
            if (r == null) {
                finished = true;
                close();
                break;
            }
            read++;
            Row converted = convert(r);
            if (converted != null) {
                batch.add(converted);
                bytes += size(converted);
            }
        }
        return batch;
    }

    /** Dimensione stimata della riga nell'istruzione (UTF-8 al peggio 3 byte per carattere, più apici e virgole). */
    private static long size(Row r) {
        long n = 0;
        for (Object v : r.params()) {
            n += v == null ? 5 : v.toString().length() * 3L + 4;
        }
        return n;
    }

    private Row convert(SourceRow r) {
        if (r.extraValues() > 0) {
            skip(r.line(), CoreMessages.get("import.reject.extraValues", r.values().size() + r.extraValues(),
                    r.values().size()));
            return null;
        }
        List<ImportPlan.Mapping> mappings = plan.mappings();
        Object[] params = new Object[mappings.size()];
        ImportPlan.Options options = plan.options();
        for (int i = 0; i < mappings.size(); i++) {
            ImportPlan.Mapping m = mappings.get(i);
            Object raw = m.sourceIndex() < r.values().size() ? r.values().get(m.sourceIndex()) : null;
            DateOrder order = options.dateOrder() != null ? options.dateOrder() : m.dates();
            try {
                params[i] = ValueConverter.convert(raw, m.target(), m.decimals(), order, options.emptyIsNull());
            } catch (ValueConverter.Rejected e) {
                boolean missing = raw == null && m.sourceIndex() >= r.values().size() - r.missingValues();
                skip(r.line(), missing ? CoreMessages.get("import.reject.shortRow", r.values().size() - r.missingValues(),
                        r.values().size(), m.target().name()) : e.getMessage());
                return null;
            }
        }
        return new Row(r.line(), params);
    }

    private void skip(long line, String reason) {
        rejectedCount++;
        if (rejected.size() < it.ramasql.core.exec.BatchResult.MAX_DETAILS) {
            rejected.add(new Skipped(line, reason));
        }
    }

    /** Righe lette dal file finora (non vuote). */
    public long read() {
        return read;
    }

    /** Righe scartate prima del server, in tutto. */
    public long rejectedCount() {
        return rejectedCount;
    }

    /** Righe scartate prima del server (al più {@code BatchResult.MAX_DETAILS}). */
    public List<Skipped> rejected() {
        return List.copyOf(rejected);
    }

    /** Tutto il file è stato letto. */
    public boolean finished() {
        return finished;
    }

    @Override
    public void close() throws IOException {
        if (source != null) {
            skippedAtClose = source.skippedEmptyRows();
            source.close();
        }
    }

    private long skippedAtClose;

    /** Righe vuote saltate (anche dopo la chiusura). */
    public long skippedEmptyRows() {
        return source == null ? 0 : Math.max(skippedAtClose, source.skippedEmptyRows());
    }
}
