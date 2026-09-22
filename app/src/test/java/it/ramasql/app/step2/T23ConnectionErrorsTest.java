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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.app.connection.ConnectionErrorDialog;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ProfileStore;

/**
 * T2.3 — cinque errori di connessione provocati davvero (host inesistente, porta chiusa, password errata, catalogo
 * senza permesso, catalogo inesistente), passando dal flusso vero dell'interfaccia: clic sulla tessera → password →
 * tentativo → finestra d'errore. Attesi: cinque messaggi distinti, in italiano, che dicono cosa correggere, con il
 * codice originale del server visibile.
 */
@Tag("step2")
@Tag("ui")
class T23ConnectionErrorsTest {

    private static final String WRONG_PASSWORD = "password-sbagliata-di-prova";

    /** Un caso: come si chiama, quale profilo lo provoca, che cosa ci si aspetta di leggere. */
    private record Case(String id, ConnectionProfile profile, boolean wrongPassword, ConnectionErrorCause cause, int code,
            List<String> mustMention) {
    }

    @TempDir
    Path dataDir;

    @BeforeAll
    static void lookAndFeel() {
        UiTestSupport.requireRedirectedAppData();
        UiTestSupport.setupLookAndFeel();
    }

    private static List<Case> cases(TestServer server) {
        ConnectionProfile ok = server.profile("x", "");
        return List.of(
                new Case("1-host-inesistente",
                        ConnectionProfile.create("Host inesistente", "host-che-non-esiste.invalid", ok.port(), ok.user(), "", ""),
                        false, ConnectionErrorCause.UNKNOWN_HOST, 0, List.of("host-che-non-esiste.invalid", "Host")),
                new Case("2-porta-chiusa",
                        ConnectionProfile.create("Porta chiusa", "127.0.0.1", 3399, ok.user(), "", ""),
                        false, ConnectionErrorCause.PORT_CLOSED, 0, List.of("3399", "Porta")),
                new Case("3-password-errata", server.profile("Password errata", ""),
                        true, ConnectionErrorCause.ACCESS_DENIED, 1045, List.of("password", ok.user())),
                new Case("4-catalogo-senza-permesso", server.profile("Catalogo senza permesso", "mysql"),
                        false, ConnectionErrorCause.CATALOG_ACCESS_DENIED, 1044, List.of("«mysql»", "permesso", "Catalogo")),
                new Case("5-catalogo-inesistente", server.profile("Catalogo inesistente", "ramasql_test_non_esiste"),
                        false, ConnectionErrorCause.UNKNOWN_CATALOG, 1049, List.of("«ramasql_test_non_esiste»", "non esiste", "Catalogo")));
    }

    @ParameterizedTest
    @EnumSource(TestServer.class)
    void t23_cinqueErroriProvocati_cinqueMessaggiDistintiInItalianoConIlCodiceOriginale(TestServer server) throws Exception {
        List<Case> cases = cases(server);
        ProfileStore store = new ProfileStore(dataDir);
        for (Case c : cases) {
            store.add(c.profile());
        }
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> p.name().equals("Password errata") ? WRONG_PASSWORD.toCharArray() : server.password();
        App app = fromEdt(() -> App.create(dataDir, prompts));
        MainFrame frame = app.frame();
        String suffix = server.name().toLowerCase();
        StringBuilder report = new StringBuilder("T2.3 — errori di connessione provocati contro " + server.label()
                + " (flusso: clic sulla tessera → password → tentativo → finestra d'errore)\n\n");
        Set<String> distinctMessages = new HashSet<>();
        List<ConnectionErrorCause> causes = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            int index = i;
            int shownBefore = prompts.connectionErrors.size();
            long start = System.nanoTime();

            onEdt(() -> frame.homePanel().profileTiles().get(index).doClick());
            waitUntil("finestra d'errore per " + c.id(), 15_000, () -> prompts.connectionErrors.size() > shownBefore);
            long millis = (System.nanoTime() - start) / 1_000_000;

            FakePrompts.ShownConnectionError shown = prompts.connectionErrors.get(shownBefore);
            ConnectionFailure failure = shown.failure();
            assertEquals(c.profile().name(), shown.profile().name());
            assertEquals(c.cause(), failure.cause(), c.id());
            assertEquals(c.code(), failure.errorCode(), c.id() + ": codice originale del server");
            for (String word : c.mustMention()) {
                assertTrue(failure.message().contains(word), c.id() + ": il messaggio deve nominare «" + word + "»: " + failure.message());
            }
            assertFalse(failure.originalText().isBlank(), "il testo originale c'è sempre");
            assertFalse(failure.message().contains(WRONG_PASSWORD) || failure.originalDetail().contains(WRONG_PASSWORD),
                    "la password non compare mai nei messaggi");
            onEdt(() -> {
                assertEquals(MainFrame.Screen.HOME, frame.screen(), "dopo l'errore si torna alle tessere");
                assertNull(app.connections().session());
            });

            // la finestra vera, con gli stessi dati: ciò che l'utente legge
            ConnectionErrorDialog dialog = fromEdt(() -> new ConnectionErrorDialog(frame, shown.profile(), failure));
            onEdt(() -> {
                assertEquals(failure.message(), dialog.explanationText());
                assertEquals(failure.originalDetail(), dialog.originalText());
                if (c.code() != 0) {
                    assertTrue(dialog.originalText().contains("Errore " + c.code()), dialog.originalText());
                }
            });
            UiTestSupport.paintWindow(dialog, "T2.3-" + suffix + "-" + c.id() + ".png");
            onEdt(dialog::dispose);

            distinctMessages.add(failure.message());
            causes.add(failure.cause());
            report.append(c.id()).append("  (").append(millis).append(" ms)\n")
                    .append("  profilo: ").append(c.profile().address())
                    .append(c.profile().hasDefaultCatalog() ? " catalogo " + c.profile().defaultCatalog() : "").append('\n')
                    .append("  causa: ").append(failure.cause()).append('\n')
                    .append("  messaggio: ").append(failure.message()).append('\n')
                    .append("  originale: ").append(failure.originalDetail()).append('\n')
                    .append("  schermata: T2.3-").append(suffix).append('-').append(c.id()).append(".png\n\n");
        }

        assertEquals(5, distinctMessages.size(), "cinque messaggi distinti");
        assertEquals(5, new HashSet<>(causes).size(), "cinque cause distinte");
        assertTrue(prompts.errors.isEmpty(), prompts.errors.toString());
        UiTestSupport.writeText("T2.3-" + suffix + ".txt", report.toString());
    }
}
