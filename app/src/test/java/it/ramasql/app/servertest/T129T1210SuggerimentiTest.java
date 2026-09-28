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

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static it.ramasql.app.servertest.Probe.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.AboutDialog;
import it.ramasql.app.App;
import it.ramasql.app.GuideDialog;
import it.ramasql.app.connection.ConnectionErrorDialog;
import it.ramasql.app.connection.ProfileDialog;
import it.ramasql.app.dump.DumpWizard;
import it.ramasql.app.dump.ScriptRunTab;
import it.ramasql.app.er.ErModelWindow;
import it.ramasql.app.er.ModelTablesDialog;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.importer.ImportWizard;
import it.ramasql.app.navigator.CreateCatalogDialog;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.app.pipeline.ShowCreateDialog;
import it.ramasql.app.settings.SettingsDialog;
import it.ramasql.app.tableeditor.TableEditor;
import it.ramasql.app.theme.RamaSqlLaf;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ProfileStore;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>T12.9</b>: si costruiscono tutte le schermate del programma vero (schermata iniziale, area di lavoro con barra,
 * menu e navigatore con i suoi menu contestuali, editor SQL, griglia con la scheda record, editor di tabelle con ogni
 * linguetta, tabella nuova, query visiva, importazione a ogni passo, dump a ogni passo, esecuzione di script, modello
 * ER, e le finestre di dialogo) e si visita ogni componente con cui l'utente interagisce: <b>zero senza
 * suggerimento</b> (il test elenca quelli che mancano). <b>T12.10</b>: ogni lista a discesa trovata spiega <b>ogni
 * sua voce</b>; le liste chieste dalla roadmap (tipo di colonna, engine, set di caratteri e collation, tipo d'indice,
 * ON DELETE/ON UPDATE, tabella riferita, impostazioni) ci sono tutte.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class T129T1210SuggerimentiTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
        onEdt(RamaSqlLaf::setup);
    }

    private static void tabs(TipCoverage cov, String screen, Component root) {
        // ogni linguetta scelta a turno (le non scelte sono nascoste)
        List<JTabbedPane> panes = new ArrayList<>();
        collect(root, panes);
        cov.visit(screen, root);
        for (JTabbedPane p : panes) {
            if (p.getParent() == null) {
                continue;
            }
            int keep = p.getSelectedIndex();
            for (int i = 0; i < p.getTabCount(); i++) {
                int k = i;
                onEdt(() -> p.setSelectedIndex(k));
                cov.visit(screen + " › " + p.getTitleAt(i), p.getComponentAt(i));
            }
            onEdt(() -> p.setSelectedIndex(keep));
        }
    }

    private static void collect(Component c, List<JTabbedPane> out) {
        if (c instanceof JTabbedPane p && !(p.getParent() instanceof javax.swing.JLayeredPane)) {
            out.add(p);
        }
        if (c instanceof java.awt.Container k) {
            for (Component child : k.getComponents()) {
                collect(child, out);
            }
        }
    }

    @Test
    void t129_t1210_ogniComponenteEOgniVoceHaIlSuoSuggerimento() throws Exception {
        DbServer server = DbServer.MARIADB;
        String catalog = DbServer.newCatalogName("t129");
        TipCoverage cov = new TipCoverage();
        StringBuilder ev = new StringBuilder("T12.9 / T12.10 — suggerimenti su tutto il programma (" + server.label()
                + ")\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            // 1) schermata iniziale, prima di collegarsi
            Path homeDir = dataDir.resolve("home");
            java.nio.file.Files.createDirectories(homeDir);
            new ProfileStore(homeDir).add(server.profile());
            App home = fromEdt(() -> App.create(homeDir, new QuietPrompts(server), new FakeWorkspacePrompts()));
            try {
                onEdt(() -> cov.visit("schermata iniziale", home.frame()));
            } finally {
                onEdt(() -> {
                    home.connections().shutdown();
                    home.frame().dispose();
                });
            }
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                // 2) area di lavoro: barra, menu, navigatore aperto fino alle colonne, menu contestuali di ogni nodo
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                for (NavNode.Kind group : List.of(NavNode.Kind.TABLES, NavNode.Kind.VIEWS)) {
                    TreePath p = fromEdt(() -> a.nav().find(group, catalog, null));
                    if (p != null) {
                        a.expandPath(p);
                    }
                }
                a.expand(NavNode.Kind.TABLE, catalog, "libri");
                for (NavNode.Kind group : List.of(NavNode.Kind.COLUMNS, NavNode.Kind.INDEXES, NavNode.Kind.FOREIGN_KEYS)) {
                    TreePath p = fromEdt(() -> a.nav().find(group, catalog, "libri"));
                    if (p != null) {
                        a.expandPath(p);
                    }
                }
                onEdt(() -> cov.visit("area di lavoro", a.frame()));
                onEdt(() -> {
                    cov.menu("barra › Importa", a.frame().importMenu());
                    cov.menu("barra › Modello ER", a.frame().erMenu());
                    javax.swing.JTree tree = a.nav().tree();
                    Set<NavNode.Kind> seen = new TreeSet<>();
                    for (int r = 0; r < tree.getRowCount(); r++) {
                        TreePath path = tree.getPathForRow(r);
                        Object last = path.getLastPathComponent();
                        NavNode n = last instanceof javax.swing.tree.DefaultMutableTreeNode d
                                && d.getUserObject() instanceof NavNode nn ? nn : null;
                        if (n != null && seen.add(n.kind())) {
                            tree.setSelectionPath(path);
                            javax.swing.JPopupMenu menu = a.nav().menuFor(path);
                            if (menu != null) {
                                cov.menu("navigatore › " + n.kind(), menu);
                            }
                        }
                    }
                });
                // 3) le schede
                a.ws.onPreview = d -> d.cancelButton().doClick();
                onEdt(() -> tabs(cov, "editor SQL", a.frame().openSqlEditor()));
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, libri));
                waitUntil("righe della griglia", ClientApp.TIMEOUT, () -> grid.table().getRowCount() > 0);
                onEdt(() -> tabs(cov, "griglia dati", grid));
                onEdt(() -> grid.showRecordForm(true));
                onEdt(() -> tabs(cov, "griglia dati con la scheda record", grid));
                TableEditor editor = fromEdt(() -> a.frame().openTableEditor(catalog, libri));
                onEdt(() -> tabs(cov, "editor di tabelle", editor));
                TableEditor fresh = fromEdt(() -> a.frame().openTableEditor(catalog, null));
                onEdt(() -> tabs(cov, "tabella nuova", fresh));
                var visual = fromEdt(() -> a.frame().openVisualQuery(catalog));
                onEdt(() -> tabs(cov, "query visiva", visual));
                ImportWizard imp = fromEdt(() -> a.frame().openImport(catalog, "soci"));
                onEdt(() -> tabs(cov, "importa › 1 file", imp));
                ImportSupport.choose(a, imp, ImportSupport.fixture("soci.csv"));
                onEdt(() -> tabs(cov, "importa › 1 file scelto", imp));
                ImportSupport.toPreview(imp);
                onEdt(() -> tabs(cov, "importa › 2 anteprima", imp));
                ImportSupport.toTarget(imp);
                onEdt(() -> tabs(cov, "importa › 3 tabella esistente", imp));
                onEdt(() -> imp.newRadio().doClick());
                onEdt(() -> tabs(cov, "importa › 3 tabella nuova", imp));
                onEdt(() -> imp.existingRadio().doClick());
                onEdt(() -> imp.nextButton().doClick());
                onEdt(() -> tabs(cov, "importa › 4 opzioni", imp));
                onEdt(() -> imp.nextButton().doClick());
                onEdt(() -> tabs(cov, "importa › 5 importa", imp));
                DumpWizard dump = fromEdt(() -> a.frame().openDump(catalog, null));
                waitUntil("oggetti letti", ClientApp.TIMEOUT, () -> !dump.isLoading());
                onEdt(() -> tabs(cov, "dump › 1 cosa", dump));
                onEdt(() -> dump.nextButton().doClick());
                onEdt(() -> tabs(cov, "dump › 2 opzioni", dump));
                a.ws.filesToSave.put(it.ramasql.app.workspace.FilePrompts.Purpose.DUMP, dataDir.resolve("d.sql"));
                onEdt(dump::chooseFile);
                onEdt(() -> dump.nextButton().doClick());
                onEdt(() -> tabs(cov, "dump › 3 esporta", dump));
                ScriptRunTab script = fromEdt(() -> a.frame().openScriptRun(catalog));
                onEdt(() -> tabs(cov, "esegui script", script));
                // 4) il modello ER, con l'elenco dei suggerimenti aperto
                int before = fromEdt(() -> a.frame().erWindows().size());
                onEdt(() -> a.nav().tree().setSelectionPath(a.nav().find(NavNode.Kind.CATALOG, catalog, catalog)));
                onEdt(() -> ClientApp.menuItem(a.frame().erMenu(), "er.menu.new").doClick());
                waitUntil("modello ER", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                        && a.frame().erWindows().size() == before + 1);
                ErModelWindow er = fromEdt(() -> a.frame().erWindows().get(a.frame().erWindows().size() - 1));
                onEdt(() -> er.panel().suggest());
                onEdt(() -> tabs(cov, "modello ER", er));
                onEdt(() -> cov.menu("modello ER › relazione", er.panel().relationshipMenuFor(
                        er.panel().model().relationships().get(0))));
                // 5) le finestre di dialogo
                List<java.awt.Window> dialogs = new ArrayList<>();
                onEdt(() -> {
                    dialogs.add(new PreviewDialog(a.frame(), SqlScript.of("Anteprima", "Editor SQL",
                            "UPDATE t SET a = 1 WHERE id = 2"), ConfirmationPolicy.evaluate(SqlScript.of("x", "y",
                            "UPDATE t SET a = 1 WHERE id = 2"))));
                    SqlScript drop = SqlScript.of("Elimina", "Navigatore", "DROP TABLE libri");
                    dialogs.add(new PreviewDialog(a.frame(), drop, ConfirmationPolicy.evaluate(drop)));
                    dialogs.add(new CreateCatalogDialog(a.frame(), List.of(new it.ramasql.core.metadata.CollationInfo(
                            "utf8mb4_unicode_ci", "utf8mb4", false), new it.ramasql.core.metadata.CollationInfo(
                            "utf8mb4_general_ci", "utf8mb4", true)), "utf8mb4", null));
                    dialogs.add(new ProfileDialog(a.frame(), server.profile(), a.app.connections()));
                    dialogs.add(new SettingsDialog(a.frame(), AppSettings.defaults()));
                    dialogs.add(new AboutDialog(a.frame()));
                    dialogs.add(new GuideDialog(a.frame()));
                    dialogs.add(new ConnectionErrorDialog(a.frame(), server.profile(), new ConnectionFailure(
                            ConnectionErrorCause.PORT_CLOSED, "Il server non risponde.", 0, "", "Connection refused")));
                    dialogs.add(new ModelTablesDialog(a.frame(), catalog, List.of("autori", "libri")));
                    dialogs.add(new ShowCreateDialog(a.frame(), "libri", "SHOW CREATE TABLE", "CREATE TABLE libri (…)",
                            s -> { }));
                    for (java.awt.Window d : dialogs) {
                        cov.visit("finestra " + d.getName(), d);
                    }
                });
                onEdt(() -> dialogs.forEach(java.awt.Window::dispose));
                // T12.10: le voci di ogni lista a discesa
                Map<String, List<String>> itemsMissing = new LinkedHashMap<>();
                Set<String> named = new TreeSet<>();
                onEdt(() -> {
                    for (Object[] c : cov.combos) {
                        JComboBox<?> combo = (JComboBox<?>) c[1];
                        named.add(String.valueOf(combo.getName()));
                        List<String> without = TipCoverage.itemsWithoutTip(combo);
                        if (!without.isEmpty()) {
                            itemsMissing.put(c[0] + " › " + TipCoverage.describe(combo), without);
                        }
                    }
                });
                // le liste delle celle delle tabelle (tipo, azioni, tipo d'indice, tabella riferita) sono editor delle
                // celle: si controllano dall'editor delle colonne
                onEdt(() -> {
                    for (javax.swing.JTable t : tablesIn(editor)) {
                        for (int col = 0; col < t.getColumnCount(); col++) {
                            javax.swing.table.TableCellEditor ce = t.getColumnModel().getColumn(col).getCellEditor();
                            if (ce instanceof javax.swing.DefaultCellEditor dce
                                    && dce.getComponent() instanceof JComboBox<?> combo) {
                                named.add(String.valueOf(combo.getName()));
                                List<String> without = TipCoverage.itemsWithoutTip(combo);
                                if (!without.isEmpty()) {
                                    itemsMissing.put("editor di tabelle › " + TipCoverage.describe(combo), without);
                                }
                            }
                        }
                    }
                });
                ev.append("Componenti controllati: ").append(cov.checked).append("; senza suggerimento: ")
                        .append(cov.missing.size()).append('\n');
                cov.missing.forEach(m -> ev.append("  MANCA ").append(m).append('\n'));
                ev.append("Liste a discesa trovate: ").append(named).append('\n');
                itemsMissing.forEach((k, v) -> ev.append("  VOCI SENZA SPIEGAZIONE in ").append(k).append(": ")
                        .append(v).append('\n'));
                Set<String> required = Set.of("columns.typeEditor", "options.engine", "options.charset",
                        "options.collation", "indexes.kind", "fks.action", "fks.refTable", "settings.language",
                        "catalog.create.charset", "catalog.create.collation");
                Set<String> absent = new TreeSet<>(required);
                absent.removeAll(named);
                ev.append("Liste richieste da T12.10 assenti: ").append(absent).append('\n');
                assertEquals(List.of(), List.copyOf(cov.missing), "componenti senza suggerimento");
                assertEquals(Map.of(), itemsMissing, "voci senza spiegazione");
                assertEquals(Set.of(), absent, "liste di T12.10 non trovate");
                assertTrue(cov.checked > 300, "controllati " + cov.checked);
                ev.append("Esito: OK\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t.getMessage() == null ? t.toString()
                    : t.getMessage().lines().findFirst().orElse("")).append('\n');
            throw t;
        } finally {
            Probe.writeText("step12", "T12.9-T12.10-copertura.txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    private static List<javax.swing.JTable> tablesIn(Component c) {
        List<javax.swing.JTable> out = new ArrayList<>();
        if (c instanceof javax.swing.JTable t) {
            out.add(t);
        }
        if (c instanceof java.awt.Container k) {
            for (Component child : k.getComponents()) {
                out.addAll(tablesIn(child));
            }
        }
        return out;
    }
}
