/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.it.TestResults;

/**
 * T3.9 — test d'architettura: l'SQL si esegue solo in {@code SqlExecutor} (per l'utente) e nel canale interno
 * ({@code InternalQueries} per la sessione, {@code MetadataQueries} per i metadati). Due controlli:
 * <ol>
 *   <li><b>sorgenti</b> (come chiede la roadmap): nei {@code src/main} di core, app, model e sqleo-qb, fuori da commenti
 *       e stringhe, le chiamate {@code execute(…)}, {@code executeUpdate(}, {@code executeQuery(},
 *       {@code executeLargeUpdate(}, {@code executeLargeBatch(}, {@code executeBatch(}, {@code addBatch(} compaiono
 *       solo nei tre file ammessi. {@code execute()} <b>senza argomenti</b> non si conta nei sorgenti (è quello di
 *       {@code SwingWorker}): lo copre il secondo controllo;</li>
 *   <li><b>bytecode</b> (preciso: guarda il tipo su cui si chiama il metodo): nelle classi compilate degli stessi
 *       moduli, <b>ogni</b> riferimento a {@code java.sql.*} od {@code org.mariadb.*} — chiamate {@code invoke*},
 *       riferimenti a metodo nei bootstrap di {@code invokedynamic} ({@code Connection::commit} in una lambda),
 *       costanti {@code MethodHandle} — fuori dalle tre classi ammesse deve stare in un <b>elenco esplicito di sole
 *       letture</b> ({@code ResultSet.get*}, {@code next}, {@code getMetaData}, {@code DatabaseMetaData.*},
 *       {@code close}, {@code isClosed}, {@code getWarnings}…; l'apertura delle connessioni solo in {@code Session}).
 *       Tutto il resto è una violazione: {@code Connection.setAutoCommit/commit/rollback/setCatalog/abort/
 *       createStatement/prepareStatement/prepareCall}, {@code ResultSet.update…}, {@code insertRow}, {@code deleteRow},
 *       {@code Statement.execute*}, qualunque classe del driver. Un test a parte prova che il controllo vede i
 *       trucchi. (La riflessione non si vede nel bytecode: non la usa nessuno, e resterebbe comunque sotto gli occhi
 *       di chi rivede il codice.)</li>
 * </ol>
 * Le letture di {@code DatabaseMetaData} del query builder ereditato (sqleo-qb) sono elencate nell'evidenza: non
 * eseguono SQL scritto dal client (le query le compone il driver per leggere i metadati).
 */
@Tag("step3")
class T39ArchitetturaSqlTest {

