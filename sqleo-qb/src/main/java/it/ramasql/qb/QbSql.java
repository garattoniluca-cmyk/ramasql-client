/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

import com.sqleo.querybuilder.QueryModel;
import com.sqleo.querybuilder.syntax.SQLParser;

/**
 * Punto d'ingresso «senza interfaccia» al parser ereditato da SQLeo: SQL → modello → SQL e
 * verifica di rappresentabilità (docs/FEASIBILITY.md F-05, regola R-03: se la query non è
 * rappresentabile la vista grafica si disattiva e il testo non si tocca).
 *
 * <p>Una query è <em>rappresentabile</em> quando il parser produce un modello senza eccezioni e
 * l'SQL rigenerato dal modello è equivalente all'originale dopo la normalizzazione
 * ({@link #normalize(String)}, più l'ordine indifferente dei due operandi di un'uguaglianza semplice): così nessun
 * pezzo di testo va perso passando dalla vista grafica.
 *
 * <p>Il parser ereditato è statico: le chiamate sono serializzate su questa classe.
 */
public final class QbSql {

    /**
     * Esito di {@link #check(String)}.
     *
     * @param representable true se l'SQL rigenerato equivale all'originale
     * @param model         modello prodotto dal parser; {@code null} se il parser ha fallito
     * @param regenerated   SQL rigenerato su una riga; {@code null} se il parser ha fallito
     * @param warnings      avvisi emessi dal parser durante l'analisi
     * @param reason        motivo della non rappresentabilità; {@code null} se rappresentabile
     */
    public record Result(boolean representable, QueryModel model, String regenerated,
                         List<String> warnings, String reason) {
    }

    private QbSql() {
    }

    /** SQL → modello. Lancia {@link QbParseException} se il parser ereditato fallisce in qualunque modo. */
    public static synchronized QueryModel parse(String sql) {
        try {
            return SQLParser.toQueryModel(sql);
        } catch (Exception | StackOverflowError e) {
            throw new QbParseException("Il parser non ha potuto leggere la query: " + e, e);
        }
    }

    /** Modello → SQL, su una riga o a capo per clausola. */
    public static String toSql(QueryModel model, boolean wrap) {
        return model.toString(wrap);
    }

    public static boolean isRepresentable(String sql) {
        return check(sql).representable();
    }

    /** Analizza, rigenera e confronta. Non lancia mai eccezioni. */
    public static synchronized Result check(String sql) {
        List<String> warnings = new ArrayList<>();
        // gli avvisi del parser si raccolgono con una facciata del solo thread corrente (2026-09-27, BUG-007): prima si
        // sostituiva per un attimo la facciata di tutto il processo, che le altre schede stavano usando
        return QbRuntime.withThreadHost(new WarningCollector(QbRuntime.host(), warnings), () -> checkWith(sql, warnings));
    }

    private static Result checkWith(String sql, List<String> warnings) {
        {
            QueryModel model;
            String regenerated;
            try {
                model = SQLParser.toQueryModel(sql);
                regenerated = model.toString(false);
            } catch (Exception | StackOverflowError e) {
                return new Result(false, null, null, warnings, "errore del parser: " + e);
            }
            // Il parser ereditato è tollerante: «SELECT DELETE FROM libri» o «SELECT ((( FROM x» li rilegge e li
            // rigenera identici, quindi il solo confronto dei testi non basta a dichiararli rappresentabili.
            String structural = structuralProblem(sql);
            if (structural != null) {
                return new Result(false, model, regenerated, warnings, "il testo non è una SELECT valida: " + structural);
            }
            String a = canonicalEqualities(normalize(sql));
            String b = canonicalEqualities(normalize(regenerated));
            if (a.equals(b)) {
                return new Result(true, model, regenerated, warnings, null);
            }
            return new Result(false, model, regenerated, warnings,
                    "l'SQL rigenerato differisce dall'originale (da: «" + firstDifference(a, b) + "»)");
        }
    }

