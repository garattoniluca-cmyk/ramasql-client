/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.qb.QbHost;
import it.ramasql.qb.QbRuntime;
import it.ramasql.qb.QbSql;

/**
 * {@code BUG-024} (Step 12): aggiungere una tabella al diagramma (o caricarvi un modello) non legge mai i metadati
 * sull'EDT, e con una sorgente lenta l'interfaccia non si congela.
 *
 * <p>La sorgente dei metadati è finta ({@link BibliotecaFinta}) e aspetta {@value #RITARDO_MS} ms a ogni lettura;
 * registra il thread di ogni lettura. Mentre l'operazione gira sull'EDT, il thread del test manda all'EDT un «battito»
 * ogni 25 ms e misura quanto aspetta prima di essere eseguito: con l'EDT bloccato dalle letture il battito
 * aspetterebbe quanto tutta l'operazione (≥ 2 letture = ≥ {@value #RITARDO_MS}×2 ms). Si verifica anche che l'attesa
 * sia mostrata (la finestra «Lettura dal server: …» di {@link it.ramasql.qb.OffEdtQbMetadata}) e che, a lettura finita, il diagramma sia completo (colonne e join
 * proposti), cioè che il completamento avvenga sull'EDT come prima.
 */
@Tag("step12")
@Tag("ui")
class T12MetadatiFuoriDallEdtTest {

    static final long RITARDO_MS = 300;
    /** Il battito più lento ammesso: l'attesa breve iniziale (120 ms) più un margine per la macchina di prova. */
    static final long BATTITO_MAX_MS = 400;

    private QbHost prima;
    private BibliotecaFinta.Host host;
    private JFrame frame;
    private QueryBuilder qb;
    private final List<String> attese = new CopyOnWriteArrayList<>();
    private final AWTEventListener finestre = e -> {
        if (e.getID() == WindowEvent.WINDOW_OPENED && e.getSource() instanceof JDialog d
                && "qb.metadata.wait".equals(d.getName())) {
            attese.add(testo(d));
        }
    };
    private final StringBuilder evidenza = new StringBuilder("BUG-024 — metadati del query builder fuori dall'EDT\n");

