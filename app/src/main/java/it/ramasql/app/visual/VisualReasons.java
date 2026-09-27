/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.visual;

import java.util.regex.Pattern;

import it.ramasql.app.Texts;
import it.ramasql.qb.QbSql;

/**
 * Perché una query non si disegna, detto a uno studente (Step 7). {@link QbSql#check} dà un motivo tecnico (un pezzo di
 * testo normalizzato, o l'eccezione del parser); qui si riconosce il costrutto che il diagramma non sa rappresentare e
 * se ne dà il nome, con i testi nei file di risorse. Se nessun costrutto noto c'è, un motivo generico.
 */
public final class VisualReasons {

    private static final Pattern WINDOW = Pattern.compile("(?is)\\bover\\s*\\(");
    private static final Pattern CTE = Pattern.compile("(?is)^\\s*with\\b");
    private static final Pattern UNION = Pattern.compile("(?is)\\bunion\\b");
    private static final Pattern COMMENT = Pattern.compile("(?s)(--|#|/\\*)");
    private static final Pattern USING = Pattern.compile("(?is)\\bjoin\\b[^;]*?\\busing\\s*\\(");
    private static final Pattern CROSS = Pattern.compile("(?is)\\bcross\\s+join\\b");
    private static final Pattern FROM = Pattern.compile("(?is)\\bfrom\\b");

    private VisualReasons() {
    }

    /** Il motivo in italiano; {@code check} può essere {@code null}. */
    public static String of(String sql, QbSql.Result check) {
        String text = sql == null ? "" : sql;
        String withoutStrings = text.replaceAll("'(?:[^'\\\\]|\\\\.|'')*'", "''");
        if (!FROM.matcher(withoutStrings).find()) {
            return Texts.get("visual.reason.noTables");
        }
        if (COMMENT.matcher(withoutStrings).find()) {
            return Texts.get("visual.reason.comment");
        }
        if (WINDOW.matcher(withoutStrings).find()) {
            return Texts.get("visual.reason.window");
        }
        if (CTE.matcher(withoutStrings).find()) {
            return Texts.get("visual.reason.cte");
        }
        if (UNION.matcher(withoutStrings).find()) {
            return Texts.get("visual.reason.union");
        }
        if (USING.matcher(withoutStrings).find()) {
            return Texts.get("visual.reason.using");
        }
        if (CROSS.matcher(withoutStrings).find()) {
            return Texts.get("visual.reason.cross");
        }
        if (withoutStrings.strip().replaceAll(";+$", "").indexOf(';') >= 0) {
            return Texts.get("visual.reason.manyStatements");
        }
        // i costrutti noti prima; poi il testo rotto (il controllo di forma del parser segnala anche cose valide)
        if (check != null && check.reason() != null && check.reason().startsWith("il testo non è una SELECT valida")) {
            return Texts.get("visual.reason.notSelect");
        }
        return Texts.get("visual.reason.other");
    }
}
