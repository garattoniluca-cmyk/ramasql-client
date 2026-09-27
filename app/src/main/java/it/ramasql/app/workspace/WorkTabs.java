/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.workspace;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import it.ramasql.app.Texts;
import it.ramasql.app.editor.CompletionSource;
import it.ramasql.app.editor.EditorPrompts;
import it.ramasql.app.editor.SqlEditor;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.grid.GridPrompts;
import it.ramasql.app.grid.TableGridDataSource;
import it.ramasql.app.tableeditor.TableEditor;
import it.ramasql.app.tableeditor.TableEditorPrompts;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.TableDef;

/**
 * Le schede dell'area di lavoro: data-entry di una tabella, editor SQL, editor di tabelle. È qui che i componenti degli
 * step 4-6 incontrano la sessione: la griglia riceve un {@link TableGridDataSource} che legge le pagine dal server
 * passando dall'esecutore, la sua <em>Conferma</em> passa dal {@link GridApplier}, l'editor SQL esegue con il
 * {@link PipelineSqlRunner} e l'editor di tabelle applica con il {@link PipelineTableApplier}: ogni strada finisce
 * nell'anteprima e nel registro.
 *
 * <p><b>Chiusura.</b> Una scheda con modifiche in sospeso non si chiude in silenzio: si chiede «Conferma, scarta o
 * resta?» ({@code T4.12}). «Resta» lascia tutto dov'è; «Scarta» butta le modifiche (operazione locale, sul server non
 * si scrive); «Conferma» manda le modifiche all'anteprima e <b>non</b> chiude la scheda, così l'utente vede l'esito e
 * le eventuali righe rifiutate dal server.
 */
public final class WorkTabs {

    private final JTabbedPane tabs;
    private final WorkspacePrompts prompts;
    /** Il titolo di ogni scheda, per aggiornarlo quando la tabella cambia nome o viene creata. */
    private final java.util.Map<Component, JLabel> titles = new java.util.HashMap<>();
    private int queryCounter;
    private int visualCounter;
    private int newTableCounter;

    public WorkTabs(JTabbedPane tabs, WorkspacePrompts prompts) {
        this.tabs = Objects.requireNonNull(tabs, "tabs");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
    }

    // ---------------------------------------------------------------- apertura

