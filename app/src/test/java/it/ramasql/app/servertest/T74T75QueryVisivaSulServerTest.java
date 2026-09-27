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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqleo.querybuilder.QbOperations;

import it.ramasql.app.visual.VisualQueryTab;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlOrigin;

/**
 * T7.4 e T7.5 — la scheda «Query visiva» del <b>programma vero</b>, aperta dal pulsante <em>Nuova query visiva</em>
 * della barra, contro MariaDB e MySQL con la fixture {@code biblioteca}:
 * <ul>
 *   <li><b>T7.4</b>: si aggiungono le 5 tabelle {@code libri}, {@code editori}, {@code libri_autori}, {@code autori},
 *       {@code prestiti} (come il doppio clic sull'elenco a sinistra del diagramma): tutte accettate, nessun avviso di
 *       limite, e i join <b>proposti dalle chiavi esterne</b> sono esattamente le 4 FK della fixture tra quelle tabelle
 *       (lette dal server con una connessione separata).</li>
 *   <li><b>T7.5</b>: {@code editori} e {@code libri}; il join diventa «tutte le righe di editori» (LEFT), filtro
 *       {@code anno > 2000}, conteggio {@code COUNT(*)} raggruppato per editore, ordinamento: dopo <b>ogni</b> gesto il
 *       testo della vista SQL è cambiato e contiene il pezzo nuovo. <em>Esegui</em> passa dalla pipeline (anteprima,
 *       registro con origine «Query visiva», {@code USE} del catalogo della scheda) e le righe mostrate sono quelle
 *       che la stessa domanda, scritta a mano, dà sul server interrogato con una connessione separata.</li>
 * </ul>
 */
