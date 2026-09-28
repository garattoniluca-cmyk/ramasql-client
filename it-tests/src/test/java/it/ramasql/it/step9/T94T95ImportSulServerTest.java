/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.ConnectionProfile;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.BatchListener;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.importer.FileAnalyzer;
import it.ramasql.core.importer.ImportFile;
import it.ramasql.core.importer.ImportPlan;
import it.ramasql.core.importer.ImportPlanner;
import it.ramasql.core.importer.ImportReport;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.it.fixtures.ImportFixtures;

/**
 * Importazione contro i server veri, con il codice del client (analisi del file, piano, esecutore a lotti):
 * <ul>
 *   <li><b>T9.4</b>: {@code soci.csv} (100 righe) nella tabella {@code soci} esistente della biblioteca e
 *       {@code libri.json} (200) in una <b>tabella nuova</b>: righe sul server = righe del file, valori riletti uguali
 *       a quelli del file, tipi della nuova tabella = quelli dedotti;</li>
 *   <li><b>T9.5</b>: CSV da un milione di righe (generato qui, non in git): completato, memoria stabile (la memoria
 *       usata non cresce con il file); una seconda importazione interrotta a metà si ferma, e il numero di righe del
 *       rapporto è quello che c'è sul server.</li>
 * </ul>
 */
@Tag("step9")
@Tag("it")
class T94T95ImportSulServerTest {

    private static final Pattern HOST_PORT = Pattern.compile("jdbc:[a-z]+://([^:/]+):(\\d+)/.*");
    private static final long MILIONE = 1_000_000;

    @TempDir
    Path dir;

    static Session open(ItServers server, String catalog) throws Exception {
        Matcher m = HOST_PORT.matcher(server.url());
        assertTrue(m.matches(), server.url());
        ConnectionProfile p = ConnectionProfile.create("Test " + server.label(), m.group(1),
                Integer.parseInt(m.group(2)), server.user(), catalog, "");
        String value = System.getenv("RAMASQL_IT_" + server.name() + "_PASSWORD");
        if (value == null) {
            throw new AssertionError("Manca RAMASQL_IT_" + server.name() + "_PASSWORD: i test non si saltano.");
        }
        return Session.open(p, value.toCharArray());
    }

    static Path fixture(String name) {
        return TestResults.projectRoot().resolve("it-tests/fixtures").resolve(ImportFixtures.DIR).resolve(name);
    }

