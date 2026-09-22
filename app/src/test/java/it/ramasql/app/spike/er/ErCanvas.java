/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app.spike.er;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * PROTOTIPO USA-E-GETTA dello spike S6 (canvas ER in Java2D): entità con colonne, relazioni con
 * «zampa di gallina», trascinamento, zoom con la rotella, pan, esportazione PNG.
 * Non è codice di prodotto: il modello ER vero nasce nello Step 11 (modulo {@code model}).
 */
final class ErCanvas extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Lati di un'entità da cui può uscire una relazione. */
    enum Side { LEFT, RIGHT, TOP, BOTTOM }

    /** Entità: nome, colonne, posizione in coordinate del diagramma (non dello schermo). */
    static final class Entity {
        final String name;
        final List<String> columns;
        double x;
        double y;

        Entity(String name, List<String> columns, double x, double y) {
            this.name = name;
            this.columns = columns;
            this.x = x;
            this.y = y;
        }

        private double width = -1;

        /** Larghezza: quanto basta alla riga più lunga (misurata una volta sola, non a ogni ridisegno). */
        double width() {
            if (width < 0) {
                double w = HEADER_FONT.getStringBounds(name, MEASURE).getWidth();
                for (String c : columns) {
                    w = Math.max(w, COLUMN_FONT.getStringBounds(c, MEASURE).getWidth());
                }
                width = Math.max(ENTITY_WIDTH, Math.ceil(w) + 16);
            }
            return width;
        }

        double height() {
            return HEADER_HEIGHT + columns.size() * ROW_HEIGHT + 6;
        }

        Rectangle2D bounds() {
            return new Rectangle2D.Double(x, y, width(), height());
        }
    }

    /** Relazione uno-a-molti: {@code many} è la tabella figlia (lato zampa di gallina), {@code one} la madre. */
    record Relation(Entity many, Entity one, boolean optional) {
    }

    /** Percorso calcolato di una relazione: punti della spezzata e lati di uscita. */
    record Route(List<Point2D> points, Side manySide, Side oneSide) {
    }

    /** Larghezza minima di un'entità. */
    static final double ENTITY_WIDTH = 160;
    static final double HEADER_HEIGHT = 22;
    static final double ROW_HEIGHT = 15;
    static final double MIN_ZOOM = 0.1;
    static final double MAX_ZOOM = 8.0;

    private static final Color BACKGROUND = new Color(0xF7F8FA);
    private static final Color ENTITY_FILL = Color.WHITE;
    private static final Color ENTITY_BORDER = new Color(0x5B6B7D);
    private static final Color HEADER_FILL = new Color(0x2F6FDE);
    private static final Color HEADER_TEXT = Color.WHITE;
    private static final Color COLUMN_TEXT = new Color(0x1F2933);
    private static final Color KEY_TEXT = new Color(0xB45309);
    private static final Color RELATION = new Color(0x374151);
    private static final Color SELECTED = new Color(0xE11D48);
    private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font COLUMN_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final java.awt.font.FontRenderContext MEASURE = new java.awt.font.FontRenderContext(null, true, true);
    private static final BasicStroke LINE = new BasicStroke(1.3f);
    private static final BasicStroke BORDER = new BasicStroke(1.0f);

    private final List<Entity> entities;
    private final List<Relation> relations;
    private double zoom = 1.0;
    private double offsetX;
    private double offsetY;

    private Entity dragged;
    private Point lastMouse;

    ErCanvas(List<Entity> entities, List<Relation> relations) {
        this.entities = entities;
        this.relations = relations;
        setOpaque(true);
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(1600, 1000));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouse = e.getPoint();
                dragged = entityAt(e.getPoint());
                if (dragged != null) { // l'entità presa va in primo piano
                    entities.remove(dragged);
                    entities.add(dragged);
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMouse == null) {
                    return;
                }
                double dx = e.getX() - lastMouse.x;
                double dy = e.getY() - lastMouse.y;
                if (dragged != null) { // trascinamento dell'entità: lo spostamento sullo schermo diviso lo zoom
                    dragged.x += dx / zoom;
                    dragged.y += dy / zoom;
                } else { // pan: si sposta tutto il foglio
                    offsetX += dx;
                    offsetY += dy;
                }
                lastMouse = e.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragged = null;
                lastMouse = null;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                setZoom(zoom * Math.pow(1.1, -e.getPreciseWheelRotation()), e.getPoint());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    // ------------------------------------------------------------------ diagramma di prova

    /** Diagramma deterministico: entità su una griglia a 6 colonne, relazioni soprattutto tra vicine. */
    static ErCanvas demo(int entityCount, int relationCount, long seed) {
        String[] names = {"studente", "classe", "docente", "materia", "voto", "assenza", "aula", "orario", "libro",
            "autore", "editore", "prestito", "copia", "genere", "utente", "sede", "corso", "iscrizione", "esame",
            "appello", "sessione", "indirizzo", "comune", "provincia", "regione", "fattura", "riga_fattura",
            "prodotto", "categoria", "fornitore", "ordine", "riga_ordine", "magazzino", "reparto", "dipendente"};
        String[] cols = {"nome VARCHAR(80)", "descrizione TEXT", "data_inizio DATE", "data_fine DATE",
            "importo DECIMAL(8,2)", "attivo TINYINT(1)", "codice CHAR(8)", "note VARCHAR(200)", "creato DATETIME",
            "quantita INT", "email VARCHAR(120)"};
        Random rnd = new Random(seed);
        List<Entity> entities = new ArrayList<>();
        int perRow = 6;
        for (int i = 0; i < entityCount; i++) {
            List<String> columns = new ArrayList<>();
            columns.add("id INT UNSIGNED");
            int n = 4 + rnd.nextInt(3); // da 4 a 6 colonne, più le chiavi esterne: mai oltre 8
            int start = rnd.nextInt(cols.length);
            for (int c = 1; c < n; c++) {
                columns.add(cols[(start + c) % cols.length]);
            }
            String name = i < names.length ? names[i] : "tabella_" + (i + 1);
            entities.add(new Entity(name, columns, 40 + (i % perRow) * 255.0, 24 + (i / perRow) * 196.0));
        }
        // candidate: coppie di vicine (a destra, sotto), poi qualcuna in diagonale
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < entityCount; i++) {
            if ((i + 1) % perRow != 0 && i + 1 < entityCount) {
                pairs.add(new int[] {i, i + 1});
            }
            if (i + perRow < entityCount) {
                pairs.add(new int[] {i, i + perRow});
            }
        }
        for (int i = 0; i + perRow + 1 < entityCount; i += 4) {
            if ((i + 1) % perRow != 0) {
                pairs.add(new int[] {i, i + perRow + 1});
            }
        }
        java.util.Collections.shuffle(pairs, rnd);
        List<Relation> relations = new ArrayList<>();
        for (int i = 0; i < relationCount; i++) {
            int[] p = pairs.get(i % pairs.size());
            boolean flip = rnd.nextBoolean();
            Entity many = entities.get(flip ? p[0] : p[1]);
            Entity one = entities.get(flip ? p[1] : p[0]);
            boolean optional = rnd.nextInt(3) == 0;
            if (many.columns.size() < 8) {
                many.columns.add(one.name + "_id INT UNSIGNED" + (optional ? " NULL" : ""));
            }
            relations.add(new Relation(many, one, optional));
        }
        return new ErCanvas(entities, relations);
    }

    // ------------------------------------------------------------------ stato

    List<Entity> entities() {
        return entities;
    }

    List<Relation> relations() {
        return relations;
    }

    double zoom() {
        return zoom;
    }

    Point2D offset() {
        return new Point2D.Double(offsetX, offsetY);
    }

    /** Zoom tenendo fermo il punto dello schermo indicato (quello sotto il mouse). */
    void setZoom(double newZoom, Point anchor) {
        double z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        Point2D world = toWorld(anchor);
        zoom = z;
        offsetX = anchor.x - world.getX() * zoom;
        offsetY = anchor.y - world.getY() * zoom;
        repaint();
    }

    /** Zoom e origine espliciti (per i test e per «adatta alla finestra»). */
    void setView(double newZoom, double newOffsetX, double newOffsetY) {
        zoom = newZoom;
        offsetX = newOffsetX;
        offsetY = newOffsetY;
        repaint();
    }

    Point2D toWorld(Point screen) {
        return new Point2D.Double((screen.x - offsetX) / zoom, (screen.y - offsetY) / zoom);
    }

    Point toScreen(double worldX, double worldY) {
        return new Point((int) Math.round(worldX * zoom + offsetX), (int) Math.round(worldY * zoom + offsetY));
    }

    /** Entità sotto il punto dello schermo (quella più in primo piano), o {@code null}. */
    Entity entityAt(Point screen) {
        Point2D w = toWorld(screen);
        for (int i = entities.size() - 1; i >= 0; i--) {
            if (entities.get(i).bounds().contains(w)) {
                return entities.get(i);
            }
        }
        return null;
    }

    /** Rettangolo che contiene tutto il diagramma, in coordinate del diagramma. */
    Rectangle2D diagramBounds() {
        Rectangle2D r = null;
        for (Entity e : entities) {
            r = r == null ? e.bounds() : r.createUnion(e.bounds());
        }
        return r == null ? new Rectangle2D.Double() : r;
    }

    // ------------------------------------------------------------------ percorso delle relazioni

    /** Spezzata ortogonale tra i due bordi che si guardano; i punti estremi stanno sul bordo delle entità. */
    Route route(Relation r) {
        Rectangle2D a = r.many().bounds();
        Rectangle2D b = r.one().bounds();
        double ay = slot(r, r.many());
        double by = slot(r, r.one());
        List<Point2D> pts = new ArrayList<>();
        if (a.getMaxX() + 40 <= b.getMinX() || b.getMaxX() + 40 <= a.getMinX()) { // affiancate: lati sinistro/destro
            boolean aLeft = a.getMaxX() <= b.getMinX();
            double x1 = aLeft ? a.getMaxX() : a.getMinX();
            double x2 = aLeft ? b.getMinX() : b.getMaxX();
            double y1 = a.getMinY() + ay * a.getHeight();
            double y2 = b.getMinY() + by * b.getHeight();
            double mid = (x1 + x2) / 2;
            pts.add(new Point2D.Double(x1, y1));
            pts.add(new Point2D.Double(mid, y1));
            pts.add(new Point2D.Double(mid, y2));
            pts.add(new Point2D.Double(x2, y2));
            return new Route(pts, aLeft ? Side.RIGHT : Side.LEFT, aLeft ? Side.LEFT : Side.RIGHT);
        }
        boolean aAbove = a.getCenterY() <= b.getCenterY(); // una sopra l'altra: lati alto/basso
        double y1 = aAbove ? a.getMaxY() : a.getMinY();
        double y2 = aAbove ? b.getMinY() : b.getMaxY();
        double x1 = a.getMinX() + ay * a.getWidth();
        double x2 = b.getMinX() + by * b.getWidth();
        double mid = (y1 + y2) / 2;
        pts.add(new Point2D.Double(x1, y1));
        pts.add(new Point2D.Double(x1, mid));
        pts.add(new Point2D.Double(x2, mid));
        pts.add(new Point2D.Double(x2, y2));
        return new Route(pts, aAbove ? Side.BOTTOM : Side.TOP, aAbove ? Side.TOP : Side.BOTTOM);
    }

    /** Frazione (0..1) del lato a cui attaccare la relazione: le relazioni della stessa entità non si sovrappongono. */
    private double slot(Relation r, Entity e) {
        int count = 0;
        int index = 0;
        for (Relation other : relations) {
            if (other.many() == e || other.one() == e) {
                if (other == r) {
                    index = count;
                }
                count++;
            }
        }
        return (index + 1.0) / (count + 1.0);
    }

    // ------------------------------------------------------------------ disegno

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, getWidth(), getHeight());
            Rectangle clip = g2.getClipBounds();
            if (clip == null) {
                clip = new Rectangle(0, 0, getWidth(), getHeight());
            }
            g2.translate(offsetX, offsetY);
            g2.scale(zoom, zoom);
            Rectangle2D visible = new Rectangle2D.Double((clip.x - offsetX) / zoom, (clip.y - offsetY) / zoom,
                    clip.width / zoom, clip.height / zoom);
            paintDiagram(g2, visible);
        } finally {
            g2.dispose();
        }
    }

    /** Disegna il diagramma in coordinate del diagramma; {@code visible} serve a saltare ciò che è fuori vista. */
    private void paintDiagram(Graphics2D g2, Rectangle2D visible) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setStroke(LINE);
        for (Relation r : relations) {
            boolean highlight = dragged != null && (r.many() == dragged || r.one() == dragged);
            g2.setColor(highlight ? SELECTED : RELATION);
            paintRelation(g2, r);
        }
        for (Entity e : entities) {
            if (visible == null || visible.intersects(e.x - 2, e.y - 2, e.width() + 4, e.height() + 4)) {
                paintEntity(g2, e);
            }
        }
    }

    private void paintEntity(Graphics2D g2, Entity e) {
        Rectangle2D box = e.bounds();
        g2.setColor(ENTITY_FILL);
        g2.fill(box);
        g2.setColor(HEADER_FILL);
        g2.fill(new Rectangle2D.Double(e.x, e.y, e.width(), HEADER_HEIGHT));
        g2.setStroke(BORDER);
        g2.setColor(e == dragged ? SELECTED : ENTITY_BORDER);
        g2.draw(box);
        g2.setFont(HEADER_FONT);
        g2.setColor(HEADER_TEXT);
        g2.drawString(e.name, (float) e.x + 8, (float) (e.y + 15));
        g2.setFont(COLUMN_FONT);
        float y = (float) (e.y + HEADER_HEIGHT + 12);
        for (String c : e.columns) {
            boolean key = c.startsWith("id ") || c.contains("_id ");
            g2.setColor(key ? KEY_TEXT : COLUMN_TEXT);
            g2.drawString(c, (float) e.x + 8, y);
            y += ROW_HEIGHT;
        }
        g2.setStroke(LINE);
    }

    private void paintRelation(Graphics2D g2, Relation r) {
        Route route = route(r);
        List<Point2D> p = route.points();
        Path2D path = new Path2D.Double();
        path.moveTo(p.get(0).getX(), p.get(0).getY());
        for (int i = 1; i < p.size(); i++) {
            path.lineTo(p.get(i).getX(), p.get(i).getY());
        }
        g2.draw(path);
        paintManyEnd(g2, p.get(0), route.manySide(), r.optional());
        paintOneEnd(g2, p.get(p.size() - 1), route.oneSide());
    }

    /** Zampa di gallina sul bordo dell'entità figlia: tre «dita» che si aprono verso l'entità, più cerchio (0) o barra (1). */
    private void paintManyEnd(Graphics2D g2, Point2D at, Side side, boolean optional) {
        double dx = dirX(side);
        double dy = dirY(side);
        double nx = -dy; // perpendicolare
        double ny = dx;
        double tipX = at.getX() + dx * 13;
        double tipY = at.getY() + dy * 13;
        g2.draw(new Line2D.Double(tipX, tipY, at.getX() + nx * 7, at.getY() + ny * 7));
        g2.draw(new Line2D.Double(tipX, tipY, at.getX() - nx * 7, at.getY() - ny * 7));
        double mx = at.getX() + dx * 19;
        double my = at.getY() + dy * 19;
        if (optional) { // «zero o molti»: cerchietto vuoto
            Ellipse2D circle = new Ellipse2D.Double(mx - 3.5, my - 3.5, 7, 7);
            Color line = g2.getColor();
            g2.setColor(BACKGROUND);
            g2.fill(circle);
            g2.setColor(line);
            g2.draw(circle);
        } else { // «uno o molti»: barra
            g2.draw(new Line2D.Double(mx + nx * 6, my + ny * 6, mx - nx * 6, my - ny * 6));
        }
    }

    /** Lato «uno e uno solo»: due barre perpendicolari alla linea. */
    private void paintOneEnd(Graphics2D g2, Point2D at, Side side) {
        double dx = dirX(side);
        double dy = dirY(side);
        double nx = -dy;
        double ny = dx;
        for (double d : new double[] {8, 13}) {
            double mx = at.getX() + dx * d;
            double my = at.getY() + dy * d;
            g2.draw(new Line2D.Double(mx + nx * 6, my + ny * 6, mx - nx * 6, my - ny * 6));
        }
    }

    private static double dirX(Side s) {
        return s == Side.LEFT ? -1 : s == Side.RIGHT ? 1 : 0;
    }

    private static double dirY(Side s) {
        return s == Side.TOP ? -1 : s == Side.BOTTOM ? 1 : 0;
    }

    // ------------------------------------------------------------------ esportazione

    /** Immagine dell'intero diagramma (non della sola parte visibile), con un margine, alla scala indicata. */
    BufferedImage exportImage(double scale) {
        Rectangle2D b = diagramBounds();
        int margin = 30;
        int w = (int) Math.ceil((b.getWidth() + 2 * margin) * scale);
        int h = (int) Math.ceil((b.getHeight() + 2 * margin) * scale);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, w, h);
            g2.setTransform(AffineTransform.getScaleInstance(scale, scale));
            g2.translate(margin - b.getX(), margin - b.getY());
            paintDiagram(g2, null);
        } finally {
            g2.dispose();
        }
        return img;
    }

    void exportPng(Path file, double scale) throws IOException {
        Files.createDirectories(file.getParent());
        if (!ImageIO.write(exportImage(scale), "png", file.toFile())) {
            throw new IOException("Nessun codificatore PNG disponibile");
        }
    }
}
