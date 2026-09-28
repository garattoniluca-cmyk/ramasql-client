/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.workspace;

import java.nio.file.Path;

/**
 * Le finestre per scegliere un file (importazione, dump, script da eseguire, modello ER, immagine PNG) e le conferme e
 * gli errori delle schede che lavorano con i file. Nel programma le apre {@link SwingFilePrompts}; nei test una finta
 * restituisce i file preparati dal test. Tutto sull'EDT.
 */
public interface FilePrompts {

    /** Che cosa si sceglie: decide titolo, filtro ed estensione della finestra. */
    enum Purpose {
        /** File CSV o JSON da importare. */
        IMPORT_DATA("csv", "json", "txt"),
        /** Script {@code .sql} da eseguire (ripristino di un dump). */
        RUN_SCRIPT("sql"),
        /** File {@code .sql} in cui scrivere il dump. */
        DUMP("sql"),
        /** Modello ER da aprire o salvare. */
        MODEL("rsqlmodel"),
        /** Immagine del diagramma. */
        PNG("png");

        private final String[] extensions;

        Purpose(String... extensions) {
            this.extensions = extensions;
        }

        public String[] extensions() {
            return extensions.clone();
        }

        /** Chiave dei testi: {@code files.<scopo>.title}, {@code files.<scopo>.filter}. */
        public String key() {
            return "files." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Un file esistente da leggere; {@code null} = annullato. */
    Path chooseToOpen(Purpose purpose);

    /**
     * Un file da scrivere (chiede se sovrascrivere uno esistente); {@code null} = annullato.
     *
     * @param suggestedName nome proposto, con l'estensione
     */
    Path chooseToSave(Purpose purpose, String suggestedName);

    /** Domanda sì/no con il pulsante di conferma indicato. */
    boolean confirm(String title, String message, String confirmLabel);

    void showError(String title, String message);
}
