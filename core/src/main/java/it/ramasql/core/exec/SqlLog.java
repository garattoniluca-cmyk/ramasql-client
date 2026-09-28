/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import it.ramasql.core.CoreMessages;

/**
 * Registro in memoria di <b>ogni</b> istruzione eseguita dal client per conto dell'utente (pannello SQL →
 * Registro). Lo alimenta solo {@link SqlExecutor}; le letture interne dei metadati non ci finiscono.
 *
 * <h2>Esportazione come script {@code .sql}</h2>
 * {@link #exportScript(ExportOptions)} produce uno script <b>rieseguibile</b> («il compito di oggi in SQL»):
 * intestazione di commenti (data, connessioni, conteggi), poi le istruzioni nell'ordine di esecuzione, ciascuna
 * preceduta da una riga di commento con ora, origine ed esito. <b>Scelta:</b> le istruzioni non riuscite o interrotte
 * restano nello script ma <b>commentate</b> (ogni riga preceduta da {@code -- }) con il messaggio d'errore: lo
 * studente vede che cosa aveva provato e perché non è andato. <b>Attenzione:</b> un'istruzione non riuscita può essere
 * stata applicata <b>in parte</b> (MariaDB {@code DROP TABLE a, b} con {@code b} inesistente elimina {@code a};
 * un {@code INSERT} di più righe su MyISAM che si ferma su un duplicato lascia le righe precedenti): per questo sotto
 * ciascuna compare l'avviso «può essere stata applicata in parte: verifica», e in quel caso la riesecuzione può non
 * riprodurre lo stato finale. Le istruzioni con {@code ;} interni sono racchiuse tra {@code DELIMITER $$}. Con
 * {@link ExportOptions#withoutCatalog(String)} i riferimenti a quel catalogo si tolgono e le istruzioni che
 * riguardano il catalogo stesso si commentano (vedi {@link #replayForm}): lo script si riesegue su un altro catalogo
 * e non crea, modifica né elimina mai quello d'origine.
 */
public final class SqlLog {

    /** Esito registrato. */
    public enum Outcome { OK, ERROR, INTERRUPTED }

    /**
     * Una riga del registro.
     *
     * @param sequence       numero progressivo (da 1)
     * @param time           quando è finita
     * @param connection     connessione, es. «Laboratorio (MariaDB 11.5.2)»
     * @param origin         origine: «Editor SQL», «Navigatore», «Griglia»…
     * @param sql            testo eseguito
     * @param outcome        esito
     * @param errorCode      codice d'errore del server (0 se OK)
     * @param sqlState       SQLSTATE ({@code ""} se OK)
     * @param message        messaggio del server ({@code ""} se OK)
     * @param durationMillis durata
     * @param rows           righe interessate o lette
     * @param note           spiegazione in più ({@code ""} se nessuna): per un'istruzione preparata, i lotti e le righe
     * @param parameterized  istruzione preparata con segnaposti {@code ?} (i valori erano in un file, non nel testo):
     *                       nell'esportazione si commenta, perché così non si può rieseguire
     * @param file           il file {@code .sql} da cui viene l'istruzione ({@code ""} se nessuno): nell'esportazione
     *                       le istruzioni di un file diventano un solo commento che rimanda al file (rieseguirne solo
     *                       alcune — i {@code DROP} sì, i {@code CREATE} abbreviati no — distruggerebbe dati)
     */
    public record Entry(long sequence, Instant time, String connection, String origin, String sql, Outcome outcome,
            int errorCode, String sqlState, String message, long durationMillis, long rows, String note,
            boolean parameterized, String file) {

        public Entry {
            Objects.requireNonNull(time, "time");
            connection = connection == null ? "" : connection;
            origin = origin == null ? "" : origin;
            Objects.requireNonNull(sql, "sql");
            Objects.requireNonNull(outcome, "outcome");
            sqlState = sqlState == null ? "" : sqlState;
            message = message == null ? "" : message;
            note = note == null ? "" : note;
            file = file == null ? "" : file;
        }

        /** Viene dall'esecuzione di un file {@code .sql}. */
        public boolean fromFile() {
            return !file.isEmpty();
        }

        public boolean isOk() {
            return outcome == Outcome.OK;
        }
    }

    /** Ascoltatore del registro (pannello SQL). Chiamato sul thread che registra: dall'UI si torna sull'EDT. */
    public interface Listener {
        void entryAdded(Entry entry);

        default void cleared() {
        }
    }

