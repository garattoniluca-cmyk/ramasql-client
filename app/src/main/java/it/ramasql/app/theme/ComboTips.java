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
import java.awt.Point;
import java.awt.Rectangle;
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
                fitRows(combo);
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

    private static final String MAX_ROWS = "rama.comboTips.maxRows";

    /**
     * Le righe della lista aperta, quante ne stanno sullo schermo (T12.13): al proiettore, con il carattere al
     * massimo, una lista lunga come quella dei tipi uscirebbe dallo schermo; si accorcia (e scorre) quanto basta.
     */
    static void fitRows(JComboBox<?> combo) {
        if (!(combo.getClientProperty(MAX_ROWS) instanceof Integer wanted)) {
            combo.putClientProperty(MAX_ROWS, combo.getMaximumRowCount());
            fitRows(combo);
            return;
        }
        if (!combo.isShowing() || combo.getItemCount() == 0) {
            return;
        }
        Rectangle screen = Screens.usable(combo);
        Point at = combo.getLocationOnScreen();
        int room = Math.max(at.y - screen.y, screen.y + screen.height - (at.y + combo.getHeight()))
                - com.formdev.flatlaf.util.UIScale.scale(16);
        @SuppressWarnings("unchecked")
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) combo.getRenderer();
        JList<Object> list = popupList(combo);
        int row = list != null && list.getFixedCellHeight() > 0 ? list.getFixedCellHeight()
                : renderer.getListCellRendererComponent(list != null ? list : new JList<>(), combo.getItemAt(0), 0,
                        false, false).getPreferredSize().height;
        combo.setMaximumRowCount(Math.max(3, Math.min(wanted, room / Math.max(1, row))));
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
        Point listOnScreen = list.getLocationOnScreen();
        Rectangle cell = list.getCellBounds(index, index);
        Rectangle popupOnScreen = new Rectangle(listOnScreen.x, listOnScreen.y + list.getVisibleRect().y,
                list.getVisibleRect().width, list.getVisibleRect().height);
        java.awt.Container popupRoot = SwingUtilities.getAncestorOfClass(javax.swing.JPopupMenu.class, list);
        if (popupRoot != null) {
            popupOnScreen = new Rectangle(popupRoot.getLocationOnScreen(), popupRoot.getSize());
        }
        Rectangle screen = Screens.usable(combo);
        int gap = com.formdev.flatlaf.util.UIScale.scale(6);
        int itemY = cell == null ? popupOnScreen.y : listOnScreen.y + cell.y;   // la lista scorre: la sua origine è già spostata
        java.awt.Dimension size = tip.getPreferredSize();
        int room = Math.max(screen.x + screen.width - (popupOnScreen.x + popupOnScreen.width),
                popupOnScreen.x - screen.x) - gap;
        if (size.width > room && room > 0) {
            // al proiettore la spiegazione intera non sta accanto alla lista: va a capo più stretta, sul lato più largo
            java.awt.Insets in = tip.getInsets();
            tip.putClientProperty(RamaToolTipUI.NARROW, Math.max(com.formdev.flatlaf.util.UIScale.scale(120),
                    room - in.left - in.right - com.formdev.flatlaf.util.UIScale.scale(2 * RamaToolTipUI.PAD_X)));
            size = tip.getPreferredSize();
        }
        Rectangle place = place(popupOnScreen, itemY, size, screen, gap);
        int x = place.x;
        int y = place.y;
        state.popup = PopupFactory.getSharedInstance().getPopup(list, tip, x, y);
        state.tip = tip;
        state.bounds = new Rectangle(x, y, size.width, size.height);
        state.text = text;
        state.popup.show();
    }

    /**
     * Dove mettere la spiegazione (coordinate dello schermo): a destra della lista aperta, o a sinistra se a destra
     * non c'è posto; all'altezza della voce, ma sempre dentro lo schermo.
     */
    static Rectangle place(Rectangle popup, int itemY, java.awt.Dimension size, Rectangle screen, int gap) {
        int right = popup.x + popup.width + gap;
        int left = popup.x - gap - size.width;
        int x;
        if (right + size.width <= screen.x + screen.width) {
            x = right;
        } else if (left >= screen.x) {
            x = left;
        } else {
            // nessun lato basta: il lato più largo, tenendo la spiegazione dentro lo schermo
            boolean rightWider = screen.x + screen.width - right >= popup.x - gap - screen.x;
            x = rightWider ? Math.min(right, screen.x + screen.width - size.width) : Math.max(screen.x, left);
        }
        int y = Math.max(screen.y, Math.min(itemY, screen.y + screen.height - size.height));
        return new Rectangle(x, y, size.width, size.height);
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
