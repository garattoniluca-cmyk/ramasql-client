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

import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.app.connection.ConnectionController;
import it.ramasql.app.connection.ProfileDialog;
import it.ramasql.app.settings.SettingsController;
import it.ramasql.core.connection.ConnectionAttempt;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ProfileStore;
import it.ramasql.core.connection.Session;

/** Dalla tessera alla connessione, contro i server veri: barra di stato, una connessione per volta, disconnessione, prova. */
@Tag("step2")
@Tag("ui")
class ConnectionFlowTest {

    @TempDir
    Path dataDir;

    private App app;

    @BeforeAll
    static void lookAndFeel() {
        UiTestSupport.requireRedirectedAppData();
        UiTestSupport.setupLookAndFeel();
    }

    @AfterEach
    void closeEverything() {
        if (app != null) {
            onEdt(() -> {
                app.connections().shutdown();
                app.frame().dispose();
            });
        }
    }

    private App start(FakePrompts prompts, TestServer... servers) throws Exception {
        ProfileStore store = new ProfileStore(dataDir);
        for (TestServer server : servers) {
            store.add(server.profile(server.label() + " locale", ""));
        }
        app = fromEdt(() -> App.create(dataDir, prompts));
        return app;
    }

    @ParameterizedTest
    @EnumSource(TestServer.class)
    void clicSullaTessera_password_connessione_barraDiStato(TestServer server) throws Exception {
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> server.password();
        MainFrame frame = start(prompts, server).frame();
        assertEquals("Mai usata", fromEdt(() -> ShellLayoutTest.labels(frame.homePanel().profileTiles().get(0)).get(2)));

        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
        waitUntil("area di lavoro visibile", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);

        String expectedServer = server.isMariaDb() ? "MariaDB 11." : "MySQL 8.";
        onEdt(() -> {
            assertEquals(List.of(server.label() + " locale"), prompts.passwordRequests, "la password si chiede alla connessione");
            assertTrue(frame.statusServer().getText().startsWith(expectedServer), frame.statusServer().getText());
            assertEquals(app.connections().session().serverInfo().displayName(), frame.statusServer().getText());
            assertTrue(frame.statusConnection().getText().contains("«" + server.label() + " locale»"), frame.statusConnection().getText());
            assertTrue(frame.statusConnection().getText().contains("ramasql_test@127.0.0.1"), frame.statusConnection().getText());
            assertEquals("Nessun catalogo scelto", frame.statusCatalog().getText());
            assertEquals("Disconnetti", frame.connectButton().getText());
            // le tre zone sono la schermata visibile (CardLayout: solo la carta mostrata è visibile), le tessere no
            assertTrue(frame.sqlPanelSplit().isVisible(), "area di lavoro a tre zone visibile");
            assertFalse(frame.homePanel().isVisible(), "la schermata a tessere non è più visibile");
            assertTrue(SwingUtilities.isDescendingFrom(frame.navigatorZone(), frame.sqlPanelSplit()),
                    "il navigatore sta dentro l'area di lavoro mostrata");
        });
        if (server.isMariaDb()) {
            UiTestSupport.paintWindow(frame, "shell.png");
        }
        Session session = app.connections().session();

        onEdt(() -> frame.connectButton().doClick()); // ora è «Disconnetti»

        onEdt(() -> {
            assertTrue(prompts.confirmations.isEmpty(), "senza schede aperte si disconnette senza domande");
            assertEquals(MainFrame.Screen.HOME, frame.screen());
            assertNull(app.connections().session());
            assertEquals("Nessuna connessione aperta", frame.statusConnection().getText());
            assertEquals("", frame.statusServer().getText());
            assertEquals("Connetti", frame.connectButton().getText());
            // la tessera ora ricorda che server era
            assertTrue(ShellLayoutTest.labels(frame.homePanel().profileTiles().get(0)).get(2).startsWith(expectedServer));
        });
        waitUntil("sessione chiusa", 5_000, session::isClosed);
        assertTrue(new ProfileStore(dataDir).profiles().get(0).lastServer().displayName().startsWith(expectedServer));
        assertTrue(prompts.connectionErrors.isEmpty() && prompts.errors.isEmpty());
    }