    private static List<List<String>> rows(Connection c, String sql) throws Exception {
        List<List<String>> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> r = new ArrayList<>();
                for (int i = 1; i <= n; i++) {
                    r.add(rs.getString(i));
                }
                out.add(r);
            }
        }
        return out;
    }

    // ================================================================ T9.4

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t94_sociCsvInTabellaEsistente(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t94_soci")) {
            cat.runScript("/fixtures/biblioteca.sql");
            SqlLog log = new SqlLog();
            try (Session s = open(server, cat.name()); SqlExecutor ex = new SqlExecutor(s, log, null)) {
                MetadataReader reader = MetadataReader.of(s);
                TableDef soci = reader.table(cat.name(), "soci").orElseThrow();
                FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(fixture(ImportFixtures.SOCI)), null,
                        null);
                assertEquals(ImportFixtures.SOCI_ROWS, a.rows());
                Map<Integer, ColumnDef> map = ImportPlanner.autoMapping(a.columns(), soci.columns());
                assertEquals(5, map.size(), "abbinamento per nome di tutte e cinque le colonne del file");
                ImportPlan plan = new ImportPlan(a.file(), cat.name(), soci, false, ImportPlanner.mappings(a, map),
                        ImportPlan.Options.defaults(), a.rows());
                ImportReport report = ImportPlanner.run(ex, plan, null).get(60, TimeUnit.SECONDS);

                assertTrue(report.completed(), "completata");
                assertEquals(100, report.read());
                assertEquals(100, report.inserted());
                assertEquals(0, report.rejectedCount(), String.valueOf(report.rejected()));
                assertEquals(1, report.batches());
                // sul server, con una connessione separata
                assertEquals("200", rows(cat.connection(), "SELECT COUNT(*) FROM soci").get(0).get(0),
                        "100 della biblioteca + 100 del file");
                List<List<String>> server100 = rows(cat.connection(),
                        "SELECT tessera, cognome, nome, email, nato_il FROM soci WHERE tessera LIKE 'S%' ORDER BY tessera");
                assertEquals(100, server100.size());
                List<ImportFixtures.Socio> file = ImportFixtures.sociList();
                for (int i = 0; i < file.size(); i++) {
                    ImportFixtures.Socio f = file.get(i);
                    assertEquals(List.of(f.tessera(), f.cognome(), f.nome()), server100.get(i).subList(0, 3));
                    assertEquals(f.email(), server100.get(i).get(3), "email vuota → NULL");
                    assertEquals(f.natoIl().toString(), server100.get(i).get(4), "gg/mm/aaaa → DATE");
                }
                SqlLog.Entry entry = log.entries().get(log.size() - 1);
                assertTrue(entry.parameterized());
                assertTrue(entry.sql().startsWith("INSERT INTO `" + cat.name() + "`.`soci` (`tessera`"), entry.sql());
                assertTrue(entry.note().contains("lotti: 1") && entry.note().contains("righe inserite: 100"),
                        entry.note());
                TestResults.write("step9", "T9.4-soci-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                        "T9.4 — soci.csv nella tabella soci esistente (" + server.label() + ")\n"
                                + "Anteprima: " + plan.script().title() + "\n" + plan.script().text() + "\n\n"
                                + "Rapporto: lette " + report.read() + ", inserite " + report.inserted() + ", scartate "
                                + report.rejectedCount() + ", lotti " + report.batches() + "\n"
                                + "Registro: " + entry.sql() + " — " + entry.note() + "\n"
                                + "Sul server: COUNT(*) soci = 200; le 100 righe S* rilette sono uguali al file\n"
                                + "Esito: OK\n");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t94_libriJsonInNuovaTabella(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t94_libri")) {
            try (Session s = open(server, cat.name()); SqlExecutor ex = new SqlExecutor(s, new SqlLog(), null)) {
                FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(fixture(ImportFixtures.LIBRI)), null,
                        null);
                assertEquals(ImportFixtures.LIBRI_ROWS, a.rows());
                assertEquals(List.of("id", "titolo", "isbn", "anno", "prezzo", "disponibile", "note"), a.columns());
                TableDef nuova = ImportPlanner.newTable(cat.name(), "libri_importati", a, true);
                ImportPlan plan = new ImportPlan(a.file(), cat.name(), nuova, true,
                        ImportPlanner.mappings(a, ImportPlanner.mappingForNewTable(a, nuova)),
                        ImportPlan.Options.defaults(), a.rows());
                ImportReport report = ImportPlanner.run(ex, plan, null).get(60, TimeUnit.SECONDS);
                assertTrue(report.completed());
                assertEquals(200, report.inserted());
                assertEquals(0, report.rejectedCount(), String.valueOf(report.rejected()));

                assertEquals("200", rows(cat.connection(), "SELECT COUNT(*) FROM libri_importati").get(0).get(0));
                // tipi sul server = tipi dedotti
                TableDef letta = MetadataReader.of(s).table(cat.name(), "libri_importati").orElseThrow();
                StringBuilder tipi = new StringBuilder();
                for (ColumnDef dedotta : nuova.columns()) {
                    ColumnDef sulServer = letta.column(dedotta.name()).orElseThrow();
                    assertTrue(SqlTypes.sameType(dedotta, sulServer),
                            dedotta.name() + ": dedotto " + dedotta.fullType() + ", sul server " + sulServer.fullType());
                    assertEquals(dedotta.nullable(), sulServer.nullable(), dedotta.name());
                    tipi.append(dedotta.name()).append(' ').append(dedotta.fullType())
                            .append(dedotta.nullable() ? " NULL" : " NOT NULL").append(" — sul server ")
                            .append(sulServer.fullType()).append('\n');
                }
                assertEquals("INT", nuova.column("id").orElseThrow().fullType());
                assertEquals(List.of("id"), letta.primaryKey().orElseThrow().columns());
                assertEquals("DECIMAL(4,2)", nuova.column("prezzo").orElseThrow().fullType());
                assertEquals("TINYINT(1)", nuova.column("disponibile").orElseThrow().fullType());
                assertTrue(nuova.column("anno").orElseThrow().nullable());
                // valori: ogni riga del server confrontata con il file letto da un lettore JSON indipendente
                List<Map<String, Object>> file = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        fixture(ImportFixtures.LIBRI).toFile(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
                List<List<String>> db = rows(cat.connection(),
                        "SELECT id, titolo, isbn, anno, prezzo, disponibile, note FROM libri_importati ORDER BY id");
                assertEquals(file.size(), db.size());
                for (int i = 0; i < file.size(); i++) {
                    Map<String, Object> f = file.get(i);
                    List<String> r = db.get(i);
                    assertEquals(String.valueOf(f.get("id")), r.get(0));
                    assertEquals(f.get("titolo"), r.get(1));
                    assertEquals(f.get("isbn"), r.get(2));
                    assertEquals(f.get("anno") == null ? null : String.valueOf(f.get("anno")), r.get(3), "anno null");
                    assertEquals(0, new java.math.BigDecimal(String.valueOf(f.get("prezzo")))
                            .compareTo(new java.math.BigDecimal(r.get(4))), "prezzo riga " + (i + 1));
                    assertEquals(Boolean.TRUE.equals(f.get("disponibile")) ? "1" : "0", r.get(5), "vero/falso → 1/0");
                    assertEquals(f.get("note"), r.get(6), "chiave mancante → NULL; accenti ed emoji intatti");
                }
                TestResults.write("step9", "T9.4-libri-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                        "T9.4 — libri.json in una tabella nuova (" + server.label() + ")\n"
                                + plan.script().text() + "\n\nTipi dedotti e riletti dal server:\n" + tipi
                                + "\nRighe sul server: 200 (= righe del file)\nEsito: OK\n");
            }
        }
    }

    // ================================================================ T9.5

    /** Scrive il CSV da un milione di righe (circa 45 MB) nella cartella temporanea del test. */
    private Path milione() throws Exception {
        Path f = dir.resolve("grande.csv");
        try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            w.write("id;nome;valore;quando\n");
            for (long i = 1; i <= MILIONE; i++) {
                w.write(i + ";riga numero " + i + ";" + (i % 1000) + "," + (i % 100) / 10 + (i % 10) + ";"
                        + String.format("%02d/%02d/%04d", 1 + i % 28, 1 + i % 12, 1990 + i % 30) + "\n");
            }
        }
        return f;
    }

    private static final String TABELLA = "CREATE TABLE grande (id INT NOT NULL PRIMARY KEY, nome VARCHAR(40) NOT NULL,"
            + " valore DECIMAL(8,2) NOT NULL, quando DATE NULL) ENGINE=InnoDB";

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t95_unMilioneDiRigheMemoriaStabile(ItServers server) throws Exception {
        Path csv = milione();
        try (TestCatalog cat = TestCatalog.create(server, "t95_grande")) {
            cat.execute(TABELLA);
            try (Session s = open(server, cat.name()); SqlExecutor ex = new SqlExecutor(s, new SqlLog(), null)) {
                TableDef t = MetadataReader.of(s).table(cat.name(), "grande").orElseThrow();
                Runtime rt = Runtime.getRuntime();
                System.gc();
                long beforeAnalysis = rt.totalMemory() - rt.freeMemory();
                FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(csv), null, null);
                assertEquals(MILIONE, a.rows());
                System.gc();
                long afterAnalysis = rt.totalMemory() - rt.freeMemory();
                long analysisMb = (afterAnalysis - beforeAnalysis) / (1024 * 1024);
                // l'analisi (che resta viva nella procedura guidata) non tiene le righe: anche con la colonna «id»
                assertTrue(analysisMb < 16, "l'analisi occupa " + analysisMb + " MB");
                ImportPlan plan = new ImportPlan(a.file(), cat.name(), t, false,
                        ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), t.columns())),
                        ImportPlan.Options.defaults(), a.rows());
                long base = afterAnalysis;
                AtomicLong peak = new AtomicLong(base);
                List<String> samples = new ArrayList<>();
                long start = System.nanoTime();
                ImportReport report = ImportPlanner.run(ex, plan, new BatchListener() {
                    @Override
                    public void batchFinished(long batches, long inserted, long rejected, long dup) {
                        if (batches % 100 == 0) {
                            System.gc();
                            long used = rt.totalMemory() - rt.freeMemory();
                            peak.accumulateAndGet(used, Math::max);
                            samples.add(batches + " lotti, " + inserted + " righe: " + used / (1024 * 1024) + " MB");
                        }
                    }
                }).get(10, TimeUnit.MINUTES);
                long millis = (System.nanoTime() - start) / 1_000_000;
                assertTrue(report.completed());
                assertEquals(MILIONE, report.inserted());
                assertEquals(0, report.rejectedCount());
                assertEquals(1000, report.batches());
                assertEquals(String.valueOf(MILIONE), rows(cat.connection(), "SELECT COUNT(*) FROM grande").get(0).get(0));
                long k = 777_777;
                String quando = java.time.LocalDate.of((int) (1990 + k % 30), (int) (1 + k % 12), (int) (1 + k % 28))
                        .toString();
                assertEquals(List.of("777777", "riga numero 777777", "777.77", quando), 
                        rows(cat.connection(), "SELECT * FROM grande WHERE id = 777777").get(0));
                long fileMb = Files.size(csv) / (1024 * 1024);
                long growthMb = (peak.get() - base) / (1024 * 1024);
                // stabile: la memoria in più (dopo la raccolta) resta molto sotto la dimensione del file
                assertTrue(growthMb < 32 && growthMb < fileMb / 2,
                        "memoria cresciuta di " + growthMb + " MB con un file di " + fileMb + " MB");
                TestResults.write("step9", "T9.5-milione-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                        "T9.5 — CSV da 1 000 000 di righe (" + fileMb + " MB) nella tabella grande (" + server.label()
                                + ")\nAnalisi del file (colonna «id» compresa): memoria in più " + analysisMb + " MB\n"
                                + "Inserite " + report.inserted() + " in " + report.batches() + " lotti, circa "
                                + millis / 1000 + " s\nMemoria usata dopo la raccolta: all'inizio " + base / (1024 * 1024)
                                + " MB, massimo " + peak.get() / (1024 * 1024) + " MB (crescita " + growthMb + " MB)\n"
                                + String.join("\n", samples) + "\nEsito: OK\n");
            }
        }
    }

    /**
     * Il percorso delle tabelle <b>MyISAM</b> (una riga per istruzione, esito riga per riga): righe errate, duplicati
     * ignorati e interruzione, con i conteggi ricontrollati sul server. MyISAM non ha chiavi esterne: la riga con il
     * libro inesistente entra (su InnoDB, in T9.7, è rifiutata).
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t94_percorsoMyIsam(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t94_myisam")) {
            cat.runScript("/fixtures/biblioteca_myisam.sql");
            long before = Long.parseLong(rows(cat.connection(), "SELECT COUNT(*) FROM prestiti").get(0).get(0));
            try (Session s = open(server, cat.name()); SqlExecutor ex = new SqlExecutor(s, new SqlLog(), null)) {
                TableDef t = MetadataReader.of(s).table(cat.name(), "prestiti").orElseThrow();
                assertEquals("MyISAM", t.engine());
                FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(fixture(ImportFixtures.PRESTITI_ERRORI)),
                        null, null);
                ImportPlan plan = new ImportPlan(a.file(), cat.name(), t, false,
                        ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), t.columns())),
                        ImportPlan.Options.defaults(), a.rows());
                assertFalse(plan.insert().atomic(), "MyISAM: righe una per volta");
                ImportReport r = ImportPlanner.run(ex, plan, null).get(60, TimeUnit.SECONDS);
                assertEquals(96, r.inserted());
                assertEquals(List.of(11L, 23L, 37L, 58L), r.rejected().stream().map(ImportReport.Rejection::line).toList());
                assertEquals(1062, r.rejected().get(0).serverCode());
                long after = Long.parseLong(rows(cat.connection(), "SELECT COUNT(*) FROM prestiti").get(0).get(0));
                assertEquals(before + 96, after, "nessuna riga inserita due volte");
                assertEquals("1", rows(cat.connection(), "SELECT COUNT(*) FROM prestiti WHERE id = 1002").get(0).get(0));
                // di nuovo, ignorando i duplicati: tutte le righe già presenti (e la 11) si saltano
                ImportPlan again = new ImportPlan(a.file(), cat.name(), t, false, plan.mappings(),
                        new ImportPlan.Options(false, true, true, null), a.rows());
                ImportReport r2 = ImportPlanner.run(ex, again, null).get(60, TimeUnit.SECONDS);
                assertEquals(0, r2.inserted());
                assertEquals(97, r2.duplicatesIgnored());
                assertEquals(3, r2.rejectedCount());
                assertEquals(after, Long.parseLong(rows(cat.connection(), "SELECT COUNT(*) FROM prestiti").get(0).get(0)));
                TestResults.write("step9", "T9.4-myisam-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                        "Percorso MyISAM (" + server.label() + "): prestiti-errori.csv in biblioteca_myisam.prestiti\n"
                                + "Prima importazione: inserite 96, rifiutate le righe 11 (1062), 23, 37, 58; la riga 90"
                                + " (libro inesistente) entra perché MyISAM non ha chiavi esterne\n"
                                + "Seconda, ignorando i duplicati: inserite 0, duplicati ignorati 97, scartate 3\n"
                                + "Sul server: prestiti = " + before + " + 96 (nessun doppio inserimento)\nEsito: OK\n");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t95_interrompiSuMyIsam(ItServers server) throws Exception {
        Path csv = dir.resolve("duecentomila.csv");
        try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            w.write("id;nome\n");
            for (int i = 1; i <= 200_000; i++) {
                w.write(i + ";riga " + i + "\n");
            }
        }
        try (TestCatalog cat = TestCatalog.create(server, "t95_myisam")) {
            cat.execute("CREATE TABLE piccola (id INT NOT NULL PRIMARY KEY, nome VARCHAR(20) NOT NULL) ENGINE=MyISAM");
            try (Session s = open(server, cat.name()); SqlExecutor ex = new SqlExecutor(s, new SqlLog(), null)) {
                TableDef t = MetadataReader.of(s).table(cat.name(), "piccola").orElseThrow();
                FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(csv), null, null);
                ImportPlan plan = new ImportPlan(a.file(), cat.name(), t, false,
                        ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), t.columns())),
                        ImportPlan.Options.defaults(), a.rows());
                AtomicLong progress = new AtomicLong();
                var future = ImportPlanner.run(ex, plan, new BatchListener() {
                    @Override
                    public void batchFinished(long batches, long inserted, long rejected, long dup) {
                        progress.set(inserted);
                    }
                });
                while (progress.get() < 20_000 && !future.isDone()) {
                    Thread.sleep(2);
                }
                ex.interrupt();
                ImportReport report = future.get(2, TimeUnit.MINUTES);
                long onServer = Long.parseLong(rows(cat.connection(), "SELECT COUNT(*) FROM piccola").get(0).get(0));
                assertTrue(report.interrupted() || report.completed());
                assertEquals(report.inserted(), onServer, "il rapporto dice quante righe ci sono davvero");
                assertTrue(report.interrupted(), "interrotta prima della fine (inserite " + report.inserted() + ")");
                TestResults.write("step9", "T9.5-interrotta-myisam-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                        "T9.5 — Interrompi su una tabella MyISAM (" + server.label() + "): inserite " + report.inserted()
                                + ", sul server " + onServer + " (uguale)\nEsito: OK\n");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void t95_interrompiAMeta(ItServers server) throws Exception {
        Path csv = milione();
        try (TestCatalog cat = TestCatalog.create(server, "t95_interrotta")) {
            cat.execute(TABELLA);
            try (Session s = open(server, cat.name()); SqlExecutor ex = new SqlExecutor(s, new SqlLog(), null)) {
                TableDef t = MetadataReader.of(s).table(cat.name(), "grande").orElseThrow();
                FileAnalyzer.Analysis a = FileAnalyzer.analyze(ImportFile.detect(csv), null, null);
                ImportPlan plan = new ImportPlan(a.file(), cat.name(), t, false,
                        ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), t.columns())),
                        ImportPlan.Options.defaults(), a.rows());
                AtomicLong progress = new AtomicLong();
                var future = ImportPlanner.run(ex, plan, new BatchListener() {
                    @Override
                    public void batchFinished(long batches, long inserted, long rejected, long dup) {
                        progress.set(inserted);
                    }
                });
                // «Interrompi» premuto da un altro thread (come dall'interfaccia) quando si è a un terzo
                while (progress.get() < 300_000 && !future.isDone()) {
                    Thread.sleep(5);
                }
                assertTrue(ex.interrupt(), "c'era un'importazione in corso");
                ImportReport report = future.get(2, TimeUnit.MINUTES);
                assertTrue(report.interrupted());
                assertFalse(report.completed());
                assertTrue(report.inserted() >= 300_000 && report.inserted() < MILIONE, "inserite " + report.inserted());
                long onServer = Long.parseLong(rows(cat.connection(), "SELECT COUNT(*) FROM grande").get(0).get(0));
                assertEquals(report.inserted(), onServer, "il rapporto dice quante righe ci sono davvero");
                TestResults.write("step9", "T9.5-interrotta-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                        "T9.5 — Interrompi a metà (" + server.label() + ")\nRapporto: interrotta, inserite "
                                + report.inserted() + " righe in " + report.batches() + " lotti\n"
                                + "Sul server: COUNT(*) = " + onServer + " (uguale)\nEsito: OK\n");
            }
        }
    }
}
