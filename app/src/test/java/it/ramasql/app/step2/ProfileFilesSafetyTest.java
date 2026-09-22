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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JMenuItem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ProfileStore;

/**
 * I file dell'utente non si perdono: l'importazione chiede conferma prima di sostituire profili con lo stesso nome;
 * un file dei profili o delle impostazioni rovinato non impedisce l'avvio, si mette da parte con un nome univoco e
 * l'utente viene avvisato in italiano. Nessun server: tutto dall'interfaccia, con un {@code Prompts} finto.
 */
@Tag("step2")
@Tag("ui")
class ProfileFilesSafetyTest {

    @TempDir
    Path temp;

    private App app;

    @BeforeAll
    static void lookAndFeel() {
        UiTestSupport.requireRedirectedAppData();
        UiTestSupport.setupLookAndFeel();
    }

    @AfterEach
    void close() {
        if (app != null) {
            onEdt(() -> app.frame().dispose());
        }
    }

    private Path dataDir() {
        return temp.resolve("RamaSQL");
    }

    /** File esportato dal docente: «Aula» e «Casa» (già presenti dallo studente, con altre maiuscole) e «Nuovo». */
    private Path teacherExport() throws Exception {
        ProfileStore teacher = new ProfileStore(temp.resolve("docente"));
        teacher.add(ConnectionProfile.create("Aula", "10.0.0.5", 3306, "studente", "biblioteca", ""));
        teacher.add(ConnectionProfile.create("casa", "192.168.1.9", 3307, "studente", "", ""));
        teacher.add(ConnectionProfile.create("Nuovo", "10.0.0.6", 3306, "studente", "", ""));
        Path exported = temp.resolve("connessioni-3A.json");
        teacher.exportTo(exported);
        return exported;
    }

    private List<ConnectionProfile> studentProfiles() throws Exception {
        ProfileStore student = new ProfileStore(dataDir());
        student.add(ConnectionProfile.create("AULA", "vecchio-host", 3306, "io", "", ""));
        student.add(ConnectionProfile.create("Casa", "127.0.0.1", 3306, "io", "", ""));
        student.add(ConnectionProfile.create("Mio", "localhost", 3307, "io", "", ""));
        return student.profiles();
    }

    private List<String> tileNames(MainFrame frame) {
        return fromEdt(() -> frame.homePanel().profileTiles().stream().map(t -> ShellLayoutTest.labels(t).get(0)).toList());
    }

    @Test
    void importazione_seLUtenteRinunciaNonCambiaNulla() throws Exception {
        Path exported = teacherExport();
        List<ConnectionProfile> before = studentProfiles();
        String fileBefore = Files.readString(dataDir().resolve(ProfileStore.FILE_NAME), StandardCharsets.UTF_8);
        FakePrompts prompts = new FakePrompts();
        prompts.nextOpenFile = exported;
        prompts.confirmAnswer = false;
        app = fromEdt(() -> App.create(dataDir(), prompts));

        onEdt(() -> menuItem(app.frame(), "Importa profili…").doClick());

        assertEquals(1, prompts.confirmations.size(), "prima di sostituire si chiede");
        String question = prompts.confirmations.get(0);
        assertTrue(question.contains("2 connessioni") && question.contains("«AULA», «Casa»")
                && question.contains("[Importa e sostituisci]"), question);
        assertFalse(question.contains("Nuovo"), "si elencano solo quelle che verranno sostituite: " + question);
        assertEquals(before, app.connections().profiles(), "in memoria nulla è cambiato");
        assertEquals(fileBefore, Files.readString(dataDir().resolve(ProfileStore.FILE_NAME), StandardCharsets.UTF_8),
                "su disco nulla è cambiato");
        assertEquals(List.of("AULA", "Casa", "Mio"), tileNames(app.frame()));
        assertTrue(prompts.infos.isEmpty() && prompts.errors.isEmpty(), "nessun «importazione fatta»");
    }

    @Test
    void importazione_conLaConfermaSostituisceEAggiunge() throws Exception {
        Path exported = teacherExport();
        List<ConnectionProfile> before = studentProfiles();
        FakePrompts prompts = new FakePrompts();
        prompts.nextOpenFile = exported;
        prompts.confirmAnswer = true;
        app = fromEdt(() -> App.create(dataDir(), prompts));

        onEdt(() -> menuItem(app.frame(), "Importa profili…").doClick());

        assertEquals(1, prompts.confirmations.size());
        List<ConnectionProfile> after = new ProfileStore(dataDir()).profiles();
        assertEquals(List.of("Aula", "casa", "Mio", "Nuovo"), after.stream().map(ConnectionProfile::name).toList());
        assertEquals("10.0.0.5", after.get(0).host(), "sostituito dalla versione del file");
        assertEquals(before.get(0).id(), after.get(0).id(), "stesso profilo, aggiornato");
        assertEquals(List.of("Aula", "casa", "Mio", "Nuovo"), tileNames(app.frame()));
        assertTrue(prompts.infos.get(0).contains("aggiunti: 1") && prompts.infos.get(0).contains("aggiornati (stesso nome): 2"),
                prompts.infos.toString());
    }

