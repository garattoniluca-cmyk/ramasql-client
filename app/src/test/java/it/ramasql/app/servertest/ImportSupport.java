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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import javax.swing.JMenuItem;
import javax.swing.tree.TreePath;

import it.ramasql.app.importer.ImportWizard;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.workspace.FilePrompts;

/** Come si usa la procedura guidata «Importa dati» dai test, passando dai punti dell'interfaccia dell'utente. */
final class ImportSupport {

    private ImportSupport() {
    }

    /** Disegna la scheda dopo averne sistemato tutto il contenuto (griglie comprese). */
    static void paint(ImportWizard w, String fileName) {
        onEdt(() -> {
            java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(w);
            if (window != null) {
                window.validate();   // una scheda appena aggiunta non ha ancora una misura
            }
            w.validate();
            layoutAll(w);
        });
        Probe.paint("step9", w, fileName);
    }

    private static void layoutAll(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container k) {
                layoutAll(k);
            }
        }
    }

    static Path fixture(String name) {
        return Probe.projectRoot().resolve("it-tests/fixtures/import").resolve(name);
    }

    /** Seleziona il catalogo nel navigatore, poi «Importa» della barra → «Importa dati da un file CSV o JSON…». */
    static ImportWizard fromToolbar(ClientApp a, String catalog) {
        TreePath node = a.node(NavNode.Kind.CATALOG, catalog, catalog);
        onEdt(() -> a.nav().tree().setSelectionPath(node));
        waitUntil("«Importa» abilitato", ClientApp.TIMEOUT, () -> a.frame().button("import").isEnabled());
        onEdt(() -> {
            JMenuItem item = ClientApp.menuItem(a.frame().importMenu(), "import.menu.data");
            item.doClick();
        });
        return current(a);
    }

    /** Tasto destro sulla tabella nel navigatore → «Importa dati…». */
    static ImportWizard fromTableMenu(ClientApp a, String catalog, String table) {
        a.expand(NavNode.Kind.CATALOG, catalog, catalog);
        a.expand(NavNode.Kind.TABLES, catalog, null);
        a.menu(NavNode.Kind.TABLE, catalog, table, "nav.menu.importData");
        return current(a);
    }

    private static ImportWizard current(ClientApp a) {
        ImportWizard w = fromEdt(() -> a.frame().tabs().selected() instanceof ImportWizard iw ? iw : null);
        assertTrue(w != null, "la scheda «Importa dati» è davanti");
        return w;
    }

    /** «Scegli…» con il file dato (finestra dei file finta), aspettando il riconoscimento del formato. */
    static void choose(ClientApp a, ImportWizard w, Path file) {
        a.ws.filesToOpen.put(FilePrompts.Purpose.IMPORT_DATA, file);
        onEdt(w::chooseFile);
        waitUntil("formato riconosciuto", ClientApp.TIMEOUT,
                () -> w.file() != null && w.file().path().equals(file) && w.nextButton().isEnabled());
    }

    /** «Avanti» dal passo 1 e attesa dell'analisi del file. */
    static void toPreview(ImportWizard w) {
        onEdt(() -> w.nextButton().doClick());
        waitUntil("file letto", 120_000, () -> !w.isAnalyzing() && (w.analysis() != null
                || !w.previewBanner().text().isEmpty()));
    }

    /** «Avanti» al passo 3 e attesa dell'abbinamento (tabella esistente) o della tabella nuova. */
    static void toTarget(ImportWizard w) {
        onEdt(() -> w.nextButton().doClick());
        waitUntil("passo 3 pronto", ClientApp.TIMEOUT, () -> w.newRadio().isSelected()
                ? w.newColumnsTable().getRowCount() > 0
                : w.mappingTable().getRowCount() == w.analysis().columns().size());
    }

    /** Dal passo 3 «Avanti» fino al passo 5, poi «Importa» e attesa del rapporto. */
    static void runToEnd(ClientApp a, ImportWizard w) {
        onEdt(() -> w.nextButton().doClick());   // → opzioni
        onEdt(() -> w.nextButton().doClick());   // → importa
        startAndWait(a, w);
    }

    static void startAndWait(ClientApp a, ImportWizard w) {
        onEdt(() -> w.nextButton().doClick());   // «Importa»: anteprima, poi esecuzione
        waitUntil("importazione finita", 300_000, () -> !w.isRunning()
                && (w.report() != null || !w.resultBanner().text().isEmpty() || !w.progressLabel().getText().isBlank()));
        a.waitIdle();
    }
}
