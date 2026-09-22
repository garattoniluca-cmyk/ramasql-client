/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import it.ramasql.core.CoreMessages;

/**
 * Trasforma l'eccezione di una connessione non riuscita in una {@link ConnectionFailure}: una causa distinta,
 * un messaggio italiano che dice cosa correggere e il testo originale, che non si perde mai.
 * <p>Ordine di giudizio: prima i <em>codici del server</em> (se il server ha risposto, la rete funziona), poi le
 * <em>eccezioni di rete</em> nella catena delle cause, infine qualche indizio nel testo; il resto è {@code OTHER}.
 */
public final class ConnectionErrorClassifier {

    private ConnectionErrorClassifier() {
    }

    public static ConnectionFailure classify(Throwable error, ConnectionProfile profile) {
        List<Throwable> chain = chain(error);
        SQLException sql = serverError(chain);
        ConnectionErrorCause cause = sql != null ? byServerCode(sql.getErrorCode()) : null;
        if (cause == null) {
            cause = byNetworkException(chain);
            if (cause != null) {
                sql = null; // il codice del server non è tra quelli riconosciuti e la causa vera è di rete: si mostra quella
            }
        }
        if (cause == null) {
            cause = byText(chain, profile);
        }
        if (cause == null) {
            cause = ConnectionErrorCause.OTHER;
        }
        return new ConnectionFailure(cause, message(cause, profile),
                sql != null ? sql.getErrorCode() : 0,
                sql != null && sql.getSQLState() != null ? sql.getSQLState() : sqlState(chain),
                sql != null ? text(sql) : networkText(chain));
    }

    /** Diagnosi del tempo massimo scaduto senza alcuna eccezione del driver (tentativo abbandonato dal client). */
    public static ConnectionFailure timeout(ConnectionProfile profile) {
        return new ConnectionFailure(ConnectionErrorCause.TIMEOUT, message(ConnectionErrorCause.TIMEOUT, profile), 0, "",
                CoreMessages.get("connection.error.timeout.original", ConnectionAttempt.DEADLINE_SECONDS));
    }

    static String message(ConnectionErrorCause cause, ConnectionProfile p) {
        String key = "connection.error." + cause.name();
        return switch (cause) {
            case UNKNOWN_HOST -> CoreMessages.get(key, p.host());
            case PORT_CLOSED -> CoreMessages.get(key, p.host(), p.port());
            // senza numero di secondi: il tempo scaduto può essere del client, del driver o del sistema operativo
            case TIMEOUT -> CoreMessages.get(key, p.host(), p.port());
            case ACCESS_DENIED, HOST_NOT_ALLOWED, AUTH_PLUGIN -> CoreMessages.get(key, p.user());
            case CATALOG_ACCESS_DENIED -> CoreMessages.get(key, p.user(), p.defaultCatalog());
            case UNKNOWN_CATALOG -> CoreMessages.get(key, p.defaultCatalog());
            case TOO_MANY_CONNECTIONS, SSL, OTHER -> CoreMessages.get(key);
        };
    }

    private static ConnectionErrorCause byServerCode(int code) {
        return switch (code) {
            case 1045, 1698 -> ConnectionErrorCause.ACCESS_DENIED;
            case 1044 -> ConnectionErrorCause.CATALOG_ACCESS_DENIED;
            case 1049 -> ConnectionErrorCause.UNKNOWN_CATALOG;
            case 1040, 1203, 1226 -> ConnectionErrorCause.TOO_MANY_CONNECTIONS;
            case 1129, 1130 -> ConnectionErrorCause.HOST_NOT_ALLOWED;
            // 1043 («Bad handshake») non è sempre SSL: lo decide il testo (byText)
            case 3159, 2026 -> ConnectionErrorCause.SSL;
            case 1251, 1524, 2059, 2061 -> ConnectionErrorCause.AUTH_PLUGIN;
            default -> null;
        };
    }

    private static ConnectionErrorCause byNetworkException(List<Throwable> chain) {
        for (Throwable t : chain) {
            if (t instanceof UnknownHostException) {
                return ConnectionErrorCause.UNKNOWN_HOST;
            }
            if (t instanceof SocketTimeoutException || t instanceof NoRouteToHostException
                    || t instanceof SQLTimeoutException) {
                return ConnectionErrorCause.TIMEOUT;
            }
            if (t instanceof ConnectException) {
                // «Connection timed out: connect» è il tempo massimo del sistema operativo, non un rifiuto
                return lower(t).contains("timed out") ? ConnectionErrorCause.TIMEOUT : ConnectionErrorCause.PORT_CLOSED;
            }
            if (t instanceof SSLException) {
                return ConnectionErrorCause.SSL;
            }
        }
        return null;
    }

    private static ConnectionErrorCause byText(List<Throwable> chain, ConnectionProfile profile) {
        for (Throwable t : chain) {
            String m = withoutAddress(lower(t), profile);
            if (m.contains("public key") || m.contains("allowpublickeyretrieval") || m.contains("authentication plugin")
                    || m.contains("auth plugin") || m.contains("plugin") && m.contains("not supported")) {
                return ConnectionErrorCause.AUTH_PLUGIN;
            }
            if (SSL_WORD.matcher(m).find() || m.contains("secure transport")) {
                return ConnectionErrorCause.SSL;
            }
        }
        return null;
    }

    /** «ssl» o «tls» come parole intere (non dentro «classlab» o «atlas»). */
    private static final Pattern SSL_WORD = Pattern.compile("(?<![a-z0-9])(ssl|tls)(?![a-z0-9])");

    /** La parte {@code address=(host=…)(port=…)…} del driver. */
    private static final Pattern DRIVER_ADDRESS = Pattern.compile("address=(\\([^()]*\\))+");

    /** Il testo senza l'indirizzo del server: il nome dell'host non deve far scattare nessun indizio. */
    static String withoutAddress(String lowerText, ConnectionProfile profile) {
        String m = DRIVER_ADDRESS.matcher(lowerText).replaceAll(" ");
        String host = profile.host().toLowerCase(Locale.ROOT);
        return host.isEmpty() ? m : m.replace(host, " ");
    }

    /** La prima {@link SQLException} della catena con un codice del server. */
    private static SQLException serverError(List<Throwable> chain) {
        for (Throwable t : chain) {
            if (t instanceof SQLException s && s.getErrorCode() != 0) {
                return s;
            }
        }
        return null;
    }

    private static String sqlState(List<Throwable> chain) {
        for (Throwable t : chain) {
            if (t instanceof SQLException s && s.getSQLState() != null) {
                return s.getSQLState();
            }
        }
        return "";
    }

    /** Per gli errori di rete il testo più utile è quello della causa più profonda, con il nome dell'eccezione. */
    private static String networkText(List<Throwable> chain) {
        Throwable root = chain.get(chain.size() - 1);
        String state = sqlState(chain);
        String text = root.getClass().getName() + ": " + text(root);
        return state.isEmpty() ? text : "SQLSTATE " + state + " · " + text;
    }

    private static String text(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m.strip();
    }

    private static String lower(Throwable t) {
        return t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
    }

    private static List<Throwable> chain(Throwable error) {
        List<Throwable> list = new ArrayList<>();
        for (Throwable t = error; t != null && !list.contains(t) && list.size() < 20; t = t.getCause()) {
            list.add(t);
        }
        return list;
    }
}
