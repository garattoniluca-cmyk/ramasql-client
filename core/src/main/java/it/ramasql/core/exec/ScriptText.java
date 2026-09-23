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

/** Scrittura di istruzioni in forma di script rieseguibile (anteprima e esportazione del registro). */
final class ScriptText {

    static final String ALT_DELIMITER = "$$";

    private ScriptText() {
    }

    /** L'istruzione con il suo terminatore: {@code ;}, oppure {@code DELIMITER $$} se contiene già dei {@code ;}. */
    static String terminated(String statement) {
        String text = statement.strip();
        java.util.List<StatementSplitter.SplitStatement> parts = StatementSplitter.split(text);
        // un ';' interno (corpo di una routine) o finale chiede un delimitatore diverso
        if (parts.size() == 1 && !text.endsWith(";")) {
            // un commento di riga in coda si mangerebbe il ';': in quel caso va a capo
            return parts.get(0).endOffset() < text.length() ? text + "\n;" : text + ";";
        }
        return "DELIMITER " + ALT_DELIMITER + "\n" + text + "\n" + ALT_DELIMITER + "\nDELIMITER ;";
    }

    /** Ogni riga preceduta da {@code -- }: l'istruzione resta leggibile ma non viene rieseguita. */
    static String commentedOut(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.strip().split("\\R", -1)) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append("-- ").append(line);
        }
        return out.toString();
    }
}
