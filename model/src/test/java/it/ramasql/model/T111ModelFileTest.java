/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T11.1 — serializzazione {@code .rsqlmodel}: salva → carica dà il modello identico; {@code formatVersion} presente;
 * un file di versione futura dà un errore chiaro.
 */
@Tag("step11")
class T111ModelFileTest {

    @TempDir
    Path dir;

    private static ErModel sample() {
        ErModel m = AutoLayout.layout(ReverseEngineer.build("bib", ModelFixtures.biblioteca(true), true),
                AutoLayout.ESTIMATE);
        m = m.addLogical(new Relationship("log:x", Relationship.Kind.LOGICAL, "prestiti", List.of("id_socio"), "soci",
                List.of("id"), Relationship.Cardinality.ONE_TO_MANY, true, "chi ha preso il libro"));
        return m.changeEntity("soci", e -> e.withMissing(true).at(123.5, 456.25));
    }

    @Test
    void salvaECaricaDaIlModelloIdentico() throws Exception {
        ErModel m = sample();
        Path f = dir.resolve("biblioteca.rsqlmodel");
        ModelFile.write(f, m);
        ErModel back = ModelFile.read(f);
        assertEquals(m, back, "entità, colonne, posizioni, relazioni fisiche e logiche");
        assertEquals(123.5, back.entity("soci").orElseThrow().x());
        assertTrue(back.entity("soci").orElseThrow().missing());
    }

    @Test
    void formatVersionInTesta() throws Exception {
        String json = ModelFile.toJson(sample()).replace("\r\n", "\n");
        assertTrue(json.stripLeading().startsWith("{\n  \"formatVersion\" : 1,"), json.substring(0, 60));
        assertTrue(json.contains("\"kind\" : \"LOGICAL\""));
        assertTrue(json.contains("\"kind\" : \"PHYSICAL\""));
    }

    @Test
    void versioneFuturaErroreChiaro() throws Exception {
        Path f = dir.resolve("futuro.rsqlmodel");
        Files.writeString(f, ModelFile.toJson(sample()).replace("\"formatVersion\" : 1", "\"formatVersion\" : 7"),
                StandardCharsets.UTF_8);
        ModelFile.ModelFileException e = assertThrows(ModelFile.ModelFileException.class, () -> ModelFile.read(f));
        assertTrue(e.getMessage().contains("futuro.rsqlmodel") && e.getMessage().contains("versione più recente")
                && e.getMessage().contains("7"), e.getMessage());
    }

    @Test
    void fileNonModelloErroreChiaro() throws Exception {
        for (String content : new String[] {"non è json", "[]", "{\"nome\": 1}", "{\"formatVersion\": \"uno\"}"}) {
            Path f = dir.resolve("x.rsqlmodel");
            Files.writeString(f, content, StandardCharsets.UTF_8);
            ModelFile.ModelFileException e = assertThrows(ModelFile.ModelFileException.class, () -> ModelFile.read(f));
            assertTrue(e.getMessage().contains("non è un modello ER"), e.getMessage());
        }
    }

    @Test
    void chiaviSconosciuteDellaStessaVersioneIgnorate() throws Exception {
        String json = ModelFile.toJson(sample()).replaceFirst("\\{", "{\n  \"note\" : \"aggiunta da un'altra copia\",");
        assertEquals(sample(), ModelFile.fromJson(json, "x"));
    }

    @Test
    void scritturaAtomicaNessunFileTemporaneoResta() throws Exception {
        Path f = dir.resolve("m.rsqlmodel");
        ModelFile.write(f, sample());
        ModelFile.write(f, sample());
        try (var s = Files.list(dir)) {
            assertEquals(List.of("m.rsqlmodel"), s.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void estensioneEVersione() {
        assertEquals("rsqlmodel", ModelFormat.FILE_EXTENSION);
        assertEquals(1, ModelFormat.FORMAT_VERSION);
    }
}
