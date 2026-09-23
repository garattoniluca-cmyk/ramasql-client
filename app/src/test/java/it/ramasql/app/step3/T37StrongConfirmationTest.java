/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.step3;

import static it.ramasql.app.step3.Step3Ui.fromEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.ScriptResult;

/**
 * <b>T3.7</b> — {@code DROP} e {@code TRUNCATE} chiedono la <b>conferma rafforzata</b>: nell'anteprima <em>Esegui</em>
 * è disabilitato (e non è il pulsante predefinito) finché il nome dell'oggetto non è riscritto esatto; con un nome
 * sbagliato (anche solo nelle maiuscole) premere Esegui non fa nulla e sul server non cambia niente. Una rinomina
 * (non distruttiva) non chiede di riscrivere nulla. Infine, con il nome esatto, lo svuotamento avviene davvero.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class T37StrongConfirmationTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Step3Ui.setup();
    }

    @ParameterizedTest
    @EnumSource(Step3Server.class)
    void t37_dropETruncateRichiedonoIlNomeRiscrittoEsatto(Step3Server server) throws Exception {
        String catalog = Step3Server.newCatalogName("forte");
        StringBuilder ev = new StringBuilder("T3.7 — conferma rafforzata, su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            long prestiti = server.rowCount(catalog, "prestiti");
            try (Step3App a = Step3App.connect(server, dataDir)) {
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                a.expand(NavNode.Kind.TABLES, catalog, null);
                List<String> observed = new ArrayList<>();

                // nome sbagliato → Esegui disabilitato, il clic non fa nulla → Annulla
                a.ws.onPreview = d -> {
                    String name = d.confirmation().typeToConfirm();
                    observed.add(d.sqlText() + " → livello " + d.confirmation().level() + ", da riscrivere «" + name + "»");
                    assertTrue(d.requiresTypedConfirmation());
                    assertTrue(d.confirmationField().isShowing() || d.confirmationField().getParent() != null,
                            "il campo della conferma è nella finestra");
                    assertFalse(d.executeButton().isEnabled(), "Esegui disabilitato a campo vuoto");
                    assertTrue(d.getRootPane().getDefaultButton() != d.executeButton(), "Invio non conferma");
                    for (String wrong : List.of(name.substring(0, name.length() - 1), name.toUpperCase(), name + "x",
                            "CONFERMO")) {
                        d.confirmationField().setText(wrong);
                        assertFalse(d.executeButton().isEnabled(), "Esegui abilitato con «" + wrong + "»");
                        d.executeButton().doClick();
                        assertEquals(PreviewDialog.Decision.CANCEL, d.decision(), "clic su Esegui disabilitato ignorato");
                        observed.add("   «" + wrong + "» → Esegui disabilitato, clic ignorato");
                    }
                    d.confirmationField().setText(name.substring(0, 3));
                    if (server == Step3Server.MARIADB && observed.size() < 7) {
                        Step3Ui.paintWindow(d, "conferma-rafforzata.png");
                    }
                    Step3Ui.paintWindow(d, "T3.7-" + (name.startsWith("ramasql_test_") ? "catalogo" : name) + "-"
                            + server.id() + ".png");
                    d.confirmationField().setText(name);
                    assertTrue(d.executeButton().isEnabled(), "con il nome esatto Esegui si abilita");
                    observed.add("   «" + name + "» → Esegui abilitato; l'utente preme comunque Annulla");
                    d.cancelButton().doClick();
                };
                a.menu(NavNode.Kind.TABLE, catalog, "libri", "nav.menu.dropTable");
                assertNull(a.awaitLastProposal());
                a.menu(NavNode.Kind.TABLE, catalog, "prestiti", "nav.menu.truncate");
                assertNull(a.awaitLastProposal());
                a.menu(NavNode.Kind.CATALOG, catalog, catalog, "nav.menu.dropCatalog");
                assertNull(a.awaitLastProposal());
                assertEquals(3, a.ws.previews.size());
                for (FakeWorkspacePrompts.Shown s : a.ws.previews) {
                    assertEquals(ConfirmationPolicy.Level.STRONG, s.confirmation().level(), s.sqlInDialog());
                    assertEquals(PreviewDialog.Decision.CANCEL, s.decision());
                }
                assertEquals("libri", a.ws.previews.get(0).confirmation().typeToConfirm());
                assertEquals("prestiti", a.ws.previews.get(1).confirmation().typeToConfirm());
                assertEquals(catalog, a.ws.previews.get(2).confirmation().typeToConfirm());
                // nulla è stato eseguito
                assertEquals(0, fromEdt(() -> a.log().size()));
                assertTrue(server.catalogExists(catalog));
                assertTrue(server.tableExists(catalog, "libri"));
                assertEquals(prestiti, server.rowCount(catalog, "prestiti"));
                ev.append(String.join("\n", observed)).append('\n');
                ev.append("Dopo tre anteprime annullate: registro 0 istruzioni; server: catalogo e libri presenti, prestiti ")
                        .append(prestiti).append(" righe (invariato)\n");

                // una rinomina non è distruttiva: nessun campo da riscrivere, Esegui subito abilitato e predefinito
                a.ws.onRename = current -> "libri_nuovi";
                a.ws.onPreview = d -> {
                    assertFalse(d.requiresTypedConfirmation());
                    assertTrue(d.executeButton().isEnabled());
                    assertSame(d.executeButton(), d.getRootPane().getDefaultButton());
                    d.cancelButton().doClick();
                };
                a.menu(NavNode.Kind.TABLE, catalog, "libri", "nav.menu.rename");
                assertNull(a.awaitLastProposal());
                assertEquals(ConfirmationPolicy.Level.CONFIRM, a.ws.previews.get(3).confirmation().level());
                ev.append("Rinomina (non distruttiva): livello CONFIRM, nessun nome da riscrivere, Esegui predefinito\n");

                // con il nome esatto lo svuotamento avviene
                a.ws.onPreview = d -> {
                    d.confirmationField().setText(d.confirmation().typeToConfirm());
                    d.executeButton().doClick();
                };
                a.menu(NavNode.Kind.TABLE, catalog, "prestiti", "nav.menu.truncate");
                ScriptResult r = a.awaitLastProposal();
                assertTrue(r != null && r.completed());
                assertEquals(0, server.rowCount(catalog, "prestiti"));
                assertEquals(1, fromEdt(() -> a.log().size()));
                ev.append("Svuota prestiti con «prestiti» riscritto: eseguito, registro 1 istruzione, server 0 righe\n");
                ev.append("Schermate: T3.7-libri/prestiti/catalogo-").append(server.id())
                        .append(".png").append(server == Step3Server.MARIADB ? ", conferma-rafforzata.png" : "")
                        .append('\n');
            }
        } finally {
            server.dropQuietly(catalog);
        }
        Step3Ui.writeText("T3.7-" + server.id() + ".txt", ev.toString());
    }
}
