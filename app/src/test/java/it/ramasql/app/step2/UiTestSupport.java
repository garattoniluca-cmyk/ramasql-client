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

import it.ramasql.app.theme.RamaSqlLaf;

import it.ramasql.core.connection.AppData;

/**
 * Attrezzi dei test d'interfaccia dello Step 2: tutto <strong>in-process</strong> (niente Robot, niente controllo
 * del desktop, nessuna finestra resa visibile). I componenti si pilotano via API sull'EDT; l'evidenza visiva è il
 * componente disegnato su un'immagine e salvata in {@code test-results/step2/}.
 */
final class UiTestSupport {

    private UiTestSupport() {
    }

    /** Aspetto e lingua come nel programma vero. */
    static void setupLookAndFeel() {
        Locale.setDefault(Locale.ITALIAN);
        onEdt(RamaSqlLaf::setup);
    }

    /** Sicurezza: i test non devono mai poter scrivere nella vera cartella dei dati dell'utente. */
    static void requireRedirectedAppData() {
        String override = System.getProperty(AppData.OVERRIDE_PROPERTY);
        if (override == null || override.isBlank()) {
            throw new AssertionError("La proprietà " + AppData.OVERRIDE_PROPERTY + " non è impostata: i test scriverebbero in %APPDATA%");
        }
    }

    // ------------------------------------------------------------------ EDT

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

    /** Aspetta (interrogando l'EDT) che la condizione diventi vera; ritorna i millisecondi passati. */
    static long waitUntil(String what, long timeoutMillis, BooleanSupplier conditionOnEdt) {
        long start = System.nanoTime();
        while (true) {
            if (fromEdt(conditionOnEdt::getAsBoolean)) {
                return (System.nanoTime() - start) / 1_000_000;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (elapsed > timeoutMillis) {
                throw new AssertionError("Dopo " + elapsed + " ms non è ancora vero: " + what);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    // ------------------------------------------------------------------ evidenze

    /** Radice del progetto: si risale da {@code user.dir} finché si trova {@code mvnw.cmd}. */
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

    static Path resultsDir() {
        try {
            return Files.createDirectories(projectRoot().resolve("test-results").resolve("step2"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Scrive un file di testo di evidenza. Mai metterci password. */
    static Path writeText(String fileName, String text) {
        try {
            return Files.writeString(resultsDir().resolve(fileName), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Disegna una finestra (mai resa visibile) su un'immagine e la salva: tutto il contenuto, menu compreso. */
    static BufferedImage paintWindow(Window window, String fileName) {
        return fromEdt(() -> {
            window.validate();
            Component root = window instanceof JFrame f ? f.getRootPane()
                    : window instanceof JDialog d ? d.getRootPane() : window;
            return paint(root, fileName);
        });
    }

    private static BufferedImage paint(Component c, String fileName) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            c.paint(g);
        } finally {
            g.dispose();
        }
        try {
            ImageIO.write(img, "png", resultsDir().resolve(fileName).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return img;
    }
}
