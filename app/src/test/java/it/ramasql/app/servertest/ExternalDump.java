/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import it.ramasql.core.connection.ConnectionProfile;

/**
 * Un dump prodotto dai programmi installati sul PC, per T10.8: {@code mariadb-dump} di MariaDB 11.5 (il suo
 * {@code mysqldump.exe} è lo stesso programma, byte per byte) e il {@code mysqldump} vero di Oracle, quello che arriva con
 * MySQL Workbench 8.0, che sa fare il dump da MySQL 8 (righe GTID, commenti {@code /*!80016}).
 * La password passa dalla variabile d'ambiente {@code MYSQL_PWD} del processo figlio: non compare sulla riga di comando
 * né nelle evidenze.
 */
final class ExternalDump {

    static final Path BIN = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "MariaDB 11.5",
            "bin");
    /** Il mysqldump di Oracle (MySQL 8.0), con MySQL Workbench. */
    static final Path ORACLE_MYSQLDUMP = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"),
            "MySQL", "MySQL Workbench 8.0 CE", "mysqldump.exe");

    /** Dump con il mysqldump di Oracle (senza righe GTID, che su un altro server non si possono eseguire). */
    static void runOracle(DbServer server, String catalog, Path out) throws IOException, InterruptedException {
        DbServer.requireTestName(catalog);
        if (!Files.isRegularFile(ORACLE_MYSQLDUMP)) {
            throw new AssertionError("mysqldump di Oracle non trovato in " + ORACLE_MYSQLDUMP);
        }
        ConnectionProfile p = server.profile();
        List<String> cmd = List.of(ORACLE_MYSQLDUMP.toString(), "--host=" + p.host(), "--port=" + p.port(),
                "--user=" + p.user(), "--default-character-set=utf8mb4", "--ssl-mode=DISABLED",
                "--set-gtid-purged=OFF", "--no-tablespaces", "--result-file=" + out, catalog);
        start(cmd, server, out, "mysqldump (Oracle)");
    }

    private ExternalDump() {
    }

    static void run(DbServer server, String tool, String catalog, Path out) throws IOException, InterruptedException {
        run(server, tool, catalog, List.of(), out);
    }

    /** Come sopra, con opzioni in più per il programma (es. {@code --skip-extended-insert}: un INSERT per riga). */
    static void runWith(DbServer server, String tool, String catalog, List<String> options, Path out)
            throws IOException, InterruptedException {
        run(server, tool, catalog, List.of(), options, out);
    }

    /** Come sopra, per le sole tabelle elencate (vuoto = tutto il catalogo). */
    static void run(DbServer server, String tool, String catalog, List<String> tables, Path out)
            throws IOException, InterruptedException {
        run(server, tool, catalog, tables, List.of(), out);
    }

    private static void run(DbServer server, String tool, String catalog, List<String> tables, List<String> options,
            Path out) throws IOException, InterruptedException {
        DbServer.requireTestName(catalog);
        Path exe = BIN.resolve(tool);
        if (!Files.isRegularFile(exe)) {
            throw new AssertionError(tool + " non trovato in " + BIN + " (goal.md: strumenti già presenti sul PC)");
        }
        ConnectionProfile p = server.profile();
        List<String> cmd = new java.util.ArrayList<>(List.of(exe.toString(), "--host=" + p.host(), "--port=" + p.port(),
                "--user=" + p.user(), "--default-character-set=utf8mb4", "--skip-ssl", "--result-file=" + out));
        cmd.addAll(options);
        cmd.add(catalog);
        cmd.addAll(tables);
        start(cmd, server, out, tool);
    }

    private static void start(List<String> cmd, DbServer server, Path out, String tool)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("MYSQL_PWD", new String(server.password()));
        Path err = out.resolveSibling(out.getFileName() + ".err");
        pb.redirectError(err.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process proc = pb.start();
        if (!proc.waitFor(120, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new AssertionError(tool + " non ha finito in 2 minuti");
        }
        String errors = Files.exists(err) ? Files.readString(err, StandardCharsets.UTF_8) : "";
        if (proc.exitValue() != 0 || !Files.exists(out) || Files.size(out) == 0) {
            throw new AssertionError(tool + " è uscito con " + proc.exitValue() + ": " + errors);
        }
    }
}