    @Test
    void ilCatalogoPredefinitoCompareNellaBarraDiStato() throws Exception {
        // «information_schema» esiste su ogni server ed è leggibile da tutti: non serve creare nulla
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> TestServer.MYSQL.password();
        ProfileStore store = new ProfileStore(dataDir);
        store.add(TestServer.MYSQL.profile("Con catalogo", "information_schema"));
        app = fromEdt(() -> App.create(dataDir, prompts));
        MainFrame frame = app.frame();

        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
        waitUntil("area di lavoro visibile", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);

        assertEquals("Catalogo: information_schema", fromEdt(() -> frame.statusCatalog().getText()));
    }

    /**
     * DESIGN §3.1: una connessione attiva per volta; cambiare connessione chiude le schede della precedente, con
     * conferma. Tutto dall'INTERFACCIA: pulsante «Disconnetti» (con una scheda aperta chiede conferma), poi la tessera
     * dell'altro server. Mentre si è connessi le tessere non si vedono e il pulsante non offre altri profili: l'unico
     * percorso per cambiare è questo.
     */
    @Test
    void unaSolaConnessionePerVolta_cambiareDallInterfacciaChiedeConfermaEChiudeLeSchede() throws Exception {
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> p.name().startsWith("MariaDB") ? TestServer.MARIADB.password() : TestServer.MYSQL.password();
        MainFrame frame = start(prompts, TestServer.MARIADB, TestServer.MYSQL).frame();
        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
        waitUntil("connesso a MariaDB", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);
        Session first = app.connections().session();
        assertTrue(first.serverInfo().isMariaDb());
        onEdt(() -> {
            assertFalse(frame.homePanel().isVisible(), "connessi, le tessere degli altri profili non sono raggiungibili");
            assertEquals("Disconnetti", frame.connectButton().getText(), "il pulsante non apre l'elenco dei profili");
            // una scheda aperta nell'area di lavoro (dallo Step 3 le apriranno navigatore ed editor)
            frame.workTabs().addTab("Query 1", new JPanel());
        });

        // «Disconnetti», ma l'utente ci ripensa: non cambia nulla
        prompts.confirmAnswer = false;
        onEdt(() -> frame.connectButton().doClick());
        onEdt(() -> {
            assertEquals(1, prompts.confirmations.size());
            String question = prompts.confirmations.get(0);
            assertTrue(question.contains("«MariaDB locale»") && question.contains("1 schede aperte")
                    && question.contains("[Disconnetti]"), question);
            assertEquals(MainFrame.Screen.WORKSPACE, frame.screen());
            assertEquals(first, app.connections().session());
            assertFalse(first.isClosed());
            assertEquals(1, frame.workTabs().getTabCount(), "la scheda resta aperta");
        });

        // «Disconnetti» confermato: sessione chiusa, schede chiuse, di nuovo le tessere
        prompts.confirmAnswer = true;
        onEdt(() -> frame.connectButton().doClick());
        onEdt(() -> {
            assertEquals(2, prompts.confirmations.size());
            assertEquals(MainFrame.Screen.HOME, frame.screen());
            assertEquals(0, frame.workTabs().getTabCount(), "le schede della connessione precedente si chiudono");
            assertNull(app.connections().session());
        });
        waitUntil("prima sessione chiusa", 5_000, first::isClosed);
        waitUntil("connessioni della prima sessione chiuse", 5_000, () -> isClosed(first));

        // dalla tessera dell'altro server si apre la nuova connessione
        onEdt(() -> frame.homePanel().profileTiles().get(1).doClick());
        waitUntil("connesso a MySQL", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);
        onEdt(() -> {
            assertTrue(app.connections().session().serverInfo().isMySql());
            assertTrue(frame.statusServer().getText().startsWith("MySQL 8."), frame.statusServer().getText());
            assertEquals(List.of("MariaDB locale", "MySQL locale"), prompts.passwordRequests);
        });
    }