    private static final List<String> MODULES = List.of("core", "app", "model", "sqleo-qb");
    private static final Set<String> ALLOWED_FILES = Set.of(
            "core/src/main/java/it/ramasql/core/exec/SqlExecutor.java",
            "core/src/main/java/it/ramasql/core/connection/InternalQueries.java",
            "core/src/main/java/it/ramasql/core/metadata/MetadataQueries.java");
    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "it/ramasql/core/exec/SqlExecutor",
            "it/ramasql/core/connection/InternalQueries",
            "it/ramasql/core/metadata/MetadataQueries");
    private static final Pattern CALL = Pattern.compile(
            "\\b(execute|executeUpdate|executeQuery|executeLargeUpdate|executeLargeBatch|executeBatch|addBatch)\\s*\\(");
    private static final Set<String> STATEMENT_TYPES =
            Set.of("java/sql/Statement", "java/sql/PreparedStatement", "java/sql/CallableStatement");
    private static final Set<String> EXECUTING = Set.of("execute", "executeQuery", "executeUpdate",
            "executeLargeUpdate", "executeBatch", "executeLargeBatch", "addBatch");
    private static final Set<String> CREATING = Set.of("createStatement", "prepareStatement", "prepareCall");

    @Test
    void t39_nelSorgenteSoloSqlExecutorEIlCanaleInterno() throws IOException {
        Path root = TestResults.projectRoot();
        Map<String, List<String>> found = new TreeMap<>();
        int files = 0;
        for (String module : MODULES) {
            Path src = root.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(src), "manca " + src);
            List<Path> javaFiles;
            try (Stream<Path> s = Files.walk(src)) {
                javaFiles = s.filter(p -> p.toString().endsWith(".java")).toList();
            }
            assertTrue(!javaFiles.isEmpty(), "nessun sorgente in " + src);
            for (Path file : javaFiles) {
                files++;
                String code = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
                Matcher m = CALL.matcher(code);
                while (m.find()) {
                    if (m.group(1).equals("execute") && nextNonBlank(code, m.end()) == ')') {
                        continue;   // execute() senza argomenti: SwingWorker (il bytecode controlla il resto)
                    }
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    found.computeIfAbsent(rel, k -> new ArrayList<>()).add(lineOf(code, m.start()) + ": " + m.group(1));
                }
            }
        }
        StringBuilder evidence = new StringBuilder("T3.9 — ricerca nei sorgenti (" + files + " file .java di "
                + MODULES + ", commenti e stringhe esclusi)\n");
        found.forEach((f, hits) -> evidence.append(f).append(" → ").append(hits).append('\n'));
        TestResults.write("step3", "T3.9-sorgenti.txt", evidence.toString());

        assertEquals(ALLOWED_FILES, found.keySet(), "chiamate che eseguono SQL fuori dai punti ammessi:\n" + evidence);
    }

    @Test
    void t39_nelBytecodeSoloSqlExecutorEIlCanaleInterno() throws IOException {
        Path root = TestResults.projectRoot();
        Map<String, Set<String>> executing = new TreeMap<>();
        Map<String, Set<String>> allowedReads = new TreeMap<>();
        Map<String, Set<String>> violations = new TreeMap<>();
        Map<String, Set<String>> metadataReads = new TreeMap<>();
        int classes = 0;
        int jdbcCalls = 0;
        for (String module : MODULES) {
            Path dir = root.resolve(module).resolve("target/classes");
            assertTrue(Files.isDirectory(dir), "classi non compilate: " + dir + " (compilare prima il modulo)");
            List<Path> classFiles;
            try (Stream<Path> s = Files.walk(dir)) {
                classFiles = s.filter(p -> p.toString().endsWith(".class")).toList();
            }
            assertTrue(!classFiles.isEmpty(), "nessuna classe in " + dir);
            for (Path file : classFiles) {
                classes++;
                ClassModel cm = ClassFile.of().parse(Files.readAllBytes(file));
                String owner = cm.thisClass().asInternalName();
                String top = topLevel(owner);
                for (MethodModel method : cm.methods()) {
                    CodeModel code = method.code().orElse(null);
                    if (code == null) {
                        continue;
                    }
                    for (CodeElement e : code) {
                        for (String[] call : jdbcTargets(e)) {
                            String target = call[0];
                            String name = call[1];
                            if (!isJdbc(target)) {
                                continue;
                            }
                            jdbcCalls++;
                            String label = target.substring(target.lastIndexOf('/') + 1) + "." + name + call[2];
                            boolean runs = STATEMENT_TYPES.contains(target) && EXECUTING.contains(name);
                            boolean creates = target.equals("java/sql/Connection") && CREATING.contains(name);
                            if (runs || creates) {
                                executing.computeIfAbsent(top, k -> new TreeSet<>()).add(label);
                            }
                            if (target.equals("java/sql/DatabaseMetaData")) {
                                metadataReads.computeIfAbsent(module + ": " + owner, k -> new TreeSet<>()).add(name);
                            }
                            if (ALLOWED_CLASSES.contains(top)) {
                                continue;   // i tre punti ammessi possono tutto (e sono gli unici)
                            }
                            if (isReadOnly(top, target, name)) {
                                allowedReads.computeIfAbsent(module + ": " + top, k -> new TreeSet<>()).add(label);
                            } else {
                                violations.computeIfAbsent(module + ": " + top, k -> new TreeSet<>()).add(label);
                            }
                        }
                    }
                }
            }
        }
        StringBuilder evidence = new StringBuilder("T3.9 — controllo del bytecode (" + classes + " classi di "
                + MODULES + "; " + jdbcCalls + " riferimenti a java.sql.* / org.mariadb.*: chiamate invoke*, riferimenti"
                + " a metodo dei bootstrap di invokedynamic, costanti MethodHandle)\n"
                + "Classi ammesse (possono eseguire SQL): " + ALLOWED_CLASSES + "\n"
                + "Chiamate che creano o eseguono istruzioni SQL:\n");
        executing.forEach((c, calls) -> evidence.append("  ").append(c).append(" → ").append(calls).append('\n'));
        evidence.append("Fuori dalle classi ammesse, riferimenti JDBC consentiti (solo letture, chiusure, valori, e"
                + " l'apertura delle connessioni in Session):\n");
        allowedReads.forEach((c, calls) -> evidence.append("  ").append(c).append(" → ").append(calls).append('\n'));
        evidence.append("Letture di java.sql.DatabaseMetaData (solo metadati, SQL composto dal driver):\n");
        metadataReads.forEach((c, calls) -> evidence.append("  ").append(c).append(" → ").append(calls).append('\n'));
        evidence.append("VIOLAZIONI (riferimenti JDBC fuori dall'elenco delle letture): ")
                .append(violations.isEmpty() ? "nessuna" : "").append('\n');
        violations.forEach((c, calls) -> evidence.append("  ").append(c).append(" → ").append(calls).append('\n'));
        TestResults.write("step3", "T3.9-bytecode.txt", evidence.toString());

        assertEquals(Map.of(), violations, "riferimenti JDBC non di sola lettura fuori dai punti ammessi:\n" + evidence);
        assertEquals(ALLOWED_CLASSES, executing.keySet(), "SQL eseguito fuori dai punti ammessi:\n" + evidence);
        assertTrue(metadataReads.keySet().stream().allMatch(k -> k.startsWith("sqleo-qb: com/sqleo/querybuilder/")),
                "DatabaseMetaData letto fuori dal query builder ereditato:\n" + evidence);
    }

    /**
     * Il controllo stesso deve vedere i trucchi: una chiamata diretta, un riferimento a metodo ({@code Connection::commit}
     * in una lambda, cioè un bootstrap di {@code invokedynamic}) e un aggiornamento di {@code ResultSet} sono
     * violazioni; le letture no. Si prova su una classe compilata apposta per il test (non fa parte del prodotto).
     */
    @Test
    void t39_ilControlloVedeAncheIRiferimentiAMetodoELeScrittureNascoste() throws IOException {
        Path self = TestResults.projectRoot().resolve("it-tests/target/test-classes")
                .resolve(Trucchi.class.getName().replace('.', '/') + ".class");
        ClassModel cm = ClassFile.of().parse(Files.readAllBytes(self));
        Set<String> seen = new TreeSet<>();
        Set<String> bad = new TreeSet<>();
        for (MethodModel method : cm.methods()) {
            CodeModel code = method.code().orElse(null);
            if (code == null) {
                continue;
            }
            for (CodeElement e : code) {
                for (String[] call : jdbcTargets(e)) {
                    if (isJdbc(call[0])) {
                        String label = call[0].substring(call[0].lastIndexOf('/') + 1) + "." + call[1] + call[2];
                        seen.add(label);
                        if (!isReadOnly("x", call[0], call[1])) {
                            bad.add(label);
                        }
                    }
                }
            }
        }
        assertEquals(Set.of("Connection.commit (riferimento a metodo)", "Connection.createStatement",
                "Connection.setAutoCommit", "ResultSet.updateString", "ResultSet.deleteRow",
                "Connection.rollback (riferimento a metodo)", "Connection.setCatalog", "Connection.abort"), bad,
                "visti: " + seen);
        assertTrue(seen.contains("ResultSet.getString") && seen.contains("ResultSet.next")
                && seen.contains("Connection.close (riferimento a metodo)"), seen.toString());
    }

    /** Codice «malizioso» su cui si prova il controllo: non viene mai eseguito. */
    @SuppressWarnings("unused")
    static final class Trucchi {
        private Trucchi() {
        }

        static void scritture(java.sql.Connection c, java.sql.ResultSet rs) throws java.sql.SQLException {
            c.setAutoCommit(false);
            c.createStatement();
            c.setCatalog("x");
            c.abort(Runnable::run);
            rs.updateString(1, "x");
            rs.deleteRow();
            java.util.function.Consumer<java.sql.Connection> commit = unchecked(java.sql.Connection::commit);
            java.util.function.Consumer<java.sql.Connection> rollback = unchecked(java.sql.Connection::rollback);
        }

        static void letture(java.sql.Connection c, java.sql.ResultSet rs) throws java.sql.SQLException {
            while (rs.next()) {
                rs.getString(1);
            }
            java.util.function.Consumer<java.sql.Connection> close = unchecked(java.sql.Connection::close);
        }

        interface SqlConsumer<T> {
            void accept(T t) throws java.sql.SQLException;
        }

        static <T> java.util.function.Consumer<T> unchecked(SqlConsumer<T> c) {
            return t -> {
                try {
                    c.accept(t);
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            };
        }
    }

    // ================================================================ elenco delle letture ammesse

    private static final Set<String> RESULT_SET_READS = Set.of("next", "previous", "first", "last", "beforeFirst",
            "afterLast", "absolute", "relative", "isFirst", "isLast", "isBeforeFirst", "isAfterLast", "wasNull",
            "findColumn", "close", "isClosed", "getWarnings", "clearWarnings", "getMetaData", "setFetchSize",
            "setFetchDirection", "unwrap", "isWrapperFor");
    private static final Set<String> CONNECTION_READS = Set.of("close", "isClosed", "isValid", "getMetaData",
            "getCatalog", "getSchema", "getAutoCommit", "isReadOnly", "getTransactionIsolation", "getWarnings",
            "clearWarnings", "getClientInfo", "getNetworkTimeout", "getHoldability", "unwrap", "isWrapperFor");
    private static final Set<String> STATEMENT_READS = Set.of("close", "isClosed", "getWarnings", "clearWarnings");
    private static final Set<String> LOB_READS = Set.of("length", "getBytes", "getBinaryStream", "getSubString",
            "getCharacterStream", "getAsciiStream", "position", "free", "getString", "getArray", "getBaseType",
            "getBaseTypeName");
    /** Valori ed eccezioni: nessuno di questi tipi parla con il server. */
    private static final Set<String> VALUE_TYPES = Set.of("java/sql/Date", "java/sql/Time", "java/sql/Timestamp",
            "java/sql/Types", "java/sql/JDBCType", "java/sql/SQLType", "java/sql/SQLWarning", "java/sql/DataTruncation",
            "java/sql/BatchUpdateException", "java/sql/DriverPropertyInfo");
    /** Aperture di connessione ammesse, solo nella sessione (che poi le passa ai tre punti ammessi). */
    private static final Map<String, Set<String>> CLASS_EXCEPTIONS = Map.of(
            "it/ramasql/core/connection/Session", Set.of("java/sql/Driver.connect"));

    private static boolean isJdbc(String internalName) {
        return internalName.startsWith("java/sql/") || internalName.startsWith("org/mariadb/");
    }

    /**
     * Il riferimento è di <b>sola lettura</b> (o una chiusura, o un tipo valore/eccezione)? Tutto ciò che non è
     * nell'elenco è una violazione: {@code Connection.setAutoCommit/commit/rollback/setCatalog/abort/createStatement/
     * prepareStatement/prepareCall}, {@code ResultSet.update* / insertRow / deleteRow}, {@code Statement.execute*},
     * qualunque classe del driver {@code org.mariadb.*}…
     */
    static boolean isReadOnly(String topClass, String target, String name) {
        if (CLASS_EXCEPTIONS.getOrDefault(topClass, Set.of()).contains(target + "." + name)) {
            return true;
        }
        if (target.startsWith("org/mariadb/")) {
            return false;
        }
        if (VALUE_TYPES.contains(target) || (target.startsWith("java/sql/SQL") && target.endsWith("Exception"))) {
            return true;
        }
        return switch (target) {
            case "java/sql/ResultSet" -> RESULT_SET_READS.contains(name) || name.startsWith("get");
            case "java/sql/ResultSetMetaData", "java/sql/DatabaseMetaData", "java/sql/ParameterMetaData" -> true;
            case "java/sql/Connection" -> CONNECTION_READS.contains(name);
            case "java/sql/Statement", "java/sql/PreparedStatement", "java/sql/CallableStatement" ->
                    STATEMENT_READS.contains(name);
            case "java/sql/Blob", "java/sql/Clob", "java/sql/NClob", "java/sql/SQLXML", "java/sql/Array" ->
                    LOB_READS.contains(name);
            case "java/sql/Wrapper" -> name.equals("unwrap") || name.equals("isWrapperFor");
            default -> false;
        };
    }

    /**
     * I metodi a cui un elemento di codice si riferisce: {@code [owner, nome, nota]} per le istruzioni {@code invoke*},
     * per i riferimenti a metodo negli argomenti (e nel metodo) di bootstrap di {@code invokedynamic} (le lambda e i
     * {@code Classe::metodo}) e per le costanti {@code MethodHandle} caricate con {@code ldc}.
     */
    static List<String[]> jdbcTargets(CodeElement e) {
        List<String[]> out = new ArrayList<>();
        if (e instanceof InvokeInstruction call) {
            out.add(new String[] {call.owner().asInternalName(), call.name().stringValue(), ""});
        } else if (e instanceof InvokeDynamicInstruction indy) {
            addHandle(out, indy.bootstrapMethod());
            for (ConstantDesc arg : indy.bootstrapArgs()) {
                addHandle(out, arg);
            }
        } else if (e instanceof ConstantInstruction constant) {
            addHandle(out, constant.constantValue());
        }
        return out;
    }

    private static void addHandle(List<String[]> out, ConstantDesc desc) {
        if (desc instanceof DirectMethodHandleDesc mh) {
            String descriptor = mh.owner().descriptorString();
            String internal = descriptor.startsWith("L") && descriptor.endsWith(";")
                    ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
            out.add(new String[] {internal, mh.methodName(), " (riferimento a metodo)"});
        }
    }

    /** {@code a/b/C$1} → {@code a/b/C}: le classi interne e le lambda contano come la classe che le contiene. */
    private static String topLevel(String internalName) {
        int dollar = internalName.indexOf('$');
        return dollar < 0 ? internalName : internalName.substring(0, dollar);
    }

    /** Il sorgente con commenti, stringhe, blocchi di testo e caratteri sostituiti da spazi (a-capo conservati). */
    static String codeOnly(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char ch = src.charAt(i);
            if (ch == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (ch == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                blank(src, i, end, out);
                i = end;
            } else if (src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                blank(src, i, end, out);
                i = end;
            } else if (ch == '"' || ch == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != ch && src.charAt(j) != '\n') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                int end = Math.min(n, j + 1);
                blank(src, i, end, out);
                i = end;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    private static void blank(String src, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) {
            out.append(src.charAt(k) == '\n' ? '\n' : ' ');
        }
    }

    private static char nextNonBlank(String s, int from) {
        for (int k = from; k < s.length(); k++) {
            if (!Character.isWhitespace(s.charAt(k))) {
                return s.charAt(k);
            }
        }
        return 0;
    }

    private static int lineOf(String s, int pos) {
        int line = 1;
        for (int k = 0; k < pos; k++) {
            if (s.charAt(k) == '\n') {
                line++;
            }
        }
        return line;
    }
}
