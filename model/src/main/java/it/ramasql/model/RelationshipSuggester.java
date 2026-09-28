/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.metadata.SqlTypes;

/**
 * Propone relazioni <b>logiche</b> guardando i nomi delle colonne ({@code DESIGN.md} §3.11, requisito 8 «indipendente
 * dalle chiavi esterne»): serve soprattutto ai database senza chiavi esterne (MyISAM, o fatti senza). Le proposte non si
 * applicano mai da sole: l'utente le accetta una per una.
 *
 * <p>Regole, per ogni colonna non ancora in una relazione:
 * <ol>
 *   <li>il nome indica una tabella: {@code id_editore}, {@code editore_id}, {@code editoreId}, {@code idEditore};</li>
 *   <li>la tabella esiste con quel nome, al plurale o al singolare — italiano ({@code editore}/{@code editori},
 *       {@code socio}/{@code soci}, {@code libro}/{@code libri}, {@code tessera}/{@code tessere}) e inglese semplice
 *       ({@code author}/{@code authors}, {@code category}/{@code categories});</li>
 *   <li>la tabella ha una chiave primaria di una sola colonna, di tipo <b>compatibile</b> (interi con interi, testi con
 *       testi, stesso tipo per il resto): con tipi incompatibili nessuna proposta;</li>
 *   <li>in più: una colonna con lo stesso nome della chiave primaria (non generica come {@code id}) di un'altra tabella
 *       ({@code codice_fiscale} → {@code studenti.codice_fiscale}).</li>
 * </ol>
 * Un nome che non corrisponde a nessuna tabella ({@code id_esterno}) non produce nulla: meglio una proposta in meno che
 * una sbagliata.
 */
public final class RelationshipSuggester {

    /**
     * Una proposta.
     *
     * @param relationship la relazione logica proposta
     * @param score        fiducia da 0 a 100
     * @param reason       perché, in italiano
     */
    public record Suggestion(Relationship relationship, int score, String reason) {
    }

    private static final Pattern ID_PREFIX = Pattern.compile("(?i)id_?(.+)");
    private static final Pattern ID_SUFFIX = Pattern.compile("(?i)(.+?)_?id");
    private static final Pattern CAMEL_PREFIX = Pattern.compile("id([A-Z].*)");
    private static final Pattern CAMEL_SUFFIX = Pattern.compile("(.+)Id");

    private RelationshipSuggester() {
    }

    public static List<Suggestion> suggest(ErModel model) {
        List<Suggestion> out = new ArrayList<>();
        for (ErModel.Entity child : model.entities()) {
            if (child.missing()) {
                continue;
            }
            List<String> ownPk = child.primaryKey();
            for (ErModel.Attribute col : child.columns()) {
                if (ownPk.size() == 1 && ownPk.get(0).equalsIgnoreCase(col.name())) {
                    continue;   // la propria chiave primaria di una colonna non riferisce altro
                }
                if (inRelationship(model, child.table(), col.name())) {
                    continue;
                }
                bestFor(model, child, col).ifPresent(out::add);
            }
        }
        out.sort(Comparator.comparingInt(Suggestion::score).reversed()
                .thenComparing(s -> s.relationship().describe()));
        return out;
    }

    private static boolean inRelationship(ErModel model, String table, String column) {
        return model.relationships().stream().anyMatch(r -> r.fromTable().equalsIgnoreCase(table)
                && r.fromColumns().stream().anyMatch(column::equalsIgnoreCase));
    }

    private static Optional<Suggestion> bestFor(ErModel model, ErModel.Entity child, ErModel.Attribute col) {
        List<Suggestion> candidates = new ArrayList<>();
        // 1-3: il nome indica una tabella
        for (String stem : stems(col.name())) {
            for (ErModel.Entity parent : model.entities()) {
                if (parent.missing()) {
                    continue;
                }
                int nameScore = nameMatch(stem, parent.table());
                if (nameScore == 0) {
                    continue;
                }
                List<String> pk = parent.primaryKey();
                if (pk.size() != 1) {
                    continue;
                }
                ErModel.Attribute target = parent.column(pk.get(0)).orElseThrow();
                int typeScore = typeMatch(col.type(), target.type());
                if (typeScore < 0) {
                    continue;   // tipi incompatibili: nessuna proposta
                }
                if (parent.table().equalsIgnoreCase(child.table()) && pk.get(0).equalsIgnoreCase(col.name())) {
                    continue;
                }
                int score = Math.min(100, nameScore + typeScore);
                String reason = ModelMessages.get(nameScore >= 85 ? "suggest.reason.exact" : "suggest.reason.plural",
                        col.name(), parent.table(), pk.get(0));
                candidates.add(new Suggestion(relationship(model, child, col, parent, target), score, reason));
            }
        }
        // 4: stesso nome della chiave primaria di un'altra tabella (non «id»)
        for (ErModel.Entity parent : model.entities()) {
            List<String> pk = parent.primaryKey();
            if (parent.missing() || parent.table().equalsIgnoreCase(child.table()) || pk.size() != 1
                    || pk.get(0).equalsIgnoreCase("id") || !pk.get(0).equalsIgnoreCase(col.name())) {
                continue;
            }
            ErModel.Attribute target = parent.column(pk.get(0)).orElseThrow();
            int typeScore = typeMatch(col.type(), target.type());
            if (typeScore >= 0) {
                candidates.add(new Suggestion(relationship(model, child, col, parent, target), 60 + typeScore,
                        ModelMessages.get("suggest.reason.samePk", col.name(), parent.table())));
            }
        }
        return candidates.stream().max(Comparator.comparingInt(Suggestion::score));
    }

