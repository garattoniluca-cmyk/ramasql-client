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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.TreePath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqleo.querybuilder.QbOperations;
import com.sqleo.querybuilder.QueryBuilder;

import it.ramasql.app.MainFrame;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.visual.VisualQueryTab;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.sqlgen.ViewDdl;

/**
 * Le viste dello Step 8 sul <b>programma vero</b>, contro MariaDB e MySQL, ricontrollate sul server con una
 * connessione separata del test:
 * <ul>
 *   <li><b>T8.4</b>: {@code v_prestiti_aperti} costruita nel diagramma da <em>Nuova vista</em> (3 tabelle, filtro
 *       {@code data_reso IS NULL}), salvata ({@code CREATE VIEW} dall'anteprima), scheda chiusa, riaperta con
 *       <em>Modifica vista</em> dal menu del navigatore: <b>livello 1</b>, stesso diagramma e stesso testo, nessuna
 *       modifica da salvare; aggiunta una colonna → {@code CREATE OR REPLACE VIEW} → la vista sul server restituisce la
 *       colonna nuova. Poi: nome già usato (1050, spiegato in italiano), colonne con lo stesso nome (1060, detto prima e
 *       senza mandare nulla), <em>Salva come vista…</em> da una query visiva normale.</li>
 *   <li><b>T8.5</b>: <em>Modifica vista</em> su 5 viste create <b>fuori</b> dal client: tre si disegnano (livello 2),
 *       due (funzione finestra, UNION ALL) si aprono come testo con l'avviso (livello 3); nessun blocco e nessuna
 *       perdita: il testo riaperto, eseguito, dà le righe della vista; salvato di nuovo, la vista resta quella.</li>
 *   <li><b>T8.6</b>: i dati di una vista si aprono in griglia in sola lettura; la copia a blocchi funziona.</li>
 *   <li><b>T8.7</b>: eliminata una tabella usata da una vista, aprirla mostra l'errore del server (1356) con la
 *       spiegazione in italiano.</li>
 *   <li><b>T8.7b</b>: {@code v_libri_sopra_media} (sottoquery) e {@code v_riepilogo} costruita <b>sopra</b>
 *       {@code v_prestiti_aperti}, create nel diagramma e riaperte; nella vista-su-vista la vista di base è un'entità
 *       del diagramma; eliminata la vista di base, la vista che la usa risulta non valida (1356). Le due viste reali di
 *       {@code bibliotecasoft} (copiate in un catalogo di test) si riaprono nel diagramma e danno le loro righe:
 *       {@code bibliotecasoft} è un catalogo dell'utente <b>solo su MariaDB</b>, quindi questa parte gira solo lì (su
 *       MySQL il test lo verifica e lo scrive nell'evidenza).</li>
 * </ul>
 */
