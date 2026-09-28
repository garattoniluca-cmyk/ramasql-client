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
import it.ramasql.app.pipeline.PipelineView;
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
    private transient it.ramasql.core.connection.ViewSourceStore viewSources;

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
        navigatorPanel.setOnEditView(this::editView);
        navigatorPanel.setOnImportData(this::openImport);
        navigatorPanel.setOnExport(this::openDump);
        navigatorPanel.setOnSelection(this::updateToolbar);
        settings.addListener(changed -> {
            if (workspace != null) {
                workspace.setRowLimit(changed.rowLimit());
            }
        });
        setIconImages(AppIcon.images());
        // la finestra si chiude solo da requestExit: con DISPOSE_ON_CLOSE si chiuderebbe anche scegliendo «Resta»
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestExit();
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
        // menu, barra, navigatore e pannello SQL: i componenti con un nome ricevono <nome>.tooltip (ADR-020)
        it.ramasql.app.theme.Tips.fromNames(getRootPane());

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
                // ogni scheda nuova: i componenti con un nome ricevono il suggerimento <nome>.tooltip (ADR-020)
                it.ramasql.app.theme.Tips.fromNames(e.getChild());
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
        JMenuItem openModel = item("menu.file.openModel", AppIcons.ER_MODEL, null, this::openErModelFile);
        openModel.setToolTipText(Texts.get("menu.file.openModel.tooltip"));
        file.add(openModel);
        file.addSeparator();
        file.add(item("menu.file.settings", AppIcons.MENU_SETTINGS,
                KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK), settings::edit));
        file.addSeparator();
        file.add(item("menu.file.exit", AppIcons.MENU_EXIT, KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK),
                () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

        JMenu help = new JMenu(Texts.get("menu.help"));
        help.setName("menu.help");
        JMenuItem guide = item("menu.help.guide", AppIcons.MENU_ABOUT, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                () -> new GuideDialog(this).setVisible(true));
        guide.setToolTipText(Texts.get("menu.help.guide.tooltip"));
        help.add(guide);
        JMenuItem about = item("menu.help.about", AppIcons.MENU_ABOUT, null, () -> new AboutDialog(this).setVisible(true));
        about.setToolTipText(Texts.get("menu.help.about.tooltip"));
        help.add(about);

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
        button("newVisualQuery").addActionListener(e -> {
            String catalog = navigatorPanel.selectedCatalog();
            if (catalog != null && !catalog.isBlank()) {
                openVisualQuery(catalog);
            }
        });
        button("newView").addActionListener(e -> {
            String catalog = navigatorPanel.selectedCatalog();
            if (catalog != null && !catalog.isBlank()) {
                newView(catalog);
            }
        });
        button("newTable").addActionListener(e -> {
            String catalog = navigatorPanel.selectedCatalog();
            if (catalog != null && !catalog.isBlank()) {
                openTableEditor(catalog, null);
            }
        });
        button("import").addActionListener(e -> showImportMenu());
        button("erModel").addActionListener(e -> {
            JPopupMenu menu = erMenu();
            menu.show(button("erModel"), 0, button("erModel").getHeight());
        });
        button("export").addActionListener(e -> openDump(navigatorPanel.selectedCatalog(),
                navigatorPanel.selectedObjectName()));
        button("run").addActionListener(e -> {
            SqlEditor editor = tabs.selectedEditor();
            if (editor != null) {
                editor.runCurrent();
            } else if (tabs.selectedVisualQuery() != null) {
                tabs.selectedVisualQuery().run();
            }
        });
        button("stop").addActionListener(e -> {
            SqlEditor editor = tabs.selectedEditor();
            if (editor != null) {
                editor.cancelRun();
            } else if (tabs.selectedVisualQuery() != null) {
                tabs.selectedVisualQuery().cancelRun();
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
        DataGrid grid;
        try {
            grid = tabs.openDataEntry(workspace, table, settings.settings().rowLimit(),
                    workspacePrompts.gridPrompts());
        } catch (RuntimeException e) {
            // la prima pagina non si è letta (connessione caduta, permessi, tabella sparita): si dice e basta
            sqlPanel.message(PipelineView.MessageKind.ERROR, Texts.get("grid.read.failed", table.name(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return null;
        }
        updateToolbar();
        return grid;
    }

    /** Apre una vista in sola lettura. */
    public DataGrid openView(NavigatorPanel.ViewToOpen view) {
        if (workspace == null) {
            return null;
        }
        if (view.columns().isEmpty()) {
            probeView(view.catalog(), view.name());
            return null;
        }
        DataGrid grid;
        try {
            grid = tabs.openView(workspace, view.catalog(), view.name(), view.columns(),
                    settings.settings().rowLimit(), workspacePrompts.gridPrompts());
        } catch (RuntimeException e) {
            sqlPanel.message(PipelineView.MessageKind.ERROR, Texts.get("grid.read.failed", view.name(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return null;
        }
        updateToolbar();
        return grid;
    }

    /**
     * Una vista di cui il server non restituisce le colonne: la si legge (una riga, dall'esecutore: la lettura finisce
     * nel registro come quelle della griglia) per mostrare l'errore vero del server, con la spiegazione in italiano —
     * tipicamente 1356, «la vista usa tabelle o colonne che non esistono più».
     */
    private void probeView(String catalog, String view) {
        it.ramasql.core.exec.SqlScript probe = it.ramasql.core.exec.SqlScript.of(Texts.get("grid.read.title", view),
                it.ramasql.core.exec.SqlOrigin.GRID.label(),
                "SELECT * FROM " + it.ramasql.core.sqlgen.SqlIdentifiers.qualified(catalog, view) + " LIMIT 1");
        SessionWorkspace ws = workspace;
        // fuori dall'EDT: l'esecutore può essere occupato da una query lunga di un'altra scheda
        ws.executor().submit(probe, null, 1).whenComplete((result, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (ws != workspace) {
                return;   // la connessione è cambiata nel frattempo
            }
            var failure = result == null ? java.util.Optional.<it.ramasql.core.exec.StatementResult>empty()
                    : result.results().stream().filter(r -> !r.isOk()).findFirst();
            if (failure.isPresent() && failure.get().error() != null) {
                var e = failure.get().error();
                // prima la spiegazione in italiano, sotto il messaggio originale del server; e la scheda Messaggi davanti
                String spiegazione = it.ramasql.app.editor.ErrorExplainer.explain(e.code()).orElse(e.message());
                sqlPanel.message(PipelineView.MessageKind.ERROR, Texts.get("view.read.failed", view, spiegazione)
                        + "\n" + Texts.get("error.serverLine", e.code(), e.message()));
                sqlPanel.setSelectedIndex(2);
                return;
            }
            // la lettura riesce: la vista è tornata valida (le colonne in cache erano vecchie); si rilegge e si apre
            ws.reader().invalidate(catalog, view);
            try {
                java.util.List<it.ramasql.core.metadata.ColumnDef> columns = ws.reader().viewColumns(catalog, view);
                if (!columns.isEmpty()) {
                    openView(new NavigatorPanel.ViewToOpen(catalog, view, columns));
                    return;
                }
            } catch (java.sql.SQLException ignored) {
                // si dice sotto
            }
            sqlPanel.message(PipelineView.MessageKind.WARNING, Texts.get("view.noColumns", view));
        }));
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
        // mentre una query gira, «Esegui» si spegne e «Interrompi» si accende
        editor.addPropertyChangeListener(SqlEditor.PROPERTY_RUNNING, e -> updateToolbar());
        updateToolbar();
        return editor;
    }

    /**
     * Apre una scheda «Query visiva N» sul catalogo dato (Step 7). Gli avvisi del query builder vanno nella scheda
     * Messaggi del pannello SQL.
     */
    public it.ramasql.app.visual.VisualQueryTab openVisualQuery(String catalog) {
        if (workspace == null || catalog == null || catalog.isBlank()) {
            return null;
        }
        it.ramasql.app.visual.VisualQueryTab tab = tabs.openVisualQuery(workspace, catalog,
                settings.settings().rowLimit(), workspacePrompts.editorPrompts(), workspacePrompts.gridPrompts(),
                new MetadataCompletionSource(workspace.reader(), () -> catalog), this::queryBuilderAlert,
                viewSaver());
        watch(tab);
        updateToolbar();
        return tab;
    }

    // ---------------------------------------------------------------- viste (Step 8)

    /** Archivio dei sorgenti originali delle viste (lo imposta l'avvio del programma). */
    public void setViewSources(it.ramasql.core.connection.ViewSourceStore store) {
        this.viewSources = store;
    }

    public it.ramasql.core.connection.ViewSourceStore viewSources() {
        return viewSources;
    }

    /**
     * Collega una scheda visiva alla finestra: «Esegui»/«Interrompi» della barra seguono l'esecuzione; il catalogo della
     * barra di stato segue il {@code USE} della query visiva; una vista salvata compare nell'elenco di tutte le schede
     * visive.
     */
    private void watch(it.ramasql.app.visual.VisualQueryTab tab) {
        tab.addPropertyChangeListener(SqlEditor.PROPERTY_RUNNING, e -> {
            updateToolbar();
            if (Boolean.FALSE.equals(e.getNewValue())) {
                // la query visiva esegue «USE catalogo»: da qui in poi è il catalogo corrente della sessione (come il
                // «default schema» di Workbench), anche per le schede Editor SQL; la barra di stato lo dice
                statusCatalog.setText(Texts.get("status.catalog", tab.catalog()));
            }
        });
        tab.addPropertyChangeListener(it.ramasql.app.visual.VisualQueryTab.PROPERTY_VIEW_SAVED, e -> {
            statusCatalog.setText(Texts.get("status.catalog", tab.catalog()));   // anche il salvataggio fa «USE»
            // una «Nuova vista N» appena salvata diventa la scheda di quella vista (Modifica vista la ritrova)
            tabs.becomeViewEditor(tab, tab.catalog(), String.valueOf(e.getNewValue()));
            for (java.awt.Component c : workTabs.getComponents()) {
                if (c instanceof it.ramasql.app.visual.VisualQueryTab v) {
                    v.refreshObjects();
                }
            }
        });
    }

    // ---------------------------------------------------------------- importazione (Step 9)

    /** Il menu del pulsante «Importa»: dati da un file CSV o JSON (e, dallo Step 10, uno script SQL). */
    private void showImportMenu() {
        JPopupMenu menu = importMenu();
        menu.show(button("import"), 0, button("import").getHeight());
    }

    /** Il menu del pulsante «Importa», con le voci che valgono adesso (anche per i test). */
    public JPopupMenu importMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setName("import.menu");
        JMenuItem data = new JMenuItem(Texts.get("import.menu.data"), AppIcons.small(AppIcons.IMPORT));
        data.setName("import.menu.data");
        String dataCatalog = navigatorPanel.selectedCatalog();
        data.setEnabled(dataCatalog != null && !dataCatalog.isBlank());   // i dati vanno in un catalogo: prima lo si sceglie
        data.setToolTipText(Texts.get("import.menu.data.tooltip"));
        data.addActionListener(e -> openImport(navigatorPanel.selectedCatalog(), navigatorPanel.selectedTableName()));
        menu.add(data);
        JMenuItem script = new JMenuItem(Texts.get("import.menu.script"), AppIcons.small(AppIcons.IMPORT));
        script.setName("import.menu.script");
        script.setToolTipText(Texts.get("import.menu.script.tooltip"));
        script.addActionListener(e -> openScriptRun(navigatorPanel.selectedCatalog()));
        menu.add(script);
        return menu;
    }

    /** Apre la scheda «Esegui script SQL» (ripristino di un dump). */
    public it.ramasql.app.dump.ScriptRunTab openScriptRun(String catalog) {
        if (workspace == null) {
            return null;
        }
        it.ramasql.app.dump.ScriptRunTab tab = tabs.openScriptRun(workspace, catalog);
        updateToolbar();
        return tab;
    }

    /** Apre la procedura guidata «Esporta / Dump», con catalogo e oggetto proposti (anche {@code null}). */
    public it.ramasql.app.dump.DumpWizard openDump(String catalog, String object) {
        if (workspace == null) {
            return null;
        }
        it.ramasql.app.dump.DumpWizard w = tabs.openDump(workspace, catalog, object);
        updateToolbar();
        return w;
    }

    /** Apre la procedura guidata «Importa dati» sul catalogo, con la tabella proposta (anche {@code null}). */
    public it.ramasql.app.importer.ImportWizard openImport(String catalog, String table) {
        if (workspace == null || catalog == null || catalog.isBlank()) {
            return null;
        }
        it.ramasql.app.importer.ImportWizard wizard = tabs.openImport(workspace, catalog, table,
                navigatorPanel::openTable);
        updateToolbar();
        return wizard;
    }

    // ---------------------------------------------------------------- modello ER (Step 11)

    private final List<it.ramasql.app.er.ErModelWindow> erWindows = new ArrayList<>();

    /** Il menu del pulsante «Modello ER»: nuovo modello dal catalogo scelto, oppure un modello salvato. */
    public JPopupMenu erMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setName("er.menu");
        String catalog = navigatorPanel.selectedCatalog();
        JMenuItem fresh = new JMenuItem(catalog == null ? Texts.get("er.menu.new.noCatalog")
                : Texts.get("er.menu.new", catalog), AppIcons.small(AppIcons.ER_MODEL));
        fresh.setName("er.menu.new");
        fresh.setToolTipText(Texts.get("er.menu.new.tooltip"));
        fresh.setEnabled(workspace != null && catalog != null && !catalog.isBlank());
        fresh.addActionListener(e -> newErModel(catalog));
        menu.add(fresh);
        JMenuItem open = new JMenuItem(Texts.get("er.menu.open"), AppIcons.small(AppIcons.ER_MODEL));
        open.setName("er.menu.open");
        open.setToolTipText(Texts.get("er.menu.open.tooltip"));
        open.addActionListener(e -> openErModelFile());
        menu.add(open);
        return menu;
    }

    /** Ciò che una finestra del modello chiede a questa. */
    private final transient it.ramasql.app.er.ErContext erContext = new it.ramasql.app.er.ErContext() {
        @Override
        public it.ramasql.core.metadata.MetadataReader reader() {
            return workspace == null ? null : workspace.reader();
        }

        @Override
        public String serverAddress() {
            return workspace == null ? null : workspace.session().profile().address();
        }

        @Override
        public String askLabel(String relationship, String current) {
            return workspacePrompts.askRelationshipLabel(relationship, current);
        }

        @Override
        public void openTableEditor(String catalog, String table) {
            if (workspace == null) {
                return;
            }
            navigatorPanel.designTable(catalog, table);
            toFront();
        }

        @Override
        public it.ramasql.app.workspace.FilePrompts files() {
            return workspacePrompts.files();
        }

        @Override
        public CloseChoice askSaveOnClose(String modelName) {
            return switch (workspacePrompts.askPendingOnClose(Texts.get("er.close.question", modelName))) {
                case CONFIRM -> CloseChoice.SAVE;
                case DISCARD -> CloseChoice.DISCARD;
                case STAY -> CloseChoice.STAY;
            };
        }
    };

    /**
     * Uscita dal programma (X della finestra, Alt+F4, File → Esci): prima i modelli ER aperti (ciascuno chiede se
     * salvare; se si salva, l'uscita riprende a salvataggio finito), poi il lavoro in sospeso delle schede. Con
     * «Resta» non si esce.
     */
    public void requestExit() {
        for (it.ramasql.app.er.ErModelWindow w : erWindows()) {
            if (!w.closeIfAllowed(() -> javax.swing.SwingUtilities.invokeLater(this::requestExit))) {
                return;
            }
        }
        if (!tabs.confirmCloseAll()) {
            return;   // c'è lavoro in sospeso e l'utente ha scelto di restare
        }
        closeWorkspace();
        connections.shutdown();
        dispose();
    }

    /** Le finestre dei modelli aperte. */
    public List<it.ramasql.app.er.ErModelWindow> erWindows() {
        erWindows.removeIf(w -> !w.isDisplayable());
        return List.copyOf(erWindows);
    }

    /** Una finestra per il modello; si mostra se si vede la finestra principale (nei test resta nascosta). */
    public it.ramasql.app.er.ErModelWindow openErWindow(it.ramasql.model.ErModel model, java.nio.file.Path file) {
        it.ramasql.app.er.ErModelWindow w = new it.ramasql.app.er.ErModelWindow(model, file, erContext,
                getIconImages());
        erWindows.add(w);
        w.setLocationRelativeTo(this);
        if (isShowing()) {
            w.setVisible(true);
        }
        return w;
    }

    /** Stato della retroingegneria in corso (per i test). */
    private volatile boolean erLoading;

    public boolean isErLoading() {
        return erLoading;
    }

    /**
     * «Nuovo modello dal catalogo»: l'elenco delle tabelle in sottofondo, la scelta di quali mettere nel modello
     * ({@code DESIGN.md} §3.11), la retroingegneria in sottofondo, poi la disposizione automatica.
     */
    public void newErModel(String catalog) {
        if (workspace == null || catalog == null) {
            return;
        }
        it.ramasql.core.metadata.MetadataReader reader = workspace.reader();
        String server = workspace.session().profile().address();
        erLoading = true;
        new javax.swing.SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return reader.tables(catalog).stream().filter(t -> !t.isView())
                        .map(it.ramasql.core.metadata.TableSummary::name).toList();
            }

            @Override
            protected void done() {
                List<String> all;
                try {
                    all = get();
                } catch (Exception e) {
                    erLoading = false;
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    sqlPanel.message(PipelineView.MessageKind.ERROR, Texts.get("er.new.failed", catalog, t.getMessage()));
                    return;
                }
                List<String> chosen = all.isEmpty() ? all : workspacePrompts.chooseModelTables(catalog, all);
                if (chosen == null) {
                    erLoading = false;
                    sqlPanel.message(PipelineView.MessageKind.INFO, Texts.get("er.new.cancelled", catalog));
                    return;
                }
                // tutte le tabelle = il catalogo intero (Aggiorna dal database aggiungerà anche quelle nuove)
                reverseEngineer(reader, server, catalog, chosen.size() == all.size() ? List.of() : chosen);
            }
        }.execute();
    }

    private void reverseEngineer(it.ramasql.core.metadata.MetadataReader reader, String server, String catalog,
            List<String> tables) {
        sqlPanel.message(PipelineView.MessageKind.INFO, Texts.get("er.new.running", catalog));
        new javax.swing.SwingWorker<it.ramasql.model.ErModel, Void>() {
            @Override
            protected it.ramasql.model.ErModel doInBackground() throws Exception {
                return it.ramasql.model.ReverseEngineer.fromCatalog(reader, catalog, tables).withServer(server);
            }

            @Override
            protected void done() {
                erLoading = false;
                try {
                    it.ramasql.model.ErModel m = get();
                    it.ramasql.app.er.ErModelWindow w = openErWindow(m, null);
                    w.panel().autoLayout();
                    sqlPanel.message(PipelineView.MessageKind.SUCCESS, Texts.get("er.new.done", catalog,
                            m.entities().size(), m.physical().size()));
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    sqlPanel.message(PipelineView.MessageKind.ERROR, Texts.get("er.new.failed", catalog, t.getMessage()));
                }
            }
        }.execute();
    }

    /** «Apri modello ER…»: anche senza connessione. */
    public void openErModelFile() {
        java.nio.file.Path file = workspacePrompts.files().chooseToOpen(it.ramasql.app.workspace.FilePrompts.Purpose.MODEL);
        if (file != null) {
            openErModel(file);
        }
    }

    /** Apre un modello salvato (lettura del file in sottofondo); se è già aperto, porta avanti la sua finestra. */
    public void openErModel(java.nio.file.Path file) {
        for (it.ramasql.app.er.ErModelWindow w : erWindows()) {
            java.nio.file.Path open = w.panel().file();
            if (open != null && open.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())) {
                w.toFront();   // due finestre sullo stesso file: un salvataggio cancellerebbe l'altro
                return;
            }
        }
        erLoading = true;
        new javax.swing.SwingWorker<it.ramasql.model.ErModel, Void>() {
            @Override
            protected it.ramasql.model.ErModel doInBackground() throws Exception {
                return it.ramasql.model.ModelFile.read(file);
            }

            @Override
            protected void done() {
                erLoading = false;
                try {
                    openErWindow(get(), file);
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    workspacePrompts.files().showError(Texts.get("er.menu.open"), t.getMessage());
                }
            }
        }.execute();
    }

    private void queryBuilderAlert(String text) {
        sqlPanel.message(PipelineView.MessageKind.WARNING, text);
    }

    private it.ramasql.app.visual.ViewSaver viewSaver() {
        return new it.ramasql.app.workspace.PipelineViewSaver(workspace.pipeline(), workspace.reader(), viewSources,
                workspace.session().profile().address(), sqlPanel);
    }

    /** «Nuova vista»: la query visiva in modalità vista, sul catalogo dato. */
    public it.ramasql.app.visual.VisualQueryTab newView(String catalog) {
        if (workspace == null || catalog == null || catalog.isBlank()) {
            return null;
        }
        it.ramasql.app.visual.VisualQueryTab tab = tabs.openViewEditor(workspace, catalog, null,
                settings.settings().rowLimit(), workspacePrompts.editorPrompts(), workspacePrompts.gridPrompts(),
                new MetadataCompletionSource(workspace.reader(), () -> catalog), this::queryBuilderAlert,
                viewSaver());
        watch(tab);
        updateToolbar();
        return tab;
    }

    /** Esito di «Modifica vista»: la scheda aperta e il livello con cui si è riaperta la vista. */
    public record ViewEditing(it.ramasql.app.visual.VisualQueryTab tab, it.ramasql.app.visual.ViewReopening reopening) {
    }

    /**
     * «Modifica vista» ({@code DESIGN.md} §3.8): riapre la vista con la strategia a tre livelli — sorgente originale
     * archiviato (se la definizione sul server non è cambiata), definizione del server normalizzata, oppure testo con
     * un avviso — nella query visiva in modalità vista; il salvataggio è un {@code CREATE OR REPLACE VIEW}.
     *
     * @return {@code null} se la vista non c'è (messaggio nel pannello)
     */
    public ViewEditing editView(String catalog, String view) {
        if (workspace == null) {
            return null;
        }
        it.ramasql.app.visual.VisualQueryTab aperta = tabs.findViewEditor(catalog, view);
        if (aperta != null) {
            // c'è già: la si riporta davanti così com'è (rileggere cancellerebbe il lavoro non salvato)
            tabs.select(aperta);
            return new ViewEditing(aperta, null);
        }
        java.util.Optional<it.ramasql.core.metadata.ViewDef> def;
        try {
            workspace.reader().invalidate(catalog, view);   // solo la vista: il resto del catalogo resta in cache
            def = workspace.reader().view(catalog, view);
        } catch (java.sql.SQLException e) {
            sqlPanel.message(PipelineView.MessageKind.ERROR, Texts.get("nav.load.error", e.getMessage()));
            return null;
        }
        if (def.isEmpty()) {
            sqlPanel.message(PipelineView.MessageKind.WARNING, Texts.get("nav.table.missing"));
            return null;
        }
        String definition = def.get().selectSql();
        java.util.Optional<String> source = viewSources == null ? java.util.Optional.empty()
                : viewSources.sourceFor(workspace.session().profile().address(), catalog, view, definition);
        it.ramasql.app.visual.ViewReopening reopening = it.ramasql.app.visual.ViewReopening.decide(source, definition,
                catalog);
        it.ramasql.app.visual.VisualQueryTab tab = tabs.openViewEditor(workspace, catalog, view,
                settings.settings().rowLimit(), workspacePrompts.editorPrompts(), workspacePrompts.gridPrompts(),
                new MetadataCompletionSource(workspace.reader(), () -> catalog), this::queryBuilderAlert,
                viewSaver());
        boolean disegnata = tab.setSql(reopening.sql());
        java.util.List<String> avvisi = new java.util.ArrayList<>();
        if (!disegnata) {   // non si disegna: la scheda resta sul testo con l'avviso del motivo
            avvisi.add(Texts.get("visual.view.textOnly", it.ramasql.app.visual.VisualReasons.of(reopening.sql(),
                    it.ramasql.qb.QbSql.check(reopening.sql()))));
        }
        // opzioni che la v1 non gestisce: salvando da qui (CREATE OR REPLACE) tornerebbero ai valori predefiniti
        it.ramasql.core.metadata.ViewDef d = def.get();
        java.util.List<String> opzioni = new java.util.ArrayList<>();
        if (!"NONE".equalsIgnoreCase(d.checkOption())) {
            opzioni.add("WITH " + d.checkOption() + " CHECK OPTION");
        }
        if ("INVOKER".equalsIgnoreCase(d.securityType())) {
            opzioni.add("SQL SECURITY INVOKER");
        }
        if (!opzioni.isEmpty()) {
            avvisi.add(Texts.get("visual.view.optionsLost", String.join(", ", opzioni)));
        }
        if (!avvisi.isEmpty()) {
            tab.notice(String.join("\n", avvisi));
        }
        tab.markViewUnchanged();   // appena riaperta: niente da salvare
        watch(tab);
        updateToolbar();
        return new ViewEditing(tab, reopening);
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
        enable(button("newQuery"), connected, Texts.get("toolbar.newQuery.tooltip"),
                Texts.get("toolbar.disabled.notConnected"));
        String catalog = navigatorPanel.selectedCatalog();
        enable(button("newTable"), connected && catalog != null && !catalog.isBlank(),
                Texts.get("toolbar.newTable.tooltip"),
                connected ? Texts.get("toolbar.disabled.noCatalog") : Texts.get("toolbar.disabled.notConnected"));
        enable(button("newVisualQuery"), connected && catalog != null && !catalog.isBlank(),
                Texts.get("toolbar.newVisualQuery.tooltip"),
                connected ? Texts.get("toolbar.disabled.noCatalog") : Texts.get("toolbar.disabled.notConnected"));
        enable(button("newView"), connected && catalog != null && !catalog.isBlank(),
                Texts.get("toolbar.newView.tooltip"),
                connected ? Texts.get("toolbar.disabled.noCatalog") : Texts.get("toolbar.disabled.notConnected"));
        enable(button("import"), connected, Texts.get("toolbar.import.tooltip"),
                Texts.get("toolbar.disabled.notConnected"));
        enable(button("export"), connected, Texts.get("toolbar.export.tooltip"),
                Texts.get("toolbar.disabled.notConnected"));
        enable(button("erModel"), connected, Texts.get("toolbar.erModel.tooltip"),
                Texts.get("toolbar.disabled.notConnected"));
        SqlEditor editor = tabs.selectedEditor();
        it.ramasql.app.visual.VisualQueryTab visual = tabs.selectedVisualQuery();
        boolean hasRunner = editor != null || visual != null;
        boolean running = editor != null ? editor.isRunning() : visual != null && visual.isRunning();
        enable(button("run"), hasRunner && !running, Texts.get("toolbar.run.tooltip"),
                !hasRunner ? Texts.get("toolbar.disabled.noEditor") : Texts.get("toolbar.disabled.running"));
        enable(button("stop"), hasRunner && running, Texts.get("toolbar.stop.tooltip"),
                Texts.get("toolbar.disabled.nothingRunning"));
    }

    /**
     * Accende o spegne un pulsante <b>già realizzato</b>: quando è spento il suggerimento dice <i>perché</i> non si
     * può usare adesso, non «Arriva in una prossima versione» (che sarebbe falso).
     */
    private static void enable(JButton button, boolean on, String tooltip, String whyDisabled) {
        button.setEnabled(on);
        button.setToolTipText(on ? tooltip : whyDisabled);
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
