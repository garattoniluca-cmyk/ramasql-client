/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import it.ramasql.app.connection.ConnectingPanel;
import it.ramasql.app.connection.ConnectionController;
import it.ramasql.app.connection.HomePanel;
import it.ramasql.app.connection.ShellView;
import it.ramasql.app.settings.SettingsController;
import it.ramasql.core.ProductInfo;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;

/**
 * Finestra principale, <strong>a tre zone non riconfigurabili</strong> ({@code DESIGN.md} §2): <em>Navigatore</em> a
 * sinistra, <em>Area di lavoro</em> a schede al centro, <em>Pannello SQL</em> in basso (Registro / Anteprima /
 * Messaggi; ridimensionabile e comprimibile, ma sempre presente). Sopra, una barra strumenti corta con testo;
 * sotto, la barra di stato con connessione, server e catalogo. Senza connessione, al posto delle tre zone c'è la
 * schermata iniziale a tessere.
 */
public final class MainFrame extends JFrame implements ShellView {

    private static final long serialVersionUID = 1L;

    /** Le schermate che si alternano al centro della finestra. */
    public enum Screen { HOME, CONNECTING, WORKSPACE }

    /** I dieci pulsanti della barra strumenti, nell'ordine di {@code DESIGN.md} §2. */
    private static final String[] TOOLBAR_KEYS = {"connect", "newQuery", "newVisualQuery", "newTable", "newView",
            "import", "export", "erModel", "run", "stop"};

    private final transient ConnectionController connections;
    private final transient SettingsController settings;
    private final transient Prompts prompts;

    private final CardLayout screens = new CardLayout();
    private final JPanel screenHost = new JPanel(screens);
    private final HomePanel home;
    private final ConnectingPanel connecting;
    private final JPanel navigator = new JPanel(new BorderLayout());
    private final JTabbedPane workTabs = new JTabbedPane();
    private final JTabbedPane sqlPanel = new JTabbedPane();
    private final JSplitPane horizontalSplit;
    private final JSplitPane verticalSplit;
    private final List<JButton> toolbarButtons = new ArrayList<>();
    private final JLabel statusConnection = new JLabel();
    private final JLabel statusServer = new JLabel();
    private final JLabel statusCatalog = new JLabel();
    private Screen screen = Screen.HOME;

