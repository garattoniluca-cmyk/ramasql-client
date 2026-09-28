/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static it.ramasql.app.servertest.Probe.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.plaf.basic.ComboPopup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.AboutDialog;
import it.ramasql.app.GuideDialog;
import it.ramasql.app.SwingPrompts;
import it.ramasql.app.Texts;
import it.ramasql.app.connection.ConnectionErrorDialog;
import it.ramasql.app.connection.PasswordDialog;
import it.ramasql.app.connection.ProfileDialog;
import it.ramasql.app.editor.ErrorExplainer;
import it.ramasql.app.er.ModelTablesDialog;
import it.ramasql.app.navigator.CreateCatalogDialog;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.app.pipeline.ShowCreateDialog;
import it.ramasql.app.settings.SettingsDialog;
import it.ramasql.app.tableeditor.SwingTableEditorPrompts;
import it.ramasql.app.tableeditor.TableEditor;
import it.ramasql.app.theme.ComboTips;
import it.ramasql.app.theme.RamaSqlLaf;
import it.ramasql.app.theme.Screens;
import it.ramasql.app.workspace.SwingWorkspacePrompts;
import it.ramasql.core.connection.AppSettings;
import it.ramasql.core.connection.ConnectionErrorCause;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.metadata.CollationInfo;
import it.ramasql.core.metadata.TableDef;

