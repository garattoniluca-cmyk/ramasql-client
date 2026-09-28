/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.importer;

import java.util.Collections;
import java.util.List;

/**
 * Una riga del file, allineata alle colonne della sorgente ({@link ImportSource#columns()}).
 *
 * <p>Valori: {@code null} = assente (campo mancante in una riga CSV corta, chiave mancante o {@code null} in JSON);
 * {@link String} (tutti i valori CSV, le stringhe JSON); {@link java.math.BigDecimal} o {@link Long} (numeri JSON);
 * {@link Boolean} (JSON); {@link JsonText} (oggetto o elenco annidato in JSON, conservato come testo JSON).
 *
 * @param line        riga del file in cui comincia (da 1)
 * @param values      un valore per colonna
 * @param extraValues   valori non vuoti oltre l'ultima colonna (una riga CSV con più campi dell'intestazione): una
 *                      riga così non si importa, perché non si sa a quale colonna appartengano
 * @param missingValues campi mancanti in fondo (una riga CSV più corta dell'intestazione): valgono come vuoti
 */
public record SourceRow(long line, List<Object> values, int extraValues, int missingValues) {

    public SourceRow {
        values = Collections.unmodifiableList(values);
    }

    /** Oggetto o elenco annidato di un JSON, conservato come testo JSON. */
    public record JsonText(String json) {
        @Override
        public String toString() {
            return json;
        }
    }
}
