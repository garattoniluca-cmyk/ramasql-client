/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.connection;

import java.awt.BorderLayout;
import java.awt.Window;

import javax.swing.JDialog;
import javax.swing.JLabel;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.core.connection.ConnectionProfile;

/** Finestra del profilo: {@link ProfileForm} con <em>Salva</em> e <em>Annulla</em>. */
public final class ProfileDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final ProfileForm form;
    private final JLabel error = DialogButtons.errorLabel();
    private transient ConnectionProfile result;

    public ProfileDialog(Window owner, ConnectionProfile initial, ConnectionController controller) {
        super(owner, Texts.get(initial == null ? "profile.title.new" : "profile.title.edit"), ModalityType.APPLICATION_MODAL);
        form = new ProfileForm(initial, controller);
        DialogButtons buttons = new DialogButtons(this, Texts.get("profile.save"), error, this::save);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    private void save() {
        String problem = form.validationError();
        if (problem != null) {
            error.setText(problem);
            return;
        }
        result = form.profile();
        dispose();
    }

    /**
     * Chiusura in qualsiasi modo (Salva, Annulla, Esc, X): una prova ancora in corso si abbandona subito, prima che
     * un suo esito già in coda possa mostrare qualcosa.
     */
    @Override
    public void dispose() {
        if (form != null) {
            form.abandonTest();
        }
        super.dispose();
    }

    /** Mostra la finestra e aspetta. {@code null} = annullato. */
    public ConnectionProfile showModal() {
        setVisible(true);
        return result;
    }

    public ProfileForm form() {
        return form;
    }
}
