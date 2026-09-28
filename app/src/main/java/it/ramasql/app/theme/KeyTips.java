/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.theme;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.table.JTableHeader;

/**
 * I suggerimenti da tastiera (T12.13): <b>Ctrl+F1</b> apre il suggerimento del componente con il fuoco, subito sotto
 * di lui; <b>Esc</b> (o di nuovo Ctrl+F1) lo chiude, e si chiude anche quando il fuoco passa altrove, al primo clic o
 * dopo il tempo di tutti i suggerimenti. Negli alberi, nelle tabelle e negli elenchi il suggerimento è quello della
 * <b>voce scelta</b> (il nodo del navigatore, la riga), sotto di lei; in una cella senza suggerimento proprio vale quello
 * dell'intestazione della sua colonna; senza voce scelta né suggerimento proprio vale quello dell'area che lo contiene
 * (la linguetta del registro, per esempio). Se non c'è nulla da dire non compare nulla (invece del riquadro vuoto).
 */
public final class KeyTips implements KeyEventDispatcher {

    private static boolean installed;
    private static Popup popup;
    private static JToolTip shown;
    private static Timer dismiss;
    private static JComponent owner;

    private KeyTips() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher(new KeyTips());
        // il fuoco passa a un altro componente: si chiude; se il programma perde il fuoco per un attimo (Windows lo fa
        // quando riattiva la finestra) si aspetta un momento prima di chiuderlo
        kfm.addPropertyChangeListener("focusOwner", e -> {
            if (e.getNewValue() != null && e.getNewValue() != owner) {
                hide();
            } else if (e.getNewValue() == null && popup != null) {
                Timer later = new Timer(400, t -> {
                    if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == null) {
                        hide();
                    }
                });
                later.setRepeats(false);
                later.start();
            }
        });
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                hide();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }
        boolean ctrlF1 = e.getKeyCode() == KeyEvent.VK_F1 && e.isControlDown() && !e.isAltDown() && !e.isShiftDown();
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && popup != null) {
            hide();
            e.consume();
            return true;
        }
        if (!ctrlF1 || !(e.getComponent() instanceof JComponent c)) {
            return false;
        }
        if (popup != null) {
            hide();
        } else {
            show(c);
        }
        e.consume();
        return true;
    }

    /** Il suggerimento del componente (della voce scelta, per alberi, tabelle ed elenchi) sotto di lui. */
    static void show(JComponent c) {
        hide();
        if (!c.isShowing()) {
            return;
        }
        Rectangle cell = selectedCell(c);
        String text = null;
        if (cell != null) {
            MouseEvent at = new MouseEvent(c, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                    cell.x + Math.min(cell.width / 2, Tokens.px(24)), cell.y + cell.height / 2, 0, false);
            text = c.getToolTipText(at);
            if (blank(text) && c instanceof JTable table) {
                text = headerTip(table, table.columnAtPoint(at.getPoint()));
            }
        }
        if (blank(text)) {
            text = c.getToolTipText();
            cell = null;
        }
        if (blank(text)) {
            text = areaTip(c);   // per esempio un registro ancora senza riga scelta: la sua linguetta
        }
        if (blank(text)) {
            return;   // niente da dire: meglio nulla che un riquadro vuoto
        }
        Rectangle anchor = cell != null ? cell : c.getVisibleRect();
        // sotto la voce o il componente; dentro un componente grande (un registro, un editor), in alto
        int below = anchor.height > Tokens.px(80) ? Tokens.px(28) : anchor.height + Tokens.px(4);
        Point p = new Point(anchor.x + Math.min(anchor.width / 4, Tokens.px(24)), anchor.y + below);
        SwingUtilities.convertPointToScreen(p, c);
        JToolTip tip = c.createToolTip();
        tip.setName("keyTips.tip");
        tip.setTipText(text);
        popup = PopupFactory.getSharedInstance().getPopup(c, tip, p.x, p.y);
        shown = tip;
        owner = c;
        popup.show();
        dismiss = new Timer(ToolTipManager.sharedInstance().getDismissDelay(), e -> hide());
        dismiss.setRepeats(false);
        dismiss.start();
    }

    static void hide() {
        if (dismiss != null) {
            dismiss.stop();
            dismiss = null;
        }
        if (popup != null) {
            popup.hide();
            popup = null;
        }
        shown = null;
        owner = null;
    }

    /** Il suggerimento aperto da tastiera, se c'è (per le prove). */
    public static JToolTip shown() {
        return shown;
    }

    /** Il suggerimento dell'area che contiene il componente: un contenitore con suggerimento, o la sua linguetta. */
    private static String areaTip(Component c) {
        for (Component child = c, p = c.getParent(); p != null; child = p, p = p.getParent()) {
            if (p instanceof javax.swing.JTabbedPane tabs) {
                int i = tabs.indexOfComponent(child);
                if (i >= 0 && !blank(tabs.getToolTipTextAt(i))) {
                    return tabs.getToolTipTextAt(i);
                }
            } else if (p instanceof JComponent j && !blank(j.getToolTipText())) {
                return j.getToolTipText();
            }
        }
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String headerTip(JTable table, int column) {
        JTableHeader header = table.getTableHeader();
        if (header == null || column < 0) {
            return null;
        }
        Rectangle r = header.getHeaderRect(column);
        return header.getToolTipText(new MouseEvent(header, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                r.x + r.width / 2, r.y + Math.max(1, r.height / 2), 0, false));
    }

    /** Dove sta la voce scelta ({@code null} se non ce n'è una), in coordinate del componente. */
    static Rectangle selectedCell(Component c) {
        if (c instanceof JTree tree) {
            int row = tree.getLeadSelectionRow();
            if (row < 0) {
                row = tree.getMinSelectionRow();
            }
            if (row < 0) {
                return null;
            }
            tree.scrollRowToVisible(row);
            return tree.getRowBounds(row);
        }
        if (c instanceof JTable table) {
            int row = table.getSelectionModel().getLeadSelectionIndex();
            int col = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
            if (row < 0 || row >= table.getRowCount()) {
                return null;
            }
            col = col < 0 || col >= table.getColumnCount() ? 0 : col;
            Rectangle r = table.getCellRect(row, col, false);
            table.scrollRectToVisible(r);
            return r;
        }
        if (c instanceof JList<?> list) {
            int i = list.getLeadSelectionIndex();
            if (i < 0 || i >= list.getModel().getSize()) {
                return null;
            }
            list.ensureIndexIsVisible(i);
            return list.getCellBounds(i, i);
        }
        return null;
    }
}
