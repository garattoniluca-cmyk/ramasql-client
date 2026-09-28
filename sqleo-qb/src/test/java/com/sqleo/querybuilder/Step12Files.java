/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Evidenze dei test dello Step 12 del modulo: {@code test-results/step12/} nella radice del progetto. */
final class Step12Files {

    private Step12Files() {
    }

    static Path dir() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("mvnw.cmd"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("radice del progetto non trovata (mvnw.cmd)");
        }
        Path dir = root.resolve("test-results").resolve("step12");
        Files.createDirectories(dir);
        return dir;
    }

    static Path write(String fileName, String content) throws IOException {
        Path f = dir().resolve(fileName);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }
}
