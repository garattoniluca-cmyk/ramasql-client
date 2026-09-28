/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import static it.ramasql.app.servertest.Probe.onEdt;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import javax.swing.SwingUtilities;

/** Disegna una scheda dell'area di lavoro dopo averne sistemato tutto il contenuto (anche una scheda appena aggiunta). */
final class PaintSupport {

    private PaintSupport() {
    }

    static void paint(Component c, String step, String fileName) {
        onEdt(() -> {
            Window window = SwingUtilities.getWindowAncestor(c);
            if (window != null) {
                window.validate();
            }
            if (c instanceof Container k) {
                k.validate();
                layoutAll(k);
            }
        });
        Probe.paint(step, c, fileName);
    }

    private static void layoutAll(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container k) {
                layoutAll(k);
            }
        }
    }
}
