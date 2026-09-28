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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.data.ValueValidator;
import it.ramasql.core.importer.ValueParsing.DateOrder;
import it.ramasql.core.importer.ValueParsing.DecimalStyle;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;

/**
 * Trasforma un valore del file nel testo che si manda al server per la colonna di destinazione, <b>prima</b>
 * dell'inserimento, e rifiuta con una spiegazione in italiano ciò che il server non accetterebbe (o accetterebbe
 * storpiandolo): testo in una colonna numerica, data inesistente, valore vuoto in una colonna {@code NOT NULL}, testo
 * troppo lungo, numero fuori intervallo. Dopo la conversione il valore passa da {@link ValueValidator}, lo stesso
 * controllo delle celle del data-entry, quindi le due strade non si contraddicono.
 *
 * <ul>
 *   <li>numeri: virgola o punto decimale secondo lo stile della colonna del file ({@code 1.234,50} → {@code 1234.50});</li>
 *   <li>date: {@code gg/mm/aaaa} (o l'ordine scelto) e ISO → {@code aaaa-mm-gg}; con l'ora → {@code aaaa-mm-gg hh:mm:ss};</li>
 *   <li>valori logici: {@code vero/falso}, {@code sì/no}, {@code true/false} → {@code 1}/{@code 0};</li>
 *   <li>vuoto → {@code NULL} (se l'opzione «stringa vuota = NULL» è attiva; altrimenti resta testo vuoto nelle
 *       colonne di testo). Nelle colonne {@code AUTO_INCREMENT} {@code NULL} fa generare il numero al server.</li>
 * </ul>
 */
public final class ValueConverter {

    /** Il valore non si può importare in quella colonna: {@link #getMessage()} dice perché, in italiano. */
    public static final class Rejected extends Exception {
        private static final long serialVersionUID = 1L;

        public Rejected(String message) {
            super(message, null, false, false);
        }
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter DATE_TIME_MICROS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS", Locale.ROOT);

    private ValueConverter() {
    }

    /**
     * @param raw         valore letto dal file (vedi {@link SourceRow})
     * @param target      colonna di destinazione
     * @param decimals    stile decimale della colonna del file
     * @param dates       ordine delle date della colonna del file
     * @param emptyIsNull un testo vuoto vale {@code NULL}
     * @return il testo da inviare; {@code null} = {@code NULL}
     */
    public static String convert(Object raw, ColumnDef target, DecimalStyle decimals, DateOrder dates,
            boolean emptyIsNull) throws Rejected {
        String type = SqlTypes.canonical(target.dataType());
        boolean textual = SqlTypes.isText(type) && !type.equals("ENUM") && !type.equals("SET");
        String text = raw == null ? null : raw instanceof BigDecimal d ? d.toPlainString() : raw.toString();
        if (text != null && text.isEmpty() && !(textual && !emptyIsNull)) {
            text = null;
        }
        if (text != null && text.isBlank() && !textual) {
            text = null;   // spazi in una colonna numerica o di date: vuoto
        }
        if (text == null) {
            if (target.nullable() || target.autoIncrement()) {
                return null;
            }
            throw new Rejected(CoreMessages.get("import.reject.notNull", target.name()));
        }
        String value = textual ? text : normalize(raw, text.trim(), target, type, decimals, dates);
        ValueValidator.Result check = ValueValidator.validate(value, target);
        if (!check.valid()) {
            throw new Rejected(CoreMessages.get("import.reject.invalid", shorten(text), target.name(),
                    check.message()));
        }
        return value;
    }

    private static String normalize(Object raw, String t, ColumnDef target, String type, DecimalStyle decimals,
            DateOrder dates) throws Rejected {
        if (SqlTypes.isBoolean(target)) {
            if (raw instanceof Boolean b) {
                return b ? "1" : "0";
            }
            Boolean b = ValueParsing.parseBoolean(t);
            if (b != null) {
                return b ? "1" : "0";
            }
        }
        if (raw instanceof Boolean b && SqlTypes.isNumeric(type)) {
            return b ? "1" : "0";
        }
        if (raw instanceof Number) {
            decimals = DecimalStyle.DOT;   // numero JSON: già un numero, con il punto
        }
        if (SqlTypes.isInteger(type) || type.equals("YEAR")) {
            BigDecimal n = ValueParsing.parseNumber(t, decimals);
            if (n == null) {
                throw reject(t, target, "import.reject.notInteger");
            }
            try {
                return n.stripTrailingZeros().toBigIntegerExact().toString();
            } catch (ArithmeticException e) {
                throw reject(t, target, "import.reject.notInteger");
            }
        }
        if (type.equals("DECIMAL") || SqlTypes.isApproximate(type)) {
            BigDecimal n = ValueParsing.parseNumber(t, decimals);
            if (n == null) {
                throw reject(t, target, decimals == DecimalStyle.COMMA
                        ? "import.reject.notNumberComma" : "import.reject.notNumberDot");
            }
            return n.toPlainString();
        }
        if (type.equals("DATE")) {
            LocalDateTime d = ValueParsing.parseDateTime(t, dates);
            if (d == null || !d.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                throw reject(t, target, "import.reject.date." + dates.name());
            }
            return d.toLocalDate().toString();
        }
        if (type.equals("DATETIME") || type.equals("TIMESTAMP")) {
            LocalDateTime d = ValueParsing.parseDateTime(t, dates);
            if (d == null) {
                throw reject(t, target, "import.reject.datetime." + dates.name());
            }
            return (d.getNano() == 0 ? DATE_TIME : DATE_TIME_MICROS).format(d);
        }
        if (type.equals("TIME")) {
            LocalTime time = ValueParsing.parseTime(t);
            return time == null ? t : time.toString().length() == 5 ? time + ":00" : time.toString();
        }
        return t;
    }

    private static Rejected reject(String value, ColumnDef target, String key) {
        return new Rejected(CoreMessages.get(key, shorten(value), target.name()));
    }

    /** Un valore lunghissimo non riempie il rapporto: si cita l'inizio. */
    static String shorten(String s) {
        String one = s.replace('\n', ' ').replace('\r', ' ');
        return one.length() <= 60 ? one : one.substring(0, 57) + "…";
    }

    /** Solo per la documentazione del formato delle date nei messaggi. */
    static String iso(LocalDate d) {
        return d.toString();
    }
}
