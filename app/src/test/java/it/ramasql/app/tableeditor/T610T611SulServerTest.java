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

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.editor.SqlEditor;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.grid.GridTestSupport;
import it.ramasql.app.servertest.ClientApp;
import it.ramasql.app.servertest.DbServer;
import it.ramasql.app.servertest.Probe;
import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * <b>T6.10</b> e <b>T6.11</b> (Tappa M1) <b>sul server vero</b>.
 *
 * <p>T6.10: dal data-entry si prova a eliminare un editore che ha libri (chiave esterna RESTRICT) → errore <b>1451</b>
 * spiegato e la riga resta; poi si elimina un libro che ha autori (CASCADE) → le righe di {@code libri_autori}
 * spariscono, e si vede riaprendo la tabella.
 * <p>T6.11: la {@code biblioteca} si costruisce <b>solo con il client</b> — catalogo dal navigatore, tabelle, indici e
 * chiavi esterne dall'editor di struttura, dati dal data-entry (digitati e incollati a blocchi come da Excel) — poi si
 * <b>esporta il registro</b> come {@code .sql} e lo si <b>riesegue</b> dall'editor SQL su un secondo catalogo vuoto: i
 * due cataloghi risultano identici (metadati tabella per tabella e {@code CHECKSUM TABLE}).
 */
