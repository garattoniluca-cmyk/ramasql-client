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

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import it.ramasql.app.DialogButtons;
import it.ramasql.app.Texts;
import it.ramasql.app.theme.AppIcons;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.connection.ConnectionProfile;

/** Finestra del profilo: {@link ProfileForm} con <em>Salva</em> e <em>Annulla</em>. */
public final class ProfileDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final ProfileForm form;
    private final JLabel error = DialogButtons.errorLabel();
    private transient ConnectionProfile result;

    public ProfileDialog(Window owner, ConnectionProfile initial, ConnectionController controller) {
        super(owner, Texts.get(initial == null ? "profile.title.new" : "profile.title.edit"), ModalityType.APPLICATION_MODAL);
        setName("profile.dialog");
        form = new ProfileForm(initial, controller);
        DialogButtons buttons = new DialogButtons(this, Texts.get("profile.save"), error, this::save);
        buttons.confirmButton().setToolTipText(Texts.get("profile.save.tooltip"));
        add(header(initial == null ? "profile.title.new" : "profile.title.edit"), BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        buttons.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_8), Tokens.px(Tokens.SPACE_24),
                Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24)));
        add(buttons, BorderLayout.SOUTH);
        setResizable(false);
        it.ramasql.app.theme.Tips.fromNames(getRootPane());   // suggerimenti <nome>.tooltip (ADR-020)
        pack();
        setLocationRelativeTo(owner);
        it.ramasql.app.theme.Screens.fit(this);   // dentro lo schermo anche a 1024x768 (T12.6)
    }

    /** Titolo {@code heading} con l'icona della connessione e una riga che dice a cosa serve la finestra. */
    private static JPanel header(String titleKey) {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_24), Tokens.px(Tokens.SPACE_24), 0,
                Tokens.px(Tokens.SPACE_24)));
        JLabel title = Styles.text(new JLabel(Texts.get(titleKey), AppIcons.get(AppIcons.MENU_NEW_CONNECTION, 24),
                JLabel.LEADING), "heading", Tokens.TEXT_PRIMARY);
        title.setIconTextGap(Tokens.px(Tokens.SPACE_12));
        JLabel subtitle = new JLabel(Texts.get("profile.subtitle"));
        subtitle.setForeground(Tokens.TEXT_SECONDARY);
        subtitle.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_4), 0, 0, 0));
        header.add(title);
        header.add(subtitle);
        return header;
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
