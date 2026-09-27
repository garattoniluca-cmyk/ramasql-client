/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Dove finiscono le evidenze dello Step 7 di questo modulo: {@code <radice del progetto>/test-results/step7}. */
final class Step7Files {

    private Step7Files() {
    }

    /** Risale da {@code user.dir} finché trova {@code mvnw.cmd}. */
    static Path projectRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("mvnw.cmd"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("radice del progetto non trovata (mvnw.cmd) a partire da "
                    + System.getProperty("user.dir"));
        }
        return p;
    }

    static Path write(String fileName, String content) throws IOException {
        Path dir = projectRoot().resolve("test-results").resolve("step7");
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    /** Testo adatto a una cella di tabella Markdown. */
    static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }
}
