/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.ResultTable;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.StatementResult;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;
import it.ramasql.core.verify.SchemaVerifier;
import it.ramasql.core.verify.Verification;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.it.step5.EditorSupport;

/**
 * T6.5 — integrità referenziale <b>reale</b> sui due server, con tabelle e FK create dal generatore
 * ({@link TableDiff}) e applicate con {@link SqlExecutor}; i dati si toccano con la stessa pipeline e gli errori si
 * leggono da {@link StatementResult} e dal registro {@link SqlLog}: il comportamento del server è quello dichiarato
 * dalla FK.
 */
@Tag("step6")
@Tag("it")
class T65IntegritaRealeTest {

    private static final Map<ItServers, Map<String, String>> EVIDENZE = new EnumMap<>(ItServers.class);

    static final TableDef EDITORI = TableDef.of(null, "editori")
            .addColumn(ColumnDef.of("id", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "50").notNull())
            .withPrimaryKey("id").withEngine("InnoDB").withOrdinalPositions();

    static final TableDef LIBRI = TableDef.of(null, "libri")
            .addColumn(ColumnDef.of("id", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("titolo", "VARCHAR", "100").notNull())
            .addColumn(ColumnDef.of("id_editore", "INT").withUnsigned(true))
            .withPrimaryKey("id").withEngine("InnoDB").withOrdinalPositions();

    private static final String DATI_EDITORI = "INSERT INTO `editori` (`id`, `nome`) VALUES (1, 'Zanichelli'), "
            + "(2, 'Hoepli')";
    private static final String DATI_LIBRI = "INSERT INTO `libri` (`id`, `titolo`, `id_editore`) VALUES "
            + "(10, 'Basi di dati', 1), (11, 'SQL per tutti', 1), (12, 'Reti', 2)";

    /** Crea le due tabelle e la FK con il generatore e l'esecutore, inserisce i dati, verifica la FK sul server. */
    private static TableDef prepara(TestCatalog cat, Session session, SqlExecutor exec, FkAction onDelete,
            FkAction onUpdate, StringBuilder ev) throws Exception {
        TableDef conFk = LIBRI.addForeignKey(ForeignKeyDef.of("fk_libri_editori", "id_editore", "editori", "id")
                .withActions(onDelete, onUpdate));
        List<String> sql = new ArrayList<>();
        sql.addAll(TableDiff.diff(null, EDITORI, session.serverInfo()));
        sql.addAll(TableDiff.diff(null, LIBRI, session.serverInfo()));
        sql.addAll(TableDiff.diff(LIBRI, conFk, session.serverInfo()));
        EditorSupport.apply(exec, "T6.5 struttura", sql);
        ev.append("struttura (generatore → SqlExecutor):\n").append(EditorSupport.block(sql)).append('\n');
        Verification v = SchemaVerifier.verify(LIBRI, conFk.withCatalog(cat.name()),
                EditorSupport.reread(session, cat.name(), "libri"));
        assertTrue(v.conforming(), v::summary);
        ev.append("verifica dopo: ").append(v.summary().lines().findFirst().orElse("")).append('\n');
        EditorSupport.apply(exec, "T6.5 dati", List.of(DATI_EDITORI, DATI_LIBRI));
        ev.append("dati:\n    ").append(DATI_EDITORI).append(";\n    ").append(DATI_LIBRI).append(";\n");
        return conFk;
    }

    /** Esegue una istruzione dell'«utente» (origine Editor SQL) e ne restituisce l'esito. */
    private static StatementResult esegui(SqlExecutor exec, String sql, StringBuilder ev) {
        ScriptResult r = exec.run(SqlScript.of("T6.5", SqlOrigin.EDITOR.label(), sql));
        StatementResult s = r.results().get(0);
        ev.append("> ").append(sql).append(";\n  → ").append(s.isOk() ? "OK, righe " + s.affectedRows()
                : "ERRORE " + s.error().code() + " (" + s.error().sqlState() + "): " + s.error().message()).append('\n');
        return s;
    }

    private static String righe(SqlExecutor exec, String select, StringBuilder ev) {
        ResultTable t = exec.run(SqlScript.of("T6.5", SqlOrigin.EDITOR.label(), select)).results().get(0)
                .firstResult().orElseThrow();
        List<String> out = new ArrayList<>();
        for (int r = 0; r < t.rowCount(); r++) {
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < t.columns().size(); c++) {
                cells.add(String.valueOf(t.value(r, c)));
            }
            out.add(String.join("|", cells));
        }
        String text = String.join("; ", out);
        ev.append("> ").append(select).append(";\n  → ").append(text.isEmpty() ? "(nessuna riga)" : text).append('\n');
        return text;
    }

