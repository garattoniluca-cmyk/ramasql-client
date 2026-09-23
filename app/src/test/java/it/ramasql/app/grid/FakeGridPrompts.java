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
import java.util.ArrayList;
import java.util.List;

/** Finta di {@link GridPrompts}: risponde da sola e registra ciò che sarebbe stato mostrato. */
final class FakeGridPrompts implements GridPrompts {

    final List<String> shown = new ArrayList<>();
    boolean confirmAnswer = true;
    String longTextAnswer;
    Path csvFile;

    @Override
    public boolean confirm(String title, String message, String confirmLabel) {
        shown.add("confirm: " + title + " | " + message + " | " + confirmLabel);
        return confirmAnswer;
    }

    @Override
    public String editLongText(String title, String initialText) {
        shown.add("longText: " + title + " | " + initialText);
        return longTextAnswer;
    }

    @Override
    public Path chooseCsvFile(String suggestedName) {
        shown.add("csv: " + suggestedName);
        return csvFile;
    }

    @Override
    public void showError(String title, String message) {
        shown.add("error: " + title + " | " + message);
    }
}
