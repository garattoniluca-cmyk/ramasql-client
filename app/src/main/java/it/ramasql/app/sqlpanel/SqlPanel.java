/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.sqlpanel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import com.formdev.flatlaf.icons.FlatSearchIcon;

import it.ramasql.app.Texts;
import it.ramasql.app.editor.ErrorExplainer;
import it.ramasql.app.navigator.NavigatorIcons;
import it.ramasql.app.pipeline.PipelineView;
import it.ramasql.app.pipeline.SqlText;
import it.ramasql.app.theme.Pill;
import it.ramasql.app.theme.Tokens;
import it.ramasql.app.workspace.WorkspacePrompts;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.exec.StatementResult;

/**
 * Il <b>Pannello SQL</b> in basso (DESIGN §3.5), sempre presente, con tre schede:
 * <ul>
 *   <li><b>Registro</b>: ogni istruzione eseguita per conto dell'utente (alimentato da {@link SqlLog}): ora, origine,
 *       SQL, esito, durata, righe; le righe in errore sono evidenziate. Filtro per testo; doppio clic copia l'SQL negli
 *       appunti (il collegamento a una scheda dell'editor arriverà con l'editor SQL); <em>Esporta…</em> salva un
 *       {@code .sql} rieseguibile ({@link SqlLog#exportScript}).</li>
 *   <li><b>Anteprima</b>: l'SQL dell'operazione in corso di composizione (per ora: l'ultimo script proposto).</li>
 *   <li><b>Messaggi</b>: esiti, errori e avvisi in italiano con il codice originale del server; dopo ogni esecuzione
 *       dice che cosa è stato applicato e che cosa no.</li>
 * </ul>
 * Il registro appartiene alla finestra: resta anche cambiando connessione.
 */
public final class SqlPanel extends JTabbedPane implements PipelineView {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    /** Un messaggio mostrato nella scheda Messaggi. */
    public record Message(Instant time, MessageKind kind, String text) {
    }

    private final transient SqlLog log;
    private final transient WorkspacePrompts prompts;
    private final transient Supplier<String> exportCatalogCandidate;

    private final LogTableModel logModel = new LogTableModel();
    /** Righe del registro arrivate e non ancora mostrate (da qualunque thread). */
    private final java.util.concurrent.ConcurrentLinkedQueue<SqlLog.Entry> pendingEntries =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicBoolean flushScheduled =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final JTable logTable = new JTable(logModel);
    private final TableRowSorter<LogTableModel> sorter = new TableRowSorter<>(logModel);
    private final JTextField logFilter = new JTextField(22);
    private final JButton export = new JButton(Texts.get("panel.log.export"));

    private final CardLayout previewCards = new CardLayout();
    private final JPanel previewHost = new JPanel(previewCards);
    private final JLabel previewTitle = new JLabel();
    private final RSyntaxTextArea previewSql = SqlText.readOnly("", 6, 60);
    private transient SqlScript previewed;

    private final JTextPane messagesPane = new JTextPane();
    private final transient List<Message> messages = new ArrayList<>();

    /**
     * @param log                    registro della finestra
     * @param prompts                finestre (esportazione, appunti)
     * @param exportCatalogCandidate catalogo proposto per l'esportazione «senza nome del catalogo» (quello selezionato
     *                               nel navigatore), {@code null} se nessuno
     */
    public SqlPanel(SqlLog log, WorkspacePrompts prompts, Supplier<String> exportCatalogCandidate) {
        this.log = log;
        this.prompts = prompts;
        this.exportCatalogCandidate = exportCatalogCandidate;
        setName("zone.sqlPanel");
        setMinimumSize(new Dimension(100, 0));
        // DESIGN-SYSTEM §3.6: schede a sottolineatura, 2 px d'accento sulla scheda attiva
        putClientProperty("FlatLaf.style", "underlineColor: " + Tokens.hex(Tokens.ACCENT)
                + "; inactiveUnderlineColor: " + Tokens.hex(Tokens.ACCENT) + "; tabSelectionHeight: 2");
        addTab(Texts.get("sqlPanel.log"), buildLogTab());
        addTab(Texts.get("sqlPanel.preview"), buildPreviewTab());
        addTab(Texts.get("sqlPanel.messages"), buildMessagesTab());
        setToolTipTextAt(0, Texts.get("sqlPanel.log.tooltip"));
        setToolTipTextAt(1, Texts.get("sqlPanel.preview.tooltip"));
        setToolTipTextAt(2, Texts.get("sqlPanel.messages.tooltip"));
        it.ramasql.app.theme.Tips.fromNames(this);
        it.ramasql.app.theme.Tips.headers(logTable);

        log.entries().forEach(logModel::add);
        log.addListener(new SqlLog.Listener() {
            @Override
            public void entryAdded(SqlLog.Entry entry) {
                // a blocchi: le righe arrivate nel frattempo entrano con un solo passaggio sull'EDT (uno script
                // lungo ne registra migliaia di fila, un evento per riga bloccherebbe l'interfaccia)
                pendingEntries.add(entry);
                if (flushScheduled.compareAndSet(false, true)) {
                    SwingUtilities.invokeLater(SqlPanel.this::flushEntries);
                }
            }

            @Override
            public void cleared() {
                pendingEntries.clear();
                SwingUtilities.invokeLater(logModel::clear);
            }
        });
    }

