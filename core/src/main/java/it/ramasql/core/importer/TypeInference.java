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

import java.math.BigInteger;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.importer.ValueParsing.DateOrder;
import it.ramasql.core.importer.ValueParsing.DecimalStyle;
import it.ramasql.core.metadata.ColumnDef;

/**
 * Deduce il tipo SQL di una colonna del file dal suo {@link ColumnProfile}: è il tipo proposto per la <b>nuova
 * tabella</b>, che l'utente può cambiare. Regole, in ordine:
 * <ol>
 *   <li>colonna tutta vuota → {@code VARCHAR(255)} (non si sa nulla: il testo accetta tutto);</li>
 *   <li>oggetti o elenchi annidati (JSON) → {@code TEXT} con il loro testo JSON;</li>
 *   <li>solo valori logici ({@code true/false}, {@code vero/falso}, {@code sì/no}) → {@code TINYINT(1)}, cioè
 *       {@code BOOLEAN};</li>
 *   <li>solo interi senza zeri davanti → {@code INT}, o {@code BIGINT} se non ci stanno; un intero con zeri davanti
 *       (CAP {@code 00144}, telefono {@code 0541…}) o con il {@code +} ({@code +39…}), o in una colonna il cui nome dice
 *       che è un codice ({@code telefono}, {@code cellulare}, {@code cap}, {@code isbn}, {@code matricola},
 *       {@code codice_fiscale}…) è un <b>codice</b> e rende la colonna testo;</li>
 *   <li>numeri con decimali → {@code DECIMAL(p,s)} con cifre e decimali quanti servono; la virgola decimale italiana
 *       ({@code 12,50}) si riconosce, e decide lo stile della colonna; notazione esponenziale → {@code DOUBLE};</li>
 *   <li>date {@code gg/mm/aaaa} (o {@code mm/gg/aaaa} se il secondo numero supera 12) e ISO {@code aaaa-mm-gg}, tutte
 *       esistenti → {@code DATE}; con l'ora → {@code DATETIME}; solo ore → {@code TIME};</li>
 *   <li>altrimenti testo: {@code CHAR(n)} se tutti i valori hanno la stessa lunghezza (fino a 20: codici come ISBN,
 *       tessere), {@code VARCHAR(n)} fino a 255 caratteri (n arrotondato: 10, 20, 50, 100, 150, 200, 255),
 *       {@code TEXT} oltre, {@code MEDIUMTEXT} per testi lunghissimi.</li>
 * </ol>
 * Annullabile se la colonna ha almeno un valore vuoto.
 */
public final class TypeInference {

    private static final int[] VARCHAR_STEPS = {10, 20, 50, 100, 150, 200, 255};
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * Il tipo dedotto e come leggere i valori della colonna.
     *
     * @param column      definizione proposta (nome, tipo, argomenti, annullabile)
     * @param decimals    stile del separatore decimale dei valori
     * @param dates       ordine di giorno e mese nelle date con le barre
     * @param explanation perché questo tipo, in italiano (per il suggerimento della procedura guidata)
     */
    public record Inferred(ColumnDef column, DecimalStyle decimals, DateOrder dates, String explanation) {
    }

    private TypeInference() {
    }

