/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

import javax.swing.Icon;

/**
 * Icone predefinite del query builder, disegnate in Java2D (dal 2026-09-22 sostituiscono le 13 PNG ereditate da
 * SQLeo, di origine non dichiarata). Disegno su una griglia logica di 16×16 moltiplicata per la scala: restano
 * nitide a qualunque scala (HiDPI, FlatLaf). Colori sobri, pensati per il tema chiaro.
 */
public final class QbDrawnIcon implements Icon {

    /** Lato logico dell'icona, in pixel «a 100%». */
    public static final int LOGICAL_SIZE = 16;

    private static final Color OUTLINE = new Color(0x5F6B7A);
    private static final Color PAPER = new Color(0xFFFFFF);
    private static final Color BLUE = new Color(0x3F72AF);
    private static final Color BLUE_LIGHT = new Color(0xA9C4E6);
    private static final Color BLUE_DARK = new Color(0x2B5286);
    private static final Color GRID = new Color(0xB8C2CE);
    private static final Color PANEL = new Color(0xE3E8EE);
    private static final Color GOLD = new Color(0xD9A928);
    private static final Color GOLD_DARK = new Color(0x8A6A10);
    private static final Color GREEN = new Color(0x4C9A52);
    private static final Color GREEN_DARK = new Color(0x2E6B33);
    private static final Color GREY = new Color(0x9AA1AB);
    private static final Color GREY_DARK = new Color(0x5D646E);
    private static final Color TEAL = new Color(0x3F8F86);
    private static final Color TEAL_DARK = new Color(0x2A6660);
    private static final Color INDIGO = new Color(0x474C8F);
    private static final Color ROSE = new Color(0xC4557F);
    private static final Color ROSE_DARK = new Color(0x8E3157);

    private final QbIcon id;
    private final float scale;

    /**
     * @param id    icona da disegnare
     * @param scale fattore di scala (1 = 16×16 pixel); deve essere positivo
     */
    public QbDrawnIcon(QbIcon id, float scale) {
        this.id = Objects.requireNonNull(id, "id");
        if (!(scale > 0f)) {
            throw new IllegalArgumentException("scala non positiva: " + scale);
        }
        this.scale = scale;
    }

    public QbIcon id() {
        return id;
    }

    public float scale() {
        return scale;
    }

    @Override
    public int getIconWidth() {
        return Math.round(LOGICAL_SIZE * scale);
    }

