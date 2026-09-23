/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import it.ramasql.app.theme.RamaSqlLaf;

/**
 * Attrezzi dei test della griglia: tutto <strong>in-process</strong> (niente Robot, niente controllo del desktop,
 * nessuna finestra resa visibile). Le azioni si pilotano dalla ActionMap e dal modello sull'EDT; l'evidenza è il
 * componente disegnato su un'immagine in {@code test-results/step4/}.
 */
public final class GridTestSupport {

    private GridTestSupport() {
    }

    public static void setupLookAndFeel() {
        Locale.setDefault(Locale.ITALIAN);
        onEdt(RamaSqlLaf::setup);
    }

    public static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
            SwingUtilities.invokeAndWait(() -> { });   // lascia girare ciò che l'azione ha accodato (invokeLater)
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

    public static <T> T fromEdt(Supplier<T> supplier) {
        AtomicReference<T> value = new AtomicReference<>();
        onEdt(() -> value.set(supplier.get()));
        return value.get();
    }

    /** Esegue un'azione della griglia come farebbe la sua scorciatoia (Ctrl+C, Ctrl+V, Canc…). */
    public static void action(DataGrid grid, String name) {
        onEdt(() -> {
            var action = grid.table().getActionMap().get(name);
            if (action == null) {
                throw new AssertionError("Azione mancante: " + name);
            }
            action.actionPerformed(new java.awt.event.ActionEvent(grid.table(), 0, name));
        });
    }

    /** Scrive in una cella come l'utente: apre l'editor, sostituisce il testo, chiude l'editor (Invio). */
    public static void type(DataGrid grid, int row, int column, String text) {
        onEdt(() -> {
            grid.table().changeSelection(row, column, false, false);
            if (!grid.table().editCellAt(row, column)) {
                throw new AssertionError("Cella non modificabile: " + row + "," + column);
            }
            ((JTextField) grid.table().getEditorComponent()).setText(text);
            grid.table().getCellEditor().stopCellEditing();
        });
    }

    /** Il componente in una finestra mai resa visibile, alla dimensione data (serve per disegnarlo). */
    public static JFrame host(Component c, int width, int height) {
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

    static Path writeText(String fileName, String text) {
        try {
            return Files.writeString(resultsDir().resolve(fileName), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Disegna la finestra ospite (contenuto) su un'immagine e la salva in test-results/step4. */
    static Path screenshot(JFrame frame, String fileName) {
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
            Path out = resultsDir().resolve(fileName);
            try {
                ImageIO.write(img, "png", out.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out;
        });
    }

    /** Il testo con tabulazioni e a-capo resi visibili, per i file di evidenza. */
    public static String visible(String text) {
        return text == null ? "<null>" : text.replace("\t", "<TAB>").replace("\r", "<CR>").replace("\n", "<LF>\n");
    }

    /** Le celle della griglia (righe di dati) come liste, per confronti. */
    public static List<List<String>> cells(DataGrid grid, int firstRow, int lastRow, int firstColumn, int lastColumn) {
        return fromEdt(() -> {
            List<List<String>> out = new ArrayList<>();
            for (int r = firstRow; r <= lastRow; r++) {
                List<String> row = new ArrayList<>();
                for (int c = firstColumn; c <= lastColumn; c++) {
                    row.add((String) grid.model().getValueAt(r, c));
                }
                out.add(row);
            }
            return out;
        });
    }
}
