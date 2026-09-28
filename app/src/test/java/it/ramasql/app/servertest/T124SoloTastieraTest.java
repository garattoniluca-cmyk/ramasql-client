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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.navigator.CreateCatalogDialog;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.app.tableeditor.TableEditor;
import it.ramasql.app.theme.RamaSqlLaf;

/**
 * <b>T12.4</b>: l'esercitazione T6.11 (la {@code biblioteca} costruita da zero solo con il client, registro esportato e
 * rieseguito su un catalogo vuoto, confronto) eseguita <b>solo da tastiera</b>: ogni gesto è un tasto vero consegnato
 * al componente con il fuoco ({@link Keys}); le finestre di conferma e di anteprima sono quelle vere. Il test «guarda
 * lo schermo» come l'utente (dove è il fuoco, quale cella è scelta) per decidere il tasto successivo, e conta i tasti.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class T124SoloTastieraTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
        onEdt(RamaSqlLaf::setup);
    }

    // ------------------------------------------------------------------------------------------------ gesti

    private static boolean inside(Component c, Component area) {
        return c != null && (c == area || (area instanceof Container k && k.isAncestorOf(c)));
    }

    static Component byName(Component c, String name) {
        if (name.equals(c.getName())) {
            return c;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                Component f = byName(child, name);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    /** F6 finché il fuoco non è nell'area. */
    private static void f6To(Keys k, String what, Component area) {
        for (int i = 0; i < 6; i++) {
            if (inside(Keys.owner(), area)) {
                return;
            }
            k.press(KeyEvent.VK_F6);
        }
        throw new AssertionError("con F6 non si arriva a " + what + " (fuoco su " + Keys.describe(Keys.owner()) + ")");
    }

    /** Con le frecce, dalla cella scelta alla cella data. */
    private static void cell(Keys k, JTable t, int row, int col) {
        for (int i = 0; i < 60; i++) {
            int r = fromEdt(() -> t.getSelectionModel().getLeadSelectionIndex());
            int c = fromEdt(() -> t.getColumnModel().getSelectionModel().getLeadSelectionIndex());
            if (r == row && c == col) {
                return;
            }
            if (r < 0 || c < 0) {
                k.press(KeyEvent.VK_HOME, InputEvent.CTRL_DOWN_MASK);
            } else if (r < row) {
                k.press(KeyEvent.VK_DOWN);
            } else if (r > row) {
                k.press(KeyEvent.VK_UP);
            } else if (c < col) {
                k.press(KeyEvent.VK_RIGHT);
            } else {
                k.press(KeyEvent.VK_LEFT);
            }
        }
        throw new AssertionError("con le frecce non si arriva alla cella " + row + "," + col);
    }

    /** F2 sulla cella, tutto il testo scelto, il valore nuovo, Invio. */
    private static void write(Keys k, JTable t, int row, int col, String value) {
        cell(k, t, row, col);
        k.press(KeyEvent.VK_F2);
        k.press(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
        k.type(value);
        k.press(KeyEvent.VK_ENTER);
        waitUntil("fine della modifica della cella", 5_000, () -> !t.isEditing());
        // Invio nella tabella scende di una riga: si torna dove si era
        cell(k, t, row, col);
    }

    /** Spazio sulla casella: si accende o si spegne. */
    private static void toggle(Keys k, JTable t, int row, int col, boolean wanted) {
        cell(k, t, row, col);
        if (!Boolean.valueOf(wanted).equals(fromEdt(() -> t.getValueAt(row, col)))) {
            k.press(KeyEvent.VK_SPACE);
        }
        waitUntil("casella " + row + "," + col + " = " + wanted, 5_000,
                () -> Boolean.valueOf(wanted).equals(t.getValueAt(row, col)) && !t.isEditing());
    }

    /** Il pulsante con quel nome, raggiunto con Tab e premuto con lo Spazio. */
    private static void button(Keys k, String name) {
        k.tabTo(name);
        k.press(KeyEvent.VK_SPACE);
    }

    /** L'anteprima vera: Invio la esegue (se non chiede di riscrivere il nome). */
    private static void previewEnter(Keys k) {
        Keys.waitWindow(PreviewDialog.class);
        k.press(KeyEvent.VK_ENTER);
    }

    /** Una colonna da creare: nome, tipo, argomenti, NN, UN, PK, AI. */
    private record Col(String name, String type, String args, boolean nn, boolean un, boolean pk, boolean ai) {
    }

    @SuppressWarnings("unchecked")
    private static javax.swing.JList<Object> popupList(javax.swing.JComboBox<?> combo) {
        Object child = combo.getUI().getAccessibleChild(combo, 0);
        return child instanceof javax.swing.plaf.basic.ComboPopup p ? (javax.swing.JList<Object>) p.getList() : null;
    }

    /** Nella lista aperta con Alt+Giù, le frecce fino alla voce, poi Invio (la sceglie). */
    private static void pick(Keys k, javax.swing.JComboBox<?> combo, String value) {
        k.press(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
        try {
            waitUntil("lista aperta", 5_000, () -> combo.isPopupVisible());
        } catch (AssertionError e) {
            java.util.List<String> wins = new java.util.ArrayList<>();
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w.isShowing()) {
                    wins.add(w.getClass().getSimpleName() + (w.isFocused() ? "*" : ""));
                }
            }
            throw new AssertionError("finestre " + wins + "; la lista " + combo.getName() + " non si apre: voci " + fromEdt(combo::getItemCount)
                    + ", mostrata " + fromEdt(combo::isShowing) + ", fuoco su " + Keys.describe(Keys.owner()), e);
        }
        javax.swing.JList<Object> list = fromEdt(() -> popupList(combo));
        int target = fromEdt(() -> {
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (value.equals(String.valueOf(list.getModel().getElementAt(i)))) {
                    return i;
                }
            }
            return -1;
        });
        assertTrue(target >= 0, "la lista " + combo.getName() + " non ha la voce «" + value + "»");
        for (int i = 0; i < 80; i++) {
            int at = fromEdt(list::getSelectedIndex);
            if (at == target) {
                break;
            }
            k.press(at < target ? KeyEvent.VK_DOWN : KeyEvent.VK_UP);
        }
        k.press(KeyEvent.VK_ENTER);
        waitUntil("scelta «" + value + "»", 5_000, () -> !combo.isPopupVisible());
    }

    /** Una lista nella cella: Alt+Giù la apre, frecce, Invio. */
    private static void choose(Keys k, JTable t, int row, int col, String value) {
        cell(k, t, row, col);
        javax.swing.JComboBox<?> combo = fromEdt(() -> (javax.swing.JComboBox<?>)
                ((javax.swing.DefaultCellEditor) t.getCellEditor(row, col)).getComponent());
        pick(k, combo, value);
        waitUntil("fine della modifica della cella", 5_000, () -> !t.isEditing());
        assertEquals(value, fromEdt(() -> String.valueOf(t.getValueAt(row, col))), "cella " + row + "," + col);
    }

    /** Dalla striscia delle linguette (Maiusc+Tab), le frecce fino alla linguetta. */
    private static void tabOf(Keys k, TableEditor e, int index) {
        k.tabTo("le linguette", c -> c == e.tabs(), true);
        for (int i = 0; i < 8 && fromEdt(() -> e.tabs().getSelectedIndex()) != index; i++) {
            k.press(fromEdt(() -> e.tabs().getSelectedIndex()) < index ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT);
        }
        assertEquals(index, (int) fromEdt(() -> e.tabs().getSelectedIndex()));
    }

    private static void selectRow(Keys k, javax.swing.JTree tree, int row) {
        for (int i = 0; i < 200; i++) {
            int lead = fromEdt(tree::getLeadSelectionRow);
            if (lead == row) {
                return;
            }
            k.press(lead < 0 ? KeyEvent.VK_HOME : lead < row ? KeyEvent.VK_DOWN : KeyEvent.VK_UP);
        }
        throw new AssertionError("con le frecce non si arriva alla riga " + row + " del navigatore");
    }

    /** Nel navigatore, dal catalogo in giù: ogni nodo si sceglie con le frecce e si apre con Destra. */
    private static void navTo(Keys k, ClientApp a, String catalog, String table) {
        javax.swing.JTree tree = a.nav().tree();
        f6To(k, "il navigatore", tree);
        List<java.util.function.Supplier<javax.swing.tree.TreePath>> chain = new java.util.ArrayList<>();
        chain.add(() -> a.nav().find(NavNode.Kind.CATALOG, catalog, catalog));
        if (table != null) {
            chain.add(() -> a.nav().find(NavNode.Kind.TABLES, catalog, null));
            chain.add(() -> a.nav().find(NavNode.Kind.TABLE, catalog, table));
        }
        for (int i = 0; i < chain.size(); i++) {
            java.util.function.Supplier<javax.swing.tree.TreePath> s = chain.get(i);
            waitUntil("nodo nel navigatore", ClientApp.TIMEOUT, () -> s.get() != null
                    && tree.getRowForPath(s.get()) >= 0);
            selectRow(k, tree, fromEdt(() -> tree.getRowForPath(s.get())));
            if (i < chain.size() - 1 && !fromEdt(() -> tree.isExpanded(s.get()))) {
                k.press(KeyEvent.VK_RIGHT);
                waitUntil("nodo aperto", ClientApp.TIMEOUT, () -> tree.isExpanded(s.get()));
            }
        }
    }

    /** Maiusc+F10 sul nodo scelto, Giù fino alla voce, Invio. */
    private static void menu(Keys k, String item) {
        k.press(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK);
        waitUntil("menu aperto", 5_000,
                () -> javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 1);
        for (int i = 0; i < 20; i++) {
            javax.swing.MenuElement[] path = fromEdt(
                    () -> javax.swing.MenuSelectionManager.defaultManager().getSelectedPath());
            if (path[path.length - 1] instanceof javax.swing.JMenuItem m && item.equals(m.getName())) {
                k.press(KeyEvent.VK_ENTER);
                return;
            }
            k.press(KeyEvent.VK_DOWN);
        }
        throw new AssertionError("nel menu non c'è «" + item + "»");
    }

    private static void newCatalog(Keys k, ClientApp a, DbServer server, String name) throws Exception {
        javax.swing.JTree tree = a.nav().tree();
        f6To(k, "il navigatore", tree);
        selectRow(k, tree, 0);
        menu(k, "nav.menu.newCatalog");
        Keys.waitWindow(CreateCatalogDialog.class);
        k.tabTo("catalog.create.name");
        k.type(name);
        k.press(KeyEvent.VK_ENTER);
        previewEnter(k);
        a.awaitLastProposal();
        a.waitIdle();
        assertTrue(server.catalogExists(name), "catalogo creato da tastiera: " + name);
    }

    private static TableEditor newTable(Keys k, ClientApp a, String catalog, String name) {
        navTo(k, a, catalog, null);
        menu(k, "nav.menu.newTable");
        waitUntil("editor di tabelle", ClientApp.TIMEOUT, () -> a.frame().tabs().selected() instanceof TableEditor);
        TableEditor e = fromEdt(() -> (TableEditor) a.frame().tabs().selected());
        f6To(k, "l'editor di tabelle", e);
        tabOf(k, e, 3);
        k.tabTo("options.name");
        k.press(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
        k.type(name);
        tabOf(k, e, 0);
        return e;
    }

    private static void columns(Keys k, TableEditor e, List<Col> cols) {
        JTable t = fromEdt(() -> (JTable) byName(e, "columns.table"));
        for (int r = 0; r < cols.size(); r++) {
            Col c = cols.get(r);
            if (r > 0) {
                button(k, "columns.add");
                k.tabTo("columns.table", x -> x == t, true);
            } else {
                k.tabTo("columns.table", x -> x == t, false);
            }
            if (r > 0 || !"id".equals(c.name())) {
                write(k, t, r, 1, c.name());
            }
            if (r > 0) {
                write(k, t, r, 2, c.type());
                if (c.args() != null) {
                    write(k, t, r, 3, c.args());
                }
            }
            toggle(k, t, r, 4, c.pk());
            toggle(k, t, r, 5, c.nn());
            toggle(k, t, r, 7, c.ai());
            toggle(k, t, r, 8, c.un());
        }
    }

    private static void index(Keys k, TableEditor e, String name, String kind, String column) {
        tabOf(k, e, 1);
        JTable t = fromEdt(() -> (JTable) byName(e, "indexes.table"));
        button(k, "indexes.add");
        k.tabTo("indexes.table", x -> x == t, true);
        int row = fromEdt(() -> t.getRowCount() - 1);
        write(k, t, row, 1, name);
        choose(k, t, row, 2, kind);
        javax.swing.JComboBox<?> choice = (javax.swing.JComboBox<?>) k.tabTo("indexes.columnChoice");
        pick(k, choice, column);
        button(k, "indexes.addColumn");
    }

    private static void foreignKey(Keys k, TableEditor e, String name, String refTable, String column,
            String refColumn, String onDelete) {
        tabOf(k, e, 2);
        JTable t = fromEdt(() -> (JTable) byName(e, "fks.table"));
        JTable pairs = fromEdt(() -> (JTable) byName(e, "fks.pairs"));
        button(k, "fks.add");
        k.tabTo("fks.table", x -> x == t, true);
        int row = fromEdt(() -> t.getRowCount() - 1);
        write(k, t, row, 1, name);
        choose(k, t, row, 2, refTable);
        boolean paired = fromEdt(() -> pairs.getRowCount() > 0 && column.equals(String.valueOf(pairs.getValueAt(0, 0)))
                && refColumn.equals(String.valueOf(pairs.getValueAt(0, 1))));
        if (!paired) {
            if (fromEdt(pairs::getRowCount) == 0) {
                button(k, "fks.addPair");
            }
            k.tabTo("fks.pairs", x -> x == pairs, false);
            choose(k, pairs, 0, 0, column);
            choose(k, pairs, 0, 1, refColumn);
        }
        k.tabTo("fks.table", x -> x == t, true);
        choose(k, t, row, 4, onDelete);
    }

    private static void apply(Keys k, ClientApp a, TableEditor e, String table, StringBuilder ev) throws Exception {
        button(k, "tableeditor.apply");
        previewEnter(k);
        a.awaitLastProposal();
        a.waitIdle();
        String outcome = fromEdt(e::outcomeText);
        assertTrue(outcome.contains("verificato sul server"), "creazione di " + table + ": " + outcome.replace('\n', ' '));
        ev.append("   ").append(table).append(": creata e verificata sul server\n");
    }

    private static it.ramasql.app.grid.DataGrid openTable(Keys k, ClientApp a, String catalog, String table) {
        navTo(k, a, catalog, table);
        menu(k, "nav.menu.openTable");
        waitUntil("griglia di " + table, ClientApp.TIMEOUT,
                () -> a.frame().tabs().selected() instanceof it.ramasql.app.grid.DataGrid g && !g.isLoading()
                        && g.tableDef() != null && table.equals(g.tableDef().name()));
        it.ramasql.app.grid.DataGrid g = fromEdt(() -> (it.ramasql.app.grid.DataGrid) a.frame().tabs().selected());
        f6To(k, "la griglia", g);
        k.tabTo("dataGrid.table");
        return g;
    }

    private static void typeRows(Keys k, it.ramasql.app.grid.DataGrid g, List<List<String>> rows) {
        JTable t = fromEdt(g::table);
        for (List<String> r : rows) {
            int row = fromEdt(() -> t.getRowCount() - 1);   // la riga d'inserimento, in fondo
            for (int c = 0; c < r.size(); c++) {
                write(k, t, row, c + 1, r.get(c));
            }
        }
    }

    private static void confirmGrid(Keys k, ClientApp a) throws Exception {
        k.press(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
        previewEnter(k);
        a.awaitLastProposal();
        a.waitIdle();
    }

    private static String authors() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("Autore").append(String.format("%02d", i)).append('\t')
                    .append(i % 3 == 0 ? "Nicolò" : "Nome " + i).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------ prova

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t124_esercitazioneT611SoloDaTastiera(DbServer server) throws Exception {
        String origine = DbServer.newCatalogName("k1a");
        String copia = DbServer.newCatalogName("k1b");
        StringBuilder ev = new StringBuilder("T12.4 — esercitazione T6.11 solo da tastiera, " + server.label() + "\n"
                + "(ogni gesto è un tasto vero consegnato al componente con il fuoco; anteprime e conferme sono le "
                + "finestre vere; restano finte solo le finestre dei file di Windows)\n");
        Keys k = new Keys();
        try (ClientApp a = ClientApp.connect(server, dataDir)) {
            a.ws.realOwner = a::frame;
            onEdt(() -> {
                a.frame().setVisible(true);
                a.frame().toFront();
            });
            waitUntil("finestra attiva", ClientApp.TIMEOUT, () -> a.frame().isActive());

            // 1) il catalogo
            newCatalog(k, a, server, origine);
            ev.append("1) catalogo ").append(origine).append(": F6 fino al navigatore, Maiusc+F10 sul server, ")
                    .append("«Nuovo catalogo…», nome, Invio, Invio nell'anteprima (").append(k.presses)
                    .append(" tasti finora)\n");

            // 2) le tabelle, gli indici e le chiavi esterne
            ev.append("2) tabelle dall'editor (Maiusc+F10 sul catalogo → «Nuova tabella»; Tab, frecce, F2, Spazio, ")
                    .append("Alt+Giù nelle liste):\n");
            TableEditor e = newTable(k, a, origine, "editori");
            columns(k, e, List.of(new Col("id", "INT", null, true, true, true, true),
                    new Col("nome", "VARCHAR", "80", true, false, false, false),
                    new Col("citta", "VARCHAR", "60", false, false, false, false)));
            index(k, e, "uq_editori_nome", "UNIQUE", "nome");
            apply(k, a, e, "editori", ev);
            e = newTable(k, a, origine, "autori");
            columns(k, e, List.of(new Col("id", "INT", null, true, true, true, true),
                    new Col("cognome", "VARCHAR", "60", true, false, false, false),
                    new Col("nome", "VARCHAR", "60", false, false, false, false)));
            apply(k, a, e, "autori", ev);
            e = newTable(k, a, origine, "libri");
            columns(k, e, List.of(new Col("id", "INT", null, true, true, true, true),
                    new Col("titolo", "VARCHAR", "150", true, false, false, false),
                    new Col("isbn", "CHAR", "10", false, false, false, false),
                    new Col("id_editore", "INT", null, false, true, false, false)));
            index(k, e, "uq_libri_isbn", "UNIQUE", "isbn");
            foreignKey(k, e, "fk_libri_editori", "editori", "id_editore", "id", "RESTRICT");
            apply(k, a, e, "libri", ev);
            e = newTable(k, a, origine, "libri_autori");
            columns(k, e, List.of(new Col("id_libro", "INT", null, true, true, true, false),
                    new Col("id_autore", "INT", null, true, true, true, false)));
            foreignKey(k, e, "fk_libri_autori_libri", "libri", "id_libro", "id", "CASCADE");
            foreignKey(k, e, "fk_libri_autori_autori", "autori", "id_autore", "id", "CASCADE");
            apply(k, a, e, "libri_autori", ev);
            assertEquals("3", server.scalar("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS"
                    + " WHERE CONSTRAINT_SCHEMA = '" + origine + "'"), "3 chiavi esterne create da tastiera");
            ev.append("   (").append(k.presses).append(" tasti finora)\n");

            // 3) i dati: digitati e incollati (Ctrl+V) nella griglia, confermati con Ctrl+S
            it.ramasql.app.grid.DataGrid g = openTable(k, a, origine, "editori");
            typeRows(k, g, List.of(List.of("Einaudi", "Torino"), List.of("Adelphi", "Milano"),
                    List.of("Sellerio", "Palermo")));
            confirmGrid(k, a);
            assertEquals("3", server.scalar("SELECT COUNT(*) FROM `" + origine + "`.`editori`"));
            g = openTable(k, a, origine, "autori");
            java.awt.datatransfer.Clipboard appunti = new java.awt.datatransfer.Clipboard("excel");
            appunti.setContents(new java.awt.datatransfer.StringSelection(authors()), null);
            it.ramasql.app.grid.DataGrid ga = g;
            onEdt(() -> ga.setClipboard(appunti));   // le righe copiate da Excel (fuori dal programma)
            JTable ta = fromEdt(g::table);
            cell(k, ta, fromEdt(() -> ta.getRowCount() - 1), 1);
            k.press(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK);
            confirmGrid(k, a);
            assertEquals("20", server.scalar("SELECT COUNT(*) FROM `" + origine + "`.`autori`"), "20 autori incollati");
            g = openTable(k, a, origine, "libri");
            typeRows(k, g, List.of(List.of("Se questo è un uomo", "9788806001", "1"),
                    List.of("Il deserto dei Tartari", "9788845902", "2"),
                    List.of("Il birraio di Preston", "9788838903", "3")));
            confirmGrid(k, a);
            assertEquals("3", server.scalar("SELECT COUNT(*) FROM `" + origine + "`.`libri`"));
            ev.append("3) dati: 3 editori e 3 libri digitati, 20 autori incollati con Ctrl+V, Ctrl+S e Invio ")
                    .append("nell'anteprima (").append(k.presses).append(" tasti finora)\n");

            // 4) il registro esportato: F6 fino al pannello SQL, linguetta Registro, Tab su «Esporta…», Spazio
            Path file = dataDir.resolve("registro-" + server.id() + ".sql");
            a.ws.nextExport = FakeWorkspacePrompts.export(file, origine);
            f6To(k, "il pannello SQL", a.panel());
            k.tabTo("le linguette del pannello", c -> c == a.panel(), true);
            k.times(KeyEvent.VK_LEFT, 3);
            button(k, "panel.log.export");
            waitUntil("registro esportato", ClientApp.TIMEOUT, () -> java.nio.file.Files.exists(file));
            String script = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(script.contains("CREATE TABLE") && script.contains("INSERT INTO"), "registro con tabelle e dati");
            ev.append("4) registro esportato senza il nome del catalogo: ").append(script.lines().count())
                    .append(" righe\n");

            // 5) il secondo catalogo e la riesecuzione: Importa → Esegui script SQL…, catalogo, Esegui
            newCatalog(k, a, server, copia);
            f6To(k, "la barra degli strumenti", fromEdt(() -> a.frame().button("connect").getParent()));
            for (int i = 0; i < 12 && !Keys.named(Keys.owner(), "toolbar.import"); i++) {
                k.press(KeyEvent.VK_RIGHT);
            }
            assertTrue(Keys.named(Keys.owner(), "toolbar.import"), "con le frecce si arriva a «Importa»");
            k.press(KeyEvent.VK_SPACE);
            waitUntil("menu Importa", 5_000,
                    () -> javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 0);
            for (int i = 0; i < 4; i++) {
                javax.swing.MenuElement[] path = fromEdt(
                        () -> javax.swing.MenuSelectionManager.defaultManager().getSelectedPath());
                if (path[path.length - 1] instanceof javax.swing.JMenuItem m && "import.menu.script".equals(m.getName())) {
                    break;
                }
                k.press(KeyEvent.VK_DOWN);
            }
            k.press(KeyEvent.VK_ENTER);
            waitUntil("scheda Esegui script", ClientApp.TIMEOUT,
                    () -> a.frame().tabs().selected() instanceof it.ramasql.app.dump.ScriptRunTab);
            it.ramasql.app.dump.ScriptRunTab st = fromEdt(() -> (it.ramasql.app.dump.ScriptRunTab) a.frame().tabs()
                    .selected());
            a.ws.filesToOpen.put(it.ramasql.app.workspace.FilePrompts.Purpose.RUN_SCRIPT, file);
            f6To(k, "la scheda", st);
            button(k, "script.file.choose");
            DumpUiSupport.awaitScan(st);
            javax.swing.JComboBox<?> target = (javax.swing.JComboBox<?>) k.tabTo("script.target");
            pick(k, target, copia);
            button(k, "script.run");
            previewEnter(k);
            waitUntil("script finito", 300_000, () -> !st.isRunning() && st.preview() != null);
            a.waitIdle();
            ev.append("5) catalogo ").append(copia).append(" creato; «Importa» → «Esegui script SQL…» dalla barra ")
                    .append("(F6, frecce, Spazio, Giù, Invio), file, catalogo di destinazione, «Esegui», Invio\n");

            // 6) i due cataloghi sono identici
            List<String> confronti = new java.util.ArrayList<>();
            for (String t : List.of("editori", "autori", "libri", "libri_autori")) {
                String c1 = server.rows("CHECKSUM TABLE `" + origine + "`.`" + t + "`").get(0).get(1);
                String c2 = server.rows("CHECKSUM TABLE `" + copia + "`.`" + t + "`").get(0).get(1);
                assertEquals(c1, c2, "CHECKSUM diverso per " + t);
                String s1 = server.rows("SHOW CREATE TABLE `" + origine + "`.`" + t + "`").get(0).get(1);
                String s2 = server.rows("SHOW CREATE TABLE `" + copia + "`.`" + t + "`").get(0).get(1);
                assertEquals(s1, s2, "struttura diversa per " + t);
                confronti.add(t + ": stessa struttura (SHOW CREATE TABLE), CHECKSUM " + c1);
            }
            ev.append("6) confronto:\n   ").append(String.join("\n   ", confronti)).append('\n');
            onEdt(() -> Probe.paintWindow("step12", a.frame(), "T12.4-tastiera-" + server.id() + ".png"));
            onEdt(() -> a.frame().setVisible(false));
            ev.append("Tasti premuti in tutto: ").append(k.presses).append(" (nessun clic del mouse)\nEsito: SUPERATO\n");
        } catch (Throwable t) {
            ev.append("Fuoco al momento dell'errore: ").append(Keys.describe(Keys.owner())).append('\n')
                    .append("Ultimi tasti: ").append(k.log.subList(Math.max(0, k.log.size() - 20), k.log.size()))
                    .append("\nEsito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step12", "T12.4-tastiera-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(origine);
            server.dropQuietly(copia);
        }
    }
}
