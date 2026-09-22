/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * BOZZA (spike S2c) del normalizzatore delle definizioni di vista rilette dal server
 * ({@code information_schema.VIEWS.VIEW_DEFINITION}): MariaDB e MySQL non restituiscono il testo scritto
 * dall'utente ma una riscrittura (nomi qualificati col catalogo, parentesi attorno ai join e alle condizioni,
 * alias {@code AS} su ogni colonna, introducer di charset {@code _utf8mb4'…'}). Il normalizzatore toglie solo ciò
 * che si può togliere <b>senza cambiare il significato</b>, per dare al parser del query builder un testo più
 * vicino a quello che un utente scriverebbe. Per ora è una classe di test; diventerà codice di prodotto allo Step 8
 * (T8.2) con il campionario raccolto in {@code test-results/step1/S2c-definizioni-*.md}.
 *
 * <p>Regole (tutte consapevoli di stringhe e identificatori tra backtick):
 * <ol>
 * <li>qualificatore del catalogo corrente: {@code `cat`.`tabella`} → {@code `tabella`};</li>
 * <li>introducer di charset davanti a una stringa: {@code _utf8mb4'x'} → {@code 'x'};</li>
 * <li>parentesi che avvolgono i join a sinistra subito dopo {@code FROM}: {@code from ((a join b on …) join c on …)};</li>
 * <li>parentesi che avvolgono l'intera condizione di {@code ON}, {@code WHERE}, {@code HAVING};</li>
 * <li>parentesi attorno a un predicato semplice (senza AND/OR/SELECT al suo interno) tra AND/OR;</li>
 * <li>alias ridondante: {@code `l`.`titolo` AS `titolo`} → {@code `l`.`titolo`}.</li>
 * </ol>
 * Non tocca: parentesi di funzioni, sottoquery, liste {@code IN}, espressioni aritmetiche, {@code NOT (…)}, {@code COLLATE}.
 */
final class ViewDefinitionNormalizer {

    private enum Kind { WORD, QUOTED_ID, STRING, NUMBER, PUNCT }

    private record Tok(String text, Kind kind, boolean spaceBefore) {
        boolean is(String s) {
            return kind != Kind.STRING && kind != Kind.QUOTED_ID && text.equalsIgnoreCase(s);
        }

        boolean isAny(Set<String> words) {
            return kind == Kind.WORD && words.contains(text.toLowerCase(Locale.ROOT));
        }
    }

    /** Parole che chiudono una condizione di ON/WHERE/HAVING. */
    private static final Set<String> FINE_CONDIZIONE = Set.of("join", "inner", "left", "right", "cross", "natural",
            "straight_join", "where", "group", "having", "order", "limit", "union", "window");
    private static final Set<String> AND_OR = Set.of("and", "or", "xor");

    private ViewDefinitionNormalizer() {
    }

    /**
     * @param definition testo di {@code VIEW_DEFINITION} (o la parte dopo {@code AS} di {@code SHOW CREATE VIEW})
     * @param catalog    catalogo corrente, il cui qualificatore si toglie; {@code null} = non si toglie nulla
     */
    static String normalize(String definition, String catalog) {
        List<Tok> t = tokenize(definition);
        removeCatalogQualifier(t, catalog);
        removeCharsetIntroducers(t);
        boolean changed = true;
        int giri = 0;
        while (changed && giri++ < 50) {
            changed = removeOneRedundantParenthesis(t);
        }
        removeRedundantAliases(t);
        return render(t);
    }

