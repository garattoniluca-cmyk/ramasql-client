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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JMenuItem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.app.connection.ProfileDialog;
import it.ramasql.core.connection.ProfileStore;
import it.ramasql.core.connection.Session;

/**
 * T2.6 — esporta profili → importa con un altro «utente Windows» (un'altra cartella dei dati) → tessere presenti →
 * connessione riuscita dopo aver digitato la password.<br>
 * T2.7 — dopo un ciclo d'uso completo, la password usata non si trova in nessun file della cartella dei dati né nel
 * file esportato.
 */
@Tag("step2")
@Tag("ui")
class T26T27ProfilesOnDiskTest {

    @TempDir
    Path temp;

    @BeforeAll
    static void lookAndFeel() {
        UiTestSupport.requireRedirectedAppData();
        UiTestSupport.setupLookAndFeel();
    }

    @Test
    void t26_esportaProfili_importaConUnAltroUtente_tessere_connessione() throws Exception {
        Path teacherDir = temp.resolve("utente-docente").resolve("RamaSQL");
        Path studentDir = temp.resolve("utente-studente").resolve("RamaSQL");
        Path exported = temp.resolve("chiavetta").resolve("connessioni-3A.json");
        Files.createDirectories(exported.getParent());

        // --- il docente: due profili, poi File → Esporta profili…
        ProfileStore teacherStore = new ProfileStore(teacherDir);
        teacherStore.add(TestServer.MARIADB.profile("Aula 3A - MariaDB", ""));
        teacherStore.add(TestServer.MYSQL.profile("Aula 3A - MySQL", ""));
        FakePrompts teacherPrompts = new FakePrompts();
        teacherPrompts.nextSaveFile = exported;
        App teacher = fromEdt(() -> App.create(teacherDir, teacherPrompts));
        onEdt(() -> menuItem(teacher.frame(), "Esporta profili…").doClick());
        assertTrue(Files.exists(exported));
        assertTrue(teacherPrompts.infos.get(0).contains("non contiene password"), teacherPrompts.infos.toString());
        onEdt(() -> teacher.frame().dispose());

        // --- lo studente: un altro utente, nessun profilo; File → Importa profili…
        FakePrompts studentPrompts = new FakePrompts();
        studentPrompts.nextOpenFile = exported;
        studentPrompts.password = p -> (p.name().contains("MariaDB") ? TestServer.MARIADB : TestServer.MYSQL).password();
        App student = fromEdt(() -> App.create(studentDir, studentPrompts));
        MainFrame frame = student.frame();
        assertEquals(0, fromEdt(() -> frame.homePanel().profileTiles().size()), "utente nuovo: nessuna tessera");

        onEdt(() -> menuItem(frame, "Importa profili…").doClick());

        List<String> tiles = fromEdt(() -> frame.homePanel().profileTiles().stream()
                .map(t -> ShellLayoutTest.labels(t).get(0)).toList());
        assertEquals(List.of("Aula 3A - MariaDB", "Aula 3A - MySQL"), tiles, "tessere presenti dopo l'importazione");
        assertTrue(studentPrompts.infos.get(0).contains("aggiunti: 2"), studentPrompts.infos.toString());
        assertTrue(Files.exists(studentDir.resolve("connessioni.json")), "profili salvati nella cartella dello studente");
        UiTestSupport.paintWindow(frame, "T2.6-tessere-importate.png");

        // --- e con la password digitata ci si connette, a tutti e due i server
        StringBuilder report = new StringBuilder("T2.6 — esporta → importa con un altro utente → connetti\n"
                + "file esportato: " + exported.getFileName() + " (" + Files.size(exported) + " byte)\n"
                + "tessere dopo l'importazione: " + tiles + "\n"
                + "messaggio dell'importazione: " + studentPrompts.infos.get(0) + "\n");
        for (int i = 0; i < 2; i++) {
            int index = i;
            onEdt(() -> frame.homePanel().profileTiles().get(index).doClick());
            long millis = waitUntil("connessione dalla tessera importata " + tiles.get(i), 15_000,
                    () -> frame.screen() == MainFrame.Screen.WORKSPACE);
            String status = fromEdt(() -> frame.statusConnection().getText() + " · " + frame.statusServer().getText());
            assertTrue(status.contains(i == 0 ? "MariaDB 11." : "MySQL 8."), status);
            report.append("connessione da «").append(tiles.get(i)).append("» in ").append(millis).append(" ms → ").append(status).append('\n');
            if (i == 1) {
                UiTestSupport.paintWindow(frame, "T2.6-connesso.png");
            }
            Session session = student.connections().session();
            onEdt(() -> student.connections().disconnect());
            waitUntil("sessione chiusa", 5_000, session::isClosed);
        }
        assertEquals(2, studentPrompts.passwordRequests.size(), "la password si digita: nel file non c'è");
        assertTrue(studentPrompts.connectionErrors.isEmpty() && studentPrompts.errors.isEmpty());
        onEdt(frame::dispose);
        UiTestSupport.writeText("T2.6.txt", report.toString());
    }