    /**
     * Opzioni dell'esportazione.
     *
     * @param unqualifyCatalog catalogo i cui riferimenti espliciti vanno tolti ({@code null} = nessuno)
     * @param exportTime       ora dell'esportazione scritta nell'intestazione
     */
    public record ExportOptions(String unqualifyCatalog, Instant exportTime) {

        public static ExportOptions defaults() {
            return new ExportOptions(null, Instant.now());
        }

        /** Rieseguibile su un altro catalogo: toglie {@code `catalogo`.} e commenta {@code USE catalogo}. */
        public static ExportOptions withoutCatalog(String catalog) {
            return new ExportOptions(catalog, Instant.now());
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final List<Entry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private long nextSequence = 1;

    /** Registra un'istruzione eseguita: solo {@link SqlExecutor}. */
    Entry add(String connection, String origin, String sql, Outcome outcome, int errorCode, String sqlState,
            String message, long durationMillis, long rows) {
        return add(connection, origin, sql, outcome, errorCode, sqlState, message, durationMillis, rows, "", false, "");
    }

    /** Registra un'istruzione, con una nota e l'indicazione di istruzione preparata: solo {@link SqlExecutor}. */
    Entry add(String connection, String origin, String sql, Outcome outcome, int errorCode, String sqlState,
            String message, long durationMillis, long rows, String note, boolean parameterized) {
        return add(connection, origin, sql, outcome, errorCode, sqlState, message, durationMillis, rows, note,
                parameterized, "");
    }

    /** Registra un'istruzione eseguita da un file {@code .sql}: solo {@link SqlExecutor}. */
    Entry add(String connection, String origin, String sql, Outcome outcome, int errorCode, String sqlState,
            String message, long durationMillis, long rows, String note, boolean parameterized, String file) {
        Entry e;
        synchronized (entries) {
            e = new Entry(nextSequence++, Instant.now(), connection, origin, sql, outcome, errorCode, sqlState,
                    message, durationMillis, rows, note, parameterized, file);
            entries.add(e);
        }
        for (Listener l : listeners) {
            try {
                l.entryAdded(e);
            } catch (RuntimeException ignored) {
                // un ascoltatore guasto non deve impedire di registrare né di eseguire
            }
        }
        return e;
    }

    /** Copia delle righe, in ordine. */
    public List<Entry> entries() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    /** Svuota il registro (non tocca il server). */
    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
        for (Listener l : listeners) {
            l.cleared();
        }
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ================================================================ esportazione

    public String exportScript() {
        return exportScript(ExportOptions.defaults());
    }

    /** Lo script {@code .sql} rieseguibile (vedi la documentazione della classe). */
    public String exportScript(ExportOptions options) {
        return exportScript(entries(), options);
    }

    /** Scrive lo script in UTF-8. */
    public void export(Path file, ExportOptions options) throws IOException {
        Files.writeString(file, exportScript(options), StandardCharsets.UTF_8);
    }

    /** Come {@link #exportScript(ExportOptions)} per un elenco di righe dato (es. quelle filtrate nel pannello). */
    public static String exportScript(List<Entry> list, ExportOptions options) {
        long ok = list.stream().filter(Entry::isOk).count();
        long failed = list.size() - ok;
        Set<String> connections = new LinkedHashSet<>();
        list.forEach(e -> {
            if (!e.connection().isEmpty()) {
                connections.add(e.connection());
            }
        });
        StringBuilder out = new StringBuilder();
        out.append("-- ").append(CoreMessages.get("log.export.header", DATE_TIME.format(options.exportTime())))
                .append('\n');
        if (!connections.isEmpty()) {
            out.append("-- ").append(CoreMessages.get("log.export.connections", String.join(", ", connections)))
                    .append('\n');
        }
        out.append("-- ").append(CoreMessages.get("log.export.counts", ok, failed)).append('\n');
        if (options.unqualifyCatalog() != null) {
            out.append("-- ").append(CoreMessages.get("log.export.unqualified", options.unqualifyCatalog()))
                    .append('\n');
        }
        String catalog = options.unqualifyCatalog();
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (e.fromFile()) {
                // le istruzioni consecutive dello stesso file: un solo rimando al file, niente da rieseguire qui
                int j = i;
                long statements = 0;
                long fileFailed = 0;
                while (j < list.size() && list.get(j).file().equals(e.file())) {
                    statements++;
                    if (!list.get(j).isOk()) {
                        fileFailed++;
                    }
                    j++;
                }
                out.append('\n').append("-- ").append(CoreMessages.get("log.export.file", TIME.format(e.time()),
                        e.file(), statements, fileFailed)).append('\n');
                i = j - 1;
                continue;
            }
            out.append('\n').append("-- ").append(describe(e)).append('\n');
            if (!e.note().isEmpty()) {
                out.append("-- ").append(oneLine(e.note())).append('\n');
            }
            if (e.parameterized()) {
                out.append(ScriptText.commentedOut(e.sql() + ";")).append('\n');
                out.append("-- ").append(CoreMessages.get("log.export.parameterized")).append('\n');
                continue;
            }
            Replay r = catalog == null ? new Replay(ReplayKind.UNCHANGED, e.sql()) : replayForm(e.sql(), catalog);
            switch (r.kind()) {
                case USE_ORIGIN -> {
                    out.append(ScriptText.commentedOut(e.sql() + ";")).append('\n');
                    out.append("-- ").append(CoreMessages.get("log.export.useOmitted")).append('\n');
                    continue;
                }
                case CATALOG_STATEMENT -> {
                    out.append(ScriptText.commentedOut(e.sql() + ";")).append('\n');
                    out.append("-- ").append(CoreMessages.get("log.export.catalogStatement", catalog)).append('\n');
                    continue;
                }
                case RESIDUAL_REFERENCE -> {
                    out.append(ScriptText.commentedOut(e.sql() + ";")).append('\n');
                    out.append("-- ").append(CoreMessages.get("log.export.residualReference", catalog)).append('\n');
                    continue;
                }
                default -> {
                    // UNCHANGED, STRIPPED: sotto
                }
            }
            if (!e.isOk()) {
                out.append(ScriptText.commentedOut(r.sql() + ";")).append('\n');
                out.append("-- ").append(CoreMessages.get("log.export.partial")).append('\n');
                continue;
            }
            if (r.kind() == ReplayKind.STRIPPED && e.origin().equals(SqlOrigin.EDITOR.label())) {
                out.append("-- ").append(CoreMessages.get("log.export.strippedByHand", catalog)).append('\n');
            }
            out.append(ScriptText.terminated(r.sql())).append('\n');
        }
        return out.toString();
    }

