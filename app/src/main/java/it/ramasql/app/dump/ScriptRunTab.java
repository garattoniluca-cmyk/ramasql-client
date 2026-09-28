/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.dump;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import it.ramasql.app.Texts;
import it.ramasql.app.editor.ErrorExplainer;
import it.ramasql.app.pipeline.SqlPipeline;
import it.ramasql.app.tableeditor.Banner;
import it.ramasql.app.tableeditor.Ui;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.core.exec.CollationCompat;
import it.ramasql.core.exec.ScriptFileListener;
import it.ramasql.core.exec.ScriptFileResult;
import it.ramasql.core.exec.ScriptPreview;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.CollationInfo;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.sqlgen.SqlIdentifiers;

/**
 * Scheda «Esegui script SQL» ({@code DESIGN.md} §3.9, voce distinta del menu <em>Importa</em>; come «Data Import /
 * Restore» di Workbench ridotto al file unico): ripristina un dump del client, di {@code mysqldump} o di Navicat, o
 * esegue qualunque script {@code .sql}, anche di milioni di righe, <b>a flusso</b>.
 * <ul>
 *   <li>il file si legge tutto una volta in sottofondo: quante istruzioni, quali distruttive, quali cataloghi usa;</li>
 *   <li>il catalogo in cui eseguire: quello che lo script sceglie da sé ({@code USE} o {@code CREATE DATABASE}: allora
 *       non se ne può scegliere un altro) o uno del server, scelto <b>esplicitamente</b> (nessuna scelta predefinita),
 *       che diventa un {@code USE} mostrato nell'anteprima;</li>
 *   <li>il riepilogo del file dice anche la codifica (se non è UTF-8), le collation che il server non conosce e che si
 *       sostituiscono, le impostazioni della sessione che lo script cambia, e se è un dump di RamaSQL incompleto;</li>
 *   <li>in caso d'errore: <em>fermati</em> (predefinito) o <em>continua</em>;</li>
 *   <li><em>Esegui</em> passa dalla pipeline: anteprima con le prime istruzioni e il numero totale, conferma rafforzata
 *       se il file contiene istruzioni distruttive (anche in fondo); poi esecuzione in autocommit con avanzamento e
 *       <em>Interrompi</em>; alla fine il rapporto con riga, istruzione ed errore spiegato, gli avvisi del server, la
 *       transazione lasciata aperta dal file e, se il file ha lasciato cambiata la sessione, il pulsante che la rimette
 *       com'era (dalla pipeline).</li>
 * </ul>
 */
