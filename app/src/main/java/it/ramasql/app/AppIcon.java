/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app;

import java.awt.Image;
import java.util.List;

import it.ramasql.app.theme.AppIcons;

/**
 * Icona dell'applicazione: un cilindro (il database) bianco con le fasce d'accento su un quadrato arrotondato blu,
 * disegnata da noi in SVG ({@code icons/app.svg}) e resa a tutte le dimensioni che Windows usa (16…256 px) per la
 * barra del titolo, la barra delle applicazioni e Alt+Tab.
 */
final class AppIcon {

    private AppIcon() {
    }

    static List<Image> images() {
        return AppIcons.windowImages();
    }
}
