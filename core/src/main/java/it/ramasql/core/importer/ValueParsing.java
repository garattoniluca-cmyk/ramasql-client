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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Come si leggono numeri, date e valori logici scritti in un file: con la virgola decimale all'italiana
 * ({@code 1.234,56}) o con il punto ({@code 1234.56}), con le date {@code gg/mm/aaaa}, {@code mm/gg/aaaa} o ISO
 * {@code aaaa-mm-gg}, con {@code vero}/{@code falso}. Lo usano la deduzione dei tipi ({@link TypeInference}) e la
 * conversione dei valori prima dell'inserimento ({@link ValueConverter}), così le due decisioni coincidono.
 */
public final class ValueParsing {

    /** Separatore decimale di una colonna. */
    public enum DecimalStyle {
        /** {@code 1234.56} (eventuali migliaia con la virgola: {@code 1,234.56}). */
        DOT,
        /** {@code 1234,56} o {@code 1.234,56}: Excel e LibreOffice in italiano. */
        COMMA
    }

    /** Ordine di giorno, mese e anno nelle date con le barre. */
    public enum DateOrder {
        /** {@code 31/12/2025}: l'uso italiano (predefinito). */
        DMY,
        /** {@code 12/31/2025}: l'uso americano. */
        MDY,
        /** {@code 2025-12-31}: ISO, la forma dell'SQL. */
        YMD
    }

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern DOT_DECIMAL = Pattern.compile("[+-]?(\\d+|\\d{1,3}(,\\d{3})+)?\\.\\d+|[+-]?\\d{1,3}(,\\d{3})+");
    private static final Pattern COMMA_DECIMAL = Pattern.compile("[+-]?(\\d+|\\d{1,3}(\\.\\d{3})+)?,\\d+|[+-]?\\d{1,3}(\\.\\d{3})+");
    private static final Pattern EXPONENT = Pattern.compile("[+-]?(\\d+([.,]\\d*)?|[.,]\\d+)[eE][+-]?\\d+");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern SLASH_DATE = Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2})(?:[.,](\\d{1,6}))?)?");

    private ValueParsing() {
    }

    // ---------------------------------------------------------------- numeri

    /** Intero scritto senza separatori: {@code 42}, {@code -7}, {@code +3}. */
    public static boolean isInteger(String s) {
        return INTEGER.matcher(s).matches();
    }

    /** Un intero con zeri davanti ({@code 00144}, {@code 0541…}): è un codice (CAP, telefono), non un numero. */
    public static boolean hasLeadingZero(String s) {
        String digits = s.startsWith("+") || s.startsWith("-") ? s.substring(1) : s;
        return digits.length() > 1 && digits.charAt(0) == '0' && INTEGER.matcher(s).matches();
    }

    /** Un numero con la parte decimale (o le migliaia) scritta nello stile dato. */
    public static boolean isDecimal(String s, DecimalStyle style) {
        return (style == DecimalStyle.DOT ? DOT_DECIMAL : COMMA_DECIMAL).matcher(s).matches();
    }

    /** Numero in notazione esponenziale ({@code 1.5E3}). */
    public static boolean isExponent(String s) {
        return EXPONENT.matcher(s).matches();
    }

    /** Sembra un numero, in un qualunque stile (serve a riconoscere l'intestazione). */
    public static boolean isNumberLike(String s) {
        return isInteger(s) || isDecimal(s, DecimalStyle.DOT) || isDecimal(s, DecimalStyle.COMMA) || isExponent(s);
    }

    /**
     * Il valore di un numero scritto nello stile dato, oppure {@code null} se non è un numero in quello stile.
     * Gli interi si accettano in ogni stile.
     */
    public static BigDecimal parseNumber(String s, DecimalStyle style) {
        String t = s.trim();
        if (isInteger(t)) {
            return new BigDecimal(t.startsWith("+") ? t.substring(1) : t);
        }
        if (isExponent(t)) {
            return new BigDecimal(t.replace(',', '.').replace("+", "").replaceFirst("^\\.", "0."));
        }
        if (!isDecimal(t, style)) {
            return null;
        }
        String plain = style == DecimalStyle.DOT ? t.replace(",", "") : t.replace(".", "").replace(',', '.');
        if (plain.startsWith("+")) {
            plain = plain.substring(1);
        }
        if (plain.startsWith(".") || plain.startsWith("-.")) {
            plain = plain.replaceFirst("\\.", "0.");
        }
        return new BigDecimal(plain);
    }

    // ---------------------------------------------------------------- date e ore

    /** Sembra una data (con o senza ora), valida o no: serve a riconoscere l'intestazione. */
    public static boolean isDateLike(String s) {
        String date = s.trim().split("[ T]", 2)[0];
        return ISO_DATE.matcher(date).matches() || SLASH_DATE.matcher(date).matches();
    }

    /**
     * Forma della data: ISO ({@link DateOrder#YMD}), con le barre (giorno e mese da decidere con l'ordine), o
     * {@code null} se non è una data.
     */
    public static DateOrder dateShape(String datePart) {
        if (ISO_DATE.matcher(datePart).matches()) {
            return DateOrder.YMD;
        }
        return SLASH_DATE.matcher(datePart).matches() ? DateOrder.DMY : null;
    }

    /** Primo e secondo numero di una data con le barre ({@code 31/12/2025} → 31, 12), per decidere l'ordine. */
    static int[] slashParts(String datePart) {
        Matcher m = SLASH_DATE.matcher(datePart);
        return m.matches() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    /**
     * La data, oppure {@code null} se non è una data esistente. La forma ISO si accetta sempre; quelle con le barre
     * secondo {@code order} ({@link DateOrder#YMD} = solo ISO).
     */
    public static LocalDate parseDate(String s, DateOrder order) {
        String t = s.trim();
        try {
            Matcher iso = ISO_DATE.matcher(t);
            if (iso.matches()) {
                return year(LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                        Integer.parseInt(iso.group(3))));
            }
            Matcher m = SLASH_DATE.matcher(t);
            if (m.matches() && order != DateOrder.YMD) {
                int a = Integer.parseInt(m.group(1));
                int b = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                return year(order == DateOrder.DMY ? LocalDate.of(y, b, a) : LocalDate.of(y, a, b));
            }
        } catch (DateTimeException e) {
            return null;   // 31/02, 30/13…
        }
        return null;
    }

    private static LocalDate year(LocalDate d) {
        return d.getYear() >= 1000 && d.getYear() <= 9999 ? d : null;
    }

    /** L'ora {@code hh:mm[:ss[.ffffff]]}, oppure {@code null}. */
    public static LocalTime parseTime(String s) {
        Matcher m = TIME.matcher(s.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            int nanos = 0;
            if (m.group(4) != null) {
                String f = (m.group(4) + "000000").substring(0, 6);
                nanos = Integer.parseInt(f) * 1000;
            }
            return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    m.group(3) == null ? 0 : Integer.parseInt(m.group(3)), nanos);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Data e ora ({@code 2025-12-31 08:30}, {@code 31/12/2025 08:30:15}), oppure {@code null}. Senza ora: mezzanotte. */
    public static LocalDateTime parseDateTime(String s, DateOrder order) {
        String t = s.trim();
        String[] parts = t.split("[ T]", 2);
        LocalDate date = parseDate(parts[0], order);
        if (date == null) {
            return null;
        }
        if (parts.length == 1) {
            return date.atStartOfDay();
        }
        LocalTime time = parseTime(parts[1].trim());
        return time == null ? null : LocalDateTime.of(date, time);
    }

    /** Il valore ha anche l'ora ({@code aaaa-mm-gg hh:mm}). */
    public static boolean hasTime(String s) {
        String[] parts = s.trim().split("[ T]", 2);
        return parts.length == 2 && TIME.matcher(parts[1].trim()).matches();
    }

    // ---------------------------------------------------------------- valori logici

    /** {@code true/false}, {@code vero/falso}, {@code sì/si/no}; {@code null} se non è un valore logico. */
    public static Boolean parseBoolean(String s) {
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "true", "vero", "sì", "si" -> Boolean.TRUE;
            case "false", "falso", "no" -> Boolean.FALSE;
            default -> null;
        };
    }
}
