/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

/** Il parser ereditato da SQLeo non è riuscito a leggere una query (vedi {@link QbSql#parse(String)}). */
public class QbParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QbParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