    @Override
    public int getIconHeight() {
        return Math.round(LOGICAL_SIZE * scale);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.translate(x, y);
            g2.scale(scale, scale);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setStroke(new BasicStroke(1f));
            paintGlyph(g2, id);
        } finally {
            g2.dispose();
        }
    }

    private static void paintGlyph(Graphics2D g, QbIcon id) {
        switch (id) {
            case DIAG_TABLE, DIAG_OBJECT, QB_TABLE -> table(g);
            case DIAG_FIELD -> key(g);
            case DIAG_QUERY, QB_QUERY -> query(g);
            case QB_WHERE -> funnel(g, GREEN, GREEN_DARK);
            case QB_HAVING -> funnel(g, GREY, GREY_DARK);
            case QB_KEYANDWHERE -> keyAndFilter(g);
            case QB_FROM -> relationship(g);
            case QB_SELECT -> layout(g);
            case QB_ORDER -> sort(g);
            case QB_GROUP -> organisation(g);
            case QB_FIELD -> textField(g);
            case QB_EXPR -> sum(g);
            case QB_FOLDER -> bullet(g);
        }
    }

    private static void fillAndStroke(Graphics2D g, Shape s, Color fill, Color line) {
        g.setColor(fill);
        g.fill(s);
        g.setColor(line);
        g.draw(s);
    }

    /** Tabella: riquadro con riga d'intestazione blu e griglia. */
    private static void table(Graphics2D g) {
        miniTable(g, 1.5, 2.5, 13, 11);
    }

    private static void miniTable(Graphics2D g, double x, double y, double w, double h) {
        RoundRectangle2D body = new RoundRectangle2D.Double(x, y, w, h, 2, 2);
        g.setColor(PAPER);
        g.fill(body);
        double header = Math.min(3.5, h / 3);
        Shape oldClip = g.getClip();
        g.clip(body);
        g.setColor(BLUE);
        g.fill(new Rectangle2D.Double(x, y, w, header));
        g.setClip(oldClip);
        g.setColor(GRID);
        double rowY = y + header + (h - header) / 2;
        g.draw(new Line2D.Double(x + 0.5, rowY, x + w - 0.5, rowY));
        double colX = x + Math.round(w * 0.4);
        g.draw(new Line2D.Double(colX, y + header, colX, y + h - 0.5));
        g.setColor(BLUE_DARK);
        g.draw(body);
    }

    /** Chiave dorata (chiave primaria). */
    private static void key(Graphics2D g) {
        Area key = new Area(new Ellipse2D.Double(1.5, 4.5, 7, 7));
        key.add(new Area(new Rectangle2D.Double(7.5, 7, 7, 2)));
        key.add(new Area(new Rectangle2D.Double(11, 9, 1.6, 2.5)));
        key.add(new Area(new Rectangle2D.Double(13, 9, 1.5, 1.8)));
        key.subtract(new Area(new Ellipse2D.Double(3.5, 6.5, 3, 3)));
        fillAndStroke(g, key, GOLD, GOLD_DARK);
    }

    /** Foglio con un piccolo database: una query. */
    private static void query(Graphics2D g) {
        Path2D page = new Path2D.Double();
        page.moveTo(2.5, 1.5);
        page.lineTo(9.5, 1.5);
        page.lineTo(13.5, 5.5);
        page.lineTo(13.5, 14.5);
        page.lineTo(2.5, 14.5);
        page.closePath();
        fillAndStroke(g, page, PAPER, OUTLINE);
        Path2D fold = new Path2D.Double();
        fold.moveTo(9.5, 1.5);
        fold.lineTo(9.5, 5.5);
        fold.lineTo(13.5, 5.5);
        g.draw(fold);
        // cilindro del database
        Area body = new Area(new Rectangle2D.Double(4.5, 8, 7, 4));
        body.add(new Area(new Ellipse2D.Double(4.5, 10.5, 7, 3)));
        body.add(new Area(new Ellipse2D.Double(4.5, 6.5, 7, 3)));
        fillAndStroke(g, body, BLUE, BLUE_DARK);
        fillAndStroke(g, new Ellipse2D.Double(4.5, 6.5, 7, 3), BLUE_LIGHT, BLUE_DARK);
    }

    private static Path2D funnelShape() {
        Path2D p = new Path2D.Double();
        p.moveTo(1.5, 2.5);
        p.lineTo(14.5, 2.5);
        p.lineTo(9.5, 8.5);
        p.lineTo(9.5, 12.5);
        p.lineTo(6.5, 14.5);
        p.lineTo(6.5, 8.5);
        p.closePath();
        return p;
    }

    /** Imbuto (filtro): verde per WHERE, grigio per HAVING. */
    private static void funnel(Graphics2D g, Color fill, Color line) {
        fillAndStroke(g, funnelShape(), fill, line);
    }

    /** Chiave con un piccolo imbuto verde: chiave primaria usata in WHERE. */
    private static void keyAndFilter(Graphics2D g) {
        AffineTransform saved = g.getTransform();
        g.translate(-0.5, -1.5);
        g.scale(0.75, 0.75);
        key(g);
        g.setTransform(saved);
        g.translate(7, 6.5);
        g.scale(0.56, 0.56);
        g.setStroke(new BasicStroke(1.4f));
        funnel(g, GREEN, GREEN_DARK);
        g.setTransform(saved);
        g.setStroke(new BasicStroke(1f));
    }

    /** Due tabelle collegate: FROM e join. */
    private static void relationship(Graphics2D g) {
        miniTable(g, 0.5, 0.5, 8, 7);
        miniTable(g, 7.5, 8.5, 8, 7);
        g.setColor(OUTLINE);
        Path2D link = new Path2D.Double();
        link.moveTo(3.5, 7.5);
        link.lineTo(3.5, 12);
        link.lineTo(7.5, 12);
        g.draw(link);
    }

    /** Finestra con intestazione e colonne: l'elenco SELECT. */
    private static void layout(Graphics2D g) {
        RoundRectangle2D frame = new RoundRectangle2D.Double(1.5, 2.5, 13, 11, 2, 2);
        g.setColor(PAPER);
        g.fill(frame);
        Shape oldClip = g.getClip();
        g.clip(frame);
        g.setColor(BLUE);
        g.fill(new Rectangle2D.Double(1.5, 2.5, 13, 3));
        g.setColor(PANEL);
        g.fill(new Rectangle2D.Double(1.5, 5.5, 4, 8));
        g.setClip(oldClip);
        g.setColor(GRID);
        g.draw(new Line2D.Double(7.5, 7.5, 12.5, 7.5));
        g.draw(new Line2D.Double(7.5, 9.5, 12.5, 9.5));
        g.draw(new Line2D.Double(7.5, 11.5, 11, 11.5));
        g.setColor(BLUE_DARK);
        g.draw(frame);
    }

    /** Righe di lunghezza decrescente e freccia: ORDER BY. */
    private static void sort(Graphics2D g) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(OUTLINE);
        g.draw(new Line2D.Double(2, 3.5, 9, 3.5));
        g.draw(new Line2D.Double(2, 8, 7, 8));
        g.draw(new Line2D.Double(2, 12.5, 5, 12.5));
        g.setColor(BLUE);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(12.5, 2.5, 12.5, 13));
        Path2D head = new Path2D.Double();
        head.moveTo(10, 10.5);
        head.lineTo(12.5, 13.5);
        head.lineTo(15, 10.5);
        g.draw(head);
        g.setStroke(new BasicStroke(1f));
    }

    /** Organigramma: GROUP BY (una voce che raccoglie le altre). */
    private static void organisation(Graphics2D g) {
        g.setColor(OUTLINE);
        Path2D lines = new Path2D.Double();
        lines.moveTo(8, 5.5);
        lines.lineTo(8, 8);
        lines.moveTo(3.5, 10.5);
        lines.lineTo(3.5, 8);
        lines.lineTo(12.5, 8);
        lines.lineTo(12.5, 10.5);
        g.draw(lines);
        fillAndStroke(g, new RoundRectangle2D.Double(4.5, 1.5, 7, 4, 1.5, 1.5), TEAL, TEAL_DARK);
        fillAndStroke(g, new RoundRectangle2D.Double(0.5, 10.5, 6, 4, 1.5, 1.5), PAPER, TEAL_DARK);
        fillAndStroke(g, new RoundRectangle2D.Double(9.5, 10.5, 6, 4, 1.5, 1.5), PAPER, TEAL_DARK);
    }

    /** Casella di testo con cursore: una colonna. */
    private static void textField(Graphics2D g) {
        fillAndStroke(g, new RoundRectangle2D.Double(1.5, 4.5, 13, 7, 1.5, 1.5), PAPER, OUTLINE);
        g.setColor(BLUE_DARK);
        Path2D caret = new Path2D.Double();
        caret.moveTo(4, 6.5);
        caret.lineTo(6, 6.5);
        caret.moveTo(5, 6.5);
        caret.lineTo(5, 9.5);
        caret.moveTo(4, 9.5);
        caret.lineTo(6, 9.5);
        g.draw(caret);
        g.setColor(GRID);
        g.draw(new Line2D.Double(7.5, 8, 12, 8));
    }

    /** Simbolo di sommatoria: espressione o aggregato. */
    private static void sum(Graphics2D g) {
        g.setColor(INDIGO);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        Path2D sigma = new Path2D.Double();
        sigma.moveTo(12.5, 5);
        sigma.lineTo(12.5, 2.5);
        sigma.lineTo(3.5, 2.5);
        sigma.lineTo(8.5, 8);
        sigma.lineTo(3.5, 13.5);
        sigma.lineTo(12.5, 13.5);
        sigma.lineTo(12.5, 11);
        g.draw(sigma);
        g.setStroke(new BasicStroke(1f));
    }

    /** Punto elenco. */
    private static void bullet(Graphics2D g) {
        fillAndStroke(g, new Ellipse2D.Double(4.5, 4.5, 7, 7), ROSE, ROSE_DARK);
    }

    @Override
    public String toString() {
        return "QbDrawnIcon[" + id + ", " + scale + "]";
    }
}
