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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import it.ramasql.core.CoreMessages;

/**
 * Quale conferma chiedere prima di eseguire uno {@link SqlScript}:
 * <ul>
 *   <li>solo letture ({@link RiskLevel#SAFE}) → {@link Level#NONE};</li>
 *   <li>modifiche circoscritte ({@link RiskLevel#MODIFIES}) → {@link Level#CONFIRM}: l'anteprima con <i>Esegui</i>;</li>
 *   <li>istruzioni distruttive ({@link RiskLevel#DESTRUCTIVE}: {@code DROP}, {@code TRUNCATE}, {@code DELETE} /
 *       {@code UPDATE} senza {@code WHERE}, {@code ALTER … DROP COLUMN}) → {@link Level#STRONG}: conferma rafforzata,
 *       l'utente deve <b>riscrivere il nome dell'oggetto</b> (come in Workbench). Se lo script ne tocca più d'uno,
 *       o il nome non si riconosce, si riscrive la parola {@code confirm.word} dei messaggi.</li>
 * </ul>
 * Puro: guarda solo il testo.
 */
public final class ConfirmationPolicy {

    public enum Level { NONE, CONFIRM, STRONG }

    /**
     * La conferma richiesta.
     *
     * @param level         livello
     * @param typeToConfirm testo da riscrivere per la conferma rafforzata ({@code null} se non serve)
     * @param message       spiegazione per la finestra di conferma ({@code ""} per NONE)
     */
    public record Confirmation(Level level, String typeToConfirm, String message) {

        public Confirmation {
            Objects.requireNonNull(level, "level");
            message = message == null ? "" : message;
        }

        /** Il testo digitato dall'utente basta a confermare (sempre vero sotto STRONG). */
        public boolean accepts(String typed) {
            return level != Level.STRONG || (typed != null && typed.strip().equals(typeToConfirm));
        }
    }

    private static final Set<String> DROPPABLE = Set.of("TABLE", "VIEW", "DATABASE", "SCHEMA", "INDEX",
            "PROCEDURE", "FUNCTION", "TRIGGER", "EVENT");

    private ConfirmationPolicy() {
    }

    public static Confirmation evaluate(SqlScript script) {
        return switch (script.risk()) {
            case SAFE -> new Confirmation(Level.NONE, null, "");
            case MODIFIES -> new Confirmation(Level.CONFIRM, null, CoreMessages.get("confirm.normal"));
            case DESTRUCTIVE -> strong(script.statements());
        };
    }

    private static Confirmation strong(List<SqlStatement> statements) {
        Set<String> names = new LinkedHashSet<>();
        boolean unknown = false;
        for (SqlStatement s : statements) {
            if (s.risk() != RiskLevel.DESTRUCTIVE) {
                continue;
            }
            String name = targetOf(s.text());
            if (name == null) {
                unknown = true;
            } else {
                names.add(name);
            }
        }
        Set<String> distinct = new LinkedHashSet<>();
        names.forEach(n -> distinct.add(n.toLowerCase(Locale.ROOT)));
        String word = !unknown && distinct.size() == 1 ? names.iterator().next() : CoreMessages.get("confirm.word");
        return new Confirmation(Level.STRONG, word, CoreMessages.get("confirm.strong", word));
    }

    /** Che cosa fa un'istruzione distruttiva, per la frase «Stai per …» della conferma rafforzata. */
    public enum Action { DROP_TABLE, DROP_VIEW, DROP_CATALOG, TRUNCATE, OTHER }

    /**
     * L'azione della prima istruzione distruttiva dello script ({@link Action#OTHER} se non ce n'è o non si
     * riconosce). Commenti iniziali, {@code DROP TEMPORARY TABLE} e involucri ({@code SET STATEMENT … FOR}) compresi.
     */
    public static Action actionOf(SqlScript script) {
        for (SqlStatement s : script.statements()) {
            if (s.risk() == RiskLevel.DESTRUCTIVE) {
                return actionOf(s.text());
            }
        }
        return Action.OTHER;
    }

