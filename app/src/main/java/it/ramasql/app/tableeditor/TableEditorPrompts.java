/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

/**
 * Le finestre modali dell'editor di tabelle: l'editor non apre mai un dialogo da sé. Nel programma c'è
 * {@link SwingTableEditorPrompts}; nei test una finta che risponde da sola. Gli avvisi non bloccanti (controlli
 * preventivi, spiegazioni sull'engine) non passano da qui: stanno accanto alla riga, nella scheda.
 */
public interface TableEditorPrompts {

    /** Domanda sì/no; {@code confirmLabel} è il testo del pulsante che conferma. */
    boolean confirm(String title, String message, String confirmLabel);

    void showError(String title, String message);

    void showMessage(String title, String message);
}
