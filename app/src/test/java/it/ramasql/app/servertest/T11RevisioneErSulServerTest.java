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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JMenuItem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.er.ErCanvas;
import it.ramasql.app.er.ErModelPanel;
import it.ramasql.app.er.ErModelWindow;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.app.workspace.WorkspacePrompts.PendingChoice;
import it.ramasql.model.ErModel;
import it.ramasql.model.ModelFile;
import it.ramasql.model.Relationship;

/**
 * Le correzioni della revisione dello Step 11, sul <b>programma vero</b>: uscendo dal programma i modelli non salvati
 * non si perdono («Resta» resta davvero, «Salva» salva e poi chiude); lo stesso file non si apre due volte; Ctrl+rotella
 * sul diagramma fa lo zoom e non cambia il carattere del programma; un modello non si aggiorna da un altro server;
 * etichetta e N:M di una relazione logica si salvano nel file.
 */
@Tag("step11")
@Tag("ui")
@Tag("it")
class T11RevisioneErSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    private static ErModelWindow newModel(ClientApp a, String catalog) {
        int before = fromEdt(() -> a.frame().erWindows().size());
        a.node(NavNode.Kind.CATALOG, catalog, catalog);
        onEdt(() -> a.nav().tree().setSelectionPath(a.nav().find(NavNode.Kind.CATALOG, catalog, catalog)));
        waitUntil("«Modello ER» abilitato", ClientApp.TIMEOUT, () -> a.frame().button("erModel").isEnabled());
        onEdt(() -> ClientApp.menuItem(a.frame().erMenu(), "er.menu.new").doClick());
        waitUntil("modello letto", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                && a.frame().erWindows().size() == before + 1);
        return fromEdt(() -> a.frame().erWindows().get(a.frame().erWindows().size() - 1));
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void uscireNonPerdeIModelliEFileApertoUnaVolta(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t11esci");
        StringBuilder ev = new StringBuilder("Step 11, revisione — uscita dal programma con un modello modificato ("
                + server.label() + ")\n");
        Path file = dataDir.resolve("uscita.rsqlmodel");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            ClientApp a = ClientApp.connect(server, dataDir);
            try {
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                ErModelWindow w = newModel(a, catalog);
                ErModelPanel p = w.panel();
                a.ws.filesToSave.put(FilePrompts.Purpose.MODEL, file);
                onEdt(p::save);
                waitUntil("salvato", ClientApp.TIMEOUT, () -> !p.isBusy() && !p.isModified());
                assertEquals(server.profile().address(), ModelFile.read(file).server(), "il server nel file");
                // lo stesso file riaperto: si porta avanti la finestra che c'è già
                onEdt(() -> a.frame().openErModel(file));
                waitUntil("apertura", ClientApp.TIMEOUT, () -> !a.frame().isErLoading());
                assertEquals(1, (int) fromEdt(() -> a.frame().erWindows().size()), "una sola finestra per file");
                ev.append("Stesso file aperto due volte: una sola finestra\n");
                // una modifica, poi «Esci» con «Resta»
                ErModel.Entity e = fromEdt(() -> p.model().entities().get(0));
                onEdt(() -> p.canvas().moveEntity(e.table(), e.x() + 100, e.y() + 50));
                assertTrue(fromEdt(p::isModified));
                a.ws.onPendingOnClose = q -> PendingChoice.STAY;
                int questions = a.ws.pendingQuestions.size();
                onEdt(() -> a.frame().dispatchEvent(new WindowEvent(a.frame(), WindowEvent.WINDOW_CLOSING)));
                assertEquals(questions + 1, a.ws.pendingQuestions.size(), "chiede del modello");
                assertTrue(fromEdt(w::isDisplayable) && fromEdt(() -> a.frame().isDisplayable()),
                        "«Resta»: né il modello né il programma si chiudono");
                ev.append("Esci con il modello modificato → domanda «").append(a.ws.pendingQuestions.get(questions))
                        .append("»; «Resta»: tutto aperto\n");
                // «Salva»: si salva, la finestra del modello si chiude e l'uscita continua
                a.ws.onPendingOnClose = q -> PendingChoice.CONFIRM;
                ErModel expected = fromEdt(p::model);
                onEdt(() -> a.frame().dispatchEvent(new WindowEvent(a.frame(), WindowEvent.WINDOW_CLOSING)));
                waitUntil("programma chiuso dopo il salvataggio", ClientApp.TIMEOUT, () -> !w.isDisplayable()
                        && !a.frame().isDisplayable());
                assertEquals(expected, ModelFile.read(file), "le modifiche sono nel file");
                ev.append("«Salva»: modello salvato (posizione nuova nel file), finestra del modello chiusa, programma"
                        + " chiuso\nEsito: OK\n");
            } finally {
                a.close();
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step11", "T11-revisione-uscita-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    @Test
    void ctrlRotellaEAltroServerEtichettaENm() throws Exception {
        String catalog = DbServer.newCatalogName("t11srv");
        StringBuilder ev = new StringBuilder("Step 11, revisione — Ctrl+rotella, altro server, etichetta e N:M\n");
        Path file = dataDir.resolve("mariadb.rsqlmodel");
        try {
            DbServer.MARIADB.createCatalog(catalog);
            DbServer.MARIADB.loadFixture(catalog, "biblioteca_myisam.sql");
            // su MySQL un catalogo con lo stesso nome ma altre tabelle
            DbServer.MYSQL.createCatalog(catalog);
            DbServer.MYSQL.run("CREATE TABLE `" + catalog + "`.tutt_altro (id INT PRIMARY KEY)");
            ErModel saved;
            try (ClientApp a = ClientApp.connect(DbServer.MARIADB, dataDir)) {
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                ErModelWindow w = newModel(a, catalog);
                ErModelPanel p = w.panel();
                ErCanvas c = p.canvas();
                // Ctrl+rotella: zoom del diagramma, il carattere del programma non cambia
                onEdt(() -> a.app.settings().installCtrlWheelZoom());
                int font = a.app.settings().settings().fontSize();
                double zoom = fromEdt(c::zoom);
                onEdt(() -> c.dispatchEvent(new MouseWheelEvent(c, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                        InputEvent.CTRL_DOWN_MASK, 50, 50, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1)));
                assertTrue(fromEdt(c::zoom) > zoom, "zoom del diagramma");
                assertEquals(font, a.app.settings().settings().fontSize(), "carattere del programma invariato");
                // cambiando davvero il carattere, le tabelle si rimisurano
                ErModel.Entity libri = fromEdt(() -> c.model().entity("libri").orElseThrow());
                double width = fromEdt(() -> c.measure().of(libri).width());
                onEdt(() -> a.app.settings().changeFontSize(3));
                double wider = fromEdt(() -> c.measure().of(libri).width());
                onEdt(() -> a.app.settings().changeFontSize(-3));
                assertTrue(wider > width, "misure rifatte col carattere nuovo: " + width + " → " + wider);
                ev.append("Ctrl+rotella sul diagramma: zoom ").append(Math.round(zoom * 100)).append("% → ")
                        .append(Math.round(fromEdt(c::zoom) * 100)).append("%, carattere del programma ").append(font)
                        .append(" invariato; con il carattere a +3 la tabella libri passa da ").append(width)
                        .append(" a ").append(wider).append(" di larghezza\n");
                // etichetta e N:M di una relazione logica, dal menu della relazione
                onEdt(p::suggest);
                onEdt(p::acceptChecked);
                Relationship r = fromEdt(() -> p.model().logical().stream()
                        .filter(x -> x.fromTable().equals("libri_autori") && x.toTable().equals("autori")).findFirst()
                        .orElseThrow());
                a.ws.relationshipLabel = "scrive";
                onEdt(() -> ClientApp.menuItem(p.relationshipMenuFor(r), "er.relationship.label").doClick());
                Relationship labelled = fromEdt(() -> p.model().logical().stream().filter(x -> x.id().equals(r.id()))
                        .findFirst().orElseThrow());
                onEdt(() -> {
                    JMenuItem nm = ClientApp.menuItem(p.relationshipMenuFor(labelled),
                            "er.relationship.cardinality.MANY_TO_MANY");
                    nm.doClick();
                });
                a.ws.filesToSave.put(FilePrompts.Purpose.MODEL, file);
                onEdt(p::save);
                waitUntil("salvato", ClientApp.TIMEOUT, () -> !p.isBusy() && !p.isModified());
                saved = ModelFile.read(file);
                Relationship back = saved.logical().stream().filter(x -> x.id().equals(r.id())).findFirst().orElseThrow();
                assertEquals("scrive", back.label());
                assertEquals(Relationship.Cardinality.MANY_TO_MANY, back.cardinality());
                PaintSupport.paint(p, "step11", "T11-revisione-etichetta-nm.png");
                ev.append("Etichetta «scrive» e N:M su ").append(r.describe()).append(" dal menu della relazione:"
                        + " salvate e rilette dal file\n");
                onEdt(w::closeIfAllowed);
            }
            // un altro server con un catalogo omonimo: aggiornare e aprire le tabelle si rifiutano
            // un'altra cartella dei dati: una sola tessera, quella di MySQL
            try (ClientApp a = ClientApp.connect(DbServer.MYSQL, Files.createDirectories(dataDir.resolve("mysql")))) {
                onEdt(() -> a.frame().openErModel(file));
                waitUntil("modello aperto", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                        && !a.frame().erWindows().isEmpty());
                ErModelPanel p = fromEdt(() -> a.frame().erWindows().get(0).panel());
                onEdt(p::refreshFromDatabase);
                String banner = fromEdt(() -> p.banner().text());
                assertTrue(banner.contains(DbServer.MARIADB.profile().address()) && banner.contains(
                        DbServer.MYSQL.profile().address()), banner);
                assertEquals(saved, fromEdt(p::model), "nessuna modifica al modello");
                assertFalse(fromEdt(p::isModified));
                onEdt(() -> p.openTable("libri"));
                String open = fromEdt(() -> p.banner().text());
                assertTrue(open.contains("potrebbe essere un'altra"), open);
                assertFalse(fromEdt(() -> a.frame().tabs().selected()) instanceof it.ramasql.app.tableeditor.TableEditor);
                ev.append("Modello di MariaDB aperto collegati a MySQL (catalogo omonimo, altre tabelle): «Aggiorna» →"
                        + " «").append(banner.replace('\n', ' ')).append("», modello invariato; doppio clic → «")
                        .append(open.replace('\n', ' ')).append("»\n");
                onEdt(() -> a.frame().erWindows().forEach(ErModelWindow::closeIfAllowed));
            }
            ev.append("Esito: OK\n");
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step11", "T11-revisione-server.txt", ev.toString());
            DbServer.MARIADB.dropQuietly(catalog);
            DbServer.MYSQL.dropQuietly(catalog);
        }
    }
}
