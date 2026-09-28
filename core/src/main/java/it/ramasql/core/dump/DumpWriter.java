/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.sqlgen.SqlIdentifiers;

/**
 * Scrive il file {@code .sql} del dump <b>in streaming</b> (le righe si scrivono man mano che arrivano): un solo file,
 * UTF-8, rieseguibile dal client, dall'editor SQL, da {@code mysql}/{@code mariadb} o da Navicat. Forma:
 * <pre>
 * -- intestazione (data, server, contenuto)
 * SET NAMES utf8mb4;
 * SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;  SET FOREIGN_KEY_CHECKS = 0;   (se richiesto o circolari)
 * SET @OLD_SQL_MODE = @@SQL_MODE;  SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';
 * SET @OLD_TIME_ZONE = @@TIME_ZONE;  SET TIME_ZONE = '+00:00';          (i TIMESTAMP sono scritti in UTC)
 * per ogni catalogo: [DROP DATABASE IF EXISTS] [CREATE DATABASE … ; USE …]
 *   per ogni tabella: [DROP TABLE IF EXISTS] CREATE TABLE … ; INSERT … VALUES (…), (…);
 *   per ogni vista:   [DROP VIEW IF EXISTS] CREATE VIEW … ;
 * SET TIME_ZONE = @OLD_TIME_ZONE;  SET SQL_MODE = @OLD_SQL_MODE;  SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
 * -- Fine del dump
 * </pre>
 * I nomi degli oggetti nei commenti {@code --} passano da {@link #comment} (senza a-capo: un nome con un a-capo
 * diventerebbe SQL eseguibile).
 * Il {@code SQL_MODE} del ripristino: niente {@code NO_BACKSLASH_ESCAPES} (i letterali usano le barre rovesciate),
 * niente modalità rigorosa (le date «zero» si ripristinano com'erano), {@code NO_AUTO_VALUE_ON_ZERO} (un {@code id} 0 in
 * una colonna {@code AUTO_INCREMENT} resta 0). Sono le scelte di {@code mysqldump}.
 */