    /**
     * Apre (o riporta in primo piano) la scheda di data-entry di una tabella. La prima pagina si legge subito: chi
     * chiama deve essere fuori dall'EDT oppure accettare l'attesa (vedi {@code BUG-017}).
     *
     * @param table    tabella già letta dai metadati
     * @param pageSize righe per pagina (il «limite righe» delle impostazioni)
     */
    public DataGrid openDataEntry(SessionWorkspace workspace, TableDef table, int pageSize, GridPrompts gridPrompts) {
        String name = tabName("dataEntry", table.catalog(), table.name());
        DataGrid existing = (DataGrid) find(name);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return existing;
        }
        TableGridDataSource source = new TableGridDataSource(workspace.executor(), table);
        DataGrid grid = DataGrid.forTable(table, source, pageSize, gridPrompts);
        grid.setName(name);
        new GridApplier(workspace.pipeline(), workspace.view()).bind(grid, table);
        add(grid, Texts.get("tabs.dataEntry.title", table.name()), table.catalog() + "." + table.name());
        return grid;
    }

    /**
     * Apre una vista in <b>sola lettura</b>: i dati di una vista si modificano nelle tabelle da cui la vista li
     * prende, quindi la griglia non ha Conferma; copiare ed esportare sì.
     */
    public DataGrid openView(SessionWorkspace workspace, String catalog, String view,
            java.util.List<it.ramasql.core.metadata.ColumnDef> columns, int pageSize, GridPrompts gridPrompts) {
        String name = tabName("view", catalog, view);
        DataGrid existing = (DataGrid) find(name);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return existing;
        }
        TableGridDataSource source = new TableGridDataSource(workspace.executor(), catalog, view,
                columns.stream().map(it.ramasql.core.metadata.ColumnDef::name).toList(),
                it.ramasql.core.exec.SqlOrigin.GRID.label());
        DataGrid grid = DataGrid.readOnly(columns, source, pageSize, DataGrid.viewExplanation(), gridPrompts);
        grid.setName(name);
        add(grid, Texts.get("tabs.dataEntry.title", view), catalog + "." + view);
        return grid;
    }

    /** Apre una scheda «Query N» con l'editor SQL collegato alla pipeline e al completamento dai metadati. */
    public SqlEditor openSqlEditor(SessionWorkspace workspace, int rowLimit, EditorPrompts editorPrompts,
            GridPrompts gridPrompts, CompletionSource completion) {
        queryCounter++;
        String title = Texts.get("tabs.query.title", queryCounter);
        SqlEditor editor = new SqlEditor(title, new PipelineSqlRunner(workspace.pipeline()),
                completion == null ? CompletionSource.empty() : completion, editorPrompts, gridPrompts, rowLimit);
        editor.setName(tabName("query", null, String.valueOf(queryCounter)));
        add(editor, title, null);
        return editor;
    }

    /**
     * Apre una scheda «Query visiva N» sul catalogo dato (Step 7): diagramma del query builder e testo SQL
     * sincronizzati; l'esecuzione passa dalla pipeline come quella dell'editor SQL.
     *
     * @param alerts dove finiscono gli avvisi del query builder (il pannello Messaggi)
     */
    public it.ramasql.app.visual.VisualQueryTab openVisualQuery(SessionWorkspace workspace, String catalog,
            int rowLimit, EditorPrompts editorPrompts, GridPrompts gridPrompts, CompletionSource completion,
            java.util.function.Consumer<String> alerts, it.ramasql.app.visual.ViewSaver viewSaver) {
        return openVisual(workspace, catalog, Texts.get("tabs.visual.title", ++visualCounter),
                "visual." + visualCounter, rowLimit, editorPrompts, gridPrompts, completion, alerts, viewSaver);
    }

    /**
     * Apre la query visiva in <b>modalità vista</b> (Step 8): per una vista nuova ({@code view} nullo, «Nuova vista»)
     * o per modificarne una esistente («Modifica vista», che ha una scheda sola: un secondo clic la riporta davanti).
     */
    public it.ramasql.app.visual.VisualQueryTab openViewEditor(SessionWorkspace workspace, String catalog, String view,
            int rowLimit, EditorPrompts editorPrompts, GridPrompts gridPrompts, CompletionSource completion,
            java.util.function.Consumer<String> alerts, it.ramasql.app.visual.ViewSaver viewSaver) {
        if (view != null) {
            Component existing = find(tabName("viewEditor", catalog, view));
            if (existing instanceof it.ramasql.app.visual.VisualQueryTab tab) {
                tabs.setSelectedComponent(tab);
                return tab;
            }
        }
        String title = view == null ? Texts.get("tabs.view.new", ++visualCounter) : Texts.get("tabs.view.title", view);
        String key = view == null ? "visual." + visualCounter : null;
        it.ramasql.app.visual.VisualQueryTab tab = openVisual(workspace, catalog, title, key, rowLimit, editorPrompts,
                gridPrompts, completion, alerts, viewSaver);
        if (view != null) {
            tab.setName(tabName("viewEditor", catalog, view));
        }
        tab.enterViewMode(view, view != null);
        return tab;
    }

    /** La scheda di modifica di quella vista, se è aperta; {@code null} altrimenti. */
    public it.ramasql.app.visual.VisualQueryTab findViewEditor(String catalog, String view) {
        return find(tabName("viewEditor", catalog, view)) instanceof it.ramasql.app.visual.VisualQueryTab t ? t : null;
    }

    /** Dopo il primo salvataggio una «Nuova vista N» diventa la scheda di quella vista: «Vista nome». */
    public void becomeViewEditor(it.ramasql.app.visual.VisualQueryTab tab, String catalog, String view) {
        tab.setName(tabName("viewEditor", catalog, view));
        retitle(tab, Texts.get("tabs.view.title", view));
    }

    public void select(Component tab) {
        tabs.setSelectedComponent(tab);
    }

    private it.ramasql.app.visual.VisualQueryTab openVisual(SessionWorkspace workspace, String catalog, String title,
            String key, int rowLimit, EditorPrompts editorPrompts, GridPrompts gridPrompts, CompletionSource completion,
            java.util.function.Consumer<String> alerts, it.ramasql.app.visual.ViewSaver viewSaver) {
        it.ramasql.app.visual.VisualQueryTab tab = new it.ramasql.app.visual.VisualQueryTab(title, catalog,
                workspace.reader(), new PipelineSqlRunner(workspace.pipeline()),
                completion == null ? CompletionSource.empty() : completion, editorPrompts, gridPrompts, rowLimit,
                alerts, viewSaver);
        if (key != null) {
            tab.setName(tabName("visual", catalog, key));
        }
        add(tab, title, catalog);
        return tab;
    }

    /**
     * Apre l'editor di tabelle: struttura di una tabella esistente ({@code original} non nullo) o tabella nuova.
     *
     * @param original tabella letta dal server, {@code null} per una tabella nuova
     * @param catalog  catalogo in cui sta (o starà) la tabella
     */
    public TableEditor openTableEditor(SessionWorkspace workspace, TableDef original, String catalog,
            TableEditorPrompts editorPrompts) {
        // una tabella nuova apre sempre una scheda sua (due «Nuova tabella…» sono due tabelle diverse);
        // una tabella esistente ne ha una sola, che si riporta davanti
        String name;
        if (original == null) {
            newTableCounter++;
            name = tabName("tableEditor", catalog, "#nuova" + newTableCounter);   // «#» non è un nome di tabella
        } else {
            name = tabName("tableEditor", catalog, original.name());
            TableEditor existing = (TableEditor) find(name);
            if (existing != null) {
                tabs.setSelectedComponent(existing);
                return existing;
            }
        }
        TableEditor editor = new TableEditor(original, catalog, workspace.session().serverInfo(),
                new MetadataCatalogTables(workspace.reader(), catalog), editorPrompts,
                new PipelineTableApplier(workspace.pipeline(), workspace.reader()),
                new PipelineDataCheck(workspace.pipeline()));
        editor.setName(name);
        String title = original == null ? Texts.get("tabs.tableEditor.new")
                : Texts.get("tabs.tableEditor.title", original.name());
        add(editor, title, catalog + (original == null ? "" : "." + original.name()));
        // appena la tabella prende (o cambia) nome, il titolo della scheda lo segue
        editor.setOnChange(() -> retitle(editor, Texts.get("tabs.tableEditor.title", editor.editedTable().name())));
        return editor;
    }

    // ---------------------------------------------------------------- chiusura

    /**
     * Chiude la scheda, chiedendo prima all'utente se ci sono modifiche in sospeso o un file non salvato.
     *
     * @return {@code false} se la scheda è rimasta aperta (l'utente ha scelto «Resta», o ha confermato le modifiche)
     */
    public boolean close(Component tab) {
        int index = tabs.indexOfComponent(tab);
        if (index < 0) {
            return false;
        }
        if (tab instanceof DataGrid grid && grid.hasPending()) {
            switch (prompts.askPendingOnClose(grid.closeQuestion())) {
                case STAY -> {
                    return false;
                }
                case CONFIRM -> {
                    grid.confirm();   // la scheda resta aperta: l'esito e le righe rifiutate si devono vedere
                    return false;
                }
                case DISCARD -> grid.model().pending().discard();
            }
        }
        if (tab instanceof SqlEditor editor) {
            if (editor.isRunning() && prompts.askPendingOnClose(Texts.get("tabs.close.running"))
                    == WorkspacePrompts.PendingChoice.STAY) {
                return false;   // c'è una query in corso: chiudere la interromperebbe
            }
            if (!editor.canClose()) {
                return false;
            }
            editor.dispose();
        }
        if (tab instanceof it.ramasql.app.visual.VisualQueryTab visual) {
            if (visual.isRunning() && prompts.askPendingOnClose(Texts.get("tabs.close.running"))
                    == WorkspacePrompts.PendingChoice.STAY) {
                return false;
            }
            if (visual.isViewMode() && visual.isModified()) {
                // una vista con modifiche non salvate: salvarla sul server, scartarle o restare (non un file .sql)
                switch (prompts.askPendingOnClose(Texts.get("visual.view.close.question", visual.viewName()))) {
                    case STAY -> {
                        return false;
                    }
                    case CONFIRM -> {
                        visual.saveView();   // come per la griglia: si salva e la scheda resta, per vedere l'esito
                        return false;
                    }
                    case DISCARD -> { }
                }
            }
            if (!visual.canClose()) {
                return false;
            }
            visual.dispose();
        }
        if (tab instanceof TableEditor editor && editor.isModified()) {
            switch (prompts.askPendingOnClose(Texts.get("tableeditor.close.question", editor.editedTable().name()))) {
                case STAY -> {
                    return false;
                }
                case CONFIRM -> {
                    editor.apply();   // come per la griglia: si applica e la scheda resta, per vedere l'esito
                    return false;
                }
                case DISCARD -> editor.revert();
            }
        }
        titles.remove(tab);
        tabs.remove(index);
        return true;
    }

    /**
     * Da chiamare prima di chiudere il programma o la connessione: se qualche scheda ha lavoro non salvato — modifiche
     * in sospeso nel data-entry, un file {@code .sql} modificato, una tabella con modifiche non applicate — lo dice e
     * chiede se chiudere lo stesso.
     *
     * @return {@code false} se l'utente ha scelto di restare (chi chiama deve annullare la chiusura)
     */
    public boolean confirmCloseAll() {
        List<String> conLavoro = new ArrayList<>();
        for (Component c : tabs.getComponents()) {
            if (c instanceof DataGrid g && g.hasPending()) {
                conLavoro.add(titleOf(c));
            } else if (c instanceof SqlEditor e && e.isModified()) {
                conLavoro.add(titleOf(c));
            } else if (c instanceof TableEditor e && e.isModified()) {
                conLavoro.add(titleOf(c));
            } else if (c instanceof it.ramasql.app.visual.VisualQueryTab v && v.isModified()) {
                conLavoro.add(titleOf(c));
            }
        }
        if (conLavoro.isEmpty()) {
            return true;
        }
        return prompts.askPendingOnClose(Texts.get("tabs.close.pendingWork", String.join(", ", conLavoro)))
                != WorkspacePrompts.PendingChoice.STAY;
    }

    private String titleOf(Component tab) {
        JLabel label = titles.get(tab);
        return label == null ? String.valueOf(tab.getName()) : label.getText();
    }

    /** Chiude tutte le schede senza chiedere nulla: la connessione è finita, le schede non hanno più un server. */
    public void closeAllSilently() {
        for (Component c : tabs.getComponents()) {
            if (c instanceof SqlEditor editor) {
                editor.dispose();
            } else if (c instanceof it.ramasql.app.visual.VisualQueryTab visual) {
                visual.dispose();
            }
        }
        titles.clear();
        tabs.removeAll();
    }

    // ---------------------------------------------------------------- lettura

    /** La scheda in primo piano; {@code null} se non ce n'è nessuna. */
    public Component selected() {
        return tabs.getSelectedComponent();
    }

    /** L'editor SQL in primo piano; {@code null} se la scheda davanti non è un editor. */
    public SqlEditor selectedEditor() {
        return tabs.getSelectedComponent() instanceof SqlEditor e ? e : null;
    }

    /** La query visiva in primo piano; {@code null} se la scheda davanti è un'altra. */
    public it.ramasql.app.visual.VisualQueryTab selectedVisualQuery() {
        return tabs.getSelectedComponent() instanceof it.ramasql.app.visual.VisualQueryTab v ? v : null;
    }

    /** Le griglie di data-entry aperte, nell'ordine delle schede. */
    public List<DataGrid> grids() {
        List<DataGrid> result = new ArrayList<>();
        for (Component c : tabs.getComponents()) {
            if (c instanceof DataGrid g) {
                result.add(g);
            }
        }
        return result;
    }

    public int count() {
        return tabs.getTabCount();
    }

    /** La scheda con quel nome di componente; {@code null} se non c'è. */
    public Component find(String componentName) {
        for (Component c : tabs.getComponents()) {
            if (componentName.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- dettagli

    private static String tabName(String kind, String catalog, String key) {
        return "tab." + kind + "." + (catalog == null ? "" : catalog + ".") + key;
    }

    /** Aggiunge la scheda con la sua intestazione (titolo, suggerimento e «×» per chiudere) e la porta davanti. */
    private void add(JComponent tab, String title, String tooltip) {
        tabs.addTab(title, tab);
        int index = tabs.getTabCount() - 1;
        tabs.setTabComponentAt(index, header(tab, title, tooltip));
        tabs.setTitleAt(index, title);
        tabs.setToolTipTextAt(index, tooltip);
        tabs.setSelectedIndex(index);
    }

    /** Cambia il titolo mostrato sulla linguetta (la scheda resta quella). */
    private void retitle(Component tab, String title) {
        JLabel label = titles.get(tab);
        if (label != null && !title.equals(label.getText())) {
            label.setText(title);
            int index = tabs.indexOfComponent(tab);
            if (index >= 0) {
                tabs.setTitleAt(index, title);
            }
        }
    }

    private JComponent header(Component tab, String title, String tooltip) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.px(6), 0));
        panel.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setToolTipText(tooltip);
        titles.put(tab, label);
        panel.add(label);
        JButton close = new JButton(Texts.get("tabs.close.symbol"));
        close.setName("tabs.close." + tab.getName());
        close.setToolTipText(Texts.get("tabs.close"));
        close.setFocusable(false);
        close.setBorder(null);
        close.setContentAreaFilled(false);
        Styles.text(close, "smallText", Tokens.TEXT_TERTIARY);
        close.addActionListener(e -> close(tab));
        panel.add(close);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(panel, BorderLayout.CENTER);
        return wrapper;
    }
}
