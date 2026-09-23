/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import it.ramasql.app.theme.RamaSqlLaf;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/**
 * Attrezzi dei test dell'editor di tabelle: tutto <b>in-process</b> (niente Robot, nessuna finestra resa visibile,
 * nessun database). L'editor si pilota con le stesse azioni dei pulsanti e dei modelli delle griglie, sull'EDT;
 * l'evidenza è il componente disegnato su un'immagine in {@code test-results/step5|6/tableeditor-*.png}.
 */
final class TableEditorTestSupport {

    static final ServerInfo MARIADB = ServerInfo.parse("11.5.2-MariaDB");
    static final ServerInfo MYSQL = ServerInfo.parse("8.0.39");

    private TableEditorTestSupport() {
    }

    static void setupLookAndFeel() {
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

    // ================================================================ editor e finestra ospite

    /** Editor ospitato in una finestra mai resa visibile (serve per disegnarlo). */
    static final class Harness {
        final TableEditor editor;
        final JFrame frame;
        final FakePrompts prompts;
        final FakeApplier applier;
        final FakeDataCheck dataCheck;
        final FakeTables tables;

        Harness(TableEditor editor, JFrame frame, FakePrompts prompts, FakeApplier applier, FakeDataCheck dataCheck,
                FakeTables tables) {
            this.editor = editor;
            this.frame = frame;
            this.prompts = prompts;
            this.applier = applier;
            this.dataCheck = dataCheck;
            this.tables = tables;
        }

        void dispose() {
            onEdt(frame::dispose);
        }

        void tab(int index) {
            onEdt(() -> {
                editor.tabs().setSelectedIndex(index);
                frame.validate();
            });
        }

        /** Disegna la finestra e scrive l'immagine in {@code test-results/<step>/tableeditor-<name>.png}. */
        Path screenshot(String step, String name) {
            return fromEdt(() -> {
                frame.validate();
                frame.getRootPane().doLayout();
                frame.validate();
                Component root = frame.getRootPane();
                BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    root.paint(g);
                } finally {
                    g.dispose();
                }
                Path out = resultsDir(step).resolve("tableeditor-" + name + ".png");
                try {
                    ImageIO.write(img, "png", out.toFile());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return out;
            });
        }
    }

    static Harness open(TableDef original, String catalog, ServerInfo server, FakeTables tables) {
        FakePrompts prompts = new FakePrompts();
        FakeApplier applier = new FakeApplier();
        FakeDataCheck dataCheck = new FakeDataCheck();
        return fromEdt(() -> {
            TableEditor editor = new TableEditor(original, catalog, server, tables, prompts, applier, dataCheck);
            JFrame frame = new JFrame();
            frame.add(editor);
            frame.pack();
            frame.setSize(1180, 820);
            frame.validate();
            return new Harness(editor, frame, prompts, applier, dataCheck, tables);
        });
    }

    // ================================================================ file di evidenza

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

    static Path resultsDir(String step) {
        try {
            return Files.createDirectories(projectRoot().resolve("test-results").resolve(step));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Path writeText(String step, String name, String text) {
        try {
            return Files.writeString(resultsDir(step).resolve("tableeditor-" + name + ".txt"), text,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ================================================================ finte

    /** Dialoghi finti: registrano cosa è stato chiesto e rispondono con {@link #confirmAnswer}. */
    static final class FakePrompts implements TableEditorPrompts {
        final List<String> confirms = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        boolean confirmAnswer = true;

        @Override
        public boolean confirm(String title, String message, String confirmLabel) {
            confirms.add(title + "\n" + message + "\n[" + confirmLabel + "]");
            return confirmAnswer;
        }

        @Override
        public void showError(String title, String message) {
            errors.add(title + "\n" + message);
        }

        @Override
        public void showMessage(String title, String message) {
            messages.add(title + "\n" + message);
        }
    }

    /** Applicatore finto: registra le richieste e risponde con {@link #answer} (subito, come sull'EDT). */
    static final class FakeApplier implements TableApplier {
        final List<ApplyRequest> requests = new ArrayList<>();
        /** Di norma: tutto applicato e la tabella riletta è esattamente quella chiesta. */
        Function<ApplyRequest, ApplyOutcome> answer =
                r -> ApplyOutcome.success(r.statements(), r.edited().withOrdinalPositions());

        @Override
        public void apply(ApplyRequest request, java.util.function.Consumer<ApplyOutcome> done) {
            requests.add(request);
            done.accept(answer.apply(request));
        }
    }

    /** Controllo dati finto: registra le query e risponde con {@link #answer}. */
    static final class FakeDataCheck implements DataCheck {
        final List<String> queries = new ArrayList<>();
        DataCheckResult answer = new DataCheckResult(List.of(), List.of(), null);

        @Override
        public void run(String sql, java.util.function.Consumer<DataCheckResult> done) {
            queries.add(sql);
            done.accept(answer);
        }
    }

    /** Catalogo finto in memoria. */
    static final class FakeTables implements CatalogTables {
        final Map<String, TableDef> tables = new HashMap<>();
        final Map<String, List<IncomingReference>> incoming = new HashMap<>();

        FakeTables with(TableDef... defs) {
            for (TableDef t : defs) {
                tables.put(t.name().toLowerCase(Locale.ROOT), t);
            }
            return this;
        }

        FakeTables referencedBy(String table, String childTable, String constraint) {
            incoming.computeIfAbsent(table.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(new IncomingReference(childTable, constraint));
            return this;
        }

        @Override
        public List<String> tableNames() {
            return tables.values().stream().map(TableDef::name).sorted().toList();
        }

        @Override
        public Optional<TableDef> table(String name) {
            return Optional.ofNullable(tables.get(name.toLowerCase(Locale.ROOT)));
        }

        @Override
        public List<IncomingReference> referencing(String tableName) {
            return incoming.getOrDefault(tableName.toLowerCase(Locale.ROOT), List.of());
        }
    }

    // ================================================================ la «biblioteca» (it-tests/fixtures/biblioteca.sql)

    static final String CHARSET = "utf8mb4";
    static final String COLLATION = "utf8mb4_unicode_ci";

    static ColumnDef idColumn() {
        return ColumnDef.of("id", "INT").withUnsigned(true).notNull().withAutoIncrement(true);
    }

    private static TableDef table(String catalog, String name, List<ColumnDef> columns, List<IndexDef> indexes,
            List<ForeignKeyDef> fks) {
        return new TableDef(catalog, name, "InnoDB", CHARSET, COLLATION, "", null, columns, indexes, fks, List.of())
                .withOrdinalPositions();
    }

    static TableDef editori(String catalog) {
        return table(catalog, "editori", List.of(idColumn(),
                ColumnDef.of("nome", "VARCHAR", "80").notNull(),
                ColumnDef.of("citta", "VARCHAR", "60")),
                List.of(IndexDef.primary("id"), IndexDef.unique("uq_editori_nome", "nome")), List.of());
    }

    static TableDef autori(String catalog) {
        return table(catalog, "autori", List.of(idColumn(),
                ColumnDef.of("cognome", "VARCHAR", "60").notNull(),
                ColumnDef.of("nome", "VARCHAR", "60").notNull(),
                ColumnDef.of("nazionalita", "VARCHAR", "40").notNull()
                        .withDefault(ColumnDefault.literal("italiana"))),
                List.of(IndexDef.primary("id"), IndexDef.index("ix_autori_cognome", "cognome", "nome")), List.of());
    }

    /** {@code libri} «nuda»: colonne e chiave primaria, senza indici secondari né chiavi esterne. */
    static TableDef libriNuda(String catalog) {
        return table(catalog, "libri", List.of(idColumn(),
                ColumnDef.of("titolo", "VARCHAR", "150").notNull(),
                ColumnDef.of("isbn", "CHAR", "13"),
                ColumnDef.of("anno", "SMALLINT").withUnsigned(true),
                ColumnDef.of("prezzo", "DECIMAL", "6,2").notNull().withDefault(ColumnDefault.literal("0.00")),
                ColumnDef.of("id_editore", "INT").withUnsigned(true)),
                List.of(IndexDef.primary("id")), List.of()).withComment("Catalogo dei libri");
    }

    static TableDef libri(String catalog) {
        return libriNuda(catalog)
                .withIndexes(List.of(IndexDef.primary("id"), IndexDef.unique("uq_libri_isbn", "isbn"),
                        IndexDef.index("ix_libri_editore", "id_editore")))
                .withForeignKeys(List.of(new ForeignKeyDef("fk_libri_editori", List.of("id_editore"), null,
                        "editori", List.of("id"), FkAction.RESTRICT, FkAction.RESTRICT)));
    }

    static TableDef soci(String catalog) {
        return table(catalog, "soci", List.of(idColumn(),
                ColumnDef.of("tessera", "CHAR", "8").notNull(),
                ColumnDef.of("cognome", "VARCHAR", "60").notNull(),
                ColumnDef.of("nome", "VARCHAR", "60").notNull(),
                ColumnDef.of("email", "VARCHAR", "120"),
                ColumnDef.of("nato_il", "DATE")),
                List.of(IndexDef.primary("id"), IndexDef.unique("uq_soci_tessera", "tessera")), List.of());
    }

    /** {@code prestiti} «nuda»: senza indici secondari né chiavi esterne. */
    static TableDef prestitiNuda(String catalog) {
        return table(catalog, "prestiti", List.of(idColumn(),
                ColumnDef.of("id_libro", "INT").withUnsigned(true).notNull(),
                ColumnDef.of("id_socio", "INT").withUnsigned(true).notNull(),
                ColumnDef.of("data_prestito", "DATE").notNull(),
                ColumnDef.of("data_reso", "DATE").withDefault(ColumnDefault.NULL_VALUE)),
                List.of(IndexDef.primary("id")), List.of());
    }

    /** Tutte le tabelle della biblioteca (con le loro chiavi esterne) e chi riferisce chi. */
    static FakeTables biblioteca(String catalog) {
        return new FakeTables().with(editori(catalog), autori(catalog), libri(catalog), soci(catalog),
                prestitiNuda(catalog))
                .referencedBy("editori", "libri", "fk_libri_editori");
    }
}
