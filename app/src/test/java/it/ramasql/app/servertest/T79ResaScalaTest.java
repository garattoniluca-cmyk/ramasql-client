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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T7.9 — resa della query visiva alla scala dello schermo del <b>100%</b> e del <b>150%</b>, sul programma vero
 * collegato a MariaDB e a MySQL. Ogni scala gira in una JVM a sé ({@code -Dflatlaf.uiScale}, come lo spike S5), perché
 * la scala si fissa all'avvio. La scena ({@link T79Scena}) apre la scheda dal pulsante della barra, aggiunge tre
 * tabelle con quattro filtri (le icone dei filtri erano la causa di {@code BUG-011}) e prepara le maschere di join,
 * condizione, alias ed espressione; poi controlla ogni etichetta e pulsante con il calcolo di Swing: nessun testo
 * tagliato, nessuna entità più stretta del necessario, l'elenco delle tabelle visibile (almeno 5 righe), il diagramma
 * alto almeno il 40% della scheda, ogni casella di spunta contenuta nella riga del suo campo, la fascia d'avviso di una
 * query non disegnabile che va a capo senza tagliarsi. Evidenza: {@code test-results/step7/T7.9-<scala>-<server>.txt}
 * e {@code .png} (più {@code -avviso.png}).
 *
 * <p><b>Scala emulata:</b> la scala del 150% si ottiene con {@code -Dflatlaf.uiScale=1.5} e
 * {@code -Dsun.java2d.uiScale=1} (FlatLaf ingrandisce caratteri, icone e misure; la finestra si ingrandisce della stessa
 * misura), non con l'impostazione di Windows: il risultato sui testi è lo stesso, il disegno a basso livello no.
 */
@Tag("step7")
@Tag("ui")
@Tag("it")
class T79ResaScalaTest {

    @TempDir
    Path dataDir;

    static Stream<Arguments> casi() {
        List<Arguments> out = new ArrayList<>();
        for (int scala : new int[] {100, 150}) {
            for (DbServer s : DbServer.values()) {
                out.add(Arguments.of(scala, s));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0}% {1}")
    @MethodSource("casi")
    void nessunTestoTagliato(int scala, DbServer server) throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");
        Path dir = Probe.resultsDir("step7");
        String nome = "T7.9-" + scala + "-" + server.id();
        Path report = dir.resolve(nome + ".txt");
        Path png = dir.resolve(nome + ".png");
        Files.deleteIfExists(report);
        Files.deleteIfExists(png);
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Dflatlaf.uiScale=" + (scala / 100.0));
        cmd.add("-Dsun.java2d.uiScale=1");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(T79Scena.class.getName());
        cmd.add(server.name());
        cmd.add(dataDir.toString());
        cmd.add(report.toString());
        cmd.add(png.toString());
        File log = dir.resolve(nome + "-jvm.log").toFile();
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start();
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "la JVM della scena non ha finito in tempo");
        String rapporto = Files.exists(report) ? Files.readString(report, StandardCharsets.UTF_8) : "(nessun rapporto)";
        assertEquals(0, p.exitValue(), "scena a " + scala + "% su " + server.label() + ":\n" + rapporto + "\nlog:\n"
                + Files.readString(log.toPath(), StandardCharsets.UTF_8));
        assertTrue(rapporto.contains("problemi=0"), rapporto);
        assertTrue(Files.size(png) > 10_000, "immagine della scena");
    }
}
