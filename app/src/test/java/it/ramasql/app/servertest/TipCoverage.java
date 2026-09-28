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

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.text.JTextComponent;

import it.ramasql.app.theme.ComboTips;

/**
 * Il controllo di copertura dei suggerimenti (T12.9): si visita l'albero dei componenti di una schermata e per ogni
 * componente con cui l'utente interagisce (pulsanti, voci di menu, caselle, campi modificabili, liste a discesa,
 * selettori numerici, linguette, alberi, tabelle con le loro intestazioni) si controlla che abbia il suo suggerimento.
 * Per gli alberi e le tabelle il suggerimento può venire dal disegnatore delle righe o delle intestazioni: si chiede
 * davvero, come farebbe il mouse. Raccoglie anche le liste a discesa, per il controllo delle voci (T12.10).
 */
final class TipCoverage {

    /** Componenti senza suggerimento, con dove stanno. */
    final Set<String> missing = new LinkedHashSet<>();
    /** Liste a discesa trovate, con la schermata. */
    final List<Object[]> combos = new ArrayList<>();
    /** Componenti controllati. */
    int checked;
    /** Ogni componente (o riga, colonna, linguetta) si conta e si segnala una volta sola. */
    private final java.util.Set<String> seen = new java.util.HashSet<>();

    private boolean first(Component c, Object part) {
        return seen.add(System.identityHashCode(c) + ":" + part);
    }

    /** Visita una finestra o un pannello. */
    void visit(String screen, Component root) {
        walk(screen, root, true);
        if (root instanceof javax.swing.RootPaneContainer rp && rp.getRootPane().getJMenuBar() != null) {
            menuBar(screen, rp.getRootPane().getJMenuBar());
        }
    }

