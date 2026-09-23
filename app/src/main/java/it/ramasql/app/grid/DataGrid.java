/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.TableColumn;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Pill;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.data.ClipboardBlock;
import it.ramasql.core.data.PendingChanges;
import it.ramasql.core.data.PendingChanges.PasteResult;
import it.ramasql.core.data.PendingChanges.RowKind;
import it.ramasql.core.data.RowChange;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.DmlGenerator;

/**
 * La griglia di <b>data-entry</b> (DESIGN §3.3), riusabile anche in sola lettura per viste e risultati di query.
 *
 * <h2>Cosa fa</h2>
 * <ul>
 *   <li>{@link JTable} su {@link DataGridModel} (che avvolge {@link PendingChanges}); selezione <b>a celle</b>, sempre
 *       un solo rettangolo; clic sull'intestazione = colonna intera, sul numero di riga = riga intera, Ctrl+A = tutto.</li>
 *   <li>Riga d'inserimento vuota sempre in fondo (solo se modificabile); colori per righe nuove, modificate, eliminate,
 *       in errore; celle non valide marcate con il messaggio del validatore come suggerimento; NULL in grigio corsivo.</li>
 *   <li>Appunti a blocchi: Ctrl+C, «Copia con intestazioni» (Ctrl+Maiusc+C), Ctrl+V, Ctrl+X, Canc, Ctrl+Z (ritira
 *       l'ultimo incolla, riempimento o svuotamento in blocco). Il testo si pubblica con {@link BlockTransferable}.</li>
 *   <li>Barra con Griglia ⇄ Scheda ({@link RecordForm}), pagine, «Esporta CSV…», contatore, <b>Scarta</b> e
 *       <b>Conferma</b> (Ctrl+S).</li>
 * </ul>
 *
 * <h2>Cosa non fa</h2>
 * <b>Non esegue SQL e non scrive mai</b>: la <i>Conferma</i> consegna le modifiche a chi si è registrato con
 * {@link #setOnConfirm} (l'integrazione: {@code DmlGenerator} → anteprima → esecutore), che poi riporta l'esito riga
 * per riga con {@link #markSaved} e {@link #markError}. Nessuna scrittura implicita: cambiare riga, ordinare o cambiare
 * pagina non produce nulla.
 *
 * <h2>Pagine e ordinamento con modifiche in sospeso (scelta)</h2>
 * Le modifiche in sospeso appartengono alle righe della pagina caricata. Finché ce ne sono, cambiare pagina e ordinare
 * <b>non sono possibili</b>: i pulsanti sono disabilitati con la spiegazione e, se richiesti lo stesso, la griglia lo
 * dice nella riga degli avvisi e non fa nulla. Così le modifiche non si perdono e non si scrivono, e non esistono
 * modifiche «invisibili» su pagine che non si vedono. Si conferma o si scarta, poi si cambia pagina. L'ordinamento si
 * sceglie con il tasto destro sull'intestazione (il clic sinistro seleziona la colonna, come in DESIGN §3.3).
 *
 * <p>Limiti: il fornitore di dati è chiamato sull'EDT (l'integrazione con il server dovrà spostare la lettura fuori
 * dall'EDT se lenta); una selezione non rettangolare non è possibile (modalità a intervallo singolo).
 */
