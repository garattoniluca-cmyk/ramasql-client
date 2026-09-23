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

import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.servertest.ClientApp;
import it.ramasql.app.servertest.DbServer;
import it.ramasql.app.servertest.Probe;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T6.6</b> e <b>T6.7</b> <b>sul server vero</b>.
 *
 * <p>T6.6: partendo da tabelle <b>nude</b> (nessun indice secondario, nessuna chiave esterna) si costruiscono indici e
 * chiavi esterne con l'editor; dopo ogni <em>Esegui</em> compare «✔ verificato sul server» — e la verifica è vera: il
 * client rilegge {@code information_schema} e confronta con ciò che era stato chiesto. Indici e chiavi esterne
 * compaiono poi nel navigatore.
 * <p>T6.7: chiave esterna {@code prestiti.id_socio → soci.id} con <b>3 righe orfane</b>: «Verifica dati» le elenca con
 * la query usata — questo vale su entrambi i server. Eseguendo comunque, i due server <b>non</b> si comportano allo
 * stesso modo ({@code BUG-018}): MySQL rifiuta con <b>1452</b> spiegato, MariaDB crea la chiave e lascia lì le righe
 * orfane. Il test asserisce ciò che è vero su ciascuno e, dopo aver corretto le righe dal data-entry, verifica su
 * entrambi che la chiave si crei e risulti verificata sul server.
 */
