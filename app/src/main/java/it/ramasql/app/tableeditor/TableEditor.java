/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;
import it.ramasql.core.verify.SchemaVerifier;
import it.ramasql.core.verify.Verification;
import it.ramasql.core.verify.VerificationIssue;

/**
 * <b>Editor di tabelle</b> (DESIGN §3.6; Table Editor di Workbench ridotto): pannello da mettere in una scheda
 * dell'area di lavoro, con le schede Colonne, Indici, Chiavi esterne, Opzioni e SQL.
 *
 * <p>Il cuore è un modello immutabile: {@link #originalTable()} (com'è sul server; {@code null} per una tabella
 * nuova) e {@link #editedTable()} (come la vuole l'utente). Ogni gesto nelle schede produce un nuovo modello; a ogni
 * modifica si ricalcolano l'anteprima SQL ({@code TableDiff}, scheda SQL) e i <b>controlli prima</b> (errori evidenti,
 * avvisi di {@code FkPrecheck}/{@code IndexPrecheck}, troncamenti possibili), tutti senza contattare il server.
 *
 * <p><b>Applica</b> passa a {@link TableApplier} esattamente le istruzioni dell'anteprima; all'esito l'editor si
 * ricarica con la tabella <b>riletta dal server</b>, mostra cosa è stato applicato e cosa no e, se tutto è andato,
 * l'esito della <b>verifica dopo</b> ({@link SchemaVerifier}: «✔ verificato sul server» o le differenze).
 *
 * <p>Tutti i metodi vanno chiamati sull'EDT.
 */
