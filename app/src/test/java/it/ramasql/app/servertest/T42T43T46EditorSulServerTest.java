/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.editor.SqlEditor;
import it.ramasql.core.exec.ScriptResult;

/**
 * L'editor SQL <b>contro i server veri</b>, dalla scheda «Query 1» del programma:
 * <ul>
 *   <li><b>T4.2</b>: uno script di <b>50 istruzioni</b> miste (DDL, DML, SELECT) con un blocco {@code DELIMITER},
 *       eseguito in ordine, con l'esito di ognuna (righe lette o righe interessate, e la durata);</li>
 *   <li><b>T4.3</b>: {@code SELECT SLEEP(30)} interrotto con <em>Interrompi</em> in meno di 2 secondi, con la
 *       sessione che resta utilizzabile subito dopo;</li>
 *   <li><b>T4.6</b>: un errore di sintassi voluto → messaggio del server, spiegazione in italiano e riga indicata.</li>
 * </ul>
 */
@Tag("step4")
@Tag("ui")
@Tag("it")
class T42T43T46EditorSulServerTest {

    private static final int ISTRUZIONI = 50;

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t42_t43_t46_editorEseguiInterrompiESpiegaGliErrori(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("editor");
        StringBuilder ev = new StringBuilder("T4.2, T4.3, T4.6 — editor SQL su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                SqlEditor editor = fromEdt(a.frame()::openSqlEditor);
                assertTrue(fromEdt(() -> a.frame().button("run").isEnabled()), "«Esegui» si accende con un editor");

                // ---------------------------------------------------------------- T4.2
                String script = script(catalog);
                onEdt(() -> editor.setText(script));
                onEdt(editor::runAll);
                Probe.waitUntil("script eseguito", 60_000, () -> !fromEdt(editor::isRunning));
                a.waitIdle();

                ScriptResult esito = a.workspace().pipeline().lastProposal().get();
                assertTrue(esito != null && esito.completed(), "le " + ISTRUZIONI + " istruzioni sono passate tutte: "
                        + (esito == null ? "annullato"
                        : esito.failure().map(f -> f.statement().text() + " → " + f.error()).orElse("?")));
                assertEquals(ISTRUZIONI, esito.script().size(), "lo script ha " + ISTRUZIONI + " istruzioni");
                assertEquals(ISTRUZIONI, esito.results().size(), "ognuna ha il suo esito");
                assertEquals(ISTRUZIONI, fromEdt(() -> editor.results().outcomeTable().getRowCount()),
                        "l'editor mostra un esito per istruzione");
                long conRighe = esito.results().stream().filter(r -> r.firstResult().isPresent()).count();
                assertTrue(conRighe >= 3, "ci sono più risultati tabellari: " + conRighe);
                // il DELIMITER ha tenuto insieme la procedura: sul server c'è, e la CALL è passata
                assertEquals("1", server.scalar("SELECT COUNT(*) FROM information_schema.ROUTINES WHERE"
                        + " ROUTINE_SCHEMA = '" + catalog + "' AND ROUTINE_NAME = 'conta_per_anno'"),
                        "la procedura creata dal blocco DELIMITER è sul server");
                assertEquals(String.valueOf(ISTRUZIONI - 1), String.valueOf(esito.results().size() - 1));
                Probe.paintWindow("step4", a.frame(), "T4.2-server-" + server.id() + ".png");
                ev.append("Catalogo di test: ").append(catalog).append('\n')
                        .append("T4.2 — script di ").append(esito.script().size())
                        .append(" istruzioni (DDL, DML, SELECT, blocco DELIMITER con procedura): tutte eseguite in ")
                        .append(esito.durationMillis()).append(" ms, ").append(conRighe)
                        .append(" risultati tabellari; procedura conta_per_anno presente sul server; ")
                        .append("righe in prova_t42: ").append(server.rowCount(catalog, "prova_t42")).append('\n')
                        .append("  esito riga 1: ").append(sintesi(esito, 0)).append("; ultima: ")
                        .append(sintesi(esito, esito.results().size() - 1)).append('\n');

                // ---------------------------------------------------------------- T4.3
                onEdt(() -> editor.setText("SELECT SLEEP(30);"));
                onEdt(editor::runAll);
                Probe.waitUntil("SLEEP partito", 10_000, () -> fromEdt(editor::isRunning));
                long start = System.nanoTime();
                onEdt(editor::cancelRun);
                Probe.waitUntil("interrotto", 10_000, () -> !fromEdt(editor::isRunning));
                long interruptMs = (System.nanoTime() - start) / 1_000_000;
                a.waitIdle();
                assertTrue(interruptMs < 2000, "interrotto in " + interruptMs + " ms, limite 2000");
                Probe.paintWindow("step4", a.frame(), "T4.3-server-" + server.id() + ".png");

                // la sessione resta utilizzabile: subito dopo una query normale funziona
                onEdt(() -> editor.setText("SELECT COUNT(*) FROM `" + catalog + "`.`soci`;"));
                onEdt(editor::runAll);
                Probe.waitUntil("query dopo l'interruzione", 20_000, () -> !fromEdt(editor::isRunning));
                a.waitIdle();
                ScriptResult dopo = a.workspace().pipeline().lastProposal().get();
                assertTrue(dopo != null && dopo.completed(), "dopo l'interruzione la sessione funziona");
                String conteggio = String.valueOf(dopo.results().get(0).firstResult().orElseThrow().value(0, 0));
                assertEquals(server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.`soci`"), conteggio);
                ev.append("T4.3 — SELECT SLEEP(30) interrotto in ").append(interruptMs)
                        .append(" ms (limite 2000); subito dopo SELECT COUNT(*) FROM soci = ").append(conteggio)
                        .append(" (uguale al server): la sessione è utilizzabile\n");

                // ---------------------------------------------------------------- T4.6
                onEdt(() -> editor.setText("SELECT titolo FROM `" + catalog + "`.`libri`;\n"
                        + "SELEC * FROM `" + catalog + "`.`soci`;"));
                onEdt(editor::runAll);
                Probe.waitUntil("errore di sintassi", 20_000, () -> !fromEdt(editor::isRunning));
                a.waitIdle();
                String errore = fromEdt(() -> editor.results().errorText());
                assertTrue(errore.contains("1064"), "codice del server: " + errore);
                assertTrue(errore.contains("sintassi"), "spiegazione in italiano: " + errore);
                assertEquals(2, fromEdt(editor::errorLine), "la riga dell'istruzione sbagliata è la 2");
                assertFalse(fromEdt(editor::errorHighlightedText).isBlank(), "il punto è evidenziato nell'editor");
                Probe.paintWindow("step4", a.frame(), "T4.6-server-" + server.id() + ".png");
                ev.append("T4.6 — errore voluto «SELEC»: «").append(errore.replace('\n', ' ')).append("»; riga ")
                        .append(fromEdt(editor::errorLine)).append(" evidenziata, testo evidenziato «")
                        .append(fromEdt(editor::errorHighlightedText)).append("»\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            // un test fallito non deve lasciare un file di evidenza che sembra valido
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step4", "T4.2-T4.3-T4.6-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    private static String sintesi(ScriptResult esito, int index) {
        var r = esito.results().get(index);
        return r.statement().text().lines().findFirst().orElse("") + " → "
                + (r.firstResult().isPresent() ? r.firstResult().get().rowCount() + " righe lette"
                        : r.affectedRows() + " righe interessate") + ", " + r.durationMillis() + " ms";
    }

    /**
     * Lo script di prova: 50 istruzioni in tutto — 1 CREATE TABLE, 30 INSERT, 5 UPDATE (tutte con WHERE: nessuna
     * conferma rafforzata di mezzo), 1 ALTER, 3 SELECT, 1 procedura dentro un blocco {@code DELIMITER}, 1 CALL,
     * 8 SELECT di controllo.
     */
    private static String script(String catalog) {
        StringBuilder sb = new StringBuilder();
        String t = "`" + catalog + "`.`prova_t42`";
        sb.append("CREATE TABLE ").append(t).append(" (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                + " titolo VARCHAR(80) NOT NULL, anno SMALLINT UNSIGNED NULL, PRIMARY KEY (id))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;\n");
        for (int i = 1; i <= 30; i++) {
            sb.append("INSERT INTO ").append(t).append(" (titolo, anno) VALUES ('Titolo ").append(i).append("', ")
                    .append(1990 + (i % 30)).append(");\n");
        }
        for (int i = 1; i <= 5; i++) {
            sb.append("UPDATE ").append(t).append(" SET anno = anno + 1 WHERE id = ").append(i).append(";\n");
        }
        sb.append("ALTER TABLE ").append(t).append(" ADD COLUMN nota VARCHAR(40) NULL;\n");
        sb.append("SELECT COUNT(*) AS quante FROM ").append(t).append(";\n");
        sb.append("SELECT titolo, anno FROM ").append(t).append(" WHERE anno > 2000 ORDER BY anno;\n");
        sb.append("SELECT anno, COUNT(*) FROM ").append(t).append(" GROUP BY anno ORDER BY anno;\n");
        sb.append("DELIMITER //\n")
                .append("CREATE PROCEDURE `").append(catalog).append("`.`conta_per_anno`(IN dal SMALLINT)\n")
                .append("BEGIN\n")
                .append("  SELECT anno, COUNT(*) FROM ").append(t).append(" WHERE anno >= dal GROUP BY anno;\n")
                .append("END //\n")
                .append("DELIMITER ;\n");
        sb.append("CALL `").append(catalog).append("`.`conta_per_anno`(1995);\n");
        for (int i = 1; i <= 8; i++) {
            sb.append("SELECT id, titolo FROM ").append(t).append(" WHERE id = ").append(i).append(";\n");
        }
        return sb.toString();
    }
}
