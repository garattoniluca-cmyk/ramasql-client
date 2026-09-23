/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.navigator;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

import javax.swing.Icon;

import com.formdev.flatlaf.util.UIScale;

import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableSummary;

/**
 * <b>Unico punto d'accesso</b> alle icone del navigatore, dell'anteprima e del pannello SQL: per ora disegnate in codice
 * (16×16, tratto 1,5, colori dai {@link Tokens}, nessuna immagine di terzi), da sostituire con le icone SVG
 * definitive di {@code DESIGN-SYSTEM.md} §2 cambiando solo questa classe. Le tabelle InnoDB e MyISAM hanno icone
 * <b>diverse</b> (blu / viola con la «M»): lo studente vede a colpo d'occhio quali tabelle non possono avere chiavi
 * esterne.
 */
public final class NavigatorIcons {

    private static final float STROKE = 1.5f;

    /** Server. */
    public static final Icon SERVER = new DrawnIcon("server", g -> {
        stroke(g, Tokens.TEXT_SECONDARY);
        g.draw(new RoundRectangle2D.Double(2, 2.5, 12, 4.5, 2, 2));
        g.draw(new RoundRectangle2D.Double(2, 9, 12, 4.5, 2, 2));
        g.setColor(Tokens.ACCENT);
        g.fill(new Ellipse2D.Double(4, 4, 1.6, 1.6));
        g.fill(new Ellipse2D.Double(4, 10.5, 1.6, 1.6));
    });

    /** Catalogo (cilindro). */
    public static final Icon CATALOG = new DrawnIcon("catalog", g -> cylinder(g, Tokens.ACCENT));

    /** Catalogo di sistema. */
    public static final Icon SYSTEM_CATALOG = new DrawnIcon("systemCatalog", g -> cylinder(g, Tokens.TEXT_TERTIARY));

    /** Tabella InnoDB: griglia blu. */
    public static final Icon TABLE_INNODB = new DrawnIcon("table.innodb", g -> grid(g, Tokens.ENGINE_INNODB));

    /** Tabella MyISAM: griglia viola con la «M» (niente chiavi esterne). */
    public static final Icon TABLE_MYISAM = new DrawnIcon("table.myisam", g -> {
        stroke(g, Tokens.ENGINE_MYISAM);
        g.draw(new RoundRectangle2D.Double(1.75, 2.75, 12.5, 10.5, 3, 3));
        g.fill(new RoundRectangle2D.Double(1.75, 2.75, 12.5, 3.5, 3, 3));
        glyph(g, "M", Tokens.ENGINE_MYISAM, 8, 12.6, 7.5f);
    });

    /** Tabella con altro engine (MEMORY, Aria…). */
    public static final Icon TABLE_OTHER = new DrawnIcon("table.other", g -> grid(g, Tokens.TEXT_SECONDARY));

    /** Vista: occhio sopra la griglia. */
    public static final Icon VIEW = new DrawnIcon("view", g -> {
        stroke(g, Tokens.SQL_FUNCTION);
        Path2D eye = new Path2D.Double();
        eye.moveTo(1.5, 8);
        eye.quadTo(8, 1.5, 14.5, 8);
        eye.quadTo(8, 14.5, 1.5, 8);
        g.draw(eye);
        g.fill(new Ellipse2D.Double(6, 6, 4, 4));
    });

    /** Procedura o funzione («ƒ»). */
    public static final Icon ROUTINE = new DrawnIcon("routine", g -> {
        g.setColor(Tokens.SUCCESS_TINT);
        g.fill(new RoundRectangle2D.Double(1, 1.5, 14, 13, 4, 4));
        glyph(g, "ƒ", Tokens.SUCCESS, 8, 12.3, 11f);
    });

    /** Trigger (lampo). */
    public static final Icon TRIGGER = new DrawnIcon("trigger", g -> {
        g.setColor(Tokens.WARNING);
        Path2D bolt = new Path2D.Double();
        bolt.moveTo(9.5, 1);
        bolt.lineTo(3, 9);
        bolt.lineTo(7.5, 9);
        bolt.lineTo(6, 15);
        bolt.lineTo(13, 6.5);
        bolt.lineTo(8.5, 6.5);
        bolt.closePath();
        g.fill(bolt);
    });

