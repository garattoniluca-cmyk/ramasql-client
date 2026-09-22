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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.connection.ConnectionController;
import it.ramasql.app.connection.ProfileDialog;
import it.ramasql.core.connection.ConnectionAttempt;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ConnectionFailedException;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ProfileStore;

/**
 * Finestra del profilo chiusa durante «Prova connessione»: la prova si annulla e il suo esito non fa comparire
 * nessuna finestra d'errore «dal nulla». Apritore di sessione finto (nessun server): il tempo lo decide il test.
 */
@Tag("step2")
@Tag("ui")
class ProfileTestOnCloseTest {

    private static final ConnectionFailure ACCESS_DENIED = new ConnectionFailure(ConnectionErrorCause.ACCESS_DENIED,
            "Il server ha rifiutato l'accesso.", 1045, "28000", "Access denied for user 'studente'");

    @TempDir
    Path dataDir;

    @BeforeAll
    static void lookAndFeel() {
        UiTestSupport.requireRedirectedAppData();
        UiTestSupport.setupLookAndFeel();
    }

    /** Apritore che fallisce (password sbagliata) quando il test lo lascia andare. */
    private static final class FailingOpener implements ConnectionAttempt.Opener {
        final CountDownLatch release;
        final AtomicInteger calls = new AtomicInteger();

        FailingOpener(boolean immediately) {
            release = new CountDownLatch(immediately ? 0 : 1);
        }

        @Override
        public it.ramasql.core.connection.Session open(ConnectionProfile p, char[] password, long deadline)
                throws ConnectionFailedException {
            calls.incrementAndGet();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new ConnectionFailedException(ACCESS_DENIED, null);
        }
    }

    private ProfileDialog dialog(FakePrompts prompts, FailingOpener opener) throws Exception {
        ConnectionController controller = new ConnectionController(new ProfileStore(dataDir), prompts, opener);
        prompts.password = p -> "qualsiasi".toCharArray();
        return fromEdt(() -> new ProfileDialog(null,
                ConnectionProfile.create("Aula", "db.example", 3306, "studente", "", ""), controller));
    }

    @Test
    void controprova_conLaFinestraApertaLErroreSiMostra() throws Exception {
        FakePrompts prompts = new FakePrompts();
        FailingOpener opener = new FailingOpener(true);
        ProfileDialog dialog = dialog(prompts, opener);

        onEdt(() -> dialog.form().testButton().doClick());
        waitUntil("errore mostrato", 5_000, () -> !prompts.connectionErrors.isEmpty());

        assertEquals(1045, prompts.connectionErrors.get(0).failure().errorCode());
        assertEquals("Connessione non riuscita.", fromEdt(() -> dialog.form().testStatusLabel().getText()));
        onEdt(dialog::dispose);
    }

    @Test
    void annullaDellaFinestraDuranteLaProva_nessunErroreDalNulla() throws Exception {
        FakePrompts prompts = new FakePrompts();
        FailingOpener opener = new FailingOpener(false);
        ProfileDialog dialog = dialog(prompts, opener);

        onEdt(() -> dialog.form().testButton().doClick());
        waitUntil("prova partita", 5_000, () -> opener.calls.get() == 1);
        assertTrue(fromEdt(() -> dialog.form().isTesting()));

        onEdt(() -> button(dialog, "dialog.cancel").doClick()); // «Annulla» della finestra: la chiude
        assertFalse(fromEdt(() -> dialog.form().isTesting()), "la prova è stata annullata con la finestra");

        opener.release.countDown(); // il «driver» risponde adesso: password sbagliata
        Thread.sleep(300);
        onEdt(() -> { }); // tutto ciò che era in coda sull'EDT è stato servito
        assertTrue(prompts.connectionErrors.isEmpty(), "nessuna finestra d'errore dopo la chiusura");
    }

    @Test
    void esitoGiaInCodaQuandoLaFinestraSiChiude_nessunErroreDalNulla() throws Exception {
        FakePrompts prompts = new FakePrompts();
        FailingOpener opener = new FailingOpener(true);
        ProfileDialog dialog = dialog(prompts, opener);

        // tutto in un solo evento dell'EDT: la prova parte e fallisce subito, il suo esito si mette in coda
        // sull'EDT, e prima che venga servito l'utente chiude la finestra (X / Esc / Annulla = dispose)
        onEdt(() -> {
            dialog.form().testButton().doClick();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dialog.dispose();
        });
        onEdt(() -> { });
        Thread.sleep(100);
        onEdt(() -> { });

        assertEquals(1, opener.calls.get());
        assertTrue(prompts.connectionErrors.isEmpty(), "l'esito arrivato dopo la chiusura si scarta");
    }

    private static JButton button(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && name.equals(b.getName())) {
                return b;
            }
            if (c instanceof Container inner) {
                try {
                    return button(inner, name);
                } catch (AssertionError notHere) {
                    // si cerca altrove
                }
            }
        }
        throw new AssertionError("pulsante assente: " + name);
    }
}
