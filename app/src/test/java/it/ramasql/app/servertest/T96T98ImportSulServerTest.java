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

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static it.ramasql.app.servertest.Probe.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.importer.ImportWizard;
import it.ramasql.app.pipeline.PreviewDialog;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.importer.ImportReport;

/**
 * La procedura guidata «Importa dati» sul <b>programma vero</b>, contro MariaDB e MySQL, con l'esito ricontrollato sul
 * server da una connessione separata del test:
 * <ul>
 *   <li><b>T9.6</b>: {@code soci-excel.csv} salvato da Excel italiano (punto e virgola, Windows-1252, date
 *       gg/mm/aaaa, virgola decimale, accenti): formato riconosciuto al passo 1, anteprima corretta al passo 2,
 *       abbinamento automatico per nome nella tabella {@code soci} esistente, poi lo stesso file in una tabella
 *       nuova con il {@code CREATE TABLE} nell'anteprima; nel registro l'{@code INSERT} preparata con i lotti;</li>
 *   <li><b>T9.7</b>: {@code prestiti-errori.csv}, 100 righe di cui 5 sbagliate (duplicato, data impossibile, testo in
 *       colonna numerica, NOT NULL vuoto, chiave esterna inesistente), aperto dal menu della tabella {@code prestiti}:
 *       95 importate, 5 nel rapporto con riga e motivo in italiano;</li>
 *   <li><b>T9.8</b>: «ignora duplicati» (e il contrario), «svuota prima» con la conferma rafforzata (e il caso di una
 *       tabella usata da chiavi esterne, che il server rifiuta con una spiegazione).</li>
 * </ul>
 */
