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

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Il tema di RamaSQL ({@code docs/DESIGN-SYSTEM.md}): un {@link FlatLightLaf} con i token del sistema visivo. Le chiavi
 * stanno in {@code RamaSqlLaf.properties}, accanto a questa classe, che FlatLaf carica da solo per nome di classe
 * dopo {@code FlatLaf.properties} e {@code FlatLightLaf.properties}. Un solo tema chiaro, calibrato per il proiettore.
 */
public class RamaSqlLaf extends FlatLightLaf {

    public static final String NAME = "RamaSQL";

    /** Installa il tema (al posto di {@code FlatLightLaf.setup()}): va chiamato prima di creare le finestre. */
    public static boolean setup() {
        boolean ok = FlatLaf.setup(new RamaSqlLaf());
        installTips();
        Screens.install();
        KeyTips.install();
        freeF6();
        return ok;
    }

    /**
     * F6 e Maiusc+F6 passano da un'area all'altra della finestra (T12.4): i divisori ({@code JSplitPane}) non li usano
     * più per sé (in Swing F6 salta fra i due lati di un divisore, e ogni divisore lo intercetterebbe).
     */
    static void freeF6() {
        if (javax.swing.UIManager.get("SplitPane.ancestorInputMap") instanceof javax.swing.InputMap im) {
            im.remove(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, 0));
            im.remove(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6,
                    java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        }
    }

    /** Pausa prima di comparire (ms): {@code DESIGN-SYSTEM.md} §3.9. */
    public static final int TIP_INITIAL_DELAY = 500;
    /** Quanto resta a schermo (ms): il tempo di leggerlo ad alta voce in classe, almeno 20 s. */
    public static final int TIP_DISMISS_DELAY = 60_000;

    /** I tempi dei suggerimenti, uguali in tutto il programma. */
    public static void installTips() {
        javax.swing.ToolTipManager tips = javax.swing.ToolTipManager.sharedInstance();
        tips.setInitialDelay(TIP_INITIAL_DELAY);
        tips.setDismissDelay(TIP_DISMISS_DELAY);
        tips.setReshowDelay(200);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "RamaSQL Client - tema chiaro del sistema visivo";
    }
}
