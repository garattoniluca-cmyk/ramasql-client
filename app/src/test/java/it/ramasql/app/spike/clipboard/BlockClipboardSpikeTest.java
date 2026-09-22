/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app.spike.clipboard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatLightLaf;
import it.ramasql.app.spike.SpikeFiles;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spike S7 — appunti a blocchi. I test usano gli APPUNTI DI SISTEMA veri (è ciò che si vuole provare):
 * mentre girano, il contenuto degli appunti dell'utente viene sostituito. Prima del primo test si salva TUTTO il
 * contenuto degli appunti (ogni {@code DataFlavor} leggibile: testo, HTML, immagini, file…, vedi
 * {@link ClipboardSnapshot}) e dopo l'ultimo, anche se un test fallisce, lo si rimette.
 * Excel è pilotato via COM da PowerShell, invisibile; nessun controllo del desktop.
 * Tag {@code office}: questi test usano gli appunti di sistema, Excel e LibreOffice (restano {@code step1}+{@code ui}
 * e girano sempre; il tag serve solo a riconoscerli).
 */
@Tag("step1")
@Tag("ui")
@Tag("office")
class BlockClipboardSpikeTest {

    private static final String TAB = "via Roma\t12";
    private static final String A_CAPO = "riga uno\nriga due";
    private static final String VIRGOLETTE = "dice \"ciao\"";
    private static final String TUTTO_TRA_VIRGOLETTE = "\"tra virgolette\"";

    private static Clipboard clipboard;
    /** Tutto il contenuto degli appunti dell'utente (ogni flavor), preso prima dei test. */
    private static ClipboardSnapshot appuntiDellUtente;
    /** Solo per il controllo finale: il testo che c'era, se c'era. */
    private static String testoDellUtente;

    @BeforeAll
    static void prepara() {
        FlatLightLaf.setup();
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        appuntiDellUtente = ClipboardSnapshot.take(clipboard);
        try {
            testoDellUtente = BlockClipboard.getText(clipboard);
        } catch (RuntimeException e) {
            testoDellUtente = null;
        }
    }

    @AfterAll
    static void rimettiGliAppunti() {
        // @AfterAll gira anche quando un test fallisce: gli appunti dell'utente tornano come erano, in tutti i formati
        appuntiDellUtente.restore(clipboard);
        if (testoDellUtente != null && !testoDellUtente.isEmpty()) {
            assertEquals(testoDellUtente, BlockClipboard.getText(clipboard),
                    "appunti dell'utente non ripristinati (formati salvati: " + appuntiDellUtente.mimeTypes() + ")");
        }
    }

    /** Tabella 10×6 «r<riga>c<colonna>», con i valori difficili dentro il blocco righe 3–5 × colonne 1–4. */
    private static JTable tabella() throws Exception {
        JTable[] out = new JTable[1];
        SwingUtilities.invokeAndWait(() -> {
            String[] names = {"A", "B", "C", "D", "E", "F"};
            DefaultTableModel model = new DefaultTableModel(names, 0);
            for (int r = 0; r < 10; r++) {
                Object[] row = new Object[6];
                for (int c = 0; c < 6; c++) {
                    row[c] = "r" + r + "c" + c;
                }
                model.addRow(row);
            }
            model.setValueAt(TAB, 3, 2);
            model.setValueAt(A_CAPO, 3, 3);
            model.setValueAt(VIRGOLETTE, 4, 1);
            model.setValueAt(TUTTO_TRA_VIRGOLETTE, 4, 4);
            model.setValueAt("", 4, 2);
            model.setValueAt("àèìòù € 😀", 5, 3);
            JTable t = new JTable(model);
            BlockClipboard.install(t);
            out[0] = t;
        });
        return out[0];
    }

    private static List<List<String>> bloccoTrePerQuattro() {
        return List.of(
                List.of("r3c1", TAB, A_CAPO, "r3c4"),
                List.of(VIRGOLETTE, "", "r4c3", TUTTO_TRA_VIRGOLETTE),
                List.of("r5c1", "r5c2", "àèìòù € 😀", "r5c4"));
    }

