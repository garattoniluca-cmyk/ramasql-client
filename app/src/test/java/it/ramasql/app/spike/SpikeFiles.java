/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app.spike;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Cartella delle evidenze degli spike dello Step 1: {@code <radice del progetto>/test-results/step1/}. */
public final class SpikeFiles {

    private SpikeFiles() {
    }

    /** Radice del progetto: si risale da {@code user.dir} finché si trova {@code mvnw.cmd}. */
    public static Path projectRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("mvnw.cmd"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Radice del progetto non trovata (manca mvnw.cmd)");
        }
        return p;
    }

    public static Path step1Dir() {
        try {
            return Files.createDirectories(projectRoot().resolve("test-results").resolve("step1"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Disegna il componente su un'immagine (senza toccare lo schermo) e la salva come PNG. */
    public static BufferedImage paintToPng(Component c, Path file) throws IOException {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            c.paint(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", file.toFile());
        return img;
    }
}
