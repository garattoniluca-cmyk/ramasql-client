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

import java.awt.Color;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;

/** Stili FlatLaf costruiti dai {@link Tokens} (nessun colore scritto a mano nei componenti). */
public final class Styles {

    private Styles() {
    }

    /**
     * Pulsante primario pieno (DESIGN-SYSTEM §3.7): {@code accent} per l'azione normale, {@code danger} per quella
     * distruttiva; testo bianco, raggio dei controlli. Disabilitato resta grigio (lo fa FlatLaf).
     */
    public static void primary(JButton button, Color color) {
        boolean danger = color.equals(Tokens.DANGER);
        String hover = Tokens.hex(danger ? color.darker() : Tokens.ACCENT_HOVER);
        String pressed = Tokens.hex(danger ? color.darker().darker() : Tokens.ACCENT_PRESSED);
        // il pulsante predefinito (Invio) usa le chiavi «default…»: stessi colori
        button.putClientProperty("FlatLaf.style", "background: " + Tokens.hex(color)
                + "; default.background: " + Tokens.hex(color)
                + "; default.foreground: " + Tokens.hex(Tokens.BG_SURFACE)
                + "; default.hoverBackground: " + hover
                + "; default.pressedBackground: " + pressed
                + "; default.borderColor: " + Tokens.hex(color)
                + "; default.focusedBorderColor: " + Tokens.hex(color)
                + "; foreground: " + Tokens.hex(Tokens.BG_SURFACE)
                + "; hoverBackground: " + hover
                + "; pressedBackground: " + pressed
                + "; borderColor: " + Tokens.hex(color)
                + "; focusedBorderColor: " + Tokens.hex(color)
                + "; disabledBackground: " + Tokens.hex(Tokens.BG_SUNKEN)
                + "; disabledText: " + Tokens.hex(Tokens.TEXT_TERTIARY)
                + "; arc: " + Tokens.RADIUS_CONTROL * 2
                + "; font: bold");
    }

    /**
     * Pulsante di contorno nel colore dato (es. <em>Interrompi</em> in {@code danger}, §3.1): testo e bordo colorati,
     * fondo bianco che al passaggio prende la tinta. Disabilitato torna grigio (lo fa FlatLaf).
     */
    public static void outline(JButton button, Color color, Color tint) {
        button.putClientProperty("FlatLaf.style", "foreground: " + Tokens.hex(color)
                + "; borderColor: " + Tokens.hex(color)
                + "; hoverBorderColor: " + Tokens.hex(color)
                + "; focusedBorderColor: " + Tokens.hex(color)
                + "; hoverBackground: " + Tokens.hex(tint)
                + "; pressedBackground: " + Tokens.hex(tint)
                + "; disabledText: " + Tokens.hex(Tokens.TEXT_TERTIARY)
                + "; arc: " + Tokens.RADIUS_CONTROL * 2
                + "; font: $rama.emphasis.font");
    }

    /** Pulsante «da barra» (senza bordo, fondo solo al passaggio) anche fuori da una {@code JToolBar}. */
    public static void toolbarButton(AbstractButton button) {
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
    }

    /**
     * Stile tipografico della scala (§1.2) per un componente FlatLaf: {@code display}, {@code heading}, {@code title},
     * {@code emphasis}, {@code smallText}, {@code caption}. Segue da solo la dimensione del carattere delle impostazioni.
     */
    public static <T extends JComponent> T text(T component, String styleClass) {
        component.putClientProperty("FlatLaf.styleClass", styleClass);
        return component;
    }

    /** Come {@link #text(JComponent, String)}, con in più il colore del testo. */
    public static <T extends JComponent> T text(T component, String styleClass, Color foreground) {
        text(component, styleClass);
        component.setForeground(foreground);
        return component;
    }
}
