/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.connection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import it.ramasql.app.Prompts;
import it.ramasql.app.Texts;
import it.ramasql.core.connection.ConnectionAttempt;
import it.ramasql.core.connection.ConnectionAttempt.Outcome;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ProfileStore;
import it.ramasql.core.connection.Session;

/**
 * Logica delle connessioni, separata dalle finestre: profili (nuovo, modifica, duplica, elimina, importa,
 * esporta), connessione annullabile, <strong>una sola connessione attiva per volta</strong>, disconnessione.
 * <p>Tutti i metodi si chiamano sull'EDT e non aspettano mai la rete (tranne {@link #shutdown()}, che alla chiusura
 * del programma aspetta al massimo {@value #SHUTDOWN_WAIT_MILLIS} ms): il lavoro del driver sta in
 * {@link ConnectionAttempt}, su un thread a parte; l'esito torna sull'EDT. Le finestre modali passano da
 * {@link Prompts}, così nei test si pilota tutto senza bloccare.
 */
public final class ConnectionController {

    /** Tempo massimo che la chiusura del programma concede alla chiusura della sessione. */
    public static final long SHUTDOWN_WAIT_MILLIS = 2000;

    /** Esito di «Prova connessione», già pronto da mostrare. */
    public record TestResult(boolean finished, boolean success, String text) {
    }

    /**
     * Una «Prova connessione» in corso. {@link #cancel()} è il pulsante «Annulla prova» (l'esito «Prova annullata»
     * arriva come sempre); {@link #abandon()} si usa quando la finestra del profilo si chiude: la prova si annulla e
     * <strong>nessun esito</strong> viene più mostrato, nemmeno se era già in viaggio.
     */
    public static final class TestHandle {
        private final ConnectionAttempt attempt;
        private final AtomicBoolean abandoned;

        private TestHandle(ConnectionAttempt attempt, AtomicBoolean abandoned) {
            this.attempt = attempt;
            this.abandoned = abandoned;
        }

        public void cancel() {
            attempt.cancel();
        }

        public void abandon() {
            abandoned.set(true);
            attempt.cancel();
        }
    }

    private final ProfileStore store;
    private final Prompts prompts;
    private final ConnectionAttempt.Opener opener;
    private ShellView view;
    private Session session;
    private ConnectionAttempt attempt;

    public ConnectionController(ProfileStore store, Prompts prompts) {
        this(store, prompts, ConnectionAttempt.SERVER);
    }

    /** Con un apritore di sessione diverso da quello vero: per i test. */
    public ConnectionController(ProfileStore store, Prompts prompts, ConnectionAttempt.Opener opener) {
        this.store = store;
        this.prompts = prompts;
        this.opener = opener;
    }

    /** Collega la finestra principale e mostra la schermata iniziale. */
    public void attach(ShellView shellView) {
        this.view = shellView;
        view.showHome(store.profiles());
    }

    public List<ConnectionProfile> profiles() {
        return store.profiles();
    }

    public ProfileStore store() {
        return store;
    }

    /** Sessione aperta, oppure {@code null}. */
    public Session session() {
        return session;
    }

    public boolean isConnected() {
        return session != null;
    }

    public boolean isConnecting() {
        return attempt != null;
    }

    // ------------------------------------------------------------------ connessione

    /**
     * Clic su una tessera: chiede la password e avvia il tentativo. Ritorna subito. Se c'è già una connessione aperta
     * (l'interfaccia di oggi non lo permette: le tessere si vedono solo da disconnessi) si chiede conferma, perché la
     * precedente verrà chiusa con le sue schede.
     */
    public void connect(ConnectionProfile profile) {
        if (attempt != null) {
            return;
        }
        if (session != null && !prompts.confirm(Texts.get("connect.replace.title"),
                Texts.get("connect.replace.message", session.profile().name(), profile.name()),
                Texts.get("connect.replace.confirm"))) {
            return;
        }
        char[] password = prompts.askPassword(profile);
        if (password == null) {
            return;
        }
        closeSession();
        view.showConnecting(profile);
        attempt = ConnectionAttempt.start(profile, password, outcome -> deliver(profile, outcome), false, opener);
        Arrays.fill(password, '\0');
    }

