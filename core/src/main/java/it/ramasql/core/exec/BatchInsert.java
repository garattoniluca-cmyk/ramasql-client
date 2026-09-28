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

import it.ramasql.core.sqlgen.SqlIdentifiers;

/**
 * Un inserimento <b>a lotti</b> con un'istruzione preparata (l'importazione dello Step 9): la pipeline lo mostra
 * come {@link #statement()} — {@code INSERT INTO `c`.`t` (`a`, `b`) VALUES (?, ?)} — e {@link SqlExecutor} lo
 * esegue lotto per lotto, <b>in autocommit</b> (mai transazioni, {@code ADR-010}).
 *
 * <p>Come si esegue un lotto ({@code ADR-025}):
 * <ul>
 *   <li>tabella <b>InnoDB</b> ({@link #atomic()}): un lotto è <b>una sola</b> {@code INSERT} con tante righe di
 *       valori — {@code VALUES (?, ?), (?, ?), …} — quindi un solo commit per lotto (con un commit per riga un milione
 *       di righe richiederebbe ore). Un'istruzione InnoDB che fallisce non lascia nulla: le righe di quel lotto si
 *       ritentano allora <b>una per volta</b>, per sapere quale riga ha causato l'errore e inserire le altre;</li>
 *   <li>altre tabelle (MyISAM): un'istruzione che fallisce a metà lascia le righe precedenti, quindi le righe si
 *       mandano una per volta (in un lotto JDBC, senza attese fra l'una e l'altra) e ciascuna ha il suo esito.</li>
 * </ul>
 *
 * @param catalog          catalogo della tabella
 * @param table            tabella
 * @param columns          colonne da riempire, nell'ordine dei parametri
 * @param atomic           la tabella annulla da sé un'istruzione non riuscita (InnoDB)
 * @param ignoreDuplicates una riga rifiutata per chiave duplicata (1062) si conta come «duplicato ignorato» e non
 *                         come errore
 * @param origin           origine per il registro («Importazione»)
 */
public record BatchInsert(String catalog, String table, List<String> columns, boolean atomic,
        boolean ignoreDuplicates, String origin) {

    /** Righe al massimo per istruzione (e per lotto). */
    public static final int ROWS_PER_STATEMENT = 1000;
    /** Segnaposti al massimo in un'istruzione preparata (limite del protocollo). */
    static final int MAX_PLACEHOLDERS = 65_535;

    public BatchInsert {
        Objects.requireNonNull(table, "table");
        columns = List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("nessuna colonna da inserire");
        }
        origin = origin == null ? "" : origin;
    }

    /** L'istruzione come la vede lo studente (e come si esegue una riga alla volta). */
    public SqlStatement statement() {
        return new SqlStatement(head() + " " + row(), origin, RiskLevel.MODIFIES);
    }

    /** {@code INSERT INTO `c`.`t` (`a`, `b`) VALUES} */
    String head() {
        return "INSERT INTO " + SqlIdentifiers.qualified(catalog, table) + " " + SqlIdentifiers.columnList(columns)
                + " VALUES";
    }

    /** {@code (?, ?)} */
    String row() {
        return "(" + String.join(", ", java.util.Collections.nCopies(columns.size(), "?")) + ")";
    }

    /** L'istruzione per {@code rows} righe di valori. */
    String multiRow(int rows) {
        String one = row();
        StringBuilder sb = new StringBuilder(head().length() + rows * (one.length() + 2));
        sb.append(head()).append(' ');
        for (int i = 0; i < rows; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(one);
        }
        return sb.toString();
    }

    /** Righe per lotto: {@link #ROWS_PER_STATEMENT}, meno se le colonne sono tante. */
    public int rowsPerBatch() {
        return Math.max(1, Math.min(ROWS_PER_STATEMENT, MAX_PLACEHOLDERS / columns.size()));
    }
}
