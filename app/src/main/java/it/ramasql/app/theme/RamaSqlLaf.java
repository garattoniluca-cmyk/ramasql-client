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
        return FlatLaf.setup(new RamaSqlLaf());
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
