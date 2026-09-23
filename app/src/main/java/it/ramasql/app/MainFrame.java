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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
import javax.swing.BoxLayout;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import it.ramasql.app.connection.ConnectingPanel;
import it.ramasql.app.connection.ConnectionController;
import it.ramasql.app.connection.HomePanel;
import it.ramasql.app.connection.ShellView;
import it.ramasql.app.editor.SqlEditor;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.navigator.NavigatorPanel;
import it.ramasql.app.tableeditor.TableEditor;
import it.ramasql.app.workspace.MetadataCompletionSource;
import it.ramasql.app.workspace.WorkTabs;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.app.settings.SettingsController;
import it.ramasql.app.sqlpanel.SqlPanel;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Dot;
import it.ramasql.app.theme.Pill;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.app.workspace.SessionWorkspace;
import it.ramasql.app.workspace.SwingWorkspacePrompts;
import it.ramasql.app.workspace.WorkspacePrompts;
import it.ramasql.core.ProductInfo;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.SqlLog;

/**
 * Finestra principale, <strong>a tre zone non riconfigurabili</strong> ({@code DESIGN.md} §2): <em>Navigatore</em> a
 * sinistra, <em>Area di lavoro</em> a schede al centro, <em>Pannello SQL</em> in basso (Registro / Anteprima /
 * Messaggi; ridimensionabile e comprimibile, ma sempre presente). Sopra, una barra strumenti corta con testo;
 * sotto, la barra di stato con connessione, server e catalogo. Senza connessione, al posto delle tre zone c'è la
 * schermata iniziale a tessere.
 * <p>Alla connessione nasce un {@link SessionWorkspace} (lettore dei metadati + esecutore SQL + pipeline d'anteprima),
 * a cui si collegano navigatore e pannello SQL; alla disconnessione (o alla chiusura della finestra) si chiude. Il
 * registro SQL ({@link SqlLog}) è unico per la finestra e resta tra una connessione e l'altra.
 */
public final class MainFrame extends JFrame implements ShellView {

    private static final long serialVersionUID = 1L;

    /** Le schermate che si alternano al centro della finestra. */
    public enum Screen { HOME, CONNECTING, WORKSPACE }

    /** I dieci pulsanti della barra strumenti, nell'ordine di {@code DESIGN.md} §2. */
    private static final String[] TOOLBAR_KEYS = {"connect", "newQuery", "newVisualQuery", "newTable", "newView",
            "import", "export", "erModel", "run", "stop"};
    /** Le icone dei pulsanti, nello stesso ordine ({@code DESIGN-SYSTEM.md} §2). */
    private static final String[] TOOLBAR_ICONS = {AppIcons.CONNECT, AppIcons.NEW_QUERY, AppIcons.VISUAL_QUERY,
            AppIcons.NEW_TABLE, AppIcons.NEW_VIEW, AppIcons.IMPORT, AppIcons.EXPORT, AppIcons.ER_MODEL, AppIcons.RUN,
            AppIcons.STOP};
    /** Dove comincia un gruppo (§3.1): Connessione · Crea · Dati · a destra Esegui e Interrompi. */
    private static final List<String> GROUP_STARTS = List.of("newQuery", "import");

    private final transient ConnectionController connections;
    private final transient SettingsController settings;
    private final transient Prompts prompts;
    private final transient WorkspacePrompts workspacePrompts;
    private final transient SqlLog sqlLog = new SqlLog();
    private transient SessionWorkspace workspace;

    private final CardLayout screens = new CardLayout();
    private final JPanel screenHost = new JPanel(screens);
    private final HomePanel home;
    private final ConnectingPanel connecting;
    private final JPanel navigator = new JPanel(new BorderLayout());
    private final CardLayout navigatorCards = new CardLayout();
    private final JPanel navigatorHost = new JPanel(navigatorCards);
    private final NavigatorPanel navigatorPanel = new NavigatorPanel();
    private final JTabbedPane workTabs = new JTabbedPane();
    private final transient WorkTabs tabs;
    private final SqlPanel sqlPanel;
    private final JSplitPane horizontalSplit;
    private final JSplitPane verticalSplit;
    private final List<JButton> toolbarButtons = new ArrayList<>();
    private final JLabel statusConnection = new JLabel();
    private final Pill statusServer = Pill.dotted("", Tokens.TEXT_TERTIARY);
    private final JLabel statusCatalog = new JLabel();
    private Screen screen = Screen.HOME;

