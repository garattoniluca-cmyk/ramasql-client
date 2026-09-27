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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqleo.querybuilder.QbOperations;
import com.sqleo.querybuilder.QueryBuilder;

import it.ramasql.app.visual.VisualQueryTab;
import it.ramasql.core.exec.ScriptResult;

/**
 * T7.6, T7.7, T7.7b e T7.8 — la scheda «Query visiva» del programma vero contro MariaDB e MySQL:
 * <ul>
 *   <li><b>T7.6</b>: nella vista SQL si aggiunge a mano una colonna; tornando alla Grafica il diagramma la mostra spuntata.</li>
 *   <li><b>T7.7</b>: una query con funzione finestra ({@code ROW_NUMBER() OVER …}) non passa alla Grafica: avviso
 *       «non si può mostrare nel diagramma», testo intatto, esecuzione possibile e corretta.</li>
 *   <li><b>T7.7b</b>: query nidificate. «Libri con prezzo sopra la media» (sottoquery in WHERE) ed «editori con almeno un
 *       libro» ({@code EXISTS} correlata) si <b>costruiscono dal diagramma</b>: si aggiunge la condizione con
 *       sottoquery, se ne apre il nodo nell'albero, dentro si aggiungono tabella, colonne e filtri, si torna alla query
 *       esterna. «Numero di prestiti per socio» come tabella derivata filtrata parte dal testo (la costruzione di una
 *       tabella derivata dal diagramma è provata nello spike S2d): se ne apre il nodo, la si modifica, si torna fuori.
 *       Ogni volta l'SQL esterno contiene la modifica e le righe mostrate sono quelle del server.</li>
 *   <li><b>T7.8</b>: due schede «Query visiva» su cataloghi e tabelle diversi, lavorando alternando; nessuna
 *       interferenza (rischio R-06, stato {@code static} del codice ereditato, {@code BUG-007}).</li>
 * </ul>
 * Le righe attese si leggono con una connessione separata del test, dalla stessa domanda scritta a mano.
 */
