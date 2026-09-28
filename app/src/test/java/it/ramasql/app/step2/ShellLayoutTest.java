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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.UIManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.app.connection.ProfileDialog;
import it.ramasql.app.settings.SettingsDialog;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ProfileStore;
import it.ramasql.core.connection.ServerInfo;

/** La schermata: tre zone, barra strumenti corta, menu essenziale, tessere, finestra del profilo, quattro impostazioni. */
@Tag("step2")
@Tag("ui")
class ShellLayoutTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void lookAndFeel() {
        UiTestSupport.requireRedirectedAppData();
        UiTestSupport.setupLookAndFeel();
    }

    private App appWithSampleProfiles(FakePrompts prompts) throws Exception {
        ProfileStore store = new ProfileStore(dataDir);
        store.add(ConnectionProfile.create("MariaDB locale", "127.0.0.1", 3306, "studente", "biblioteca", "")
                .withLastServer(ServerInfo.parse("11.5.2-MariaDB")));
        store.add(ConnectionProfile.create("MySQL dell'aula 3A", "db.scuola.example", 3307, "studente", "", "")
                .withLastServer(ServerInfo.parse("8.0.40")));
        store.add(ConnectionProfile.create("Server di casa", "192.168.1.20", 3306, "luca", "", ""));
        return fromEdt(() -> App.create(dataDir, prompts));
    }

    @Test
    void laFinestraHaTreZoneFisseEIlPannelloSqlConTreSchede() throws Exception {
        MainFrame frame = appWithSampleProfiles(new FakePrompts()).frame();
        onEdt(() -> {
            assertEquals(MainFrame.Screen.HOME, frame.screen(), "senza connessione si parte dalla schermata iniziale");
            assertNotNull(frame.navigatorZone());
            assertNotNull(frame.workTabs());
            assertEquals(List.of("Registro", "Anteprima", "Messaggi"), List.of(frame.sqlPanel().getTitleAt(0),
                    frame.sqlPanel().getTitleAt(1), frame.sqlPanel().getTitleAt(2)));

            // navigatore a sinistra, area di lavoro al centro, pannello SQL in basso: due divisori, nient'altro
            JSplitPane vertical = frame.sqlPanelSplit();
            assertEquals(JSplitPane.VERTICAL_SPLIT, vertical.getOrientation());
            assertSame(frame.sqlPanel(), vertical.getBottomComponent());
            JSplitPane horizontal = (JSplitPane) vertical.getTopComponent();
            assertEquals(JSplitPane.HORIZONTAL_SPLIT, horizontal.getOrientation());
            assertSame(frame.navigatorZone(), horizontal.getLeftComponent());
            assertTrue(isAncestor((Container) horizontal.getRightComponent(), frame.workTabs()));
            assertTrue(vertical.isOneTouchExpandable(), "il pannello SQL è comprimibile");
            assertEquals(0, frame.sqlPanel().getMinimumSize().height, "e si può ridurre a zero");
            assertEquals("Navigatore", firstLabel(frame.navigatorZone()));
        });
    }

    @Test
    void laBarraStrumentiHaDieciPulsantiConTestoEQuelliNonProntiSonoDisabilitatiNonNascosti() throws Exception {
        MainFrame frame = appWithSampleProfiles(new FakePrompts()).frame();
        onEdt(() -> {
            List<JButton> buttons = frame.toolbarButtons();
            assertTrue(buttons.size() <= 10, "al massimo dieci pulsanti: " + buttons.size());
            assertEquals(List.of("Connetti", "Nuova query", "Nuova query visiva", "Nuova tabella", "Nuova vista", "Importa",
                    "Esporta/Dump", "Modello ER", "Esegui", "Interrompi"), buttons.stream().map(JButton::getText).toList());
            for (JButton b : buttons) {
                assertTrue(b.isVisible(), b.getText() + " è nascosto");
                assertFalse(b.getText().isBlank());
            }
            assertTrue(buttons.get(0).isEnabled(), "Connetti è attivo");
            assertTrue(buttons.stream().skip(1).noneMatch(JButton::isEnabled), "gli altri arrivano con gli step successivi");
        });
    }

    @Test
    void ilMenuEEssenziale() throws Exception {
        MainFrame frame = appWithSampleProfiles(new FakePrompts()).frame();
        onEdt(() -> {
            assertEquals(2, frame.getJMenuBar().getMenuCount());
            // Step 11 (ADR-027): «Apri modello ER…» è l'unico modo di riaprire un modello senza connessione
            assertEquals(List.of("Importa profili…", "Esporta profili…", "Apri modello ER…", "Impostazioni…", "Esci"),
                    items(frame.getJMenuBar().getMenu(0)));
            assertEquals(List.of("Informazioni"), items(frame.getJMenuBar().getMenu(1)));
            assertEquals("File", frame.getJMenuBar().getMenu(0).getText());
            assertEquals("Aiuto", frame.getJMenuBar().getMenu(1).getText());
        });
    }

    @Test
    void laSchermataInizialeHaUnaTesseraPerProfiloPiuNuovaConnessione() throws Exception {
        MainFrame frame = appWithSampleProfiles(new FakePrompts()).frame();
        onEdt(() -> {
            List<JButton> tiles = frame.homePanel().profileTiles();
            assertEquals(3, tiles.size());
            List<String> first = labels(tiles.get(0));
            assertEquals(List.of("MariaDB locale", "studente@127.0.0.1:3306", "MariaDB 11.5.2"), first);
            assertEquals(List.of("MySQL dell'aula 3A", "studente@db.scuola.example:3307", "MySQL 8.0.40"), labels(tiles.get(1)));
            assertEquals("Mai usata", labels(tiles.get(2)).get(2));
            assertTrue(frame.homePanel().newConnectionTile().getText().contains("Nuova connessione"));

            JPopupMenu popup = tiles.get(0).getComponentPopupMenu();
            List<String> entries = new ArrayList<>();
            for (Component c : popup.getComponents()) {
                if (c instanceof JMenuItem item) {
                    entries.add(item.getText());
                }
            }
            assertEquals(List.of("Modifica…", "Duplica", "Elimina…"), entries);
            assertEquals("Nessuna connessione aperta", frame.statusConnection().getText());
        });
        BufferedImage image = UiTestSupport.paintWindow(frame, "home-tessere.png");
        assertTrue(image.getWidth() > 800 && image.getHeight() > 500);
    }

    @Test
    void nuovaDuplicaModificaEdEliminaPassanoDalleTessereESiSalvano() throws Exception {
        FakePrompts prompts = new FakePrompts();
        App app = appWithSampleProfiles(prompts);
        MainFrame frame = app.frame();

        prompts.nextProfile = ConnectionProfile.create("Nuovo", "10.0.0.9", 3306, "io", "", "");
        onEdt(() -> frame.homePanel().newConnectionTile().doClick());
        assertEquals(4, fromEdt(() -> frame.homePanel().profileTiles().size()));

        onEdt(() -> menuItem(frame.homePanel().profileTiles().get(3), "Duplica").doClick());
        assertEquals("Nuovo (copia)", fromEdt(() -> labels(frame.homePanel().profileTiles().get(4)).get(0)));

        ConnectionProfile toEdit = app.connections().profiles().get(3);
        prompts.nextProfile = toEdit.withDetails("Rinominato", "10.0.0.10", 3310, "io", "", "");
        onEdt(() -> menuItem(frame.homePanel().profileTiles().get(3), "Modifica…").doClick());
        assertEquals(List.of("Rinominato", "io@10.0.0.10:3310", "Mai usata"),
                fromEdt(() -> labels(frame.homePanel().profileTiles().get(3))));

        prompts.confirmAnswer = false;
        onEdt(() -> menuItem(frame.homePanel().profileTiles().get(4), "Elimina…").doClick());
        assertEquals(5, app.connections().profiles().size(), "senza conferma non si elimina");
        prompts.confirmAnswer = true;
        onEdt(() -> menuItem(frame.homePanel().profileTiles().get(4), "Elimina…").doClick());
        assertTrue(prompts.confirmations.get(1).contains("«Nuovo (copia)»") && prompts.confirmations.get(1).contains("[Elimina]"),
                prompts.confirmations.toString());

        List<String> saved = new ProfileStore(dataDir).profiles().stream().map(ConnectionProfile::name).toList();
        assertEquals(List.of("MariaDB locale", "MySQL dell'aula 3A", "Server di casa", "Rinominato"), saved);
        assertTrue(prompts.errors.isEmpty(), prompts.errors.toString());
    }

    @Test
    void laFinestraDelProfiloNonHaUnCampoPasswordEControllaIDati() throws Exception {
        App app = appWithSampleProfiles(new FakePrompts());
        ProfileDialog dialog = fromEdt(() -> new ProfileDialog(app.frame(), null, app.connections()));
        onEdt(() -> {
            assertEquals(3306, dialog.form().portField().getValue(), "porta predefinita");
            assertTrue(allOfType(dialog, javax.swing.JPasswordField.class).isEmpty(), "la password non si salva: nessun campo");
            assertEquals("Dai un nome alla connessione.", dialog.form().validationError());
            dialog.form().nameField().setText("mariadb LOCALE");
            assertTrue(dialog.form().validationError().contains("Esiste già"), dialog.form().validationError());
            dialog.form().nameField().setText("Aula 3A");
            assertTrue(dialog.form().validationError().contains("host"), dialog.form().validationError());
            dialog.form().hostField().setText("db.scuola.example");
            assertTrue(dialog.form().validationError().contains("utente"), dialog.form().validationError());
            dialog.form().userField().setText("studente");
            dialog.form().catalogField().setText("biblioteca");
            assertNull(dialog.form().validationError());
            ConnectionProfile p = dialog.form().profile();
            assertEquals("studente@db.scuola.example:3306", p.address());
            assertEquals("biblioteca", p.defaultCatalog());
        });
        UiTestSupport.paintWindow(dialog, "profilo.png");
        onEdt(dialog::dispose);
    }

    @Test
    void leImpostazioniSonoEsattamenteQuattroESiSalvano() throws Exception {
        FakePrompts prompts = new FakePrompts();
        App app = appWithSampleProfiles(prompts);
        SettingsDialog dialog = fromEdt(() -> new SettingsDialog(app.frame(), app.settings().settings()));
        onEdt(() -> {
            assertEquals(4, dialog.settingEditors().size(), "quattro voci, non una di più");
            assertEquals(List.of("Lingua", "Dimensione carattere", "Limite righe", "Cartella di lavoro"), labelsOf(dialog).subList(0, 4));
            assertEquals(1000, dialog.settings().rowLimit());
            assertEquals("it", dialog.settings().language());
        });
        UiTestSupport.paintWindow(dialog, "impostazioni.png");
        onEdt(dialog::dispose);

        prompts.nextSettings = new AppSettings("it", 18, 500, dataDir.toString());
        onEdt(() -> app.settings().edit());

        assertEquals(new AppSettings("it", 18, 500, dataDir.toString()), AppSettings.load(dataDir));
        assertTrue(Files.exists(dataDir.resolve("impostazioni.json")));
        assertEquals(18, fromEdt(() -> UIManager.getFont("defaultFont").getSize()), "il carattere vale per tutta l'interfaccia");
        assertEquals(18, fromEdt(() -> app.frame().statusConnection().getFont().getSize()));

        onEdt(() -> app.settings().changeFontSize(-5)); // come Ctrl+rotella
        assertEquals(13, AppSettings.load(dataDir).fontSize());
        assertEquals(13, fromEdt(() -> app.frame().statusConnection().getFont().getSize()));
    }

    // ------------------------------------------------------------------ attrezzi

    static List<String> labels(Container container) {
        List<String> texts = new ArrayList<>();
        for (JLabel label : allOfType(container, JLabel.class)) {
            texts.add(label.getText());
        }
        return texts;
    }

    private static List<String> labelsOf(Container container) {
        return labels(container);
    }

    private static String firstLabel(Container container) {
        return labels(container).get(0);
    }

    static <T> List<T> allOfType(Container container, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component c : container.getComponents()) {
            if (type.isInstance(c)) {
                found.add(type.cast(c));
            }
            if (c instanceof Container child) {
                found.addAll(allOfType(child, type));
            }
        }
        return found;
    }

    private static JMenuItem menuItem(JButton tile, String text) {
        for (Component c : tile.getComponentPopupMenu().getComponents()) {
            if (c instanceof JMenuItem item && item.getText().equals(text)) {
                return item;
            }
        }
        throw new AssertionError("voce di menu assente: " + text);
    }

    private static List<String> items(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (Component c : menu.getMenuComponents()) {
            if (c instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    private static boolean isAncestor(Container ancestor, Component c) {
        return javax.swing.SwingUtilities.isDescendingFrom(c, ancestor);
    }
}
