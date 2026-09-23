/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.nio.file.Path;

/**
 * Le finestre modali della griglia (come {@link it.ramasql.app.Prompts} per il resto del programma): la griglia non
 * apre mai un dialogo per conto suo. Nel programma c'è {@link SwingGridPrompts}; nei test una finta che risponde da sola.
 * Gli avvisi non bloccanti (eccedenza scartata, colonne saltate…) non passano da qui: compaiono nella riga degli avvisi
 * della griglia ({@link DataGrid#notice()}).
 */
public interface GridPrompts {

    /** Domanda sì/no; {@code confirmLabel} è il testo del pulsante che conferma (es. «Scarta»). */
    boolean confirm(String title, String message, String confirmLabel);

    /** Editor a finestra per un testo lungo; {@code null} = annullato. */
    String editLongText(String title, String initialText);

    /** File CSV da scrivere, con un nome proposto; {@code null} = annullato. */
    Path chooseCsvFile(String suggestedName);

    void showError(String title, String message);
}
