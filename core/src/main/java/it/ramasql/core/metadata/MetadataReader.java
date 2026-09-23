/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.connection.Session;

/**
 * Lettore dei metadati del server, con <b>caricamento pigro</b> e <b>cache per sessione</b>.
 *
 * <ul>
 *   <li>Legge da {@code information_schema} (e {@code SHOW CREATE}) sulla <b>connessione di servizio</b> della
 *       sessione, tramite il canale interno {@link MetadataQueries}: queste letture non passano da
 *       {@code SqlExecutor} e non finiscono nel registro dell'utente.</li>
 *   <li>Pigro: {@link #tables(String)} dà l'elenco di tabelle e viste di un catalogo con <b>una sola</b> lettura e
 *       senza colonne; i dettagli di una tabella ({@link #table}) si leggono solo quando servono.</li>
 *   <li>Cache: ogni risposta resta in memoria finché non viene invalidata in modo esplicito
 *       ({@link #invalidate(String)}, {@link #invalidate(String, String)}, {@link #invalidateCatalogs()},
 *       {@link #invalidateAll()}); {@code SqlExecutor} invalida da solo dopo ogni DDL. Chi mostra i metadati ascolta
 *       le invalidazioni con {@link #addListener}.</li>
 *   <li>Le differenze tra MariaDB e MySQL sono normalizzate ({@link MetadataNormalizer}): lo stesso schema produce
 *       gli stessi {@link TableDef} sui due server, e {@code TableDiff.diff(letto, letto)} è vuoto.</li>
 *   <li>Routine, trigger ed eventi: solo elenco e testo a richiesta, in sola lettura (ADR-017).</li>
 * </ul>
 * Sicuro tra thread: la cache è concorrente; due letture contemporanee dello stesso oggetto al più lo leggono due
 * volte. Le chiamate sono bloccanti: dall'interfaccia vanno fatte fuori dall'EDT.
 */
public final class MetadataReader {

    private final Connection connection;
    private final boolean quotedDefaults;
    private final ConcurrentMap<String, CatalogCache> cache = new ConcurrentHashMap<>();
    private final List<MetadataListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong queryCount = new AtomicLong();
    private volatile List<CatalogInfo> catalogs;
    private volatile List<CollationInfo> collations;

    /** Cache di un catalogo; ogni parte si riempie solo quando viene chiesta. */
    private static final class CatalogCache {
        volatile List<TableSummary> tables;
        volatile List<ViewDef> views;
        volatile List<RoutineInfo> routines;
        final ConcurrentMap<String, TableDef> tableDefs = new ConcurrentHashMap<>();
        final ConcurrentMap<String, List<ColumnDef>> viewColumns = new ConcurrentHashMap<>();
    }

    /**
     * @param serviceConnection connessione su cui leggere (in pratica quella di servizio della sessione)
     * @param server            tipo e versione del server: decidono come interpretare i default
     */
    public MetadataReader(Connection serviceConnection, ServerInfo server) {
        this.connection = Objects.requireNonNull(serviceConnection, "serviceConnection");
        Objects.requireNonNull(server, "server");
        // MariaDB ≥ 10.2.7 scrive COLUMN_DEFAULT come SQL ('IT', NULL, current_timestamp())
        this.quotedDefaults = server.isMariaDb()
                && (server.atLeast(10, 3) || (server.major() == 10 && server.minor() == 2 && server.patch() >= 7));
    }

    /** Lettore sulla connessione di servizio della sessione. */
    public static MetadataReader of(Session session) {
        return new MetadataReader(session.serviceConnection(), session.serverInfo());
    }

    // ================================================================ cataloghi

    /** Tutti i cataloghi visibili all'utente, di sistema compresi (vedi {@link CatalogInfo#system()}), per nome. */
    public List<CatalogInfo> catalogs() throws SQLException {
        List<CatalogInfo> cached = catalogs;
        if (cached == null) {
            List<CatalogInfo> list = new ArrayList<>();
            for (String[] r : query(MetadataQueries.CATALOGS)) {
                list.add(new CatalogInfo(r[0], r[1], r[2], CatalogInfo.isSystemCatalog(r[0])));
            }
            cached = List.copyOf(list);
            catalogs = cached;
        }
        return cached;
    }

