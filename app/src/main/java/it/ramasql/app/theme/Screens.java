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

import java.awt.BorderLayout;
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

    private static boolean installed;

    /**
     * Ogni finestra di dialogo che si apre (anche quelle dei messaggi e delle domande, e quelle del query builder) si
     * sistema dentro lo schermo: una rete di sicurezza oltre alle chiamate esplicite a {@link #fit}.
     */
    public static synchronized void install() {
        if (!(javax.swing.PopupFactory.getSharedInstance() instanceof InsideScreen)) {
            javax.swing.PopupFactory.setSharedInstance(new InsideScreen(javax.swing.PopupFactory.getSharedInstance()));
        }
        if (installed) {
            return;
        }
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == java.awt.event.WindowEvent.WINDOW_OPENED && e.getSource() instanceof java.awt.Dialog d) {
                fit(d);
            }
        }, java.awt.AWTEvent.WINDOW_EVENT_MASK);
    }

    /**
     * I popup (suggerimenti, liste aperte, menu) restano dentro lo schermo utilizzabile (T12.13): se la posizione
     * proposta li farebbe uscire da un bordo, si spostano all'interno. Su uno schermo vero Swing li tiene già dentro,
     * e qui non cambia nulla; con lo schermo emulato delle prove si comporta come uno schermo di quella misura.
     */
    static final class InsideScreen extends javax.swing.PopupFactory {

        private final javax.swing.PopupFactory delegate;

        InsideScreen(javax.swing.PopupFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public javax.swing.Popup getPopup(Component owner, Component contents, int x, int y) {
            if (contents != null) {
                Rectangle s = usable(owner);
                Dimension d = contents.getPreferredSize();
                x = Math.max(s.x, Math.min(x, s.x + s.width - d.width));
                y = Math.max(s.y, Math.min(y, s.y + s.height - d.height));
            }
            return delegate.getPopup(owner, contents, x, y);
        }
    }

    /** Proprietà della radice di una finestra già adattata allo schermo. */
    public static final String FITTED = "ramasql.screens.fitted";

    /**
     * Il contenuto diventa scorrevole, ma i pulsanti restano sempre in vista: se il contenuto è diviso con
     * {@link BorderLayout}, scorre solo la parte centrale (se non scorre già da sé); altrimenti scorre tutto.
     */
    private static void scrollCenter(RootPaneContainer rp) {
        Container content = rp.getContentPane();
        if (content.getLayout() instanceof BorderLayout layout && layout.getLayoutComponent(BorderLayout.CENTER) != null) {
            Component center = layout.getLayoutComponent(BorderLayout.CENTER);
            if (!(center instanceof JScrollPane)) {
                content.remove(center);
                content.add(scroll(center), BorderLayout.CENTER);
            }
            return;
        }
        rp.setContentPane(scroll(content));
    }

    private static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(Tokens.px(24));
        scroll.getHorizontalScrollBar().setUnitIncrement(Tokens.px(24));
        return scroll;
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
        Dimension min = w.getMinimumSize();
        if (min.width > s.width || min.height > s.height) {
            w.setMinimumSize(new Dimension(Math.min(min.width, s.width), Math.min(min.height, s.height)));
        }
        if (d.width > s.width || d.height > s.height) {
            // una finestra di dialogo diventa scorrevole; la finestra principale si riduce e basta (ha i divisori)
            if (w instanceof javax.swing.JDialog && w instanceof RootPaneContainer rp
                    && rp.getRootPane().getClientProperty(FITTED) == null) {
                rp.getRootPane().putClientProperty(FITTED, Boolean.TRUE);
                scrollCenter(rp);
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