    private static void erroreNelRegistro(SqlExecutor exec, int code) {
        SqlLog.Entry last = exec.log().entries().get(exec.log().entries().size() - 1);
        assertEquals(SqlLog.Outcome.ERROR, last.outcome());
        assertEquals(code, last.errorCode(), "codice d'errore nel registro");
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void restrictDeleteDelPadreDa1451(ItServers server) throws Exception {
        caso(server, "1 RESTRICT: DELETE del padre → 1451", (cat, session, exec, ev) -> {
            prepara(cat, session, exec, FkAction.RESTRICT, FkAction.RESTRICT, ev);
            StatementResult r = esegui(exec, "DELETE FROM `editori` WHERE `id` = 1", ev);
            assertEquals(StatementResult.Status.FAILED, r.status());
            assertEquals(1451, r.error().code());
            assertEquals("23000", r.error().sqlState());
            erroreNelRegistro(exec, 1451);
            assertEquals("1|Zanichelli; 2|Hoepli", righe(exec, "SELECT `id`, `nome` FROM `editori` ORDER BY `id`", ev),
                    "il padre non è stato eliminato");
            StatementResult u = esegui(exec, "UPDATE `editori` SET `id` = 5 WHERE `id` = 2", ev);
            assertEquals(1451, u.error().code(), "ON UPDATE RESTRICT: anche la modifica della chiave è rifiutata");
            erroreNelRegistro(exec, 1451);
        });
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void figlioOrfanoDa1452(ItServers server) throws Exception {
        caso(server, "2 INSERT di un figlio orfano → 1452", (cat, session, exec, ev) -> {
            prepara(cat, session, exec, FkAction.RESTRICT, FkAction.RESTRICT, ev);
            StatementResult r = esegui(exec,
                    "INSERT INTO `libri` (`id`, `titolo`, `id_editore`) VALUES (20, 'Orfano', 99)", ev);
            assertEquals(StatementResult.Status.FAILED, r.status());
            assertEquals(1452, r.error().code());
            erroreNelRegistro(exec, 1452);
            StatementResult u = esegui(exec, "UPDATE `libri` SET `id_editore` = 98 WHERE `id` = 12", ev);
            assertEquals(1452, u.error().code(), "anche un UPDATE che rende orfano il figlio");
            erroreNelRegistro(exec, 1452);
            assertEquals("10|1; 11|1; 12|2", righe(exec,
                    "SELECT `id`, `id_editore` FROM `libri` ORDER BY `id`", ev), "nessun orfano entrato");
            StatementResult nullo = esegui(exec,
                    "INSERT INTO `libri` (`id`, `titolo`, `id_editore`) VALUES (21, 'Senza editore', NULL)", ev);
            assertTrue(nullo.isOk(), "NULL nella colonna della FK è ammesso");
        });
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void cascadeEliminaEAggiornaIFigli(ItServers server) throws Exception {
        caso(server, "3 CASCADE: DELETE del padre → figli eliminati; UPDATE → figli aggiornati",
                (cat, session, exec, ev) -> {
                    prepara(cat, session, exec, FkAction.CASCADE, FkAction.CASCADE, ev);
                    StatementResult r = esegui(exec, "DELETE FROM `editori` WHERE `id` = 1", ev);
                    assertTrue(r.isOk());
                    assertEquals(1, r.affectedRows(), "il server conta solo la riga del padre");
                    assertEquals("12|2", righe(exec, "SELECT `id`, `id_editore` FROM `libri` ORDER BY `id`", ev),
                            "i due libri dell'editore 1 sono spariti");
                    assertTrue(esegui(exec, "UPDATE `editori` SET `id` = 7 WHERE `id` = 2", ev).isOk());
                    assertEquals("12|7", righe(exec, "SELECT `id`, `id_editore` FROM `libri` ORDER BY `id`", ev),
                            "ON UPDATE CASCADE: il figlio segue la nuova chiave");
                });
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void setNullMetteIFigliANull(ItServers server) throws Exception {
        caso(server, "4 SET NULL: DELETE del padre → figli a NULL", (cat, session, exec, ev) -> {
            prepara(cat, session, exec, FkAction.SET_NULL, FkAction.SET_NULL, ev);
            assertTrue(esegui(exec, "DELETE FROM `editori` WHERE `id` = 1", ev).isOk());
            assertEquals("10|null; 11|null; 12|2", righe(exec,
                    "SELECT `id`, `id_editore` FROM `libri` ORDER BY `id`", ev), "figli rimasti, con editore NULL");
            assertEquals("2", righe(exec, "SELECT COUNT(*) FROM `libri` WHERE `id_editore` IS NULL", ev));
            assertTrue(esegui(exec, "UPDATE `editori` SET `id` = 8 WHERE `id` = 2", ev).isOk());
            assertEquals("10|null; 11|null; 12|null", righe(exec,
                    "SELECT `id`, `id_editore` FROM `libri` ORDER BY `id`", ev), "ON UPDATE SET NULL");
            assertEquals("3", righe(exec, "SELECT COUNT(*) FROM `libri`", ev), "nessun libro eliminato");
        });
    }

    // ================================================================ attrezzi

    @FunctionalInterface
    interface Corpo {
        void esegui(TestCatalog cat, Session session, SqlExecutor exec, StringBuilder ev) throws Exception;
    }

    private static void caso(ItServers server, String titolo, Corpo corpo) throws Exception {
        StringBuilder ev = new StringBuilder("## " + titolo + "\n");
        try (TestCatalog cat = TestCatalog.create(server, "t65"); Session session = EditorSupport.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            ev.append("server: ").append(session.serverInfo().displayName()).append('\n');
            corpo.esegui(cat, session, exec, ev);
            assertFalse(exec.log().entries().isEmpty());
            ev.append("ESITO: comportamento del server = dichiarato dalla FK\n");
        } catch (Throwable t) {
            ev.append("ESITO: FALLITO — ").append(t.getMessage()).append('\n');
            throw t;
        } finally {
            synchronized (EVIDENZE) {
                EVIDENZE.computeIfAbsent(server, k -> new TreeMap<>()).put(titolo, ev.toString());
            }
        }
    }

    @AfterAll
    static void scriviEvidenze() {
        synchronized (EVIDENZE) {
            for (Map.Entry<ItServers, Map<String, String>> e : EVIDENZE.entrySet()) {
                StringBuilder out = new StringBuilder("# T6.5 — integrità referenziale reale su " + e.getKey().label()
                        + "\n# Classe: it.ramasql.it.step6.T65IntegritaRealeTest (step6, it)\n"
                        + "# Struttura e FK create dal generatore, eseguite con SqlExecutor; esiti da StatementResult "
                        + "e dal registro SqlLog.\n\n");
                e.getValue().values().forEach(v -> out.append(v).append('\n'));
                TestResults.write("step6", "T6.5-" + e.getKey().name().toLowerCase(Locale.ROOT) + ".txt", out.toString());
            }
        }
    }
}
