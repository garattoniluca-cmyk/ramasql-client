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
    /**
     * Facciata valida solo per il thread corrente, per il tempo di un'operazione ({@link QbSql#check} la usa per
     * raccogliere gli avvisi del parser): così non si sostituisce la facciata di tutto il processo, che le altre schede
     * stanno usando (rischio R-06, {@code BUG-007}).
     */
    private static final ThreadLocal<QbHost> OVERRIDE = new ThreadLocal<>();

    private QbRuntime() {
    }

    public static QbHost host() {
        QbHost local = OVERRIDE.get();
        return local != null ? local : host;
    }

    /** La facciata di processo, ignorando l'eventuale sostituzione del thread corrente. */
    public static QbHost processHost() {
        return host;
    }

    /** Esegue {@code action} con {@code local} come facciata del solo thread corrente. */
    public static <T> T withThreadHost(QbHost local, java.util.function.Supplier<T> action) {
        QbHost previous = OVERRIDE.get();
        OVERRIDE.set(Objects.requireNonNull(local, "local"));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
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
