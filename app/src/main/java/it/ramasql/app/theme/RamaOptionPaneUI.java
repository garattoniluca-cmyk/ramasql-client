/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.theme;

import java.awt.Container;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import com.formdev.flatlaf.ui.FlatOptionPaneUI;

/**
 * I messaggi e le domande del programma (T12.6): le righe vanno a capo entro {@code OptionPane.maxCharactersPerLine}
 * caratteri anche dentro una parola più lunga della riga (un percorso di file, un nome di vincolo), dopo un
 * separatore ({@code \ / _ . - ,}) se c'è. Swing andrebbe a capo solo agli spazi, e con il carattere al massimo la
 * finestra uscirebbe dallo schermo del proiettore. Il testo non cambia: si spezza solo la riga.
 */
public final class RamaOptionPaneUI extends FlatOptionPaneUI {

    public static ComponentUI createUI(JComponent c) {
        return new RamaOptionPaneUI();
    }

    @Override
    protected void burstStringInto(Container c, String d, int maxll) {
        if (d.length() > maxll && d.lastIndexOf(' ', maxll) <= 0) {
            int cut = breakPoint(d, maxll);
            super.burstStringInto(c, d.substring(0, cut), maxll);
            burstStringInto(c, d.substring(cut), maxll);
            return;
        }
        super.burstStringInto(c, d, maxll);
    }

    /** Dove spezzare una parola troppo lunga: dopo l'ultimo separatore della seconda metà della riga, o a fine riga. */
    static int breakPoint(String d, int maxll) {
        for (int i = Math.min(maxll, d.length()) - 1; i >= maxll / 2; i--) {
            if ("\\/_.-,;:".indexOf(d.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return Math.min(maxll, d.length());
    }
}
