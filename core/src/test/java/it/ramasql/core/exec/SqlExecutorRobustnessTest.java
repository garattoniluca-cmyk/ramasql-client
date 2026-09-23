/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.connection.TestSessions;

/**
 * {@link SqlExecutor} senza server: un guasto dentro il client (qui: la connessione che manca, un ascoltatore che si
 * rompe) non fa sparire l'istruzione, che risulta nel registro come ERROR; {@link SqlExecutor#close()} non lascia
 * appesi gli script in coda mai partiti.
 */
@Tag("step3")
class SqlExecutorRobustnessTest {

    @Test
    void unGuastoDelClientRegistraComunqueLIstruzioneComeErrore() {
        SqlLog log = new SqlLog();
        try (SqlExecutor exec = new SqlExecutor(TestSessions.detached(), log, null)) {
            // la sessione non ha connessione: il driver non c'è, si ha una RuntimeException dentro runOne
            ScriptResult r = exec.run(SqlScript.of("t", "Editor SQL", "DELETE FROM t WHERE id = 1", "SELECT 1"));
            assertFalse(r.completed());
            StatementResult failure = r.failure().orElseThrow();
            assertEquals(StatementResult.Status.FAILED, failure.status());
            assertEquals(0, failure.error().code());
            assertTrue(failure.error().message().startsWith(CoreMessages.get("exec.internalError", "").strip()),
                    failure.error().message());
            assertEquals(List.of(r.script().statements().get(1)), r.notExecuted());
            assertEquals(1, log.size(), "l'istruzione tentata è nel registro");
            SqlLog.Entry e = log.entries().get(0);
            assertEquals(SqlLog.Outcome.ERROR, e.outcome());
            assertEquals("DELETE FROM t WHERE id = 1", e.sql());
            assertFalse(exec.isRunning());
        }
    }

    @Test
    void unAscoltatoreCheSiRompeNonFermaIlRegistroNeLEsito() throws Exception {
        SqlLog log = new SqlLog();
        log.addListener(entry -> {
            throw new IllegalStateException("ascoltatore del registro guasto");
        });
        try (SqlExecutor exec = new SqlExecutor(TestSessions.detached(), log, null)) {
            CompletableFuture<ScriptResult> f = exec.submit(SqlScript.of("t", "Editor SQL", "SELECT 1"),
                    new ExecutionListener() {
                        @Override
                        public void statementStarted(SqlScript s, int index, SqlStatement statement) {
                            throw new IllegalStateException("ascoltatore guasto");
                        }

                        @Override
                        public void scriptFinished(ScriptResult result) {
                            throw new IllegalStateException("anche qui");
                        }
                    });
            ScriptResult r = f.get(5, TimeUnit.SECONDS);
            assertEquals(StatementResult.Status.FAILED, r.failure().orElseThrow().status());
            assertTrue(r.failure().orElseThrow().error().message().contains("ascoltatore guasto"));
            assertEquals(1, log.size());
            assertEquals(SqlLog.Outcome.ERROR, log.entries().get(0).outcome());
        }
    }

    @Test
    void closeCompletaConUnErroreGliScriptInCodaMaiPartiti() throws Exception {
        SqlLog log = new SqlLog();
        SqlExecutor exec = new SqlExecutor(TestSessions.detached(), log, null);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<ScriptResult> first = exec.submit(SqlScript.of("primo", "o", "SELECT 1"),
                new ExecutionListener() {
                    @Override
                    public void scriptStarted(SqlScript script) {
                        inside.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
        assertTrue(inside.await(5, TimeUnit.SECONDS));
        CompletableFuture<ScriptResult> second = exec.submit(SqlScript.of("secondo", "o", "SELECT 2"), null);
        CompletableFuture<ScriptResult> third = exec.submit(SqlScript.of("terzo", "o", "SELECT 3"), null);

        exec.close();

        for (CompletableFuture<ScriptResult> f : List.of(second, third)) {
            ExecutionException e = assertThrows(ExecutionException.class, () -> f.get(2, TimeUnit.SECONDS),
                    "uno script in coda non resta appeso dopo close()");
            IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals(CoreMessages.get("exec.closed"), cause.getMessage());
        }
        release.countDown();
        first.get(5, TimeUnit.SECONDS);   // quello in corso finisce da sé
        assertTrue(log.entries().stream().noneMatch(x -> x.sql().equals("SELECT 2") || x.sql().equals("SELECT 3")),
                "gli script mai partiti non sono stati eseguiti");
        // dopo la chiusura: rifiutato subito, mai un future appeso
        java.util.concurrent.RejectedExecutionException late = assertThrows(
                java.util.concurrent.RejectedExecutionException.class,
                () -> exec.submit(SqlScript.of("tardi", "o", "SELECT 4"), null));
        assertEquals(CoreMessages.get("exec.closed"), late.getMessage());
        java.util.concurrent.RejectedExecutionException direct = assertThrows(
                java.util.concurrent.RejectedExecutionException.class,
                () -> exec.run(SqlScript.of("tardi", "o", "SELECT 5")));
        assertEquals(CoreMessages.get("exec.closed"), direct.getMessage());
    }
}
