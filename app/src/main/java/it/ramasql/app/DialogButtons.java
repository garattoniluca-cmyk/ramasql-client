/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 * Fondo comune delle finestre di dialogo: a sinistra l'eventuale errore, a destra <em>Annulla</em> e il pulsante
 * che conferma (predefinito: Invio). Esc chiude come Annulla.
 */
public final class DialogButtons extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JButton confirm;
    private final JButton cancel;

    /**
     * @param cancelLabel {@code null} = finestra con il solo pulsante di conferma (avvisi)
     */
    public DialogButtons(JDialog dialog, String confirmLabel, String cancelLabel, JLabel message, Runnable onConfirm) {
        super(new BorderLayout(16, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 28, 20, 28));
        confirm = new JButton(confirmLabel);
        confirm.setName("dialog.confirm");
        confirm.addActionListener(e -> onConfirm.run());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (cancelLabel != null) {
            cancel = new JButton(cancelLabel);
            cancel.setName("dialog.cancel");
            cancel.addActionListener(e -> dialog.dispose());
            right.add(cancel);
        } else {
            cancel = null;
        }
        right.add(confirm);
        if (message != null) {
            message.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            add(message, BorderLayout.NORTH); // su una riga sua: un testo lungo non viene tagliato dai pulsanti
        }
        add(right, BorderLayout.EAST);

        dialog.getRootPane().setDefaultButton(confirm);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    public DialogButtons(JDialog dialog, String confirmLabel, JLabel message, Runnable onConfirm) {
        this(dialog, confirmLabel, Texts.get("dialog.cancel"), message, onConfirm);
    }

    /** Etichetta per gli errori di compilazione, nel colore degli errori. */
    public static JLabel errorLabel() {
        JLabel label = new JLabel(" ");
        label.setName("dialog.error");
        label.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));
        return label;
    }

    public JButton confirmButton() {
        return confirm;
    }

    public JButton cancelButton() {
        return cancel;
    }
}