    /** Visita un menu contestuale (voci comprese). */
    void menu(String screen, JPopupMenu menu) {
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item) {
                menuItem(screen + " › menu", item);
            }
        }
    }

    private void menuBar(String screen, JMenuBar bar) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu m = bar.getMenu(i);
            if (m != null) {
                menuItem(screen + " › barra dei menu", m);
            }
        }
    }

    private void menuItem(String screen, JMenuItem item) {
        if (item instanceof JMenu m) {
            // un menu della barra si apre con un clic: il suggerimento serve alle sue voci
            for (int i = 0; i < m.getItemCount(); i++) {
                if (m.getItem(i) != null) {
                    menuItem(screen + " › " + m.getText(), m.getItem(i));
                }
            }
            return;
        }
        check(screen, item, has(item.getToolTipText()));
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private void check(String screen, Component c, boolean ok) {
        if (!first(c, "")) {
            return;
        }
        checked++;
        if (!ok) {
            missing.add(screen + " › " + describe(c));
        }
    }

    static String describe(Component c) {
        String text = c instanceof AbstractButton b ? b.getText() : c instanceof JTextComponent ? "" : null;
        return c.getClass().getSimpleName() + (c.getName() == null ? "" : " «" + c.getName() + "»")
                + (text == null || text.isBlank() ? "" : " \"" + text + "\"");
    }

    /** Un componente interno di Swing o di FlatLaf (frecce delle liste, barre di scorrimento, pulsanti delle linguette). */
    private static boolean internal(Component c) {
        String cls = c.getClass().getName();
        if (cls.startsWith("javax.swing.plaf") || cls.startsWith("com.formdev") || cls.contains("ArrowButton")) {
            return true;
        }
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof JComboBox || p instanceof JScrollBar || p instanceof JSpinner || p instanceof JTable
                    || p instanceof JTree || p.getClass().getName().startsWith("com.formdev")
                    || p instanceof javax.swing.JFileChooser) {
                return true;
            }
        }
        return false;
    }

    private void walk(String screen, Component c, boolean root) {
        if (!root && !c.isVisible()) {
            return;   // nascosto: non lo vede nessuno (le linguette non scelte si visitano sceglendole)
        }
        if (c instanceof JComponent j && !internal(c)) {
            if (c instanceof AbstractButton b) {
                check(screen, b, has(b.getToolTipText()));
            } else if (c instanceof JComboBox<?> combo) {
                check(screen, combo, has(combo.getToolTipText()));
                combos.add(new Object[] {screen, combo});
            } else if (c instanceof JTextComponent t && t.isEditable() && t.isEnabled()) {
                check(screen, t, has(t.getToolTipText()));
            } else if (c instanceof JSpinner || c instanceof JSlider) {
                check(screen, c, has(j.getToolTipText()));
            } else if (c instanceof JTabbedPane tabs) {
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    if (!first(tabs, i)) {
                        continue;
                    }
                    checked++;
                    if (!has(tabs.getToolTipTextAt(i))) {
                        missing.add(screen + " › linguetta \"" + tabs.getTitleAt(i) + "\" di " + describe(tabs));
                    }
                }
            } else if (c instanceof JTree tree) {
                tree(screen, tree);
            } else if (c instanceof JTable table) {
                table(screen, table);
            } else if (c instanceof JList<?> list && !(SwingUtilitiesInPopup.inComboPopup(list))) {
                check(screen, list, has(list.getToolTipText()));
            }
        }
        if (c instanceof Container k && !(c instanceof JComboBox) && !(c instanceof JSpinner)) {
            for (Component child : k.getComponents()) {
                walk(screen, child, false);
            }
        }
    }

    /** Ogni riga visibile dell'albero ha il suo suggerimento (dal disegnatore delle righe). */
    private void tree(String screen, JTree tree) {
        int rows = Math.min(tree.getRowCount(), 60);
        if (rows == 0) {
            check(screen, tree, has(tree.getToolTipText()));
            return;
        }
        for (int r = 0; r < rows; r++) {
            Rectangle b = tree.getRowBounds(r);
            if (b == null) {
                continue;
            }
            MouseEvent e = new MouseEvent(tree, MouseEvent.MOUSE_MOVED, 0, 0, b.x + b.width / 2, b.y + b.height / 2,
                    0, false);
            if (!first(tree, tree.getPathForRow(r))) {
                continue;
            }
            checked++;
            if (!has(tree.getToolTipText(e))) {
                missing.add(screen + " › nodo \"" + tree.getPathForRow(r).getLastPathComponent() + "\" di "
                        + describe(tree));
            }
        }
    }

    /** La tabella (o ciascuna intestazione di colonna) ha il suo suggerimento. */
    private void table(String screen, JTable table) {
        boolean tableTip = has(table.getToolTipText());
        javax.swing.table.JTableHeader header = table.getTableHeader();
        for (int col = 0; col < table.getColumnCount(); col++) {
            boolean ok = tableTip;
            if (!ok && header != null) {
                Rectangle r = header.getHeaderRect(col);
                MouseEvent e = new MouseEvent(header, MouseEvent.MOUSE_MOVED, 0, 0, r.x + r.width / 2,
                        r.y + Math.max(1, r.height / 2), 0, false);
                ok = has(header.getToolTipText(e));
            }
            if (!first(table, col)) {
                continue;
            }
            checked++;
            if (!ok) {
                missing.add(screen + " › colonna \"" + table.getColumnName(col) + "\" di " + describe(table));
            }
        }
        if (table.getColumnCount() == 0) {
            check(screen, table, tableTip);
        }
        // le celle della griglia dei dati (T12.9): la prima riga e la riga d'inserimento, colonna per colonna
        if ("dataGrid.table".equals(table.getName()) && table.getRowCount() > 0) {
            for (int row : new int[] {0, table.getRowCount() - 1}) {
                for (int col = 0; col < table.getColumnCount(); col++) {
                    Rectangle r = table.getCellRect(row, col, false);
                    MouseEvent e = new MouseEvent(table, MouseEvent.MOUSE_MOVED, 0, 0, r.x + r.width / 2,
                            r.y + r.height / 2, 0, false);
                    checked++;
                    if (!has(table.getToolTipText(e))) {
                        missing.add(screen + " › cella " + row + "," + col + " di " + describe(table));
                    }
                }
            }
        }
    }

    /** Le voci senza spiegazione di una lista a discesa (le intestazioni di gruppo, non sceglibili, non contano). */
    static List<String> itemsWithoutTip(JComboBox<?> combo) {
        List<String> out = new ArrayList<>();
        if (!ComboTips.installed(combo)) {
            out.add("(la lista non spiega le sue voci)");
            return out;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object item = combo.getItemAt(i);
            if (item != null && item.getClass().getSimpleName().equals("Header")) {
                continue;
            }
            String tip = ComboTips.tipFor(combo, item);
            if (tip == null || tip.isBlank()) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    /** Le finestre di primo livello aperte ora (per visitare anche i dialoghi). */
    static List<Window> windows() {
        return List.of(Window.getWindows());
    }

    /** Una lista dentro la tendina di una lista a discesa (la copre il controllo delle voci). */
    static final class SwingUtilitiesInPopup {
        private SwingUtilitiesInPopup() {
        }

        static boolean inComboPopup(Component c) {
            for (Container p = c.getParent(); p != null; p = p.getParent()) {
                if (p instanceof javax.swing.plaf.basic.ComboPopup) {
                    return true;
                }
            }
            return false;
        }
    }
}