public final class ScriptRunTab extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient SqlPipeline pipeline;
    private final transient MetadataReader reader;
    private final transient FilePrompts files;
    private final String proposedCatalog;

    private transient Path file;
    private transient ScriptPreview preview;
    private boolean scanning;
    private boolean running;
    private transient AtomicBoolean cancelScan = new AtomicBoolean();
    private transient ScriptFileResult result;
    private transient List<CatalogInfo> catalogs = List.of();
    /** Le collation del server (per sostituire quelle che non conosce); {@code null} finché non sono lette. */
    private transient List<CollationInfo> collations;
    private transient CollationCompat compat = CollationCompat.none();
    private transient List<String> summaryLines = List.of();

    private final JTextField fileField = new JTextField();
    private final JButton chooseButton;
    private final JTextArea fileSummary = Banner.wrapText(" ", Tokens.TEXT_SECONDARY);
    private final JComboBox<String> targetCombo = new JComboBox<>();
    private final JRadioButton stopRadio = new JRadioButton(Texts.get("script.onError.stop"), true);
    private final JRadioButton continueRadio = new JRadioButton(Texts.get("script.onError.continue"));
    private final JButton runButton;
    private final JButton stopButton;
    private final JProgressBar progress = new JProgressBar();
    private final JLabel progressLabel = new JLabel(" ");
    private final Banner resultBanner = new Banner("script.result");
    private final FailureModel failureModel = new FailureModel();
    private final JTable failureTable = new JTable(failureModel);
    private final JButton sessionButton;

    /** Voce «(il catalogo scelto dallo script)» della lista dei cataloghi. */
    static final String FROM_SCRIPT = "";
    /** Voce «(scegli il catalogo)»: nessuna scelta, «Esegui» spento. */
    public static final String NONE = "\u0001";

    public ScriptRunTab(SqlPipeline pipeline, MetadataReader reader, FilePrompts files, String catalog) {
        super(new BorderLayout());
        this.pipeline = pipeline;
        this.reader = reader;
        this.files = files;
        this.proposedCatalog = catalog;
        setName("script.tab");
        setBackground(Tokens.BG_SURFACE);
        chooseButton = Ui.button("script.file.choose", Texts.get("script.file.choose"), this::chooseFile);
        runButton = Ui.primary("script.run", Texts.get("script.run"), this::start);
        stopButton = Ui.button("script.stop", Texts.get("script.stop"), this::interrupt);
        Styles.outline(stopButton, Tokens.DANGER, Tokens.DANGER_TINT);
        sessionButton = Ui.button("script.session", Texts.get("script.session"), this::restoreSession);

        JPanel top = new JPanel(new GridBagLayout());
        top.setBackground(Tokens.BG_SURFACE);
        top.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(Tokens.px(Tokens.SPACE_4), 0, Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        top.add(Styles.text(new JLabel(Texts.get("script.title")), "heading", Tokens.TEXT_PRIMARY), c);
        c.gridwidth = 1;
        c.gridy = 1;
        top.add(label("script.file.label"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        fileField.setName("script.file");
        fileField.setEditable(false);
        top.add(fileField, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        top.add(chooseButton, c);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        fileSummary.setName("script.file.summary");
        top.add(fileSummary, c);
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 0;
        c.gridy = 3;
        top.add(label("script.target.label"), c);
        c.gridx = 1;
        targetCombo.setName("script.target");
        it.ramasql.app.theme.ComboTips.install(targetCombo, v -> FROM_SCRIPT.equals(v)
                ? it.ramasql.app.theme.Tips.titled(Texts.get("script.target.fromScript"), Texts.get("script.target.fromScript.tooltip"))
                : NONE.equals(v) ? it.ramasql.app.theme.Tips.titled(Texts.get("script.target.none"), Texts.get("script.target.none.tooltip"))
                : it.ramasql.app.theme.Tips.titled(v, Texts.get("script.target.catalog.tooltip", v)));
        targetCombo.addActionListener(e -> {
            if (!refreshingTargets) {
                targetChosen = true;
                refreshButtons();
            }
        });
        targetCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                String text = FROM_SCRIPT.equals(value) ? Texts.get("script.target.fromScript")
                        : NONE.equals(value) ? Texts.get("script.target.none") : String.valueOf(value);
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        Dimension d = new Dimension(Tokens.px(280), targetCombo.getPreferredSize().height);
        targetCombo.setPreferredSize(d);
        targetCombo.setMinimumSize(d);
        top.add(targetCombo, c);
        c.gridx = 0;
        c.gridy = 4;
        top.add(label("script.onError.label"), c);
        JPanel radios = new JPanel();
        radios.setLayout(new BoxLayout(radios, BoxLayout.X_AXIS));
        radios.setOpaque(false);
        ButtonGroup g = new ButtonGroup();
        g.add(stopRadio);
        g.add(continueRadio);
        stopRadio.setName("script.onError.stop");
        continueRadio.setName("script.onError.continue");
        radios.add(stopRadio);
        radios.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_16)));
        radios.add(continueRadio);
        c.gridx = 1;
        c.gridwidth = 2;
        top.add(radios, c);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel bar = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_12), 0));
        bar.setOpaque(false);
        progress.setName("script.progress");
        bar.add(progress, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setOpaque(false);
        buttons.add(stopButton);
        buttons.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
        buttons.add(runButton);
        bar.add(buttons, BorderLayout.EAST);
        top.add(bar, c);
        add(top, BorderLayout.NORTH);
        // sotto: avanzamento, esito e tabella degli errori, che prende tutto lo spazio che resta
        JPanel center = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_8)));
        center.setBackground(Tokens.BG_SURFACE);
        center.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_16),
                Tokens.px(Tokens.SPACE_24)));
        JPanel outcome = new JPanel();
        outcome.setLayout(new BoxLayout(outcome, BoxLayout.Y_AXIS));
        outcome.setOpaque(false);
        progressLabel.setName("script.progressLabel");
        progressLabel.setForeground(Tokens.TEXT_SECONDARY);
        sessionButton.setVisible(false);
        for (JComponent x : new JComponent[] {progressLabel, resultBanner, sessionButton}) {
            x.setAlignmentX(LEFT_ALIGNMENT);
            outcome.add(x);
            outcome.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)));
        }
        center.add(outcome, BorderLayout.NORTH);
        failureTable.setName("script.failures");
        Ui.styleTable(failureTable);
        Ui.narrow(failureTable.getColumnModel().getColumn(0), Tokens.px(72));
        failureTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void setValue(Object value) {
                String s = value == null ? "" : value.toString();
                setText(s.replace('\n', ' '));
                setToolTipText(s.length() > 30 ? s : null);
            }
        });
        failureTable.setPreferredScrollableViewportSize(new Dimension(Tokens.px(400), Tokens.px(Tokens.ROW_HEIGHT) * 4));
        center.add(Ui.scroll(failureTable), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        for (JComponent x : new JComponent[] {fileField, chooseButton, targetCombo, stopRadio, continueRadio, runButton,
            stopButton, progress, failureTable, sessionButton}) {
            x.setToolTipText(Texts.get(x.getName() + ".tooltip"));
        }
        runButton.setEnabled(false);
        stopButton.setEnabled(false);
        loadCatalogs();
    }

    private static JLabel label(String key) {
        return Styles.text(new JLabel(Texts.get(key)), "emphasis", Tokens.TEXT_SECONDARY);
    }

    private void loadCatalogs() {
        new SwingWorker<List<CatalogInfo>, Void>() {
            private List<CollationInfo> read = List.of();

            @Override
            protected List<CatalogInfo> doInBackground() throws SQLException {
                try {
                    read = reader.collations();
                } catch (SQLException e) {
                    read = List.of();   // senza l'elenco non si sostituisce nulla
                }
                return reader.catalogs().stream().filter(x -> !x.system()).toList();
            }

            @Override
            protected void done() {
                try {
                    catalogs = get();
                } catch (Exception e) {
                    catalogs = List.of();
                }
                collations = read;
                updateCompat();
                refreshTargets();
            }
        }.execute();
    }

    /** L'utente ha scelto il catalogo a mano: non si cambia più da soli. */
    private boolean targetChosen;
    private boolean refreshingTargets;

    private void refreshTargets() {
        Object keep = targetChosen ? targetCombo.getSelectedItem() : null;
        refreshingTargets = true;
        try {
            fillTargets(keep);
        } finally {
            refreshingTargets = false;
        }
    }

    /** Il file sceglie da sé il catalogo: allora non se ne può scegliere un altro (le sue istruzioni andrebbero lì). */
    private boolean scriptChoosesCatalog() {
        return preview != null && (preview.hasUse() || preview.createsCatalog());
    }

    private void fillTargets(Object keep) {
        targetCombo.removeAllItems();
        if (scriptChoosesCatalog()) {
            targetCombo.addItem(FROM_SCRIPT);
            targetCombo.setSelectedItem(FROM_SCRIPT);
            targetCombo.setToolTipText(Texts.get("script.target.locked"));
            return;
        }
        targetCombo.setToolTipText(Texts.get("script.target.tooltip"));
        targetCombo.addItem(NONE);
        for (CatalogInfo c : catalogs) {
            targetCombo.addItem(c.name());
        }
        javax.swing.DefaultComboBoxModel<String> m = (javax.swing.DefaultComboBoxModel<String>) targetCombo.getModel();
        if (keep != null && m.getIndexOf(keep) >= 0) {
            targetCombo.setSelectedItem(keep);
        } else if (proposedCatalog != null && m.getIndexOf(proposedCatalog) >= 0) {
            targetCombo.setSelectedItem(proposedCatalog);
        } else {
            targetCombo.setSelectedItem(NONE);   // nessuna scelta al posto dell'utente
        }
    }

    /** Il catalogo è scelto (o lo sceglie lo script). */
    private boolean hasTarget() {
        Object t = targetCombo.getSelectedItem();
        return t != null && !NONE.equals(t);
    }

    /** Le collation del file che il server non conosce, da sostituire. */
    private void updateCompat() {
        compat = preview == null || collations == null ? CollationCompat.none()
                : CollationCompat.of(preview.collations(), collations);
        showSummary();
    }

    private void showSummary() {
        List<String> lines = new ArrayList<>(summaryLines);
        if (!compat.isEmpty()) {
            lines.add(Texts.get("script.file.collations", String.join(", ", compat.replacements().entrySet().stream()
                    .map(e -> e.getKey() + " → " + e.getValue()).toList())));
        }
        if (scriptChoosesCatalog()) {
            lines.add(Texts.get("script.target.locked"));
        }
        if (!lines.isEmpty()) {
            fileSummary.setText(String.join("\n", lines));
        }
    }

    // ================================================================ file

    /** «Scegli…»: il file .sql, poi la lettura di prova in sottofondo. */
    public void chooseFile() {
        Path p = files.chooseToOpen(FilePrompts.Purpose.RUN_SCRIPT);
        if (p != null) {
            setFile(p);
        }
    }

    public void setFile(Path p) {
        file = p;
        preview = null;
        result = null;
        summaryLines = List.of();
        compat = CollationCompat.none();
        fileField.setText(p.toString());
        fileSummary.setText(Texts.get("script.file.reading"));
        resultBanner.clear();
        failureModel.set(List.of());
        sessionButton.setVisible(false);
        scanning = true;
        // la lettura di prova di un file scelto prima si annulla: il suo esito non conta più
        cancelScan.set(true);
        AtomicBoolean token = new AtomicBoolean();
        cancelScan = token;
        refreshButtons();
        new SwingWorker<ScriptPreview, Void>() {
            @Override
            protected ScriptPreview doInBackground() throws Exception {
                return ScriptPreview.scan(p, SqlOrigin.SCRIPT_FILE.label(), token::get);
            }

            @Override
            protected void done() {
                if (token != cancelScan) {
                    return;   // un'altra lettura è partita dopo questa
                }
                scanning = false;
                try {
                    preview = get();
                    List<String> lines = new ArrayList<>();
                    lines.add(Texts.get("script.file.summary", preview.statements(), preview.destructiveCount()));
                    if (!preview.catalogs().isEmpty()) {
                        lines.add(Texts.get("script.file.catalogs", String.join(", ", preview.catalogs())));
                    }
                    if (preview.notUtf8()) {
                        lines.add(Texts.get("script.file.notUtf8"));
                    }
                    if (preview.incompleteDump()) {
                        lines.add(Texts.get("script.file.incomplete"));
                    }
                    if (!preview.sessionChanges().isEmpty()) {
                        lines.add(Texts.get("script.file.session", String.join(", ", preview.sessionChanges().stream()
                                .map(Enum::name).sorted().toList())));
                    }
                    summaryLines = lines;
                    fileSummary.setText(String.join("\n", lines));
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    fileSummary.setText(Texts.get("script.file.unreadable", p.getFileName(), t.getMessage()));
                }
                targetChosen = false;
                refreshTargets();
                updateCompat();
                refreshButtons();
            }
        }.execute();
    }

    private void refreshButtons() {
        runButton.setEnabled(preview != null && !scanning && !running && hasTarget());
        stopButton.setEnabled(running);
        chooseButton.setEnabled(!running);
        targetCombo.setEnabled(!running && !scriptChoosesCatalog());
        stopRadio.setEnabled(!running);
        continueRadio.setEnabled(!running);
    }

    // ================================================================ esecuzione

    /** Le istruzioni prima del file: il {@code USE} del catalogo scelto (nessuna se decide lo script). */
    public List<SqlStatement> before() {
        Object t = targetCombo.getSelectedItem();
        if (t == null || FROM_SCRIPT.equals(t) || NONE.equals(t)) {
            return List.of();
        }
        return List.of(SqlStatement.of("USE " + SqlIdentifiers.quote(t.toString()), SqlOrigin.SCRIPT_FILE.label()));
    }

    /** «Esegui»: anteprima dalla pipeline, poi esecuzione a flusso. */
    public void start() {
        if (running || preview == null || !hasTarget()) {
            return;
        }
        if (preview.changedOnDisk()) {
            // cambiato dopo la lettura di prova: si rilegge, e l'utente riguarda il riepilogo prima di eseguire
            setFile(file);
            resultBanner.set(Banner.Tone.WARNING, Texts.get("script.file.changed"));
            return;
        }
        List<SqlStatement> before = before();
        String origin = SqlOrigin.SCRIPT_FILE.label();
        running = true;
        result = null;
        resultBanner.clear();
        failureModel.set(List.of());
        sessionButton.setVisible(false);
        long total = Math.max(1, preview.chars());
        progress.setMaximum(1000);
        progress.setValue(0);
        progressLabel.setText(" ");
        refreshButtons();
        boolean continueOnError = continueRadio.isSelected();
        ScriptFileListener listener = new ScriptFileListener() {
            @Override
            public void progress(long statements, long chars) {
                if (statements % 50 == 0) {
                    SwingUtilities.invokeLater(() -> {
                        progress.setValue((int) Math.min(1000, chars * 1000 / total));
                        progressLabel.setText(Texts.get("script.progress.text", statements, preview.statements()));
                    });
                }
            }
        };
        CompletableFuture<ScriptFileResult> f = pipeline.proposeScriptFile(preview, before, continueOnError, origin,
                compat, listener);
        f.whenComplete((r, e) -> SwingUtilities.invokeLater(() -> finished(r, e)));
    }

    private void finished(ScriptFileResult r, Throwable error) {
        running = false;
        refreshButtons();
        if (error != null || r == null) {
            progressLabel.setText(error == null ? Texts.get("script.cancelled") : " ");
            return;
        }
        result = r;
        progress.setValue(r.completed() || r.failureCount() > 0 && !r.stoppedOnError() && !r.interrupted() ? 1000
                : progress.getValue());
        progressLabel.setText(Texts.get("script.progress.text", r.executed(), preview.statements()));
        List<Banner.Line> lines = new ArrayList<>();
        String counts = Texts.get("script.result.counts", r.executed(), preview.statements(), r.succeeded(),
                r.failureCount(), r.durationMillis() / 1000.0);
        if (r.completed()) {
            lines.add(new Banner.Line(Banner.Tone.SUCCESS, Texts.get("script.result.done", counts)));
        } else if (r.interrupted()) {
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("script.result.interrupted", counts)));
        } else if (r.stoppedOnError()) {
            lines.add(new Banner.Line(Banner.Tone.DANGER, Texts.get(switch (r.stop()) {
                case CATALOG -> "script.result.stoppedCatalog";
                case CONNECTION -> "script.result.stoppedConnection";
                default -> "script.result.stopped";
            }, counts)));
        } else if (r.readError() != null) {
            lines.add(new Banner.Line(Banner.Tone.DANGER, Texts.get("script.result.readError", counts, r.readError())));
        } else {
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("script.result.withErrors", counts)));
        }
        if (r.transactionOpen()) {
            lines.add(new Banner.Line(Banner.Tone.DANGER, Texts.get("script.result.transaction")));
        }
        if (!r.restore().isEmpty()) {
            // lo script ha lasciato cambiata la sessione (di solito si è fermato prima di rimetterla com'era)
            sessionButton.setVisible(true);
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("script.result.session",
                    String.join(", ", r.restore().stream().map(ScriptRunTab::settingName).toList()))));
        }
        if (r.warningCount() > 0) {
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("script.result.warnings", r.warningCount(),
                    String.join(" · ", r.warnings().stream().limit(3).map(w -> Texts.get("script.result.warning",
                            w.line(), w.code(), w.message())).toList()))));
        }
        resultBanner.set(lines);
        failureModel.set(r.failures());
        reader.invalidateAll();
    }

    /** Il nome, per l'utente, dell'impostazione che un'istruzione di ripristino rimette. */
    static String settingName(String restoreStatement) {
        String u = restoreStatement.toUpperCase(java.util.Locale.ROOT);
        String key = u.startsWith("UNLOCK") ? "unlock" : u.contains("SQL_MODE") ? "sqlMode" : u.contains("TIME_ZONE")
                ? "timeZone" : u.contains("FOREIGN_KEY_CHECKS") ? "foreignKeys" : u.contains("UNIQUE_CHECKS")
                ? "uniqueChecks" : "names";
        return Texts.get("script.session.setting." + key);
    }

    /** Rimette la sessione com'era prima dello script, dalla pipeline (anteprima e registro). */
    public void restoreSession() {
        if (result == null || result.restore().isEmpty()) {
            return;
        }
        pipeline.propose(it.ramasql.core.exec.SqlScript.of(Texts.get("script.session.title"),
                SqlOrigin.SCRIPT_FILE.label(), result.restore().toArray(String[]::new)));
        sessionButton.setVisible(false);
    }

    /** «Interrompi»: l'istruzione in corso si ferma; le precedenti restano. */
    public void interrupt() {
        if (!running) {
            return;
        }
        stopButton.setEnabled(false);
        progressLabel.setText(Texts.get("script.stopping"));
        try {
            pipeline.executor().interrupt();
        } catch (SQLException e) {
            stopButton.setEnabled(true);
        }
    }

    // ================================================================ per i test e per la finestra

    public boolean isRunning() {
        return running;
    }

    public boolean isScanning() {
        return scanning;
    }

    public ScriptPreview preview() {
        return preview;
    }

    public ScriptFileResult result() {
        return result;
    }

    public JComboBox<String> targetCombo() {
        return targetCombo;
    }

    public JRadioButton stopRadio() {
        return stopRadio;
    }

    public JRadioButton continueRadio() {
        return continueRadio;
    }

    public JButton runButton() {
        return runButton;
    }

    public JButton stopButton() {
        return stopButton;
    }

    public JButton sessionButton() {
        return sessionButton;
    }

    /** Le sostituzioni di collation che si applicheranno. */
    public CollationCompat compat() {
        return compat;
    }

    public javax.swing.JTextArea fileSummary() {
        return fileSummary;
    }

    public Banner resultBanner() {
        return resultBanner;
    }

    public JTable failureTable() {
        return failureTable;
    }

    public JProgressBar progress() {
        return progress;
    }

    public JLabel progressLabel() {
        return progressLabel;
    }

    public boolean canClose() {
        return !running;
    }

    public void stopForClose() {
        cancelScan.set(true);
        if (running) {
            interrupt();
        }
    }

    /** Righe non riuscite: riga del file, istruzione, errore del server spiegato. */
    private static final class FailureModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private List<ScriptFileResult.Failure> rows = List.of();

        void set(List<ScriptFileResult.Failure> r) {
            rows = r;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return Texts.get("script.failures.column." + column);
        }

        @Override
        public Object getValueAt(int row, int column) {
            ScriptFileResult.Failure f = rows.get(row);
            return switch (column) {
                case 0 -> f.line() == 0 ? "—" : String.valueOf(f.line());
                case 1 -> f.text();
                default -> ErrorExplainer.explain(f.code()).map(x -> x + " ").orElse("")
                        + Texts.get("import.rejected.server", f.code(), f.message());
            };
        }
    }
}
