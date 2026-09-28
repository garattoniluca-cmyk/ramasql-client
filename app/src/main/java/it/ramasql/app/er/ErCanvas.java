/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;
import it.ramasql.model.AutoLayout;
import it.ramasql.model.ErModel;
import it.ramasql.model.Relationship;
import it.ramasql.model.ReverseEngineer;

/**
 * Il diagramma ER disegnato con Java2D ({@code ARCHITECTURE.md} §6, notazione a <b>zampa di gallina</b> come
 * Workbench): entità come schede trascinabili (nome, colonne con la chiave oro per la chiave primaria e il tipo in
 * grigio), relazioni come connettori ortogonali che partono dalla riga della colonna. Relazioni <b>fisiche</b> (le
 * chiavi esterne) a tratto pieno, <b>logiche</b> tratteggiate e nel colore d'accento. Estremo «uno» del padre: due
 * barrette se obbligatoria, barretta e cerchio se facoltativa; estremo della figlia: zampa di gallina (molti) o
 * barretta (uno a uno). Le tabelle ponte hanno la pillola «N:M»; le tabelle sparite dal database il bordo rosso
 * tratteggiato. Zoom con Ctrl+rotella; trascinando da una colonna a una colonna di un'altra entità si disegna una
 * relazione logica; doppio clic su un'entità apre l'editor della tabella.
 */
public final class ErCanvas extends JComponent {

    private static final long serialVersionUID = 1L;

    static final double HEADER = 30;
    static final double ROW = 22;
    static final double PAD = 10;
    static final double MIN_WIDTH = 150;
    static final double MARGIN = 40;

    private transient ErModel model;
    private double zoom = 1.0;
    private final transient Map<String, AutoLayout.Size> sizes = new HashMap<>();
    private transient Set<String> bridges = Set.of();
    private String selectedRelationship;
    private transient Consumer<ErModel> onChange = m -> { };
    private transient Consumer<String> onOpenTable = t -> { };
    private transient RelationshipRequest onDraw = (a, b, c, d) -> { };
    private transient Consumer<Relationship> onRelationshipMenu = r -> { };

    /** Una relazione disegnata a mano: dalla colonna della figlia alla colonna del padre. */
    @FunctionalInterface
    public interface RelationshipRequest {
        void draw(String fromTable, String fromColumn, String toTable, String toColumn);
    }

    // trascinamento
    private String dragged;
    private double dragDx;
    private double dragDy;
    private String drawFromTable;
    private String drawFromColumn;
    private Point drawTo;
    private boolean moved;

