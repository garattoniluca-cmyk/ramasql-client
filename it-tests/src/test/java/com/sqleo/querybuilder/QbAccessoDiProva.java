/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import java.util.ArrayList;
import java.util.List;

import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax._ReservedWords;

/**
 * Codice NOSTRO (non derivato da SQLeo): sta in questo pacchetto solo per raggiungere, dai test d'integrazione,
 * i membri «di pacchetto» del query builder ereditato ({@code diagram}, {@code browser}). Fa le stesse chiamate
 * che fanno i menu contestuali del diagramma ({@code DiagramField.ActionAddWhere}, «add group by», «add order by»),
 * senza le finestre di dialogo: così lo spike S2a costruisce la query dal diagramma e non dal testo.
 *
 * <p>Nota per lo Step 7: queste operazioni andranno esposte dalla facciata di {@code sqleo-qb}; qui sono solo di prova.
 */
public final class QbAccessoDiProva {

    private QbAccessoDiProva() {
    }

    /** Entità (tabelle) presenti nel diagramma, per nome di tabella. */
    public static List<String> tabelleNelDiagramma(QueryBuilder qb) {
        List<String> nomi = new ArrayList<>();
        for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
            nomi.add(e.getQueryToken().getName());
        }
        return nomi;
    }

    /** Entità del diagramma per nome di tabella; {@code null} se non c'è. */
    public static DiagramEntity entita(QueryBuilder qb, String tabella) {
        return qb.diagram.getEntity(null, tabella);
    }

    /** Nomi delle colonne caricate dai metadati JDBC per la tabella. */
    public static List<String> colonne(QueryBuilder qb, String tabella) {
        List<String> nomi = new ArrayList<>();
        DiagramEntity e = entita(qb, tabella);
        for (java.awt.Component c : e.getFields().getComponents()) {
            if (c instanceof DiagramField f) {
                nomi.add(f.getLabel());
            }
        }
        return nomi;
    }

    /** Join presenti nel diagramma, nella forma {@code nomeFK: primaria.col = esterna.col}. */
    public static List<String> join(QueryBuilder qb) {
        List<String> out = new ArrayList<>();
        for (DiagramRelation r : qb.diagram.getRelations()) {
            out.add(r.getName() + ": " + r.querytoken.getPrimary().getIdentifier() + " = "
                    + r.querytoken.getForeign().getIdentifier());
        }
        return out;
    }

    /** Spunta la casella del campo: come il clic dell'utente, aggiunge la colonna alla lista SELECT. */
    public static void seleziona(QueryBuilder qb, String tabella, String colonna) {
        campo(qb, tabella, colonna).setSelected(true);
    }

    /** Come «add expression…» del menu del campo: {@code FUNZIONE(tabella.colonna) AS alias} nella lista SELECT. */
    public static void aggiungiEspressione(QueryBuilder qb, String funzione, String tabella, String colonna, String alias) {
        DiagramField f = campo(qb, tabella, colonna);
        QueryTokens.DefaultExpression token =
                new QueryTokens.DefaultExpression(funzione + "(" + f.getQueryToken().getIdentifier() + ")");
        token.setAlias(alias);
        qb.browser.addSelectList(token);
    }

    /** Come «add where condition…» del menu del campo (senza la maschera): {@code tabella.colonna <op> <valore>}. */
    public static void aggiungiWhere(QueryBuilder qb, String tabella, String colonna, String operatore, String valore) {
        DiagramField f = campo(qb, tabella, colonna);
        QueryTokens.Condition token = new QueryTokens.Condition();
        token.setLeft(new QueryTokens.DefaultExpression(f.getQueryToken().getIdentifier()));
        token.setOperator(operatore);
        token.setRight(new QueryTokens.DefaultExpression(valore));
        if (qb.browser.getQuerySpecification().getWhereClause().length > 0) {
            token.setAppend(_ReservedWords.AND);
        }
        qb.browser.addWhereClause(token);
        f.setWhereIcon();
    }

    /** Come «add to group-by» del menu dell'albero: il token è la colonna stessa. */
    public static void aggiungiGroupBy(QueryBuilder qb, String tabella, String colonna) {
        DiagramField f = campo(qb, tabella, colonna);
        qb.browser.addGroupByClause(new QueryTokens.Group(f.getQueryToken()));
    }

    /** Come «add to order-by» del menu dell'albero: il token è la colonna stessa. */
    public static void aggiungiOrderBy(QueryBuilder qb, String tabella, String colonna, boolean crescente) {
        DiagramField f = campo(qb, tabella, colonna);
        qb.browser.addOrderByClause(new QueryTokens.Sort(f.getQueryToken(), crescente));
    }

    // ------------------------------------------------------------------ query nidificate (spike S2d, parte grafica)

    /** Un nodo di query dell'albero a sinistra (la radice o una sottoquery), in ordine di visita in profondità. */
    public record NodoDiQuery(int indice, int livello, String tipo, String etichetta, String clausola) {
    }

    /**
     * Tutti i nodi di query dell'albero (indice 0 = la query principale): tipo = classe del nodo
     * ({@code QueryTreeItem}, {@code ConditionQueryTreeItem}, {@code DiagramQueryTreeItem}, …), livello = numero di
     * nodi di query antenati, clausola = etichetta del ramo che lo contiene (SELECT, [ FROM ], WHERE…).
     */
    public static List<NodoDiQuery> nodiDiQuery(QueryBuilder qb) {
        List<NodoDiQuery> out = new ArrayList<>();
        for (BrowserItems.AbstractQueryTreeItem item : itemDiQuery(qb)) {
            int livello = 0;
            for (javax.swing.tree.TreeNode p = item.getParent(); p != null; p = p.getParent()) {
                if (p instanceof BrowserItems.AbstractQueryTreeItem) {
                    livello++;
                }
            }
            String clausola = item.getParent() == null ? "" : String.valueOf(item.getParent());
            out.add(new NodoDiQuery(out.size(), livello, item.getClass().getSimpleName(), String.valueOf(item), clausola));
        }
        return out;
    }

    /** Seleziona nell'albero il nodo di query di indice dato, come il clic dell'utente: il diagramma passa a quel livello. */
    public static void selezionaNodo(QueryBuilder qb, int indice) {
        qb.browser.setSelectedItem(itemDiQuery(qb).get(indice));
    }

    /** SQL della sola (sotto)query del nodo selezionato, come la vede il query builder. */
    public static String sqlDelNodoSelezionato(QueryBuilder qb) {
        return qb.browser.getQueryExpression().toString();
    }

    /** Entità del diagramma visibile: {@code tabella alias} per le tabelle, {@code (derivata) alias} per le tabelle derivate. */
    public static List<String> entitaNelDiagramma(QueryBuilder qb) {
        List<String> out = new ArrayList<>();
        for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
            QueryTokens.Table t = e.getQueryToken();
            out.add(e instanceof DiagramQuery ? "(derivata) " + t.getAlias() : t.getName() + " " + t.getAlias());
        }
        return out;
    }

    private static List<BrowserItems.AbstractQueryTreeItem> itemDiQuery(QueryBuilder qb) {
        List<BrowserItems.AbstractQueryTreeItem> out = new ArrayList<>();
        raccogli(qb.browser.getRootQueryItem(), out);
        return out;
    }

    private static void raccogli(javax.swing.tree.TreeNode n, List<BrowserItems.AbstractQueryTreeItem> out) {
        if (n instanceof BrowserItems.AbstractQueryTreeItem q) {
            out.add(q);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            raccogli(n.getChildAt(i), out);
        }
    }

    private static DiagramField campo(QueryBuilder qb, String tabella, String colonna) {
        DiagramEntity e = entita(qb, tabella);
        if (e == null) {
            throw new IllegalStateException("tabella non presente nel diagramma: " + tabella);
        }
        DiagramField f = e.getField(colonna);
        if (f == null) {
            throw new IllegalStateException("colonna non caricata dai metadati: " + tabella + "." + colonna);
        }
        return f;
    }
}