    public MainFrame(ConnectionController connections, SettingsController settings, Prompts prompts) {
        super(ProductInfo.title());
        this.connections = connections;
        this.settings = settings;
        this.prompts = prompts;
        setIconImages(AppIcon.images());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                connections.shutdown();
            }
        });

        home = new HomePanel(connections);
        connecting = new ConnectingPanel(connections);

        buildNavigator();
        JPanel workArea = buildWorkArea();
        buildSqlPanel();
        horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigator, workArea);
        horizontalSplit.setDividerLocation(260);
        horizontalSplit.setBorder(null);
        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizontalSplit, sqlPanel);
        verticalSplit.setResizeWeight(1.0);
        verticalSplit.setOneTouchExpandable(true); // comprimibile con un clic, ma sempre presente
        verticalSplit.setBorder(null);

        screenHost.add(home, Screen.HOME.name());
        screenHost.add(connecting, Screen.CONNECTING.name());
        screenHost.add(verticalSplit, Screen.WORKSPACE.name());

        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(screenHost, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(1180, 760));
        setMinimumSize(new Dimension(820, 520));
        pack();
        verticalSplit.setDividerLocation(getHeight() - 330);
        setLocationRelativeTo(null);
        showHome(connections.profiles());
    }

    // ------------------------------------------------------------------ costruzione

    private void buildNavigator() {
        navigator.setName("zone.navigator");
        navigator.setMinimumSize(new Dimension(160, 100));
        navigator.add(zoneTitle(Texts.get("zone.navigator")), BorderLayout.NORTH);
        navigator.add(emptyState(Texts.get("zone.navigator.empty")), BorderLayout.CENTER);
    }

    private JPanel buildWorkArea() {
        CardLayout cards = new CardLayout();
        JPanel area = new JPanel(cards);
        area.setName("zone.workArea");
        area.add(emptyState(Texts.get("zone.workArea.empty")), "empty");
        area.add(workTabs, "tabs");
        workTabs.setName("zone.workArea.tabs");
        // finché non c'è nessuna scheda si vede un invito discreto, non un riquadro vuoto
        workTabs.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                cards.show(area, "tabs");
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
                cards.show(area, workTabs.getTabCount() == 0 ? "empty" : "tabs");
            }
        });
        return area;
    }

    private void buildSqlPanel() {
        sqlPanel.setName("zone.sqlPanel");
        sqlPanel.setMinimumSize(new Dimension(100, 0));
        sqlPanel.addTab(Texts.get("sqlPanel.log"), emptyState(Texts.get("sqlPanel.log.empty")));
        sqlPanel.addTab(Texts.get("sqlPanel.preview"), emptyState(Texts.get("sqlPanel.preview.empty")));
        sqlPanel.addTab(Texts.get("sqlPanel.messages"), emptyState(Texts.get("sqlPanel.messages.empty")));
    }

    private static JLabel zoneTitle(String text) {
        JLabel title = new JLabel(text);
        title.putClientProperty("FlatLaf.styleClass", "h4");
        title.setBorder(BorderFactory.createEmptyBorder(10, 14, 8, 14));
        return title;
    }

    private static JLabel emptyState(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        return label;
    }

    private JMenuBar buildMenuBar() {
        JMenu file = new JMenu(Texts.get("menu.file"));
        file.add(item("menu.file.importProfiles", connections::importProfiles));
        file.add(item("menu.file.exportProfiles", connections::exportProfiles));
        file.addSeparator();
        file.add(item("menu.file.settings", settings::edit));
        file.addSeparator();
        file.add(item("menu.file.exit", () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

        JMenu help = new JMenu(Texts.get("menu.help"));
        help.add(item("menu.help.about", () -> prompts.showInfo(Texts.get("about.title"),
                Texts.get("about.message", ProductInfo.NAME, ProductInfo.version()))));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(help);
        return bar;
    }

    private static JMenuItem item(String key, Runnable action) {
        JMenuItem item = new JMenuItem(Texts.get(key));
        item.setName(key);
        item.addActionListener(e -> action.run());
        return item;
    }

    /** Barra corta, con testo. I comandi non ancora realizzati sono disabilitati, non nascosti: la barra non cambia forma. */
    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        for (String key : TOOLBAR_KEYS) {
            JButton button = new JButton(Texts.get("toolbar." + key));
            button.setName("toolbar." + key);
            button.setFocusable(false);
            button.setEnabled(false);
            toolbarButtons.add(button);
            if (key.equals("run")) {
                bar.add(Box.createHorizontalStrut(18));
            }
            bar.add(button);
        }
        connectButton().setEnabled(true);
        connectButton().addActionListener(e -> connectOrDisconnect());
        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(24, 0));
        bar.setName("statusBar");
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        statusConnection.setName("status.connection");
        statusServer.setName("status.server");
        statusCatalog.setName("status.catalog");
        JPanel right = new JPanel(new BorderLayout(24, 0));
        right.add(statusServer, BorderLayout.WEST);
        right.add(statusCatalog, BorderLayout.EAST);
        bar.add(statusConnection, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ comportamento

    /**
     * «Connetti» apre l'elenco dei profili; con una connessione aperta lo stesso pulsante è «Disconnetti» (che chiede
     * conferma se ci sono schede aperte) e riporta alle tessere: così si cambia connessione, una per volta.
     */
    private void connectOrDisconnect() {
        if (connections.isConnected()) {
            connections.disconnect();
            return;
        }
        List<ConnectionProfile> profiles = connections.profiles();
        if (profiles.isEmpty()) {
            connections.newProfile();
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (ConnectionProfile profile : profiles) {
            JMenuItem entry = new JMenuItem(profile.name());
            entry.addActionListener(e -> connections.connect(profile));
            menu.add(entry);
        }
        menu.show(connectButton(), 0, connectButton().getHeight());
    }

    @Override
    public void showHome(List<ConnectionProfile> profiles) {
        workTabs.removeAll(); // le schede appartengono alla connessione che si è chiusa
        home.setProfiles(profiles);
        show(Screen.HOME);
        connectButton().setText(Texts.get("toolbar.connect"));
        connectButton().setEnabled(true);
        statusConnection.setText(Texts.get("status.disconnected"));
        statusServer.setText("");
        statusCatalog.setText("");
    }

    @Override
    public void showConnecting(ConnectionProfile profile) {
        workTabs.removeAll();
        connecting.setProfile(profile);
        show(Screen.CONNECTING);
        connectButton().setEnabled(false);
        statusConnection.setText(Texts.get("status.connecting", profile.name()));
        statusServer.setText("");
        statusCatalog.setText("");
    }

    @Override
    public void showConnected(Session session, String catalog) {
        show(Screen.WORKSPACE);
        connectButton().setText(Texts.get("toolbar.disconnect"));
        connectButton().setEnabled(true);
        statusConnection.setText(Texts.get("status.connected", session.profile().name(), session.profile().address()));
        statusServer.setText(session.serverInfo().displayName());
        statusCatalog.setText(catalog == null || catalog.isEmpty()
                ? Texts.get("status.catalog.none") : Texts.get("status.catalog", catalog));
    }

    @Override
    public int openTabCount() {
        return workTabs.getTabCount();
    }

    private void show(Screen next) {
        screen = next;
        screens.show(screenHost, next.name());
    }

    // ------------------------------------------------------------------ accesso per i test e per gli step successivi

    public Screen screen() {
        return screen;
    }

    public HomePanel homePanel() {
        return home;
    }

    public ConnectingPanel connectingPanel() {
        return connecting;
    }

    public JPanel navigatorZone() {
        return navigator;
    }

    public JTabbedPane workTabs() {
        return workTabs;
    }

    public JTabbedPane sqlPanel() {
        return sqlPanel;
    }

    public JSplitPane sqlPanelSplit() {
        return verticalSplit;
    }

    public List<JButton> toolbarButtons() {
        return List.copyOf(toolbarButtons);
    }

    public final JButton connectButton() {
        return toolbarButtons.get(0);
    }

    public JLabel statusConnection() {
        return statusConnection;
    }

    public JLabel statusServer() {
        return statusServer;
    }

    public JLabel statusCatalog() {
        return statusCatalog;
    }
}
