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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.Texts;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.ReadOnlyIndex;

/**
 * Il navigatore non nasconde ciò che il server ha: sulla fixture {@code tipi_speciali.sql} la cartella INDICI mostra
 * <b>tutti</b> gli indici del server (come {@code information_schema.STATISTICS}), compresi FULLTEXT, su prefisso e
 * DESC, questi in sola lettura con l'etichetta del motivo; la colonna ZEROFILL lo dice. Le abbreviazioni visibili
 * vengono dai file di risorse.
 */
@Tag("step3")
@Tag("ui")
@Tag("it")
class NavigatorSpecialIndexesTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void indiciSpecialiEZerofillNelNavigatore(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("speciali");
        StringBuilder ev = new StringBuilder("Navigatore sui tipi speciali, " + server.label() + "\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "tipi_speciali.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                a.expand(NavNode.Kind.TABLES, catalog, null);
                a.expand(NavNode.Kind.TABLE, catalog, "articoli");
                Map<String, String> indexes = labels(a, catalog, NavNode.Kind.INDEXES);
                Map<String, String> columns = labels(a, catalog, NavNode.Kind.COLUMNS);

                // tutti gli indici del server, nessuno escluso
                TreeSet<String> onServer = new TreeSet<>();
                try (var c = server.connect(); var st = c.createStatement(); var rs = st.executeQuery(
                        "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '"
                        + catalog + "' AND TABLE_NAME = 'articoli'")) {
                    while (rs.next()) {
                        onServer.add(rs.getString(1));
                    }
                }
                assertEquals(onServer, new TreeSet<>(indexes.keySet()), "INDICI = information_schema.STATISTICS");
                String folder = fromEdt(() -> a.nav().labelOf(a.nav().findChild(
                        a.nav().find(NavNode.Kind.TABLE, catalog, "articoli"), NavNode.Kind.INDEXES)));
                assertEquals(Texts.get("nav.section", Texts.get("nav.folder.indexes").toUpperCase(), 6), folder);

                assertTrue(indexes.get("ft_testo").endsWith(Texts.get("nav.index.feature.fulltext")),
                        indexes.get("ft_testo"));
                assertTrue(indexes.get("ix_titolo_prefisso").contains("(titolo(20))")
                        && indexes.get("ix_titolo_prefisso").endsWith(Texts.get("nav.index.feature.prefix")),
                        indexes.get("ix_titolo_prefisso"));
                assertTrue(indexes.get("ix_anno_desc").contains("(anno DESC)")
                        && indexes.get("ix_anno_desc").endsWith(Texts.get("nav.index.feature.descending")),
                        indexes.get("ix_anno_desc"));
                assertEquals("uq_codice  (codice) " + Texts.get("nav.index.unique"), indexes.get("uq_codice"));
                assertEquals("PRIMARY  (id) " + Texts.get("nav.index.primary"), indexes.get("PRIMARY"));
                // i nodi in sola lettura portano il loro ReadOnlyIndex, quelli modificabili l'IndexDef
                List<NavNode> nodes = fromEdt(() -> a.nav().childrenOf(a.nav().findChild(
                        a.nav().find(NavNode.Kind.TABLE, catalog, "articoli"), NavNode.Kind.INDEXES)));
                assertEquals(3, nodes.stream().filter(n -> n.data() instanceof ReadOnlyIndex).count());
                assertEquals(3, nodes.stream().filter(n -> n.data() instanceof IndexDef).count());

                assertEquals("codice  INT(6) " + Texts.get("nav.column.unsigned") + " "
                        + Texts.get("nav.column.zerofill") + " " + Texts.get("nav.column.notNull"), columns.get("codice"));
                assertEquals("id  INT " + Texts.get("nav.column.unsigned") + " " + Texts.get("nav.column.notNull") + " "
                        + Texts.get("nav.column.autoIncrement"), columns.get("id"));
                assertEquals("AI", Texts.get("nav.column.autoIncrement"), "l'abbreviazione viene dalle risorse");
                assertEquals("attivo  TINYINT(1) " + Texts.get("nav.column.notNull"), columns.get("attivo"));
                assertEquals("dati  JSON", columns.get("dati"));
                indexes.forEach((k, v) -> ev.append("  indice ").append(v).append('\n'));
                columns.forEach((k, v) -> ev.append("  colonna ").append(v).append('\n'));
                Probe.paintWindow("step3", a.frame(), "navigatore-indici-speciali-" + server.id() + ".png");
            }
        } finally {
            server.dropQuietly(catalog);
        }
        Probe.writeText("step3", "navigatore-indici-speciali-" + server.id() + ".txt", ev.toString());
    }

    /** Nome → testo mostrato, per i figli della cartella indicata di «articoli». */
    private static Map<String, String> labels(ClientApp a, String catalog, NavNode.Kind folder) {
        return fromEdt(() -> {
            TreePath fp = a.nav().findChild(a.nav().find(NavNode.Kind.TABLE, catalog, "articoli"), folder);
            DefaultMutableTreeNode f = (DefaultMutableTreeNode) fp.getLastPathComponent();
            Map<String, String> out = new TreeMap<>();
            List<TreePath> children = new ArrayList<>();
            for (int i = 0; i < f.getChildCount(); i++) {
                children.add(fp.pathByAddingChild(f.getChildAt(i)));
            }
            for (TreePath p : children) {
                NavNode n = (NavNode) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
                out.put(n.name(), a.nav().labelOf(p));
            }
            return out;
        });
    }
}
