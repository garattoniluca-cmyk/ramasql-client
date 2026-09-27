/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import it.ramasql.app.Texts;
import it.ramasql.app.grid.GridPrompts;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.exec.SqlStatement;

/**
 * L'editor SQL «raw» (DESIGN §3.4, req. 5): una scheda con il testo dello script sopra e i risultati sotto.
 *
 * <h2>Cosa fa</h2>
 * <ul>
 *   <li>{@link RSyntaxTextArea} con evidenziazione SQL, numeri di riga, parentesi abbinate, riga corrente, trova e
 *       sostituisci (Ctrl+F), commenta/decommenta righe (Ctrl+/), completamento (Ctrl+Spazio,
 *       {@link SqlCompletionProvider}); il carattere segue la dimensione delle impostazioni.</li>
 *   <li><b>Esegui</b> (Ctrl+Invio): la selezione se c'è, altrimenti l'istruzione al cursore ({@link StatementLocator},
 *       {@code DELIMITER} compreso). <b>Esegui tutto</b> (Ctrl+Maiusc+Invio): tutto lo script, in ordine.
 *       <b>Interrompi</b> (pulsante o Esc durante l'esecuzione).</li>
 *   <li>Prima di inviare istruzioni {@link RiskLevel#DESTRUCTIVE} (DROP, TRUNCATE, UPDATE/DELETE senza WHERE…) chiede
 *       la <b>conferma rafforzata</b> del navigatore ({@link ConfirmationPolicy}, {@link EditorPrompts#confirmDestructive}:
 *       riscrivere il nome dell'oggetto o la parola di conferma); senza il testo giusto non parte nulla.</li>
 *   <li>Risultati in {@link ResultsPanel}: una sotto-scheda per risultato, una riga d'esito per istruzione.</li>
 *   <li>Errori: messaggio originale + spiegazione in italiano ({@link ErrorExplainer}) + posizione evidenziata
 *       nell'editor (riga dell'errore e, per 1064, il testo citato dal server dopo «near»).</li>
 *   <li>Apri/Salva {@code .sql} in UTF-8; titolo con «*» se modificato; domanda alla chiusura ({@link #canClose}).</li>
 * </ul>
 *
 * <h2>Cosa non fa</h2>
 * <b>Non esegue SQL</b>: consegna le istruzioni già separate a un {@link SqlRunner} (nel programma la pipeline di
 * {@code core}, che le registra). Nessuna gestione di transazioni, EXPLAIN, formattatore o cronologia (fuori dalla v1).
 * Ctrl+T (nuova scheda) spetta alla finestra principale.
 */
