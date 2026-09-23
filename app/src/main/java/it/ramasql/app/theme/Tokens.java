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
import java.awt.Font;

import javax.swing.UIManager;

import com.formdev.flatlaf.util.UIScale;

/**
 * I <b>token</b> del sistema visivo ({@code docs/DESIGN-SYSTEM.md} §1) per i componenti disegnati a mano: nessun
 * colore si scrive nel codice fuori da qui (e dal tema FlatLaf, {@code RamaSqlLaf.properties}, che usa gli stessi
 * valori: lo verifica un test). Tutte le misure sono in px al 100% e si scalano con {@link #px(int)}; i caratteri
 * seguono la dimensione scelta nelle impostazioni ({@link #font(int, int)}).
 */
public final class Tokens {

    // ---------------------------------------------------------------- colori (§1.1)
    public static final Color BG_WINDOW = new Color(0xF6F7F9);
    public static final Color BG_SURFACE = new Color(0xFFFFFF);
    public static final Color BG_SUNKEN = new Color(0xEEF1F5);
    public static final Color BORDER_SUBTLE = new Color(0xE4E7EC);
    public static final Color BORDER_DEFAULT = new Color(0xD0D5DD);
    public static final Color TEXT_PRIMARY = new Color(0x101828);
    public static final Color TEXT_SECONDARY = new Color(0x475467);
    public static final Color TEXT_TERTIARY = new Color(0x98A2B3);
    public static final Color ACCENT = new Color(0x2563EB);
    public static final Color ACCENT_HOVER = new Color(0x1D4ED8);
    public static final Color ACCENT_PRESSED = new Color(0x1E40AF);
    public static final Color ACCENT_TINT = new Color(0xEAF1FF);
    public static final Color SUCCESS = new Color(0x16A34A);
    public static final Color SUCCESS_TINT = new Color(0xEAF7EE);
    public static final Color WARNING = new Color(0xD97706);
    public static final Color WARNING_TINT = new Color(0xFFF4DB);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color DANGER_TINT = new Color(0xFDECEC);
    public static final Color ENGINE_INNODB = new Color(0x2563EB);
    public static final Color ENGINE_MYISAM = new Color(0x8B5CF6);
    public static final Color SERVER_MARIADB = new Color(0x0E7490);
    public static final Color SERVER_MYSQL = new Color(0xEA580C);
    /** Chiave primaria (icona «chiave oro», §2). */
    public static final Color KEY_GOLD = new Color(0xCA8A04);
    /** Testo e icone sopra un fondo pieno d'accento o di pericolo (pulsante primario, §3.1: «testo bianco»). */
    public static final Color ON_ACCENT = new Color(0xFFFFFF);
    /** Zebratura della griglia sulle righe pari (§3.4). */
    public static final Color BG_ZEBRA = new Color(0xFAFBFC);
    /** Ombra {@code elev.1}: tessere a riposo, {@code 0 1 2 rgba(16,24,40,.06)} (§1.3). */
    public static final Color SHADOW_1 = new Color(16, 24, 40, 15);
    /** Ombra {@code elev.2}: tessera al passaggio e menu, {@code 0 8 24 rgba(16,24,40,.12)} (§1.3). */
    public static final Color SHADOW_2 = new Color(16, 24, 40, 31);

    // ---------------------------------------------------------------- sintassi SQL (§1.1)
    public static final Color SQL_KEYWORD = new Color(0x2563EB);
    public static final Color SQL_STRING = new Color(0xB45309);
    public static final Color SQL_NUMBER = new Color(0x047857);
    public static final Color SQL_COMMENT = new Color(0x98A2B3);
    public static final Color SQL_FUNCTION = new Color(0x7C3AED);
    public static final Color SQL_IDENTIFIER = new Color(0x101828);
    public static final Color SQL_OPERATOR = new Color(0x475467);
    public static final Color SQL_CURRENT_LINE = new Color(0xF5F8FF);
    public static final Color SQL_SELECTION = new Color(0xDCE7FF);

