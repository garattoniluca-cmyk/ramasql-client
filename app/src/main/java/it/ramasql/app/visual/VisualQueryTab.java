/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.visual;

import java.awt.BorderLayout;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import com.sqleo.querybuilder.QueryBuilder;

import it.ramasql.app.Texts;
import it.ramasql.app.editor.CompletionSource;
import it.ramasql.app.editor.EditorPrompts;
import it.ramasql.app.editor.SqlEditor;
import it.ramasql.app.editor.SqlRunner;
import it.ramasql.app.grid.GridPrompts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.MetadataReader;
import com.sqleo.querybuilder.QbOperations;
import it.ramasql.qb.QbSql;

/**
 * La scheda «Query visiva» ({@code DESIGN.md} §3.7, Step 7): la stessa query vista in due modi, <b>Grafica</b> (il
 * diagramma del query builder ereditato da SQLeo) e <b>SQL</b> (il testo), sincronizzate.
 *
 * <ul>
 *   <li>In vista Grafica il diagramma comanda: <b>a ogni gesto</b> (tabella aggiunta, colonna spuntata, join cambiato,
 *       filtro…) il testo SQL si rigenera. Il testo è sempre quello che verrà eseguito e salvato.</li>
 *   <li>In vista SQL comanda il testo. Tornando alla Grafica il testo passa da {@link QbSql#check}: se è
 *       rappresentabile il diagramma si ricostruisce; se non lo è (funzioni finestra, CTE, UNION ALL…) si resta sul
 *       testo, intatto ed eseguibile, con un avviso che dice perché ({@code R-03}, {@code BUG-005}: al diagramma non
 *       arriva mai un modello che {@code check} rifiuta).</li>
 *   <li>Esecuzione, risultati, errori spiegati e salvataggio {@code .sql} sono quelli dell'editor SQL, che la scheda
 *       incorpora ({@link SqlEditor}); l'esecuzione passa dalla pipeline «anteprima SQL», con origine «Query visiva»,
 *       preceduta dal {@code USE} del catalogo della scheda ({@link CatalogScopedRunner}).</li>
 * </ul>
 */
public final class VisualQueryTab extends JPanel {

    private static final long serialVersionUID = 1L;

    private final String catalog;
    private final transient QueryBuilder builder;
    private final SqlEditor editor;
    private final JToggleButton graphicToggle = new JToggleButton(Texts.get("visual.view.graphic"));
    private final JToggleButton sqlToggle = new JToggleButton(Texts.get("visual.view.sql"));
    private final JButton runButton = new JButton(Texts.get("visual.run"));
    private final JButton stopButton = new JButton(Texts.get("visual.stop"));
    private final JButton saveButton = new JButton(Texts.get("visual.save"));
    /** Il testo della fascia d'avviso: va a capo (una frase lunga non si taglia al bordo). */
    private final javax.swing.JTextArea notice = new javax.swing.JTextArea();
    private final JPanel noticeBand = new JPanel(new BorderLayout());
    private final JPanel actions = new JPanel();
    /** L'SQL che il diagramma ha scritto l'ultima volta nel testo: se il testo è ancora quello, il diagramma vale. */
    private String lastDiagramSql = "";
    private boolean syncing;

    // modalità vista (Step 8)
    private final transient ViewSaver viewSaver;
    private final JButton saveAsViewButton = new JButton(Texts.get("visual.saveAsView"));
    private final javax.swing.JTextField viewName = new javax.swing.JTextField(24);
    private final JButton saveViewButton = new JButton(Texts.get("visual.view.save"));
    private final JPanel viewBar = new JPanel();
    private boolean viewMode;
    private boolean replaceExisting;
    /** Il testo dell'ultima vista salvata o riaperta; {@code null} = mai salvata. */
    private String savedViewSql;

