/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.settings;

import java.awt.AWTEvent;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLaf;

import it.ramasql.app.Prompts;
import it.ramasql.app.Texts;
import it.ramasql.core.connection.AppSettings;

/**
 * Le quattro impostazioni: lettura all'avvio, finestra di modifica, salvataggio. La dimensione del carattere
 * vale per <strong>tutta</strong> l'interfaccia e si cambia anche con Ctrl+rotella.
 */
public final class SettingsController {

    private final Path dataDirectory;
    private final Prompts prompts;
    private final AppSettings.Loading loading;
    private AppSettings settings;
    private final List<Consumer<AppSettings>> listeners = new CopyOnWriteArrayList<>();

    /** Legge le impostazioni; un file rovinato si mette da parte (il problema resta in {@link #loading()}). */
    public SettingsController(Path dataDirectory, Prompts prompts) {
        this.dataDirectory = dataDirectory;
        this.prompts = prompts;
        this.loading = AppSettings.loadRecovering(dataDirectory);
        this.settings = loading.settings();
    }

    /** Com'è andata la lettura all'avvio (per l'avviso, se il file era rovinato). */
    public AppSettings.Loading loading() {
        return loading;
    }

    public AppSettings settings() {
        return settings;
    }

    /** Menu File → Impostazioni… */
    public void edit() {
        AppSettings edited = prompts.editSettings(settings);
        if (edited != null) {
            update(edited);
        }
    }

    /** Chi usa le impostazioni (es. il limite di righe dell'esecutore) viene avvisato quando cambiano. */
    public void addListener(Consumer<AppSettings> listener) {
        listeners.add(listener);
    }

    /** Ctrl+rotella: un punto in più o in meno, entro i limiti. */
    public void changeFontSize(int delta) {
        AppSettings changed = settings.withFontSize(settings.fontSize() + delta);
        if (changed.fontSize() != settings.fontSize()) {
            update(changed);
        }
    }

    private void update(AppSettings changed) {
        boolean fontChanged = changed.fontSize() != settings.fontSize();
        settings = changed;
        try {
            settings.save(dataDirectory);
        } catch (IOException e) {
            prompts.showError(Texts.get("settings.save.error.title"), e.getMessage());
        }
        if (fontChanged) {
            applyFont();
        }
        listeners.forEach(l -> l.accept(settings));
    }

    /** Applica la dimensione del carattere a tutte le finestre aperte e a quelle future. */
    public void applyFont() {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = UIManager.getFont("Label.font");
        }
        UIManager.put("defaultFont", base.deriveFont((float) settings.fontSize()));
        FlatLaf.updateUI();
    }

    /** Attiva Ctrl+rotella in tutto il programma. */
    public void installCtrlWheelZoom() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseWheelEvent wheel && wheel.isControlDown() && wheel.getID() == MouseWheelEvent.MOUSE_WHEEL) {
                changeFontSize(wheel.getWheelRotation() < 0 ? 1 : -1);
                wheel.consume();
            }
        }, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }
}