    private static void seleziona(JTable t, int r1, int c1, int r2, int c2) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            t.changeSelection(r1, c1, false, false);
            t.changeSelection(r2, c2, false, true);
        });
    }

    private static void azione(JTable t, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> t.getActionMap().get(name)
                .actionPerformed(new ActionEvent(t, ActionEvent.ACTION_PERFORMED, name)));
    }

    /** Blocco 20×3 con i casi difficili a rotazione nella colonna centrale. */
    private static List<List<String>> bloccoVentiPerTre() {
        List<List<String>> block = new ArrayList<>();
        String[] difficili = {"con\ttabulazione", "prima riga\nseconda riga", "detto \"tra virgolette\"", "\"inizia con virgolette", "semplice"};
        for (int r = 0; r < 20; r++) {
            block.add(List.of("cod-" + (r + 1), difficili[r % difficili.length] + " " + (r + 1), "descrizione àè " + (r + 1)));
        }
        return block;
    }

    // ------------------------------------------------------------------ (1)

    @Test
    void copiaDiUnBloccoTrePerQuattro() throws Exception {
        JTable t = tabella();
        seleziona(t, 3, 1, 5, 4);
        assertEquals(bloccoTrePerQuattro(), BlockClipboard.selectedBlock(t));
        azione(t, "copy");

        String atteso = "r3c1\t\"via Roma\t12\"\t\"riga uno\nriga due\"\tr3c4\r\n"
                + "\"dice \"\"ciao\"\"\"\t\tr4c3\t\"\"\"tra virgolette\"\"\"\r\n"
                + "r5c1\tr5c2\tàèìòù € 😀\tr5c4\r\n";
        assertEquals(atteso, BlockClipboard.getText(clipboard), "testo negli appunti di sistema");
        assertEquals(bloccoTrePerQuattro(), BlockClipboard.parse(atteso), "andata e ritorno del testo");

        // evidenza: la tabella con il blocco selezionato, disegnata su un'immagine
        JPanel panel = new JPanel(new BorderLayout());
        SwingUtilities.invokeAndWait(() -> {
            panel.add(t.getTableHeader(), BorderLayout.NORTH);
            panel.add(t, BorderLayout.CENTER);
            panel.setSize(720, t.getRowHeight() * t.getRowCount() + 28);
            panel.doLayout();
            t.doLayout();
        });
        Path png = SpikeFiles.step1Dir().resolve("S7-blocco.png");
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                img[0] = SpikeFiles.paintToPng(panel, png);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertTrue(Files.size(png) > 2_000);
        int selezione = t.getSelectionBackground().getRGB();
        int pixelSelezionati = 0;
        for (int y = 0; y < img[0].getHeight(); y++) {
            for (int x = 0; x < img[0].getWidth(); x++) {
                if (img[0].getRGB(x, y) == selezione) {
                    pixelSelezionati++;
                }
            }
        }
        assertTrue(pixelSelezionati > 5_000, "nell'immagine si deve vedere il blocco selezionato (" + pixelSelezionati + " pixel)");
    }

    // ------------------------------------------------------------------ (2)

    @Test
    void incollaDiUnBloccoVentiPerTreCreaLeRighe() throws Exception {
        JTable t = tabella();
        List<List<String>> blocco = bloccoVentiPerTre();
        BlockClipboard.setText(clipboard, BlockClipboard.toText(blocco));
        seleziona(t, 8, 2, 8, 2); // cella attiva: riga 8, colonna C → servono 18 righe nuove
        azione(t, "paste");

        assertEquals(28, t.getRowCount(), "10 righe + 18 create");
        for (int r = 0; r < 20; r++) {
            for (int c = 0; c < 3; c++) {
                assertEquals(blocco.get(r).get(c), t.getValueAt(8 + r, 2 + c), "cella " + r + "," + c);
            }
        }
        assertEquals("r8c1", t.getValueAt(8, 1), "le celle fuori dal blocco non si toccano");
        assertEquals("r8c5", t.getValueAt(8, 5));
        assertEquals("r7c2", t.getValueAt(7, 2));
        assertEquals(null, t.getValueAt(27, 0), "nelle righe nuove le colonne non incollate restano vuote");
        assertArrayEquals(new int[] {8, 27}, new int[] {t.getSelectedRows()[0], t.getSelectedRows()[19]});
        assertArrayEquals(new int[] {2, 3, 4}, t.getSelectedColumns());

        // testo scritto da altri programmi: a-capo \n soltanto, nessun a-capo finale, riga con cella finale vuota
        assertEquals(List.of(List.of("a", "b"), List.of("c", "")), BlockClipboard.parse("a\tb\nc\t"));
        assertEquals(List.of(List.of("5\" di diametro", "x")), BlockClipboard.parse("5\" di diametro\tx\r\n"));
        // come scrive Excel VERO: virgolette non raddoppiate, anche a inizio cella; tra virgolette solo tab e a-capo
        assertEquals(List.of(List.of("\"inizia", "detto \"ciao\"", "con\ttab"), List.of("a\nb \"x\"", "")),
                BlockClipboard.parse("\"inizia\tdetto \"ciao\"\t\"con\ttab\"\r\n\"a\nb \"\"x\"\"\"\t\r\n"));
        // blocco più largo della tabella: le colonne in più si scartano, senza eccezioni
        BlockClipboard.setText(clipboard, "1\t2\t3\r\n");
        seleziona(t, 0, 4, 0, 4);
        assertArrayEquals(new int[] {1, 2, 0, 1}, BlockClipboard.paste(t, clipboard));
        assertEquals("2", t.getValueAt(0, 5));
    }

    // ------------------------------------------------------------------ (3) Excel vero

    @Test
    void excelRiceveIlBloccoTrePerQuattroNelleCelleGiuste(@TempDir Path tmp) throws Exception {
        JTable t = tabella();
        seleziona(t, 3, 1, 5, 4);
        azione(t, "copy");

        Path out = tmp.resolve("excel-letto.txt");
        String log = powershell(tmp, "-Mode", "Paste", "-OutFile", out.toString());
        assertTrue(Files.exists(out), "Excel non ha scritto il risultato. Uscita dello script:\n" + log);
        List<List<String>> letto = leggiBlocco(out);
        scriviEvidenza("S7-excel-incolla.txt", "JTable -> appunti -> Excel (Paste in A1), celle rilette da Excel con Value2:\n"
                + descrivi(letto) + "\nscript: " + log);
        // COMPORTAMENTO REALE: Java su Windows scrive negli appunti ogni LF come CR+LF (java.awt.datatransfer lo fa
        // sempre per il testo), quindi l'a-capo dentro la cella arriva in Excel come CR+LF invece del solo LF.
        // La cella è quella giusta e va a capo; tabulazione, virgolette, accenti ed emoji arrivano identici.
        List<List<String>> atteso = new ArrayList<>();
        for (List<String> row : bloccoTrePerQuattro()) {
            List<String> r = new ArrayList<>();
            for (String cell : row) {
                r.add(cell.replace("\n", "\r\n"));
            }
            atteso.add(r);
        }
        assertEquals(atteso, letto, "celle lette da Excel");
    }

    @Test
    void daExcelUnBloccoVentiPerTreCadeNelleCelleGiuste(@TempDir Path tmp) throws Exception {
        List<List<String>> blocco = bloccoVentiPerTre();
        Path in = tmp.resolve("excel-da-scrivere.txt");
        scriviBlocco(in, blocco);
        Path ready = tmp.resolve("pronto.flag");
        Path done = tmp.resolve("letto.flag");
        BlockClipboard.setText(clipboard, "appunti da sovrascrivere");

        Process p = avviaPowershell(tmp, "-Mode", "Copy", "-InFile", in.toString(),
                "-ReadyFile", ready.toString(), "-DoneFile", done.toString());
        String testo;
        try {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (!Files.exists(ready) && p.isAlive() && System.nanoTime() < limite) {
                Thread.sleep(100);
            }
            assertTrue(Files.exists(ready), "Excel non ha copiato il blocco. Uscita dello script:\n" + uscita(p, tmp));
            testo = BlockClipboard.getText(clipboard); // Excel è ancora vivo: gli appunti sono i suoi
        } finally {
            Files.writeString(done, "letto");
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        }
        String log = uscita(p, tmp);
        assertEquals(0, p.exitValue(), "script Excel fallito: " + log);

        JTable t = tabella();
        BlockClipboard.setText(clipboard, testo); // Excel ha chiuso: si rimette lo stesso testo per l'azione Incolla
        seleziona(t, 2, 1, 2, 1);
        azione(t, "paste");
        List<List<String>> incollato = new ArrayList<>();
        for (int r = 0; r < 20; r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < 3; c++) {
                row.add(2 + r < t.getRowCount() ? String.valueOf(t.getValueAt(2 + r, 1 + c)) : "<riga mancante>");
            }
            incollato.add(row);
        }
        scriviEvidenza("S7-excel-copia.txt", "Excel (Range.Copy di 20x3 celle di testo) -> appunti -> JTable.\n"
                + "Testo grezzo negli appunti (\\t = <TAB>, \\r = <CR>, \\n = <LF>):\n"
                + testo.replace("\t", "<TAB>").replace("\r", "<CR>").replace("\n", "<LF>\n")
                + "\nCelle nella JTable:\n" + descrivi(incollato) + "\nscript: " + log);
        assertEquals(22, t.getRowCount(), "10 righe iniziali, blocco dalla riga 2 alla 21: 12 righe nuove");
        assertEquals(blocco, incollato, "celle incollate nella JTable");
        assertEquals("r2c0", t.getValueAt(2, 0));
        assertEquals("r2c4", t.getValueAt(2, 4));
    }

    // ------------------------------------------------------------------ (4) LibreOffice Calc vero

    /**
     * JTable → appunti → Calc. Calc, quando s'incolla TESTO semplice, apre la finestra «Importazione testo»:
     * senza interfaccia ({@code --headless}) quella finestra non può comparire e {@code .uno:Paste} non fa nulla
     * (provato). Lo script fa quindi leggere gli appunti di sistema A LIBREOFFICE e passa il testo allo stesso
     * motore d'importazione con le opzioni predefinite di quella finestra (tabulazione, virgolette).
     * NON è quindi un Ctrl+V letterale: il Ctrl+V a mano resta un controllo per l'utente.
     */
    @Test
    void calcRiceveIlBloccoTrePerQuattroNelleCelleGiuste(@TempDir Path tmp) throws Exception {
        JTable t = tabella();
        seleziona(t, 3, 1, 5, 4);
        azione(t, "copy");

        Path out = tmp.resolve("calc-letto.txt");
        Process p = avviaCalc(tmp, "paste", out.toString());
        if (!p.waitFor(180, TimeUnit.SECONDS)) {
            p.destroyForcibly();
        }
        String log = uscita(p, tmp);
        assertEquals(0, p.exitValue(), "script Calc fallito: " + log);
        List<List<String>> letto = leggiBlocco(out);
        scriviEvidenza("S7-calc-incolla.txt", "JTable -> appunti di sistema -> letti da LibreOffice -> motore "
                + "\"Importazione testo\" di Calc (tab, virgolette), celle rilette con .String:\n" + descrivi(letto)
                + "\nscript: " + log);
        assertEquals(bloccoTrePerQuattro(), letto, "celle lette da Calc");
    }

    /**
     * Calc → appunti → JTable, con {@code .uno:Copy} vero (funziona anche senza interfaccia).
     * COMPORTAMENTO REALE di Calc: nel formato testo degli appunti NON usa virgolette e sostituisce con uno
     * spazio le tabulazioni e gli a-capo che stanno dentro una cella. Le celle cadono al posto giusto, ma quei due
     * caratteri si perdono all'origine (li conserva solo il formato HTML degli appunti, che il prototipo non legge).
     */
    @Test
    void daCalcUnBloccoVentiPerTreCadeNelleCelleGiuste(@TempDir Path tmp) throws Exception {
        List<List<String>> blocco = bloccoVentiPerTre();
        Path in = tmp.resolve("calc-da-scrivere.txt");
        scriviBlocco(in, blocco);
        Path ready = tmp.resolve("pronto.flag");
        Path done = tmp.resolve("letto.flag");
        BlockClipboard.setText(clipboard, "appunti da sovrascrivere");

        Process p = avviaCalc(tmp, "copy", in.toString(), ready.toString(), done.toString());
        String testo;
        try {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
            while (!Files.exists(ready) && p.isAlive() && System.nanoTime() < limite) {
                Thread.sleep(100);
            }
            assertTrue(Files.exists(ready), "Calc non ha copiato il blocco. Uscita dello script:\n" + uscita(p, tmp));
            // Su Windows Calc pubblica gli appunti in modo differito e sotto carico il testo può arrivare
            // un momento dopo il segnale «copiato»: si aspetta (max 15 s) che ci sia, poi si controlla tutto.
            testo = BlockClipboard.getText(clipboard);
            long attesa = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (testo.isEmpty() && System.nanoTime() < attesa) {
                Thread.sleep(200);
                testo = BlockClipboard.getText(clipboard);
            }
            assertFalse(testo.isEmpty(), "negli appunti non è arrivato nulla da Calc entro 15 s");
        } finally {
            Files.writeString(done, "letto");
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        }
        String log = uscita(p, tmp);
        assertEquals(0, p.exitValue(), "script Calc fallito: " + log);

        JTable t = tabella();
        BlockClipboard.setText(clipboard, testo);
        seleziona(t, 2, 1, 2, 1);
        azione(t, "paste");
        List<List<String>> incollato = new ArrayList<>();
        for (int r = 0; r < 20; r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < 3; c++) {
                row.add(2 + r < t.getRowCount() ? String.valueOf(t.getValueAt(2 + r, 1 + c)) : "<riga mancante>");
            }
            incollato.add(row);
        }
        scriviEvidenza("S7-calc-copia.txt", "Calc (.uno:Copy di 20x3 celle di testo) -> appunti -> JTable.\n"
                + "Testo grezzo negli appunti:\n"
                + testo.replace("\t", "<TAB>").replace("\r", "<CR>").replace("\n", "<LF>\n")
                + "\nCelle nella JTable:\n" + descrivi(incollato) + "\nscript: " + log);
        List<List<String>> atteso = new ArrayList<>();
        for (List<String> row : blocco) {
            List<String> r = new ArrayList<>();
            for (String cell : row) {
                r.add(cell.replace('\t', ' ').replace('\n', ' ')); // lo fa Calc, non il client
            }
            atteso.add(r);
        }
        assertEquals(22, t.getRowCount());
        assertEquals(atteso, incollato, "celle incollate nella JTable (tab e a-capo in cella: spazio, per scelta di Calc)");
        assertEquals("r2c0", t.getValueAt(2, 0));
        assertEquals("r2c4", t.getValueAt(2, 4));
    }

    private static Process avviaCalc(Path tmp, String mode, String... files) throws Exception {
        Path python = Path.of("C:\\Program Files\\LibreOffice\\program\\python.exe");
        assertTrue(Files.exists(python), "LibreOffice non trovato: " + python);
        Path py = tmp.resolve("calc-roundtrip.py");
        try (var in = BlockClipboardSpikeTest.class.getResourceAsStream("calc-roundtrip.py")) {
            Files.copy(in, py);
        }
        int port;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            port = s.getLocalPort();
        }
        // profilo di LibreOffice separato da quello dell'utente, riusato tra i test (il primo avvio lo crea)
        Path profile = Path.of(System.getProperty("user.dir"), "target", "lo-profile").toAbsolutePath();
        List<String> cmd = new ArrayList<>(List.of(python.toString(), py.toString(), mode, profile.toString(),
                String.valueOf(port)));
        cmd.addAll(List.of(files));
        return new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(tmp.resolve("powershell.log").toFile()).start();
    }

    // ------------------------------------------------------------------ PowerShell

    private static Path script(Path tmp) throws Exception {
        Path ps1 = tmp.resolve("excel-roundtrip.ps1");
        if (!Files.exists(ps1)) {
            try (var in = BlockClipboardSpikeTest.class.getResourceAsStream("excel-roundtrip.ps1")) {
                Files.copy(in, ps1);
            }
        }
        return ps1;
    }

    private static Process avviaPowershell(Path tmp, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script(tmp).toString()));
        cmd.addAll(List.of(args));
        return new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(tmp.resolve("powershell.log").toFile()).start();
    }

    private static String uscita(Process p, Path tmp) throws Exception {
        Path log = tmp.resolve("powershell.log");
        String text = Files.exists(log) ? Files.readString(log, StandardCharsets.ISO_8859_1).strip() : "";
        return text + (p.isAlive() ? " [ancora in esecuzione]" : " [exit " + p.exitValue() + "]");
    }

    private static String powershell(Path tmp, String... args) throws Exception {
        Process p = avviaPowershell(tmp, args);
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("PowerShell/Excel non ha finito entro 120 s: " + uscita(p, tmp));
        }
        String log = uscita(p, tmp);
        assertEquals(0, p.exitValue(), "script Excel fallito: " + log);
        return log;
    }

    // ------------------------------------------------------------------ file di scambio (celle in Base64)

    private static void scriviBlocco(Path file, List<List<String>> block) throws Exception {
        List<String> lines = new ArrayList<>();
        for (List<String> row : block) {
            List<String> cells = new ArrayList<>();
            for (String cell : row) {
                cells.add(Base64.getEncoder().encodeToString(cell.getBytes(StandardCharsets.UTF_8)));
            }
            lines.add(String.join("|", cells));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static List<List<String>> leggiBlocco(Path file) throws Exception {
        List<List<String>> block = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            List<String> row = new ArrayList<>();
            for (String cell : line.split("\\|", -1)) {
                row.add(new String(Base64.getDecoder().decode(cell), StandardCharsets.UTF_8));
            }
            block.add(row);
        }
        return block;
    }

    private static String descrivi(List<List<String>> block) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : block) {
            List<String> cells = new ArrayList<>();
            for (String c : row) {
                cells.add("[" + c.replace("\t", "<TAB>").replace("\r", "<CR>").replace("\n", "<LF>") + "]");
            }
            sb.append(String.join(" ", cells)).append('\n');
        }
        return sb.toString();
    }

    private static void scriviEvidenza(String name, String text) throws Exception {
        Files.writeString(SpikeFiles.step1Dir().resolve(name), text, StandardCharsets.UTF_8);
    }
}