    /**
     * @param title      titolo della scheda (es. «Query visiva 1»)
     * @param catalog    catalogo su cui si costruisce la query
     * @param reader     canale dei metadati della sessione
     * @param runner     esecutore del programma (la pipeline)
     * @param alerts     avvisi del query builder (pannello Messaggi)
     * @param viewSaver  chi salva le viste (Step 8); {@code null} = la scheda non offre «Salva come vista…»
     */
    public VisualQueryTab(String title, String catalog, MetadataReader reader, SqlRunner runner,
            CompletionSource completion, EditorPrompts editorPrompts, GridPrompts gridPrompts, int rowLimit,
            Consumer<String> alerts, ViewSaver viewSaver) {
        super(new BorderLayout());
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.viewSaver = viewSaver;
        setName("visualQuery");
        AppQbHost.installGlobal();
        builder = new QueryBuilder(new AppQbHost(reader, catalog, alerts));
        builder.setName("visualQuery.diagram");
        builder.hideSyntaxTab();
        builder.addQueryChangeListener(this::diagramChanged);

        editor = new SqlEditor(title, new CatalogScopedRunner(runner, catalog), completion, editorPrompts, gridPrompts,
                rowLimit);
        editor.setToolbarVisible(false);
        editor.setAlternateView(builder);
        editor.showAlternateView(true);
        editor.addPropertyChangeListener(SqlEditor.PROPERTY_RUNNING, e -> updateActions());

        add(buildTop(), BorderLayout.NORTH);
        add(editor, BorderLayout.CENTER);
        updateActions();
        preload(reader, catalog);
    }

