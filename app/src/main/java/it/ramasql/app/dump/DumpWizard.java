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
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import it.ramasql.app.Texts;
import it.ramasql.app.tableeditor.Banner;
import it.ramasql.app.tableeditor.Ui;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.core.dump.DumpContent;
import it.ramasql.core.dump.DumpOptions;
import it.ramasql.core.dump.Dumper;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.CatalogInfo;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.core.sqlgen.SqlIdentifiers;

/**
 * Scheda «Esporta / Dump» ({@code DESIGN.md} §3.10, come «Data Export» di Workbench ridotto): tre passi, un solo
 * pulsante primario.
 * <ol>
 *   <li><b>Cosa</b>: i cataloghi a sinistra (con la casella «tutto il catalogo»), gli oggetti del catalogo scelto a
 *       destra, ciascuno con la sua casella e, per le tabelle, <em>struttura e dati</em>, <em>solo struttura</em> o
 *       <em>solo dati</em>;</li>
 *   <li><b>Opzioni</b>: {@code DROP … IF EXISTS}, {@code CREATE DATABASE}/{@code USE}, righe per {@code INSERT},
 *       controlli delle chiavi esterne spenti durante il ripristino, il file;</li>
 *   <li><b>Esporta</b>: il dump si scrive in sottofondo, a flusso, con avanzamento e <em>Interrompi</em>; alla fine il
 *       riepilogo e le cose da sapere (routine non incluse, chiavi circolari).</li>
 * </ol>
 * Il dump <b>legge</b> soltanto: le {@code SELECT} delle righe passano dall'esecutore e finiscono nel registro, le
 * strutture dal canale dei metadati ({@code SHOW CREATE}). Nessuna scrittura sul server, quindi nessuna anteprima.
 */
public final class DumpWizard extends JPanel {

    private static final long serialVersionUID = 1L;

    /** I tre passi. */
    public enum Step { WHAT, OPTIONS, RUN }

    private final transient MetadataReader reader;
    private final transient SqlExecutor executor;
    private final transient FilePrompts files;
    private final String server;

    private Step step = Step.WHAT;
    /** Catalogo → oggetti scelti (nome → contenuto); un catalogo senza voce non è nel dump. */
    private final Map<String, Map<String, DumpContent>> chosen = new LinkedHashMap<>();
    /** Catalogo → oggetti del catalogo (letti una volta). */
    private final Map<String, List<TableSummary>> objects = new LinkedHashMap<>();
    private transient List<CatalogInfo> catalogs = List.of();
    private String shownCatalog;
    private boolean running;
    private final AtomicBoolean cancel = new AtomicBoolean();
    private transient Dumper.Result result;
    private transient Path file;
    private int loading;

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final List<JLabel> stepLabels = new ArrayList<>();
    private final JButton back;
    private final JButton next;

    // passo 1
    private final CatalogModel catalogModel = new CatalogModel();
    private final JTable catalogTable = new JTable(catalogModel);
    private final ObjectModel objectModel = new ObjectModel();
    private final JTable objectTable = new JTable(objectModel);
    private final JButton allCatalogsButton;
    private final JLabel whatSummary = new JLabel(" ");

