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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.tree.TreePath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ramasql.app.AboutDialog;
import it.ramasql.app.GuideDialog;
import it.ramasql.app.dump.DumpWizard;
import it.ramasql.app.dump.ScriptRunTab;
import it.ramasql.app.editor.SqlEditor;
import it.ramasql.app.er.ErModelWindow;
import it.ramasql.app.grid.DataGrid;
import it.ramasql.app.importer.ImportWizard;
import it.ramasql.app.navigator.NavNode;
import it.ramasql.app.tableeditor.TableEditor;
import it.ramasql.app.theme.RamaSqlLaf;
import it.ramasql.app.workspace.FilePrompts;
import it.ramasql.core.metadata.TableDef;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * <b>T12.5</b>: una sessione d'uso completa del programma vero, prima con MariaDB e poi con MySQL (collegamento,
 * navigatore, griglia dei dati, editor SQL con una modifica confermata, query visiva, editor di tabelle, importazione
 * di un CSV, dump, esecuzione del file di dump, modello ER dal database, guida rapida e «Informazioni su»,
 * scollegamento), mentre due «monitor di rete» guardano il processo: <b>Get-NetTCPConnection</b> di Windows (quello
 * che mostra il Monitor risorse), interrogato ogni mezzo secondo sul PID del programma, e il registratore di Java (JFR)
 * con ogni lettura e scrittura su socket. Risultato atteso: connessioni <b>solo</b> verso i due server di database
 * (127.0.0.1:3306 e 127.0.0.1:3307), nessuna porta in ascolto aperta dal programma. Le righe «Bound» che Windows
 * mostra con la stessa porta locale di una connessione verso il server sono il lato locale di quella connessione.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class T125SoloIlServerTest {

    private static final Set<Integer> DB_PORTS = Set.of(3306, 3307);

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
        onEdt(RamaSqlLaf::setup);
    }

    /** Il monitor di Windows: le connessioni TCP del processo, lette di continuo finché la sessione dura. */
    private static Thread sampler(long pid, Set<String> seen, AtomicBoolean running, AtomicInteger samples) {
        Thread t = new Thread(() -> {
            String cmd = "Get-NetTCPConnection -OwningProcess " + pid + " -ErrorAction SilentlyContinue | "
                    + "ForEach-Object { $_.State.ToString() + ' ' + $_.LocalAddress + ':' + $_.LocalPort + ' > ' "
                    + "+ $_.RemoteAddress + ':' + $_.RemotePort }";
            while (running.get()) {
                try {
                    Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", cmd)
                            .redirectErrorStream(true).start();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(),
                            StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (!line.isBlank()) {
                                seen.add(line.strip());
                            }
                        }
                    }
                    p.waitFor();
                    samples.incrementAndGet();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    seen.add("ERRORE del monitor: " + e);
                }
            }
        }, "T12.5-monitor");
        t.setDaemon(true);
        return t;
    }

    /** Una riga del monitor è permessa se va verso uno dei due server locali; nessuna porta in ascolto. */
    static boolean allowed(String line) {
        String[] parts = line.split(" ");
        if (parts.length < 4 || !parts[2].equals(">")) {
            return false;
        }
        String state = parts[0];
        String remote = parts[3];
        int colon = remote.lastIndexOf(':');
        String host = remote.substring(0, colon);
        int port = Integer.parseInt(remote.substring(colon + 1));
        if (state.equals("Listen") || state.equals("Bound")) {
            return false;
        }
        return (host.equals("127.0.0.1") || host.equals("::1")) && DB_PORTS.contains(port);
    }

    private static String localPort(String line) {
        String local = line.split(" ")[1];
        return local.substring(local.lastIndexOf(':') + 1);
    }

    @Test
    void allowedRiconosceSoloIServerLocali() {
        assertTrue(allowed("Established 127.0.0.1:51234 > 127.0.0.1:3306"));
        assertTrue(allowed("TimeWait ::1:51234 > ::1:3307"));
        assertTrue(!allowed("Established 192.168.1.5:51234 > 151.101.1.69:443"));
        assertTrue(!allowed("Listen 0.0.0.0:5005 > 0.0.0.0:0"));
        assertTrue(!allowed("Established 127.0.0.1:51234 > 127.0.0.1:8080"));
        assertTrue(!allowed("Bound :::59775 > :::0"));
        assertEquals("59775", localPort("Bound :::59775 > :::0"));
    }

    @Test
    void t125_unaSessioneCompletaParlaSoloConIServerDiDatabase() throws Exception {
        long pid = ProcessHandle.current().pid();
        Set<String> seen = new ConcurrentSkipListSet<>();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger samples = new AtomicInteger();
        StringBuilder ev = new StringBuilder("T12.5 — connessioni di rete durante una sessione d'uso completa (PID "
                + pid + ")\n");
        List<String> steps = new ArrayList<>();
        Set<String> jfr = new TreeSet<>();
        Thread monitor = sampler(pid, seen, running, samples);
        try (Recording rec = new Recording()) {
            rec.enable("jdk.SocketRead").withThreshold(Duration.ZERO);
            rec.enable("jdk.SocketWrite").withThreshold(Duration.ZERO);
            rec.start();
            monitor.start();
            waitUntil("prima lettura del monitor", 60_000, () -> samples.get() > 0);
            for (DbServer server : DbServer.values()) {
                session(server, steps);
            }
            waitUntil("ultima lettura del monitor", 60_000, () -> samples.get() > 2);
            int last = samples.get();
            waitUntil("lettura dopo la sessione", 60_000, () -> samples.get() > last);
            running.set(false);
            monitor.join(30_000);
            rec.stop();
            Path file = dataDir.resolve("t125.jfr");
            rec.dump(file);
            for (RecordedEvent e : RecordingFile.readAllEvents(file)) {
                jfr.add(e.getString("address") + ":" + e.getInt("port") + " (" + e.getString("host") + ")");
            }
        } finally {
            running.set(false);
        }
        List<String> problems = new ArrayList<>();
        // «Bound» con indirizzo remoto vuoto: Windows lo mostra per la porta locale di un socket in uscita (la stessa
        // porta di una connessione verso il server); è un problema solo se quella porta non appartiene a una di loro
        Set<String> clientPorts = new TreeSet<>();
        for (String line : seen) {
            if (allowed(line)) {
                clientPorts.add(localPort(line));
            }
        }
        for (String line : seen) {
            boolean clientSocket = line.startsWith("Bound ") && clientPorts.contains(localPort(line));
            if (!allowed(line) && !clientSocket) {
                problems.add("Get-NetTCPConnection: " + line);
            }
        }
        for (String endpoint : jfr) {
            String addr = endpoint.substring(0, endpoint.indexOf(' '));
            int colon = addr.lastIndexOf(':');
            String host = addr.substring(0, colon);
            int port = Integer.parseInt(addr.substring(colon + 1));
            if (!(host.equals("127.0.0.1") || host.equals("0:0:0:0:0:0:0:1")) || !DB_PORTS.contains(port)) {
                problems.add("JFR: traffico verso " + endpoint);
            }
        }
        ev.append("Sessione:\n");
        steps.forEach(s -> ev.append("  ").append(s).append('\n'));
        ev.append("Monitor di Windows (Get-NetTCPConnection sul PID, ").append(samples.get())
                .append(" letture): connessioni viste\n");
        seen.forEach(s -> ev.append("  ").append(s).append('\n'));
        ev.append("Registratore di Java (JFR, letture e scritture su socket): destinazioni\n");
        jfr.forEach(s -> ev.append("  ").append(s).append('\n'));
        ev.append("Problemi: ").append(problems.size()).append('\n');
        problems.forEach(p -> ev.append("  ").append(p).append('\n'));
        ev.append("Esito: ").append(problems.isEmpty() ? "OK" : "FALLITO").append('\n');
        Probe.writeText("step12", "T12.5-rete.txt", ev.toString());
        assertEquals(List.of(), problems);
        assertTrue(samples.get() >= 5, "il monitor ha letto più volte: " + samples.get());
        assertTrue(seen.stream().anyMatch(l -> l.endsWith(":3306")) && seen.stream().anyMatch(l -> l.endsWith(":3307")),
                "il monitor ha visto le connessioni ai due server: " + seen);
        assertTrue(jfr.stream().anyMatch(s -> s.contains(":3306")) && jfr.stream().anyMatch(s -> s.contains(":3307")),
                "JFR ha visto il traffico con i due server: " + jfr);
    }

    /** Una sessione d'uso completa su un server, dal collegamento allo scollegamento. */
    private void session(DbServer server, List<String> steps) throws Exception {
        String catalog = DbServer.newCatalogName("t125");
        Path dir = dataDir.resolve(server.name().toLowerCase());
        Files.createDirectories(dir);
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, dir)) {
                a.ws.onPreview = d -> d.executeButton().doClick();
                steps.add(server.label() + ": collegamento");
                a.expand(NavNode.Kind.CATALOG, catalog, catalog);
                TreePath tables = fromEdt(() -> a.nav().find(NavNode.Kind.TABLES, catalog, null));
                a.expandPath(tables);
                a.expand(NavNode.Kind.TABLE, catalog, "libri");
                steps.add(server.label() + ": navigatore aperto fino alle colonne di libri");
                TableDef libri = a.workspace().reader().table(catalog, "libri").orElseThrow();
                DataGrid grid = fromEdt(() -> a.frame().openDataEntry(catalog, libri));
                waitUntil("righe della griglia", ClientApp.TIMEOUT, () -> grid.table().getRowCount() > 0);
                steps.add(server.label() + ": griglia dei dati di libri");
                SqlEditor editor = fromEdt(a.frame()::openSqlEditor);
                onEdt(() -> editor.setText("SELECT COUNT(*) FROM `" + catalog + "`.libri;\nUPDATE `" + catalog
                        + "`.soci SET nome = nome WHERE id = 1;"));
                onEdt(editor::runAll);
                waitUntil("editor SQL", ClientApp.TIMEOUT, () -> !editor.isRunning());
                a.waitIdle();
                steps.add(server.label() + ": editor SQL, una SELECT e un UPDATE confermato");
                var visual = VisualSupport.openFromToolbar(a, catalog);
                steps.add(server.label() + ": query visiva aperta (" + fromEdt(() -> visual.getName()) + ")");
                TableEditor structure = fromEdt(() -> a.frame().openTableEditor(catalog, libri));
                steps.add(server.label() + ": editor di tabelle di libri (" + fromEdt(structure::tabs).getTabCount()
                        + " linguette)");
                ImportWizard imp = ImportSupport.fromToolbar(a, catalog);
                ImportSupport.choose(a, imp, ImportSupport.fixture("soci.csv"));
                ImportSupport.toPreview(imp);
                onEdt(() -> imp.newRadio().doClick());
                onEdt(() -> imp.newNameField().setText("soci_importati"));
                ImportSupport.toTarget(imp);
                ImportSupport.runToEnd(a, imp);
                assertTrue(server.tableExists(catalog, "soci_importati"), "importazione in una tabella nuova");
                steps.add(server.label() + ": importazione di soci.csv in una tabella nuova");
                DumpWizard dump = DumpUiSupport.fromToolbar(a);
                onEdt(dump::clearSelection);
                onEdt(() -> {
                    for (int i = 0; i < dump.catalogTable().getRowCount(); i++) {
                        if (catalog.equals(dump.catalogTable().getValueAt(i, 1))) {
                            dump.catalogTable().setValueAt(Boolean.TRUE, i, 0);
                        }
                    }
                });
                waitUntil("oggetti letti", ClientApp.TIMEOUT, () -> !dump.isLoading() && !dump.selection().isEmpty());
                onEdt(() -> dump.nextButton().doClick());
                Path file = dir.resolve("dump.sql");
                a.ws.filesToSave.put(FilePrompts.Purpose.DUMP, file);
                onEdt(dump::chooseFile);
                onEdt(() -> dump.nextButton().doClick());
                onEdt(() -> dump.nextButton().doClick());
                waitUntil("dump finito", 300_000, () -> !dump.isRunning() && dump.result() != null);
                steps.add(server.label() + ": dump del catalogo (" + Files.size(file) + " byte)");
                ScriptRunTab script = DumpUiSupport.scriptTab(a);
                a.ws.filesToOpen.put(FilePrompts.Purpose.RUN_SCRIPT, file);
                onEdt(script::chooseFile);
                DumpUiSupport.awaitScan(script);
                onEdt(() -> script.targetCombo().setSelectedItem(catalog));
                DumpUiSupport.runAndWait(a, script);
                steps.add(server.label() + ": il file di dump rieseguito sul catalogo");
                int before = fromEdt(() -> a.frame().erWindows().size());
                onEdt(() -> a.nav().tree().setSelectionPath(a.nav().find(NavNode.Kind.CATALOG, catalog, catalog)));
                onEdt(() -> ClientApp.menuItem(a.frame().erMenu(), "er.menu.new").doClick());
                waitUntil("modello ER", ClientApp.TIMEOUT, () -> !a.frame().isErLoading()
                        && a.frame().erWindows().size() == before + 1);
                ErModelWindow er = fromEdt(() -> a.frame().erWindows().get(a.frame().erWindows().size() - 1));
                steps.add(server.label() + ": modello ER dal database (" + fromEdt(() -> er.panel().model().entities()
                        .size()) + " entità)");
                onEdt(() -> {
                    new GuideDialog(a.frame()).dispose();
                    new AboutDialog(a.frame()).dispose();
                });
                steps.add(server.label() + ": guida rapida e «Informazioni su»");
            }
            steps.add(server.label() + ": scollegamento");
        } finally {
            server.dropQuietly(catalog);
        }
    }
}
