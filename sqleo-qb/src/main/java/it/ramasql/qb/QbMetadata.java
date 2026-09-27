/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.sql.SQLException;
import java.util.List;

/**
 * I metadati che servono al diagramma del query builder: elenco di tabelle e viste, colonne, chiavi esterne.
 *
 * <p>In SQLeo il query builder li leggeva da sé con {@code java.sql.DatabaseMetaData}: l'SQL lo componeva il driver e
 * non passava dal client, quindi non compariva da nessuna parte ({@code BUG-016}). Ora li chiede alla facciata
 * ({@link QbHost#metadata()}): il programma risponde con il suo canale dei metadati, lo stesso del navigatore;
 * {@link com.sqleo.querybuilder.JdbcQbMetadata} resta come ripiego per chi ha solo una connessione JDBC (prove del modulo).
 *
 * <p>In MariaDB e MySQL il «catalogo» è il database. {@code catalog} nullo = il catalogo corrente della facciata.
 */
public interface QbMetadata {

    /** Una colonna, nell'ordine della tabella. */
    record Column(String name, String type, boolean primaryKey) {
    }

    /** Una colonna di chiave esterna: {@code foreignTable.foreignColumn → primaryTable.primaryColumn}. */
    record ForeignKey(String name, String primaryTable, String primaryColumn, String foreignTable,
                      String foreignColumn) {
    }

    /** Tabelle del catalogo (non le viste), per nome. */
    List<String> tables(String catalog) throws SQLException;

    /** Viste del catalogo, per nome. */
    List<String> views(String catalog) throws SQLException;

    /**
     * Il nome di tabella o vista così come è scritto sul server (le maiuscole possono differire da quelle digitate);
     * {@code null} se non esiste.
     */
    String find(String catalog, String table) throws SQLException;

    /** Colonne di una tabella o di una vista; vuoto se non esiste. */
    List<Column> columns(String catalog, String table) throws SQLException;

    /** Le chiavi esterne DI questa tabella (verso le tabelle che referenzia). */
    List<ForeignKey> importedKeys(String catalog, String table) throws SQLException;

    /** Le chiavi esterne delle altre tabelle che referenziano questa. */
    List<ForeignKey> exportedKeys(String catalog, String table) throws SQLException;
}
