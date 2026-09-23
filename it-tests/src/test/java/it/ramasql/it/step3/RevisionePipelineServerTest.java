/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.ExecutionListener;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.exec.StatementResult;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * Difetti della pipeline trovati dalla revisione dello Step 3, provati contro i due server: involucri che aggirano la
 * conferma rafforzata, istruzioni non riuscite ma applicate in parte (cache dei metadati ed esportazione), un
 * {@code KILL QUERY} che non deve mai colpire lo script successivo né aspettare una lettura di metadati lunga.
 */
@Tag("step3")
@Tag("it")
class RevisionePipelineServerTest {

    private static String editor() {
        return SqlOrigin.EDITOR.label();
    }

    private static String id(ItServers server) {
        return server.name().toLowerCase(Locale.ROOT);
    }

    /**
     * MariaDB {@code SET STATEMENT … FOR DELETE} e {@code ANALYZE DELETE} cancellano davvero: sono DESTRUCTIVE e chiedono
     * di riscrivere il nome della tabella. MySQL non conosce {@code SET STATEMENT} (errore di sintassi, nulla cancellato)
     * e su {@code EXPLAIN ANALYZE DELETE} (8.0.39) risponde col piano senza cancellare: la classificazione è la stessa
     * sui due server, prudente.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void involucriCheCancellanoSonoDistruttivi(ItServers server) throws Exception {
        StringBuilder ev = new StringBuilder("Involucri che eseguono un'altra istruzione, su " + server.label() + "\n");
        try (TestCatalog cat = TestCatalog.create(server, "t3_involucri"); Session session = Step3Support.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            cat.execute("CREATE TABLE t (id INT PRIMARY KEY, x INT)", "INSERT INTO t VALUES (1, 1), (2, 2), (3, 3)");
            // istruzione → righe che restano dopo (-1 = errore di sintassi 1064), come si comporta davvero il server
            java.util.Map<String, Integer> cases = new java.util.LinkedHashMap<>();
            if (server.isMariaDb()) {
                cases.put("SET STATEMENT max_statement_time=1 FOR DELETE FROM t", 0);
                cases.put("ANALYZE DELETE FROM t", 0);
                cases.put("ANALYZE FORMAT=JSON UPDATE t SET x = 0", 3);
            } else {
                cases.put("SET STATEMENT max_statement_time=1 FOR DELETE FROM t", -1);
                // MySQL 8.0.39: EXPLAIN ANALYZE di una DELETE (anche multi-tabella) risponde con il piano senza
                // cancellare; la documentazione però dice che le istruzioni «analizzate» si eseguono, e le versioni
                // cambiano: la classificazione resta prudente (DESTRUCTIVE), come su MariaDB dove ANALYZE esegue
                cases.put("EXPLAIN ANALYZE DELETE t FROM t JOIN t AS u ON u.id = t.id", 3);
                cases.put("EXPLAIN ANALYZE DELETE FROM t", 3);
            }
            for (java.util.Map.Entry<String, Integer> k : cases.entrySet()) {
                String sql = k.getKey();
                cat.execute("DELETE FROM t", "INSERT INTO t VALUES (1, 1), (2, 2), (3, 3)");
                SqlScript script = SqlScript.of("t", editor(), sql);
                assertEquals(RiskLevel.DESTRUCTIVE, script.risk(), sql);
                ConfirmationPolicy.Confirmation c = ConfirmationPolicy.evaluate(script);
                assertEquals(ConfirmationPolicy.Level.STRONG, c.level(), sql);
                assertEquals("t", c.typeToConfirm(), sql);
                ScriptResult r = exec.run(script);
                String rows = Step3Support.scalar(cat.connection(), "SELECT COUNT(*) FROM t");
                ev.append(sql).append(" → rischio ").append(script.risk()).append(", da riscrivere «")
                        .append(c.typeToConfirm()).append("»; esito ")
                        .append(r.completed() ? "OK" : "ERRORE " + r.failure().orElseThrow().error().code())
                        .append("; righe rimaste: ").append(rows).append(" su 3");
                String checkX = Step3Support.scalar(cat.connection(), "SELECT COALESCE(SUM(x), 0) FROM t");
                ev.append(", somma di x: ").append(checkX).append('\n');
                if (k.getValue() < 0) {
                    assertFalse(r.completed(), "MySQL non conosce SET STATEMENT");
                    assertEquals(1064, r.failure().orElseThrow().error().code());
                    assertEquals("3", rows);
                } else {
                    assertTrue(r.completed(), () -> sql + " → " + r.failure());
                    assertEquals(String.valueOf(k.getValue()), rows, sql);
                }
                if (sql.contains("UPDATE")) {
                    assertEquals("0", checkX, sql + " modifica davvero le righe");
                }
            }
        } finally {
            TestResults.write("step3", "involucri-" + id(server) + ".txt", ev.toString());
        }
    }

    /**
     * {@code DROP TABLE a, b} con {@code b} inesistente: MariaDB elimina {@code a} e poi dà errore 1051; MySQL (DDL
     * atomico) non elimina nulla. In entrambi i casi la cache dei metadati si invalida, e il registro esportato
     * commenta l'istruzione con l'avviso «può essere stata applicata in parte: verifica».
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void dropDiPiuTabelleFallitoMaApplicatoInParte(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_parziale"); Session session = Step3Support.open(server,
                cat.name())) {
            cat.execute("CREATE TABLE a (id INT)", "CREATE TABLE c (id INT)");
            MetadataReader reader = MetadataReader.of(session);
            List<String> events = new ArrayList<>();
            reader.addListener((catalog, table) -> events.add(catalog + "/" + table));
            assertEquals(List.of("a", "c"), reader.tables(cat.name()).stream().map(TableSummary::name).toList());
            try (SqlExecutor exec = new SqlExecutor(session, new SqlLog(), reader)) {
                ScriptResult r = exec.run(SqlScript.of("t", editor(), "DROP TABLE a, b_manca"));
                assertFalse(r.completed());
                assertEquals(1051, r.failure().orElseThrow().error().code(), "tabella sconosciuta");
                boolean aExists = "1".equals(Step3Support.scalar(cat.connection(),
                        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + cat.name()
                        + "' AND TABLE_NAME = 'a'"));
                assertEquals(!server.isMariaDb(), aExists, server.isMariaDb()
                        ? "MariaDB elimina le tabelle che esistono, poi segnala l'errore"
                        : "MySQL: DDL atomico, nessuna tabella eliminata");
                // la cache è stata invalidata anche se l'istruzione è «non riuscita»
                assertFalse(reader.isCached(cat.name()), "cache del catalogo invalidata dopo il DDL non riuscito");
                assertEquals(List.of(cat.name() + "/null"), events);
                assertEquals(aExists ? List.of("a", "c") : List.of("c"),
                        reader.tables(cat.name()).stream().map(TableSummary::name).toList(),
                        "il navigatore rilegge lo stato reale");
                String exported = exec.log().exportScript();
                assertTrue(exported.contains("-- DROP TABLE a, b_manca;\n-- "
                        + CoreMessages.get("log.export.partial") + "\n"), exported);
                TestResults.write("step3", "fallimento-parziale-" + id(server) + ".txt",
                        "DROP TABLE a, b_manca su " + server.label() + ": errore "
                        + r.failure().orElseThrow().error().code() + "; tabella a "
                        + (aExists ? "ANCORA PRESENTE (DDL atomico)" : "ELIMINATA (applicata in parte)")
                        + "\ncache dei metadati invalidata: " + events + "; elenco riletto: "
                        + reader.tables(cat.name()).stream().map(TableSummary::name).toList()
                        + "\nnel registro esportato:\n" + exported.substring(exported.indexOf("-- DROP TABLE a"))
                        .replace(cat.name(), "<catalogo>"));
            }
        }
    }

    /** Un INSERT di più righe su MyISAM che si ferma su un duplicato lascia le righe precedenti: «in parte» è reale. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void insertMyIsamFallitoLasciaLeRighePrecedenti(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_myisam"); Session session = Step3Support.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            cat.execute("CREATE TABLE m (id INT PRIMARY KEY) ENGINE=MyISAM");
            ScriptResult r = exec.run(SqlScript.of("t", editor(), "INSERT INTO m VALUES (1), (2), (2), (3)"));
            assertEquals(1062, r.failure().orElseThrow().error().code());
            assertEquals("2", Step3Support.scalar(cat.connection(), "SELECT COUNT(*) FROM m"),
                    "le due righe prima del duplicato restano: l'istruzione «non riuscita» ha avuto effetto");
            assertTrue(exec.log().exportScript().contains("-- INSERT INTO m VALUES (1), (2), (2), (3);\n-- "
                    + CoreMessages.get("log.export.partial")));
        }
    }

    /**
     * Il {@code KILL QUERY} non aspetta una lettura di metadati lunga: ha una connessione tutta sua. Qui la connessione
     * di servizio (quella dei metadati) è occupata da una lettura artificialmente lenta ({@code SELECT SLEEP(6)});
     * intanto {@code SELECT SLEEP(30)} dell'utente si interrompe in meno di 2 secondi.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void killQueryNonAspettaLaLetturaDeiMetadati(ItServers server) throws Exception {
        try (Session session = Step3Support.open(server, "");
                SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            CountDownLatch slowStarted = new CountDownLatch(1);
            CompletableFuture<Void> slowMetadata = CompletableFuture.runAsync(() -> {
                try (Statement st = session.serviceConnection().createStatement()) {
                    slowStarted.countDown();
                    st.executeQuery("SELECT SLEEP(6) FROM information_schema.SCHEMATA LIMIT 1").close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<ScriptResult> user = exec.submit(SqlScript.of("lenta", editor(), "SELECT SLEEP(30)"),
                    new ExecutionListener() {
                        @Override
                        public void statementStarted(SqlScript s, int index, SqlStatement statement) {
                            started.countDown();
                        }
                    });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.sleep(400);
            assertFalse(slowMetadata.isDone(), "la lettura dei metadati è ancora in corso");
            long t0 = System.nanoTime();
            assertTrue(exec.interrupt());
            ScriptResult r = user.get(10, TimeUnit.SECONDS);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(ms < 2000, "interrotta in " + ms + " ms mentre i metadati erano occupati");
            assertFalse(slowMetadata.isDone(), "il KILL non ha aspettato la lettura dei metadati");
            assertEquals(StatementResult.Status.INTERRUPTED, r.failure().orElseThrow().status());
            slowMetadata.get(15, TimeUnit.SECONDS);
            // tre connessioni distinte: principale, servizio, KILL
            String main = Step3Support.scalar(session.mainConnection(), "SELECT CONNECTION_ID()");
            String service = Step3Support.scalar(session.serviceConnection(), "SELECT CONNECTION_ID()");
            assertNotEquals(main, service);
            TestResults.write("step3", "kill-query-metadati-" + id(server) + ".txt",
                    "Su " + server.label() + ": connessione di servizio occupata da SELECT SLEEP(6) (lettura di metadati"
                    + " lenta); SELECT SLEEP(30) dell'utente interrotta con KILL QUERY in " + ms + " ms, dalla"
                    + " connessione dedicata al KILL, senza aspettare i metadati; esito " + r.failure().orElseThrow()
                    .status() + "\n");
        }
    }

    /**
     * Un {@code KILL QUERY} non colpisce mai lo script successivo: né quando arriva a connessione ferma, né quando
     * l'interruzione cade proprio mentre uno script finisce e il successivo (già in coda) sta per cominciare.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void ilKillNonColpisceMaiLoScriptSuccessivo(ItServers server) throws Exception {
        try (Session session = Step3Support.open(server, "");
                SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            // KILL QUERY a connessione principale ferma: l'istruzione dopo non ne risente
            session.interruptRunningStatement();
            ScriptResult afterIdleKill = exec.run(SqlScript.of("dopo", editor(), "SELECT 'libero'"));
            assertTrue(afterIdleKill.completed(), () -> afterIdleKill.failure().toString());
            assertEquals("libero", String.valueOf(afterIdleKill.results().get(0).firstResult().orElseThrow()
                    .value(0, 0)));

            // l'interruzione chiesta mentre lo script A sta finendo vale solo per A
            int hitsOnB = 0;
            int interruptedA = 0;
            for (int i = 0; i < 25; i++) {
                SqlScript a = SqlScript.of("A", editor(), "SELECT SLEEP(0.05)", "SELECT 'a'");
                SqlScript b = SqlScript.of("B", editor(), "SELECT SLEEP(0.2)", "SELECT 'b'");
                CompletableFuture<ScriptResult> fa = exec.submit(a, new ExecutionListener() {
                    @Override
                    public void statementFinished(SqlScript s, StatementResult result) {
                        if (result.index() == 1) {
                            try {
                                exec.interrupt();   // A ha finito l'ultima istruzione: B è in coda
                            } catch (java.sql.SQLException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    }
                });
                CompletableFuture<ScriptResult> fb = exec.submit(b, null);
                ScriptResult ra = fa.get(10, TimeUnit.SECONDS);
                ScriptResult rb = fb.get(10, TimeUnit.SECONDS);
                if (!rb.completed()) {
                    hitsOnB++;
                }
                if (ra.results().size() == 2 && ra.results().stream().allMatch(StatementResult::isOk)) {
                    assertTrue(ra.completed());
                } else {
                    interruptedA++;
                }
                assertEquals("b", String.valueOf(rb.results().get(1).firstResult().orElseThrow().value(0, 0)));
            }
            assertEquals(0, hitsOnB, "lo script successivo non è mai stato colpito");
            assertEquals(0, interruptedA, "A aveva già eseguito tutto: resta completato");
            // un'interruzione vera durante B risulta INTERRUPTED
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<ScriptResult> slow = exec.submit(SqlScript.of("C", editor(), "SELECT SLEEP(30)"),
                    new ExecutionListener() {
                        @Override
                        public void statementStarted(SqlScript s, int index, SqlStatement statement) {
                            started.countDown();
                        }
                    });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.sleep(300);
            assertTrue(exec.interrupt());
            ScriptResult rc = slow.get(10, TimeUnit.SECONDS);
            assertTrue(rc.interrupted());
            assertEquals(StatementResult.Status.INTERRUPTED, rc.failure().orElseThrow().status());
            TestResults.write("step3", "kill-query-successivo-" + id(server) + ".txt",
                    "Su " + server.label() + ": KILL QUERY a connessione ferma → l'istruzione dopo riesce;\n"
                    + "25 volte: interruzione chiesta alla fine di A con B in coda → B mai colpito (" + hitsOnB
                    + " volte), A completato; poi SELECT SLEEP(30) interrotta → INTERRUPTED\n");
        }
    }

    /**
     * Regola ferrea: rieseguire il registro «senza catalogo» non crea, non modifica e non elimina mai il catalogo
     * d'origine. Nel registro ci sono {@code CREATE DATABASE}, {@code ALTER DATABASE}, {@code USE}, un nome qualificato
     * con spazi e, alla fine, {@code DROP DATABASE} dell'origine: rieseguito su un altro catalogo, l'origine (ricreata
     * identica per il confronto) resta com'era.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void senzaCatalogoLOrigineNonSiToccaMai(ItServers server) throws Exception {
        String origin = TestCatalog.requireTestName("ramasql_test_t38o_" + Long.toString(System.nanoTime(), 36));
        try (TestCatalog target = TestCatalog.create(server, "t38_destinazione")) {
            try (Session s = Step3Support.open(server, ""); SqlExecutor exec = new SqlExecutor(s, new SqlLog(), null)) {
                String q = "`" + origin + "`";
                ScriptResult r = exec.run(SqlScript.fromText("lavoro", editor(), String.join(";\n",
                        "CREATE DATABASE " + q + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                        "USE " + q,
                        "CREATE TABLE " + q + ".`t` (id INT PRIMARY KEY, nota VARCHAR(20))",
                        "INSERT INTO " + origin + " . t VALUES (1, 'spazi')",
                        "INSERT INTO t VALUES (2, 'corrente')",
                        "ALTER DATABASE " + q + " COLLATE utf8mb4_bin",
                        "CREATE TABLE u (x INT)",
                        "DROP DATABASE " + q)));
                assertTrue(r.completed(), () -> r.failure().toString());
                String exported = exec.log().exportScript(SqlLog.ExportOptions.withoutCatalog(origin));
                TestResults.write("step3", "T3.8-senza-catalogo-" + id(server) + ".sql",
                        exported.replace(origin, "<origine>"));

                // l'origine si ricrea identica a com'era prima del DROP, per vedere se la riesecuzione la tocca
                try (Connection c = server.connect(); Statement st = c.createStatement()) {
                    st.execute("CREATE DATABASE " + q + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
                    st.execute("CREATE TABLE " + q + ".`t` (id INT PRIMARY KEY, nota VARCHAR(20))");
                    st.execute("INSERT INTO " + q + ".`t` VALUES (1, 'spazi'), (2, 'corrente')");
                    st.execute("CREATE TABLE " + q + ".`u` (x INT)");
                }
                String before = snapshot(server, origin);

                try (Session sb = Step3Support.open(server, target.name());
                        SqlExecutor execB = new SqlExecutor(sb, new SqlLog(), null)) {
                    ScriptResult rerun = execB.run(SqlScript.fromText("riesecuzione", editor(), exported));
                    assertTrue(rerun.completed(), () -> rerun.failure().toString());
                    for (StatementResult sr : rerun.results()) {
                        String code = sr.statement().text().lines().filter(l -> !l.strip().startsWith("--"))
                                .reduce("", (x, y) -> x + y + "\n");
                        assertFalse(code.contains(origin), "rieseguita un'istruzione che nomina l'origine: " + code);
                    }
                }
                assertEquals(before, snapshot(server, origin), "il catalogo d'origine non è stato toccato");
                assertEquals("1", Step3Support.scalar(target.connection(), "SELECT COUNT(*) FROM t"),
                        "sul catalogo corrente: solo l'INSERT senza qualificatore; quello con «origine . t» è commentato");
                assertEquals("0", Step3Support.scalar(target.connection(), "SELECT COUNT(*) FROM u"));
                assertTrue(exported.contains(CoreMessages.get("log.export.catalogStatement", origin)), exported);
                assertTrue(exported.contains(CoreMessages.get("log.export.residualReference", origin)), exported);
                TestResults.write("step3", "T3.8-senza-catalogo-" + id(server) + ".txt",
                        "Su " + server.label() + ": registro con CREATE/ALTER/DROP DATABASE dell'origine, USE, nome"
                        + " qualificato con spazi → esportato «senza catalogo» e rieseguito su un altro catalogo:\n"
                        + "origine prima  = " + before.replace(origin, "<origine>") + "\n"
                        + "origine dopo   = " + snapshot(server, origin).replace(origin, "<origine>") + "\n"
                        + "esito: ORIGINE MAI TOCCATA (catalogo, collation, tabelle, righe identici)\n");
            }
        } finally {
            Step3Support.dropTestCatalog(server, origin);
        }
    }

    /** Collation, tabelle e righe di un catalogo, in una riga confrontabile. */
    private static String snapshot(ItServers server, String catalog) throws Exception {
        StringBuilder out = new StringBuilder();
        try (Connection c = server.connect(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA"
                    + " WHERE SCHEMA_NAME = '" + catalog + "'")) {
                out.append(rs.next() ? rs.getString(1) : "ASSENTE");
            }
            try (ResultSet rs = st.executeQuery("SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES"
                    + " WHERE TABLE_SCHEMA = '" + catalog + "' ORDER BY TABLE_NAME")) {
                while (rs.next()) {
                    out.append(' ').append(rs.getString(1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT GROUP_CONCAT(id, ':', nota ORDER BY id) FROM `" + catalog
                    + "`.`t`")) {
                out.append(" t=").append(rs.next() ? rs.getString(1) : "");
            } catch (java.sql.SQLException e) {
                out.append(" t=ASSENTE");
            }
        }
        return out.toString();
    }
}
