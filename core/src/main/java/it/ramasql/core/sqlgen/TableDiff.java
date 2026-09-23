/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generatore a differenze dell'editor di tabelle: dal modello originale e da quello modificato produce
 * il {@code CREATE TABLE} (tabella nuova) oppure l'{@code ALTER TABLE} <b>minimo</b>. Puro: nessun accesso al server.
 *
 * <h2>Scelte (valide su MariaDB e su MySQL, tutte le versioni sostenute)</h2>
 * <ul>
 *   <li>Identificatori sempre tra backtick. Il nome della tabella è qualificato con il catalogo solo se
 *       {@link TableDef#catalog()} non è nullo: con catalogo nullo l'SQL è rieseguibile su un altro catalogo.</li>
 *   <li><b>Rinomina di colonna:</b> sempre {@code CHANGE COLUMN `vecchio` `nuovo` <definizione completa>}, l'unica
 *       forma accettata da tutte le versioni dei due server ({@code RENAME COLUMN} richiede MariaDB ≥ 10.5 / MySQL ≥ 8.0).
 *       La rinomina è riconosciuta da {@link ColumnDef#ordinalPosition()} (vedi lì); senza posizione, per nome.</li>
 *   <li><b>Modifica di colonna:</b> {@code MODIFY COLUMN} con l'intera definizione (tipo, NULL, default, AI, commento),
 *       perché il server sostituisce tutta la definizione. NULL / NOT NULL è sempre scritto.</li>
 *   <li>Le colonne aggiunte in mezzo ricevono {@code FIRST} / {@code AFTER}; quelle in fondo no. Il riordino di
 *       colonne esistenti non è generato (v1).</li>
 *   <li><b>Ordine delle istruzioni:</b> 1) {@code RENAME TABLE}; 2) un {@code ALTER} con tutti i
 *       {@code DROP FOREIGN KEY} (FK eliminate o modificate); 3) un {@code ALTER} con il resto, nell'ordine:
 *       DROP indici/PK, DROP colonne, CHANGE/MODIFY, ADD colonne, ADD PK/indici, RENAME INDEX, ADD CONSTRAINT FK,
 *       opzioni di tabella. La modifica di una FK è quindi sempre «prima DROP, poi ADD» in due istruzioni distinte:
 *       MySQL non ammette di togliere e rimettere una FK con lo stesso nome nello stesso ALTER.</li>
 *   <li>Modifica delle colonne (o del tipo) di un indice = {@code DROP} + {@code ADD} nello stesso ALTER.
 *       Un indice che cambia solo nome → {@code RENAME INDEX} (MySQL ≥ 5.7, MariaDB ≥ 10.5; altrimenti DROP + ADD).</li>
 *   <li>Default: {@code CURRENT_TIMESTAMP} e sinonimi scritti nudi; le altre espressioni tra parentesi
 *       ({@code DEFAULT (uuid())}), forma accettata da MySQL ≥ 8.0.13 e MariaDB ≥ 10.2. «Nessun default» e
 *       {@code DEFAULT NULL} sono equivalenti per il server e non generano differenze.</li>
 *   <li>Charset/collation di tabella: {@code DEFAULT CHARSET=… COLLATE=…} cambia il predefinito per le colonne
 *       future, non converte le esistenti (come Workbench).</li>
 *   <li>Elementi avanzati (CHECK, colonne generate, partizioni): mai toccati; le colonne con
 *       {@link ColumnDef#generated()} sono ignorate in entrambe le direzioni.</li>
 * </ul>
 */
public final class TableDiff {

    private static final Pattern NOW_FUNCTION =
            Pattern.compile("(CURRENT_TIMESTAMP|NOW|LOCALTIME|LOCALTIMESTAMP)(\\s*\\(\\s*(\\d*)\\s*\\))?");
    private static final Pattern BIT_LITERAL = Pattern.compile("[bB]'[01]+'");

    private TableDiff() {
    }

    /** Come {@link #diff}, ma con origine e classe di rischio per la pipeline «anteprima SQL». */
    public static List<SqlStatement> statements(TableDef original, TableDef edited, ServerInfo server, String origin) {
        return SqlStatement.listOf(diff(original, edited, server), origin);
    }

    /**
     * @param original stato letto dal server; {@code null} = tabella nuova
     * @param edited   stato voluto
     * @return le istruzioni da eseguire in ordine (senza {@code ;} finale); lista vuota se non c'è nulla da fare
     */
    public static List<String> diff(TableDef original, TableDef edited, ServerInfo server) {
        Objects.requireNonNull(edited, "edited");
        Objects.requireNonNull(server, "server");
        if (original == null) {
            return List.of(createTable(edited));
        }
        List<String> out = new ArrayList<>();

        String targetCatalog = edited.catalog() != null ? edited.catalog() : original.catalog();
        String target = SqlIdentifiers.qualified(targetCatalog, edited.name());
        boolean sameCatalog = original.catalog() == null || original.catalog().equalsIgnoreCase(targetCatalog);
        if (!original.name().equals(edited.name()) || !sameCatalog) {
            out.add("RENAME TABLE " + SqlIdentifiers.qualified(original.catalog(), original.name()) + " TO " + target);
        }

        // ---- colonne: abbinamento originale ↔ modificato
        List<ColumnDef> oc = original.columns();
        List<ColumnDef> ec = edited.columns();
        int[] match = matchColumns(oc, ec);
        boolean[] used = new boolean[oc.size()];
        Map<String, String> renames = new HashMap<>();
        for (int e = 0; e < ec.size(); e++) {
            if (match[e] >= 0) {
                used[match[e]] = true;
                String oldName = oc.get(match[e]).name();
                if (!oldName.equals(ec.get(e).name())) {
                    renames.put(lower(oldName), ec.get(e).name());
                }
            }
        }

        List<String> dropColumns = new ArrayList<>();
        for (int o = 0; o < oc.size(); o++) {
            if (!used[o] && !oc.get(o).generated()) {
                dropColumns.add("DROP COLUMN " + SqlIdentifiers.quote(oc.get(o).name()));
            }
        }
        List<String> changeColumns = new ArrayList<>();
        List<String> addColumns = new ArrayList<>();
        for (int e = 0; e < ec.size(); e++) {
            ColumnDef edit = ec.get(e);
            if (edit.generated()) {
                continue;
            }
            if (match[e] < 0) {
                addColumns.add("ADD COLUMN " + columnDefinition(edit, edited) + position(ec, match, e));
                continue;
            }
            ColumnDef orig = oc.get(match[e]);
            if (orig.generated()) {
                continue;
            }
            if (!orig.name().equals(edit.name())) {
                changeColumns.add("CHANGE COLUMN " + SqlIdentifiers.quote(orig.name()) + " "
                        + columnDefinition(edit, edited));
            } else if (!sameDefinition(orig, edit, original)) {
                changeColumns.add("MODIFY COLUMN " + columnDefinition(edit, edited));
            }
        }

        // ---- indici
        List<IndexDef> origIndexes = original.indexes().stream()
                .map(i -> new IndexDef(i.name(), i.kind(), mapNames(i.columns(), renames))).toList();
        List<IndexDef> dropIdx = new ArrayList<>();
        List<IndexDef> addIdx = new ArrayList<>();
        for (IndexDef o : origIndexes) {
            IndexDef e = findIndex(edited.indexes(), o);
            if (e == null || !sameIndex(o, e)) {
                dropIdx.add(o);
            }
        }
        for (IndexDef e : edited.indexes()) {
            IndexDef o = findIndex(origIndexes, e);
            if (o == null || !sameIndex(o, e)) {
                addIdx.add(e);
            }
        }
        List<String> renameIndexes = new ArrayList<>();
        if (supportsRenameIndex(server)) {
            for (IndexDef d : new ArrayList<>(dropIdx)) {
                if (d.isPrimary() || findIndex(edited.indexes(), d) != null) {
                    continue;
                }
                for (IndexDef a : new ArrayList<>(addIdx)) {
                    if (!a.isPrimary() && findIndex(origIndexes, a) == null && sameIndex(d, a)) {
                        renameIndexes.add("RENAME INDEX " + SqlIdentifiers.quote(d.name()) + " TO "
                                + SqlIdentifiers.quote(a.name()));
                        dropIdx.remove(d);
                        addIdx.remove(a);
                        break;
                    }
                }
            }
        }

        // ---- chiavi esterne
        List<ForeignKeyDef> origFks = original.foreignKeys().stream()
                .map(f -> normalizeFk(f, original, edited, renames, true)).toList();
        List<ForeignKeyDef> editFks = edited.foreignKeys().stream()
                .map(f -> normalizeFk(f, original, edited, renames, false)).toList();
        List<String> dropFks = new ArrayList<>();
        List<String> addFks = new ArrayList<>();
        boolean[] origFkKept = new boolean[origFks.size()];
        for (ForeignKeyDef e : editFks) {
            int o = findFk(origFks, e, origFkKept, targetCatalog);
            if (o >= 0 && sameFk(origFks.get(o), e, targetCatalog)) {
                origFkKept[o] = true;
            } else {
                addFks.add("ADD " + foreignKeyClause(e, targetCatalog));
            }
        }
        for (int o = 0; o < origFks.size(); o++) {
            if (!origFkKept[o]) {
                dropFks.add("DROP FOREIGN KEY " + SqlIdentifiers.quote(origFks.get(o).name()));
            }
        }

        // ---- montaggio
        if (!dropFks.isEmpty()) {
            out.add(alter(target, dropFks));
        }
        List<String> clauses = new ArrayList<>();
        for (IndexDef d : dropIdx) {
            clauses.add(d.isPrimary() ? "DROP PRIMARY KEY" : "DROP INDEX " + SqlIdentifiers.quote(d.name()));
        }
        clauses.addAll(dropColumns);
        clauses.addAll(changeColumns);
        clauses.addAll(addColumns);
        for (IndexDef a : addIdx) {
            clauses.add("ADD " + indexClause(a));
        }
        clauses.addAll(renameIndexes);
        clauses.addAll(addFks);
        clauses.addAll(optionClauses(original, edited));
        if (!clauses.isEmpty()) {
            out.add(alter(target, clauses));
        }
        return List.copyOf(out);
    }

    /** {@code RENAME INDEX}: MySQL ≥ 5.7, MariaDB ≥ 10.5. */
    public static boolean supportsRenameIndex(ServerInfo server) {
        return server.isMariaDb() ? server.atLeast(10, 5) : server.atLeast(5, 7);
    }

    // ================================================================ CREATE TABLE

    /** {@code CREATE TABLE} completo, una riga per colonna, indice e vincolo. */
    public static String createTable(TableDef table) {
        List<String> lines = new ArrayList<>();
        for (ColumnDef c : table.columns()) {
            if (!c.generated()) {
                lines.add(columnDefinition(c, table));
            }
        }
        table.primaryKey().ifPresent(pk -> lines.add(indexClause(pk)));
        for (IndexDef i : table.indexes()) {
            if (!i.isPrimary()) {
                lines.add(indexClause(i));
            }
        }
        for (ForeignKeyDef fk : table.foreignKeys()) {
            lines.add(foreignKeyClause(fk, table.catalog()));
        }
        StringBuilder sql = new StringBuilder("CREATE TABLE ")
                .append(SqlIdentifiers.qualified(table.catalog(), table.name())).append(" (\n  ")
                .append(String.join(",\n  ", lines)).append("\n)");
        if (table.engine() != null) {
            sql.append(" ENGINE=").append(table.engine());
        }
        if (table.charset() != null) {
            sql.append(" DEFAULT CHARSET=").append(table.charset());
        }
        if (table.collation() != null) {
            sql.append(" COLLATE=").append(table.collation());
        }
        if (table.autoIncrementStart() != null) {
            sql.append(" AUTO_INCREMENT=").append(table.autoIncrementStart());
        }
        if (!table.comment().isEmpty()) {
            sql.append(" COMMENT=").append(SqlLiterals.string(table.comment()));
        }
        return sql.toString();
    }

    // ================================================================ frammenti

    /** Definizione completa di una colonna, com'è scritta in CREATE, ADD, MODIFY e CHANGE. */
    public static String columnDefinition(ColumnDef c, TableDef table) {
        StringBuilder sql = new StringBuilder(SqlIdentifiers.quote(c.name())).append(' ').append(c.fullType());
        if (c.unsigned()) {
            sql.append(" UNSIGNED");
        }
        if (c.zerofill()) {
            sql.append(" ZEROFILL");
        }
        if (c.charset() != null && !c.charset().equalsIgnoreCase(table.charset())) {
            sql.append(" CHARACTER SET ").append(c.charset());
        }
        if (c.collation() != null && !c.collation().equalsIgnoreCase(table.collation())) {
            sql.append(" COLLATE ").append(c.collation());
        }
        sql.append(c.nullable() ? " NULL" : " NOT NULL");
        if (!c.autoIncrement()) {
            sql.append(defaultClause(c));
        }
        if (c.onUpdate() != null) {
            sql.append(" ON UPDATE ").append(normalizeExpression(c.onUpdate()));
        }
        if (c.autoIncrement()) {
            sql.append(" AUTO_INCREMENT");
        }
        if (!c.comment().isEmpty()) {
            sql.append(" COMMENT ").append(SqlLiterals.string(c.comment()));
        }
        return sql.toString();
    }

    private static String defaultClause(ColumnDef c) {
        ColumnDefault d = c.defaultValue();
        return switch (d.kind()) {
            case NONE -> "";
            case NULL -> c.nullable() ? " DEFAULT NULL" : "";
            case EXPRESSION -> {
                String expr = normalizeExpression(d.value());
                yield " DEFAULT " + (expr.startsWith("CURRENT_TIMESTAMP") ? expr : "(" + expr + ")");
            }
            case LITERAL -> {
                boolean raw = (SqlTypes.isNumeric(c.dataType()) && SqlLiterals.isNumber(d.value()))
                        || (c.dataType().equals("BIT") && BIT_LITERAL.matcher(d.value()).matches());
                yield " DEFAULT " + (raw ? d.value().trim() : SqlLiterals.string(d.value()));
            }
        };
    }

    /** {@code PRIMARY KEY (…)}, {@code UNIQUE INDEX `n` (…)}, {@code INDEX `n` (…)}. */
    public static String indexClause(IndexDef index) {
        String cols = SqlIdentifiers.columnList(index.columns());
        return switch (index.kind()) {
            case PRIMARY -> "PRIMARY KEY " + cols;
            case UNIQUE -> "UNIQUE INDEX " + SqlIdentifiers.quote(index.name()) + " " + cols;
            case INDEX -> "INDEX " + SqlIdentifiers.quote(index.name()) + " " + cols;
        };
    }

    /**
     * {@code CONSTRAINT `n` FOREIGN KEY (…) REFERENCES `t` (…) ON DELETE … ON UPDATE …}: le due azioni sono sempre
     * scritte. La tabella riferita è qualificata solo se sta in un catalogo diverso da {@code tableCatalog}.
     */
    public static String foreignKeyClause(ForeignKeyDef fk, String tableCatalog) {
        boolean otherCatalog = fk.refCatalog() != null && !fk.refCatalog().equalsIgnoreCase(tableCatalog);
        String ref = otherCatalog ? SqlIdentifiers.qualified(fk.refCatalog(), fk.refTable())
                : SqlIdentifiers.quote(fk.refTable());
        return (fk.name() == null ? "" : "CONSTRAINT " + SqlIdentifiers.quote(fk.name()) + " ")
                + "FOREIGN KEY " + SqlIdentifiers.columnList(fk.columns())
                + " REFERENCES " + ref + " " + SqlIdentifiers.columnList(fk.refColumns())
                + " ON DELETE " + fk.onDelete().sql() + " ON UPDATE " + fk.onUpdate().sql();
    }

    private static List<String> optionClauses(TableDef original, TableDef edited) {
        List<String> out = new ArrayList<>();
        if (edited.engine() != null && !edited.engine().equalsIgnoreCase(original.engine())) {
            out.add("ENGINE=" + edited.engine());
        }
        boolean charsetChanged = edited.charset() != null && !edited.charset().equalsIgnoreCase(original.charset());
        boolean collationChanged =
                edited.collation() != null && !edited.collation().equalsIgnoreCase(original.collation());
        if (charsetChanged) {
            out.add("DEFAULT CHARSET=" + edited.charset()
                    + (edited.collation() != null ? " COLLATE=" + edited.collation() : ""));
        } else if (collationChanged) {
            out.add("COLLATE=" + edited.collation());
        }
        if (edited.autoIncrementStart() != null
                && !edited.autoIncrementStart().equals(original.autoIncrementStart())) {
            out.add("AUTO_INCREMENT=" + edited.autoIncrementStart());
        }
        if (!edited.comment().equals(original.comment())) {
            out.add("COMMENT=" + SqlLiterals.string(edited.comment()));
        }
        return out;
    }

    private static String alter(String target, List<String> clauses) {
        if (clauses.size() == 1) {
            return "ALTER TABLE " + target + " " + clauses.get(0);
        }
        return "ALTER TABLE " + target + "\n  " + String.join(",\n  ", clauses);
    }

    // ================================================================ confronto

    /** Per ogni colonna modificata, l'indice dell'originale abbinata (-1 = nuova): prima per posizione, poi per nome. */
    private static int[] matchColumns(List<ColumnDef> oc, List<ColumnDef> ec) {
        int[] match = new int[ec.size()];
        Arrays.fill(match, -1);
        boolean[] used = new boolean[oc.size()];
        for (int e = 0; e < ec.size(); e++) {
            int pos = ec.get(e).ordinalPosition();
            for (int o = 0; pos > 0 && o < oc.size(); o++) {
                if (!used[o] && oc.get(o).ordinalPosition() == pos) {
                    match[e] = o;
                    used[o] = true;
                    break;
                }
            }
        }
        for (int e = 0; e < ec.size(); e++) {
            for (int o = 0; match[e] < 0 && o < oc.size(); o++) {
                if (!used[o] && oc.get(o).name().equalsIgnoreCase(ec.get(e).name())) {
                    match[e] = o;
                    used[o] = true;
                }
            }
        }
        return match;
    }

    /** {@code FIRST} / {@code AFTER} solo se dopo la colonna nuova ce n'è almeno una già esistente. */
    private static String position(List<ColumnDef> ec, int[] match, int e) {
        boolean existingAfter = false;
        for (int k = e + 1; k < ec.size(); k++) {
            existingAfter |= match[k] >= 0;
        }
        if (!existingAfter) {
            return "";
        }
        return e == 0 ? " FIRST" : " AFTER " + SqlIdentifiers.quote(ec.get(e - 1).name());
    }

    private static boolean sameDefinition(ColumnDef o, ColumnDef e, TableDef original) {
        return SqlTypes.sameType(o, e)
                && o.zerofill() == e.zerofill()
                && o.nullable() == e.nullable()
                && o.autoIncrement() == e.autoIncrement()
                && o.comment().equals(e.comment())
                && sameDefault(o, e)
                && sameExpression(o.onUpdate(), e.onUpdate())
                && (!SqlTypes.isText(e.dataType())
                        || (sameOrInherited(o.charset(), e.charset(), original.charset())
                        && sameOrInherited(o.collation(), e.collation(), original.collation())));
    }

    /** Charset/collation nulli = ereditati dalla tabella com'è sul server. */
    private static boolean sameOrInherited(String a, String b, String tableDefault) {
        String left = a == null ? tableDefault : a;
        String right = b == null ? tableDefault : b;
        return left == null || right == null || left.equalsIgnoreCase(right);
    }

    private static boolean sameDefault(ColumnDef o, ColumnDef e) {
        ColumnDefault a = o.defaultValue();
        ColumnDefault b = e.defaultValue();
        boolean absentA = a.kind() == ColumnDefault.Kind.NONE || a.kind() == ColumnDefault.Kind.NULL;
        boolean absentB = b.kind() == ColumnDefault.Kind.NONE || b.kind() == ColumnDefault.Kind.NULL;
        if (absentA || absentB) {
            return absentA && absentB;
        }
        if (a.kind() != b.kind()) {
            return false;
        }
        if (a.kind() == ColumnDefault.Kind.EXPRESSION) {
            return sameExpression(a.value(), b.value());
        }
        if (a.value().equals(b.value())) {
            return true;
        }
        if (SqlTypes.isNumeric(e.dataType()) && SqlLiterals.isNumber(a.value()) && SqlLiterals.isNumber(b.value())) {
            return new BigDecimal(a.value().trim()).compareTo(new BigDecimal(b.value().trim())) == 0;
        }
        return false;
    }

    private static boolean sameExpression(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return normalizeExpression(a).equalsIgnoreCase(normalizeExpression(b));
    }

    /**
     * Toglie le parentesi esterne e riconduce {@code current_timestamp()}, {@code NOW()}… a {@code CURRENT_TIMESTAMP}
     * (con la precisione, se c'è): MariaDB e MySQL riportano la stessa espressione in forme diverse.
     */
    static String normalizeExpression(String expression) {
        String s = expression.trim();
        while (s.length() >= 2 && s.charAt(0) == '(' && closingOf(s, 0) == s.length() - 1) {
            s = s.substring(1, s.length() - 1).trim();
        }
        Matcher m = NOW_FUNCTION.matcher(s.toUpperCase(Locale.ROOT));
        if (m.matches()) {
            String precision = m.group(3);
            boolean withPrecision = precision != null && !precision.isEmpty() && !precision.equals("0");
            return "CURRENT_TIMESTAMP" + (withPrecision ? "(" + precision + ")" : "");
        }
        return s;
    }

    private static int closingOf(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                i = Math.max(i, skipString(s, i) - 1);
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int skipString(String s, int i) {
        i++;
        while (i < s.length()) {
            if (s.charAt(i) == '\\') {
                i += 2;
            } else if (s.charAt(i) == '\'') {
                return i + 1;
            } else {
                i++;
            }
        }
        return s.length();
    }

    private static IndexDef findIndex(List<IndexDef> indexes, IndexDef wanted) {
        for (IndexDef i : indexes) {
            if (wanted.isPrimary() ? i.isPrimary() : !i.isPrimary() && i.name().equalsIgnoreCase(wanted.name())) {
                return i;
            }
        }
        return null;
    }

    private static boolean sameIndex(IndexDef a, IndexDef b) {
        return a.kind() == b.kind() && sameNames(a.columns(), b.columns());
    }

    private static boolean sameNames(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equalsIgnoreCase(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> mapNames(List<String> names, Map<String, String> renames) {
        return names.stream().map(n -> renames.getOrDefault(lower(n), n)).toList();
    }

    /** Porta una FK ai nomi del modello modificato: colonne rinominate e, se autoreferenziale, tabella rinominata. */
    private static ForeignKeyDef normalizeFk(ForeignKeyDef fk, TableDef original, TableDef edited,
            Map<String, String> renames, boolean fromOriginal) {
        boolean selfCatalog = fk.refCatalog() == null || original.catalog() == null
                || fk.refCatalog().equalsIgnoreCase(original.catalog());
        boolean self = selfCatalog && (fk.refTable().equalsIgnoreCase(original.name())
                || (!fromOriginal && fk.refTable().equalsIgnoreCase(edited.name())));
        List<String> columns = fromOriginal ? mapNames(fk.columns(), renames) : fk.columns();
        if (!self) {
            return new ForeignKeyDef(fk.name(), columns, fk.refCatalog(), fk.refTable(), fk.refColumns(),
                    fk.onDelete(), fk.onUpdate());
        }
        List<String> refColumns = fromOriginal ? mapNames(fk.refColumns(), renames) : fk.refColumns();
        return new ForeignKeyDef(fk.name(), columns, fk.refCatalog(), edited.name(), refColumns, fk.onDelete(),
                fk.onUpdate());
    }

    /** L'originale con lo stesso nome; per una FK senza nome, l'originale con la stessa struttura. */
    private static int findFk(List<ForeignKeyDef> originals, ForeignKeyDef wanted, boolean[] taken, String catalog) {
        for (int o = 0; o < originals.size(); o++) {
            if (taken[o]) {
                continue;
            }
            ForeignKeyDef candidate = originals.get(o);
            if (wanted.name() == null ? sameFkStructure(candidate, wanted, catalog)
                    : wanted.name().equalsIgnoreCase(candidate.name())) {
                return o;
            }
        }
        return -1;
    }

    private static boolean sameFk(ForeignKeyDef a, ForeignKeyDef b, String catalog) {
        return sameFkStructure(a, b, catalog);
    }

    private static boolean sameFkStructure(ForeignKeyDef a, ForeignKeyDef b, String catalog) {
        String catA = a.refCatalog() == null ? catalog : a.refCatalog();
        String catB = b.refCatalog() == null ? catalog : b.refCatalog();
        return sameNames(a.columns(), b.columns())
                && (catA == null || catB == null || catA.equalsIgnoreCase(catB))
                && a.refTable().equalsIgnoreCase(b.refTable())
                && sameNames(a.refColumns(), b.refColumns())
                && a.onDelete() == b.onDelete()
                && a.onUpdate() == b.onUpdate();
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