    private static String describe(Entry e) {
        String time = TIME.format(e.time());
        return switch (e.outcome()) {
            case OK -> CoreMessages.get("log.export.ok", e.sequence(), time, e.origin(), e.rows(), e.durationMillis());
            case ERROR -> CoreMessages.get("log.export.error", e.sequence(), time, e.origin(), e.errorCode(),
                    e.sqlState(), oneLine(e.message()));
            case INTERRUPTED -> CoreMessages.get("log.export.interrupted", e.sequence(), time, e.origin());
        };
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Come un'istruzione del registro si riscrive nell'esportazione «senza catalogo». */
    enum ReplayKind {
        /** Non nomina il catalogo: si riesegue così com'è. */
        UNCHANGED,
        /** I qualificatori {@code `catalogo`.} sono stati tolti. */
        STRIPPED,
        /** {@code USE catalogo}: commentata. */
        USE_ORIGIN,
        /** {@code CREATE/ALTER/DROP DATABASE catalogo}: commentata (creerebbe, cambierebbe o eliminerebbe l'origine). */
        CATALOG_STATEMENT,
        /** Dopo aver tolto i qualificatori nomina ancora il catalogo (forma non riconosciuta): commentata. */
        RESIDUAL_REFERENCE
    }

    /**
     * @param kind come è stata trattata
     * @param sql  testo da rieseguire (per USE/CATALOG/RESIDUAL quello originale, da commentare)
     */
    record Replay(ReplayKind kind, String sql) {
    }

    private static final Set<String> CATALOG_VERBS = Set.of("CREATE", "ALTER", "DROP");

    /**
     * Forma rieseguibile «senza catalogo» di un'istruzione. <b>Regola ferrea:</b> rieseguire lo script non deve
     * <b>mai</b> creare, modificare o eliminare il catalogo d'origine. Quindi:
     * <ul>
     *   <li>{@code USE c} e {@code CREATE [OR REPLACE] / ALTER / DROP DATABASE|SCHEMA [IF [NOT] EXISTS] c} (anche
     *       dentro {@code SET STATEMENT … FOR} o in un commento eseguibile) → commentate, con la spiegazione;</li>
     *   <li>i qualificatori {@code `c`.} / {@code c.} attaccati al nome si tolgono (fuori da stringhe e commenti);
     *       se l'istruzione era scritta a mano (origine «Editor SQL») un commento lo segnala;</li>
     *   <li>se dopo averli tolti il catalogo è ancora nominato — {@code c . t} con spazi, un nome {@code c} isolato
     *       ({@code SHOW TABLES FROM c}), {@code "c".t} tra virgolette doppie, oppure una stringa che lo nomina in
     *       un'istruzione con {@code PREPARE}/{@code EXECUTE} (SQL dinamico) — l'istruzione si commenta: nel dubbio
     *       non si riesegue.</li>
     * </ul>
     */
    static Replay replayForm(String sql, String catalog) {
        String unq = unqualify(sql, catalog);
        if (unq == null) {
            return new Replay(ReplayKind.USE_ORIGIN, sql);
        }
        if (isCatalogStatement(sql, catalog)) {
            return new Replay(ReplayKind.CATALOG_STATEMENT, sql);
        }
        if (namesCatalog(unq, catalog)) {
            return new Replay(ReplayKind.RESIDUAL_REFERENCE, sql);
        }
        return new Replay(unq.equals(sql) ? ReplayKind.UNCHANGED : ReplayKind.STRIPPED, unq);
    }

    /** {@code CREATE [OR REPLACE] | ALTER | DROP  DATABASE|SCHEMA [IF [NOT] EXISTS] c}, anche avvolta. */
    private static boolean isCatalogStatement(String sql, String catalog) {
        List<SqlLexer.Token> t = SqlLexer.tokenize(SqlLexer.innermost(sql));
        int i = SqlLexer.firstMeaningful(t);
        if (i < 0 || t.get(i).type() != SqlLexer.Type.WORD || !CATALOG_VERBS.contains(t.get(i).upper())) {
            return false;
        }
        i++;
        if (i + 1 < t.size() && t.get(i).isWord("OR") && t.get(i + 1).isWord("REPLACE")) {
            i += 2;
        }
        if (i >= t.size() || !(t.get(i).isWord("DATABASE") || t.get(i).isWord("SCHEMA"))) {
            return false;
        }
        i = SqlLexer.skipIfExists(t, i + 1);
        return i < t.size() && t.get(i).isName() && t.get(i).text().equalsIgnoreCase(catalog);
    }

    /** Il testo nomina ancora il catalogo (vedi {@link #replayForm}). */
    private static boolean namesCatalog(String sql, String catalog) {
        List<SqlLexer.Token> t = SqlLexer.tokenize(sql);
        boolean dynamic = t.stream().anyMatch(x -> x.isWord("PREPARE") || x.isWord("EXECUTE"));
        Pattern word = Pattern.compile("(?i)(^|[^A-Za-z0-9_$])" + Pattern.quote(catalog) + "([^A-Za-z0-9_$]|$)");
        for (int i = 0; i < t.size(); i++) {
            SqlLexer.Token x = t.get(i);
            boolean afterDot = i > 0 && t.get(i - 1).type() == SqlLexer.Type.DOT;
            boolean beforeDot = i + 1 < t.size() && t.get(i + 1).type() == SqlLexer.Type.DOT;
            if (x.isName() && x.text().equalsIgnoreCase(catalog) && (!afterDot || beforeDot)) {
                return true;   // c isolato, oppure c . t
            }
            if (x.type() == SqlLexer.Type.STRING) {
                String body = x.text().length() >= 2 ? x.text().substring(1, x.text().length() - 1) : x.text();
                if ((beforeDot || afterDot) && body.equalsIgnoreCase(catalog)) {
                    return true;   // "c".t con ANSI_QUOTES
                }
                if (dynamic && word.matcher(body).find()) {
                    return true;   // PREPARE s FROM 'DROP DATABASE c'
                }
            }
        }
        return false;
    }

    /**
     * Toglie i riferimenti espliciti al catalogo ({@code `c`.x}, {@code c.x}) fuori da stringhe e commenti;
     * {@code null} se l'istruzione è {@code USE c} (da non rieseguire).
     */
    static String unqualify(String sql, String catalog) {
        List<SqlLexer.Token> tokens = SqlLexer.tokenize(sql);
        int first = SqlLexer.firstMeaningful(tokens);
        if (first >= 0 && tokens.get(first).isWord("USE") && first + 1 < tokens.size()
                && tokens.get(first + 1).isName() && tokens.get(first + 1).text().equalsIgnoreCase(catalog)) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int copied = 0;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            SqlLexer.Token t = tokens.get(i);
            SqlLexer.Token next = tokens.get(i + 1);
            boolean previousIsDot = i > 0 && tokens.get(i - 1).type() == SqlLexer.Type.DOT
                    && tokens.get(i - 1).end() == t.start();
            if (t.isName() && t.text().equalsIgnoreCase(catalog) && next.type() == SqlLexer.Type.DOT
                    && next.start() == t.end() && !previousIsDot) {
                out.append(sql, copied, t.start());
                copied = next.end();
            }
        }
        out.append(sql, copied, sql.length());
        return out.toString();
    }
}
