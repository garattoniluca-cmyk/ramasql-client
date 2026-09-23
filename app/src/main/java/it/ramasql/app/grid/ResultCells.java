/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import it.ramasql.core.exec.ResultTable;

/**
 * Da valore letto dal server a <b>testo della cella</b>. Un solo posto per questa conversione, perché il testo che si
 * vede nella griglia è anche quello che il generatore DML riscrive nell'{@code UPDATE}: se la lettura e la scrittura
 * non usassero la stessa forma, un andata-e-ritorno cambierebbe i dati.
 *
 * <p>Regole: {@code NULL} resta {@code null} (la griglia lo mostra come «NULL» in corsivo, non come la stringa
 * «NULL»); le date e gli orari prendono la forma che MariaDB e MySQL accettano nei letterali
 * ({@code 2026-09-23 10:15:00}, non {@code 2026-09-23T10:15}); i decimali non passano mai per la notazione
 * scientifica; i valori binari diventano un letterale {@code 0x…} (in v1 il contenuto binario si vede e si copia, non
 * si modifica: l'editor BLOB è {@code IDEA-011}).
 */
public final class ResultCells {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private ResultCells() {
    }

    /** Tutte le righe di un risultato, convertite in testo. */
    public static List<List<String>> rows(ResultTable table) {
        List<List<String>> result = new ArrayList<>(table.rowCount());
        for (int r = 0; r < table.rowCount(); r++) {
            List<String> row = new ArrayList<>(table.columnCount());
            for (int c = 0; c < table.columnCount(); c++) {
                row.add(text(table.value(r, c)));
            }
            result.add(row);
        }
        return result;
    }

    /** Il testo di una cella; {@code null} per {@code NULL}. */
    public static String text(Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case byte[] bytes -> "0x" + HexFormat.of().withUpperCase().formatHex(bytes);
            case Boolean b -> b ? "1" : "0";
            case BigDecimal d -> d.toPlainString();
            case LocalDateTime d -> withFraction(DATE_TIME.format(d), d.getNano());
            case LocalDate d -> d.toString();
            case LocalTime t -> withFraction(TIME.format(t), t.getNano());
            case OffsetDateTime d -> withFraction(DATE_TIME.format(d.toLocalDateTime()), d.getNano());
            case java.sql.Timestamp t -> text(t.toLocalDateTime());
            case java.sql.Date d -> d.toLocalDate().toString();
            case java.sql.Time t -> text(t.toLocalTime());
            default -> String.valueOf(value);
        };
    }

    /** Aggiunge i decimali di secondo solo se ci sono (i server li scrivono così nei valori con precisione). */
    private static String withFraction(String base, int nanos) {
        if (nanos == 0) {
            return base;
        }
        String micros = String.format(Locale.ROOT, "%06d", nanos / 1000);
        while (micros.endsWith("0")) {
            micros = micros.substring(0, micros.length() - 1);
        }
        return micros.isEmpty() ? base : base + "." + micros;
    }
}
