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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.theme.RamaSqlLaf;
import it.ramasql.core.metadata.TableDef;

/**
 * <b>BUG-037</b>: con più schede aperte (editor di tabella, due query visive, editor SQL) un clic sul <b>titolo</b>
 * di una linguetta non la sceglieva: l'etichetta del titolo aveva un suggerimento proprio, e in Swing un componente
 * con un suggerimento riceve i clic al posto della linguetta. Si cambiava scheda solo cliccando sul bordo, fuori
 * dalla scritta. Qui clic veri del mouse (con {@code Robot}) al centro del titolo di ogni linguetta, tre giri: ogni
 * clic sceglie la sua scheda subito, e il suggerimento della linguetta resta.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class Bug037ClicSulleSchedeTest {

    /** Il tempo massimo perché un clic scelga la scheda (la scheda cambia in qualche decina di millisecondi). */
    private static final long MAX_MS = 2_000;

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
        onEdt(RamaSqlLaf::setup);
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void unClicSulTitoloSceglieLaScheda(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("b37");
        StringBuilder ev = new StringBuilder("BUG-037 — clic sul titolo delle linguette, su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                onEdt(() -> {
                    a.frame().setVisible(true);
                    a.frame().setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                });
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                onEdt(() -> a.frame().openTableEditor(catalog, libri));
                var v1 = fromEdt(() -> a.frame().openVisualQuery(catalog));
                onEdt(() -> {
                    com.sqleo.querybuilder.QbOperations.addTable(v1.queryBuilder(), "prestiti");
                    com.sqleo.querybuilder.QbOperations.addTable(v1.queryBuilder(), "libri");
                });
                var v2 = fromEdt(() -> a.frame().openVisualQuery(catalog));
                onEdt(() -> com.sqleo.querybuilder.QbOperations.addTable(v2.queryBuilder(), "soci"));
                onEdt(() -> a.frame().openSqlEditor());
                a.waitIdle();
                JTabbedPane tabs = fromEdt(() -> a.frame().workTabs());
                int n = fromEdt(tabs::getTabCount);
                assertEquals(4, n, "editor di tabella, due query visive, editor SQL");

                // 1. nell'intestazione delle linguette solo la «×» riceve il mouse: il resto lo lascia alla linguetta
                List<String> takers = fromEdt(() -> {
                    List<String> out = new ArrayList<>();
                    for (int i = 0; i < tabs.getTabCount(); i++) {
                        mouseTakers(tabs.getTabComponentAt(i), tabs.getTitleAt(i), out);
                    }
                    return out;
                });
                assertTrue(takers.isEmpty(), "componenti dell'intestazione che si prendono i clic: " + takers);
                ev.append("1. Intestazioni delle ").append(n)
                        .append(" linguette: nessun componente, tranne la «×», riceve il mouse al posto della linguetta\n");

                // 2. clic veri al centro del titolo, tre giri, con la finestra davanti
                onEdt(() -> {
                    a.frame().toFront();
                    a.frame().requestFocus();
                });
                Thread.sleep(500);
                Robot robot = new Robot();
                long worst = 0;
                for (int round = 0; round < 3; round++) {
                    for (int k = 0; k < n; k++) {
                        int target = round % 2 == 0 ? k : n - 1 - k;
                        if (fromEdt(tabs::getSelectedIndex) == target) {
                            continue;
                        }
                        Point p = fromEdt(() -> titleCenterOnScreen(tabs, target));
                        String title = fromEdt(() -> tabs.getTitleAt(target));
                        long t0 = System.nanoTime();
                        robot.mouseMove(p.x, p.y);
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                        long ms;
                        while (true) {
                            ms = (System.nanoTime() - t0) / 1_000_000;
                            if (fromEdt(tabs::getSelectedIndex) == target || ms > MAX_MS) {
                                break;
                            }
                            Thread.sleep(10);
                        }
                        assertEquals(target, (int) fromEdt(tabs::getSelectedIndex),
                                "clic sul titolo «" + title + "»: la scheda non è stata scelta entro " + MAX_MS + " ms");
                        worst = Math.max(worst, ms);
                        ev.append("   clic su «").append(title).append("»: scelta in ").append(ms).append(" ms\n");
                    }
                }
                ev.append("2. Clic veri del mouse sul titolo, tre giri: ogni clic sceglie la sua scheda (la più lenta in ")
                        .append(worst).append(" ms)\n");

                // 3. il suggerimento della linguetta si vede anche sopra il titolo
                for (int k = 0; k < n; k++) {
                    int i = k;
                    String tip = fromEdt(() -> {
                        Point p = titleCenterOnScreen(tabs, i);
                        SwingUtilities.convertPointFromScreen(p, tabs);
                        return tabs.getToolTipText(new MouseEvent(tabs, MouseEvent.MOUSE_MOVED, 0, 0, p.x, p.y, 0,
                                false));
                    });
                    String expected = fromEdt(() -> tabs.getToolTipTextAt(i));
                    assertTrue(expected != null && !expected.isBlank(), "la linguetta " + i + " ha un suggerimento");
                    assertEquals(expected, tip, "sopra il titolo si vede il suggerimento della linguetta " + i);
                }
                ev.append("3. Sopra il titolo di ogni linguetta si vede il suo suggerimento\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step12", "BUG-037-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    /** Il centro dell'etichetta del titolo, sullo schermo. */
    private static Point titleCenterOnScreen(JTabbedPane tabs, int index) {
        Component header = tabs.getTabComponentAt(index);
        JLabel label = header == null ? null : firstLabel(header);
        Point p;
        if (label != null) {
            p = new Point(label.getWidth() / 2, label.getHeight() / 2);
            SwingUtilities.convertPointToScreen(p, label);
        } else {
            Rectangle r = tabs.getBoundsAt(index);
            p = new Point(r.x + r.width / 2, r.y + r.height / 2);
            SwingUtilities.convertPointToScreen(p, tabs);
        }
        return p;
    }

    private static JLabel firstLabel(Component c) {
        if (c instanceof JLabel l) {
            return l;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                JLabel l = firstLabel(child);
                if (l != null) {
                    return l;
                }
            }
        }
        return null;
    }

    /** I componenti (pulsanti esclusi) che, avendo ascoltatori del mouse, si prenderebbero i clic della linguetta. */
    private static void mouseTakers(Component c, String title, List<String> out) {
        if (c == null) {
            return;
        }
        if (!(c instanceof AbstractButton) && (c.getMouseListeners().length > 0
                || c.getMouseMotionListeners().length > 0)) {
            out.add("«" + title + "» › " + c.getClass().getSimpleName()
                    + (c instanceof JLabel l ? " \"" + l.getText() + "\"" : ""));
        }
        if (c instanceof Container k && !(c instanceof AbstractButton)) {
            for (Component child : k.getComponents()) {
                mouseTakers(child, title, out);
            }
        }
    }
}
