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

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.function.Function;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JToolTip;
import javax.swing.ListCellRenderer;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;

/**
 * Le spiegazioni delle <b>singole voci</b> delle liste a discesa ({@code ADR-020}, {@code DESIGN-SYSTEM.md} §3.9): con
 * la lista aperta, il suggerimento della voce evidenziata compare <b>accanto</b> alla lista (a destra, o a sinistra se
 * a destra non c'è posto), allineato alla voce, senza coprire le altre; segue il mouse e le frecce della tastiera. Con
 * la lista chiusa, il suggerimento della lista dice anche la spiegazione della voce scelta. Un solo meccanismo per
 * tutte le liste del programma, anche quelle costruite dal server (collation, tabelle, colonne), dove il testo si
 * compone dai dati.
 */
public final class ComboTips {

    /** Proprietà del componente con la funzione che spiega le voci (per i test di copertura). */
    public static final String PROPERTY = "rama.comboTips";
    private static final String BASE_TIP = "rama.comboTips.base";
    private static final String STATE = "rama.comboTips.state";

    private ComboTips() {
    }

    /** Lo stato di una lista: il suggerimento accanto, se è aperto. */
    private static final class State {
        Popup popup;
        JToolTip tip;
        Rectangle bounds;
        String text;
        JList<?> list;
    }

