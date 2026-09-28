/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.metadata.CollationInfo;

/**
 * Le collation di uno script che il server di destinazione non conosce, sostituite con la collation predefinita dello
 * stesso set di caratteri su quel server. È il caso comune del ripristino da un server all'altro: MariaDB 11.5 scrive
 * {@code utf8mb4_uca1400_ai_ci} (la sua predefinita) in ogni {@code CREATE TABLE}, che MySQL 8 rifiuta (errore 1273);
 * MySQL scrive {@code utf8mb4_0900_ai_ci}, che MariaDB 11.5 non ha. La sostituzione si <b>mostra</b> prima di eseguire
 * (anteprima e riepilogo del file) e le istruzioni finiscono nel registro già sostituite: si esegue ciò che si vede.
 */
public final class CollationCompat implements UnaryOperator<String> {

    /** {@code COLLATE x}, {@code COLLATE=x}, {@code COLLATE 'x'} e {@code collation_connection = x} (mysqldump). */
    private static final Pattern COLLATION = Pattern.compile(
            "(?i)(\\bCOLLATE\\s*=?\\s*|\\bcollation_(?:connection|database|server)\\s*=\\s*)(['\"`]?)([a-z0-9]+_[a-z0-9_]+)\\2");

    private final Map<String, String> replacements;

    private CollationCompat(Map<String, String> replacements) {
        this.replacements = Map.copyOf(replacements);
    }

    /** Nessuna sostituzione. */
    public static CollationCompat none() {
        return new CollationCompat(Map.of());
    }

    /**
     * Le sostituzioni per le collation {@code used} di uno script, sapendo quelle del server di destinazione.
     *
     * @param used   collation nominate dallo script ({@link #collationsIn})
     * @param target collation del server di destinazione
     */
    public static CollationCompat of(Collection<String> used, Collection<CollationInfo> target) {
        Set<String> known = new HashSet<>();
        Map<String, String> defaults = new HashMap<>();
        for (CollationInfo c : target) {
            known.add(c.name().toLowerCase(Locale.ROOT));
            if (c.isDefault()) {
                defaults.put(c.charset().toLowerCase(Locale.ROOT), c.name());
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String u : used) {
            String name = u.toLowerCase(Locale.ROOT);
            if (known.isEmpty() || known.contains(name)) {
                continue;
            }
            String charset = charsetOf(name);
            String replacement = defaults.get(charset);
            if (replacement == null && charset.equals("utf8")) {
                replacement = defaults.get("utf8mb3");
            }
            if (replacement != null) {
                out.put(name, replacement);
            }
        }
        return new CollationCompat(out);
    }

    /** Il set di caratteri di una collation: la parte prima del primo {@code _} ({@code utf8mb4_0900_ai_ci} → utf8mb4). */
    static String charsetOf(String collation) {
        int u = collation.indexOf('_');
        return (u < 0 ? collation : collation.substring(0, u)).toLowerCase(Locale.ROOT);
    }

    /** Le collation nominate da un'istruzione (in minuscolo). */
    public static Set<String> collationsIn(String sql) {
        Set<String> out = new HashSet<>();
        Matcher m = COLLATION.matcher(sql);
        while (m.find()) {
            out.add(m.group(3).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** La posizione cade dentro una stringa {@code '…'} o {@code "…"} (con le barre rovesciate e gli apici doppi). */
    static boolean insideString(String sql, int pos) {
        char quote = 0;
        for (int i = 0; i < pos; i++) {
            char c = sql.charAt(i);
            if (quote == 0) {
                if (c == '\'' || c == '"' || c == '`') {
                    quote = c;
                }
            } else if (c == '\\' && quote != '`') {
                i++;
            } else if (c == quote) {
                if (i + 1 < pos && sql.charAt(i + 1) == quote) {
                    i++;
                } else {
                    quote = 0;
                }
            }
        }
        return quote == '\'' || quote == '"';
    }

    /** Collation sconosciuta → sostituta. Vuoto = niente da cambiare. */
    public Map<String, String> replacements() {
        return replacements;
    }

    public boolean isEmpty() {
        return replacements.isEmpty();
    }

    @Override
    public String apply(String sql) {
        if (replacements.isEmpty() || sql == null) {
            return sql;
        }
        Matcher m = COLLATION.matcher(sql);
        StringBuilder out = null;
        int last = 0;
        while (m.find()) {
            String r = replacements.get(m.group(3).toLowerCase(Locale.ROOT));
            if (r == null || insideString(sql, m.start())) {
                continue;   // il testo di una stringa non si tocca
            }
            if (out == null) {
                out = new StringBuilder(sql.length());
            }
            out.append(sql, last, m.start(3)).append(r);
            last = m.end(3);
        }
        if (out == null) {
            return sql;
        }
        out.append(sql, last, sql.length());
        return out.toString();
    }
}
