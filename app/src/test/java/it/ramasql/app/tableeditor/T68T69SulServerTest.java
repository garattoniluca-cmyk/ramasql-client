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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.servertest.ClientApp;
import it.ramasql.app.servertest.DbServer;
import it.ramasql.app.servertest.Probe;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T6.8</b> e <b>T6.9</b> <b>sul server vero</b>.
 *
 * <p>T6.8: indice UNIQUE su {@code soci.email} con valori duplicati → avviso <b>con la query che trova i duplicati</b>
 * (eseguita davvero dal client, non solo mostrata); insistendo, il server rifiuta con <b>1062</b> spiegato.
 * <p>T6.9: la scheda <em>Chiavi esterne</em> di una tabella MyISAM è disabilitata con la spiegazione, e propone di
 * convertire la tabella in InnoDB; la conversione si fa e la scheda si riabilita.
 */
@Tag("step6")
@Tag("ui")
@Tag("it")
class T68T69SulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t68_t69_duplicatiEMyisamSulServer(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("unici");
        StringBuilder ev = new StringBuilder("T6.8 e T6.9 su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.run("CREATE TABLE `" + catalog + "`.`soci` (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + " tessera CHAR(8) NOT NULL, email VARCHAR(120) NULL, PRIMARY KEY (id))"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            server.run("INSERT INTO `" + catalog + "`.`soci` (tessera, email) VALUES"
                    + " ('T100001', 'anna@scuola.it'), ('T100002', 'bruno@scuola.it'),"
                    + " ('T100003', 'anna@scuola.it'), ('T100004', NULL), ('T100005', 'bruno@scuola.it')");
            server.run("CREATE TABLE `" + catalog + "`.`vecchia` (id INT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + " id_socio INT UNSIGNED NULL, nota VARCHAR(60) NULL, PRIMARY KEY (id)) ENGINE=MyISAM"
                    + " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            ev.append("Catalogo di test: ").append(catalog)
                    .append(" — soci con 2 email duplicate (una due volte, una due volte) e un NULL;")
                    .append(" tabella «vecchia» MyISAM\n");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                a.ws.tableEditorConfirmAnswer = true;

                // ---------------------------------------------------------------- T6.8
                TableDef soci = a.workspace().reader().table(catalog, "soci").orElseThrow();
                TableEditor editor = fromEdt(() -> a.frame().openTableEditor(catalog, soci));
                onEdt(() -> {
                    IndexesTab idx = editor.indexesTab();
                    idx.addIndex();
                    idx.select(1);
                    idx.setCell(1, IndexesTab.NAME, "uq_soci_email");
                    idx.setCell(1, IndexesTab.KIND, IndexKind.UNIQUE);
                    idx.addColumn("email");
                });
                onEdt(() -> editor.indexesTab().select(1));
                onEdt(() -> editor.indexesTab().verifySelectedData());
                a.awaitLastProposal();
                a.waitIdle();
                DataCheckPanel panel = fromEdt(() -> editor.indexesTab().dataCheck());
                String query = fromEdt(panel::queryText);
                String stato = fromEdt(panel::statusText);
                assertTrue(query.contains("GROUP BY") && query.contains("HAVING"),
                        "la query che trova i duplicati: " + query);
                assertEquals(2, (int) fromEdt(panel::rowCount), "due valori duplicati trovati: " + query);
                assertTrue(stato.contains("1062"), "l'avviso dice che il server rifiuterebbe con 1062: " + stato);
                Probe.paintWindow("step6", a.frame(), "T6.8-duplicati-server-" + server.id() + ".png");

                // si procede comunque: il server rifiuta
                onEdt(editor::apply);
                a.awaitLastProposal();
                a.waitIdle();
                String esito = fromEdt(editor::outcomeText);
                assertTrue(esito.contains("1062"), "errore del server: " + esito);
                assertTrue(esito.contains("duplicat"), "spiegazione in italiano: " + esito);
                assertEquals("0", server.scalar("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE"
                        + " TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = 'soci'"
                        + " AND INDEX_NAME = 'uq_soci_email'"), "l'indice UNIQUE non è stato creato");
                ev.append("T6.8 — query dei duplicati: ").append(query).append('\n')
                        .append("  esito della verifica: ").append(stato).append('\n')
                        .append("  eseguito comunque: ").append(esito.replace('\n', ' ')).append('\n')
                        .append("  indice uq_soci_email sul server: assente\n");
                onEdt(() -> a.frame().tabs().close(editor));

                // ---------------------------------------------------------------- T6.9
                TableDef vecchia = a.workspace().reader().table(catalog, "vecchia").orElseThrow();
                assertEquals("MyISAM", vecchia.engine(), "la tabella di partenza è MyISAM");
                TableEditor myisam = fromEdt(() -> a.frame().openTableEditor(catalog, vecchia));
                assertEquals(ForeignKeysTab.CARD_MYISAM, fromEdt(() -> myisam.foreignKeysTab().visibleCard()),
                        "la scheda Chiavi esterne mostra la spiegazione invece della tabella");
                String spiegazione = fromEdt(() -> myisam.foreignKeysTab().myisamText());
                assertTrue(spiegazione.contains("MyISAM") && spiegazione.contains("non supporta le chiavi esterne"),
                        "con la spiegazione: " + spiegazione);
                assertTrue(spiegazione.contains("converti la tabella in InnoDB"), spiegazione);
                onEdt(() -> myisam.foreignKeysTab().addForeignKey());
                assertTrue(fromEdt(() -> myisam.editedTable().foreignKeys().isEmpty()),
                        "su MyISAM non si aggiungono chiavi esterne");
                Probe.paintWindow("step6", a.frame(), "T6.9-myisam-server-" + server.id() + ".png");

                // si accetta la proposta: la tabella diventa InnoDB sul server e la scheda si riabilita
                onEdt(() -> myisam.foreignKeysTab().convertButton().doClick());
                assertEquals("InnoDB", fromEdt(() -> myisam.editedTable().engine()), "engine cambiato nell'editor");
                onEdt(myisam::apply);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals("InnoDB", server.scalar("SELECT ENGINE FROM information_schema.TABLES WHERE"
                        + " TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = 'vecchia'"),
                        "convertita in InnoDB sul server");
                assertFalse(ForeignKeysTab.CARD_MYISAM.equals(fromEdt(() -> myisam.foreignKeysTab().visibleCard())),
                        "ora la scheda Chiavi esterne è utilizzabile");
                Probe.paintWindow("step6", a.frame(), "T6.9-convertita-server-" + server.id() + ".png");
                ev.append("T6.9 — «vecchia» MyISAM: scheda Chiavi esterne disabilitata, «")
                        .append(spiegazione.replace('\n', ' ')).append("»; accettata la conversione → engine sul ")
                        .append("server InnoDB e scheda riabilitata\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            // un test fallito non deve lasciare un file di evidenza che sembra valido
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step6", "T6.8-T6.9-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }
}