    /** Solo la regola 1 (serve anche a copiare una vista reale in un altro catalogo senza toccare altro). */
    static String stripCatalog(String definition, String catalog) {
        List<Tok> t = tokenize(definition);
        removeCatalogQualifier(t, catalog);
        return render(t);
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

    private static void removeCatalogQualifier(List<Tok> t, String catalog) {
        if (catalog == null) {
            return;
        }
        String quoted = "`" + catalog.replace("`", "``") + "`";
        for (int i = 0; i + 2 < t.size(); i++) {
            Tok a = t.get(i);
            boolean isCatalog = (a.kind() == Kind.QUOTED_ID && a.text().equalsIgnoreCase(quoted))
                    || (a.kind() == Kind.WORD && a.text().equalsIgnoreCase(catalog));
            boolean precededByDot = i > 0 && t.get(i - 1).is(".");
            if (isCatalog && !precededByDot && t.get(i + 1).is(".")
                    && (t.get(i + 2).kind() == Kind.QUOTED_ID || t.get(i + 2).kind() == Kind.WORD)) {
                Tok next = t.get(i + 2);
                t.set(i + 2, new Tok(next.text(), next.kind(), a.spaceBefore()));
                t.remove(i + 1);
                t.remove(i);
            }
        }
    }

    private static void removeCharsetIntroducers(List<Tok> t) {
        for (int i = 0; i + 1 < t.size(); i++) {
            Tok a = t.get(i);
            if (a.kind() == Kind.WORD && a.text().startsWith("_") && a.text().length() > 1
                    && t.get(i + 1).kind() == Kind.STRING) {
                Tok s = t.get(i + 1);
                t.set(i + 1, new Tok(s.text(), s.kind(), a.spaceBefore() || s.spaceBefore()));
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
                continue; // «()» di una funzione senza argomenti
            }
            Tok prev = open > 0 ? t.get(open - 1) : null;
            Tok next = close + 1 < t.size() ? t.get(close + 1) : null;
            boolean startsWithSelect = t.get(open + 1).is("select") || t.get(open + 1).is("with");
            if (prev == null || startsWithSelect) {
                continue;
            }
            boolean nextEndsCondition = next == null || next.is(")") || next.isAny(FINE_CONDIZIONE);
            boolean redundant = false;
            if (prev.is("from") && containsAtDepthZero(t, open, close, Set.of("join", "straight_join"))) {
                // join a sinistra avvolti: from ((a join b on …) join c on …)
                redundant = next == null || next.is(")") || next.isAny(FINE_CONDIZIONE);
            } else if ((prev.is("on") || prev.is("where") || prev.is("having")) && nextEndsCondition) {
                // l'intera condizione tra parentesi
                redundant = true;
            } else if (prev.is("on") || prev.is("where") || prev.is("having") || prev.isAny(AND_OR) || prev.is("(")) {
                // predicato semplice tra AND/OR: i predicati legano più di AND/OR, le parentesi non cambiano nulla
                redundant = (nextEndsCondition || (next != null && next.isAny(AND_OR)))
                        && !containsAtDepthZero(t, open, close, AND_OR)
                        && !containsAtDepthZero(t, open, close, Set.of("select", "between", ","));
            }
            if (redundant) {
                Tok first = t.get(open + 1);
                t.set(open + 1, new Tok(first.text(), first.kind(), true));
                if (next != null) {
                    t.set(close + 1, new Tok(next.text(), next.kind(), next.spaceBefore() || !next.is(")")));
                }
                t.remove(close);
                t.remove(open);
                return true;
            }
        }
        return false;
    }

    /** Dopo la regola sui join: `from (a join b …)` diventa `from a join b …`; qui gli alias ridondanti della lista SELECT. */
    private static void removeRedundantAliases(List<Tok> t) {
        // schema: [`tab` .] `col` AS `col`  seguito da «,» o FROM, e preceduto da «,», SELECT o DISTINCT (colonna semplice)
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
            if (i >= 2 && t.get(i - 1).is(".") && t.get(i - 2).kind() == Kind.QUOTED_ID) {
                start = i - 2;
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
        java.util.Arrays.fill(match, -1);
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
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
                boolean attaccato = prev.is("(") || k.is(")") || k.is(",");
                if (!attaccato && (k.spaceBefore() || (wordLike && prevWordLike))) {
                    sb.append(' ');
                }
            }
            sb.append(k.text());
            prev = k;
        }
        return sb.toString().strip();
    }
}
