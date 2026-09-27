/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.awt.Color;

/**
 * I colori del diagramma, chiesti alla facciata ({@link QbHost#color(QbColor)}) invece che scritti nel codice
 * ereditato (in SQLeo: rosso, giallo e verde puri, {@code BUG-004}). I valori predefiniti sono i token di
 * {@code docs/DESIGN-SYSTEM.md} §1.1; il programma li prende dalla sua unica tavolozza.
 */
public enum QbColor {
    /** Fondo del diagramma ({@code bg.surface}). */
    CANVAS(0xFFFFFF),
    /** Fondo di un campo ({@code bg.surface}). */
    FIELD(0xFFFFFF),
    /** Campo che partecipa a un join ({@code accent.tint}). */
    FIELD_JOINED(0xEAF1FF),
    /** Primo campo scelto mentre si disegna un join a mano ({@code warning.tint}). */
    FIELD_JOIN_START(0xFFF4DB),
    /** Testo di una colonna o tabella che sul server non c'è ({@code danger}). */
    MISSING(0xDC2626),
    /** Linea di un join ({@code text.tertiary}). */
    LINE(0x98A2B3),
    /** Linea di un join evidenziata ({@code accent}). */
    LINE_HIGHLIGHT(0x2563EB),
    /** Nodo di un join interno ({@code accent}). */
    JOIN_INNER(0x2563EB),
    /** Nodo di un join esterno LEFT o RIGHT ({@code warning}). */
    JOIN_OUTER(0xD97706),
    /** Estremo di un join esterno: il lato di cui si tengono tutte le righe ({@code warning}). */
    JOIN_ALL_ROWS(0xD97706),
    /** Testo secondario: alias, tipo della colonna ({@code text.secondary}). */
    TEXT_SECONDARY(0x475467),
    /** Bordo di un'entità ({@code border.default}). */
    BORDER(0xD0D5DD),
    /** Fondo dell'intestazione di un'entità ({@code bg.sunken}). */
    HEADER(0xEEF1F5);

    private final Color defaultColor;

    QbColor(int rgb) {
        this.defaultColor = new Color(rgb);
    }

    public Color defaultColor() {
        return defaultColor;
    }
}