/**
 * Il programma vero, collegato a ciascuno dei due server, su schermi piccoli e al proiettore.
 * <ul>
 * <li><b>T12.6</b>: carattere al massimo (28), schermo emulato di 1024×768 (proiettore) e di 1366×768 (portatile), con
 * la barra delle applicazioni: la finestra principale e <b>ogni finestra di dialogo</b> del programma — le sue e quelle
 * dei messaggi e delle domande, anche con un testo lunghissimo — stanno dentro lo schermo; se il contenuto non ci sta,
 * diventa scorrevole (niente di tagliato).</li>
 * <li><b>T12.12</b>: nell'editor di tabelle si passa con il mouse su <b>ogni voce</b> di ogni lista a discesa (tipo,
 * engine, set di caratteri, collation, tabella riferita, ON DELETE/ON UPDATE, tipo d'indice): accanto alla voce compare
 * la sua spiegazione, all'altezza della voce, <b>senza coprire la lista</b>, dentro lo schermo.</li>
 * <li><b>T12.13</b>: al proiettore con il carattere al massimo ogni suggerimento va a capo entro una larghezza leggibile
 * e sta nello schermo; i suggerimenti mostrati davvero (anche quelli dei pulsanti sul bordo destro) e le spiegazioni
 * delle voci restano dentro lo schermo; restano visibili almeno 20 secondi; si aprono anche <b>da tastiera</b>
 * (Ctrl+F1 sul componente con il fuoco, frecce nella lista aperta).</li>
 * </ul>
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class T126T1212T1213SchermiESuggerimentiTest {

    private static final List<String> SCREENS = List.of("1024x768", "1366x768");

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
        onEdt(RamaSqlLaf::setup);
    }

    @AfterEach
    void screenBack() {
        System.clearProperty(Screens.EMULATED);
    }

    // ------------------------------------------------------------------------------------------------ strumenti

    private static void fontSize(ClientApp a, int size) {
        onEdt(() -> a.app.settings().changeFontSize(size - a.app.settings().settings().fontSize()));
        waitUntil("carattere a " + size, ClientApp.TIMEOUT,
                () -> UIManager.getFont("defaultFont").getSize() == size);
    }

    private static <T extends Component> List<T> all(Component root, Class<T> type) {
        List<T> out = new ArrayList<>();
        collect(root, type, out);
        return out;
    }

    private static <T extends Component> void collect(Component c, Class<T> type, List<T> out) {
        if (type.isInstance(c)) {
            out.add(type.cast(c));
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                collect(child, type, out);
            }
        }
    }

    private static JDialog showingDialog(String title) {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) {
                return d;
            }
        }
        return null;
    }

    /** Il suggerimento a schermo in questo momento ({@code null} se non ce n'è). */
    private static JToolTip showingTip() {
        for (Window w : Window.getWindows()) {
            if (!w.isShowing()) {
                continue;
            }
            for (JToolTip t : all(w, JToolTip.class)) {
                if (t.isShowing() && !"comboTips.tip".equals(t.getName())) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Il componente e tutti i contenitori fino alla finestra sono visibili (non in una linguetta nascosta). */
    private static boolean visibleIn(Component c, Window w) {
        for (Component p = c; p != null && p != w; p = p.getParent()) {
            if (!p.isVisible()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Una finestra dentro lo schermo; se è più piccola di quanto chiede il contenuto, il contenuto scorre (niente di
     * tagliato); i pulsanti si vedono per intero senza scorrere.
     */
    private static String checkWindow(String what, Window w, Rectangle screen, List<String> problems) {
        w.validate();
        Rectangle b = w.getBounds();
        Dimension pref = w.getPreferredSize();
        boolean fitted = w instanceof JDialog d && d.getRootPane().getClientProperty(Screens.FITTED) != null;
        boolean scrolls = false;
        for (JScrollPane sp : all(w, JScrollPane.class)) {
            Dimension view = sp.getViewport().getViewSize();
            Dimension port = sp.getViewport().getExtentSize();
            scrolls |= view.width > port.width || view.height > port.height;
        }
        if (!screen.contains(b)) {
            problems.add(what + ": " + b.width + "×" + b.height + " in " + b.x + "," + b.y + " esce dallo schermo "
                    + screen.width + "×" + screen.height);
        }
        if (w instanceof JDialog && !fitted && (b.width < pref.width - 1 || b.height < pref.height - 1)) {
            problems.add(what + ": ridotta a " + b.width + "×" + b.height + " ma il contenuto chiede " + pref.width
                    + "×" + pref.height + " e non scorre");
        }
        int buttons = 0;
        for (javax.swing.JButton button : all(w, javax.swing.JButton.class)) {
            // nella finestra principale contano i pulsanti della barra: il resto si adatta con i divisori
            boolean mainBar = w instanceof JDialog || (button.getName() != null && button.getName().startsWith("toolbar."));
            if (!mainBar || !visibleIn(button, w) || SwingUtilities.getAncestorOfClass(JComboBox.class, button) != null
                    || SwingUtilities.getAncestorOfClass(javax.swing.JScrollBar.class, button) != null
                    || SwingUtilities.getAncestorOfClass(javax.swing.JSpinner.class, button) != null
                    || button.getWidth() == 0) {
                continue;
            }
            // un pulsante dentro il contenuto che scorre è raggiungibile scorrendo; quelli in basso restano fissi
            boolean inScroll = SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, button) != null;
            Rectangle visible = button.getVisibleRect();
            if (!inScroll && (visible.width < button.getWidth() || visible.height < button.getHeight())) {
                problems.add(what + ": il pulsante «" + (button.getName() != null ? button.getName() : button.getText())
                        + "» è tagliato (" + rect(SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), w))
                        + ", in vista " + visible.width + "×" + visible.height + ", contenitore " + button.getParent().getSize()
                        + " su " + button.getParent().getPreferredSize() + ")");
            }
            if (!inScroll) {
                buttons++;
            }
        }
        return String.format("  %-44s %5d×%-4d in %4d,%-4d %d pulsanti fissi in vista%s%n", what, b.width, b.height,
                b.x, b.y, buttons, scrolls ? ", contenuto scorrevole" : "");
    }

    // ------------------------------------------------------------------------------------------------ T12.6

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t126_carattereAlMassimoNessunaFinestraEsceDalloSchermo(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t126");
        StringBuilder ev = new StringBuilder("T12.6 — carattere al massimo (" + AppSettings.MAX_FONT_SIZE
                + ") su schermi piccoli, " + server.label() + "\n");
        List<String> problems = new ArrayList<>();
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                fontSize(a, AppSettings.MAX_FONT_SIZE);
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                String createLibri = server.rows("SHOW CREATE TABLE `" + catalog + "`.`libri`").get(0).get(1);
                for (String size : SCREENS) {
                    System.setProperty(Screens.EMULATED, size);
                    Rectangle screen = Screens.usable(null);
                    ev.append("\nSchermo ").append(size).append(" (utilizzabile ").append(screen.width).append('×')
                            .append(screen.height).append(", barra delle applicazioni esclusa)\n");
                    // la finestra principale, con l'area di lavoro e un editor di tabelle aperto
                    onEdt(() -> a.frame().openTableEditor(catalog, libri));
                    onEdt(() -> Screens.fit(a.frame()));
                    ev.append(fromEdt(() -> checkWindow("finestra principale", a.frame(), screen, problems)));
                    ev.append("    barra degli strumenti: ").append(fromEdt(a.frame()::isToolbarCompact)
                            ? "solo icone (i nomi nei suggerimenti)\n" : "icone e nomi\n");
                    // le finestre di dialogo del programma, con i contenuti più ingombranti
                    Map<String, Supplier<Window>> dialogs = dialogs(a, server, catalog, createLibri);
                    for (Map.Entry<String, Supplier<Window>> e : dialogs.entrySet()) {
                        Window w = fromEdt(e.getValue()::get);
                        ev.append(fromEdt(() -> checkWindow(e.getKey(), w, screen, problems)));
                        if (size.equals("1024x768") && e.getKey().startsWith("anteprima SQL (")) {
                            onEdt(() -> Probe.paintWindow("step12", w, "T12.6-anteprima-1024x768-"
                                    + server.name().toLowerCase() + ".png"));
                        }
                        onEdt(w::dispose);
                    }
                    // i messaggi e le domande (JOptionPane) del programma, aperti come li apre lui
                    for (Map.Entry<String, Prompt> e : prompts(a).entrySet()) {
                        String title = e.getValue().title();
                        SwingUtilities.invokeLater(e.getValue().open());
                        waitUntil("finestra «" + title + "»", ClientApp.TIMEOUT, () -> showingDialog(title) != null);
                        waitUntil("finestra «" + title + "» dentro lo schermo", 5_000,
                                () -> screen.contains(showingDialog(title).getBounds()));
                        JDialog d = fromEdt(() -> showingDialog(title));
                        ev.append(fromEdt(() -> checkWindow(e.getKey(), d, screen, problems)));
                        if (size.equals("1024x768") && e.getKey().startsWith("errore spiegato")) {
                            onEdt(() -> Probe.paintWindow("step12", d, "T12.6-errore-1024x768-"
                                    + server.name().toLowerCase() + ".png"));
                        }
                        onEdt(d::dispose);
                    }
                }
                fontSize(a, AppSettings.DEFAULT_FONT_SIZE);
            }
            ev.append("\nProblemi: ").append(problems.size()).append('\n');
            problems.forEach(p -> ev.append("  ").append(p).append('\n'));
            assertEquals(List.of(), problems);
            ev.append("Esito: OK\n");
        } catch (AssertionError | Exception e) {
            ev.append("Esito: FALLITO — ").append(e.getMessage()).append('\n');
            throw e;
        } finally {
            server.dropQuietly(catalog);
            Probe.writeText("step12", "T12.6-schermi-" + server.name().toLowerCase() + ".txt", ev.toString());
        }
    }

    private Map<String, Supplier<Window>> dialogs(ClientApp a, DbServer server, String catalog, String createLibri) {
        Map<String, Supplier<Window>> m = new LinkedHashMap<>();
        StringBuilder many = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            many.append("INSERT INTO `").append(catalog).append("`.`libri` (`titolo`, `isbn`, `anno`) VALUES ('Titolo ")
                    .append(i).append(" di un libro con un nome piuttosto lungo', '978000000").append(i)
                    .append("', 2020);\n");
        }
        SqlScript insert = SqlScript.of("Inserisci", "Editor SQL", many.toString());
        SqlScript drop = SqlScript.of("Elimina", "Navigatore", "DROP TABLE `" + catalog + "`.`libri`");
        m.put("anteprima SQL (60 istruzioni)", () -> new PreviewDialog(a.frame(), insert,
                ConfirmationPolicy.evaluate(insert)));
        m.put("anteprima SQL (conferma scritta)", () -> new PreviewDialog(a.frame(), drop,
                ConfirmationPolicy.evaluate(drop)));
        m.put("nuovo catalogo", () -> new CreateCatalogDialog(a.frame(), List.of(new CollationInfo(
                "utf8mb4_unicode_ci", "utf8mb4", false), new CollationInfo("utf8mb4_general_ci", "utf8mb4", true)),
                "utf8mb4", null));
        m.put("profilo di connessione", () -> new ProfileDialog(a.frame(), server.profile(), a.app.connections()));
        m.put("password", () -> new PasswordDialog(a.frame(), server.profile()));
        m.put("impostazioni", () -> new SettingsDialog(a.frame(), AppSettings.defaults()));
        m.put("informazioni", () -> new AboutDialog(a.frame()));
        m.put("guida rapida", () -> new GuideDialog(a.frame()));
        m.put("errore di connessione (messaggio aperto)", () -> {
            ConnectionErrorDialog d = new ConnectionErrorDialog(a.frame(), server.profile(), new ConnectionFailure(
                    ConnectionErrorCause.PORT_CLOSED, "Il server non risponde.", 0, "",
                    "Could not connect to address=(host=127.0.0.1)(port=3399)(type=primary) : Connection refused: "
                            + "getsockopt; the server is not listening on that port or a firewall is blocking it"));
            d.setOriginalShown(true);
            return d;
        });
        m.put("tabelle del modello ER", () -> new ModelTablesDialog(a.frame(), catalog, List.of("autori", "editori",
                "libri", "libri_autori", "prestiti", "soci")));
        m.put("SQL di creazione di libri", () -> new ShowCreateDialog(a.frame(), "libri", "SHOW CREATE TABLE",
                createLibri, s -> { }));
        return m;
    }

    /** Un messaggio del programma: il titolo con cui ritrovarne la finestra, e come aprirlo. */
    private record Prompt(String title, Runnable open) {
    }

    /** I messaggi del programma con i testi più lunghi. */
    private static Map<String, Prompt> prompts(ClientApp a) {
        Map<String, Prompt> m = new LinkedHashMap<>();
        SwingPrompts main = new SwingPrompts(a::frame, () -> "");
        SwingTableEditorPrompts editor = new SwingTableEditorPrompts(a::frame);
        SwingWorkspacePrompts ws = new SwingWorkspacePrompts(a::frame, () -> "");
        String explained = ErrorExplainer.explain(1451).orElseThrow() + "\n\nMessaggio del server: Cannot delete or "
                + "update a parent row: a foreign key constraint fails (`ramasql_test_biblioteca`.`prestiti`, "
                + "CONSTRAINT `fk_prestiti_soci` FOREIGN KEY (`id_socio`) REFERENCES `soci` (`id`) ON DELETE RESTRICT "
                + "ON UPDATE CASCADE)";
        m.put("errore spiegato", new Prompt("T12.6 errore spiegato", () -> main.showError("T12.6 errore spiegato",
                explained)));
        m.put("errore con un percorso lunghissimo", new Prompt("T12.6 percorso", () -> main.showError("T12.6 percorso",
                "Impossibile leggere il file C:\\Users\\studente\\Documents\\Esercitazioni\\Basi_di_dati\\2026\\"
                        + "Biblioteca_comunale\\importazioni\\elenco_completo_dei_soci_iscritti_dal_2019.csv")));
        m.put("domanda", new Prompt("T12.6 domanda", () -> main.confirm("T12.6 domanda",
                Texts.get("tableeditor.close.question", "libri"), Texts.get("dialog.ok"))));
        m.put("messaggio dell'editor di tabelle", new Prompt("T12.6 editor", () -> editor.showMessage("T12.6 editor",
                explained)));
        m.put("richiesta di un nome", new Prompt(Texts.get("nav.rename.title"), () -> ws.askNewTableName(
                "ramasql_test_biblioteca", "prestiti_dei_soci_iscritti_dal_duemiladiciannove_archivio")));
        return m;
    }

    // ------------------------------------------------------------------------------------------------ T12.12

    /** Una lista a discesa dell'editor di tabelle, e come aprirla (le liste dentro le tabelle sono editor di cella). */
    private record ComboAt(String name, JTable table, int column, JComboBox<?> combo) {
    }

    private static List<ComboAt> combosOf(Component tab) {
        List<ComboAt> out = new ArrayList<>();
        for (JComboBox<?> c : all(tab, JComboBox.class)) {
            if (c.isShowing() && ComboTips.installed(c)) {
                out.add(new ComboAt(c.getName(), null, -1, c));
            }
        }
        for (JTable t : all(tab, JTable.class)) {
            if (!t.isShowing() || t.getRowCount() == 0) {
                continue;
            }
            for (int col = 0; col < t.getColumnCount(); col++) {
                if (t.getColumnModel().getColumn(col).getCellEditor() instanceof DefaultCellEditor ed
                        && ed.getComponent() instanceof JComboBox<?> c && ComboTips.installed(c)) {
                    out.add(new ComboAt(c.getName() + " (colonna «" + t.getColumnName(col) + "»)", t, col, c));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static JList<Object> popupList(JComboBox<?> combo) {
        Object child = combo.getUI().getAccessibleChild(combo, 0);
        return child instanceof ComboPopup p ? (JList<Object>) p.getList() : null;
    }

    private static void open(ComboAt c) {
        onEdt(() -> {
            if (c.table() != null) {
                c.table().editCellAt(0, c.column());
            }
        });
        waitUntil("lista «" + c.name() + "» visibile", ClientApp.TIMEOUT, () -> c.combo().isShowing());
        onEdt(() -> c.combo().showPopup());
        waitUntil("lista «" + c.name() + "» aperta", ClientApp.TIMEOUT, () -> {
            JList<Object> list = popupList(c.combo());
            return list != null && list.isShowing();
        });
    }

    private static void close(ComboAt c) {
        onEdt(() -> {
            c.combo().hidePopup();
            if (c.table() != null && c.table().getCellEditor() != null) {
                c.table().getCellEditor().cancelCellEditing();
            }
        });
    }

    /**
     * Passa con il mouse su ogni voce della lista aperta e controlla la spiegazione accanto: il testo giusto, accanto
     * alla voce (all'altezza della voce, o spinta dentro lo schermo), senza coprire la lista, dentro lo schermo.
     */
    private static int hoverEveryItem(ComboAt c, Rectangle screen, List<String> problems, StringBuilder ev) {
        JList<Object> list = fromEdt(() -> popupList(c.combo()));
        int n = fromEdt(() -> list.getModel().getSize());
        int explained = 0;
        for (int i = 0; i < n; i++) {
            int k = i;
            Object item = fromEdt(() -> list.getModel().getElementAt(k));
            onEdt(() -> {
                list.ensureIndexIsVisible(k);
                Rectangle cell = list.getCellBounds(k, k);
                list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                        cell.x + cell.width / 2, cell.y + cell.height / 2, 0, false));
            });
            String expected = fromEdt(() -> ComboTips.tipFor(c.combo(), item));
            String shown = fromEdt(() -> ComboTips.shownText(c.combo()));
            Rectangle tip = fromEdt(() -> ComboTips.shownBounds(c.combo()));
            Rectangle popup = fromEdt(() -> ComboTips.popupBounds(c.combo()));
            int itemY = fromEdt(() -> {
                Rectangle cell = list.getCellBounds(k, k);
                return list.getLocationOnScreen().y + cell.y;
            });
            String what = c.name() + " › «" + item + "»";
            if (expected == null) {
                // solo le intestazioni dei gruppi di tipi, che non si scelgono
                if (!item.getClass().getSimpleName().contains("Header")) {
                    problems.add(what + ": nessuna spiegazione");
                } else if (shown != null) {
                    problems.add(what + ": resta a schermo la spiegazione di un'altra voce");
                }
                continue;
            }
            explained++;
            if (!expected.equals(shown) || tip == null) {
                problems.add(what + ": accanto compare «" + shown + "»");
                continue;
            }
            if (popup != null && tip.intersects(popup)) {
                problems.add(what + ": la spiegazione " + tip + " copre la lista " + popup);
            }
            if (!screen.contains(tip)) {
                problems.add(what + ": la spiegazione " + tip + " esce dallo schermo " + screen);
            }
            if (Math.abs(tip.y - itemY) > tip.height) {
                problems.add(what + ": la spiegazione (y=" + tip.y + ") è lontana dalla voce (y=" + itemY + ")");
            }
            if (explained == 1) {
                ev.append(String.format("  %-48s lista %s, prima spiegazione %s%n", c.name(), rect(popup), rect(tip)));
            }
        }
        return explained;
    }

    private static String rect(Rectangle r) {
        return r == null ? "—" : r.x + "," + r.y + " " + r.width + "×" + r.height;
    }

    /** Tutte le liste a discesa di ogni linguetta dell'editor di tabelle, voce per voce. */
    private static Set<String> hoverAllCombos(TableEditor editor, Rectangle screen, List<String> problems,
            StringBuilder ev) {
        Set<String> seen = new LinkedHashSet<>();
        JTabbedPane tabs = editor.tabs();
        int count = fromEdt(tabs::getTabCount);
        for (int t = 0; t < count; t++) {
            int k = t;
            onEdt(() -> tabs.setSelectedIndex(k));
            waitUntil("linguetta " + k, ClientApp.TIMEOUT, () -> tabs.getComponentAt(k).isShowing());
            List<ComboAt> combos = fromEdt(() -> combosOf(tabs.getComponentAt(k)));
            for (ComboAt c : combos) {
                open(c);
                int n = hoverEveryItem(c, screen, problems, ev);
                close(c);
                seen.add(c.combo().getName());
                ev.append(String.format("    %d voci spiegate%n", n));
            }
        }
        return seen;
    }

    private static final Set<String> REQUIRED = Set.of("columns.typeEditor", "options.engine", "options.charset",
            "options.collation", "fks.refTable", "fks.action", "indexes.kind");

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t1212_accantoAOgniVoceLaSuaSpiegazione(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t1212");
        StringBuilder ev = new StringBuilder("T12.12 — passaggio del mouse su ogni voce delle liste dell'editor di "
                + "tabelle (" + server.label() + ")\n");
        List<String> problems = new ArrayList<>();
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                TableDef prestiti = a.workspace().reader().table(catalog, "prestiti").orElseThrow();
                TableEditor editor = fromEdt(() -> a.frame().openTableEditor(catalog, prestiti));
                onEdt(() -> a.frame().setVisible(true));
                waitUntil("finestra a schermo", ClientApp.TIMEOUT, () -> editor.isShowing());
                Rectangle screen = fromEdt(() -> Screens.usable(a.frame()));
                ev.append("Schermo utilizzabile: ").append(rect(screen)).append('\n');
                Set<String> seen = hoverAllCombos(editor, screen, problems, ev);
                Set<String> missing = new LinkedHashSet<>(REQUIRED);
                missing.removeAll(seen);
                ev.append("Liste provate: ").append(seen).append("\nListe richieste non trovate: ").append(missing)
                        .append('\n');
                assertTrue(missing.isEmpty(), "liste non trovate: " + missing);
                onEdt(() -> a.frame().setVisible(false));
            }
            ev.append("Problemi: ").append(problems.size()).append('\n');
            problems.forEach(p -> ev.append("  ").append(p).append('\n'));
            assertEquals(List.of(), problems);
            ev.append("Esito: OK\n");
        } catch (AssertionError | Exception e) {
            ev.append("Esito: FALLITO — ").append(e.getMessage()).append('\n');
            throw e;
        } finally {
            server.dropQuietly(catalog);
            Probe.writeText("step12", "T12.12-voci-" + server.name().toLowerCase() + ".txt", ev.toString());
        }
    }

    // ------------------------------------------------------------------------------------------------ T12.13

    private static Map<String, String> allTipTexts() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        for (String res : List.of("/it/ramasql/app/messages.properties", "/it/ramasql/qb/qb_it.properties")) {
            Properties p = new Properties();
            try (InputStream in = T126T1212T1213SchermiESuggerimentiTest.class.getResourceAsStream(res)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            for (String k : p.stringPropertyNames()) {
                if (k.endsWith(".tooltip")) {
                    out.put(k, p.getProperty(k).replace("%s", "utf8mb4"));
                }
            }
        }
        return out;
    }

    /** Il fuoco della tastiera sul componente, davvero (la finestra deve essere attiva). */
    private static boolean focus(JComponent c) {
        onEdt(() -> {
            Window w = SwingUtilities.getWindowAncestor(c);
            w.toFront();
            c.requestFocusInWindow();
        });
        try {
            waitUntil("fuoco su " + TipCoverage.describe(c), 5_000, c::isFocusOwner);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    /** Un tasto premuto e rilasciato, dalla coda degli eventi di AWT: passa da dove passa un tasto vero. */
    private static void press(JComponent c, int key, int modifiers) {
        java.awt.EventQueue q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
        long now = System.currentTimeMillis();
        q.postEvent(new KeyEvent(c, KeyEvent.KEY_PRESSED, now, modifiers, key, KeyEvent.CHAR_UNDEFINED));
        q.postEvent(new KeyEvent(c, KeyEvent.KEY_RELEASED, now + 1, modifiers, key, KeyEvent.CHAR_UNDEFINED));
        onEdt(() -> { });   // gli eventi sono stati smistati
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t1213_suggerimentiAlProiettoreEDaTastiera(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t1213");
        StringBuilder ev = new StringBuilder("T12.13 — suggerimenti al proiettore (1024×768, carattere "
                + AppSettings.MAX_FONT_SIZE + ") e da tastiera, " + server.label() + "\n");
        List<String> problems = new ArrayList<>();
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                fontSize(a, AppSettings.MAX_FONT_SIZE);
                System.setProperty(Screens.EMULATED, "1024x768");
                Rectangle screen = Screens.usable(null);
                // 1) ogni testo, impaginato come a schermo: a capo entro una larghezza leggibile, dentro lo schermo
                Map<String, String> texts = allTipTexts();
                int readable = (int) (screen.width * 0.8);
                int[] widest = {0};
                int[] tallest = {0};
                String[] widestKey = {""};
                onEdt(() -> {
                    JToolTip probe = a.frame().getRootPane().createToolTip();
                    for (Map.Entry<String, String> e : texts.entrySet()) {
                        probe.setTipText(e.getValue());
                        Dimension d = probe.getPreferredSize();
                        if (d.width > widest[0]) {
                            widest[0] = d.width;
                            widestKey[0] = e.getKey();
                        }
                        tallest[0] = Math.max(tallest[0], d.height);
                        if (d.width > readable || d.height > screen.height) {
                            problems.add(e.getKey() + ": " + d.width + "×" + d.height + " non sta al proiettore");
                        }
                    }
                });
                ev.append("1) ").append(texts.size()).append(" testi impaginati: il più largo ").append(widest[0])
                        .append(" px (").append(widestKey[0]).append("; limite leggibile ").append(readable).append(" px), il più alto ")
                        .append(tallest[0]).append(" px (schermo ").append(screen.height).append(" px)\n");
                // 2) il tempo per leggerli
                int dismiss = ToolTipManager.sharedInstance().getDismissDelay();
                ev.append("2) restano a schermo ").append(dismiss / 1000).append(" s (richiesti almeno 20)\n");
                if (dismiss < 20_000) {
                    problems.add("i suggerimenti spariscono dopo " + dismiss + " ms");
                }
                // 3) da tastiera: Ctrl+F1 su ogni componente che si raggiunge con Tab (finestra al proiettore)
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                TableEditor editor = fromEdt(() -> a.frame().openTableEditor(catalog, libri));
                onEdt(() -> {
                    a.frame().setLocation(0, 0);
                    Screens.fit(a.frame());
                    a.frame().setVisible(true);
                    a.frame().toFront();
                });
                waitUntil("finestra a schermo", ClientApp.TIMEOUT, () -> editor.isShowing());
                onEdt(() -> {
                    a.nav().tree().setSelectionRow(0);
                    JTable columns = all(editor, JTable.class).get(0);
                    columns.changeSelection(1, 1, false, false);
                });
                List<JComponent> targets = fromEdt(() -> {
                    List<JComponent> l = new ArrayList<>();
                    for (JComponent j : all(a.frame().getContentPane(), JComponent.class)) {
                        boolean items = j instanceof JTree || j instanceof JTable || j instanceof JList;
                        if (j.isShowing() && j.isFocusable() && j.isEnabled() && (items || j.getToolTipText() != null)
                                && SwingUtilities.getAncestorOfClass(JComboBox.class, j) == null
                                && SwingUtilities.getAncestorOfClass(javax.swing.JSpinner.class, j) == null
                                && !(j instanceof javax.swing.JScrollBar) && !(j instanceof JTabbedPane)) {
                            l.add(j);
                        }
                    }
                    return l;
                });
                int posted = 0;
                for (JComponent c : targets) {
                    String what = TipCoverage.describe(c);
                    if (!focus(c)) {
                        problems.add(what + ": non prende il fuoco");
                        continue;
                    }
                    press(c, KeyEvent.VK_F1, InputEvent.CTRL_DOWN_MASK);
                    try {
                        waitUntil("suggerimento di " + what, 5_000, () -> showingTip() != null);
                    } catch (AssertionError e) {
                        problems.add(what + ": Ctrl+F1 non apre il suggerimento");
                        continue;
                    }
                    JToolTip tip = fromEdt(T126T1212T1213SchermiESuggerimentiTest::showingTip);
                    Rectangle b = fromEdt(() -> new Rectangle(tip.getLocationOnScreen(), tip.getSize()));
                    String text = fromEdt(tip::getTipText);
                    if (!screen.contains(b) || text == null || text.isBlank()) {
                        problems.add(what + ": suggerimento " + rect(b) + " «" + text + "» di "
                                + fromEdt(() -> tip.getComponent() == null ? "?" : TipCoverage.describe(tip.getComponent())));
                        press(c, KeyEvent.VK_ESCAPE, 0);
                        continue;
                    }
                    boolean hasItem = fromEdt(() -> c instanceof JTree tr ? tr.getLeadSelectionRow() >= 0
                            : c instanceof JTable t && t.getSelectedRow() >= 0);
                    if (hasItem) {
                        // accanto alla voce scelta, non in fondo al componente
                        Rectangle cell = fromEdt(() -> {
                            Rectangle r = c instanceof JTree t ? t.getRowBounds(t.getLeadSelectionRow())
                                    : ((JTable) c).getCellRect(((JTable) c).getSelectedRow(),
                                            Math.max(0, ((JTable) c).getSelectedColumn()), false);
                            java.awt.Point o = c.getLocationOnScreen();
                            return new Rectangle(o.x + r.x, o.y + r.y, r.width, r.height);
                        });
                        if (Math.abs(b.y - (cell.y + cell.height)) > cell.height * 2 && !b.intersects(cell)) {
                            problems.add(what + ": il suggerimento " + rect(b) + " è lontano dalla voce " + rect(cell));
                        }
                        ev.append("   Ctrl+F1 su ").append(what).append(" (voce scelta ").append(rect(cell))
                                .append("): «").append(text.lines().findFirst().orElse("")).append("…» in ")
                                .append(rect(b)).append('\n');
                    }
                    if (posted == 0) {
                        // il primo resta a schermo il tempo di leggerlo ad alta voce
                        Thread.sleep(20_500);
                        boolean still = fromEdt(() -> tip.isShowing());
                        ev.append("   dopo 20,5 s il suggerimento di ").append(what)
                                .append(still ? " è ancora a schermo\n" : " è sparito\n");
                        if (!still) {
                            problems.add("il suggerimento sparisce prima di 20 s");
                        }
                    }
                    press(c, KeyEvent.VK_ESCAPE, 0);   // Esc: si chiude
                    try {
                        waitUntil("suggerimento chiuso", 5_000, () -> showingTip() == null);
                    } catch (AssertionError e) {
                        problems.add(what + ": Esc non chiude il suggerimento");
                    }
                    posted++;
                }
                ev.append("3) Ctrl+F1 con il fuoco sul componente: ").append(posted).append(" suggerimenti aperti (e "
                        + "chiusi con Esc) su ").append(targets.size()).append(" componenti che prendono il fuoco "
                        + "nella schermata (navigatore, editor di tabelle, pannello SQL; il fuoco dato con "
                        + "requestFocusInWindow, i tasti veri), tutti dentro lo schermo\n");
                // 4) le spiegazioni delle voci al proiettore: engine, collation, azioni; anche con le frecce
                ev.append("4) liste dell'editor di tabelle al proiettore:\n");
                Set<String> seen = hoverAllCombos(editor, screen, problems, ev);
                ev.append("   liste provate: ").append(seen).append('\n');
                JTabbedPane tabs = editor.tabs();
                onEdt(() -> tabs.setSelectedIndex(tabs.indexOfTab(Texts.get("tableeditor.tab.options"))));
                ComboAt engine = fromEdt(() -> combosOf(tabs.getSelectedComponent()).stream()
                        .filter(c -> "options.engine".equals(c.combo().getName())).findFirst().orElseThrow());
                assertTrue(focus(engine.combo()), "fuoco sulla lista dell'engine");
                open(engine);
                String before = fromEdt(() -> ComboTips.shownText(engine.combo()));
                int indexBefore = fromEdt(() -> popupList(engine.combo()).getSelectedIndex());
                press(engine.combo(), indexBefore == 0 ? KeyEvent.VK_DOWN : KeyEvent.VK_UP, 0);
                waitUntil("voce cambiata con la freccia", 5_000,
                        () -> popupList(engine.combo()).getSelectedIndex() != indexBefore);
                String after = fromEdt(() -> ComboTips.shownText(engine.combo()));
                Object selected = fromEdt(() -> popupList(engine.combo()).getSelectedValue());
                boolean keyboard = after != null && after.equals(ComboTips.tipFor(engine.combo(), selected))
                        && !after.equals(before);
                int indexAfter = fromEdt(() -> popupList(engine.combo()).getSelectedIndex());
                ev.append("5) freccia nella lista aperta dell'engine (voce ").append(indexBefore).append(" → ")
                        .append(indexAfter).append("): la spiegazione accanto passa a «").append(selected).append("»: ")
                        .append(keyboard ? "sì" : "no, era «" + before + "», ora «" + after + "»").append('\n');
                if (!keyboard) {
                    problems.add("con le frecce la spiegazione della voce non cambia");
                }
                close(engine);
                onEdt(() -> a.frame().setVisible(false));
                fontSize(a, AppSettings.DEFAULT_FONT_SIZE);
            }
            ev.append("Problemi: ").append(problems.size()).append('\n');
            problems.forEach(p -> ev.append("  ").append(p).append('\n'));
            assertEquals(List.of(), problems);
            ev.append("Esito: OK\n");
        } catch (AssertionError | Exception e) {
            ev.append("Esito: FALLITO — ").append(e.getMessage()).append('\n');
            throw e;
        } finally {
            server.dropQuietly(catalog);
            Probe.writeText("step12", "T12.13-proiettore-" + server.name().toLowerCase() + ".txt", ev.toString());
        }
    }
}