    /** Evento (orologio). */
    public static final Icon EVENT = new DrawnIcon("event", g -> {
        stroke(g, Tokens.SUCCESS);
        g.draw(new Ellipse2D.Double(2, 2, 12, 12));
        g.draw(new Line2D.Double(8, 4.5, 8, 8));
        g.draw(new Line2D.Double(8, 8, 10.5, 9.5));
    });

    /** Colonna. */
    public static final Icon COLUMN = new DrawnIcon("column", g -> {
        g.setColor(Tokens.TEXT_TERTIARY);
        g.fill(new RoundRectangle2D.Double(3, 6.5, 10, 3, 3, 3));
    });

    /** Colonna della chiave primaria (chiave oro). */
    public static final Icon COLUMN_KEY = new DrawnIcon("column.key", g -> key(g, Tokens.KEY_GOLD));

    /** Indice (fulmine sottile su righe). */
    public static final Icon INDEX = new DrawnIcon("index", g -> {
        stroke(g, Tokens.TEXT_SECONDARY);
        for (int i = 0; i < 3; i++) {
            g.draw(new Line2D.Double(2.5, 4 + i * 4, 4, 4 + i * 4));
            g.draw(new Line2D.Double(6.5, 4 + i * 4, 13.5, 4 + i * 4));
        }
    });

    /** Indice UNIQUE o PRIMARY. */
    public static final Icon INDEX_UNIQUE = new DrawnIcon("index.unique", g -> key(g, Tokens.TEXT_SECONDARY));

    /** Chiave esterna (catena). */
    public static final Icon FOREIGN_KEY = new DrawnIcon("foreignKey", g -> {
        stroke(g, Tokens.ACCENT);
        g.draw(new RoundRectangle2D.Double(1.5, 5.5, 7, 5, 5, 5));
        g.draw(new RoundRectangle2D.Double(7.5, 5.5, 7, 5, 5, 5));
    });

    /** Nodo di servizio (caricamento, nessun oggetto, errore di lettura). */
    public static final Icon MESSAGE = new DrawnIcon("message", g -> {
        g.setColor(Tokens.TEXT_TERTIARY);
        g.fill(new Ellipse2D.Double(6.5, 6.5, 3, 3));
    });

