/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import static it.ramasql.app.grid.GridTestSupport.fromEdt;
import static it.ramasql.app.grid.GridTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.TableDef;

/**
 * {@code BUG-017} — la griglia legge le pagine <b>fuori dall'EDT</b>. Una sorgente finta che dorme 1 s per pagina:
 * mentre la lettura è in corso l'EDT risponde (un evento accodato torna in pochi millisecondi), compare l'indicatore
 * «Lettura dal server…», i comandi e le modifiche della griglia sono sospesi; poi i dati arrivano e l'indicatore
 * sparisce. Vale per il cambio pagina, per l'ordinamento e per la prima pagina (costruzione della griglia); un errore
 * della lettura torna come prima (avviso, pagina invariata). Le sorgenti in memoria restano sull'EDT.
 */
@Tag("step12")
@Tag("ui")
class Bug017PaginaFuoriDallEdtTest {

    /** Quanto dorme la sorgente lenta. */
    private static final long SLOW_MS = 1000;
    /** Tempo massimo di risposta dell'EDT durante la lettura: molto meno della lettura. */
    private static final long EDT_RESPONSE_LIMIT_MS = 250;

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    /** Sorgente «dal server» finta: righe in memoria, ma dorme a comando e dice su che thread è stata chiamata. */
    static final class SlowSource implements GridDataSource {

        private final InMemoryGridDataSource rows;
        volatile long delayMs;
        volatile boolean fail;
        volatile boolean calledOnEdt;
        volatile CountDownLatch started = new CountDownLatch(1);

        SlowSource(TableDef table, List<List<String>> rows) {
            this.rows = new InMemoryGridDataSource(table.columns(), rows);
        }

        @Override
        public Page load(int pageIndex, int pageSize, SortOrder orderBy) {
            calledOnEdt |= SwingUtilities.isEventDispatchThread();
            started.countDown();
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (fail) {
                throw new IllegalStateException("connessione persa");
            }
            synchronized (rows) {
                return rows.load(pageIndex, pageSize, orderBy);
            }
        }
    }

