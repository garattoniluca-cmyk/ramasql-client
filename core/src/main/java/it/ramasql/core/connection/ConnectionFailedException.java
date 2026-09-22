/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

/** Connessione non riuscita: porta con sé la diagnosi ({@link ConnectionFailure}). */
public final class ConnectionFailedException extends Exception {

    private static final long serialVersionUID = 1L;

    private final transient ConnectionFailure failure;

    public ConnectionFailedException(ConnectionFailure failure, Throwable cause) {
        super(failure.message() + " - " + failure.originalDetail(), cause);
        this.failure = failure;
    }

    public ConnectionFailure failure() {
        return failure;
    }
}
