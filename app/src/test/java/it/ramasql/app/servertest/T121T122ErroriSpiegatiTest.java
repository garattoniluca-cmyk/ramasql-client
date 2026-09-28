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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.text.JTextComponent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.App;
import it.ramasql.app.MainFrame;
import it.ramasql.app.connection.ConnectionErrorDialog;
import it.ramasql.app.editor.ErrorExplainer;
import it.ramasql.app.editor.SqlEditor;
import it.ramasql.core.connection.ConnectionFailure;
import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.ProfileStore;

/**
 * <b>T12.1</b>: gli errori frequenti hanno una spiegazione in italiano. <b>T12.2</b>: provocati ciascuno dal
 * <b>programma vero</b> sui due server — dall'editor SQL (1064, 1146, 1054, 1062, 1452, 1451, 1049, 1044 e la chiave
 * esterna impossibile: 1005 su MariaDB, 1822 su MySQL 8, dove 1215 non esiste più) e dalla schermata iniziale (1045
 * password sbagliata, 2003 server spento) — l'utente vede il messaggio originale del server e, accanto, la spiegazione.
 */
@Tag("step12")
class T121T122ErroriSpiegatiTest {

    /** I codici di T12.1, più 1822 (la forma di MySQL 8 dell'errore 1215). */
    static final List<Integer> CODES = List.of(1044, 1045, 1049, 1054, 1062, 1064, 1146, 1215, 1005, 1822, 1451, 1452,
            2003);

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @Test
    void t121_ogniErroreFrequenteHaLaSuaSpiegazione() {
        List<String> missing = new ArrayList<>();
        for (int code : CODES) {
            String text = ErrorExplainer.explain(code).orElse("");
            if (text.length() < 40 || !text.contains(" ")) {
                missing.add(code + ": «" + text + "»");
            }
        }
        assertTrue(missing.isEmpty(), "spiegazioni mancanti o troppo corte: " + missing);
        Probe.writeText("step12", "T12.1-spiegazioni.txt", "T12.1 — spiegazioni in italiano degli errori frequenti\n"
                + String.join("\n", CODES.stream().map(c -> c + ": " + ErrorExplainer.explain(c).orElseThrow()).toList())
                + "\nEsito: OK\n");
    }

