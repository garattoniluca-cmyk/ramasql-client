/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalizzatore delle definizioni di vista rilette dal server ({@code information_schema.VIEWS.VIEW_DEFINITION}),
 * livello 2 della riapertura delle viste ({@code FEASIBILITY.md} F-06, Step 8).
 *
 * <p>MariaDB e MySQL non restituiscono il testo scritto dall'utente ma una riscrittura: nomi qualificati con il
 * catalogo, parentesi attorno ai join e alle condizioni, {@code AS} su ogni colonna, introducer di charset
 * {@code _utf8mb4'…'}. Il normalizzatore toglie solo ciò che si può togliere <b>senza cambiare il significato</b>,
 * per dare al parser del query builder un testo vicino a quello che scriverebbe una persona. Se una riscrittura non è
 * sicura la lascia com'è: la vista risulterà «non rappresentabile» e si aprirà nell'editor SQL (livello 3), senza
 * perdite. Nato dalla bozza dello spike S2c (Step 1); rispetto alla bozza corregge i limiti di {@code BUG-010}.
 *
 * <p>Regole (tutte attente a stringhe e identificatori tra backtick):
 * <ol>
 * <li>qualificatore del catalogo: {@code `cat`.`t`.`col`} → {@code `t`.`col`} sempre; {@code `cat`.`t`} →
 *     {@code `t`} solo dove sta un nome di tabella (dopo FROM, JOIN, o una virgola nella clausola FROM): così un
 *     alias di tabella uguale al nome del catalogo ({@code `scuola`.`nome`}) non si tocca;</li>
 * <li>introducer di charset testuali davanti a una stringa: {@code _utf8mb4'x'} → {@code 'x'} ({@code _binary} resta);</li>
 * <li>parentesi che avvolgono i join a sinistra subito dopo {@code FROM}: {@code from ((a join b on …) join c on …)};
 *     i join annidati <em>a destra</em> ({@code a join (b join c on …) on …}) restano: cambierebbero l'ordine;</li>
 * <li>parentesi che avvolgono l'intera condizione di {@code ON}, {@code WHERE}, {@code HAVING};</li>
 * <li>parentesi attorno a un predicato semplice (senza AND/OR/SELECT al suo interno) tra AND/OR;</li>
 * <li>parentesi che avvolgono un intero elemento della lista SELECT: {@code (`l`.`prezzo` * 1.22) AS `lordo`};</li>
 * <li>alias ridondante: {@code `l`.`titolo` AS `titolo`} → {@code `l`.`titolo`}.</li>
 * </ol>
 * Non tocca: parentesi di funzioni, sottoquery, liste {@code IN}, espressioni aritmetiche dentro un'espressione,
 * {@code NOT (…)}, {@code COLLATE}. Non lancia mai eccezioni: nel caso peggiore restituisce il testo di partenza.
 */
public final class ViewDefinitionNormalizer {

    private enum Kind { WORD, QUOTED_ID, STRING, NUMBER, PUNCT }

    private record Tok(String text, Kind kind, boolean spaceBefore) {
        boolean is(String s) {
            return kind != Kind.STRING && kind != Kind.QUOTED_ID && text.equalsIgnoreCase(s);
        }

        boolean isAny(Set<String> words) {
            return kind == Kind.WORD && words.contains(text.toLowerCase(Locale.ROOT));
        }

        boolean isName() {
            return kind == Kind.QUOTED_ID || kind == Kind.WORD;
        }

        Tok spaced(boolean space) {
            return new Tok(text, kind, space);
        }
    }

    /** Parole che chiudono una condizione di ON/WHERE/HAVING. */
    private static final Set<String> END_OF_CONDITION = Set.of("join", "inner", "left", "right", "cross", "natural",
            "straight_join", "where", "group", "having", "order", "limit", "union", "window");
    private static final Set<String> AND_OR = Set.of("and", "or", "xor");
    /** Parole dopo le quali (nella clausola FROM) viene un nome di tabella. */
    private static final Set<String> TABLE_FOLLOWS = Set.of("from", "join", "straight_join");
    /** Parole che chiudono la clausola FROM (e aprono un'altra clausola o una condizione). */
    private static final Set<String> LEAVE_FROM = Set.of("where", "group", "having", "order", "limit", "on", "using",
            "union", "window", "select");

    private ViewDefinitionNormalizer() {
    }