    @Test
    void importazione_senzaNomiInComuneNonSiChiedeNulla() throws Exception {
        Path exported = teacherExport();
        FakePrompts prompts = new FakePrompts();
        prompts.nextOpenFile = exported;
        prompts.confirmAnswer = false; // se chiedesse, rinuncerebbe: l'importazione deve avvenire comunque
        app = fromEdt(() -> App.create(dataDir(), prompts));

        onEdt(() -> menuItem(app.frame(), "Importa profili…").doClick());

        assertTrue(prompts.confirmations.isEmpty(), prompts.confirmations.toString());
        assertEquals(List.of("Aula", "casa", "Nuovo"), tileNames(app.frame()));
    }

    @Test
    void fileDeiProfiliRovinato_lAvvioRiesceLAvvisoEInItalianoELaCopiaEMessaDaParte() throws Exception {
        Files.createDirectories(dataDir());
        Files.writeString(dataDir().resolve(ProfileStore.FILE_NAME), "{ \"profiles\": [ rovinato", StandardCharsets.UTF_8);
        FakePrompts prompts = new FakePrompts();

        app = fromEdt(() -> App.create(dataDir(), prompts));

        assertEquals(MainFrame.Screen.HOME, fromEdt(() -> app.frame().screen()));
        assertEquals(List.of(), tileNames(app.frame()), "si riparte da un elenco vuoto");
        assertEquals(1, prompts.errors.size());
        String warning = prompts.errors.get(0);
        assertTrue(warning.startsWith("Profili non leggibili | Il file «connessioni.json» non è un elenco di profili"), warning);
        assertTrue(warning.contains("messo da parte con il nome «connessioni.json.illeggibile»"), warning);
        assertEquals("{ \"profiles\": [ rovinato",
                Files.readString(dataDir().resolve("connessioni.json.illeggibile"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(dataDir().resolve(ProfileStore.FILE_NAME)));
    }

    @Test
    void fileDeiProfiliRovinatoConUnaCopiaGiaMessaDaParte_laVecchiaCopiaNonSiPerde() throws Exception {
        Files.createDirectories(dataDir());
        Files.writeString(dataDir().resolve("connessioni.json.illeggibile"), "rovinato la settimana scorsa", StandardCharsets.UTF_8);
        Files.writeString(dataDir().resolve(ProfileStore.FILE_NAME), "rovinato oggi", StandardCharsets.UTF_8);
        FakePrompts prompts = new FakePrompts();

        app = fromEdt(() -> App.create(dataDir(), prompts));

        assertTrue(prompts.errors.get(0).contains("«connessioni.json.illeggibile-2»"), prompts.errors.toString());
        assertEquals("rovinato la settimana scorsa",
                Files.readString(dataDir().resolve("connessioni.json.illeggibile"), StandardCharsets.UTF_8));
        assertEquals("rovinato oggi", Files.readString(dataDir().resolve("connessioni.json.illeggibile-2"), StandardCharsets.UTF_8));

        // si continua a lavorare: il primo profilo creato si salva in un file nuovo
        prompts.nextProfile = ConnectionProfile.create("Dopo il guasto", "localhost", 3306, "io", "", "");
        onEdt(() -> app.frame().homePanel().newConnectionTile().doClick());
        assertEquals(List.of("Dopo il guasto"), new ProfileStore(dataDir()).profiles().stream().map(ConnectionProfile::name).toList());
    }

    @Test
    void fileDelleImpostazioniRovinato_avvisoAllAvvioECopiaMessaDaParte() throws Exception {
        Files.createDirectories(dataDir());
        Files.writeString(dataDir().resolve(AppSettings.FILE_NAME), "{ \"fontSize\": ", StandardCharsets.UTF_8);
        FakePrompts prompts = new FakePrompts();

        app = fromEdt(() -> App.create(dataDir(), prompts));

        assertEquals(1, prompts.errors.size());
        String warning = prompts.errors.get(0);
        assertTrue(warning.startsWith("Impostazioni non leggibili | Il file delle impostazioni «impostazioni.json» è rovinato"), warning);
        assertTrue(warning.contains("«impostazioni.json.illeggibile»") && warning.contains("impostazioni predefinite"), warning);
        assertEquals(AppSettings.defaults(), app.settings().settings());
        assertEquals("{ \"fontSize\": ", Files.readString(dataDir().resolve("impostazioni.json.illeggibile"), StandardCharsets.UTF_8));

        // cambiare le impostazioni ora riscrive il file, ma la copia rovinata resta al sicuro
        onEdt(() -> app.settings().changeFontSize(1));
        assertEquals(AppSettings.DEFAULT_FONT_SIZE + 1, AppSettings.load(dataDir()).fontSize());
        assertEquals("{ \"fontSize\": ", Files.readString(dataDir().resolve("impostazioni.json.illeggibile"), StandardCharsets.UTF_8));
        onEdt(() -> app.settings().changeFontSize(-1));
    }

    private static JMenuItem menuItem(MainFrame frame, String text) {
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
