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

import java.util.Objects;

import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlStatement;

/**
 * Un'istruzione dell'editor SQL già separata dalle altre, con la sua posizione nel documento: serve a chi esegue
 * (testo, origine, rischio) e all'editor per riportare gli errori al punto giusto.
 *
 * @param statement   istruzione della pipeline (testo senza separatore finale, origine «Editor SQL», rischio)
 * @param startOffset posizione del primo carattere nel documento dell'editor
 * @param endOffset   posizione dopo l'ultimo carattere (esclusa)
 * @param line        riga del documento (da 1) in cui l'istruzione comincia: la «riga 1» dei messaggi del server
 */
public record PositionedStatement(SqlStatement statement, int startOffset, int endOffset, int line) {

    public PositionedStatement {
        Objects.requireNonNull(statement, "statement");
        if (startOffset < 0 || endOffset < startOffset || line < 1) {
            throw new IllegalArgumentException("Posizione non valida: " + startOffset + "-" + endOffset + " riga " + line);
        }
    }

    public String text() {
        return statement.text();
    }

    public RiskLevel risk() {
        return statement.risk();
    }
}