    /**
     * Due testi SQL sono la stessa query per il confronto di {@link #check}: stessa forma normale, a meno dell'ordine
     * degli operandi delle uguaglianze semplici. Serve a verificare che il diagramma, dopo aver caricato un testo,
     * lo riscriva equivalente (Step 7): se no, il testo resta com'è.
     */
    public static boolean equivalent(String a, String b) {
        return canonicalEqualities(normalize(a)).equals(canonicalEqualities(normalize(b)));
    }

    /** Parole che aprono un'istruzione diversa da SELECT: fuori da apici e backtick non possono comparire in una SELECT. */
    private static final java.util.Set<String> STATEMENT_WORDS = java.util.Set.of("delete", "insert", "update", "drop",
            "create", "alter", "truncate", "grant", "revoke", "rename", "call", "load");

    /** Parole con cui una SELECT non può finire (clausola lasciata a metà). */
    private static final java.util.Set<String> DANGLING_WORDS = java.util.Set.of("select", "distinct", "from", "where",
            "join", "on", "using", "group", "order", "by", "having", "limit", "offset", "and", "or", "not", "in", "like",
            "between", "is", "as", "union", "all", "inner", "left", "right", "outer", "cross", "natural", "with",
            "recursive", "exists", "case", "when", "then", "else", "over", "partition");

    /**
     * Parole chiave che possono stare accanto a un nome senza essere un nome: servono solo a riconoscere tre nomi
     * semplici di fila ({@code SELECT a b c FROM t}), che in una SELECT valida non capitano. Se qui manca una parola
     * chiave l'effetto è prudente: la query risulta «non rappresentabile» e resta solo testo.
     */
    private static final java.util.Set<String> KEYWORDS = java.util.Set.of("select", "distinct", "distinctrow", "all",
            "from", "where", "join", "inner", "left", "right", "outer", "cross", "natural", "straight_join", "on",
            "using", "group", "order", "by", "having", "limit", "offset", "and", "or", "xor", "not", "in", "like",
            "rlike", "regexp", "between", "is", "null", "true", "false", "unknown", "as", "asc", "desc", "union",
            "except", "intersect", "with", "recursive", "exists", "case", "when", "then", "else", "end", "div", "mod",
            "interval", "collate", "binary", "separator", "over", "partition", "rows", "range", "unbounded",
            "preceding", "following", "current", "row", "window", "escape", "sounds", "any", "some", "for", "share",
            "lock", "mode", "rollup", "signed", "unsigned", "integer", "char", "character", "set", "decimal", "date",
            "datetime", "time", "year", "month", "day", "hour", "minute", "second", "microsecond", "week", "quarter",
            "force", "use", "ignore", "index", "key", "nulls", "first", "last", "both", "leading", "trailing",
            "sql_calc_found_rows", "high_priority", "sql_no_cache", "delete", "insert", "update", "drop", "create",
            "alter", "truncate", "grant", "revoke", "rename", "call", "load");

