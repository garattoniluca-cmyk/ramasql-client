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
import java.math.BigInteger;

import it.ramasql.core.importer.ValueParsing.DecimalStyle;

/**
 * Quello che si sa dei valori di una colonna del file dopo averli visti tutti: la base di {@link TypeInference}. Si
 * aggiorna un valore alla volta ({@link #add(Object)}), con <b>memoria costante</b> anche per un milione di righe: per
 * sapere se una colonna {@code id} ha valori tutti diversi (e può essere la chiave primaria) basta una mappa di bit dei
 * valori da 1 a {@link #UNIQUE_LIMIT} (2 MB), oppure che i valori crescano sempre.
 */
public final class ColumnProfile {

    /** Valori della colonna {@code id} ricordati con un bit ciascuno (da 1 a questo numero): 2 MB al massimo. */
    static final int UNIQUE_LIMIT = 16_777_216;

    /** Nomi di colonna che indicano un codice fatto di cifre (telefono, CAP, ISBN…): testo, non numero. */
    private static final java.util.regex.Pattern CODE_NAME = java.util.regex.Pattern.compile(
            "(?i)(.*(tel|cell|phone|fax|zip|isbn|issn|codice|matricola|iban|piva|partita_?iva|fiscale).*"
                    + "|(.*_)?(cap|ean|cf|cod)(_.*)?)");

    final String name;
    long empty;
    long nonEmpty;
    boolean nested;
    boolean allBoolean = true;
    boolean allInteger = true;
    boolean leadingZero;
    boolean plusSign;
    final boolean codeName;
    long replacements;
    BigInteger minInteger;
    BigInteger maxInteger;
    boolean allDot = true;
    boolean allComma = true;
    boolean commaMark;
    boolean exponent;
    int dotIntDigits;
    int dotScale;
    int commaIntDigits;
    int commaScale;
    boolean allDate = true;
    boolean anyTime;
    boolean anySlashDate;
    boolean firstOver12;
    boolean secondOver12;
    boolean allTime = true;
    boolean invalidDmy;
    boolean invalidMdy;
    int maxLength;
    int minLength = Integer.MAX_VALUE;
    private final boolean trackUnique;
    private java.util.BitSet seen;
    private BigInteger last;
    private boolean increasing = true;
    private boolean inRange = true;
    boolean duplicate;

    public ColumnProfile(String name) {
        this.name = name;
        this.trackUnique = name.equalsIgnoreCase("id");
        this.seen = trackUnique ? new java.util.BitSet() : null;
        this.codeName = CODE_NAME.matcher(name).matches();
    }

    public String name() {
        return name;
    }

    /** Valori vuoti o assenti. */
    public long emptyCount() {
        return empty;
    }

    public long nonEmptyCount() {
        return nonEmpty;
    }

    /** Un valore assente nelle righe prima che la colonna comparisse (chiave JSON vista per la prima volta dopo). */
    void addMissing(long count) {
        empty += count;
    }

