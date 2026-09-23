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

import java.awt.Component;
import java.util.function.Supplier;

import javax.swing.JOptionPane;

import it.ramasql.app.Texts;

/** Le finestre modali vere dell'editor di tabelle. */
public final class SwingTableEditorPrompts implements TableEditorPrompts {

    private final Supplier<Component> owner;

    /** @param owner componente sopra cui aprire i dialoghi */
    public SwingTableEditorPrompts(Supplier<Component> owner) {
        this.owner = owner;
    }

    @Override
    public boolean confirm(String title, String message, String confirmLabel) {
        Object[] options = {confirmLabel, Texts.get("dialog.cancel")};
        int choice = JOptionPane.showOptionDialog(owner.get(), message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    @Override
    public void showError(String title, String message) {
        JOptionPane.showMessageDialog(owner.get(), message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showMessage(String title, String message) {
        JOptionPane.showMessageDialog(owner.get(), message, title, JOptionPane.INFORMATION_MESSAGE);
    }
}
