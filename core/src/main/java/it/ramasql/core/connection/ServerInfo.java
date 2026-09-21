/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.core.connection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tipo e versione del server, rilevati alla connessione (mai chiesti all'utente).
 * Guida le poche differenze di sintassi tra MariaDB e MySQL nei generatori SQL.
 *
 * @param kind        MariaDB o MySQL
 * @param versionText testo originale di {@code SELECT VERSION()}, es. {@code 11.5.2-MariaDB}
 */
public record ServerInfo(ServerKind kind, String versionText, int major, int minor, int patch) {

    public enum ServerKind { MARIADB, MYSQL }

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** Riconosce il server dal testo di {@code SELECT VERSION()}. */
    public static ServerInfo parse(String versionText) {
        String text = versionText == null ? "" : versionText.trim();
        ServerKind kind = text.toLowerCase().contains("mariadb") ? ServerKind.MARIADB : ServerKind.MYSQL;
        // MariaDB dietro alcuni proxy risponde "5.5.5-10.6.12-MariaDB": vale la versione dopo il prefisso
        String cleaned = kind == ServerKind.MARIADB && text.startsWith("5.5.5-") ? text.substring(6) : text;
        Matcher m = VERSION.matcher(cleaned);
        if (!m.find()) {
            return new ServerInfo(kind, text, 0, 0, 0);
        }
        int patch = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        return new ServerInfo(kind, text, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), patch);
    }

    public boolean isMariaDb() {
        return kind == ServerKind.MARIADB;
    }

    public boolean isMySql() {
        return kind == ServerKind.MYSQL;
    }

    public boolean atLeast(int wantedMajor, int wantedMinor) {
        return major > wantedMajor || (major == wantedMajor && minor >= wantedMinor);
    }

    /** Nome leggibile per la barra di stato, es. «MariaDB 11.5.2». */
    public String displayName() {
        return (isMariaDb() ? "MariaDB " : "MySQL ") + major + "." + minor + "." + patch;
    }
}