@Tag("step6")
@Tag("ui")
@Tag("it")
class T66T67SulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t66_t67_indiciEChiaviEsterneVerificateSulServer(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("integrita");
        StringBuilder ev = new StringBuilder("T6.6 e T6.7 — indici e chiavi esterne su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            nude(server, catalog);
            ev.append("Catalogo di test: ").append(catalog)
                    .append(" — tabelle nude (solo PK, nessun indice secondario, nessuna FK)\n");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                a.ws.tableEditorConfirmAnswer = true;

                // ---------------------------------------------------------------- T6.6
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                TableEditor editor = fromEdt(() -> a.frame().openTableEditor(catalog, libri));
                onEdt(() -> {
                    IndexesTab idx = editor.indexesTab();
                    idx.addIndex();
                    idx.select(1);
                    idx.setCell(1, IndexesTab.NAME, "uq_libri_isbn");
                    idx.setCell(1, IndexesTab.KIND, IndexKind.UNIQUE);
                    idx.addColumn("isbn");
                    idx.addIndex();
                    idx.select(2);
                    idx.setCell(2, IndexesTab.NAME, "ix_libri_editore");
                    idx.setCell(2, IndexesTab.KIND, IndexKind.INDEX);
                    idx.addColumn("id_editore");
                    ForeignKeysTab fks = editor.foreignKeysTab();
                    fks.addForeignKey();
                    fks.setCell(0, ForeignKeysTab.NAME, "fk_libri_editori");
                    fks.setCell(0, ForeignKeysTab.REF_TABLE, "editori");
                    fks.setCell(0, ForeignKeysTab.ON_DELETE, "RESTRICT");
                    fks.setCell(0, ForeignKeysTab.ON_UPDATE, "CASCADE");
                });
                List<String> piano = fromEdt(editor::previewStatements);
                onEdt(editor::apply);
                a.awaitLastProposal();
                a.waitIdle();
                String esito = fromEdt(editor::outcomeText);
                assertTrue(esito.contains("verificato sul server"), "la verifica dopo è dichiarata: " + esito);

                // la verifica è vera: information_schema dice la stessa cosa
                assertEquals("1", server.scalar("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE"
                        + " TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = 'libri'"
                        + " AND INDEX_NAME = 'uq_libri_isbn' AND NON_UNIQUE = 0"), "UNIQUE su isbn creato");
                assertEquals("1", server.scalar("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE"
                        + " TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = 'libri'"
                        + " AND INDEX_NAME = 'ix_libri_editore' AND NON_UNIQUE = 1"), "indice su id_editore creato");
                List<List<String>> fk = server.rows("SELECT r.CONSTRAINT_NAME, r.REFERENCED_TABLE_NAME,"
                        + " r.DELETE_RULE, r.UPDATE_RULE, k.COLUMN_NAME, k.REFERENCED_COLUMN_NAME"
                        + " FROM information_schema.REFERENTIAL_CONSTRAINTS r"
                        + " JOIN information_schema.KEY_COLUMN_USAGE k ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA"
                        + " AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME"
                        + " WHERE r.CONSTRAINT_SCHEMA = '" + catalog + "' AND r.TABLE_NAME = 'libri'");
                assertEquals(1, fk.size(), "una chiave esterna");
                assertEquals(List.of("fk_libri_editori", "editori", "RESTRICT", "CASCADE", "id_editore", "id"),
                        fk.get(0), "nome, tabella riferita, azioni e colonne come chiesto");

                // nel navigatore compaiono indici e chiavi esterne
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                a.expand(NavNode.Kind.TABLES, catalog, null);
                var nodoLibri = a.expand(NavNode.Kind.TABLE, catalog, "libri");
                var gruppoIndici = fromEdt(() -> a.nav().findChild(nodoLibri, NavNode.Kind.INDEXES));
                assertNotNull(gruppoIndici, "il gruppo Indici c'è sotto libri");
                a.expandPath(gruppoIndici);
                List<String> indiciNellAlbero = fromEdt(() -> a.nav().childrenOf(gruppoIndici).stream()
                        .map(NavNode::name).sorted().toList());
                assertTrue(indiciNellAlbero.containsAll(List.of("ix_libri_editore", "uq_libri_isbn")),
                        "gli indici creati si vedono nel navigatore: " + indiciNellAlbero);
                var gruppoFk = fromEdt(() -> a.nav().findChild(nodoLibri, NavNode.Kind.FOREIGN_KEYS));
                assertNotNull(gruppoFk, "il gruppo Chiavi esterne c'è sotto libri");
                a.expandPath(gruppoFk);
                List<String> fkNellAlbero = fromEdt(() -> a.nav().childrenOf(gruppoFk).stream()
                        .map(NavNode::name).toList());
                assertTrue(fkNellAlbero.contains("fk_libri_editori"),
                        "la chiave esterna si vede nel navigatore: " + fkNellAlbero);
                Probe.paintWindow("step6", a.frame(), "T6.6-server-" + server.id() + ".png");
                ev.append("T6.6 — piano applicato:\n  ").append(String.join(";\n  ", piano)).append(";\n")
                        .append("  esito: ").append(esito.replace('\n', ' ')).append('\n')
                        .append("  navigatore: indici ").append(indiciNellAlbero)
                        .append(", chiavi esterne ").append(fkNellAlbero).append('\n')
                        .append("  information_schema: uq_libri_isbn (UNIQUE), ix_libri_editore (INDEX), ")
                        .append("fk_libri_editori → editori(id) ON DELETE RESTRICT ON UPDATE CASCADE\n");
                onEdt(() -> a.frame().tabs().close(editor));

                // ---------------------------------------------------------------- T6.7
                // 3 prestiti con un socio che non esiste
                server.run("INSERT INTO `" + catalog + "`.`prestiti` (id_libro, id_socio, data_prestito) VALUES"
                        + " (1, 901, '2026-03-02'), (1, 902, '2026-03-05'), (1, 903, '2026-04-11')");
                TableDef prestiti = a.workspace().reader().table(catalog, "prestiti").orElseThrow();
                TableEditor conFk = fromEdt(() -> a.frame().openTableEditor(catalog, prestiti));
                onEdt(() -> {
                    ForeignKeysTab fks = conFk.foreignKeysTab();
                    fks.addForeignKey();
                    fks.setCell(0, ForeignKeysTab.NAME, "fk_prestiti_soci");
                    fks.setCell(0, ForeignKeysTab.REF_TABLE, "soci");
                });
                onEdt(() -> conFk.foreignKeysTab().select(0));
                onEdt(() -> conFk.foreignKeysTab().verifySelectedData());
                a.awaitLastProposal();
                a.waitIdle();
                DataCheckPanel panel = fromEdt(() -> conFk.foreignKeysTab().dataCheck());
                String query = fromEdt(panel::queryText);
                assertEquals(3, (int) fromEdt(panel::rowCount), "le 3 righe orfane sono elencate: " + query);
                String stato = fromEdt(panel::statusText);
                assertTrue(stato.contains("3") && stato.contains("orfane"), stato);
                assertTrue(stato.contains("1452"), "e dice che il server rifiuterebbe con 1452: " + stato);
                assertTrue(query.contains("LEFT JOIN") && query.contains("IS NULL"), "la query usata: " + query);
                Probe.paintWindow("step6", a.frame(), "T6.7-orfane-server-" + server.id() + ".png");

                // si esegue comunque: qui i due server non si comportano allo stesso modo (BUG-018)
                onEdt(conFk::apply);
                a.awaitLastProposal();
                a.waitIdle();
                String rifiutata = fromEdt(conFk::outcomeText);
                boolean creataLoStesso = "1".equals(server.scalar("SELECT COUNT(*) FROM"
                        + " information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '" + catalog
                        + "' AND TABLE_NAME = 'prestiti' AND CONSTRAINT_NAME = 'fk_prestiti_soci'"));
                ev.append("T6.7 - query di verifica: ").append(query).append('\n')
                        .append("  esito della verifica: ").append(stato).append('\n')
                        .append("  eseguita comunque su ").append(server.label()).append(": ")
                        .append(rifiutata.replace('\n', ' ')).append('\n');
                // su entrambi i server, comunque sia andata, l'utente era stato avvisato PRIMA
                assertTrue(stato.contains("orfane"), "il controllo preventivo aveva avvisato: " + stato);
                if (creataLoStesso) {
                    // MariaDB non controlla le righe esistenti quando si aggiunge una chiave esterna: la crea, e le
                    // righe orfane restano. È il controllo PRIMA del client a proteggere l'utente (ADR-011).
                    assertEquals("3", server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.`prestiti`"
                            + " WHERE id_socio IN (901, 902, 903)"),
                            "il server ha accettato la chiave esterna e le righe orfane sono ancora lì");
                    ev.append("  ATTENZIONE: ").append(server.label()).append(" ha accettato la chiave esterna")
                            .append(" nonostante le 3 righe orfane, che restano sul server (BUG-018): qui l'errore")
                            .append(" 1452 non arriva, e l'unica difesa è il controllo Verifica dati.")
                            .append('\n');
                    // si torna indietro dal client, per provare il seguito come sull'altro server
                    onEdt(() -> {
                        conFk.foreignKeysTab().select(0);
                        conFk.foreignKeysTab().removeSelected();
                    });
                    onEdt(conFk::apply);
                    a.awaitLastProposal();
                    a.waitIdle();
                    assertEquals("0", server.scalar("SELECT COUNT(*) FROM"
                            + " information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '" + catalog
                            + "' AND TABLE_NAME = 'prestiti'"), "chiave esterna tolta dal client");
                } else {
                    assertTrue(rifiutata.contains("1452"), "errore del server: " + rifiutata);
                    assertTrue(rifiutata.contains("chiave esterna"), "spiegazione in italiano: " + rifiutata);
                    ev.append("  il server ha rifiutato con 1452, come chiede la roadmap; nessuna chiave creata")
                            .append('\n');
                }

                // si correggono le righe dal data-entry del client, poi la chiave si crea
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, prestiti));
                a.waitIdle();
                int eliminate = 0;
                for (String orfano : List.of("901", "902", "903")) {
                    int riga = rigaCon(grid, 2, orfano);
                    assertTrue(riga >= 0, "la riga con id_socio " + orfano + " è in griglia");
                    int r = riga;
                    onEdt(() -> grid.selectRows(r, r));
                    it.ramasql.app.grid.GridTestSupport.action(grid, DataGrid.ACTION_DELETE_ROWS);
                    eliminate++;
                }
                assertEquals(3, eliminate);
                onEdt(grid::confirm);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("0", server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.`prestiti`"
                        + " WHERE id_socio IN (901, 902, 903)"), "le righe orfane non ci sono più");

                onEdt(() -> {
                    if (conFk.editedTable().foreignKeys().isEmpty()) {
                        ForeignKeysTab fks = conFk.foreignKeysTab();
                        fks.addForeignKey();
                        fks.setCell(0, ForeignKeysTab.NAME, "fk_prestiti_soci");
                        fks.setCell(0, ForeignKeysTab.REF_TABLE, "soci");
                    }
                });
                onEdt(conFk::apply);
                a.awaitLastProposal();
                a.waitIdle();
                String riuscita = fromEdt(conFk::outcomeText);
                assertTrue(riuscita.contains("verificato sul server"), "ora è verificata; esito: "
                        + riuscita.replace('\n', ' ') + " | piano: " + fromEdt(conFk::previewStatements));
                assertEquals("1", server.scalar("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS"
                        + " WHERE CONSTRAINT_SCHEMA = '" + catalog + "' AND TABLE_NAME = 'prestiti'"
                        + " AND CONSTRAINT_NAME = 'fk_prestiti_soci'"), "la chiave esterna adesso c'è");
                Probe.paintWindow("step6", a.frame(), "T6.7-creata-server-" + server.id() + ".png");
                ev.append("  corrette dal data-entry (3 righe eliminate con Conferma) → ")
                        .append(riuscita.replace('\n', ' ')).append("\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            // un test fallito non deve lasciare un file di evidenza che sembra valido
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step6", "T6.6-T6.7-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    /** L'indice della riga della griglia che ha quel valore nella colonna indicata; -1 se non c'è. */
    private static int rigaCon(DataGrid grid, int column, String value) throws Exception {
        return fromEdt(() -> {
            var p = grid.model().pending();
            for (int r = 0; r < p.rowCount(); r++) {
                if (value.equals(p.value(r, column)) && p.kind(r) != it.ramasql.core.data.PendingChanges
                        .RowKind.DELETED) {
                    return r;
                }
            }
            return -1;
        });
    }

    /** Le tabelle della biblioteca senza indici secondari e senza chiavi esterne: il punto di partenza di T6.6. */
    private static void nude(DbServer server, String catalog) throws Exception {
        server.run("CREATE TABLE `" + catalog + "`.`editori` (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                + " nome VARCHAR(80) NOT NULL, citta VARCHAR(60) NULL, PRIMARY KEY (id))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        server.run("CREATE TABLE `" + catalog + "`.`libri` (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                + " titolo VARCHAR(150) NOT NULL, isbn CHAR(13) NULL, id_editore INT UNSIGNED NULL,"
                + " PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        server.run("CREATE TABLE `" + catalog + "`.`soci` (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                + " tessera CHAR(8) NOT NULL, cognome VARCHAR(60) NOT NULL, email VARCHAR(120) NULL,"
                + " PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        server.run("CREATE TABLE `" + catalog + "`.`prestiti` (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                + " id_libro INT UNSIGNED NOT NULL, id_socio INT UNSIGNED NOT NULL, data_prestito DATE NOT NULL,"
                + " PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        server.run("INSERT INTO `" + catalog + "`.`editori` (nome, citta) VALUES ('Einaudi', 'Torino')");
        server.run("INSERT INTO `" + catalog + "`.`libri` (titolo, isbn, id_editore) VALUES"
                + " ('Se questo è un uomo', '9788806219', 1)");
        server.run("INSERT INTO `" + catalog + "`.`soci` (tessera, cognome, email) VALUES ('T100001', 'Rossi', NULL)");
    }
}
