/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JTree;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.sqleo.querybuilder.syntax.DerivedTable;
import com.sqleo.querybuilder.syntax.QueryExpression;
import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax.SubQuery;
import com.sqleo.querybuilder.syntax._ReservedWords;

import it.ramasql.qb.BasicQbHost;
import it.ramasql.qb.QbHost;
import it.ramasql.qb.QbIcon;
import it.ramasql.qb.QbRuntime;

/** Renderer dell'albero della query, riscritto da zero il 2026-09-22: ogni tipo di nodo ha icona e testo. */
@Tag("step1")
class QueryModelTreeCellRendererTest {

    private QbHost prima;

    @BeforeEach
    void hostDiBase() {
        prima = QbRuntime.host();
        QbRuntime.setHost(new BasicQbHost());
    }

    @AfterEach
    void ripristina() {
        QbRuntime.setHost(prima);
    }

    /** Un nodo per ciascun tipo che {@link ViewBrowser} e {@link BrowserItems} mettono nell'albero, con l'icona attesa. */
    private static Map<Object, QbIcon> nodi() {
        QueryTokens.Table libri = new QueryTokens.Table(null, "libri");
        QueryTokens.Column titolo = new QueryTokens.Column(libri, "titolo");
        QueryTokens.DefaultExpression conta = new QueryTokens.DefaultExpression("COUNT(*)");
        SubQuery sotto = new SubQuery();
        DerivedTable derivata = new DerivedTable();
        derivata.setAlias("d");

        Map<Object, QbIcon> m = new LinkedHashMap<>();
        m.put(new BrowserItems.DefaultTreeItem("QUERY-TREE-MODEL"), QbIcon.QB_FOLDER);
        m.put(new BrowserItems.QueryTreeItem("ROOTQUERY", new QueryExpression()), QbIcon.QB_QUERY);
        m.put(new BrowserItems.QueryTreeItem("SUBQUERY", new SubQuery()), QbIcon.QB_QUERY);
        m.put(new BrowserItems.QueryTreeItem("SUBQUERY s1", sotto), QbIcon.QB_QUERY);
        m.put(new BrowserItems.DiagramQueryTreeItem(derivata), QbIcon.QB_QUERY);
        m.put(new BrowserItems.UnionQueryTreeItem(new QueryExpression()), QbIcon.QB_QUERY);
        m.put(new BrowserItems.ConditionQueryTreeItem(new QueryTokens.Condition(titolo, "IN", new SubQuery())), QbIcon.QB_QUERY);
        m.put(new BrowserItems.TableTreeItem(libri), QbIcon.QB_TABLE);
        BrowserItems.TableTreeItem unita = new BrowserItems.TableTreeItem(new QueryTokens.Table("b", "autori"));
        unita.joined();
        m.put(unita, QbIcon.QB_TABLE);
        m.put(new BrowserItems.FromTreeItem(), QbIcon.QB_FROM);
        BrowserItems.FromTreeItem fromAttivo = new BrowserItems.FromTreeItem();
        fromAttivo.setSelected(true);
        m.put(fromAttivo, QbIcon.QB_FROM);
        m.put(new BrowserItems.ClauseTreeItem(_ReservedWords.SELECT), QbIcon.QB_SELECT);
        m.put(new BrowserItems.ClauseTreeItem(_ReservedWords.SELECT + " " + _ReservedWords.DISTINCT), QbIcon.QB_SELECT);
        m.put(new BrowserItems.ClauseTreeItem(_ReservedWords.WHERE), QbIcon.QB_WHERE);
        m.put(new BrowserItems.ClauseTreeItem(_ReservedWords.GROUP_BY), QbIcon.QB_GROUP);
        m.put(new BrowserItems.ClauseTreeItem(_ReservedWords.HAVING), QbIcon.QB_HAVING);
        m.put(new BrowserItems.ClauseTreeItem(_ReservedWords.ORDER_BY), QbIcon.QB_ORDER);
        m.put(new BrowserItems.DefaultTreeItem(titolo), QbIcon.QB_FIELD);
        m.put(new BrowserItems.DefaultTreeItem(conta), QbIcon.QB_EXPR);
        m.put(new BrowserItems.DefaultTreeItem(new QueryTokens.Condition(titolo, "=", new QueryTokens.DefaultExpression("'x'"))), QbIcon.QB_FOLDER);
        m.put(new BrowserItems.DefaultTreeItem(new QueryTokens.Group(titolo)), QbIcon.QB_FIELD);
        m.put(new BrowserItems.DefaultTreeItem(new QueryTokens.Group(conta)), QbIcon.QB_EXPR);
        m.put(new BrowserItems.DefaultTreeItem(new QueryTokens.Sort(titolo, false)), QbIcon.QB_FIELD);
        m.put(new BrowserItems.DefaultTreeItem(new QueryTokens.Sort(conta)), QbIcon.QB_EXPR);
        m.put(new BrowserItems.DefaultTreeItem(null), QbIcon.QB_FOLDER);
        return m;
    }

