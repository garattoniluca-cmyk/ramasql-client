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

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tentativo di connessione <strong>asincrono e annullabile</strong>: il driver lavora su un thread a parte, chi
 * chiama non aspetta mai. Serve sia per connettersi ({@link #open}) sia per «Prova connessione» ({@link #test}).
 * <ul>
 *   <li>l'esito arriva <strong>una sola volta</strong> all'ascoltatore (su un thread qualsiasi: la UI lo riporta
 *       sull'EDT);</li>
 *   <li>{@link #cancel()} ritorna subito con esito {@link Outcome.Cancelled}; se il driver più tardi riesce
 *       comunque a connettersi, quella sessione viene chiusa in silenzio;</li>
 *   <li>dopo {@value #DEADLINE_SECONDS} secondi senza risposta l'esito è {@code TIMEOUT}, qualunque cosa stia
 *       facendo il driver; la stessa scadenza vale dentro {@link Session#open(ConnectionProfile, char[], long)}.</li>
 * </ul>
 */
public final class ConnectionAttempt {

    /** Tempo massimo complessivo di un tentativo, in secondi. */
    public static final int DEADLINE_SECONDS = 10;

    /** Esito di un tentativo. */
    public sealed interface Outcome {
        /** Connessione aperta: la sessione ora è di chi riceve l'esito. */
        record Connected(Session session) implements Outcome {
        }

        /** Prova riuscita: server riconosciuto, connessioni già richiuse. */
        record Tested(ServerInfo serverInfo, long millis) implements Outcome {
        }

        record Failed(ConnectionFailure failure) implements Outcome {
        }

        record Cancelled() implements Outcome {
        }
    }

    /**
     * Chi apre davvero la sessione: nel programma {@link Session#open(ConnectionProfile, char[], long)}; nei test
     * un apritore finto (lento, che fallisce…) per provare i casi limite senza server.
     */
    @FunctionalInterface
    public interface Opener {
        Session open(ConnectionProfile profile, char[] password, long deadlineNanos) throws ConnectionFailedException;
    }

    /** L'apritore vero. */
    public static final Opener SERVER = Session::open;

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ramasql-connessione-scadenza");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean done = new AtomicBoolean();
    private final Consumer<Outcome> listener;
    private volatile ScheduledFuture<?> deadline;

    private ConnectionAttempt(Consumer<Outcome> listener) {
        this.listener = listener;
    }

    /** Apre una sessione. */
    public static ConnectionAttempt open(ConnectionProfile profile, char[] password, Consumer<Outcome> listener) {
        return start(profile, password, listener, false, SERVER);
    }

    /** Prova la connessione: apre, riconosce il server, richiude. */
    public static ConnectionAttempt test(ConnectionProfile profile, char[] password, Consumer<Outcome> listener) {
        return start(profile, password, listener, true, SERVER);
    }

    /**
     * Avvia un tentativo con l'apritore indicato.
     *
     * @param testOnly vero per «Prova connessione»: la sessione aperta si richiude subito
     */
    public static ConnectionAttempt start(ConnectionProfile profile, char[] password, Consumer<Outcome> listener,
            boolean testOnly, Opener opener) {
        ConnectionAttempt attempt = new ConnectionAttempt(listener);
        char[] secret = password.clone(); // il chiamante può azzerare subito il suo array
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
        attempt.deadline = WATCHDOG.schedule(
                () -> attempt.complete(new Outcome.Failed(ConnectionErrorClassifier.timeout(profile))),
                DEADLINE_SECONDS, TimeUnit.SECONDS);
        Thread worker = new Thread(() -> attempt.run(profile, secret, testOnly, opener, deadlineNanos),
                "ramasql-connessione");
        worker.setDaemon(true);
        worker.start();
        return attempt;
    }

    /** Annulla: ritorna subito; l'ascoltatore riceve {@link Outcome.Cancelled} (se l'esito non era già arrivato). */
    public void cancel() {
        complete(new Outcome.Cancelled());
    }

    public boolean isDone() {
        return done.get();
    }

    private void run(ConnectionProfile profile, char[] secret, boolean testOnly, Opener opener, long deadlineNanos) {
        long start = System.nanoTime();
        Session session = null;
        Outcome outcome;
        try {
            session = opener.open(profile, secret, deadlineNanos);
            outcome = testOnly
                    ? new Outcome.Tested(session.serverInfo(), (System.nanoTime() - start) / 1_000_000)
                    : new Outcome.Connected(session);
        } catch (ConnectionFailedException e) {
            outcome = new Outcome.Failed(e.failure());
        } catch (RuntimeException e) {
            outcome = new Outcome.Failed(ConnectionErrorClassifier.classify(e, profile));
        } finally {
            Arrays.fill(secret, '\0');
        }
        boolean delivered = complete(outcome);
        if (session != null && (testOnly || !delivered)) {
            session.close(); // prova finita, oppure esito arrivato tardi (annullato o scaduto): nessuno la vuole più
        }
    }

    private boolean complete(Outcome outcome) {
        if (!done.compareAndSet(false, true)) {
            return false;
        }
        ScheduledFuture<?> d = deadline;
        if (d != null) {
            d.cancel(false);
        }
        listener.accept(outcome);
        return true;
    }
}
