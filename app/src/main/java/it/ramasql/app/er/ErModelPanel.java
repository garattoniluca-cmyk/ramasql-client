/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

import it.ramasql.app.Texts;
import it.ramasql.app.tableeditor.Banner;
import it.ramasql.app.tableeditor.Ui;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.model.AutoLayout;
import it.ramasql.model.ErModel;
import it.ramasql.model.ModelFile;
import it.ramasql.model.ModelRefresh;
import it.ramasql.model.Relationship;
import it.ramasql.model.RelationshipSuggester;
import it.ramasql.model.ReverseEngineer;

/**
 * Il documento «Modello ER» ({@code DESIGN.md} §3.11): una barra con poche azioni — <em>Suggerisci relazioni</em>,
 * <em>Disponi</em>, <em>Aggiorna dal database</em>, <em>Esporta PNG…</em>, lo zoom e <em>Salva</em> (primario) —, il
 * diagramma, e a destra, quando servono, i suggerimenti da accettare. Le relazioni logiche vivono solo nel file
 * {@code .rsqlmodel}: il modello <b>non esegue mai SQL</b> (legge i metadati solo per la retroingegneria e per
 * l'aggiornamento).
 */
public final class ErModelPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient ErContext context;
    private final ErCanvas canvas = new ErCanvas();
    private final JScrollPane scroll;
    private transient Path file;
    private boolean modified;
    private boolean busy;
    private final Banner banner = new Banner("er.banner");
    private final JButton suggestButton;
    private final JButton layoutButton;
    private final JButton refreshButton;
    private final JButton exportButton;
    private final JButton saveButton;
    private final JButton zoomOut;
    private final JButton zoomIn;
    private final JLabel zoomLabel = new JLabel("100%");
    private final JPanel side = new JPanel(new BorderLayout(0, Tokens.px(Tokens.SPACE_8)));
    private final SuggestionModel suggestions = new SuggestionModel();
    private final JTable suggestionTable = new JTable(suggestions) {
        private static final long serialVersionUID = 1L;

        @Override
        public String getToolTipText(java.awt.event.MouseEvent e) {
            int row = rowAtPoint(e.getPoint());
            return row >= 0 ? reasonAt(row) : super.getToolTipText(e);
        }
    };
    private final JButton acceptButton;
    private transient Runnable onTitleChange = () -> { };

    public ErModelPanel(ErModel model, Path file, ErContext context) {
        super(new BorderLayout());
        this.context = context;
        this.file = file;
        setName("er.panel");
        setBackground(Tokens.BG_SURFACE);
        suggestButton = Ui.button("er.suggest", Texts.get("er.suggest"), this::suggest);
        layoutButton = Ui.button("er.layout", Texts.get("er.layout"), this::autoLayout);
        refreshButton = Ui.button("er.refresh", Texts.get("er.refresh"), this::refreshFromDatabase);
        exportButton = Ui.button("er.export", Texts.get("er.export"), this::exportPng);
        saveButton = Ui.primary("er.save", Texts.get("er.save"), this::save);
        zoomOut = Ui.button("er.zoomOut", "−", () -> zoom(1 / 1.2));
        zoomIn = Ui.button("er.zoomIn", "+", () -> zoom(1.2));
        acceptButton = Ui.primary("er.suggestions.accept", Texts.get("er.suggestions.accept"), this::acceptChecked);
        zoomLabel.setName("er.zoom");

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Tokens.BG_WINDOW);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12),
                        Tokens.px(Tokens.SPACE_4), Tokens.px(Tokens.SPACE_12))));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.px(Tokens.SPACE_8), 0));
        left.setOpaque(false);
        left.add(suggestButton);
        left.add(layoutButton);
        left.add(refreshButton);
        left.add(exportButton);
        left.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_12)));
        left.add(zoomOut);
        left.add(zoomLabel);
        left.add(zoomIn);
        bar.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(saveButton);
        bar.add(right, BorderLayout.EAST);
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        banner.setAlignmentX(LEFT_ALIGNMENT);
        north.add(bar);
        north.add(banner);
        add(north, BorderLayout.NORTH);

        scroll = new JScrollPane(canvas);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(Tokens.px(24));
        scroll.getHorizontalScrollBar().setUnitIncrement(Tokens.px(24));
        add(scroll, BorderLayout.CENTER);

        side.setName("er.suggestions");
        side.setBackground(Tokens.BG_WINDOW);
        side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Tokens.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_12),
                        Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_12))));
        side.add(Ui.sectionTitle(Texts.get("er.suggestions.title")), BorderLayout.NORTH);
        suggestionTable.setName("er.suggestions.table");
        Ui.styleTable(suggestionTable);
        Ui.narrow(suggestionTable.getColumnModel().getColumn(0), Tokens.px(32));
        Ui.narrow(suggestionTable.getColumnModel().getColumn(2), Tokens.px(52));
        suggestionTable.setPreferredScrollableViewportSize(new Dimension(Tokens.px(330), Tokens.px(200)));
        side.add(Ui.scroll(suggestionTable), BorderLayout.CENTER);
        JButton close = Ui.button("er.suggestions.close", Texts.get("er.suggestions.close"), () -> showSuggestions(false));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.px(Tokens.SPACE_8), 0));
        actions.setOpaque(false);
        actions.add(close);
        actions.add(acceptButton);
        side.add(actions, BorderLayout.SOUTH);
        side.setVisible(false);
        add(side, BorderLayout.EAST);

        for (JComponent c : new JComponent[] {suggestButton, layoutButton, refreshButton, exportButton, saveButton,
            zoomOut, zoomIn, zoomLabel, suggestionTable, acceptButton, close, canvas}) {
            c.setToolTipText(Texts.get(c.getName() + ".tooltip"));
        }

        canvas.setModel(model);
        canvas.setOnChange(m -> changed());
        canvas.setOnOpenTable(t -> context.openTableEditor(canvas.model().catalog(), t));
        canvas.setOnDraw(this::drawRelationship);
        canvas.setOnRelationshipMenu(this::relationshipMenu);
        refreshButtons();
    }

    // ================================================================ stato

    public ErModel model() {
        return canvas.model();
    }

    public ErCanvas canvas() {
        return canvas;
    }

    public Path file() {
        return file;
    }

    public boolean isModified() {
        return modified;
    }

    public boolean isBusy() {
        return busy;
    }

    public Banner banner() {
        return banner;
    }

    /** Il titolo della finestra: nome del modello, file e «•» se ci sono modifiche da salvare. */
    public String title() {
        String name = file == null ? model().name() : file.getFileName().toString();
        return Texts.get(modified ? "er.title.modified" : "er.title", name);
    }

    public void setOnTitleChange(Runnable r) {
        this.onTitleChange = r;
    }

    private void changed() {
        modified = true;
        refreshButtons();
        onTitleChange.run();
    }

    private void setModel(ErModel m) {
        canvas.setModel(m);
        changed();
    }

    private void refreshButtons() {
        refreshButton.setEnabled(!busy && context.reader() != null);
        refreshButton.setToolTipText(Texts.get(context.reader() != null ? "er.refresh.tooltip"
                : "er.refresh.disconnected"));
        suggestButton.setEnabled(!busy);
        layoutButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        exportButton.setEnabled(!busy);
        zoomLabel.setText(Math.round(canvas.zoom() * 100) + "%");
    }

    // ================================================================ azioni

    /** «Disponi»: disposizione automatica (le tabelle più collegate al centro). */
    public void autoLayout() {
        setModel(AutoLayout.layout(model(), canvas.measure()));
        banner.set(Banner.Tone.INFO, Texts.get("er.layout.done"));
    }

    public void zoom(double factor) {
        canvas.setZoom(canvas.zoom() * factor);
        refreshButtons();
    }

    /** «Suggerisci relazioni»: le proposte per nome, da accettare (nessuna si applica da sola). */
    public void suggest() {
        List<RelationshipSuggester.Suggestion> list = RelationshipSuggester.suggest(model());
        suggestions.set(list);
        showSuggestions(true);
        banner.set(list.isEmpty() ? Banner.Tone.INFO : Banner.Tone.INFO, Texts.get(list.isEmpty()
                ? "er.suggestions.none" : "er.suggestions.found", list.size()));
    }

    private void showSuggestions(boolean show) {
        side.setVisible(show);
        revalidate();
        repaint();
    }

    public boolean suggestionsVisible() {
        return side.isVisible();
    }

    /** Le proposte mostrate (per i test). */
    public List<RelationshipSuggester.Suggestion> suggestions() {
        return List.copyOf(suggestions.rows);
    }

    /** Spunta o toglie una proposta (per i test e per la tastiera). */
    public void setSuggestionChecked(int index, boolean checked) {
        suggestions.setValueAt(checked, index, 0);
    }

    /** «Accetta le spuntate»: diventano relazioni logiche (tratteggiate). */
    public void acceptChecked() {
        ErModel m = model();
        int n = 0;
        for (int i = 0; i < suggestions.rows.size(); i++) {
            if (suggestions.checked.get(i)) {
                Relationship r = suggestions.rows.get(i).relationship();
                if (!m.hasRelationship(r.fromTable(), r.fromColumns(), r.toTable())) {
                    m = m.addLogical(r);
                    n++;
                }
            }
        }
        if (n > 0) {
            setModel(m);
        }
        banner.set(Banner.Tone.SUCCESS, Texts.get("er.suggestions.accepted", n));
        suggest();   // quelle accettate spariscono dall'elenco
        if (suggestions.rows.isEmpty()) {
            showSuggestions(false);
        }
    }

    /** Una relazione logica disegnata a mano, dalla colonna della figlia alla colonna del padre. */
    public void drawRelationship(String fromTable, String fromColumn, String toTable, String toColumn) {
        ErModel m = model();
        if (m.hasRelationship(fromTable, List.of(fromColumn), toTable)) {
            banner.set(Banner.Tone.WARNING, Texts.get("er.draw.exists", fromTable + "." + fromColumn, toTable));
            return;
        }
        ErModel.Entity child = m.entity(fromTable).orElseThrow();
        ErModel.Attribute col = child.column(fromColumn).orElseThrow();
        Relationship.Cardinality card = child.isUnique(List.of(fromColumn)) ? Relationship.Cardinality.ONE_TO_ONE
                : Relationship.Cardinality.ONE_TO_MANY;
        String id = "log:" + fromTable.toLowerCase(Locale.ROOT) + "." + fromColumn.toLowerCase(Locale.ROOT) + ">"
                + toTable.toLowerCase(Locale.ROOT);
        Relationship r = new Relationship(id, Relationship.Kind.LOGICAL, fromTable, List.of(fromColumn), toTable,
                List.of(toColumn), card, !col.nullable(), "");
        setModel(m.addLogical(r));
        ErModel.Attribute target = m.entity(toTable).orElseThrow().column(toColumn).orElseThrow();
        List<Banner.Line> lines = new ArrayList<>();
        lines.add(new Banner.Line(Banner.Tone.SUCCESS, Texts.get("er.draw.done", r.describe())));
        if (!sameFamily(col.type(), target.type())) {
            lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("er.draw.types", col.type(), target.type())));
        }
        banner.set(lines);
    }

    private static boolean sameFamily(String a, String b) {
        String x = a.replaceAll("[^A-Za-z].*", "").toUpperCase(Locale.ROOT);
        String y = b.replaceAll("[^A-Za-z].*", "").toUpperCase(Locale.ROOT);
        boolean ix = x.endsWith("INT");
        boolean iy = y.endsWith("INT");
        return x.equals(y) || (ix && iy) || (x.contains("CHAR") && y.contains("CHAR"));
    }

    private void relationshipMenu(Relationship r) {
        JPopupMenu menu = relationshipMenuFor(r);
        java.awt.Point p = getMousePosition(true);
        if (p != null) {
            menu.show(this, p.x, p.y);
        }
    }

    /** Il menu di una relazione: per le logiche cardinalità, etichetta, eliminazione; per le fisiche solo che cos'è. */
    public JPopupMenu relationshipMenuFor(Relationship r) {
        JPopupMenu menu = new JPopupMenu();
        menu.setName("er.relationship.menu");
        if (r.kind() == Relationship.Kind.PHYSICAL) {
            JMenuItem info = new JMenuItem(Texts.get("er.relationship.physical", r.label()));
            info.setName("er.relationship.physical");
            info.setEnabled(false);
            info.setToolTipText(Texts.get("er.relationship.physical.tooltip"));
            menu.add(info);
            return menu;
        }
        for (Relationship.Cardinality c : Relationship.Cardinality.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(Texts.get("er.cardinality." + c.name()),
                    r.cardinality() == c);
            item.setName("er.relationship.cardinality." + c.name());
            item.setToolTipText(Texts.get("er.cardinality." + c.name() + ".tooltip"));
            item.addActionListener(e -> replace(r, r.withCardinality(c)));
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem delete = new JMenuItem(Texts.get("er.relationship.delete"));
        delete.setName("er.relationship.delete");
        delete.setToolTipText(Texts.get("er.relationship.delete.tooltip"));
        delete.addActionListener(e -> setModel(model().removeRelationship(r.id())));
        menu.add(delete);
        return menu;
    }

    /** Cambia l'etichetta di una relazione logica. */
    public void setLabel(String relationshipId, String label) {
        model().relationships().stream().filter(r -> r.id().equals(relationshipId)).findFirst()
                .ifPresent(r -> replace(r, r.withLabel(label == null ? "" : label.trim())));
    }

    private void replace(Relationship old, Relationship now) {
        List<Relationship> out = new ArrayList<>();
        for (Relationship r : model().relationships()) {
            out.add(r.id().equals(old.id()) ? now : r);
        }
        setModel(model().withRelationships(out));
    }

    /** «Aggiorna dal database»: rilegge le tabelle dal server, conserva posizioni e relazioni logiche. */
    public void refreshFromDatabase() {
        MetadataReader reader = context.reader();
        if (reader == null || busy) {
            return;
        }
        ErModel current = model();
        List<String> tables = current.wholeCatalog() ? List.of()
                : current.entities().stream().map(ErModel.Entity::table).toList();
        busy = true;
        refreshButtons();
        banner.set(Banner.Tone.INFO, Texts.get("er.refresh.running"));
        new SwingWorker<ErModel, Void>() {
            @Override
            protected ErModel doInBackground() throws Exception {
                reader.invalidate(current.catalog());
                if (reader.catalog(current.catalog()).isEmpty()) {
                    // un altro server, o il catalogo eliminato: senza questo controllo tutto risulterebbe «mancante»
                    throw new IllegalStateException(Texts.get("er.refresh.noCatalog", current.catalog()));
                }
                return ReverseEngineer.fromCatalog(reader, current.catalog(), tables);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    ErModel fresh = get();
                    ModelRefresh.Result r = ModelRefresh.refresh(model(), fresh, canvas.measure());
                    setModel(r.model());
                    List<Banner.Line> lines = new ArrayList<>();
                    lines.add(new Banner.Line(Banner.Tone.SUCCESS, Texts.get("er.refresh.done")));
                    if (!r.added().isEmpty()) {
                        lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("er.refresh.added", String.join(", ",
                                r.added()))));
                    }
                    if (!r.changed().isEmpty()) {
                        lines.add(new Banner.Line(Banner.Tone.INFO, Texts.get("er.refresh.changed", String.join(", ",
                                r.changed()))));
                    }
                    if (!r.missing().isEmpty()) {
                        lines.add(new Banner.Line(Banner.Tone.WARNING, Texts.get("er.refresh.missing", String.join(", ",
                                r.missing()))));
                    }
                    banner.set(lines);
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    banner.set(Banner.Tone.DANGER, Texts.get("er.refresh.failed", t.getMessage()));
                }
                refreshButtons();
            }
        }.execute();
    }

    /** «Salva»: il file {@code .rsqlmodel} (la prima volta si sceglie dove). */
    public void save() {
        Path target = file;
        if (target == null) {
            target = context.files().chooseToSave(FilePrompts.Purpose.MODEL, model().name() + ".rsqlmodel");
            if (target == null) {
                return;
            }
        }
        Path where = target;
        ErModel snapshot = model();
        busy = true;
        refreshButtons();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ModelFile.write(where, snapshot);
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                    file = where;
                    if (model() == snapshot) {
                        modified = false;
                    }
                    banner.set(Banner.Tone.SUCCESS, Texts.get("er.save.done", where.getFileName()));
                    onTitleChange.run();
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    context.files().showError(Texts.get("er.save"), Texts.get("er.save.failed", where.getFileName(),
                            t.getMessage()));
                }
                refreshButtons();
            }
        }.execute();
    }

    /** Scala dell'immagine esportata: il doppio, perché regga la stampa e il proiettore. */
    public static final double EXPORT_SCALE = 2.0;

    /** «Esporta PNG…»: l'immagine del diagramma, al doppio della scala, su fondo bianco. */
    public void exportPng() {
        Path target = context.files().chooseToSave(FilePrompts.Purpose.PNG, model().name() + ".png");
        if (target == null) {
            return;
        }
        BufferedImage image = canvas.render(EXPORT_SCALE);
        busy = true;
        refreshButtons();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ImageIO.write(image, "png", target.toFile());
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                    banner.set(Banner.Tone.SUCCESS, Texts.get("er.export.done", target.getFileName(), image.getWidth(),
                            image.getHeight()));
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    context.files().showError(Texts.get("er.export"), Texts.get("er.export.failed", t.getMessage()));
                }
                refreshButtons();
            }
        }.execute();
    }

    /** Si può chiudere: chiede se salvare le modifiche. */
    public boolean canClose() {
        if (!modified) {
            return true;
        }
        return switch (context.askSaveOnClose(title())) {
            case SAVE -> {
                save();
                yield false;   // si chiude dopo, a salvataggio finito
            }
            case DISCARD -> true;
            case STAY -> false;
        };
    }

    // ================================================================ proposte

    /** Proposte: casella, relazione proposta, punteggio (il motivo nel suggerimento). */
    private final class SuggestionModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private List<RelationshipSuggester.Suggestion> rows = List.of();
        private final List<Boolean> checked = new ArrayList<>();

        void set(List<RelationshipSuggester.Suggestion> list) {
            rows = List.copyOf(list);
            checked.clear();
            list.forEach(s -> checked.add(Boolean.TRUE));   // spuntate: si accettano con un clic, o si tolgono
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
            return Texts.get("er.suggestions.column." + column);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            RelationshipSuggester.Suggestion s = rows.get(row);
            return switch (column) {
                case 0 -> checked.get(row);
                case 1 -> s.relationship().describe();
                default -> s.score() + "%";
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            checked.set(row, Boolean.TRUE.equals(value));
            fireTableCellUpdated(row, column);
        }
    }

    /** Il motivo di una proposta (per il suggerimento della riga). */
    String reasonAt(int row) {
        return suggestions.rows.get(row).reason();
    }
}
