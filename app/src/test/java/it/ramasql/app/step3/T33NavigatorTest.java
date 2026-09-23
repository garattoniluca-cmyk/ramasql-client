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
import static it.ramasql.app.step3.Step3Ui.onEdt;
import static it.ramasql.app.step3.Step3Ui.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.tree.TreePath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.navigator.NavigatorIcons;
import it.ramasql.core.metadata.CatalogInfo;

/**
 * <b>T3.3</b> — navigatore su {@code biblioteca} (InnoDB) e {@code biblioteca_myisam}, su entrambi i server: icone
 * distinte InnoDB/MyISAM assegnate ai nodi, colonne/indici/chiavi esterne sotto ogni tabella (confrontati con
 * {@code information_schema} letto da una connessione separata), viste e routine (sola lettura), cataloghi di
 * sistema nascosti, filtro per nome, «Mostra SQL di creazione». Nessuna di queste letture finisce nel registro.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class T33NavigatorTest {

    private static final List<String> TABLES = List.of("autori", "editori", "libri", "libri_autori", "prestiti", "soci");

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Step3Ui.setup();
    }

    @ParameterizedTest
    @EnumSource(Step3Server.class)
    void t33_alberoIconeFigliFiltroESistemaNascosti(Step3Server server) throws Exception {
        String innodb = Step3Server.newCatalogName("nav");
        String myisam = innodb + "_myisam";
        StringBuilder evidence = new StringBuilder("T3.3 — navigatore su " + server.label() + "\n");
        try {
            server.createCatalog(innodb);
            server.createCatalog(myisam);
            server.loadFixture(innodb, "biblioteca.sql");
            server.loadFixture(myisam, "biblioteca_myisam.sql");
            server.run("CREATE PROCEDURE `" + innodb + "`.`conta_libri`() SELECT COUNT(*) FROM `" + innodb
                    + "`.`libri`");
            evidence.append("Cataloghi di test: ").append(innodb).append(" (fixture biblioteca.sql), ").append(myisam)
                    .append(" (fixture biblioteca_myisam.sql), procedura conta_libri\n\n");

            try (Step3App a = Step3App.connect(server, dataDir)) {
                // ---------- cataloghi di sistema nascosti di default
                List<String> visibleCatalogs = catalogNames(a);
                for (String system : CatalogInfo.SYSTEM_CATALOGS) {
                    assertFalse(visibleCatalogs.contains(system), "catalogo di sistema visibile: " + system);
                }
                assertTrue(visibleCatalogs.contains(innodb) && visibleCatalogs.contains(myisam), visibleCatalogs.toString());
                a.menu(NavNode.Kind.SERVER, null, null, "nav.menu.showSystem");
                waitUntil("information_schema visibile", Step3App.TIMEOUT,
                        () -> a.nav().find(NavNode.Kind.CATALOG, null, "information_schema") != null);
                assertSame(NavigatorIcons.SYSTEM_CATALOG,
                        fromEdt(() -> a.nav().iconOf(a.nav().find(NavNode.Kind.CATALOG, null, "information_schema"))));
                a.menu(NavNode.Kind.SERVER, null, null, "nav.menu.showSystem");
                waitUntil("information_schema di nuovo nascosto", Step3App.TIMEOUT,
                        () -> a.nav().find(NavNode.Kind.CATALOG, null, "information_schema") == null);
                evidence.append("Cataloghi di sistema: nascosti di default ").append(CatalogInfo.SYSTEM_CATALOGS)
                        .append("; «Mostra cataloghi di sistema» li mostra (icona grigia) e li rinasconde\n");

                // ---------- per ogni tabella: icona dell'engine, colonne, indici, chiavi esterne = information_schema
                for (String catalog : List.of(innodb, myisam)) {
                    boolean isMyisam = catalog.equals(myisam);
                    a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                    TreePath folder = a.expand(NavNode.Kind.TABLES, catalog, null);
                    List<String> tables = fromEdt(() -> a.nav().childrenOf(folder).stream().map(NavNode::name).toList());
                    assertEquals(TABLES, tables, "tabelle di " + catalog);
                    evidence.append("\nCatalogo ").append(catalog).append(isMyisam ? " (MyISAM)" : " (InnoDB)").append('\n');
                    for (String table : TABLES) {
                        TreePath tp = a.expand(NavNode.Kind.TABLE, catalog, table);
                        Object icon = fromEdt(() -> a.nav().iconOf(tp));
                        assertSame(isMyisam ? NavigatorIcons.TABLE_MYISAM : NavigatorIcons.TABLE_INNODB, icon,
                                "icona di " + catalog + "." + table);
                        List<NavNode> folders = fromEdt(() -> a.nav().childrenOf(tp));
                        assertEquals(List.of(NavNode.Kind.COLUMNS, NavNode.Kind.INDEXES, NavNode.Kind.FOREIGN_KEYS),
                                folders.stream().map(NavNode::kind).toList());
                        List<String> columns = childNames(a, NavNode.Kind.COLUMNS, catalog, table);
                        List<String> indexes = childNames(a, NavNode.Kind.INDEXES, catalog, table);
                        List<String> fks = childNames(a, NavNode.Kind.FOREIGN_KEYS, catalog, table);
                        List<String> serverColumns = list(server, "SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                                + " WHERE TABLE_SCHEMA='" + catalog + "' AND TABLE_NAME='" + table
                                + "' ORDER BY ORDINAL_POSITION");
                        Set<String> serverIndexes = new TreeSet<>(list(server, "SELECT DISTINCT INDEX_NAME FROM"
                                + " information_schema.STATISTICS WHERE TABLE_SCHEMA='" + catalog + "' AND TABLE_NAME='"
                                + table + "'"));
                        Set<String> serverFks = new TreeSet<>(list(server, "SELECT CONSTRAINT_NAME FROM"
                                + " information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='" + catalog
                                + "' AND TABLE_NAME='" + table + "'"));
                        assertEquals(serverColumns, columns, "colonne di " + table);
                        assertEquals(serverIndexes, new TreeSet<>(indexes), "indici di " + table);
                        assertEquals(serverFks, new TreeSet<>(fks), "chiavi esterne di " + table);
                        if (isMyisam) {
                            assertTrue(fks.isEmpty(), "MyISAM senza chiavi esterne");
                        }
                        String engine = server.scalar("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='"
                                + catalog + "' AND TABLE_NAME='" + table + "'");
                        evidence.append(String.format("  %-13s engine %-6s %-20s colonne %d, indici %s, FK %s%n",
                                table, engine, icon, columns.size(), indexes, fks));
                        assertEquals(isMyisam ? "MyISAM" : "InnoDB", engine);
                    }
                    TreePath views = a.expand(NavNode.Kind.VIEWS, catalog, null);
                    List<NavNode> viewNodes = fromEdt(() -> a.nav().childrenOf(views));
                    assertEquals(List.of("v_libri_editori", "v_prestiti_aperti"),
                            viewNodes.stream().map(NavNode::name).sorted().toList());
                    for (NavNode v : viewNodes) {
                        assertSame(NavigatorIcons.VIEW, NavigatorIcons.iconFor(v, false));
                    }
                    evidence.append("  viste: ").append(viewNodes.stream().map(NavNode::name).toList()).append('\n');
                }
                // la procedura, in sola lettura, sotto «Routine»
                TreePath routines = a.expand(NavNode.Kind.ROUTINES, innodb, null);
                List<String> routineNames = fromEdt(() -> a.nav().childrenOf(routines).stream().map(NavNode::name).toList());
                assertEquals(List.of("conta_libri"), routineNames);
                evidence.append("Routine di ").append(innodb).append(": ").append(routineNames).append(" (sola lettura)\n");

                // ---------- schermata delle icone: tabelle chiuse, i due cataloghi uno sotto l'altro
                onEdt(() -> {
                    for (String catalog : List.of(innodb, myisam)) {
                        for (String table : TABLES) {
                            a.nav().tree().collapsePath(a.nav().find(NavNode.Kind.TABLE, catalog, table));
                        }
                        a.nav().tree().collapsePath(a.nav().find(NavNode.Kind.VIEWS, catalog, null));
                    }
                    a.nav().tree().expandPath(a.nav().find(NavNode.Kind.ROUTINES, innodb, null));
                });
                a.waitIdle();
                Step3Ui.paintAtPreferredSize(a.nav().tree(), "T3.3-icone-" + server.id() + ".png");

                // ---------- schermata: una tabella aperta con le colonne visibili
                onEdt(() -> a.nav().tree().expandPath(a.nav().findChild(
                        a.nav().find(NavNode.Kind.TABLE, innodb, "libri"), NavNode.Kind.COLUMNS)));
                onEdt(() -> {
                    a.nav().tree().setSelectionPath(a.nav().find(NavNode.Kind.TABLE, innodb, "libri"));
                    a.nav().tree().scrollPathToVisible(a.nav().find(NavNode.Kind.TABLE, innodb, "libri"));
                });
                Step3Ui.paintWindow(a.frame(), "T3.3-" + server.id() + ".png");
                if (server == Step3Server.MARIADB) {
                    Step3Ui.paintWindow(a.frame(), "navigatore.png");
                }

                // ---------- filtro per nome
                onEdt(() -> a.nav().filterField().setText("libri"));
                a.waitIdle();
                List<NavNode> filtered = fromEdt(() -> a.nav().allNodes());
                List<String> filteredTables = new ArrayList<>();
                for (NavNode n : filtered) {
                    if (n.kind() == NavNode.Kind.TABLE || n.kind() == NavNode.Kind.VIEW || n.kind() == NavNode.Kind.ROUTINE) {
                        assertTrue(n.name().toLowerCase().contains("libri"), "non corrisponde al filtro: " + n.name());
                        if (n.kind() == NavNode.Kind.TABLE) {
                            filteredTables.add(n.catalog() + "." + n.name());
                        }
                    }
                    if (n.kind() == NavNode.Kind.CATALOG) {
                        String name = n.name();
                        boolean nameMatches = name.contains("libri");
                        boolean hasMatches = filtered.stream().anyMatch(o -> name.equals(o.catalog())
                                && (o.kind() == NavNode.Kind.TABLE || o.kind() == NavNode.Kind.VIEW
                                || o.kind() == NavNode.Kind.ROUTINE));
                        assertTrue(nameMatches || hasMatches, "catalogo senza corrispondenze visibile: " + name);
                    }
                }
                for (String c : List.of(innodb, myisam)) {
                    assertTrue(filteredTables.contains(c + ".libri") && filteredTables.contains(c + ".libri_autori"),
                            filteredTables.toString());
                    assertFalse(filteredTables.contains(c + ".soci"));
                }
                assertTrue(filtered.stream().anyMatch(n -> n.kind() == NavNode.Kind.VIEW && n.name().equals("v_libri_editori")));
                assertTrue(filtered.stream().anyMatch(n -> n.kind() == NavNode.Kind.ROUTINE && n.name().equals("conta_libri")));
                Step3Ui.paintWindow(a.frame(), "T3.3-filtro-" + server.id() + ".png");
                evidence.append("\nFiltro «libri»: tabelle visibili ").append(filteredTables)
                        .append("; nessuna tabella o catalogo senza corrispondenze\n");

                String suffix = innodb.substring("ramasql_test_s3_nav_".length());
                onEdt(() -> a.nav().filterField().setText(suffix.toUpperCase()));
                a.waitIdle();
                assertEquals(List.of(innodb, myisam), catalogNames(a), "filtro sul nome del catalogo (maiuscole ignorate)");
                onEdt(() -> a.nav().filterField().setText("zz_nessun_nome_così"));
                a.waitIdle();
                assertEquals(List.of(), catalogNames(a));
                assertNotNull(fromEdt(() -> a.nav().find(NavNode.Kind.MESSAGE, null, null)), "messaggio «nessun nome»");
                onEdt(() -> a.nav().filterField().setText(""));
                a.waitIdle();
                assertTrue(catalogNames(a).containsAll(List.of(innodb, myisam)));
                assertNotNull(fromEdt(() -> a.nav().find(NavNode.Kind.COLUMN, innodb, "titolo")),
                        "svuotato il filtro, l'albero torna com'era (espansioni ricordate)");
                evidence.append("Filtro «").append(suffix.toUpperCase()).append("»: solo ").append(List.of(innodb, myisam))
                        .append("; filtro senza corrispondenze: messaggio; filtro vuoto: albero com'era\n");

                // ---------- Mostra SQL di creazione (tabella, vista, routine): canale interno, sola lettura
                a.ws.onShowCreate = d -> {
                    if (server == Step3Server.MARIADB && a.ws.createSql.size() == 1) {
                        Step3Ui.paintWindow(d, "mostra-sql.png");
                    }
                };
                a.menu(NavNode.Kind.TABLE, innodb, "libri", "nav.menu.showCreate");
                waitUntil("SQL di creazione della tabella", Step3App.TIMEOUT, () -> a.ws.createSql.size() == 1);
                a.menu(NavNode.Kind.VIEW, innodb, "v_libri_editori", "nav.menu.showCreate");
                waitUntil("SQL di creazione della vista", Step3App.TIMEOUT, () -> a.ws.createSql.size() == 2);
                a.menu(NavNode.Kind.ROUTINE, innodb, "conta_libri", "nav.menu.showCreate");
                waitUntil("SQL di creazione della routine", Step3App.TIMEOUT, () -> a.ws.createSql.size() == 3);
                assertTrue(a.ws.createSql.get(0).sql().startsWith("CREATE TABLE `libri`"), a.ws.createSql.get(0).sql());
                assertTrue(a.ws.createSql.get(0).statement().startsWith("SHOW CREATE TABLE"));
                assertTrue(a.ws.createSql.get(1).sql().contains("`v_libri_editori` AS select"), a.ws.createSql.get(1).sql());
                assertTrue(a.ws.createSql.get(2).sql().contains("PROCEDURE `conta_libri`"), a.ws.createSql.get(2).sql());
                a.menu(NavNode.Kind.TABLE, innodb, "libri", "nav.menu.copyName");
                assertEquals("libri", a.ws.clipboard.get(a.ws.clipboard.size() - 1));
                evidence.append("Mostra SQL di creazione: tabella («")
                        .append(a.ws.createSql.get(0).sql().lines().findFirst().orElse(""))
                        .append("…»), vista, procedura; Copia nome → «libri»\n");

                // ---------- navigare non scrive nulla nel registro
                assertEquals(0, fromEdt(() -> a.log().size()), "le letture del navigatore non vanno nel registro");
                assertTrue(a.prompts.errors.isEmpty(), a.prompts.errors.toString());
                evidence.append("Registro dopo la navigazione: 0 istruzioni (letture sul canale interno)\n");
                evidence.append("Schermate: T3.3-").append(server.id()).append(".png, T3.3-filtro-").append(server.id())
                        .append(".png").append(server == Step3Server.MARIADB ? ", navigatore.png, mostra-sql.png" : "")
                        .append('\n');
            }
        } finally {
            server.dropQuietly(innodb);
            server.dropQuietly(myisam);
        }
        assertFalse(server.catalogExists(innodb));
        Step3Ui.writeText("T3.3-" + server.id() + ".txt", evidence.toString());
    }

    private static List<String> catalogNames(Step3App a) {
        return fromEdt(() -> a.nav().allNodes().stream().filter(n -> n.kind() == NavNode.Kind.CATALOG)
                .map(NavNode::name).toList());
    }

    private static List<String> childNames(Step3App a, NavNode.Kind folder, String catalog, String table) {
        return fromEdt(() -> {
            TreePath fp = a.nav().findChild(a.nav().find(NavNode.Kind.TABLE, catalog, table), folder);
            if (fp == null) {
                throw new AssertionError("cartella " + folder + " assente sotto " + table);
            }
            return a.nav().childrenOf(fp).stream().map(NavNode::name).toList();
        });
    }

    private static List<String> list(Step3Server server, String sql) throws Exception {
        List<String> out = new ArrayList<>();
        try (var c = server.connect(); var st = c.createStatement(); var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
