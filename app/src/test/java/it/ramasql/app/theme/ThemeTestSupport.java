/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.theme;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Attrezzi dei test del tema: EDT, cartella delle evidenze ({@code test-results/step2/}), disegno su immagine. */
final class ThemeTestSupport {

    private ThemeTestSupport() {
    }

    static void setupTheme() {
        Locale.setDefault(Locale.ITALIAN);
        onEdt(RamaSqlLaf::setup);
    }

    static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
            SwingUtilities.invokeAndWait(() -> { });
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

    static Path writeText(String fileName, String text) {
        try {
            return Files.writeString(resultsDir().resolve(fileName), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Path writePng(BufferedImage image, String fileName) {
        Path out = resultsDir().resolve(fileName);
        try {
            ImageIO.write(image, "png", out.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Disegna un componente (già dimensionato) su un'immagine opaca e la salva. */
    static BufferedImage paint(Component c, String fileName) {
        return fromEdt(() -> {
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                c.paint(g);
            } finally {
                g.dispose();
            }
            writePng(img, fileName);
            return img;
        });
    }

    /** Rapporto di contrasto WCAG 2.x tra due colori opachi (1…21). */
    static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
