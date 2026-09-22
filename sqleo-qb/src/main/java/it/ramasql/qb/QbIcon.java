/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

/**
 * Icone che il query builder chiede all'applicazione (in SQLeo: le costanti ICON_* della classe Application).
 * Le icone predefinite sono disegnate nel codice da {@link QbDrawnIcon} (dal 2026-09-22: sostituiscono le PNG
 * ereditate da SQLeo, di origine non dichiarata).
 */
public enum QbIcon {
    /** Intestazione di una tabella nel diagramma. */
    DIAG_TABLE,
    /** Campo di chiave primaria nel diagramma. */
    DIAG_FIELD,
    /** Tabella derivata (sottoquery nel FROM) nel diagramma. */
    DIAG_QUERY,
    /** Tabella o vista nell'elenco degli oggetti. */
    DIAG_OBJECT,
    /** Nodo query (principale, sottoquery, UNION) nell'albero della query. */
    QB_QUERY,
    /** Clausola WHERE; campo usato in una condizione WHERE. */
    QB_WHERE,
    /** Campo di chiave primaria usato in una condizione WHERE. */
    QB_KEYANDWHERE,
    /** Clausola FROM. */
    QB_FROM,
    /** Clausola SELECT. */
    QB_SELECT,
    /** Tabella nell'albero della query. */
    QB_TABLE,
    /** Clausola ORDER BY. */
    QB_ORDER,
    /** Clausola GROUP BY. */
    QB_GROUP,
    /** Clausola HAVING. */
    QB_HAVING,
    /** Colonna. */
    QB_FIELD,
    /** Espressione (funzione, calcolo, aggregato). */
    QB_EXPR,
    /** Nodo generico (condizione, voce senza tipo proprio). */
    QB_FOLDER
}
