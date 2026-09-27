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

import java.util.Locale;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;

/**
 * Le viste (Step 8, {@code DESIGN.md} §3.8): {@code CREATE VIEW}, {@code CREATE OR REPLACE VIEW}, {@code DROP VIEW}.
 * Generatori puri, senza server. Nome della vista sempre tra backtick e qualificato con il catalogo; la query si
 * scrive così come l'ha prodotta il query builder (o l'utente), senza il punto e virgola finale. Nessuna opzione
 * ALGORITHM / SQL SECURITY / WITH CHECK OPTION: sono «[dopo]» (§1-bis).
 */
public final class ViewDdl {

    private ViewDdl() {
    }

    /**
     * {@code CREATE [OR REPLACE] VIEW `c`.`v` AS <select>}.
     *
     * @param catalog   catalogo della vista; {@code null} = catalogo corrente (nome non qualificato)
     * @param orReplace {@code true} per sostituire una vista che esiste già
     * @throws IllegalArgumentException se il nome è vuoto o la query non è una SELECT
     */
    public static String createView(String catalog, String view, String select, boolean orReplace) {
        if (view == null || view.isBlank()) {
            throw new IllegalArgumentException(CoreMessages.get("sqlgen.view.nameMissing"));
        }
        String body = selectBody(select);
        return (orReplace ? "CREATE OR REPLACE VIEW " : "CREATE VIEW ") + name(catalog, view) + " AS\n" + body;
    }

    /** {@code DROP VIEW `c`.`v`}. */
    public static String dropView(String catalog, String view) {
        return "DROP VIEW " + name(catalog, view);
    }

    /**
     * La query della vista ripulita: senza spazi ai bordi e senza i punti e virgola finali. Deve cominciare con
     * {@code SELECT}, {@code WITH} o una parentesi (una SELECT tra parentesi o una UNION).
     */
    public static String selectBody(String select) {
        String body = select == null ? "" : select.strip();
        while (body.endsWith(";")) {
            body = body.substring(0, body.length() - 1).strip();
        }
        String head = body.toLowerCase(Locale.ROOT);
        boolean ok = head.startsWith("(") || startsWithWord(head, "select") || startsWithWord(head, "with");
        if (!ok) {
            throw new IllegalArgumentException(CoreMessages.get("sqlgen.view.notSelect"));
        }
        return body;
    }

    /**
     * Lo script per la pipeline «anteprima SQL», con origine «Query visiva». Se il catalogo è indicato, prima del
     * {@code CREATE VIEW} c'è {@code USE `catalogo`}: i nomi di tabella non qualificati nella SELECT (quelli che scrive
     * il query builder, e uno studente) il server li cerca nel catalogo <b>corrente della sessione</b>, non in quello
     * della vista; senza il {@code USE} la vista userebbe le tabelle di un altro catalogo, o fallirebbe (1046).
     */
    public static SqlScript createScript(String catalog, String view, String select, boolean orReplace) {
        String title = CoreMessages.get(orReplace ? "script.replaceView" : "script.createView", view);
        String create = createView(catalog, view, select, orReplace);
        if (catalog == null || catalog.isBlank()) {
            return SqlScript.of(title, SqlOrigin.QUERY_BUILDER.label(), create);
        }
        return SqlScript.of(title, SqlOrigin.QUERY_BUILDER.label(), "USE " + SqlIdentifiers.quote(catalog), create);
    }

    private static boolean startsWithWord(String text, String word) {
        return text.startsWith(word) && (text.length() == word.length()
                || !Character.isLetterOrDigit(text.charAt(word.length())) && text.charAt(word.length()) != '_');
    }

    private static String name(String catalog, String view) {
        return catalog == null || catalog.isBlank() ? SqlIdentifiers.quote(view)
                : SqlIdentifiers.qualified(catalog, view);
    }
}
