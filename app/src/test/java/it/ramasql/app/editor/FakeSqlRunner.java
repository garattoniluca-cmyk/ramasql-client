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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Esecutore finto: registra ciò che l'editor gli consegna e risponde come farebbe il server.
 * <ul>
 *   <li>{@link #automatic()}: risponde subito, <b>da un altro thread</b> (come l'esecutore vero), con esiti calcolati
 *       dalla funzione {@code responder} (predefinita: SELECT/SHOW/CALL → righe, il resto → righe interessate);</li>
 *   <li>{@link #manual()}: non risponde; il test pilota l'avanzamento con {@link #listener()}.</li>
 * </ul>
 * Si ferma al primo errore, come da contratto di {@link SqlRunner}.
 */
final class FakeSqlRunner implements SqlRunner {

    private final boolean automatic;
    private final List<List<PositionedStatement>> runs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> origins = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger cancelCount = new AtomicInteger();
    private volatile Listener listener;
    private BiFunction<Integer, PositionedStatement, StatementOutcome> responder = FakeSqlRunner::defaultOutcome;

    private FakeSqlRunner(boolean automatic) {
        this.automatic = automatic;
    }

    static FakeSqlRunner automatic() {
        return new FakeSqlRunner(true);
    }

    static FakeSqlRunner manual() {
        return new FakeSqlRunner(false);
    }

    FakeSqlRunner respondingWith(BiFunction<Integer, PositionedStatement, StatementOutcome> r) {
        this.responder = r;
        return this;
    }

    @Override
    public void run(List<PositionedStatement> statements, String origin, Listener l) {
        runs.add(List.copyOf(statements));
        origins.add(origin);
        listener = l;
        if (!automatic) {
            return;
        }
        Thread worker = new Thread(() -> {
            for (int i = 0; i < statements.size(); i++) {
                l.started(i);
                StatementOutcome o = responder.apply(i, statements.get(i));
                l.finished(o);
                if (o.isError()) {
                    break;
                }
            }
            l.done(false);
        }, "fake-sql-runner");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void cancel() {
        cancelCount.incrementAndGet();
    }

    /** SELECT/SHOW/CALL → 2 colonne e (i+1) righe; il resto → (i+1) righe interessate; durata (i+1) ms. */
    static StatementOutcome defaultOutcome(int index, PositionedStatement st) {
        Duration d = Duration.ofMillis(index + 1L);
        String verb = st.text().strip().split("\\s+")[0].toUpperCase(Locale.ROOT);
        if (verb.equals("SELECT") || verb.equals("SHOW") || verb.equals("CALL")) {
            List<List<String>> rows = new ArrayList<>();
            for (int r = 0; r <= index; r++) {
                rows.add(List.of(String.valueOf(r + 1), "valore " + (r + 1)));
            }
            ResultData data = new ResultData(List.of(new ResultData.Column("id", "INT"),
                    new ResultData.Column("nome", "VARCHAR")), rows, false);
            return StatementOutcome.rows(index, data, d, List.of());
        }
        return StatementOutcome.update(index, index + 1L, d, List.of());
    }

    int runCount() {
        return runs.size();
    }

    List<PositionedStatement> lastRun() {
        return runs.getLast();
    }

    List<String> lastTexts() {
        return lastRun().stream().map(PositionedStatement::text).toList();
    }

    String lastOrigin() {
        return origins.getLast();
    }

    int cancelCount() {
        return cancelCount.get();
    }

    Listener listener() {
        return listener;
    }
}
