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

import java.util.List;
import java.util.Objects;

/**
 * Un'istruzione della pipeline «anteprima SQL» (ARCHITECTURE.md §4): testo, origine e classe di rischio.
 *
 * @param text   testo SQL di una sola istruzione, senza {@code ;} finale
 * @param origin da dove nasce, in chiaro per il registro: «Editor di tabelle», «Data-entry», «Editor SQL»…
 * @param risk   classe di rischio
 */
public record SqlStatement(String text, String origin, RiskLevel risk) {

    public SqlStatement {
        Objects.requireNonNull(text, "text");
        origin = origin == null ? "" : origin;
        Objects.requireNonNull(risk, "risk");
    }

    /** Istruzione con rischio calcolato da {@link RiskClassifier}. */
    public static SqlStatement of(String text, String origin) {
        return new SqlStatement(text, origin, RiskClassifier.classify(text));
    }

    /** Converte i testi prodotti da un generatore, tutti con la stessa origine. */
    public static List<SqlStatement> listOf(List<String> texts, String origin) {
        return texts.stream().map(t -> of(t, origin)).toList();
    }
}
