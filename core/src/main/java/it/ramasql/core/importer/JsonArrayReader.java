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

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lettore JSON <b>in streaming</b> (Jackson {@link JsonParser}): il file è un <b>elenco di oggetti piatti</b>
 * {@code [ {"titolo": "…", "anno": 1999}, … ]}, letto un oggetto per volta.
 * <ul>
 *   <li>stringhe → {@link String}; numeri interi → {@link Long} (o {@link java.math.BigDecimal} se non ci stanno);
 *       numeri con decimali → {@link java.math.BigDecimal}, senza perdere cifre; {@code true/false} →
 *       {@link Boolean}; {@code null} → {@code null};</li>
 *   <li>un oggetto o un elenco annidato → {@link SourceRow.JsonText} (il suo testo JSON), da importare come testo;</li>
 *   <li>file non valido, radice che non è un elenco, elemento che non è un oggetto: {@link ImportFileException} in
 *       italiano con riga e colonna.</li>
 * </ul>
 */
public final class JsonArrayReader implements AutoCloseable {

    /**
     * Un oggetto dell'elenco.
     *
     * @param line   riga del file in cui comincia (da 1)
     * @param values chiavi e valori, nell'ordine del file
     */
    public record JsonRecord(long line, Map<String, Object> values) {
    }

    private static final JsonFactory FACTORY = JsonFactory.builder().build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonParser parser;
    private boolean started;
    private boolean finished;

    public JsonArrayReader(Reader in) throws IOException {
        this.parser = FACTORY.createParser(Objects.requireNonNull(in, "in"));
    }

    /** L'oggetto successivo; {@code null} a fine elenco. */
    public JsonRecord next() throws IOException, ImportFileException {
        if (finished) {
            return null;
        }
        try {
            if (!started) {
                started = true;
                JsonToken first = parser.nextToken();
                if (first == null) {
                    throw ImportFileException.of(1, 1, "import.json.empty");
                }
                if (first != JsonToken.START_ARRAY) {
                    JsonLocation at = parser.currentTokenLocation();
                    throw ImportFileException.of(at.getLineNr(), at.getColumnNr(), "import.json.notArray",
                            at.getLineNr(), at.getColumnNr());
                }
            }
            JsonToken t = parser.nextToken();
            if (t == JsonToken.END_ARRAY) {
                finished = true;
                JsonToken after = parser.nextToken();
                if (after != null) {
                    JsonLocation at = parser.currentTokenLocation();
                    throw ImportFileException.of(at.getLineNr(), at.getColumnNr(), "import.json.afterEnd",
                            at.getLineNr(), at.getColumnNr());
                }
                return null;
            }
            if (t == null) {
                JsonLocation at = parser.currentLocation();
                throw ImportFileException.of(at.getLineNr(), at.getColumnNr(), "import.json.truncated",
                        at.getLineNr(), at.getColumnNr());
            }
            JsonLocation start = parser.currentTokenLocation();
            if (t != JsonToken.START_OBJECT) {
                throw ImportFileException.of(start.getLineNr(), start.getColumnNr(), "import.json.notObject",
                        start.getLineNr(), start.getColumnNr());
            }
            Map<String, Object> values = new LinkedHashMap<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String key = parser.currentName();
                JsonToken v = parser.nextToken();
                values.put(key, value(v));
            }
            if (parser.currentToken() != JsonToken.END_OBJECT) {
                JsonLocation at = parser.currentLocation();
                throw ImportFileException.of(at.getLineNr(), at.getColumnNr(), "import.json.truncated",
                        at.getLineNr(), at.getColumnNr());
            }
            return new JsonRecord(start.getLineNr(), values);
        } catch (JsonProcessingException e) {
            JsonLocation at = e.getLocation();
            long line = at == null ? 0 : at.getLineNr();
            long column = at == null ? 0 : at.getColumnNr();
            throw new ImportFileException(it.ramasql.core.CoreMessages.get("import.json.invalid", line, column,
                    explain(e.getOriginalMessage())), line, column, e);
        }
    }

    /** Il motivo del lettore JSON in italiano (il testo di Jackson è in inglese). */
    static String explain(String original) {
        String o = original == null ? "" : original;
        java.util.regex.Matcher ch = java.util.regex.Pattern.compile("Unexpected character \\('(.+?)'").matcher(o);
        if (ch.find()) {
            return it.ramasql.core.CoreMessages.get("import.json.why.char", ch.group(1));
        }
        java.util.regex.Matcher tok = java.util.regex.Pattern.compile("Unrecognized token '(.+?)'").matcher(o);
        if (tok.find()) {
            return it.ramasql.core.CoreMessages.get("import.json.why.token", tok.group(1));
        }
        if (o.contains("end-of-input") || o.contains("Unexpected end")) {
            return it.ramasql.core.CoreMessages.get("import.json.why.end");
        }
        if (o.contains("Unexpected close marker")) {
            return it.ramasql.core.CoreMessages.get("import.json.why.close");
        }
        if (o.contains("Illegal unquoted character") || o.contains("control character")) {
            return it.ramasql.core.CoreMessages.get("import.json.why.control");
        }
        return it.ramasql.core.CoreMessages.get("import.json.why.other");
    }

    private Object value(JsonToken t) throws IOException {
        return switch (t) {
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT -> parser.getNumberType() == JsonParser.NumberType.BIG_INTEGER
                    ? new java.math.BigDecimal(parser.getBigIntegerValue()) : (Object) parser.getLongValue();
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            case START_OBJECT, START_ARRAY -> {
                JsonNode node = MAPPER.readTree(parser);
                yield new SourceRow.JsonText(MAPPER.writeValueAsString(node));
            }
            default -> throw new IllegalStateException("token JSON inatteso: " + t);
        };
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }
}