    // passo 2
    private final JCheckBox dropCheck = new JCheckBox(Texts.get("dump.option.drop"));
    private final JCheckBox createDbCheck = new JCheckBox(Texts.get("dump.option.createDatabase"));
    private final JSpinner rowsSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10_000, 50));
    private final JCheckBox fkCheck = new JCheckBox(Texts.get("dump.option.foreignKeys"), true);
    private final JTextField fileField = new JTextField();
    private final JButton chooseButton;
    private final JTextArea optionsSummary = Banner.wrapText(" ", Tokens.TEXT_SECONDARY);

    // passo 3
    private final JTextArea runSummary = Banner.wrapText(" ", Tokens.TEXT_PRIMARY);
    private final JProgressBar progress = new JProgressBar();
    private final JLabel progressLabel = new JLabel(" ");
    private final JButton stopButton;
    private final Banner resultBanner = new Banner("dump.run.result");

    /**
     * @param server   server d'origine («MariaDB 11.5.2»), scritto nell'intestazione del file
     * @param catalog  catalogo proposto (il navigatore); {@code null} = nessuno
     * @param object   tabella o vista proposta; {@code null} = tutto il catalogo
     */
    public DumpWizard(MetadataReader reader, SqlExecutor executor, FilePrompts files, String server, String catalog,
            String object) {
        super(new BorderLayout());
        this.reader = reader;
        this.executor = executor;
        this.files = files;
        this.server = server;
        setName("dump.wizard");
        setBackground(Tokens.BG_SURFACE);
        back = Ui.button("dump.back", Texts.get("dump.back"), this::back);
        next = Ui.primary("dump.next", Texts.get("dump.next"), this::next);
        allCatalogsButton = Ui.button("dump.what.allCatalogs", Texts.get("dump.what.allCatalogs"), this::chooseAll);
        chooseButton = Ui.button("dump.file.choose", Texts.get("dump.file.choose"), this::chooseFile);
        stopButton = Ui.button("dump.run.stop", Texts.get("dump.run.stop"), this::interrupt);
        Styles.outline(stopButton, Tokens.DANGER, Tokens.DANGER_TINT);
        add(header(), BorderLayout.NORTH);
        body.setOpaque(false);
        body.add(whatStep(), Step.WHAT.name());
        body.add(optionsStep(), Step.OPTIONS.name());
        body.add(runStep(), Step.RUN.name());
        add(body, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        tooltips();
        loadCatalogs(catalog, object);
        show(Step.WHAT);
    }

    // ================================================================ costruzione

    private JComponent header() {
        JPanel h = new JPanel();
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.setBackground(Tokens.BG_SURFACE);
        h.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));
        JLabel title = Styles.text(new JLabel(Texts.get("dump.title")), "heading", Tokens.TEXT_PRIMARY);
        title.setAlignmentX(LEFT_ALIGNMENT);
        h.add(title);
        h.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_12)));
        JPanel steps = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        steps.setOpaque(false);
        steps.setAlignmentX(LEFT_ALIGNMENT);
        for (Step s : Step.values()) {
            JLabel l = new JLabel(Texts.get("dump.step." + s.name().toLowerCase(Locale.ROOT), s.ordinal() + 1));
            l.setName("dump.step." + s.name().toLowerCase(Locale.ROOT));
            l.setBorder(BorderFactory.createEmptyBorder(Tokens.px(4), Tokens.px(10), Tokens.px(4), Tokens.px(10)));
            l.setOpaque(true);
            stepLabels.add(l);
            steps.add(l);
            if (s != Step.RUN) {
                JLabel sep = Styles.text(new JLabel("›"), "smallText", Tokens.TEXT_TERTIARY);
                sep.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(4), 0, Tokens.px(4)));
                steps.add(sep);
            }
        }
        h.add(steps);
        return h;
    }

    private JComponent footer() {
        JPanel f = new JPanel(new BorderLayout());
        f.setBackground(Tokens.BG_WINDOW);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                        Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24))));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.px(Tokens.SPACE_8), 0));
        right.setOpaque(false);
        right.add(back);
        right.add(next);
        f.add(right, BorderLayout.EAST);
        return f;
    }

    private static JPanel page() {
        JPanel p = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_8)));
        p.setBackground(Tokens.BG_SURFACE);
        p.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));
        return p;
    }

    private JComponent whatStep() {
        JPanel p = page();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Ui.sectionTitle(Texts.get("dump.what.intro")), BorderLayout.WEST);
        top.add(allCatalogsButton, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        catalogTable.setName("dump.what.catalogs");
        Ui.styleTable(catalogTable);
        Ui.narrow(catalogTable.getColumnModel().getColumn(0), Tokens.px(36));
        catalogTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && catalogTable.getSelectedRow() >= 0) {
                showCatalog(catalogs.get(catalogTable.getSelectedRow()).name());
            }
        });
        objectTable.setName("dump.what.objects");
        Ui.styleTable(objectTable);
        Ui.narrow(objectTable.getColumnModel().getColumn(0), Tokens.px(36));
        JComboBox<String> content = new JComboBox<>(new String[] {Texts.get("dump.content.BOTH"),
            Texts.get("dump.content.STRUCTURE"), Texts.get("dump.content.DATA")});
        content.setName("dump.what.content");
        it.ramasql.app.theme.ComboTips.install(content, it.ramasql.app.theme.Tips.of("dump.content", List.of(Texts.get("dump.content.BOTH"),
                Texts.get("dump.content.STRUCTURE"), Texts.get("dump.content.DATA")), List.of("BOTH", "STRUCTURE", "DATA")));
        objectTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(content));
        objectTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, f, r, c);
                boolean editable = t.getModel().isCellEditable(r, c);
                setForeground(editable ? Tokens.TEXT_PRIMARY : Tokens.TEXT_TERTIARY);
                setToolTipText(Texts.get("dump.what.content.tooltip"));
                return this;
            }
        });
        catalogTable.setPreferredScrollableViewportSize(new Dimension(Tokens.px(220), Tokens.px(Tokens.ROW_HEIGHT) * 5));
        objectTable.setPreferredScrollableViewportSize(new Dimension(Tokens.px(420), Tokens.px(Tokens.ROW_HEIGHT) * 5));
        JPanel both = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_12), 0));
        both.setOpaque(false);
        both.add(Ui.scroll(catalogTable), BorderLayout.WEST);
        both.add(Ui.scroll(objectTable), BorderLayout.CENTER);
        p.add(both, BorderLayout.CENTER);
        whatSummary.setName("dump.what.summary");
        whatSummary.setForeground(Tokens.TEXT_SECONDARY);
        p.add(whatSummary, BorderLayout.SOUTH);
        return p;
    }

    private JComponent optionsStep() {
        JPanel p = page();
        p.add(Ui.sectionTitle(Texts.get("dump.options.intro")), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(Tokens.px(Tokens.SPACE_4), 0, Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12));
        c.gridx = 0;
        c.gridwidth = 2;
        dropCheck.setName("dump.option.drop");
        createDbCheck.setName("dump.option.createDatabase");
        fkCheck.setName("dump.option.foreignKeys");
        rowsSpinner.setName("dump.option.rows");
        fileField.setName("dump.file");
        fileField.setEditable(false);
        c.gridy = 0;
        grid.add(dropCheck, c);
        c.gridy = 1;
        grid.add(createDbCheck, c);
        c.gridy = 2;
        grid.add(fkCheck, c);
        c.gridwidth = 1;
        c.gridy = 3;
        grid.add(Styles.text(new JLabel(Texts.get("dump.option.rows.label")), "emphasis", Tokens.TEXT_SECONDARY), c);
        c.gridx = 1;
        grid.add(rowsSpinner, c);
        c.gridx = 0;
        c.gridy = 4;
        grid.add(Styles.text(new JLabel(Texts.get("dump.file.label")), "emphasis", Tokens.TEXT_SECONDARY), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        JPanel fileRow = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_8), 0));
        fileRow.setOpaque(false);
        fileRow.add(fileField, BorderLayout.CENTER);
        fileRow.add(chooseButton, BorderLayout.EAST);
        grid.add(fileRow, c);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        grid.add(optionsSummary, c);
        c.gridy = 6;
        c.weighty = 1;
        grid.add(Box.createGlue(), c);
        p.add(grid, BorderLayout.CENTER);
        for (JComponent x : new JComponent[] {dropCheck, createDbCheck, fkCheck}) {
            ((JCheckBox) x).addActionListener(e -> refreshOptions());
        }
        return p;
    }

    private JComponent runStep() {
        JPanel p = page();
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        runSummary.setName("dump.run.summary");
        progress.setName("dump.run.progress");
        progressLabel.setName("dump.run.progressLabel");
        progressLabel.setForeground(Tokens.TEXT_SECONDARY);
        JPanel bar = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_12), 0));
        bar.setOpaque(false);
        bar.add(progress, BorderLayout.CENTER);
        bar.add(stopButton, BorderLayout.EAST);
        for (JComponent x : new JComponent[] {runSummary, bar, progressLabel, resultBanner}) {
            x.setAlignmentX(LEFT_ALIGNMENT);
            top.add(x);
            top.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)));
        }
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
        p.add(top, BorderLayout.NORTH);
        stopButton.setEnabled(false);
        return p;
    }

    private void tooltips() {
        for (JComponent c : new JComponent[] {catalogTable, objectTable, allCatalogsButton, dropCheck, createDbCheck,
            fkCheck, rowsSpinner, fileField, chooseButton, progress, stopButton, back, next}) {
            c.setToolTipText(Texts.get(c.getName() + ".tooltip"));
        }
        for (JLabel l : stepLabels) {
            l.setToolTipText(Texts.get(l.getName() + ".tooltip"));
        }
    }

    // ================================================================ navigazione

    public Step step() {
        return step;
    }

    private void show(Step s) {
        step = s;
        cards.show(body, s.name());
        for (int i = 0; i < stepLabels.size(); i++) {
            JLabel l = stepLabels.get(i);
            boolean current = i == s.ordinal();
            l.setBackground(current ? Tokens.ACCENT_TINT : Tokens.BG_SURFACE);
            l.setForeground(current ? Tokens.ACCENT : i < s.ordinal() ? Tokens.TEXT_PRIMARY : Tokens.TEXT_TERTIARY);
            Styles.text(l, current ? "emphasis" : "smallText");
        }
        next.setText(Texts.get(s == Step.RUN ? "dump.run.start" : "dump.next"));
        next.setToolTipText(Texts.get(s == Step.RUN ? "dump.run.start.tooltip" : "dump.next.tooltip"));
        if (s == Step.OPTIONS) {
            refreshOptions();
        }
        if (s == Step.RUN) {
            runSummary.setText(Texts.get("dump.run.summary", selection().size(), chosen.size(), file == null ? ""
                    : file.getFileName()));
        }
        refreshButtons();
        revalidate();
        repaint();
    }

    private void refreshButtons() {
        back.setEnabled(step != Step.WHAT && !running);
        boolean ok = switch (step) {
            case WHAT -> loading == 0 && !selection().isEmpty();
            case OPTIONS -> file != null;
            case RUN -> !running && result == null;
        };
        next.setEnabled(ok && !running);
        allCatalogsButton.setEnabled(loading == 0 && !running);
    }

    public void back() {
        if (step.ordinal() > 0 && !running) {
            if (step == Step.RUN) {
                result = null;
                resultBanner.clear();
                progressLabel.setText(" ");
            }
            show(Step.values()[step.ordinal() - 1]);
        }
    }

    public void next() {
        switch (step) {
            case WHAT -> show(Step.OPTIONS);
            case OPTIONS -> show(Step.RUN);
            case RUN -> start();
        }
    }

    // ================================================================ passo 1: cosa

    /** Legge i cataloghi (e gli oggetti di quello proposto) in sottofondo. */
    private void loadCatalogs(String catalog, String object) {
        loading++;
        new SwingWorker<List<CatalogInfo>, Void>() {
            @Override
            protected List<CatalogInfo> doInBackground() throws SQLException {
                return reader.catalogs().stream().filter(c -> !c.system()).toList();
            }

            @Override
            protected void done() {
                loading--;
                try {
                    catalogs = get();
                } catch (Exception e) {
                    catalogs = List.of();
                    whatSummary.setText(Texts.get("dump.what.error", cause(e)));
                }
                catalogModel.fireTableDataChanged();
                if (catalog != null) {
                    int i = indexOf(catalog);
                    if (i >= 0) {
                        loadObjects(catalog, () -> {
                            Map<String, DumpContent> sel = new LinkedHashMap<>();
                            for (TableSummary t : objects.get(catalog)) {
                                if (object == null || t.name().equalsIgnoreCase(object)) {
                                    sel.put(t.name(), DumpContent.BOTH);
                                }
                            }
                            chosen.put(catalog, sel);
                            catalogTable.setRowSelectionInterval(i, i);
                            showCatalog(catalog);
                        });
                    }
                }
                refreshButtons();
            }
        }.execute();
    }

    private int indexOf(String catalog) {
        for (int i = 0; i < catalogs.size(); i++) {
            if (catalogs.get(i).name().equalsIgnoreCase(catalog)) {
                return i;
            }
        }
        return -1;
    }

    private void loadObjects(String catalog, Runnable then) {
        if (objects.containsKey(catalog)) {
            then.run();
            return;
        }
        loading++;
        refreshButtons();
        new SwingWorker<List<TableSummary>, Void>() {
            @Override
            protected List<TableSummary> doInBackground() throws SQLException {
                return reader.tables(catalog);
            }

            @Override
            protected void done() {
                loading--;
                try {
                    objects.put(catalog, get());
                } catch (Exception e) {
                    objects.put(catalog, List.of());
                    whatSummary.setText(Texts.get("dump.what.error", cause(e)));
                }
                then.run();
                refreshButtons();
            }
        }.execute();
    }

    private void showCatalog(String catalog) {
        loadObjects(catalog, () -> {
            shownCatalog = catalog;
            objectModel.fireTableDataChanged();
            refreshSummary();
        });
    }

    /** Casella del catalogo: tutto il catalogo (struttura e dati) o niente. */
    void toggleCatalog(String catalog, boolean on) {
        if (!on) {
            chosen.remove(catalog);
            catalogModel.fireTableDataChanged();
            objectModel.fireTableDataChanged();
            refreshSummary();
            return;
        }
        loadObjects(catalog, () -> {
            Map<String, DumpContent> sel = new LinkedHashMap<>();
            for (TableSummary t : objects.get(catalog)) {
                sel.put(t.name(), DumpContent.BOTH);
            }
            chosen.put(catalog, sel);
            catalogModel.fireTableDataChanged();
            objectModel.fireTableDataChanged();
            refreshSummary();
        });
    }

    /** «Tutti i cataloghi»: ogni catalogo non di sistema, tutto (il dump totale). */
    public void chooseAll() {
        for (CatalogInfo c : catalogs) {
            toggleCatalog(c.name(), true);
        }
    }

    /** Sceglie (o toglie) un oggetto del catalogo con il suo contenuto. */
    public void setObject(String catalog, String name, DumpContent content) {
        loadObjects(catalog, () -> {
            Map<String, DumpContent> sel = chosen.computeIfAbsent(catalog, k -> new LinkedHashMap<>());
            if (content == null) {
                sel.remove(name);
                if (sel.isEmpty()) {
                    chosen.remove(catalog);
                }
            } else {
                TableSummary t = find(catalog, name);
                sel.put(name, t != null && t.isView() ? DumpContent.STRUCTURE : content);
            }
            catalogModel.fireTableDataChanged();
            objectModel.fireTableDataChanged();
            refreshSummary();
        });
    }

    /** Toglie tutto quello che era scelto. */
    public void clearSelection() {
        chosen.clear();
        catalogModel.fireTableDataChanged();
        objectModel.fireTableDataChanged();
        refreshSummary();
    }

    private TableSummary find(String catalog, String name) {
        for (TableSummary t : objects.getOrDefault(catalog, List.of())) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    /** Gli oggetti scelti, nell'ordine dei cataloghi e degli oggetti. */
    public List<Dumper.Item> selection() {
        List<Dumper.Item> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, DumpContent>> e : chosen.entrySet()) {
            for (TableSummary t : objects.getOrDefault(e.getKey(), List.of())) {
                DumpContent c = e.getValue().get(t.name());
                if (c != null) {
                    out.add(new Dumper.Item(e.getKey(), t.name(), t.isView(), c));
                }
            }
        }
        return out;
    }

    private void refreshSummary() {
        List<Dumper.Item> sel = selection();
        long tables = sel.stream().filter(i -> !i.view()).count();
        long views = sel.size() - tables;
        whatSummary.setText(sel.isEmpty() ? Texts.get("dump.what.nothing")
                : Texts.get("dump.what.summary", chosen.size(), tables, views));
        refreshButtons();
    }

    // ================================================================ passo 2: opzioni

    public DumpOptions options() {
        boolean several = chosen.size() > 1;
        return new DumpOptions(dropCheck.isSelected(), createDbCheck.isSelected() || several,
                (Integer) rowsSpinner.getValue(), fkCheck.isSelected());
    }

    private void refreshOptions() {
        boolean several = chosen.size() > 1;
        if (several) {
            createDbCheck.setSelected(true);   // più cataloghi in un file: ognuno con il suo nome
        }
        createDbCheck.setEnabled(!several && !running);
        if (file == null) {
            List<String> cats = new ArrayList<>(chosen.keySet());
            fileField.setText(Texts.get("dump.file.none"));
            fileField.setToolTipText(Texts.get("dump.file.tooltip"));
            suggested = Dumper.suggestedFileName(cats, LocalDate.now());
        }
        DumpOptions o = options();
        List<String> lines = new ArrayList<>();
        lines.add(Texts.get(o.createDatabase() ? "dump.options.summary.createDatabase" : "dump.options.summary.noUse"));
        if (o.dropIfExists()) {
            lines.add(Texts.get(o.createDatabase() ? "dump.options.summary.dropAll" : "dump.options.summary.drop"));
        }
        optionsSummary.setText(String.join("\n", lines));
        refreshButtons();
    }

    private String suggested = "dump.sql";

    /** «Scegli…»: dove scrivere il file. */
    public void chooseFile() {
        Path p = files.chooseToSave(FilePrompts.Purpose.DUMP, suggested);
        if (p != null) {
            file = p;
            fileField.setText(p.toString());
        }
        refreshButtons();
    }

    // ================================================================ passo 3: esporta

    /** «Esporta»: scrive il file in sottofondo. */
    public void start() {
        if (running || file == null) {
            return;
        }
        List<Dumper.Item> sel = selection();
        DumpOptions options = options();
        Path target = file;
        running = true;
        cancel.set(false);
        result = null;
        resultBanner.clear();
        stopButton.setEnabled(true);
        progress.setMaximum(Math.max(1, sel.size()));
        progress.setValue(0);
        refreshButtons();
        Dumper dumper = new Dumper(reader, Dumper.fromExecutor(executor, SqlOrigin.DUMP.label()), server);
        new SwingWorker<Dumper.Result, Void>() {
            @Override
            protected Dumper.Result doInBackground() throws Exception {
                // si scrive accanto, in un file temporaneo, e si rinomina solo a dump finito: un dump fallito o
                // interrotto non lascia un file troncato e non cancella un dump buono con lo stesso nome
                Path part = target.resolveSibling(target.getFileName() + ".part");
                try {
                    Dumper.Result r;
                    try (BufferedWriter out = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                        r = write(out);
                    }
                    if (!r.interrupted()) {
                        Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    }
                    return r;
                } finally {
                    Files.deleteIfExists(part);
                }
            }

            private Dumper.Result write(BufferedWriter out) throws Exception {
                {
                    return dumper.write(sel, options, out, cancel::get, new Dumper.Progress() {
                        @Override
                        public void object(String catalog, String name, int index, int total) {
                            SwingUtilities.invokeLater(() -> {
                                progress.setValue(index - 1);
                                progressLabel.setText(Texts.get("dump.run.object", index, total, catalog, name));
                            });
                        }

                        @Override
                        public void rows(String table, long rows) {
                            SwingUtilities.invokeLater(() -> progressLabel.setText(
                                    Texts.get("dump.run.rows", table, rows)));
                        }
                    });
                }
            }

            @Override
            protected void done() {
                running = false;
                stopButton.setEnabled(false);
                try {
                    result = get();
                    progress.setValue(progress.getMaximum());
                    List<Banner.Line> lines = new ArrayList<>();
                    lines.add(new Banner.Line(result.interrupted() ? Banner.Tone.WARNING : Banner.Tone.SUCCESS,
                            Texts.get(result.interrupted() ? "dump.run.interrupted" : "dump.run.done", target.getFileName(),
                                    result.tables(), result.views(), result.rows(), result.statements())));
                    for (String w : result.warnings()) {
                        lines.add(new Banner.Line(Banner.Tone.INFO, w));
                    }
                    resultBanner.set(lines);
                    progressLabel.setText(" ");
                } catch (Exception e) {
                    resultBanner.set(Banner.Tone.DANGER, Texts.get("dump.run.failed", cause(e)));
                }
                refreshButtons();
            }
        }.execute();
    }

    /** «Interrompi»: il file si chiude con la riga che dice che è incompleto. */
    public void interrupt() {
        if (!running) {
            return;
        }
        cancel.set(true);
        stopButton.setEnabled(false);
        progressLabel.setText(Texts.get("dump.run.stopping"));
        try {
            executor.interrupt();
        } catch (SQLException ignored) {
            // la lettura in corso finirà da sola; il dump si ferma alla prossima riga
        }
    }

    // ================================================================ per i test e per la finestra

    public boolean isRunning() {
        return running;
    }

    public boolean isLoading() {
        return loading > 0;
    }

    public Dumper.Result result() {
        return result;
    }

    public Path file() {
        return file;
    }

    public JButton nextButton() {
        return next;
    }

    public JLabel progressLabel() {
        return progressLabel;
    }

    public JButton stopButton() {
        return stopButton;
    }

    public JCheckBox dropCheck() {
        return dropCheck;
    }

    public JCheckBox createDatabaseCheck() {
        return createDbCheck;
    }

    public JCheckBox foreignKeysCheck() {
        return fkCheck;
    }

    public JSpinner rowsSpinner() {
        return rowsSpinner;
    }

    public Banner resultBanner() {
        return resultBanner;
    }

    public JTable catalogTable() {
        return catalogTable;
    }

    public JTable objectTable() {
        return objectTable;
    }

    public boolean canClose() {
        return !running;
    }

    public void stopForClose() {
        if (running) {
            interrupt();
        }
    }

    private static String cause(Exception e) {
        Throwable t = e.getCause() != null ? e.getCause() : e;
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    // ================================================================ modelli

    /** Cataloghi: casella «tutto / niente / in parte» e nome. */
    private final class CatalogModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;

        @Override
        public int getRowCount() {
            return catalogs.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return Texts.get("dump.what.catalogs.column." + column);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            String name = catalogs.get(row).name();
            return column == 0 ? chosen.containsKey(name) : name;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && !running;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            toggleCatalog(catalogs.get(row).name(), Boolean.TRUE.equals(value));
        }
    }

    /** Oggetti del catalogo mostrato: casella, nome, tabella o vista, contenuto. */
    private final class ObjectModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;

        private List<TableSummary> rows() {
            return shownCatalog == null ? List.of() : objects.getOrDefault(shownCatalog, List.of());
        }

        @Override
        public int getRowCount() {
            return rows().size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return Texts.get("dump.what.objects.column." + column);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            TableSummary t = rows().get(row);
            DumpContent c = chosen.getOrDefault(shownCatalog, Map.of()).get(t.name());
            return switch (column) {
                case 0 -> c != null;
                case 1 -> t.name();
                case 2 -> Texts.get(t.isView() ? "dump.what.kind.view" : "dump.what.kind.table");
                default -> c == null ? "" : Texts.get("dump.content." + c.name());
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            if (running) {
                return false;
            }
            TableSummary t = rows().get(row);
            return column == 0 || (column == 3 && !t.isView()
                    && chosen.getOrDefault(shownCatalog, Map.of()).containsKey(t.name()));
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            TableSummary t = rows().get(row);
            if (column == 0) {
                setObject(shownCatalog, t.name(), Boolean.TRUE.equals(value) ? DumpContent.BOTH : null);
            } else if (column == 3) {
                for (DumpContent c : DumpContent.values()) {
                    if (Texts.get("dump.content." + c.name()).equals(value)) {
                        setObject(shownCatalog, t.name(), c);
                    }
                }
            }
        }
    }
}
