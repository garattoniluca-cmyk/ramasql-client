/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.core.exec.BatchSource;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlScript;
import it.ramasql.core.importer.ValueParsing.DateOrder;
import it.ramasql.core.importer.ValueParsing.DecimalStyle;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.TableDef;

/**
 * Conversione dei valori del file prima dell'inserimento e SQL del piano d'importazione (ciò che l'anteprima mostra):
 * la parte «senza server» di T9.4, T9.6, T9.7 e T9.8.
 */
@Tag("step9")
class ImportConversionTest {

    @TempDir
    Path dir;

    private static final ColumnDef NOME = ColumnDef.of("cognome", "VARCHAR", "10").withNullable(false);
    private static final ColumnDef NATO = ColumnDef.of("nato_il", "DATE", null);
    private static final ColumnDef PREZZO = ColumnDef.of("prezzo", "DECIMAL", "6,2");
    private static final ColumnDef ANNO = ColumnDef.of("anno", "SMALLINT", null).withUnsigned(true);

    private static String conv(Object raw, ColumnDef c) throws ValueConverter.Rejected {
        return ValueConverter.convert(raw, c, DecimalStyle.COMMA, DateOrder.DMY, true);
    }

    @Test
    void dateItalianeDiventanoIso() throws Exception {
        assertEquals("2001-03-12", conv("12/03/2001", NATO));
        assertEquals("2001-03-12", conv("2001-03-12", NATO), "le ISO si accettano sempre");
        assertEquals("2001-12-03", ValueConverter.convert("12/03/2001", NATO, DecimalStyle.DOT, DateOrder.MDY, true));
    }

    @Test
    void dataImpossibileRifiutataConMotivo() {
        ValueConverter.Rejected e = assertThrows(ValueConverter.Rejected.class, () -> conv("31/02/2001", NATO));
        assertTrue(e.getMessage().contains("31/02/2001") && e.getMessage().contains("nato_il"), e.getMessage());
    }

    @Test
    void virgolaDecimaleItaliana() throws Exception {
        assertEquals("1234.50", conv("1.234,50", PREZZO));
        assertEquals("12.5", ValueConverter.convert("12.5", PREZZO, DecimalStyle.DOT, DateOrder.DMY, true));
        assertEquals("12.5", conv(new BigDecimal("12.5"), PREZZO), "numero JSON");
    }

    @Test
    void troppiDecimaliRifiutati() {
        ValueConverter.Rejected e = assertThrows(ValueConverter.Rejected.class, () -> conv("1,234", PREZZO));
        assertTrue(e.getMessage().contains("decimali"), e.getMessage());
    }

    @Test
    void testoInColonnaNumericaRifiutato() {
        ValueConverter.Rejected e = assertThrows(ValueConverter.Rejected.class, () -> conv("duemila", ANNO));
        assertTrue(e.getMessage().contains("non è un numero intero"), e.getMessage());
    }

    @Test
    void numeroFuoriIntervalloRifiutato() {
        assertThrows(ValueConverter.Rejected.class, () -> conv("-5", ANNO));
        assertThrows(ValueConverter.Rejected.class, () -> conv("70000", ANNO));
    }

    @Test
    void vuotoInColonnaNotNullRifiutato() {
        ValueConverter.Rejected e = assertThrows(ValueConverter.Rejected.class, () -> conv("", NOME));
        assertTrue(e.getMessage().contains("NOT NULL"), e.getMessage());
        assertThrows(ValueConverter.Rejected.class, () -> conv(null, NOME));
    }

    @Test
    void vuotoDiventaNullOppureTestoVuoto() throws Exception {
        assertNull(conv("", NATO));
        ColumnDef email = ColumnDef.of("email", "VARCHAR", "50");
        assertNull(conv("", email));
        assertEquals("", ValueConverter.convert("", email, DecimalStyle.DOT, DateOrder.DMY, false),
                "senza «vuoto = NULL» una colonna di testo riceve il testo vuoto");
        assertNull(ValueConverter.convert("", NATO, DecimalStyle.DOT, DateOrder.DMY, false),
                "una data vuota resta NULL anche senza l'opzione");
    }

    @Test
    void autoIncrementVuotoLasciaGenerareIlServer() throws Exception {
        ColumnDef id = ColumnDef.of("id", "INT", null).withNullable(false).withAutoIncrement(true);
        assertNull(conv("", id));
    }

    @Test
    void testoTroppoLungoRifiutato() {
        ValueConverter.Rejected e = assertThrows(ValueConverter.Rejected.class, () -> conv("Abcdefghijklm", NOME));
        assertTrue(e.getMessage().contains("troppo lungo"), e.getMessage());
    }

    @Test
    void valoriLogici() throws Exception {
        ColumnDef b = ColumnDef.of("attivo", "TINYINT", "1");
        assertEquals("1", conv("Vero", b));
        assertEquals("0", conv("no", b));
        assertEquals("1", conv(Boolean.TRUE, b));
        assertEquals("1", conv("1", b));
    }