    /**
     * @param definition testo di {@code VIEW_DEFINITION} (o la parte dopo {@code AS} di {@code SHOW CREATE VIEW})
     * @param catalog    catalogo della vista, il cui qualificatore si toglie; {@code null} = non si toglie nulla
     * @return la definizione normalizzata; mai {@code null}
     */
    public static String normalize(String definition, String catalog) {
        if (definition == null) {
            return "";
        }
        try {
            List<Tok> t = tokenize(definition);
            removeCatalogQualifier(t, catalog);
            removeCharsetIntroducers(t);
            boolean changed = true;
            int rounds = 0;
            while (changed && rounds++ < 200) {
                changed = removeOneRedundantParenthesis(t);
            }
            removeRedundantAliases(t);
            return render(t);
        } catch (RuntimeException e) {
            return definition.strip();   // mai eccezioni: nel peggiore dei casi il testo del server, intatto
        }
    }

    /** Solo la regola 1: serve a copiare una vista in un altro catalogo senza toccare altro. */
    public static String stripCatalog(String definition, String catalog) {
        if (definition == null) {
            return "";
        }
        try {
            List<Tok> t = tokenize(definition);
            removeCatalogQualifier(t, catalog);
            return render(t);
        } catch (RuntimeException e) {
            return definition.strip();
        }
    }

    // ------------------------------------------------------------------ analisi lessicale