    /** Quanto ci mette l'EDT a eseguire un evento vuoto accodato adesso. */
    private static long edtResponseMs() throws Exception {
        long start = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> { });
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void awaitTrue(String what, AtomicBoolean flag) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!flag.get()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("tempo scaduto: " + what);
            }
            Thread.sleep(10);
        }
    }

    @Test
    void paginaLentaNonBloccaLEdtEIDatiArrivano() throws Exception {
        TableDef soci = Fixtures.soci();
        SlowSource source = new SlowSource(soci, Fixtures.sociRows());
        DataGrid grid = fromEdt(() -> DataGrid.forTable(soci, source, 5, new FakeGridPrompts()));
        JFrame frame = GridTestSupport.host(grid, 1100, 380);
        try {
            assertFalse(source.calledOnEdt, "anche la prima pagina si legge fuori dall'EDT");
            assertEquals("1", GridTestSupport.cells(grid, 0, 0, 0, 0).get(0).get(0));

            // pagina 2 con una lettura da 1 s, chiesta sull'EDT come farebbe il clic sulla freccia
            source.delayMs = SLOW_MS;
            source.started = new CountDownLatch(1);
            AtomicReference<Boolean> esito = new AtomicReference<>();
            AtomicBoolean finito = new AtomicBoolean();
            long start = System.nanoTime();
            SwingUtilities.invokeLater(() -> {
                esito.set(grid.nextPage());
                finito.set(true);
            });
            assertTrue(source.started.await(5, TimeUnit.SECONDS), "la lettura è partita");

            // l'EDT risponde mentre la sorgente dorme
            long risposta = edtResponseMs();
            assertTrue(risposta < EDT_RESPONSE_LIMIT_MS, "l'EDT ha risposto in " + risposta + " ms durante la lettura");
            assertFalse(finito.get(), "la lettura non è ancora finita");
            assertTrue(fromEdt(grid::isLoading), "la griglia sa che sta leggendo");
            assertFalse(source.calledOnEdt, "la sorgente è chiamata fuori dall'EDT");

            // durante la lettura: indicatore visibile (dopo il ritardo), comandi e modifiche sospesi
            Thread.sleep(DataGrid.LOADING_INDICATOR_DELAY_MS + 200L);
            assertTrue(fromEdt(grid::isLoadingIndicatorShown), "l'indicatore «Lettura dal server…» è visibile");
            evidenza(frame, "BUG-017-lettura-in-corso.png");
            assertFalse(fromEdt(() -> grid.model().isCellEditable(0, 1)), "nessuna modifica durante la lettura");
            assertFalse(fromEdt(() -> grid.sortBy(new GridDataSource.SortOrder("nome", true))),
                    "un secondo comando durante la lettura è rifiutato");
            assertFalse(fromEdt(() -> findButton(grid, "dataGrid.page.next").isEnabled()),
                    "le frecce sono spente durante la lettura");
            long rispostaDopo = edtResponseMs();
            assertTrue(rispostaDopo < EDT_RESPONSE_LIMIT_MS, "l'EDT risponde ancora: " + rispostaDopo + " ms");

            // i dati arrivano
            awaitTrue("pagina 2 letta", finito);
            long durata = (System.nanoTime() - start) / 1_000_000;
            assertEquals(Boolean.TRUE, esito.get(), "nextPage riuscito");
            assertTrue(durata >= SLOW_MS - 50, "la lettura è durata quanto la sorgente: " + durata + " ms");
            assertEquals(1, (int) fromEdt(grid::pageIndex));
            assertEquals("6", GridTestSupport.cells(grid, 0, 0, 0, 0).get(0).get(0), "prima riga della pagina 2");
            assertFalse(fromEdt(grid::isLoading));
            assertFalse(fromEdt(grid::isLoadingIndicatorShown), "l'indicatore sparisce a lettura finita");
            assertTrue(fromEdt(() -> grid.model().isCellEditable(0, 1)), "le modifiche tornano possibili");
            evidenza(frame, "BUG-017-pagina-arrivata.png");
        } finally {
            onEdt(frame::dispose);
        }
    }

    @Test
    void ordinamentoLentoFuoriDallEdt() throws Exception {
        TableDef soci = Fixtures.soci();
        SlowSource source = new SlowSource(soci, Fixtures.sociRows());
        DataGrid grid = fromEdt(() -> DataGrid.forTable(soci, source, 5, new FakeGridPrompts()));
        JFrame frame = GridTestSupport.host(grid, 1100, 380);
        try {
            source.delayMs = SLOW_MS;
            source.started = new CountDownLatch(1);
            AtomicBoolean finito = new AtomicBoolean();
            SwingUtilities.invokeLater(() -> {
                grid.sortBy(new GridDataSource.SortOrder("id", false));
                finito.set(true);
            });
            assertTrue(source.started.await(5, TimeUnit.SECONDS));
            long risposta = edtResponseMs();
            assertTrue(risposta < EDT_RESPONSE_LIMIT_MS, "l'EDT ha risposto in " + risposta + " ms durante l'ordinamento");
            awaitTrue("ordinamento letto", finito);
            assertEquals("12", GridTestSupport.cells(grid, 0, 0, 0, 0).get(0).get(0), "id decrescente");
            assertEquals(new GridDataSource.SortOrder("id", false), fromEdt(grid::sortOrder));
        } finally {
            onEdt(frame::dispose);
        }
    }

    @Test
    void primaPaginaLentaNonBloccaLEdt() throws Exception {
        TableDef soci = Fixtures.soci();
        SlowSource source = new SlowSource(soci, Fixtures.sociRows());
        source.delayMs = SLOW_MS;
        AtomicReference<DataGrid> creata = new AtomicReference<>();
        AtomicBoolean finito = new AtomicBoolean();
        SwingUtilities.invokeLater(() -> {
            creata.set(DataGrid.forTable(soci, source, 5, new FakeGridPrompts()));
            finito.set(true);
        });
        assertTrue(source.started.await(5, TimeUnit.SECONDS));
        long risposta = edtResponseMs();
        assertTrue(risposta < EDT_RESPONSE_LIMIT_MS, "l'EDT ha risposto in " + risposta + " ms durante la prima pagina");
        assertFalse(finito.get());
        awaitTrue("griglia creata", finito);
        assertNotNull(creata.get());
        assertEquals(5, (int) fromEdt(() -> creata.get().model().dataRowCount()));
    }

    @Test
    void erroreDiLetturaLasciaLaPaginaEDiceIlPerche() throws Exception {
        TableDef soci = Fixtures.soci();
        SlowSource source = new SlowSource(soci, Fixtures.sociRows());
        DataGrid grid = fromEdt(() -> DataGrid.forTable(soci, source, 5, new FakeGridPrompts()));
        source.delayMs = 300;
        source.fail = true;
        assertFalse(fromEdt(grid::nextPage), "la lettura fallita non cambia pagina");
        assertEquals(0, (int) fromEdt(grid::pageIndex));
        assertEquals("1", GridTestSupport.cells(grid, 0, 0, 0, 0).get(0).get(0), "la pagina resta quella di prima");
        assertTrue(fromEdt(grid::notice).contains("connessione persa"), fromEdt(grid::notice));
        assertFalse(fromEdt(grid::isLoading));
        // e la prima pagina che fallisce fa fallire la creazione, come prima (chi apre la scheda lo dice)
        AtomicReference<RuntimeException> errore = new AtomicReference<>();
        onEdt(() -> {
            try {
                DataGrid.forTable(soci, source, 5, new FakeGridPrompts());
            } catch (RuntimeException e) {
                errore.set(e);
            }
        });
        assertNotNull(errore.get());
        assertEquals("connessione persa", errore.get().getMessage());
    }

    @Test
    void sorgenteInMemoriaRestaSullEdt() {
        TableDef soci = Fixtures.soci();
        InMemoryGridDataSource source = new InMemoryGridDataSource(soci.columns(), Fixtures.sociRows());
        assertTrue(source.inMemory());
        assertFalse(new SlowSource(soci, Fixtures.sociRows()).inMemory(), "chi legge dal server non è «in memoria»");
        DataGrid grid = fromEdt(() -> DataGrid.forTable(soci, source, 5, new FakeGridPrompts()));
        assertTrue(fromEdt(grid::nextPage));
        assertEquals(2, source.loadCount());
        assertFalse(fromEdt(grid::isLoadingIndicatorShown));
    }

    /** La finestra ospite disegnata in {@code test-results/step12/} (l'EDT la disegna anche durante la lettura). */
    private static void evidenza(JFrame frame, String fileName) throws Exception {
        java.nio.file.Path dir = GridTestSupport.projectRoot().resolve("test-results").resolve("step12");
        java.nio.file.Files.createDirectories(dir);
        java.awt.image.BufferedImage img = fromEdt(() -> {
            frame.validate();
            java.awt.Component root = frame.getRootPane();
            java.awt.image.BufferedImage i = new java.awt.image.BufferedImage(root.getWidth(), root.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = i.createGraphics();
            try {
                root.paint(g);
            } finally {
                g.dispose();
            }
            return i;
        });
        javax.imageio.ImageIO.write(img, "png", dir.resolve(fileName).toFile());
    }

    private static javax.swing.JButton findButton(java.awt.Container c, String name) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof javax.swing.JButton b && name.equals(b.getName())) {
                return b;
            }
            if (child instanceof java.awt.Container inner) {
                javax.swing.JButton found = findButton(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