public final class DumpWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    /** Oltre questa lunghezza un'istruzione INSERT si chiude anche prima di {@code rowsPerInsert} righe. */
    static final int MAX_STATEMENT_CHARS = 1_000_000;

    private final Writer out;
    private final DumpOptions options;
    private boolean foreignKeysOff;
    private long statements;

    public DumpWriter(Writer out, DumpOptions options) {
        this.out = Objects.requireNonNull(out, "out");
        this.options = Objects.requireNonNull(options, "options");
    }

    /** Istruzioni scritte finora. */
    public long statements() {
        return statements;
    }

    /**
     * Intestazione e impostazioni della sessione di ripristino.
     *
     * @param server      server d'origine, es. «MariaDB 11.5.2»
     * @param catalogs    cataloghi nel dump
     * @param circular    ci sono chiavi esterne circolari: controlli spenti anche senza l'opzione
     */
    public void header(Instant time, String server, List<String> catalogs, boolean circular) throws IOException {
        line("-- " + CoreMessages.get("dump.header.title", TIME.format(time)));
        line("-- " + comment(CoreMessages.get("dump.header.server", server)));
        line("-- " + comment(CoreMessages.get("dump.header.catalogs", String.join(", ", catalogs))));
        line("-- " + CoreMessages.get("dump.header.howto"));
        line("");
        statement("SET NAMES utf8mb4");
        foreignKeysOff = options.disableForeignKeys() || circular;
        if (foreignKeysOff) {
            if (circular && !options.disableForeignKeys()) {
                line("-- " + CoreMessages.get("dump.header.circular"));
            }
            statement("SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS");
            statement("SET FOREIGN_KEY_CHECKS = 0");
        }
        statement("SET @OLD_SQL_MODE = @@SQL_MODE");
        statement("SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO'");
        statement("SET @OLD_TIME_ZONE = @@TIME_ZONE");
        statement("SET TIME_ZONE = '+00:00'");
    }

    /** Inizio di un catalogo: eventuali {@code DROP DATABASE}, {@code CREATE DATABASE} e {@code USE}. */
    public void catalog(String name, String charset, String collation, boolean useIt) throws IOException {
        line("");
        line("-- " + comment(CoreMessages.get("dump.catalog", name)));
        if (options.createDatabase()) {
            if (options.dropIfExists()) {
                statement("DROP DATABASE IF EXISTS " + SqlIdentifiers.quote(name));
            }
            StringBuilder create = new StringBuilder("CREATE DATABASE ");
            if (!options.dropIfExists()) {
                create.append("IF NOT EXISTS ");
            }
            create.append(SqlIdentifiers.quote(name));
            if (charset != null && !charset.isBlank()) {
                create.append(" CHARACTER SET ").append(charset);
            }
            if (collation != null && !collation.isBlank()) {
                create.append(" COLLATE ").append(collation);
            }
            statement(create.toString());
        }
        if (options.createDatabase() || useIt) {
            statement("USE " + SqlIdentifiers.quote(name));
        }
    }

    /** Struttura di una tabella: {@code SHOW CREATE TABLE} del server, con il {@code DROP} se richiesto. */
    public void tableStructure(String table, String createSql) throws IOException {
        line("");
        line("-- " + comment(CoreMessages.get("dump.table.structure", table)));
        if (options.dropIfExists()) {
            statement("DROP TABLE IF EXISTS " + SqlIdentifiers.quote(table));
        }
        statement(createSql.strip());
    }

    /** Struttura di una vista (già ripulita con {@link #viewForDump}). */
    public void view(String view, String createSql) throws IOException {
        line("");
        line("-- " + comment(CoreMessages.get("dump.view", view)));
        if (options.dropIfExists()) {
            statement("DROP VIEW IF EXISTS " + SqlIdentifiers.quote(view));
        }
        statement(createSql.strip());
    }

    /**
     * I dati di una tabella: si aggiungono righe con {@link TableData#row}, poi {@link TableData#end}.
     *
     * @param columns nomi delle colonne, nell'ordine dei valori
     * @param kinds   come si scrive ogni colonna
     */
    public TableData tableData(String table, List<String> columns, List<DumpLiterals.Kind> kinds) throws IOException {
        line("");
        line("-- " + comment(CoreMessages.get("dump.table.data", table)));
        return new TableData(table, columns, kinds);
    }

    /** Righe di una tabella, in {@code INSERT} estesi. */
    public final class TableData {
        private final String head;
        private final List<DumpLiterals.Kind> kinds;
        private final StringBuilder pending = new StringBuilder();
        private int rowsInStatement;
        private long rows;
        private long largestRow;

        private TableData(String table, List<String> columns, List<DumpLiterals.Kind> kinds) {
            this.head = "INSERT INTO " + SqlIdentifiers.quote(table) + " " + SqlIdentifiers.columnList(columns)
                    + " VALUES\n";
            this.kinds = List.copyOf(kinds);
        }

        public void row(Object[] values) throws IOException {
            if (rowsInStatement == 0) {
                pending.append(head);
            } else {
                pending.append(",\n");
            }
            int before = pending.length();
            pending.append('(');
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    pending.append(", ");
                }
                pending.append(DumpLiterals.literal(values[i], kinds.get(i)));
            }
            pending.append(')');
            largestRow = Math.max(largestRow, pending.length() - before);
            rowsInStatement++;
            rows++;
            if (rowsInStatement >= options.rowsPerInsert() || pending.length() >= MAX_STATEMENT_CHARS) {
                flush();
            }
        }

        /** Righe scritte. */
        public long rows() {
            return rows;
        }

        /** Caratteri della riga più lunga (una riga sola fa un INSERT: oltre {@code max_allowed_packet} non entra). */
        public long largestRow() {
            return largestRow;
        }

        private void flush() throws IOException {
            if (rowsInStatement > 0) {
                statement(pending.toString());
                pending.setLength(0);
                rowsInStatement = 0;
            }
        }

        public void end() throws IOException {
            flush();
            if (rows == 0) {
                line("-- " + CoreMessages.get("dump.table.empty"));
            }
        }
    }

    /** Chiusura: impostazioni della sessione rimesse come prima. */
    public void footer(boolean interrupted) throws IOException {
        line("");
        statement("SET TIME_ZONE = @OLD_TIME_ZONE");
        statement("SET SQL_MODE = @OLD_SQL_MODE");
        if (foreignKeysOff) {
            statement("SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS");
        }
        line("-- " + CoreMessages.get(interrupted ? "dump.footer.interrupted" : "dump.footer.done", statements));
        out.flush();
    }

    private void statement(String sql) throws IOException {
        out.write(sql);
        out.write(";\n");
        statements++;
    }

    /** Un testo per un commento {@code --}: niente a-capo né ritorni carrello. */
    static String comment(String text) {
        return text.replace('\r', ' ').replace('\n', ' ');
    }

    private void line(String text) throws IOException {
        out.write(text);
        out.write('\n');
    }

    // ================================================================ DDL delle viste

    private static final Pattern DEFINER = Pattern.compile(
            "\\s+DEFINER\\s*=\\s*(`[^`]*`|'[^']*'|[^\\s@]+)\\s*@\\s*(`[^`]*`|'[^']*'|\\S+)", Pattern.CASE_INSENSITIVE);

    /**
     * Il {@code SHOW CREATE VIEW} del server pronto per il dump: senza {@code DEFINER} (ripristinando con un altro
     * utente il server rifiuterebbe una vista «di» qualcun altro) e senza il nome del catalogo davanti a tabelle e
     * colonne (il server lo aggiunge sempre: la vista ripristinata in un altro catalogo leggerebbe ancora quello
     * d'origine).
     */
    public static String viewForDump(String showCreateView, String catalog) {
        String sql = showCreateView.strip();
        int viewKeyword = indexOfKeyword(sql, "VIEW");
        if (viewKeyword > 0) {
            String head = DEFINER.matcher(sql.substring(0, viewKeyword)).replaceAll("");
            sql = head.stripTrailing() + " " + sql.substring(viewKeyword);
        }
        return unqualify(sql, catalog);
    }

    private static int indexOfKeyword(String sql, String keyword) {
        Matcher m = Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE).matcher(sql);
        return m.find() ? m.start() : -1;
    }

    /**
     * Toglie {@code `catalogo`.} fuori dalle stringhe. Dopo averlo tolto, il nome che segue si copia così com'è: in
     * {@code `scuola`.`scuola`.`id`} (catalogo, tabella e colonna con lo stesso nome) resta {@code `scuola`.`id`}.
     */
    public static String unqualify(String sql, String catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return sql;
        }
        String quoted = SqlIdentifiers.quote(catalog) + ".";
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char ch = sql.charAt(i);
            if (ch == '\'' || ch == '"') {
                int end = skipQuoted(sql, i, ch);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (sql.regionMatches(true, i, quoted, 0, quoted.length())) {
                i += quoted.length();
                if (i < n && sql.charAt(i) == '`') {
                    int end = skipQuoted(sql, i, '`');
                    out.append(sql, i, end);
                    i = end;
                }
                continue;
            }
            if (ch == '`') {
                int end = skipQuoted(sql, i, '`');
                out.append(sql, i, end);
                i = end;
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static int skipQuoted(String s, int i, char quote) {
        int n = s.length();
        i++;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '\\' && quote != '`') {
                i += 2;
            } else if (ch == quote) {
                if (i + 1 < n && s.charAt(i + 1) == quote) {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        return n;
    }

    private static final Pattern THREE_PART = Pattern.compile("`((?:[^`]|``)+)`\\.`(?:[^`]|``)+`\\.`");

    /** I cataloghi che una vista (già ripulita) nomina ancora: {@code `altro`.`tabella`.`colonna`}. */
    public static List<String> otherCatalogs(String viewSql) {
        List<String> out = new ArrayList<>();
        Matcher m = THREE_PART.matcher(viewSql);
        while (m.find()) {
            String c = m.group(1).replace("``", "`");
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Le tabelle riferite dalle chiavi esterne di un {@code CREATE TABLE} (stesso catalogo), per {@link DumpOrder}. */
    public static List<String> referencedTables(String createTable) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("REFERENCES\\s+(?:`[^`]+`\\.)?`([^`]+)`", Pattern.CASE_INSENSITIVE)
                .matcher(createTable);
        while (m.find()) {
            out.add(m.group(1).replace("``", "`"));
        }
        return out;
    }
}