@Tag("step8")
@Tag("ui")
@Tag("it")
class T84T87VisteSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    // ---------------------------------------------------------------- aiutanti

    /**
     * La fixture {@code biblioteca} contiene già {@code v_prestiti_aperti} e {@code v_libri_editori} (servono allo Step
     * 4, create da script): qui le viste le deve creare il client, quindi quelle della fixture si tolgono prima di
     * avviare il programma (connessione del test, catalogo di test).
     */
    private static void senzaVisteDellaFixture(DbServer server, String catalog) throws java.sql.SQLException {
        server.run("DROP VIEW `" + catalog + "`.`v_prestiti_aperti`");
        server.run("DROP VIEW `" + catalog + "`.`v_libri_editori`");
    }

    /** Seleziona il catalogo e preme «Nuova vista» nella barra. */
    private static VisualQueryTab nuovaVista(ClientApp a, String catalog) {
        TreePath path = a.node(NavNode.Kind.CATALOG, catalog, null);
        onEdt(() -> a.nav().tree().setSelectionPath(path));
        Probe.waitUntil("«Nuova vista» accesa", ClientApp.TIMEOUT, () -> a.frame().button("newView").isEnabled());
        onEdt(() -> a.frame().button("newView").doClick());
        VisualQueryTab tab = fromEdt(() -> a.frame().tabs().selectedVisualQuery());
        assertNotNull(tab, "«Nuova vista» apre la query visiva");
        assertTrue(fromEdt(tab::isViewMode), "in modalità vista");
        return tab;
    }

    /** Scrive il nome, preme «Salva vista», conferma l'anteprima (finta) e aspetta l'esito; restituisce lo script. */
    private static ScriptResult salva(ClientApp a, VisualQueryTab tab, String nome) throws Exception {
        onEdt(() -> {
            javax.swing.JTextField campo = (javax.swing.JTextField) find(tab, "visualQuery.viewName");
            if (campo.isEditable()) {
                campo.setText(nome);
            }
        });
        assertTrue(fromEdt(tab::saveView), "«Salva vista» parte");
        return a.awaitLastProposal();
    }

    private static java.awt.Component find(java.awt.Container c, String name) {
        for (java.awt.Component k : c.getComponents()) {
            if (name.equals(k.getName())) {
                return k;
            }
            if (k instanceof java.awt.Container cc) {
                java.awt.Component f = find(cc, name);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static List<List<String>> ordinate(List<List<String>> righe) {
        List<List<String>> out = new ArrayList<>(righe);
        out.sort((x, y) -> String.join("\u0001", x.stream().map(String::valueOf).toList())
                .compareTo(String.join("\u0001", y.stream().map(String::valueOf).toList())));
        return out;
    }

    /** Aspetta nel pannello Messaggi un messaggio che contiene il testo. */
    private static String messaggio(ClientApp a, String contiene) {
        Probe.waitUntil("messaggio con «" + contiene + "»", ClientApp.TIMEOUT, () -> a.panel().messages().stream()
                .anyMatch(m -> m.text().contains(contiene)));
        return fromEdt(() -> a.panel().messages().stream().filter(m -> m.text().contains(contiene)).reduce((x, y) -> y)
                .orElseThrow().text());
    }

    /** Costruisce nel diagramma la SELECT di v_prestiti_aperti (tre tabelle, filtro sui prestiti non resi). */
    private static void prestitiAperti(VisualQueryTab tab) {
        QueryBuilder qb = tab.queryBuilder();
        onEdt(() -> {
            QbOperations.addTable(qb, "prestiti");
            QbOperations.addTable(qb, "libri");
            QbOperations.addTable(qb, "soci");
            for (String t : List.of("prestiti", "libri", "soci")) {
                QbOperations.deselectAll(qb, t);
            }
            QbOperations.select(qb, "prestiti", "id", true);
            QbOperations.select(qb, "libri", "titolo", true);
            QbOperations.select(qb, "soci", "cognome", true);
            QbOperations.select(qb, "prestiti", "data_prestito", true);
            QbOperations.addWhere(qb, "prestiti", "data_reso", "IS", "NULL");
        });
    }

    // ---------------------------------------------------------------- T8.4 e T8.6

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t84_t86_vistaCreataGraficamenteRiapertaEModificata(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("vista84");
        StringBuilder ev = new StringBuilder("T8.4 e T8.6 su " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            senzaVisteDellaFixture(server, catalog);
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                ev.append("Catalogo di test: ").append(catalog).append('\n');
                VisualQueryTab tab = nuovaVista(a, catalog);
                prestitiAperti(tab);
                String sqlSalvato = VisualSupport.textView(tab);
                assertTrue(fromEdt(tab::isModified), "una vista nuova con la query costruita è lavoro da salvare");
                ScriptResult creata = salva(a, tab, "v_prestiti_aperti");
                assertTrue(creata != null && creata.completed(), "vista creata");
                assertTrue(creata.script().statements().get(1).text()
                        .startsWith("CREATE VIEW `" + catalog + "`.`v_prestiti_aperti` AS"), creata.script().text());
                assertTrue(server.tableExists(catalog, "v_prestiti_aperti"), "la vista è sul server");
                List<List<String>> attese = server.rows("SELECT p.id, l.titolo, s.cognome, p.data_prestito FROM `"
                        + catalog + "`.prestiti p JOIN `" + catalog + "`.libri l ON p.id_libro = l.id JOIN `" + catalog
                        + "`.soci s ON p.id_socio = s.id WHERE p.data_reso IS NULL");
                assertFalse(attese.isEmpty(), "ci sono prestiti aperti nella fixture");
                assertEquals(ordinate(attese), ordinate(server.rows("SELECT * FROM `" + catalog
                        + "`.v_prestiti_aperti")), "la vista restituisce i prestiti aperti");
                // l'archiviazione del sorgente avviene fuori dall'EDT dopo l'esito: la si aspetta
                Probe.waitUntil("sorgente archiviato", ClientApp.TIMEOUT, () -> a.frame().viewSources()
                        .find(server.profile().address(), catalog, "v_prestiti_aperti").isPresent());
                Probe.waitUntil("vista non modificata dopo il salvataggio", ClientApp.TIMEOUT, () -> !tab.isModified());
                ev.append("Creata dal diagramma con «Salva vista»:\n").append(creata.script().text()).append('\n')
                        .append("Righe della vista sul server: ").append(attese.size()).append('\n')
                        .append("Dopo il salvataggio la scheda risulta senza modifiche; sorgente nell'archivio\n");

                // «Modifica vista» dal menu contestuale vero del navigatore
                onEdt(() -> a.frame().tabs().close(tab));
                String definizione = server.scalar("SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE"
                        + " TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = 'v_prestiti_aperti'");
                assertTrue(fromEdt(() -> a.frame().viewSources().sourceFor(server.profile().address(), catalog,
                        "v_prestiti_aperti", definizione)).isPresent(),
                        "livello 1: il sorgente archiviato vale per la definizione che c'è sul server");
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                a.expand(NavNode.Kind.VIEWS, catalog, null);
                a.menu(NavNode.Kind.VIEW, catalog, "v_prestiti_aperti", "nav.menu.editView");
                VisualQueryTab t2 = fromEdt(() -> a.frame().tabs().selectedVisualQuery());
                assertNotNull(t2, "«Modifica vista» apre la query visiva");
                assertTrue(fromEdt(t2::isViewMode) && fromEdt(t2::replacesExistingView), "in modalità vista esistente");
                assertEquals("v_prestiti_aperti", fromEdt(t2::viewName));
                assertTrue(fromEdt(t2::isGraphicShown), "riaperta nel diagramma");
                assertFalse(fromEdt(t2::isModified), "appena riaperta non c'è niente da salvare");
                assertEquals("", fromEdt(t2::noticeText), "nessun avviso: è il sorgente scritto nel client");
                // le stesse tre tabelle (nel diagramma riaperto l'ordine è quello del FROM salvato) e lo stesso SQL
                assertEquals(java.util.Set.of("prestiti", "libri", "soci"),
                        java.util.Set.copyOf(fromEdt(() -> QbOperations.tables(t2.queryBuilder()))));
                assertEquals(3, fromEdt(() -> QbOperations.joins(t2.queryBuilder())).size() + 1,
                        "con i due join fra le tre tabelle");
                assertEquals(sqlSalvato, VisualSupport.textView(t2), "stesso SQL di quando è stata salvata");
                Probe.paintWindow("step8", a.frame(), "T8.4-riaperta-" + server.id() + ".png");

                onEdt(() -> QbOperations.select(t2.queryBuilder(), "soci", "nome", true));
                assertTrue(fromEdt(t2::isModified), "aggiunta una colonna: c'è da salvare");
                // una seconda «Modifica vista» riporta davanti la stessa scheda senza rileggere (il lavoro resta)
                MainFrame.ViewEditing ancora = fromEdt(() -> a.frame().editView(catalog, "v_prestiti_aperti"));
                assertTrue(ancora != null && ancora.tab() == t2 && ancora.reopening() == null,
                        "stessa scheda, nessuna rilettura");
                assertTrue(fromEdt(t2::isModified) && VisualSupport.textView(t2).contains("nome"),
                        "la modifica non salvata è ancora lì");
                ScriptResult sostituita = salva(a, t2, "v_prestiti_aperti");
                assertTrue(sostituita != null && sostituita.completed(), "vista sostituita");
                assertTrue(sostituita.script().statements().get(1).text().startsWith("CREATE OR REPLACE VIEW `"
                        + catalog + "`.`v_prestiti_aperti` AS"), sostituita.script().text());
                assertEquals("1", server.scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                        + catalog + "' AND TABLE_NAME = 'v_prestiti_aperti' AND COLUMN_NAME = 'nome'"),
                        "la colonna nuova è nella vista");
                assertEquals(String.valueOf(attese.size()), server.scalar("SELECT COUNT(nome) FROM `" + catalog
                        + "`.v_prestiti_aperti"), "e la vista la restituisce");
                Probe.waitUntil("vista non modificata dopo la sostituzione", ClientApp.TIMEOUT, () -> !t2.isModified());
                ev.append("Riaperta con «Modifica vista» dal menu del navigatore: livello 1 (sorgente archiviato valido")
                        .append(" per la definizione sul server), stesso diagramma (prestiti, libri, soci) e stesso SQL,")
                        .append(" nessuna modifica da salvare\nAggiunta la colonna soci.nome; una seconda «Modifica")
                        .append(" vista» riporta la stessa scheda senza perdere la modifica:\n")
                        .append(sostituita.script().text()).append('\n');

                // un nome già usato (1050): il server rifiuta, la spiegazione è in italiano, la vista esistente resta
                VisualQueryTab doppia = nuovaVista(a, catalog);
                onEdt(() -> {
                    QbOperations.addTable(doppia.queryBuilder(), "libri");
                    QbOperations.deselectAll(doppia.queryBuilder(), "libri");
                    QbOperations.select(doppia.queryBuilder(), "libri", "titolo", true);
                });
                ScriptResult rifiutata = salva(a, doppia, "v_prestiti_aperti");
                assertTrue(rifiutata != null && !rifiutata.completed(), "CREATE VIEW su un nome esistente non riesce");
                assertTrue(rifiutata.script().statements().get(1).text().startsWith("CREATE VIEW "),
                        "una vista nuova non sostituisce mai quella che c'è: " + rifiutata.script().text());
                String msg1050 = messaggio(a, "1050");
                assertTrue(msg1050.contains("Esiste già"), "spiegazione in italiano: " + msg1050);
                assertEquals(String.valueOf(attese.size()), server.scalar("SELECT COUNT(nome) FROM `" + catalog
                        + "`.v_prestiti_aperti"), "la vista esistente è intatta");
                assertTrue(fromEdt(doppia::isModified), "il lavoro della scheda resta da salvare");
                ev.append("Nome già usato: «").append(msg1050.replace('\n', ' '))
                        .append("»; la vista esistente è intatta\n");

                // colonne con lo stesso nome (1060): lo si dice prima, con i nomi, senza mandare nulla al server
                VisualQueryTab stessiNomi = nuovaVista(a, catalog);
                onEdt(() -> {
                    QbOperations.addTable(stessiNomi.queryBuilder(), "libri");
                    QbOperations.addTable(stessiNomi.queryBuilder(), "editori");
                });
                int registroPrima = a.log().entries().size();
                Object propostaPrima = fromEdt(() -> a.workspace().pipeline().lastProposal());
                onEdt(() -> ((javax.swing.JTextField) find(stessiNomi, "visualQuery.viewName"))
                        .setText("v_libri_editori_tutto"));
                assertFalse(fromEdt(stessiNomi::saveView), "con due colonne «id» non si propone nulla");
                String avviso1060 = fromEdt(stessiNomi::noticeText);
                assertTrue(avviso1060.contains("«id»") && avviso1060.contains("libri.id")
                        && avviso1060.contains("editori.id"), "avviso con i nomi: " + avviso1060);
                assertTrue(propostaPrima == fromEdt(() -> a.workspace().pipeline().lastProposal()),
                        "nessuna nuova proposta");
                assertEquals(registroPrima, a.log().entries().size(), "niente nel registro");
                assertFalse(server.tableExists(catalog, "v_libri_editori_tutto"), "nessuna vista creata");
                Probe.paintWindow("step8", a.frame(), "T8.4-colonne-doppie-" + server.id() + ".png");
                ev.append("Colonne con lo stesso nome: avviso «").append(avviso1060.replace('\n', ' '))
                        .append("», nessun CREATE mandato al server\n");
                a.ws.onPendingOnClose = q -> it.ramasql.app.workspace.WorkspacePrompts.PendingChoice.DISCARD;
                for (VisualQueryTab t : List.of(doppia, stessiNomi)) {
                    onEdt(() -> a.frame().tabs().close(t));
                }

                // «Salva come vista…» da una query visiva normale
                TreePath nodoCatalogo = a.node(NavNode.Kind.CATALOG, catalog, null);
                onEdt(() -> a.nav().tree().setSelectionPath(nodoCatalogo));
                onEdt(() -> a.frame().button("newVisualQuery").doClick());
                VisualQueryTab query = fromEdt(() -> a.frame().tabs().selectedVisualQuery());
                assertNotNull(query, "query visiva aperta");
                assertFalse(fromEdt(query::isViewMode), "una query visiva normale non è una vista");
                onEdt(() -> {
                    QbOperations.addTable(query.queryBuilder(), "libri");
                    QbOperations.deselectAll(query.queryBuilder(), "libri");
                    QbOperations.select(query.queryBuilder(), "libri", "titolo", true);
                    QbOperations.select(query.queryBuilder(), "libri", "anno", true);
                    ((javax.swing.JButton) find(query, "visualQuery.saveAsView")).doClick();
                });
                assertTrue(fromEdt(query::isViewMode), "«Salva come vista…» passa alla modalità vista");
                ScriptResult daQuery = salva(a, query, "v_titoli_anni");
                assertTrue(daQuery != null && daQuery.completed(), "vista creata da una query visiva");
                assertTrue(daQuery.script().statements().get(1).text().startsWith("CREATE VIEW `" + catalog
                        + "`.`v_titoli_anni` AS"), daQuery.script().text());
                assertEquals(server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.libri"),
                        server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.v_titoli_anni"), "righe della vista");
                ev.append("«Salva come vista…» da una query visiva normale:\n").append(daQuery.script().text())
                        .append('\n');

                // ------------------------------------------------ T8.6: i dati della vista
                onEdt(() -> a.nav().openView(catalog, "v_prestiti_aperti"));
                Probe.waitUntil("griglia della vista", ClientApp.TIMEOUT,
                        () -> a.frame().workTabs().getSelectedComponent() instanceof DataGrid);
                a.waitIdle();
                DataGrid griglia = fromEdt(() -> (DataGrid) a.frame().workTabs().getSelectedComponent());
                assertFalse(fromEdt(griglia::isConfirmEnabled), "una vista non si modifica dalla griglia");
                assertFalse(fromEdt(griglia::isEditable), "sola lettura");
                String spiegazione = fromEdt(griglia::readOnlyExplanation);
                assertFalse(spiegazione.isBlank(), "la griglia dice perché è in sola lettura");
                java.awt.datatransfer.Clipboard appunti = new java.awt.datatransfer.Clipboard("prova");
                onEdt(() -> griglia.setClipboard(appunti));
                onEdt(() -> griglia.selectBlock(0, 0, 1, 1));
                it.ramasql.app.grid.GridTestSupport.action(griglia, DataGrid.ACTION_COPY);
                String copiato = (String) appunti.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                assertEquals(2, copiato.strip().split("\n").length, "blocco 2×2 copiato: " + copiato);
                assertEquals(2, copiato.strip().split("\n")[0].split("\t").length, "due colonne: " + copiato);
                Probe.paintWindow("step8", a.frame(), "T8.6-" + server.id() + ".png");
                ev.append("T8.6 — dati della vista in griglia: sola lettura («").append(spiegazione)
                        .append("»), blocco 2×2 copiato: «").append(copiato.strip().replace('\n', '|')
                        .replace('\t', '»')).append("»\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step8", "T8.4-T8.6-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ---------------------------------------------------------------- T8.5

    record Esterna(String nome, String select, int livello, String traccia) {
    }

    static final List<Esterna> ESTERNE = List.of(
            new Esterna("v_semplice", "SELECT titolo, anno FROM libri WHERE anno > 2000", 2, "`anno` > 2000"),
            new Esterna("v_join", "SELECT l.titolo, e.nome AS editore FROM libri l JOIN editori e ON l.id_editore = e.id",
                    2, "join"),
            new Esterna("v_aggregata", "SELECT id_editore, COUNT(*) AS n FROM libri GROUP BY id_editore", 2, "count"),
            new Esterna("v_finestra", "SELECT titolo, ROW_NUMBER() OVER (ORDER BY prezzo DESC, id) AS posizione"
                    + " FROM libri", 3, "row_number"),
            new Esterna("v_union", "SELECT cognome FROM autori UNION ALL SELECT cognome FROM soci", 3, "union all"));

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t85_cinqueVisteCreateFuoriDalClient(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("vista85");
        StringBuilder ev = new StringBuilder("T8.5 — Modifica vista su 5 viste create fuori dal client, " + server.label()
                + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            for (Esterna v : ESTERNE) {   // fuori dal client: con la connessione del test
                server.run("CREATE VIEW `" + catalog + "`.`" + v.nome() + "` AS " + v.select().replace("FROM ",
                        "FROM `" + catalog + "`.").replace("JOIN ", "JOIN `" + catalog + "`."));
            }
            server.run("CREATE VIEW `" + catalog + "`.`v_con_check` AS SELECT titolo, anno FROM `" + catalog
                    + "`.libri WHERE anno > 2000 WITH CASCADED CHECK OPTION");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                ev.append("Catalogo di test: ").append(catalog).append('\n');
                for (Esterna v : ESTERNE) {
                    MainFrame.ViewEditing r = fromEdt(() -> a.frame().editView(catalog, v.nome()));
                    assertNotNull(r, v.nome());
                    VisualQueryTab tab = r.tab();
                    assertEquals(v.livello(), r.reopening().level(), v.nome() + ": " + r.reopening().reason());
                    assertEquals(v.livello() == 2, fromEdt(tab::isGraphicShown), v.nome() + ": disegnata?");
                    String testo = fromEdt(() -> tab.editor().getText());
                    assertTrue(testo.toLowerCase().contains(v.traccia()), v.nome() + ": testo intatto: " + testo);
                    String avviso = fromEdt(tab::noticeText);   // quello mostrato all'apertura
                    if (v.livello() == 3) {
                        assertTrue(avviso.contains("non si può mostrare nel diagramma"), "avviso: " + avviso);
                    }
                    // nessuna perdita: il testo riaperto, eseguito, dà le righe della vista
                    ScriptResult eseguita = VisualSupport.run(a, tab);
                    assertTrue(eseguita != null && eseguita.completed(), v.nome() + " eseguita");
                    List<List<String>> dalTesto = VisualSupport.rows(eseguita.results().get(1).firstResult().orElseThrow());
                    List<List<String>> dallaVista = server.rows("SELECT * FROM `" + catalog + "`.`" + v.nome() + "`");
                    assertEquals(ordinate(dallaVista), ordinate(dalTesto), v.nome() + ": stesse righe della vista");
                    Probe.paintWindow("step8", a.frame(), "T8.5-" + v.nome() + "-" + server.id() + ".png");
                    // nessun blocco: si salva di nuovo, così com'è (CREATE OR REPLACE), e la vista resta la stessa
                    ScriptResult salvata = salva(a, tab, v.nome());
                    assertTrue(salvata != null && salvata.completed(), v.nome() + " salvata di nuovo");
                    assertTrue(salvata.script().statements().get(1).text().startsWith("CREATE OR REPLACE VIEW"));
                    assertEquals(ordinate(dallaVista), ordinate(server.rows("SELECT * FROM `" + catalog + "`.`"
                            + v.nome() + "`")), v.nome() + ": dopo il salvataggio le righe sono le stesse");
                    ev.append("— ").append(v.nome()).append(": livello ").append(r.reopening().level())
                            .append(v.livello() == 2 ? " (nel diagramma)" : " (testo, avviso: «"
                                    + avviso + "»)")
                            .append("; eseguita: ").append(dalTesto.size())
                            .append(" righe = righe della vista; salvata di nuovo con CREATE OR REPLACE, righe invariate\n");
                    onEdt(() -> a.frame().tabs().close(tab));
                }
                // un'opzione che la v1 non gestisce (WITH CHECK OPTION): lo si dice all'apertura, prima di salvare
                MainFrame.ViewEditing conOpzione = fromEdt(() -> a.frame().editView(catalog, "v_con_check"));
                assertNotNull(conOpzione, "v_con_check");
                String avvisoOpzione = fromEdt(conOpzione.tab()::noticeText);
                assertTrue(avvisoOpzione.contains("CHECK OPTION"), "avviso sull'opzione: " + avvisoOpzione);
                Probe.paintWindow("step8", a.frame(), "T8.5-v_con_check-" + server.id() + ".png");
                ev.append("— v_con_check (WITH CASCADED CHECK OPTION): livello ").append(conOpzione.reopening().level())
                        .append(", avviso all'apertura: «").append(avvisoOpzione.replace('\n', ' ')).append("»\n");
                ev.append("Esito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step8", "T8.5-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ---------------------------------------------------------------- T8.7

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t87_vistaNonPiuValida(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("vista87");
        StringBuilder ev = new StringBuilder("T8.7 — tabella eliminata sotto una vista, " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            server.run("CREATE TABLE `" + catalog + "`.`collane` (id INT PRIMARY KEY, nome VARCHAR(40))");
            server.run("INSERT INTO `" + catalog + "`.`collane` VALUES (1, 'Gialli'), (2, 'Classici')");
            server.run(ViewDdl.createView(catalog, "v_collane_editori", "SELECT e.nome, c.nome AS collana FROM `" + catalog
                    + "`.editori e JOIN `" + catalog + "`.collane c ON c.id = e.id", false));
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                assertFalse(server.rows("SELECT * FROM `" + catalog + "`.v_collane_editori").isEmpty(),
                        "prima la vista funziona");
                // la tabella si elimina fuori dal client (come farebbe un compagno da un altro PC)
                server.run("DROP TABLE `" + catalog + "`.`collane`");
                int prima = fromEdt(() -> a.panel().messages().size());
                onEdt(() -> a.nav().openView(catalog, "v_collane_editori"));
                String msg = messaggio(a, "1356");
                assertTrue(msg.contains("non è più valida"), "spiegazione in italiano: " + msg);
                assertTrue(msg.indexOf("non è più valida") < msg.indexOf("1356"),
                        "prima la spiegazione in italiano, poi la riga del server: " + msg);
                assertTrue(fromEdt(() -> a.panel().messages().size()) > prima);
                assertEquals(2, (int) fromEdt(() -> a.panel().getSelectedIndex()), "la scheda Messaggi è davanti");
                assertFalse(fromEdt(() -> a.frame().workTabs().getSelectedComponent() instanceof DataGrid),
                        "nessuna griglia vuota spacciata per buona");
                boolean nelRegistro = a.log().entries().stream().anyMatch(e -> e.sql().contains("v_collane_editori")
                        && e.outcome() == it.ramasql.core.exec.SqlLog.Outcome.ERROR);
                assertTrue(nelRegistro, "la lettura fallita è nel registro");
                Probe.paintWindow("step8", a.frame(), "T8.7-" + server.id() + ".png");
                ev.append("Catalogo di test: ").append(catalog).append("\nEliminata la tabella collane, usata dalla vista")
                        .append(" v_collane_editori; aprendo la vista, nel pannello Messaggi:\n  ").append(msg)
                        .append("\nLa scheda Messaggi è davanti; la lettura che ha dato l'errore è nel registro")
                        .append(" (esito: errore)\nEsito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step8", "T8.7-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ---------------------------------------------------------------- T8.7b

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t87b_visteNidificateEVisteReali(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("vista87b");
        String copia = null;
        StringBuilder ev = new StringBuilder("T8.7b — viste nidificate e viste reali, " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            senzaVisteDellaFixture(server, catalog);
            boolean reali = CopiaBibliotecasoft.disponibile(server);
            if (server == DbServer.MARIADB) {
                assertTrue(reali, "su MariaDB il catalogo bibliotecasoft dell'utente deve esserci (si legge soltanto)");
            }
            if (reali) {
                copia = DbServer.newCatalogName("bibliosoft");
                server.createCatalog(copia);
                CopiaBibliotecasoft.copia(server, copia);
                for (String vista : CopiaBibliotecasoft.VISTE) {
                    String def = server.scalar("SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = '"
                            + copia + "' AND TABLE_NAME = '" + vista + "'");
                    assertNotNull(def, vista + " copiata");
                    assertFalse(def.contains(CopiaBibliotecasoft.ORIGINE),
                            vista + ": la copia non deve leggere dal catalogo originale: " + def);
                }
            }
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                ev.append("Catalogo di test: ").append(catalog).append('\n');

                // v_libri_sopra_media: vista con sottoquery, costruita nel diagramma
                VisualQueryTab t1 = nuovaVista(a, catalog);
                QueryBuilder q1 = t1.queryBuilder();
                onEdt(() -> {
                    QbOperations.addTable(q1, "libri");
                    QbOperations.deselectAll(q1, "libri");
                    QbOperations.select(q1, "libri", "titolo", true);
                    QbOperations.select(q1, "libri", "prezzo", true);
                    QbOperations.addSubqueryCondition(q1, "libri", "prezzo", ">");
                });
                int nodo = fromEdt(() -> QbOperations.nodes(q1)).stream().filter(n -> n.level() == 1).findFirst()
                        .orElseThrow().index();
                onEdt(() -> {
                    QbOperations.selectNode(q1, nodo);
                    QbOperations.addTable(q1, "libri");
                    QbOperations.deselectAll(q1, "libri");
                    QbOperations.addExpression(q1, "AVG", "libri", "prezzo", null);
                    QbOperations.selectNode(q1, 0);
                });
                assertTrue(salva(a, t1, "v_libri_sopra_media").completed(), "v_libri_sopra_media creata");
                assertEquals(ordinate(server.rows("SELECT l.titolo, l.prezzo FROM `" + catalog + "`.libri l WHERE"
                        + " l.prezzo > (SELECT AVG(prezzo) FROM `" + catalog + "`.libri)")),
                        ordinate(server.rows("SELECT * FROM `" + catalog + "`.v_libri_sopra_media")),
                        "v_libri_sopra_media restituisce i libri sopra la media");

                // v_prestiti_aperti, poi v_riepilogo costruita SOPRA di essa
                VisualQueryTab t2 = nuovaVista(a, catalog);
                prestitiAperti(t2);
                assertTrue(salva(a, t2, "v_prestiti_aperti").completed(), "v_prestiti_aperti creata");
                VisualQueryTab t3 = nuovaVista(a, catalog);
                QueryBuilder q3 = t3.queryBuilder();
                assertTrue(fromEdt(() -> QbOperations.objects(q3)).contains("v_prestiti_aperti"),
                        "la vista è nell'elenco a sinistra, insieme alle tabelle");
                onEdt(() -> {
                    QbOperations.addTable(q3, "v_prestiti_aperti");
                    QbOperations.deselectAll(q3, "v_prestiti_aperti");
                    QbOperations.select(q3, "v_prestiti_aperti", "cognome", true);
                    QbOperations.addExpression(q3, "COUNT", "v_prestiti_aperti", null, "aperti");
                    QbOperations.addGroupBy(q3, "v_prestiti_aperti", "cognome");
                });
                assertEquals(List.of("id", "titolo", "cognome", "data_prestito"),
                        fromEdt(() -> QbOperations.columns(q3, "v_prestiti_aperti")), "colonne della vista di base");
                assertTrue(salva(a, t3, "v_riepilogo").completed(), "v_riepilogo creata");
                assertEquals(ordinate(server.rows("SELECT s.cognome, COUNT(*) FROM `" + catalog + "`.prestiti p JOIN `"
                        + catalog + "`.soci s ON p.id_socio = s.id JOIN `" + catalog + "`.libri l ON p.id_libro = l.id"
                        + " WHERE p.data_reso IS NULL GROUP BY s.cognome")),
                        ordinate(server.rows("SELECT * FROM `" + catalog + "`.v_riepilogo")), "righe di v_riepilogo");
                ev.append("Create nel diagramma: v_libri_sopra_media (sottoquery), v_prestiti_aperti, v_riepilogo (sopra")
                        .append(" v_prestiti_aperti); righe verificate sul server\n");

                for (VisualQueryTab t : List.of(t1, t2, t3)) {
                    onEdt(() -> a.frame().tabs().close(t));
                }
                MainFrame.ViewEditing rr = fromEdt(() -> a.frame().editView(catalog, "v_riepilogo"));
                assertTrue(fromEdt(rr.tab()::isGraphicShown), "v_riepilogo riaperta nel diagramma");
                assertEquals(List.of("v_prestiti_aperti"), fromEdt(() -> QbOperations.tables(rr.tab().queryBuilder())),
                        "la vista di base compare come «tabella» nel diagramma");
                Probe.paintWindow("step8", a.frame(), "T8.7b-vista-su-vista-" + server.id() + ".png");
                MainFrame.ViewEditing rm = fromEdt(() -> a.frame().editView(catalog, "v_libri_sopra_media"));
                assertTrue(fromEdt(rm.tab()::isGraphicShown), "v_libri_sopra_media riaperta nel diagramma");
                assertTrue(fromEdt(() -> QbOperations.nodes(rm.tab().queryBuilder())).stream()
                        .anyMatch(n -> n.level() == 1), "con il nodo della sottoquery");
                ev.append("Riaperte: v_riepilogo (livello ").append(rr.reopening().level())
                        .append(", entità del diagramma: v_prestiti_aperti), v_libri_sopra_media (livello ")
                        .append(rm.reopening().level()).append(", con il nodo della sottoquery)\n");

                // eliminata la vista di base, quella che la usa non è più valida
                server.run("DROP VIEW `" + catalog + "`.`v_prestiti_aperti`");
                onEdt(() -> a.nav().openView(catalog, "v_riepilogo"));
                String msg = messaggio(a, "1356");
                assertTrue(msg.contains("non è più valida"), msg);
                ev.append("Eliminata v_prestiti_aperti: aprendo v_riepilogo → «").append(msg).append("»\n");

                if (reali) {
                    final String cat = copia;
                    for (String vista : CopiaBibliotecasoft.VISTE) {
                        MainFrame.ViewEditing r = fromEdt(() -> a.frame().editView(cat, vista));
                        assertNotNull(r, vista);
                        assertEquals(2, r.reopening().level(), vista + ": " + r.reopening().reason());
                        assertTrue(fromEdt(r.tab()::isGraphicShown), vista + " riaperta nel diagramma");
                        ScriptResult es = VisualSupport.run(a, r.tab());
                        assertTrue(es != null && es.completed(), vista + " eseguita");
                        assertEquals(ordinate(server.rows("SELECT * FROM `" + cat + "`.`" + vista + "`")),
                                ordinate(VisualSupport.rows(es.results().get(1).firstResult().orElseThrow())),
                                vista + ": il testo riaperto dà le righe della vista");
                        Probe.paintWindow("step8", a.frame(), "T8.7b-" + vista + "-" + server.id() + ".png");
                        ev.append("Vista reale di bibliotecasoft ").append(vista).append(" (copiata in ").append(cat)
                                .append("): livello 2, nel diagramma: ").append(fromEdt(() ->
                                        QbOperations.tables(r.tab().queryBuilder()))).append("; righe uguali alla vista\n");
                    }
                } else {
                    ev.append("bibliotecasoft non c'è su ").append(server.label())
                            .append(" (catalogo dell'utente solo su MariaDB): parte delle viste reali non applicabile qui\n");
                }
                ev.append("Esito: SUPERATO\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step8", "T8.7b-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
            server.dropQuietly(copia);
        }
    }
}