    private static List<String> texts(Container c) {
        List<String> out = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof JLabel l && l.getText() != null) {
                out.add(l.getText());
            } else if (child instanceof JTextComponent t) {
                out.add(t.getText());
            }
            if (child instanceof Container k) {
                out.addAll(texts(k));
            }
        }
        return out;
    }

    /** Una statement che deve fallire con uno dei codici dati. */
    private record Case(Set<Integer> codes, String sql) {
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    @Tag("ui")
    @Tag("it")
    void t122_erroriProvocatiDalProgramma(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t122");
        StringBuilder ev = new StringBuilder("T12.2 — errori provocati dal programma (" + server.label() + ")\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            String socioConPrestiti = server.scalar("SELECT id_socio FROM `" + catalog + "`.prestiti LIMIT 1");
            String q = "`" + catalog + "`.";
            List<Case> cases = List.of(
                    new Case(Set.of(1064), "SELEC * FROM " + q + "soci"),
                    new Case(Set.of(1146), "SELECT * FROM " + q + "tabella_che_non_esiste"),
                    new Case(Set.of(1054), "SELECT colonna_che_non_esiste FROM " + q + "soci"),
                    new Case(Set.of(1062), "INSERT INTO " + q + "soci (id, tessera, cognome, nome) VALUES (1, 'X0000001',"
                            + " 'Rossi', 'Anna')"),
                    new Case(Set.of(1452), "INSERT INTO " + q + "prestiti (id_libro, id_socio, data_prestito) VALUES"
                            + " (999999, 1, '2026-01-01')"),
                    new Case(Set.of(1451), "DELETE FROM " + q + "soci WHERE id = " + socioConPrestiti),
                    new Case(Set.of(1049), "USE ramasql_test_non_esiste_mai"),
                    new Case(Set.of(1044), "CREATE TABLE information_schema.prova (a INT)"),
                    new Case(Set.of(1215, 1005, 1822), "CREATE TABLE " + q + "figlia (id INT PRIMARY KEY, cognome"
                            + " VARCHAR(60), FOREIGN KEY (cognome) REFERENCES " + q + "soci (cognome))"));
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> {
                    if (d.requiresTypedConfirmation()) {
                        d.confirmationField().setText(d.confirmation().typeToConfirm());
                    }
                    d.executeButton().doClick();
                };
                SqlEditor editor = fromEdt(a.frame()::openSqlEditor);
                for (Case c : cases) {
                    onEdt(() -> editor.setText(c.sql() + ";"));
                    onEdt(editor::runAll);
                    waitUntil("esecuzione di " + c.sql(), 30_000, () -> !editor.isRunning());
                    a.waitIdle();
                    String error = fromEdt(() -> editor.results().errorText());
                    int code = c.codes().stream().filter(k -> error.contains(String.valueOf(k))).findFirst().orElse(-1);
                    assertTrue(code > 0, c.sql() + " → atteso " + c.codes() + ": " + error);
                    String explanation = ErrorExplainer.explain(code).orElseThrow();
                    assertTrue(error.contains(explanation), "spiegazione accanto al messaggio del server: " + error);
                    ev.append("[").append(code).append("] ").append(c.sql()).append("\n  → ")
                            .append(error.replace('\n', ' ')).append('\n');
                }
                Probe.paintWindow("step12", a.frame(), "T12.2-editor-" + server.id() + ".png");
            }
            // dalla schermata iniziale: password sbagliata (1045) e server irraggiungibile (2003)
            for (String what : List.of("password", "porta")) {
                Path dir = dataDir.resolve(what);
                java.nio.file.Files.createDirectories(dir);
                ProfileStore store = new ProfileStore(dir);
                ConnectionProfile p = server.profile();
                ConnectionProfile profile = what.equals("porta")
                        ? ConnectionProfile.create("Spento", p.host(), 3399, p.user(), "", "") : p;
                store.add(profile);
                QuietPrompts prompts = new QuietPrompts(server);
                if (what.equals("password")) {
                    prompts.passwordOverride = "password-sbagliata".toCharArray();
                }
                FakeWorkspacePrompts ws = new FakeWorkspacePrompts();
                App app = fromEdt(() -> App.create(dir, prompts, ws));
                try {
                    onEdt(() -> app.frame().homePanel().profileTiles().get(0).doClick());
                    waitUntil("errore di connessione", 30_000, () -> !prompts.failures.isEmpty());
                    ConnectionFailure f = prompts.failures.get(0);
                    int expected = what.equals("password") ? 1045 : 2003;
                    ConnectionErrorDialog d = fromEdt(() -> new ConnectionErrorDialog(app.frame(), profile, f));
                    try {
                        List<String> shown = fromEdt(() -> texts(d.getContentPane()));
                        String all = String.join(" | ", shown);
                        assertTrue(all.contains(f.message()), "spiegazione: " + all);
                        assertTrue(all.contains(f.originalText()) || all.contains(f.originalDetail()),
                                "messaggio originale: " + all);
                        if (expected == 1045) {
                            assertTrue(f.errorCode() == 1045 && all.contains("1045"), all);
                        } else {
                            assertTrue(f.cause().name().equals("PORT_CLOSED"), f.toString());
                        }
                        assertFalse(fromEdt(() -> app.frame().screen() == MainFrame.Screen.WORKSPACE));
                        Probe.paintWindow("step12", d, "T12.2-connessione-" + what + "-" + server.id() + ".png");
                        ev.append("[").append(expected).append("] connessione (").append(what).append(") → finestra: «")
                                .append(all.replace('\n', ' ')).append("»\n");
                    } finally {
                        onEdt(d::dispose);
                    }
                } finally {
                    onEdt(() -> {
                        app.connections().shutdown();
                        app.frame().dispose();
                    });
                }
            }
            ev.append("Esito: OK\n");
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step12", "T12.2-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }
}
