/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Licenze del modulo (2026-09-22): nessun file con la sola GPL versione 2 (incompatibile con un prodotto
 * GPL-3.0-or-later) e nessuna immagine di origine incerta tra le risorse (le icone sono disegnate nel codice).
 */
@Tag("step1")
class LicenzeERisorseTest {

    private static Path moduleRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("mvnw.cmd"))) {
            p = p.getParent();
        }
        assertNotNull(p, "radice del progetto non trovata da " + System.getProperty("user.dir"));
        return p.resolve("sqleo-qb");
    }

    private static List<Path> files(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    /** Testo senza asterischi e barre dei commenti, spazi compattati, minuscolo: le frasi della licenza vanno a capo. */
    static String normalizza(String testo) {
        return testo.replaceAll("[*/#]", " ").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static boolean gpl2Only(String testo) {
        String t = normalizza(testo);
        return t.contains("version 2 as published") && !t.contains("any later version");
    }

    @Test
    void nessunFileConLaSolaGplVersione2() throws IOException {
        List<Path> sorgenti = files(moduleRoot().resolve("src").resolve("main"));
        assertTrue(sorgenti.size() > 50, "troppo pochi file esaminati: " + sorgenti.size());
        int conLicenzaGpl = 0;
        StringBuilder colpevoli = new StringBuilder();
        for (Path f : sorgenti) {
            String testo = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
            if (normalizza(testo).contains("gnu general public license")) {
                conLicenzaGpl++;
            }
            if (gpl2Only(testo)) {
                colpevoli.append('\n').append(f);
            }
        }
        assertTrue(conLicenzaGpl > 40, "intestazioni GPL trovate solo in " + conLicenzaGpl + " file: il controllo non guarda i sorgenti giusti");
        assertTrue(colpevoli.isEmpty(), "file con licenza GPL-2.0-only:" + colpevoli);
    }

    @Test
    void ilControlloRiconosceUnIntestazioneGpl2Only() {
        String soloV2 = """
                 * This program is free software; you can redistribute it and/or modify
                 * it under the terms of the GNU General Public License version 2 as published by
                 * the Free Software Foundation.""";
        String v2OSuccessive = """
                 * This program is free software; you can redistribute it and/or modify
                 * it under the terms of the GNU General Public License version 2 as published by
                 * the Free Software Foundation, or (at your option)
                 * any later version.""";
        assertTrue(gpl2Only(soloV2));
        assertFalse(gpl2Only(v2OSuccessive));
    }

    @Test
    void nessunaImmagineTraLeRisorse() throws IOException {
        Path risorse = moduleRoot().resolve("src").resolve("main").resolve("resources");
        assertTrue(Files.exists(risorse.resolve("it/ramasql/qb/qb_it.properties")), "cartella delle risorse sbagliata: " + risorse);
        List<Path> immagini = files(risorse).stream()
                .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|gif|jpe?g)$"))
                .toList();
        assertTrue(immagini.isEmpty(), "immagini nelle risorse del modulo: " + immagini);
    }
}