public final class TableEditor extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final int TAB_COLUMNS = 0;
    public static final int TAB_INDEXES = 1;
    public static final int TAB_FOREIGN_KEYS = 2;
    public static final int TAB_OPTIONS = 3;
    public static final int TAB_SQL = 4;

    private final String catalog;
    private final ServerInfo server;
    private final CatalogTables tables;
    private final TableEditorPrompts prompts;
    private final TableApplier applier;
    private final DataCheck dataCheck;

    private TableDef original;
    private TableDef edited;
    private Checks checks;
    private List<String> preview = List.of();
    private boolean applying;
    private boolean refreshing;
    private Runnable onChange = () -> { };
    private Verification lastVerification;

    private final JTabbedPane tabs = new JTabbedPane();
    private final ColumnsTab columnsTab;
    private final IndexesTab indexesTab;
    private final ForeignKeysTab foreignKeysTab;
    private final OptionsTab optionsTab;
    private final SqlTab sqlTab = new SqlTab();
    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final Banner notice = new Banner("tableeditor.notice");
    private final Banner outcome = new Banner("tableeditor.outcome");
    private final JButton applyButton;
    private final JButton revertButton;

    /**
     * @param original  la tabella com'è sul server; {@code null} = nuova tabella
     * @param catalog   catalogo in cui sta (o starà) la tabella
     * @param server    tipo e versione del server (l'SQL generato ne tiene conto)
     * @param tables    le altre tabelle del catalogo (FK, controlli, blocco di MyISAM)
     * @param prompts   dialoghi modali
     * @param applier   chi esegue le istruzioni (dalla pipeline «anteprima SQL»)
     * @param dataCheck chi esegue le query di «Verifica dati»
     */
    public TableEditor(TableDef original, String catalog, ServerInfo server, CatalogTables tables,
            TableEditorPrompts prompts, TableApplier applier, DataCheck dataCheck) {
        super(new BorderLayout());
        this.catalog = catalog;
        this.server = Objects.requireNonNull(server, "server");
        this.tables = Objects.requireNonNull(tables, "tables");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.dataCheck = Objects.requireNonNull(dataCheck, "dataCheck");
        this.original = original == null ? null : original.withOrdinalPositions();
        this.edited = this.original != null ? this.original : newTable(catalog);
        this.checks = Checks.compute(this.original, edited, this::parentOf);
        setName("tableeditor");
        setBackground(Tokens.BG_WINDOW);

        columnsTab = new ColumnsTab(this);
        indexesTab = new IndexesTab(this);
        foreignKeysTab = new ForeignKeysTab(this);
        optionsTab = new OptionsTab(this);

        tabs.setName("tableeditor.tabs");
        tabs.addTab(Texts.get("tableeditor.tab.columns"), columnsTab);
        tabs.addTab(Texts.get("tableeditor.tab.indexes"), indexesTab);
        tabs.addTab(Texts.get("tableeditor.tab.fks"), foreignKeysTab);
        tabs.addTab(Texts.get("tableeditor.tab.options"), optionsTab);
        tabs.addTab(Texts.get("tableeditor.tab.sql"), sqlTab);
        tabs.putClientProperty("JTabbedPane.tabHeight", 36);
        tabs.putClientProperty("JTabbedPane.tabType", "underlined");
        tabs.setBackground(Tokens.BG_WINDOW);          // la striscia delle schede sta sul fondo della finestra
        tabs.putClientProperty("JTabbedPane.tabAreaInsets", new java.awt.Insets(0, Tokens.SPACE_8, 0, Tokens.SPACE_8));

        title.setName("tableeditor.title");
        Styles.text(title, "title");
        title.setForeground(Tokens.TEXT_PRIMARY);
        subtitle.setName("tableeditor.subtitle");
        subtitle.setForeground(Tokens.TEXT_SECONDARY);
        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_16, Tokens.SPACE_16,
                Tokens.SPACE_8, Tokens.SPACE_16));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);

        applyButton = Ui.primary("tableeditor.apply", Texts.get("tableeditor.apply"), this::apply);
        revertButton = Ui.button("tableeditor.revert", Texts.get("tableeditor.revert"), this::revert);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.SPACE_8, 0));
        buttons.setOpaque(false);
        buttons.add(revertButton);
        buttons.add(applyButton);
        JPanel south = new JPanel(new BorderLayout(0, Tokens.SPACE_8));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(Tokens.SPACE_12, Tokens.SPACE_16,
                        Tokens.SPACE_12, Tokens.SPACE_16)));
        JPanel messages = new JPanel(new BorderLayout(0, Tokens.SPACE_8));
        messages.setOpaque(false);
        messages.add(notice, BorderLayout.NORTH);
        messages.add(outcome, BorderLayout.CENTER);
        south.add(messages, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        refresh();
    }

    /** Tabella nuova proposta: {@code id INT NOT NULL AUTO_INCREMENT} chiave primaria, InnoDB (come Workbench). */
    private static TableDef newTable(String catalog) {
        ColumnDef id = ColumnDef.of("id", "INT").notNull().withAutoIncrement(true);
        return new TableDef(catalog, Texts.get("tableeditor.newTableName"), "InnoDB", null, null, "", null,
                List.of(id), List.of(IndexDef.primary("id")), List.of(), List.of());
    }

    // ================================================================ API pubblica

    /** La tabella com'è sul server (dopo un'applicazione: com'è stata riletta); {@code null} = non ancora creata. */
    public TableDef originalTable() {
        return original;
    }

    /** La tabella come l'ha modificata l'utente. */
    public TableDef editedTable() {
        return edited;
    }

    public boolean isNewTable() {
        return original == null;
    }

    /** Vero se «Applica» avrebbe qualcosa da eseguire. */
    public boolean isModified() {
        return !preview.isEmpty();
    }

    public boolean isApplying() {
        return applying;
    }

    /** Le istruzioni dell'anteprima, in ordine: sono quelle che «Applica» passa a {@link TableApplier}. */
    public List<String> previewStatements() {
        return preview;
    }

    /** Il testo della scheda SQL ({@code ""} se non ci sono modifiche). */
    public String previewText() {
        return sqlTab.sql();
    }

    /** Il testo dell'esito dell'ultima applicazione, come lo legge l'utente ({@code ""} se nessuna). */
    public String outcomeText() {
        return outcome.text();
    }

    /** L'esito dell'ultima verifica dopo l'applicazione; {@code null} se non c'è stata. */
    public Verification lastVerification() {
        return lastVerification;
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    public String catalog() {
        return catalog;
    }

    public ServerInfo server() {
        return server;
    }

    /** Chiamato dopo ogni modifica del modello e dopo ogni applicazione (es. per il titolo della scheda). */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange == null ? () -> { } : onChange;
    }

    /**
     * «Applica»: controlla gli errori evidenti (bloccano), chiede conferma se ci sono dati a rischio (troncamenti,
     * NOT NULL), poi passa le istruzioni dell'anteprima a {@link TableApplier}.
     */
    public void apply() {
        stopEditing();
        if (applying) {
            return;
        }
        List<String> statements = preview;
        if (statements.isEmpty() && checks.errors.isEmpty()) {
            prompts.showMessage(Texts.get("tableeditor.apply.title"), Texts.get("tableeditor.apply.nothing"));
            return;
        }
        if (!checks.errors.isEmpty()) {
            StringBuilder list = new StringBuilder();
            checks.errors.forEach(p -> list.append("\n• ").append(p.message()));
            prompts.showError(Texts.get("tableeditor.apply.errors.title"),
                    Texts.get("tableeditor.apply.errors", list.toString()));
            return;
        }
        List<Checks.Problem> risky = checks.truncationWarnings();
        if (!risky.isEmpty()) {
            StringBuilder list = new StringBuilder();
            risky.forEach(p -> list.append("\n• ").append(p.message()));
            if (!prompts.confirm(Texts.get("tableeditor.apply.risk.title"),
                    Texts.get("tableeditor.apply.risk", list.toString()),
                    Texts.get("tableeditor.apply.risk.confirm"))) {
                return;
            }
        }
        applying = true;
        refreshButtons();
        TableDef requestedOriginal = original;
        TableDef requested = edited;
        applier.apply(new TableApplier.ApplyRequest(requestedOriginal, requested, statements),
                result -> applied(requestedOriginal, requested, result));
    }

    /** «Annulla modifiche»: torna alla tabella com'è sul server (o alla proposta iniziale, se nuova). */
    public void revert() {
        stopEditing();
        edited = original != null ? original : newTable(catalog);
        optionsTab.showEngineBlocked(null);
        notice.clear();
        refresh();
    }

    // ================================================================ esito

    private void applied(TableDef requestedOriginal, TableDef requested, TableApplier.ApplyOutcome result) {
        applying = false;
        if (result.cancelled()) {
            refresh();
            return;
        }
        Verification verification = null;
        if (result.reloaded() != null) {
            TableDef reloaded = result.reloaded().withOrdinalPositions();
            if (result.error() == null) {
                verification = SchemaVerifier.verify(requestedOriginal, requested, reloaded);
            }
            original = reloaded;
            edited = reloaded;
        } else if (result.error() == null) {
            original = requested.withOrdinalPositions();     // riletta non disponibile: vale ciò che si è chiesto
            edited = original;
        }
        lastVerification = verification;
        optionsTab.showEngineBlocked(null);
        notice.clear();
        outcome.set(outcomeLines(result, verification));
        refresh();
    }

    private static List<Banner.Line> outcomeLines(TableApplier.ApplyOutcome result, Verification verification) {
        List<Banner.Line> lines = new ArrayList<>();
        int total = result.applied().size() + result.notApplied().size();
        if (result.error() == null) {
            lines.add(new Banner.Line(Banner.Tone.SUCCESS, total == 1 ? Texts.get("tableeditor.outcome.okOne")
                    : Texts.get("tableeditor.outcome.ok", total)));
        } else {
            lines.add(new Banner.Line(Banner.Tone.DANGER, Texts.get("tableeditor.outcome.failed",
                    result.applied().size(), total, result.applied().size() + 1)));
            String error = Texts.get("tableeditor.outcome.error", result.error().code(), result.error().message());
            if (!result.error().explanation().isBlank()) {
                error += "\n" + result.error().explanation();
            }
            lines.add(new Banner.Line(Banner.Tone.DANGER, error));
        }
        for (String s : result.applied()) {
            lines.add(new Banner.Line(Banner.Tone.NEUTRAL, Texts.get("tableeditor.outcome.applied", compact(s))));
        }
        for (int i = 0; i < result.notApplied().size(); i++) {
            String key = i == 0 && result.error() != null ? "tableeditor.outcome.failedStatement"
                    : "tableeditor.outcome.notRun";
            lines.add(new Banner.Line(Banner.Tone.NEUTRAL, Texts.get(key, compact(result.notApplied().get(i)))));
        }
        if (result.reloaded() != null) {
            lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("tableeditor.outcome.reloaded")));
        } else if (result.error() != null) {
            lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("tableeditor.outcome.notReloaded")));
        }
        if (verification != null) {
            String head = verification.summary().lines().findFirst().orElse("");
            if (head.startsWith("✔")) {
                head = head.substring(1).strip();
            }
            lines.add(new Banner.Line(verification.conforming() ? Banner.Tone.SUCCESS : Banner.Tone.DANGER, head));
            for (VerificationIssue issue : verification.blocking()) {
                lines.add(new Banner.Line(Banner.Tone.DANGER, issue.message()));
            }
            for (VerificationIssue issue : verification.informational()) {
                lines.add(new Banner.Line(Banner.Tone.INFO, issue.message()));
            }
        }
        return lines;
    }

    /** Un'istruzione su una riga, accorciata se lunga. */
    static String compact(String sql) {
        String one = sql.replaceAll("\\s*\\n\\s*", " ").strip();
        return one.length() > 160 ? one.substring(0, 157) + "…" : one;
    }

    // ================================================================ per le schede

    /** Applica una modifica al modello e aggiorna tutto (anteprima, controlli, schede). */
    void update(UnaryOperator<TableDef> change) {
        if (refreshing || applying) {
            return;
        }
        TableDef next = change.apply(edited);
        if (next == null || next.equals(edited)) {
            return;
        }
        edited = next;
        notice.clear();
        refresh();
    }

    Checks checks() {
        return checks;
    }

    CatalogTables tables() {
        return tables;
    }

    DataCheck dataCheck() {
        return dataCheck;
    }

    /** La tabella com'è sul server, qualificata con il catalogo: è quella su cui girano le query di controllo. */
    TableDef serverTable() {
        TableDef t = original != null ? original : edited;
        return t.catalog() == null && catalog != null ? t.withCatalog(catalog) : t;
    }

    /** La tabella riferita da una FK: questa stessa (autoreferenziale) o una del catalogo; {@code null} se ignota. */
    TableDef parentOf(ForeignKeyDef fk) {
        if (fk.refTable().isBlank()) {
            return null;
        }
        boolean sameCatalog = fk.refCatalog() == null || catalog == null || fk.refCatalog().equalsIgnoreCase(catalog);
        return sameCatalog ? tableNamed(fk.refTable()) : null;
    }

    TableDef tableNamed(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.equalsIgnoreCase(edited.name()) || (original != null && name.equalsIgnoreCase(original.name()))) {
            return edited;
        }
        return tables.table(name).orElse(null);
    }

    /**
     * Cambio di engine chiesto dalla scheda Opzioni (o da «Converti in InnoDB»). Verso MyISAM è <b>bloccato con
     * spiegazione</b> se la tabella ha chiavi esterne o è riferita da quelle di altre tabelle (T5.7).
     *
     * @return {@code true} se il cambio è stato fatto
     */
    boolean requestEngine(String engine) {
        if (engine == null) {
            return false;
        }
        String wanted = OptionsTab.canonicalEngine(engine);
        String current = OptionsTab.canonicalEngine(edited.engine() == null ? "InnoDB" : edited.engine());
        if (wanted.equals(current)) {
            optionsTab.showEngineBlocked(null);
            refresh();
            return true;
        }
        if (!wanted.equalsIgnoreCase("InnoDB")) {
            String blocked = engineBlockers();
            if (blocked != null) {
                optionsTab.showEngineBlocked(blocked);
                refresh();                  // la scelta torna all'engine attuale
                return false;
            }
        }
        optionsTab.showEngineBlocked(null);
        update(t -> t.withEngine(wanted));
        return true;
    }

    private String engineBlockers() {
        List<String> own = edited.foreignKeys().stream().map(f -> f.name() == null ? "?" : f.name()).toList();
        List<String> incoming = new ArrayList<>();
        if (original != null) {
            for (CatalogTables.IncomingReference r : tables.referencing(original.name())) {
                if (!r.table().equalsIgnoreCase(original.name())) {
                    incoming.add(r.table() + "." + r.constraint());
                }
            }
        }
        if (own.isEmpty() && incoming.isEmpty()) {
            // elenco vuoto: sicuro solo se la lettura dei metadati è riuscita davvero
            return tables.isComplete() ? null : Texts.get("tableeditor.options.engine.unknown", edited.name());
        }
        StringBuilder sb = new StringBuilder(Texts.get("tableeditor.options.engine.blocked", edited.name()));
        if (!incoming.isEmpty()) {
            sb.append('\n').append(Texts.get("tableeditor.options.engine.blockedIncoming", String.join(", ", incoming)));
        }
        if (!own.isEmpty()) {
            sb.append('\n').append(Texts.get("tableeditor.options.engine.blockedOwn", String.join(", ", own)));
        }
        return sb.toString();
    }

    /** Un avviso passeggero sotto le schede (es. «c'è già una chiave primaria»). */
    void notice(String message) {
        notice.set(Banner.Tone.WARNING, message);
    }

    String noticeText() {
        return notice.text();
    }

    ColumnsTab columnsTab() {
        return columnsTab;
    }

    IndexesTab indexesTab() {
        return indexesTab;
    }

    ForeignKeysTab foreignKeysTab() {
        return foreignKeysTab;
    }

    OptionsTab optionsTab() {
        return optionsTab;
    }

    SqlTab sqlTab() {
        return sqlTab;
    }

    JButton applyButton() {
        return applyButton;
    }

    // ================================================================ aggiornamento

    private void stopEditing() {
        columnsTab.stopEditing();
        indexesTab.stopEditing();
        foreignKeysTab.stopEditing();
    }

    private void refresh() {
        refreshing = true;
        try {
            checks = Checks.compute(original, edited, this::parentOf);
            boolean complete;
            try {
                preview = TableDiff.diff(original, edited, server);
                complete = true;
            } catch (IllegalArgumentException e) {
                preview = List.of();      // nome vuoto o coppia incompleta: l'SQL non si può ancora scrivere
                complete = false;
            }
            columnsTab.refresh();
            indexesTab.refresh();
            foreignKeysTab.refresh();
            optionsTab.refresh();
            sqlTab.show(preview, complete);
            refreshHeader();
            refreshTabTitles();
            refreshButtons();
        } finally {
            refreshing = false;
        }
        onChange.run();
    }

    private void refreshHeader() {
        title.setText(edited.name().isBlank() ? Texts.get("tableeditor.untitled") : edited.name());
        String engine = edited.engine() == null ? "InnoDB" : edited.engine();
        String state = original == null ? Texts.get("tableeditor.state.new")
                : isModified() ? Texts.get("tableeditor.state.modified") : Texts.get("tableeditor.state.saved");
        subtitle.setText(Texts.get("tableeditor.subtitle", catalog == null ? "" : catalog, engine, state));
    }

    private void refreshTabTitles() {
        title(TAB_COLUMNS, "tableeditor.tab.columns", Checks.Area.COLUMNS);
        title(TAB_INDEXES, "tableeditor.tab.indexes", Checks.Area.INDEXES);
        title(TAB_FOREIGN_KEYS, "tableeditor.tab.fks", Checks.Area.FOREIGN_KEYS);
        title(TAB_OPTIONS, "tableeditor.tab.options", Checks.Area.OPTIONS);
        boolean innoDb = edited.isInnoDb();
        tabs.setForegroundAt(TAB_FOREIGN_KEYS, innoDb ? null : Tokens.TEXT_TERTIARY);
        tabs.setToolTipTextAt(TAB_FOREIGN_KEYS, innoDb ? null : Texts.get("tableeditor.fks.myisam.title"));
        int statements = preview.size();
        tabs.setTitleAt(TAB_SQL, statements == 0 ? Texts.get("tableeditor.tab.sql")
                : Texts.get("tableeditor.tab.sqlCount", statements));
    }

    private void title(int tab, String key, Checks.Area area) {
        String text = Texts.get(key);
        if (!checks.errors(area).isEmpty()) {
            text += "  " + Banner.Tone.DANGER.glyph;
        } else if (!checks.warnings(area).isEmpty()) {
            text += "  " + Banner.Tone.WARNING.glyph;
        }
        tabs.setTitleAt(tab, text);
    }

    private void refreshButtons() {
        applyButton.setEnabled(!applying && (!preview.isEmpty() || !checks.errors.isEmpty()));
        revertButton.setEnabled(!applying && (!preview.isEmpty() || !edited.equals(original)));
    }
}