    @BeforeEach
    void apri() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");
        prima = QbRuntime.processHost();
        host = new BibliotecaFinta.Host(true);
        QbRuntime.setHost(host);
        Toolkit.getDefaultToolkit().addAWTEventListener(finestre, AWTEvent.WINDOW_EVENT_MASK);
        SwingUtilities.invokeAndWait(() -> {
            // riscaldamento: le classi della finestra d'attesa si caricano qui, non durante la misura
            JDialog d = new JDialog();
            d.add(new JProgressBar());
            d.pack();
            d.dispose();
            qb = new QueryBuilder(host);
            frame = new JFrame("RamaSQL - T12 metadati fuori dall'EDT");
            frame.getContentPane().add(qb);
            Dimension size = new Dimension(1100, 700);
            frame.setSize(size);
            frame.addNotify();
            frame.getRootPane().setSize(size);
            frame.getRootPane().validate();
        });
    }

    @AfterEach
    void chiudi() throws Exception {
        Toolkit.getDefaultToolkit().removeAWTEventListener(finestre);
        SwingUtilities.invokeAndWait(() -> frame.dispose());
        QbRuntime.setHost(prima);
    }

    @Test
    void sorgenteLentaNonBloccaLEdt() throws Exception {
        try {
            host.metadati.delayMs = RITARDO_MS;

            // 1. prima tabella: nome esatto e colonne (niente join da proporre: il diagramma è vuoto)
            Misura m1 = misura("1. aggiunta di prestiti", () -> QbOperations.addTable(qb, "prestiti"));
            assertEquals(List.of("id", "id_libro", "id_socio", "data_prestito", "data_reso"),
                    fromEdt(() -> QbOperations.columns(qb, "prestiti")), "colonne lette fuori dall'EDT, entità completa");
            assertTrue(attese.stream().anyMatch(t -> t.contains("prestiti")), "attesa mostrata con il nome: " + attese);

            // 2. seconda tabella: anche le chiavi esterne (join proposto) si leggono fuori dall'EDT
            misura("2. aggiunta di soci (join proposto)", () -> QbOperations.addTable(qb, "soci"));
            assertEquals(1, (int) fromEdt(() -> QbOperations.joins(qb).size()), "join prestiti–soci proposto");
            assertTrue(host.metadati.letture.stream().anyMatch(l -> l.metodo().equals("importedKeys")),
                    "chiavi esterne lette");

            // 3. «Apri le tabelle referenziate» (menu dell'entità): chiavi e tabelle collegate in una sola attesa
            var prestiti = entita("prestiti").getQueryToken();
            misura("3. tabelle referenziate da prestiti", () -> DiagramLoader.run(DiagramLoader.ALL_PRIMARY_TABLES, qb,
                    prestiti, true));
            assertTrue(fromEdt(() -> QbOperations.tables(qb)).contains("libri"), "libri aggiunta dal menu dell'entità");

            // 4. un modello caricato dall'SQL: le definizioni di tutte le tabelle, prima di disegnare
            String query = "SELECT a.cognome, l.titolo FROM autori a INNER JOIN libri_autori la ON a.id = la.id_autore"
                    + " INNER JOIN libri l ON la.id_libro = l.id INNER JOIN editori e ON l.id_editore = e.id";
            misura("4. modello a 4 tabelle dall'SQL", () -> qb.setQueryModel(QbSql.parse(query)));
            assertEquals(4, (int) fromEdt(() -> qb.diagram.getEntities().length), "4 entità dal modello");
            assertEquals(QbSql.normalize(query), QbSql.normalize(fromEdt(() -> qb.getQueryModel().toString(false))),
                    "stessa query rigenerata");

            // 5. l'elenco delle tabelle a sinistra del diagramma
            misura("5. elenco degli oggetti", () -> QbOperations.refreshObjects(qb));
            assertTrue(fromEdt(() -> QbOperations.objects(qb)).contains("soci"), "elenco riletto");

            // 6. sorgente veloce (definizioni già in memoria, il caso normale): nessuna finestra d'attesa
            host.metadati.delayMs = 0;
            int finestrePrima = attese.size();
            onEdt(() -> QbOperations.clear(qb));
            onEdt(() -> QbOperations.addTable(qb, "libri"));
            onEdt(() -> QbOperations.addTable(qb, "editori"));
            assertEquals(finestrePrima, attese.size(), "con letture rapide nessuna finestra d'attesa: " + attese);
            assertEquals(1, (int) fromEdt(() -> QbOperations.joins(qb).size()), "join libri–editori proposto");

            assertEquals(List.of(), host.metadati.sullEdt(), "letture dei metadati avvenute sull'EDT");
            assertTrue(host.avvisi.isEmpty(), "avvisi inattesi: " + host.avvisi);
            evidenza.append("letture dei metadati in tutto: ").append(host.metadati.letture.size())
                    .append(", di cui sull'EDT: 0 (thread: ")
                    .append(host.metadati.letture.stream().map(BibliotecaFinta.Lettura::thread).distinct().toList())
                    .append(")\nfinestre d'attesa mostrate: ").append(attese).append("\nEsito: SUPERATO\n");
        } catch (Throwable t) {
            evidenza.append("letture sull'EDT: ").append(host.metadati.sullEdt()).append("\nEsito: FALLITO - ")
                    .append(t).append('\n');
            throw t;
        } finally {
            Step12Files.write("T12-BUG024-metadati-fuori-edt.txt", evidenza.toString());
        }
    }

    /** Durata dell'operazione e battito più lento dell'EDT mentre gira. */
    record Misura(long durataMs, long battitoMaxMs, int battiti) {
    }

    /**
     * Esegue {@code operazione} sull'EDT (con {@code invokeLater}, come un clic) e intanto misura i battiti; verifica
     * che l'operazione sia durata almeno due letture lente, che l'EDT abbia continuato a rispondere e che nessuna
     * lettura sia avvenuta sull'EDT.
     */
    private Misura misura(String titolo, Azione operazione) throws Exception {
        int lettureInizio = host.metadati.letture.size();
        AtomicBoolean finita = new AtomicBoolean();
        AtomicReference<Throwable> errore = new AtomicReference<>();
        long[] durata = new long[1];
        SwingUtilities.invokeLater(() -> {
            long t0 = System.nanoTime();
            try {
                operazione.run();
            } catch (Throwable t) {
                errore.set(t);
            } finally {
                durata[0] = (System.nanoTime() - t0) / 1_000_000;
                finita.set(true);
            }
        });
        long massimo = 0;
        int battiti = 0;
        long limite = System.currentTimeMillis() + 60_000;
        while (!finita.get() && System.currentTimeMillis() < limite) {
            long inviato = System.nanoTime();
            long[] attesa = new long[1];
            CountDownLatch eseguito = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                attesa[0] = (System.nanoTime() - inviato) / 1_000_000;
                eseguito.countDown();
            });
            assertTrue(eseguito.await(30, TimeUnit.SECONDS), "l'EDT non risponde");
            if (!finita.get()) {
                massimo = Math.max(massimo, attesa[0]);
                battiti++;
            }
            Thread.sleep(25);
        }
        assertTrue(finita.get(), titolo + ": operazione non finita in tempo");
        rilancia(errore.get());
        onEdt(() -> { }); // l'operazione ha finito sull'EDT: il diagramma è completo
        int letture = host.metadati.letture.size() - lettureInizio;
        evidenza.append(titolo).append(": ").append(durata[0]).append(" ms, ").append(letture).append(" letture da ")
                .append(RITARDO_MS).append(" ms, ").append(battiti).append(" battiti dell'EDT, il più lento ")
                .append(massimo).append(" ms\n");
        assertTrue(letture >= 2, titolo + ": letture " + letture);
        assertTrue(durata[0] >= RITARDO_MS * 2 - 50, titolo + ": l'operazione ha davvero aspettato la sorgente lenta ("
                + durata[0] + " ms)");
        assertTrue(battiti >= 3, titolo + ": battiti dell'EDT durante l'operazione: " + battiti);
        assertTrue(massimo <= BATTITO_MAX_MS, titolo + ": l'EDT ha risposto dopo " + massimo + " ms (massimo "
                + BATTITO_MAX_MS + ")");
        assertEquals(List.of(), host.metadati.sullEdt(), titolo + ": letture sull'EDT");
        return new Misura(durata[0], massimo, battiti);
    }

    private DiagramAbstractEntity entita(String tabella) throws Exception {
        return fromEdt(() -> {
            for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
                if (e.getQueryToken().getName().equalsIgnoreCase(tabella)) {
                    return e;
                }
            }
            throw new AssertionError("entità assente: " + tabella);
        });
    }

    /** Il testo della finestra d'attesa. */
    private static String testo(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l && "qb.metadata.wait.message".equals(l.getName())) {
                return l.getText();
            }
            if (k instanceof Container sub) {
                String t = testo(sub);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- EDT

    private interface Azione {
        void run() throws Exception;
    }

    private static void onEdt(Azione a) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                a.run();
            } catch (Throwable t) {
                err.set(t);
            }
        });
        rilancia(err.get());
    }

    private static <T> T fromEdt(java.util.concurrent.Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        onEdt(() -> out.set(c.call()));
        return out.get();
    }

    private static void rilancia(Throwable t) throws Exception {
        if (t instanceof Exception e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
    }
}
