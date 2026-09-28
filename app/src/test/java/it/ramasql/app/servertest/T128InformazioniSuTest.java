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

import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.app.AboutDialog;
import it.ramasql.app.theme.RamaSqlLaf;

/**
 * <b>T12.8</b>: «Informazioni su» aperta dal menu Aiuto del programma vero, collegato a ciascuno dei due server. La
 * finestra dice la licenza (GPL-3.0-or-later), le attribuzioni a SQLeo e SQLeonardo con i loro autori e la loro licenza,
 * dove trovare i sorgenti; la tabella delle librerie elenca <b>ogni</b> libreria di terze parti che il programma
 * contiene (confrontata con i jar da cui il programma le carica davvero) con una licenza compatibile con la GPL-3. Le
 * promesse della finestra sono vere: il file della licenza esiste, la versione portabile porta con sé i sorgenti
 * ({@code scripts/crea-portabile.ps1}), le modifiche a SQLeo sono in {@code sqleo-qb/UPSTREAM.md}.
 */
@Tag("step12")
@Tag("ui")
@Tag("it")
class T128InformazioniSuTest {

    /** Libreria (riga della tabella) → un suo tipo, per trovare il jar da cui il programma la carica. */
    private static final Map<String, List<String>> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put("flatlaf", List.of("com.formdev.flatlaf.FlatLaf", "com.formdev.flatlaf.extras.FlatSVGIcon"));
        CLASSES.put("jsvg", List.of("com.github.weisj.jsvg.SVGDocument"));
        CLASSES.put("rsyntax", List.of("org.fife.ui.rsyntaxtextarea.RSyntaxTextArea",
                "org.fife.ui.autocomplete.AutoCompletion"));
        CLASSES.put("mariadb", List.of("org.mariadb.jdbc.Driver"));
        CLASSES.put("jackson", List.of("com.fasterxml.jackson.databind.ObjectMapper",
                "com.fasterxml.jackson.core.JsonFactory", "com.fasterxml.jackson.annotation.JsonProperty"));
    }

    /** Jar che servono solo alle prove (non entrano nel programma distribuito). */
    private static final List<String> TEST_ONLY = List.of("junit-", "opentest4j", "apiguardian", "surefire",
            "maven-", "plexus-", "commons-", "assertj", "byte-buddy", "hamcrest",
            // arrivano con JUnit 6 (ambito test)
            "jspecify-", "open-test-reporting-");

    private static final Set<String> GPL_COMPATIBLE = Set.of("Apache License 2.0", "MIT License", "BSD 3-Clause",
            "GNU LGPL 2.1 o successiva", "GNU GPL 2 con Classpath Exception");

    @TempDir
    Path dataDir;

    @BeforeAll
    static void setup() {
        Probe.setup();
        onEdt(RamaSqlLaf::setup);
    }

    private static String jarOf(String className) throws Exception {
        Class<?> c = Class.forName(className);
        return new File(c.getProtectionDomain().getCodeSource().getLocation().toURI()).getName();
    }

    @ParameterizedTest
    @EnumSource(DbServer.class)
    void t128_informazioniSuDiceLicenzaAttribuzioniLibrerieESorgenti(DbServer server) throws Exception {
        StringBuilder ev = new StringBuilder("T12.8 — «Informazioni su» dal programma collegato a " + server.label()
                + "\n");
        List<String> problems = new ArrayList<>();
        try (ClientApp a = ClientApp.connect(server, dataDir)) {
            // Aiuto › Informazioni, dal menu vero (la finestra è modale: si apre dopo, dall'EDT)
            SwingUtilities.invokeLater(() -> ClientApp.menuItem(a.frame().getJMenuBar().getMenu(1).getPopupMenu(),
                    "menu.help.about").doClick());
            waitUntil("finestra Informazioni", ClientApp.TIMEOUT, () -> about() != null);
            AboutDialog d = fromEdt(T128InformazioniSuTest::about);
            String text = fromEdt(d::allText);
            onEdt(() -> Probe.paintWindow("step12", d, "T12.8-informazioni-" + server.name().toLowerCase() + ".png"));
            for (String must : List.of("GPL-3.0-or-later", "versione 3", "senza alcuna garanzia", "LICENZA.txt",
                    "SQLeo Visual Query Builder", "anudeepgade", "SQLeonardo", "nickyb", "JasperSoft",
                    "GNU GPL versione 2 o successiva", "RamaSQL-sorgenti.zip", "sqleo-qb/UPSTREAM.md")) {
                if (!text.contains(must)) {
                    problems.add("il testo non dice «" + must + "»");
                }
            }
            // la tabella delle librerie, com'è a schermo
            JTable libs = fromEdt(() -> firstTable(d));
            Map<String, String[]> rows = new LinkedHashMap<>();
            onEdt(() -> {
                for (int r = 0; r < libs.getRowCount(); r++) {
                    rows.put(AboutDialog.LIBRARIES.get(r), new String[] {String.valueOf(libs.getValueAt(r, 0)),
                            String.valueOf(libs.getValueAt(r, 1)), String.valueOf(libs.getValueAt(r, 2))});
                }
            });
            ev.append("Librerie elencate nella finestra:\n");
            rows.forEach((k, v) -> ev.append("  ").append(v[0]).append(" — ").append(v[1]).append(" — ").append(v[2])
                    .append('\n'));
            for (Map.Entry<String, String[]> e : rows.entrySet()) {
                if (!GPL_COMPATIBLE.contains(e.getValue()[2])) {
                    problems.add(e.getValue()[0] + ": licenza «" + e.getValue()[2] + "» non fra quelle compatibili");
                }
            }
            // ogni jar di terze parti da cui il programma carica le classi è elencato
            Map<String, String> jarToRow = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : CLASSES.entrySet()) {
                for (String cls : e.getValue()) {
                    jarToRow.put(jarOf(cls), e.getKey());
                }
            }
            Set<String> classpathJars = new LinkedHashSet<>();
            for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
                String name = new File(entry).getName();
                if (name.endsWith(".jar") && !name.startsWith("ramasql-")
                        && TEST_ONLY.stream().noneMatch(name::startsWith)) {
                    classpathJars.add(name);
                }
            }
            ev.append("Jar di terze parti del programma (dal classpath, tolti quelli delle sole prove):\n");
            for (String jar : classpathJars) {
                String row = jarToRow.get(jar);
                ev.append("  ").append(jar).append(" → ").append(row == null ? "NON ELENCATO" : rows.get(row)[0])
                        .append('\n');
                if (row == null || !rows.containsKey(row)) {
                    problems.add(jar + ": libreria contenuta nel programma ma non elencata");
                }
            }
            // e la versione portabile più recente, se c'è: sono i jar distribuiti davvero
            Path dist = Probe.projectRoot().resolve("dist");
            if (Files.isDirectory(dist)) {
                try (var dirs = Files.list(dist)) {
                    Path latest = dirs.filter(dd -> Files.isDirectory(dd.resolve("RamaSQL/app")))
                            .max(java.util.Comparator.comparing(dd -> dd.getFileName().toString())).orElse(null);
                    if (latest != null) {
                        ev.append("Jar della versione portabile ").append(latest.getFileName()).append(":\n");
                        try (var jars = Files.list(latest.resolve("RamaSQL/app"))) {
                            for (Path jar : jars.filter(j -> j.toString().endsWith(".jar")).toList()) {
                                String name = jar.getFileName().toString();
                                if (name.startsWith("ramasql-")) {
                                    continue;
                                }
                                String row = jarToRow.get(name);
                                ev.append("  ").append(name).append(" → ")
                                        .append(row == null ? "NON ELENCATO" : rows.get(row)[0]).append('\n');
                                if (row == null) {
                                    problems.add(name + ": nella versione portabile ma non elencata");
                                }
                            }
                        }
                    }
                }
            }
            if (!rows.containsKey("java")) {
                problems.add("manca il runtime Java incluso nella versione portabile");
            }
            // le promesse della finestra sono vere
            Path root = Probe.projectRoot();
            String license = Files.readString(root.resolve("LICENSE"), StandardCharsets.UTF_8);
            check(problems, license.contains("GNU GENERAL PUBLIC LICENSE") && license.contains("Version 3"),
                    "LICENSE non è il testo della GPL-3");
            String portable = Files.readString(root.resolve("scripts/crea-portabile.ps1"), StandardCharsets.UTF_8);
            check(problems, portable.contains("'LICENZA.txt'"), "la versione portabile non porta LICENZA.txt");
            check(problems, portable.contains("git archive") && portable.contains("RamaSQL-sorgenti.zip"),
                    "la versione portabile non porta i sorgenti");
            String upstream = Files.readString(root.resolve("sqleo-qb/UPSTREAM.md"), StandardCharsets.UTF_8);
            check(problems, upstream.contains("Step 12"), "sqleo-qb/UPSTREAM.md non elenca le ultime modifiche");
            String leggimi = Files.readString(root.resolve("packaging/LEGGIMI.txt"), StandardCharsets.UTF_8);
            check(problems, leggimi.contains("RamaSQL-sorgenti.zip") && leggimi.contains("GPL-3.0-or-later"),
                    "LEGGIMI.txt non dice licenza e sorgenti");
            ev.append("Controlli: testo (licenza, garanzia, attribuzioni SQLeo/SQLeonardo/JasperSoft, sorgenti), ")
                    .append("licenze compatibili, jar elencati, LICENSE = GPL-3, crea-portabile.ps1 copia LICENZA.txt ")
                    .append("e crea RamaSQL-sorgenti.zip, UPSTREAM.md aggiornato, LEGGIMI.txt\n");
            onEdt(d::dispose);
        }
        ev.append("Problemi: ").append(problems.size()).append('\n');
        problems.forEach(p -> ev.append("  ").append(p).append('\n'));
        ev.append("Esito: ").append(problems.isEmpty() ? "OK" : "FALLITO").append('\n');
        Probe.writeText("step12", "T12.8-informazioni-" + server.name().toLowerCase() + ".txt", ev.toString());
        assertEquals(List.of(), problems);
        assertTrue(rows(ev) >= 6, "librerie elencate");
    }

    private static int rows(StringBuilder ev) {
        return (int) ev.toString().lines().filter(l -> l.startsWith("  ") && l.contains(" — ")).count();
    }

    private static void check(List<String> problems, boolean ok, String problem) {
        if (!ok) {
            problems.add(problem);
        }
    }

    private static JTable firstTable(java.awt.Container c) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JTable t) {
                return t;
            }
            if (child instanceof java.awt.Container k) {
                JTable t = firstTable(k);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static AboutDialog about() {
        for (Window w : Window.getWindows()) {
            if (w instanceof AboutDialog d && d.isShowing()) {
                return d;
            }
        }
        return null;
    }
}