    public static Inferred infer(ColumnProfile p) {
        boolean nullable = p.empty > 0;
        DecimalStyle style = p.allComma && p.commaMark ? DecimalStyle.COMMA : DecimalStyle.DOT;
        DateOrder order = dateOrder(p);
        if (p.nonEmpty == 0) {
            return result(p, "VARCHAR", "255", true, style, order, "import.type.empty");
        }
        if (p.nested) {
            return result(p, "TEXT", null, nullable, style, order, "import.type.nested");
        }
        if (p.allBoolean) {
            return result(p, "TINYINT", "1", nullable, style, order, "import.type.boolean");
        }
        if (p.allInteger && (p.plusSign || p.codeName)) {
            // cifre che sono un codice: telefono (+39…), CAP, ISBN, matricola… (lo dicono il segno o il nome)
            return text(p, nullable, style, order, p.plusSign ? "import.type.plusSign" : "import.type.codeName");
        }
        if (p.allInteger && !p.leadingZero) {
            if (p.minInteger.compareTo(INT_MIN) >= 0 && p.maxInteger.compareTo(INT_MAX) <= 0) {
                return result(p, "INT", null, nullable, style, order, "import.type.int", p.minInteger, p.maxInteger);
            }
            if (p.minInteger.compareTo(LONG_MIN) >= 0 && p.maxInteger.compareTo(LONG_MAX) <= 0) {
                return result(p, "BIGINT", null, nullable, style, order, "import.type.bigint", p.minInteger,
                        p.maxInteger);
            }
            // troppo grande anche per BIGINT: è un codice
            return text(p, nullable, style, order, "import.type.hugeInteger");
        }
        if (p.allInteger && p.leadingZero) {
            return text(p, nullable, style, order, "import.type.leadingZero");
        }
        boolean numeric = style == DecimalStyle.COMMA ? p.allComma : p.allDot;
        if (numeric && !p.leadingZero) {
            if (p.exponent) {
                return result(p, "DOUBLE", null, nullable, style, order, "import.type.double");
            }
            int intDigits = style == DecimalStyle.COMMA ? p.commaIntDigits : p.dotIntDigits;
            int scale = style == DecimalStyle.COMMA ? p.commaScale : p.dotScale;
            scale = Math.min(scale, 30);
            int precision = Math.max(intDigits + scale, scale + 1);
            if (precision > 65) {
                return result(p, "DOUBLE", null, nullable, style, order, "import.type.double");
            }
            String key = style == DecimalStyle.COMMA ? "import.type.decimalComma" : "import.type.decimalDot";
            return result(p, "DECIMAL", precision + "," + scale, nullable, style, order, key, intDigits, scale);
        }
        if (p.allDate && order != null) {
            if (p.anyTime) {
                return result(p, "DATETIME", null, nullable, style, order, "import.type.datetime." + order.name());
            }
            return result(p, "DATE", null, nullable, style, order, "import.type.date." + order.name());
        }
        if (p.allTime) {
            return result(p, "TIME", null, nullable, style, order, "import.type.time");
        }
        return text(p, nullable, style, order, null);
    }

    /**
     * Ordine delle date della colonna, se tutte le date sono valide con quell'ordine; {@code null} se non sono date
     * (o alcune non esistono, come 31/02).
     */
    static DateOrder dateOrder(ColumnProfile p) {
        if (!p.allDate || p.nonEmpty == 0) {
            return null;
        }
        if (!p.anySlashDate) {
            return p.validDates(DateOrder.YMD) ? DateOrder.YMD : null;
        }
        if (p.firstOver12 && p.secondOver12) {
            return null;   // 31/12 e 12/31 insieme: non sono date di un solo formato
        }
        DateOrder order = p.secondOver12 ? DateOrder.MDY : DateOrder.DMY;
        return p.validDates(order) ? order : null;
    }

    private static Inferred text(ColumnProfile p, boolean nullable, DecimalStyle style, DateOrder order,
            String reasonKey) {
        int n = p.maxLength;
        if (n > 16_383) {
            return result(p, "MEDIUMTEXT", null, nullable, style, order, "import.type.mediumtext", n);
        }
        if (n > 255) {
            return result(p, "TEXT", null, nullable, style, order, "import.type.text", n);
        }
        if (p.minLength == n && n <= 20 && p.nonEmpty > 1) {
            return result(p, "CHAR", String.valueOf(n), nullable, style, order,
                    reasonKey != null ? reasonKey : "import.type.char", n);
        }
        int size = 255;
        for (int step : VARCHAR_STEPS) {
            if (n <= step) {
                size = step;
                break;
            }
        }
        return result(p, "VARCHAR", String.valueOf(size), nullable, style, order,
                reasonKey != null ? reasonKey : "import.type.varchar", n);
    }

    private static Inferred result(ColumnProfile p, String type, String args, boolean nullable, DecimalStyle style,
            DateOrder order, String key, Object... explanationArgs) {
        ColumnDef column = ColumnDef.of(p.name(), type, args).withNullable(nullable);
        return new Inferred(column, style, order == null ? DateOrder.DMY : order,
                CoreMessages.get(key, explanationArgs));
    }
}
