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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.ExecutionListener;
import it.ramasql.core.exec.ResultTable;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.exec.StatementResult;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.core.metadata.ViewDef;
import it.ramasql.core.sqlgen.TreeScripts;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * La pipeline SQL contro i due server: {@link SqlExecutor} (una istruzione alla volta, arresto al primo errore,
 * mai transazioni, interruzione), {@link SqlLog} (registro ed esportazione rieseguibile, cuore di T3.8), operazioni
 * del navigatore (lato core di T3.5–T3.7) e invalidazione della cache dei metadati dopo i DDL.
 */
@Tag("step3")
@Tag("it")
class SqlExecutorServerTest {

    private static final Pattern TRANSACTION = Pattern.compile(
            "(?i)\\b(START\\s+TRANSACTION|BEGIN|COMMIT|ROLLBACK|SAVEPOINT|SET\\s+(SESSION\\s+)?autocommit)\\b");

    private static String editor() {
        return SqlOrigin.EDITOR.label();
    }

    /** Script eseguito istruzione per istruzione, con righe, durata, avvisi, risultati e registro. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void scriptConRegistroRisultatiEAvvisi(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_exec"); Session session = Step3Support.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            List<String> events = Collections.synchronizedList(new ArrayList<>());
            ExecutionListener listener = new ExecutionListener() {
                @Override
                public void scriptStarted(SqlScript script) {
                    events.add("inizio");
                }

                @Override
                public void statementStarted(SqlScript script, int index, SqlStatement statement) {
                    events.add("via " + index + " " + Thread.currentThread().getName());
                }

                @Override
                public void statementFinished(SqlScript script, StatementResult result) {
                    events.add("fine " + result.index());
                }

                @Override
                public void scriptFinished(ScriptResult result) {
                    events.add("fine script");
                }
            };
            SqlScript script = SqlScript.fromText("prova", editor(), """
                    CREATE TABLE soci_x (id INT PRIMARY KEY, nome VARCHAR(20));
                    INSERT INTO soci_x VALUES (1, 'Anna'), (2, 'Luca'), (3, 'Zoë 😀');
                    UPDATE soci_x SET nome = 'Lucia' WHERE id = 2;
                    SELECT id, nome FROM soci_x ORDER BY id;
                    DROP TABLE IF EXISTS non_esiste""");
            ScriptResult r = exec.run(script, listener);

            assertTrue(r.completed(), () -> r.failure().toString());
            assertEquals(List.of(0L, 3L, 1L, 3L, 0L), r.results().stream().map(StatementResult::affectedRows).toList());
            ResultTable table = r.results().get(3).firstResult().orElseThrow();
            assertEquals(List.of("id", "nome"), table.columns().stream().map(ResultTable.Column::label).toList());
            assertEquals("Lucia", table.value(1, 1));
            assertEquals("Zoë 😀", table.value(2, 1));
            assertFalse(table.truncated());
            StatementResult drop = r.results().get(4);
            assertEquals(1051, drop.warnings().get(0).code(), "avviso «tabella sconosciuta» del server");
            assertEquals(List.of("inizio", "via 0 RamaSQL - esecuzione SQL", "fine 0", "via 1 RamaSQL - esecuzione SQL",
                    "fine 1", "via 2 RamaSQL - esecuzione SQL", "fine 2", "via 3 RamaSQL - esecuzione SQL", "fine 3",
                    "via 4 RamaSQL - esecuzione SQL", "fine 4", "fine script"), events);

            List<SqlLog.Entry> log = exec.log().entries();
            assertEquals(5, log.size());
            for (int i = 0; i < 5; i++) {
                SqlLog.Entry e = log.get(i);
                assertEquals(SqlLog.Outcome.OK, e.outcome());
                assertEquals(editor(), e.origin());
                assertEquals(script.statements().get(i).text(), e.sql());
                assertTrue(e.durationMillis() >= 0);
                assertEquals(r.results().get(i).affectedRows(), e.rows());
                assertTrue(e.connection().contains(session.serverInfo().displayName()), e.connection());
            }

            exec.setRowLimit(2);
            ResultTable limited = exec.run(SqlScript.of("t", editor(), "SELECT * FROM soci_x ORDER BY id"))
                    .results().get(0).firstResult().orElseThrow();
            assertEquals(2, limited.rowCount());
            assertTrue(limited.truncated());
        }
    }

    /** Arresto alla prima istruzione non riuscita: che cosa è applicato, che cosa no; lo dice anche il server. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void arrestoAlPrimoErrore(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_errore"); Session session = Step3Support.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            SqlScript script = SqlScript.of("errore", editor(), "CREATE TABLE t (id INT PRIMARY KEY)",
                    "INSERT INTO t VALUES (1)", "INSERT INTO t VALUES (1)", "INSERT INTO t VALUES (2)");
            ScriptResult r = exec.run(script);

            assertFalse(r.completed());
            assertEquals(script.statements().subList(0, 2), r.applied());
            StatementResult failure = r.failure().orElseThrow();
            assertEquals(2, failure.index());
            assertEquals(StatementResult.Status.FAILED, failure.status());
            assertEquals(1062, failure.error().code());
            assertEquals("23000", failure.error().sqlState());
            assertEquals(List.of(script.statements().get(3)), r.notExecuted());
            assertEquals("1", Step3Support.scalar(cat.connection(), "SELECT COUNT(*) FROM t"));

            List<SqlLog.Entry> log = exec.log().entries();
            assertEquals(3, log.size(), "l'istruzione non tentata non va nel registro");
            assertEquals(SqlLog.Outcome.ERROR, log.get(2).outcome());
            assertEquals(1062, log.get(2).errorCode());
            assertFalse(log.get(2).message().isEmpty());
        }
    }

    /** Mai transazioni: sempre autocommit, ogni istruzione è subito visibile agli altri, niente BEGIN/COMMIT. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void maiTransazioni(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_autocommit"); Session session = Step3Support.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            assertTrue(exec.run(SqlScript.of("t", editor(), "CREATE TABLE t (id INT PRIMARY KEY) ENGINE=InnoDB",
                    "INSERT INTO t VALUES (1)", "INSERT INTO t VALUES (2)")).completed());
            // un'altra connessione vede subito le righe: nessuna transazione aperta dall'esecutore
            try (Connection other = server.connect(cat.name())) {
                assertEquals("2", Step3Support.scalar(other, "SELECT COUNT(*) FROM t"));
            }
            ScriptResult ac = exec.run(SqlScript.of("t", editor(), "SELECT @@autocommit"));
            assertEquals("1", String.valueOf(ac.results().get(0).firstResult().orElseThrow().value(0, 0)));

            // anche se l'utente spegne l'autocommit, lo script successivo lo ritrova acceso (ADR-010)
            assertTrue(exec.run(SqlScript.of("t", editor(), "SET autocommit = 0")).completed());
            assertTrue(exec.run(SqlScript.of("t", editor(), "INSERT INTO t VALUES (3)")).completed());
            try (Connection other = server.connect(cat.name())) {
                assertEquals("3", Step3Support.scalar(other, "SELECT COUNT(*) FROM t"));
            }
            assertTrue(session.mainConnection().getAutoCommit());

            // nel registro c'è solo ciò che ha scritto l'utente: l'esecutore non ha aggiunto istruzioni di transazione
            List<String> emitted = exec.log().entries().stream().map(SqlLog.Entry::sql)
                    .filter(s -> TRANSACTION.matcher(s).find()).toList();
            assertEquals(List.of("SET autocommit = 0"), emitted);
        }
    }

    /** KILL QUERY di {@code SELECT SLEEP(30)} in meno di 2 s; la sessione resta utilizzabile. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void interruzioneConKillQuery(ItServers server) throws Exception {
        try (Session session = Step3Support.open(server, "");
                SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            CountDownLatch started = new CountDownLatch(1);
            SqlScript script = SqlScript.of("lenta", editor(), "SELECT SLEEP(30)", "SELECT 1");
            CompletableFuture<ScriptResult> future = exec.submit(script, new ExecutionListener() {
                @Override
                public void statementStarted(SqlScript s, int index, SqlStatement statement) {
                    started.countDown();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.sleep(300);
            assertTrue(exec.isRunning());
            long t0 = System.nanoTime();
            assertTrue(exec.interrupt());
            ScriptResult r = future.get(10, TimeUnit.SECONDS);
            long ms = (System.nanoTime() - t0) / 1_000_000;

            assertTrue(ms < 2000, "interrotta in " + ms + " ms");
            assertTrue(r.interrupted());
            assertEquals(StatementResult.Status.INTERRUPTED, r.failure().orElseThrow().status());
            assertEquals(List.of(script.statements().get(1)), r.notExecuted());
            assertEquals(SqlLog.Outcome.INTERRUPTED, exec.log().entries().get(0).outcome());
            assertFalse(exec.isRunning());
            assertFalse(exec.interrupt(), "nulla da interrompere");

            ScriptResult after = exec.run(SqlScript.of("dopo", editor(), "SELECT 42"));
            assertTrue(after.completed());
            assertEquals("42", String.valueOf(after.results().get(0).firstResult().orElseThrow().value(0, 0)));
            TestResults.write("step3", "kill-query-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                    "SELECT SLEEP(30) interrotta con KILL QUERY in " + ms + " ms su " + server.label()
                    + "; esito: " + r.failure().orElseThrow().status() + " (" + r.failure().orElseThrow().error()
                    + "); poi SELECT 42 → 42 sulla stessa sessione\n");
        }
    }

    /**
     * Cuore di T3.8: lavoro vario eseguito dal client su un catalogo → registro esportato come {@code .sql} (senza il
     * nome del catalogo) → rieseguito dal client su un catalogo vuoto → stessi metadati e stessi {@code CHECKSUM TABLE}.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void registroEsportatoERieseguitoDaLoStessoRisultato(ItServers server) throws Exception {
        try (TestCatalog a = TestCatalog.create(server, "t38_origine"); TestCatalog b = TestCatalog.create(server,
                "t38_copia"); Session sa = Step3Support.open(server, a.name())) {
            MetadataReader readerA = MetadataReader.of(sa);
            try (SqlExecutor exec = new SqlExecutor(sa, new SqlLog(), readerA)) {
                String fixture;
                try (InputStream in = getClass().getResourceAsStream(Step3Support.FIXTURE_INNODB)) {
                    fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                assertTrue(exec.run(SqlScript.fromText("biblioteca", editor(), fixture)).completed());
                assertTrue(exec.run(SqlScript.fromText("lavoro", editor(), """
                        CREATE TABLE appunti (id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, testo VARCHAR(100));
                        INSERT INTO appunti (testo) VALUES ('uno'), ('l''altro'), ('tre 🎉');
                        UPDATE soci SET email = NULL WHERE id = 3;
                        DELETE FROM prestiti WHERE id > 490;
                        CREATE TABLE da_buttare (x INT)""")).completed());
                // un errore a metà: registrato, commentato nell'esportazione, nessun effetto
                assertFalse(exec.run(SqlScript.of("errore", editor(),
                        "INSERT INTO soci (tessera, cognome, nome) VALUES ('T0001037', 'Doppia', 'Tessera')"))
                        .completed());
                // operazioni del navigatore (nomi qualificati con il catalogo d'origine)
                assertTrue(exec.run(TreeScripts.truncateTable(a.name(), "appunti")).completed());
                assertTrue(exec.run(SqlScript.of("t", editor(), "INSERT INTO appunti (testo) VALUES ('dopo')"))
                        .completed());
                assertTrue(exec.run(TreeScripts.renameTable(a.name(), "appunti", "note")).completed());
                assertTrue(exec.run(TreeScripts.dropTable(a.name(), "da_buttare")).completed());
                assertTrue(exec.run(TreeScripts.dropView(a.name(), "v_libri_editori")).completed());

                String exported = exec.log().exportScript(SqlLog.ExportOptions.withoutCatalog(a.name()));
                TestResults.write("step3", "T3.8-registro-" + server.name().toLowerCase(Locale.ROOT) + ".sql",
                        exported.replace(a.name(), "<catalogo>"));
                assertFalse(exported.contains("`" + a.name() + "`"), "lo script non nomina il catalogo d'origine");

                try (Session sb = Step3Support.open(server, b.name());
                        SqlExecutor execB = new SqlExecutor(sb, new SqlLog(), null)) {
                    ScriptResult rerun = execB.run(SqlScript.fromText("riesecuzione", editor(), exported));
                    assertTrue(rerun.completed(), () -> rerun.failure().toString());
                    assertEquals(exec.log().entries().stream().filter(SqlLog.Entry::isOk).count(), rerun.results().size());

                    MetadataReader readerB = MetadataReader.of(sb);
                    List<TableDef> tablesA = readerA.allTables(a.name()).stream().map(t -> t.withCatalog(null)).toList();
                    List<TableDef> tablesB = readerB.allTables(b.name()).stream().map(t -> t.withCatalog(null)).toList();
                    // struttura identica; l'unico contatore diverso è quello di «soci»: InnoDB consuma un valore di
                    // AUTO_INCREMENT anche per l'INSERT fallito per chiave duplicata, che nello script è commentato
                    assertEquals(tablesA.stream().map(t -> t.withAutoIncrementStart(null)).toList(),
                            tablesB.stream().map(t -> t.withAutoIncrementStart(null)).toList());
                    Map<String, Long> aiA = new java.util.TreeMap<>();
                    Map<String, Long> aiB = new java.util.TreeMap<>();
                    tablesA.forEach(t -> aiA.put(t.name(), t.autoIncrementStart()));
                    tablesB.forEach(t -> aiB.put(t.name(), t.autoIncrementStart()));
                    assertEquals(102L, aiA.get("soci"), "101 + 1 consumato dall'INSERT fallito");
                    assertEquals(101L, aiB.get("soci"));
                    aiA.remove("soci");
                    aiB.remove("soci");
                    assertEquals(aiA, aiB, "gli altri contatori AUTO_INCREMENT coincidono");
                    assertEquals(readerA.views(a.name()).stream().map(ViewDef::name).toList(),
                            readerB.views(b.name()).stream().map(ViewDef::name).toList());
                    Map<String, Long> sumA = Step3Support.checksums(a.connection(), readerA, a.name());
                    Map<String, Long> sumB = Step3Support.checksums(b.connection(), readerB, b.name());
                    assertEquals(sumA, sumB);
                    assertTrue(sumA.containsKey("note") && !sumA.containsKey("appunti") && !sumA.containsKey("da_buttare"));
                    TestResults.write("step3", "T3.8-confronto-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                            "T3.8 (lato core) su " + server.label() + "\n"
                            + "registro: " + exec.log().size() + " istruzioni (" + rerun.results().size()
                            + " riuscite e rieseguite, le altre commentate)\n"
                            + "tabelle: " + tablesA.stream().map(TableDef::name).toList()
                            + " — TableDef identici (colonne, indici, FK, engine, charset, commenti)\n"
                            + "AUTO_INCREMENT: uguali tranne soci (origine 102, copia 101): l'INSERT fallito per"
                            + " chiave duplicata consuma un valore in InnoDB, e nello script è commentato\n"
                            + "viste: " + readerB.views(b.name()).stream().map(ViewDef::name).toList() + "\n"
                            + "CHECKSUM TABLE origine: " + sumA + "\n"
                            + "CHECKSUM TABLE copia:   " + sumB + "\n"
                            + "esito: STESSO RISULTATO\n");
                }
            }
        }
    }

    /**
     * T3.8 con un <b>fallimento parziale</b>: {@code DROP TABLE x, manca} non riesce, ma su MariaDB elimina {@code x}
     * (su MySQL, DDL atomico, no). Il registro esportato commenta l'istruzione con l'avviso «può essere stata applicata
     * in parte: verifica»: rieseguito su un catalogo vuoto, su MySQL dà lo stesso stato; su MariaDB no ({@code x} c'è
     * nella copia e non nell'origine), ed è esattamente il caso di cui l'avviso parla. Si asserisce il comportamento
     * reale di ciascun server.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t38_registroConFallimentoParziale(ItServers server) throws Exception {
        try (TestCatalog a = TestCatalog.create(server, "t38p_origine"); TestCatalog b = TestCatalog.create(server,
                "t38p_copia"); Session sa = Step3Support.open(server, a.name())) {
            MetadataReader readerA = MetadataReader.of(sa);
            try (SqlExecutor exec = new SqlExecutor(sa, new SqlLog(), readerA)) {
                assertTrue(exec.run(SqlScript.fromText("lavoro", editor(), """
                        CREATE TABLE x (id INT PRIMARY KEY);
                        INSERT INTO x VALUES (1), (2);
                        CREATE TABLE y (id INT PRIMARY KEY)""")).completed());
                ScriptResult drop = exec.run(SqlScript.of("drop", editor(), "DROP TABLE x, manca"));
                assertEquals(1051, drop.failure().orElseThrow().error().code());
                assertTrue(exec.run(SqlScript.of("t", editor(), "INSERT INTO y VALUES (7)")).completed());
                boolean xInOrigin = readerA.tables(a.name()).stream().anyMatch(t -> t.name().equals("x"));
                assertEquals(!server.isMariaDb(), xInOrigin, "comportamento reale di " + server.label());

                String exported = exec.log().exportScript(SqlLog.ExportOptions.withoutCatalog(a.name()));
                assertTrue(exported.contains("-- DROP TABLE x, manca;\n-- "
                        + it.ramasql.core.CoreMessages.get("log.export.partial") + "\n"), exported);
                TestResults.write("step3", "T3.8-parziale-registro-" + server.name().toLowerCase(Locale.ROOT) + ".sql",
                        exported.replace(a.name(), "<catalogo>"));
                try (Session sb = Step3Support.open(server, b.name());
                        SqlExecutor execB = new SqlExecutor(sb, new SqlLog(), null)) {
                    ScriptResult rerun = execB.run(SqlScript.fromText("riesecuzione", editor(), exported));
                    assertTrue(rerun.completed(), () -> rerun.failure().toString());
                    MetadataReader readerB = MetadataReader.of(sb);
                    List<String> tablesA = readerA.tables(a.name()).stream().map(TableSummary::name).toList();
                    List<String> tablesB = readerB.tables(b.name()).stream().map(TableSummary::name).toList();
                    assertEquals(List.of("x", "y"), tablesB, "la copia non riesegue il DROP commentato");
                    Map<String, Long> sumA = Step3Support.checksums(a.connection(), readerA, a.name());
                    Map<String, Long> sumB = Step3Support.checksums(b.connection(), readerB, b.name());
                    if (server.isMariaDb()) {
                        assertEquals(List.of("y"), tablesA, "MariaDB: x eliminata dall'istruzione «non riuscita»");
                        assertFalse(sumA.equals(sumB), "stato diverso: è il caso segnalato dall'avviso nello script");
                        assertEquals(sumA.get("y"), sumB.get("y"));
                    } else {
                        assertEquals(List.of("x", "y"), tablesA, "MySQL: DDL atomico, nulla eliminato");
                        assertEquals(sumA, sumB, "stesso stato finale");
                    }
                    TestResults.write("step3", "T3.8-parziale-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                            "T3.8 con fallimento parziale su " + server.label() + "\n"
                            + "DROP TABLE x, manca → errore 1051; x nell'origine dopo l'errore: "
                            + (xInOrigin ? "presente (DDL atomico)" : "ELIMINATA (applicata in parte)") + "\n"
                            + "registro esportato: istruzione commentata con «"
                            + it.ramasql.core.CoreMessages.get("log.export.partial") + "»\n"
                            + "tabelle origine: " + tablesA + " · copia rieseguita: " + tablesB + "\n"
                            + "CHECKSUM origine: " + sumA + "\nCHECKSUM copia:   " + sumB + "\n"
                            + "esito: " + (server.isMariaDb()
                                    ? "STATO DIVERSO, come avvisato nello script (l'istruzione fallita aveva effetto)"
                                    : "STESSO RISULTATO") + "\n");
                }
            }
        }
    }

    /**
     * Operazioni del navigatore eseguite dall'esecutore e verificate sul server con una connessione separata
     * (lato core di T3.5–T3.7); il lettore dei metadati si aggiorna da solo.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void operazioniSullAlberoVerificateSulServer(ItServers server) throws Exception {
        String created = TestCatalog.requireTestName("ramasql_test_t36_" + Long.toString(System.nanoTime(), 36));
        try (TestCatalog cat = TestCatalog.create(server, "t36_albero"); Session session = Step3Support.open(server,
                cat.name())) {
            cat.execute("CREATE TABLE vecchia (id INT PRIMARY KEY)", "INSERT INTO vecchia VALUES (1), (2)",
                    "CREATE TABLE eliminami (id INT)", "CREATE VIEW v_vecchia AS SELECT id FROM vecchia");
            MetadataReader reader = MetadataReader.of(session);
            List<String> events = Collections.synchronizedList(new ArrayList<>());
            reader.addListener((c, t) -> events.add(c + "/" + t));
            reader.tables(cat.name());
            reader.catalogs();
            Connection check = cat.connection();
            try (SqlExecutor exec = new SqlExecutor(session, new SqlLog(), reader)) {
                // T3.5 lato core: l'anteprima si prepara ma non si esegue («Annulla») → niente sul server, niente registro
                SqlScript preview = TreeScripts.dropTable(cat.name(), "vecchia");
                assertEquals(ConfirmationPolicy.Level.STRONG, ConfirmationPolicy.evaluate(preview).level());
                assertEquals(0, exec.log().size());
                assertEquals("1", Step3Support.scalar(check, "SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = '" + cat.name() + "' AND TABLE_NAME = 'vecchia'"));

                // crea catalogo con charset e collation scelti
                assertTrue(exec.run(TreeScripts.createCatalog(created, "latin1", "latin1_swedish_ci")).completed());
                assertEquals("latin1|latin1_swedish_ci", Step3Support.scalar(check,
                        "SELECT CONCAT(DEFAULT_CHARACTER_SET_NAME, '|', DEFAULT_COLLATION_NAME)"
                        + " FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + created + "'"));
                CatalogInfo info = reader.catalog(created).orElseThrow();
                assertEquals("latin1", info.charset());

                // rinomina, svuota, elimina
                assertTrue(exec.run(TreeScripts.renameTable(cat.name(), "vecchia", "nuova")).completed());
                assertEquals(List.of("eliminami", "nuova", "v_vecchia"),
                        reader.tables(cat.name()).stream().map(TableSummary::name).toList());
                assertEquals("nuova", Step3Support.scalar(check, "SELECT TABLE_NAME FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = '" + cat.name() + "' AND TABLE_NAME IN ('vecchia', 'nuova')"));
                assertTrue(exec.run(TreeScripts.truncateTable(cat.name(), "nuova")).completed());
                assertEquals("0", Step3Support.scalar(check, "SELECT COUNT(*) FROM nuova"));
                assertTrue(exec.run(TreeScripts.dropView(cat.name(), "v_vecchia")).completed());
                assertTrue(exec.run(TreeScripts.dropTable(cat.name(), "eliminami")).completed());
                assertEquals("0", Step3Support.scalar(check, "SELECT COUNT(*) FROM information_schema.TABLES"
                        + " WHERE TABLE_SCHEMA = '" + cat.name() + "' AND TABLE_NAME IN ('eliminami', 'v_vecchia')"));
                assertEquals(List.of("nuova"), reader.tables(cat.name()).stream().map(TableSummary::name).toList());

                // elimina il catalogo creato
                assertTrue(exec.run(TreeScripts.dropCatalog(created)).completed());
                assertNull(Step3Support.scalar(check,
                        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + created + "'"));
                assertTrue(reader.catalog(created).isEmpty());

                // registro: ogni operazione con origine «Navigatore», esito, durata
                List<SqlLog.Entry> log = exec.log().entries();
                assertEquals(6, log.size());
                assertTrue(log.stream().allMatch(e -> e.isOk() && e.origin().equals(SqlOrigin.NAVIGATOR.label())
                        && e.durationMillis() >= 0));
                assertTrue(events.contains(cat.name() + "/null"), events.toString());
                assertTrue(events.contains("null/null"), events.toString());
                StringBuilder evidence = new StringBuilder("Operazioni del navigatore su " + server.label() + "\n");
                log.forEach(e -> evidence.append(e.sequence()).append(". [").append(e.origin()).append("] ")
                        .append(e.outcome()).append(' ').append(e.durationMillis()).append(" ms — ")
                        .append(e.sql().replace(cat.name(), "<catalogo>").replace(created, "<nuovo>")).append('\n'));
                TestResults.write("step3", "T3.6-navigatore-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                        evidence.toString());
            }
        } finally {
            Step3Support.dropTestCatalog(server, created);
        }
    }

    /** Dopo un DDL eseguito dall'esecutore la cache del catalogo toccato si svuota da sola (anche per nomi qualificati). */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void invalidazioneDellaCacheDopoDdl(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t3_inval"); TestCatalog other = TestCatalog.create(server,
                "t3_inval_altro"); Session session = Step3Support.open(server, cat.name())) {
            cat.execute("CREATE TABLE x (id INT PRIMARY KEY)");
            MetadataReader reader = MetadataReader.of(session);
            List<String> events = Collections.synchronizedList(new ArrayList<>());
            reader.addListener((c, t) -> events.add(c + "/" + t));
            try (SqlExecutor exec = new SqlExecutor(session, new SqlLog(), reader)) {
                assertEquals(1, reader.table(cat.name(), "x").orElseThrow().columns().size());
                reader.tables(other.name());
                assertTrue(reader.isCached(cat.name(), "x"));

                assertTrue(exec.run(SqlScript.of("t", editor(), "SELECT * FROM x", "INSERT INTO x VALUES (1)"))
                        .completed());
                assertTrue(reader.isCached(cat.name(), "x"), "SELECT e INSERT non invalidano");
                assertEquals(List.of(), events);

                assertTrue(exec.run(SqlScript.of("t", editor(), "ALTER TABLE x ADD COLUMN y INT")).completed());
                assertFalse(reader.isCached(cat.name(), "x"));
                assertEquals(2, reader.table(cat.name(), "x").orElseThrow().columns().size());
                assertEquals(List.of(cat.name() + "/null"), events);

                assertTrue(reader.isCached(other.name()));
                assertTrue(exec.run(SqlScript.of("t", editor(),
                        "CREATE TABLE `" + other.name() + "`.`z` (id INT)")).completed());
                assertFalse(reader.isCached(other.name()));
                assertEquals(List.of("z"), reader.tables(other.name()).stream().map(TableSummary::name).toList());
                assertNotNull(events.stream().filter(e -> e.startsWith(other.name())).findFirst().orElse(null));
            }
        }
    }
}