    @Test
    void accentiEdEmojiNelTestoRestano() throws Exception {
        ColumnDef t = ColumnDef.of("nota", "VARCHAR", "20");
        assertEquals("Forlì 😀 d'Arco", conv("Forlì 😀 d'Arco", t));
    }

    @Test
    void dataEOra() throws Exception {
        ColumnDef dt = ColumnDef.of("quando", "DATETIME", null);
        assertEquals("2001-03-12 08:30:00", conv("12/03/2001 08:30", dt));
        assertEquals("2001-03-12 00:00:00", conv("12/03/2001", dt));
    }

    // ---------------------------------------------------------------- piano e SQL mostrato

    private FileAnalyzer.Analysis analysis(String name, String text) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, text, StandardCharsets.UTF_8);
        return FileAnalyzer.analyze(ImportFile.detect(f), null, null);
    }

    private static final TableDef SOCI = TableDef.of("biblio", "soci").withEngine("InnoDB").withColumns(List.of(
            ColumnDef.of("id", "INT", null).withNullable(false).withAutoIncrement(true),
            ColumnDef.of("tessera", "CHAR", "8").withNullable(false),
            ColumnDef.of("cognome", "VARCHAR", "60").withNullable(false),
            ColumnDef.of("nome", "VARCHAR", "60").withNullable(false),
            ColumnDef.of("email", "VARCHAR", "120"),
            ColumnDef.of("nato_il", "DATE", null)));

    @Test
    void abbinamentoAutomaticoPerNome() {
        Map<Integer, ColumnDef> m = ImportPlanner.autoMapping(List.of("Tessera", "COGNOME", "Nome", "E-mail", "Città"),
                SOCI.columns());
        assertEquals("tessera", m.get(0).name());
        assertEquals("cognome", m.get(1).name());
        assertEquals("nome", m.get(2).name());
        assertEquals("email", m.get(3).name(), "trattini e maiuscole non contano");
        assertNull(m.get(4), "nessuna colonna città");
        assertEquals("citta", ImportPlanner.normalize("Città"));
    }

    @Test
    void scriptTabellaEsistenteConInsertPreparataELotti() throws Exception {
        FileAnalyzer.Analysis a = analysis("soci.csv", "tessera;cognome;nome\nT0000001;Rossi;Anna\n");
        ImportPlan plan = new ImportPlan(a.file(), "biblio", SOCI, false,
                ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), SOCI.columns())),
                ImportPlan.Options.defaults(), 2500);
        SqlScript s = plan.script();
        assertEquals(1, s.size());
        assertEquals("INSERT INTO `biblio`.`soci` (`tessera`, `cognome`, `nome`) VALUES (?, ?, ?)",
                s.statements().get(0).text());
        assertEquals(3, plan.expectedBatches());
        assertTrue(s.title().contains("soci.csv") && s.title().contains("2500 righe") && s.title().contains("3 lotti"),
                s.title());
        assertTrue(plan.insert().atomic(), "InnoDB: una INSERT per lotto");
        assertEquals(RiskLevel.MODIFIES, s.risk());
    }

    @Test
    void svuotaPrimaEUnTruncateDistruttivo() throws Exception {
        FileAnalyzer.Analysis a = analysis("soci.csv", "tessera;cognome;nome\nT0000001;Rossi;Anna\n");
        ImportPlan plan = new ImportPlan(a.file(), "biblio", SOCI, false,
                ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), SOCI.columns())),
                new ImportPlan.Options(true, false, true, null), 1);
        SqlScript s = plan.script();
        assertEquals("TRUNCATE TABLE `biblio`.`soci`", s.statements().get(0).text());
        assertEquals(RiskLevel.DESTRUCTIVE, s.risk(), "passa dalla conferma rafforzata");
        assertTrue(s.title().contains("1 lotto"), s.title());
    }

    @Test
    void nuovaTabellaConTipiDedottiEChiave() throws Exception {
        FileAnalyzer.Analysis a = analysis("libri.json",
                "[{\"id\": 1, \"titolo\": \"Uno\", \"prezzo\": 12.5, \"uscita\": \"2001-03-12\"},"
                        + " {\"id\": 2, \"titolo\": \"Due libri\", \"prezzo\": 8, \"uscita\": null}]");
        TableDef t = ImportPlanner.newTable("biblio", "libri_json", a, true);
        assertEquals("id", t.primaryKey().orElseThrow().columns().get(0), "la colonna id del file è la chiave");
        assertTrue(t.column("id").orElseThrow().autoIncrement());
        ImportPlan plan = new ImportPlan(a.file(), "biblio", t, true,
                ImportPlanner.mappings(a, ImportPlanner.mappingForNewTable(a, t)), ImportPlan.Options.defaults(), 2);
        SqlScript s = plan.script();
        String create = s.statements().get(0).text();
        assertTrue(create.startsWith("CREATE TABLE `biblio`.`libri_json`"), create);
        assertTrue(create.contains("`prezzo` DECIMAL(3,1)"), create);
        assertTrue(create.contains("`uscita` DATE NULL") || create.contains("`uscita` DATE"), create);
        assertTrue(create.contains("PRIMARY KEY (`id`)"), create);
        assertEquals("INSERT INTO `biblio`.`libri_json` (`id`, `titolo`, `prezzo`, `uscita`) VALUES (?, ?, ?, ?)",
                s.statements().get(1).text());
    }

    @Test
    void nuovaTabellaSenzaIdRiceveLaChiaveAggiunta() throws Exception {
        FileAnalyzer.Analysis a = analysis("x.csv", "cognome;nome\nRossi;Anna\nBianchi;Luca\n");
        TableDef t = ImportPlanner.newTable("biblio", "x", a, true);
        assertEquals("id", t.columns().get(0).name());
        assertEquals(List.of("id"), t.primaryKey().orElseThrow().columns());
        assertTrue(ImportPlanner.newTable("biblio", "x", a, false).primaryKey().isEmpty());
    }

    @Test
    void righeConvertiteARottiELotti() throws Exception {
        FileAnalyzer.Analysis a = analysis("soci.csv", "tessera;cognome;nome;nato_il\n"
                + "T0000001;Rossi;Anna;12/03/2001\n"
                + "T0000002;;Luca;01/01/2000\n"          // cognome vuoto: NOT NULL
                + "T0000003;Verdi;Ugo;31/02/2000\n"       // data impossibile
                + "T0000004;Neri;Eva;\n"
                + "T0000005;Blu;Ada;05/05/2005;in più\n");
        ImportPlan plan = new ImportPlan(a.file(), "biblio", SOCI, false,
                ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), SOCI.columns())),
                ImportPlan.Options.defaults(), a.rows());
        try (ImportRows rows = plan.rows()) {
            List<BatchSource.Row> batch = rows.nextBatch(1000);
            assertEquals(2, batch.size());
            assertEquals(2, batch.get(0).line());
            assertEquals(List.of("T0000001", "Rossi", "Anna", "2001-03-12"), List.of(batch.get(0).params()));
            assertNull(batch.get(1).params()[3]);
            assertTrue(rows.nextBatch(1000).isEmpty());
            assertEquals(5, rows.read());
            assertEquals(3, rows.rejectedCount());
            assertEquals(List.of(3L, 4L, 6L), rows.rejected().stream().map(ImportRows.Skipped::line).toList());
            assertTrue(rows.rejected().get(0).reason().contains("cognome"));
            assertTrue(rows.rejected().get(2).reason().contains("5 valori"), rows.rejected().get(2).reason());
        }
    }

    @Test
    void rigaCortaSuColonnaNotNullDiceCheMancaIlValore() throws Exception {
        FileAnalyzer.Analysis a = analysis("corti.csv", "tessera;cognome;nome\nT0000001;Rossi\n");
        ImportPlan plan = new ImportPlan(a.file(), "biblio", SOCI, false,
                ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), SOCI.columns())),
                ImportPlan.Options.defaults(), a.rows());
        try (ImportRows rows = plan.rows()) {
            assertTrue(rows.nextBatch(10).isEmpty());
            String reason = rows.rejected().get(0).reason();
            assertTrue(reason.contains("solo 2 valori") && reason.contains("«nome»"), reason);
        }
    }

    @Test
    void lottoChiusoPrimaSeIValoriSonoTroppoGrandi() throws Exception {
        StringBuilder sb = new StringBuilder("tessera;cognome;nome\n");
        String lungo = "x".repeat(50);
        for (int i = 0; i < 40_000; i++) {
            sb.append(String.format("T%07d", i)).append(';').append(lungo).append(';').append(lungo).append('\n');
        }
        FileAnalyzer.Analysis a = analysis("molti.csv", sb.toString());
        TableDef larga = SOCI.changeColumn("cognome", c -> c.withTypeArgs("100"))
                .changeColumn("nome", c -> c.withTypeArgs("100"));
        ImportPlan plan = new ImportPlan(a.file(), "biblio", larga, false,
                ImportPlanner.mappings(a, ImportPlanner.autoMapping(a.columns(), larga.columns())),
                ImportPlan.Options.defaults(), a.rows());
        try (ImportRows rows = plan.rows()) {
            int first = rows.nextBatch(100_000).size();
            assertTrue(first < 40_000 && first > 1000, "lotto limitato dalla dimensione: " + first + " righe");
        }
    }

    @Test
    void tabellaNuovaSempreInnoDb() throws Exception {
        FileAnalyzer.Analysis a = analysis("x.csv", "a;b\n1;2\n");
        assertEquals("InnoDB", ImportPlanner.newTable("biblio", "x", a, true).engine());
        assertTrue(it.ramasql.core.sqlgen.TableDiff.createTable(ImportPlanner.newTable("biblio", "x", a, true))
                .contains("ENGINE=InnoDB"));
    }

    @Test
    void colonnaNotNullConDefaultVuotaRifiutata() {
        ColumnDef naz = ColumnDef.of("nazionalita", "VARCHAR", "40").withNullable(false)
                .withDefault(ColumnDefault.literal("italiana"));
        assertThrows(ValueConverter.Rejected.class, () -> conv("", naz),
                "con l'istruzione preparata il valore predefinito non si può chiedere riga per riga");
    }
}
