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

import static it.ramasql.app.theme.ThemeTestSupport.contrast;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * (d) Contrasto dei token principali, calcolato con la formula WCAG 2.x ({@code DESIGN-SYSTEM.md} §5): testo
 * ≥ 4,5:1 sulle superfici su cui compare, bordi che delimitano un controllo e icone ≥ 3:1. Il rapporto completo (anche
 * delle coppie decorative, che non hanno soglia) va in {@code test-results/step2/contrasto-token.txt}.
 */
@Tag("step2")
@Tag("ui")
class ContrastTest {

    private record Pair(String what, Color fg, Color bg, double min) {
    }

    private static final double TEXT = 4.5;
    private static final double UI = 3.0;
    /** Nessuna soglia: coppia decorativa o ridondante (c'è sempre anche un altro segno), solo riportata. */
    private static final double INFO = 0;

    @Test
    void iTokenPrincipaliHannoIlContrastoRichiesto() {
        List<Pair> pairs = new ArrayList<>();
        Color[] surfaces = {Tokens.BG_SURFACE, Tokens.BG_WINDOW, Tokens.BG_SUNKEN, Tokens.BG_ZEBRA};
        String[] names = {"bg.surface", "bg.window", "bg.sunken", "zebra"};
        for (int i = 0; i < surfaces.length; i++) {
            pairs.add(new Pair("text.primary su " + names[i], Tokens.TEXT_PRIMARY, surfaces[i], TEXT));
            pairs.add(new Pair("text.secondary su " + names[i], Tokens.TEXT_SECONDARY, surfaces[i], TEXT));
            pairs.add(new Pair("icone (tratto text.secondary) su " + names[i], Tokens.TEXT_SECONDARY, surfaces[i], UI));
        }
        // stati: il testo resta text.primary sulle tinte; i colori pieni valgono per icone, barre e glifi
        for (Color tint : new Color[] {Tokens.ACCENT_TINT, Tokens.SUCCESS_TINT, Tokens.WARNING_TINT, Tokens.DANGER_TINT}) {
            pairs.add(new Pair("text.primary su tinta " + Tokens.hex(tint), Tokens.TEXT_PRIMARY, tint, TEXT));
            pairs.add(new Pair("text.secondary su tinta " + Tokens.hex(tint), Tokens.TEXT_SECONDARY, tint, TEXT));
        }
        pairs.add(new Pair("accent (collegamenti, testo) su bg.surface", Tokens.ACCENT, Tokens.BG_SURFACE, TEXT));
        pairs.add(new Pair("accent pressed su accent.tint (toggle attivo)", Tokens.ACCENT_PRESSED, Tokens.ACCENT_TINT, TEXT));
        pairs.add(new Pair("danger (testo d'errore, righe da eliminare) su bg.surface", Tokens.DANGER, Tokens.BG_SURFACE, TEXT));
        pairs.add(new Pair("danger forte su danger.tint (testo barrato, «−»)", Tokens.strong(Tokens.DANGER),
                Tokens.DANGER_TINT, TEXT));
        pairs.add(new Pair("bianco su accent (pulsante primario)", Tokens.ON_ACCENT, Tokens.ACCENT, TEXT));
        pairs.add(new Pair("bianco su accent.hover", Tokens.ON_ACCENT, Tokens.ACCENT_HOVER, TEXT));
        pairs.add(new Pair("bianco su danger (pulsante distruttivo)", Tokens.ON_ACCENT, Tokens.DANGER, TEXT));
        pairs.add(new Pair("bianco su text.primary (suggerimenti)", Tokens.ON_ACCENT, Tokens.TEXT_PRIMARY, TEXT));
        pairs.add(new Pair("anello di focus accent su bg.surface", Tokens.ACCENT, Tokens.BG_SURFACE, UI));
        pairs.add(new Pair("anello di focus accent su bg.window", Tokens.ACCENT, Tokens.BG_WINDOW, UI));
        pairs.add(new Pair("icona successo su bg.surface", Tokens.SUCCESS, Tokens.BG_SURFACE, UI));
        pairs.add(new Pair("icona avviso su bg.surface", Tokens.WARNING, Tokens.BG_SURFACE, UI));
        pairs.add(new Pair("icona errore su bg.surface", Tokens.DANGER, Tokens.BG_SURFACE, UI));
        pairs.add(new Pair("tabella MyISAM (viola) su bg.window", Tokens.ENGINE_MYISAM, Tokens.BG_WINDOW, UI));
        pairs.add(new Pair("punto MariaDB su bg.surface", Tokens.SERVER_MARIADB, Tokens.BG_SURFACE, UI));
        pairs.add(new Pair("punto MySQL su bg.surface", Tokens.SERVER_MYSQL, Tokens.BG_SURFACE, UI));
        pairs.add(new Pair("barra e «+» riga nuova (success forte) su success.tint", Tokens.strong(Tokens.SUCCESS),
                Tokens.SUCCESS_TINT, UI));
        pairs.add(new Pair("barra riga modificata (warning forte) su warning.tint", Tokens.strong(Tokens.WARNING),
                Tokens.WARNING_TINT, UI));
        pairs.add(new Pair("triangolino cella modificata (warning forte) su warning.tint", Tokens.strong(Tokens.WARNING),
                Tokens.WARNING_TINT, UI));
        pairs.add(new Pair("bordo cella non valida (danger) su danger.tint", Tokens.DANGER, Tokens.DANGER_TINT, UI));
        pairs.add(new Pair("bordo del blocco selezionato (accent) su accent.tint", Tokens.ACCENT, Tokens.ACCENT_TINT, UI));
        // riportate senza soglia (decorative o con un secondo segno): vedi rapporto
        pairs.add(new Pair("danger puro su danger.tint (per questo si usa la versione forte)", Tokens.DANGER,
                Tokens.DANGER_TINT, INFO));
        pairs.add(new Pair("success puro su success.tint (idem)", Tokens.SUCCESS, Tokens.SUCCESS_TINT, INFO));
        pairs.add(new Pair("warning puro su warning.tint (idem)", Tokens.WARNING, Tokens.WARNING_TINT, INFO));
        pairs.add(new Pair("text.tertiary su bg.surface (segnaposti, numeri di riga)", Tokens.TEXT_TERTIARY, Tokens.BG_SURFACE, INFO));
        pairs.add(new Pair("text.tertiary su bg.window", Tokens.TEXT_TERTIARY, Tokens.BG_WINDOW, INFO));
        pairs.add(new Pair("border.default su bg.surface (bordo dei campi)", Tokens.BORDER_DEFAULT, Tokens.BG_SURFACE, INFO));
        pairs.add(new Pair("border.subtle su bg.surface (divisori)", Tokens.BORDER_SUBTLE, Tokens.BG_SURFACE, INFO));
        pairs.add(new Pair("chiave oro su bg.window (icona PK, con forma di chiave)", Tokens.KEY_GOLD, Tokens.BG_WINDOW, INFO));
        pairs.add(new Pair("bianco su MySQL arancio (NON usato: la pillola ha testo text.primary)", Tokens.ON_ACCENT,
                Tokens.SERVER_MYSQL, INFO));

        StringBuilder report = new StringBuilder("Contrasto dei token (WCAG 2.x) — DESIGN-SYSTEM §5\n");
        report.append("soglie: testo 4,5:1 · bordi di controlli e icone 3:1 · «—» = solo riportato\n\n");
        List<String> failures = new ArrayList<>();
        for (Pair p : pairs) {
            double ratio = contrast(p.fg(), p.bg());
            boolean ok = ratio >= p.min();
            report.append(String.format(Locale.ITALIAN, "%-70s %s su %s  %5.2f:1  %s%n", p.what(), Tokens.hex(p.fg()),
                    Tokens.hex(p.bg()), ratio, p.min() == INFO ? "—" : ok ? "OK (≥ " + p.min() + ")" : "NO (< " + p.min() + ")"));
            if (!ok) {
                failures.add(p.what() + ": " + String.format(Locale.ITALIAN, "%.2f", ratio));
            }
        }
        ThemeTestSupport.writeText("contrasto-token.txt", report.toString());
        assertTrue(failures.isEmpty(), "contrasto insufficiente: " + failures);
    }
}
