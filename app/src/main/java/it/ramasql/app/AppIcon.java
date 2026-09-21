/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Icona provvisoria disegnata a codice (quadrato arrotondato con «R»); quella definitiva arriva con l'installer. */
final class AppIcon {

    private AppIcon() {
    }

    static List<Image> images() {
        List<Image> list = new ArrayList<>();
        for (int size : new int[] {16, 24, 32, 48, 64, 128, 256}) {
            list.add(draw(size));
        }
        return list;
    }

    private static Image draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x1F6FEB));
        int arc = Math.max(4, size / 4);
        g.fillRoundRect(0, 0, size, size, arc, arc);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size * 0.68f)));
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth("R")) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString("R", x, y);
        g.dispose();
        return img;
    }
}