    /** Avvertimento (conferma rafforzata). */
    public static final Icon WARNING = new DrawnIcon("warning", g -> {
        g.setColor(Tokens.DANGER);
        Path2D t = new Path2D.Double();
        t.moveTo(8, 1.5);
        t.lineTo(15, 14);
        t.lineTo(1, 14);
        t.closePath();
        g.fill(t);
        g.setColor(Tokens.BG_SURFACE);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(8, 6, 8, 9.5));
        g.fill(new Ellipse2D.Double(7.1, 11, 1.8, 1.8));
    });

    /** Esito riuscito (spunta). */
    public static final Icon OUTCOME_OK = new DrawnIcon("outcome.ok", g -> {
        stroke(g, Tokens.SUCCESS);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Double();
        check.moveTo(3, 8.5);
        check.lineTo(6.5, 12);
        check.lineTo(13, 4.5);
        g.draw(check);
    });

    /** Esito non riuscito (croce). */
    public static final Icon OUTCOME_ERROR = new DrawnIcon("outcome.error", g -> {
        g.setColor(Tokens.DANGER);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(4, 4, 12, 12));
        g.draw(new Line2D.Double(12, 4, 4, 12));
    });

    /** Esito interrotto dall'utente (quadrato). */
    public static final Icon OUTCOME_INTERRUPTED = new DrawnIcon("outcome.interrupted", g -> {
        g.setColor(Tokens.WARNING);
        g.fill(new RoundRectangle2D.Double(4, 4, 8, 8, 2, 2));
    });

    private NavigatorIcons() {
    }

    /** L'icona di un nodo del navigatore; {@code null} per le intestazioni di sezione («TABELLE · 6»). */
    public static Icon iconFor(NavNode node, boolean expanded) {
        return switch (node.kind()) {
            case SERVER -> SERVER;
            case CATALOG -> node.data() instanceof CatalogInfo c && c.system() ? SYSTEM_CATALOG : CATALOG;
            case TABLE -> forTable(node.table());
            case VIEW -> VIEW;
            case ROUTINE -> switch (node.routine().kind()) {
                case PROCEDURE, FUNCTION -> ROUTINE;
                case TRIGGER -> TRIGGER;
                case EVENT -> EVENT;
            };
            case COLUMN -> node.count() == 1 ? COLUMN_KEY : COLUMN;   // count 1 = colonna della chiave primaria
            case INDEX -> node.data() instanceof IndexDef i && i.isUnique() ? INDEX_UNIQUE : INDEX;
            case FOREIGN_KEY -> FOREIGN_KEY;
            case LOADING, MESSAGE -> MESSAGE;
            case TABLES, VIEWS, ROUTINES, COLUMNS, INDEXES, FOREIGN_KEYS -> null;
        };
    }

    /** Icona di una tabella secondo l'engine: InnoDB, MyISAM, altro. */
    public static Icon forTable(TableSummary table) {
        if (table == null || table.engine() == null) {
            return TABLE_OTHER;
        }
        if (table.isMyIsam()) {
            return TABLE_MYISAM;
        }
        return "InnoDB".equalsIgnoreCase(table.engine()) ? TABLE_INNODB : TABLE_OTHER;
    }

    private static void stroke(Graphics2D g, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    private static void cylinder(Graphics2D g, Color color) {
        stroke(g, color);
        g.draw(new Ellipse2D.Double(2.75, 1.75, 10.5, 3.5));
        g.draw(new Line2D.Double(2.75, 3.5, 2.75, 12.5));
        g.draw(new Line2D.Double(13.25, 3.5, 13.25, 12.5));
        Path2D bottom = new Path2D.Double();
        bottom.moveTo(2.75, 12.5);
        bottom.quadTo(8, 16, 13.25, 12.5);
        g.draw(bottom);
        Path2D middle = new Path2D.Double();
        middle.moveTo(2.75, 8);
        middle.quadTo(8, 11.2, 13.25, 8);
        g.draw(middle);
    }

    private static void grid(Graphics2D g, Color color) {
        stroke(g, color);
        g.draw(new RoundRectangle2D.Double(1.75, 2.75, 12.5, 10.5, 3, 3));
        g.fill(new RoundRectangle2D.Double(1.75, 2.75, 12.5, 3.5, 3, 3));
        g.draw(new Line2D.Double(1.75, 9.75, 14.25, 9.75));
        g.draw(new Line2D.Double(8, 6, 8, 13.25));
    }

    private static void key(Graphics2D g, Color color) {
        stroke(g, color);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(1.5, 5, 5.5, 5.5));
        g.draw(new Line2D.Double(7, 7.75, 14, 7.75));
        g.draw(new Line2D.Double(12, 7.75, 12, 10.5));
        g.draw(new Line2D.Double(14, 7.75, 14, 10));
    }

    /** Una lettera centrata in x con la base in y. */
    private static void glyph(Graphics2D g, String text, Color color, double cx, double baseline, float size) {
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(size);
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector gv = f.createGlyphVector(frc, text);
        Rectangle2D b = gv.getVisualBounds();
        g.setColor(color);
        g.fill(gv.getOutline((float) (cx - b.getCenterX()), (float) baseline));
    }

    /** Icona 16×16 disegnata con Java2D, scalata come il resto dell'interfaccia. */
    static final class DrawnIcon implements Icon {
        private final String id;
        private final Consumer<Graphics2D> painter;

        DrawnIcon(String id, Consumer<Graphics2D> painter) {
            this.id = id;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                float s = UIScale.getUserScaleFactor();
                g2.scale(s, s);
                painter.accept(g2);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return UIScale.scale(16);
        }

        @Override
        public int getIconHeight() {
            return UIScale.scale(16);
        }

        @Override
        public String toString() {
            return "icona " + id;
        }
    }
}
