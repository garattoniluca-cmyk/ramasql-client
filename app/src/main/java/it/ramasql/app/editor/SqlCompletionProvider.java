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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

import it.ramasql.app.Texts;

/**
 * Il completamento dell'editor (Ctrl+Spazio, DESIGN §3.4): parole chiave, cataloghi, tabelle del catalogo in uso e
 * colonne, presi da una {@link CompletionSource}.
 *
 * <ul>
 *   <li>{@code SELECT * FROM li|} → le tabelle che cominciano per «li» ({@code libri}, {@code libri_autori}), poi le
 *       parole chiave ({@code LIKE}, {@code LIMIT});</li>
 *   <li>{@code libri.|} → le colonne di {@code libri}; {@code l.|} con {@code FROM libri l} → le stesse (alias);
 *       {@code biblioteca.|} → le tabelle del catalogo; {@code biblioteca.libri.|} → le colonne;</li>
 *   <li>senza qualificatore, prima le colonne delle tabelle nominate nell'istruzione, poi tabelle, cataloghi,
 *       parole chiave; in ogni gruppo in ordine alfabetico, senza distinguere maiuscole e minuscole.</li>
 * </ul>
 * I nomi che non sono identificatori semplici si inseriscono tra backtick.
 */
final class SqlCompletionProvider extends DefaultCompletionProvider {

    /** Tipo di proposta, in ordine di priorità. */
    enum Kind { COLUMN, TABLE, CATALOG, KEYWORD }

    /**
     * Una proposta.
     *
     * @param name  nome da inserire (senza backtick)
     * @param kind  tipo
     * @param owner tabella (per le colonne) o catalogo (per le tabelle), {@code null} per il resto
     */
    record Proposal(String name, Kind kind, String owner) {
    }

    private record TableRef(String catalog, String table) {
    }

