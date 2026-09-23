/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import it.ramasql.app.theme.Tokens;

/**
 * Fasce di stato (DESIGN-SYSTEM §1.1, §5): una riga per messaggio, con fondo tinto, barra sinistra del colore dello
 * stato e <b>glifo</b> (lo stato non è mai affidato al solo colore): errori {@code ✘} su {@code danger.tint}, avvisi
 * {@code ⚠} su {@code warning.tint}, esito positivo {@code ✔} su {@code success.tint}, informazioni {@code ℹ} su
 * {@code accent.tint}; le righe neutre (elenco delle istruzioni) su {@code bg.sunken}.
 */
final class Banner extends JPanel {

    private static final long serialVersionUID = 1L;

    enum Tone {
        DANGER("✘", Tokens.DANGER, Tokens.DANGER_TINT),
        WARNING("⚠", Tokens.WARNING, Tokens.WARNING_TINT),
        SUCCESS("✔", Tokens.SUCCESS, Tokens.SUCCESS_TINT),
        INFO("ℹ", Tokens.ACCENT, Tokens.ACCENT_TINT),
        NEUTRAL("", Tokens.BORDER_DEFAULT, Tokens.BG_SUNKEN);

        final String glyph;
        final Color color;
        final Color tint;

        Tone(String glyph, Color color, Color tint) {
            this.glyph = glyph;
            this.color = color;
            this.tint = tint;
        }
    }

    record Line(Tone tone, String text) {
    }

    private final List<Line> lines = new ArrayList<>();

    Banner(String name) {
        setName(name);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setVisible(false);
    }

    void clear() {
        set(List.of());
    }

    void set(List<Line> newLines) {
        lines.clear();
        lines.addAll(newLines);
        removeAll();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                add(Box.createVerticalStrut(Tokens.SPACE_4));
            }
            add(row(lines.get(i)));
        }
        setVisible(!lines.isEmpty());
        revalidate();
        repaint();
    }

    void set(Tone tone, String text) {
        set(text == null || text.isEmpty() ? List.of() : List.of(new Line(tone, text)));
    }

    /** Errori prima, poi avvisi. */
    void setProblems(List<Checks.Problem> problems) {
        List<Line> out = new ArrayList<>();
        problems.stream().filter(Checks.Problem::isError).forEach(p -> out.add(new Line(Tone.DANGER, p.message())));
        problems.stream().filter(p -> !p.isError()).forEach(p -> out.add(new Line(Tone.WARNING, p.message())));
        set(out);
    }

    List<Line> lines() {
        return List.copyOf(lines);
    }

    /** Il testo come lo legge l'utente: una riga per messaggio, con il suo glifo. */
    String text() {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            sb.append(sb.isEmpty() ? "" : "\n").append(l.tone().glyph.isEmpty() ? "" : l.tone().glyph + " ")
                    .append(l.text());
        }
        return sb.toString();
    }

    private static JPanel row(Line line) {
        JPanel row = new JPanel(new BorderLayout(Tokens.SPACE_8, 0));
        row.setBackground(line.tone().tint);
        row.setOpaque(true);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, line.tone().color),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        row.setAlignmentX(LEFT_ALIGNMENT);
        if (!line.tone().glyph.isEmpty()) {
            JLabel glyph = new JLabel(line.tone().glyph);
            glyph.setForeground(line.tone().color);
            glyph.setFont(glyph.getFont().deriveFont(Font.BOLD));
            glyph.setVerticalAlignment(SwingConstants.TOP);
            row.add(glyph, BorderLayout.WEST);
        }
        row.add(wrapText(line.text(), Tokens.TEXT_PRIMARY), BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return row;
    }

    /** Testo a capo automatico che non allarga il contenitore oltre una misura leggibile. */
    static JTextArea wrapText(String text, Color foreground) {
        JTextArea area = new JTextArea(text) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, 640), d.height);
            }
        };
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setForeground(foreground);
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            area.setFont(font);
        }
        return area;
    }
}