@Tag("step7")
@Tag("ui")
@Tag("it")
class T76T78QueryVisivaSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t76_t77_testoAManoEQueryNonRappresentabile(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("visual76");
        StringBuilder ev = new StringBuilder("T7.6 e T7.7 su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                VisualQueryTab tab = VisualSupport.openFromToolbar(a, catalog);
                QueryBuilder qb = tab.queryBuilder();
                ev.append("Catalogo di test: ").append(catalog).append('\n');

                // ---------------------------------------------------------------- T7.6
                onEdt(() -> {
                    QbOperations.addTable(qb, "libri");
                    QbOperations.deselectAll(qb, "libri");
                    QbOperations.select(qb, "libri", "titolo", true);
                });
                String prima = VisualSupport.textView(tab);
                assertEquals(List.of("titolo"), fromEdt(() -> QbOperations.selectedColumns(qb, "libri")));
                onEdt(tab::showSql);
                assertFalse(fromEdt(tab::isGraphicShown), "vista SQL");
                String aMano = prima.replace("`libri`.`titolo`", "`libri`.`titolo`,\n\t`libri`.`anno`");
                assertNotEquals(prima, aMano, "il testo è stato cambiato a mano");
                onEdt(() -> tab.editor().setText(aMano));
                assertTrue(fromEdt(tab::showGraphic), "si torna alla Grafica");
                assertEquals(List.of("titolo", "anno"), fromEdt(() -> QbOperations.selectedColumns(qb, "libri")),
                        "il diagramma riflette la colonna aggiunta a mano");
                String dopo = VisualSupport.textView(tab);
                assertTrue(dopo.contains("`anno`"), "e l'SQL la contiene:\n" + dopo);
                Probe.paintWindow("step7", a.frame(), "T7.6-" + server.id() + ".png");
                ev.append("T7.6 — SQL del diagramma:\n").append(prima).append("\nscritto a mano nella vista SQL:\n")
                        .append(aMano).append("\ntornando alla Grafica, colonne spuntate di libri: ")
                        .append(fromEdt(() -> QbOperations.selectedColumns(qb, "libri"))).append('\n');

                // ---------------------------------------------------------------- T7.7
                String finestra = "SELECT titolo, prezzo, ROW_NUMBER() OVER (ORDER BY prezzo DESC, id) AS posizione"
                        + " FROM libri ORDER BY posizione";
                boolean disegnata = fromEdt(() -> tab.setSql(finestra));
                assertFalse(disegnata, "una funzione finestra non si disegna");
                assertFalse(fromEdt(tab::isGraphicShown), "si resta sulla vista SQL");
                String avviso = fromEdt(tab::noticeText);
                assertTrue(avviso.contains("non si può mostrare nel diagramma"), "avviso: " + avviso);
                assertEquals(finestra, fromEdt(() -> tab.editor().getText()), "testo intatto");
                Probe.paintWindow("step7", a.frame(), "T7.7-" + server.id() + ".png");
                ScriptResult esito = VisualSupport.run(a, tab);
                assertTrue(esito != null && esito.completed(), "si esegue lo stesso");
                assertEquals(finestra, esito.script().statements().get(1).text().strip().replaceAll(";$", ""));
                List<List<String>> mostrate = VisualSupport.rows(esito.results().get(1).firstResult().orElseThrow());
                List<List<String>> attese = server.rows("SELECT titolo, prezzo, ROW_NUMBER() OVER (ORDER BY prezzo DESC,"
                        + " id) AS posizione FROM `" + catalog + "`.libri ORDER BY posizione");
                assertEquals(attese, mostrate, "righe della funzione finestra uguali al server");
                ev.append("T7.7 — ").append(finestra).append("\n  avviso: «").append(avviso)
                        .append("»\n  testo intatto; eseguita: ").append(mostrate.size())
                        .append(" righe, uguali a quelle lette dal server con una connessione separata\n")
                        .append("Esito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step7", "T7.6-T7.7-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t77b_queryNidificateDalDiagramma(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("visual77b");
        StringBuilder ev = new StringBuilder("T7.7b — query nidificate su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            server.run("INSERT INTO `" + catalog + "`.`editori` (nome, citta) VALUES ('Editore senza libri', 'Bari')");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                ev.append("Catalogo di test: ").append(catalog).append('\n');
                String c = "`" + catalog + "`.";

                // ------------------------------------------------ libri con prezzo sopra la media
                VisualQueryTab t1 = VisualSupport.openFromToolbar(a, catalog);
                QueryBuilder q1 = t1.queryBuilder();
                onEdt(() -> {
                    QbOperations.addTable(q1, "libri");
                    QbOperations.deselectAll(q1, "libri");
                    QbOperations.select(q1, "libri", "titolo", true);
                    QbOperations.select(q1, "libri", "prezzo", true);
                    QbOperations.addSubqueryCondition(q1, "libri", "prezzo", ">");
                    QbOperations.addOrderBy(q1, "libri", "titolo", true);
                });
                int nodo1 = sottoquery(q1);
                onEdt(() -> QbOperations.selectNode(q1, nodo1));
                assertEquals(List.of(), fromEdt(() -> QbOperations.tables(q1)), "la sottoquery si apre vuota");
                onEdt(() -> {
                    QbOperations.addTable(q1, "libri");
                    QbOperations.deselectAll(q1, "libri");
                    QbOperations.addExpression(q1, "AVG", "libri", "prezzo", null);
                    QbOperations.selectNode(q1, 0);
                });
                assertEquals(List.of("libri"), fromEdt(() -> QbOperations.tables(q1)), "di nuovo la query esterna");
                String sopraMedia = VisualSupport.textView(t1);
                assertTrue(sopraMedia.replaceAll("\\s+", " ").contains("> (SELECT AVG(`libri`.`prezzo`)"),
                        "la sottoquery costruita è nell'SQL esterno:\n" + sopraMedia);
                confronta(a, t1, server, "SELECT l.titolo, l.prezzo FROM " + c + "libri l WHERE l.prezzo >"
                        + " (SELECT AVG(l2.prezzo) FROM " + c + "libri l2) ORDER BY l.titolo", ev, "sopra la media");
                // si riapre la sottoquery e la si modifica: la media diventa quella dei libri dopo il 1990
                onEdt(() -> {
                    QbOperations.selectNode(q1, nodo1);
                    QbOperations.addWhere(q1, "libri", "anno", ">", "1990");
                    QbOperations.selectNode(q1, 0);
                });
                String modificata = VisualSupport.textView(t1);
                assertNotEquals(sopraMedia, modificata, "l'SQL esterno si aggiorna");
                assertTrue(modificata.replaceAll("\\s+", " ").contains("WHERE `libri`.`anno` > 1990"),
                        "la modifica sta dentro la sottoquery:\n" + modificata);
                confronta(a, t1, server, "SELECT l.titolo, l.prezzo FROM " + c + "libri l WHERE l.prezzo >"
                        + " (SELECT AVG(l2.prezzo) FROM " + c + "libri l2 WHERE l2.anno > 1990) ORDER BY l.titolo", ev,
                        "sopra la media dei libri dopo il 1990");
                Probe.paintWindow("step7", a.frame(), "T7.7b-sopra-media-" + server.id() + ".png");

                // ------------------------------------------------ editori con almeno un libro (EXISTS correlata)
                VisualQueryTab t2 = VisualSupport.openFromToolbar(a, catalog);
                QueryBuilder q2 = t2.queryBuilder();
                onEdt(() -> {
                    QbOperations.addTable(q2, "editori");
                    QbOperations.deselectAll(q2, "editori");
                    QbOperations.select(q2, "editori", "nome", true);
                    QbOperations.addSubqueryCondition(q2, null, null, "EXISTS");
                    QbOperations.addOrderBy(q2, "editori", "nome", true);
                });
                int nodo2 = sottoquery(q2);
                onEdt(() -> {
                    QbOperations.selectNode(q2, nodo2);
                    QbOperations.addTable(q2, "libri");
                    QbOperations.deselectAll(q2, "libri");
                    QbOperations.select(q2, "libri", "id", true);
                    QbOperations.addWhere(q2, "libri", "id_editore", "=", "`editori`.`id`");
                    QbOperations.selectNode(q2, 0);
                });
                String exists = VisualSupport.textView(t2);
                assertTrue(exists.replaceAll("\\s+", " ").contains("EXISTS (SELECT `libri`.`id` FROM `libri` WHERE"
                        + " `libri`.`id_editore` = `editori`.`id`)"), "EXISTS correlata nell'SQL:\n" + exists);
                confronta(a, t2, server, "SELECT e.nome FROM " + c + "editori e WHERE EXISTS (SELECT l.id FROM " + c
                        + "libri l WHERE l.id_editore = e.id) ORDER BY e.nome", ev, "editori con almeno un libro");
                Probe.paintWindow("step7", a.frame(), "T7.7b-exists-" + server.id() + ".png");

                // ------------------------------------------------ prestiti per socio (tabella derivata filtrata)
                VisualQueryTab t3 = VisualSupport.openFromToolbar(a, catalog);
                QueryBuilder q3 = t3.queryBuilder();
                String derivata = "SELECT t.id_socio, t.numero_prestiti FROM (SELECT p.id_socio, COUNT(*) AS"
                        + " numero_prestiti FROM prestiti p GROUP BY p.id_socio) t WHERE t.numero_prestiti > 3"
                        + " ORDER BY t.id_socio";
                assertTrue(fromEdt(() -> t3.setSql(derivata)), "la tabella derivata si disegna");
                assertTrue(fromEdt(() -> QbOperations.entities(q3)).contains("(derivata) t"), "entità derivata t");
                confronta(a, t3, server, derivata.replace("FROM prestiti", "FROM " + c + "prestiti"), ev,
                        "prestiti per socio (tabella derivata)");
                int nodo3 = sottoquery(q3);
                onEdt(() -> QbOperations.selectNode(q3, nodo3));
                assertEquals(List.of("prestiti"), fromEdt(() -> QbOperations.tables(q3)), "dentro la derivata c'è prestiti");
                onEdt(() -> {
                    QbOperations.addWhere(q3, "prestiti", "id_libro", ">", "10");
                    QbOperations.selectNode(q3, 0);
                });
                String derivataModificata = VisualSupport.textView(t3);
                assertTrue(derivataModificata.replaceAll("\\s+", " ").contains("`id_libro` > 10 GROUP BY"),
                        "la modifica sta dentro la tabella derivata:\n" + derivataModificata);
                confronta(a, t3, server, "SELECT t.id_socio, t.numero_prestiti FROM (SELECT p.id_socio, COUNT(*) AS"
                        + " numero_prestiti FROM " + c + "prestiti p WHERE p.id_libro > 10 GROUP BY p.id_socio) t"
                        + " WHERE t.numero_prestiti > 3 ORDER BY t.id_socio", ev, "derivata modificata");
                Probe.paintWindow("step7", a.frame(), "T7.7b-derivata-" + server.id() + ".png");
                ev.append("Esito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step7", "T7.7b-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t78_dueSchedeSenzaInterferenze(DbServer server) throws Exception {
        String catA = DbServer.newCatalogName("visual78a");
        String catB = DbServer.newCatalogName("visual78b");
        StringBuilder ev = new StringBuilder("T7.8 — due schede «Query visiva» su " + server.label() + "\n");
        try {
            server.createCatalog(catA);
            server.loadFixture(catA, "biblioteca.sql");
            server.createCatalog(catB);
            server.run("CREATE TABLE `" + catB + "`.`studenti` (id INT PRIMARY KEY, cognome VARCHAR(40), classe CHAR(2))");
            server.run("INSERT INTO `" + catB + "`.`studenti` VALUES (1,'Rossi','3A'),(2,'Bianchi','3B'),(3,'Verdi','3A')");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                VisualQueryTab ta = VisualSupport.openFromToolbar(a, catA);
                VisualQueryTab tb = VisualSupport.openFromToolbar(a, catB);
                QueryBuilder qa = ta.queryBuilder();
                QueryBuilder qb = tb.queryBuilder();
                assertTrue(fromEdt(() -> QbOperations.objects(qb)).equals(List.of("studenti")),
                        "la scheda B vede solo il suo catalogo");

                // lavoro alternato: A, B, A, B, A…
                onEdt(() -> a.frame().workTabs().setSelectedComponent(ta));
                onEdt(() -> {
                    QbOperations.addTable(qa, "libri");
                    QbOperations.deselectAll(qa, "libri");
                    QbOperations.select(qa, "libri", "titolo", true);
                });
                onEdt(() -> a.frame().workTabs().setSelectedComponent(tb));
                onEdt(() -> {
                    QbOperations.addTable(qb, "studenti");
                    QbOperations.deselectAll(qb, "studenti");
                    QbOperations.select(qb, "studenti", "cognome", true);
                });
                onEdt(() -> a.frame().workTabs().setSelectedComponent(ta));
                onEdt(() -> QbOperations.addWhere(qa, "libri", "anno", "<", "1960"));
                onEdt(() -> a.frame().workTabs().setSelectedComponent(tb));
                // nella scheda B passa dal parser un testo scritto a mano (il parser ereditato è statico)
                assertTrue(fromEdt(() -> tb.setSql("SELECT cognome, classe FROM studenti WHERE classe = '3A'"
                        + " ORDER BY cognome")));
                onEdt(() -> a.frame().workTabs().setSelectedComponent(ta));
                onEdt(() -> {
                    QbOperations.addTable(qa, "editori");   // metadati del catalogo A anche dopo il lavoro in B
                    QbOperations.addOrderBy(qa, "libri", "titolo", true);
                });
                assertEquals(List.of("id", "nome", "citta"), fromEdt(() -> QbOperations.columns(qa, "editori")));

                String sqlA = VisualSupport.textView(ta);
                String sqlB = VisualSupport.textView(tb);
                assertFalse(sqlA.toLowerCase(Locale.ROOT).contains("studenti"), "A non vede B:\n" + sqlA);
                assertFalse(sqlB.toLowerCase(Locale.ROOT).contains("libri"), "B non vede A:\n" + sqlB);
                assertTrue(sqlA.contains("< 1960") && sqlA.contains("`editori`"), sqlA);
                assertTrue(sqlB.contains("'3A'"), sqlB);

                onEdt(() -> a.frame().workTabs().setSelectedComponent(ta));
                ScriptResult ra = VisualSupport.run(a, ta);
                assertEquals("USE `" + catA + "`", ra.script().statements().get(0).text());
                onEdt(() -> a.frame().workTabs().setSelectedComponent(tb));
                ScriptResult rb = VisualSupport.run(a, tb);
                assertEquals("USE `" + catB + "`", rb.script().statements().get(0).text());
                assertEquals(server.rows("SELECT cognome, classe FROM `" + catB + "`.studenti WHERE classe = '3A'"
                        + " ORDER BY cognome"), VisualSupport.rows(rb.results().get(1).firstResult().orElseThrow()));
                assertTrue(ra.completed(), "A eseguita");
                Probe.paintWindow("step7", a.frame(), "T7.8-" + server.id() + ".png");
                ev.append("Cataloghi: A = ").append(catA).append(", B = ").append(catB).append('\n')
                        .append("SQL della scheda A:\n").append(sqlA).append("\nSQL della scheda B:\n").append(sqlB)
                        .append("\nEseguite: A con ").append(ra.script().statements().get(0).text()).append(", B con ")
                        .append(rb.script().statements().get(0).text()).append("; righe di B uguali al server: ")
                        .append(VisualSupport.rows(rb.results().get(1).firstResult().orElseThrow()))
                        .append("\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step7", "T7.8-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catA);
            server.dropQuietly(catB);
        }
    }

    // ---------------------------------------------------------------- aiutanti

    /** Indice del primo nodo di sottoquery (livello 1) nell'albero della query. */
    private static int sottoquery(QueryBuilder qb) {
        List<QbOperations.QueryNode> nodi = fromEdt(() -> QbOperations.nodes(qb));
        return nodi.stream().filter(n -> n.level() == 1).findFirst()
                .orElseThrow(() -> new AssertionError("nessuna sottoquery nell'albero: " + nodi)).index();
    }

    /** Esegue la scheda e confronta le righe mostrate con la domanda di riferimento letta dal server. */
    private static void confronta(ClientApp a, VisualQueryTab tab, DbServer server, String riferimento,
            StringBuilder ev, String nome) throws Exception {
        String sql = VisualSupport.textView(tab);
        ScriptResult esito = VisualSupport.run(a, tab);
        assertTrue(esito != null && esito.completed(), nome + " eseguita: " + (esito == null ? "annullata"
                : esito.failure().map(f -> f.statement().text() + " → " + f.error()).orElse("?")));
        List<List<String>> mostrate = VisualSupport.rows(esito.results().get(1).firstResult().orElseThrow());
        List<List<String>> attese = new ArrayList<>(server.rows(riferimento));
        assertFalse(attese.isEmpty(), nome + ": la domanda di riferimento ha righe");
        assertEquals(attese, mostrate, nome + ": righe mostrate = righe del server");
        ev.append("— ").append(nome).append(":\n").append(sql).append("\n  ").append(mostrate.size())
                .append(" righe, uguali a quelle del riferimento scritto a mano: ").append(riferimento).append('\n');
    }
}
