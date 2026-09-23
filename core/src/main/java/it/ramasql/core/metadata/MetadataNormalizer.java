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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dalle righe di {@code information_schema} (e dal testo di {@code SHOW CREATE TABLE}) ai record del modello,
 * con le <b>differenze tra MariaDB e MySQL normalizzate</b>: lo stesso schema letto dai due server produce
 * {@link TableDef} uguali. Puro: nessun accesso al server, collaudabile con righe simulate.
 *
 * <h2>Normalizzazioni</h2>
 * <ul>
 *   <li><b>Larghezza degli interi</b>: MariaDB riporta {@code int(11)}, {@code int(10) unsigned}, {@code bigint(20)},
 *       MySQL ≥ 8.0.19 {@code int}, {@code int unsigned}: la larghezza di visualizzazione si toglie, tranne
 *       {@code TINYINT(1)} (booleano, riportato uguale dai due) e le colonne {@code ZEROFILL}, che restano tali
 *       ({@link ColumnDef#zerofill()}). {@code YEAR(4)} → {@code YEAR}.</li>
 *   <li><b>Default</b> ({@code COLUMN_DEFAULT}): MariaDB ≥ 10.2.7 lo scrive come SQL ({@code 'IT'}, {@code NULL},
 *       {@code current_timestamp()}), MySQL come valore nudo ({@code IT}, SQL NULL, {@code CURRENT_TIMESTAMP} con
 *       {@code DEFAULT_GENERATED} in {@code EXTRA}). Esito comune: {@link ColumnDefault} LITERAL senza apici, EXPRESSION
 *       normalizzata, NULL, NONE. «Nessun default» su una colonna che ammette NULL equivale per il server a
 *       {@code DEFAULT NULL} e i due server non li distinguono: si legge sempre {@link ColumnDefault#NULL_VALUE};
 *       su una colonna NOT NULL si legge {@link ColumnDefault#NONE}.</li>
 *   <li><b>Espressioni</b>: {@code current_timestamp()}, {@code now()}, {@code CURRENT_TIMESTAMP(0)} →
 *       {@code CURRENT_TIMESTAMP}; {@code current_timestamp(3)} → {@code CURRENT_TIMESTAMP(3)}; parentesi esterne tolte
 *       ({@code (1 + 1)} → {@code 1 + 1}). Vale anche per {@code ON UPDATE}.</li>
 *   <li><b>Charset/collation di colonna</b> uguali a quelli della tabella → {@code null} («ereditati»), come li
 *       intende {@link ColumnDef}.</li>
 *   <li><b>JSON</b>: MariaDB lo conserva come {@code LONGTEXT} con {@code CHECK (json_valid(`c`))} → tipo {@code JSON}
 *       senza charset, come MySQL.</li>
 *   <li><b>Indici</b>: PRIMARY per primo, poi per nome; <b>chiavi esterne</b> per nome; catalogo riferito
 *       {@code null} se è quello della tabella.</li>
 *   <li><b>Elementi avanzati</b> (CHECK, colonne generate, partizioni, indici FULLTEXT/SPATIAL, su prefisso, su
 *       espressione o con colonne in ordine {@code DESC}, chiavi esterne con {@code SET DEFAULT}): conservati come righe di {@code SHOW CREATE TABLE} in {@link TableDef#advancedElements()}, nel
 *       testo del server (non normalizzato: i due server li scrivono in modo diverso).</li>
 *   <li>{@code AUTO_INCREMENT=} letto da {@code SHOW CREATE TABLE}, esatto su entrambi i server
 *       (su MySQL 8 {@code information_schema.TABLES.AUTO_INCREMENT} può essere vecchio di un giorno).</li>
 * </ul>
 */
final class MetadataNormalizer {

    // colonne delle righe prodotte da MetadataQueries (stesso ordine delle SELECT)
    static final int T_NAME = 0;
    static final int T_TYPE = 1;
    static final int T_ENGINE = 2;
    static final int T_COLLATION = 3;
    static final int T_COMMENT = 4;

    static final int C_NAME = 0;
    static final int C_TYPE = 1;
    static final int C_NULLABLE = 2;
    static final int C_DEFAULT = 3;
    static final int C_EXTRA = 4;
    static final int C_CHARSET = 5;
    static final int C_COLLATION = 6;
    static final int C_COMMENT = 7;
    static final int C_POSITION = 8;

    static final int I_NAME = 0;
    static final int I_NON_UNIQUE = 1;
    static final int I_SEQ = 2;
    static final int I_COLUMN = 3;
    static final int I_SUB_PART = 4;
    static final int I_TYPE = 5;
    /** {@code A} crescente, {@code D} decrescente (MySQL 8, MariaDB ≥ 10.8), {@code NULL} per FULLTEXT/SPATIAL. */
    static final int I_COLLATION = 6;

    static final int F_NAME = 0;
    static final int F_COLUMN = 1;
    static final int F_REF_SCHEMA = 2;
    static final int F_REF_TABLE = 3;
    static final int F_REF_COLUMN = 4;
    static final int F_UPDATE = 5;
    static final int F_DELETE = 6;

    private static final Set<String> INTEGER_TYPES = Set.of("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT");
    private static final Set<String> TEMPORAL_TYPES = Set.of("TIMESTAMP", "DATETIME", "DATE", "TIME");
    private static final Pattern NOW_FUNCTION = Pattern.compile(
            "(CURRENT_TIMESTAMP|NOW|LOCALTIME|LOCALTIMESTAMP)(\\s*\\(\\s*(\\d*)\\s*\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern BIT_OR_HEX = Pattern.compile("([bB]'[01]*'|[xX]'[0-9a-fA-F]*'|0x[0-9a-fA-F]+)");
    private static final Pattern ON_UPDATE = Pattern.compile("(?i)\\bon update\\s+(\\S.*)$");
    private static final Pattern GENERATED = Pattern.compile("(?i)\\b(VIRTUAL|STORED|PERSISTENT)\\b");
    private static final Pattern AUTO_INCREMENT = Pattern.compile("\\bAUTO_INCREMENT=(\\d+)");

    private MetadataNormalizer() {
    }

    // ================================================================ tabella completa

    /**
     * Monta la tabella dalle righe lette.
     *
     * @param catalog        catalogo della tabella
     * @param tableRow       riga di {@link MetadataQueries#TABLE}
     * @param columnRows     righe di {@link MetadataQueries#COLUMNS}
     * @param indexRows      righe di {@link MetadataQueries#INDEXES}
     * @param fkRows         righe di {@link MetadataQueries#FOREIGN_KEYS}
     * @param showCreate     testo di {@code SHOW CREATE TABLE} ({@code null} se non disponibile)
     * @param quotedDefaults il server scrive i default come SQL (MariaDB ≥ 10.2.7)
     */
    static TableDef table(String catalog, String[] tableRow, List<String[]> columnRows, List<String[]> indexRows,
            List<String[]> fkRows, String showCreate, boolean quotedDefaults) {
        String collation = blankToNull(tableRow[T_COLLATION]);
        String charset = charsetOf(collation);
        ShowCreate ddl = ShowCreate.parse(showCreate);
        List<String> advanced = new ArrayList<>();

        List<ColumnDef> columns = new ArrayList<>();
        for (String[] row : columnRows) {
            String line = ddl.columnLines.get(lower(row[C_NAME]));
            ColumnDef c = column(row, quotedDefaults, charset, collation, line);
            columns.add(c);
            if (line != null && (c.generated() || hasColumnCheck(line, c))) {
                advanced.add(line);
            }
        }

        // indici: raggruppati per nome nell'ordine delle colonne
        Map<String, List<String[]>> byIndex = new LinkedHashMap<>();
        for (String[] row : indexRows) {
            byIndex.computeIfAbsent(row[I_NAME], k -> new ArrayList<>()).add(row);
        }
        List<IndexDef> indexes = new ArrayList<>();
        for (Map.Entry<String, List<String[]>> e : byIndex.entrySet()) {
            List<String[]> rows = new ArrayList<>(e.getValue());
            rows.sort(Comparator.comparingInt(r -> Integer.parseInt(r[I_SEQ].trim())));
            boolean special = false;
            List<String> cols = new ArrayList<>();
            for (String[] r : rows) {
                String type = r[I_TYPE] == null ? "" : r[I_TYPE].toUpperCase(Locale.ROOT);
                boolean descending = r.length > I_COLLATION && "D".equalsIgnoreCase(r[I_COLLATION]);
                if (r[I_COLUMN] == null || r[I_SUB_PART] != null || type.equals("FULLTEXT") || type.equals("SPATIAL")
                        || descending) {
                    special = true;   // v1 non li modifica: restano elementi avanzati, intatti (vedi IndexFeature)
                }
                cols.add(r[I_COLUMN]);
            }
            String name = e.getKey();
            if (special) {
                String line = ddl.keyLines.get(lower(name));
                advanced.add(line != null ? line : "KEY `" + name + "`");
                continue;
            }
            IndexKind kind = name.equalsIgnoreCase(IndexDef.PRIMARY_NAME) ? IndexKind.PRIMARY
                    : "0".equals(rows.get(0)[I_NON_UNIQUE].trim()) ? IndexKind.UNIQUE : IndexKind.INDEX;
            indexes.add(new IndexDef(name, kind, cols));
        }
        indexes.sort(Comparator.comparing((IndexDef i) -> !i.isPrimary())
                .thenComparing(i -> i.name().toLowerCase(Locale.ROOT)));

        // chiavi esterne
        Map<String, List<String[]>> byFk = new LinkedHashMap<>();
        for (String[] row : fkRows) {
            byFk.computeIfAbsent(row[F_NAME], k -> new ArrayList<>()).add(row);
        }
        List<ForeignKeyDef> fks = new ArrayList<>();
        for (Map.Entry<String, List<String[]>> e : byFk.entrySet()) {
            List<String[]> rows = e.getValue();
            String[] first = rows.get(0);
            String refCatalog = first[F_REF_SCHEMA] == null || first[F_REF_SCHEMA].equalsIgnoreCase(catalog)
                    ? null : first[F_REF_SCHEMA];
            java.util.Optional<FkAction> onDelete = FkAction.parse(first[F_DELETE]);
            java.util.Optional<FkAction> onUpdate = FkAction.parse(first[F_UPDATE]);
            if (onDelete.isEmpty() || onUpdate.isEmpty()) {
                // SET DEFAULT (o altro che il client non genera): la chiave si conserva com'è, in sola lettura
                String line = ddl.fkLines.get(lower(e.getKey()));
                advanced.add(line != null ? line : "CONSTRAINT `" + e.getKey().replace("`", "``") + "` FOREIGN KEY"
                        + " ON DELETE " + first[F_DELETE] + " ON UPDATE " + first[F_UPDATE]);
                continue;
            }
            fks.add(new ForeignKeyDef(e.getKey(), rows.stream().map(r -> r[F_COLUMN]).toList(), refCatalog,
                    first[F_REF_TABLE], rows.stream().map(r -> r[F_REF_COLUMN]).toList(),
                    onDelete.get(), onUpdate.get()));
        }
        fks.sort(Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));

        advanced.addAll(ddl.checkLines);
        if (ddl.partitions != null) {
            advanced.add(ddl.partitions);
        }
        String comment = tableRow[T_COMMENT] == null ? "" : tableRow[T_COMMENT];
        return new TableDef(catalog, tableRow[T_NAME], blankToNull(tableRow[T_ENGINE]), charset, collation, comment,
                ddl.autoIncrement, columns, indexes, fks, advanced);
    }

    // ================================================================ colonne

    /** Tipo scomposto da {@code COLUMN_TYPE}. */
    record ParsedType(String dataType, String typeArgs, boolean unsigned, boolean zerofill) {
    }

    /**
     * {@code int(10) unsigned} → INT, senza argomenti, UNSIGNED; {@code enum('a','b''c')} → ENUM, {@code 'a','b''c'};
     * {@code decimal(6,2)} → DECIMAL, {@code 6,2}.
     */
    static ParsedType parseColumnType(String columnType) {
        String s = columnType.trim();
        int open = s.indexOf('(');
        int space = s.indexOf(' ');
        String base;
        String args = null;
        String rest;
        if (open > 0 && (space < 0 || open < space)) {
            int close = closingParen(s, open);
            base = s.substring(0, open);
            args = s.substring(open + 1, close < 0 ? s.length() : close);
            rest = close < 0 ? "" : s.substring(close + 1);
        } else {
            base = space < 0 ? s : s.substring(0, space);
            rest = space < 0 ? "" : s.substring(space);
        }
        String lowerRest = rest.toLowerCase(Locale.ROOT);
        boolean unsigned = lowerRest.contains("unsigned");
        boolean zerofill = lowerRest.contains("zerofill");
        String dataType = base.trim().toUpperCase(Locale.ROOT);
        if (args != null) {
            args = args.trim();
            boolean keep = zerofill || (dataType.equals("TINYINT") && args.equals("1"));
            if ((INTEGER_TYPES.contains(dataType) && !keep) || (dataType.equals("YEAR") && args.equals("4"))) {
                args = null;
            }
        }
        return new ParsedType(dataType, args, unsigned, zerofill);
    }

    /**
     * Una colonna dalla riga di {@code information_schema.COLUMNS}.
     *
     * @param createLine riga della colonna in {@code SHOW CREATE TABLE} ({@code null} se non disponibile)
     */
    static ColumnDef column(String[] row, boolean quotedDefaults, String tableCharset, String tableCollation,
            String createLine) {
        ParsedType type = parseColumnType(row[C_TYPE]);
        String extra = row[C_EXTRA] == null ? "" : row[C_EXTRA];
        boolean nullable = "YES".equalsIgnoreCase(row[C_NULLABLE]);
        boolean autoIncrement = extra.toLowerCase(Locale.ROOT).contains("auto_increment");
        boolean generated = isGenerated(extra);
        String dataType = type.dataType();
        String typeArgs = type.typeArgs();
        String charset = blankToNull(row[C_CHARSET]);
        String collation = blankToNull(row[C_COLLATION]);
        if (dataType.equals("LONGTEXT") && createLine != null && isMariaDbJson(createLine, row[C_NAME])) {
            dataType = "JSON";
            typeArgs = null;
        }
        if (dataType.equals("JSON")) {
            charset = null;
            collation = null;
        }
        if (charset != null && charset.equalsIgnoreCase(tableCharset)) {
            charset = null;
        }
        if (collation != null && collation.equalsIgnoreCase(tableCollation)) {
            collation = null;
        }
        ColumnDefault def = autoIncrement || generated ? ColumnDefault.NONE
                : defaultValue(row[C_DEFAULT], extra, nullable, quotedDefaults, dataType);
        int position = row[C_POSITION] == null ? 0 : Integer.parseInt(row[C_POSITION].trim());
        return new ColumnDef(row[C_NAME], dataType, typeArgs, type.unsigned(), nullable, def, autoIncrement,
                row[C_COMMENT], charset, collation, position, onUpdate(extra), generated, type.zerofill());
    }

    /** Il default nella forma comune ai due server (vedi la documentazione della classe). */
    static ColumnDefault defaultValue(String raw, String extra, boolean nullable, boolean quotedDefaults,
            String dataType) {
        if (raw == null) {
            return nullable ? ColumnDefault.NULL_VALUE : ColumnDefault.NONE;
        }
        String upperType = dataType.toUpperCase(Locale.ROOT);
        if (quotedDefaults) {
            if (raw.equals("NULL")) {
                return ColumnDefault.NULL_VALUE;
            }
            if (raw.length() >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
                return ColumnDefault.literal(unquote(raw));
            }
            if (NUMBER.matcher(raw.trim()).matches() || BIT_OR_HEX.matcher(raw.trim()).matches()) {
                return ColumnDefault.literal(raw.trim());
            }
            return ColumnDefault.expression(normalizeExpression(raw));
        }
        if (extra != null && extra.toUpperCase(Locale.ROOT).contains("DEFAULT_GENERATED")) {
            return ColumnDefault.expression(normalizeExpression(raw));
        }
        if (TEMPORAL_TYPES.contains(upperType) && NOW_FUNCTION.matcher(raw.trim()).matches()) {
            return ColumnDefault.expression(normalizeExpression(raw));   // MySQL 5.7: niente DEFAULT_GENERATED
        }
        return ColumnDefault.literal(raw);
    }

    /**
     * Parentesi esterne tolte; {@code now()}, {@code current_timestamp()}, {@code CURRENT_TIMESTAMP(0)} →
     * {@code CURRENT_TIMESTAMP}, con la precisione se maggiore di zero.
     */
    static String normalizeExpression(String expression) {
        String s = expression.trim();
        while (s.length() >= 2 && s.charAt(0) == '(' && closingParen(s, 0) == s.length() - 1) {
            s = s.substring(1, s.length() - 1).trim();
        }
        Matcher m = NOW_FUNCTION.matcher(s);
        if (m.matches()) {
            String precision = m.group(3);
            boolean withPrecision = precision != null && !precision.isEmpty() && !precision.equals("0");
            return "CURRENT_TIMESTAMP" + (withPrecision ? "(" + precision + ")" : "");
        }
        return s;
    }

    /** {@code ON UPDATE} da {@code EXTRA}: {@code on update current_timestamp()} → {@code CURRENT_TIMESTAMP}. */
    static String onUpdate(String extra) {
        if (extra == null) {
            return null;
        }
        Matcher m = ON_UPDATE.matcher(extra.trim());
        return m.find() ? normalizeExpression(m.group(1)) : null;
    }

    /** {@code VIRTUAL GENERATED}, {@code STORED GENERATED} (e {@code PERSISTENT} di MariaDB); non {@code DEFAULT_GENERATED}. */
    static boolean isGenerated(String extra) {
        return extra != null && GENERATED.matcher(extra).find();
    }

    /** Charset dalla collation: la parte prima del primo «_» ({@code utf8mb4_unicode_ci} → {@code utf8mb4}). */
    static String charsetOf(String collation) {
        if (collation == null || collation.isBlank()) {
            return null;
        }
        int u = collation.indexOf('_');
        return u < 0 ? collation.trim() : collation.substring(0, u);
    }

    /** Letterale SQL tra apici → valore: {@code 'l''ora'} → {@code l'ora}; sequenze con backslash risolte. */
    static String unquote(String literal) {
        String body = literal.substring(1, literal.length() - 1);
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'' && i + 1 < body.length() && body.charAt(i + 1) == '\'') {
                out.append('\'');
                i++;
            } else if (ch == '\\' && i + 1 < body.length()) {
                char next = body.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '0' -> '\0';
                    case 'Z' -> '';
                    default -> next;
                });
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static boolean isMariaDbJson(String createLine, String columnName) {
        String quoted = "`" + columnName.replace("`", "``") + "`";
        return createLine.replace(" ", "").toLowerCase(Locale.ROOT)
                .contains(("CHECK(json_valid(" + quoted + "))").toLowerCase(Locale.ROOT));
    }

    /** CHECK scritto nella riga della colonna (MariaDB), escluso quello automatico del tipo JSON. */
    private static boolean hasColumnCheck(String createLine, ColumnDef c) {
        if (c.dataType().equals("JSON") && isMariaDbJson(createLine, c.name())) {
            return false;
        }
        return outsideQuotes(createLine).toUpperCase(Locale.ROOT).contains(" CHECK (");
    }

    // ================================================================ SHOW CREATE TABLE

    /** Parti di {@code SHOW CREATE TABLE} che {@code information_schema} non dice (o non dice bene). */
    static final class ShowCreate {
        final Map<String, String> columnLines = new HashMap<>();
        final Map<String, String> keyLines = new HashMap<>();
        final Map<String, String> fkLines = new HashMap<>();
        final List<String> checkLines = new ArrayList<>();
        String partitions;
        Long autoIncrement;

        static ShowCreate parse(String ddl) {
            ShowCreate out = new ShowCreate();
            if (ddl == null || ddl.isBlank()) {
                return out;
            }
            String[] lines = ddl.replace("\r\n", "\n").split("\n");
            int i = 1;
            for (; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith(")")) {
                    Matcher m = AUTO_INCREMENT.matcher(outsideQuotes(line));
                    if (m.find()) {
                        out.autoIncrement = Long.parseLong(m.group(1));
                    }
                    break;
                }
                if (line.endsWith(",")) {
                    line = line.substring(0, line.length() - 1);
                }
                String upper = line.toUpperCase(Locale.ROOT);
                if (line.startsWith("`")) {
                    out.columnLines.put(lower(identifierAt(line, 0)), line);
                } else if (upper.startsWith("PRIMARY KEY")) {
                    out.keyLines.put("primary", line);
                } else if (upper.startsWith("CONSTRAINT") && outsideQuotes(upper).contains(" CHECK ")) {
                    out.checkLines.add(line);
                } else if (upper.startsWith("CHECK")) {
                    out.checkLines.add(line);
                } else if (upper.startsWith("CONSTRAINT") && line.indexOf('`') >= 0) {
                    out.fkLines.put(lower(identifierAt(line, line.indexOf('`'))), line);
                } else if (!upper.startsWith("CONSTRAINT")) {
                    int tick = line.indexOf('`');
                    if (tick >= 0) {
                        out.keyLines.put(lower(identifierAt(line, tick)), line);   // KEY, UNIQUE KEY, FULLTEXT KEY…
                    }
                }
            }
            StringBuilder tail = new StringBuilder();
            for (i = i + 1; i < lines.length; i++) {
                tail.append(lines[i].trim()).append('\n');
            }
            String rest = tail.toString().trim();
            out.partitions = rest.isEmpty() ? null : rest;
            return out;
        }
    }

    /** Identificatore tra backtick che comincia in {@code start}. */
    private static String identifierAt(String s, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start + 1; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '`') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '`') {
                    out.append('`');
                    i++;
                } else {
                    break;
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /** Il testo con il contenuto di stringhe e identificatori sostituito da spazi (per cercare parole chiave). */
    private static String outsideQuotes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (quote == 0) {
                if (ch == '\'' || ch == '`' || ch == '"') {
                    quote = ch;
                }
                out.append(ch);
            } else if (ch == '\\' && quote != '`' && i + 1 < s.length()) {
                out.append("  ");
                i++;
            } else if (ch == quote) {
                if (i + 1 < s.length() && s.charAt(i + 1) == quote) {
                    out.append("  ");
                    i++;
                } else {
                    quote = 0;
                    out.append(ch);
                }
            } else {
                out.append(' ');
            }
        }
        return out.toString();
    }

    private static int closingParen(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (quote != 0) {
                if (ch == '\\' && quote != '`') {
                    i++;
                } else if (ch == quote) {
                    if (i + 1 < s.length() && s.charAt(i + 1) == quote) {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (ch == '\'' || ch == '`' || ch == '"') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