    /**
     * Legge in sottofondo, all'apertura della scheda, le definizioni di tutte le tabelle del catalogo: il query builder
     * ereditato le chiede in modo sincrono sull'EDT quando si aggiunge una tabella (colonne, chiavi esterne), e così le
     * trova già nella cache del lettore invece di interrogare il server mentre l'interfaccia aspetta.
     */
    private static void preload(MetadataReader reader, String catalog) {
        if (reader == null) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                reader.allTables(catalog);
            } catch (java.sql.SQLException | RuntimeException e) {
                // non è grave: si leggeranno quando servono
            }
        }, "RamaSQL - metadati per la query visiva");
        t.setDaemon(true);
        t.start();
    }

    /** Rilegge l'elenco delle tabelle e viste a sinistra del diagramma (dopo un cambio del catalogo). */
    public void refreshObjects() {
        QbOperations.refreshObjects(builder);
    }

    // ---------------------------------------------------------------- costruzione

    /**
     * Barra della scheda, come quella dei dati (DESIGN-SYSTEM §3.4): a sinistra l'interruttore segmentato
     * <em>Grafica | SQL</em> e il catalogo; a destra <em>Salva .sql…</em>, <em>Interrompi</em> ed <em>Esegui</em>
     * (primario). Sotto, la fascia d'avviso quando il testo non si può disegnare.
     */
    private JComponent buildTop() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(Tokens.BG_WINDOW);
        bar.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12)));
        ButtonGroup views = new ButtonGroup();
        views.add(graphicToggle);
        views.add(sqlToggle);
        graphicToggle.setSelected(true);
        graphicToggle.setIcon(AppIcons.small(AppIcons.VISUAL_QUERY));
        sqlToggle.setIcon(AppIcons.small(AppIcons.NEW_QUERY));
        graphicToggle.putClientProperty("JButton.buttonType", "segmented");
        graphicToggle.putClientProperty("JButton.segmentPosition", "first");
        sqlToggle.putClientProperty("JButton.buttonType", "segmented");
        sqlToggle.putClientProperty("JButton.segmentPosition", "last");
        graphicToggle.setName("visualQuery.view.graphic");
        sqlToggle.setName("visualQuery.view.sql");
        graphicToggle.setToolTipText(Texts.get("visual.view.graphic.tooltip"));
        sqlToggle.setToolTipText(Texts.get("visual.view.sql.tooltip"));
        graphicToggle.setFocusable(false);
        sqlToggle.setFocusable(false);
        graphicToggle.addActionListener(e -> showGraphic());
        sqlToggle.addActionListener(e -> showSql());
        bar.add(graphicToggle);
        bar.add(sqlToggle);
        bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_16)));
        JLabel catalogLabel = new JLabel(catalog, AppIcons.treeCatalog(), SwingConstants.LEADING);
        catalogLabel.setName("visualQuery.catalog");
        catalogLabel.setIconTextGap(Tokens.px(Tokens.SPACE_4));
        catalogLabel.setForeground(Tokens.TEXT_SECONDARY);
        catalogLabel.setToolTipText(Texts.get("visual.catalog.tooltip", catalog));
        bar.add(catalogLabel);
        bar.add(Box.createHorizontalGlue());

        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setOpaque(false);
        saveButton.setName("visualQuery.save");
        saveButton.setToolTipText(Texts.get("visual.save.tooltip"));
        saveButton.setIcon(AppIcons.small(AppIcons.EXPORT));
        Styles.toolbarButton(saveButton);
        saveButton.addActionListener(e -> save());
        stopButton.setName("visualQuery.stop");
        stopButton.setToolTipText(Texts.get("visual.stop.tooltip"));
        stopButton.setFocusable(false);
        Styles.outline(stopButton, Tokens.DANGER, Tokens.DANGER_TINT);
        stopButton.addActionListener(e -> cancelRun());
        runButton.setName("visualQuery.run");
        runButton.setToolTipText(Texts.get("visual.run.tooltip"));
        runButton.setFocusable(false);
        runButton.setIcon(AppIcons.onAccent(AppIcons.small(AppIcons.RUN)));
        runButton.setDisabledIcon(AppIcons.small(AppIcons.RUN).getDisabledIcon());
        Styles.primary(runButton, Tokens.ACCENT);
        runButton.addActionListener(e -> run());
        if (viewSaver != null) {
            saveAsViewButton.setName("visualQuery.saveAsView");
            saveAsViewButton.setToolTipText(Texts.get("visual.saveAsView.tooltip"));
            saveAsViewButton.setIcon(AppIcons.small(AppIcons.NEW_VIEW));
            Styles.toolbarButton(saveAsViewButton);
            saveAsViewButton.addActionListener(e -> enterViewMode(null, false));
            actions.add(saveAsViewButton);
            actions.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_4)));
        }
        actions.add(saveButton);
        actions.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_12)));
        actions.add(stopButton);
        actions.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
        actions.add(runButton);
        bar.add(actions);

        notice.setName("visualQuery.notice");
        notice.setEditable(false);
        notice.setFocusable(false);
        notice.setLineWrap(true);
        notice.setWrapStyleWord(true);
        notice.setOpaque(false);
        notice.setBorder(null);
        notice.setFont(javax.swing.UIManager.getFont("Label.font"));
        notice.setForeground(Tokens.TEXT_PRIMARY);
        JLabel noticeIcon = new JLabel(AppIcons.small(AppIcons.STATUS_WARNING));
        noticeIcon.setVerticalAlignment(SwingConstants.TOP);
        noticeIcon.setBorder(BorderFactory.createEmptyBorder(Tokens.px(2), 0, 0, Tokens.px(Tokens.SPACE_8)));
        noticeBand.setBackground(Tokens.WARNING_TINT);
        noticeBand.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, Tokens.px(3), 0, 0, Tokens.WARNING),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12),
                        Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12))));
        noticeBand.add(noticeIcon, BorderLayout.WEST);
        noticeBand.add(notice, BorderLayout.CENTER);
        noticeBand.setVisible(false);

        // riga della vista (Step 8): «Nome della vista» [campo] [Salva vista]; compare solo in modalità vista
        viewBar.setLayout(new BoxLayout(viewBar, BoxLayout.X_AXIS));
        viewBar.setName("visualQuery.viewBar");
        viewBar.setBackground(Tokens.BG_WINDOW);
        viewBar.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_8),
                Tokens.px(Tokens.SPACE_12)));
        JLabel nameLabel = new JLabel(Texts.get("visual.view.name"), AppIcons.small(AppIcons.NEW_VIEW),
                SwingConstants.LEADING);
        nameLabel.setIconTextGap(Tokens.px(Tokens.SPACE_8));
        nameLabel.setLabelFor(viewName);
        viewName.setName("visualQuery.viewName");
        viewName.setToolTipText(Texts.get("visual.view.name.tooltip"));
        viewName.putClientProperty("JTextField.placeholderText", Texts.get("visual.view.name.placeholder"));
        viewName.setMaximumSize(new java.awt.Dimension(Tokens.px(320), viewName.getPreferredSize().height));
        viewName.addActionListener(e -> saveView());
        saveViewButton.setName("visualQuery.saveView");
        saveViewButton.setToolTipText(Texts.get("visual.view.save.tooltip"));
        saveViewButton.setFocusable(false);
        Styles.primary(saveViewButton, Tokens.ACCENT);
        saveViewButton.addActionListener(e -> saveView());
        viewBar.add(nameLabel);
        viewBar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
        viewBar.add(viewName);
        viewBar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
        viewBar.add(saveViewButton);
        viewBar.add(Box.createHorizontalGlue());
        viewBar.setVisible(false);

        JPanel rows = new JPanel(new BorderLayout());
        rows.add(bar, BorderLayout.NORTH);
        rows.add(viewBar, BorderLayout.SOUTH);
        JPanel top = new JPanel(new BorderLayout());
        top.add(rows, BorderLayout.NORTH);
        top.add(noticeBand, BorderLayout.SOUTH);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.BORDER_SUBTLE));
        return top;
    }

    /** Pulsanti in più della barra (lo Step 8 aggiunge «Salva come vista…»), a sinistra di «Salva .sql…». */
    protected void addAction(JComponent action) {
        actions.add(action, 0);
        actions.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)), 1);
    }

    // ---------------------------------------------------------------- sincronizzazione Grafica ⇄ SQL

    /** Il diagramma è cambiato: se comanda lui (vista Grafica), il testo si rigenera. */
    private void diagramChanged() {
        if (syncing || !isGraphicShown()) {
            return;
        }
        String sql = QbOperations.sql(builder);
        if (!sql.equals(editor.getText())) {
            syncing = true;
            try {
                editor.setText(sql);
            } finally {
                syncing = false;
            }
        }
        lastDiagramSql = sql;
    }

    /**
     * Mostra il diagramma. Se il testo è stato cambiato a mano lo si ridisegna, solo se rappresentabile.
     *
     * @return {@code false} se il testo non si può disegnare (si resta sulla vista SQL, con l'avviso)
     */
    public boolean showGraphic() {
        String text = editor.getText();
        if (!text.equals(lastDiagramSql)) {
            if (text.isBlank()) {
                QbOperations.clear(builder);
            } else {
                QbSql.Result check = QbSql.check(text);
                if (!check.representable() || !QbOperations.hasTables(check.model())) {
                    // non si disegna (o non c'è niente da disegnare: SELECT NOW()): il testo resta com'è
                    return stayOnText(VisualReasons.of(text, check));
                }
                QbOperations.load(builder, check);
                // check verifica il parser; il diagramma, caricando, può ancora riscrivere (join, colonne): se il suo
                // SQL non è equivalente al testo, il testo resta com'è e il diagramma si svuota
                if (!sameQuery(text, QbOperations.sql(builder), check)) {
                    QbOperations.clear(builder);
                    lastDiagramSql = "";
                    return stayOnText(Texts.get("visual.reason.diagramRewrites"));
                }
            }
        }
        hideNotice();
        graphicToggle.setSelected(true);
        editor.showAlternateView(true);
        diagramChanged();   // il testo prende la forma scritta dal diagramma (equivalente, verificato da check)
        return true;
    }

    /** Mostra il testo SQL (sempre possibile). */
    public void showSql() {
        diagramChanged();
        sqlToggle.setSelected(true);
        editor.showAlternateView(false);
        editor.textArea().requestFocusInWindow();
    }

    public boolean isGraphicShown() {
        return editor.isAlternateViewShown();
    }

    /**
     * Imposta la query dal testo (come se l'utente l'avesse scritta nella vista SQL) e prova a passare alla Grafica.
     *
     * @return {@code true} se è rappresentabile (diagramma ricostruito)
     */
    public boolean setSql(String sql) {
        if (isGraphicShown()) {
            sqlToggle.setSelected(true);
            editor.showAlternateView(false);
        }
        editor.setText(sql == null ? "" : sql);
        return showGraphic();
    }

    /** Il testo SQL della query, quello che si esegue e si salva. */
    public String sql() {
        if (isGraphicShown()) {
            diagramChanged();
        }
        return editor.getText();
    }

    /**
     * Il diagramma ha riscritto la stessa query? Confronto stretto ({@link QbSql#equivalent}); con una sola tabella nel
     * FROM si ammette in più che il diagramma qualifichi le colonne con il nome (o l'alias) della tabella
     * ({@code cognome} → {@code studenti.cognome}): con una tabella sola non può cambiare il significato.
     */
    private static boolean sameQuery(String text, String drawn, QbSql.Result check) {
        if (QbSql.equivalent(text, drawn)) {
            return true;
        }
        var from = check.model().getQueryExpression().getQuerySpecification().getFromClause();
        if (from.length != 1 || !(from[0] instanceof com.sqleo.querybuilder.syntax.QueryTokens.Table table)) {
            return false;
        }
        String a = QbSql.normalize(text);
        String b = QbSql.normalize(drawn);
        for (String qualifier : new String[] {table.getName(), table.getAlias()}) {
            if (qualifier == null) {
                continue;
            }
            String q = java.util.regex.Pattern.quote(qualifier.replace("`", "").toLowerCase(java.util.Locale.ROOT));
            a = a.replaceAll("(?<![\\w.$@])" + q + "\\.", "");
            b = b.replaceAll("(?<![\\w.$@])" + q + "\\.", "");
        }
        return QbSql.equivalent(a, b);
    }

    /** Si resta sulla vista SQL, con il testo intatto e l'avviso del motivo. */
    private boolean stayOnText(String reason) {
        showNotice(Texts.get("visual.notRepresentable", reason));
        sqlToggle.setSelected(true);
        editor.showAlternateView(false);
        return false;
    }

    private void showNotice(String text) {
        notice.setText(text);
        noticeBand.setVisible(true);
        revalidate();
    }

    private void hideNotice() {
        notice.setText("");
        noticeBand.setVisible(false);
        revalidate();
    }

    // ---------------------------------------------------------------- modalità vista (Step 8)

    /**
     * Passa alla modalità vista: compare la riga con il nome e «Salva vista».
     *
     * @param name     nome della vista ({@code null} = da scrivere)
     * @param existing {@code true} se la vista esiste già (Modifica vista): si salva con {@code CREATE OR REPLACE}
     */
    public void enterViewMode(String name, boolean existing) {
        if (viewSaver == null) {
            return;
        }
        viewMode = true;
        replaceExisting = existing;
        if (name != null) {
            viewName.setText(name);
        }
        viewName.setEditable(!existing);   // una vista esistente non si rinomina da qui (si salverebbe una vista nuova)
        styleViewName();
        viewBar.setVisible(true);
        saveAsViewButton.setVisible(false);
        // una sola azione primaria nella scheda: «Salva vista»; «Esegui» (per vedere i dati) passa a contorno
        Styles.outline(runButton, Tokens.ACCENT, Tokens.ACCENT_TINT);
        runButton.setIcon(AppIcons.small(AppIcons.RUN));
        revalidate();
        if (name == null) {
            viewName.requestFocusInWindow();
        }
    }

    /** Il nome di una vista che esiste già si legge come un titolo, non come un campo spento. */
    private void styleViewName() {
        if (viewName.isEditable()) {
            return;
        }
        viewName.setBorder(BorderFactory.createEmptyBorder());
        viewName.setOpaque(false);
        viewName.putClientProperty("FlatLaf.style", "font: $rama.emphasis.font");
        viewName.setForeground(Tokens.TEXT_PRIMARY);
        viewName.setFocusable(false);
    }

    /**
     * Salva la query come vista: {@code CREATE VIEW} per una vista nuova, {@code CREATE OR REPLACE VIEW} per una
     * esistente, attraverso la pipeline (anteprima, conferma, registro).
     *
     * @return {@code false} se non è partito nulla (nome o query mancanti)
     */
    public boolean saveView() {
        if (viewSaver == null || !viewMode) {
            return false;
        }
        String name = viewName.getText().strip();
        if (name.isEmpty()) {
            showNotice(Texts.get("visual.view.nameMissing"));
            viewName.requestFocusInWindow();
            return false;
        }
        String select = sql().strip();
        if (select.isEmpty()) {
            showNotice(Texts.get("visual.view.queryMissing"));
            return false;
        }
        // in una vista i nomi delle colonne devono essere diversi (errore 1060): con due tabelle spuntate per intero
        // capita subito (libri.id, editori.id); lo si dice prima, con i nomi, invece di far fallire il server
        String doppie = duplicateOutputNames(select);
        if (doppie != null) {
            showNotice(Texts.get("visual.view.duplicateColumns", doppie));
            return false;
        }
        hideNotice();
        saveViewButton.setEnabled(false);
        viewSaver.save(catalog, name, select, replaceExisting, ok -> {
            saveViewButton.setEnabled(true);
            if (ok) {
                replaceExisting = true;   // da qui in poi è una vista che esiste: si sostituisce
                viewName.setEditable(false);
                styleViewName();
                savedViewSql = select;
                editor.setUnmodified();
                firePropertyChange(PROPERTY_VIEW_SAVED, null, name);
            }
        });
        return true;
    }

    /**
     * I nomi di colonna che la SELECT darebbe due volte, come «id (libri.id, editori.id)»; {@code null} se nessuno (o se
     * il testo non si analizza: allora decide il server). Il nome d'uscita è l'alias, se c'è, altrimenti il nome della
     * colonna.
     */
    static String duplicateOutputNames(String select) {
        QbSql.Result check = QbSql.check(select);
        if (check.model() == null) {
            return null;
        }
        java.util.Map<String, java.util.List<String>> byName = new java.util.LinkedHashMap<>();
        for (var e : check.model().getQueryExpression().getQuerySpecification().getSelectList()) {
            String shown;
            String name;
            if (e instanceof com.sqleo.querybuilder.syntax.QueryTokens.Column c) {
                name = c.getAlias() != null ? c.getAlias() : c.getName();
                shown = c.getTable() == null ? c.getName()
                        : com.sqleo.querybuilder.syntax.SQLFormatter.stripQuote(c.getTable().getReference()) + "."
                                + com.sqleo.querybuilder.syntax.SQLFormatter.stripQuote(c.getName());
            } else if (e instanceof com.sqleo.querybuilder.syntax.QueryTokens.DefaultExpression d) {
                String v = d.getValue() == null ? "" : d.getValue();
                name = d.getAlias() != null ? d.getAlias() : v.substring(v.lastIndexOf('.') + 1);
                shown = com.sqleo.querybuilder.syntax.SQLFormatter.stripQuote(v);
            } else {
                continue;
            }
            name = com.sqleo.querybuilder.syntax.SQLFormatter.stripQuote(name).toLowerCase(java.util.Locale.ROOT);
            byName.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(shown);
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        byName.forEach((n, where) -> {
            if (where.size() > 1) {
                out.add("«" + n + "» (" + String.join(", ", where) + ")");
            }
        });
        return out.isEmpty() ? null : String.join("; ", out);
    }

    /** Proprietà: la vista è stata salvata sul server (valore nuovo = nome della vista). */
    public static final String PROPERTY_VIEW_SAVED = "visualQuery.viewSaved";

    public boolean isViewMode() {
        return viewMode;
    }

    public String viewName() {
        return viewName.getText().strip();
    }

    /** {@code true} se «Salva vista» sostituirà una vista esistente ({@code CREATE OR REPLACE}). */
    public boolean replacesExistingView() {
        return replaceExisting;
    }

    /** Mostra un avviso nella fascia sotto la barra (es. il motivo per cui una vista si riapre come testo). */
    public void notice(String text) {
        showNotice(text);
    }

    // ---------------------------------------------------------------- esecuzione e file

    /** Esegue la query (tutto il testo) nel catalogo della scheda; {@code false} se non è partito nulla. */
    public boolean run() {
        sql();
        return editor.runAll();
    }

    public boolean cancelRun() {
        return editor.cancelRun();
    }

    public boolean isRunning() {
        return editor.isRunning();
    }

    /** Salva il testo in un file {@code .sql} (il diagramma si ricostruisce dal testo quando lo si riapre). */
    public boolean save() {
        sql();
        return editor.save();
    }

    private void updateActions() {
        boolean running = editor.isRunning();
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        firePropertyChange(SqlEditor.PROPERTY_RUNNING, !running, running);
    }

    // ---------------------------------------------------------------- per la finestra e per i test

    public String catalog() {
        return catalog;
    }

    public QueryBuilder queryBuilder() {
        return builder;
    }

    public SqlEditor editor() {
        return editor;
    }

    public String noticeText() {
        return noticeBand.isVisible() ? notice.getText() : "";
    }

    /**
     * Lavoro non salvato. In modalità vista: il testo è diverso da quello dell'ultima vista salvata (o riaperta); fuori:
     * il testo non è salvato in un file {@code .sql}, come nell'editor SQL.
     */
    public boolean isModified() {
        if (viewMode) {
            return !sql().strip().equals(savedViewSql);
        }
        return editor.isModified();
    }

    /**
     * Si può chiudere senza chiedere? In modalità vista la domanda (salvare la vista, scartare, restare) la fa chi chiude
     * la scheda; fuori, l'editor chiede se salvare il file {@code .sql}.
     */
    public boolean canClose() {
        if (viewMode) {
            return true;
        }
        return editor.canClose();
    }

    /** Il testo attuale è quello della vista sul server (appena riaperta o appena salvata). */
    public void markViewUnchanged() {
        savedViewSql = sql().strip();
        editor.setUnmodified();
    }

    public void dispose() {
        editor.dispose();
    }
}
