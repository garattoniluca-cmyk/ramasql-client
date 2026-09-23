/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.servertest.ClientApp;
import it.ramasql.app.servertest.DbServer;
import it.ramasql.app.servertest.Probe;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * <b>T5.4</b> e <b>T5.8</b> <b>sul server vero</b>, dall'editor di tabelle del programma (aperto dal navigatore con
 * «Nuova tabella…»).
 *
 * <p>T5.4: {@code editori} e {@code libri} della {@code biblioteca} costruite da zero cella per cella; l'anteprima
 * viva coincide <b>carattere per carattere</b> con l'SQL <b>registrato ed eseguito</b> (confronto con il
 * {@link SqlLog}, come chiede la roadmap), e la tabella riletta dal server è quella voluta.
 * <p>T5.8: tabella {@code ordine dettagli} con colonna {@code order} — nomi con spazio e parole riservate — creata
 * per davvero grazie ai backtick.
 */
@Tag("step5")
@Tag("ui")
@Tag("it")
class T54T58SulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t54_t58_tabelleCreateDallEditorSulServer(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("editor5");
        StringBuilder ev = new StringBuilder("T5.4 e T5.8 — editor di tabelle sul server " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            ev.append("Catalogo di test: ").append(catalog).append('\n');

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();

                // ---------------------------------------------------------------- T5.4: editori
                TableEditor editori = fromEdt(() -> a.frame().openTableEditor(catalog, null));
                assertNotNull(editori, "l'editor si è aperto");
                onEdt(() -> {
                    editori.optionsTab().setTableName("editori");
                    ColumnsTab c = editori.columnsTab();
                    c.setCell(0, ColumnsTab.UN, true);              // id INT UNSIGNED PK NN AI (proposti)
                    c.addColumn();
                    c.setCell(1, ColumnsTab.NAME, "nome");
                    c.setCell(1, ColumnsTab.TYPE, "VARCHAR");
                    c.setCell(1, ColumnsTab.ARGS, "80");
                    c.setCell(1, ColumnsTab.NN, true);
                    c.addColumn();
                    c.setCell(2, ColumnsTab.NAME, "citta");
                    c.setCell(2, ColumnsTab.TYPE, "VARCHAR(60)");
                });
                List<String> anteprima = fromEdt(editori::previewStatements);
                int primaDelRegistro = fromEdt(() -> a.log().size());
                onEdt(editori::apply);
                a.awaitLastProposal();
                a.waitIdle();

                List<SqlLog.Entry> tutte = fromEdt(() -> a.log().entries());
                List<SqlLog.Entry> registrate = tutte.subList(primaDelRegistro, tutte.size());
                assertEquals(anteprima.size(), registrate.size(), "un'istruzione registrata per ognuna in anteprima");
                for (int i = 0; i < anteprima.size(); i++) {
                    assertEquals(anteprima.get(i), registrate.get(i).sql(),
                            "anteprima e registro coincidono carattere per carattere");
                    assertEquals(SqlLog.Outcome.OK, registrate.get(i).outcome(), "eseguita senza errori");
                }
                String esito = fromEdt(editori::outcomeText);
                assertTrue(esito.contains("verificato sul server"), esito);
                assertTrue(server.tableExists(catalog, "editori"), "editori è sul server");
                TableDef letta = a.workspace().reader().table(catalog, "editori").orElseThrow();
                assertEquals(List.of(), TableDiff.diff(letta, fromEdt(editori::editedTable),
                        a.workspace().session().serverInfo()), "la tabella riletta è quella voluta: 0 differenze");
                Probe.paintWindow("step5", a.frame(), "T5.4-editori-server-" + server.id() + ".png");
                ev.append("T5.4 — editori: anteprima = registro = eseguito\n")
                        .append(String.join(";\n", anteprima)).append(";\n")
                        .append("  esito: ").append(esito.replace('\n', ' ')).append('\n')
                        .append("  TableDiff(riletta dal server, voluta): 0 istruzioni\n");

                // ---------------------------------------------------------------- T5.4: libri (con FK su editori)
                TableEditor libri = fromEdt(() -> a.frame().openTableEditor(catalog, null));
                onEdt(() -> {
                    libri.optionsTab().setTableName("libri");
                    libri.optionsTab().setComment("Catalogo dei libri");
                    ColumnsTab c = libri.columnsTab();
                    c.setCell(0, ColumnsTab.UN, true);
                    c.addColumn();
                    c.setCell(1, ColumnsTab.NAME, "titolo");
                    c.setCell(1, ColumnsTab.TYPE, "VARCHAR(150)");
                    c.setCell(1, ColumnsTab.NN, true);
                    c.addColumn();
                    c.setCell(2, ColumnsTab.NAME, "anno");
                    c.setCell(2, ColumnsTab.TYPE, "SMALLINT");
                    c.setCell(2, ColumnsTab.UN, true);
                });
                List<String> anteprimaLibri = fromEdt(libri::previewStatements);
                int prima2 = fromEdt(() -> a.log().size());
                onEdt(libri::apply);
                a.awaitLastProposal();
                a.waitIdle();
                List<SqlLog.Entry> tutte2 = fromEdt(() -> a.log().entries());
                List<SqlLog.Entry> reg2 = tutte2.subList(prima2, tutte2.size());
                assertEquals(anteprimaLibri, reg2.stream().map(SqlLog.Entry::sql).toList(),
                        "anche per libri: anteprima = registro");
                assertTrue(server.tableExists(catalog, "libri"), "libri è sul server");
                assertEquals("Catalogo dei libri", server.scalar("SELECT TABLE_COMMENT FROM"
                        + " information_schema.TABLES WHERE TABLE_SCHEMA = '" + catalog
                        + "' AND TABLE_NAME = 'libri'"), "il commento è sul server");
                ev.append("T5.4 — libri: anteprima = registro; commento sul server «Catalogo dei libri»\n");

                // ---------------------------------------------------------------- T5.8
                TableEditor riservate = fromEdt(() -> a.frame().openTableEditor(catalog, null));
                onEdt(() -> {
                    riservate.optionsTab().setTableName("ordine dettagli");
                    ColumnsTab c = riservate.columnsTab();
                    c.setCell(0, ColumnsTab.NAME, "order");
                    c.setCell(0, ColumnsTab.TYPE, "INT");
                    c.setCell(0, ColumnsTab.NN, true);
                    c.addColumn();
                    c.setCell(1, ColumnsTab.NAME, "select");
                    c.setCell(1, ColumnsTab.TYPE, "VARCHAR(45)");
                    c.setCell(1, ColumnsTab.DEFAULT, "nuovo");
                });
                List<String> anteprimaRis = fromEdt(riservate::previewStatements);
                assertTrue(anteprimaRis.get(0).contains("`ordine dettagli`"), anteprimaRis.get(0));
                assertTrue(anteprimaRis.get(0).contains("`order`") && anteprimaRis.get(0).contains("`select`"),
                        anteprimaRis.get(0));
                onEdt(riservate::apply);
                a.awaitLastProposal();
                a.waitIdle();
                assertTrue(server.tableExists(catalog, "ordine dettagli"), "la tabella con lo spazio nel nome esiste");
                assertEquals("2", server.scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                        + catalog + "' AND TABLE_NAME = 'ordine dettagli' AND COLUMN_NAME IN ('order','select')"),
                        "le colonne «order» e «select» sono sul server");
                // ci si può anche scrivere dentro
                server.run("INSERT INTO `" + catalog + "`.`ordine dettagli` (`order`) VALUES (7)");
                assertEquals("7", server.scalar("SELECT `order` FROM `" + catalog + "`.`ordine dettagli`"));
                assertEquals("nuovo", server.scalar("SELECT `select` FROM `" + catalog + "`.`ordine dettagli`"),
                        "il DEFAULT è arrivato al server");
                Probe.paintWindow("step5", a.frame(), "T5.8-server-" + server.id() + ".png");
                ev.append("T5.8 — «ordine dettagli» con colonne «order» e «select»: creata sul server;\n  ")
                        .append(anteprimaRis.get(0).replace('\n', ' ')).append("\n  riga di prova scritta: order=7, ")
                        .append("select='nuovo' (DEFAULT)\nEsito: SUPERATO\n");
            }
        } finally {
            Probe.writeText("step5", "T5.4-T5.8-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }
}
