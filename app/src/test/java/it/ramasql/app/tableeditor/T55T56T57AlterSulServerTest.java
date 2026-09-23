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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.navigator.NavigatorIcons;
import it.ramasql.app.servertest.ClientApp;
import it.ramasql.app.servertest.DbServer;
import it.ramasql.app.servertest.Probe;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T5.5</b>, <b>T5.6</b> e <b>T5.7</b> <b>sul server vero</b>, dall'editor di struttura del programma.
 *
 * <p>T5.5: restringere {@code titolo} a {@code VARCHAR(20)} con righe più lunghe → avviso <b>prima</b> di eseguire e
 * conferma esplicita; se si rifiuta non parte nulla; se si insiste è il <b>server</b> a rifiutare e l'errore arriva
 * spiegato, con la colonna intatta.
 * <p>T5.6: una sequenza in cui un'istruzione <b>fallisce davvero</b> → l'esito dice cosa è stato applicato e cosa no, e
 * l'editor <b>ricarica lo stato reale</b> dal server.
 * <p>T5.7: conversione a MyISAM <b>bloccata</b> per una tabella riferita da una chiave esterna (controllo locale, senza
 * contattare il server) ed <b>eseguita</b> per una tabella libera, con l'icona del navigatore che segue l'engine.
 */