    /** Il catalogo con questo nome, se esiste. */
    public Optional<CatalogInfo> catalog(String name) throws SQLException {
        return catalogs().stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    /** Collation del server (per «Crea catalogo»); non cambiano durante la sessione e non si invalidano. */
    public List<CollationInfo> collations() throws SQLException {
        List<CollationInfo> cached = collations;
        if (cached == null) {
            List<CollationInfo> list = new ArrayList<>();
            for (String[] r : query(MetadataQueries.COLLATIONS)) {
                if (r[0] != null && r[1] != null) {
                    list.add(new CollationInfo(r[0], r[1], "Yes".equalsIgnoreCase(r[2])));
                }
            }
            cached = List.copyOf(list);
            collations = cached;
        }
        return cached;
    }

    // ================================================================ tabelle e viste

    /** Elenco veloce di tabelle e viste del catalogo, per nome, senza colonne (una sola lettura). */
    public List<TableSummary> tables(String catalog) throws SQLException {
        CatalogCache c = cacheOf(catalog);
        List<TableSummary> cached = c.tables;
        if (cached == null) {
            List<TableSummary> list = new ArrayList<>();
            for (String[] r : query(MetadataQueries.TABLES_OF_CATALOG, catalog)) {
                boolean view = r[1] != null && r[1].toUpperCase(Locale.ROOT).contains("VIEW");
                list.add(new TableSummary(catalog, r[0], view ? TableKind.VIEW : TableKind.TABLE,
                        view ? null : r[2], view ? "" : r[3]));
            }
            cached = List.copyOf(list);
            c.tables = cached;
        }
        return cached;
    }

    /**
     * Dettagli completi di una tabella: colonne, indici, chiavi esterne, opzioni, elementi avanzati. Vuoto se la
     * tabella non esiste (o è una vista: per le viste {@link #view} e {@link #viewColumns}).
     */
    public Optional<TableDef> table(String catalog, String table) throws SQLException {
        CatalogCache c = cacheOf(catalog);
        String key = key(table);
        TableDef cached = c.tableDefs.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        List<String[]> tableRows = query(MetadataQueries.TABLE, catalog, table);
        if (tableRows.isEmpty() || isView(tableRows.get(0))) {
            return Optional.empty();
        }
        String[] tableRow = tableRows.get(0);
        String realName = tableRow[MetadataNormalizer.T_NAME];
        List<String[]> columns = query(MetadataQueries.COLUMNS, catalog, realName);
        List<String[]> indexes = query(MetadataQueries.INDEXES, catalog, realName);
        List<String[]> fks = query(MetadataQueries.FOREIGN_KEYS, catalog, realName);
        String ddl = showCreate("TABLE", catalog, realName);
        TableDef def = MetadataNormalizer.table(catalog, tableRow, columns, indexes, fks, ddl, quotedDefaults);
        c.tableDefs.put(key, def);
        return Optional.of(def);
    }

    /** Dettagli di tutte le tabelle (non viste) del catalogo, per nome: comodo per il modello ER e i confronti. */
    public List<TableDef> allTables(String catalog) throws SQLException {
        List<TableDef> out = new ArrayList<>();
        for (TableSummary s : tables(catalog)) {
            if (!s.isView()) {
                table(catalog, s.name()).ifPresent(out::add);
            }
        }
        return out;
    }

    /** Viste del catalogo, per nome, con la definizione riportata dal server ({@code VIEW_DEFINITION}). */
    public List<ViewDef> views(String catalog) throws SQLException {
        CatalogCache c = cacheOf(catalog);
        List<ViewDef> cached = c.views;
        if (cached == null) {
            List<ViewDef> list = new ArrayList<>();
            for (String[] r : query(MetadataQueries.VIEWS, catalog)) {
                list.add(new ViewDef(catalog, r[0], r[1], r[2], "YES".equalsIgnoreCase(r[3]), r[4], r[5]));
            }
            cached = List.copyOf(list);
            c.views = cached;
        }
        return cached;
    }

    public Optional<ViewDef> view(String catalog, String view) throws SQLException {
        return views(catalog).stream().filter(v -> v.name().equalsIgnoreCase(view)).findFirst();
    }

    /** Colonne di una vista (nome e tipo come per le tabelle; charset/collation come riportati dal server). */
    public List<ColumnDef> viewColumns(String catalog, String view) throws SQLException {
        CatalogCache c = cacheOf(catalog);
        String key = key(view);
        List<ColumnDef> cached = c.viewColumns.get(key);
        if (cached == null) {
            List<ColumnDef> list = new ArrayList<>();
            for (String[] r : query(MetadataQueries.COLUMNS, catalog, view)) {
                list.add(MetadataNormalizer.column(r, quotedDefaults, null, null, null));
            }
            cached = List.copyOf(list);
            c.viewColumns.put(key, cached);
        }
        return cached;
    }

    // ================================================================ routine, trigger, eventi (sola lettura)

    /** Procedure, funzioni, trigger ed eventi del catalogo (in quest'ordine, ciascun gruppo per nome). */
    public List<RoutineInfo> routines(String catalog) throws SQLException {
        CatalogCache c = cacheOf(catalog);
        List<RoutineInfo> cached = c.routines;
        if (cached == null) {
            List<RoutineInfo> procedures = new ArrayList<>();
            List<RoutineInfo> functions = new ArrayList<>();
            for (String[] r : query(MetadataQueries.ROUTINES, catalog)) {
                if ("PROCEDURE".equalsIgnoreCase(r[1])) {
                    procedures.add(new RoutineInfo(catalog, r[0], RoutineKind.PROCEDURE, null, ""));
                } else if ("FUNCTION".equalsIgnoreCase(r[1])) {
                    functions.add(new RoutineInfo(catalog, r[0], RoutineKind.FUNCTION, null, r[2]));
                }
            }
            List<RoutineInfo> list = new ArrayList<>(procedures);
            list.addAll(functions);
            for (String[] r : query(MetadataQueries.TRIGGERS, catalog)) {
                list.add(new RoutineInfo(catalog, r[0], RoutineKind.TRIGGER, r[1], r[2] + " " + r[3]));
            }
            for (String[] r : query(MetadataQueries.EVENTS, catalog)) {
                list.add(new RoutineInfo(catalog, r[0], RoutineKind.EVENT, null, r[1]));
            }
            cached = List.copyOf(list);
            c.routines = cached;
        }
        return cached;
    }

    /**
     * Testo di creazione di una routine, un trigger o un evento ({@code SHOW CREATE …}); vuoto se il server non lo
     * mostra (oggetto sparito o permesso mancante). Non in cache: si chiede solo quando l'utente lo apre.
     */
    public Optional<String> showCreate(RoutineInfo routine) throws SQLException {
        return Optional.ofNullable(showCreateOrNull(routine.kind().sql(), routine.catalog(), routine.name()));
    }

    /** {@code SHOW CREATE TABLE}: l'SQL di creazione come lo scrive il server; vuoto se la tabella non c'è. */
    public Optional<String> showCreateTable(String catalog, String table) throws SQLException {
        return Optional.ofNullable(showCreateOrNull("TABLE", catalog, table));
    }

    /** {@code SHOW CREATE VIEW}; vuoto se la vista non c'è. */
    public Optional<String> showCreateView(String catalog, String view) throws SQLException {
        return Optional.ofNullable(showCreateOrNull("VIEW", catalog, view));
    }

    /** L'istruzione {@code SHOW CREATE} che il lettore usa, per mostrarla accanto al risultato («come l'ho letto»). */
    public static String showCreateStatement(String kind, String catalog, String name) {
        return MetadataQueries.showCreateText(kind, catalog, name);
    }

    // ================================================================ cache

    /** Dimentica tutto ciò che si sa del catalogo (tabelle, viste, routine) e avvisa gli ascoltatori. */
    public void invalidate(String catalog) {
        cache.remove(key(catalog));
        fire(catalog, null);
    }

    /**
     * Dimentica una tabella (o vista) e l'elenco del suo catalogo (una rinomina o un'eliminazione lo cambiano);
     * gli altri dettagli già letti del catalogo restano.
     */
    public void invalidate(String catalog, String table) {
        CatalogCache c = cache.get(key(catalog));
        if (c != null) {
            c.tables = null;
            c.views = null;
            c.tableDefs.remove(key(table));
            c.viewColumns.remove(key(table));
        }
        fire(catalog, table);
    }

    /** Dimentica l'elenco dei cataloghi (dopo {@code CREATE}/{@code DROP DATABASE}). */
    public void invalidateCatalogs() {
        catalogs = null;
        fire(null, null);
    }

    /** Dimentica tutto (tasto «Aggiorna» del navigatore). */
    public void invalidateAll() {
        catalogs = null;
        cache.clear();
        fire(null, null);
    }

    /** Diagnostica (test del caricamento pigro e dell'invalidazione): il catalogo ha qualcosa in cache? */
    public boolean isCached(String catalog) {
        return cache.containsKey(key(catalog));
    }

    /** Diagnostica: i dettagli di questa tabella sono in cache? */
    public boolean isCached(String catalog, String table) {
        CatalogCache c = cache.get(key(catalog));
        return c != null && c.tableDefs.containsKey(key(table));
    }

    /** Diagnostica: quante letture ha fatto finora il lettore sul server. */
    public long queryCount() {
        return queryCount.get();
    }

    public void addListener(MetadataListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(MetadataListener listener) {
        listeners.remove(listener);
    }

    // ================================================================ interni

    private void fire(String catalog, String table) {
        for (MetadataListener l : listeners) {
            l.metadataInvalidated(catalog, table);
        }
    }

    private CatalogCache cacheOf(String catalog) {
        Objects.requireNonNull(catalog, "catalog");
        return cache.computeIfAbsent(key(catalog), k -> new CatalogCache());
    }

    private List<String[]> query(String sql, String... params) throws SQLException {
        queryCount.incrementAndGet();
        return MetadataQueries.rows(connection, sql, params);
    }

    private String showCreate(String kind, String catalog, String name) throws SQLException {
        queryCount.incrementAndGet();
        return MetadataQueries.showCreate(connection, kind, catalog, name);
    }

    /** {@code SHOW CREATE} di un oggetto che potrebbe non esistere: «tabella/vista sconosciuta» → {@code null}. */
    private String showCreateOrNull(String kind, String catalog, String name) throws SQLException {
        try {
            return showCreate(kind, catalog, name);
        } catch (SQLException e) {
            // 1146 tabella, 1049 catalogo, 1305 routine, 1360 trigger, 1539 evento inesistenti; 1347 «non è una vista»
            int code = e.getErrorCode();
            if (code == 1146 || code == 1049 || code == 1305 || code == 1360 || code == 1539 || code == 1347) {
                return null;
            }
            throw e;
        }
    }

    private static boolean isView(String[] tableRow) {
        String type = tableRow[MetadataNormalizer.T_TYPE];
        return type != null && type.toUpperCase(Locale.ROOT).contains("VIEW");
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