@Tag("step7")
@Tag("ui")
@Tag("it")
class T74T75QueryVisivaSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t74_cinqueTabelleEJoinDalleChiaviEsterne(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("visual74");
        StringBuilder ev = new StringBuilder("T7.4 — cinque tabelle nella query visiva su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                VisualQueryTab tab = VisualSupport.openFromToolbar(a, catalog);
                var qb = tab.queryBuilder();
                List<String> oggetti = fromEdt(() -> QbOperations.objects(qb));
                assertTrue(oggetti.containsAll(List.of("autori", "editori", "libri", "libri_autori", "prestiti",
                        "soci")), "l'elenco a sinistra mostra le tabelle del catalogo: " + oggetti);
                int messaggiPrima = fromEdt(() -> a.panel().messages().size());
                // l'elenco delle tabelle si vede davvero (almeno 5 righe) e il diagramma ha spazio (≥ 40% della scheda)
                onEdt(() -> a.frame().validate());
                javax.swing.JList<?> elenco = fromEdt(() -> QbOperations.objectList(qb));
                int righeVisibili = fromEdt(() -> elenco.getVisibleRect().height / Math.max(1,
                        elenco.getCellBounds(0, 0).height));
                assertTrue(righeVisibili >= 5, "righe visibili dell'elenco delle tabelle: " + righeVisibili);
                int altezzaDiagramma = fromEdt(() -> QbOperations.diagram(qb).getHeight());
                int altezzaScheda = fromEdt(tab::getHeight);
                assertTrue(altezzaDiagramma * 100 >= altezzaScheda * 40, "diagramma alto " + altezzaDiagramma
                        + " px su " + altezzaScheda + " della scheda");
                List<String> tabelle = List.of("libri", "editori", "libri_autori", "autori", "prestiti");
                for (String t : tabelle) {
                    // doppio clic sulla voce dell'elenco, come l'utente (stesso percorso del trascinamento)
                    onEdt(() -> {
                        elenco.setSelectedIndex(QbOperations.objects(qb).indexOf(t));
                        java.awt.Rectangle r = elenco.getCellBounds(elenco.getSelectedIndex(), elenco.getSelectedIndex());
                        elenco.dispatchEvent(new java.awt.event.MouseEvent(elenco, java.awt.event.MouseEvent.MOUSE_PRESSED,
                                System.currentTimeMillis(), 0, r.x + 5, r.y + 5, 2, false, java.awt.event.MouseEvent.BUTTON1));
                    });
                    assertTrue(fromEdt(() -> QbOperations.tables(qb)).contains(t), "tabella accettata: " + t);
                }
                assertEquals(tabelle, fromEdt(() -> QbOperations.tables(qb)), "tutte e 5 nel diagramma");
                assertEquals(messaggiPrima, (int) fromEdt(() -> a.panel().messages().size()),
                        "nessun avviso (in SQLeo originale: limite di 3 tabelle)");

                // un join «a.x = b.y» è la stessa condizione di «b.y = a.x»: il diagramma mette per primo il lato già
                // presente nel FROM, non per forza la tabella riferita; si confrontano le due colonne senza verso,
                // più il nome (quello della FK)
                Set<String> proposti = new TreeSet<>();
                for (QbOperations.JoinInfo j : fromEdt(() -> QbOperations.joins(qb))) {
                    proposti.add(j.name() + ": " + senzaVerso(j.foreignTable() + "." + j.foreignColumn(),
                            j.primaryTable() + "." + j.primaryColumn()));
                }
                Set<String> fkSulServer = new TreeSet<>();
                for (List<String> r : server.rows("SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME,"
                        + " REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE"
                        + " WHERE TABLE_SCHEMA = '" + catalog + "' AND REFERENCED_TABLE_NAME IS NOT NULL")) {
                    if (tabelle.contains(r.get(1)) && tabelle.contains(r.get(3))) {
                        fkSulServer.add(r.get(0) + ": " + senzaVerso(r.get(1) + "." + r.get(2), r.get(3) + "." + r.get(4)));
                    }
                }
                assertEquals(4, fkSulServer.size(), "la fixture ha 4 FK tra le 5 tabelle: " + fkSulServer);
                assertEquals(fkSulServer, proposti, "i join proposti sono le chiavi esterne del server");
                String sql = VisualSupport.textView(tab);
                for (String t : tabelle) {
                    assertTrue(sql.contains(t), "la vista SQL nomina " + t + ":\n" + sql);
                }
                Probe.paintWindow("step7", a.frame(), "T7.4-" + server.id() + ".png");
                ev.append("Catalogo di test: ").append(catalog).append('\n')
                        .append("Elenco a sinistra del diagramma: ").append(oggetti).append(" (").append(righeVisibili)
                        .append(" righe visibili; diagramma alto ").append(altezzaDiagramma).append(" px su ")
                        .append(altezzaScheda).append(")\nTabelle aggiunte con il doppio clic sull'elenco\n")
                        .append("Tabelle nel diagramma: ").append(tabelle).append(" (nessun avviso)\n")
                        .append("Join proposti dal diagramma: ").append(proposti).append('\n')
                        .append("FK lette dal server (information_schema.KEY_COLUMN_USAGE): ").append(fkSulServer)
                        .append('\n').append("SQL della vista testo:\n").append(sql).append("\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step7", "T7.4-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    /**
     * T7.4b (difetto B1 della revisione): il menu del join dice ciò che l'SQL fa, anche quando il formatter gira il join.
     * {@code libri_autori} aggiunta prima di {@code autori}: il join nasce con {@code autori} come primaria (tabella
     * riferita), ma nel FROM viene prima {@code libri_autori} e il formatter lo gira. Clic vero sulla voce «Tutte le righe
     * di autori»: sul server un autore senza libri deve comparire.
     */
    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t74b_ilMenuDelJoinDiceCioCheFaLSql(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("visual74b");
        StringBuilder ev = new StringBuilder("T7.4b — menu del join e verso dell'SQL su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            server.run("INSERT INTO `" + catalog + "`.`autori` (cognome, nome) VALUES ('Senzalibri', 'Anna')");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                VisualQueryTab tab = VisualSupport.openFromToolbar(a, catalog);
                var qb = tab.queryBuilder();
                onEdt(() -> {
                    QbOperations.addTable(qb, "libri_autori");
                    QbOperations.addTable(qb, "autori");
                    QbOperations.deselectAll(qb, "libri_autori");
                    QbOperations.deselectAll(qb, "autori");
                    QbOperations.select(qb, "autori", "cognome", true);
                    QbOperations.select(qb, "libri_autori", "id_libro", true);
                });
                String prima = VisualSupport.textView(tab);   // la resa gira il join se serve
                int join = fromEdt(() -> QbOperations.joinIndex(qb, "autori", "libri_autori"));
                javax.swing.JPopupMenu menu = fromEdt(() -> QbOperations.joinMenu(qb, join));
                javax.swing.JMenuItem voce = null;
                StringBuilder voci = new StringBuilder();
                for (java.awt.Component c : menu.getComponents()) {
                    if (c instanceof javax.swing.JMenuItem mi) {
                        voci.append("«").append(mi.getText()).append("» ");
                        if ("Tutte le righe di autori".equals(mi.getText())) {
                            voce = mi;
                        }
                    }
                }
                assertNotNull(voce, "voce del menu: " + voci);
                javax.swing.JMenuItem scelta = voce;
                onEdt(scelta::doClick);
                String dopo = VisualSupport.textView(tab);
                assertEquals("Tutte le righe di autori", fromEdt(() -> QbOperations.joins(qb).get(join).description()));
                ScriptResult esito = VisualSupport.run(a, tab);
                assertTrue(esito != null && esito.completed(), "eseguita");
                List<List<String>> righe = VisualSupport.rows(esito.results().get(1).firstResult().orElseThrow());
                assertTrue(righe.stream().anyMatch(r -> r.contains("Senzalibri")),
                        "l'autore senza libri c'è: tutte le righe di autori, come dice il menu\n" + dopo);
                assertEquals(server.rows("SELECT COUNT(*) FROM `" + catalog + "`.autori a LEFT JOIN `" + catalog
                        + "`.libri_autori la ON la.id_autore = a.id").get(0).get(0), String.valueOf(righe.size()),
                        "tante righe quante l'autori LEFT JOIN libri_autori scritto a mano");
                Probe.paintWindow("step7", a.frame(), "T7.4b-" + server.id() + ".png");
                ev.append("Catalogo di test: ").append(catalog).append("\nSQL prima della scelta:\n").append(prima)
                        .append("\nVoci del menu del join: ").append(voci).append("\nScelta «Tutte le righe di autori»:\n")
                        .append(dopo).append("\nRighe: ").append(righe.size()).append(", compreso l'autore senza libri")
                        .append(" (Senzalibri)\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step7", "T7.4b-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    private static String senzaVerso(String a, String b) {
        return a.compareTo(b) <= 0 ? a + " = " + b : b + " = " + a;
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t75_ogniGestoAggiornaLSqlEdEseguiDaIlRisultatoAtteso(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("visual75");
        StringBuilder ev = new StringBuilder("T7.5 — gesti sul diagramma ed esecuzione su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            // un editore senza libri: il LEFT JOIN deve servire davvero
            server.run("INSERT INTO `" + catalog + "`.`editori` (nome, citta) VALUES ('Editore senza libri', 'Bari')");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                VisualQueryTab tab = VisualSupport.openFromToolbar(a, catalog);
                var qb = tab.queryBuilder();
                ev.append("Catalogo di test: ").append(catalog).append('\n');

                onEdt(() -> QbOperations.addTable(qb, "editori"));
                String s0 = VisualSupport.textView(tab);
                onEdt(() -> QbOperations.addTable(qb, "libri"));
                String s1 = VisualSupport.textView(tab);
                assertNotEquals(s0, s1, "aggiunta di libri: l'SQL cambia");
                assertTrue(s1.toLowerCase().contains("join"), "il join proposto dalla FK è nell'SQL:\n" + s1);
                ev.append("gesto 1 — tabelle editori e libri:\n").append(s1).append("\n\n");

                // solo le colonne che servono: via la spunta da tutte, poi editori.nome
                onEdt(() -> {
                    QbOperations.deselectAll(qb, "editori");
                    QbOperations.deselectAll(qb, "libri");
                    QbOperations.select(qb, "editori", "nome", true);
                });
                String s2 = VisualSupport.textView(tab);
                assertNotEquals(s1, s2, "colonne scelte: l'SQL cambia");
                ev.append("gesto 2 — solo editori.nome:\n").append(s2).append("\n\n");

                int join = fromEdt(() -> QbOperations.joinIndex(qb, "editori", "libri"));
                assertTrue(join >= 0, "c'è il join editori–libri");
                // clic sul nodo del join → voce «Tutte le righe di editori» del menu, come l'utente
                onEdt(() -> {
                    for (java.awt.Component c : QbOperations.joinMenu(qb, join).getComponents()) {
                        if (c instanceof javax.swing.JMenuItem mi && "Tutte le righe di editori".equals(mi.getText())) {
                            mi.doClick();
                        }
                    }
                });
                String s3 = VisualSupport.textView(tab);
                assertNotEquals(s2, s3, "join LEFT: l'SQL cambia");
                assertTrue(s3.contains("`editori`\n\tLEFT OUTER JOIN `libri`"), "editori LEFT OUTER JOIN libri:\n" + s3);
                assertEquals("Tutte le righe di editori", fromEdt(() -> QbOperations.joins(qb).get(join).description()));
                // il LEFT conta davvero: l'editore senza libri compare (il filtro sull'anno, dopo, lo toglierà)
                ScriptResult conLeft = VisualSupport.run(a, tab);
                assertTrue(conLeft != null && conLeft.completed(), "eseguita dopo il gesto 3");
                List<List<String>> righeLeft = VisualSupport.rows(conLeft.results().get(1).firstResult().orElseThrow());
                assertTrue(righeLeft.stream().anyMatch(r -> r.contains("Editore senza libri")),
                        "con «tutte le righe di editori» c'è anche l'editore senza libri");
                ev.append("gesto 3 — join «tutte le righe di editori» (clic sulla voce del menu del join):\n").append(s3)
                        .append("\n  eseguita: ").append(righeLeft.size())
                        .append(" righe, compreso «Editore senza libri»\n\n");

                onEdt(() -> QbOperations.addWhere(qb, "libri", "anno", ">", "2000"));
                String s4 = VisualSupport.textView(tab);
                assertNotEquals(s3, s4, "filtro: l'SQL cambia");
                assertTrue(s4.contains("> 2000"), "il filtro è nell'SQL:\n" + s4);
                ev.append("gesto 4 — filtro anno > 2000:\n").append(s4).append("\n\n");

                onEdt(() -> {
                    QbOperations.addExpression(qb, "COUNT", "libri", null, "numero_libri");
                    QbOperations.addGroupBy(qb, "editori", "nome");
                });
                String s5 = VisualSupport.textView(tab);
                assertNotEquals(s4, s5, "raggruppamento: l'SQL cambia");
                assertTrue(s5.toUpperCase().contains("COUNT(*)") && s5.toUpperCase().contains("GROUP BY"),
                        "conteggio e raggruppamento nell'SQL:\n" + s5);
                ev.append("gesto 5 — COUNT(*) per editore:\n").append(s5).append("\n\n");

                onEdt(() -> QbOperations.addOrderBy(qb, "editori", "nome", true));
                String s6 = VisualSupport.textView(tab);
                assertNotEquals(s5, s6, "ordinamento: l'SQL cambia");
                assertTrue(s6.toUpperCase().contains("ORDER BY"), "ordinamento nell'SQL:\n" + s6);
                ev.append("gesto 6 — ordinamento per nome:\n").append(s6).append("\n\n");

                int registroPrima = a.log().size();
                ScriptResult esito = VisualSupport.run(a, tab);
                assertTrue(esito != null && esito.completed(), "eseguita: " + (esito == null ? "annullata"
                        : esito.failure().map(f -> f.statement().text() + " → " + f.error()).orElse("?")));
                assertEquals(2, esito.script().size(), "USE del catalogo + la query");
                assertEquals("USE `" + catalog + "`", esito.script().statements().get(0).text());
                assertEquals(s6.strip(), esito.script().statements().get(1).text().strip(),
                        "si esegue proprio il testo della vista SQL");
                List<SqlLog.Entry> nuove = a.log().entries().subList(registroPrima, a.log().size());
                assertEquals(2, nuove.size(), "due righe nel registro");
                assertTrue(nuove.stream().allMatch(e -> e.origin().equals(SqlOrigin.QUERY_BUILDER.label())),
                        "origine «Query visiva» nel registro: " + nuove);

                List<List<String>> mostrate = VisualSupport.rows(esito.results().get(1).firstResult().orElseThrow());
                List<List<String>> attese = server.rows("SELECT e.nome, COUNT(*) FROM `" + catalog + "`.editori e"
                        + " LEFT JOIN `" + catalog + "`.libri l ON l.id_editore = e.id WHERE l.anno > 2000"
                        + " GROUP BY e.nome ORDER BY e.nome");
                assertFalse(attese.isEmpty(), "la domanda ha risposta non vuota sulla fixture");
                assertEquals(attese, mostrate, "le righe mostrate sono quelle del server");
                assertEquals(1, (int) fromEdt(() -> tab.editor().results().outcomeTable().getRowCount()),
                        "la scheda mostra l'esito della query (il USE non si mostra come risultato)");
                Probe.paintWindow("step7", a.frame(), "T7.5-" + server.id() + ".png");
                ev.append("Esecuzione: ").append(esito.script().statements().get(0).text()).append(" + la query;")
                        .append(" registro: ").append(nuove.size()).append(" righe con origine «")
                        .append(SqlOrigin.QUERY_BUILDER.label()).append("»\n")
                        .append("Righe mostrate (").append(mostrate.size()).append("): ").append(mostrate).append('\n')
                        .append("Righe della stessa domanda scritta a mano, lette con una connessione separata: ")
                        .append(attese).append("\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step7", "T7.5-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }
}