public final class DataGrid extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final String ACTION_COPY = "copy";
    public static final String ACTION_CUT = "cut";
    public static final String ACTION_PASTE = "paste";
    public static final String ACTION_COPY_WITH_HEADERS = "ramasql.copyWithHeaders";
    public static final String ACTION_CLEAR = "ramasql.clear";
    public static final String ACTION_UNDO_PASTE = "ramasql.undoPaste";
    public static final String ACTION_CONFIRM = "ramasql.confirm";
    public static final String ACTION_SET_NULL = "ramasql.setNull";
    public static final String ACTION_EDIT_IN_WINDOW = "ramasql.editInWindow";
    public static final String ACTION_DELETE_ROWS = "ramasql.deleteRows";
    public static final String ACTION_RESTORE_ROWS = "ramasql.restoreRows";
    public static final String ACTION_SELECT_ALL = "selectAll";

    private static final String CARD_GRID = "grid";
    private static final String CARD_FORM = "form";

    /** Rettangolo di celle, estremi inclusi. */
    public record CellRange(int firstRow, int lastRow, int firstColumn, int lastColumn) {
        public int cellCount() {
            return (lastRow - firstRow + 1) * (lastColumn - firstColumn + 1);
        }
    }

    private final TableDef table;
    private final List<ColumnDef> columns;
    private final GridDataSource source;
    private final int pageSize;
    private final GridPrompts prompts;
    private final boolean editable;
    private final String readOnlyExplanation;

    private final DataGridModel model;
    private final JTable grid;
    private final RowHeader rowHeader;
    private final RecordForm form;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JToggleButton gridToggle = new JToggleButton(Texts.get("grid.view.grid"));
    private final JToggleButton formToggle = new JToggleButton(Texts.get("grid.view.form"));
    private final JButton previousPage = new JButton(Texts.get("grid.page.previous"));
    private final JButton nextPage = new JButton(Texts.get("grid.page.next"));
    private final JLabel pageLabel = new JLabel();
    private final JButton exportButton = new JButton(Texts.get("grid.export.button"));
    private final Pill counter = new Pill("", Tokens.TEXT_SECONDARY, Tokens.BG_SUNKEN);
    private final JButton discardButton = new JButton(Texts.get("grid.discard"));
    private final JButton confirmButton = new JButton(Texts.get("grid.confirm"));
    private final JLabel noticeLabel = new JLabel(" ");
    private final JLabel invalidLabel = new JLabel(" ");

    private Clipboard clipboard;
    private Consumer<List<RowChange>> onConfirm;
    private int pageIndex;
    private boolean hasMore;
    private GridDataSource.SortOrder sortOrder;
    private String notice = "";

    // ---------------------------------------------------------------- creazione

    /**
     * Data-entry su una tabella: modificabile se ha una chiave primaria o un indice UNIQUE tutto NOT NULL
     * ({@link DmlGenerator#isEditable}), altrimenti in sola lettura con la spiegazione.
     *
     * @param pageSize righe per pagina (il limite righe delle impostazioni)
     */
    public static DataGrid forTable(TableDef table, GridDataSource source, int pageSize, GridPrompts prompts) {
        boolean editable = DmlGenerator.isEditable(table);
        return new DataGrid(table, table.columns(), source, pageSize, prompts, editable,
                editable ? null : Texts.get("grid.readOnly.noKey"));
    }

    /**
     * Griglia in sola lettura (viste, risultati di query): solo copia ed esportazione.
     *
     * @param explanation riga di spiegazione in cima (es. {@link #viewExplanation()}); {@code null} = nessuna
     */
    public static DataGrid readOnly(List<ColumnDef> columns, GridDataSource source, int pageSize, String explanation,
            GridPrompts prompts) {
        return new DataGrid(null, columns, source, pageSize, prompts, false, explanation);
    }

    /** «Le viste si leggono soltanto…»: la spiegazione da passare a {@link #readOnly} per una vista. */
    public static String viewExplanation() {
        return Texts.get("grid.readOnly.view");
    }

    private DataGrid(TableDef table, List<ColumnDef> columns, GridDataSource source, int pageSize, GridPrompts prompts,
            boolean editable, String readOnlyExplanation) {
        super(new BorderLayout());
        this.table = table;
        this.columns = List.copyOf(columns);
        this.source = Objects.requireNonNull(source, "source");
        this.pageSize = pageSize;
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.editable = editable;
        this.readOnlyExplanation = readOnlyExplanation;
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        GridDataSource.Page first = source.load(0, pageSize, null);
        hasMore = first.hasMore();
        model = new DataGridModel(new PendingChanges(this.columns, first.rows()), editable);
        grid = new JTable(model);
        configureTable();
        rowHeader = new RowHeader(grid, i -> pageIndex * pageSize + i + 1);
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setRowHeaderView(rowHeader);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tokens.BORDER_SUBTLE));
        scroll.getViewport().setBackground(Tokens.BG_SURFACE);
        JPanel corner = new JPanel();
        corner.setBackground(Tokens.BG_SUNKEN);
        corner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Tokens.BORDER_DEFAULT));
        scroll.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, corner);
        form = new RecordForm(this);
        cardPanel.add(scroll, CARD_GRID);
        cardPanel.add(form, CARD_FORM);

        add(buildTop(), BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        installActions();
        model.addTableModelListener(e -> refreshBar());
        grid.getSelectionModel().addListSelectionListener(e -> rowHeader.repaint());
        refreshBar();
        setName("dataGrid");
    }

    private void configureTable() {
        grid.setName("dataGrid.table");
        grid.setCellSelectionEnabled(true);
        grid.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        grid.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        grid.setFillsViewportHeight(true);
        grid.setDefaultRenderer(String.class, new GridCellRenderer());
        grid.setDefaultRenderer(Object.class, new GridCellRenderer());
        grid.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        grid.getTableHeader().setReorderingAllowed(false);
        grid.setShowGrid(true);
        grid.setGridColor(Tokens.BORDER_SUBTLE);
        grid.setBackground(Tokens.BG_SURFACE);
        grid.setSelectionBackground(Tokens.ACCENT_TINT);
        grid.setSelectionForeground(Tokens.TEXT_PRIMARY);
        // intestazione su due righe: nome e tipo (§3.4)
        Set<String> primaryKey = table == null ? Set.of()
                : table.primaryKey().map(pk -> Set.copyOf(pk.columns())).orElse(Set.of());
        grid.getTableHeader().setDefaultRenderer(new GridHeaderRenderer(columns, primaryKey));
        FontMetrics nameMetrics = grid.getFontMetrics(Tokens.semibold(Tokens.EMPHASIS));
        FontMetrics typeMetrics = grid.getFontMetrics(Tokens.font(Tokens.CAPTION, Font.PLAIN));
        for (int c = 0; c < columns.size(); c++) {
            TableColumn tc = grid.getColumnModel().getColumn(c);
            ColumnDef def = columns.get(c);
            int byName = nameMetrics.stringWidth(def.name() + " ▼") + Tokens.px(Tokens.SPACE_16) + 4;
            int byTypeText = typeMetrics.stringWidth(GridHeaderRenderer.typeText(def)) + Tokens.px(Tokens.SPACE_16 + 20);
            int byType = Tokens.px(SqlTypes.isNumeric(def.dataType()) ? 80 : SqlTypes.isText(def.dataType()) ? 160 : 120);
            tc.setPreferredWidth(Math.min(Tokens.px(280), Math.max(Math.max(byName, byTypeText), byType)));
            tc.setHeaderValue(def.name());
        }
        grid.getTableHeader().addMouseListener(new MouseAdapter() {
            private int anchor = -1;

            @Override
            public void mousePressed(MouseEvent e) {
                int column = grid.columnAtPoint(e.getPoint());
                if (column < 0 || grid.getTableHeader().getCursor().getType() != java.awt.Cursor.DEFAULT_CURSOR) {
                    return;   // bordo di ridimensionamento
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    sortMenu(column).show(grid.getTableHeader(), e.getX(), e.getY());
                    return;
                }
                anchor = e.isShiftDown() && anchor >= 0 ? anchor : column;
                selectColumns(anchor, column);
            }
        });
        grid.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = grid.rowAtPoint(e.getPoint());
                    int column = grid.columnAtPoint(e.getPoint());
                    if (row >= 0 && column >= 0 && !grid.isCellSelected(row, column)) {
                        grid.changeSelection(row, column, false, false);
                    }
                }
            }
        });
        grid.setComponentPopupMenu(cellMenu());
    }

    /**
     * Barra della scheda (§3.4): interruttore segmentato <em>Griglia | Scheda</em>, pagine con le frecce,
     * <em>Esporta CSV…</em>; a destra il contatore in pillola, <em>Scarta</em> (secondario) e <em>Conferma</em>
     * (primario, Ctrl+S). Sotto, le righe degli avvisi; in cima, per la sola lettura, la fascia informativa.
     */
    private JComponent buildTop() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(Tokens.BG_WINDOW);
        bar.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12),
                Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12)));
        gridToggle.setIcon(AppIcons.small(AppIcons.VIEW_GRID));
        formToggle.setIcon(AppIcons.small(AppIcons.VIEW_FORM));
        gridToggle.setFocusable(false);
        formToggle.setFocusable(false);
        ButtonGroup views = new ButtonGroup();
        views.add(gridToggle);
        views.add(formToggle);
        gridToggle.setSelected(true);
        gridToggle.putClientProperty("JButton.buttonType", "segmented");
        gridToggle.putClientProperty("JButton.segmentPosition", "first");
        formToggle.putClientProperty("JButton.buttonType", "segmented");
        formToggle.putClientProperty("JButton.segmentPosition", "last");
        gridToggle.setName("dataGrid.view.grid");
        formToggle.setName("dataGrid.view.form");
        gridToggle.addActionListener(e -> showRecordForm(false));
        formToggle.addActionListener(e -> showRecordForm(true));
        bar.add(gridToggle);
        bar.add(formToggle);
        bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_16)));
        previousPage.setName("dataGrid.page.previous");
        nextPage.setName("dataGrid.page.next");
        previousPage.addActionListener(e -> previousPage());
        nextPage.addActionListener(e -> nextPage());
        // frecce: solo icona (il testo resta per i lettori di schermo)
        for (JButton arrow : List.of(previousPage, nextPage)) {
            arrow.getAccessibleContext().setAccessibleName(Texts.get(arrow == previousPage
                    ? "grid.page.previous.name" : "grid.page.next.name"));
            arrow.setText(null);
            Styles.toolbarButton(arrow);
        }
        previousPage.setIcon(AppIcons.small(AppIcons.PAGE_PREVIOUS));
        nextPage.setIcon(AppIcons.small(AppIcons.PAGE_NEXT));
        pageLabel.setForeground(Tokens.TEXT_SECONDARY);
        bar.add(previousPage);
        bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_4)));
        bar.add(pageLabel);
        bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_4)));
        bar.add(nextPage);
        bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_16)));
        exportButton.setName("dataGrid.export");
        exportButton.setIcon(AppIcons.small(AppIcons.GRID_EXPORT));
        Styles.toolbarButton(exportButton);
        exportButton.addActionListener(e -> exportCsv());
        bar.add(exportButton);
        bar.add(Box.createHorizontalGlue());
        counter.setName("dataGrid.counter");
        discardButton.setName("dataGrid.discard");
        confirmButton.setName("dataGrid.confirm");
        discardButton.setIcon(AppIcons.small(AppIcons.GRID_DISCARD));
        confirmButton.setIcon(AppIcons.onAccent(AppIcons.small(AppIcons.GRID_CONFIRM)));
        confirmButton.setDisabledIcon(AppIcons.small(AppIcons.GRID_CONFIRM).getDisabledIcon());
        discardButton.addActionListener(e -> discard());
        confirmButton.addActionListener(e -> confirm());
        Styles.primary(confirmButton, Tokens.ACCENT);
        if (editable) {
            bar.add(counter);
            bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_12)));
            bar.add(discardButton);
            bar.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
            bar.add(confirmButton);
        }

        JPanel lines = new JPanel();
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        lines.setBackground(Tokens.BG_WINDOW);
        if (readOnlyExplanation != null) {
            // fascia di sola lettura: accent.tint, icona «i», la spiegazione in una riga
            JLabel explanation = new JLabel(readOnlyExplanation, AppIcons.small(AppIcons.STATUS_INFO),
                    SwingConstants.LEADING);
            explanation.setName("dataGrid.readOnly");
            explanation.setIconTextGap(Tokens.px(Tokens.SPACE_8));
            explanation.setForeground(Tokens.TEXT_PRIMARY);
            JPanel band = new JPanel(new BorderLayout());
            band.setBackground(Tokens.ACCENT_TINT);
            band.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.BORDER_SUBTLE),
                    BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12),
                            Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_12))));
            band.add(explanation, BorderLayout.CENTER);
            lines.add(band);
        }
        lines.add(bar);
        invalidLabel.setName("dataGrid.invalid");
        invalidLabel.setForeground(Tokens.DANGER);
        invalidLabel.setIconTextGap(Tokens.px(6));
        invalidLabel.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_12), Tokens.px(2),
                Tokens.px(Tokens.SPACE_12)));
        noticeLabel.setName("dataGrid.notice");
        noticeLabel.setVisible(false);
        noticeLabel.setForeground(Tokens.TEXT_SECONDARY);
        noticeLabel.setIconTextGap(Tokens.px(6));
        noticeLabel.setBorder(BorderFactory.createEmptyBorder(0, Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_4),
                Tokens.px(Tokens.SPACE_12)));
        lines.add(wrapLeft(invalidLabel));
        lines.add(wrapLeft(noticeLabel));
        return lines;
    }

    private static JComponent wrapLeft(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    // ---------------------------------------------------------------- azioni e scorciatoie

    private void installActions() {
        put(ACTION_COPY, () -> copySelection(false));
        put(ACTION_COPY_WITH_HEADERS, () -> copySelection(true));
        put(ACTION_CUT, this::cut);
        put(ACTION_PASTE, this::paste);
        put(ACTION_CLEAR, this::clearSelection);
        put(ACTION_UNDO_PASTE, this::undoPaste);
        put(ACTION_CONFIRM, this::confirm);
        put(ACTION_SET_NULL, this::setNullOnSelection);
        put(ACTION_EDIT_IN_WINDOW, this::editInWindow);
        put(ACTION_DELETE_ROWS, this::deleteSelectedRows);
        put(ACTION_RESTORE_ROWS, this::restoreSelectedRows);
        int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        var focused = grid.getInputMap(JComponent.WHEN_FOCUSED);
        focused.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, ctrl), ACTION_COPY);
        focused.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, ctrl | InputEvent.SHIFT_DOWN_MASK), ACTION_COPY_WITH_HEADERS);
        focused.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, ctrl), ACTION_CUT);
        focused.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, ctrl), ACTION_PASTE);
        focused.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), ACTION_CLEAR);
        focused.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrl), ACTION_UNDO_PASTE);
        // Ctrl+S vale anche dalla scheda record: la scorciatoia sta sul pannello
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrl),
                ACTION_CONFIRM);
        getActionMap().put(ACTION_CONFIRM, grid.getActionMap().get(ACTION_CONFIRM));
    }

    private void put(String name, Runnable body) {
        grid.getActionMap().put(name, new AbstractAction(name) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                body.run();
            }
        });
    }

    private JPopupMenu cellMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copy = item(menu, "grid.menu.copy", ACTION_COPY, "ctrl C");
        JMenuItem copyHeaders = item(menu, "grid.menu.copyWithHeaders", ACTION_COPY_WITH_HEADERS, "ctrl shift C");
        JMenuItem cut = item(menu, "grid.menu.cut", ACTION_CUT, "ctrl X");
        JMenuItem paste = item(menu, "grid.menu.paste", ACTION_PASTE, "ctrl V");
        menu.addSeparator();
        JMenuItem setNull = item(menu, "grid.menu.setNull", ACTION_SET_NULL, null);
        JMenuItem clear = item(menu, "grid.menu.clear", ACTION_CLEAR, "DELETE");
        JMenuItem window = item(menu, "grid.menu.editInWindow", ACTION_EDIT_IN_WINDOW, null);
        menu.addSeparator();
        JMenuItem delete = item(menu, "grid.menu.deleteRows", ACTION_DELETE_ROWS, null);
        JMenuItem restore = item(menu, "grid.menu.restoreRows", ACTION_RESTORE_ROWS, null);
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                boolean hasData = dataSelection() != null;
                copy.setEnabled(hasData);
                copyHeaders.setEnabled(hasData);
                for (JMenuItem i : List.of(cut, paste, setNull, clear, window, delete, restore)) {
                    i.setVisible(editable);
                }
                cut.setEnabled(hasData);
                setNull.setEnabled(hasData);
                clear.setEnabled(hasData);
                window.setEnabled(activeCell() != null && model.isCellEditable(activeCell()[0], activeCell()[1]));
                delete.setEnabled(hasData);
                restore.setVisible(editable && selectionHasDeletedRows());
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        return menu;
    }

    private JMenuItem item(JPopupMenu menu, String textKey, String action, String accelerator) {
        Action a = grid.getActionMap().get(action);
        JMenuItem item = new JMenuItem(Texts.get(textKey));
        item.addActionListener(a);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        menu.add(item);
        return item;
    }

    private JPopupMenu sortMenu(int column) {
        String name = columns.get(column).name();
        JPopupMenu menu = new JPopupMenu();
        JMenuItem asc = new JMenuItem(Texts.get("grid.sort.ascending"));
        JMenuItem desc = new JMenuItem(Texts.get("grid.sort.descending"));
        JMenuItem none = new JMenuItem(Texts.get("grid.sort.none"));
        asc.addActionListener(e -> sortBy(new GridDataSource.SortOrder(name, true)));
        desc.addActionListener(e -> sortBy(new GridDataSource.SortOrder(name, false)));
        none.addActionListener(e -> sortBy(null));
        none.setEnabled(sortOrder != null);
        menu.add(asc);
        menu.add(desc);
        menu.add(none);
        if (model.pending().hasPending()) {
            for (JMenuItem i : List.of(asc, desc, none)) {
                i.setEnabled(false);
                i.setToolTipText(Texts.get("grid.blocked.pending"));
            }
        }
        return menu;
    }

    // ---------------------------------------------------------------- API per l'integrazione

    /** Chi riceve le modifiche alla <i>Conferma</i> (DmlGenerator → anteprima → esecuzione). La griglia non scrive. */
    public void setOnConfirm(Consumer<List<RowChange>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    /** Gli appunti da usare (nei test un {@code new Clipboard("test")}); di norma quelli di sistema. */
    public void setClipboard(Clipboard clipboard) {
        this.clipboard = Objects.requireNonNull(clipboard);
    }

    /**
     * L'istruzione della riga è riuscita.
     *
     * @param refreshedValues valori riletti dal server da mostrare (es. {@code id} AUTO_INCREMENT della riga nuova)
     */
    public void markSaved(long rowId, Map<String, String> refreshedValues) {
        model.pending().markSaved(rowId, refreshedValues);
        dataChanged();
    }

    /** L'istruzione della riga è fallita: la riga resta in sospeso, marcata, con il messaggio (spiegato) visibile. */
    public void markError(long rowId, String message) {
        model.pending().markError(rowId, message);
        dataChanged();
        int row = model.pending().indexOf(rowId);
        if (row >= 0) {
            setNotice(Texts.get("grid.row.error.notice", rowLabel(row), message));
        }
        form.refresh();
    }

    public boolean hasPending() {
        return model.pending().hasPending();
    }

    /** Il testo della domanda da fare chiudendo la scheda con modifiche in sospeso («Conferma, scarta o resta?»). */
    public String closeQuestion() {
        return Texts.get("grid.close.question", summary());
    }

    /** I tre pulsanti della domanda alla chiusura, in quest'ordine: «Conferma», «Scarta», «Resta» (predefinito). */
    public static List<String> closeQuestionOptions() {
        return List.of(Texts.get("grid.close.confirm"), Texts.get("grid.close.discard"), Texts.get("grid.close.stay"));
    }

    public boolean isEditable() {
        return editable;
    }

    /** La spiegazione della sola lettura, {@code null} se modificabile o se non c'è spiegazione. */
    public String readOnlyExplanation() {
        return readOnlyExplanation;
    }

    /** La tabella (null per le griglie di sola lettura create con {@link #readOnly}). */
    public TableDef tableDef() {
        return table;
    }

    public JTable table() {
        return grid;
    }

    public DataGridModel model() {
        return model;
    }

    public RecordForm recordForm() {
        return form;
    }

    /** Il testo dell'ultimo avviso non bloccante («Il blocco ha 2 colonne in più…»); vuoto se nessuno. */
    public String notice() {
        return notice;
    }

    /** Il contatore mostrato nella barra. */
    public String counterText() {
        return counter.getText();
    }

    /** Perché la <i>Conferma</i> è disabilitata; vuoto se è abilitata. */
    public String confirmDisabledReason() {
        return confirmButton.isEnabled() ? "" : Objects.requireNonNullElse(confirmButton.getToolTipText(), "");
    }

    public boolean isConfirmEnabled() {
        return confirmButton.isEnabled();
    }

    public boolean isDiscardEnabled() {
        return discardButton.isEnabled();
    }

    // ---------------------------------------------------------------- Conferma e Scarta

    /**
     * <b>Conferma</b> (Ctrl+S): consegna le modifiche a {@link #setOnConfirm}. Con celle non valide non fa nulla (il
     * pulsante è disabilitato e la riga sotto la barra dice quante celle correggere).
     */
    public void confirm() {
        stopEditing();
        PendingChanges pending = model.pending();
        if (!pending.hasPending()) {
            return;
        }
        if (!pending.canConfirm()) {
            return;   // la spiegazione è già visibile sotto la barra (celle non valide) e sul pulsante
        }
        if (onConfirm != null) {
            onConfirm.accept(pending.toRowChanges());
        }
    }

    /** <b>Scarta</b>, dopo averlo chiesto: operazione locale, sul server non è stato scritto nulla. */
    public boolean discard() {
        stopEditing();
        if (!hasPending()) {
            return false;
        }
        if (!prompts.confirm(Texts.get("grid.discard.title"), Texts.get("grid.discard.message", summary()),
                Texts.get("grid.discard"))) {
            return false;
        }
        model.pending().discard();
        dataChanged();
        setNotice(Texts.get("grid.discard.done"));
        return true;
    }

    private String summary() {
        return model.pending().summary();
    }

    private String invalidText() {
        int n = model.pending().invalidCells().size();
        return n == 1 ? Texts.get("grid.invalid.one") : Texts.get("grid.invalid.many", n);
    }

    private void refreshBar() {
        PendingChanges pending = model.pending();
        boolean anything = pending.hasPending();
        counter.setText(anything ? Texts.get("grid.counter", summary()) : Texts.get("grid.counter.none"));
        if (anything) {
            counter.setColors(Tokens.TEXT_PRIMARY, Tokens.WARNING_TINT);
        } else {
            counter.setColors(Tokens.TEXT_SECONDARY, Tokens.BG_SUNKEN);
        }
        int invalid = anything ? pending.invalidCells().size() : 0;
        discardButton.setEnabled(anything);
        confirmButton.setEnabled(anything && invalid == 0);
        confirmButton.setToolTipText(!anything ? Texts.get("grid.confirm.nothing")
                : invalid > 0 ? invalidText() : Texts.get("grid.confirm.tooltip"));
        invalidLabel.setText(invalid > 0 ? invalidText() : " ");
        invalidLabel.setIcon(invalid > 0 ? AppIcons.small(AppIcons.STATUS_ERROR) : null);
        invalidLabel.setVisible(invalid > 0);   // nessuna riga vuota tra la barra e la griglia
        previousPage.setEnabled(pageIndex > 0 && !anything);
        nextPage.setEnabled(hasMore && !anything);
        String blocked = anything ? Texts.get("grid.blocked.pending") : null;
        previousPage.setToolTipText(blocked != null ? blocked : Texts.get("grid.page.previous.name"));
        nextPage.setToolTipText(blocked != null ? blocked : Texts.get("grid.page.next.name"));
        pageLabel.setText(Texts.get("grid.page", pageIndex + 1));
        rowHeader.repaint();
    }

    private void setNotice(String text) {
        notice = text == null ? "" : text;
        noticeLabel.setText(notice.isEmpty() ? " " : notice);
        noticeLabel.setIcon(notice.isEmpty() ? null : AppIcons.small(AppIcons.STATUS_INFO));
        noticeLabel.setVisible(!notice.isEmpty());
    }

    // ---------------------------------------------------------------- pagine e ordinamento

    public int pageIndex() {
        return pageIndex;
    }

    public boolean hasNextPage() {
        return hasMore;
    }

    public GridDataSource.SortOrder sortOrder() {
        return sortOrder;
    }

    /** Pagina successiva; rifiutata (con avviso) se ci sono modifiche in sospeso. */
    public boolean nextPage() {
        return hasMore && goTo(pageIndex + 1, sortOrder);
    }

    /** Pagina precedente; rifiutata (con avviso) se ci sono modifiche in sospeso. */
    public boolean previousPage() {
        return pageIndex > 0 && goTo(pageIndex - 1, sortOrder);
    }

    /** Ordina (dalla prima pagina); {@code null} = ordine naturale. Rifiutato (con avviso) con modifiche in sospeso. */
    public boolean sortBy(GridDataSource.SortOrder order) {
        return goTo(0, order);
    }

    /** Rilegge la pagina corrente dal fornitore; rifiutato (con avviso) con modifiche in sospeso. */
    public boolean reload() {
        return goTo(pageIndex, sortOrder);
    }

    private boolean goTo(int page, GridDataSource.SortOrder order) {
        stopEditing();
        if (hasPending()) {
            setNotice(Texts.get("grid.blocked.pending"));
            return false;
        }
        GridDataSource.Page loaded = source.load(page, pageSize, order);
        pageIndex = page;
        sortOrder = order;
        hasMore = loaded.hasMore();
        model.replace(new PendingChanges(columns, loaded.rows()));
        for (int c = 0; c < columns.size(); c++) {
            String name = columns.get(c).name();
            String arrow = order != null && order.column().equalsIgnoreCase(name) ? (order.ascending() ? " ▲" : " ▼") : "";
            grid.getColumnModel().getColumn(c).setHeaderValue(name + arrow);
        }
        grid.getTableHeader().repaint();
        form.showRow(0);
        setNotice("");
        refreshBar();
        return true;
    }

    // ---------------------------------------------------------------- vista Griglia ⇄ Scheda

    public void showRecordForm(boolean show) {
        stopEditing();
        if (show) {
            int[] active = activeCell();
            form.showRow(active == null ? 0 : active[0]);
            cards.show(cardPanel, CARD_FORM);
            formToggle.setSelected(true);
        } else {
            form.stopEditing();
            int row = form.currentRow();
            cards.show(cardPanel, CARD_GRID);
            gridToggle.setSelected(true);
            if (row >= 0 && row < grid.getRowCount() && grid.getColumnCount() > 0) {
                grid.changeSelection(row, 0, false, false);
            }
        }
    }

    public boolean isRecordFormShown() {
        return formToggle.isSelected();
    }

    // ---------------------------------------------------------------- selezione

    /** Seleziona il rettangolo (estremi inclusi); la cella attiva è l'angolo in alto a sinistra. */
    public void selectBlock(int firstRow, int firstColumn, int lastRow, int lastColumn) {
        grid.changeSelection(firstRow, firstColumn, false, false);
        grid.changeSelection(lastRow, lastColumn, false, true);
    }

    /** Clic sull'intestazione: colonne intere (tutte le righe di dati). */
    public void selectColumns(int fromColumn, int toColumn) {
        stopEditing();
        int lastRow = Math.max(0, model.dataRowCount() - 1);
        if (grid.getRowCount() == 0) {
            return;
        }
        grid.setRowSelectionInterval(0, lastRow);
        grid.setColumnSelectionInterval(fromColumn, toColumn);
        grid.requestFocusInWindow();
    }

    /** Clic sul numero di riga: righe intere. */
    public void selectRows(int fromRow, int toRow) {
        stopEditing();
        rowHeader.selectRows(fromRow, toRow);
    }

    /** Il rettangolo selezionato, o {@code null}. */
    public CellRange selection() {
        int[] rows = grid.getSelectedRows();
        int[] cols = grid.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            return null;
        }
        return new CellRange(rows[0], rows[rows.length - 1], cols[0], cols[cols.length - 1]);
    }

    /** La selezione limitata alle righe di dati (senza la riga d'inserimento), o {@code null}. */
    private CellRange dataSelection() {
        CellRange s = selection();
        if (s == null) {
            return null;
        }
        int last = Math.min(s.lastRow(), model.dataRowCount() - 1);
        return last < s.firstRow() ? null : new CellRange(s.firstRow(), last, s.firstColumn(), s.lastColumn());
    }

    /** La cella attiva: l'angolo in alto a sinistra della selezione, o {@code null}. */
    private int[] activeCell() {
        CellRange s = selection();
        return s == null ? null : new int[] {s.firstRow(), s.firstColumn()};
    }

    private boolean selectionHasDeletedRows() {
        CellRange s = dataSelection();
        if (s == null) {
            return false;
        }
        for (int r = s.firstRow(); r <= s.lastRow(); r++) {
            if (model.pending().kind(r) == RowKind.DELETED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Il modello è cambiato (incolla, Scarta, esito della Conferma…): la tabella si ridisegna e la selezione resta dov'era
     * (una {@code fireTableDataChanged} da sola la cancellerebbe, e il Ctrl+V successivo finirebbe altrove).
     */
    private void dataChanged() {
        CellRange before = selection();
        model.fireTableDataChanged();
        form.refresh();
        int rows = grid.getRowCount();
        if (before != null && rows > 0 && grid.getColumnCount() > 0) {
            int first = Math.min(before.firstRow(), rows - 1);
            int last = Math.min(before.lastRow(), rows - 1);
            selectBlock(first, before.firstColumn(), last, before.lastColumn());
        }
    }

    void stopEditing() {
        if (grid.isEditing()) {
            grid.getCellEditor().stopCellEditing();
        }
    }

    private String rowLabel(int row) {
        return model.pending().kind(row) == RowKind.INSERTED ? Texts.get("grid.row.new")
                : String.valueOf(pageIndex * pageSize + row + 1);
    }

    // ---------------------------------------------------------------- appunti

    /** Ctrl+C / «Copia con intestazioni»: il rettangolo come testo tabulato (convenzione Excel); il testo copiato. */
    public String copySelection(boolean withHeaders) {
        stopEditing();
        CellRange s = dataSelection();
        if (s == null) {
            return null;
        }
        ClipboardBlock block = model.pending().copy(s.firstRow(), s.lastRow(), s.firstColumn(), s.lastColumn());
        if (withHeaders) {
            List<List<String>> rows = new ArrayList<>();
            List<String> header = new ArrayList<>();
            for (int c = s.firstColumn(); c <= s.lastColumn(); c++) {
                header.add(columns.get(c).name());
            }
            rows.add(header);
            rows.addAll(block.rows());
            block = new ClipboardBlock(rows);
        }
        String text = block.toText();
        BlockTransferable.write(clipboard, text);
        setNotice(Texts.get("grid.copied", s.lastRow() - s.firstRow() + 1, s.lastColumn() - s.firstColumn() + 1));
        return text;
    }

    /**
     * Ctrl+V: stende il blocco dalla cella attiva; oltre l'ultima riga crea righe nuove, oltre l'ultima colonna
     * scarta con avviso, salta con avviso le colonne AUTO_INCREMENT/generate. Un valore solo su una selezione di più
     * celle le riempie tutte. Nulla va sul server: sono modifiche in sospeso, annullabili con Ctrl+Z.
     */
    public PasteResult paste() {
        if (!editable) {
            return null;
        }
        stopEditing();
        String text = BlockTransferable.read(clipboard);
        ClipboardBlock block = text == null ? null : ClipboardBlock.parse(text);
        if (block == null || block.rowCount() == 0 || block.columnCount() == 0) {
            setNotice(Texts.get("grid.paste.empty"));
            return null;
        }
        CellRange s = selection();
        int startRow = s == null ? model.dataRowCount() : s.firstRow();
        int startColumn = s == null ? 0 : s.firstColumn();
        PendingChanges pending = model.pending();
        CellRange data = dataSelection();
        PasteResult result;
        CellRange written;
        if (block.isSingleCell() && data != null && data.cellCount() > 1) {
            result = pending.fill(data.firstRow(), data.lastRow(), data.firstColumn(), data.lastColumn(),
                    block.cell(0, 0));
            written = data;
        } else {
            result = pending.paste(startRow, startColumn, block);
            int usable = block.columnCount() - result.discardedColumns();
            written = new CellRange(startRow, startRow + block.rowCount() - 1, startColumn,
                    startColumn + Math.max(1, usable) - 1);
        }
        dataChanged();
        selectBlock(written.firstRow(), written.firstColumn(), written.lastRow(), written.lastColumn());
        setNotice(pasteNotice(result, written));
        return result;
    }

    private String pasteNotice(PasteResult r, CellRange written) {
        List<String> parts = new ArrayList<>();
        parts.add(r.rowsAdded() > 0 ? Texts.get("grid.paste.doneWithRows", r.cellsWritten(), r.rowsAdded())
                : Texts.get("grid.paste.done", r.cellsWritten()));
        if (r.discardedColumns() > 0) {
            parts.add(Texts.get("grid.paste.discarded", r.discardedColumns()));
        }
        List<String> readOnly = readOnlyColumnsIn(written);
        if (!readOnly.isEmpty()) {
            parts.add(Texts.get("grid.paste.skippedColumns", String.join(", ", readOnly)));
        }
        int skippedDeleted = r.skippedCells() - readOnly.size() * (written.lastRow() - written.firstRow() + 1);
        if (skippedDeleted > 0) {
            parts.add(Texts.get("grid.paste.skippedDeleted", skippedDeleted));
        }
        parts.add(Texts.get("grid.paste.undoHint"));
        return String.join(" ", parts);
    }

    private List<String> readOnlyColumnsIn(CellRange range) {
        List<String> out = new ArrayList<>();
        for (int c = range.firstColumn(); c <= range.lastColumn() && c < columns.size(); c++) {
            if (model.isReadOnlyColumn(c)) {
                out.add(columns.get(c).name());
            }
        }
        return out;
    }

    /** Ctrl+X: copia, poi svuota come {@link #clearSelection()}. */
    public void cut() {
        if (!editable || dataSelection() == null) {
            return;
        }
        copySelection(false);
        clearSelection();
    }

    /**
     * Canc (e seconda metà di Ctrl+X) sul blocco: NULL dove la colonna lo ammette; stringa vuota nelle colonne di testo
     * NOT NULL; nelle altre colonne NOT NULL la cella resta com'è, con avviso. Sulle righe nuove la cella torna «non
     * impostata» (userà il DEFAULT). Un'operazione unica, annullabile con Ctrl+Z.
     */
    public void clearSelection() {
        if (!editable) {
            return;
        }
        stopEditing();
        CellRange s = dataSelection();
        if (s == null) {
            return;
        }
        PendingChanges pending = model.pending();
        List<String> kept = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (int r = s.firstRow(); r <= s.lastRow(); r++) {
            List<String> cells = new ArrayList<>();
            boolean isNew = pending.kind(r) == RowKind.INSERTED;
            for (int c = s.firstColumn(); c <= s.lastColumn(); c++) {
                ColumnDef def = columns.get(c);
                String value = null;
                if (!isNew && !def.nullable() && !model.isReadOnlyColumn(c)) {
                    if (isClearableText(def)) {
                        value = "";
                    } else {
                        value = pending.value(r, c);
                        if (!kept.contains(def.name())) {
                            kept.add(def.name());
                        }
                    }
                }
                cells.add(value);
            }
            rows.add(cells);
        }
        pending.paste(s.firstRow(), s.firstColumn(), new ClipboardBlock(rows));
        dataChanged();
        selectBlock(s.firstRow(), s.firstColumn(), s.lastRow(), s.lastColumn());
        setNotice(kept.isEmpty() ? Texts.get("grid.clear.done")
                : Texts.get("grid.clear.kept", String.join(", ", kept)));
    }

    private static boolean isClearableText(ColumnDef def) {
        String type = SqlTypes.canonical(def.dataType());
        return SqlTypes.isText(type) && !type.equals("ENUM");
    }

    /** Ctrl+Z: ritira l'ultimo incolla (o riempimento, o svuotamento in blocco) come operazione unica. */
    public boolean undoPaste() {
        if (!editable) {
            return false;
        }
        stopEditing();
        boolean undone = model.pending().undoLastPaste();
        dataChanged();
        setNotice(undone ? Texts.get("grid.undo.done") : Texts.get("grid.undo.nothing"));
        return undone;
    }

    // ---------------------------------------------------------------- NULL, testo lungo, righe

    /** «Imposta NULL» sulle celle selezionate delle colonne che lo ammettono; le altre restano, con avviso. */
    public void setNullOnSelection() {
        if (!editable) {
            return;
        }
        stopEditing();
        CellRange s = dataSelection();
        if (s == null) {
            return;
        }
        PendingChanges pending = model.pending();
        List<String> refused = new ArrayList<>();
        for (int c = s.firstColumn(); c <= s.lastColumn(); c++) {
            ColumnDef def = columns.get(c);
            if (model.isReadOnlyColumn(c)) {
                continue;
            }
            if (!def.nullable()) {
                refused.add(def.name());
                continue;
            }
            for (int r = s.firstRow(); r <= s.lastRow(); r++) {
                if (pending.kind(r) != RowKind.DELETED) {
                    pending.setValue(r, c, null);
                }
            }
        }
        dataChanged();
        selectBlock(s.firstRow(), s.firstColumn(), s.lastRow(), s.lastColumn());
        setNotice(refused.isEmpty() ? "" : Texts.get("grid.setNull.refused", String.join(", ", refused)));
    }

    /** «Modifica in una finestra…» sulla cella attiva (testo lungo, a-capo). */
    public void editInWindow() {
        stopEditing();
        int[] cell = activeCell();
        if (cell == null || !model.isCellEditable(cell[0], cell[1])) {
            return;
        }
        String current = (String) model.getValueAt(cell[0], cell[1]);
        String title = Texts.get("grid.longText.title", columns.get(cell[1]).name(),
                model.isInsertRow(cell[0]) ? Texts.get("grid.row.new") : rowLabel(cell[0]));
        String edited = prompts.editLongText(title, current);
        if (edited != null) {
            model.setValueAt(edited, cell[0], cell[1]);
            form.refresh();
        }
    }

    /** Marca da eliminare le righe selezionate (le righe nuove spariscono subito). Nulla va sul server. */
    public void deleteSelectedRows() {
        if (!editable) {
            return;
        }
        stopEditing();
        CellRange s = dataSelection();
        if (s == null) {
            return;
        }
        for (int r = s.lastRow(); r >= s.firstRow(); r--) {
            model.pending().deleteRow(r);
        }
        dataChanged();
    }

    /** Ritira l'eliminazione delle righe selezionate. */
    public void restoreSelectedRows() {
        CellRange s = dataSelection();
        if (!editable || s == null) {
            return;
        }
        for (int r = s.firstRow(); r <= s.lastRow(); r++) {
            if (model.pending().kind(r) == RowKind.DELETED) {
                model.pending().restoreRow(r);
            }
        }
        dataChanged();
    }

    // ---------------------------------------------------------------- esporta CSV

    /** «Esporta CSV…»: chiede il file e scrive il contenuto caricato (vedi {@link #exportCsv(Path)}). */
    public void exportCsv() {
        stopEditing();
        String base = table != null ? table.name() : Texts.get("grid.export.defaultName");
        Path file = prompts.chooseCsvFile(base + ".csv");
        if (file == null) {
            return;
        }
        try {
            exportCsv(file);
            setNotice(Texts.get("grid.export.done", file.getFileName()));
        } catch (IOException e) {
            prompts.showError(Texts.get("grid.export.error.title"), Texts.get("grid.export.error", e.getMessage()));
        }
    }

    /**
     * Scrive il contenuto <b>caricato</b> (la pagina visibile) così come lo si vede, modifiche in sospeso comprese; le
     * righe marcate da eliminare e la riga d'inserimento sono escluse. Formato: {@link CsvExporter}.
     */
    public void exportCsv(Path file) throws IOException {
        PendingChanges pending = model.pending();
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < pending.rowCount(); r++) {
            if (pending.kind(r) == RowKind.DELETED) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < columns.size(); c++) {
                cells.add(pending.value(r, c));
            }
            rows.add(cells);
        }
        CsvExporter.write(file, columns, rows);
    }
}
