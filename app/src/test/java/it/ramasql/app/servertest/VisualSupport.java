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

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.TreePath;

import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.visual.VisualQueryTab;
import it.ramasql.core.exec.ResultTable;
import it.ramasql.core.exec.ScriptResult;

/** Aiutanti dei test della query visiva (Step 7-8) contro i server veri, sul programma vero. */
final class VisualSupport {

    private VisualSupport() {
    }

    /** Seleziona il catalogo nel navigatore e preme «Nuova query visiva» nella barra, come l'utente. */
    static VisualQueryTab openFromToolbar(ClientApp a, String catalog) {
        TreePath path = a.node(NavNode.Kind.CATALOG, catalog, null);
        onEdt(() -> a.nav().tree().setSelectionPath(path));
        Probe.waitUntil("«Nuova query visiva» accesa", ClientApp.TIMEOUT,
                () -> a.frame().button("newVisualQuery").isEnabled());
        onEdt(() -> a.frame().button("newVisualQuery").doClick());
        VisualQueryTab tab = fromEdt(() -> a.frame().tabs().selectedVisualQuery());
        if (tab == null) {
            throw new AssertionError("il pulsante non ha aperto la scheda «Query visiva»");
        }
        a.waitIdle();
        return tab;
    }

    /** Il testo SQL che la vista SQL mostra adesso (aspetta la notifica del diagramma, che arriva in coda sull'EDT). */
    static String textView(VisualQueryTab tab) {
        onEdt(() -> { });   // lascia passare la notifica in coda
        onEdt(() -> { });
        return fromEdt(() -> tab.editor().getText());
    }

    /** Esegue la query della scheda (anteprima confermata dalla finta) e aspetta l'esito mostrato. */
    static ScriptResult run(ClientApp a, VisualQueryTab tab) throws Exception {
        onEdt(tab::run);
        Probe.waitUntil("query visiva eseguita", 60_000, () -> !tab.isRunning());
        a.waitIdle();
        return a.workspace().pipeline().lastProposal().get();
    }

    /** Le righe di un risultato, valori come testo ({@code null} = NULL). */
    static List<List<String>> rows(ResultTable t) {
        List<List<String>> out = new ArrayList<>();
        for (int r = 0; r < t.rowCount(); r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < t.columnCount(); c++) {
                Object v = t.value(r, c);
                row.add(v == null ? null : String.valueOf(v));
            }
            out.add(row);
        }
        return out;
    }
}
