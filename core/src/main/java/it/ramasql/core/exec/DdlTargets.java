/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Che cosa cambia nei metadati un'istruzione eseguita: serve a {@link SqlExecutor} per invalidare la cache dopo un
 * DDL. Prudente: nel dubbio invalida di più (un'invalidazione in più costa una rilettura, una in meno mostra dati
 * vecchi).
 *
 * @param ddl            l'istruzione cambia la struttura ({@code CREATE}, {@code ALTER}, {@code DROP},
 *                       {@code RENAME}, {@code TRUNCATE})
 * @param catalogList    cambia l'elenco dei cataloghi ({@code CREATE/DROP DATABASE})
 * @param catalogs       cataloghi nominati esplicitamente (nomi qualificati o {@code … DATABASE x})
 * @param currentCatalog può toccare oggetti non qualificati, cioè del catalogo corrente
 */
record DdlTargets(boolean ddl, boolean catalogList, Set<String> catalogs, boolean currentCatalog) {

    private static final Set<String> DDL_VERBS = Set.of("CREATE", "ALTER", "DROP", "RENAME", "TRUNCATE");
    /** {@code ALTER DATABASE CHARACTER SET …} senza nome: vale per il catalogo corrente. */
    private static final Set<String> DATABASE_OPTIONS = Set.of("CHARACTER", "CHARSET", "DEFAULT", "COLLATE",
            "COMMENT", "READ", "ENCRYPTION", "UPGRADE");
    private static final DdlTargets NONE = new DdlTargets(false, false, Set.of(), false);

    static DdlTargets of(String sql) {
        // SET STATEMENT … FOR DROP TABLE t: conta l'istruzione avvolta
        List<SqlLexer.Token> tokens = SqlLexer.tokenize(SqlLexer.innermost(sql));
        int first = SqlLexer.firstMeaningful(tokens);
        if (first < 0 || !DDL_VERBS.contains(tokens.get(first).upper())) {
            return NONE;
        }
        // CREATE [OR REPLACE] DATABASE|SCHEMA [IF NOT EXISTS] x · DROP DATABASE [IF EXISTS] x · ALTER DATABASE [x]
        int i = first + 1;
        if (i + 1 < tokens.size() && tokens.get(i).isWord("OR") && tokens.get(i + 1).isWord("REPLACE")) {
            i += 2;
        }
        if (i < tokens.size() && (tokens.get(i).isWord("DATABASE") || tokens.get(i).isWord("SCHEMA"))) {
            int nameAt = SqlLexer.skipIfExists(tokens, i + 1);
            String[] name = SqlLexer.qualifiedName(tokens, nameAt);
            boolean option = nameAt < tokens.size() && tokens.get(nameAt).type() == SqlLexer.Type.WORD
                    && DATABASE_OPTIONS.contains(tokens.get(nameAt).upper());
            Set<String> cats = new LinkedHashSet<>();
            if (name != null && name[0] == null && !option) {
                cats.add(name[1]);
            }
            return new DdlTargets(true, true, Set.copyOf(cats), cats.isEmpty());
        }
        Set<String> cats = new LinkedHashSet<>();
        for (int k = first + 1; k < tokens.size(); k++) {
            String[] name = SqlLexer.qualifiedName(tokens, k);
            if (name != null && name[0] != null) {
                cats.add(name[0]);
                k += 2;
            }
        }
        return new DdlTargets(true, false, Set.copyOf(cats), true);
    }
}
