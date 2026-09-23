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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Pillola: un'etichetta breve su fondo tinto con i bordi arrotondati (origine nel registro, rischio nell'anteprima,
 * tipo di server). {@code DESIGN-SYSTEM.md} §1.3 (raggio «pillola»).
 */
public final class Pill extends JLabel {

    private static final long serialVersionUID = 1L;

    private Color fill;
    private Color outline;
    private boolean ready;

    public Pill(String text, Color foreground, Color fill) {
        super(text, SwingConstants.CENTER);
        setColors(foreground, fill);
        setOpaque(false);
        setIconTextGap(Tokens.px(6));
        ready = true;
        updateUI();
    }

    /**
     * Pillola «con punto»: un punto pieno del colore dato (≥ 3:1), fondo tinto dello stesso colore e testo
     * {@code text.primary} (≥ 4,5:1 anche quando il colore, come l'arancio di MySQL, non basterebbe per il testo).
     * La usano il tipo di server (§3.2, §3.8) e gli stati.
     */
    public static Pill dotted(String text, Color color) {
        Pill pill = new Pill(text, Tokens.TEXT_PRIMARY, Tokens.alpha(color, 30));
        pill.setIcon(Dot.pill(color));
        pill.outline = Tokens.alpha(color, 70);
        return pill;
    }

    /** Cambia colore a una pillola «con punto». */
    public void setDotColor(Color color) {
        setIcon(Dot.pill(color));
        fill = Tokens.alpha(color, 30);
        outline = Tokens.alpha(color, 70);
        setForeground(Tokens.TEXT_PRIMARY);
        repaint();
    }

    /** Il carattere ({@code caption} semibold) e i margini seguono la dimensione del carattere delle impostazioni. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (ready) {
            setFont(Tokens.semibold(Tokens.CAPTION));
            setBorder(BorderFactory.createEmptyBorder(Tokens.px(2), Tokens.px(8), Tokens.px(2), Tokens.px(8)));
        }
    }

    public void setColors(Color foreground, Color background) {
        setForeground(foreground);
        this.fill = background;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (fill != null && getText() != null && !getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                int h = getHeight();
                g2.fillRoundRect(0, 0, getWidth(), h, h, h);
                if (outline != null) {
                    g2.setColor(outline);
                    g2.drawRoundRect(0, 0, getWidth() - 1, h - 1, h - 1, h - 1);
                }
            } finally {
                g2.dispose();
            }
        }
        super.paintComponent(g);
    }
}