    private static List<Tok> tokenize(String sql) {
        List<Tok> out = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        boolean space = false;
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                space = true;
                i++;
                continue;
            }
            int j;
            Kind kind;
            if (c == '\'' || c == '"') {
                j = i + 1;
                while (j < n) {
                    char d = sql.charAt(j);
                    if (d == '\\' && j + 1 < n) {
                        j += 2;
                    } else if (d == c) {
                        if (j + 1 < n && sql.charAt(j + 1) == c) {
                            j += 2;
                        } else {
                            break;
                        }
                    } else {
                        j++;
                    }
                }
                j = Math.min(n, j + 1);
                kind = Kind.STRING;
            } else if (c == '`') {
                j = i + 1;
                while (j < n) {
                    if (sql.charAt(j) == '`') {
                        if (j + 1 < n && sql.charAt(j + 1) == '`') {
                            j += 2;
                        } else {
                            break;
                        }
                    } else {
                        j++;
                    }
                }
                j = Math.min(n, j + 1);
                kind = Kind.QUOTED_ID;
            } else if (Character.isDigit(c)) {
                j = i;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '.')) {
                    j++;
                }
                kind = Kind.NUMBER;
            } else if (Character.isLetter(c) || c == '_' || c == '@' || c == '$') {
                j = i;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_'
                        || sql.charAt(j) == '@' || sql.charAt(j) == '$')) {
                    j++;
                }
                kind = Kind.WORD;
            } else if ((c == '<' || c == '>' || c == '!' || c == ':' || c == '|' || c == '&') && i + 1 < n
                    && "=>|&".indexOf(sql.charAt(i + 1)) >= 0) {
                j = i + 2;
                if (c == '<' && sql.charAt(i + 1) == '=' && j < n && sql.charAt(j) == '>') {
                    j++;   // <=>
                }
                kind = Kind.PUNCT;
            } else {
                j = i + 1;
                kind = Kind.PUNCT;
            }
            out.add(new Tok(sql.substring(i, j), kind, space));
            space = false;
            i = j;
        }
        return out;
    }

    // ------------------------------------------------------------------ regole

    private static boolean isCatalog(Tok a, String catalog) {
        String quoted = "`" + catalog.replace("`", "``") + "`";
        return (a.kind() == Kind.QUOTED_ID && a.text().equalsIgnoreCase(quoted))
                || (a.kind() == Kind.WORD && a.text().equalsIgnoreCase(catalog));
    }

    private static void removeCatalogQualifier(List<Tok> t, String catalog) {
        if (catalog == null || catalog.isBlank()) {
            return;
        }
        // dove si è: dentro la clausola FROM (per profondità di parentesi) e subito dopo FROM/JOIN/virgola
        Deque<Boolean> inFromStack = new ArrayDeque<>();
        boolean inFrom = false;
        for (int i = 0; i < t.size(); i++) {
            Tok a = t.get(i);
            if (a.is("(")) {
                inFromStack.push(inFrom);
                // una sottoquery ricomincia da capo; una parentesi di join resta nella FROM
                inFrom = inFrom && !(i + 1 < t.size() && t.get(i + 1).is("select"));
                continue;
            }
            if (a.is(")")) {
                inFrom = inFromStack.isEmpty() ? false : inFromStack.pop();
                continue;
            }
            if (a.isAny(TABLE_FOLLOWS)) {
                inFrom = true;
                continue;
            }
            if (a.isAny(LEAVE_FROM)) {
                inFrom = false;
                continue;
            }
            boolean precededByDot = i > 0 && t.get(i - 1).is(".");
            if (!isCatalog(a, catalog) || precededByDot || i + 2 >= t.size() || !t.get(i + 1).is(".")
                    || !t.get(i + 2).isName()) {
                continue;
            }
            boolean threeParts = i + 4 < t.size() && t.get(i + 3).is(".") && t.get(i + 4).isName();
            Tok prev = i > 0 ? t.get(i - 1) : null;
            boolean tablePosition = inFrom && prev != null
                    && (prev.isAny(TABLE_FOLLOWS) || prev.is(",") || prev.is("("));
            if (threeParts || tablePosition) {
                Tok next = t.get(i + 2);
                t.set(i + 2, next.spaced(a.spaceBefore()));
                t.remove(i + 1);
                t.remove(i);
            }
        }
    }

    /**
     * Gli introducer di charset testuali che il server aggiunge davanti alle stringhe: toglierli non cambia il
     * significato (la stringa prende il charset della connessione). {@code _binary} invece sì (confronto a byte invece
     * che per collation): quello resta.
     */
    private static final Set<String> TEXT_INTRODUCERS = Set.of("_utf8mb4", "_utf8mb3", "_utf8", "_latin1", "_ascii",
            "_ucs2", "_utf16", "_utf16le", "_utf32", "_cp1252");

    private static void removeCharsetIntroducers(List<Tok> t) {
        for (int i = 0; i + 1 < t.size(); i++) {
            Tok a = t.get(i);
            if (a.kind() == Kind.WORD && TEXT_INTRODUCERS.contains(a.text().toLowerCase(Locale.ROOT))
                    && t.get(i + 1).kind() == Kind.STRING) {
                Tok s = t.get(i + 1);
                t.set(i + 1, s.spaced(a.spaceBefore() || s.spaceBefore()));
                t.remove(i);
            }
        }
    }

    /** Toglie una coppia di parentesi ridondanti, se ne trova una; {@code true} se ha tolto qualcosa. */
    private static boolean removeOneRedundantParenthesis(List<Tok> t) {
        int[] match = matchParentheses(t);
        for (int open = 0; open < t.size(); open++) {
            if (!t.get(open).is("(") || match[open] < 0) {
                continue;
            }
            int close = match[open];
            if (close == open + 1) {
                continue;   // «()» di una funzione senza argomenti
            }
            Tok prev = open > 0 ? t.get(open - 1) : null;
            Tok next = close + 1 < t.size() ? t.get(close + 1) : null;
            boolean startsWithSelect = t.get(open + 1).is("select") || t.get(open + 1).is("with");
            if (prev == null || startsWithSelect) {
                continue;
            }
            boolean nextEndsCondition = next == null || next.is(")") || next.isAny(END_OF_CONDITION);
            boolean redundant = false;
            if (prev.is("from") && containsAtDepthZero(t, open, close, Set.of("join", "straight_join"))) {
                // join a sinistra avvolti: from ((a join b on …) join c on …)
                redundant = startsWithTableOrParenthesis(t, open) && (next == null || next.is(")")
                        || next.isAny(END_OF_CONDITION));
            } else if ((prev.is("on") || prev.is("where") || prev.is("having")) && nextEndsCondition) {
                // l'intera condizione tra parentesi
                redundant = true;
            } else if (prev.is("on") || prev.is("where") || prev.is("having") || prev.isAny(AND_OR)
                    || (prev.is("(") && isConditionParenthesis(t, match, open - 1))) {
                // predicato semplice tra AND/OR: i predicati legano più di AND/OR, le parentesi non cambiano nulla
                redundant = (nextEndsCondition || (next != null && next.isAny(AND_OR)))
                        && !containsAtDepthZero(t, open, close, AND_OR)
                        && !containsAtDepthZero(t, open, close, Set.of("select", "between", ","));
            } else if (isSelectItemStart(t, open) && next != null
                    && (next.is("as") || next.is(",") || next.is("from"))) {
                // un intero elemento della lista SELECT: «(`l`.`prezzo` * 1.22) AS `lordo`»
                redundant = !containsAtDepthZero(t, open, close, Set.of(","));
            }
            if (redundant) {
                Tok first = t.get(open + 1);
                t.set(open + 1, first.spaced(true));
                if (next != null) {
                    t.set(close + 1, next.spaced(next.spaceBefore() || !next.is(")")));
                }
                t.remove(close);
                t.remove(open);
                return true;
            }
        }
        return false;
    }

    /** Il join avvolto comincia con un nome di tabella o con un'altra parentesi di join (non con una sottoquery). */
    private static boolean startsWithTableOrParenthesis(List<Tok> t, int open) {
        Tok first = t.get(open + 1);
        return first.isName() || first.is("(");
    }

    /** La parentesi {@code open} è a sua volta una parentesi di condizione (preceduta da ON/WHERE/HAVING/AND/OR). */
    private static boolean isConditionParenthesis(List<Tok> t, int[] match, int open) {
        if (open <= 0 || match[open] < 0) {
            return false;
        }
        Tok prev = t.get(open - 1);
        return prev.is("on") || prev.is("where") || prev.is("having") || prev.isAny(AND_OR)
                || (prev.is("(") && isConditionParenthesis(t, match, open - 1));
    }

    /** La parentesi apre un elemento della lista SELECT (dopo SELECT, DISTINCT o una virgola della lista). */
    private static boolean isSelectItemStart(List<Tok> t, int open) {
        Tok prev = t.get(open - 1);
        if (prev.is("select") || prev.is("distinct")) {
            return true;
        }
        if (!prev.is(",")) {
            return false;
        }
        // la virgola deve essere quella della lista SELECT: risalendo alla stessa profondità si incontra SELECT
        int depth = 0;
        for (int i = open - 2; i >= 0; i--) {
            Tok k = t.get(i);
            if (k.is(")")) {
                depth++;
            } else if (k.is("(")) {
                if (depth == 0) {
                    return false;   // la virgola è dentro gli argomenti di una funzione
                }
                depth--;
            } else if (depth == 0 && (k.is("select") || k.is("distinct"))) {
                return true;
            } else if (depth == 0 && (k.is("from") || k.is("where") || k.is("group") || k.is("order")
                    || k.is("having") || k.is("on"))) {
                return false;
            }
        }
        return false;
    }

    /** Alias ridondanti della lista SELECT: {@code [`tab` .] `col` AS `col`}. */
    private static void removeRedundantAliases(List<Tok> t) {
        for (int i = 0; i + 2 < t.size(); i++) {
            Tok col = t.get(i);
            if (col.kind() != Kind.QUOTED_ID || !t.get(i + 1).is("as") || t.get(i + 2).kind() != Kind.QUOTED_ID
                    || !t.get(i + 2).text().equalsIgnoreCase(col.text())) {
                continue;
            }
            Tok after = i + 3 < t.size() ? t.get(i + 3) : null;
            if (after != null && !after.is(",") && !after.is("from")) {
                continue;
            }
            int start = i;
            while (start >= 2 && t.get(start - 1).is(".") && t.get(start - 2).kind() == Kind.QUOTED_ID) {
                start -= 2;   // `t`.`col` o `cat`.`t`.`col`
            }
            Tok before = start > 0 ? t.get(start - 1) : null;
            if (before == null || !(before.is(",") || before.is("select") || before.is("distinct"))) {
                continue;
            }
            t.remove(i + 2);
            t.remove(i + 1);
        }
    }

    // ------------------------------------------------------------------ utilità

    private static int[] matchParentheses(List<Tok> t) {
        int[] match = new int[t.size()];
        Arrays.fill(match, -1);
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < t.size(); i++) {
            if (t.get(i).is("(")) {
                stack.push(i);
            } else if (t.get(i).is(")") && !stack.isEmpty()) {
                int o = stack.pop();
                match[o] = i;
                match[i] = o;
            }
        }
        return match;
    }

    private static boolean containsAtDepthZero(List<Tok> t, int open, int close, Set<String> words) {
        int depth = 0;
        for (int i = open + 1; i < close; i++) {
            Tok k = t.get(i);
            if (k.is("(")) {
                depth++;
            } else if (k.is(")")) {
                depth--;
            } else if (depth == 0 && k.kind() != Kind.STRING && k.kind() != Kind.QUOTED_ID
                    && words.contains(k.text().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String render(List<Tok> t) {
        StringBuilder sb = new StringBuilder();
        Tok prev = null;
        for (Tok k : t) {
            if (prev != null) {
                boolean wordLike = k.kind() != Kind.PUNCT;
                boolean prevWordLike = prev.kind() != Kind.PUNCT;
                boolean attached = prev.is("(") || k.is(")") || k.is(",") || k.is(".") || prev.is(".")
                        // un introducer rimasto (_binary'x') resta attaccato alla sua stringa, come l'ha scritto il server
                        || (prev.kind() == Kind.WORD && prev.text().startsWith("_") && k.kind() == Kind.STRING
                                && !k.spaceBefore());
                if (!attached && (k.spaceBefore() || (wordLike && prevWordLike))) {
                    sb.append(' ');
                }
            }
            sb.append(k.text());
            prev = k;
        }
        return sb.toString().strip();
    }
}
