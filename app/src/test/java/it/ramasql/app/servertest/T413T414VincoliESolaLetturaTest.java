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

import static it.ramasql.app.grid.GridTestSupport.type;
import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import it.ramasql.app.navigator.NavigatorPanel;
import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T4.13</b> e <b>T4.14</b> <b>contro i server veri</b>.
 *
 * <p>T4.13: inserimento in {@code prestiti} con un {@code id_socio} che non esiste → il server risponde
 * <b>1452</b>, il messaggio arriva spiegato in italiano e la riga <b>resta in griglia, non salvata</b>: si corregge e
 * si riprova senza riscrivere tutto.
 * <p>T4.14: una tabella <b>senza chiave primaria né UNIQUE</b> e una <b>vista</b> si aprono in <b>sola lettura</b>,
 * con la spiegazione del perché; copiare i dati resta possibile.
 */
@Tag("step4")
@Tag("ui")
@Tag("it")
class T413T414VincoliESolaLetturaTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t413_t414_fkMancanteSpiegataESolaLetturaSenzaChiave(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("vincoli");
        StringBuilder ev = new StringBuilder("T4.13 e T4.14 su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            server.run("CREATE TABLE `" + catalog + "`.`note_libere` (testo VARCHAR(80) NULL, numero INT NULL)"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            server.run("INSERT INTO `" + catalog + "`.`note_libere` (testo, numero) VALUES ('una nota', 1),"
                    + " ('un''altra', 2)");
            ev.append("Catalogo di test: ").append(catalog).append('\n');

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                // ---------------------------------------------------------------- T4.13
                TableDef prestiti = a.workspace().reader().table(catalog, "prestiti").orElseThrow();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, prestiti));
                a.waitIdle();
                long righePrima = server.rowCount(catalog, "prestiti");
                String libro = server.scalar("SELECT id FROM `" + catalog + "`.`libri` ORDER BY id LIMIT 1");
                String socioVero = server.scalar("SELECT id FROM `" + catalog + "`.`soci` ORDER BY id LIMIT 1");

                int nuova = fromEdt(() -> grid.model().pending().rowCount());
                type(grid, nuova, 1, libro);
                type(grid, nuova, 2, "999999");             // socio inesistente
                type(grid, nuova, 3, "2026-09-23");
                long idRiga = fromEdt(() -> grid.model().pending().rowId(nuova));

                a.ws.onPreview = d -> d.executeButton().doClick();
                onEdt(grid::confirm);
                a.awaitLastProposal();
                a.waitIdle();

                int riga = fromEdt(() -> grid.model().pending().indexOf(idRiga));
                assertTrue(riga >= 0, "la riga è ancora in griglia");
                assertEquals(PendingChanges.State.IN_ERRORE, fromEdt(() -> grid.model().pending().state(riga)),
                        "la riga non è salvata");
                assertEquals(PendingChanges.RowKind.INSERTED, fromEdt(() -> grid.model().pending().kind(riga)),
                        "resta un inserimento in sospeso");
                String errore = fromEdt(() -> grid.model().pending().errorMessage(riga).orElse(""));
                assertTrue(errore.contains("1452"), "codice del server: " + errore);
                assertTrue(errore.contains("chiave esterna"), "spiegazione in italiano: " + errore);
                assertEquals(righePrima, server.rowCount(catalog, "prestiti"), "sul server non è entrato niente");
                Probe.paintWindow("step4", a.frame(), "T4.13-" + server.id() + ".png");
                ev.append("T4.13 — inserimento con id_socio=999999: «").append(errore)
                        .append("»; riga ancora in sospeso (").append(fromEdt(() -> grid.model().pending().kind(riga)))
                        .append("), prestiti sul server ").append(server.rowCount(catalog, "prestiti"))
                        .append(" righe (prima ").append(righePrima).append(")\n");

                // corretto il socio, la stessa riga si salva
                type(grid, riga, 2, socioVero);
                onEdt(grid::confirm);
                a.awaitLastProposal();
                a.waitIdle();
                assertEquals(righePrima + 1, server.rowCount(catalog, "prestiti"), "corretta, la riga è entrata");
                ev.append("  corretto id_socio=").append(socioVero).append(" → prestiti ")
                        .append(server.rowCount(catalog, "prestiti")).append(" righe\n");
                onEdt(() -> a.frame().tabs().close(grid));

                // ---------------------------------------------------------------- T4.14 — tabella senza chiave
                TableDef senzaChiave = a.workspace().reader().table(catalog, "note_libere").orElseThrow();
                DataGrid libere = fromEdt(() -> a.frame().openDataEntry(catalog, senzaChiave));
                a.waitIdle();
                assertFalse(fromEdt(libere::isConfirmEnabled), "Conferma spenta");
                String spiegazione = fromEdt(libere::readOnlyExplanation);
                assertTrue(spiegazione.contains("chiave primaria") && spiegazione.contains("UNIQUE"), spiegazione);
                assertTrue(spiegazione.contains("copiare") || spiegazione.contains("Puoi copiare"), spiegazione);
                assertEquals(2, fromEdt(() -> libere.model().pending().rowCount()), "le righe si leggono");
                // copia consentita
                java.awt.datatransfer.Clipboard appunti = new java.awt.datatransfer.Clipboard("prova");
                onEdt(() -> libere.setClipboard(appunti));
                onEdt(() -> libere.selectBlock(0, 0, 1, 1));
                it.ramasql.app.grid.GridTestSupport.action(libere, DataGrid.ACTION_COPY);
                String copiato = (String) appunti.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                assertTrue(copiato.contains("una nota"), "il blocco copiato: " + copiato);
                Probe.paintWindow("step4", a.frame(), "T4.14-senza-chiave-" + server.id() + ".png");
                ev.append("T4.14 — note_libere (nessuna PK, nessun UNIQUE): sola lettura, «").append(spiegazione)
                        .append("»; copia di un blocco 2×2: «").append(copiato.replace('\n', '|').replace('\t', '»'))
                        .append("»\n");

                // ---------------------------------------------------------------- T4.14 — vista
                List<it.ramasql.core.metadata.ColumnDef> colonne =
                        a.workspace().reader().viewColumns(catalog, "v_prestiti_aperti");
                assertFalse(colonne.isEmpty(), "le colonne della vista si leggono");
                DataGrid vista = fromEdt(() -> a.frame().openView(
                        new NavigatorPanel.ViewToOpen(catalog, "v_prestiti_aperti", colonne)));
                a.waitIdle();
                assertNotNull(vista, "la scheda della vista è aperta");
                assertFalse(fromEdt(vista::isConfirmEnabled), "una vista non si modifica");
                String spiegazioneVista = fromEdt(vista::readOnlyExplanation);
                assertTrue(spiegazioneVista.contains("viste"), spiegazioneVista);
                Probe.paintWindow("step4", a.frame(), "T4.14-vista-" + server.id() + ".png");
                ev.append("T4.14 — vista v_prestiti_aperti: ").append(colonne.size())
                        .append(" colonne, sola lettura, «").append(spiegazioneVista).append("»\nEsito: SUPERATO\n");
            }
        } finally {
            Probe.writeText("step4", "T4.13-T4.14-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }
}