    /** Con le finestre vere dell'area di lavoro. */
    public MainFrame(ConnectionController connections, SettingsController settings, Prompts prompts) {
        this(connections, settings, prompts, null);
    }

    /** @param workspacePrompts finestre dell'area di lavoro; {@code null} = quelle vere */
    public MainFrame(ConnectionController connections, SettingsController settings, Prompts prompts,
            WorkspacePrompts workspacePrompts) {
        super(ProductInfo.title());
        this.connections = connections;
        this.settings = settings;
        this.prompts = prompts;
        this.workspacePrompts = workspacePrompts != null ? workspacePrompts
                : new SwingWorkspacePrompts(() -> this, () -> settings.settings().workDirectory());
        this.sqlPanel = new SqlPanel(sqlLog, this.workspacePrompts, navigatorPanel::selectedCatalog);
        this.tabs = new WorkTabs(workTabs, this.workspacePrompts);
        navigatorPanel.setOnOpenTable(this::openDataEntry);
        navigatorPanel.setOnDesignTable((catalog, table) -> openTableEditor(catalog, table));
        navigatorPanel.setOnNewTable(catalog -> openTableEditor(catalog, null));
        navigatorPanel.setOnOpenView(this::openView);
        settings.addListener(changed -> {
            if (workspace != null) {
                workspace.setRowLimit(changed.rowLimit());
            }
        });
        setIconImages(AppIcon.images());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWorkspace();
                connections.shutdown();
            }
        });

        home = new HomePanel(connections);
        connecting = new ConnectingPanel(connections);

        buildNavigator();
        JPanel workArea = buildWorkArea();
        horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigator, workArea);
        horizontalSplit.setDividerLocation(300);
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
        navigator.setBackground(Tokens.BG_WINDOW);
        navigatorHost.setOpaque(false);
        navigator.setMinimumSize(new Dimension(160, 100));
        navigator.add(zoneTitle(Texts.get("zone.navigator")), BorderLayout.NORTH);
        navigatorHost.add(emptyState(Texts.get("zone.navigator.empty"), null, AppIcons.TREE_CATALOG), "empty");
        navigatorHost.add(navigatorPanel, "tree");
        navigatorCards.show(navigatorHost, "empty");
        navigator.add(navigatorHost, BorderLayout.CENTER);
    }

    private JPanel buildWorkArea() {
        CardLayout cards = new CardLayout();
        JPanel area = new JPanel(cards);
        area.setName("zone.workArea");
        area.setBackground(Tokens.BG_SURFACE);
        area.add(emptyState(Texts.get("zone.workArea.empty"), Texts.get("zone.workArea.hint"), AppIcons.NEW_QUERY),
                "empty");
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

    private static JLabel zoneTitle(String text) {
        JLabel title = Styles.text(new JLabel(text), "emphasis", Tokens.TEXT_SECONDARY);
        title.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_16)));
        return title;
    }

    /** Stato vuoto: un'icona grande e discreta, una frase e (facoltativo) un suggerimento su cosa fare. */
    private static JPanel emptyState(String text, String hint, String icon) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        JLabel image = new JLabel(AppIcons.get(icon, 40));
        JLabel label = Styles.text(new JLabel(text, SwingConstants.CENTER), "title", Tokens.TEXT_PRIMARY);
        image.setAlignmentX(CENTER_ALIGNMENT);
        label.setAlignmentX(CENTER_ALIGNMENT);
        box.add(image);
        box.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_12)));
        box.add(label);
        if (hint != null) {
            JLabel sub = new JLabel(hint, SwingConstants.CENTER);
            sub.setForeground(Tokens.TEXT_SECONDARY);
            sub.setAlignmentX(CENTER_ALIGNMENT);
            box.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)));
            box.add(sub);
        }
        JPanel host = new JPanel(new GridBagLayout());
        host.setOpaque(false);
        host.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_16)));
        host.add(box);
        return host;
    }

    private JMenuBar buildMenuBar() {
        JMenu file = new JMenu(Texts.get("menu.file"));
        file.setName("menu.file");
        file.add(item("menu.file.importProfiles", AppIcons.MENU_IMPORT_PROFILES, null, connections::importProfiles));
        file.add(item("menu.file.exportProfiles", AppIcons.MENU_EXPORT_PROFILES, null, connections::exportProfiles));
        file.addSeparator();
        file.add(item("menu.file.settings", AppIcons.MENU_SETTINGS,
                KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK), settings::edit));
        file.addSeparator();
        file.add(item("menu.file.exit", AppIcons.MENU_EXIT, KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK),
                () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

        JMenu help = new JMenu(Texts.get("menu.help"));
        help.setName("menu.help");
        help.add(item("menu.help.about", AppIcons.MENU_ABOUT, null, () -> prompts.showInfo(Texts.get("about.title"),
                Texts.get("about.message", ProductInfo.NAME, ProductInfo.version()))));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(help);
        return bar;
    }

    /** Voce di menu: icona 16 a sinistra, scorciatoia in grigio a destra (la disegna il tema). */
    private static JMenuItem item(String key, String icon, KeyStroke accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(Texts.get(key), AppIcons.small(icon));
        item.setName(key);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    /**
     * Barra corta, con icone e testo, a gruppi separati da spazio (non da linee): <em>Connessione</em> ·
     * <em>Crea</em> · <em>Dati</em> · a destra <em>Esegui</em> (primario pieno) e <em>Interrompi</em> (contorno
     * rosso). I comandi non ancora realizzati sono disabilitati, non nascosti — la barra non cambia forma — e lo
     * dicono nel suggerimento.
     */
    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setName("toolbar");
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12))));
        // Esegui e Interrompi stanno in un pannello: sono pulsanti veri (pieno / contorno), non «da barra»
        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setOpaque(false);
        for (int i = 0; i < TOOLBAR_KEYS.length; i++) {
            String key = TOOLBAR_KEYS[i];
            JButton button = new JButton(Texts.get("toolbar." + key), AppIcons.toolbar(TOOLBAR_ICONS[i]));
            button.setName("toolbar." + key);
            button.setFocusable(false);
            button.setEnabled(false);
            button.setIconTextGap(Tokens.px(6));
            button.setToolTipText(Texts.get("toolbar.comingSoon"));
            toolbarButtons.add(button);
            if (key.equals("run")) {
                Styles.primary(button, Tokens.ACCENT);
                button.setIcon(AppIcons.onAccent(AppIcons.toolbar(AppIcons.RUN)));
                button.setDisabledIcon(AppIcons.toolbar(AppIcons.RUN).getDisabledIcon());
                actions.add(button);
            } else if (key.equals("stop")) {
                Styles.outline(button, Tokens.DANGER, Tokens.DANGER_TINT);
                actions.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
                actions.add(button);
            } else {
                if (GROUP_STARTS.contains(key)) {
                    bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_12)));
                }
                Styles.text(button, "smallText");
                bar.add(button);
            }
        }
        bar.add(Box.createHorizontalGlue());
        bar.add(actions);
        connectButton().setEnabled(true);
        connectButton().setToolTipText(Texts.get("toolbar.connect.tooltip"));
        connectButton().addActionListener(e -> connectOrDisconnect());
        button("newQuery").addActionListener(e -> openSqlEditor());
        button("newTable").addActionListener(e -> {
            String catalog = navigatorPanel.selectedCatalog();
            if (catalog != null && !catalog.isBlank()) {
                openTableEditor(catalog, null);
            }
        });
        button("run").addActionListener(e -> {
            SqlEditor editor = tabs.selectedEditor();
            if (editor != null) {
                editor.runCurrent();
            }
        });
        button("stop").addActionListener(e -> {
            SqlEditor editor = tabs.selectedEditor();
            if (editor != null) {
                editor.cancelRun();
            }
        });
        workTabs.addChangeListener(e -> updateToolbar());
        return bar;
    }

    /**
     * Barra di stato (§3.8): a sinistra il punto dello stato e «Connesso a <em>nome</em>»; a destra la pillola del
     * server (petrolio MariaDB, arancio MySQL) con la versione e il catalogo corrente con l'icona del cilindro.
     */
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_24), 0));
        bar.setName("statusBar");
        bar.setBackground(Tokens.BG_WINDOW);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_16))));
        statusConnection.setName("status.connection");
        statusConnection.setIconTextGap(Tokens.px(4));
        statusServer.setName("status.server");
        statusCatalog.setName("status.catalog");
        statusCatalog.setIcon(AppIcons.treeCatalog());
        statusCatalog.setIconTextGap(Tokens.px(6));
        statusCatalog.setForeground(Tokens.TEXT_SECONDARY);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.px(Tokens.SPACE_16), 0));
        right.setOpaque(false);
        right.add(statusServer);
        right.add(statusCatalog);
        bar.add(statusConnection, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** La pillola del server: colore del tipo, testo della versione; nascosta senza connessione. */
    private void setServerPill(String text, Color color) {
        statusServer.setText(text);
        statusServer.setDotColor(color);
        statusServer.setVisible(!text.isEmpty());
    }

    private void setStatusConnection(String text, Color dot) {
        statusConnection.setText(text);
        statusConnection.setIcon(Dot.status(dot));
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
            JMenuItem entry = new JMenuItem(profile.name(), AppIcons.treeServer());
            entry.setToolTipText(profile.address());
            entry.addActionListener(e -> connections.connect(profile));
            menu.add(entry);
        }
        menu.show(connectButton(), 0, connectButton().getHeight());
    }

    @Override
    public void showHome(List<ConnectionProfile> profiles) {
        closeWorkspace();
        tabs.closeAllSilently(); // le schede appartengono alla connessione che si è chiusa
        home.setProfiles(profiles);
        show(Screen.HOME);
        connectButton().setText(Texts.get("toolbar.connect"));
        connectButton().setIcon(AppIcons.toolbar(AppIcons.CONNECT));
        connectButton().setToolTipText(Texts.get("toolbar.connect.tooltip"));
        connectButton().setEnabled(true);
        setStatusConnection(Texts.get("status.disconnected"), Tokens.TEXT_TERTIARY);
        setServerPill("", Tokens.TEXT_TERTIARY);
        statusCatalog.setText("");
        statusCatalog.setVisible(false);
        updateToolbar();
    }

    @Override
    public void showConnecting(ConnectionProfile profile) {
        closeWorkspace();
        tabs.closeAllSilently();
        connecting.setProfile(profile);
        show(Screen.CONNECTING);
        connectButton().setEnabled(false);
        setStatusConnection(Texts.get("status.connecting", profile.name()), Tokens.WARNING);
        setServerPill("", Tokens.TEXT_TERTIARY);
        statusCatalog.setText("");
        statusCatalog.setVisible(false);
    }

    @Override
    public void showConnected(Session session, String catalog) {
        openWorkspace(session);
        show(Screen.WORKSPACE);
        connectButton().setText(Texts.get("toolbar.disconnect"));
        connectButton().setIcon(AppIcons.toolbar(AppIcons.DISCONNECT));
        connectButton().setToolTipText(Texts.get("toolbar.disconnect.tooltip"));
        connectButton().setEnabled(true);
        setStatusConnection(Texts.get("status.connected", session.profile().name(), session.profile().address()),
                Tokens.SUCCESS);
        setServerPill(session.serverInfo().displayName(),
                session.serverInfo().isMariaDb() ? Tokens.SERVER_MARIADB : Tokens.SERVER_MYSQL);
        statusCatalog.setText(catalog == null || catalog.isEmpty()
                ? Texts.get("status.catalog.none") : Texts.get("status.catalog", catalog));
        statusCatalog.setVisible(true);
        updateToolbar();
    }

    @Override
    public int openTabCount() {
        return workTabs.getTabCount();
    }

    // ---------------------------------------------------------------- schede dell'area di lavoro

    /** Le schede aperte (data-entry, editor SQL, editor di tabelle). */
    public WorkTabs tabs() {
        return tabs;
    }

    /** Apre il data-entry di una tabella già letta dai metadati. */
    public DataGrid openDataEntry(String catalog, TableDef table) {
        if (workspace == null) {
            return null;
        }
        DataGrid grid = tabs.openDataEntry(workspace, table, settings.settings().rowLimit(),
                workspacePrompts.gridPrompts());
        updateToolbar();
        return grid;
    }

    /** Apre una vista in sola lettura. */
    public DataGrid openView(NavigatorPanel.ViewToOpen view) {
        if (workspace == null) {
            return null;
        }
        DataGrid grid = tabs.openView(workspace, view.catalog(), view.name(), view.columns(),
                settings.settings().rowLimit(), workspacePrompts.gridPrompts());
        updateToolbar();
        return grid;
    }

    /** Apre l'editor di struttura: {@code table} nullo = tabella nuova nel catalogo. */
    public TableEditor openTableEditor(String catalog, TableDef table) {
        if (workspace == null) {
            return null;
        }
        TableEditor editor = tabs.openTableEditor(workspace, table, catalog,
                workspacePrompts.tableEditorPrompts());
        updateToolbar();
        return editor;
    }

    /** Apre una scheda «Query N» con l'editor SQL. */
    public SqlEditor openSqlEditor() {
        if (workspace == null) {
            return null;
        }
        SqlEditor editor = tabs.openSqlEditor(workspace, settings.settings().rowLimit(),
                workspacePrompts.editorPrompts(), workspacePrompts.gridPrompts(),
                new MetadataCompletionSource(workspace.reader(), navigatorPanel::selectedCatalog));
        updateToolbar();
        return editor;
    }

    /** Lettore dei metadati, esecutore e pipeline per la sessione appena aperta; il navigatore inizia a leggere. */
    private void openWorkspace(Session session) {
        closeWorkspace();
        workspace = SessionWorkspace.open(session, sqlLog, settings.settings().rowLimit(), workspacePrompts, sqlPanel);
        navigatorPanel.attach(workspace);
        navigatorCards.show(navigatorHost, "tree");
    }

    /** Chiude esecutore e navigatore della connessione che finisce (la sessione la chiude il controller). */
    private void closeWorkspace() {
        if (workspace == null) {
            return;
        }
        navigatorPanel.detach();
        navigatorCards.show(navigatorHost, "empty");
        workspace.close();
        workspace = null;
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

    public SqlPanel sqlPanel() {
        return sqlPanel;
    }

    public NavigatorPanel navigator() {
        return navigatorPanel;
    }

    /** Ciò che vive con la connessione aperta; {@code null} se non si è connessi. */
    public SessionWorkspace workspace() {
        return workspace;
    }

    /** Il registro SQL della finestra. */
    public SqlLog sqlLog() {
        return sqlLog;
    }

    public JSplitPane sqlPanelSplit() {
        return verticalSplit;
    }

    public List<JButton> toolbarButtons() {
        return List.copyOf(toolbarButtons);
    }

    /** Il pulsante della barra con quella chiave («newQuery», «run»…). */
    public JButton button(String key) {
        for (JButton b : toolbarButtons) {
            if (("toolbar." + key).equals(b.getName())) {
                return b;
            }
        }
        throw new IllegalArgumentException("pulsante assente nella barra: " + key);
    }

    /**
     * Abilita i pulsanti che ora hanno senso: «Nuova query» e «Nuova tabella» appena c'è una connessione, «Esegui» e
     * «Interrompi» solo con un editor SQL davanti (e «Interrompi» solo mentre qualcosa sta girando). Gli altri restano
     * spenti con il loro «Arriva in una prossima versione» finché il loro step non c'è.
     */
    public void updateToolbar() {
        boolean connected = workspace != null;
        enable(button("newQuery"), connected, Texts.get("toolbar.newQuery.tooltip"));
        enable(button("newTable"), connected && navigatorPanel.selectedCatalog() != null,
                Texts.get("toolbar.newTable.tooltip"));
        SqlEditor editor = tabs.selectedEditor();
        enable(button("run"), editor != null && !editor.isRunning(), Texts.get("toolbar.run.tooltip"));
        enable(button("stop"), editor != null && editor.isRunning(), Texts.get("toolbar.stop.tooltip"));
    }

    private static void enable(JButton button, boolean on, String tooltip) {
        button.setEnabled(on);
        button.setToolTipText(on ? tooltip : Texts.get("toolbar.comingSoon"));
    }

    public final JButton connectButton() {
        return toolbarButtons.get(0);
    }

    public JLabel statusConnection() {
        return statusConnection;
    }

    /** La pillola del server nella barra di stato (il testo è il nome leggibile, es. «MariaDB 11.5.2»). */
    public JLabel statusServer() {
        return statusServer;
    }

    public JLabel statusCatalog() {
        return statusCatalog;
    }
}
