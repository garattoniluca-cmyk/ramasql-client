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

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.formdev.flatlaf.icons.FlatSearchIcon;

import it.ramasql.app.Texts;
import it.ramasql.app.pipeline.PipelineView;
import it.ramasql.app.theme.Pill;
import it.ramasql.app.theme.Tokens;
import it.ramasql.app.workspace.SessionWorkspace;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.CollationInfo;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.ReadOnlyIndex;
import it.ramasql.core.metadata.MetadataListener;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.RoutineInfo;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.core.sqlgen.TreeScripts;

/**
 * Il <b>Navigatore</b> (DESIGN §3.2, come il Navigator di Workbench): albero Server → Cataloghi → Tabelle / Viste /
 * Routine → per ogni tabella Colonne, Indici, Chiavi esterne.
 * <ul>
 *   <li><b>Caricamento pigro</b> fuori dall'EDT: un catalogo si legge quando lo si apre, una tabella quando la si
 *       apre (nel frattempo il nodo «caricamento…»). Tutto passa dal {@link MetadataReader} (canale interno, non va
 *       nel registro).</li>
 *   <li>Si <b>aggiorna da solo</b>: ascolta le invalidazioni del lettore (dopo ogni DDL eseguito da
 *       {@code SqlExecutor}) e rilegge ciò che è aperto; F5 (o «Aggiorna») rilegge tutto. Espansione e selezione si
 *       conservano.</li>
 *   <li>Icone diverse per InnoDB e MyISAM ({@link NavigatorIcons}); cataloghi di sistema nascosti per default.</li>
 *   <li><b>Filtro</b> per nome in cima: tiene i cataloghi il cui nome contiene il testo e, nei cataloghi già aperti,
 *       le tabelle, viste e routine il cui nome lo contiene (un catalogo resta visibile se ha qualcosa che
 *       corrisponde). Senza distinguere maiuscole.</li>
 *   <li>Menu contestuale essenziale: ogni operazione che cambia il server produce uno {@link SqlScript} e lo consegna
 *       alla pipeline (anteprima → Esegui).</li>
 * </ul>
 * Tutti i metodi pubblici vanno chiamati sull'EDT.
 */
