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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.JScrollPane;
import javax.swing.RootPaneContainer;

/**
 * Le finestre dentro lo schermo (T12.6, T12.13): al proiettore (1024×768) o su un portatile (1366×768), con il
 * carattere al massimo, nessuna finestra di dialogo esce dai bordi. Dopo {@code pack()} si chiama {@link #fit}: se la
 * finestra è più grande dello schermo utilizzabile (senza la barra delle applicazioni), il contenuto diventa scorrevole
 * e la finestra si riduce; in ogni caso si sposta dentro i bordi. Per le prove, {@code -Dramasql.screen=1024x768}
 * emula uno schermo di quella misura.
 */
public final class Screens {

    /** Proprietà di sistema che emula la misura dello schermo, es. {@code 1024x768}. */
    public static final String EMULATED = "ramasql.screen";
    /** Altezza della barra delle applicazioni nello schermo emulato. */
    static final int TASKBAR = 40;

    private Screens() {
    }

    /** Lo spazio utilizzabile dello schermo della finestra (quello emulato, se c'è). */
    public static Rectangle usable(Component c) {
        String emulated = System.getProperty(EMULATED);
        if (emulated != null && emulated.matches("\\d+x\\d+")) {
            String[] wh = emulated.split("x");
            return new Rectangle(0, 0, Integer.parseInt(wh[0]), Integer.parseInt(wh[1]) - TASKBAR);
        }
        GraphicsConfiguration gc = c == null ? null : c.getGraphicsConfiguration();
        if (gc == null) {
            return new Rectangle(new Dimension(Toolkit.getDefaultToolkit().getScreenSize()));
        }
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }

    /** La finestra dentro lo schermo: ridotta (con il contenuto scorrevole) se non ci sta, e spostata dentro i bordi. */
    public static void fit(Window w) {
        Rectangle s = usable(w);
        Dimension d = w.getSize();
        if (d.width > s.width || d.height > s.height) {
            if (w instanceof RootPaneContainer rp && !(rp.getContentPane() instanceof JScrollPane)) {
                Container content = rp.getContentPane();
                JScrollPane scroll = new JScrollPane(content);
                scroll.setBorder(null);
                scroll.getVerticalScrollBar().setUnitIncrement(Tokens.px(24));
                scroll.getHorizontalScrollBar().setUnitIncrement(Tokens.px(24));
                rp.setContentPane(scroll);
            }
            w.setSize(Math.min(d.width, s.width), Math.min(d.height, s.height));
            w.validate();
        }
        Rectangle r = w.getBounds();
        int x = Math.max(s.x, Math.min(r.x, s.x + s.width - r.width));
        int y = Math.max(s.y, Math.min(r.y, s.y + s.height - r.height));
        w.setLocation(x, y);
    }
}
