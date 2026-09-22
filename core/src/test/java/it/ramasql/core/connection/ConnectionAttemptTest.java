/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link ConnectionAttempt} e la scadenza di {@link Session#open}, con un apritore di sessione finto: nessun server.
 */
@Tag("step2")
class ConnectionAttemptTest {

    private static final ConnectionProfile PROFILE =
            ConnectionProfile.create("Finto", "db.example", 3306, "studente", "biblioteca", "");

    /** Apritore finto e lento: aspetta il via del test, poi restituisce una sessione senza connessioni. */
    private static final class SlowOpener implements ConnectionAttempt.Opener {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Session> opened = new AtomicReference<>();
        final AtomicLong deadlineNanos = new AtomicLong();

        @Override
        public Session open(ConnectionProfile profile, char[] password, long deadline) {
            deadlineNanos.set(deadline);
            started.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Session s = Session.detached(profile, ServerInfo.parse("11.5.2-MariaDB"), profile.defaultCatalog());
            opened.set(s);
            return s;
        }
    }

    private static void waitFor(String what, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("non è successo entro 5 s: " + what);
            }
            Thread.sleep(5);
        }
    }

    @Test
    void unaConnessioneRiuscitaDopoLAnnullamentoVieneChiusaEMaiConsegnata() throws Exception {
        SlowOpener opener = new SlowOpener();
        List<ConnectionAttempt.Outcome> outcomes = new CopyOnWriteArrayList<>();
        ConnectionAttempt attempt = ConnectionAttempt.start(PROFILE, "x".toCharArray(), outcomes::add, false, opener);
        assertTrue(opener.started.await(5, TimeUnit.SECONDS));

        attempt.cancel();
        assertEquals(1, outcomes.size());
        assertInstanceOf(ConnectionAttempt.Outcome.Cancelled.class, outcomes.get(0));

        opener.release.countDown(); // ora il «driver» riesce a connettersi, ma è tardi
        waitFor("sessione aperta dall'apritore", () -> opener.opened.get() != null);
        Session late = opener.opened.get();
        waitFor("sessione tardiva chiusa", late::isClosed);

        Thread.sleep(100);
        assertEquals(1, outcomes.size(), "l'esito arriva una sola volta: la sessione tardiva non si consegna");
        assertTrue(attempt.isDone());
    }

    @Test
    void controprova_senzaAnnullareLaSessioneSiConsegnaAperta() throws Exception {
        SlowOpener opener = new SlowOpener();
        List<ConnectionAttempt.Outcome> outcomes = new CopyOnWriteArrayList<>();
        ConnectionAttempt.start(PROFILE, "x".toCharArray(), outcomes::add, false, opener);
        opener.release.countDown();
        waitFor("esito consegnato", () -> !outcomes.isEmpty());

        ConnectionAttempt.Outcome.Connected connected = assertInstanceOf(ConnectionAttempt.Outcome.Connected.class, outcomes.get(0));
        assertSame(opener.opened.get(), connected.session());
        Thread.sleep(100);
        assertFalse(connected.session().isClosed(), "la sessione consegnata è di chi la riceve: resta aperta");
        assertEquals("biblioteca", connected.session().openedCatalog(), "il catalogo arriva con la sessione");
        connected.session().close();
    }

    @Test
    void lApritoreRiceveLaScadenzaComplessivaDiDieciSecondi() throws Exception {
        SlowOpener opener = new SlowOpener();
        long before = System.nanoTime();
        ConnectionAttempt attempt = ConnectionAttempt.start(PROFILE, "x".toCharArray(), o -> { }, true, opener);
        assertTrue(opener.started.await(5, TimeUnit.SECONDS));
        long after = System.nanoTime();
        long deadline = opener.deadlineNanos.get();
        long tenSeconds = TimeUnit.SECONDS.toNanos(ConnectionAttempt.DEADLINE_SECONDS);
        assertTrue(deadline >= before + tenSeconds && deadline <= after + tenSeconds,
                "scadenza passata all'apritore = inizio del tentativo + 10 s");
        attempt.cancel();
        opener.release.countDown();
    }

    @Test
    void laSecondaConnessioneHaSoloIlTempoCheResta() throws Exception {
        long now = System.nanoTime();
        assertEquals(Session.CONNECT_TIMEOUT_MS, Session.remainingMillis(now + TimeUnit.SECONDS.toNanos(10), now),
                "con tutto il tempo davanti: il massimo per una connessione");
        // la prima connessione ha usato 8 s dei 10: alla seconda ne restano 2, non altri 8
        assertEquals(2000, Session.remainingMillis(now + TimeUnit.SECONDS.toNanos(10), now + TimeUnit.SECONDS.toNanos(8)));
        assertEquals(750, Session.remainingMillis(now + TimeUnit.MILLISECONDS.toNanos(750), now));
        assertThrows(Session.DeadlineExpired.class,
                () -> Session.remainingMillis(now + TimeUnit.MILLISECONDS.toNanos(10), now), "residuo troppo piccolo");
        assertThrows(Session.DeadlineExpired.class,
                () -> Session.remainingMillis(now, now + TimeUnit.SECONDS.toNanos(1)), "scadenza già passata");
    }

    @Test
    void sessionOpenNonSuperaLaScadenzaIndicata() {
        // indirizzo non instradabile: il socket non riceve mai risposta; la scadenza è di 1,5 s, non 8 né 10
        ConnectionProfile unreachable = ConnectionProfile.create("Irraggiungibile", "10.255.255.1", 3306, "x", "", "");
        long start = System.nanoTime();
        ConnectionFailedException e = assertThrows(ConnectionFailedException.class,
                () -> Session.open(unreachable, "x".toCharArray(), start + TimeUnit.MILLISECONDS.toNanos(1500)));
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertEquals(ConnectionErrorCause.TIMEOUT, e.failure().cause(), e.failure().originalDetail());
        assertTrue(millis < 3000, "Session.open rispetta la scadenza complessiva: " + millis + " ms");
    }

    @Test
    void conLaScadenzaGiaPassataNonSiTentaNemmeno() {
        long start = System.nanoTime();
        ConnectionFailedException e = assertThrows(ConnectionFailedException.class,
                () -> Session.open(PROFILE, "x".toCharArray(), start - 1));
        assertEquals(ConnectionErrorCause.TIMEOUT, e.failure().cause());
        assertFalse(e.failure().originalText().matches(".*\\d+ second.*"),
                "nessun numero di secondi inventato: " + e.failure().originalText());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
    }
}
