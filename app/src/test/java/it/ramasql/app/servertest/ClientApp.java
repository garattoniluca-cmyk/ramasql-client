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

import java.awt.Component;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.tree.TreePath;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.navigator.NavigatorPanel;
import it.ramasql.app.sqlpanel.SqlPanel;
import it.ramasql.app.workspace.SessionWorkspace;
import it.ramasql.core.connection.ProfileStore;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlLog;

/** Il programma vero (finestra mai mostrata), connesso a un server di test dalla sua tessera. */
public final class ClientApp implements AutoCloseable {

    public static final long TIMEOUT = 30_000;

    public final DbServer server;
    public final App app;
    public final QuietPrompts prompts;
    public final FakeWorkspacePrompts ws;

    private ClientApp(DbServer server, App app, QuietPrompts prompts, FakeWorkspacePrompts ws) {
        this.server = server;
        this.app = app;
        this.prompts = prompts;
        this.ws = ws;
    }

    /** Avvia il programma e si connette (clic sulla tessera, password dal test); aspetta i cataloghi nel navigatore. */
    public static ClientApp connect(DbServer server, Path dataDir) throws Exception {
        ProfileStore store = new ProfileStore(dataDir);
        store.add(server.profile());
        QuietPrompts prompts = new QuietPrompts(server);
        FakeWorkspacePrompts ws = new FakeWorkspacePrompts();
        App app = fromEdt(() -> App.create(dataDir, prompts, ws));
        ClientApp a = new ClientApp(server, app, prompts, ws);
        onEdt(() -> app.frame().homePanel().profileTiles().get(0).doClick());
        waitUntil("connesso a " + server.label(), TIMEOUT, () -> app.frame().screen() == MainFrame.Screen.WORKSPACE);
        a.waitIdle();
        waitUntil("cataloghi nel navigatore", TIMEOUT,
                () -> a.nav().find(NavNode.Kind.CATALOG, null, null) != null);
        return a;
    }

    public MainFrame frame() {
        return app.frame();
    }

    public NavigatorPanel nav() {
        return app.frame().navigator();
    }

    public SqlPanel panel() {
        return app.frame().sqlPanel();
    }

    public SessionWorkspace workspace() {
        return app.frame().workspace();
    }

    public SqlLog log() {
        return app.frame().sqlLog();
    }

    /** Navigatore senza letture in corso e nessun esito d'esecuzione in sospeso. */
    public void waitIdle() {
        waitUntil("navigatore e pipeline a riposo", TIMEOUT, () -> nav().isIdle()
                && (workspace() == null || !workspace().pipeline().isBusy()));
    }

    /** Il nodo, aspettando che compaia. */
    public TreePath node(NavNode.Kind kind, String catalog, String name) {
        waitUntil("nodo " + kind + " " + catalog + "." + name, TIMEOUT, () -> nav().find(kind, catalog, name) != null);
        return fromEdt(() -> nav().find(kind, catalog, name));
    }

    /** Apre il nodo e aspetta che i suoi figli siano letti (nessun «caricamento…»). */
    public TreePath expand(NavNode.Kind kind, String catalog, String name) {
        node(kind, catalog, name);
        onEdt(() -> nav().tree().expandPath(nav().find(kind, catalog, name)));
        waitUntil("figli letti di " + kind + " " + catalog + "." + name, TIMEOUT, () -> {
            if (!nav().isIdle()) {
                return false;
            }
            TreePath p = nav().find(kind, catalog, name);
            return p != null && nav().tree().isExpanded(p)
                    && nav().childrenOf(p).stream().noneMatch(n -> n.kind() == NavNode.Kind.LOADING);
        });
        return fromEdt(() -> nav().find(kind, catalog, name));
    }

    /** Clic su una voce del menu contestuale del nodo. */
    public void menu(NavNode.Kind kind, String catalog, String name, String itemName) {
        node(kind, catalog, name);
        onEdt(() -> {
            TreePath path = nav().find(kind, catalog, name);
            nav().tree().setSelectionPath(path);
            JPopupMenu menu = nav().menuFor(path);
            menuItem(menu, itemName).doClick();
        });
    }

    public static JMenuItem menuItem(JPopupMenu menu, String name) {
        if (menu == null) {
            throw new AssertionError("nessun menu contestuale");
        }
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && name.equals(item.getName())) {
                return item;
            }
        }
        throw new AssertionError("voce di menu assente: " + name);
    }

    /** Aspetta l'esito dell'ultima proposta (null se annullata o copiata). */
    public ScriptResult awaitLastProposal() throws Exception {
        CompletableFuture<ScriptResult> f = fromEdt(() -> workspace().pipeline().lastProposal());
        ScriptResult r = f.get(TIMEOUT, TimeUnit.MILLISECONDS);
        waitIdle();
        return r;
    }

    @Override
    public void close() {
        onEdt(() -> {
            app.connections().disconnect();   // chiude anche esecutore e navigatore della sessione
            app.connections().shutdown();
            app.frame().dispose();
        });
    }
}