    private static final String IDENT = "(?:`[^`]+`|[\\p{L}\\p{N}_$]+)";
    private static final Pattern TABLE_CLAUSE = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|UPDATE|INTO)\\s+(" + IDENT + ")(?:\\s*\\.\\s*(" + IDENT + "))?"
                    + "(?:\\s+(?:AS\\s+)?(" + IDENT + "))?");
    private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private final Supplier<CompletionSource> source;

    SqlCompletionProvider(Supplier<CompletionSource> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    protected boolean isValidChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp) {
        String text = comp.getText();
        int caret = Math.min(comp.getCaretPosition(), text.length());
        List<Completion> out = new ArrayList<>();
        for (Proposal p : propose(source.get(), text, caret)) {
            BasicCompletion c = new BasicCompletion(this,
                    p.kind() == Kind.KEYWORD ? p.name() : insertionText(p.name()), description(p));
            c.setRelevance((Kind.values().length - p.kind().ordinal()) * 100);
            out.add(c);
        }
        return out;
    }

    private static String description(Proposal p) {
        return switch (p.kind()) {
            case COLUMN -> Texts.get("editor.completion.column", p.owner());
            case TABLE -> Texts.get("editor.completion.table");
            case CATALOG -> Texts.get("editor.completion.catalog");
            case KEYWORD -> Texts.get("editor.completion.keyword");
        };
    }

    static String insertionText(String name) {
        return SIMPLE_NAME.matcher(name).matches() && !SqlKeywords.isKeyword(name) ? name
                : "`" + name.replace("`", "``") + "`";
    }

    // ---------------------------------------------------------------- logica pura

    /** Le proposte per il cursore in {@code caret} del testo {@code text}. */
    static List<Proposal> propose(CompletionSource src, String text, int caret) {
        int wordStart = caret;
        while (wordStart > 0 && isWordChar(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        String prefix = text.substring(wordStart, caret);

        List<String> qualifiers = qualifiersBefore(text, wordStart);
        PositionedStatement current = StatementLocator.atCaret(text, caret, "");
        String statement = current == null ? text : current.text();
        String catalog = src.currentCatalog();

        List<Proposal> all = new ArrayList<>();
        if (qualifiers.size() >= 2) {
            String cat = match(src.catalogs(), qualifiers.get(0));
            String tab = cat == null ? null : match(src.tables(cat), qualifiers.get(1));
            if (tab != null) {
                addAll(all, src.columns(cat, tab), Kind.COLUMN, tab);
            }
        } else if (qualifiers.size() == 1) {
            String q = qualifiers.getFirst();
            TableRef ref = aliases(statement, src).get(q.toLowerCase(Locale.ROOT));
            String table = catalog == null ? null : match(src.tables(catalog), q);
            if (ref != null) {
                addAll(all, src.columns(ref.catalog(), ref.table()), Kind.COLUMN, ref.table());
            } else if (table != null) {
                addAll(all, src.columns(catalog, table), Kind.COLUMN, table);
            } else {
                String cat = match(src.catalogs(), q);
                if (cat != null) {
                    addAll(all, src.tables(cat), Kind.TABLE, cat);
                }
            }
        } else {
            for (TableRef ref : referencedTables(statement, src)) {
                addAll(all, src.columns(ref.catalog(), ref.table()), Kind.COLUMN, ref.table());
            }
            if (catalog != null) {
                addAll(all, src.tables(catalog), Kind.TABLE, catalog);
            }
            addAll(all, src.catalogs(), Kind.CATALOG, null);
            addAll(all, SqlKeywords.ALL, Kind.KEYWORD, null);
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();
        List<Proposal> filtered = new ArrayList<>();
        for (Proposal p : all) {
            if (p.name().toLowerCase(Locale.ROOT).startsWith(lower) && seen.add(p.kind() + "|" + p.name())) {
                filtered.add(p);
            }
        }
        filtered.sort(Comparator.comparing((Proposal p) -> p.kind().ordinal())
                .thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        return filtered;
    }

    private static void addAll(List<Proposal> out, List<String> names, Kind kind, String owner) {
        for (String n : names) {
            out.add(new Proposal(n, kind, owner));
        }
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    /** {@code a.b.|} → [a, b]; {@code a.|} → [a]; niente punto → []. I backtick si tolgono. */
    private static List<String> qualifiersBefore(String text, int wordStart) {
        List<String> out = new ArrayList<>();
        int i = wordStart;
        while (out.size() < 2 && i > 0 && text.charAt(i - 1) == '.') {
            int end = i - 1;
            int start;
            if (end > 0 && text.charAt(end - 1) == '`') {
                int open = text.lastIndexOf('`', end - 2);
                if (open < 0) {
                    break;
                }
                out.addFirst(text.substring(open + 1, end - 1));
                start = open;
            } else {
                start = end;
                while (start > 0 && isWordChar(text.charAt(start - 1))) {
                    start--;
                }
                if (start == end) {
                    break;
                }
                out.addFirst(text.substring(start, end));
            }
            i = start;
        }
        return out;
    }

    /** Alias (e nomi) delle tabelle nominate dopo FROM/JOIN/UPDATE/INTO, in minuscolo. */
    private static Map<String, TableRef> aliases(String statement, CompletionSource src) {
        Map<String, TableRef> out = new HashMap<>();
        Matcher m = TABLE_CLAUSE.matcher(statement);
        while (m.find()) {
            TableRef ref = resolve(m.group(1), m.group(2), src);
            if (ref == null) {
                continue;
            }
            String alias = m.group(3) == null ? null : unquote(m.group(3));
            if (alias != null && !SqlKeywords.isKeyword(alias) && !isJoinWord(alias)) {
                out.put(alias.toLowerCase(Locale.ROOT), ref);
            }
        }
        return out;
    }

    private static List<TableRef> referencedTables(String statement, CompletionSource src) {
        Set<TableRef> out = new LinkedHashSet<>();
        Matcher m = TABLE_CLAUSE.matcher(statement);
        while (m.find()) {
            TableRef ref = resolve(m.group(1), m.group(2), src);
            if (ref != null) {
                out.add(ref);
            }
        }
        return List.copyOf(out);
    }

    private static TableRef resolve(String first, String second, CompletionSource src) {
        if (second != null) {
            String cat = match(src.catalogs(), unquote(first));
            String tab = cat == null ? null : match(src.tables(cat), unquote(second));
            return tab == null ? null : new TableRef(cat, tab);
        }
        String catalog = src.currentCatalog();
        String tab = catalog == null ? null : match(src.tables(catalog), unquote(first));
        return tab == null ? null : new TableRef(catalog, tab);
    }

    private static boolean isJoinWord(String word) {
        String w = word.toUpperCase(Locale.ROOT);
        return w.equals("NATURAL") || w.equals("STRAIGHT_JOIN") || w.equals("PARTITION");
    }

    private static String unquote(String ident) {
        return ident.length() >= 2 && ident.startsWith("`") && ident.endsWith("`")
                ? ident.substring(1, ident.length() - 1) : ident;
    }

    /** Il nome dell'elenco uguale a {@code wanted} senza badare a maiuscole/minuscole, {@code null} se assente. */
    private static String match(List<String> names, String wanted) {
        for (String n : names) {
            if (n.equalsIgnoreCase(wanted)) {
                return n;
            }
        }
        return null;
    }
}
