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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.settings.SettingsDialog;
import it.ramasql.app.theme.RamaSqlLaf;
import it.ramasql.core.connection.AppSettings;

/**
 * <b>T12.7</b>: la revisione «alla Apple». Si costruiscono tutte le schermate del programma vero (le stesse di T12.9)
 * e per ciascuna si elencano i controlli visibili, con una fotografia delle schermate principali: è il materiale su cui
 * si chiede, controllo per controllo, «serve a uno dei 9 requisiti?» ({@code docs/REVISIONE-APPLE.md}). Il test
 * controlla le due regole con un numero — barra degli strumenti di <b>al più 10 pulsanti</b>, impostazioni di <b>4
 * voci</b> — e che la revisione copra ogni schermata trovata.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class T127RevisioneAppleTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
        onEdt(RamaSqlLaf::setup);
    }

    /** I controlli con cui l'utente interagisce, visibili nella schermata (una riga ciascuno). */
    static List<String> controls(Component root) {
        List<String> out = new ArrayList<>();
        walk(root, out, true);
        return out;
    }

    private static void walk(Component c, List<String> out, boolean root) {
        if (!root && !c.isVisible()) {
            return;
        }
        if (c instanceof JScrollBar || c.getClass().getName().startsWith("javax.swing.plaf")
                || c.getClass().getName().startsWith("com.formdev")) {
            return;
        }
        String name = c.getName() == null ? "" : " «" + c.getName() + "»";
        if (c instanceof JCheckBox b) {
            out.add("casella \"" + b.getText() + "\"" + name);
        } else if (c instanceof JRadioButton b) {
            out.add("scelta \"" + b.getText() + "\"" + name);
        } else if (c instanceof AbstractButton b && !(c instanceof JMenuItem)) {
            String shown = b.getText() == null || b.getText().isBlank() ? b.getAccessibleContext().getAccessibleName()
                    : b.getText();
            String text = shown == null || shown.isBlank() ? "(icona)" : "\"" + shown + "\"";
            out.add("pulsante " + text + name);
        } else if (c instanceof JComboBox<?> b) {
            out.add("lista a discesa" + name + " (" + b.getItemCount() + " voci)");
            return;
        } else if (c instanceof JSpinner) {
            out.add("numero" + name);
            return;
        } else if (c instanceof JTextComponent t && t.isEditable()) {
            out.add("campo di testo" + name);
        } else if (c instanceof JTable t) {
            List<String> cols = new ArrayList<>();
            for (int i = 0; i < t.getColumnCount(); i++) {
                cols.add(t.getColumnName(i));
            }
            out.add("tabella" + name + " " + cols);
            return;
        } else if (c instanceof JTree) {
            out.add("albero" + name);
            return;
        } else if (c instanceof JList<?>) {
            out.add("elenco" + name);
            return;
        } else if (c instanceof JTabbedPane t) {
            List<String> titles = new ArrayList<>();
            for (int i = 0; i < t.getTabCount(); i++) {
                titles.add(t.getTitleAt(i));
            }
            out.add("linguette" + name + " " + titles);
        }
        if (c instanceof Container k && !(c instanceof JComboBox)) {
            for (Component child : k.getComponents()) {
                walk(child, out, false);
            }
        }
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9àèéìòù]+", "-").replaceAll("(^-|-$)", "");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(DbServer.class)
    void t127_revisioneAppleDiOgniSchermata(DbServer server) throws Exception {
        boolean photos0 = server == DbServer.MARIADB;   // le fotografie una volta sola (le schermate sono le stesse)
        String catalog = DbServer.newCatalogName("t127");
        Map<String, List<String>> inventory = new LinkedHashMap<>();
        List<String> photos = new ArrayList<>();
        Schermate.Visitor visitor = new Schermate.Visitor() {
            @Override
            public void screen(String name, Component root) {
                inventory.putIfAbsent(name, controls(root));
                if (photos0 && (!name.contains(" › ") || name.startsWith("importa") || name.startsWith("dump"))) {
                    Window w = root instanceof Window win ? win : SwingUtilities.getWindowAncestor(root);
                    if (w != null) {
                        String file = String.format("T12.7-%02d-%s.png", photos.size() + 1, slug(name));
                        Probe.paintWindow("step12", w, file);
                        photos.add(file + " — " + name);
                    }
                }
            }

            @Override
            public void menu(String name, JPopupMenu menu) {
                List<String> items = new ArrayList<>();
                for (Component c : menu.getComponents()) {
                    if (c instanceof JMenuItem m) {
                        items.add("voce \"" + m.getText() + "\"" + (m.getName() == null ? "" : " «" + m.getName()
                                + "»"));
                    }
                }
                inventory.putIfAbsent("menu " + name, items);
            }
        };
        int toolbar;
        int settings;
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            Schermate.home(server, dataDir, visitor);
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                Schermate.workspace(a, server, catalog, dataDir, visitor);
                toolbar = fromEdt(() -> a.frame().toolbarButtons().size());
                settings = fromEdt(() -> {
                    SettingsDialog d = new SettingsDialog(a.frame(), AppSettings.defaults());
                    int n = d.settingCount();
                    d.dispose();
                    return n;
                });
            }
        } finally {
            server.dropQuietly(catalog);
        }
        StringBuilder ev = new StringBuilder("T12.7 — inventario delle schermate per la revisione «alla Apple» ("
                + server.label() + ")\n");
        ev.append("Barra degli strumenti: ").append(toolbar).append(" pulsanti (al più 10)\n");
        ev.append("Impostazioni: ").append(settings).append(" voci (devono essere 4)\n");
        ev.append("Schermate: ").append(inventory.size()).append("; fotografie: ").append(photos.size()).append('\n');
        photos.forEach(p -> ev.append("  ").append(p).append('\n'));
        for (Map.Entry<String, List<String>> e : inventory.entrySet()) {
            ev.append("\n## ").append(e.getKey()).append(" (").append(e.getValue().size()).append(" controlli)\n");
            e.getValue().forEach(c -> ev.append("- ").append(c).append('\n'));
        }
        // la revisione copre ogni schermata (per titolo, senza le linguette interne)
        Path review = Probe.projectRoot().resolve("docs/REVISIONE-APPLE.md");
        List<String> uncovered = new ArrayList<>();
        String reviewText = Files.isRegularFile(review) ? Files.readString(review, StandardCharsets.UTF_8) : "";
        for (String screen : inventory.keySet()) {
            String top = screen.contains(" › ") && !screen.startsWith("importa") && !screen.startsWith("dump")
                    && !screen.startsWith("menu ") ? screen.substring(0, screen.indexOf(" › ")) : screen;
            if (!reviewText.contains("«" + top + "»")) {
                uncovered.add(top);
            }
        }
        ev.append("\nSchermate senza revisione in docs/REVISIONE-APPLE.md: ").append(uncovered.stream().distinct()
                .toList()).append('\n');
        boolean ok = toolbar <= 10 && settings == 4 && uncovered.isEmpty();
        ev.append("Esito: ").append(ok ? "OK" : "FALLITO").append('\n');
        Probe.writeText("step12", "T12.7-inventario-" + server.id() + ".txt", ev.toString());
        assertTrue(toolbar <= 10, "pulsanti della barra: " + toolbar);
        assertEquals(4, settings, "voci delle impostazioni");
        assertEquals(List.of(), uncovered.stream().distinct().toList(), "schermate senza revisione");
        assertTrue(inventory.size() > 40, "schermate: " + inventory.size());
    }
}
