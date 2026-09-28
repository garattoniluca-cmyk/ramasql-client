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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.ramasql.core.CoreMessages;
import it.ramasql.core.importer.EncodingDetector;

/**
 * Uno script {@code .sql} guardato tutto una volta prima di eseguirlo (il ripristino di un dump), a flusso e fuori
 * dall'EDT: quante istruzioni ha, le prime (per l'anteprima, abbreviate se lunghissime), e — <b>senza tetto</b> — quelle
 * più avanti che scelgono, creano o eliminano un catalogo, perché l'anteprima e la conferma rafforzata le vedano anche
 * se stanno alla riga 5000. Annota anche che cosa lo script cambia nella sessione ({@code SQL_MODE}, fuso orario,
 * controlli delle chiavi esterne, {@code LOCK TABLES}, transazioni aperte con {@code BEGIN}), i cataloghi che nomina
 * (anche dentro i commenti eseguibili {@code /*!40000 … *}{@code /} di mysqldump e come {@code catalogo.tabella}), le
 * collation che usa (per sostituire quelle che il server di destinazione non conosce), la codifica (UTF-8, altrimenti
 * Windows-1252 con un avviso) e se è un dump di questo client rimasto <b>incompleto</b>.
 *
 * @param file             il file
 * @param size             byte del file quando è stato letto
 * @param modifiedMillis   data di modifica quando è stato letto (se cambia, si rilegge prima di eseguire)
 * @param charset          codifica con cui si legge
 * @param notUtf8          il file non è UTF-8 valido: si legge come Windows-1252 (da dire all'utente)
 * @param statements       istruzioni nel file
 * @param first            le prime istruzioni (al più {@link #FIRST}), abbreviate per l'anteprima
 * @param later            dopo le prime: tutte quelle sui cataloghi e le distruttive (queste al più
 *                         {@link #MAX_DESTRUCTIVE}, ma i {@code DROP DATABASE} sempre), nell'ordine del file
 * @param destructive      le istruzioni distruttive trovate (al più {@link #MAX_DESTRUCTIVE}, i {@code DROP DATABASE}
 *                         sempre)
 * @param destructiveCount istruzioni distruttive in tutto, senza tetto
 * @param catalogs         cataloghi nominati da {@code USE}, {@code CREATE DATABASE}, {@code DROP DATABASE}
 * @param qualifiedCatalogs cataloghi nominati come {@code catalogo.tabella}
 * @param hasUse           lo script sceglie da sé il catalogo ({@code USE})
 * @param createsCatalog   lo script crea o elimina cataloghi
 * @param chars            caratteri del file (per l'avanzamento)
 * @param sessionChanges   che cosa lo script cambia nella sessione
 * @param collations       collation nominate (in minuscolo)
 * @param incompleteDump   è un dump di RamaSQL senza la riga finale «Fine del dump» (interrotto o troncato)
 */