    /** Aggiunge un valore della colonna ({@code null} o testo vuoto = vuoto). */
    public void add(Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            empty++;
            return;
        }
        nonEmpty++;
        if (value instanceof SourceRow.JsonText j) {
            nested = true;
            length(j.json());
            notBoolean();
            notNumber();
            notDate();
            return;
        }
        if (value instanceof Boolean) {
            allInteger = false;
            notNumber();
            notDate();
            length(value.toString());
            return;
        }
        if (value instanceof Long l) {
            length(l.toString());
            notBoolean();
            notDate();
            integer(BigInteger.valueOf(l));
            return;
        }
        if (value instanceof BigDecimal d) {
            length(d.toPlainString());
            notBoolean();
            notDate();
            jsonDecimal(d);
            return;
        }
        String s = value.toString().trim();
        if (value.toString().indexOf('�') >= 0) {
            replacements++;   // lettera non leggibile con la codifica scelta
        }
        length(value.toString());
        if (ValueParsing.parseBoolean(s) == null) {
            notBoolean();
        }
        text(s);
    }

    private void text(String s) {
        // numeri
        if (ValueParsing.isInteger(s)) {
            if (ValueParsing.hasLeadingZero(s)) {
                leadingZero = true;
            }
            if (s.startsWith("+") && s.length() >= 8) {
                plusSign = true;   // +39…: un prefisso telefonico, non un numero positivo
            }
            integer(new BigInteger(s.startsWith("+") ? s.substring(1) : s));
        } else {
            allInteger = false;
            boolean dot = ValueParsing.isDecimal(s, DecimalStyle.DOT);
            boolean comma = ValueParsing.isDecimal(s, DecimalStyle.COMMA);
            if (ValueParsing.isExponent(s)) {
                exponent = true;
                dot = true;
                comma = true;
            }
            if (!dot) {
                allDot = false;
            } else if (!exponent) {
                digits(ValueParsing.parseNumber(s, DecimalStyle.DOT), DecimalStyle.DOT);
            }
            if (!comma) {
                allComma = false;
            } else {
                if (s.indexOf(',') >= 0) {
                    commaMark = true;
                }
                if (!exponent) {
                    digits(ValueParsing.parseNumber(s, DecimalStyle.COMMA), DecimalStyle.COMMA);
                }
            }
        }
        // date e ore
        String datePart = s.split("[ T]", 2)[0];
        ValueParsing.DateOrder shape = ValueParsing.dateShape(datePart);
        if (shape == null) {
            allDate = false;
        } else {
            if (ValueParsing.hasTime(s)) {
                anyTime = true;
            } else if (!datePart.equals(s)) {
                allDate = false;   // «2025-01-05 dopo pranzo»
            }
            if (ValueParsing.parseDateTime(s, ValueParsing.DateOrder.DMY) == null) {
                invalidDmy = true;   // 31/02/2025, 2025-13-01…
            }
            if (ValueParsing.parseDateTime(s, ValueParsing.DateOrder.MDY) == null) {
                invalidMdy = true;
            }
            if (shape == ValueParsing.DateOrder.DMY) {
                anySlashDate = true;
                int[] parts = ValueParsing.slashParts(datePart);
                if (parts[0] > 12) {
                    firstOver12 = true;
                }
                if (parts[1] > 12) {
                    secondOver12 = true;
                }
            }
        }
        if (ValueParsing.parseTime(s) == null) {
            allTime = false;
        }
    }

    private void integer(BigInteger v) {
        minInteger = minInteger == null || v.compareTo(minInteger) < 0 ? v : minInteger;
        maxInteger = maxInteger == null || v.compareTo(maxInteger) > 0 ? v : maxInteger;
        int digits = v.abs().toString().length();
        dotIntDigits = Math.max(dotIntDigits, digits);
        commaIntDigits = Math.max(commaIntDigits, digits);
        allTime = false;
        if (trackUnique) {
            remember(v);
        }
    }

    private void jsonDecimal(BigDecimal d) {
        allInteger = false;
        allComma = false;
        allTime = false;
        if (d.scale() > 0 || d.precision() - d.scale() > 0) {
            digits(d, DecimalStyle.DOT);
        }
    }

    private void digits(BigDecimal v, DecimalStyle style) {
        if (v == null) {
            return;
        }
        BigDecimal abs = v.abs().stripTrailingZeros();
        int scale = Math.max(0, abs.scale());
        int intDigits = Math.max(1, abs.precision() - abs.scale());
        if (abs.signum() == 0) {
            intDigits = 1;
        }
        if (style == DecimalStyle.DOT) {
            dotIntDigits = Math.max(dotIntDigits, intDigits);
            dotScale = Math.max(dotScale, scale);
        } else {
            commaIntDigits = Math.max(commaIntDigits, intDigits);
            commaScale = Math.max(commaScale, scale);
        }
    }

    private void remember(BigInteger v) {
        if (duplicate) {
            return;
        }
        if (last != null && v.compareTo(last) <= 0) {
            increasing = false;
        }
        last = v;
        if (v.signum() > 0 && v.bitLength() < 32 && v.intValue() <= UNIQUE_LIMIT) {
            int i = v.intValue();
            if (seen.get(i)) {
                duplicate = true;
                seen = null;
                return;
            }
            seen.set(i);
        } else {
            inRange = false;   // fuori dalla mappa: resta unico solo se i valori crescono sempre
        }
    }

    private void length(String s) {
        int n = s.codePointCount(0, s.length());
        maxLength = Math.max(maxLength, n);
        minLength = Math.min(minLength, n);
    }

    private void notBoolean() {
        allBoolean = false;
    }

    private void notNumber() {
        allInteger = false;
        allDot = false;
        allComma = false;
        allTime = false;
    }

    private void notDate() {
        allDate = false;
        allTime = false;
    }

    /** Tutte le date della colonna esistono leggendole con quell'ordine (le ISO valgono sempre). */
    boolean validDates(ValueParsing.DateOrder order) {
        return order == ValueParsing.DateOrder.MDY ? !invalidMdy : !invalidDmy;
    }

    /**
     * La colonna ha valori interi tutti diversi, tutti da 1 in su, e nessun vuoto: può essere la chiave primaria con
     * {@code AUTO_INCREMENT} (uno 0 in una colonna AUTO_INCREMENT farebbe generare un numero nuovo).
     */
    public boolean uniqueIntegers() {
        boolean unique = !duplicate && (inRange || increasing);
        return trackUnique && unique && empty == 0 && nonEmpty > 0 && allInteger && !leadingZero && !plusSign
                && minInteger != null && minInteger.signum() > 0;
    }

    /** Valori con lettere illeggibili (�): la codifica scelta non è quella del file. */
    public long replacementCount() {
        return replacements;
    }
}