    @Test
    void ogniTipoDiNodoHaIconaETesto() {
        QueryModelTreeCellRenderer renderer = new QueryModelTreeCellRenderer();
        JTree tree = new JTree();
        int row = 0;
        for (Map.Entry<Object, QbIcon> e : nodi().entrySet()) {
            Object nodo = e.getKey();
            String tipo = nodo.getClass().getSimpleName() + " «" + nodo + "»";
            for (boolean selezionato : new boolean[] {false, true}) {
                Component c = renderer.getTreeCellRendererComponent(tree, nodo, selezionato, true, false, row++, selezionato);
                assertSame(renderer, c, tipo);
                JLabel label = (JLabel) c;
                assertNotNull(label.getIcon(), "icona mancante: " + tipo);
                assertTrue(label.getIcon().getIconWidth() > 0, "icona vuota: " + tipo);
                assertNotNull(label.getText(), "testo mancante: " + tipo);
                assertFalse(label.getText().isBlank(), "testo vuoto: " + tipo);
            }
            assertEquals(e.getValue(), QueryModelTreeCellRenderer.iconIdFor(nodo), "icona del nodo " + tipo);
            assertSame(QbRuntime.host().icon(e.getValue()), QueryModelTreeCellRenderer.iconFor(nodo), tipo);
        }
    }

    @Test
    void leEtichetteInterneDiventanoTestiItaliani() {
        assertEquals("Query", QueryModelTreeCellRenderer.textFor(new BrowserItems.QueryTreeItem("ROOTQUERY", new QueryExpression())));
        assertEquals("Sottoquery", QueryModelTreeCellRenderer.textFor(new BrowserItems.QueryTreeItem("SUBQUERY", new SubQuery())));
        assertEquals("Sottoquery s1", QueryModelTreeCellRenderer.textFor(new BrowserItems.QueryTreeItem("SUBQUERY s1", new SubQuery())));
        QueryTokens.Column titolo = new QueryTokens.Column(new QueryTokens.Table(null, "libri"), "titolo");
        String condizione = QueryModelTreeCellRenderer.textFor(
                new BrowserItems.ConditionQueryTreeItem(new QueryTokens.Condition(titolo, "IN", new SubQuery())));
        assertTrue(condizione.endsWith("(sottoquery)"), condizione);
        assertFalse(condizione.contains("SUBQUERY"), condizione);
        // le parole chiave SQL restano come sono
        assertEquals("WHERE", QueryModelTreeCellRenderer.textFor(new BrowserItems.ClauseTreeItem(_ReservedWords.WHERE)));
        assertEquals("[ FROM ]", QueryModelTreeCellRenderer.textFor(selezionato(new BrowserItems.FromTreeItem())));
        assertEquals("?", QueryModelTreeCellRenderer.textFor(new BrowserItems.DefaultTreeItem(null)));
    }

    private static BrowserItems.FromTreeItem selezionato(BrowserItems.FromTreeItem item) {
        item.setSelected(true);
        return item;
    }
}
