/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.importer;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
import it.ramasql.core.exec.BatchListener;
import it.ramasql.core.exec.BatchResult;
import it.ramasql.core.importer.CsvFormat;
import it.ramasql.core.importer.EncodingDetector;
import it.ramasql.core.importer.FileAnalyzer;
import it.ramasql.core.importer.ImportFile;
import it.ramasql.core.importer.ImportFileException;
import it.ramasql.core.importer.ImportPlan;
import it.ramasql.core.importer.ImportPlanner;
import it.ramasql.core.importer.ImportReport;
import it.ramasql.core.importer.ImportRows;
import it.ramasql.core.importer.SourceRow;
import it.ramasql.core.importer.TypeInference;
import it.ramasql.core.importer.ValueParsing.DateOrder;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.metadata.TableSummary;
import it.ramasql.core.sqlgen.TableDiff;

/**
 * Scheda «Importa dati» ({@code DESIGN.md} §3.9, come il Table Data Import Wizard di Workbench ridotto all'essenziale):
 * cinque passi, uno per schermata, con un solo pulsante primario in basso a destra.
 * <ol>
 *   <li><b>File</b>: si sceglie il file; formato, codifica, separatore e intestazione sono rilevati e correggibili;</li>
 *   <li><b>Anteprima</b>: il file si legge tutto una volta (in sottofondo) e se ne mostrano le prime righe, così si
 *       vede subito se codifica e separatore sono giusti;</li>
 *   <li><b>Destinazione</b>: tabella esistente (colonne abbinate per nome, correggibili) o tabella nuova (tipi dedotti,
 *       correggibili, con il {@code CREATE TABLE} che si aggiorna mentre si scrive);</li>
 *   <li><b>Opzioni</b>: svuota prima, duplicati, vuoto = NULL, formato delle date;</li>
 *   <li><b>Importa</b>: l'SQL passa dalla pipeline (anteprima: {@code TRUNCATE}/{@code CREATE TABLE} e l'{@code INSERT}
 *       preparata con il numero di lotti), poi l'inserimento a lotti in autocommit con avanzamento e
 *       <em>Interrompi</em>; alla fine il rapporto: righe lette, inserite, scartate con riga e motivo in italiano.</li>
 * </ol>
 * Nessuna lettura o scrittura sull'EDT: analisi del file, letture dei metadati e importazione girano in sottofondo.
 */
public final class ImportWizard extends JPanel {

    private static final long serialVersionUID = 1L;

    /** I cinque passi. */
    public enum Step { FILE, PREVIEW, TARGET, OPTIONS, RUN }

    /** Voce «non importare» dell'abbinamento. */
    static final String SKIP = "";

    private static final Pattern TYPE = Pattern.compile("\\s*([A-Za-z]+)\\s*(?:\\(([^)]*)\\))?\\s*(UNSIGNED)?\\s*",
            Pattern.CASE_INSENSITIVE);

    private final transient SqlPipeline pipeline;
    private final transient MetadataReader reader;
    private final transient FilePrompts files;
    private final String catalog;
    private final transient BiConsumer<String, String> openTable;

    private Step step = Step.FILE;
    private transient ImportFile file;
    private transient FileAnalyzer.Analysis analysis;
    private transient SwingWorker<?, ?> analyzing;
    private final AtomicBoolean cancelAnalysis = new AtomicBoolean();
    private transient TableDef existingTable;
    private transient List<TableSummary> catalogTables = List.of();
    /** Nomi di tutti gli oggetti del catalogo (tabelle e viste): una tabella nuova non può chiamarsi così. */
    private transient List<String> catalogNames = List.of();
    /** La tabella esistente scelta è riferita da chiavi esterne di altre tabelle: TRUNCATE sarebbe rifiutato. */
    private transient List<String> referencedBy = List.of();
    private boolean running;
    private transient ImportReport report;
    private transient ImportRows rows;
    private long expectedRows;

    // ---- struttura
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final List<JLabel> stepLabels = new ArrayList<>();
    private final JButton back;
    private final JButton next;

    // ---- passo 1
    private final JTextField fileField = new JTextField();
    private final JButton chooseButton;
    private final JComboBox<String> kindCombo = new JComboBox<>(new String[] {"CSV", "JSON"});
    private final JComboBox<Charset> charsetCombo = new JComboBox<>(new Charset[] {StandardCharsets.UTF_8,
        EncodingDetector.WINDOWS_1252, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE});
    private final JComboBox<Character> separatorCombo = new JComboBox<>(new Character[] {';', ',', '\t', '|'});
    private final JCheckBox headerCheck = new JCheckBox(Texts.get("import.header"));
    private final JLabel detectedLabel = new JLabel(" ");
    private final Banner fileBanner = new Banner("import.file.banner");

    // ---- passo 2
    private final PreviewModel previewModel = new PreviewModel();
    private final JTable previewTable = new JTable(previewModel);
    private final JLabel previewSummary = new JLabel(" ");
    private final Banner previewBanner = new Banner("import.preview.banner");

    // ---- passo 3
    private final JRadioButton existingRadio = new JRadioButton(Texts.get("import.target.existing"));
    private final JRadioButton newRadio = new JRadioButton(Texts.get("import.target.new"));
    private final JComboBox<String> tableCombo = new JComboBox<>();
    private final JTextField newNameField = new JTextField(20);
    private final CardLayout targetCards = new CardLayout();
    private final JPanel targetBody = new JPanel(targetCards);
    private final MappingModel mappingModel = new MappingModel();
    private final JTable mappingTable = new JTable(mappingModel);
    private final JComboBox<String> mappingEditor = new JComboBox<>();
    private final NewColumnsModel newColumnsModel = new NewColumnsModel();
    private final JTable newColumnsTable = new JTable(newColumnsModel);
    private final JCheckBox addKeyCheck = new JCheckBox(Texts.get("import.target.addKey"));
    private final JTextArea createSql = new JTextArea(6, 60);
    private final Banner targetBanner = new Banner("import.target.banner");

    // ---- passo 4
    private final JCheckBox truncateCheck = new JCheckBox(Texts.get("import.option.truncate"));
    private final JComboBox<String> duplicatesCombo = new JComboBox<>(new String[] {
        Texts.get("import.option.duplicates.error"), Texts.get("import.option.duplicates.ignore")});
    private final JCheckBox emptyNullCheck = new JCheckBox(Texts.get("import.option.emptyNull"), true);
    private final JComboBox<String> datesCombo = new JComboBox<>(new String[] {Texts.get("import.option.dates.auto"),
        Texts.get("import.option.dates.DMY"), Texts.get("import.option.dates.MDY"),
        Texts.get("import.option.dates.YMD")});
    private final JTextArea optionsSummary = Banner.wrapText(" ", Tokens.TEXT_SECONDARY);

    // ---- passo 5
    private final JTextArea runSummary = Banner.wrapText(" ", Tokens.TEXT_PRIMARY);
    private final JProgressBar progress = new JProgressBar();
    private final JLabel progressLabel = new JLabel(" ");
    private final JButton stopButton;
    private final Banner resultBanner = new Banner("import.run.result");
    private final RejectedModel rejectedModel = new RejectedModel();
    private final JTable rejectedTable = new JTable(rejectedModel);
    private final JButton openTableButton;

