/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 *
 * Riscritta da zero il 2026-09-22 per sostituire un file GPL-2.0-only ereditato da SQLeo; scritta a partire
 * dall'uso nel resto del modulo, senza consultare l'originale.
 */
package com.sqleo.querybuilder;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax._ReservedWords;

import it.ramasql.qb.QbIcon;
import it.ramasql.qb.QbRuntime;

/**
 * Disegna i nodi dell'albero della query ({@link ViewBrowser}): per ogni nodo un'icona che ne dice il tipo
 * (query, clausola, tabella, campo, espressione, condizione) e un testo. Il testo è quello del nodo
 * ({@code toString}), tranne le etichette interne del modello ({@code ROOTQUERY}, {@code SUBQUERY}) che diventano
 * testi d'interfaccia tramite la facciata ({@link it.ramasql.qb.QbHost#text}). Le icone si chiedono alla facciata
 * a ogni disegno, così seguono l'host e la scala correnti.
 */
public class QueryModelTreeCellRenderer extends DefaultTreeCellRenderer {

    /** Etichetta del nodo della query principale, assegnata da {@link ViewBrowser}. */
    static final String ROOT_QUERY_LABEL = "ROOTQUERY";
    /** Etichetta delle sottoquery, assegnata da {@link ViewBrowser} e {@link BrowserItems}. */
    static final String SUBQUERY_LABEL = "SUBQUERY";

    public QueryModelTreeCellRenderer() {
        super();
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        setIcon(iconFor(value));
        setText(textFor(value));
        return this;
    }

    /** Icona del nodo; mai {@code null}. */
    static Icon iconFor(Object node) {
        return QbRuntime.host().icon(iconIdFor(node));
    }

    /** Tipo di icona del nodo. */
    static QbIcon iconIdFor(Object node) {
        if (node instanceof BrowserItems.AbstractQueryTreeItem) {
            return QbIcon.QB_QUERY;
        }
        if (node instanceof BrowserItems.TableTreeItem) {
            return QbIcon.QB_TABLE;
        }
        if (node instanceof BrowserItems.FromTreeItem) {
            return QbIcon.QB_FROM;
        }
        if (node instanceof BrowserItems.ClauseTreeItem) {
            return clauseIcon(String.valueOf(((BrowserItems.ClauseTreeItem) node).getUserObject()));
        }
        Object user = node instanceof BrowserItems.DefaultTreeItem ? ((BrowserItems.DefaultTreeItem) node).getUserObject() : node;
        return tokenIcon(user);
    }

    private static QbIcon clauseIcon(String label) {
        if (label.startsWith(_ReservedWords.SELECT)) {
            return QbIcon.QB_SELECT;
        }
        if (label.startsWith(_ReservedWords.WHERE)) {
            return QbIcon.QB_WHERE;
        }
        if (label.startsWith(_ReservedWords.GROUP_BY)) {
            return QbIcon.QB_GROUP;
        }
        if (label.startsWith(_ReservedWords.HAVING)) {
            return QbIcon.QB_HAVING;
        }
        if (label.startsWith(_ReservedWords.ORDER_BY)) {
            return QbIcon.QB_ORDER;
        }
        return QbIcon.QB_FOLDER;
    }

    private static QbIcon tokenIcon(Object token) {
        if (token instanceof QueryTokens.Table) {
            return QbIcon.QB_TABLE;
        }
        if (token instanceof QueryTokens.Column) {
            return QbIcon.QB_FIELD;
        }
        if (token instanceof QueryTokens.Group) {
            return expressionIcon(((QueryTokens.Group) token).getExpression());
        }
        if (token instanceof QueryTokens.Sort) {
            return expressionIcon(((QueryTokens.Sort) token).getExpression());
        }
        if (token instanceof QueryTokens._Expression) {
            return QbIcon.QB_EXPR;
        }
        // condizioni e nodi senza tipo proprio: punto elenco
        return QbIcon.QB_FOLDER;
    }

    private static QbIcon expressionIcon(QueryTokens._Expression expr) {
        return expr instanceof QueryTokens.Column ? QbIcon.QB_FIELD : QbIcon.QB_EXPR;
    }

    /** Testo del nodo; mai vuoto. */
    static String textFor(Object node) {
        String text = node == null ? null : node.toString();
        if (text == null || text.isBlank()) {
            return "?";
        }
        if (node instanceof BrowserItems.ConditionQueryTreeItem) {
            String suffix = "(" + SUBQUERY_LABEL + ")";
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length()) + "(" + subqueryText().toLowerCase() + ")";
            }
        } else if (node instanceof BrowserItems.AbstractQueryTreeItem) {
            if (text.equals(ROOT_QUERY_LABEL)) {
                text = QbRuntime.host().text("querybuilder.tree.query", "Query");
            } else if (text.equals(SUBQUERY_LABEL) || text.startsWith(SUBQUERY_LABEL + " ")) {
                text = subqueryText() + text.substring(SUBQUERY_LABEL.length());
            }
        }
        return text;
    }

    private static String subqueryText() {
        return QbRuntime.host().text("querybuilder.tree.subquery", "Subquery");
    }
}
