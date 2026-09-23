/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import java.util.Locale;
import java.util.Optional;

import it.ramasql.core.CoreMessages;

/**
 * Azione referenziale di una chiave esterna ({@code ON DELETE} / {@code ON UPDATE}) che il client sa generare.
 *
 * <p><b>{@code SET DEFAULT}</b> non è fra le costanti, per scelta: InnoDB la rifiuta su entrambi i server (MySQL e MariaDB
 * la riconoscono nella sintassi, ma il motore non crea la chiave), quindi il generatore non deve mai proporla né
 * scriverla. Se però il server la riporta ({@code information_schema}, altri motori), la lettura della tabella <b>non
 * fallisce</b>: {@link #parse(String)} restituisce vuoto e il lettore dei metadati conserva quella chiave esterna tra
 * gli elementi avanzati, in sola lettura, com'è scritta nel server (mapparla su un'altra azione nasconderebbe una
 * differenza reale).
 */
public enum FkAction {
    RESTRICT("RESTRICT"),
    CASCADE("CASCADE"),
    SET_NULL("SET NULL"),
    NO_ACTION("NO ACTION");

    private final String sql;

    FkAction(String sql) {
        this.sql = sql;
    }

    /** Testo SQL dell'azione, es. {@code SET NULL}. */
    public String sql() {
        return sql;
    }

    /** Dal testo di {@code information_schema.REFERENTIAL_CONSTRAINTS}; vuoto o nullo vale RESTRICT (default del server). */
    public static FkAction fromSql(String text) {
        return parse(text).orElseThrow(
                () -> new IllegalArgumentException(CoreMessages.get("metadata.fkAction.unsupported", text)));
    }

    /**
     * Come {@link #fromSql}, ma senza eccezioni: vuoto per un'azione che il client non genera ({@code SET DEFAULT} o
     * sconosciuta).
     */
    public static Optional<FkAction> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.of(RESTRICT);
        }
        String wanted = text.trim().replace('_', ' ').replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        for (FkAction a : values()) {
            if (a.sql.equals(wanted)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}
