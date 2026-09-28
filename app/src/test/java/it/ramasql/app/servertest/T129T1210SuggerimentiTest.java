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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(DbServer.class)
    void t129_t1210_ogniComponenteEOgniVoceHaIlSuoSuggerimento(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t129");
        TipCoverage cov = new TipCoverage();
        StringBuilder ev = new StringBuilder("T12.9 / T12.10 — suggerimenti su tutto il programma (" + server.label()
                + ")\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            // 1) schermata iniziale, 2-5) tutto il resto (Schermate)
            Schermate.Visitor visitor = new Schermate.Visitor() {
                @Override
                public void screen(String name, Component root) {
                    cov.visit(name, root);
                }

                @Override
                public void menu(String name, javax.swing.JPopupMenu menu) {
                    cov.menu(name, menu);
                }
            };
            Schermate.home(server, dataDir, visitor);
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                TableEditor editor = Schermate.workspace(a, server, catalog, dataDir, visitor).editor();
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
            Probe.writeText("step12", "T12.9-T12.10-copertura-" + server.id() + ".txt", ev.toString());
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
