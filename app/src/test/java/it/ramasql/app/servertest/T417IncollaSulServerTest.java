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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.grid.GridTestSupport;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T4.17</b> <b>contro i server veri</b>: un blocco di <b>20 righe × 3 colonne</b> (la forma in cui Excel e Calc
 * mettono negli appunti una selezione rettangolare: celle separate da tabulazione, righe da a-capo) si incolla sulla
 * riga d'inserimento di {@code autori} → 20 righe in sospeso → l'anteprima mostra <b>20 INSERT</b> → sul server
 * compaiono <b>20 righe</b>, con gli accenti e gli apostrofi al loro posto.
 */
@Tag("step4")
@Tag("ui")
@Tag("it")
class T417IncollaSulServerTest {

    private static final int RIGHE = 20;

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t417_venteRigheIncollateDiventanoVentiInsertSulServer(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("incolla");
        StringBuilder ev = new StringBuilder("T4.17 — incolla 20×3 su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            long prima = server.rowCount(catalog, "autori");

            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                TableDef autori = a.workspace().reader().table(catalog, "autori").orElseThrow();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, autori));
                a.waitIdle();

                String blocco = blocco();
                Clipboard appunti = new Clipboard("prova");
                appunti.setContents(new StringSelection(blocco), null);
                onEdt(() -> grid.setClipboard(appunti));

                int nuova = fromEdt(() -> grid.model().pending().rowCount());
                onEdt(() -> grid.selectBlock(nuova, 1, nuova, 1));   // cella attiva: riga d'inserimento, «cognome»
                GridTestSupport.action(grid, DataGrid.ACTION_PASTE);

                assertEquals(RIGHE, fromEdt(() -> grid.model().pending().rowCount()) - nuova,
                        "20 righe nuove in sospeso");
                String contatore = fromEdt(grid::counterText);
                assertTrue(contatore.contains(String.valueOf(RIGHE)), contatore);
                Probe.paintWindow("step4", a.frame(), "T4.17-incollate-" + server.id() + ".png");

                a.ws.onPreview = d -> {
                    Probe.paintWindow("step4", d, "T4.17-anteprima-" + server.id() + ".png");
                    d.executeButton().doClick();
                };
                onEdt(grid::confirm);
                var esito = a.awaitLastProposal();
                a.waitIdle();

                var mostrata = a.ws.previews.getLast();
                assertEquals(RIGHE, mostrata.script().size(), "l'anteprima ha 20 istruzioni");
                long insert = mostrata.script().statements().stream()
                        .filter(s -> s.text().startsWith("INSERT")).count();
                assertEquals(RIGHE, insert, "sono tutte INSERT");
                assertTrue(esito != null && esito.completed(), "tutte eseguite");
                assertEquals(prima + RIGHE, server.rowCount(catalog, "autori"), "20 righe in più sul server");

                List<List<String>> scritte = server.rows("SELECT cognome, nome, nazionalita FROM `" + catalog
                        + "`.`autori` WHERE cognome LIKE 'Incolla%' ORDER BY cognome");
                assertEquals(RIGHE, scritte.size(), "le 20 righe incollate si rileggono dal server");
                assertTrue(scritte.stream().anyMatch(r -> r.get(1).contains("'")),
                        "l'apostrofo è arrivato intero: " + scritte);
                assertTrue(scritte.stream().anyMatch(r -> r.get(2).contains("è")),
                        "gli accenti sono arrivati interi: " + scritte);
                Probe.paintWindow("step4", a.frame(), "T4.17-dopo-" + server.id() + ".png");

                ev.append("Catalogo di test: ").append(catalog).append(" — autori: ").append(prima)
                        .append(" righe prima\n")
                        .append("Blocco negli appunti: ").append(RIGHE).append(" righe × 3 colonne, tabulazioni e ")
                        .append("a-capo come Excel\nContatore dopo l'incolla: «").append(contatore).append("»\n")
                        .append("Anteprima: ").append(mostrata.script().size()).append(" istruzioni, tutte INSERT\n")
                        .append("Prima istruzione: ").append(mostrata.script().statements().get(0).text())
                        .append("\nSul server: ").append(server.rowCount(catalog, "autori")).append(" righe (")
                        .append(scritte.size()).append(" con cognome «Incolla…»); esempi ")
                        .append(scritte.subList(0, 3)).append("\nEsito: SUPERATO\n");
            }
        } finally {
            Probe.writeText("step4", "T4.17-server-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    /** Il testo che Excel mette negli appunti per una selezione 20×3: celle con tabulazione, righe con a-capo. */
    private static String blocco() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= RIGHE; i++) {
            sb.append("Incolla").append(String.format("%02d", i)).append('\t')
                    .append(i % 4 == 0 ? "Nicolò D'Angiò" : "Nome " + i).append('\t')
                    .append(i % 3 == 0 ? "irlandese" : "italiana è così").append('\n');
        }
        return sb.toString();
    }
}
