/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

import it.ramasql.core.connection.AppData;
import it.ramasql.core.connection.AppSettings;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path dataDirectory = AppData.directory();
        // la lingua vale anche per i testi di Swing (Sì/No/Annulla, finestre dei file)
        Locale.setDefault(Locale.forLanguageTag(AppSettings.load(dataDirectory).language()));
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            AtomicReference<App> app = new AtomicReference<>();
            Prompts prompts = new SwingPrompts(
                    () -> app.get() == null ? null : (Window) app.get().frame(),
                    () -> app.get() == null ? null : app.get().settings().settings().workDirectory());
            app.set(App.create(dataDirectory, prompts));
            app.get().settings().installCtrlWheelZoom();
            MainFrame frame = app.get().frame();
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    System.exit(0);
                }
            });
            frame.setVisible(true);
        });
    }
}
