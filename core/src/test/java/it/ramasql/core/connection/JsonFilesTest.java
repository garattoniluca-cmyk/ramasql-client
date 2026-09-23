/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Scrittura sicura dei file dell'utente: temporaneo forzato su disco, poi sostituzione; nessun resto. */
@Tag("step2")
class JsonFilesTest {

    @TempDir
    Path dir;

    private List<String> names() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void laScritturaSostituisceIlFileENonLasciaTemporanei() throws IOException {
        Path file = dir.resolve("impostazioni.json");
        Files.writeString(file, "{\"vecchio\": true}", StandardCharsets.UTF_8);

        JsonFiles.write(file, Map.of("nuovo", "àèìòù €"));

        assertEquals("àèìòù €", JsonFiles.MAPPER.readTree(file.toFile()).get("nuovo").asText(), "UTF-8, contenuto nuovo");
        assertEquals(List.of("impostazioni.json"), names(), "nessun .tmp rimasto");
    }

    /**
     * Su Windows un antivirus o l'indicizzatore aprono il file per un attimo senza permettere di sostituirlo
     * (osservato durante la verifica completa: «AccessDenied» su connessioni.json). Lo spostamento qui fallisce con
     * «accesso negato» le prime 4 volte: il salvataggio deve riuscire lo stesso; se il blocco dura più dei tentativi
     * previsti, l'errore arriva al chiamante (nessun salvataggio finto).
     */
    @Test
    void unFileBloccatoPerUnAttimoSiSostituisceComunque() throws IOException {
        Path file = dir.resolve("connessioni.json");
        Files.writeString(file, "{\"vecchio\": true}", StandardCharsets.UTF_8);
        Path temp = dir.resolve("nuovo.tmp");
        Files.writeString(temp, "{\"nuovo\": 1}", StandardCharsets.UTF_8);
        int[] calls = {0};
        JsonFiles.Mover lockedFourTimes = (from, to) -> {
            if (++calls[0] <= 4) {
                throw new java.nio.file.AccessDeniedException(from + " -> " + to);
            }
            JsonFiles.moveReplacing(from, to);
        };

        JsonFiles.replace(temp, file, JsonFiles.REPLACE_ATTEMPTS, lockedFourTimes);

        assertEquals(5, calls[0], "4 tentativi falliti, il quinto riesce");
        assertEquals(1, JsonFiles.MAPPER.readTree(file.toFile()).get("nuovo").asInt(), "contenuto nuovo scritto");
        assertEquals(List.of("connessioni.json"), names(), "nessun temporaneo rimasto");
    }

    @Test
    void unBloccoPiuLungoDeiTentativiArrivaAlChiamante() throws IOException {
        Path file = dir.resolve("connessioni.json");
        Files.writeString(file, "vecchio", StandardCharsets.UTF_8);
        Path temp = dir.resolve("nuovo.tmp");
        Files.writeString(temp, "nuovo", StandardCharsets.UTF_8);
        int[] calls = {0};
        JsonFiles.Mover alwaysLocked = (from, to) -> {
            calls[0]++;
            throw new java.nio.file.AccessDeniedException(to.toString());
        };

        assertThrows(java.nio.file.AccessDeniedException.class,
                () -> JsonFiles.replace(temp, file, JsonFiles.REPLACE_ATTEMPTS, alwaysLocked));
        assertEquals(JsonFiles.REPLACE_ATTEMPTS, calls[0], "si riprova esattamente il numero previsto di volte");
        assertEquals("vecchio", Files.readString(file), "il file vero resta com'era");
    }

    @Test
    void seLaSostituzioneFallisceNonRestaNessunTemporaneo() throws IOException {
        Path file = dir.resolve("connessioni.json");
        Files.createDirectories(file);
        Files.writeString(file.resolve("dentro.txt"), "x");

        assertThrows(IOException.class, () -> JsonFiles.write(file, Map.of("a", 1)));

        assertEquals(List.of("connessioni.json"), names(), "il temporaneo si cancella anche se lo spostamento fallisce");
        assertEquals("x", Files.readString(file.resolve("dentro.txt")), "ciò che c'era resta com'era");
    }

    @Test
    void mettereDaParteNonSovrascriveMaiUnaCopiaPrecedente() throws IOException {
        Path file = dir.resolve("impostazioni.json");
        Files.writeString(file, "primo");
        Path first = JsonFiles.setAside(file);
        Files.writeString(file, "secondo");
        Path second = JsonFiles.setAside(file);
        Files.writeString(file, "terzo");
        Path third = JsonFiles.setAside(file);

        assertEquals(List.of("impostazioni.json.illeggibile", "impostazioni.json.illeggibile-2",
                "impostazioni.json.illeggibile-3"), names());
        assertEquals("primo", Files.readString(first));
        assertEquals("secondo", Files.readString(second));
        assertEquals("terzo", Files.readString(third));
        assertTrue(Files.notExists(file));
    }
}
