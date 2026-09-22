/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Cartella delle evidenze dei test: {@code <radice del progetto>/test-results/<step>/}. */
public final class TestResults {

    private TestResults() {
    }

    /** Radice del progetto: si risale da {@code user.dir} finché si trova {@code mvnw.cmd}. */
    public static Path projectRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("mvnw.cmd"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Radice del progetto non trovata (manca mvnw.cmd) da " + System.getProperty("user.dir"));
        }
        return p;
    }

    /** Cartella delle evidenze dello step (es. "step1"), creata se manca. */
    public static Path dir(String step) {
        try {
            return Files.createDirectories(projectRoot().resolve("test-results").resolve(step));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Scrive un file di testo UTF-8 di evidenza. Mai metterci credenziali. */
    public static Path write(String step, String fileName, String text) {
        try {
            return Files.writeString(dir(step).resolve(fileName), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