    /**
     * Installa le spiegazioni delle voci.
     *
     * @param tip la spiegazione di una voce ({@code null} o vuota solo per voci che non si possono scegliere, come le
     *            intestazioni di gruppo); di solito {@code Tips.item("lista", voce)}
     */
    @SuppressWarnings("unchecked")
    public static <T> void install(JComboBox<T> combo, Function<? super T, String> tip) {
        combo.putClientProperty(PROPERTY, tip);
        if (combo.getClientProperty(BASE_TIP) == null) {
            combo.putClientProperty(BASE_TIP, combo.getToolTipText() == null ? "" : combo.getToolTipText());
        }
        State state = new State();
        combo.putClientProperty(STATE, state);
        ListCellRenderer<? super T> inner = combo.getRenderer();
        combo.setRenderer((list, value, index, selected, focus) -> {
            Component c = inner.getListCellRendererComponent(list, value, index, selected, focus);
            if (c instanceof JComponent j && index >= 0) {
                j.setToolTipText(null);   // la spiegazione compare accanto alla lista, non sopra la voce
            }
            return c;
        });
        combo.addActionListener(e -> updateBaseTip(combo));
        combo.addPopupMenuListener(new PopupMenuListener() {
            private ListSelectionListener listener;

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                JList<Object> list = popupList(combo);
                if (list == null) {
                    return;
                }
                state.list = list;
                if (listener == null) {
                    listener = ev -> SwingUtilities.invokeLater(() -> show(combo, state));
                    list.addListSelectionListener(listener);
                }
                SwingUtilities.invokeLater(() -> show(combo, state));
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                hide(state);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                hide(state);
            }
        });
        updateBaseTip(combo);
    }

    /** Il suggerimento della lista chiusa: quello della lista, più la spiegazione della voce scelta. */
    private static void updateBaseTip(JComboBox<?> combo) {
        Object base = combo.getClientProperty(BASE_TIP);
        String selected = tipFor(combo, combo.getSelectedItem());
        String b = base == null ? "" : base.toString();
        combo.setToolTipText(selected == null || selected.isBlank() ? (b.isEmpty() ? null : b)
                : (b.isEmpty() ? selected : b + "\n" + selected));
    }

    /** La spiegazione di una voce, se la lista ne ha ({@code null} altrimenti). */
    @SuppressWarnings("unchecked")
    public static String tipFor(JComboBox<?> combo, Object item) {
        Object f = combo.getClientProperty(PROPERTY);
        if (!(f instanceof Function<?, ?> fn) || item == null) {
            return null;
        }
        try {
            return ((Function<Object, String>) fn).apply(item);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /** La lista ha le spiegazioni delle voci. */
    public static boolean installed(JComboBox<?> combo) {
        return combo.getClientProperty(PROPERTY) != null;
    }

    @SuppressWarnings("unchecked")
    private static JList<Object> popupList(JComboBox<?> combo) {
        Object child = combo.getUI().getAccessibleChild(combo, 0);
        return child instanceof ComboPopup p ? (JList<Object>) p.getList() : null;
    }

    private static void show(JComboBox<?> combo, State state) {
        JList<?> list = state.list;
        if (list == null || !list.isShowing()) {
            hide(state);
            return;
        }
        int index = list.getSelectedIndex();
        Object item = index >= 0 ? list.getModel().getElementAt(index) : null;
        String text = tipFor(combo, item);
        if (text == null || text.isBlank()) {
            hide(state);
            return;
        }
        if (text.equals(state.text) && state.popup != null) {
            return;
        }
        hide(state);
        JToolTip tip = list.createToolTip();
        tip.setTipText(text);
        tip.setName("comboTips.tip");
        java.awt.Dimension size = tip.getPreferredSize();
        Point listOnScreen = list.getLocationOnScreen();
        Rectangle cell = list.getCellBounds(index, index);
        Rectangle popupOnScreen = new Rectangle(listOnScreen, list.getVisibleRect().getSize());
        java.awt.Container popupRoot = SwingUtilities.getAncestorOfClass(javax.swing.JPopupMenu.class, list);
        if (popupRoot != null) {
            popupOnScreen = new Rectangle(popupRoot.getLocationOnScreen(), popupRoot.getSize());
        }
        Rectangle screen = screenBounds(combo);
        int gap = com.formdev.flatlaf.util.UIScale.scale(6);
        int x = popupOnScreen.x + popupOnScreen.width + gap;
        if (x + size.width > screen.x + screen.width) {
            x = popupOnScreen.x - gap - size.width;   // a destra non c'è posto: a sinistra della lista
        }
        x = Math.max(screen.x, x);
        int y = listOnScreen.y + (cell == null ? 0 : cell.y - list.getVisibleRect().y);
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height));
        state.popup = PopupFactory.getSharedInstance().getPopup(list, tip, x, y);
        state.tip = tip;
        state.bounds = new Rectangle(x, y, size.width, size.height);
        state.text = text;
        state.popup.show();
    }

    private static void hide(State state) {
        if (state.popup != null) {
            state.popup.hide();
        }
        state.popup = null;
        state.tip = null;
        state.bounds = null;
        state.text = null;
    }

    private static Rectangle screenBounds(Component c) {
        GraphicsConfiguration gc = c.getGraphicsConfiguration();
        if (gc == null) {
            return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }
        Rectangle b = gc.getBounds();
        java.awt.Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }

    /** Dove sta ora il suggerimento accanto alla lista aperta (coordinate dello schermo), {@code null} se non c'è. */
    public static Rectangle shownBounds(JComboBox<?> combo) {
        Object s = combo.getClientProperty(STATE);
        return s instanceof State st && st.bounds != null ? new Rectangle(st.bounds) : null;
    }

    /** Il testo del suggerimento accanto alla lista aperta, {@code null} se non c'è. */
    public static String shownText(JComboBox<?> combo) {
        Object s = combo.getClientProperty(STATE);
        return s instanceof State st ? st.text : null;
    }

    /** I limiti della lista aperta sullo schermo ({@code null} se è chiusa). */
    public static Rectangle popupBounds(JComboBox<?> combo) {
        JList<Object> list = popupList(combo);
        if (list == null || !list.isShowing()) {
            return null;
        }
        java.awt.Container popupRoot = SwingUtilities.getAncestorOfClass(javax.swing.JPopupMenu.class, list);
        Component c = popupRoot != null ? popupRoot : list;
        return new Rectangle(c.getLocationOnScreen(), c.getSize());
    }
}
