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

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JComponent;

/**
 * Una tastiera per le prove (T12.4): ogni tasto è un evento vero messo nella coda di AWT e consegnato al componente
 * che ha il fuoco, come un tasto premuto dall'utente (niente {@code doClick}, niente chiamate dirette). Tiene il conto
 * dei tasti premuti.
 */
final class Keys {

    /** I tasti premuti, in ordine, per l'evidenza. */
    final List<String> log = new ArrayList<>();
    int presses;

    static Component owner() {
        return fromEdt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    }

    static String describe(Component c) {
        if (c == null) {
            return "(nessuno)";
        }
        return c.getClass().getSimpleName() + (c.getName() == null ? "" : " «" + c.getName() + "»");
    }

    private static void post(KeyEvent e) {
        EventQueue q = Toolkit.getDefaultToolkit().getSystemEventQueue();
        q.postEvent(e);
    }

    /** Aspetta che la coda abbia consegnato tutto (anche i dialoghi modali aperti nel frattempo). */
    static void settle() {
        for (int i = 0; i < 3; i++) {
            onEdt(() -> { });
        }
    }

    /** Un tasto (con i modificatori), premuto e rilasciato. */
    Keys press(int code, int modifiers) {
        Component target = waitOwner();
        long now = System.currentTimeMillis();
        char ch = KeyEvent.CHAR_UNDEFINED;
        if (code == KeyEvent.VK_ENTER) {
            ch = '\n';
        } else if (code == KeyEvent.VK_SPACE) {
            ch = ' ';
        } else if (code == KeyEvent.VK_TAB) {
            ch = '\t';
        }
        post(new KeyEvent(target, KeyEvent.KEY_PRESSED, now, modifiers, code, ch));
        if (ch != KeyEvent.CHAR_UNDEFINED && (modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK)) == 0) {
            post(new KeyEvent(target, KeyEvent.KEY_TYPED, now, modifiers, KeyEvent.VK_UNDEFINED, ch));
        }
        post(new KeyEvent(target, KeyEvent.KEY_RELEASED, now + 1, modifiers, code, ch));
        settle();
        presses++;
        log.add(KeyEvent.getModifiersExText(modifiers) + (modifiers == 0 ? "" : "+") + KeyEvent.getKeyText(code));
        return this;
    }

    Keys press(int code) {
        return press(code, 0);
    }

    Keys times(int code, int n) {
        for (int i = 0; i < n; i++) {
            press(code);
        }
        return this;
    }

    /** Scrive un testo carattere per carattere (premuto, digitato, rilasciato). */
    Keys type(String text) {
        for (char c : text.toCharArray()) {
            Component target = waitOwner();
            long now = System.currentTimeMillis();
            int code = KeyEvent.getExtendedKeyCodeForChar(c);
            int mods = Character.isUpperCase(c) ? InputEvent.SHIFT_DOWN_MASK : 0;
            post(new KeyEvent(target, KeyEvent.KEY_PRESSED, now, mods, code, c));
            post(new KeyEvent(target, KeyEvent.KEY_TYPED, now, mods, KeyEvent.VK_UNDEFINED, c));
            post(new KeyEvent(target, KeyEvent.KEY_RELEASED, now + 1, mods, code, c));
            settle();
            presses++;
        }
        log.add("«" + text + "»");
        return this;
    }

    /** Tab (o Maiusc+Tab) finché il fuoco arriva su un componente che soddisfa la condizione. */
    Component tabTo(String what, Predicate<Component> target, boolean backwards) {
        for (int i = 0; i < 120; i++) {
            Component c = owner();
            if (c != null && fromEdt(() -> target.test(c))) {
                return c;
            }
            // dalle tabelle e dagli editor di testo si esce con Ctrl+Tab (Tab lì sposta la cella o scrive)
            boolean ctrl = c instanceof javax.swing.JTable || c instanceof javax.swing.JTextArea;
            press(KeyEvent.VK_TAB, (backwards ? InputEvent.SHIFT_DOWN_MASK : 0) | (ctrl ? InputEvent.CTRL_DOWN_MASK : 0));
        }
        throw new AssertionError("con Tab non si arriva a " + what + " (fuoco su " + describe(owner()) + ")");
    }

    Component tabTo(String name) {
        return tabTo("«" + name + "»", c -> name.equals(c.getName()), false);
    }

    /** Aspetta che qualcuno abbia il fuoco (dopo l'apertura di una finestra). */
    static Component waitOwner() {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Component c = owner();
            if (c != null) {
                return c;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("nessun componente ha il fuoco della tastiera");
    }

    /** Aspetta che il fuoco sia in una finestra del tipo dato (per esempio un dialogo appena aperto). */
    static <T extends Window> T waitWindow(Class<T> type) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Window w = fromEdt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow());
            if (type.isInstance(w)) {
                return type.cast(w);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("la finestra " + type.getSimpleName() + " non ha preso il fuoco (fuoco su "
                + describe(owner()) + ")");
    }

    static boolean named(Component c, String name) {
        return c instanceof JComponent && name.equals(c.getName());
    }
}