    // ------------------------------------------------------------------ Registro

    /** Sull'EDT: le righe in attesa entrano tutte insieme, poi si scorre all'ultima. */
    private void flushEntries() {
        flushScheduled.set(false);
        java.util.List<SqlLog.Entry> batch = new java.util.ArrayList<>();
        for (SqlLog.Entry e; (e = pendingEntries.poll()) != null;) {
            batch.add(e);
        }
        if (batch.isEmpty()) {
            return;
        }
        logModel.addAll(batch);
        int last = logTable.convertRowIndexToView(logModel.getRowCount() - 1);
        if (last >= 0) {
            logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
        }
    }

    private JPanel buildLogTab() {
        logTable.setName("panel.log.table");
        logTable.setRowSorter(sorter);
        logTable.setFillsViewportHeight(true);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setDefaultRenderer(Object.class, new LogRenderer());
        logTable.getTableHeader().setReorderingAllowed(false);
        logTable.setRowHeight(Tokens.px(Tokens.TREE_ROW_HEIGHT));
        logTable.setShowGrid(false);
        logTable.setIntercellSpacing(new Dimension(0, 0));
        logTable.setBackground(Tokens.BG_SURFACE);
        logTable.setSelectionBackground(Tokens.ACCENT_TINT);
        logTable.setSelectionForeground(Tokens.TEXT_PRIMARY);
        logTable.getTableHeader().putClientProperty("FlatLaf.style", "background: " + Tokens.hex(Tokens.BG_SUNKEN)
                + "; separatorColor: " + Tokens.hex(Tokens.BORDER_SUBTLE)
                + "; bottomSeparatorColor: " + Tokens.hex(Tokens.BORDER_SUBTLE));
        TableColumnModel cols = logTable.getColumnModel();
        int[] widths = {40, 70, 110, 560, 110, 70, 60};
        for (int i = 0; i < widths.length; i++) {
            cols.getColumn(i).setPreferredWidth(Tokens.px(widths[i]));
        }
        logTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = logTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        copyEntry(logTable.convertRowIndexToModel(row));
                    }
                }
            }
        });

        logFilter.setName("panel.log.filter");
        logFilter.putClientProperty("JTextField.placeholderText", Texts.get("panel.log.filter.placeholder"));
        logFilter.putClientProperty("JTextField.showClearButton", true);
        logFilter.putClientProperty("JTextField.leadingIcon", new FlatSearchIcon());
        logFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        export.setName("panel.log.export");
        export.setToolTipText(Texts.get("panel.log.export.tooltip"));
        export.addActionListener(e -> exportLog());

        JPanel bar = new JPanel(new BorderLayout(Tokens.px(Tokens.SPACE_8), 0));
        bar.setBackground(Tokens.BG_WINDOW);
        bar.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12)));
        bar.add(logFilter, BorderLayout.WEST);
        bar.add(export, BorderLayout.EAST);
        JPanel tab = new JPanel(new BorderLayout());
        tab.add(bar, BorderLayout.NORTH);
        tab.add(new JScrollPane(logTable), BorderLayout.CENTER);
        return tab;
    }

    private void applyFilter() {
        String f = logFilter.getText().strip().toLowerCase(Locale.ROOT);
        if (f.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        sorter.setRowFilter(new RowFilter<LogTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
                SqlLog.Entry e = entry.getModel().entry(entry.getIdentifier());
                return e.sql().toLowerCase(Locale.ROOT).contains(f) || e.origin().toLowerCase(Locale.ROOT).contains(f)
                        || LogTableModel.outcome(e).toLowerCase(Locale.ROOT).contains(f)
                        || e.message().toLowerCase(Locale.ROOT).contains(f);
            }
        });
    }

    private void copyEntry(int modelRow) {
        SqlLog.Entry e = logModel.entry(modelRow);
        prompts.copyToClipboard(e.sql());
        message(MessageKind.INFO, Texts.get("panel.log.copied", e.sequence()));
    }

    /** Righe visibili (dopo il filtro), nell'ordine in cui la tabella le mostra (ordinamento compreso). */
    public List<SqlLog.Entry> visibleEntries() {
        List<SqlLog.Entry> out = new ArrayList<>();
        for (int i = 0; i < logTable.getRowCount(); i++) {
            out.add(logModel.entry(logTable.convertRowIndexToModel(i)));
        }
        return out;
    }

    /**
     * Le righe che «Esporta…» scrive: quelle visibili (tutto il registro se il filtro è vuoto) ma sempre <b>nell'ordine
     * di esecuzione</b> ({@link SqlLog.Entry#sequence()}), qualunque ordinamento sia attivo nella tabella: uno script
     * rieseguito in un altro ordine non riprodurrebbe lo stato del server.
     */
    public List<SqlLog.Entry> exportedEntries() {
        List<SqlLog.Entry> rows = new ArrayList<>(visibleEntries());
        rows.sort(java.util.Comparator.comparingLong(SqlLog.Entry::sequence));
        return rows;
    }

    /**
     * «Esporta…»: le righe visibili (tutto il registro se il filtro è vuoto), nell'ordine di esecuzione, diventano uno
     * script {@code .sql} rieseguibile; le istruzioni non riuscite restano commentate, con l'avviso che possono essere
     * state applicate in parte.
     */
    public void exportLog() {
        List<SqlLog.Entry> rows = exportedEntries();
        if (rows.isEmpty()) {
            message(MessageKind.INFO, Texts.get("panel.log.export.empty"));
            return;
        }
        String suggested = Texts.get("panel.log.export.fileName", LocalDate.now());
        WorkspacePrompts.LogExport choice = prompts.chooseLogExport(suggested, exportCatalogCandidate.get());
        if (choice == null) {
            return;
        }
        SqlLog.ExportOptions options = new SqlLog.ExportOptions(choice.unqualifyCatalog(), Instant.now());
        try {
            if (rows.size() == log.size()) {
                log.export(choice.file(), options);
            } else {
                Files.writeString(choice.file(), SqlLog.exportScript(rows, options), StandardCharsets.UTF_8);
            }
            message(MessageKind.SUCCESS, Texts.get("panel.log.export.done", rows.size(), choice.file().getFileName()));
        } catch (IOException e) {
            message(MessageKind.ERROR, Texts.get("panel.log.export.error", e.getMessage()));
        }
    }

    /**
     * DESIGN-SYSTEM §3.6: ora in piccolo, <b>pillola dell'origine</b>, SQL in carattere monospaziato colorato su una
     * riga, esito con icona (spunta / croce), durata e righe a destra. Righe in errore su fondo {@code danger.tint}.
     */
    private final class LogRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        private final Pill origin = new Pill("", Tokens.TEXT_SECONDARY, Tokens.BG_SUNKEN);
        private final JPanel originCell = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        LogRenderer() {
            originCell.add(origin);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            SqlLog.Entry e = logModel.entry(table.convertRowIndexToModel(row));
            int modelColumn = table.convertColumnIndexToModel(column);
            java.awt.Color background = isSelected ? Tokens.ACCENT_TINT : e.isOk() ? Tokens.BG_SURFACE
                    : errorBackground();
            setBackground(background);
            setForeground(Tokens.TEXT_PRIMARY);
            setFont(Tokens.font(Tokens.BODY, java.awt.Font.PLAIN));
            setIcon(null);
            setToolTipText(null);
            setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_8), 0, Tokens.px(Tokens.SPACE_8)));
            setHorizontalAlignment(modelColumn == LogTableModel.COL_DURATION || modelColumn == LogTableModel.COL_ROWS
                    || modelColumn == LogTableModel.COL_NUMBER ? SwingConstants.RIGHT : SwingConstants.LEFT);
            switch (modelColumn) {
                case LogTableModel.COL_NUMBER, LogTableModel.COL_TIME -> {
                    setFont(Tokens.font(Tokens.CAPTION, java.awt.Font.PLAIN));
                    setForeground(Tokens.TEXT_SECONDARY);
                }
                case LogTableModel.COL_ORIGIN -> {
                    origin.setText(e.origin());
                    originCell.setBackground(background);
                    originCell.setBorder(BorderFactory.createEmptyBorder(Tokens.px(5), Tokens.px(Tokens.SPACE_8), 0, 0));
                    return originCell;
                }
                case LogTableModel.COL_SQL -> {
                    setFont(Tokens.mono(Tokens.SMALL));
                    setText(SqlText.toHtml(LogTableModel.oneLine(e.sql())));
                    // testo semplice: il suggerimento va a capo entro la larghezza leggibile anche al proiettore
                    setToolTipText(e.sql());
                }
                case LogTableModel.COL_OUTCOME -> {
                    switch (e.outcome()) {
                        case OK -> {
                            setIcon(NavigatorIcons.OUTCOME_OK);
                            setForeground(Tokens.SUCCESS);
                        }
                        case ERROR -> {
                            setIcon(NavigatorIcons.OUTCOME_ERROR);
                            setForeground(Tokens.DANGER);
                            setToolTipText(e.message());
                        }
                        case INTERRUPTED -> {
                            setIcon(NavigatorIcons.OUTCOME_INTERRUPTED);
                            setForeground(Tokens.WARNING);
                        }
                    }
                }
                case LogTableModel.COL_DURATION, LogTableModel.COL_ROWS -> setForeground(Tokens.TEXT_SECONDARY);
                default -> {
                    // niente
                }
            }
            return this;
        }
    }

    /** Colore di fondo delle righe in errore ({@code danger.tint}). */
    public static java.awt.Color errorBackground() {
        return Tokens.DANGER_TINT;
    }

    // ------------------------------------------------------------------ Anteprima

    private JPanel buildPreviewTab() {
        JLabel empty = new JLabel(Texts.get("sqlPanel.preview.empty"), SwingConstants.CENTER);
        empty.setForeground(Tokens.TEXT_TERTIARY);
        previewTitle.setName("panel.preview.title");
        previewTitle.setFont(Tokens.font(Tokens.TITLE, java.awt.Font.BOLD));
        previewTitle.setForeground(Tokens.TEXT_PRIMARY);
        previewTitle.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_16),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_16)));
        previewSql.setName("panel.preview.sql");
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Tokens.BG_SURFACE);
        content.add(previewTitle, BorderLayout.NORTH);
        JScrollPane previewScroll = new JScrollPane(previewSql);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        content.add(previewScroll, BorderLayout.CENTER);
        previewHost.add(empty, "empty");
        previewHost.add(content, "sql");
        previewCards.show(previewHost, "empty");
        return previewHost;
    }

    @Override
    public void scriptProposed(SqlScript script) {
        previewed = script;
        previewTitle.setText(script.title());
        SqlText.set(previewSql, script.text());
        previewCards.show(previewHost, "sql");
    }

    /** L'ultimo script mostrato nella scheda Anteprima ({@code null} se nessuno). */
    public SqlScript previewedScript() {
        return previewed;
    }

    public String previewText() {
        return previewSql.getText();
    }

    // ------------------------------------------------------------------ Messaggi

    private JScrollPane buildMessagesTab() {
        messagesPane.setName("panel.messages");
        messagesPane.setEditable(false);
        messagesPane.setBackground(Tokens.BG_SURFACE);
        messagesPane.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_16),
                Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_16)));
        return new JScrollPane(messagesPane);
    }

    @Override
    public void scriptFinished(ScriptResult result) {
        SqlScript script = result.script();
        if (result.completed()) {
            message(MessageKind.SUCCESS, Texts.get(script.size() == 1 ? "panel.messages.done.one"
                    : "panel.messages.done.many", script.title(), script.size(), result.durationMillis()));
        } else {
            StringBuilder sb = new StringBuilder();
            StatementResult failure = result.failure().orElse(null);
            if (result.interrupted()) {
                sb.append(Texts.get("panel.messages.interrupted", script.title()));
            } else {
                sb.append(Texts.get("panel.messages.failed", script.title()));
            }
            if (failure != null && failure.error() != null && failure.status() == StatementResult.Status.FAILED) {
                StatementResult.ServerError err = failure.error();
                sb.append('\n').append(Texts.get("panel.messages.serverError", err.code(),
                        err.sqlState().isEmpty() ? "-" : err.sqlState(), err.message()));
                ErrorExplainer.explain(err.code())
                        .ifPresent(x -> sb.append('\n').append(Texts.get("panel.messages.explanation", x)));
            }
            sb.append('\n').append(Texts.get("panel.messages.applied", result.applied().size(), script.size()));
            if (script.size() > 1) {
                for (SqlStatement s : result.applied()) {
                    sb.append('\n').append(Texts.get("panel.messages.appliedOne", LogTableModel.oneLine(s.text())));
                }
                if (failure != null) {
                    sb.append('\n').append(Texts.get("panel.messages.failedOne",
                            LogTableModel.oneLine(failure.statement().text())));
                }
                for (SqlStatement s : result.notExecuted()) {
                    sb.append('\n').append(Texts.get("panel.messages.notExecutedOne", LogTableModel.oneLine(s.text())));
                }
            }
            message(MessageKind.ERROR, sb.toString());
        }
        for (StatementResult r : result.results()) {
            for (StatementResult.Warning w : r.warnings()) {
                message(MessageKind.WARNING, Texts.get("panel.messages.warning", w.code(), w.message()));
            }
        }
        setSelectedIndex(result.completed() ? 0 : 2);
    }

    @Override
    public void message(MessageKind kind, String text) {
        Message m = new Message(Instant.now(), kind, text);
        messages.add(m);
        StyledDocument doc = messagesPane.getStyledDocument();
        SimpleAttributeSet time = new SimpleAttributeSet();
        StyleConstants.setForeground(time, Tokens.TEXT_TERTIARY);
        SimpleAttributeSet body = new SimpleAttributeSet();
        SimpleAttributeSet icon = new SimpleAttributeSet();
        // mai il solo colore: anche un'icona (DESIGN-SYSTEM §5)
        switch (kind) {
            case ERROR -> {
                StyleConstants.setForeground(body, Tokens.DANGER);
                StyleConstants.setIcon(icon, NavigatorIcons.OUTCOME_ERROR);
            }
            case WARNING -> {
                StyleConstants.setForeground(body, Tokens.WARNING);
                StyleConstants.setIcon(icon, NavigatorIcons.WARNING);
            }
            case SUCCESS -> {
                StyleConstants.setForeground(body, Tokens.SUCCESS);
                StyleConstants.setIcon(icon, NavigatorIcons.OUTCOME_OK);
            }
            case INFO -> StyleConstants.setForeground(body, Tokens.TEXT_PRIMARY);
        }
        if (kind == MessageKind.ERROR || kind == MessageKind.WARNING) {
            StyleConstants.setBold(body, true);
        }
        // la prima riga dice cosa è successo (colorata); i dettagli sotto, in text.secondary e rientrati
        SimpleAttributeSet details = new SimpleAttributeSet();
        StyleConstants.setForeground(details, Tokens.TEXT_SECONDARY);
        int newline = text.indexOf('\n');
        String head = newline < 0 ? text : text.substring(0, newline);
        String rest = newline < 0 ? "" : text.substring(newline + 1);
        try {
            doc.insertString(doc.getLength(), TIME.format(m.time()) + "  ", time);
            if (kind != MessageKind.INFO) {
                doc.insertString(doc.getLength(), " ", icon);
                doc.insertString(doc.getLength(), " ", body);
            }
            doc.insertString(doc.getLength(), head + "\n", body);
            for (String line : rest.isEmpty() ? new String[0] : rest.split("\n")) {
                doc.insertString(doc.getLength(), "            " + line + "\n", details);
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        messagesPane.setCaretPosition(doc.getLength());
    }

    /** I messaggi mostrati finora, in ordine. */
    public List<Message> messages() {
        return List.copyOf(messages);
    }

    // ------------------------------------------------------------------ accesso per i test

    public JTable logTable() {
        return logTable;
    }

    public JTextField logFilterField() {
        return logFilter;
    }

    public JButton exportButton() {
        return export;
    }

    public JTextPane messagesPane() {
        return messagesPane;
    }
}