    /**
     * L'azione più grave fra tutte le istruzioni distruttive dello script (eliminare un catalogo, poi una tabella, una
     * vista, svuotare): per la frase della conferma quando lo script colpisce più oggetti — un {@code DROP DATABASE} in
     * fondo a un file non deve passare per «eliminare la tabella».
     */
    public static Action mostSevereAction(SqlScript script) {
        Action best = Action.OTHER;
        for (SqlStatement s : script.statements()) {
            if (s.risk() == RiskLevel.DESTRUCTIVE) {
                Action a = actionOf(s.text());
                if (severity(a) < severity(best)) {
                    best = a;
                }
            }
        }
        return best;
    }

    private static int severity(Action a) {
        return switch (a) {
            case DROP_CATALOG -> 0;
            case DROP_TABLE -> 1;
            case DROP_VIEW -> 2;
            case TRUNCATE -> 3;
            case OTHER -> 4;
        };
    }

    /** L'azione di una singola istruzione (vedi {@link #actionOf(SqlScript)}). */
    public static Action actionOf(String sql) {
        List<SqlLexer.Token> t = SqlLexer.tokenize(SqlLexer.innermost(sql));
        int i = SqlLexer.firstMeaningful(t);
        if (i < 0) {
            return Action.OTHER;
        }
        if (t.get(i).isWord("TRUNCATE")) {
            return Action.TRUNCATE;
        }
        if (!t.get(i).isWord("DROP")) {
            return Action.OTHER;
        }
        i++;
        if (i < t.size() && t.get(i).isWord("TEMPORARY")) {
            i++;
        }
        if (i >= t.size()) {
            return Action.OTHER;
        }
        return switch (t.get(i).upper()) {
            case "TABLE" -> Action.DROP_TABLE;
            case "VIEW" -> Action.DROP_VIEW;
            case "DATABASE", "SCHEMA" -> Action.DROP_CATALOG;
            default -> Action.OTHER;
        };
    }

    /**
     * Nome (non qualificato) dell'oggetto colpito da un'istruzione distruttiva; {@code null} se non si riconosce.
     * {@code DROP TABLE `c`.`libri`} → {@code libri}; {@code DELETE FROM soci} → {@code soci}.
     */
    static String targetOf(String sql) {
        List<SqlLexer.Token> t = SqlLexer.tokenize(SqlLexer.innermost(sql));
        int i = SqlLexer.firstMeaningful(t);
        if (i < 0) {
            return null;
        }
        String verb = t.get(i).upper();
        i++;
        switch (verb) {
            case "DROP" -> {
                if (i < t.size() && t.get(i).isWord("TEMPORARY")) {
                    i++;
                }
                if (i >= t.size() || !DROPPABLE.contains(t.get(i).upper())) {
                    return null;
                }
                i = SqlLexer.skipIfExists(t, i + 1);
            }
            case "TRUNCATE" -> {
                if (i < t.size() && t.get(i).isWord("TABLE")) {
                    i++;
                }
            }
            case "DELETE" -> {
                while (i < t.size() && (t.get(i).isWord("LOW_PRIORITY") || t.get(i).isWord("QUICK")
                        || t.get(i).isWord("IGNORE"))) {
                    i++;
                }
                if (i < t.size() && t.get(i).isWord("FROM")) {
                    i++;
                }
            }
            case "UPDATE" -> {
                while (i < t.size() && (t.get(i).isWord("LOW_PRIORITY") || t.get(i).isWord("IGNORE"))) {
                    i++;
                }
            }
            case "ALTER" -> {
                while (i < t.size() && !t.get(i).isWord("TABLE")) {
                    i++;
                }
                i++;
            }
            case "CREATE" -> {
                while (i < t.size() && !t.get(i).isWord("TABLE")) {
                    i++;
                }
                i = SqlLexer.skipIfExists(t, i + 1);
            }
            default -> {
                return null;
            }
        }
        String[] name = SqlLexer.qualifiedName(t, i);
        if (name == null) {
            return null;
        }
        // DROP TABLE a, b · DELETE a, b FROM … : più oggetti in una istruzione → la parola di conferma
        int after = i + (name[0] == null ? 1 : 3);
        if (after < t.size() && t.get(after).type() == SqlLexer.Type.OTHER && t.get(after).text().equals(",")) {
            return null;
        }
        return name[1];
    }
}