public record ScriptPreview(Path file, long size, long modifiedMillis, Charset charset, boolean notUtf8,
        long statements, List<SqlStatement> first, List<SqlStatement> later, List<SqlStatement> destructive,
        long destructiveCount, Set<String> catalogs, Set<String> qualifiedCatalogs, boolean hasUse,
        boolean createsCatalog, long chars, Set<SessionChange> sessionChanges, Set<String> collations,
        boolean incompleteDump) {

    /** Che cosa uno script può cambiare nella sessione, e lasciare cambiato se si ferma prima della fine. */
    public enum SessionChange { SQL_MODE, TIME_ZONE, FOREIGN_KEY_CHECKS, UNIQUE_CHECKS, NAMES, LOCK_TABLES, TRANSACTION }

    public static final int FIRST = 30;
    public static final int MAX_DESTRUCTIVE = 200;
    /** Oltre questa lunghezza un'istruzione si mostra abbreviata nell'anteprima (un INSERT del dump arriva a 1 MB). */
    public static final int PREVIEW_TEXT_LIMIT = 2000;
    /** Caratteri d'inizio di un'istruzione guardati per riconoscerla. */
    private static final int HEAD = 4000;

    private static final Pattern USE = Pattern.compile("(?is)^\\s*USE\\s+(`(?:[^`]|``)+`|[\\w$]+)");
    private static final Pattern DATABASE = Pattern.compile(
            "(?is)^\\s*(CREATE|DROP)\\s+(?:DATABASE|SCHEMA)\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?(`(?:[^`]|``)+`|[\\w$]+)");
    private static final Pattern QUALIFIED = Pattern.compile(
            "(?i)\\b(?:TABLE|TABLES|INTO|FROM|UPDATE|JOIN|VIEW|EXISTS|REFERENCES|TRIGGER|PROCEDURE|FUNCTION|TO)\\s+"
                    + "(`(?:[^`]|``)+`|[A-Za-z_$][\\w$]*)\\s*\\.\\s*(?=`|[A-Za-z_$])");
    private static final Pattern SET_VAR = Pattern.compile("(?is)^\\s*SET\\b.*");
    private static final Pattern NAMES = Pattern.compile(
            "(?is)^\\s*SET\\s+(?:NAMES|CHARACTER\\s+SET|CHARSET)\\b.*|.*\\bcharacter_set_client\\s*=.*");
    private static final Pattern LOCK = Pattern.compile("(?is)^\\s*LOCK\\s+TABLES?\\b.*");
    private static final Pattern TRANSACTION = Pattern.compile(
            "(?is)^\\s*(?:BEGIN(?:\\s+WORK)?|START\\s+TRANSACTION\\b.*)\\s*$|^\\s*SET\\b.*\\bAUTOCOMMIT\\s*=\\s*(?:0|OFF)\\b.*");
    private static final Pattern DATA = Pattern.compile("(?is)^\\s*(?:INSERT|REPLACE)\\b.*");

    public ScriptPreview {
        first = List.copyOf(first);
        later = List.copyOf(later);
        destructive = List.copyOf(destructive);
        catalogs = Set.copyOf(catalogs);
        qualifiedCatalogs = Set.copyOf(qualifiedCatalogs);
        sessionChanges = sessionChanges.isEmpty() ? Set.of() : Set.copyOf(sessionChanges);
        collations = Set.copyOf(collations);
    }

    /** Lo script cambia {@code FOREIGN_KEY_CHECKS} (i dump lo spengono e alla fine lo riaccendono). */
    public boolean touchesForeignKeyChecks() {
        return sessionChanges.contains(SessionChange.FOREIGN_KEY_CHECKS);
    }

    /** Apre il file come lo legge l'esecuzione: UTF-8, BOM saltato. */
    public static ScriptReader open(Path file) throws IOException {
        return open(file, StandardCharsets.UTF_8);
    }

    /** Apre il file nella codifica trovata dalla lettura di prova ({@link #charset()}). */
    public ScriptReader open() throws IOException {
        return open(file, charset);
    }

    public static ScriptReader open(Path file, Charset charset) throws IOException {
        return open(file, charset, CodingErrorAction.REPLACE);
    }

    private static ScriptReader open(Path file, Charset charset, CodingErrorAction onError) throws IOException {
        InputStream in = Files.newInputStream(file);
        try {
            byte[] head = in.readNBytes(3);
            int bom = EncodingDetector.bomLength(head, head.length, charset);
            InputStream rest = new java.io.SequenceInputStream(
                    new java.io.ByteArrayInputStream(head, bom, head.length - bom), in);
            var decoder = charset.newDecoder().onMalformedInput(onError).onUnmappableCharacter(onError);
            return new ScriptReader(new BufferedReader(new InputStreamReader(rest, decoder), 64 * 1024));
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    /**
     * La lettura di prova. Se il file non è UTF-8 valido si rilegge come Windows-1252 (la codifica dei file di Excel e
     * del Blocco note italiani): lo dice {@link #notUtf8()}.
     */
    public static ScriptPreview scan(Path file, String origin, BooleanSupplier cancelled) throws IOException {
        try {
            return scan(file, origin, cancelled, StandardCharsets.UTF_8, CodingErrorAction.REPORT);
        } catch (CharacterCodingException e) {
            return scan(file, origin, cancelled, EncodingDetector.WINDOWS_1252, CodingErrorAction.REPLACE);
        }
    }

    private static ScriptPreview scan(Path file, String origin, BooleanSupplier cancelled, Charset charset,
            CodingErrorAction onError) throws IOException {
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();
        long count = 0;
        List<SqlStatement> first = new ArrayList<>();
        List<SqlStatement> later = new ArrayList<>();
        List<SqlStatement> destructive = new ArrayList<>();
        long destructiveCount = 0;
        long laterDestructive = 0;
        Set<String> catalogs = new LinkedHashSet<>();
        Set<String> qualified = new TreeSet<>();
        Set<String> collations = new TreeSet<>();
        Set<SessionChange> session = EnumSet.noneOf(SessionChange.class);
        boolean hasUse = false;
        boolean createsCatalog = false;
        long chars;
        try (ScriptReader r = open(file, charset, onError)) {
            ScriptReader.Statement s;
            while ((s = r.next()) != null) {
                count++;
                if (cancelled != null && count % 1000 == 0 && cancelled.getAsBoolean()) {
                    throw new CancellationException();
                }
                String text = s.text();
                SqlStatement statement = SqlStatement.of(text, origin);
                String code = executable(text.length() > HEAD ? text.substring(0, HEAD) : text);
                boolean catalogStatement = false;
                Matcher use = USE.matcher(code);
                if (use.find()) {
                    hasUse = true;
                    catalogs.add(unquote(use.group(1)));
                    catalogStatement = true;
                }
                Matcher db = DATABASE.matcher(code);
                boolean dropCatalog = false;
                if (db.find()) {
                    catalogs.add(unquote(db.group(2)));
                    createsCatalog = true;
                    catalogStatement = true;
                    dropCatalog = db.group(1).equalsIgnoreCase("DROP");
                }
                Matcher q = QUALIFIED.matcher(DATA.matcher(code).matches() ? code.substring(0, Math.min(300,
                        code.length())) : code);
                while (q.find()) {
                    qualified.add(unquote(q.group(1)));
                }
                if (SET_VAR.matcher(code).matches()) {
                    String upper = code.toUpperCase(Locale.ROOT);
                    if (upper.matches("(?s).*\\bSQL_MODE\\s*=.*")) {
                        session.add(SessionChange.SQL_MODE);
                    }
                    if (upper.matches("(?s).*\\bTIME_ZONE\\s*=.*")) {
                        session.add(SessionChange.TIME_ZONE);
                    }
                    if (upper.matches("(?s).*\\bFOREIGN_KEY_CHECKS\\s*=.*")) {
                        session.add(SessionChange.FOREIGN_KEY_CHECKS);
                    }
                    if (upper.matches("(?s).*\\bUNIQUE_CHECKS\\s*=.*")) {
                        session.add(SessionChange.UNIQUE_CHECKS);
                    }
                }
                if (NAMES.matcher(code).matches()) {
                    session.add(SessionChange.NAMES);
                }
                if (LOCK.matcher(code).matches()) {
                    session.add(SessionChange.LOCK_TABLES);
                }
                if (TRANSACTION.matcher(code).matches()) {
                    session.add(SessionChange.TRANSACTION);
                }
                if (!DATA.matcher(code).matches()) {
                    collations.addAll(CollationCompat.collationsIn(executable(text)));
                }
                boolean isDestructive = statement.risk() == RiskLevel.DESTRUCTIVE;
                if (isDestructive) {
                    destructiveCount++;
                }
                SqlStatement shown = count <= FIRST || catalogStatement || isDestructive ? forPreview(text, origin)
                        : null;
                if (isDestructive && (destructive.size() < MAX_DESTRUCTIVE || dropCatalog)) {
                    destructive.add(shown);
                }
                if (count <= FIRST) {
                    first.add(shown);
                } else if (catalogStatement || isDestructive && (laterDestructive < MAX_DESTRUCTIVE || dropCatalog)) {
                    later.add(shown);
                    if (isDestructive) {
                        laterDestructive++;
                    }
                }
            }
            chars = r.charsConsumed();
        }
        return new ScriptPreview(file, size, modified, charset, !charset.equals(StandardCharsets.UTF_8), count, first,
                later, destructive, destructiveCount, catalogs, qualified, hasUse, createsCatalog, chars, session,
                collations, incompleteDump(file, charset));
    }

    /** L'istruzione come si mostra: abbreviata se lunghissima (l'esecuzione rilegge il testo intero dal file). */
    private static SqlStatement forPreview(String text, String origin) {
        return SqlStatement.of(text.length() > PREVIEW_TEXT_LIMIT ? text.substring(0, PREVIEW_TEXT_LIMIT) + " …"
                : text, origin);
    }

    /**
     * Il testo che il server esegue: i commenti eseguibili {@code /*!40000 … *}{@code /} di mysqldump aperti, gli altri
     * commenti tolti. Le stringhe non si guardano (serve solo a riconoscere l'istruzione).
     */
    static String executable(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int n = sql.length();
        int i = 0;
        int openExecutable = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                if (i + 2 < n && (sql.charAt(i + 2) == '!' || sql.charAt(i + 2) == 'M' && i + 3 < n
                        && sql.charAt(i + 3) == '!')) {
                    i += sql.charAt(i + 2) == '!' ? 3 : 4;
                    while (i < n && Character.isDigit(sql.charAt(i))) {
                        i++;
                    }
                    openExecutable++;
                    out.append(' ');
                    continue;
                }
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
                continue;
            }
            if (c == '*' && openExecutable > 0 && i + 1 < n && sql.charAt(i + 1) == '/') {
                openExecutable--;
                i += 2;
                out.append(' ');
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int end = i + 1;
                while (end < n) {
                    char d = sql.charAt(end);
                    if (d == '\\' && c != '`') {
                        end += 2;
                    } else if (d == c) {
                        if (end + 1 < n && sql.charAt(end + 1) == c) {
                            end += 2;
                        } else {
                            break;
                        }
                    } else {
                        end++;
                    }
                }
                end = Math.min(n, end + 1);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '#' || c == '-' && i + 2 < n && sql.charAt(i + 1) == '-' && Character.isWhitespace(sql.charAt(i + 2))) {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? n : end;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Un dump di RamaSQL senza la riga «Fine del dump» in fondo: interrotto, o copiato solo in parte. */
    private static boolean incompleteDump(Path file, Charset charset) throws IOException {
        String title = prefix("dump.header.title");
        String done = prefix("dump.footer.done");
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            byte[] head = new byte[(int) Math.min(512, f.length())];
            f.readFully(head);
            String start = new String(head, charset);
            if (!start.replace("﻿", "").startsWith("-- " + title)) {
                return false;
            }
            long from = Math.max(0, f.length() - 4096);
            byte[] tail = new byte[(int) (f.length() - from)];
            f.seek(from);
            f.readFully(tail);
            return !new String(tail, charset).contains("-- " + done);
        }
    }

    /** Il testo di un messaggio fino al primo segnaposto. */
    private static String prefix(String key) {
        String s = CoreMessages.get(key, "\u0000");
        int i = s.indexOf('\u0000');
        return i < 0 ? s : s.substring(0, i);
    }

    private static String unquote(String name) {
        return name.startsWith("`") ? name.substring(1, name.length() - 1).replace("``", "`") : name;
    }

    /**
     * Lo script per la finestra d'anteprima: le istruzioni prima del file (il {@code USE} scelto), le prime del file e,
     * in fondo, quelle più avanti che toccano i cataloghi o distruggono dati; il titolo dice quante sono in tutto.
     * {@code rewrite} è la sostituzione delle collation che l'esecuzione applicherà ({@link CollationCompat}).
     */
    public SqlScript previewScript(String origin, List<SqlStatement> before, UnaryOperator<String> rewrite) {
        UnaryOperator<String> r = rewrite == null ? UnaryOperator.identity() : rewrite;
        List<SqlStatement> shown = new ArrayList<>(before);
        for (SqlStatement s : first) {
            shown.add(SqlStatement.of(r.apply(s.text()), origin));
        }
        for (SqlStatement s : later) {
            shown.add(SqlStatement.of(r.apply(s.text()), origin));
        }
        String name = file.getFileName().toString();
        String title;
        if (!later.isEmpty()) {
            title = CoreMessages.get("script.file.title.later", name, statements, first.size(), later.size());
        } else {
            title = CoreMessages.get(statements > first.size() ? "script.file.title.more" : "script.file.title", name,
                    statements, first.size());
        }
        return new SqlScript(title, origin, shown);
    }

    public SqlScript previewScript(String origin, List<SqlStatement> before) {
        return previewScript(origin, before, null);
    }

    /**
     * La conferma per tutto il file: si valuta su ciò che si mostra, che comprende le istruzioni distruttive trovate più
     * avanti, così un {@code DROP DATABASE} alla riga 5000 chiede la conferma rafforzata come se fosse in cima. Se le
     * distruttive sono più di quelle tenute, la parola da riscrivere è quella generica.
     */
    public ConfirmationPolicy.Confirmation confirmation(String origin, List<SqlStatement> before) {
        List<SqlStatement> all = new ArrayList<>(before);
        all.addAll(first);
        all.addAll(later);
        ConfirmationPolicy.Confirmation c = ConfirmationPolicy.evaluate(new SqlScript("", origin, all));
        long kept = all.stream().filter(s -> s.risk() == RiskLevel.DESTRUCTIVE).count();
        if (c.level() == ConfirmationPolicy.Level.STRONG && destructiveCount > kept) {
            String word = CoreMessages.get("confirm.word");
            return new ConfirmationPolicy.Confirmation(ConfirmationPolicy.Level.STRONG, word,
                    CoreMessages.get("confirm.strong", word));
        }
        return c;
    }

    /**
     * Tutti i cataloghi nominati — con {@code USE}, {@code CREATE}/{@code DROP DATABASE} o come {@code catalogo.tabella},
     * anche dentro i commenti eseguibili — hanno il prefisso dato (per i test: solo cataloghi {@code ramasql_test_}).
     */
    public boolean onlyCatalogsStartingWith(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return catalogs.stream().allMatch(c -> c.toLowerCase(Locale.ROOT).startsWith(p))
                && qualifiedCatalogs.stream().allMatch(c -> c.toLowerCase(Locale.ROOT).startsWith(p));
    }

    /** Il file è cambiato dopo la lettura di prova (dimensione o data di modifica). */
    public boolean changedOnDisk() {
        try {
            return Files.size(file) != size || Files.getLastModifiedTime(file).toMillis() != modifiedMillis;
        } catch (IOException e) {
            return true;
        }
    }
}