    public ErCanvas() {
        setName("er.canvas");
        setOpaque(true);
        setBackground(Tokens.BG_SURFACE);
        setFocusable(true);
        setAutoscrolls(true);   // trascinando verso il bordo la vista scorre (tabelle lontane)
        putClientProperty(it.ramasql.app.settings.SettingsController.OWN_CTRL_WHEEL, Boolean.TRUE);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                pressed(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dragged(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                released(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    String t = entityAt(toModel(e.getPoint()));
                    if (t != null) {
                        onOpenTable.accept(t);
                    }
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    setZoom(zoom * (e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1));
                    e.consume();
                } else if (getParent() != null) {
                    getParent().dispatchEvent(javax.swing.SwingUtilities.convertMouseEvent(ErCanvas.this, e, getParent()));
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        installKeys();
    }

    // ================================================================ tastiera

    /** L'entità scelta con la tastiera ({@code null} se nessuna). */
    private String selectedEntity;
    private transient Consumer<Relationship> onDeleteRelationship = r -> { };

    public void setOnDeleteRelationship(Consumer<Relationship> c) {
        this.onDeleteRelationship = c;
    }

    public String selectedEntity() {
        return selectedEntity;
    }

    /**
     * Il diagramma da tastiera: Tab e Maiusc+Tab scelgono la tabella, le frecce la spostano (Maiusc: di più), Invio ne
     * apre la struttura; F6 sceglie la relazione, Canc elimina quella logica scelta, Maiusc+F10 ne apre il menu.
     */
    private void installKeys() {
        setFocusTraversalKeysEnabled(false);
        bind("TAB", "er.nextEntity", () -> cycleEntity(1));
        bind("shift TAB", "er.previousEntity", () -> cycleEntity(-1));
        for (String dir : new String[] {"LEFT", "RIGHT", "UP", "DOWN"}) {
            bind(dir, "er.move." + dir, () -> nudge(dir, 10));
            bind("shift " + dir, "er.moveMore." + dir, () -> nudge(dir, 40));
        }
        bind("ENTER", "er.open", () -> {
            if (selectedEntity != null) {
                onOpenTable.accept(selectedEntity);
            }
        });
        bind("F6", "er.nextRelationship", this::cycleRelationship);
        bind("DELETE", "er.deleteRelationship", () -> selected().ifPresent(onDeleteRelationship));
        bind("shift F10", "er.relationshipMenu", () -> selected().ifPresent(onRelationshipMenu));
        bind("CONTEXT_MENU", "er.relationshipMenu2", () -> selected().ifPresent(onRelationshipMenu));
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    private java.util.Optional<Relationship> selected() {
        return model == null || selectedRelationship == null ? java.util.Optional.empty()
                : model.relationships().stream().filter(r -> r.id().equals(selectedRelationship)).findFirst();
    }

    /** Tab: la tabella successiva (in ordine di nome), e la vista la segue. */
    public void cycleEntity(int step) {
        if (model == null || model.entities().isEmpty()) {
            return;
        }
        List<String> names = model.entities().stream().map(ErModel.Entity::table)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int i = selectedEntity == null ? (step > 0 ? -1 : 0) : names.indexOf(selectedEntity);
        selectedEntity = names.get(Math.floorMod(i + step, names.size()));
        selectedRelationship = null;
        reveal(bounds(model.entity(selectedEntity).orElseThrow()));
        repaint();
    }

    /** F6: la relazione successiva. */
    public void cycleRelationship() {
        if (model == null || model.relationships().isEmpty()) {
            return;
        }
        List<Relationship> list = model.relationships();
        int i = -1;
        for (int k = 0; k < list.size(); k++) {
            if (list.get(k).id().equals(selectedRelationship)) {
                i = k;
            }
        }
        selectedRelationship = list.get((i + 1) % list.size()).id();
        List<Point2D> pts = routes().get(selectedRelationship);
        if (pts != null && !pts.isEmpty()) {
            reveal(new Rectangle2D.Double(pts.get(0).getX() - 20, pts.get(0).getY() - 20, 40, 40));
        }
        repaint();
    }

    private void nudge(String dir, double step) {
        if (selectedEntity == null) {
            return;
        }
        ErModel.Entity e = model.entity(selectedEntity).orElseThrow();
        double dx = dir.equals("LEFT") ? -step : dir.equals("RIGHT") ? step : 0;
        double dy = dir.equals("UP") ? -step : dir.equals("DOWN") ? step : 0;
        moveEntity(selectedEntity, e.x() + dx, e.y() + dy);
        reveal(bounds(model.entity(selectedEntity).orElseThrow()));
    }

    private void reveal(Rectangle2D r) {
        scrollRectToVisible(new java.awt.Rectangle((int) (r.getX() * zoom) - 20, (int) (r.getY() * zoom) - 20,
                (int) (r.getWidth() * zoom) + 40, (int) (r.getHeight() * zoom) + 40));
    }

    // ================================================================ modello

    public ErModel model() {
        return model;
    }

    /** Le misure delle entità si rifanno (colonne cambiate): anche i percorsi. */
    public void invalidateMeasures() {
        sizes.clear();
        routedFor = null;
        revalidate();
        repaint();
    }

    /** Cambia il carattere del programma: misure e percorsi si rifanno col carattere nuovo. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (sizes != null) {
            sizes.clear();
            routedFor = null;
            revalidate();
            repaint();
        }
    }

    /** Il modello da disegnare (misure ricalcolate). */
    public void setModel(ErModel m) {
        this.model = m;
        sizes.clear();
        routedFor = null;
        bridges = new java.util.HashSet<>();
        if (m != null) {
            for (ReverseEngineer.Bridge b : ReverseEngineer.bridges(m)) {
                bridges.add(b.table().toLowerCase(Locale.ROOT));
            }
        }
        revalidate();
        repaint();
    }

    public void setOnChange(Consumer<ErModel> c) {
        this.onChange = c;
    }

    public void setOnOpenTable(Consumer<String> c) {
        this.onOpenTable = c;
    }

    public void setOnDraw(RelationshipRequest r) {
        this.onDraw = r;
    }

    public void setOnRelationshipMenu(Consumer<Relationship> c) {
        this.onRelationshipMenu = c;
    }

    public double zoom() {
        return zoom;
    }

    public void setZoom(double z) {
        zoom = Math.max(0.25, Math.min(3.0, z));
        revalidate();
        repaint();
    }

    public String selectedRelationship() {
        return selectedRelationship;
    }

    // ================================================================ misure

    private Font nameFont() {
        Font f = UIManager.getFont("Label.font");
        return (f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : f).deriveFont(Font.BOLD);
    }

    private Font rowFont() {
        Font f = UIManager.getFont("Label.font");
        return f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : f.deriveFont(f.getSize2D() - 1f);
    }

    /** Come si misura un'entità con i caratteri veri (anche per la disposizione automatica). */
    public AutoLayout.Measure measure() {
        return this::size;
    }

    AutoLayout.Size size(ErModel.Entity e) {
        return sizes.computeIfAbsent(e.table().toLowerCase(Locale.ROOT), k -> {
            BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scratch.createGraphics();
            try {
                FontMetrics name = g.getFontMetrics(nameFont());
                FontMetrics row = g.getFontMetrics(rowFont());
                // spazio per la pillola dell'intestazione («N:M», «MyISAM», «mancante»), la più larga
                FontMetrics pill = g.getFontMetrics(rowFont().deriveFont(Font.BOLD, rowFont().getSize2D() - 1));
                int pillWidth = 0;
                for (String key : new String[] {"er.entity.bridge", "er.entity.myisam", "er.entity.missing"}) {
                    pillWidth = Math.max(pillWidth, pill.stringWidth(Texts.get(key)));
                }
                double w = name.stringWidth(e.table()) + 2 * PAD + pillWidth + 22;
                for (ErModel.Attribute a : e.columns()) {
                    w = Math.max(w, 22 + row.stringWidth(a.name()) + 16 + row.stringWidth(a.type()) + 2 * PAD);
                }
                return new AutoLayout.Size(Math.max(MIN_WIDTH, Math.ceil(w)), HEADER + e.columns().size() * ROW + 6);
            } finally {
                g.dispose();
            }
        });
    }

    Rectangle2D bounds(ErModel.Entity e) {
        AutoLayout.Size s = size(e);
        return new Rectangle2D.Double(e.x(), e.y(), s.width(), s.height());
    }

    /** Il rettangolo che contiene tutto il diagramma (unità del modello). */
    public Rectangle2D extent() {
        Rectangle2D r = null;
        if (model != null) {
            for (ErModel.Entity e : model.entities()) {
                Rectangle2D b = bounds(e);
                r = r == null ? b : r.createUnion(b);
            }
        }
        return r == null ? new Rectangle2D.Double(0, 0, 400, 300) : r;
    }

    @Override
    public Dimension getPreferredSize() {
        Rectangle2D r = extent();
        return new Dimension((int) Math.ceil((r.getMaxX() + MARGIN) * zoom), (int) Math.ceil((r.getMaxY() + MARGIN) * zoom));
    }

    // ================================================================ disegno

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.scale(zoom, zoom);
            paintDiagram(g);
            if (drawFromTable != null && drawTo != null) {
                ErModel.Entity from = model.entity(drawFromTable).orElse(null);
                if (from != null) {
                    Point2D start = rowAnchor(from, drawFromColumn, true);
                    Point2D end = toModel(drawTo);
                    g.setColor(Tokens.ACCENT);
                    g.setStroke(dashed(1.5f));
                    g.draw(new Line2D.Double(start, end));
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** Disegna il diagramma in unità del modello (serve anche all'esportazione PNG). */
    public void paintDiagram(Graphics2D g) {
        if (model == null) {
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        for (Relationship r : model.relationships()) {
            paintRelationship(g, r);
        }
        for (ErModel.Entity e : model.entities()) {
            paintEntity(g, e);
        }
    }

    private void paintEntity(Graphics2D g, ErModel.Entity e) {
        Rectangle2D b = bounds(e);
        RoundRectangle2D box = new RoundRectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight(), 12, 12);
        g.setColor(Tokens.SHADOW_1);
        g.fill(new RoundRectangle2D.Double(b.getX() + 1, b.getY() + 2, b.getWidth(), b.getHeight(), 12, 12));
        g.setColor(Tokens.BG_SURFACE);
        g.fill(box);
        // intestazione
        Path2D header = new Path2D.Double();
        header.append(new RoundRectangle2D.Double(b.getX(), b.getY(), b.getWidth(), HEADER + 12, 12, 12), false);
        g.setColor(e.missing() ? Tokens.DANGER_TINT : "MyISAM".equalsIgnoreCase(e.engine())
                ? Tokens.ENGINE_MYISAM_TINT
                : Tokens.ACCENT_TINT);
        java.awt.Shape clip = g.getClip();   // il ritaglio di Swing si rimette com'era
        g.clip(new Rectangle2D.Double(b.getX(), b.getY(), b.getWidth(), HEADER));
        g.fill(header);
        g.setClip(clip);
        g.setColor(Tokens.BORDER_SUBTLE);
        g.draw(new Line2D.Double(b.getX(), b.getY() + HEADER, b.getMaxX(), b.getY() + HEADER));
        Font nameFont = nameFont();
        g.setFont(nameFont);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(e.missing() ? Tokens.DANGER : Tokens.TEXT_PRIMARY);
        double baseline = b.getY() + (HEADER + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(e.table(), (float) (b.getX() + PAD), (float) baseline);
        boolean myisam = "MyISAM".equalsIgnoreCase(e.engine());
        if (bridges.contains(e.table().toLowerCase(Locale.ROOT)) || e.missing() || myisam) {
            String pill = e.missing() ? Texts.get("er.entity.missing") : bridges.contains(e.table().toLowerCase(Locale.ROOT))
                    ? Texts.get("er.entity.bridge") : Texts.get("er.entity.myisam");
            g.setFont(rowFont().deriveFont(Font.BOLD, rowFont().getSize2D() - 1));
            FontMetrics pm = g.getFontMetrics();
            double pw = pm.stringWidth(pill) + 10;
            double px = b.getMaxX() - PAD - pw;
            double py = b.getY() + (HEADER - 16) / 2;
            g.setColor(e.missing() ? Tokens.DANGER : myisam && !bridges.contains(e.table().toLowerCase(Locale.ROOT))
                    ? Tokens.ENGINE_MYISAM : Tokens.ACCENT);
            g.fill(new RoundRectangle2D.Double(px, py, pw, 16, 16, 16));
            g.setColor(Tokens.BG_SURFACE);
            g.drawString(pill, (float) (px + 5), (float) (py + 12));
        }
        // colonne
        Font rowFont = rowFont();
        g.setFont(rowFont);
        FontMetrics rm = g.getFontMetrics();
        double y = b.getY() + HEADER + 3;
        for (ErModel.Attribute a : e.columns()) {
            double base = y + (ROW + rm.getAscent() - rm.getDescent()) / 2;
            if (a.primaryKey()) {
                paintKey(g, b.getX() + PAD, y + ROW / 2);
            } else if (isForeignColumn(e.table(), a.name())) {
                g.setColor(Tokens.TEXT_TERTIARY);
                g.fill(new java.awt.geom.Ellipse2D.Double(b.getX() + PAD + 3, y + ROW / 2 - 3, 6, 6));
            }
            g.setColor(Tokens.TEXT_PRIMARY);
            g.setFont(a.primaryKey() ? rowFont.deriveFont(Font.BOLD) : rowFont);
            g.drawString(a.name(), (float) (b.getX() + PAD + 18), (float) base);
            g.setFont(rowFont);
            g.setColor(Tokens.TEXT_SECONDARY);
            String type = a.type();
            g.drawString(type, (float) (b.getMaxX() - PAD - rm.stringWidth(type)), (float) base);
            y += ROW;
        }
        // bordo (la tabella scelta con la tastiera ha il bordo d'accento)
        boolean chosen = e.table().equals(selectedEntity);
        g.setColor(e.missing() ? Tokens.DANGER : chosen ? Tokens.ACCENT : Tokens.BORDER_DEFAULT);
        g.setStroke(e.missing() ? dashed(1.5f) : new BasicStroke(chosen ? 2.4f : 1.2f));
        g.draw(box);
        g.setStroke(new BasicStroke(1f));
    }

    private boolean isForeignColumn(String table, String column) {
        return model.relationships().stream().anyMatch(r -> r.fromTable().equalsIgnoreCase(table)
                && r.fromColumns().stream().anyMatch(column::equalsIgnoreCase));
    }

    /** La chiave oro della chiave primaria (DESIGN-SYSTEM §2). */
    private static void paintKey(Graphics2D g, double x, double cy) {
        g.setColor(Tokens.KEY_GOLD);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Ellipse2D.Double(x, cy - 4, 7, 7));
        g.draw(new Line2D.Double(x + 7, cy - 0.5, x + 13, cy - 0.5));
        g.draw(new Line2D.Double(x + 11, cy - 0.5, x + 11, cy + 2.5));
        g.setStroke(new BasicStroke(1f));
    }

    private static Stroke dashed(float width) {
        return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] {6f, 4f}, 0f);
    }

    /** I percorsi delle relazioni, ricalcolati quando il modello cambia. */
    private transient ErModel routedFor;
    private transient Map<String, List<Point2D>> routes = Map.of();

    private Map<String, List<Point2D>> routes() {
        if (model != routedFor) {
            routes = computeRoutes(null, null, Map.of());
            routedFor = model;
        }
        return routes;
    }

    /**
     * I percorsi di tutte le relazioni ({@link ErRouter}): prima le fisiche, poi le logiche. Con {@code onlyTable}
     * (durante il trascinamento) si ricalcolano solo le relazioni dell'entità trascinata e le altre restano come
     * erano; al rilascio si ricalcola tutto.
     */
    private Map<String, List<Point2D>> computeRoutes(String onlyTable, Rectangle2D area,
            Map<String, List<Point2D>> previous) {
        Map<String, List<Point2D>> out = new HashMap<>();
        if (model == null) {
            return out;
        }
        List<Rectangle2D> boxes = new ArrayList<>();
        for (ErModel.Entity e : model.entities()) {
            boxes.add(bounds(e));
        }
        ErRouter router = new ErRouter(boxes);
        List<Relationship> todo = new ArrayList<>();
        for (Relationship r : model.physical()) {
            todo.add(r);
        }
        for (Relationship r : model.logical()) {
            todo.add(r);
        }
        List<Relationship> compute = new ArrayList<>();
        for (Relationship r : todo) {
            List<Point2D> old = previous.get(r.id());
            if (onlyTable != null && old != null && !r.fromTable().equalsIgnoreCase(onlyTable)
                    && !r.toTable().equalsIgnoreCase(onlyTable) && (area == null || !crosses(old, area))) {
                out.put(r.id(), old);
                router.occupy(old);
            } else {
                compute.add(r);
            }
        }
        for (Relationship r : compute) {
            List<Point2D> pts = routeOf(r, router);
            if (pts != null) {
                out.put(r.id(), pts);
            }
        }
        return out;
    }

    /** Un percorso passa per il rettangolo. */
    private static boolean crosses(List<Point2D> pts, Rectangle2D area) {
        for (int i = 1; i < pts.size(); i++) {
            if (area.intersectsLine(pts.get(i - 1).getX(), pts.get(i - 1).getY(), pts.get(i).getX(),
                    pts.get(i).getY())) {
                return true;
            }
        }
        return false;
    }

    /** Il percorso di una relazione: dalla riga della colonna della figlia alla riga della colonna del padre. */
    private List<Point2D> routeOf(Relationship r, ErRouter router) {
        ErModel.Entity child = model.entity(r.fromTable()).orElse(null);
        ErModel.Entity parent = model.entity(r.toTable()).orElse(null);
        if (child == null || parent == null) {
            return null;
        }
        Rectangle2D cb = bounds(child);
        Rectangle2D pb = bounds(parent);
        double cy = rowY(child, r.fromColumns().isEmpty() ? null : r.fromColumns().get(0));
        double py = rowY(parent, r.toColumns().isEmpty() ? null : r.toColumns().get(0));
        if (child != parent) {
            List<Point2D> routed = router.route(cb, cy, pb, py);
            if (routed != null) {
                return routed;
            }
        }
        // anello della tabella con sé stessa, o entità sovrapposte: il percorso semplice
        Path2D path = new Path2D.Double();
        if (child == parent) {   // relazione della tabella con sé stessa: un anello a destra
            double x = cb.getMaxX();
            path.moveTo(x, cy);
            path.lineTo(x + 28, cy);
            path.lineTo(x + 28, py);
            path.lineTo(x, py);
            List<Point2D> loop = points(path);
            router.occupy(loop);
            return loop;
        }
        boolean childLeft = cb.getCenterX() <= pb.getCenterX();
        if (cb.getMaxX() + 24 <= pb.getMinX() || pb.getMaxX() + 24 <= cb.getMinX()) {
            double x1 = childLeft ? cb.getMaxX() : cb.getMinX();
            double x2 = childLeft ? pb.getMinX() : pb.getMaxX();
            double mid = (x1 + x2) / 2;
            path.moveTo(x1, cy);
            path.lineTo(mid, cy);
            path.lineTo(mid, py);
            path.lineTo(x2, py);
        } else {   // una sopra l'altra: si esce entrambe a destra
            double x = Math.max(cb.getMaxX(), pb.getMaxX()) + 28;
            path.moveTo(cb.getMaxX(), cy);
            path.lineTo(x, cy);
            path.lineTo(x, py);
            path.lineTo(pb.getMaxX(), py);
        }
        List<Point2D> pts = points(path);
        router.occupy(pts);
        return pts;
    }

    /** Il percorso della relazione come linea spezzata; {@code null} se una delle due entità manca. */
    private Path2D route(Relationship r) {
        List<Point2D> pts = routes().get(r.id());
        if (pts == null || pts.size() < 2) {
            return null;
        }
        Path2D path = new Path2D.Double();
        path.moveTo(pts.get(0).getX(), pts.get(0).getY());
        for (int i = 1; i < pts.size(); i++) {
            path.lineTo(pts.get(i).getX(), pts.get(i).getY());
        }
        return path;
    }

    private double rowY(ErModel.Entity e, String column) {
        int i = 0;
        for (ErModel.Attribute a : e.columns()) {
            if (a.name().equalsIgnoreCase(column)) {
                return e.y() + HEADER + 3 + i * ROW + ROW / 2;
            }
            i++;
        }
        return e.y() + HEADER / 2;
    }

    private Point2D rowAnchor(ErModel.Entity e, String column, boolean right) {
        Rectangle2D b = bounds(e);
        return new Point2D.Double(right ? b.getMaxX() : b.getMinX(), rowY(e, column));
    }

    private void paintRelationship(Graphics2D g, Relationship r) {
        Path2D path = route(r);
        if (path == null) {
            return;
        }
        boolean logical = r.kind() == Relationship.Kind.LOGICAL;
        boolean selected = r.id().equals(selectedRelationship);
        // logica rotta: una sua colonna non c'è più (Aggiorna dal database) → in rosso
        boolean broken = logical && !it.ramasql.model.ModelRefresh.brokenColumns(model, r).isEmpty();
        Color color = broken ? Tokens.DANGER : selected ? Tokens.ACCENT_PRESSED : logical ? Tokens.ACCENT
                : Tokens.TEXT_SECONDARY;
        g.setColor(color);
        g.setStroke(logical ? dashed(selected ? 2.4f : 1.6f) : new BasicStroke(selected ? 2.4f : 1.4f));
        g.draw(path);
        g.setStroke(new BasicStroke(1.4f));
        List<Point2D> pts = points(path);
        if (pts.size() < 2) {
            return;
        }
        // estremo della figlia (primo punto): «zero o più» (cerchio e zampa di gallina), per 1:1 «zero o uno»
        // (cerchio e barretta): a una riga del padre possono non corrispondere righe della figlia
        Point2D c0 = pts.get(0);
        Point2D c1 = pts.get(1);
        double dirC = Math.signum(c1.getX() - c0.getX());
        if (dirC == 0) {
            dirC = 1;
        }
        if (r.cardinality() == Relationship.Cardinality.ONE_TO_ONE) {
            bar(g, c0.getX() + dirC * 7, c0.getY());
        } else {
            crowFoot(g, c0, dirC);
        }
        circle(g, c0.getX() + dirC * 18, c0.getY(), color);
        // estremo del padre (ultimo punto): uno — obbligatoria due barrette, facoltativa barretta e cerchio; per N:M
        // (indicativa) la zampa di gallina anche qui
        Point2D p0 = pts.get(pts.size() - 1);
        Point2D p1 = pts.get(pts.size() - 2);
        double dirP = Math.signum(p1.getX() - p0.getX());
        if (dirP == 0) {
            dirP = 1;
        }
        if (r.cardinality() == Relationship.Cardinality.MANY_TO_MANY) {
            crowFoot(g, p0, dirP);
        } else {
            bar(g, p0.getX() + dirP * 7, p0.getY());
        }
        if (r.mandatory()) {
            bar(g, p0.getX() + dirP * 13, p0.getY());
        } else {
            circle(g, p0.getX() + dirP * 18, p0.getY(), color);
        }
        if (!r.label().isEmpty() && logical) {
            Point2D mid = pts.get(pts.size() / 2);
            g.setFont(rowFont().deriveFont(rowFont().getSize2D() - 1));
            FontMetrics fm = g.getFontMetrics();
            double w = fm.stringWidth(r.label()) + 8;
            g.setColor(Tokens.BG_SURFACE);
            g.fill(new Rectangle2D.Double(mid.getX() - w / 2, mid.getY() - 16, w, 14));
            g.setColor(color);
            g.drawString(r.label(), (float) (mid.getX() - w / 2 + 4), (float) (mid.getY() - 5));
        }
    }

    /** Zampa di gallina: tre linee che si aprono verso l'entità, dalla punta a 12 unità. */
    private static void crowFoot(Graphics2D g, Point2D at, double dir) {
        double tip = at.getX() + dir * 12;
        g.draw(new Line2D.Double(tip, at.getY(), at.getX(), at.getY() - 6));
        g.draw(new Line2D.Double(tip, at.getY(), at.getX(), at.getY() + 6));
        g.draw(new Line2D.Double(tip, at.getY(), at.getX(), at.getY()));
    }

    /** Cerchio vuoto («zero»), centrato in (x, y). */
    private static void circle(Graphics2D g, double x, double y, Color color) {
        java.awt.geom.Ellipse2D c = new java.awt.geom.Ellipse2D.Double(x - 4, y - 4, 8, 8);
        g.setColor(Tokens.BG_SURFACE);
        g.fill(c);
        g.setColor(color);
        g.draw(c);
    }

    private static void bar(Graphics2D g, double x, double y) {
        g.draw(new Line2D.Double(x, y - 6, x, y + 6));
    }

    private static List<Point2D> points(Path2D path) {
        List<Point2D> out = new ArrayList<>();
        double[] c = new double[6];
        for (java.awt.geom.PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            it.currentSegment(c);
            out.add(new Point2D.Double(c[0], c[1]));
        }
        return out;
    }

    // ================================================================ interazione

    Point2D toModel(Point p) {
        return new Point2D.Double(p.x / zoom, p.y / zoom);
    }

    /** L'entità sotto il punto (unità del modello); {@code null} se nessuna. L'ultima disegnata sta sopra. */
    public String entityAt(Point2D p) {
        if (model == null) {
            return null;
        }
        List<ErModel.Entity> list = model.entities();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (bounds(list.get(i)).contains(p)) {
                return list.get(i).table();
            }
        }
        return null;
    }

    /** La colonna sotto il punto, nell'entità data; {@code null} sull'intestazione. */
    public String columnAt(String table, Point2D p) {
        ErModel.Entity e = model.entity(table).orElse(null);
        if (e == null) {
            return null;
        }
        int i = (int) Math.floor((p.getY() - e.y() - HEADER - 3) / ROW);
        return i >= 0 && i < e.columns().size() ? e.columns().get(i).name() : null;
    }

    /** La relazione vicina al punto (entro 5 unità); {@code null} se nessuna. */
    public Relationship relationshipAt(Point2D p) {
        if (model == null) {
            return null;
        }
        for (Relationship r : model.relationships()) {
            Path2D path = route(r);
            if (path == null) {
                continue;
            }
            List<Point2D> pts = points(path);
            for (int i = 1; i < pts.size(); i++) {
                if (Line2D.ptSegDist(pts.get(i - 1).getX(), pts.get(i - 1).getY(), pts.get(i).getX(), pts.get(i).getY(),
                        p.getX(), p.getY()) <= 5) {
                    return r;
                }
            }
        }
        return null;
    }

    private void pressed(MouseEvent e) {
        Point2D p = toModel(e.getPoint());
        moved = false;
        String t = entityAt(p);
        if (t == null) {
            Relationship r = relationshipAt(p);
            selectedRelationship = r == null ? null : r.id();
            repaint();
            if (r != null && (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3)) {
                onRelationshipMenu.accept(r);
            }
            return;
        }
        selectedRelationship = null;
        String column = columnAt(t, p);
        if (column != null && e.getButton() == MouseEvent.BUTTON1 && !e.isShiftDown()) {
            drawFromTable = t;
            drawFromColumn = column;
            drawTo = null;
        } else {
            ErModel.Entity en = model.entity(t).orElseThrow();
            dragged = t;
            dragDx = p.getX() - en.x();
            dragDy = p.getY() - en.y();
        }
    }

    private void dragged(MouseEvent e) {
        scrollRectToVisible(new java.awt.Rectangle(e.getX(), e.getY(), 1, 1));   // verso il bordo: la vista scorre
        Point2D p = toModel(e.getPoint());
        if (dragged != null) {
            moved = true;
            double nx = Math.max(0, Math.round(p.getX() - dragDx));
            double ny = Math.max(0, Math.round(p.getY() - dragDy));
            model = model.changeEntity(dragged, en -> en.at(nx, ny));
            routes = computeRoutes(dragged, null, routes);
            routedFor = model;
            revalidate();
            repaint();
        } else if (drawFromTable != null) {
            drawTo = e.getPoint();
            repaint();
        }
    }

    private void released(MouseEvent e) {
        Point2D p = toModel(e.getPoint());
        if (dragged != null) {
            String released = dragged;
            dragged = null;
            // al rilascio si rifanno le relazioni dell'entità e quelle che ora le passerebbero sotto (non tutte:
            // con cento entità ricalcolarle tutte fermerebbe l'interfaccia)
            ErModel.Entity movedEntity = model.entity(released).orElse(null);
            if (movedEntity != null) {
                Rectangle2D b = bounds(movedEntity);
                Rectangle2D area = new Rectangle2D.Double(b.getX() - ErRouter.CLEAR, b.getY() - ErRouter.CLEAR,
                        b.getWidth() + 2 * ErRouter.CLEAR, b.getHeight() + 2 * ErRouter.CLEAR);
                routes = computeRoutes(released, area, routes);
                routedFor = model;
            } else {
                routedFor = null;
            }
            repaint();
            if (moved) {
                onChange.accept(model);
            }
        } else if (drawFromTable != null) {
            String target = entityAt(p);
            String targetColumn = target == null ? null : columnAt(target, p);
            String fromTable = drawFromTable;
            String fromColumn = drawFromColumn;
            drawFromTable = null;
            drawFromColumn = null;
            boolean drawn = drawTo != null;
            drawTo = null;
            repaint();
            if (drawn && target != null && targetColumn != null
                    && !(target.equalsIgnoreCase(fromTable) && targetColumn.equalsIgnoreCase(fromColumn))) {
                onDraw.draw(fromTable, fromColumn, target, targetColumn);
            }
        }
    }

    /** Il punto (coordinate del componente, zoom compreso) al centro della riga di una colonna, o dell'intestazione. */
    public Point pointOf(String table, String column) {
        ErModel.Entity e = model.entity(table).orElseThrow();
        double y = column == null ? e.y() + HEADER / 2 : rowY(e, column);
        double x = e.x() + Math.min(60, bounds(e).getWidth() / 2);
        return new Point((int) Math.round(x * zoom), (int) Math.round(y * zoom));
    }

    /** I vertici del percorso di una relazione (unità del modello); vuoto se non si disegna. */
    public List<Point2D> routePoints(String relationshipId) {
        List<Point2D> pts = routes().get(relationshipId);
        return pts == null ? List.of() : List.copyOf(pts);
    }

    /** Margine bianco attorno al diagramma nell'immagine esportata (unità del modello). */
    public static double margin() {
        return MARGIN;
    }

    /** Sposta un'entità (come un trascinamento): per la tastiera e per i test. */
    public void moveEntity(String table, double x, double y) {
        model = model.changeEntity(table, en -> en.at(Math.max(0, x), Math.max(0, y)));
        revalidate();
        repaint();
        onChange.accept(model);
    }

    /** Seleziona una relazione (per il menu e per i test). */
    public void selectRelationship(String id) {
        selectedRelationship = id;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (model == null) {
            return null;
        }
        Point2D p = toModel(e.getPoint());
        String t = entityAt(p);
        if (t != null) {
            String c = columnAt(t, p);
            if (c == null) {
                return Texts.get("er.canvas.entity.tooltip", t);
            }
            return Texts.get("er.canvas.column.tooltip", t, c);
        }
        Relationship r = relationshipAt(p);
        if (r != null) {
            return Texts.get(r.kind() == Relationship.Kind.LOGICAL ? "er.canvas.logical.tooltip"
                    : "er.canvas.physical.tooltip", r.describe(), Texts.get("er.cardinality." + r.cardinality().name()),
                    Texts.get(r.mandatory() ? "er.mandatory.yes" : "er.mandatory.no"));
        }
        return Texts.get("er.canvas.tooltip");
    }

    /** Lato massimo dell'immagine esportata, in pixel: oltre, la scala si riduce (memoria e programmi di stampa). */
    public static final int MAX_EXPORT_SIDE = 8000;

    /** La scala per l'esportazione: quella chiesta, ridotta se l'immagine supererebbe {@link #MAX_EXPORT_SIDE}. */
    public double exportScale(double wanted) {
        Rectangle2D r = extent();
        double side = Math.max(r.getWidth(), r.getHeight()) + 2 * MARGIN;
        return Math.min(wanted, MAX_EXPORT_SIDE / side);
    }

    /**
     * Una copia del canvas con lo stesso modello, misure e percorsi già calcolati: si disegna in sottofondo (PNG) senza
     * toccare il canvas che l'utente sta usando.
     */
    public ErCanvas snapshot() {
        ErCanvas copy = new ErCanvas();
        copy.setModel(model);
        copy.extent();
        copy.routes();
        return copy;
    }

    /** L'immagine del diagramma a una scala data (1 = 100%), con un margine bianco. */
    public BufferedImage render(double scale) {
        Rectangle2D r = extent();
        int w = (int) Math.ceil((r.getWidth() + 2 * MARGIN) * scale);
        int h = (int) Math.ceil((r.getHeight() + 2 * MARGIN) * scale);
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Tokens.BG_SURFACE);   // bianco: il fondo della carta
            g.fillRect(0, 0, w, h);
            g.scale(scale, scale);
            g.translate(MARGIN - r.getX(), MARGIN - r.getY());
            paintDiagram(g);
        } finally {
            g.dispose();
        }
        return img;
    }
}
