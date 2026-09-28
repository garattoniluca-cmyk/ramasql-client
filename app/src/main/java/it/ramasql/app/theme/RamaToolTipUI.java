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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToolTipUI;

import com.formdev.flatlaf.util.UIScale;

/**
 * Il suggerimento unico del programma ({@code DESIGN-SYSTEM.md} §3.9, {@code ADR-020}): riquadro chiaro con bordo,
 * pensato per essere <b>letto</b>. Il testo va a capo da solo entro una larghezza massima (360 px al carattere
 * normale, e cresce con il carattere scelto nelle impostazioni, ma mai oltre i tre quinti dello schermo: al proiettore
 * resta leggibile e non attraversa lo schermo); una prima riga fra «**» diventa il titolo in grassetto (il nome della cosa, per le voci delle liste); le
 * righe che cominciano con «Attenzione:» sono nel colore d'avviso. Il testo HTML, dove c'è, si disegna come prima.
 */
public final class RamaToolTipUI extends BasicToolTipUI {

    /** Larghezza massima del testo al carattere normale (13 pt), in pixel non scalati. */
    public static final int MAX_WIDTH = 360;
    static final int PAD_X = 12;
    private static final int PAD_Y = 10;

    public static ComponentUI createUI(JComponent c) {
        return new RamaToolTipUI();
    }

    /** Il testo diviso in titolo (facoltativo) e paragrafi. */
    record Content(String title, List<String> paragraphs) {
    }

    static Content parse(String text) {
        String t = text == null ? "" : text.strip();
        String title = null;
        if (t.startsWith("**")) {
            int end = t.indexOf("**", 2);
            if (end > 2) {
                title = t.substring(2, end).strip();
                t = t.substring(end + 2).strip();
            }
        }
        List<String> paragraphs = new ArrayList<>();
        for (String p : t.split("\n")) {
            if (!p.isBlank()) {
                paragraphs.add(p.strip());
            }
        }
        return new Content(title, paragraphs);
    }

    private static boolean isHtml(String text) {
        return text != null && text.regionMatches(true, 0, "<html>", 0, 6);
    }

    /** Proprietà del suggerimento: larghezza massima del testo più stretta di quella normale (px, Integer). */
    public static final String NARROW = "ramasql.tip.narrow";

    /** Larghezza massima del testo: 360 px al carattere normale, in proporzione al carattere in uso. */
    static int maxWidth(Font font) {
        return Math.round(UIScale.scale(MAX_WIDTH) * Math.max(1f, font.getSize2D() / UIScale.scale(13f)));
    }

    /** Le righe a capo di un paragrafo entro la larghezza data. */
    static List<String> wrap(String paragraph, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= width || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    private record Layout(Font titleFont, Font textFont, List<String> titleLines, List<List<String>> lines, int width,
            int height) {
    }

    private Layout layout(JToolTip tip) {
        Font text = tip.getFont();
        Font title = text.deriveFont(Font.BOLD);
        Content content = parse(tip.getTipText());
        FontMetrics fm = tip.getFontMetrics(text);
        FontMetrics tm = tip.getFontMetrics(title);
        // al proiettore, con il carattere al massimo, al più tre quinti dello schermo: le righe restano leggibili
        int max = Math.min(maxWidth(text), Screens.usable(tip).width * 3 / 5);
        if (tip.getClientProperty(NARROW) instanceof Integer narrow && narrow > 0) {
            max = Math.min(max, narrow);   // accanto a una lista aperta, lo spazio che resta sullo schermo
        }
        List<String> titleLines = content.title() == null ? List.of() : wrap(content.title(), tm, max);
        List<List<String>> lines = new ArrayList<>();
        int w = 0;
        for (String t : titleLines) {
            w = Math.max(w, tm.stringWidth(t));
        }
        int h = titleLines.size() * tm.getHeight();
        int gap = UIScale.scale(4);
        for (String p : content.paragraphs()) {
            List<String> l = wrap(p, fm, max);
            lines.add(l);
            for (String s : l) {
                w = Math.max(w, fm.stringWidth(s));
            }
            h += l.size() * fm.getHeight();
        }
        h += Math.max(0, lines.size() - 1 + (titleLines.isEmpty() ? 0 : 1)) * gap;
        return new Layout(title, text, titleLines, lines, w, h);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        JToolTip tip = (JToolTip) c;
        if (isHtml(tip.getTipText())) {
            return super.getPreferredSize(c);
        }
        Layout l = layout(tip);
        Insets in = c.getInsets();
        return new Dimension(l.width() + UIScale.scale(2 * PAD_X) + in.left + in.right,
                l.height() + UIScale.scale(2 * PAD_Y) + in.top + in.bottom);
    }

    @Override
    public void paint(Graphics g0, JComponent c) {
        JToolTip tip = (JToolTip) c;
        if (isHtml(tip.getTipText())) {
            super.paint(g0, c);
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Layout l = layout(tip);
            Insets in = c.getInsets();
            int x = in.left + UIScale.scale(PAD_X);
            int y = in.top + UIScale.scale(PAD_Y);
            int gap = UIScale.scale(4);
            g.setFont(l.titleFont());
            FontMetrics tm = g.getFontMetrics();
            g.setColor(Tokens.TEXT_PRIMARY);
            for (String t : l.titleLines()) {
                g.drawString(t, x, y + tm.getAscent());
                y += tm.getHeight();
            }
            if (!l.titleLines().isEmpty()) {
                y += gap;
            }
            g.setFont(l.textFont());
            FontMetrics fm = g.getFontMetrics();
            for (List<String> paragraph : l.lines()) {
                boolean warning = !paragraph.isEmpty() && paragraph.get(0).startsWith("Attenzione");
                g.setColor(warning ? Tokens.WARNING : Tokens.TEXT_PRIMARY);
                for (String s : paragraph) {
                    g.drawString(s, x, y + fm.getAscent());
                    y += fm.getHeight();
                }
                y += gap;
            }
        } finally {
            g.dispose();
        }
    }
}
