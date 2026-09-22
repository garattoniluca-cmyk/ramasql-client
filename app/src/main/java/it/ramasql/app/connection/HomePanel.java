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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import it.ramasql.app.Texts;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ServerInfo;

/**
 * Schermata iniziale, come la Home di MySQL Workbench: <strong>una tessera per profilo</strong> (nome,
 * utente@host:porta, server dell'ultima connessione) più la tessera «Nuova connessione». Un clic connette;
 * il menu contestuale della tessera offre Modifica, Duplica, Elimina.
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

        JLabel title = new JLabel(Texts.get("home.title"));
        title.putClientProperty("FlatLaf.styleClass", "h1");
        JLabel hint = new JLabel(Texts.get("home.hint"));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(40, 48, 20, 48));
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(hint);

        tiles.setBorder(BorderFactory.createEmptyBorder(4, 48, 48, 48));
        JScrollPane scroll = new JScrollPane(tiles);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
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
        JButton tile = new JButton(Texts.get("home.tile.new"));
        tile.setName(NEW_TILE_NAME);
        tile.putClientProperty("FlatLaf.style", "arc: 16; borderWidth: 1; focusWidth: 1");
        tile.putClientProperty("FlatLaf.styleClass", "h3");
        tile.addActionListener(e -> controller.newProfile());
        return tile;
    }

    private JButton profileTile(ConnectionProfile profile) {
        JButton tile = new JButton();
        tile.setName("tile." + profile.name());
        tile.putClientProperty("FlatLaf.style", "arc: 16; borderWidth: 1; focusWidth: 1");
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setMargin(new Insets(14, 18, 14, 18)); // il bordo resta quello di FlatLaf (che applica lo stile): lo spazio è il margine

        JLabel name = new JLabel(profile.name());
        name.putClientProperty("FlatLaf.styleClass", "h3");
        JLabel address = new JLabel(profile.address());
        ServerInfo last = profile.lastServer();
        JLabel server = new JLabel(last == null ? Texts.get("home.tile.neverConnected") : last.displayName());
        server.setForeground(UIManager.getColor("Label.disabledForeground"));
        for (JLabel label : List.of(name, address, server)) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setHorizontalAlignment(SwingConstants.LEFT);
        }
        tile.add(name);
        tile.add(Box.createVerticalStrut(6));
        tile.add(address);
        tile.add(Box.createVerticalGlue());
        tile.add(server);

        tile.getAccessibleContext().setAccessibleName(profile.name() + ", " + profile.address());
        tile.addActionListener(e -> controller.connect(profile));
        tile.setComponentPopupMenu(popup(profile));
        return tile;
    }

    /** Menu contestuale della tessera: Modifica, Duplica, Elimina. */
    private JPopupMenu popup(ConnectionProfile profile) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem(Texts.get("home.tile.edit"));
        edit.addActionListener(e -> controller.editProfile(profile));
        JMenuItem duplicate = new JMenuItem(Texts.get("home.tile.duplicate"));
        duplicate.addActionListener(e -> controller.duplicateProfile(profile));
        JMenuItem delete = new JMenuItem(Texts.get("home.tile.delete"));
        delete.addActionListener(e -> controller.deleteProfile(profile));
        menu.add(edit);
        menu.add(duplicate);
        menu.addSeparator();
        menu.add(delete);
        return menu;
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

    /** Tessere di dimensione fissa (in proporzione al carattere), disposte per righe. */
    private static final class TileLayout implements LayoutManager {

        private static final int GAP = 20;

        private static Dimension tileSize(Container parent) {
            int em = parent.getFontMetrics(parent.getFont()).getHeight();
            return new Dimension(em * 16, em * 6);
        }

        private static int columns(Container parent, Dimension tile) {
            Insets in = parent.getInsets();
            int width = parent.getWidth() > 0 ? parent.getWidth() : 1000;
            return Math.max(1, (width - in.left - in.right + GAP) / (tile.width + GAP));
        }

        @Override
        public void layoutContainer(Container parent) {
            Dimension tile = tileSize(parent);
            Insets in = parent.getInsets();
            int columns = columns(parent, tile);
            for (int i = 0; i < parent.getComponentCount(); i++) {
                int x = in.left + (i % columns) * (tile.width + GAP);
                int y = in.top + (i / columns) * (tile.height + GAP);
                parent.getComponent(i).setBounds(x, y, tile.width, tile.height);
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Dimension tile = tileSize(parent);
            Insets in = parent.getInsets();
            int columns = columns(parent, tile);
            int rows = (parent.getComponentCount() + columns - 1) / columns;
            return new Dimension(in.left + in.right + columns * (tile.width + GAP) - GAP,
                    in.top + in.bottom + Math.max(1, rows) * (tile.height + GAP) - GAP);
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
}
