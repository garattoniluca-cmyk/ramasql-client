/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

import java.util.regex.Pattern;

import it.ramasql.core.CoreMessages;

/**
 * Generatori puri delle operazioni del navigatore su cataloghi, tabelle e viste (Step 3): nessun accesso al
 * server. Identificatori sempre tra backtick e qualificati con il catalogo (il navigatore sa sempre dove si trova
 * l'oggetto: l'istruzione vale qualunque sia il catalogo corrente). Per rieseguire il registro su un altro catalogo
 * c'è {@code SqlLog.ExportOptions.withoutCatalog}. Il testo di {@code SHOW CREATE} lo compone solo il canale dei
 * metadati ({@code MetadataReader.showCreateStatement}): una sola implementazione, quella che viene eseguita.
 */
public final class ObjectDdl {

    /** Nomi di charset e collation: solo lettere, cifre e «_» (non si mettono tra apici). */
    private static final Pattern CHARSET_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private ObjectDdl() {
    }

    /**
     * {@code CREATE DATABASE `n` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci}; charset e collation facoltativi
     * ({@code null} = predefiniti del server).
     */
    public static String createCatalog(String name, String charset, String collation) {
        StringBuilder sql = new StringBuilder("CREATE DATABASE ").append(SqlIdentifiers.quote(name));
        if (charset != null && !charset.isBlank()) {
            sql.append(" CHARACTER SET ").append(checkCharsetName(charset));
        }
        if (collation != null && !collation.isBlank()) {
            sql.append(" COLLATE ").append(checkCharsetName(collation));
        }
        return sql.toString();
    }

    /** {@code DROP DATABASE `n`} (senza {@code IF EXISTS}: se il catalogo non c'è, l'errore del server lo dice). */
    public static String dropCatalog(String name) {
        return "DROP DATABASE " + SqlIdentifiers.quote(name);
    }

    /** {@code RENAME TABLE `c`.`vecchio` TO `c`.`nuovo`}. */
    public static String renameTable(String catalog, String oldName, String newName) {
        return "RENAME TABLE " + SqlIdentifiers.qualified(catalog, oldName) + " TO "
                + SqlIdentifiers.qualified(catalog, newName);
    }

    /** {@code TRUNCATE TABLE `c`.`t`}: svuota la tabella e riporta a 1 l'AUTO_INCREMENT. */
    public static String truncateTable(String catalog, String table) {
        return "TRUNCATE TABLE " + SqlIdentifiers.qualified(catalog, table);
    }

    /** {@code DROP TABLE `c`.`t`}. */
    public static String dropTable(String catalog, String table) {
        return "DROP TABLE " + SqlIdentifiers.qualified(catalog, table);
    }

    /** {@code DROP VIEW `c`.`v`}. */
    public static String dropView(String catalog, String view) {
        return "DROP VIEW " + SqlIdentifiers.qualified(catalog, view);
    }

    private static String checkCharsetName(String name) {
        String n = name.trim();
        if (!CHARSET_NAME.matcher(n).matches()) {
            throw new IllegalArgumentException(CoreMessages.get("sqlgen.charsetName.invalid", name));
        }
        return n;
    }
}