    @Test
    void t27_dopoUnCicloDUsoCompletoLaPasswordNonEInNessunFile() throws Exception {
        Path dataDir = temp.resolve("RamaSQL");
        Path exported = temp.resolve("esportati").resolve("connessioni-3A.json");
        Files.createDirectories(exported.getParent());
        TestServer server = TestServer.MARIADB;
        FakePrompts prompts = new FakePrompts();
        prompts.password = p -> server.password();
        App app = fromEdt(() -> App.create(dataDir, prompts));
        MainFrame frame = app.frame();

        // 1. crea il profilo dalla tessera «Nuova connessione»
        prompts.nextProfile = server.profile("Ciclo completo", "");
        onEdt(() -> frame.homePanel().newConnectionTile().doClick());
        assertEquals(1, fromEdt(() -> frame.homePanel().profileTiles().size()));

        // 2. prova connessione dalla finestra del profilo
        ProfileDialog dialog = fromEdt(() -> new ProfileDialog(frame, app.connections().profiles().get(0), app.connections()));
        JButton test = dialog.form().testButton();
        onEdt(test::doClick);
        waitUntil("esito della prova", 15_000, () -> dialog.form().testStatusLabel().getText().startsWith("Connessione riuscita"));
        onEdt(dialog::dispose);

        // 3. connetti  4. disconnetti
        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
        waitUntil("connesso", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);
        Session session = app.connections().session();
        onEdt(() -> frame.connectButton().doClick());
        waitUntil("sessione chiusa", 5_000, session::isClosed);
        assertEquals(2, prompts.passwordRequests.size(), "password chiesta per la prova e per la connessione");

        // 4-bis. riconnetti dalla stessa tessera: la password si richiede (non è stata ricordata), poi disconnetti
        onEdt(() -> frame.homePanel().profileTiles().get(0).doClick());
        waitUntil("riconnesso", 15_000, () -> frame.screen() == MainFrame.Screen.WORKSPACE);
        Session again = app.connections().session();
        onEdt(() -> frame.connectButton().doClick());
        waitUntil("sessione chiusa di nuovo", 5_000, again::isClosed);

        // 5. esporta  (+ impostazioni salvate, per avere tutti i file che il programma scrive)
        prompts.nextSaveFile = exported;
        onEdt(() -> menuItem(frame, "Esporta profili…").doClick());
        onEdt(() -> app.settings().changeFontSize(1));
        onEdt(() -> app.settings().changeFontSize(-1));
        onEdt(frame::dispose);
        assertEquals(3, prompts.passwordRequests.size(), "password chiesta per: prova, connessione, riconnessione (mai ricordata)");

        // --- ricerca della password in TUTTI i file, come byte UTF-8 e UTF-16 (LE e BE)
        String password = new String(server.password());
        assertTrue(password.length() >= 4, "la password di prova è abbastanza lunga da rendere sensata la ricerca");
        List<byte[]> needles = List.of(password.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_16LE), password.getBytes(StandardCharsets.UTF_16BE));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dataDir)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        files.add(exported);
        StringBuilder report = new StringBuilder("T2.7 — ciclo d'uso: crea profilo, prova connessione, connetti, disconnetti, riconnetti, disconnetti, esporta, salva impostazioni\n"
                + "richieste di password durante il ciclo: " + prompts.passwordRequests.size() + "\n"
                + "file esaminati (ricerca della password come byte UTF-8, UTF-16LE, UTF-16BE):\n");
        for (Path file : files) {
            byte[] content = Files.readAllBytes(file);
            for (byte[] needle : needles) {
                assertFalse(contains(content, needle), "la password compare in " + file);
            }
            report.append("  ").append(temp.relativize(file)).append(" (").append(content.length).append(" byte): assente\n");
        }
        List<String> names = files.stream().map(f -> f.getFileName().toString()).toList();
        assertTrue(names.containsAll(List.of("connessioni.json", "impostazioni.json", "connessioni-3A.json")), names.toString());
        assertTrue(contains(Files.readAllBytes(dataDir.resolve("connessioni.json")), "ramasql_test".getBytes(StandardCharsets.UTF_8)),
                "controprova: la ricerca trova ciò che nel file c'è davvero (il nome utente)");
        UiTestSupport.writeText("T2.7.txt", report.append("controprova: il nome utente si trova in connessioni.json (la ricerca funziona)\n").toString());
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static JMenuItem menuItem(MainFrame frame, String text) throws AssertionError {
        for (int m = 0; m < frame.getJMenuBar().getMenuCount(); m++) {
            for (java.awt.Component c : frame.getJMenuBar().getMenu(m).getMenuComponents()) {
                if (c instanceof JMenuItem item && item.getText().equals(text)) {
                    return item;
                }
            }
        }
        throw new AssertionError("voce di menu assente: " + text);
    }
}
