/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.connection;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Pill;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ServerInfo;

/**
 * Schermata iniziale ({@code DESIGN-SYSTEM.md} §3.2), come la Home di MySQL Workbench ma più ariosa: il saluto
 * «Ciao! A quale database ti colleghi?», poi <strong>una tessera per profilo</strong> — pillola del server (MariaDB
 * petrolio, MySQL arancio, «Mai usata» grigia), nome, utente@host:porta — più la tessera tratteggiata «Nuova
 * connessione». Un clic (o Invio) connette; le frecce passano da una tessera all'altra; il menu «⋯» e il tasto destro
 * offrono Modifica, Duplica, Elimina. Al passaggio del mouse la tessera si solleva (ombra {@code elev.2}, bordo
 * d'accento).
 */
public final class HomePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Nome del componente della tessera «Nuova connessione» (per i test). */
    public static final String NEW_TILE_NAME = "tile.new";

    private final transient ConnectionController controller;
    private final TilesPanel tiles = new TilesPanel();
    private final List<JButton> profileTiles = new ArrayList<>();
    private JButton newTile;

    public HomePanel(ConnectionController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setBackground(Tokens.BG_WINDOW);

        JLabel title = Styles.text(new JLabel(Texts.get("home.title")), "display", Tokens.TEXT_PRIMARY);
        JLabel hint = new JLabel(Texts.get("home.hint"));
        hint.setForeground(Tokens.TEXT_SECONDARY);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_48, Tokens.SPACE_48, Tokens.SPACE_24,
                Tokens.SPACE_48));
        header.add(title);
        header.add(Box.createVerticalStrut(Tokens.SPACE_8));
        header.add(hint);

        // i margini tengono conto dell'ombra delle tessere (TileLayout.SHADOW)
        tiles.setBorder(BorderFactory.createEmptyBorder(0, Tokens.SPACE_48 - TileLayout.SHADOW, Tokens.SPACE_48,
                Tokens.SPACE_48 - TileLayout.SHADOW));
        JScrollPane scroll = new JScrollPane(tiles);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        installArrowKeys();
    }

    /** Ricostruisce le tessere. */
    public void setProfiles(List<ConnectionProfile> profiles) {
        tiles.removeAll();
        profileTiles.clear();
        for (ConnectionProfile profile : profiles) {
            JButton tile = profileTile(profile);
            profileTiles.add(tile);
            tiles.add(tile);
        }
        newTile = createNewTile();
        tiles.add(newTile);
        tiles.revalidate();
        tiles.repaint();
    }

    /** Le tessere dei profili, nell'ordine mostrato. */
    public List<JButton> profileTiles() {
        return List.copyOf(profileTiles);
    }

    /** La tessera «Nuova connessione». */
    public JButton newConnectionTile() {
        return newTile;
    }

    private JButton createNewTile() {
        Tile tile = new Tile(true);
        tile.setText(Texts.get("home.tile.new"));
        tile.setIcon(AppIcons.get(AppIcons.PLUS, 24));
        tile.setHorizontalTextPosition(SwingConstants.CENTER);
        tile.setVerticalTextPosition(SwingConstants.BOTTOM);
        tile.setIconTextGap(Tokens.px(Tokens.SPACE_8));
        tile.setForeground(Tokens.ACCENT);
        Styles.text(tile, "emphasis");
        tile.setName(NEW_TILE_NAME);
        tile.setToolTipText(Texts.get("home.tile.new.tooltip"));
        tile.addActionListener(e -> controller.newProfile());
        return tile;
    }

    private JButton profileTile(ConnectionProfile profile) {
        Tile tile = new Tile(false);
        tile.setName("tile." + profile.name());
        tile.setLayout(new TileContentLayout());

        // l'ordine dei componenti è quello di lettura: nome, indirizzo, server (i test e i lettori di schermo)
        JLabel name = Styles.text(new JLabel(profile.name()), "title", Tokens.TEXT_PRIMARY);
        JLabel address = new JLabel(profile.address());
        address.setForeground(Tokens.TEXT_SECONDARY);
        ServerInfo last = profile.lastServer();
        Pill server = Pill.dotted(last == null ? Texts.get("home.tile.neverConnected") : last.displayName(),
                last == null ? Tokens.TEXT_TERTIARY : last.isMariaDb() ? Tokens.SERVER_MARIADB : Tokens.SERVER_MYSQL);
        JPopupMenu menu = popup(profile);
        JButton more = new JButton(AppIcons.small(AppIcons.MORE));
        Styles.toolbarButton(more);
        more.setName("tile.more");
        more.setToolTipText(Texts.get("home.tile.more"));
        more.getAccessibleContext().setAccessibleName(Texts.get("home.tile.more"));
        more.addActionListener(e -> menu.show(more, 0, more.getHeight()));
        for (JLabel label : List.of(name, address, server)) {
            label.setHorizontalAlignment(SwingConstants.LEFT);
        }
        tile.add(name, TileContentLayout.NAME);
        tile.add(address, TileContentLayout.ADDRESS);
        tile.add(server, TileContentLayout.SERVER);
        tile.add(more, TileContentLayout.MORE);

        tile.getAccessibleContext().setAccessibleName(profile.name() + ", " + profile.address() + ", " + server.getText());
        String tip = Texts.get("home.tile.tooltip", profile.name(), profile.address(), profile.user());
        tile.setToolTipText(profile.note().isEmpty() ? tip : tip + "\n" + profile.note());
        tile.addActionListener(e -> controller.connect(profile));
        tile.setComponentPopupMenu(menu);
        return tile;
    }

    /** Menu della tessera («⋯» e tasto destro): Modifica, Duplica, Elimina. */
    private JPopupMenu popup(ConnectionProfile profile) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem(Texts.get("home.tile.edit"), AppIcons.small(AppIcons.MENU_SETTINGS));
        edit.addActionListener(e -> controller.editProfile(profile));
        JMenuItem duplicate = new JMenuItem(Texts.get("home.tile.duplicate"), AppIcons.small(AppIcons.COPY));
        duplicate.addActionListener(e -> controller.duplicateProfile(profile));
        JMenuItem delete = new JMenuItem(Texts.get("home.tile.delete"),
                AppIcons.tinted(AppIcons.small(AppIcons.STATUS_ERROR), Tokens.DANGER));
        delete.setForeground(Tokens.DANGER);
        delete.addActionListener(e -> controller.deleteProfile(profile));
        menu.add(edit);
        menu.add(duplicate);
        menu.addSeparator();
        menu.add(delete);
        return menu;
    }

    /** Frecce: da una tessera alla vicina, come in una griglia; Invio connette (vedi {@link Tile}). */
    private void installArrowKeys() {
        int[][] moves = {{KeyEvent.VK_LEFT, -1, 0}, {KeyEvent.VK_RIGHT, 1, 0}, {KeyEvent.VK_UP, 0, -1},
                {KeyEvent.VK_DOWN, 0, 1}};
        for (int[] move : moves) {
            String name = "tile.move." + move[0];
            tiles.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(move[0], 0), name);
            tiles.getActionMap().put(name, new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    moveFocus(move[1], move[2]);
                }
            });
        }
    }

    private void moveFocus(int dx, int dy) {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        int index = -1;
        for (int i = 0; i < tiles.getComponentCount(); i++) {
            Component c = tiles.getComponent(i);
            if (c == focused || (focused != null && c instanceof Container ct && ct.isAncestorOf(focused))) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        int columns = TileLayout.columns(tiles);
        int target = index + dx + dy * columns;
        if (target >= 0 && target < tiles.getComponentCount()) {
            tiles.getComponent(target).requestFocusInWindow();
        }
    }

    // ------------------------------------------------------------------ la tessera

    /**
     * Tessera disegnata a mano: fondo {@code bg.surface}, raggio 12, bordo {@code border.subtle} e ombra {@code elev.1};
     * al passaggio (120 ms) ombra {@code elev.2} e bordo {@code accent}; con il focus anello d'accento di 2 px.
     * La tessera «Nuova connessione» ha il bordo tratteggiato e nessuna ombra.
     */
    static final class Tile extends JButton {

        private static final long serialVersionUID = 1L;
        private static final int STEPS = 8;

        private final boolean dashed;
        private float lift;
        private final transient Timer animation;

        Tile(boolean dashed) {
            this.dashed = dashed;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(BorderFactory.createEmptyBorder(TileLayout.SHADOW, TileLayout.SHADOW, TileLayout.SHADOW,
                    TileLayout.SHADOW));
            // 120 ms in 8 passi: la tessera si solleva, poi torna giù
            animation = new Timer(15, e -> {
                float target = getModel().isRollover() ? 1f : 0f;
                lift += Math.signum(target - lift) / STEPS;
                if (Math.abs(target - lift) < 0.5f / STEPS) {
                    lift = target;
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            getModel().addChangeListener(e -> {
                if (!animation.isRunning()) {
                    animation.start();
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "tile.activate");
            getActionMap().put("tile.activate", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    doClick();
                }
            });
        }

        /** Quanto è «sollevata» (0 a riposo, 1 al passaggio): per i test e le immagini. */
        float lift() {
            return lift;
        }

        void setLift(float value) {
            lift = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int m = TileLayout.SHADOW;
                int w = getWidth() - 2 * m;
                int h = getHeight() - 2 * m;
                int arc = Tokens.px(Tokens.RADIUS_TILE * 2);
                if (!dashed) {
                    paintShadow(g2, m, w, h, arc);
                }
                boolean focus = isFocusOwner();
                Color fill = dashed ? Tokens.mix(Tokens.BG_WINDOW, Tokens.ACCENT_TINT, lift) : Tokens.BG_SURFACE;
                g2.setColor(fill);
                g2.fillRoundRect(m, m, w, h, arc, arc);
                Color border = Tokens.mix(dashed ? Tokens.BORDER_DEFAULT : Tokens.BORDER_SUBTLE, Tokens.ACCENT, lift);
                float width = Tokens.px(1);
                if (dashed) {
                    g2.setStroke(new BasicStroke(Math.max(1f, Tokens.px(3) / 2f), BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND, 1f, new float[] {Tokens.px(6), Tokens.px(5)}, 0f));
                } else {
                    g2.setStroke(new BasicStroke(width));
                }
                g2.setColor(border);
                g2.drawRoundRect(m, m, w - 1, h - 1, arc, arc);
                if (focus) {
                    g2.setStroke(new BasicStroke(Tokens.px(2)));
                    g2.setColor(Tokens.ACCENT);
                    g2.drawRoundRect(m + 1, m + 1, w - 3, h - 3, arc - 2, arc - 2);
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }

        /** Ombra morbida a strati: {@code elev.1} a riposo, {@code elev.2} sollevata (§1.3). */
        private void paintShadow(Graphics2D g2, int m, int w, int h, int arc) {
            int layers = Math.max(2, Math.round(2 + lift * (m - 2)));
            int dy = Math.round(Tokens.px(1) + lift * Tokens.px(5));
            int totalAlpha = Math.round(Tokens.SHADOW_1.getAlpha() + lift
                    * (Tokens.SHADOW_2.getAlpha() - Tokens.SHADOW_1.getAlpha()));
            int alpha = Math.max(1, totalAlpha * 2 / layers);
            for (int i = layers; i >= 1; i--) {
                g2.setColor(Tokens.alpha(Tokens.SHADOW_2, Math.max(1, alpha * (layers - i + 1) / layers)));
                g2.fillRoundRect(m - i, m - i + dy, w + 2 * i, h + 2 * i, arc + 2 * i, arc + 2 * i);
            }
        }
    }

    /** Dentro la tessera: pillola del server e «⋯» in alto, nome e indirizzo in basso; margine interno 16. */
    private static final class TileContentLayout implements LayoutManager {

        static final String NAME = "name";
        static final String ADDRESS = "address";
        static final String SERVER = "server";
        static final String MORE = "more";

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int pad = Tokens.px(Tokens.SPACE_16);
            int left = in.left + pad;
            int right = parent.getWidth() - in.right - pad;
            int top = in.top + pad;
            int bottom = parent.getHeight() - in.bottom - pad;
            Component name = parent.getComponent(0);
            Component address = parent.getComponent(1);
            Component server = parent.getComponent(2);
            Component more = parent.getComponent(3);
            Dimension pill = server.getPreferredSize();
            Dimension dots = more.getPreferredSize();
            server.setBounds(left, top, Math.min(pill.width, right - left - dots.width), pill.height);
            int moreY = top + (pill.height - dots.height) / 2;
            more.setBounds(right - dots.width + Tokens.px(6), moreY, dots.width, dots.height);
            Dimension a = address.getPreferredSize();
            address.setBounds(left, bottom - a.height, right - left, a.height);
            Dimension n = name.getPreferredSize();
            name.setBounds(left, bottom - a.height - Tokens.px(2) - n.height, right - left, n.height);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return TileLayout.tileSize();
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return TileLayout.tileSize();
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
            // posizione decisa dall'ordine di inserimento
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            // nessun vincolo per componente
        }
    }

    /** Pannello delle tessere: va a capo secondo la larghezza e scorre solo in verticale. */
    private static final class TilesPanel extends JPanel implements Scrollable {

        private static final long serialVersionUID = 1L;

        TilesPanel() {
            super(new TileLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * Tessere 260×132 (§3.2, in proporzione al carattere), disposte per righe con 16 px tra l'una e l'altra; ogni
     * tessera ha intorno {@link #SHADOW} px per l'ombra.
     */
    private static final class TileLayout implements LayoutManager {

        /** Margine intorno a ogni tessera in cui si disegna l'ombra (px). */
        static final int SHADOW = 10;

        static Dimension tileSize() {
            return new Dimension(Tokens.px(Tokens.TILE_WIDTH) + 2 * SHADOW, Tokens.px(Tokens.TILE_HEIGHT) + 2 * SHADOW);
        }

        private static int gap() {
            return Math.max(0, Tokens.px(Tokens.SPACE_16) - 2 * SHADOW + Tokens.px(Tokens.SPACE_8));
        }

        static int columns(Container parent) {
            Dimension tile = tileSize();
            Insets in = parent.getInsets();
            int width = parent.getWidth() > 0 ? parent.getWidth() : 1000;
            return Math.max(1, (width - in.left - in.right + gap()) / (tile.width + gap()));
        }

        @Override
        public void layoutContainer(Container parent) {
            Dimension tile = tileSize();
            Insets in = parent.getInsets();
            int columns = columns(parent);
            for (int i = 0; i < parent.getComponentCount(); i++) {
                int x = in.left + (i % columns) * (tile.width + gap());
                int y = in.top + (i / columns) * (tile.height + gap());
                parent.getComponent(i).setBounds(x, y, tile.width, tile.height);
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Dimension tile = tileSize();
            Insets in = parent.getInsets();
            int columns = columns(parent);
            int rows = (parent.getComponentCount() + columns - 1) / columns;
            return new Dimension(in.left + in.right + columns * (tile.width + gap()) - gap(),
                    in.top + in.bottom + Math.max(1, rows) * (tile.height + gap()) - gap());
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
            // nessun vincolo per componente
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            // nessun vincolo per componente
        }
    }

    /** Per le immagini di evidenza: solleva la prima tessera come al passaggio del mouse. */
    public void liftFirstTileForPreview() {
        if (!profileTiles.isEmpty() && profileTiles.get(0) instanceof Tile tile) {
            tile.setLift(1f);
        }
    }
}