@Tag("step9")
@Tag("ui")
@Tag("it")
class T96T98ImportSulServerTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    private static final Charset CP1252 = Charset.forName("windows-1252");

    private static void executeOnPreview(ClientApp a, List<FakeWorkspacePrompts.Shown> seen) {
        a.ws.onPreview = d -> {
            if (d.requiresTypedConfirmation()) {
                d.confirmationField().setText(d.confirmation().typeToConfirm());
            }
            d.executeButton().doClick();
        };
    }

    // ================================================================ T9.6

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t96_csvDiExcelItaliano(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t96");
        StringBuilder ev = new StringBuilder("T9.6 — procedura guidata completa su soci-excel.csv salvato da Excel italiano ("
                + server.label() + ")\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                executeOnPreview(a, null);
                Path file = ImportSupport.fixture("soci-excel.csv");
                ImportWizard w = ImportSupport.fromToolbar(a, catalog);
                assertEquals(ImportWizard.Step.FILE, fromEdt(w::step));
                ImportSupport.choose(a, w, file);
                // passo 1: formato riconosciuto
                assertEquals(CP1252, fromEdt(() -> w.charsetCombo().getSelectedItem()));
                assertEquals(';', (char) fromEdt(() -> w.separatorCombo().getSelectedItem()));
                assertTrue(fromEdt(() -> w.headerCheck().isSelected()));
                ev.append("Passo 1: ").append(fromEdt(() -> w.detectedLabel().getText())).append('\n');
                ImportSupport.paint(w, "T9.6-1-file-" + server.id() + ".png");

                // passo 2: anteprima corretta (accenti, date, decimali), niente caratteri illeggibili
                ImportSupport.toPreview(w);
                assertEquals(100, w.analysis().rows());
                assertEquals(List.of("Tessera", "Cognome", "Nome", "Email", "Nato il", "Quota"), w.analysis().columns());
                List<String> excel = Files.readAllLines(file, CP1252);
                String[] first = excel.get(1).split(";", -1);
                for (int c = 0; c < 6; c++) {
                    Object shown = fromEdt(() -> w.previewTable().getValueAt(0, 0));
                    assertNotNull(shown);
                    int col = c;
                    assertEquals(first[c], fromEdt(() -> String.valueOf(w.previewTable().getValueAt(0, col))),
                            "anteprima = file, colonna " + c);
                }
                String accented = excel.stream().filter(l -> l.contains("ò")).findFirst().orElseThrow();
                int accentedRow = excel.indexOf(accented) - 1;
                boolean inPreview = accentedRow < 20;
                if (inPreview) {
                    String nome = accented.split(";")[2];
                    assertEquals(nome, fromEdt(() -> String.valueOf(w.previewTable().getValueAt(accentedRow, 2))));
                }
                assertTrue(fromEdt(() -> w.previewBanner().text()).isEmpty(), "nessun avviso: "
                        + fromEdt(() -> w.previewBanner().text()));
                assertEquals("DATE", w.analysis().inferred().get(4).column().fullType());
                assertEquals("DECIMAL(4,2)", w.analysis().inferred().get(5).column().fullType());
                ev.append("Passo 2: ").append(fromEdt(() -> w.previewSummary().getText()))
                        .append(" Prima riga: ").append(String.join(" | ", first)).append('\n');
                ImportSupport.paint(w, "T9.6-2-anteprima-" + server.id() + ".png");

                // passo 3: tabella esistente «soci», abbinamento automatico per nome («Nato il» → nato_il)
                onEdt(() -> {
                    w.existingRadio().doClick();
                    w.tableCombo().setSelectedItem("soci");
                });
                ImportSupport.toTarget(w);
                waitUntil("abbinamento letto", ClientApp.TIMEOUT, () -> "tessera".equals(w.mappingTable().getValueAt(0, 2)));
                List<String> mapping = new ArrayList<>();
                for (int r = 0; r < 6; r++) {
                    int row = r;
                    mapping.add(fromEdt(() -> w.mappingTable().getValueAt(row, 0) + " → "
                            + w.mappingTable().getValueAt(row, 2)));
                }
                assertEquals(List.of("Tessera → tessera", "Cognome → cognome", "Nome → nome", "Email → email",
                        "Nato il → nato_il", "Quota → "), mapping, "«Quota» non ha una colonna: non si importa");
                ev.append("Passo 3 (abbinamento automatico): ").append(mapping).append('\n');
                ImportSupport.paint(w, "T9.6-3-abbinamento-" + server.id() + ".png");

                int logBefore = a.log().size();
                ImportSupport.runToEnd(a, w);
                ImportReport report = w.report();
                assertNotNull(report, fromEdt(() -> w.resultBanner().text()));
                assertTrue(report.completed());
                assertEquals(100, report.inserted());
                assertEquals(0, report.rejectedCount(), String.valueOf(report.rejected()));
                FakeWorkspacePrompts.Shown preview = a.ws.previews.get(a.ws.previews.size() - 1);
                assertEquals(1, preview.script().size());
                assertTrue(preview.sqlInDialog().contains("INSERT INTO `" + catalog + "`.`soci` (`tessera`, `cognome`, `nome`,"
                        + " `email`, `nato_il`) VALUES (?, ?, ?, ?, ?)"), preview.sqlInDialog());
                assertTrue(preview.script().title().contains("1 lotto"), preview.script().title());
                SqlLog.Entry entry = a.log().entries().get(a.log().size() - 1);
                assertTrue(a.log().size() > logBefore);
                assertTrue(entry.parameterized() && entry.sql().contains("VALUES (?, ?, ?, ?, ?)"), entry.sql());
                assertTrue(entry.note().contains("lotti: 1") && entry.note().contains("righe inserite: 100"),
                        entry.note());
                assertEquals("Importazione", entry.origin());
                ev.append("Anteprima: ").append(preview.script().title()).append('\n').append(preview.sqlInDialog())
                        .append('\n').append("Registro: ").append(entry.sql()).append(" — ").append(entry.note())
                        .append('\n').append("Rapporto: ").append(fromEdt(() -> w.resultBanner().text())).append('\n');
                ImportSupport.paint(w, "T9.6-5-rapporto-" + server.id() + ".png");
                // sul server: 100 soci in più, con accenti e date giuste
                assertEquals(200, server.rowCount(catalog, "soci"));
                for (String line : excel.subList(1, excel.size())) {
                    String[] v = line.split(";", -1);
                    List<List<String>> r = server.rows("SELECT cognome, nome, email, DATE_FORMAT(nato_il, '%d/%m/%Y')"
                            + " FROM `" + catalog + "`.soci WHERE tessera = '" + v[0] + "'");
                    assertEquals(1, r.size(), v[0]);
                    assertEquals(java.util.Arrays.asList(v[1], v[2], v[3].isEmpty() ? null : v[3], v[4]), r.get(0), v[0]);
                }
                ev.append("Sul server: soci = 200; le 100 righe rilette (cognome, nome, email, nato_il) coincidono con il file\n");

                // lo stesso file in una tabella nuova: CREATE TABLE nell'anteprima, tipi dedotti
                ImportWizard w2 = ImportSupport.fromToolbar(a, catalog);
                ImportSupport.choose(a, w2, file);
                ImportSupport.toPreview(w2);
                onEdt(() -> w2.newRadio().doClick());
                ImportSupport.toTarget(w2);
                onEdt(() -> w2.newNameField().setText("soci_excel"));
                String create = fromEdt(() -> w2.createSqlArea().getText());
                assertTrue(create.startsWith("CREATE TABLE `" + catalog + "`.`soci_excel`"), create);
                assertTrue(create.contains("`Nato il` DATE") && create.contains("`Quota` DECIMAL(4,2)")
                        && create.contains("`Tessera` CHAR(8)") && create.contains("PRIMARY KEY (`id`)"), create);
                ImportSupport.paint(w2, "T9.6-3-nuova-tabella-" + server.id() + ".png");
                ImportSupport.runToEnd(a, w2);
                FakeWorkspacePrompts.Shown p2 = a.ws.previews.get(a.ws.previews.size() - 1);
                assertEquals(2, p2.script().size());
                assertTrue(p2.sqlInDialog().startsWith("CREATE TABLE"), p2.sqlInDialog());
                assertEquals(100, w2.report().inserted());
                assertEquals(100, server.rowCount(catalog, "soci_excel"));
                List<List<String>> types = server.rows("SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = 'soci_excel' ORDER BY ORDINAL_POSITION");
                ev.append("Tabella nuova — anteprima:\n").append(p2.sqlInDialog()).append("\nColonne sul server: ")
                        .append(types).append('\n');
                assertEquals("date", types.get(5).get(1));
                assertTrue(types.get(6).get(1).startsWith("decimal(4,2)"), types.toString());
                assertEquals("24.50", server.scalar("SELECT Quota FROM `" + catalog + "`.soci_excel WHERE Tessera = 'S0000001'"),
                        "virgola decimale di Excel → 24.50");
                ev.append("Esito: OK\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step9", "T9.6-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ================================================================ T9.7

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t97_cinqueRigheSbagliateSuCento(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t97");
        StringBuilder ev = new StringBuilder("T9.7 — prestiti-errori.csv: 100 righe, 5 sbagliate (" + server.label() + ")\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            long before = server.rowCount(catalog, "prestiti");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                executeOnPreview(a, null);
                ImportWizard w = ImportSupport.fromTableMenu(a, catalog, "prestiti");
                ImportSupport.choose(a, w, ImportSupport.fixture("prestiti-errori.csv"));
                ImportSupport.toPreview(w);
                assertEquals(100, w.analysis().rows());
                assertTrue(fromEdt(() -> w.existingRadio().isSelected()));
                assertEquals("prestiti", fromEdt(() -> w.tableCombo().getSelectedItem()), "tabella proposta dal menu");
                ImportSupport.toTarget(w);
                waitUntil("abbinamento", ClientApp.TIMEOUT, () -> "id".equals(w.mappingTable().getValueAt(0, 2)));
                ImportSupport.runToEnd(a, w);
                ImportReport r = w.report();
                assertNotNull(r);
                assertEquals(100, r.read());
                assertEquals(95, r.inserted());
                assertEquals(5, r.rejectedCount());
                assertEquals(List.of(11L, 23L, 37L, 58L, 90L), r.rejected().stream().map(ImportReport.Rejection::line).toList());
                List<String> reasons = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    int row = i;
                    reasons.add(fromEdt(() -> w.rejectedTable().getValueAt(row, 0) + ": " + w.rejectedTable().getValueAt(row, 1)));
                }
                assertTrue(reasons.get(0).contains("duplicato"), reasons.get(0));
                assertTrue(reasons.get(0).contains("[1062]"), "con il messaggio originale del server: " + reasons.get(0));
                assertTrue(reasons.get(1).contains("31/02/2025") && reasons.get(1).contains("non è una data valida"),
                        reasons.get(1));
                assertTrue(reasons.get(2).contains("trentasette") && reasons.get(2).contains("non è un numero intero"),
                        reasons.get(2));
                assertTrue(reasons.get(3).contains("id_socio") && reasons.get(3).contains("NOT NULL"), reasons.get(3));
                assertTrue(reasons.get(4).contains("tabella riferita") && reasons.get(4).contains("[1452]"), reasons.get(4));
                for (String reason : reasons) {
                    ev.append("  riga ").append(reason).append('\n');
                }
                assertEquals(before + 95, server.rowCount(catalog, "prestiti"));
                // la riga 11 ripete l'id 1002 della riga 3: sul server c'è una sola riga 1002, quella della riga 3
                assertEquals(List.of(List.of("15", "7")), server.rows("SELECT id_libro, id_socio FROM `" + catalog
                        + "`.prestiti WHERE id = 1002"), "una sola riga 1002, quella della riga 3 del file");
                for (String id : List.of("1022", "1036", "1057", "1089")) {
                    assertEquals("0", server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.prestiti WHERE id = " + id),
                            "la riga sbagliata con id " + id + " non c'è");
                }
                ev.append("Rapporto: ").append(fromEdt(() -> w.resultBanner().text())).append('\n')
                        .append("Sul server: prestiti = ").append(before).append(" + 95\nEsito: OK\n");
                ImportSupport.paint(w, "T9.7-rapporto-" + server.id() + ".png");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step9", "T9.7-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ================================================================ T9.5 dall'interfaccia

    /** «Interrompi» premuto nella procedura guidata a metà di un'importazione lunga (tabella nuova, InnoDB). */
    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t95_interrompiDallaProceduraGuidata(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t95ui");
        StringBuilder ev = new StringBuilder("T9.5 — Interrompi dalla procedura guidata (" + server.label() + ")\n");
        Path csv = dataDir.resolve("trecentomila.csv");
        try (java.io.BufferedWriter out = Files.newBufferedWriter(csv, java.nio.charset.StandardCharsets.UTF_8)) {
            out.write("codice;descrizione\n");
            for (int i = 1; i <= 300_000; i++) {
                out.write("C" + i + ";descrizione della riga numero " + i + "\n");
            }
        }
        try {
            server.createCatalog(catalog);
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                ImportWizard w = ImportSupport.fromToolbar(a, catalog);
                ImportSupport.choose(a, w, csv);
                ImportSupport.toPreview(w);
                assertTrue(fromEdt(() -> w.newRadio().isSelected()), "catalogo vuoto: tabella nuova");
                ImportSupport.toTarget(w);
                onEdt(() -> w.newNameField().setText("righe"));
                onEdt(() -> w.nextButton().doClick());
                onEdt(() -> w.nextButton().doClick());
                onEdt(() -> w.nextButton().doClick());   // «Importa»
                waitUntil("avanzamento oltre le 30 000 righe", 120_000, () -> w.isRunning()
                        && w.progressLabel().getText().matches(".*\\d+ inserite.*")
                        && Long.parseLong(w.progressLabel().getText().replaceAll(".*· (\\d+) inserite.*", "$1")) >= 30_000);
                assertTrue(fromEdt(() -> w.stopButton().isEnabled()));
                onEdt(() -> w.stopButton().doClick());
                waitUntil("importazione fermata", 120_000, () -> !w.isRunning());
                a.waitIdle();
                assertNotNull(w.report());
                assertTrue(w.report().interrupted(), fromEdt(() -> w.resultBanner().text()));
                long onServer = server.rowCount(catalog, "righe");
                assertEquals(w.report().inserted(), onServer, "il rapporto dice quante righe ci sono davvero");
                assertTrue(onServer < 300_000);
                assertTrue(fromEdt(() -> w.resultBanner().text()).startsWith("✘ Interrotta") || fromEdt(
                        () -> w.resultBanner().text()).contains("Interrotta"));
                ev.append("Rapporto: ").append(fromEdt(() -> w.resultBanner().text())).append("\nSul server: righe = ")
                        .append(onServer).append(" (uguale)\nEsito: OK\n");
                ImportSupport.paint(w, "T9.5-interrotta-" + server.id() + ".png");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step9", "T9.5-interfaccia-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }

    // ================================================================ T9.8

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t98_ignoraDuplicatiESvuotaPrima(DbServer server) throws Exception {
        String catalog = DbServer.newCatalogName("t98");
        StringBuilder ev = new StringBuilder("T9.8 — opzioni «ignora duplicati» e «svuota prima» (" + server.label() + ")\n");
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dataDir)) {
                executeOnPreview(a, null);
                Path soci = ImportSupport.fixture("soci.csv");
                // 1) prima importazione: 100 soci nuovi
                ImportWizard w = ImportSupport.fromTableMenu(a, catalog, "soci");
                ImportSupport.choose(a, w, soci);
                ImportSupport.toPreview(w);
                ImportSupport.toTarget(w);
                ImportSupport.runToEnd(a, w);
                assertEquals(100, w.report().inserted());
                assertEquals(200, server.rowCount(catalog, "soci"));

                // 2) di nuovo, «Segnalale come errore»: 100 righe rifiutate (1062), nessuna inserita
                ImportWizard w2 = ImportSupport.fromTableMenu(a, catalog, "soci");
                ImportSupport.choose(a, w2, soci);
                ImportSupport.toPreview(w2);
                ImportSupport.toTarget(w2);
                onEdt(() -> w2.nextButton().doClick());
                assertEquals(0, (int) fromEdt(() -> w2.duplicatesCombo().getSelectedIndex()), "predefinito: errore");
                onEdt(() -> w2.nextButton().doClick());
                ImportSupport.startAndWait(a, w2);
                assertEquals(0, w2.report().inserted());
                assertEquals(100, w2.report().rejectedCount());
                assertTrue(w2.report().rejected().stream().allMatch(x -> x.serverCode() == 1062));
                assertEquals(200, server.rowCount(catalog, "soci"));
                ev.append("Duplicati come errore: ").append(fromEdt(() -> w2.resultBanner().text())).append('\n');

                // 3) di nuovo, «Saltale (ignora i duplicati)»: 100 duplicati ignorati, 0 errori; più 1 socio nuovo
                Path conUnoNuovo = dataDir.resolve("soci-piu-uno.csv");
                Files.writeString(conUnoNuovo, Files.readString(soci) + "S9999999;Nuovo;Socio;;01/01/2000\n");
                ImportWizard w3 = ImportSupport.fromTableMenu(a, catalog, "soci");
                ImportSupport.choose(a, w3, conUnoNuovo);
                ImportSupport.toPreview(w3);
                ImportSupport.toTarget(w3);
                onEdt(() -> w3.nextButton().doClick());
                onEdt(() -> w3.duplicatesCombo().setSelectedIndex(1));
                onEdt(() -> w3.nextButton().doClick());
                ImportSupport.startAndWait(a, w3);
                assertEquals(1, w3.report().inserted());
                assertEquals(100, w3.report().duplicatesIgnored());
                assertEquals(0, w3.report().rejectedCount());
                assertEquals(201, server.rowCount(catalog, "soci"));
                ev.append("Ignora duplicati: ").append(fromEdt(() -> w3.resultBanner().text())).append('\n');
                ImportSupport.paint(w3, "T9.8-ignora-duplicati-" + server.id() + ".png");

                // 4) «svuota prima» su una tabella senza chiavi esterne che la usano: TRUNCATE con conferma rafforzata
                server.run("CREATE TABLE `" + catalog + "`.soci_copia LIKE `" + catalog + "`.soci");
                server.run("INSERT INTO `" + catalog + "`.soci_copia SELECT * FROM `" + catalog + "`.soci WHERE id <= 30");
                onEdt(() -> a.nav().refreshAll());   // F5: la tabella creata fuori dal client compare
                a.waitIdle();
                List<String> confirmations = new ArrayList<>();
                a.ws.onPreview = d -> {
                    confirmations.add(d.confirmation().level() + " «" + d.confirmation().typeToConfirm() + "»");
                    assertTrue(d.requiresTypedConfirmation(), "TRUNCATE: conferma rafforzata");
                    assertFalse(d.executeButton().isEnabled(), "Esegui spento finché non si riscrive il nome");
                    Probe.paintWindow("step9", d, "T9.8-svuota-prima-conferma-" + server.id() + ".png");
                    d.confirmationField().setText(d.confirmation().typeToConfirm());
                    d.executeButton().doClick();
                };
                ImportWizard w4 = ImportSupport.fromTableMenu(a, catalog, "soci_copia");
                ImportSupport.choose(a, w4, soci);
                ImportSupport.toPreview(w4);
                ImportSupport.toTarget(w4);
                onEdt(() -> w4.nextButton().doClick());
                waitUntil("controllo delle chiavi che riferiscono soci_copia", ClientApp.TIMEOUT,
                        () -> !w4.isCheckingTruncate());
                assertTrue(fromEdt(w4::truncateAllowed), "soci_copia non è riferita da nessuna chiave esterna");
                onEdt(() -> w4.truncateCheck().doClick());
                onEdt(() -> w4.nextButton().doClick());
                ImportSupport.startAndWait(a, w4);
                FakeWorkspacePrompts.Shown p = a.ws.previews.get(a.ws.previews.size() - 1);
                assertTrue(p.sqlInDialog().startsWith("TRUNCATE TABLE `" + catalog + "`.`soci_copia`"), p.sqlInDialog());
                assertEquals(ConfirmationPolicy.Level.STRONG, p.confirmation().level());
                assertEquals(100, w4.report().inserted());
                assertEquals(100, server.rowCount(catalog, "soci_copia"), "le 30 righe di prima non ci sono più");
                assertEquals("0", server.scalar("SELECT COUNT(*) FROM `" + catalog + "`.soci_copia WHERE tessera LIKE 'T%'"));
                ev.append("Svuota prima: anteprima\n").append(p.sqlInDialog()).append("\nconferma ").append(confirmations)
                        .append(" → soci_copia = 100 righe, tutte del file\n");

                // 5) «svuota prima» su «soci», riferita da prestiti: il server rifiuterebbe TRUNCATE (1701) anche con
                //    prestiti vuota, quindi l'opzione è spenta e il passo 4 dice perché
                ImportWizard w5 = ImportSupport.fromTableMenu(a, catalog, "soci");
                ImportSupport.choose(a, w5, soci);
                ImportSupport.toPreview(w5);
                ImportSupport.toTarget(w5);
                onEdt(() -> w5.nextButton().doClick());
                waitUntil("controllo delle chiavi che riferiscono soci", ClientApp.TIMEOUT, () -> !w5.isCheckingTruncate());
                assertFalse(fromEdt(w5::truncateAllowed), "svuota prima spento su una tabella riferita");
                assertFalse(fromEdt(() -> w5.truncateCheck().isSelected()));
                String why = fromEdt(() -> w5.truncateCheck().getToolTipText());
                assertTrue(why.contains("prestiti") && why.contains("TRUNCATE"), why);
                assertEquals(201, server.rowCount(catalog, "soci"), "soci intatta");
                ev.append("Svuota prima su soci (riferita da prestiti): opzione spenta, con il motivo: ").append(why).append('\n');
                ev.append("Esito: OK\n");
            }
        } catch (Throwable t) {
            ev.append("Esito: FALLITO — ").append(t).append('\n');
            throw t;
        } finally {
            Probe.writeText("step9", "T9.8-" + server.id() + ".txt", ev.toString());
            server.dropQuietly(catalog);
        }
    }
}
