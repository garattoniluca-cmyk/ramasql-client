/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.ramasql.qb.QbSql;

/**
 * Automazione dei test manuali S1 e S5 (docs/ROADMAP.md, Step 1).
 * S1: il query builder estratto da SQLeo gira su JDK 25 dentro un JFrame nostro, sotto FlatLaf chiaro, senza
 * Application né finestre interne di SQLeo, e senza database; una query a 5 tabelle produce 5 entità (limite di 3 tabelle rimosso).
 * S5: stessa scena a 100/150/200% (scala di FlatLaf, in JVM figlie) con i controlli di resa fattibili da programma.
 * Le immagini finiscono in {@code test-results/step1/}.
 */
@Tag("step1")
@Tag("ui")
class S1S5QueryBuilderResaTest {

    @Test
    void s1_cinqueTabelleNelDiagrammaSottoFlatLaf() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");

        Path png = SpikeFiles.step1Dir().resolve("S1-qb-5-tabelle.png");
        QbRender.Esito esito = QbRender.disegna(png, 1.0);

        assertEquals(5, esito.entita(), "entità nel diagramma (il limite di 3 tabelle di SQLeo deve essere sparito)");
        assertEquals(4, esito.relazioni(), "join disegnati tra le 5 tabelle");
        assertTrue(esito.avvisi().isEmpty(), "avvisi inattesi: " + esito.avvisi());
        // il modello caricato nel pannello rigenera la stessa query
        assertEquals(QbSql.normalize(QbRender.QUERY_5_TABELLE), QbSql.normalize(esito.sqlDelModello()),
                "SQL del pannello dopo il caricamento");
        assertImmagineNonVuota(png);
        SpikeFiles.write("S1-controlli.txt", QbRender.rapporto(esito));
    }

    @ParameterizedTest(name = "scala {0}%")
    @ValueSource(ints = {100, 150, 200})
    void s5_resaAllaScala(int percento) throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");

        Path dir = SpikeFiles.step1Dir();
        Path png = dir.resolve("S5-" + percento + ".png");
        Path rapporto = dir.resolve("S5-" + percento + "-controlli.txt");
        Files.deleteIfExists(png);
        Files.deleteIfExists(rapporto);

        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Dflatlaf.uiScale=" + (percento / 100.0));
        cmd.add("-Dsun.java2d.uiScale=1");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(QbRender.class.getName());
        cmd.add(png.toString());
        cmd.add(rapporto.toString());
        File log = dir.resolve("S5-" + percento + "-jvm.log").toFile();
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "la JVM figlia non ha finito in tempo");
        String uscita = Files.readString(log.toPath(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), "JVM figlia terminata con errore:\n" + uscita);
        assertFalse(uscita.contains("Exception"), "eccezioni durante il disegno:\n" + uscita);

        List<String> righe = Files.readAllLines(rapporto, StandardCharsets.UTF_8);
        assertTrue(righe.contains("entita=5"), "5 entità attese: " + righe);
        assertTrue(righe.contains("relazioni=4"), "4 relazioni attese: " + righe);
        assertTrue(righe.contains("scalaFlatLaf=" + (percento / 100.0f)), "scala di FlatLaf non applicata: " + righe);
        BufferedImage img = assertImmagineNonVuota(png);
        assertEquals(Math.round(1280 * percento / 100.0), img.getWidth(), 40, "larghezza dell'immagine in proporzione alla scala");

        // il controllo «≥ 2 px tra testo e bordo» deve essersi applicato davvero: 20 campi + 5 intestazioni delle entità
        int margini = righe.stream().filter(r -> r.startsWith("margini testo-bordo"))
                .mapToInt(r -> Integer.parseInt(r.substring(r.indexOf('=') + 1))).findFirst().orElse(0);
        assertTrue(margini >= 25, "margini testo-bordo controllati solo su " + margini + " testi");
        List<String> difetti = righe.stream().filter(r -> r.startsWith("difetto=")).toList();
        assertTrue(difetti.isEmpty(), "difetti di resa al " + percento + "%:\n" + String.join("\n", difetti));
    }

    /** Come il JRE in HiDPI su Windows: stessa disposizione, tutto ingrandito con una trasformazione. */
    @Test
    void s5_scalaDelJreConTrasformazioneGrafica() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "serve un ambiente grafico: il test non si salta");

        Path png = SpikeFiles.step1Dir().resolve("S5-150-scala-del-jre.png");
        QbRender.Esito esito = QbRender.disegna(png, 1.5);

        assertEquals(5, esito.entita());
        assertTrue(esito.difetti().isEmpty(), "difetti di resa: " + esito.difetti());
        assertImmagineNonVuota(png);
    }

    /** L'immagine esiste e non è una tinta unita (almeno 50 colori diversi su una griglia di campioni). */
    private static BufferedImage assertImmagineNonVuota(Path png) throws Exception {
        assertTrue(Files.size(png) > 10_000, "immagine troppo piccola: " + png);
        BufferedImage img = ImageIO.read(png.toFile());
        java.util.Set<Integer> colori = new java.util.HashSet<>();
        for (int x = 0; x < img.getWidth(); x += 7) {
            for (int y = 0; y < img.getHeight(); y += 7) {
                colori.add(img.getRGB(x, y));
            }
        }
        assertTrue(colori.size() >= 50, "immagine quasi vuota: solo " + colori.size() + " colori");
        return img;
    }
}
