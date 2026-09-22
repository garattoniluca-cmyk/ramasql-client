/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

/** Opzioni che in SQLeo stavano in {@code Preferences}. */
public enum QbOption {
    /** Join disegnati ad arco (true) o a linee spezzate (false). In SQLeo: {@code QB_RELATION_RENDER_ARC_KEY}. */
    RELATION_ARCS(true);

    private final boolean defaultValue;

    QbOption(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean defaultValue() {
        return defaultValue;
    }
}
