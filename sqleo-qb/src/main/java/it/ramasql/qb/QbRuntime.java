/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.awt.Dimension;
import java.util.Objects;

/**
 * Punto d'accesso statico alla facciata per le parti del codice ereditato che sono statiche
 * (I18n, SQLParser, renderer con icone in cache, dimensioni scalate). È stato {@code static}
 * residuo (rischio R-06): l'app imposta un solo host per processo, valido per tutte le schede;
 * la connessione invece è per istanza (vedi {@code QueryBuilder(QbHost)}).
 */
public final class QbRuntime {

    private static volatile QbHost host = new BasicQbHost();

    private QbRuntime() {
    }

    public static QbHost host() {
        return host;
    }

    public static void setHost(QbHost newHost) {
        host = Objects.requireNonNull(newHost, "host");
    }

    /** In SQLeo: {@code Preferences.getScaledRowHeight}. */
    public static int scale(int px) {
        return host.scale(px);
    }

    /** In SQLeo: {@code Preferences.getScaledDimension}. */
    public static Dimension scaledDimension(int width, int height) {
        return new Dimension(host.scale(width), host.scale(height));
    }
}