    /**
     * @param catalog       catalogo di destinazione (quello scelto nel navigatore)
     * @param preselected   tabella proposta come destinazione ({@code null} = nessuna)
     * @param openTable     apre il data-entry di una tabella (catalogo, tabella) a importazione finita
     */
    public ImportWizard(SqlPipeline pipeline, MetadataReader reader, FilePrompts files, String catalog,
            String preselected, BiConsumer<String, String> openTable) {
        super(new BorderLayout());
        this.pipeline = pipeline;
        this.reader = reader;
        this.files = files;
        this.catalog = catalog;
        this.openTable = openTable;
        setBackground(Tokens.BG_SURFACE);
        setName("import.wizard");

        back = Ui.button("import.back", Texts.get("import.back"), this::back);
        next = Ui.primary("import.next", Texts.get("import.next"), this::next);
        chooseButton = Ui.button("import.file.choose", Texts.get("import.file.choose"), this::chooseFile);
        stopButton = Ui.button("import.run.stop", Texts.get("import.run.stop"), this::interrupt);
        Styles.outline(stopButton, Tokens.DANGER, Tokens.DANGER_TINT);
        openTableButton = Ui.button("import.run.openTable", Texts.get("import.run.openTable"), this::openImportedTable);

        add(header(), BorderLayout.NORTH);
        body.setOpaque(false);
        body.add(Ui.stepScroll(fileStep()), Step.FILE.name());
        body.add(Ui.stepScroll(previewStep()), Step.PREVIEW.name());
        body.add(Ui.stepScroll(targetStep()), Step.TARGET.name());
        body.add(Ui.stepScroll(optionsStep()), Step.OPTIONS.name());
        body.add(Ui.stepScroll(runStep()), Step.RUN.name());
        add(body, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        tooltips();
        if (preselected != null) {
            existingRadio.setSelected(true);
            pendingTable = preselected;
        } else {
            existingRadio.setSelected(true);
        }
        loadCatalogTables();
        show(Step.FILE);
    }

    private String pendingTable;

    // ================================================================ costruzione

    private JComponent header() {
        JPanel h = new JPanel();
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.setBackground(Tokens.BG_SURFACE);
        h.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));
        JLabel title = Styles.text(new JLabel(Texts.get("import.title", catalog)), "heading", Tokens.TEXT_PRIMARY);
        title.setAlignmentX(LEFT_ALIGNMENT);
        h.add(title);
        h.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_12)));
        JPanel steps = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        steps.setOpaque(false);
        steps.setAlignmentX(LEFT_ALIGNMENT);
        for (Step s : Step.values()) {
            JLabel l = new JLabel(Texts.get("import.step." + s.name().toLowerCase(Locale.ROOT), s.ordinal() + 1));
            l.setName("import.step." + s.name().toLowerCase(Locale.ROOT));
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
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Tokens.BG_SURFACE);
        p.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_16), Tokens.px(Tokens.SPACE_24)));
        return p;
    }

    private static GridBagConstraints at(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(Tokens.px(Tokens.SPACE_4), 0, Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12));
        return c;
    }

    private static GridBagConstraints wide(int y, double weighty) {
        GridBagConstraints c = at(0, y);
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = weighty > 0 ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.weighty = weighty;
        return c;
    }

    /** Un controllo che non si stringe sotto la sua larghezza utile quando lo spazio della scheda è poco. */
    private static void keepSize(JComponent c, int width) {
        Dimension d = new Dimension(Tokens.px(width), c.getPreferredSize().height);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
    }

    private static JLabel label(String key) {
        return Styles.text(new JLabel(Texts.get(key)), "emphasis", Tokens.TEXT_SECONDARY);
    }

    private JComponent fileStep() {
        JPanel p = page();
        p.add(Ui.sectionTitle(Texts.get("import.file.intro")), wide(0, 0));
        fileField.setName("import.file");
        fileField.setEditable(false);
        fileField.setColumns(40);
        p.add(label("import.file.label"), at(0, 1));
        JPanel row = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_8), 0));
        row.setOpaque(false);
        row.add(fileField, BorderLayout.CENTER);
        row.add(chooseButton, BorderLayout.EAST);
        GridBagConstraints c = at(1, 1);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        p.add(row, c);
        kindCombo.setName("import.kind");
        charsetCombo.setName("import.charset");
        separatorCombo.setName("import.separator");
        headerCheck.setName("import.header");
        charsetCombo.setRenderer(new ItemRenderer(v -> Texts.get("import.charset." + ((Charset) v).name())));
        separatorCombo.setRenderer(new ItemRenderer(v -> Texts.get("import.separator." + (int) (char) (Character) v)));
        it.ramasql.app.theme.ComboTips.install(kindCombo, it.ramasql.app.theme.Tips.of("import.kind"));
        it.ramasql.app.theme.ComboTips.install(charsetCombo, v -> it.ramasql.app.theme.Tips.item("import.charset", v.name(),
                Texts.get("import.charset." + v.name())));
        it.ramasql.app.theme.ComboTips.install(separatorCombo, v -> it.ramasql.app.theme.Tips.item("import.separator", switch ((char) v) {
            case ';' -> "semicolon";
            case ',' -> "comma";
            case '\t' -> "tab";
            default -> "pipe";
        }, Texts.get("import.separator." + (int) (char) v)));
        kindCombo.setRenderer(new ItemRenderer(v -> Texts.get("import.kind." + v)));
        p.add(label("import.kind.label"), at(0, 2));
        p.add(kindCombo, at(1, 2));
        keepSize(kindCombo, 260);
        keepSize(charsetCombo, 360);
        keepSize(separatorCombo, 200);
        p.add(label("import.charset.label"), at(0, 3));
        p.add(charsetCombo, at(1, 3));
        p.add(label("import.separator.label"), at(0, 4));
        p.add(separatorCombo, at(1, 4));
        p.add(headerCheck, at(1, 5));
        detectedLabel.setName("import.file.detected");
        detectedLabel.setForeground(Tokens.TEXT_SECONDARY);
        p.add(detectedLabel, wide(6, 0));
        p.add(fileBanner, wide(7, 0));
        p.add(Box.createGlue(), wide(8, 1));
        kindCombo.addActionListener(e -> formatChanged());
        charsetCombo.addActionListener(e -> formatChanged());
        separatorCombo.addActionListener(e -> formatChanged());
        headerCheck.addActionListener(e -> formatChanged());
        return p;
    }

    private JComponent previewStep() {
        JPanel p = page();
        previewSummary.setName("import.preview.summary");
        p.add(Ui.sectionTitle(Texts.get("import.preview.intro")), wide(0, 0));
        p.add(previewSummary, wide(1, 0));
        p.add(previewBanner, wide(2, 0));
        previewTable.setName("import.preview.table");
        Ui.styleTable(previewTable);
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        previewTable.setDefaultRenderer(Object.class, new ValueRenderer());
        p.add(Ui.scroll(previewTable), wide(3, 1));
        return p;
    }

    private JComponent targetStep() {
        JPanel p = page();
        p.add(Ui.sectionTitle(Texts.get("import.target.intro")), wide(0, 0));
        ButtonGroup g = new ButtonGroup();
        g.add(existingRadio);
        g.add(newRadio);
        existingRadio.setName("import.target.existing");
        newRadio.setName("import.target.new");
        tableCombo.setName("import.target.table");
        it.ramasql.app.theme.ComboTips.install(tableCombo, v -> it.ramasql.app.theme.Tips.titled(v, Texts.get("import.table.item.tooltip", v)));
        newNameField.setName("import.target.newName");
        p.add(existingRadio, at(0, 1));
        p.add(tableCombo, at(1, 1));
        p.add(newRadio, at(0, 2));
        p.add(newNameField, at(1, 2));
        keepSize(tableCombo, 240);
        keepSize(newNameField, 240);
        existingRadio.addActionListener(e -> targetChanged());
        newRadio.addActionListener(e -> targetChanged());
        tableCombo.addActionListener(e -> {
            if (existingRadio.isSelected()) {
                loadExistingTable();
            }
        });
        newNameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshCreateSql();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshCreateSql();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshCreateSql();
            }
        });
        // tabella esistente: abbinamento
        JPanel existing = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_8)));
        existing.setOpaque(false);
        mappingTable.setName("import.mapping.table");
        Ui.styleTable(mappingTable);
        mappingTable.setPreferredScrollableViewportSize(new Dimension(Tokens.px(420), Tokens.px(Tokens.ROW_HEIGHT) * 4));
        mappingEditor.setName("import.mapping.editor");
        it.ramasql.app.theme.ComboTips.install(mappingEditor, v -> SKIP.equals(v) ? it.ramasql.app.theme.Tips.titled(Texts.get("import.mapping.skip"),
                Texts.get("import.mapping.skip.tooltip")) : it.ramasql.app.theme.Tips.titled(v, Texts.get("import.mapping.item.tooltip", v)));
        mappingEditor.setRenderer(new ItemRenderer(v -> SKIP.equals(v) ? Texts.get("import.mapping.skip")
                : String.valueOf(v)));
        mappingTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(mappingEditor));
        mappingTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void setValue(Object value) {
                boolean skip = value == null || SKIP.equals(value);
                setText(skip ? Texts.get("import.mapping.skip") : String.valueOf(value));
                setForeground(skip ? Tokens.TEXT_TERTIARY : Tokens.TEXT_PRIMARY);
            }
        });
        existing.add(Ui.scroll(mappingTable), BorderLayout.CENTER);
        // tabella nuova: colonne e CREATE TABLE
        JPanel fresh = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_8)));
        fresh.setOpaque(false);
        newColumnsTable.setName("import.newTable.table");
        Ui.styleTable(newColumnsTable);
        newColumnsTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeEditor()));
        newColumnsTable.getColumnModel().getColumn(3).setCellRenderer(new ValueRenderer());
        addKeyCheck.setName("import.target.addKey");
        addKeyCheck.setSelected(true);
        addKeyCheck.addActionListener(e -> refreshCreateSql());
        createSql.setName("import.target.sql");
        createSql.setEditable(false);
        createSql.setFont(new Font(Font.MONOSPACED, Font.PLAIN, createSql.getFont().getSize()));
        createSql.setBackground(Tokens.BG_SUNKEN);
        JPanel left = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_4)));
        left.setOpaque(false);
        left.add(Ui.scroll(newColumnsTable), BorderLayout.CENTER);
        left.add(addKeyCheck, BorderLayout.SOUTH);
        newColumnsTable.setPreferredScrollableViewportSize(new Dimension(Tokens.px(420), Tokens.px(Tokens.ROW_HEIGHT) * 4));
        createSql.setRows(4);
        createSql.setColumns(34);
        JPanel both = new JPanel(new java.awt.GridLayout(1, 2, Tokens.px(Tokens.SPACE_12), 0));
        both.setOpaque(false);
        both.add(left);
        both.add(Ui.scroll(createSql));
        fresh.add(both, BorderLayout.CENTER);
        targetBody.setOpaque(false);
        targetBody.add(existing, "existing");
        targetBody.add(fresh, "new");
        p.add(targetBanner, wide(3, 0));
        p.add(targetBody, wide(4, 1));
        return p;
    }

    private JComboBox<String> typeEditor() {
        JComboBox<String> types = new JComboBox<>(new String[] {"INT", "BIGINT", "DECIMAL(10,2)", "DOUBLE",
            "TINYINT(1)", "DATE", "DATETIME", "TIME", "CHAR(10)", "VARCHAR(50)", "VARCHAR(255)", "TEXT"});
        types.setEditable(true);
        types.setName("import.newTable.type");
        it.ramasql.app.theme.ComboTips.install(types, it.ramasql.app.theme.Tips::type);
        return types;
    }

    private JComponent optionsStep() {
        JPanel p = page();
        p.add(Ui.sectionTitle(Texts.get("import.options.intro")), wide(0, 0));
        truncateCheck.setName("import.option.truncate");
        duplicatesCombo.setName("import.option.duplicates");
        it.ramasql.app.theme.ComboTips.install(duplicatesCombo, it.ramasql.app.theme.Tips.of("import.option.duplicates",
                List.of(Texts.get("import.option.duplicates.error"), Texts.get("import.option.duplicates.ignore")),
                List.of("error", "ignore")));
        emptyNullCheck.setName("import.option.emptyNull");
        datesCombo.setName("import.option.dates");
        it.ramasql.app.theme.ComboTips.install(datesCombo, it.ramasql.app.theme.Tips.of("import.option.dates", List.of(Texts.get("import.option.dates.auto"),
                Texts.get("import.option.dates.DMY"), Texts.get("import.option.dates.MDY"),
                Texts.get("import.option.dates.YMD")), List.of("auto", "DMY", "MDY", "YMD")));
        optionsSummary.setName("import.options.summary");
        p.add(truncateCheck, wide(1, 0));
        p.add(label("import.option.duplicates.label"), at(0, 2));
        p.add(duplicatesCombo, at(1, 2));
        keepSize(duplicatesCombo, 280);
        keepSize(datesCombo, 280);
        p.add(emptyNullCheck, wide(3, 0));
        p.add(label("import.option.dates.label"), at(0, 4));
        p.add(datesCombo, at(1, 4));
        p.add(optionsSummary, wide(5, 0));
        p.add(Box.createGlue(), wide(6, 1));
        truncateCheck.addActionListener(e -> refreshOptionsSummary());
        datesCombo.addActionListener(e -> refreshOptionsSummary());
        return p;
    }

    private JComponent runStep() {
        JPanel p = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_8)));
        p.setBackground(Tokens.BG_SURFACE);
        p.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24)));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        runSummary.setName("import.run.summary");
        progress.setName("import.run.progress");
        progressLabel.setName("import.run.progressLabel");
        progressLabel.setForeground(Tokens.TEXT_SECONDARY);
        JPanel bar = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_12), 0));
        bar.setOpaque(false);
        bar.add(progress, BorderLayout.CENTER);
        bar.add(stopButton, BorderLayout.EAST);
        for (JComponent c : new JComponent[] {runSummary, bar, progressLabel, resultBanner}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            top.add(c);
            top.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)));
        }
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
        rejectedTable.setName("import.run.rejected");
        Ui.styleTable(rejectedTable);
        Ui.narrow(rejectedTable.getColumnModel().getColumn(0), Tokens.px(72));
        rejectedTable.setDefaultRenderer(Object.class, new ValueRenderer());
        rejectedTable.setPreferredScrollableViewportSize(new Dimension(Tokens.px(400), Tokens.px(Tokens.ROW_HEIGHT) * 4));
        JPanel actions = Ui.buttonRow(openTableButton);
        p.add(top, BorderLayout.NORTH);
        p.add(Ui.scroll(rejectedTable), BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);
        stopButton.setEnabled(false);
        openTableButton.setVisible(false);
        return p;
    }

    /** I suggerimenti di ogni controllo, dai file di risorse ({@code ADR-020}). */
    private void tooltips() {
        for (JComponent c : new JComponent[] {fileField, chooseButton, kindCombo, charsetCombo, separatorCombo,
            headerCheck, previewTable, existingRadio, newRadio, tableCombo, newNameField, mappingTable,
            newColumnsTable, addKeyCheck, createSql, truncateCheck, duplicatesCombo, emptyNullCheck, datesCombo,
            progress, stopButton, rejectedTable, openTableButton, back, next}) {
            c.setToolTipText(Texts.get(c.getName() + ".tooltip"));
        }
        for (int i = 0; i < stepLabels.size(); i++) {
            stepLabels.get(i).setToolTipText(Texts.get(stepLabels.get(i).getName() + ".tooltip"));
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
            boolean done = i < s.ordinal();
            l.setBackground(current ? Tokens.ACCENT_TINT : Tokens.BG_SURFACE);
            l.setForeground(current ? Tokens.ACCENT : done ? Tokens.TEXT_PRIMARY : Tokens.TEXT_TERTIARY);
            Styles.text(l, current ? "emphasis" : "smallText");
        }
        next.setText(Texts.get(s == Step.RUN ? "import.run.start" : "import.next"));
        next.setToolTipText(Texts.get(s == Step.RUN ? "import.run.start.tooltip" : "import.next.tooltip"));
        refreshButtons();
        revalidate();
        repaint();
    }

    private void refreshButtons() {
        back.setEnabled(step != Step.FILE && !running);
        boolean ok = switch (step) {
            case FILE -> file != null;
            case PREVIEW -> analysis != null && analysis.rows() > 0;
            case TARGET -> targetProblems().isEmpty();
            case OPTIONS -> true;
            case RUN -> report == null && !running;
        };
        next.setEnabled(ok && !running);
    }

    /** «Indietro». */
    public void back() {
        if (step.ordinal() > 0 && !running) {
            if (step == Step.RUN && report != null) {
                report = null;   // si torna indietro per cambiare qualcosa e rifare l'importazione
                rejectedModel.set(List.of());
                resultBanner.clear();
                openTableButton.setVisible(false);
            }
            show(Step.values()[step.ordinal() - 1]);
        }
    }

    /** «Avanti», o «Importa» all'ultimo passo. */
    public void next() {
        switch (step) {
            case FILE -> {
                show(Step.PREVIEW);
                analyze();
            }
            case PREVIEW -> {
                show(Step.TARGET);
                enterTarget();
            }
            case TARGET -> {
                show(Step.OPTIONS);
                enterOptions();
            }
            case OPTIONS -> {
                show(Step.RUN);
                enterRun();
            }
            case RUN -> start();
        }
    }

    // ================================================================ passo 1: file

    /** «Scegli…»: finestra per aprire un file CSV o JSON, poi riconoscimento del formato (in sottofondo). */
    public void chooseFile() {
        Path chosen = files.chooseToOpen(FilePrompts.Purpose.IMPORT_DATA);
        if (chosen != null) {
            setFile(chosen);
        }
    }

    /** Il file scelto: se ne riconosce il formato fuori dall'EDT. */
    public void setFile(Path path) {
        fileField.setText(path.toString());
        fileBanner.clear();
        detectedLabel.setText(Texts.get("import.file.detecting"));
        new SwingWorker<ImportFile, Void>() {
            @Override
            protected ImportFile doInBackground() throws Exception {
                return ImportFile.detect(path);
            }

            @Override
            protected void done() {
                try {
                    ImportFile detected = get();
                    applyDetected(detected);
                } catch (Exception e) {
                    file = null;
                    detectedLabel.setText(" ");
                    fileBanner.set(Banner.Tone.DANGER, Texts.get("import.file.unreadable", path.getFileName(),
                            cause(e)));
                    refreshButtons();
                }
            }
        }.execute();
    }

    private boolean applyingDetected;

    private void applyDetected(ImportFile detected) {
        applyingDetected = true;
        try {
            kindCombo.setSelectedItem(detected.kind().name());
            charsetCombo.setSelectedItem(detected.charset());
            separatorCombo.setSelectedItem(detected.csv().separator());
            headerCheck.setSelected(detected.csv().header());
        } finally {
            applyingDetected = false;
        }
        file = detected;
        detectedLabel.setText(Texts.get(detected.kind() == ImportFile.Kind.CSV ? "import.file.detected.csv"
                : "import.file.detected.json", detected.fileName(), Texts.get("import.charset." + detected.charset().name()),
                Texts.get("import.separator." + (int) detected.csv().separator())));
        refreshFormatControls();
        invalidateAnalysis();
        refreshButtons();
    }

    private void formatChanged() {
        if (applyingDetected || file == null) {
            refreshFormatControls();
            return;
        }
        Charset cs = (Charset) charsetCombo.getSelectedItem();
        char sep = (Character) separatorCombo.getSelectedItem();
        ImportFile.Kind kind = ImportFile.Kind.valueOf((String) kindCombo.getSelectedItem());
        file = new ImportFile(file.path(), kind, new CsvFormat(cs, sep, '"', headerCheck.isSelected()), List.of());
        refreshFormatControls();
        invalidateAnalysis();
        refreshButtons();
    }

    private void refreshFormatControls() {
        boolean csv = "CSV".equals(kindCombo.getSelectedItem());
        separatorCombo.setEnabled(csv);
        headerCheck.setEnabled(csv);
    }

    private void invalidateAnalysis() {
        cancelAnalysis.set(true);
        analysis = null;
    }

    // ================================================================ passo 2: anteprima e analisi

    private void analyze() {
        if (analysis != null) {
            refreshButtons();
            return;
        }
        ImportFile f = file;
        previewModel.set(List.of(), List.of());
        previewBanner.clear();
        previewSummary.setText(Texts.get("import.preview.reading", 0));
        AtomicBoolean cancel = new AtomicBoolean();
        cancelAnalysis.set(false);
        SwingWorker<FileAnalyzer.Analysis, Long> worker = new SwingWorker<>() {
            @Override
            protected FileAnalyzer.Analysis doInBackground() throws Exception {
                return FileAnalyzer.analyze(f, () -> cancel.get() || cancelAnalysis.get(), this::publishRows);
            }

            private void publishRows(long n) {
                publish(n);
            }

            @Override
            protected void process(List<Long> chunks) {
                previewSummary.setText(Texts.get("import.preview.reading", chunks.get(chunks.size() - 1)));
            }

            @Override
            protected void done() {
                if (analyzing != this) {
                    return;
                }
                analyzing = null;
                try {
                    analysis = get();
                    showAnalysis();
                } catch (CancellationException e) {
                    previewSummary.setText(" ");
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    if (c instanceof CancellationException) {
                        previewSummary.setText(" ");
                        return;
                    }
                    previewSummary.setText(" ");
                    previewBanner.set(Banner.Tone.DANGER, c instanceof ImportFileException ife ? ife.getMessage()
                            : Texts.get("import.file.unreadable", f.fileName(), cause(e)));
                }
                refreshButtons();
            }
        };
        analyzing = worker;
        worker.execute();
    }

    private void showAnalysis() {
        FileAnalyzer.Analysis a = analysis;
        List<List<Object>> values = new ArrayList<>();
        for (SourceRow r : a.preview()) {
            values.add(r.values());
        }
        previewModel.set(a.columns(), values);
        for (int i = 0; i < previewTable.getColumnCount(); i++) {
            previewTable.getColumnModel().getColumn(i).setPreferredWidth(Tokens.px(140));
        }
        previewSummary.setText(Texts.get("import.preview.summary", a.rows(), a.columns().size(),
                Math.min(a.rows(), FileAnalyzer.PREVIEW_ROWS)));
        List<Banner.Line> lines = new ArrayList<>();
        if (a.rows() == 0) {
            lines.add(new Banner.Line(Banner.Tone.DANGER, Texts.get("import.preview.empty")));
        }
        if (a.extraValues() > 0) {
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("import.preview.extra", a.extraValues())));
        }
        if (a.skippedEmpty() > 0) {
            lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("import.preview.skipped", a.skippedEmpty())));
        }
        if (a.encodingFixed()) {
            applyingDetected = true;
            try {
                charsetCombo.setSelectedItem(a.file().charset());
            } finally {
                applyingDetected = false;
            }
            lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("import.preview.encodingFixed")));
        } else if (a.replacements() > 0) {
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("import.preview.encoding", a.replacements())));
        }
        if (a.shortRows() > 0) {
            lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("import.preview.short", a.shortRows())));
        }
        if (a.file().kind() == ImportFile.Kind.CSV && a.columns().size() == 1) {
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("import.preview.oneColumn")));
        }
        previewBanner.set(lines);
        file = a.file();
    }

    // ================================================================ passo 3: destinazione

    private void loadCatalogTables() {
        new SwingWorker<List<TableSummary>, Void>() {
            @Override
            protected List<TableSummary> doInBackground() throws Exception {
                return reader.tables(catalog);
            }

            @Override
            protected void done() {
                try {
                    List<TableSummary> all = get();
                    catalogTables = all.stream().filter(t -> !t.isView()).toList();
                    catalogNames = all.stream().map(TableSummary::name).toList();
                } catch (Exception e) {
                    catalogTables = List.of();
                    catalogNames = List.of();
                }
                String keep = pendingTable != null ? pendingTable : (String) tableCombo.getSelectedItem();
                tableCombo.removeAllItems();
                for (TableSummary t : catalogTables) {
                    tableCombo.addItem(t.name());
                }
                if (keep != null) {
                    tableCombo.setSelectedItem(keep);
                }
                pendingTable = null;
                if (catalogTables.isEmpty()) {
                    newRadio.setSelected(true);
                    existingRadio.setEnabled(false);
                }
                if (step == Step.TARGET) {
                    targetChanged();
                }
            }
        }.execute();
    }

    private void enterTarget() {
        if (newNameField.getText().isBlank()) {
            String base = file.fileName().replaceFirst("\\.[^.]*$", "");
            newNameField.setText(base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", ""));
        }
        buildNewColumns();
        targetChanged();
    }

    private void targetChanged() {
        boolean fresh = newRadio.isSelected();
        tableCombo.setEnabled(!fresh);
        newNameField.setEnabled(fresh);
        targetCards.show(targetBody, fresh ? "new" : "existing");
        if (fresh) {
            refreshCreateSql();
        } else {
            loadExistingTable();
        }
        refreshButtons();
    }

    private void loadExistingTable() {
        String name = (String) tableCombo.getSelectedItem();
        if (name == null || analysis == null) {
            existingTable = null;
            mappingModel.set(List.of());
            refreshTargetProblems();
            return;
        }
        FileAnalyzer.Analysis a = analysis;
        new SwingWorker<TableDef, Void>() {
            @Override
            protected TableDef doInBackground() throws SQLException {
                return reader.table(catalog, name).orElse(null);
            }

            @Override
            protected void done() {
                if (!name.equals(tableCombo.getSelectedItem()) || a != analysis) {
                    return;
                }
                try {
                    existingTable = get();
                } catch (Exception e) {
                    existingTable = null;
                }
                List<MappingRow> rowsOut = new ArrayList<>();
                if (existingTable != null) {
                    Map<Integer, ColumnDef> auto = ImportPlanner.autoMapping(a.columns(), existingTable.columns());
                    mappingEditor.removeAllItems();
                    mappingEditor.addItem(SKIP);
                    for (ColumnDef c : existingTable.columns()) {
                        if (!c.generated()) {
                            mappingEditor.addItem(c.name());
                        }
                    }
                    for (int i = 0; i < a.columns().size(); i++) {
                        ColumnDef target = auto.get(i);
                        rowsOut.add(new MappingRow(i, a.columns().get(i), sample(a, i),
                                target == null ? SKIP : target.name()));
                    }
                }
                mappingModel.set(rowsOut);
                refreshTargetProblems();
            }
        }.execute();
    }

    private static String sample(FileAnalyzer.Analysis a, int column) {
        for (SourceRow r : a.preview()) {
            Object v = r.values().get(column);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return "";
    }

    private void buildNewColumns() {
        List<NewColumnRow> out = new ArrayList<>();
        for (TypeInference.Inferred inf : analysis.inferred()) {
            out.add(new NewColumnRow(inf.column().name(), inf.column().fullType(), inf.column().nullable(),
                    inf.explanation()));
        }
        newColumnsModel.set(out);
        refreshCreateSql();
    }

    /** La tabella nuova come la vuole l'utente: nomi e tipi della griglia, chiave dal file o aggiunta. */
    TableDef newTableDef() {
        String name = newNameField.getText().trim();
        TableDef proposed = ImportPlanner.newTable(catalog, name.isEmpty() ? "x" : name, analysis,
                addKeyCheck.isSelected());
        String pk = proposed.primaryKey().map(k -> k.columns().get(0)).orElse(null);
        String newPk = pk;
        List<ColumnDef> columns = new ArrayList<>();
        for (ColumnDef c : proposed.columns()) {
            int index = analysis.columns().indexOf(c.name());
            if (index >= 0 && index < newColumnsModel.rows.size()) {
                NewColumnRow r = newColumnsModel.rows.get(index);
                String renamed = newColumnName(index);
                if (c.name().equals(pk)) {
                    newPk = renamed;
                }
                columns.add(withType(c, r.type).withName(renamed));
            } else {
                columns.add(c);   // la chiave «id» aggiunta
            }
        }
        TableDef t = proposed.withName(name.isEmpty() ? "x" : name).withColumns(columns);
        return newPk == null ? t : t.withIndexes(List.of(it.ramasql.core.metadata.IndexDef.primary(newPk)));
    }

    /** Il nome della colonna {@code i} del file nella tabella nuova: quello scritto, o quello del file se vuoto. */
    private String newColumnName(int i) {
        String n = newColumnsModel.rows.get(i).name.trim();
        return n.isEmpty() ? analysis.columns().get(i) : n;
    }

    private static ColumnDef withType(ColumnDef c, String typeText) {
        Matcher m = TYPE.matcher(typeText == null ? "" : typeText);
        if (!m.matches()) {
            return c;
        }
        return c.withType(m.group(1).toUpperCase(Locale.ROOT), m.group(2)).withUnsigned(m.group(3) != null);
    }

    private void refreshCreateSql() {
        if (analysis == null) {
            return;
        }
        String name = newNameField.getText().trim();
        createSql.setText(name.isEmpty() ? "" : TableDiff.createTable(newTableDef().withCatalog(catalog)));
        createSql.setCaretPosition(0);
        refreshTargetProblems();
    }

    /** Ciò che impedisce di andare avanti al passo 3 (vuoto = si può). */
    List<String> targetProblems() {
        List<String> out = new ArrayList<>();
        if (analysis == null) {
            out.add(Texts.get("import.target.noAnalysis"));
            return out;
        }
        if (newRadio.isSelected()) {
            String name = newNameField.getText().trim();
            if (name.isEmpty()) {
                out.add(Texts.get("import.target.noName"));
            } else if (name.length() > 64) {
                out.add(Texts.get("import.target.tooLong", name.length()));
            } else if (catalogNames.stream().anyMatch(name::equalsIgnoreCase)) {
                out.add(Texts.get("import.target.exists", name));
            }
            for (NewColumnRow r : newColumnsModel.rows) {
                if (r.name.trim().isEmpty() && r.original.isBlank()) {
                    out.add(Texts.get("import.target.emptyColumn"));
                } else if (r.name.trim().length() > 64) {
                    out.add(Texts.get("import.target.columnTooLong", r.name.trim()));
                }
                if (!TYPE.matcher(r.type == null ? "" : r.type).matches()) {
                    out.add(Texts.get("import.target.badType", r.name, r.type));
                }
            }
            if (out.isEmpty()) {
                TableDef t = newTableDef();
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (ColumnDef c : t.columns()) {
                    if (!seen.add(c.name().toLowerCase(Locale.ROOT))) {
                        out.add(Texts.get("import.target.duplicateColumn", c.name()));
                    }
                    if (c.autoIncrement() && !it.ramasql.core.metadata.SqlTypes.isInteger(c.dataType())) {
                        out.add(Texts.get("import.target.keyNotInteger", c.name(), c.fullType()));
                    }
                }
            }
        } else {
            if (existingTable == null) {
                out.add(Texts.get("import.target.noTable"));
            } else if (mappingModel.rows.stream().allMatch(r -> SKIP.equals(r.target))) {
                out.add(Texts.get("import.plan.noColumns"));
            } else {
                java.util.Set<String> used = new java.util.HashSet<>();
                for (MappingRow r : mappingModel.rows) {
                    if (!SKIP.equals(r.target) && !used.add(r.target.toLowerCase(Locale.ROOT))) {
                        out.add(Texts.get("import.target.twice", r.target));
                    }
                }
            }
        }
        return out;
    }

    private void refreshTargetProblems() {
        List<Banner.Line> lines = new ArrayList<>();
        for (String p : targetProblems()) {
            if (analysis != null) {
                lines.add(new Banner.Line(Banner.Tone.DANGER, p));
            }
        }
        if (existingRadio.isSelected() && existingTable != null) {
            // colonne obbligatorie non riempite: ogni riga verrebbe rifiutata dal server
            java.util.Set<String> mapped = new java.util.HashSet<>();
            mappingModel.rows.forEach(r -> mapped.add(r.target.toLowerCase(Locale.ROOT)));
            for (ColumnDef c : existingTable.columns()) {
                if (!c.nullable() && !c.autoIncrement() && c.defaultValue().isNone() && !c.generated()
                        && !mapped.contains(c.name().toLowerCase(Locale.ROOT))) {
                    lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("import.target.required", c.name())));
                }
            }
        }
        targetBanner.set(lines);
        refreshButtons();
    }

    // ================================================================ passo 4: opzioni

    private void enterOptions() {
        boolean fresh = newRadio.isSelected();
        truncateCheck.setEnabled(false);
        truncateCheck.setToolTipText(Texts.get("import.option.truncate.tooltip"));
        referencedBy = List.of();
        if (fresh) {
            truncateCheck.setSelected(false);
            refreshOptionsSummary();
            return;
        }
        // TRUNCATE è rifiutato (1701) su una tabella riferita da chiavi esterne, anche se le tabelle figlie sono
        // vuote: lo si dice qui invece di scoprirlo dopo la conferma rafforzata
        String table = existingTable == null ? null : existingTable.name();
        truncateChecking = true;
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws SQLException {
                List<String> out = new ArrayList<>();
                if (table != null) {
                    for (TableDef t : reader.allTables(catalog)) {
                        boolean refers = t.foreignKeys().stream().anyMatch(fk -> fk.refTable().equalsIgnoreCase(table)
                                && (fk.refCatalog() == null || fk.refCatalog().equalsIgnoreCase(catalog))
                                && !t.name().equalsIgnoreCase(table));
                        if (refers) {
                            out.add(t.name());
                        }
                    }
                }
                return out;
            }

            @Override
            protected void done() {
                truncateChecking = false;
                try {
                    referencedBy = get();
                } catch (Exception e) {
                    referencedBy = List.of();
                }
                boolean allowed = referencedBy.isEmpty();
                truncateCheck.setEnabled(allowed && !running);
                if (!allowed) {
                    truncateCheck.setSelected(false);
                    truncateCheck.setToolTipText(Texts.get("import.option.truncate.referenced", table,
                            String.join(", ", referencedBy)));
                }
                refreshOptionsSummary();
            }
        }.execute();
        refreshOptionsSummary();
    }

    private boolean truncateChecking;

    /** L'opzione «svuota prima» si può scegliere (tabella esistente non riferita da chiavi esterne). */
    public boolean truncateAllowed() {
        return truncateCheck.isEnabled();
    }

    /** Il controllo delle chiavi esterne che riferiscono la tabella (per «svuota prima») è in corso. */
    public boolean isCheckingTruncate() {
        return truncateChecking;
    }

    private void refreshOptionsSummary() {
        String table = newRadio.isSelected() ? newNameField.getText().trim() : String.valueOf(tableCombo.getSelectedItem());
        StringBuilder text = new StringBuilder(Texts.get(truncateCheck.isSelected() ? "import.options.summary.truncate"
                : "import.options.summary", analysis == null ? 0 : analysis.rows(), table));
        if (!referencedBy.isEmpty() && existingRadio.isSelected()) {
            text.append('\n').append(Texts.get("import.option.truncate.referenced", table, String.join(", ", referencedBy)));
        }
        if (analysis != null) {
            // l'ordine di giorno e mese che si userà per le date con le barre, colonna per colonna
            Map<DateOrder, List<String>> byOrder = new java.util.EnumMap<>(DateOrder.class);
            for (int i = 0; i < analysis.columns().size(); i++) {
                TypeInference.Inferred inf = analysis.inferred().get(i);
                String t = inf.column().dataType();
                if ((t.equals("DATE") || t.equals("DATETIME")) && analysis.profiles().get(i).nonEmptyCount() > 0) {
                    DateOrder order = datesCombo.getSelectedIndex() == 0 ? inf.dates()
                            : DateOrder.values()[datesCombo.getSelectedIndex() - 1];
                    byOrder.computeIfAbsent(order, k -> new ArrayList<>()).add(analysis.columns().get(i));
                }
            }
            byOrder.forEach((order, cols) -> text.append('\n').append(Texts.get("import.options.dates." + order.name(),
                    String.join(", ", cols))));
        }
        optionsSummary.setText(text.toString());
    }

    // ================================================================ passo 5: importazione

    /** Il piano deciso nei passi 1-4. */
    public ImportPlan plan() {
        ImportPlan.Options options = new ImportPlan.Options(truncateCheck.isSelected() && existingRadio.isSelected(),
                duplicatesCombo.getSelectedIndex() == 1, emptyNullCheck.isSelected(), switch (datesCombo.getSelectedIndex()) {
                    case 1 -> DateOrder.DMY;
                    case 2 -> DateOrder.MDY;
                    case 3 -> DateOrder.YMD;
                    default -> null;
                });
        if (newRadio.isSelected()) {
            TableDef t = newTableDef();
            // l'ordine delle colonne del file coincide con quello delle righe della griglia; il nome è quello di
            // newTableDef (lo stesso ripiego se l'utente lo cancella)
            Map<Integer, ColumnDef> byFile = new LinkedHashMap<>();
            for (int i = 0; i < analysis.columns().size(); i++) {
                String name = newColumnName(i);
                int index = i;
                t.column(name).ifPresent(c -> byFile.put(index, c));
            }
            return new ImportPlan(file, catalog, t, true, ImportPlanner.mappings(analysis, byFile), options,
                    analysis.rows());
        }
        Map<Integer, ColumnDef> map = new LinkedHashMap<>();
        for (MappingRow r : mappingModel.rows) {
            if (!SKIP.equals(r.target)) {
                existingTable.column(r.target).ifPresent(c -> map.put(r.fileIndex, c));
            }
        }
        return new ImportPlan(file, catalog, existingTable, false, ImportPlanner.mappings(analysis, map), options,
                analysis.rows());
    }

    private void enterRun() {
        ImportPlan p = plan();
        runSummary.setText(Texts.get("import.run.summary", p.expectedRows(), p.table().name(), p.expectedBatches(),
                p.insert().rowsPerBatch()));
        progress.setValue(0);
        progress.setMaximum(100);
        progressLabel.setText(" ");
        refreshButtons();
    }

    /** «Importa»: l'SQL nell'anteprima; con <em>Esegui</em> l'importazione a lotti. */
    public void start() {
        if (running || report != null) {
            return;
        }
        ImportPlan p = plan();
        ImportRows source = p.rows();
        rows = source;
        expectedRows = p.expectedRows();
        running = true;
        resultBanner.clear();
        rejectedModel.set(List.of());
        progress.setIndeterminate(false);
        progress.setMaximum((int) Math.max(1, Math.min(Integer.MAX_VALUE, expectedRows)));
        progress.setValue(0);
        stopButton.setEnabled(true);
        refreshButtons();
        BatchListener listener = new BatchListener() {
            @Override
            public void batchFinished(long batches, long inserted, long rejected, long duplicates) {
                long read = source.read();
                SwingUtilities.invokeLater(() -> {
                    progress.setValue((int) Math.min(progress.getMaximum(), read));
                    progressLabel.setText(Texts.get("import.run.progress", read, expectedRows, inserted, batches));
                });
            }
        };
        CompletableFuture<BatchResult> future = pipeline.proposeBatchInsert(p.script(), p.insert(), source, listener);
        future.whenComplete((result, error) -> SwingUtilities.invokeLater(() -> finished(p, source, result, error)));
    }

    private void finished(ImportPlan p, ImportRows source, BatchResult result, Throwable error) {
        running = false;
        stopButton.setEnabled(false);
        try {
            source.close();
        } catch (java.io.IOException ignored) {
            // il file si chiude comunque con il processo
        }
        if (error != null || result == null) {
            // annullata nell'anteprima (o esecutore chiuso): si può riprovare
            progressLabel.setText(error == null ? Texts.get("import.run.cancelled") : " ");
            refreshButtons();
            return;
        }
        if (!result.started()) {
            progressLabel.setText(" ");
            resultBanner.set(Banner.Tone.DANGER, Texts.get("import.run.notStarted"));
            refreshButtons();
            return;
        }
        report = ImportReport.of(p, source, result);
        progress.setValue(progress.getMaximum());
        List<Banner.Line> lines = new ArrayList<>();
        Banner.Tone tone = report.completed() && report.rejectedCount() == 0 ? Banner.Tone.SUCCESS
                : report.completed() ? Banner.Tone.WARNING : Banner.Tone.DANGER;
        String key = report.completed() ? "import.report.done" : report.interrupted() ? "import.report.interrupted"
                : "import.report.stopped";
        lines.add(new Banner.Line(tone, Texts.get(key, report.read(), report.inserted(), report.rejectedCount(),
                report.duplicatesIgnored(), report.batches())));
        if (result.fatal() != null) {
            lines.add(new Banner.Line(Banner.Tone.DANGER, Texts.get("panel.messages.serverError", result.fatal().code(),
                    result.fatal().sqlState(), result.fatal().message())));
        }
        if (result.sourceFailure() != null) {
            lines.add(new Banner.Line(Banner.Tone.DANGER, result.sourceFailure()));
        }
        if (report.skippedEmpty() > 0) {
            lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("import.preview.skipped", report.skippedEmpty())));
        }
        if (report.rejectedCount() > report.rejected().size()) {
            lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("import.report.more",
                    report.rejectedCount() - report.rejected().size())));
        }
        resultBanner.set(lines);
        rejectedModel.set(report.rejected());
        progressLabel.setText(Texts.get("import.run.progress", report.read(), expectedRows, report.inserted(),
                report.batches()));
        openTableButton.setVisible(report.inserted() > 0 || p.newTable());
        if (p.newTable()) {
            reader.invalidate(catalog);
            loadCatalogTables();   // la tabella appena creata: un secondo «Importa» qui non può riusarne il nome
        }
        refreshButtons();
    }

    /** «Interrompi»: ferma l'importazione dopo l'istruzione in corso; le righe già inserite restano. */
    public void interrupt() {
        if (!running) {
            return;
        }
        stopButton.setEnabled(false);
        progressLabel.setText(Texts.get("import.run.stopping"));
        try {
            if (!pipeline.executor().interrupt()) {
                // l'importazione era appena finita, o non era ancora partita: niente da fermare
                stopButton.setEnabled(running);
                progressLabel.setText(Texts.get("import.run.nothingToStop"));
            }
        } catch (SQLException e) {
            stopButton.setEnabled(running);
            progressLabel.setText(Texts.get("import.run.stopFailed", e.getMessage()));
        }
    }

    private void openImportedTable() {
        if (report != null && openTable != null) {
            openTable.accept(catalog, report.table());
        }
    }

    // ================================================================ per i test e per la finestra

    public boolean isRunning() {
        return running;
    }

    /** L'analisi del file è finita (con esito). */
    public boolean isAnalyzing() {
        return analyzing != null;
    }

    public FileAnalyzer.Analysis analysis() {
        return analysis;
    }

    public ImportReport report() {
        return report;
    }

    public ImportFile file() {
        return file;
    }

    public String catalog() {
        return catalog;
    }

    public JButton nextButton() {
        return next;
    }

    public JButton backButton() {
        return back;
    }

    public JButton stopButton() {
        return stopButton;
    }

    public JButton openTableButton() {
        return openTableButton;
    }

    public JTable previewTable() {
        return previewTable;
    }

    public JTable mappingTable() {
        return mappingTable;
    }

    public JTable newColumnsTable() {
        return newColumnsTable;
    }

    public JTable rejectedTable() {
        return rejectedTable;
    }

    public JRadioButton existingRadio() {
        return existingRadio;
    }

    public JRadioButton newRadio() {
        return newRadio;
    }

    public JComboBox<String> tableCombo() {
        return tableCombo;
    }

    public JTextField newNameField() {
        return newNameField;
    }

    public JTextArea createSqlArea() {
        return createSql;
    }

    public JCheckBox truncateCheck() {
        return truncateCheck;
    }

    public JComboBox<String> duplicatesCombo() {
        return duplicatesCombo;
    }

    public JCheckBox emptyNullCheck() {
        return emptyNullCheck;
    }

    public JComboBox<String> datesCombo() {
        return datesCombo;
    }

    public JComboBox<Charset> charsetCombo() {
        return charsetCombo;
    }

    public JComboBox<Character> separatorCombo() {
        return separatorCombo;
    }

    public JCheckBox headerCheck() {
        return headerCheck;
    }

    public JLabel detectedLabel() {
        return detectedLabel;
    }

    public JLabel previewSummary() {
        return previewSummary;
    }

    public Banner previewBanner() {
        return previewBanner;
    }

    public Banner targetBanner() {
        return targetBanner;
    }

    public Banner resultBanner() {
        return resultBanner;
    }

    public JLabel progressLabel() {
        return progressLabel;
    }

    /** L'abbinamento del passo 3 per la colonna del file (vuoto = non importare). */
    public void setMapping(String fileColumn, String tableColumn) {
        for (int i = 0; i < mappingModel.rows.size(); i++) {
            if (mappingModel.rows.get(i).fileName.equals(fileColumn)) {
                mappingModel.setValueAt(tableColumn == null ? SKIP : tableColumn, i, 2);
            }
        }
    }

    /** Il tipo di una colonna della tabella nuova, come se l'utente l'avesse scritto nella griglia. */
    public void setNewColumnType(String fileColumn, String type) {
        int i = analysis.columns().indexOf(fileColumn);
        newColumnsModel.setValueAt(type, i, 1);
    }

    /** La scheda si può chiudere (nessuna importazione in corso). */
    public boolean canClose() {
        return !running;
    }

    /** Ferma un'analisi in corso (chiusura della scheda). */
    public void dispose() {
        cancelAnalysis.set(true);
    }

    /** La connessione si chiude con l'importazione in corso: la si ferma (le righe inserite restano). */
    public void stopForClose() {
        cancelAnalysis.set(true);
        if (running) {
            try {
                pipeline.executor().interrupt();
            } catch (SQLException ignored) {
                // la sessione si sta chiudendo comunque
            }
        }
    }

    private static String cause(Exception e) {
        Throwable t = e.getCause() != null ? e.getCause() : e;
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    // ================================================================ modelli delle griglie

    /** Anteprima: le prime righe del file; NULL (assente) si vede come tale. */
    private static final class PreviewModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private List<String> columns = List.of();
        private List<List<Object>> values = List.of();

        void set(List<String> newColumns, List<List<Object>> newValues) {
            columns = newColumns;
            values = newValues;
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return values.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column);
        }

        @Override
        public Object getValueAt(int row, int column) {
            return values.get(row).get(column);
        }
    }

    private static final class MappingRow {
        final int fileIndex;
        final String fileName;
        final String sample;
        String target;

        MappingRow(int fileIndex, String fileName, String sample, String target) {
            this.fileIndex = fileIndex;
            this.fileName = fileName;
            this.sample = sample;
            this.target = target;
        }
    }

    /** Abbinamento: colonna del file · esempio · colonna della tabella (modificabile). */
    private final class MappingModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<MappingRow> rows = new ArrayList<>();

        void set(List<MappingRow> newRows) {
            rows.clear();
            rows.addAll(newRows);
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
            return Texts.get("import.mapping.column." + column);
        }

        @Override
        public Object getValueAt(int row, int column) {
            MappingRow r = rows.get(row);
            return switch (column) {
                case 0 -> r.fileName;
                case 1 -> r.sample;
                default -> r.target;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 2 && !running;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            rows.get(row).target = value == null ? SKIP : value.toString();
            fireTableRowsUpdated(row, row);
            refreshTargetProblems();
        }
    }

    private static final class NewColumnRow {
        final String original;
        String name;
        String type;
        final boolean nullable;
        final String why;

        NewColumnRow(String name, String type, boolean nullable, String why) {
            this.original = name;
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.why = why;
        }
    }

    /** Colonne della tabella nuova: nome e tipo modificabili, annullabile e motivo del tipo in sola lettura. */
    private final class NewColumnsModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<NewColumnRow> rows = new ArrayList<>();

        void set(List<NewColumnRow> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return Texts.get("import.newTable.column." + column);
        }

        @Override
        public Object getValueAt(int row, int column) {
            NewColumnRow r = rows.get(row);
            return switch (column) {
                case 0 -> r.name;
                case 1 -> r.type;
                case 2 -> Texts.get(r.nullable ? "import.newTable.null.yes" : "import.newTable.null.no");
                default -> r.why;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column <= 1 && !running;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0) {
                rows.get(row).name = String.valueOf(value);
            } else if (column == 1) {
                rows.get(row).type = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
            }
            fireTableRowsUpdated(row, row);
            refreshCreateSql();
        }
    }

    /** Righe scartate: riga del file e motivo (spiegato in italiano se l'ha rifiutata il server). */
    private static final class RejectedModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private List<ImportReport.Rejection> rows = List.of();

        void set(List<ImportReport.Rejection> newRows) {
            rows = newRows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return Texts.get("import.rejected.column." + column);
        }

        @Override
        public Object getValueAt(int row, int column) {
            ImportReport.Rejection r = rows.get(row);
            return column == 0 ? r.line() : reason(r);
        }
    }

    /** Il motivo come lo legge lo studente: per un rifiuto del server, la spiegazione e poi il messaggio originale. */
    public static String reason(ImportReport.Rejection r) {
        if (r.serverCode() == 0) {
            return r.reason();
        }
        String original = Texts.get("import.rejected.server", r.serverCode(), r.reason());
        return ErrorExplainer.explain(r.serverCode()).map(x -> x + " " + original).orElse(original);
    }

    /** Testo di una voce di lista preso dai file di risorse. */
    private static final class ItemRenderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private final java.util.function.Function<Object, String> text;

        ItemRenderer(java.util.function.Function<Object, String> text) {
            this.text = text;
        }

        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value == null ? "" : text.apply(value), index, isSelected,
                    cellHasFocus);
            return this;
        }
    }

    /** Valori della griglia: vuoto e assente distinti, testo lungo su una riga. */
    private static final class ValueRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        protected void setValue(Object value) {
            if (value == null) {
                setText(Texts.get("import.preview.null"));
                setForeground(Tokens.TEXT_TERTIARY);
            } else {
                setText(value.toString().replace('\n', '⏎'));
                setForeground(Tokens.TEXT_PRIMARY);
            }
            // il testo intero nel suggerimento: le celle lunghe (motivi, spiegazioni) si leggono senza allargare
            String full = value == null ? null : value.toString();
            setToolTipText(full != null && full.length() > 30 ? full : null);
        }

        @Override
        public Dimension getPreferredSize() {
            return super.getPreferredSize();
        }
    }
}