@Tag("step6")
@Tag("ui")
@Tag("it")
class T610T611SulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    // ================================================================ T6.10

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t610_dalDataEntryRestrictERifiutatoCascadePropaga(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("integr10");
        StringBuilder ev = new StringBuilder("T6.10 — integrità dal data-entry su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            ev.append("Catalogo di test: ").append(catalog).append(" (fixture biblioteca.sql)\n");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();

                // ---------- RESTRICT: un editore che ha libri non si elimina
                String editoreConLibri = server.scalar("SELECT id_editore FROM `" + catalog
                        + "`.`libri` WHERE id_editore IS NOT NULL GROUP BY id_editore ORDER BY COUNT(*) DESC LIMIT 1");
                TableDef editori = a.workspace().reader().table(catalog, "editori").orElseThrow();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, editori));
                a.waitIdle();
                int riga = rigaCon(grid, 0, editoreConLibri);
                assertTrue(riga >= 0, "l'editore " + editoreConLibri + " è nella prima pagina");
                long idRiga = fromEdt(() -> grid.model().pending().rowId(riga));
                onEdt(() -> grid.selectRows(riga, riga));
                GridTestSupport.action(grid, DataGrid.ACTION_DELETE_ROWS);
                onEdt(grid::confirm);
                a.awaitLastProposal();
                a.waitIdle();

                int dopo = fromEdt(() -> grid.model().pending().indexOf(idRiga));
                assertTrue(dopo >= 0, "la riga è ancora in griglia");
                String errore = fromEdt(() -> grid.model().pending().errorMessage(dopo).orElse(""));
                assertTrue(errore.contains("1451"), "errore del server: " + errore);
                assertTrue(errore.contains("chiave esterna"), "spiegazione in italiano: " + errore);
                assertEquals("1", server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.`editori` WHERE id = "
                        + editoreConLibri), "sul server l'editore c'è ancora");
                Probe.paintWindow("step6", a.frame(), "T6.10-restrict-" + server.id() + ".png");
                ev.append("RESTRICT — eliminare l'editore ").append(editoreConLibri).append(" (che ha ")
                        .append(server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.`libri` WHERE id_editore = "
                                + editoreConLibri)).append(" libri): «").append(errore)
                        .append("»; sul server l'editore è ancora lì\n");
                onEdt(() -> a.frame().tabs().close(grid));

                // ---------- CASCADE: eliminando un libro sparisce la riga di libri_autori
                String libroConAutori = server.scalar("SELECT id_libro FROM `" + catalog
                        + "`.`libri_autori` GROUP BY id_libro ORDER BY COUNT(*) DESC LIMIT 1");
                long legamiPrima = Long.parseLong(server.scalar("SELECT COUNT(*) FROM `" + catalog
                        + "`.`libri_autori` WHERE id_libro = " + libroConAutori));
                assertTrue(legamiPrima > 0, "il libro scelto ha autori");
                // i prestiti riferiscono i libri con RESTRICT: si toglie prima quel legame, dal client
                server.run("DELETE FROM `" + catalog + "`.`prestiti` WHERE id_libro = " + libroConAutori);
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                DataGrid griglia = fromEdt(() -> a.frame().openDataEntry(catalog, libri));
                a.waitIdle();
                int rigaLibro = rigaCon(griglia, 0, libroConAutori);
                assertTrue(rigaLibro >= 0, "il libro " + libroConAutori + " è nella prima pagina");
                onEdt(() -> griglia.selectRows(rigaLibro, rigaLibro));
                GridTestSupport.action(griglia, DataGrid.ACTION_DELETE_ROWS);
                onEdt(griglia::confirm);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("0", server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.`libri` WHERE id = "
                        + libroConAutori), "il libro è stato eliminato");
                assertEquals("0", server.scalar("SELECT COUNT(*) FROM `" + catalog
                        + "`.`libri_autori` WHERE id_libro = " + libroConAutori),
                        "le righe di libri_autori sono sparite con il CASCADE");

                // e si vede riaprendo la tabella nel client
                TableDef legami = a.workspace().reader().table(catalog, "libri_autori").orElseThrow();
                DataGrid grigliaLegami = fromEdt(() -> a.frame().openDataEntry(catalog, legami));
                a.waitIdle();
                assertEquals(-1, rigaCon(grigliaLegami, 0, libroConAutori),
                        "riaprendo libri_autori il libro eliminato non c'è più");
                Probe.paintWindow("step6", a.frame(), "T6.10-cascade-" + server.id() + ".png");
                ev.append("CASCADE — eliminato il libro ").append(libroConAutori).append(": le ")
                        .append(legamiPrima).append(" righe di libri_autori sono sparite sul server e non ")
                        .append("compaiono più riaprendo la tabella nel client\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            // un test fallito non deve lasciare un file di evidenza che sembra valido
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step6", "T6.10-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ================================================================ T6.11 — Tappa M1

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t611_tappaM1BibliotecaCostruitaSoloConIlClientERiesguita(DbServer server) throws Exception {
        String origine = DbServer.newCatalogName("m1a");
        String copia = DbServer.newCatalogName("m1b");
        StringBuilder ev = new StringBuilder("T6.11 — Tappa M1 su " + server.label() + "\n");
        try {
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                a.ws.tableEditorConfirmAnswer = true;

                // ---------- 1) il catalogo, dal navigatore
                a.ws.onNewCatalog = d -> {
                    d.nameField().setText(origine);
                    d.buttons().confirmButton().doClick();
                };
                onEdt(() -> a.nav().newCatalog());
                a.awaitLastProposal();
                a.waitIdle();
                assertTrue(server.catalogExists(origine), "il catalogo è stato creato dal client");

                // ---------- 2) le tabelle, con indici e chiavi esterne, dall'editor di struttura
                creaEditori(a, origine);
                creaAutori(a, origine);
                creaLibri(a, origine);
                creaLibriAutori(a, origine);
                for (String t : List.of("editori", "autori", "libri", "libri_autori")) {
                    assertTrue(server.tableExists(origine, t), "tabella creata dal client: " + t);
                }
                assertEquals("3", server.scalar("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS"
                        + " WHERE CONSTRAINT_SCHEMA = '" + origine + "'"), "3 chiavi esterne create dal client");

                // ---------- 3) i dati, dal data-entry: digitati e incollati a blocchi
                TableDef editori = a.workspace().reader().table(origine, "editori").orElseThrow();
                DataGrid gEditori = fromEdt(() -> a.frame().openDataEntry(origine, editori));
                a.waitIdle();
                digita(gEditori, List.of(List.of("Einaudi", "Torino"), List.of("Adelphi", "Milano"),
                        List.of("Sellerio", "Palermo")));
                onEdt(gEditori::confirm);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("3", server.scalar("SELECT COUNT(*) FROM `" + origine + "`.`editori`"));

                TableDef autori = a.workspace().reader().table(origine, "autori").orElseThrow();
                DataGrid gAutori = fromEdt(() -> a.frame().openDataEntry(origine, autori));
                a.waitIdle();
                Clipboard appunti = new Clipboard("m1");
                appunti.setContents(new StringSelection(bloccoAutori()), null);
                onEdt(() -> gAutori.setClipboard(appunti));
                int nuova = fromEdt(() -> gAutori.model().pending().rowCount());
                onEdt(() -> gAutori.selectBlock(nuova, 1, nuova, 1));
                GridTestSupport.action(gAutori, DataGrid.ACTION_PASTE);
                onEdt(gAutori::confirm);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("20", server.scalar("SELECT COUNT(*) FROM `" + origine + "`.`autori`"),
                        "20 autori incollati a blocco");

                TableDef libri = a.workspace().reader().table(origine, "libri").orElseThrow();
                DataGrid gLibri = fromEdt(() -> a.frame().openDataEntry(origine, libri));
                a.waitIdle();
                digita(gLibri, List.of(List.of("Se questo è un uomo", "9788806001", "1"),
                        List.of("Il deserto dei Tartari", "9788845902", "2"),
                        List.of("Il birraio di Preston", "9788838903", "3")));
                onEdt(gLibri::confirm);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("3", server.scalar("SELECT COUNT(*) FROM `" + origine + "`.`libri`"));

                // ---------- 4) il registro esportato come .sql
                String script = fromEdt(() -> a.log().exportScript(SqlLog.ExportOptions.withoutCatalog(origine)));
                Path file = Probe.resultsDir("step6").resolve("T6.11-registro-" + server.id() + ".sql");
                java.nio.file.Files.writeString(file, script, java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(script.contains("CREATE TABLE"), "lo script contiene le tabelle");
                assertTrue(script.contains("INSERT INTO"), "e i dati");

                // ---------- 5) il secondo catalogo e la riesecuzione, dall'editor SQL del client
                a.ws.onNewCatalog = d -> {
                    d.nameField().setText(copia);
                    d.buttons().confirmButton().doClick();
                };
                onEdt(() -> a.nav().newCatalog());
                a.awaitLastProposal();
                a.waitIdle();
                assertTrue(server.catalogExists(copia), "il secondo catalogo è stato creato dal client");

                SqlEditor editor = fromEdt(a.frame()::openSqlEditor);
                String daEseguire = "USE `" + copia + "`;\n" + script;
                onEdt(() -> editor.setText(daEseguire));
                onEdt(editor::runAll);
                Probe.waitUntil("riesecuzione del registro", 120_000, () -> !fromEdt(editor::isRunning));
                a.waitIdle();
                var esito = a.workspace().pipeline().lastProposal().get();
                assertNotNull(esito, "la riesecuzione è partita");
                assertTrue(esito.completed(), () -> "riesecuzione: "
                        + esito.failure().map(f -> f.statement().text() + " → " + f.error()).orElse("?"));

                // ---------- 6) i due cataloghi sono identici
                List<String> confronti = new ArrayList<>();
                for (String t : List.of("editori", "autori", "libri", "libri_autori")) {
                    TableDef da = a.workspace().reader().table(origine, t).orElseThrow();
                    TableDef a2 = a.workspace().reader().table(copia, t).orElseThrow();
                    List<String> differenze = TableDiff.diff(a2, da.withCatalog(copia),
                            a.workspace().session().serverInfo());
                    assertEquals(List.of(), differenze, "metadati diversi per " + t + ": " + differenze);
                    String c1 = server.rows("CHECKSUM TABLE `" + origine + "`.`" + t + "`").get(0).get(1);
                    String c2 = server.rows("CHECKSUM TABLE `" + copia + "`.`" + t + "`").get(0).get(1);
                    assertEquals(c1, c2, "CHECKSUM diverso per " + t);
                    confronti.add(t + ": metadati uguali, CHECKSUM " + c1);
                }
                Probe.paintWindow("step6", a.frame(), "T6.11-server-" + server.id() + ".png");
                ev.append("1) catalogo ").append(origine).append(" creato dal navigatore\n")
                        .append("2) 4 tabelle con 3 chiavi esterne e gli indici, dall'editor di struttura\n")
                        .append("3) dati dal data-entry: 3 editori e 3 libri digitati, 20 autori incollati a blocco\n")
                        .append("4) registro esportato: ").append(script.lines().count())
                        .append(" righe → ").append(file.getFileName()).append('\n')
                        .append("5) catalogo ").append(copia).append(" creato dal client e registro rieseguito ")
                        .append("dall'editor SQL: ").append(esito.results().size()).append(" istruzioni, tutte OK\n")
                        .append("6) confronto fra i due cataloghi:\n   ").append(String.join("\n   ", confronti))
                        .append("\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            // un test fallito non deve lasciare un file di evidenza che sembra valido
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step6", "T6.11-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(origine);
            server.dropQuietly(copia);
        }
    }

    // ---------------------------------------------------------------- attrezzi

    /** Scrive righe nuove nella riga d'inserimento, una cella per colonna a partire dalla 1 (la 0 è l'id AI). */
    private static void digita(DataGrid grid, List<List<String>> righe) throws Exception {
        for (List<String> riga : righe) {
            int nuova = fromEdt(() -> grid.model().pending().rowCount());
            for (int c = 0; c < riga.size(); c++) {
                GridTestSupport.type(grid, nuova, c + 1, riga.get(c));
            }
        }
    }

    private static String bloccoAutori() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("Autore").append(String.format("%02d", i)).append('\t')
                    .append(i % 3 == 0 ? "Nicolò" : "Nome " + i).append('\n');
        }
        return sb.toString();
    }

    private static int rigaCon(DataGrid grid, int column, String value) throws Exception {
        return fromEdt(() -> {
            PendingChanges p = grid.model().pending();
            for (int r = 0; r < p.rowCount(); r++) {
                if (value.equals(p.value(r, column)) && p.kind(r) != PendingChanges.RowKind.DELETED) {
                    return r;
                }
            }
            return -1;
        });
    }

    private static void creaEditori(ClientApp a, String catalog) throws Exception {
        TableEditor e = fromEdt(() -> a.frame().openTableEditor(catalog, null));
        onEdt(() -> {
            e.optionsTab().setTableName("editori");
            ColumnsTab c = e.columnsTab();
            c.setCell(0, ColumnsTab.UN, true);
            c.addColumn();
            c.setCell(1, ColumnsTab.NAME, "nome");
            c.setCell(1, ColumnsTab.TYPE, "VARCHAR(80)");
            c.setCell(1, ColumnsTab.NN, true);
            c.addColumn();
            c.setCell(2, ColumnsTab.NAME, "citta");
            c.setCell(2, ColumnsTab.TYPE, "VARCHAR(60)");
            IndexesTab idx = e.indexesTab();
            idx.addIndex();
            idx.select(1);
            idx.setCell(1, IndexesTab.NAME, "uq_editori_nome");
            idx.setCell(1, IndexesTab.KIND, IndexKind.UNIQUE);
            idx.addColumn("nome");
        });
        applica(a, e);
    }

    private static void creaAutori(ClientApp a, String catalog) throws Exception {
        TableEditor e = fromEdt(() -> a.frame().openTableEditor(catalog, null));
        onEdt(() -> {
            e.optionsTab().setTableName("autori");
            ColumnsTab c = e.columnsTab();
            c.setCell(0, ColumnsTab.UN, true);
            c.addColumn();
            c.setCell(1, ColumnsTab.NAME, "cognome");
            c.setCell(1, ColumnsTab.TYPE, "VARCHAR(60)");
            c.setCell(1, ColumnsTab.NN, true);
            c.addColumn();
            c.setCell(2, ColumnsTab.NAME, "nome");
            c.setCell(2, ColumnsTab.TYPE, "VARCHAR(60)");
        });
        applica(a, e);
    }

    private static void creaLibri(ClientApp a, String catalog) throws Exception {
        TableEditor e = fromEdt(() -> a.frame().openTableEditor(catalog, null));
        onEdt(() -> {
            e.optionsTab().setTableName("libri");
            ColumnsTab c = e.columnsTab();
            c.setCell(0, ColumnsTab.UN, true);
            c.addColumn();
            c.setCell(1, ColumnsTab.NAME, "titolo");
            c.setCell(1, ColumnsTab.TYPE, "VARCHAR(150)");
            c.setCell(1, ColumnsTab.NN, true);
            c.addColumn();
            c.setCell(2, ColumnsTab.NAME, "isbn");
            c.setCell(2, ColumnsTab.TYPE, "CHAR(10)");
            c.addColumn();
            c.setCell(3, ColumnsTab.NAME, "id_editore");
            c.setCell(3, ColumnsTab.TYPE, "INT");
            c.setCell(3, ColumnsTab.UN, true);
            IndexesTab idx = e.indexesTab();
            idx.addIndex();
            idx.select(1);
            idx.setCell(1, IndexesTab.NAME, "uq_libri_isbn");
            idx.setCell(1, IndexesTab.KIND, IndexKind.UNIQUE);
            idx.addColumn("isbn");
            ForeignKeysTab fks = e.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_libri_editori");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "editori");
            fks.setCell(0, ForeignKeysTab.ON_DELETE, "RESTRICT");
        });
        applica(a, e);
    }

    private static void creaLibriAutori(ClientApp a, String catalog) throws Exception {
        TableEditor e = fromEdt(() -> a.frame().openTableEditor(catalog, null));
        onEdt(() -> {
            e.optionsTab().setTableName("libri_autori");
            ColumnsTab c = e.columnsTab();
            c.setCell(0, ColumnsTab.NAME, "id_libro");
            c.setCell(0, ColumnsTab.TYPE, "INT");
            c.setCell(0, ColumnsTab.UN, true);
            c.setCell(0, ColumnsTab.AI, false);
            c.addColumn();
            c.setCell(1, ColumnsTab.NAME, "id_autore");
            c.setCell(1, ColumnsTab.TYPE, "INT");
            c.setCell(1, ColumnsTab.UN, true);
            c.setCell(1, ColumnsTab.NN, true);
            c.setCell(1, ColumnsTab.PK, true);
            ForeignKeysTab fks = e.foreignKeysTab();
            fks.addForeignKey();
            fks.setCell(0, ForeignKeysTab.NAME, "fk_libri_autori_libri");
            fks.setCell(0, ForeignKeysTab.REF_TABLE, "libri");
            fks.select(0);
            fks.addPair("id_libro", "id");
            fks.setCell(0, ForeignKeysTab.ON_DELETE, "CASCADE");
            fks.addForeignKey();
            fks.setCell(1, ForeignKeysTab.NAME, "fk_libri_autori_autori");
            fks.setCell(1, ForeignKeysTab.REF_TABLE, "autori");
            fks.select(1);
            fks.addPair("id_autore", "id");
            fks.setCell(1, ForeignKeysTab.ON_DELETE, "CASCADE");
        });
        applica(a, e);
    }

    private static void applica(ClientApp a, TableEditor e) throws Exception {
        onEdt(e::apply);
        a.awaitLastProposal();
        a.waitIdle();
        String esito = fromEdt(e::outcomeText);
        assertTrue(esito.contains("verificato sul server") || esito.contains("applicata"),
                "creazione riuscita: " + esito.replace('\n', ' ') + " | note: "
                + fromEdt(() -> e.foreignKeysTab().notesText() + " / " + e.columnsTab().notesText())
                + " | piano: " + fromEdt(e::previewStatements));
        onEdt(() -> a.frame().tabs().close(e));
    }
}