    // ---------------------------------------------------------------- spaziature, raggi, altezze (§1.3), px al 100%
    public static final int SPACE_4 = 4;
    public static final int SPACE_8 = 8;
    public static final int SPACE_12 = 12;
    public static final int SPACE_16 = 16;
    public static final int SPACE_24 = 24;
    public static final int SPACE_32 = 32;
    public static final int SPACE_48 = 48;
    public static final int RADIUS_CONTROL = 6;
    public static final int RADIUS_PILL = 999;
    public static final int RADIUS_TILE = 12;
    public static final int RADIUS_DIALOG = 12;
    public static final int RADIUS_TREE_SELECTION = 6;
    /** Riga di griglia (§1.3). */
    public static final int ROW_HEIGHT = 28;
    public static final int TOOLBAR_HEIGHT = 44;
    public static final int CONTROL_HEIGHT = 32;
    public static final int STATUS_BAR_HEIGHT = 26;
    /** Intestazione della griglia su due righe, nome e tipo (§3.4). */
    public static final int GRID_HEADER_HEIGHT = 44;
    /** Gutter dei numeri di riga (§3.4). */
    public static final int GUTTER_WIDTH = 44;
    /** Tessera della schermata iniziale (§3.2). */
    public static final int TILE_WIDTH = 260;
    public static final int TILE_HEIGHT = 132;
    /** Icone: barra strumenti 20, albero e menu 16 (§2). */
    public static final int ICON_TOOLBAR = 20;
    public static final int ICON_SMALL = 16;
    public static final int TREE_ROW_HEIGHT = 28;

    // ---------------------------------------------------------------- tipografia (§1.2), px al 100%
    public static final int CAPTION = 11;
    public static final int SMALL = 12;
    public static final int BODY = 13;
    public static final int EMPHASIS = 13;
    public static final int TITLE = 15;
    public static final int HEADING = 18;
    public static final int DISPLAY = 24;

    /** Famiglie dei caratteri (§1.2): di sistema, nessun file da distribuire. */
    public static final String UI_FONT = "Segoe UI Variable Text";
    public static final String UI_FALLBACK = "Segoe UI";
    public static final String MONO_FONT = "Cascadia Mono";
    public static final String MONO_FALLBACK = "Consolas";

    private Tokens() {
    }

    /** Un valore in px al 100% scalato come l'interfaccia. */
    public static int px(int value) {
        return UIScale.scale(value);
    }

    /**
     * Il carattere dell'interfaccia a una dimensione della scala (§1.2), in proporzione alla dimensione scelta nelle
     * impostazioni (il carattere di base vale {@link #BODY}).
     */
    public static Font font(int size, int style) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = UIManager.getFont("Label.font");
        }
        float scaled = base.getSize2D() * size / BODY;
        return base.deriveFont(style, scaled);
    }

    /** Carattere per il codice (Cascadia Mono → Consolas → monospaziato), alla dimensione indicata della scala. */
    public static Font mono(int size) {
        Font ui = font(size, Font.PLAIN);
        for (String family : new String[] {MONO_FONT, MONO_FALLBACK}) {
            Font f = new Font(family, Font.PLAIN, 1);
            if (family.equalsIgnoreCase(f.getFamily())) {
                return f.deriveFont(ui.getSize2D());
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 1).deriveFont(ui.getSize2D());
    }

    /**
     * Il carattere <em>semibold</em> dell'interfaccia (Segoe UI Semibold su Windows) alla dimensione indicata della scala:
     * titoli, nomi di colonna, {@code emphasis}. Se il sistema non ha il peso intermedio, grassetto.
     */
    public static Font semibold(int size) {
        Font semi = UIManager.getFont("rama.emphasis.font");
        Font plain = font(size, Font.PLAIN);
        if (semi == null || semi.getFamily().equals(plain.getFamily())) {
            return plain.deriveFont(Font.BOLD);
        }
        return semi.deriveFont(Font.PLAIN, plain.getSize2D());
    }

    /** Il colore a metà strada ({@code t} da 0 a 1) tra due token, opaco: tinte intermedie senza nuovi valori. */
    public static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /**
     * La versione «forte» di un colore di stato, per i segni che stanno <em>sulla sua tinta</em> (barra del gutter,
     * «+»/«−», triangolino della cella modificata, testo barrato): un quarto verso {@code text.primary}, così il
     * contrasto sulla tinta supera 3:1 per i segni e 4,5:1 per il testo (lo verifica ContrastTest).
     */
    public static Color strong(Color state) {
        return mix(state, TEXT_PRIMARY, 0.25);
    }

    /** Lo stesso colore con l'opacità indicata (0–255): ombre, velature. */
    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** {@code #rrggbb}, per gli stili FlatLaf e l'HTML dei componenti Swing. */
    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
