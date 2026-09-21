/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.data;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.SqlTypes;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validazione del testo di una cella contro il tipo della colonna, prima di generare l'SQL: un valore non
 * valido marca la cella e blocca la <i>Conferma</i>. È volutamente più severa del server in modalità non
 * rigorosa (niente troncamenti né arrotondamenti silenziosi). I tipi non elencati passano sempre.
 */
public final class ValueValidator {

    /**
     * @param valid   esito
     * @param message spiegazione in italiano se non valido, altrimenti {@code ""}
     */
    public record Result(boolean valid, String message) {
        public static final Result OK = new Result(true, "");
    }

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("([+-]?)(\\d*)(?:\\.(\\d*))?");
    private static final Pattern APPROXIMATE = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern DATETIME =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:[ T](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d{1,6})?)?)?");
    private static final Pattern TIME = Pattern.compile("-?(\\d{1,3}):(\\d{2})(?::(\\d{2})(?:\\.\\d{1,6})?)?");
    private static final DateTimeFormatter STRICT_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private ValueValidator() {
    }

    /**
     * @param text   testo della cella; {@code null} = NULL
     * @param column colonna di destinazione
     */
    public static Result validate(String text, ColumnDef column) {
        if (text == null) {
            boolean acceptsNull = column.nullable() || column.autoIncrement() || !column.defaultValue().isNone();
            return acceptsNull ? Result.OK : error("validate.notNull", column.name());
        }
        if (SqlTypes.isBoolean(column)) {
            return text.matches("[01]") || text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")
                    ? Result.OK : error("validate.boolean");
        }
        String type = SqlTypes.canonical(column.dataType());
        return switch (type) {
            case "TINYINT" -> integer(text, column, 8);
            case "SMALLINT" -> integer(text, column, 16);
            case "MEDIUMINT" -> integer(text, column, 24);
            case "INT" -> integer(text, column, 32);
            case "BIGINT" -> integer(text, column, 64);
            case "DECIMAL" -> decimal(text, column);
            case "FLOAT", "DOUBLE" -> APPROXIMATE.matcher(text.trim()).matches()
                    ? unsignedOk(text, column) : error("validate.number");
            case "DATE" -> date(text);
            case "DATETIME", "TIMESTAMP" -> dateTime(text);
            case "TIME" -> time(text);
            case "YEAR" -> year(text);
            case "CHAR", "VARCHAR" -> length(text, column);
            case "TINYTEXT" -> bytes(text, 255);
            case "TEXT" -> bytes(text, 65_535);
            case "ENUM" -> enumValue(text, column);
            case "SET" -> setValue(text, column);
            default -> Result.OK;
        };
    }

    private static Result integer(String text, ColumnDef column, int bits) {
        String t = text.trim();
        if (!INTEGER.matcher(t).matches()) {
            return error("validate.integer");
        }
        BigInteger value = new BigInteger(t.startsWith("+") ? t.substring(1) : t);
        BigInteger min = column.unsigned() ? BigInteger.ZERO : BigInteger.TWO.pow(bits - 1).negate();
        BigInteger max = column.unsigned() ? BigInteger.TWO.pow(bits).subtract(BigInteger.ONE)
                : BigInteger.TWO.pow(bits - 1).subtract(BigInteger.ONE);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            return error("validate.range", column.fullType() + (column.unsigned() ? " UNSIGNED" : ""), min, max);
        }
        return Result.OK;
    }

    private static Result decimal(String text, ColumnDef column) {
        String t = text.trim();
        Matcher m = DECIMAL.matcher(t);
        boolean hasDigits = m.matches() && (!m.group(2).isEmpty() || (m.group(3) != null && !m.group(3).isEmpty()));
        if (!hasDigits) {
            return error("validate.number");
        }
        int precision = 10;
        int scale = 0;
        if (column.typeArgs() != null) {
            String[] parts = column.typeArgs().split(",");
            precision = Integer.parseInt(parts[0].trim());
            scale = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        }
        String integerDigits = m.group(2).replaceFirst("^0+", "");
        String fractionDigits = m.group(3) == null ? "" : m.group(3).replaceFirst("0+$", "");
        if (fractionDigits.length() > scale) {
            return error("validate.decimal.scale", column.fullType(), scale);
        }
        if (integerDigits.length() > precision - scale) {
            return error("validate.decimal.precision", column.fullType(), precision - scale);
        }
        return unsignedOk(t, column);
    }

    private static Result unsignedOk(String text, ColumnDef column) {
        boolean negative = column.unsigned() && new BigDecimal(text.trim()).signum() < 0;
        return negative ? error("validate.unsigned") : Result.OK;
    }

    private static Result date(String text) {
        return isRealDate(text.trim()) ? Result.OK : error("validate.date");
    }

    private static boolean isRealDate(String text) {
        try {
            int year = LocalDate.parse(text, STRICT_DATE).getYear();
            return year >= 1000 && year <= 9999;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static Result dateTime(String text) {
        Matcher m = DATETIME.matcher(text.trim());
        boolean ok = m.matches() && isRealDate(m.group(1))
                && (m.group(2) == null || (Integer.parseInt(m.group(2)) < 24 && Integer.parseInt(m.group(3)) < 60
                && (m.group(4) == null || Integer.parseInt(m.group(4)) < 60)));
        return ok ? Result.OK : error("validate.datetime");
    }

    private static Result time(String text) {
        Matcher m = TIME.matcher(text.trim());
        boolean ok = m.matches() && Integer.parseInt(m.group(1)) <= 838 && Integer.parseInt(m.group(2)) < 60
                && (m.group(3) == null || Integer.parseInt(m.group(3)) < 60);
        return ok ? Result.OK : error("validate.time");
    }

    private static Result year(String text) {
        String t = text.trim();
        boolean ok = t.matches("\\d{4}") && (t.equals("0000")
                || (Integer.parseInt(t) >= 1901 && Integer.parseInt(t) <= 2155));
        return ok ? Result.OK : error("validate.year");
    }

    private static Result length(String text, ColumnDef column) {
        if (column.typeArgs() == null) {
            return Result.OK;
        }
        int max = Integer.parseInt(column.typeArgs().trim());
        int actual = text.codePointCount(0, text.length());
        return actual <= max ? Result.OK : error("validate.length", max, actual);
    }

    private static Result bytes(String text, int maxBytes) {
        int actual = text.getBytes(StandardCharsets.UTF_8).length;
        return actual <= maxBytes ? Result.OK : error("validate.bytes", maxBytes, actual);
    }

    private static Result enumValue(String text, ColumnDef column) {
        List<String> allowed = SqlTypes.parseQuotedList(column.typeArgs());
        boolean ok = allowed.stream().anyMatch(v -> v.equalsIgnoreCase(text));
        return ok ? Result.OK : error("validate.enum", String.join(", ", allowed));
    }

    private static Result setValue(String text, ColumnDef column) {
        if (text.isEmpty()) {
            return Result.OK;
        }
        List<String> allowed = SqlTypes.parseQuotedList(column.typeArgs());
        for (String part : text.split(",", -1)) {
            if (allowed.stream().noneMatch(v -> v.equalsIgnoreCase(part))) {
                return error("validate.enum", String.join(", ", allowed));
            }
        }
        return Result.OK;
    }

    private static Result error(String key, Object... args) {
        return new Result(false, CoreMessages.get(key, args));
    }
}