    /** Pulsante Annulla durante l'attesa: si torna subito alla schermata iniziale, senza aspettare il driver. */
    public void cancelConnecting() {
        ConnectionAttempt running = attempt;
        if (running != null) {
            running.cancel();
        }
    }

    /**
     * Pulsante «Disconnetti»: se nell'area di lavoro ci sono schede aperte chiede conferma (verranno chiuse), poi
     * chiude la sessione e torna alla schermata a tessere, da cui si sceglie un'altra connessione.
     *
     * @return {@code false} se l'utente ha rinunciato
     */
    public boolean disconnect() {
        if (session != null) {
            int tabs = view.openTabCount();
            if (tabs > 0 && !prompts.confirm(Texts.get("disconnect.title"),
                    Texts.get("disconnect.message", session.profile().name(), tabs),
                    Texts.get("disconnect.confirm"))) {
                return false;
            }
        }
        closeSession();
        view.showHome(store.profiles());
        return true;
    }

    /**
     * Chiusura del programma: annulla un tentativo in corso e chiude la sessione <strong>aspettando</strong> (al
     * massimo {@value #SHUTDOWN_WAIT_MILLIS} ms), così il server vede le connessioni chiuse prima che il processo
     * finisca.
     */
    public void shutdown() {
        cancelConnecting();
        Session old = session;
        session = null;
        if (old != null) {
            Thread closer = new Thread(old::close, "ramasql-chiusura-sessione");
            closer.setDaemon(true);
            closer.start();
            try {
                closer.join(SHUTDOWN_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Chiamato dal thread del tentativo (o dall'EDT per l'annullamento). <strong>Nessuna chiamata di rete</strong>:
     * tutto ciò che serve a mostrare la sessione (server, catalogo) è già stato letto dentro la scadenza.
     */
    private void deliver(ConnectionProfile profile, Outcome outcome) {
        if (SwingUtilities.isEventDispatchThread()) {
            handle(profile, outcome);
        } else {
            SwingUtilities.invokeLater(() -> handle(profile, outcome));
        }
    }

    private void handle(ConnectionProfile profile, Outcome outcome) {
        attempt = null;
        switch (outcome) {
            case Outcome.Connected connected -> {
                session = connected.session();
                rememberServer(profile, connected.session());
                view.showConnected(session, session.openedCatalog());
            }
            case Outcome.Failed failed -> {
                view.showHome(store.profiles());
                prompts.showConnectionError(profile, failed.failure());
            }
            case Outcome.Cancelled cancelled -> view.showHome(store.profiles());
            case Outcome.Tested tested -> view.showHome(store.profiles());
        }
    }

    /** Sulla tessera resta scritto che server era, l'ultima volta. */
    private void rememberServer(ConnectionProfile profile, Session opened) {
        if (store.byId(profile.id()) == null) {
            return;
        }
        try {
            store.update(store.byId(profile.id()).withLastServer(opened.serverInfo()));
        } catch (IOException e) {
            prompts.showError(Texts.get("profiles.save.error.title"), e.getMessage());
        }
    }

    private void closeSession() {
        Session old = session;
        session = null;
        if (old != null) {
            Thread closer = new Thread(old::close, "ramasql-chiusura-sessione"); // chiudere parla col server: fuori dall'EDT
            closer.setDaemon(true);
            closer.start();
        }
    }

    // ------------------------------------------------------------------ prova connessione

    /**
     * «Prova connessione» della finestra del profilo: chiede la password e prova senza bloccare.
     *
     * @return la prova in corso (per annullarla o abbandonarla), oppure {@code null} se l'utente non ha dato la password
     */
    public TestHandle testConnection(ConnectionProfile draft, Consumer<TestResult> callback) {
        char[] password = prompts.askPassword(draft);
        if (password == null) {
            return null;
        }
        AtomicBoolean abandoned = new AtomicBoolean();
        ConnectionAttempt test = ConnectionAttempt.start(draft, password, outcome -> {
            Runnable report = () -> {
                if (!abandoned.get()) {
                    reportTest(draft, outcome, callback);
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                report.run();
            } else {
                SwingUtilities.invokeLater(report);
            }
        }, true, opener);
        Arrays.fill(password, '\0');
        return new TestHandle(test, abandoned);
    }

    private void reportTest(ConnectionProfile draft, Outcome outcome, Consumer<TestResult> callback) {
        switch (outcome) {
            case Outcome.Tested tested -> callback.accept(new TestResult(true, true,
                    Texts.get("profile.test.success", tested.serverInfo().displayName(), tested.millis())));
            case Outcome.Failed failed -> {
                callback.accept(new TestResult(true, false, Texts.get("profile.test.failed")));
                prompts.showConnectionError(draft, failed.failure());
            }
            case Outcome.Cancelled cancelled -> callback.accept(new TestResult(true, false, Texts.get("profile.test.cancelled")));
            case Outcome.Connected connected -> connected.session().close();
        }
    }

    // ------------------------------------------------------------------ profili

    public void newProfile() {
        ConnectionProfile created = prompts.editProfile(null, this);
        if (created != null) {
            change(() -> store.add(created));
        }
    }

    public void editProfile(ConnectionProfile profile) {
        ConnectionProfile edited = prompts.editProfile(profile, this);
        if (edited != null) {
            change(() -> store.update(edited));
        }
    }

    public void duplicateProfile(ConnectionProfile profile) {
        change(() -> store.add(profile.duplicate(store.freeName(Texts.get("profile.copyName", profile.name())))));
    }

    public void deleteProfile(ConnectionProfile profile) {
        if (prompts.confirm(Texts.get("profile.delete.title"), Texts.get("profile.delete.message", profile.name()),
                Texts.get("profile.delete.confirm"))) {
            change(() -> store.remove(profile.id()));
        }
    }

    /**
     * File → Importa profili…: se il file contiene profili con lo stesso nome di profili già presenti (senza badare
     * alle maiuscole), prima chiede conferma elencando quelli che verranno sostituiti; se l'utente rinuncia, nulla
     * cambia.
     */
    public void importProfiles() {
        Path source = prompts.chooseFileToOpen(Texts.get("profiles.import.title"));
        if (source == null) {
            return;
        }
        change(() -> {
            List<String> replaced = store.namesReplacedBy(source);
            if (!replaced.isEmpty() && !prompts.confirm(Texts.get("profiles.import.replace.title"),
                    Texts.get("profiles.import.replace.message", replaced.size(), "«" + String.join("», «", replaced) + "»"),
                    Texts.get("profiles.import.replace.confirm"))) {
                return;
            }
            ProfileStore.ImportResult result = store.importFrom(source);
            prompts.showInfo(Texts.get("profiles.import.title"),
                    Texts.get("profiles.import.done", result.added(), result.updated()));
        });
    }

    public void exportProfiles() {
        if (store.profiles().isEmpty()) {
            prompts.showInfo(Texts.get("profiles.export.title"), Texts.get("profiles.export.none"));
            return;
        }
        Path target = prompts.chooseFileToSave(Texts.get("profiles.export.title"), Texts.get("profiles.export.fileName"));
        if (target == null) {
            return;
        }
        change(() -> {
            store.exportTo(target);
            prompts.showInfo(Texts.get("profiles.export.title"),
                    Texts.get("profiles.export.done", store.profiles().size(), target.getFileName()));
        });
    }

    private interface StoreChange {
        void run() throws IOException;
    }

    private void change(StoreChange change) {
        try {
            change.run();
        } catch (IOException e) {
            prompts.showError(Texts.get("profiles.save.error.title"), e.getMessage());
        }
        if (session == null && attempt == null && view != null) {
            view.showHome(store.profiles());
        }
    }
}