public final class NavigatorPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String CATALOGS_KEY = "catalogs";
    private static final String ROOT_KEY = "S";

    /** Contenuto letto di un catalogo. */
    private record CatalogContent(List<TableSummary> tables, List<RoutineInfo> routines, String routinesError) {
    }

    private final JTextField filter = new JTextField();
    private final DefaultTreeModel model = new DefaultTreeModel(null);
    /** Largo quanto il riquadro: i dettagli a destra restano in vista, i nomi lunghi si accorciano con «…». */
    private final JTree tree = new JTree(model) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };

    private transient SessionWorkspace workspace;
    private transient MetadataListener listener;
    private int sessionEpoch;
    private int stateEpoch;
    private int pendingTasks;
    private boolean rebuilding;
    private boolean showSystem;

    private List<CatalogInfo> catalogs;
    private final Map<String, CatalogContent> contents = new HashMap<>();
    private final Map<String, TableDef> tableDefs = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Set<String> expanded = new HashSet<>();
    private String selectedKey;

    public NavigatorPanel() {
        super(new BorderLayout());
        setName("navigator");
        filter.setName("navigator.filter");
        filter.putClientProperty("JTextField.placeholderText", Texts.get("nav.filter.placeholder"));
        filter.putClientProperty("JTextField.showClearButton", true);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });
        filter.putClientProperty("JTextField.leadingIcon", new FlatSearchIcon());
        filter.putClientProperty("FlatLaf.style", "arc: " + Tokens.RADIUS_CONTROL * 2);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_8),
                Tokens.px(Tokens.SPACE_12)));
        top.add(filter);
        setBackground(Tokens.BG_WINDOW);

        // DESIGN-SYSTEM §3.3: righe da 28 px, niente linee, selezione a pillola con tinta d'accento
        tree.setName("navigator.tree");
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(Tokens.px(Tokens.TREE_ROW_HEIGHT));
        tree.setBackground(Tokens.BG_WINDOW);
        tree.putClientProperty("JTree.lineStyle", "None");
        tree.putClientProperty("JTree.wideCellRenderer", true);
        tree.putClientProperty("FlatLaf.style", "selectionArc: " + Tokens.RADIUS_CONTROL * 2
                + "; selectionInsets: 0,6,0,6"
                + "; selectionBackground: " + Tokens.hex(Tokens.ACCENT_TINT)
                + "; selectionForeground: " + Tokens.hex(Tokens.TEXT_PRIMARY)
                + "; selectionInactiveBackground: " + Tokens.hex(Tokens.ACCENT_TINT)
                + "; selectionInactiveForeground: " + Tokens.hex(Tokens.TEXT_PRIMARY)
                + "; showCellFocusIndicator: false");
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                loadIfNeeded(nodeOf(event.getPath()));
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // niente da fare
            }
        });
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                if (!rebuilding) {
                    expanded.add(nodeOf(event.getPath()).key());
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                if (!rebuilding) {
                    expanded.remove(nodeOf(event.getPath()).key());
                }
            }
        });
        tree.addTreeSelectionListener(e -> {
            if (!rebuilding) {
                TreePath p = tree.getSelectionPath();
                selectedKey = p == null ? null : nodeOf(p).key();
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Tokens.BG_WINDOW);
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "navigator.refresh");
        getActionMap().put("navigator.refresh", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                refreshAll();
            }
        });
    }

    // ================================================================ sessione

    /** Collega il navigatore alla connessione appena aperta e inizia a leggere i cataloghi. */
    public void attach(SessionWorkspace ws) {
        detach();
        workspace = ws;
        sessionEpoch++;
        stateEpoch++;
        MetadataReader reader = ws.reader();
        int epoch = sessionEpoch;
        // le notifiche arrivano sul thread che invalida (di solito quello dell'esecutore): si torna sull'EDT
        listener = (catalog, table) -> SwingUtilities.invokeLater(() -> {
            if (epoch == sessionEpoch) {
                invalidated(catalog, table);
            }
        });
        reader.addListener(listener);
        expanded.add(ROOT_KEY);
        rebuild();
    }

    /** Scollega il navigatore (disconnessione): albero vuoto, letture in corso ignorate. */
    public void detach() {
        if (workspace != null && listener != null) {
            workspace.reader().removeListener(listener);
        }
        workspace = null;
        listener = null;
        sessionEpoch++;
        stateEpoch++;
        catalogs = null;
        contents.clear();
        tableDefs.clear();
        errors.clear();
        loading.clear();
        expanded.clear();
        selectedKey = null;
        filter.setText("");
        model.setRoot(null);
    }

    /** F5: dimentica tutto e rilegge ciò che è aperto. */
    public void refreshAll() {
        if (workspace != null) {
            workspace.reader().invalidateAll();   // la notifica fa il resto
        }
    }

    private void invalidated(String catalog, String table) {
        stateEpoch++;           // le letture in corso sono vecchie: si scartano e si rifanno
        loading.clear();
        if (catalog == null) {
            catalogs = null;
            contents.clear();
            tableDefs.clear();
            errors.clear();
        } else {
            String c = NavNode.lower(catalog);
            contents.remove(c);
            errors.remove(NavNode.catalogKey(catalog));
            tableDefs.keySet().removeIf(k -> k.startsWith("T:" + c + "."));
            errors.keySet().removeIf(k -> k.startsWith("T:" + c + "."));
        }
        rebuild();
    }

    // ================================================================ caricamento pigro

    private void loadIfNeeded(NavNode node) {
        if (workspace == null) {
            return;
        }
        switch (node.kind()) {
            case CATALOG -> {
                String key = NavNode.catalogKey(node.catalog());
                if (!contents.containsKey(NavNode.lower(node.catalog())) && !errors.containsKey(key)
                        && loading.add(key)) {
                    String catalog = node.catalog();
                    MetadataReader reader = workspace.reader();
                    load(key, () -> {
                        List<TableSummary> tables = reader.tables(catalog);
                        List<RoutineInfo> routines = List.of();
                        String routinesError = null;
                        try {
                            routines = reader.routines(catalog);
                        } catch (SQLException e) {
                            routinesError = describe(e);
                        }
                        return new CatalogContent(tables, routines, routinesError);
                    }, content -> contents.put(NavNode.lower(catalog), content));
                }
            }
            case TABLE -> {
                String key = node.key();
                if (!tableDefs.containsKey(key) && !errors.containsKey(key) && loading.add(key)) {
                    String catalog = node.catalog();
                    String name = node.name();
                    MetadataReader reader = workspace.reader();
                    load(key, () -> reader.table(catalog, name), def -> {
                        if (def.isPresent()) {
                            tableDefs.put(key, def.get());
                        } else {
                            errors.put(key, Texts.get("nav.table.missing"));
                        }
                    });
                }
            }
            default -> {
                // gli altri nodi hanno già i figli
            }
        }
    }

    private void loadCatalogsIfNeeded() {
        if (workspace == null || catalogs != null || errors.containsKey(CATALOGS_KEY) || !loading.add(CATALOGS_KEY)) {
            return;
        }
        MetadataReader reader = workspace.reader();
        load(CATALOGS_KEY, reader::catalogs, list -> catalogs = list);
    }

    /** Lettura in background; al ritorno (sull'EDT) aggiorna lo stato e ricostruisce l'albero. */
    private <T> void load(String key, Callable<T> reading, Consumer<T> apply) {
        int session = sessionEpoch;
        int state = stateEpoch;
        background(reading, value -> {
            if (state != stateEpoch) {
                return;   // nel frattempo i metadati sono stati invalidati: la ricostruzione ha già rilanciato la lettura
            }
            loading.remove(key);
            apply.accept(value);
            rebuild();
        }, error -> {
            if (state != stateEpoch) {
                return;
            }
            loading.remove(key);
            errors.put(key, Texts.get("nav.load.error", describe(error)));
            rebuild();
        }, session);
    }

    /** Lavoro fuori dall'EDT; i risultati di una sessione ormai chiusa si ignorano. */
    private <T> void background(Callable<T> work, Consumer<T> onDone, Consumer<Exception> onError, int session) {
        pendingTasks++;
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                pendingTasks--;
                if (session != sessionEpoch) {
                    return;
                }
                try {
                    onDone.accept(get());
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() instanceof Exception ex ? ex : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private static String describe(Exception e) {
        if (e instanceof SQLException sql && sql.getErrorCode() != 0) {
            return Texts.get("nav.serverError", sql.getErrorCode(), sql.getMessage());
        }
        return String.valueOf(e.getMessage());
    }

    // ================================================================ costruzione dell'albero

    /** Ricostruisce l'albero dallo stato letto, dal filtro e dalle espansioni ricordate. */
    private void rebuild() {
        if (workspace == null) {
            model.setRoot(null);
            return;
        }
        rebuilding = true;
        try {
            String f = filter.getText().strip().toLowerCase(java.util.Locale.ROOT);
            var s = workspace.session();
            DefaultMutableTreeNode root = node(new NavNode(NavNode.Kind.SERVER, null, s.profile().name(),
                    s.serverInfo(), ROOT_KEY, -1));
            Set<String> autoExpand = new HashSet<>();
            if (catalogs == null) {
                String err = errors.get(CATALOGS_KEY);
                root.add(err != null ? message(ROOT_KEY, err) : loadingNode(ROOT_KEY));
                loadCatalogsIfNeeded();
            } else {
                for (CatalogInfo c : catalogs) {
                    if (c.system() && !showSystem) {
                        continue;
                    }
                    DefaultMutableTreeNode cn = catalogNode(c, f, autoExpand);
                    if (cn != null) {
                        root.add(cn);
                    }
                }
                if (root.getChildCount() == 0) {
                    root.add(message(ROOT_KEY, Texts.get(f.isEmpty() ? "nav.noCatalogs" : "nav.noMatch")));
                }
            }
            model.setRoot(root);
            restoreExpansion(root, autoExpand);
            restoreSelection(root);
        } finally {
            rebuilding = false;
        }
    }

    private DefaultMutableTreeNode catalogNode(CatalogInfo c, String f, Set<String> autoExpand) {
        String key = NavNode.catalogKey(c.name());
        DefaultMutableTreeNode cn = node(new NavNode(NavNode.Kind.CATALOG, c.name(), c.name(), c, key, -1));
        boolean nameMatches = f.isEmpty() || NavNode.lower(c.name()).contains(f);
        CatalogContent content = contents.get(NavNode.lower(c.name()));
        if (content == null) {
            if (!nameMatches) {
                return null;
            }
            String err = errors.get(key);
            cn.add(err != null ? message(key, err) : loadingNode(key));
            return cn;
        }
        String objectFilter = nameMatches ? "" : f;
        List<TableSummary> tables = new ArrayList<>();
        List<TableSummary> views = new ArrayList<>();
        for (TableSummary t : content.tables()) {
            if (objectFilter.isEmpty() || NavNode.lower(t.name()).contains(objectFilter)) {
                (t.isView() ? views : tables).add(t);
            }
        }
        List<RoutineInfo> routines = content.routines().stream()
                .filter(r -> objectFilter.isEmpty() || NavNode.lower(r.name()).contains(objectFilter)).toList();
        if (!objectFilter.isEmpty() && tables.isEmpty() && views.isEmpty() && routines.isEmpty()) {
            return null;
        }
        if (!objectFilter.isEmpty()) {
            autoExpand.add(key);
        }
        if (objectFilter.isEmpty() || !tables.isEmpty()) {
            String folderKey = key + "/tables";
            DefaultMutableTreeNode folder = node(new NavNode(NavNode.Kind.TABLES, c.name(),
                    Texts.get("nav.folder.tables"), null, folderKey, tables.size()));
            for (TableSummary t : tables) {
                folder.add(tableNode(t));
            }
            if (tables.isEmpty()) {
                folder.add(message(folderKey, Texts.get("nav.noTables")));
            }
            cn.add(folder);
            if (!objectFilter.isEmpty()) {
                autoExpand.add(folderKey);
            }
        }
        if (!views.isEmpty()) {   // sezioni vuote nascoste: meno rumore (le tabelle invece si mostrano sempre)
            String folderKey = key + "/views";
            DefaultMutableTreeNode folder = node(new NavNode(NavNode.Kind.VIEWS, c.name(),
                    Texts.get("nav.folder.views"), null, folderKey, views.size()));
            for (TableSummary v : views) {
                folder.add(node(new NavNode(NavNode.Kind.VIEW, c.name(), v.name(), v,
                        NavNode.tableKey(c.name(), v.name()), -1)));
            }
            cn.add(folder);
            if (!objectFilter.isEmpty()) {
                autoExpand.add(folderKey);
            }
        }
        if (!routines.isEmpty() || content.routinesError() != null) {
            String folderKey = key + "/routines";
            DefaultMutableTreeNode folder = node(new NavNode(NavNode.Kind.ROUTINES, c.name(),
                    Texts.get("nav.folder.routines"), null, folderKey, routines.size()));
            for (RoutineInfo r : routines) {
                folder.add(node(new NavNode(NavNode.Kind.ROUTINE, c.name(), r.name(), r,
                        folderKey + "/" + r.kind() + ":" + NavNode.lower(r.name()), -1)));
            }
            if (content.routinesError() != null) {
                folder.add(message(folderKey, content.routinesError()));
            }
            cn.add(folder);
            if (!objectFilter.isEmpty()) {
                autoExpand.add(folderKey);
            }
        }
        return cn;
    }

    private DefaultMutableTreeNode tableNode(TableSummary t) {
        String key = NavNode.tableKey(t.catalog(), t.name());
        DefaultMutableTreeNode tn = node(new NavNode(NavNode.Kind.TABLE, t.catalog(), t.name(), t, key, -1));
        TableDef def = tableDefs.get(key);
        if (def == null) {
            String err = errors.get(key);
            tn.add(err != null ? message(key, err) : loadingNode(key));
            return tn;
        }
        Set<String> pk = new HashSet<>();
        def.primaryKey().ifPresent(p -> p.columns().forEach(col -> pk.add(NavNode.lower(col))));
        DefaultMutableTreeNode cols = node(new NavNode(NavNode.Kind.COLUMNS, t.catalog(),
                Texts.get("nav.folder.columns"), null, key + "/columns", def.columns().size()));
        for (ColumnDef col : def.columns()) {
            cols.add(node(new NavNode(NavNode.Kind.COLUMN, t.catalog(), col.name(), col,
                    key + "/columns/" + NavNode.lower(col.name()), pk.contains(NavNode.lower(col.name())) ? 1 : 0)));
        }
        List<ReadOnlyIndex> readOnly = def.readOnlyIndexes();
        DefaultMutableTreeNode idx = node(new NavNode(NavNode.Kind.INDEXES, t.catalog(),
                Texts.get("nav.folder.indexes"), null, key + "/indexes", def.indexes().size() + readOnly.size()));
        for (IndexDef i : def.indexes()) {
            idx.add(node(new NavNode(NavNode.Kind.INDEX, t.catalog(), i.name(), i,
                    key + "/indexes/" + NavNode.lower(i.name()), -1)));
        }
        // FULLTEXT, SPATIAL, prefisso, espressione, DESC: v1 non li modifica, ma ci sono e si devono vedere
        for (ReadOnlyIndex i : readOnly) {
            idx.add(node(new NavNode(NavNode.Kind.INDEX, t.catalog(), i.name(), i,
                    key + "/indexes/" + NavNode.lower(i.name()), -1)));
        }
        DefaultMutableTreeNode fks = node(new NavNode(NavNode.Kind.FOREIGN_KEYS, t.catalog(),
                Texts.get("nav.folder.foreignKeys"), null, key + "/fks", def.foreignKeys().size()));
        for (ForeignKeyDef fk : def.foreignKeys()) {
            fks.add(node(new NavNode(NavNode.Kind.FOREIGN_KEY, t.catalog(), fk.name(), fk,
                    key + "/fks/" + NavNode.lower(fk.name()), -1)));
        }
        tn.add(cols);
        tn.add(idx);
        tn.add(fks);
        return tn;
    }

    private static DefaultMutableTreeNode node(NavNode n) {
        return new DefaultMutableTreeNode(n);
    }

    private static DefaultMutableTreeNode loadingNode(String parentKey) {
        return node(new NavNode(NavNode.Kind.LOADING, null, Texts.get("nav.loading"), null, parentKey + "/…", -1));
    }

    private static DefaultMutableTreeNode message(String parentKey, String text) {
        return node(new NavNode(NavNode.Kind.MESSAGE, null, text, null, parentKey + "/!", -1));
    }

    private void restoreExpansion(DefaultMutableTreeNode node, Set<String> autoExpand) {
        NavNode n = (NavNode) node.getUserObject();
        if (expanded.contains(n.key()) || autoExpand.contains(n.key())) {
            tree.expandPath(new TreePath(node.getPath()));   // può avviare la lettura di ciò che non è in cache
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            restoreExpansion((DefaultMutableTreeNode) node.getChildAt(i), autoExpand);
        }
    }

    private void restoreSelection(DefaultMutableTreeNode root) {
        if (selectedKey == null) {
            return;
        }
        TreePath path = findByKey(root, selectedKey);
        if (path != null) {
            tree.setSelectionPath(path);
        }
    }

    private static TreePath findByKey(DefaultMutableTreeNode root, String key) {
        Enumeration<javax.swing.tree.TreeNode> all = root.depthFirstEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) all.nextElement();
            if (((NavNode) n.getUserObject()).key().equals(key)) {
                return new TreePath(n.getPath());
            }
        }
        return null;
    }

    private static NavNode nodeOf(TreePath path) {
        return (NavNode) ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    // ================================================================ menu contestuale

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        tree.setSelectionPath(path);
        JPopupMenu menu = menuFor(path);
        if (menu != null) {
            menu.show(tree, e.getX(), e.getY());
        }
    }

    /** Il menu contestuale del nodo; {@code null} se il nodo non ne ha. */
    public JPopupMenu menuFor(TreePath path) {
        NavNode n = nodeOf(path);
        JPopupMenu menu = new JPopupMenu();
        switch (n.kind()) {
            case SERVER -> {
                menu.add(item("nav.menu.newCatalog", this::newCatalog));
                menu.addSeparator();
                JCheckBoxMenuItem system = new JCheckBoxMenuItem(Texts.get("nav.menu.showSystem"), showSystem);
                system.setName("nav.menu.showSystem");
                system.addActionListener(e -> setShowSystemCatalogs(system.isSelected()));
                menu.add(system);
            }
            case CATALOG -> {
                JMenuItem drop = item("nav.menu.dropCatalog", () -> propose(TreeScripts.dropCatalog(n.catalog())));
                drop.setEnabled(!(n.data() instanceof CatalogInfo c && c.system()));
                menu.add(drop);
                menu.addSeparator();
                menu.add(item("nav.menu.refresh", () -> refreshCatalog(n.catalog())));
            }
            case TABLE -> {
                menu.add(item("nav.menu.rename", () -> renameTable(n.catalog(), n.name())));
                menu.add(item("nav.menu.truncate", () -> propose(TreeScripts.truncateTable(n.catalog(), n.name()))));
                menu.add(item("nav.menu.dropTable", () -> propose(TreeScripts.dropTable(n.catalog(), n.name()))));
                menu.addSeparator();
                menu.add(item("nav.menu.showCreate", () -> showCreateTable(n.catalog(), n.name())));
                menu.add(item("nav.menu.copyName", () -> copy(n.name())));
            }
            case VIEW -> {
                menu.add(item("nav.menu.dropView", () -> propose(TreeScripts.dropView(n.catalog(), n.name()))));
                menu.addSeparator();
                menu.add(item("nav.menu.showCreate", () -> showCreateView(n.catalog(), n.name())));
            }
            case ROUTINE -> menu.add(item("nav.menu.showCreate", () -> showCreateRoutine(n.routine())));
            default -> {
                return null;
            }
        }
        return menu;
    }

    private static JMenuItem item(String key, Runnable action) {
        JMenuItem item = new JMenuItem(Texts.get(key));
        item.setName(key);
        item.addActionListener(e -> action.run());
        return item;
    }

    // ================================================================ operazioni

    private void propose(SqlScript script) {
        if (workspace != null) {
            workspace.pipeline().propose(script);
        }
    }

    /** «Nuovo catalogo…»: collation lette in background, poi la finestra, poi l'anteprima. */
    public void newCatalog() {
        if (workspace == null) {
            return;
        }
        MetadataReader reader = workspace.reader();
        background(reader::collations, list -> {
            String collation = list.stream().filter(c -> c.charset().equals("utf8mb4") && c.isDefault())
                    .map(CollationInfo::name).findFirst().orElse(null);
            CreateCatalogDialog.Choice choice = workspace.prompts().askNewCatalog(list, "utf8mb4", collation);
            if (choice != null) {
                propose(TreeScripts.createCatalog(choice.name(), choice.charset(), choice.collation()));
            }
        }, error -> view().message(PipelineView.MessageKind.ERROR, Texts.get("nav.load.error", describe(error))),
                sessionEpoch);
    }

    private void renameTable(String catalog, String table) {
        String newName = workspace.prompts().askNewTableName(catalog, table);
        if (newName == null || newName.strip().isEmpty() || newName.strip().equals(table)) {
            return;
        }
        propose(TreeScripts.renameTable(catalog, table, newName.strip()));
    }

    private void refreshCatalog(String catalog) {
        workspace.reader().invalidate(catalog);
    }

    private void copy(String text) {
        workspace.prompts().copyToClipboard(text);
    }

    private void showCreateTable(String catalog, String table) {
        MetadataReader reader = workspace.reader();
        showCreate(Texts.get("showCreate.title.table", table),
                MetadataReader.showCreateStatement("TABLE", catalog, table),
                () -> reader.showCreateTable(catalog, table));
    }

    private void showCreateView(String catalog, String view) {
        MetadataReader reader = workspace.reader();
        showCreate(Texts.get("showCreate.title.view", view), MetadataReader.showCreateStatement("VIEW", catalog, view),
                () -> reader.showCreateView(catalog, view));
    }

    private void showCreateRoutine(RoutineInfo routine) {
        MetadataReader reader = workspace.reader();
        showCreate(Texts.get("showCreate.title.routine", routine.name()),
                MetadataReader.showCreateStatement(routine.kind().sql(), routine.catalog(), routine.name()),
                () -> reader.showCreate(routine));
    }

    /** «Mostra SQL di creazione»: letto dal canale interno (sola lettura, non va nel registro). */
    private void showCreate(String title, String statement, Callable<Optional<String>> reading) {
        background(reading, text -> {
            if (text.isPresent()) {
                workspace.prompts().showCreateSql(title, statement, text.get());
            } else {
                view().message(PipelineView.MessageKind.WARNING, Texts.get("showCreate.missing", statement));
            }
        }, error -> view().message(PipelineView.MessageKind.ERROR,
                Texts.get("showCreate.error", statement, describe(error))), sessionEpoch);
    }

    private PipelineView view() {
        return workspace.view();
    }

    /** Mostra o nasconde i cataloghi di sistema. */
    public void setShowSystemCatalogs(boolean show) {
        showSystem = show;
        rebuild();
    }

    // ================================================================ accesso per i test e per le altre zone

    public JTree tree() {
        return tree;
    }

    public JTextField filterField() {
        return filter;
    }

    /** Nessuna lettura in corso. */
    public boolean isIdle() {
        return pendingTasks == 0 && loading.isEmpty();
    }

    /** Il catalogo del nodo selezionato (o del suo oggetto); {@code null} se nessuno. */
    public String selectedCatalog() {
        TreePath p = tree.getSelectionPath();
        return p == null ? null : nodeOf(p).catalog();
    }

    /** Il primo nodo visibile (nell'albero attuale) di quel tipo, catalogo e nome; {@code null} se non c'è. */
    public TreePath find(NavNode.Kind kind, String catalog, String name) {
        Object root = model.getRoot();
        if (root == null) {
            return null;
        }
        Enumeration<javax.swing.tree.TreeNode> all = ((DefaultMutableTreeNode) root).breadthFirstEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) all.nextElement();
            NavNode nav = (NavNode) n.getUserObject();
            if (nav.kind() == kind && (catalog == null || catalog.equalsIgnoreCase(nav.catalog()))
                    && (name == null || name.equalsIgnoreCase(nav.name()))) {
                return new TreePath(n.getPath());
            }
        }
        return null;
    }

    /** Il primo figlio di quel tipo del nodo indicato; {@code null} se non c'è. */
    public TreePath findChild(TreePath parent, NavNode.Kind kind) {
        DefaultMutableTreeNode n = (DefaultMutableTreeNode) parent.getLastPathComponent();
        for (int i = 0; i < n.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) n.getChildAt(i);
            if (((NavNode) child.getUserObject()).kind() == kind) {
                return parent.pathByAddingChild(child);
            }
        }
        return null;
    }

    /** I figli (nell'albero attuale) del nodo indicato. */
    public List<NavNode> childrenOf(TreePath path) {
        DefaultMutableTreeNode n = (DefaultMutableTreeNode) path.getLastPathComponent();
        List<NavNode> out = new ArrayList<>();
        for (int i = 0; i < n.getChildCount(); i++) {
            out.add((NavNode) ((DefaultMutableTreeNode) n.getChildAt(i)).getUserObject());
        }
        return out;
    }

    /** Tutti i nodi dell'albero attuale, in ordine. */
    public List<NavNode> allNodes() {
        List<NavNode> out = new ArrayList<>();
        Object root = model.getRoot();
        if (root != null) {
            Enumeration<javax.swing.tree.TreeNode> all = ((DefaultMutableTreeNode) root).preorderEnumeration();
            while (all.hasMoreElements()) {
                out.add((NavNode) ((DefaultMutableTreeNode) all.nextElement()).getUserObject());
            }
        }
        return out;
    }

    /** L'icona che il navigatore disegna per quel nodo (la stessa del renderer). */
    public javax.swing.Icon iconOf(TreePath path) {
        Component c = tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), false,
                tree.isExpanded(path), false, tree.getRowForPath(path), false);
        return ((Renderer) c).main.getIcon();
    }

    /** Il testo mostrato per quel nodo. */
    public String labelOf(TreePath path) {
        return plainLabel(nodeOf(path));
    }

    // ================================================================ disegno dei nodi

    static String plainLabel(NavNode n) {
        return switch (n.kind()) {
            case TABLES, VIEWS, ROUTINES, COLUMNS, INDEXES, FOREIGN_KEYS ->
                    Texts.get("nav.section", n.name().toUpperCase(Locale.ROOT), n.count());
            default -> n.name() + (detail(n).isEmpty() ? "" : "  " + detail(n));
        };
    }

    /** La parte grigia accanto al nome. */
    private static String detail(NavNode n) {
        return switch (n.kind()) {
            case SERVER -> n.data() instanceof it.ramasql.core.connection.ServerInfo s ? s.displayName() : "";
            case CATALOG -> n.data() instanceof CatalogInfo c && c.system() ? Texts.get("nav.catalog.system") : "";
            case TABLE -> n.table() != null && n.table().isMyIsam() ? Texts.get("nav.table.myisam") : "";
            case COLUMN -> {
                ColumnDef c = (ColumnDef) n.data();
                StringBuilder sb = new StringBuilder(c.fullType());
                if (c.unsigned()) {
                    sb.append(' ').append(Texts.get("nav.column.unsigned"));
                }
                if (c.zerofill()) {
                    sb.append(' ').append(Texts.get("nav.column.zerofill"));
                }
                if (!c.nullable()) {
                    sb.append(' ').append(Texts.get("nav.column.notNull"));
                }
                if (c.autoIncrement()) {
                    sb.append(' ').append(Texts.get("nav.column.autoIncrement"));
                }
                yield sb.toString();
            }
            case INDEX -> {
                if (n.data() instanceof ReadOnlyIndex r) {
                    StringBuilder sb = new StringBuilder("(").append(r.columns()).append(')');
                    if (r.unique()) {
                        sb.append(' ').append(Texts.get(IndexDef.PRIMARY_NAME.equals(r.name())
                                ? "nav.index.primary" : "nav.index.unique"));
                    }
                    for (ReadOnlyIndex.Feature f : ReadOnlyIndex.Feature.values()) {
                        if (r.features().contains(f)) {
                            sb.append(' ').append(Texts.get("nav.index.feature." + f.name().toLowerCase(Locale.ROOT)));
                        }
                    }
                    yield sb.toString();
                }
                IndexDef i = (IndexDef) n.data();
                String cols = "(" + String.join(", ", i.columns()) + ")";
                yield i.isPrimary() ? cols + " " + Texts.get("nav.index.primary")
                        : i.isUnique() ? cols + " " + Texts.get("nav.index.unique") : cols;
            }
            case FOREIGN_KEY -> {
                ForeignKeyDef fk = (ForeignKeyDef) n.data();
                yield "(" + String.join(", ", fk.columns()) + ") → " + fk.refTable() + " ("
                        + String.join(", ", fk.refColumns()) + ")";
            }
            case ROUTINE -> {
                RoutineInfo r = n.routine();
                String kind = Texts.get("nav.routine." + r.kind().name().toLowerCase(java.util.Locale.ROOT));
                yield r.table() != null ? kind + " " + r.detail() + " · " + r.table()
                        : r.detail().isEmpty() ? kind : kind + " · " + r.detail();
            }
            default -> "";
        };
    }

    private static String tooltip(NavNode n) {
        return switch (n.kind()) {
            case TABLE -> n.table() == null || n.table().engine() == null ? null
                    : n.table().isMyIsam() ? Texts.get("nav.tooltip.myisam")
                    : Texts.get("nav.tooltip.engine", n.table().engine());
            case VIEW -> Texts.get("nav.tooltip.view");
            case ROUTINE -> Texts.get("nav.tooltip.routine");
            case INDEX -> n.data() instanceof ReadOnlyIndex r ? Texts.get("nav.tooltip.readOnlyIndex", r.definition()) : null;
            case COLUMN -> n.data() instanceof ColumnDef c && c.autoIncrement()
                    ? Texts.get("nav.tooltip.autoIncrement") : null;
            case MESSAGE -> n.name();
            default -> null;
        };
    }

    /**
     * DESIGN-SYSTEM §3.3: a sinistra icona e nome in {@code text.primary}; a destra, allineato al bordo, il dettaglio in
     * {@code text.tertiary} (tipo della colonna, colonne dell'indice, «MyISAM»…); il server ha la pillola colorata con la
     * versione; le cartelle sono intestazioni di sezione «TABELLE · 6» senza icona. Una barra d'accento di 2 px
     * segna la riga selezionata quando l'albero ha il focus.
     */
    private static final class Renderer extends JPanel implements TreeCellRenderer {
        private static final long serialVersionUID = 1L;

        final JLabel main = new JLabel();
        private final JLabel detail = new JLabel();
        private final Pill pill = new Pill("", Tokens.BG_SURFACE, Tokens.SERVER_MARIADB);
        private boolean focusBar;

        Renderer() {
            super(null);   // disposizione in doLayout: il nome ha la precedenza, il dettaglio si accorcia
            setOpaque(false);
            main.setIconTextGap(Tokens.px(6));
            detail.setHorizontalAlignment(SwingConstants.RIGHT);
            add(main);
            add(detail);
            add(pill);
            setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(4), 0, Tokens.px(Tokens.SPACE_12)));
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            java.awt.Insets in = getInsets();
            java.awt.Dimension m = main.getPreferredSize();
            int extra = detail.isVisible() ? detail.getPreferredSize().width
                    : pill.isVisible() ? pill.getPreferredSize().width : 0;
            int w = in.left + m.width + (extra > 0 ? Tokens.px(Tokens.SPACE_8) + extra : 0) + in.right;
            return new java.awt.Dimension(w, Math.max(m.height, pill.getPreferredSize().height) + in.top + in.bottom);
        }

        @Override
        public void doLayout() {
            java.awt.Insets in = getInsets();
            int w = getWidth() - in.left - in.right;
            int h = getHeight() - in.top - in.bottom;
            // la pillola del server resta intera (è l'informazione da leggere); il nome si accorcia
            int reserved = pill.isVisible() ? pill.getPreferredSize().width + Tokens.px(Tokens.SPACE_8) : 0;
            int mw = Math.max(0, Math.min(main.getPreferredSize().width, w - reserved));
            main.setBounds(in.left, in.top, mw, h);
            int x = in.left + mw + Tokens.px(Tokens.SPACE_8);
            int rest = Math.max(0, in.left + w - x);
            detail.setBounds(x, in.top, rest, h);
            java.awt.Dimension pp = pill.getPreferredSize();
            int pw = Math.min(pp.width, rest);
            pill.setBounds(in.left + w - pw, in.top + (h - pp.height) / 2, pw, pp.height);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            focusBar = selected && tree.hasFocus();
            Object user = value instanceof DefaultMutableTreeNode d ? d.getUserObject() : null;
            detail.setVisible(false);
            pill.setVisible(false);
            if (!(user instanceof NavNode n)) {
                main.setIcon(null);
                main.setText(String.valueOf(value));
                return this;
            }
            main.setIcon(NavigatorIcons.iconFor(n, expanded));
            main.setFont(Tokens.font(Tokens.BODY, Font.PLAIN));
            main.setForeground(Tokens.TEXT_PRIMARY);
            detail.setFont(Tokens.font(Tokens.SMALL, Font.PLAIN));
            detail.setForeground(Tokens.TEXT_TERTIARY);
            switch (n.kind()) {
                case TABLES, VIEWS, ROUTINES, COLUMNS, INDEXES, FOREIGN_KEYS -> {
                    main.setText(Texts.get("nav.section", n.name().toUpperCase(Locale.ROOT), n.count()));
                    main.setFont(Tokens.font(Tokens.CAPTION, Font.BOLD));
                    main.setForeground(Tokens.TEXT_TERTIARY);
                }
                case LOADING -> {
                    main.setText(n.name());
                    main.setFont(Tokens.font(Tokens.BODY, Font.ITALIC));
                    main.setForeground(Tokens.TEXT_TERTIARY);
                }
                case MESSAGE -> {
                    main.setText(n.name());
                    main.setForeground(Tokens.TEXT_SECONDARY);
                }
                case SERVER -> {
                    main.setText(n.name());
                    main.setFont(Tokens.font(Tokens.BODY, Font.BOLD));
                    if (n.data() instanceof it.ramasql.core.connection.ServerInfo s) {
                        pill.setText(s.displayName());
                        pill.setColors(Tokens.BG_SURFACE, s.isMariaDb() ? Tokens.SERVER_MARIADB : Tokens.SERVER_MYSQL);
                        pill.setVisible(true);
                    }
                }
                default -> {
                    main.setText(n.name());
                    String d = detail(n);
                    if (!d.isEmpty()) {
                        detail.setText(d);
                        detail.setVisible(true);
                    }
                }
            }
            setToolTipText(tooltip(n));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (focusBar) {
                g.setColor(Tokens.ACCENT);
                g.fillRect(0, Tokens.px(5), Tokens.px(2), getHeight() - Tokens.px(10));
            }
        }
    }
}