    /**
     * Controlli di forma che il parser ereditato non fa: restituisce il motivo per cui il testo non può essere una
     * SELECT valida, oppure {@code null}. Non è un parser SQL: intercetta solo i casi in cui il giro
     * SQL → modello → SQL «riesce» su un testo palesemente rotto (parentesi spaiate, apici non chiusi, SELECT senza
     * colonne, clausola lasciata a metà, parola di un'altra istruzione usata come nome, tre nomi di fila).
     */
    static String structuralProblem(String sql) {
        // parole fuori da apici e backtick, in minuscolo; "`" = nome tra backtick; "." e "(" tali e quali; "#" = altro
        List<String> words = new ArrayList<>();
        int depth = 0;
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                boolean closed = false;
                while (j < n) {
                    if (c != '`' && sql.charAt(j) == '\\' && j + 1 < n) {
                        j += 2;
                    } else if (sql.charAt(j) == c) {
                        if (j + 1 < n && sql.charAt(j + 1) == c) {
                            j += 2;
                        } else {
                            closed = true;
                            break;
                        }
                    } else {
                        j++;
                    }
                }
                if (!closed) {
                    return "apice o backtick non chiuso";
                }
                words.add(c == '`' ? "`" : "#");
                i = j + 1;
            } else if (Character.isLetter(c) || c == '_' || c == '$' || c == '@') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_'
                        || sql.charAt(j) == '$' || sql.charAt(j) == '@')) {
                    j++;
                }
                words.add(sql.substring(i, j).toLowerCase(java.util.Locale.ROOT));
                i = j;
            } else {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth < 0) {
                        return "parentesi chiusa senza la sua aperta";
                    }
                }
                words.add(c == '.' ? "." : c == '(' ? "(" : c == ';' ? ";" : "#");
                i++;
            }
        }
        if (depth != 0) {
            return "parentesi aperta e mai chiusa";
        }
        while (!words.isEmpty() && words.get(words.size() - 1).equals(";")) {
            words.remove(words.size() - 1);
        }
        if (words.isEmpty()) {
            return "testo vuoto";
        }
        if (!words.get(0).equals("select") && !words.get(0).equals("with") && !words.get(0).equals("(")) {
            return "non inizia con SELECT o WITH";
        }
        String last = words.get(words.size() - 1);
        if (DANGLING_WORDS.contains(last)) {
            return "il testo finisce con «" + last.toUpperCase(java.util.Locale.ROOT) + "»";
        }
        int names = 0;
        for (int k = 0; k < words.size(); k++) {
            String w = words.get(k);
            String next = k + 1 < words.size() ? words.get(k + 1) : "";
            String prev = k > 0 ? words.get(k - 1) : "";
            if (w.equals("select") && next.equals("from")) {
                return "SELECT senza elenco di colonne";
            }
            if (STATEMENT_WORDS.contains(w) && !next.equals("(") && !prev.equals(".")
                    && !(w.equals("update") && prev.equals("for"))) {
                return "«" + w.toUpperCase(java.util.Locale.ROOT) + "» non può comparire in una SELECT";
            }
            if (w.equals(".")) {
                names = Math.max(0, names - 1); // a.b.c è UN nome: il pezzo che segue continua quello che precede
            } else if (w.equals("`") || (Character.isLetter(w.charAt(0)) || w.charAt(0) == '_')
                    && !KEYWORDS.contains(w) && !next.equals("(")) {
                names++;
                if (names >= 3) {
                    return "tre nomi di fila senza operatore né virgola";
                }
            } else {
                names = 0;
            }
        }
        return null;
    }

    /** Uguaglianza tra due operandi semplici (colonna, numero), delimitata da spazio o parentesi: {@code a.x=b.y}. */
    private static final java.util.regex.Pattern SIMPLE_EQUALITY =
            java.util.regex.Pattern.compile("(?<=^|[ (])([\\w.$@]+)(=|<>)([\\w.$@]+)(?=$|[ )])(?! collate)");

    /**
     * {@code a.x = b.y} e {@code b.y = a.x} sono la stessa condizione: il parser ereditato riordina gli operandi delle
     * ON perché il suo modello vuole a sinistra la tabella già presente nel FROM. Per il confronto gli operandi di ogni
     * uguaglianza semplice si mettono in ordine alfabetico, su entrambi i testi. Solo operandi semplici e delimitati:
     * in {@code a.x=b.y+1} non si tocca nulla.
     */
    static String canonicalEqualities(String normalized) {
        java.util.regex.Matcher m = SIMPLE_EQUALITY.matcher(normalized);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String l = m.group(1);
            String r = m.group(3);
            String ordered = l.compareTo(r) <= 0 ? l + m.group(2) + r : r + m.group(2) + l;
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(ordered));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String firstDifference(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        int from = Math.max(0, i - 10);
        return a.substring(from, Math.min(a.length(), i + 30)) + "» ≠ «" + b.substring(from, Math.min(b.length(), i + 30));
    }

    /**
     * Forma normale per il confronto: fuori dalle stringhe tra apici tutto in minuscolo, niente backtick,
     * spazi compressi e tolti attorno alla punteggiatura, {@code AS} facoltativo tolto, {@code INNER JOIN}
     * = {@code JOIN}, {@code LEFT/RIGHT OUTER JOIN} = {@code LEFT/RIGHT JOIN}, {@code ASC} sottinteso,
     * {@code !=} = {@code <>}, punto e virgola finale ignorato. Le parentesi si conservano: se il
     * modello le perde, la query risulta non rappresentabile.
     */
    public static String normalize(String sql) {
        List<String> tokens = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c) || c == '`') {
                i++;
            } else if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < n) {
                    if (sql.charAt(j) == '\\' && j + 1 < n) {
                        j += 2;
                    } else if (sql.charAt(j) == c) {
                        if (j + 1 < n && sql.charAt(j + 1) == c) {
                            j += 2;
                        } else {
                            break;
                        }
                    } else {
                        j++;
                    }
                }
                tokens.add(sql.substring(i, Math.min(n, j + 1)));
                i = j + 1;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '@') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_'
                        || sql.charAt(j) == '$' || sql.charAt(j) == '@')) {
                    j++;
                }
                tokens.add(sql.substring(i, j).toLowerCase());
                i = j;
            } else if ((c == '<' || c == '>' || c == '!') && i + 1 < n
                    && (sql.charAt(i + 1) == '=' || sql.charAt(i + 1) == '>')) {
                String op = sql.substring(i, i + 2);
                tokens.add(op.equals("!=") ? "<>" : op);
                i += 2;
            } else {
                tokens.add(String.valueOf(c));
                i++;
            }
        }
        while (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals(";")) {
            tokens.remove(tokens.size() - 1);
        }
        List<String> out = new ArrayList<>();
        for (int k = 0; k < tokens.size(); k++) {
            String t = tokens.get(k);
            String next = k + 1 < tokens.size() ? tokens.get(k + 1) : "";
            if (t.equals("as") || t.equals("asc")) {
                continue;
            }
            if ((t.equals("inner") || t.equals("outer")) && (next.equals("join") || t.equals("outer"))) {
                continue;
            }
            out.add(t);
        }
        StringBuilder sb = new StringBuilder();
        for (String t : out) {
            boolean word = Character.isLetterOrDigit(t.charAt(0)) || t.charAt(0) == '_' || t.charAt(0) == '\''
                    || t.charAt(0) == '"' || t.charAt(0) == '@' || t.charAt(0) == '$';
            if (word && sb.length() > 0) {
                char last = sb.charAt(sb.length() - 1);
                if (Character.isLetterOrDigit(last) || last == '_' || last == '\'' || last == '"') {
                    sb.append(' ');
                }
            }
            sb.append(t);
        }
        return sb.toString();
    }

    /** Facciata che inoltra tutto all'host corrente e in più raccoglie gli avvisi del parser. */
    private static final class WarningCollector implements QbHost {
        private final QbHost delegate;
        private final List<String> warnings;

        WarningCollector(QbHost delegate, List<String> warnings) {
            this.delegate = delegate;
            this.warnings = warnings;
        }

        @Override public Connection connection() { return delegate.connection(); }
        @Override public String catalog() { return delegate.catalog(); }
        @Override public Icon icon(QbIcon id) { return delegate.icon(id); }
        @Override public String text(String key, String defaultText) { return delegate.text(key, defaultText); }
        @Override public int scale(int px) { return delegate.scale(px); }
        @Override public boolean option(QbOption o) { return delegate.option(o); }
        @Override public void alert(String message) { warnings.add(message); }
        @Override public List<JoinHint> joinHints(String table) { return delegate.joinHints(table); }
    }
}
