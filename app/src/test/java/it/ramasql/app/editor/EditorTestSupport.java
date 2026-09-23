/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

import it.ramasql.app.grid.GridPrompts;

/**
 * Attrezzi dei test dell'editor SQL: tutto <strong>in-process</strong> (niente Robot, niente desktop, nessuna finestra
 * resa visibile). Le scorciatoie si risolvono dall'InputMap dell'area di testo e si invocano sull'EDT; l'evidenza è
 * il componente disegnato su un'immagine in {@code test-results/step4/editor-*.png}.
 */
final class EditorTestSupport {

    private EditorTestSupport() {
    }

    static void setupLookAndFeel() {
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
            SwingUtilities.invokeAndWait(() -> { });   // lascia girare ciò che l'azione ha accodato
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

    /** Attende (al massimo 10 s) che la condizione, valutata sull'EDT, sia vera. */
    static void waitUntil(BooleanSupplier condition, String what) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (fromEdt(condition::getAsBoolean)) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("Tempo scaduto in attesa di: " + what);
    }

    /** Un editor nuovo, in una finestra mai resa visibile. */
    static SqlEditor newEditor(SqlRunner runner, CompletionSource completion, EditorPrompts prompts) {
        return fromEdt(() -> new SqlEditor("Query 1", runner, completion, prompts, new NoGridPrompts(), 1000));
    }

    /** Preme una scorciatoia come l'utente: KeyStroke → InputMap dell'area di testo → ActionMap, sull'EDT. */
    static void press(SqlEditor editor, KeyStroke key) {
        onEdt(() -> {
            JComponent area = editor.textArea();
            Object name = area.getInputMap(JComponent.WHEN_FOCUSED).get(key);
            if (name == null) {
                throw new AssertionError("Nessuna azione per " + key);
            }
            Action action = area.getActionMap().get(name);
            if (action == null) {
                throw new AssertionError("Azione mancante: " + name);
            }
            action.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, name.toString()));
        });
    }

    /** Testo e cursore (e selezione facoltativa), sull'EDT. */
    static void setText(SqlEditor editor, String text, int caret) {
        onEdt(() -> {
            editor.textArea().setText(text);
            editor.textArea().setCaretPosition(caret);
        });
    }

    static void select(SqlEditor editor, int start, int end) {
        onEdt(() -> {
            editor.textArea().setCaretPosition(start);
            editor.textArea().moveCaretPosition(end);
        });
    }

    static JFrame host(Component c, int width, int height) {
        return fromEdt(() -> {
            JFrame frame = new JFrame();
            frame.add(c);
            frame.pack();
            frame.setSize(width, height);
            frame.validate();
            return frame;
        });
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
            return Files.createDirectories(projectRoot().resolve("test-results").resolve("step4"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Path writeText(String name, String text) {
        try {
            return Files.writeString(resultsDir().resolve("editor-" + name + ".txt"), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Disegna la finestra ospite su un'immagine: {@code test-results/step4/editor-<name>.png}. */
    static Path screenshot(JFrame frame, String name) {
        return fromEdt(() -> {
            frame.validate();
            Component root = frame.getRootPane();
            BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                root.paint(g);
            } finally {
                g.dispose();
            }
            Path out = resultsDir().resolve("editor-" + name + ".png");
            try {
                ImageIO.write(img, "png", out.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out;
        });
    }

    static String join(List<String> lines) {
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    /** Le griglie dei risultati non aprono finestre nei test. */
    static final class NoGridPrompts implements GridPrompts {
        @Override
        public boolean confirm(String title, String message, String confirmLabel) {
            throw new AssertionError("Domanda inattesa: " + message);
        }

        @Override
        public String editLongText(String title, String initialText) {
            return null;
        }

        @Override
        public Path chooseCsvFile(String suggestedName) {
            return null;
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError("Errore inatteso: " + message);
        }
    }
}
