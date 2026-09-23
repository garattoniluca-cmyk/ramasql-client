/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step3;

import static it.ramasql.app.step3.Step3Ui.fromEdt;
import static it.ramasql.app.step3.Step3Ui.onEdt;
import static it.ramasql.app.step3.Step3Ui.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.MainFrame;
import it.ramasql.app.workspace.SessionWorkspace;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlScript;

/**
 * Cablaggio della sessione: alla connessione nascono lettore dei metadati ed esecutore (con il limite di righe delle
 * impostazioni, che segue le modifiche); alla disconnessione si chiudono (l'esecutore non accetta più lavoro, il
 * navigatore si svuota); il registro appartiene alla finestra e resta alla connessione successiva.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class WorkspaceWiringTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Step3Ui.setup();
    }

    @ParameterizedTest
    @EnumSource(Step3Server.class)
    void connessioneCreaLettoreEdEsecutoreDisconnessioneLiChiude(Step3Server server) throws Exception {
        try (Step3App a = Step3App.connect(server, dataDir)) {
            SessionWorkspace first = fromEdt(() -> a.workspace());
            assertNotNull(first, "connessi: area di lavoro della sessione creata");
            assertSame(a.app.connections().session(), first.session());
            assertNotNull(first.reader());
            assertNotNull(first.executor());
            assertSame(a.log(), first.executor().log(), "l'esecutore alimenta il registro della finestra");
            assertEquals(AppSettings.DEFAULT_ROW_LIMIT, first.executor().rowLimit(), "limite righe dalle impostazioni");
            assertTrue(first.reader().queryCount() > 0, "il navigatore ha già letto i cataloghi con il lettore");

            // il limite di righe segue le impostazioni
            a.prompts.nextSettings = new AppSettings("it", 0, 250, dataDir.toString());
            onEdt(() -> a.app.settings().edit());
            assertEquals(250, first.executor().rowLimit());

            // un'istruzione eseguita finisce nel registro della finestra
            assertTrue(first.executor().run(SqlScript.of("Prova", "Test", "SELECT 1")).completed());
            waitUntil("riga nel Registro", Step3App.TIMEOUT, () -> a.panel().logTable().getRowCount() == 1);
            SqlLog log = a.log();

            // disconnessione: esecutore e navigatore chiusi, sessione chiusa
            onEdt(() -> a.app.connections().disconnect());
            assertNull(fromEdt(() -> a.workspace()));
            assertTrue(first.isClosed());
            first.awaitClosed(5_000);
            assertThrows(RejectedExecutionException.class,
                    () -> first.executor().submit(SqlScript.of("Dopo", "Test", "SELECT 1"), null),
                    "l'esecutore chiuso non accetta più istruzioni");
            assertNull(fromEdt(() -> a.nav().tree().getModel().getRoot()), "navigatore svuotato");
            waitUntil("sessione chiusa", Step3App.TIMEOUT, () -> first.session().isClosed());
            assertEquals(MainFrame.Screen.HOME, fromEdt(() -> a.frame().screen()));

            // nuova connessione: nuova area di lavoro, stesso registro (con la riga di prima)
            onEdt(() -> a.frame().homePanel().profileTiles().get(0).doClick());
            waitUntil("di nuovo connessi", Step3App.TIMEOUT, () -> a.frame().screen() == MainFrame.Screen.WORKSPACE);
            SessionWorkspace second = fromEdt(() -> a.workspace());
            assertNotSame(first, second);
            assertFalse(second.isClosed());
            assertSame(log, a.log());
            assertSame(log, second.executor().log());
            assertEquals(1, fromEdt(() -> a.panel().logTable().getRowCount()), "il registro resta tra le connessioni");
            assertEquals(250, second.executor().rowLimit(), "il limite salvato vale anche per la nuova connessione");
            a.waitIdle();
            Step3Ui.writeText("cablaggio-" + server.id() + ".txt", "Cablaggio della sessione su " + server.label() + "\n"
                    + "Connessione: SessionWorkspace creato (lettore + esecutore sullo stesso registro della finestra),"
                    + " limite righe " + AppSettings.DEFAULT_ROW_LIMIT + " → 250 dopo le impostazioni\n"
                    + "Disconnessione: esecutore chiuso (submit rifiutato), navigatore vuoto, sessione chiusa\n"
                    + "Riconnessione: nuovo SessionWorkspace, stesso registro (1 riga conservata), limite 250\n");
        }
    }
}