public final class SqlEditor extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final String ACTION_RUN_CURRENT = "ramasql.sql.runCurrent";
    public static final String ACTION_RUN_ALL = "ramasql.sql.runAll";
    public static final String ACTION_CANCEL = "ramasql.sql.cancel";
    public static final String ACTION_ESCAPE = "ramasql.sql.escape";
    public static final String ACTION_FIND = "ramasql.sql.find";
    public static final String ACTION_TOGGLE_COMMENT = "ramasql.sql.toggleComment";
    public static final String ACTION_OPEN = "ramasql.sql.open";
    public static final String ACTION_SAVE = "ramasql.sql.save";

    /** Proprietà (per {@code addPropertyChangeListener}): il titolo della scheda è cambiato. */
    public static final String PROPERTY_TITLE = "sqlEditor.title";
    /** Proprietà: l'esecuzione è partita ({@code true}) o finita ({@code false}). */
    public static final String PROPERTY_RUNNING = "sqlEditor.running";

    public static final KeyStroke KEY_RUN_CURRENT = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
    public static final KeyStroke KEY_RUN_ALL =
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    public static final KeyStroke KEY_ESCAPE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    public static final KeyStroke KEY_FIND = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
    public static final KeyStroke KEY_TOGGLE_COMMENT = KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, InputEvent.CTRL_DOWN_MASK);
    public static final KeyStroke KEY_COMPLETE = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK);
    public static final KeyStroke KEY_OPEN = KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK);
    public static final KeyStroke KEY_SAVE = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);

    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final Color ERROR_LINE = new Color(255, 224, 224);
    private static final Color ERROR_TEXT = new Color(255, 160, 160);
    private static final String MONOSPACED_FAMILY = chooseMonospacedFamily();

    private final String untitledName;
    private final SqlRunner runner;
    private final EditorPrompts prompts;
    private final String origin = Texts.get("editor.origin");

    private final SqlTextArea textArea = new SqlTextArea();
    private final RTextScrollPane scroll;
    /** Sopra i risultati: il testo oppure, nella query visiva, il diagramma ({@link #setAlternateView}). */
    private final java.awt.CardLayout editorCardLayout = new java.awt.CardLayout();
    private final JPanel editorCards = new JPanel(editorCardLayout);
    private static final String CARD_TEXT = "text";
    private static final String CARD_ALTERNATE = "alternate";
    private JComponent alternateView;
    private boolean alternateShown;
    private JPanel toolbar;
    private JSplitPane editorSplit;
    private final FindBar findBar;
    private final ResultsPanel results;
    private final AutoCompletion autoCompletion;
    private CompletionSource completionSource;

    private final Action runCurrentAction = action("editor.run.current", this::runCurrent);
    private final Action runAllAction = action("editor.run.all", this::runAll);
    private final Action cancelAction = action("editor.cancel", this::cancelRun);
    private final Action openAction = action("editor.open", this::openFile);
    private final Action saveAction = action("editor.save", this::save);

    private Path file;
    private String lineSeparator = System.lineSeparator();
    private boolean modified;
    private boolean loading;

    // stato dell'esecuzione (solo EDT)
    private boolean running;
    private int runId;
    private List<PositionedStatement> runStatements = List.of();
    private int executedCount;
    private int failedIndex = -1;
    private long runStartNanos;

    // errore evidenziato
    private Object errorHighlightTag;
    private int errorLine;
    private int[] errorRange;

    /**
     * @param untitledName nome della scheda finché non si salva (es. «Query 1»)
     * @param runner       chi esegue le istruzioni
     * @param completion   metadati per il completamento ({@link CompletionSource#empty()} se non connessi)
     * @param prompts      finestre modali dell'editor
     * @param gridPrompts  finestre modali delle griglie dei risultati (esporta CSV…)
     * @param rowLimit     righe per pagina nelle griglie dei risultati (il «limite righe» delle impostazioni)
     */
    public SqlEditor(String untitledName, SqlRunner runner, CompletionSource completion, EditorPrompts prompts,
            GridPrompts gridPrompts, int rowLimit) {
        super(new BorderLayout());
        this.untitledName = Objects.requireNonNull(untitledName, "untitledName");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.completionSource = completion == null ? CompletionSource.empty() : completion;
        setName("sqlEditor");

        configureTextArea();
        scroll = new RTextScrollPane(textArea, true);
        scroll.setName("sqlEditor.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        findBar = new FindBar(textArea);
        results = new ResultsPanel(gridPrompts, rowLimit);

        SqlCompletionProvider provider = new SqlCompletionProvider(() -> completionSource);
        autoCompletion = new AutoCompletion(provider);
        autoCompletion.setTriggerKey(KEY_COMPLETE);
        autoCompletion.install(textArea);

        JPanel editorPart = new JPanel(new BorderLayout());
        editorPart.add(findBar, BorderLayout.NORTH);
        editorPart.add(scroll, BorderLayout.CENTER);
        editorCards.add(editorPart, CARD_TEXT);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorCards, results) {
            private static final long serialVersionUID = 1L;
            private boolean placed;

            @Override
            public void doLayout() {
                // con la vista alternativa (il diagramma) la parte sopra prende tre quarti dell'altezza al primo layout
                if (!placed && alternateView != null && getHeight() > 0) {
                    placed = true;
                    setDividerLocation((int) (getHeight() * 0.75));
                }
                super.doLayout();
            }
        };
        editorSplit = split;
        split.setName("sqlEditor.split");
        split.setResizeWeight(0.6);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);

        toolbar = buildToolbar();
        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        installKeys();
        applyFontSize(initialFontSize());
        updateActions();
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                documentChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                documentChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // solo attributi: non è una modifica del testo
            }
        });
    }

    // ---------------------------------------------------------------- costruzione

    private void configureTextArea() {
        textArea.setName("sqlEditor.text");
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        textArea.setCodeFoldingEnabled(false);
        textArea.setBracketMatchingEnabled(true);
        textArea.setPaintMatchedBracketPair(true);
        textArea.setAnimateBracketMatching(false);
        textArea.setHighlightCurrentLine(true);
        textArea.setMarkOccurrences(false);
        textArea.setTabSize(4);
        textArea.setAntiAliasingEnabled(true);
        textArea.setRows(12);
        textArea.setColumns(80);
    }

    private JPanel buildToolbar() {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        left.add(button(runCurrentAction, "sqlEditor.runCurrent", "editor.run.current.tip"));
        left.add(button(runAllAction, "sqlEditor.runAll", "editor.run.all.tip"));
        left.add(button(cancelAction, "sqlEditor.cancel", "editor.cancel.tip"));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.add(button(openAction, "sqlEditor.open", "editor.open.tip"));
        right.add(button(saveAction, "sqlEditor.save", "editor.save.tip"));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setName("sqlEditor.toolbar");
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        return bar;
    }

    private static JButton button(Action action, String name, String tipKey) {
        JButton b = new JButton(action);
        b.setName(name);
        b.setToolTipText(Texts.get(tipKey));
        b.setFocusable(false);
        return b;
    }

    private void installKeys() {
        InputMap im = textArea.getInputMap(JComponent.WHEN_FOCUSED);
        var am = textArea.getActionMap();
        im.put(KEY_RUN_CURRENT, ACTION_RUN_CURRENT);
        im.put(KEY_RUN_ALL, ACTION_RUN_ALL);
        im.put(KEY_ESCAPE, ACTION_ESCAPE);
        im.put(KEY_FIND, ACTION_FIND);
        im.put(KEY_TOGGLE_COMMENT, ACTION_TOGGLE_COMMENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DIVIDE, InputEvent.CTRL_DOWN_MASK), ACTION_TOGGLE_COMMENT);
        im.put(KEY_OPEN, ACTION_OPEN);
        im.put(KEY_SAVE, ACTION_SAVE);
        am.put(ACTION_RUN_CURRENT, runCurrentAction);
        am.put(ACTION_RUN_ALL, runAllAction);
        am.put(ACTION_CANCEL, cancelAction);
        am.put(ACTION_ESCAPE, action(null, this::escape));
        am.put(ACTION_FIND, action(null, () -> {
            findBar.open();
            return true;
        }));
        am.put(ACTION_TOGGLE_COMMENT, action(null, this::toggleComment));
        am.put(ACTION_OPEN, openAction);
        am.put(ACTION_SAVE, saveAction);
        // Esc interrompe anche con il fuoco sui risultati
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KEY_ESCAPE, ACTION_CANCEL);
        getActionMap().put(ACTION_CANCEL, cancelAction);
    }

    private interface Command {
        boolean run();
    }

    private static Action action(String nameKey, Command command) {
        AbstractAction a = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                command.run();
            }
        };
        if (nameKey != null) {
            a.putValue(Action.NAME, Texts.get(nameKey));
        }
        return a;
    }

    // ---------------------------------------------------------------- esecuzione

    /**
     * Esegue la selezione, se c'è, altrimenti l'istruzione al cursore (Ctrl+Invio).
     *
     * @return {@code true} se le istruzioni sono state consegnate all'esecutore
     */
    public boolean runCurrent() {
        String text = textArea.getText();
        int start = textArea.getSelectionStart();
        int end = textArea.getSelectionEnd();
        if (start != end) {
            return submitToRunner(StatementLocator.inSelection(text, start, end, origin));
        }
        PositionedStatement one = StatementLocator.atCaret(text, textArea.getCaretPosition(), origin);
        return submitToRunner(one == null ? List.of() : List.of(one));
    }

    /** Esegue tutto lo script, in ordine (Ctrl+Maiusc+Invio). */
    public boolean runAll() {
        return submitToRunner(StatementLocator.all(textArea.getText(), origin));
    }

    /** Interrompe l'esecuzione in corso (pulsante Interrompi, Esc). {@code false} se non c'era nulla da fermare. */
    public boolean cancelRun() {
        if (!running) {
            return false;
        }
        results.setStatus(Texts.get("editor.status.cancelling"));
        runner.cancel();
        return true;
    }

    private boolean escape() {
        if (running) {
            return cancelRun();
        }
        if (findBar.isVisible()) {
            findBar.close();
            return true;
        }
        return false;
    }

    private boolean submitToRunner(List<PositionedStatement> statements) {
        if (running) {
            return false;
        }
        clearErrorMarks();
        if (statements.isEmpty()) {
            results.setStatus(Texts.get("editor.nothingToRun"));
            return false;
        }
        List<SqlStatement> dangerous = statements.stream()
                .filter(s -> s.risk() == RiskLevel.DESTRUCTIVE).map(PositionedStatement::statement).toList();
        if (!dangerous.isEmpty() && !strongConfirmation(dangerous)) {
            results.setStatus(Texts.get("editor.cancelledByUser"));
            return false;
        }
        results.clear();
        running = true;
        int id = ++runId;
        runStatements = List.copyOf(statements);
        executedCount = 0;
        failedIndex = -1;
        runStartNanos = System.nanoTime();
        results.setStatus(Texts.get("editor.status.running", 1, runStatements.size()));
        updateActions();
        firePropertyChange(PROPERTY_RUNNING, false, true);
        try {
            runner.run(runStatements, origin, new RunListener(id));
        } catch (RuntimeException e) {
            if (running && id == runId) {
                results.showError(Texts.get("editor.run.error", String.valueOf(e.getMessage())));
                finishRun(id, false);
            }
        }
        return true;
    }

    /**
     * Conferma rafforzata, la stessa del navigatore: {@link ConfirmationPolicy} decide che cosa riscrivere (il nome
     * dell'oggetto, o la parola di conferma se sono più d'uno) e il testo restituito dalla finestra si ricontrolla qui.
     */
    private boolean strongConfirmation(List<SqlStatement> dangerous) {
        SqlScript script = new SqlScript(Texts.get("editor.confirm.title"), origin, dangerous);
        ConfirmationPolicy.Confirmation confirmation = ConfirmationPolicy.evaluate(script);
        String typed = prompts.confirmDestructive(confirmation, script);
        return typed != null && confirmation.accepts(typed);
    }

    /** Riceve l'avanzamento dall'esecutore, da qualunque thread, e lo riporta sull'EDT. */
    private final class RunListener implements SqlRunner.Listener {

        private final int id;

        RunListener(int id) {
            this.id = id;
        }

        @Override
        public void started(int index) {
            onEdt(() -> {
                if (running && id == runId) {
                    results.setStatus(Texts.get("editor.status.running", index + 1, runStatements.size()));
                }
            });
        }

        @Override
        public void finished(StatementOutcome outcome) {
            onEdt(() -> {
                if (!running || id != runId || outcome.index() < 0 || outcome.index() >= runStatements.size()) {
                    return;
                }
                executedCount++;
                PositionedStatement statement = runStatements.get(outcome.index());
                results.addOutcome(outcome.index() + 1, statement, outcome);
                if (outcome.isError()) {
                    failedIndex = outcome.index();
                    int line = markError(statement, outcome.error());
                    results.showError(ErrorExplainer.describe(outcome.error(), outcome.index() + 1, line));
                }
            });
        }

        @Override
        public void done(boolean cancelled) {
            onEdt(() -> finishRun(id, cancelled));
        }
    }

    private void finishRun(int id, boolean cancelled) {
        if (!running || id != runId) {
            return;
        }
        running = false;
        String elapsed = ResultsPanel.formatDuration(Duration.ofNanos(System.nanoTime() - runStartNanos));
        int total = runStatements.size();
        if (cancelled) {
            results.setStatus(Texts.get("editor.status.cancelled", executedCount, total));
        } else if (failedIndex >= 0) {
            results.setStatus(Texts.get("editor.status.error", failedIndex + 1, executedCount, total));
        } else if (executedCount == 1) {
            results.setStatus(Texts.get("editor.status.doneOne", elapsed));
        } else {
            results.setStatus(Texts.get("editor.status.done", executedCount, elapsed));
        }
        updateActions();
        firePropertyChange(PROPERTY_RUNNING, true, false);
    }

    private void updateActions() {
        runCurrentAction.setEnabled(!running);
        runAllAction.setEnabled(!running);
        cancelAction.setEnabled(running);
        openAction.setEnabled(!running);
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    // ---------------------------------------------------------------- errori

    /** Evidenzia la riga dell'errore (e il testo «near», se c'è); restituisce la riga del documento (da 1). */
    private int markError(PositionedStatement statement, SqlError error) {
        String text = textArea.getText();
        int length = text.length();
        int start = Math.min(statement.startOffset(), length);
        int end = Math.min(statement.endOffset(), length);
        try {
            int firstLine = textArea.getLineOfOffset(start);
            int lastLine = textArea.getLineOfOffset(end);
            Optional<ErrorExplainer.Location> location = ErrorExplainer.locate(error.message());
            int line = location.map(l -> firstLine + l.line() - 1).orElse(firstLine);
            line = Math.clamp(line, firstLine, lastLine);
            textArea.addLineHighlight(line, ERROR_LINE);
            errorLine = line + 1;
            int caret = Math.max(start, textArea.getLineStartOffset(line));
            if (location.isPresent()) {
                int[] range = nearRange(text, location.get().near(), caret, start, end);
                if (range != null) {
                    errorHighlightTag = textArea.getHighlighter().addHighlight(range[0], range[1],
                            new DefaultHighlighter.DefaultHighlightPainter(ERROR_TEXT));
                    errorRange = range;
                    caret = range[0];
                }
            }
            textArea.setCaretPosition(caret);
            return errorLine;
        } catch (BadLocationException e) {
            return 0;
        }
    }

    /** Dove comincia, dalla riga dell'errore in poi, il testo che il server cita dopo «near». */
    private static int[] nearRange(String text, String near, int from, int start, int end) {
        if (near.isEmpty()) {
            return end > start ? new int[] {end - 1, end} : null;   // errore alla fine dell'istruzione
        }
        String probe = near.lines().findFirst().orElse("").stripTrailing();
        if (probe.length() > 40) {
            probe = probe.substring(0, 40);
        }
        if (probe.isBlank()) {
            return null;
        }
        int at = text.indexOf(probe, from);
        if (at < 0 || at >= end) {
            at = text.indexOf(probe, start);
        }
        return at < 0 || at >= end ? null : new int[] {at, Math.min(at + probe.length(), end)};
    }

    private void clearErrorMarks() {
        textArea.removeAllLineHighlights();
        if (errorHighlightTag != null) {
            textArea.getHighlighter().removeHighlight(errorHighlightTag);
            errorHighlightTag = null;
        }
        errorLine = 0;
        errorRange = null;
    }

    // ---------------------------------------------------------------- commenti

    /** Ctrl+/: commenta con «-- » le righe toccate dalla selezione, o le decommenta se lo sono già tutte. */
    public boolean toggleComment() {
        Element root = textArea.getDocument().getDefaultRootElement();
        int selStart = textArea.getSelectionStart();
        int selEnd = textArea.getSelectionEnd();
        int first = root.getElementIndex(selStart);
        int last = root.getElementIndex(selEnd);
        if (last > first && selEnd == root.getElement(last).getStartOffset()) {
            last--;   // selezione che finisce all'inizio di una riga: quella riga non conta
        }
        String text = textArea.getText();
        boolean allCommented = true;
        boolean anyCode = false;
        for (int i = first; i <= last; i++) {
            String line = lineText(text, root.getElement(i));
            if (!line.isBlank()) {
                anyCode = true;
                allCommented &= line.stripLeading().startsWith("--");
            }
        }
        if (!anyCode) {
            return false;
        }
        textArea.beginAtomicEdit();
        try {
            for (int i = last; i >= first; i--) {
                Element el = root.getElement(i);
                String line = lineText(text, el);
                if (line.isBlank()) {
                    continue;
                }
                int indent = line.length() - line.stripLeading().length();
                int at = el.getStartOffset() + indent;
                if (allCommented) {
                    String rest = line.substring(indent);
                    int remove = rest.startsWith("-- ") ? 3 : 2;
                    textArea.getDocument().remove(at, remove);
                } else {
                    textArea.getDocument().insertString(el.getStartOffset(), "-- ", null);
                }
            }
        } catch (BadLocationException e) {
            return false;
        } finally {
            textArea.endAtomicEdit();
        }
        return true;
    }

    private static String lineText(String text, Element el) {
        int end = Math.min(el.getEndOffset(), text.length());
        String s = text.substring(el.getStartOffset(), end);
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    // ---------------------------------------------------------------- file

    /** Apri… (Ctrl+O): se ci sono modifiche chiede prima se salvarle, poi sceglie il file. */
    public boolean openFile() {
        if (running || !canClose()) {
            return false;
        }
        Path chosen = prompts.chooseSqlFileToOpen();
        return chosen != null && open(chosen);
    }

    /** Carica un file {@code .sql} (UTF-8, con o senza BOM) al posto del testo attuale. */
    public boolean open(Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            prompts.showError(Texts.get("editor.file.error.title"),
                    Texts.get("editor.file.error.read", path.getFileName(), e.getMessage()));
            return false;
        }
        if (content.startsWith(BOM)) {
            content = content.substring(1);
        }
        lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
        loading = true;
        try {
            textArea.setText(content.replace("\r\n", "\n").replace('\r', '\n'));
            textArea.setCaretPosition(0);
            textArea.discardAllEdits();
        } finally {
            loading = false;
        }
        clearErrorMarks();
        changeFileState(path, false);
        return true;
    }

    /** Salva (Ctrl+S): nel file attuale, o chiede dove se lo script non è mai stato salvato. */
    public boolean save() {
        return file == null ? saveAs() : saveTo(file);
    }

    /** Salva con nome. */
    public boolean saveAs() {
        String suggested = file != null ? file.getFileName().toString() : untitledName + ".sql";
        Path chosen = prompts.chooseSqlFileToSave(suggested);
        return chosen != null && saveTo(withSqlExtension(chosen));
    }

    /** Scrive il testo in UTF-8 (senza BOM), con gli a-capo del file d'origine (CR LF per i file nuovi su Windows). */
    public boolean saveTo(Path path) {
        try {
            Files.writeString(path, textArea.getText().replace("\n", lineSeparator), StandardCharsets.UTF_8);
        } catch (IOException e) {
            prompts.showError(Texts.get("editor.file.error.title"),
                    Texts.get("editor.file.error.write", path.getFileName(), e.getMessage()));
            return false;
        }
        changeFileState(path, false);
        return true;
    }

    /** Aggiunge {@code .sql} se il nome non ha estensione. */
    static Path withSqlExtension(Path path) {
        String name = path.getFileName().toString();
        return name.contains(".") ? path : path.resolveSibling(name + ".sql");
    }

    /**
     * Si può chiudere la scheda? Se ci sono modifiche non salvate chiede «Salva / Non salvare / Annulla».
     * {@code false} = l'utente vuole restare (o il salvataggio non è riuscito).
     */
    public boolean canClose() {
        if (!modified) {
            return true;
        }
        return switch (prompts.askSaveChanges(documentName())) {
            case SAVE -> save();
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    /** Da chiamare alla chiusura della scheda: ferma un'esecuzione in corso e stacca il completamento. */
    public void dispose() {
        if (running) {
            runner.cancel();
        }
        autoCompletion.uninstall();
    }

    private void documentChanged() {
        if (!loading) {
            changeFileState(file, true);
        }
    }

    private void changeFileState(Path newFile, boolean newModified) {
        String old = title();
        file = newFile;
        modified = newModified;
        firePropertyChange(PROPERTY_TITLE, old, title());
    }

    // ---------------------------------------------------------------- carattere

    /** Dimensione del carattere dell'editor, in punti (le impostazioni la cambiano con Ctrl+rotella). */
    public void setFontSize(int points) {
        applyFontSize(points);
    }

    public int fontSize() {
        return textArea.getFont().getSize();
    }

    private static int initialFontSize() {
        Font ui = UIManager.getFont("defaultFont");
        return ui != null ? ui.getSize() : 13;
    }

    private void applyFontSize(int points) {
        textArea.applyFontSize(points);
        if (scroll != null) {
            scroll.getGutter().setLineNumberFont(textArea.getFont());
        }
    }

    private static String chooseMonospacedFamily() {
        try {
            List<String> families = Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            for (String wanted : List.of("Consolas", "Cascadia Mono", "DejaVu Sans Mono")) {
                if (families.contains(wanted)) {
                    return wanted;
                }
            }
        } catch (RuntimeException e) {
            // ambiente senza caratteri installati: si ripiega sul logico
        }
        return Font.MONOSPACED;
    }

    /**
     * L'area di testo: segue la dimensione del carattere dell'interfaccia (impostazioni → {@code defaultFont} +
     * {@code FlatLaf.updateUI()}), con un carattere a spaziatura fissa.
     */
    private static final class SqlTextArea extends RSyntaxTextArea {

        private static final long serialVersionUID = 1L;

        private boolean ready;

        SqlTextArea() {
            ready = true;
        }

        void applyFontSize(int points) {
            setFont(new Font(MONOSPACED_FAMILY, Font.PLAIN, Math.max(6, points)));
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (ready) {
                Font ui = UIManager.getFont("defaultFont");
                if (ui != null) {
                    applyFontSize(ui.getSize());
                    Object parent = SwingUtilities.getAncestorOfClass(RTextScrollPane.class, this);
                    if (parent instanceof RTextScrollPane sp) {
                        sp.getGutter().setLineNumberFont(getFont());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- lettura

    /**
     * Un'altra vista dello stesso script, che prende il posto del testo sopra i risultati (la vista grafica della
     * query visiva, Step 7). Esecuzione, risultati e salvataggio restano quelli dell'editor, sul testo.
     */
    public void setAlternateView(JComponent view) {
        if (alternateView != null) {
            editorCards.remove(alternateView);
        }
        alternateView = view;
        if (view != null) {
            editorCards.add(view, CARD_ALTERNATE);
            // il diagramma vuole spazio: sopra i risultati prende circa due terzi dell'altezza (vedi doLayout dello split)
            editorSplit.setResizeWeight(0.75);
        }
        showAlternateView(alternateShown && view != null);
    }

    /** Mostra la vista alternativa ({@code true}) o il testo ({@code false}). */
    public void showAlternateView(boolean show) {
        alternateShown = show && alternateView != null;
        editorCardLayout.show(editorCards, alternateShown ? CARD_ALTERNATE : CARD_TEXT);
    }

    public boolean isAlternateViewShown() {
        return alternateShown;
    }

    /** Il testo attuale conta come «salvato» (chi incorpora l'editor salva altrove, es. una vista sul server). */
    public void setUnmodified() {
        changeFileState(file, false);
    }

    /** Nasconde la barra dell'editor (Esegui, Apri, Salva): chi lo incorpora ha la sua. */
    public void setToolbarVisible(boolean visible) {
        toolbar.setVisible(visible);
    }

    public RSyntaxTextArea textArea() {
        return textArea;
    }

    public ResultsPanel results() {
        return results;
    }

    public RTextScrollPane scrollPane() {
        return scroll;
    }

    public String getText() {
        return textArea.getText();
    }

    public void setText(String text) {
        textArea.setText(text);
    }

    public void setCompletionSource(CompletionSource source) {
        this.completionSource = source == null ? CompletionSource.empty() : source;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isModified() {
        return modified;
    }

    public Path file() {
        return file;
    }

    /** Nome del documento: il file, o il nome provvisorio. */
    public String documentName() {
        return file != null ? file.getFileName().toString() : untitledName;
    }

    /** Titolo della scheda: il nome, con « *» se ci sono modifiche non salvate. */
    public String title() {
        return modified ? Texts.get("editor.title.modified", documentName()) : documentName();
    }

    /** Riga del documento (da 1) evidenziata per l'ultimo errore, {@code 0} se nessuna. */
    public int errorLine() {
        return errorLine;
    }

    /** Il testo evidenziato come punto dell'errore (il «near» del server), {@code null} se nessuno. */
    public String errorHighlightedText() {
        return errorRange == null ? null : textArea.getText().substring(errorRange[0], errorRange[1]);
    }

    public boolean isFindBarVisible() {
        return findBar.isVisible();
    }

    FindBar findBar() {
        return findBar;
    }

    AutoCompletion autoCompletion() {
        return autoCompletion;
    }

    public Action runCurrentAction() {
        return runCurrentAction;
    }

    public Action runAllAction() {
        return runAllAction;
    }

    public Action cancelAction() {
        return cancelAction;
    }
}
