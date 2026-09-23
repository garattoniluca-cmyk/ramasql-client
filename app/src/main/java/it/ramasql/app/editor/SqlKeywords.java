/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parole chiave e funzioni di MariaDB/MySQL proposte dal completamento: quelle che si usano in aula, non l'elenco
 * completo del manuale (semplicità prima di completezza). Sono sintassi SQL, non testi dell'interfaccia.
 */
final class SqlKeywords {

    static final List<String> ALL = List.of(
            "ADD", "AFTER", "ALL", "ALTER", "AND", "AS", "ASC", "AUTO_INCREMENT", "AVG",
            "BEGIN", "BETWEEN", "BIGINT", "BLOB", "BOOLEAN", "BY",
            "CALL", "CASCADE", "CASE", "CHANGE", "CHAR", "CHARACTER", "CHECK", "COALESCE", "COLLATE", "COLUMN",
            "COMMENT", "CONCAT", "CONSTRAINT", "COUNT", "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_TIMESTAMP",
            "DATABASE", "DATE", "DATETIME", "DAY", "DECIMAL", "DECLARE", "DEFAULT", "DELETE", "DELIMITER", "DESC",
            "DESCRIBE", "DISTINCT", "DO", "DOUBLE", "DROP",
            "ELSE", "ELSEIF", "END", "ENGINE", "ENUM", "EXISTS", "EXPLAIN",
            "FALSE", "FLOAT", "FOREIGN", "FROM", "FULL", "FUNCTION",
            "GROUP", "GROUP_CONCAT",
            "HAVING",
            "IF", "IFNULL", "IGNORE", "IN", "INDEX", "INNER", "INSERT", "INT", "INTEGER", "INTERVAL", "INTO", "IS",
            "JOIN",
            "KEY",
            "LEFT", "LIKE", "LIMIT", "LOWER",
            "MAX", "MIN", "MODIFY", "MONTH",
            "NOT", "NOW", "NULL",
            "OFFSET", "ON", "OR", "ORDER", "OUTER",
            "PRIMARY", "PROCEDURE",
            "REFERENCES", "RENAME", "REPLACE", "RESTRICT", "RETURN", "RETURNS", "RIGHT", "ROUND",
            "SCHEMA", "SELECT", "SET", "SHOW", "SMALLINT", "SUBSTRING", "SUM",
            "TABLE", "TABLES", "TEXT", "THEN", "TIME", "TIMESTAMP", "TINYINT", "TRIGGER", "TRIM", "TRUE", "TRUNCATE",
            "UNION", "UNIQUE", "UNSIGNED", "UPDATE", "UPPER", "USE", "USING",
            "VALUES", "VARCHAR", "VIEW",
            "WHEN", "WHERE", "WHILE", "WITH",
            "YEAR");

    private static final Set<String> UPPER = ALL.stream().collect(Collectors.toUnmodifiableSet());

    private SqlKeywords() {
    }

    static boolean isKeyword(String word) {
        return word != null && UPPER.contains(word.toUpperCase(Locale.ROOT));
    }
}
