/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import java.util.ArrayList;
import java.util.List;

import com.sqleo.querybuilder.QbOperations;
import com.sqleo.querybuilder.QueryBuilder;

/**
 * Le operazioni sul diagramma usate dagli spike S2a e S2d, con i nomi di allora. Dallo Step 7 passano dalla facciata
 * del modulo {@code sqleo-qb} ({@link QbOperations}); prima c'era una classe di prova nel pacchetto del query builder
 * dentro questo modulo (un pacchetto diviso su due moduli, {@code BUG-006}), eliminata.
 */
final class QbProva {

    /** Un nodo di query dell'albero a sinistra (la radice o una sottoquery), in ordine di visita in profondità. */
    record NodoDiQuery(int indice, int livello, String tipo, String etichetta, String clausola) {
    }

    private QbProva() {
    }

    static List<String> tabelleNelDiagramma(QueryBuilder qb) {
        return QbOperations.tables(qb);
    }

    /** L'entità della tabella, o {@code null} se non è nel diagramma (qui basta sapere se c'è). */
    static Object entita(QueryBuilder qb, String tabella) {
        return QbOperations.tables(qb).stream().anyMatch(t -> t.equalsIgnoreCase(tabella)) ? tabella : null;
    }

    static List<String> colonne(QueryBuilder qb, String tabella) {
        return QbOperations.columns(qb, tabella);
    }

    /** Join presenti nel diagramma, nella forma {@code nomeFK: primaria.col = esterna.col}. */
    static List<String> join(QueryBuilder qb) {
        List<String> out = new ArrayList<>();
        for (QbOperations.JoinInfo j : QbOperations.joins(qb)) {
            out.add(j.name() + ": " + j.primaryTable() + "." + j.primaryColumn() + " = " + j.foreignTable() + "."
                    + j.foreignColumn());
        }
        return out;
    }

    static void seleziona(QueryBuilder qb, String tabella, String colonna) {
        QbOperations.select(qb, tabella, colonna, true);
    }

    static void aggiungiEspressione(QueryBuilder qb, String funzione, String tabella, String colonna, String alias) {
        QbOperations.addExpression(qb, funzione, tabella, colonna, alias);
    }

    static void aggiungiWhere(QueryBuilder qb, String tabella, String colonna, String operatore, String valore) {
        QbOperations.addWhere(qb, tabella, colonna, operatore, valore);
    }

    static void aggiungiGroupBy(QueryBuilder qb, String tabella, String colonna) {
        QbOperations.addGroupBy(qb, tabella, colonna);
    }

    static void aggiungiOrderBy(QueryBuilder qb, String tabella, String colonna, boolean crescente) {
        QbOperations.addOrderBy(qb, tabella, colonna, crescente);
    }

    static List<NodoDiQuery> nodiDiQuery(QueryBuilder qb) {
        return QbOperations.nodes(qb).stream()
                .map(n -> new NodoDiQuery(n.index(), n.level(), n.type(), n.label(), n.clause())).toList();
    }

    static void selezionaNodo(QueryBuilder qb, int indice) {
        QbOperations.selectNode(qb, indice);
    }

    static String sqlDelNodoSelezionato(QueryBuilder qb) {
        return QbOperations.sqlOfSelectedNode(qb);
    }

    static List<String> entitaNelDiagramma(QueryBuilder qb) {
        return QbOperations.entities(qb);
    }
}
