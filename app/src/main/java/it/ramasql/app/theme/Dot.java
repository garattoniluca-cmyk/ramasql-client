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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/**
 * Punto colorato (stato della connessione, tipo di server nelle pillole, §3.8): un cerchio pieno con, se richiesto,
 * un alone tenue dello stesso colore. Diametro in px al 100%, scalato con l'interfaccia.
 */
public final class Dot implements Icon {

    private final Color color;
    private final int diameter;
    private final boolean halo;

    public Dot(Color color, int diameter, boolean halo) {
        this.color = color;
        this.diameter = diameter;
        this.halo = halo;
    }

    /** Il punto della barra di stato: 8 px con alone. */
    public static Dot status(Color color) {
        return new Dot(color, 8, true);
    }

    /** Il punto dentro una pillola: 6 px, senza alone. */
    public static Dot pill(Color color) {
        return new Dot(color, 6, false);
    }

    public Color color() {
        return color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = getIconWidth();
            int d = Tokens.px(diameter);
            int off = (size - d) / 2;
            if (halo) {
                g2.setColor(Tokens.alpha(color, 48));
                g2.fillOval(x, y, size, size);
            }
            g2.setColor(color);
            g2.fillOval(x + off, y + off, d, d);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return Tokens.px(halo ? diameter + 6 : diameter);
    }

    @Override
    public int getIconHeight() {
        return getIconWidth();
    }
}