    private static Relationship relationship(ErModel model, ErModel.Entity child, ErModel.Attribute col,
            ErModel.Entity parent, ErModel.Attribute target) {
        Relationship.Cardinality card = child.isUnique(List.of(col.name())) ? Relationship.Cardinality.ONE_TO_ONE
                : Relationship.Cardinality.ONE_TO_MANY;
        String id = "log:" + child.table().toLowerCase(Locale.ROOT) + "." + col.name().toLowerCase(Locale.ROOT)
                + ">" + parent.table().toLowerCase(Locale.ROOT);
        return new Relationship(id, Relationship.Kind.LOGICAL, child.table(), List.of(col.name()), parent.table(),
                List.of(target.name()), card, !col.nullable(), "");
    }

    /** Le parti del nome che possono essere il nome di una tabella: {@code id_editore} → {@code editore}. */
    static Set<String> stems(String column) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m;
        if ((m = CAMEL_PREFIX.matcher(column)).matches()) {
            out.add(m.group(1));
        }
        if ((m = CAMEL_SUFFIX.matcher(column)).matches()) {
            out.add(m.group(1));
        }
        String lower = column.toLowerCase(Locale.ROOT);
        if ((m = ID_PREFIX.matcher(lower)).matches() && lower.startsWith("id_")) {
            out.add(m.group(1));
        }
        if ((m = ID_SUFFIX.matcher(lower)).matches() && lower.endsWith("_id")) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Quanto il nome della tabella corrisponde alla parte del nome della colonna: 90 uguale, 80 plurale/singolare. */
    static int nameMatch(String stem, String table) {
        String s = stem.toLowerCase(Locale.ROOT);
        String t = table.toLowerCase(Locale.ROOT);
        if (s.equals(t)) {
            return 90;
        }
        for (String form : forms(s)) {
            if (form.equals(t)) {
                return 80;
            }
        }
        for (String form : forms(t)) {
            if (form.equals(s)) {
                return 80;
            }
        }
        return 0;
    }

    /** Plurali e singolari semplici, italiani e inglesi. */
    static Set<String> forms(String w) {
        Set<String> out = new LinkedHashSet<>();
        int n = w.length();
        if (n < 3) {
            return out;
        }
        out.add(w + "s");
        out.add(w + "es");
        if (w.endsWith("y")) {
            out.add(w.substring(0, n - 1) + "ies");
        }
        if (w.endsWith("ies")) {
            out.add(w.substring(0, n - 3) + "y");
        }
        if (w.endsWith("s")) {
            out.add(w.substring(0, n - 1));
        }
        if (w.endsWith("es")) {
            out.add(w.substring(0, n - 2));
        }
        char last = w.charAt(n - 1);
        String base = w.substring(0, n - 1);
        switch (last) {
            case 'o' -> {
                out.add(base + "i");                    // libro → libri
                if (w.endsWith("io")) {
                    out.add(w.substring(0, n - 2) + "i");  // socio → soci
                }
                if (w.endsWith("co") || w.endsWith("go")) {
                    out.add(base + "hi");               // parco → parchi
                }
            }
            case 'a' -> {
                out.add(base + "e");                    // tessera → tessere
                if (w.endsWith("ca") || w.endsWith("ga")) {
                    out.add(base + "he");               // biblioteca → biblioteche
                }
                if (w.endsWith("ista")) {
                    out.add(base + "i");                // giornalista → giornalisti
                }
            }
            case 'e' -> {
                out.add(base + "i");                    // editore → editori
                if (w.endsWith("he")) {
                    out.add(w.substring(0, n - 2) + "a");   // biblioteche → biblioteca
                }
            }
            case 'i' -> {
                out.add(base + "o");                    // libri → libro
                out.add(base + "e");                    // editori → editore
                out.add(base + "io");                   // soci → socio
                out.add(base + "a");                    // giornalisti → giornalista
                if (w.endsWith("hi")) {
                    out.add(w.substring(0, n - 2) + "o");
                }
            }
            default -> {
            }
        }
        return out;
    }

    /**
     * Compatibilità dei tipi: 10 se identici, 0 se della stessa famiglia (interi con interi anche di segno o
     * lunghezza diversi, testi con testi), -1 se incompatibili.
     */
    static int typeMatch(String a, String b) {
        String ta = base(a);
        String tb = base(b);
        if (a.equalsIgnoreCase(b)) {
            return 10;
        }
        if (SqlTypes.isInteger(ta) && SqlTypes.isInteger(tb)) {
            return 0;
        }
        boolean textA = ta.equals("CHAR") || ta.equals("VARCHAR");
        boolean textB = tb.equals("CHAR") || tb.equals("VARCHAR");
        if (textA && textB) {
            return 0;
        }
        return ta.equals(tb) ? 0 : -1;
    }

    private static String base(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        int i = 0;
        while (i < t.length() && Character.isLetter(t.charAt(i))) {
            i++;
        }
        return SqlTypes.canonical(t.substring(0, i));
    }
}
