/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax._ReservedWords;

import it.ramasql.qb.QbSql;

/**
 * Codice NOSTRO (non derivato da SQLeo), nel pacchetto del query builder per raggiungerne i membri «di pacchetto».
 * È la facciata operativa del diagramma per il programma (Step 7, {@code BUG-006}): le stesse operazioni che l'utente fa
 * con il mouse — aggiungere una tabella, spuntare una colonna, cambiare il tipo di un join, aggiungere un filtro, un
 * raggruppamento, un ordinamento, aprire una sottoquery — senza le finestre di dialogo, più la lettura di ciò che il
 * diagramma mostra. La usano la scheda «Query visiva» e i test d'interfaccia (prima c'era una classe di prova nel modulo
 * dei test d'integrazione, nello stesso pacchetto: un pacchetto diviso su due moduli, ora eliminato).
 *
 * <p><b>Regola ({@code BUG-005}):</b> un testo SQL arriva al diagramma solo passando da {@link QbSql#check}: se non è
 * rappresentabile, il diagramma non lo riceve. {@link #load} accetta solo un esito di {@code check} rappresentabile.
 *
 * <p>Tutti i metodi vanno chiamati sull'EDT.
 */
public final class QbOperations {

    /** Che righe tiene un join: solo quelle che corrispondono, o tutte quelle di un lato. */
    public enum JoinKind {
        /** {@code INNER JOIN}: solo le righe che corrispondono. */
        MATCHING_ONLY(QueryTokens.Join.INNER),
        /** Tutte le righe della tabella «primaria» del join (la prima disegnata). */
        ALL_FROM_PRIMARY(QueryTokens.Join.LEFT_OUTER),
        /** Tutte le righe dell'altra tabella. */
        ALL_FROM_FOREIGN(QueryTokens.Join.RIGHT_OUTER);

        final int type;

        JoinKind(int type) {
            this.type = type;
        }

        static JoinKind of(int type) {
            for (JoinKind k : values()) {
                if (k.type == type) {
                    return k;
                }
            }
            return MATCHING_ONLY;
        }
    }

    /** Un join del diagramma: {@code primaryTable.primaryColumn = foreignTable.foreignColumn}. */
    public record JoinInfo(String name, String primaryTable, String primaryColumn, String foreignTable,
                           String foreignColumn, JoinKind kind, String description) {
    }

    /** Un nodo di query dell'albero a sinistra (la radice o una sottoquery), in ordine di visita in profondità. */
    public record QueryNode(int index, int level, String type, String label, String clause) {
    }

    private QbOperations() {
    }

    // ---------------------------------------------------------------- testo ⇄ diagramma

    /** L'SQL del diagramma, a capo per clausola; vuoto se nel diagramma non c'è ancora nessuna tabella. */
    public static String sql(QueryBuilder qb) {
        QueryModel model = qb.getQueryModel();
        if (model.getQueryExpression().getQuerySpecification().getFromClause().length == 0) {
            return "";
        }
        return model.toString(true);
    }

    /** Il modello nomina almeno una tabella nel FROM (senza, nel diagramma non c'è niente da disegnare). */
    public static boolean hasTables(QueryModel model) {
        return model != null && model.getQueryExpression().getQuerySpecification().getFromClause().length > 0;
    }

    /** Svuota il diagramma. */
    public static void clear(QueryBuilder qb) {
        qb.setQueryModel(new QueryModel());
    }

    /**
     * Ricostruisce il diagramma da un testo già verificato.
     *
     * @throws IllegalArgumentException se {@code check} non è rappresentabile (regola di {@code BUG-005})
     */
    public static void load(QueryBuilder qb, QbSql.Result check) {
        Objects.requireNonNull(check, "check");
        if (!check.representable() || check.model() == null) {
            throw new IllegalArgumentException("Query non rappresentabile: il diagramma non la riceve");
        }
        qb.setQueryModel(check.model());
    }

