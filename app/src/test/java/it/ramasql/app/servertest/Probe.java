/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

import it.ramasql.core.connection.AppData;

/**
 * Attrezzi dei test d'interfaccia dello Step 3: tutto <b>in-process</b> (niente Robot, nessuna finestra resa
 * visibile); i componenti si pilotano via API sull'EDT; l'evidenza visiva è il componente disegnato su un'immagine
 * salvata in {@code test-results/stepN/}.
 */
final class Probe {

    private Probe() {
    }

    static void setup() {
        String override = System.getProperty(AppData.OVERRIDE_PROPERTY);
        if (override == null || override.isBlank()) {
            throw new AssertionError("La proprietà " + AppData.OVERRIDE_PROPERTY
                    + " non è impostata: i test scriverebbero in %APPDATA%");
        }
        Locale.setDefault(Locale.ITALIAN);
        onEdt(FlatLightLaf::setup);
    }

    static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            if (e.getCause() instanceof Error err) {
                throw err;
            }
            throw new AssertionError(e.getCause());
        }
    }

    static <T> T fromEdt(Supplier<T> supplier) {
        AtomicReference<T> value = new AtomicReference<>();
        onEdt(() -> value.set(supplier.get()));
        return value.get();
    }

    /** Aspetta (interrogando l'EDT) che la condizione diventi vera. */
    static void waitUntil(String what, long timeoutMillis, BooleanSupplier conditionOnEdt) {
        long start = System.nanoTime();
        while (true) {
            if (fromEdt(conditionOnEdt::getAsBoolean)) {
                return;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (elapsed > timeoutMillis) {
                throw new AssertionError("Dopo " + elapsed + " ms non è ancora vero: " + what);
            }
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    static Path projectRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("mvnw.cmd"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Radice del progetto non trovata (manca mvnw.cmd)");
        }
        return p;
    }

    /** La cartella delle evidenze di uno step: {@code test-results/step4}… */
    static Path resultsDir(String step) {
        try {
            return Files.createDirectories(projectRoot().resolve("test-results").resolve(step));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** File di testo di evidenza (mai password). */
    static void writeText(String step, String fileName, String text) {
        try {
            Files.writeString(resultsDir(step).resolve(fileName), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Disegna una finestra mai mostrata (menu compreso) e la salva. */
    static BufferedImage paintWindow(String step, Window window, String fileName) {
        return fromEdt(() -> {
            window.validate();
            Component root = window instanceof JFrame f ? f.getRootPane()
                    : window instanceof JDialog d ? d.getRootPane() : window;
            return paint(step, root, fileName);
        });
    }

    /** Disegna un componente alla sua dimensione preferita (es. l'albero intero, anche la parte fuori vista). */
    static BufferedImage paintAtPreferredSize(String step, Component c, String fileName) {
        return fromEdt(() -> {
            java.awt.Dimension old = c.getSize();
            c.setSize(c.getPreferredSize());
            try {
                return paint(step, c, fileName);
            } finally {
                c.setSize(old);
            }
        });
    }

    static BufferedImage paint(String step, Component c, String fileName) {
        c.doLayout();
        BufferedImage img = new BufferedImage(Math.max(1, c.getWidth()), Math.max(1, c.getHeight()),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            c.paint(g);
        } finally {
            g.dispose();
        }
        try {
            ImageIO.write(img, "png", resultsDir(step).resolve(fileName).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return img;
    }
}
