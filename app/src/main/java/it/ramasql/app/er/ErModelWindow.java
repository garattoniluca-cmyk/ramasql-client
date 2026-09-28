/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JFrame;

import it.ramasql.model.ErModel;

/**
 * La finestra di un modello ER. Un modello è un documento a sé ({@code .rsqlmodel}): vive in una finestra propria,
 * che resta aperta anche se ci si disconnette e si apre anche senza connessione (T11.7). Chiudendola con modifiche non
 * salvate chiede se salvarle.
 */
public final class ErModelWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private final ErModelPanel panel;

    public ErModelWindow(ErModel model, Path file, ErContext context, List<Image> icons) {
        panel = new ErModelPanel(model, file, context);
        setName("er.window");
        setIconImages(icons);
        setContentPane(panel);
        setTitle(panel.title());
        panel.setOnTitleChange(() -> setTitle(panel.title()));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfAllowed();
            }
        });
        setPreferredSize(new Dimension(1100, 720));
        setMinimumSize(new Dimension(640, 420));
        pack();
    }

    public ErModelPanel panel() {
        return panel;
    }

    /** Chiude la finestra, dopo aver chiesto delle modifiche non salvate; {@code false} se è rimasta aperta. */
    public boolean closeIfAllowed() {
        if (panel.canClose()) {
            dispose();
            return true;
        }
        return false;
    }
}
