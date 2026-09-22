/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step2;

import static it.ramasql.app.step2.UiTestSupport.fromEdt;
import static it.ramasql.app.step2.UiTestSupport.onEdt;
import static it.ramasql.app.step2.UiTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.app.connection.ConnectionErrorDialog;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ProfileStore;

/**
 * T2.4 — host irraggiungibile (indirizzo non instradabile {@code 10.255.255.1}): l'interfaccia resta reattiva durante
 * l'attesa, <em>Annulla</em> riporta subito alla schermata iniziale, e senza annullare il tentativo finisce entro
 * 10 secondi con la diagnosi di tempo scaduto.
 */
@Tag("step2")
@Tag("ui")
class T24UnreachableHostTest {

    private static final String UNREACHABLE = "10.255.255.1";

    @TempDir
    Path dataDir;

    @BeforeAll
    static void lookAndFeel() {
        UiTestSupport.requireRedirectedAppData();
        UiTestSupport.setupLookAndFeel();
    }

    /** Misura quanto aspetta un evento prima di essere servito dall'EDT, finché non viene fermato. */
    private static final class EdtProbe implements AutoCloseable {
        final AtomicLong maxLatencyMicros = new AtomicLong();
        final AtomicInteger served = new AtomicInteger();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread thread;

        EdtProbe() {
            thread = new Thread(() -> {
                while (running.get()) {
                    long posted = System.nanoTime();
                    SwingUtilities.invokeLater(() -> {
                        maxLatencyMicros.accumulateAndGet((System.nanoTime() - posted) / 1000, Math::max);
                        served.incrementAndGet();
                    });
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "sonda-edt");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() throws InterruptedException {
            running.set(false);
            thread.join(1000);
            onEdt(() -> { }); // gli ultimi eventi accodati vengono serviti prima di leggere i risultati
        }
    }

    private App startWithUnreachableProfile(FakePrompts prompts) throws Exception {
        ProfileStore store = new ProfileStore(dataDir);
        store.add(ConnectionProfile.create("Host irraggiungibile", UNREACHABLE, 3306, "studente", "", ""));
        prompts.password = p -> "una-password-qualsiasi".toCharArray();
        return fromEdt(() -> App.create(dataDir, prompts));
    }

    @Test
    void t24a_duranteLAttesaLInterfacciaEReattivaEAnnullaRiportaSubitoAllInizio() throws Exception {
        FakePrompts prompts = new FakePrompts();
        App app = startWithUnreachableProfile(prompts);
        MainFrame frame = app.frame();
        long clickReturnedMillis;
        long cancelMillis;
        long start = System.nanoTime();
        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
        clickReturnedMillis = (System.nanoTime() - start) / 1_000_000;
        onEdt(() -> {
            assertEquals(MainFrame.Screen.CONNECTING, frame.screen(), "indicatore di attesa visibile");
            assertTrue(app.connections().isConnecting());
            assertTrue(frame.connectingPanel().cancelButton().isEnabled());
            assertTrue(frame.statusConnection().getText().contains("in corso"), frame.statusConnection().getText());
        });
        UiTestSupport.paintWindow(frame, "T2.4-attesa.png");

        // la sonda parte dopo la schermata: disegnare 1180×760 pixel è lavoro del test, non del programma
        EdtProbe probe = new EdtProbe();
        try (probe) {
            Thread.sleep(2500); // il driver sta aspettando un host che non risponderà: l'EDT intanto deve lavorare
            assertTrue(fromEdt(() -> app.connections().isConnecting()), "dopo 2,5 s il tentativo è ancora in corso");

            long cancelStart = System.nanoTime();
            onEdt(() -> frame.connectingPanel().cancelButton().doClick());
            waitUntil("schermata iniziale dopo Annulla", 1_000, () -> frame.screen() == MainFrame.Screen.HOME);
            cancelMillis = (System.nanoTime() - cancelStart) / 1_000_000;
        }

        onEdt(() -> {
            assertFalse(app.connections().isConnecting());
            assertNull(app.connections().session());
            assertEquals("Nessuna connessione aperta", frame.statusConnection().getText());
            assertTrue(frame.connectButton().isEnabled());
        });
        long maxLatencyMillis = probe.maxLatencyMicros.get() / 1000;
        assertTrue(clickReturnedMillis < 1000, "il clic sulla tessera non aspetta la rete: " + clickReturnedMillis + " ms");
        assertTrue(probe.served.get() >= 50, "eventi serviti dall'EDT durante l'attesa: " + probe.served.get());
        assertTrue(maxLatencyMillis < 200, "latenza massima dell'EDT durante l'attesa: " + maxLatencyMillis + " ms");
        assertTrue(cancelMillis < 1000, "Annulla riporta all'inizio in " + cancelMillis + " ms");
        assertTrue(prompts.connectionErrors.isEmpty(), "annullare non è un errore");

        // l'esito tardivo del driver (tempo scaduto, qualche secondo dopo) non deve far comparire nulla
        Thread.sleep(8000);
        onEdt(() -> {
            assertEquals(MainFrame.Screen.HOME, frame.screen());
            assertTrue(prompts.connectionErrors.isEmpty(), "l'esito tardivo di un tentativo annullato si ignora");
        });

        UiTestSupport.writeText("T2.4-annulla.txt", "T2.4 (a) — host irraggiungibile " + UNREACHABLE + ":3306, poi Annulla\n"
                + "clic sulla tessera: ritorno in " + clickReturnedMillis + " ms (la rete è su un altro thread)\n"
                + "attesa osservata prima di annullare: 2500 ms, tentativo ancora in corso\n"
                + "eventi invokeLater serviti dall'EDT durante l'attesa: " + probe.served.get() + "\n"
                + "latenza massima di un evento sull'EDT: " + maxLatencyMillis + " ms (" + probe.maxLatencyMicros.get() + " µs; limite 200 ms)\n"
                + "Annulla → schermata iniziale in " + cancelMillis + " ms (limite 1000 ms)\n"
                + "finestre d'errore mostrate, anche dopo 8 s: " + prompts.connectionErrors.size() + "\n"
                + "schermata dell'attesa: T2.4-attesa.png\n");
    }

    @Test
    void t24b_senzaAnnullareIlTentativoFinisceEntroDieciSecondiConLaDiagnosiDiTempoScaduto() throws Exception {
        FakePrompts prompts = new FakePrompts();
        App app = startWithUnreachableProfile(prompts);
        MainFrame frame = app.frame();
        long elapsed;
        EdtProbe probe = new EdtProbe();
        try (probe) {
            onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
            elapsed = waitUntil("diagnosi di tempo scaduto", 12_000, () -> !prompts.connectionErrors.isEmpty());
        }

        ConnectionFailure failure = prompts.connectionErrors.get(0).failure();
        assertEquals(ConnectionErrorCause.TIMEOUT, failure.cause());
        assertTrue(elapsed <= 10_000, "tempo massimo 10 s, misurato " + elapsed + " ms");
        assertTrue(elapsed >= 3_000, "un indirizzo non instradabile non risponde subito: " + elapsed + " ms");
        assertTrue(failure.message().contains(UNREACHABLE) && failure.message().contains("firewall"), failure.message());
        long maxLatencyMillis = probe.maxLatencyMicros.get() / 1000;
        assertTrue(maxLatencyMillis < 200, "latenza massima dell'EDT durante l'attesa: " + maxLatencyMillis + " ms");
        onEdt(() -> assertEquals(MainFrame.Screen.HOME, frame.screen()));

        ConnectionErrorDialog dialog = fromEdt(() -> new ConnectionErrorDialog(frame, prompts.connectionErrors.get(0).profile(), failure));
        UiTestSupport.paintWindow(dialog, "T2.4-tempo-scaduto.png");
        onEdt(dialog::dispose);
        UiTestSupport.writeText("T2.4-tempo-scaduto.txt", "T2.4 (b) — host irraggiungibile " + UNREACHABLE + ":3306, senza annullare\n"
                + "diagnosi dopo " + elapsed + " ms (limite 10000 ms)\n"
                + "causa: " + failure.cause() + "\n"
                + "messaggio: " + failure.message() + "\n"
                + "originale: " + failure.originalDetail() + "\n"
                + "latenza massima di un evento sull'EDT durante l'attesa: " + maxLatencyMillis + " ms\n"
                + "schermata: T2.4-tempo-scaduto.png\n");
    }
}