    private static boolean isClosed(Session s) {
        try {
            return s.mainConnection().isClosed() && s.serviceConnection().isClosed();
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * La barra di stato mostra il catalogo letto DENTRO l'apertura della sessione: dopo la consegna il controller non
     * parla più con il server. Controprova: la connessione principale viene chiusa subito dopo l'apertura, prima
     * della consegna; se il controller interrogasse il server il catalogo non comparirebbe (e l'EDT rischierebbe di
     * aspettare la rete).
     */
    @Test
    void ilCatalogoDellaBarraDiStatoArrivaConLaSessioneSenzaAltreChiamateDiRete() throws Exception {
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> TestServer.MARIADB.password();
        ProfileStore store = new ProfileStore(dataDir);
        store.add(TestServer.MARIADB.profile("Con catalogo", "information_schema"));
        ConnectionAttempt.Opener openThenCutMainConnection = (profile, password, deadline) -> {
            Session opened = ConnectionAttempt.SERVER.open(profile, password, deadline);
            try {
                opened.mainConnection().close();
            } catch (java.sql.SQLException e) {
                throw new AssertionError(e);
            }
            return opened;
        };
        ConnectionController controller = new ConnectionController(store, prompts, openThenCutMainConnection);
        MainFrame frame = fromEdt(() -> {
            MainFrame f = new MainFrame(controller, new SettingsController(dataDir, prompts), prompts);
            controller.attach(f);
            return f;
        });
        try {
            onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
            waitUntil("area di lavoro visibile", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);
            assertEquals("Catalogo: information_schema", fromEdt(() -> frame.statusCatalog().getText()));
            assertEquals("information_schema", controller.session().openedCatalog());
            assertTrue(prompts.errors.isEmpty() && prompts.connectionErrors.isEmpty());
        } finally {
            onEdt(() -> {
                controller.shutdown();
                frame.dispose();
            });
        }
    }

    /** Chiusura del programma: la sessione si chiude PRIMA che la finestra sparisca (e prima di System.exit), entro 2 s. */
    @Test
    void chiudendoLaFinestraLaSessioneEGiaChiusaQuandoLaChiusuraRitorna() throws Exception {
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> TestServer.MARIADB.password();
        MainFrame frame = start(prompts, TestServer.MARIADB).frame();
        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
        waitUntil("connesso", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);
        Session session = app.connections().session();

        long start = System.nanoTime();
        onEdt(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))); // come File → Esci o la X
        long millis = (System.nanoTime() - start) / 1_000_000;

        // nessuna attesa: appena la chiusura ritorna, le due connessioni sono già chiuse
        assertTrue(session.isClosed());
        assertTrue(isClosed(session), "connessione principale e di servizio già chiuse");
        assertNull(app.connections().session());
        assertTrue(millis < ConnectionController.SHUTDOWN_WAIT_MILLIS + 500, "chiusura in " + millis + " ms");
    }

    @Test
    void seLUtenteAnnullaLaPasswordNonSuccedeNulla() throws Exception {
        FakePrompts prompts = new FakePrompts(); // la password predefinita del finto è «annullato»
        MainFrame frame = start(prompts, TestServer.MARIADB).frame();

        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());

        onEdt(() -> {
            assertEquals(MainFrame.Screen.HOME, frame.screen());
            assertFalse(app.connections().isConnecting());
            assertEquals(1, prompts.passwordRequests.size());
        });
    }

    @ParameterizedTest
    @EnumSource(TestServer.class)
    void provaConnessioneDallaFinestraDelProfilo(TestServer server) throws Exception {
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> server.password();
        start(prompts);
        ProfileDialog dialog = fromEdt(() -> new ProfileDialog(app.frame(), server.profile("Da provare", ""), app.connections()));
        JButton test = dialog.form().testButton();

        onEdt(test::doClick);
        assertEquals("Annulla prova", fromEdt(test::getText), "durante la prova lo stesso pulsante annulla");
        waitUntil("esito della prova", 15_000, () -> test.getText().equals("Prova connessione"));

        String status = fromEdt(() -> dialog.form().testStatusLabel().getText());
        assertTrue(status.startsWith("Connessione riuscita: " + (server.isMariaDb() ? "MariaDB 11." : "MySQL 8.")), status);
        assertNull(app.connections().session(), "la prova non apre una sessione");

        // stessa diagnosi della connessione vera: password sbagliata → finestra d'errore con il codice 1045
        prompts.password = p -> "password-sbagliata-di-prova".toCharArray();
        onEdt(test::doClick);
        waitUntil("errore mostrato", 15_000, () -> !prompts.connectionErrors.isEmpty());
        onEdt(() -> {
            assertEquals(ConnectionErrorCause.ACCESS_DENIED, prompts.connectionErrors.get(0).failure().cause());
            assertEquals(1045, prompts.connectionErrors.get(0).failure().errorCode());
            assertEquals("Connessione non riuscita.", dialog.form().testStatusLabel().getText());
            assertEquals("Prova connessione", test.getText());
            dialog.dispose();
        });
    }
}
