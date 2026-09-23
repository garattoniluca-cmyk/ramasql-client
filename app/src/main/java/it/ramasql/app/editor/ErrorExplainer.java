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

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.app.Texts;

/**
 * Errori del server spiegati agli studenti (DESIGN §2): il messaggio originale resta com'è, a fianco si aggiunge una
 * riga in italiano per i codici frequenti (tabella nel file di risorse, chiavi {@code editor.error.explain.<codice>})
 * e, quando il messaggio lo dice, la posizione ({@code near '…' at line N} dell'errore 1064 e simili).
 */
public final class ErrorExplainer {

    private static final String PREFIX = "editor.error.explain.";
    private static final ResourceBundle TEXTS = ResourceBundle.getBundle("it.ramasql.app.messages");
    /** MariaDB e MySQL: «… near 'FORM libri' at line 3». Il testo citato può contenere apici e a-capo. */
    private static final Pattern NEAR_AT_LINE = Pattern.compile("near '(.*)' at line (\\d+)\\s*$", Pattern.DOTALL);

    /**
     * Posizione di un errore dentro l'istruzione inviata.
     *
     * @param line riga (da 1) nel testo dell'istruzione
     * @param near il testo dal punto dell'errore in poi, come lo cita il server ({@code ""} = fine dell'istruzione)
     */
    public record Location(int line, String near) {
    }

    private ErrorExplainer() {
    }

    /** La riga di spiegazione in italiano per il codice, se è tra i frequenti. */
    public static Optional<String> explain(int code) {
        String key = PREFIX + code;
        return TEXTS.containsKey(key) ? Optional.of(TEXTS.getString(key)) : Optional.empty();
    }

    /** La posizione citata nel messaggio del server, se c'è. */
    public static Optional<Location> locate(String serverMessage) {
        if (serverMessage == null) {
            return Optional.empty();
        }
        Matcher m = NEAR_AT_LINE.matcher(serverMessage);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Location(Integer.parseInt(m.group(2)), m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Il testo mostrato sotto i risultati: messaggio originale del server, poi la spiegazione (se c'è).
     *
     * @param statementNumber numero (da 1) dell'istruzione nell'esecuzione
     * @param documentLine    riga dell'editor in cui l'errore è stato evidenziato, {@code 0} se nessuna
     */
    public static String describe(SqlError error, int statementNumber, int documentLine) {
        StringBuilder sb = new StringBuilder();
        String state = error.sqlState().isEmpty() ? "-" : error.sqlState();
        sb.append(Texts.get("editor.error.server", statementNumber, error.code(), state, error.message()));
        explain(error.code()).ifPresent(e -> sb.append('\n').append(Texts.get("editor.error.explanation", e)));
        if (documentLine > 0) {
            sb.append('\n').append(Texts.get("editor.error.position", documentLine));
        }
        return sb.toString();
    }
}