@Tag("step5")
@Tag("ui")
@Tag("it")
class T55T56T57AlterSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t55_t56_t57_alterSulServerConEsitiParzialiEEngine(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("alter5");
        StringBuilder ev = new StringBuilder("T5.5, T5.6, T5.7 — editor di struttura sul server "
                + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            server.run("CREATE TABLE `" + catalog + "`.`note_libere` (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + " testo VARCHAR(80) NULL, PRIMARY KEY (id))"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            ev.append("Catalogo di test: ").append(catalog).append(" (fixture biblioteca.sql + note_libere)\n");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                String piuLungo = server.scalar("SELECT MAX(CHAR_LENGTH(titolo)) FROM `" + catalog + "`.`libri`");
                assertTrue(Integer.parseInt(piuLungo) > 20,
                        "nella fixture c'è almeno un titolo più lungo di 20 caratteri: " + piuLungo);

                // ---------------------------------------------------------------- T5.5
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                String lunghezzaPrima = lunghezzaSulServer(server, catalog, "libri", "titolo");
                TableEditor editor = fromEdt(() -> a.frame().openTableEditor(catalog, libri));
                onEdt(() -> editor.columnsTab().setCell(riga(editor, "titolo"), ColumnsTab.ARGS, "20"));
                String note = fromEdt(() -> editor.columnsTab().notesText());
                assertTrue(note.contains("⚠") && note.contains("titolo"),
                        "l'avviso di troncamento c'è prima di eseguire: " + note);

                // conferma rifiutata: non parte niente
                a.ws.tableEditorConfirmAnswer = false;
                int registroPrima = fromEdt(() -> a.log().size());
                onEdt(editor::apply);
                a.waitIdle();
                assertEquals(registroPrima, fromEdt(() -> a.log().size()), "conferma rifiutata: 0 istruzioni");
                String confermaChiesta = a.ws.messages.getLast();
                assertTrue(confermaChiesta.contains("troncat"), "la conferma nomina il rischio: " + confermaChiesta);
                Probe.paintWindow("step5", a.frame(), "T5.5-avviso-server-" + server.id() + ".png");

                // conferma accettata: è il server a rifiutare
                a.ws.tableEditorConfirmAnswer = true;
                onEdt(editor::apply);
                a.awaitLastProposal();
                a.waitIdle();
                String esito = fromEdt(editor::outcomeText);
                assertTrue(esito.contains("1265") || esito.contains("1406"),
                        "l'errore del server è mostrato col suo codice: " + esito);
                assertTrue(esito.contains("troppo lungo") || esito.contains("non entra"),
                        "con la spiegazione in italiano: " + esito);
                assertEquals(lunghezzaPrima, lunghezzaSulServer(server, catalog, "libri", "titolo"),
                        "sul server la colonna è rimasta com'era");
                assertEquals(lunghezzaPrima, fromEdt(() -> editor.editedTable().column("titolo")
                        .map(ColumnDef::typeArgs).orElseThrow()), "l'editor ha ricaricato lo stato reale");
                Probe.paintWindow("step5", a.frame(), "T5.5-errore-server-" + server.id() + ".png");
                ev.append("T5.5 — avviso nella scheda Colonne: ").append(note.replace('\n', ' ')).append('\n')
                        .append("  conferma chiesta: «").append(confermaChiesta.replace('\n', ' ')).append("»\n")
                        .append("  rifiutata → 0 istruzioni; accettata → esito «").append(esito.replace('\n', ' '))
                        .append("»\n  titolo sul server: VARCHAR(").append(lunghezzaPrima)
                        .append("), come prima\n");
                onEdt(() -> a.frame().tabs().close(editor));

                // ---------------------------------------------------------------- T5.6
                TableDef prestiti = a.workspace().reader().table(catalog, "prestiti").orElseThrow();
                TableEditor multiplo = fromEdt(() -> a.frame().openTableEditor(catalog, prestiti));
                onEdt(() -> {
                    multiplo.optionsTab().setTableName("prestiti_storico");      // riuscirà: RENAME TABLE
                    multiplo.foreignKeysTab().select(0);
                    multiplo.foreignKeysTab().removeSelected();                  // riuscirà: DROP FOREIGN KEY
                    // fallirà: le date non entrano in un TINYINT
                    multiplo.columnsTab().setCell(riga(multiplo, "data_prestito"), ColumnsTab.TYPE, "TINYINT");
                });
                List<String> piano = fromEdt(multiplo::previewStatements);
                assertTrue(piano.size() >= 2, "il piano ha più istruzioni: " + piano);
                a.ws.tableEditorConfirmAnswer = true;
                onEdt(multiplo::apply);
                a.awaitLastProposal();
                a.waitIdle();
                String esitoMultiplo = fromEdt(multiplo::outcomeText);
                assertTrue(esitoMultiplo.contains("Errore"), "l'esito riporta l'errore del server: " + esitoMultiplo);
                assertTrue(esitoMultiplo.contains("su " + piano.size()) || esitoMultiplo.contains("Applicata"),
                        "l'esito dice quante istruzioni sono passate: " + esitoMultiplo);
                assertTrue(server.tableExists(catalog, "prestiti_storico"),
                        "la prima istruzione è stata applicata per davvero");
                assertFalse(server.tableExists(catalog, "prestiti"), "il vecchio nome non c'è più");
                TableDef reale = a.workspace().reader().table(catalog, "prestiti_storico").orElseThrow();
                assertEquals("prestiti_storico", fromEdt(() -> multiplo.editedTable().name()),
                        "l'editor mostra il nome vero");
                assertEquals(reale.column("data_prestito").orElseThrow().dataType(),
                        fromEdt(() -> multiplo.editedTable().column("data_prestito").orElseThrow().dataType()),
                        "l'editor mostra il tipo vero letto dal server, non quello che l'utente aveva chiesto");
                Probe.paintWindow("step5", a.frame(), "T5.6-server-" + server.id() + ".png");
                ev.append("T5.6 — piano di ").append(piano.size()).append(" istruzioni:\n  ")
                        .append(String.join(";\n  ", piano)).append(";\n  esito: ")
                        .append(esitoMultiplo.replace('\n', ' ')).append("\n  sul server: prestiti_storico esiste, ")
                        .append("prestiti no; stato ricaricato: data_prestito ")
                        .append(reale.column("data_prestito").orElseThrow().dataType()).append('\n');
                onEdt(() -> a.frame().tabs().close(multiplo));

                // ---------------------------------------------------------------- T5.7
                TableDef editori = a.workspace().reader().table(catalog, "editori").orElseThrow();
                TableEditor conFk = fromEdt(() -> a.frame().openTableEditor(catalog, editori));
                onEdt(() -> conFk.optionsTab().setEngine("MyISAM"));
                String bloccata = fromEdt(() -> conFk.optionsTab().engineBlockedText());
                assertTrue(bloccata.contains("MyISAM"), "la conversione è bloccata con la spiegazione: " + bloccata);
                assertTrue(bloccata.contains("libri.fk_libri_editori"), "e dice chi la riferisce: " + bloccata);
                assertEquals("InnoDB", fromEdt(() -> conFk.editedTable().engine()), "engine invariato nell'editor");
                int registroEngine = fromEdt(() -> a.log().size());
                onEdt(conFk::apply);
                a.waitIdle();
                assertEquals(registroEngine, fromEdt(() -> a.log().size()), "bloccata: nessuna istruzione eseguita");
                assertEquals("InnoDB", engineSulServer(server, catalog, "editori"), "engine invariato sul server");
                Probe.paintWindow("step5", a.frame(), "T5.7-bloccata-server-" + server.id() + ".png");
                onEdt(() -> a.frame().tabs().close(conFk));

                // tabella libera: la conversione si fa e l'albero segue
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                a.expand(NavNode.Kind.TABLES, catalog, null);
                assertSame(NavigatorIcons.TABLE_INNODB,
                        fromEdt(() -> a.nav().iconOf(a.nav().find(NavNode.Kind.TABLE, catalog, "note_libere"))),
                        "note_libere parte InnoDB");
                TableDef note2 = a.workspace().reader().table(catalog, "note_libere").orElseThrow();
                TableEditor libera = fromEdt(() -> a.frame().openTableEditor(catalog, note2));
                onEdt(() -> libera.optionsTab().setEngine("MyISAM"));
                assertEquals("", fromEdt(() -> libera.optionsTab().engineBlockedText()),
                        "una tabella libera non è bloccata");
                onEdt(libera::apply);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("MyISAM", engineSulServer(server, catalog, "note_libere"), "convertita sul server");
                Probe.waitUntil("icona MyISAM nell'albero", 20_000, () -> {
                    onEdt(() -> a.nav().refreshAll());
                    return NavigatorIcons.TABLE_MYISAM
                            == fromEdt(() -> a.nav().iconOf(a.nav().find(NavNode.Kind.TABLE, catalog, "note_libere")));
                });
                Probe.paintWindow("step5", a.frame(), "T5.7-myisam-server-" + server.id() + ".png");

                // e si torna indietro
                onEdt(() -> libera.optionsTab().setEngine("InnoDB"));
                onEdt(libera::apply);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("InnoDB", engineSulServer(server, catalog, "note_libere"), "tornata InnoDB sul server");
                ev.append("T5.7 — editori (riferita da libri.fk_libri_editori): bloccata, «")
                        .append(bloccata.replace('\n', ' ')).append("»; engine sul server ancora InnoDB, 0 istruzioni\n")
                        .append("  note_libere (libera): InnoDB → MyISAM → InnoDB eseguite sul server, icona ")
                        .append("dell'albero aggiornata a ogni passaggio\nEsito: SUPERATO\n");
            }
        } finally {
            Probe.writeText("step5", "T5.5-T5.6-T5.7-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    private static String lunghezzaSulServer(DbServer server, String catalog, String table, String column)
            throws Exception {
        return server.scalar("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                + catalog + "' AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'");
    }

    private static String engineSulServer(DbServer server, String catalog, String table) throws Exception {
        return server.scalar("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + catalog
                + "' AND TABLE_NAME = '" + table + "'");
    }

    /** L'indice di riga della colonna con quel nome nella scheda Colonne. */
    private static int riga(TableEditor editor, String name) {
        List<ColumnDef> columns = editor.editedTable().columns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("colonna assente nell'editor: " + name);
    }
}