    /** Verifica il testo e, se rappresentabile, ricostruisce il diagramma; altrimenti non tocca nulla. */
    public static QbSql.Result setSql(QueryBuilder qb, String sql) {
        QbSql.Result check = QbSql.check(sql);
        if (check.representable()) {
            load(qb, check);
        }
        return check;
    }

    // ---------------------------------------------------------------- tabelle e colonne

    /**
     * Aggiunge una tabella (o una vista) al diagramma, come il doppio clic sull'elenco degli oggetti o il trascinamento:
     * colonne dai metadati, join proposti dalle chiavi esterne verso le tabelle già presenti.
     *
     * @return {@code true} se nel diagramma c'è un'entità in più
     */
    public static boolean addTable(QueryBuilder qb, String table) {
        int before = qb.diagram.getEntities().length;
        DiagramLoader.run(DiagramLoader.DEFAULT, qb, new QueryTokens.Table(null, table), true);
        return qb.diagram.getEntities().length > before;
    }

    /** Toglie una tabella dal diagramma, come la «×» della sua intestazione. */
    public static void removeTable(QueryBuilder qb, String table) {
        entity(qb, table).doDefaultCloseAction();
    }

    /** Nomi delle tabelle nel diagramma visibile (il livello di query selezionato nell'albero). */
    public static List<String> tables(QueryBuilder qb) {
        List<String> out = new ArrayList<>();
        for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
            out.add(e.getQueryToken().getName());
        }
        return out;
    }

    /** Entità del diagramma visibile: {@code tabella alias} per le tabelle, {@code (derivata) alias} per le derivate. */
    public static List<String> entities(QueryBuilder qb) {
        List<String> out = new ArrayList<>();
        for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
            QueryTokens.Table t = e.getQueryToken();
            out.add(e instanceof DiagramQuery ? "(derivata) " + t.getAlias()
                    : t.getName() + (t.getAlias() == null ? "" : " " + t.getAlias()));
        }
        return out;
    }

    /** I componenti delle entità del diagramma visibile (per le prove di resa: testo tagliato, bordi). */
    public static List<javax.swing.JInternalFrame> entityFrames(QueryBuilder qb) {
        return List.of(qb.diagram.getEntities());
    }

    /** Colonne dell'entità, nell'ordine in cui compaiono. */
    public static List<String> columns(QueryBuilder qb, String table) {
        List<String> out = new ArrayList<>();
        for (java.awt.Component c : entity(qb, table).getFields().getComponents()) {
            if (c instanceof DiagramField f) {
                out.add(f.getLabel());
            }
        }
        return out;
    }

    /** Le colonne spuntate dell'entità (quelle nella SELECT), nell'ordine in cui compaiono. */
    public static List<String> selectedColumns(QueryBuilder qb, String table) {
        List<String> out = new ArrayList<>();
        for (java.awt.Component c : entity(qb, table).getFields().getComponents()) {
            if (c instanceof DiagramField f && f.isSelected()) {
                out.add(f.getLabel());
            }
        }
        return out;
    }

    /** Spunta (o toglie la spunta a) una colonna: come il clic dell'utente sulla casella, entra o esce dalla SELECT. */
    public static void select(QueryBuilder qb, String table, String column, boolean selected) {
        field(qb, table, column).setSelected(selected);
    }

    /** Toglie la spunta a tutte le colonne dell'entità (voce «Deseleziona tutto» della sua intestazione). */
    public static void deselectAll(QueryBuilder qb, String table) {
        entity(qb, table).setColumnSelections(false);
    }

    // ---------------------------------------------------------------- join

    public static List<JoinInfo> joins(QueryBuilder qb) {
        List<JoinInfo> out = new ArrayList<>();
        for (DiagramRelation r : qb.diagram.getRelations()) {
            QueryTokens.Join j = r.querytoken;
            out.add(new JoinInfo(r.getName(), j.getPrimary().getTable().getName(), j.getPrimary().getName(),
                    j.getForeign().getTable().getName(), j.getForeign().getName(), JoinKind.of(j.getType()),
                    r.joinDescription()));
        }
        return out;
    }

    /** Cambia il tipo del join di posizione {@code index}: come la scelta nel menu del nodo del join. */
    public static void setJoinKind(QueryBuilder qb, int index, JoinKind kind) {
        DiagramRelation r = qb.diagram.getRelations()[index];
        r.setValues(kind.type, r.querytoken.getCondition().getOperator());
    }

    /** Il menu che si apre con un clic sul nodo del join di posizione {@code index} (le stesse voci che vede l'utente). */
    public static javax.swing.JPopupMenu joinMenu(QueryBuilder qb, int index) {
        return qb.diagram.getRelations()[index].joinMenu();
    }

    /** Il join tra due tabelle (in qualunque verso); {@code -1} se non c'è. */
    public static int joinIndex(QueryBuilder qb, String tableA, String tableB) {
        List<JoinInfo> all = joins(qb);
        for (int i = 0; i < all.size(); i++) {
            JoinInfo j = all.get(i);
            if ((j.primaryTable().equalsIgnoreCase(tableA) && j.foreignTable().equalsIgnoreCase(tableB))
                    || (j.primaryTable().equalsIgnoreCase(tableB) && j.foreignTable().equalsIgnoreCase(tableA))) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- clausole

    /** Come «Aggiungi espressione…»: {@code FUNZIONE(tabella.colonna) AS alias} nella lista SELECT. */
    public static void addExpression(QueryBuilder qb, String function, String table, String column, String alias) {
        String argument = column == null ? "*" : field(qb, table, column).getQueryToken().getIdentifier();
        QueryTokens.DefaultExpression token = new QueryTokens.DefaultExpression(function + "(" + argument + ")");
        token.setAlias(alias);
        qb.browser.addSelectList(token);
    }

    /** Come «Aggiungi condizione WHERE…» del menu del campo: {@code tabella.colonna <op> <valore>}, unite con AND. */
    public static void addWhere(QueryBuilder qb, String table, String column, String operator, String value) {
        DiagramField f = field(qb, table, column);
        QueryTokens.Condition token = new QueryTokens.Condition();
        token.setLeft(new QueryTokens.DefaultExpression(f.getQueryToken().getIdentifier()));
        token.setOperator(operator);
        token.setRight(new QueryTokens.DefaultExpression(value));
        if (qb.browser.getQuerySpecification().getWhereClause().length > 0) {
            token.setAppend(_ReservedWords.AND);
        }
        qb.browser.addWhereClause(token);
        f.setWhereIcon();
    }

    /**
     * Come «Aggiungi condizione WHERE…» con la casella «SUBQUERY»: {@code [tabella.colonna] <op> (sottoquery vuota)}.
     * La sottoquery compare come nodo dell'albero: si apre con {@link #selectNode} e si costruisce come una query a sé.
     * La maschera ereditata offre la sottoquery solo con IN ed EXISTS; qui vale anche per un confronto
     * ({@code prezzo > (SELECT AVG…)}), che il modello e il parser rappresentano già.
     *
     * @param table    tabella della colonna a sinistra; {@code null} per EXISTS / NOT EXISTS
     * @param operator {@code IN}, {@code NOT IN}, {@code EXISTS}, {@code NOT EXISTS}, {@code =}, {@code >}…
     */
    public static void addSubqueryCondition(QueryBuilder qb, String table, String column, String operator) {
        QueryTokens.Condition token = new QueryTokens.Condition();
        if (qb.browser.getQuerySpecification().getWhereClause().length > 0) {
            token.setAppend(_ReservedWords.AND);
        }
        DiagramField f = table == null ? null : field(qb, table, column);
        token.setLeft(f == null ? null : new QueryTokens.DefaultExpression(f.getQueryToken().getIdentifier()));
        token.setOperator(operator);
        token.setRight(new com.sqleo.querybuilder.syntax.SubQuery());
        qb.browser.addWhereClause(token);
        if (f != null) {
            f.setWhereIcon();
        }
    }

    /** Come «Aggiungi sottoquery» sul ramo FROM: una tabella derivata vuota nel diagramma, con il suo nodo nell'albero. */
    public static void addDerivedTable(QueryBuilder qb) {
        com.sqleo.querybuilder.syntax.DerivedTable token = new com.sqleo.querybuilder.syntax.DerivedTable();
        qb.diagram.addEntity(new DiagramQuery(qb, token));
    }

    /** Come «Aggiungi condizione HAVING…»: {@code espressione <op> <valore>}. */
    public static void addHaving(QueryBuilder qb, String expression, String operator, String value) {
        QueryTokens.Condition token = new QueryTokens.Condition();
        token.setLeft(new QueryTokens.DefaultExpression(expression));
        token.setOperator(operator);
        token.setRight(new QueryTokens.DefaultExpression(value));
        qb.browser.addHavingClause(token);
    }

    /** Come «Aggiungi a GROUP BY»: il token è la colonna stessa. */
    public static void addGroupBy(QueryBuilder qb, String table, String column) {
        qb.browser.addGroupByClause(new QueryTokens.Group(field(qb, table, column).getQueryToken()));
    }

    /** Come «Aggiungi a ORDER BY». */
    public static void addOrderBy(QueryBuilder qb, String table, String column, boolean ascending) {
        qb.browser.addOrderByClause(new QueryTokens.Sort(field(qb, table, column).getQueryToken(), ascending));
    }

    /** Ordinamento su un'espressione già nella SELECT (es. l'alias di un conteggio). */
    public static void addOrderByExpression(QueryBuilder qb, String expression, boolean ascending) {
        qb.browser.addOrderByClause(new QueryTokens.Sort(new QueryTokens.DefaultExpression(expression), ascending));
    }

    // ---------------------------------------------------------------- query nidificate

    /** Tutti i nodi di query dell'albero (indice 0 = la query principale). */
    public static List<QueryNode> nodes(QueryBuilder qb) {
        List<QueryNode> out = new ArrayList<>();
        for (BrowserItems.AbstractQueryTreeItem item : queryItems(qb)) {
            int level = 0;
            for (javax.swing.tree.TreeNode p = item.getParent(); p != null; p = p.getParent()) {
                if (p instanceof BrowserItems.AbstractQueryTreeItem) {
                    level++;
                }
            }
            String clause = item.getParent() == null ? "" : String.valueOf(item.getParent());
            out.add(new QueryNode(out.size(), level, item.getClass().getSimpleName(), String.valueOf(item), clause));
        }
        return out;
    }

    /** Apre nel diagramma il nodo di query di indice dato, come il clic dell'utente sull'albero. */
    public static void selectNode(QueryBuilder qb, int index) {
        qb.browser.setSelectedItem(queryItems(qb).get(index));
    }

    /** SQL della sola (sotto)query del nodo aperto nel diagramma. */
    public static String sqlOfSelectedNode(QueryBuilder qb) {
        return qb.browser.getQueryExpression().toString();
    }

    private static List<BrowserItems.AbstractQueryTreeItem> queryItems(QueryBuilder qb) {
        List<BrowserItems.AbstractQueryTreeItem> out = new ArrayList<>();
        collect(qb.browser.getRootQueryItem(), out);
        return out;
    }

    private static void collect(javax.swing.tree.TreeNode n, List<BrowserItems.AbstractQueryTreeItem> out) {
        if (n instanceof BrowserItems.AbstractQueryTreeItem q) {
            out.add(q);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collect(n.getChildAt(i), out);
        }
    }

    // ---------------------------------------------------------------- elenco degli oggetti e componenti

    /** I nomi mostrati nell'elenco delle tabelle e viste a sinistra del diagramma. */
    public static List<String> objects(QueryBuilder qb) {
        return qb.objects.objectNames();
    }

    /** L'elenco delle tabelle e viste (da cui si trascina, o si fa doppio clic, per aggiungere al diagramma). */
    public static javax.swing.JList<?> objectList(QueryBuilder qb) {
        return qb.objects.objectsList();
    }

    /** Rilegge l'elenco delle tabelle e viste (dopo che il catalogo è cambiato: una vista nuova, una tabella in più). */
    public static void refreshObjects(QueryBuilder qb) {
        try {
            qb.objects.onConnectionChanged();
        } catch (java.sql.SQLException e) {
            qb.getHost().alert(e.getMessage());
        }
    }

    /** La maschera della condizione permette la sottoquery con questo operatore? (come la casella «SUBQUERY»). */
    public static boolean conditionMaskAllowsSubquery(QueryBuilder qb, String operator) {
        QueryTokens.Condition c = new QueryTokens.Condition();
        c.setLeft(new QueryTokens.DefaultExpression("x"));
        c.setOperator(operator);
        c.setRight(new QueryTokens.DefaultExpression("1"));
        MaskCondition mask = new MaskCondition(c, qb);
        mask.onShow();
        return mask.isSubqueryEnabled();
    }

    /**
     * Le maschere del query builder (join, condizione, alias, espressione) già riempite come quando si aprono, ma
     * senza mostrarle in una finestra modale: servono alle prove di resa (testi tagliati alle varie scale). Richiede
     * almeno una tabella e un join nel diagramma.
     */
    public static List<javax.swing.JComponent> masksForRendering(QueryBuilder qb) {
        List<javax.swing.JComponent> out = new ArrayList<>();
        DiagramRelation[] relations = qb.diagram.getRelations();
        if (relations.length > 0) {
            MaskJoin join = new MaskJoin(relations[0], qb);
            join.onShow();
            join.setName("MaskJoin");
            out.add(join);
        }
        QueryTokens.Condition condition = new QueryTokens.Condition();
        DiagramAbstractEntity[] present = qb.diagram.getEntities();
        String left = present.length > 0 && present[0].getFields().getComponentCount() > 0
                ? ((DiagramField) present[0].getFields().getComponent(0)).getQueryToken().getIdentifier() : "x";
        condition.setLeft(new QueryTokens.DefaultExpression(left));
        condition.setOperator(">");
        condition.setRight(new QueryTokens.DefaultExpression("0"));
        MaskCondition cond = new MaskCondition(condition, qb);
        cond.onShow();
        cond.setName("MaskCondition");
        out.add(cond);
        DiagramAbstractEntity[] entities = qb.diagram.getEntities();
        if (entities.length > 0) {
            MaskAlias alias = new MaskAlias(entities[0].getQueryToken(), qb);
            alias.onShow();
            alias.setName("MaskAlias");
            out.add(alias);
        }
        MaskExpression expr = new MaskExpression(new QueryTokens.DefaultExpression("COUNT(*)"), qb);
        expr.onShow();
        expr.setName("MaskExpression");
        out.add(expr);
        return out;
    }

    /** L'albero della query (per le prove di resa). */
    public static javax.swing.JComponent queryTree(QueryBuilder qb) {
        return qb.browser;
    }

    /** Il diagramma (per le prove di resa). */
    public static javax.swing.JComponent diagram(QueryBuilder qb) {
        return qb.diagram;
    }

    // ---------------------------------------------------------------- aiutanti

    private static DiagramEntity entity(QueryBuilder qb, String table) {
        for (DiagramAbstractEntity e : qb.diagram.getEntities()) {
            if (e instanceof DiagramEntity de && (table.equalsIgnoreCase(e.getQueryToken().getName())
                    || table.equalsIgnoreCase(e.getQueryToken().getAlias()))) {
                return de;
            }
        }
        throw new IllegalStateException("tabella non presente nel diagramma: " + table);
    }

    private static DiagramField field(QueryBuilder qb, String table, String column) {
        DiagramField f = entity(qb, table).getField(column);
        if (f == null) {
            throw new IllegalStateException("colonna non presente: " + table + "." + column);
        }
        return f;
    }
}
