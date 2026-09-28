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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.tree.TreePath;


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
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ProfileStore;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.TableDef;

/**
 * Tutte le schermate del programma vero, costruite una dopo l'altra (T12.7, T12.9): la schermata iniziale; l'area di
 * lavoro con barra, menu e navigatore aperto con i menu contestuali di ogni tipo di nodo; editor SQL, griglia con la
 * scheda record, editor di tabelle con ogni linguetta, tabella nuova, query visiva, importazione a ogni passo, dump a
 * ogni passo, esecuzione di script, modello ER; le finestre di dialogo. Chi le visita decide cosa farne (controllare
 * i suggerimenti, fotografarle, elencarne i controlli).
 */
final class Schermate {

    /** Chi visita le schermate. */
    interface Visitor {
        /** Una schermata (o una sua linguetta), già costruita e disposta. */
        void screen(String name, Component root);

        /** Un menu (della barra o contestuale). */
        void menu(String name, JPopupMenu menu);
    }

    /** L'editor di tabelle aperto durante la visita (le sue liste nelle celle si controllano a parte). */
    record Visited(TableEditor editor) {
    }

    private Schermate() {
    }

    static void tabs(Visitor v, String screen, Component root) {
        // ogni linguetta scelta a turno (le non scelte sono nascoste)
        List<JTabbedPane> panes = new ArrayList<>();
        collect(root, panes);
        v.screen(screen, root);
        for (JTabbedPane p : panes) {
            if (p.getParent() == null) {
                continue;
            }
            int keep = p.getSelectedIndex();
            for (int i = 0; i < p.getTabCount(); i++) {
                int k = i;
                onEdt(() -> p.setSelectedIndex(k));
                v.screen(screen + " › " + p.getTitleAt(i), p.getComponentAt(i));
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

    /** La schermata iniziale, prima di collegarsi (un programma a parte, con un profilo salvato). */
    static void home(DbServer server, Path dataDir, Visitor v) throws Exception {
        // 1) schermata iniziale, prima di collegarsi
        Path homeDir = dataDir.resolve("home");
        java.nio.file.Files.createDirectories(homeDir);
        new ProfileStore(homeDir).add(server.profile());
        App home = fromEdt(() -> App.create(homeDir, new QuietPrompts(server), new FakeWorkspacePrompts()));
        try {
            onEdt(() -> v.screen("schermata iniziale", home.frame()));
        } finally {
            onEdt(() -> {
                home.connections().shutdown();
                home.frame().dispose();
            });
        }
    }

    /** Tutto il resto, con il programma collegato e il catalogo con la {@code biblioteca}. */
    static Visited workspace(ClientApp a, DbServer server, String catalog, Path dataDir, Visitor v) throws Exception {
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
        onEdt(() -> v.screen("area di lavoro", a.frame()));
        onEdt(() -> {
            v.menu("barra › Importa", a.frame().importMenu());
            v.menu("barra › Modello ER", a.frame().erMenu());
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
                        v.menu("navigatore › " + n.kind(), menu);
                    }
                }
            }
        });
        // 3) le schede
        a.ws.onPreview = d -> d.cancelButton().doClick();
        onEdt(() -> tabs(v, "editor SQL", a.frame().openSqlEditor()));
        TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
        DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, libri));
        waitUntil("righe della griglia", ClientApp.TIMEOUT, () -> grid.table().getRowCount() > 0);
        onEdt(() -> tabs(v, "griglia dati", grid));
        onEdt(() -> grid.showRecordForm(true));
        onEdt(() -> tabs(v, "griglia dati con la scheda record", grid));
        TableEditor editor = fromEdt(() -> a.frame().openTableEditor(catalog, libri));
        onEdt(() -> tabs(v, "editor di tabelle", editor));
        TableEditor fresh = fromEdt(() -> a.frame().openTableEditor(catalog, null));
        onEdt(() -> tabs(v, "tabella nuova", fresh));
        var visual = fromEdt(() -> a.frame().openVisualQuery(catalog));
        onEdt(() -> tabs(v, "query visiva", visual));
        ImportWizard imp = fromEdt(() -> a.frame().openImport(catalog, "soci"));
        onEdt(() -> tabs(v, "importa › 1 file", imp));
        ImportSupport.choose(a, imp, ImportSupport.fixture("soci.csv"));
        onEdt(() -> tabs(v, "importa › 1 file scelto", imp));
        ImportSupport.toPreview(imp);
        onEdt(() -> tabs(v, "importa › 2 anteprima", imp));
        ImportSupport.toTarget(imp);
        onEdt(() -> tabs(v, "importa › 3 tabella esistente", imp));
        onEdt(() -> imp.newRadio().doClick());
        onEdt(() -> tabs(v, "importa › 3 tabella nuova", imp));
        onEdt(() -> imp.existingRadio().doClick());
        onEdt(() -> imp.nextButton().doClick());
        onEdt(() -> tabs(v, "importa › 4 opzioni", imp));
        onEdt(() -> imp.nextButton().doClick());
        onEdt(() -> tabs(v, "importa › 5 importa", imp));
        DumpWizard dump = fromEdt(() -> a.frame().openDump(catalog, null));
        waitUntil("oggetti letti", ClientApp.TIMEOUT, () -> !dump.isLoading());
        onEdt(() -> tabs(v, "dump › 1 cosa", dump));
        onEdt(() -> dump.nextButton().doClick());
        onEdt(() -> tabs(v, "dump › 2 opzioni", dump));
        a.ws.filesToSave.put(it.ramasql.app.workspace.FilePrompts.Purpose.DUMP, dataDir.resolve("d.sql"));
        onEdt(dump::chooseFile);
        onEdt(() -> dump.nextButton().doClick());
        onEdt(() -> tabs(v, "dump › 3 esporta", dump));
        ScriptRunTab script = fromEdt(() -> a.frame().openScriptRun(catalog));
        onEdt(() -> tabs(v, "esegui script", script));
        // 4) il modello ER, con l'elenco dei suggerimenti aperto
        int before = fromEdt(() -> a.frame().erWindows().size());
        onEdt(() -> a.nav().tree().setSelectionPath(a.nav().find(NavNode.Kind.CATALOG, catalog, catalog)));
        onEdt(() -> ClientApp.menuItem(a.frame().erMenu(), "er.menu.new").doClick());
        waitUntil("modello ER", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                && a.frame().erWindows().size() == before + 1);
        ErModelWindow er = fromEdt(() -> a.frame().erWindows().get(a.frame().erWindows().size() - 1));
        onEdt(() -> er.panel().suggest());
        onEdt(() -> tabs(v, "modello ER", er));
        onEdt(() -> v.menu("modello ER › relazione", er.panel().relationshipMenuFor(
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
                v.screen("finestra " + d.getName(), d);
            }
        });
        onEdt(() -> dialogs.forEach(java.awt.Window::dispose));
        return new Visited(editor);
    }
}
