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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** T2.5 — profili → JSON → profili identici; nel JSON nessun campo password, nemmeno se qualcuno ce lo mette. */
@Tag("step2")
class ProfileStoreTest {

    private static final List<String> FORBIDDEN_KEY_PARTS = List.of("pass", "pwd", "secret");

    @TempDir
    Path dir;

    private static List<ConnectionProfile> sampleProfiles() {
        return List.of(
                ConnectionProfile.create("MariaDB locale", "127.0.0.1", 3306, "ramasql_test", "", ""),
                ConnectionProfile.create("MySQL dell'aula «3A»", "db.scuola.example", 3307, "studente", "biblioteca",
                        "Server del laboratorio.\nChiedere la password al docente.")
                        .withLastServer(ServerInfo.parse("8.0.40")),
                ConnectionProfile.create("Città – àèìòù €", "::1", 0, "utente con spazi", "catalogo_è", "nota")
                        .withLastServer(ServerInfo.parse("11.5.2-MariaDB")));
    }

    @Test
    void iProfiliSalvatiSiRileggonoIdentici() throws IOException {
        ProfileStore store = new ProfileStore(dir);
        for (ConnectionProfile p : sampleProfiles()) {
            store.add(p);
        }

        ProfileStore reopened = new ProfileStore(dir);

        assertEquals(store.profiles(), reopened.profiles());
        assertEquals(3306, reopened.profiles().get(2).port(), "porta non indicata = 3306");
        assertEquals("MySQL 8.0.40", reopened.profiles().get(1).lastServer().displayName());
        assertNull(reopened.profiles().get(0).lastServer(), "mai connesso = nessun server visto");
    }

    @Test
    void ilJsonNonContieneAlcunCampoPassword() throws IOException {
        ProfileStore store = new ProfileStore(dir);
        for (ConnectionProfile p : sampleProfiles()) {
            store.add(p);
        }
        Path exported = dir.resolve("connessioni-3A.json");
        store.exportTo(exported);

        for (Path file : List.of(store.file(), exported)) {
            List<String> keys = new ArrayList<>();
            collectKeys(new ObjectMapper().readTree(file.toFile()), keys);
            assertTrue(keys.contains("host"), "il controllo guarda davvero le chiavi: " + keys);
            for (String key : keys) {
                for (String forbidden : FORBIDDEN_KEY_PARTS) {
                    assertFalse(key.toLowerCase(Locale.ROOT).contains(forbidden), "chiave vietata «" + key + "» in " + file);
                }
            }
        }
    }

    @Test
    void ilRecordDelProfiloNonHaUnCampoPerLaPassword() {
        for (RecordComponent c : ConnectionProfile.class.getRecordComponents()) {
            for (String forbidden : FORBIDDEN_KEY_PARTS) {
                assertFalse(c.getName().toLowerCase(Locale.ROOT).contains(forbidden), c.getName());
            }
        }
    }

    @Test
    void unaPasswordIniettataNelFileVieneIgnorataENonRiscritta() throws IOException {
        String secret = "S3gretissima!";
        Path source = dir.resolve("manomesso.json");
        Files.writeString(source, """
                {
                  "formatVersion": 1,
                  "campoDelFuturo": {"x": [1, 2, 3]},
                  "profiles": [
                    {"id": "a1", "name": "Manomesso", "host": "127.0.0.1", "port": 3306, "user": "root",
                     "password": "%s", "pwd": "%s", "colore": "rosso", "ssl": {"secret": "%s"}}
                  ]
                }
                """.formatted(secret, secret, secret), StandardCharsets.UTF_8);

        Path userDir = dir.resolve("utente");
        ProfileStore store = new ProfileStore(userDir);
        ProfileStore.ImportResult result = store.importFrom(source);

        assertEquals(new ProfileStore.ImportResult(1, 0), result);
        ConnectionProfile imported = store.profiles().get(0);
        assertEquals("Manomesso", imported.name());
        assertEquals("root@127.0.0.1:3306", imported.address());
        assertFalse(imported.toString().contains(secret));

        Path exported = dir.resolve("riesportato.json");
        store.exportTo(exported);
        for (Path file : List.of(store.file(), exported)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(text.contains(secret), "la password iniettata è stata riscritta in " + file);
            assertFalse(text.toLowerCase(Locale.ROOT).contains("pass"), text);
            assertFalse(text.contains("colore"), "i campi sconosciuti non si riscrivono");
        }
    }

    @Test
    void lImportazioneUnisceIProfiliPerNome() throws IOException {
        ProfileStore teacher = new ProfileStore(dir.resolve("docente"));
        teacher.add(ConnectionProfile.create("Aula", "10.0.0.5", 3306, "studente", "biblioteca", ""));
        teacher.add(ConnectionProfile.create("Casa", "localhost", 3306, "root", "", ""));
        Path exported = dir.resolve("connessioni-3A.json");
        teacher.exportTo(exported);

        ProfileStore student = new ProfileStore(dir.resolve("studente"));
        ConnectionProfile old = ConnectionProfile.create("AULA", "vecchio-host", 3306, "studente", "", "")
                .withLastServer(ServerInfo.parse("11.5.2-MariaDB"));
        student.add(old);
        student.add(ConnectionProfile.create("Mio", "localhost", 3307, "io", "", ""));

        ProfileStore.ImportResult result = student.importFrom(exported);

        assertEquals(new ProfileStore.ImportResult(1, 1), result);
        assertEquals(3, student.profiles().size());
        ConnectionProfile merged = student.profiles().get(0);
        assertEquals("10.0.0.5", merged.host(), "stesso nome (senza badare alle maiuscole): aggiornato");
        assertEquals(old.id(), merged.id(), "il profilo aggiornato conserva l'identificativo");
        assertEquals("MariaDB 11.5.2", merged.lastServer().displayName(), "e l'ultimo server visto");
        assertEquals(new ProfileStore.ImportResult(0, 2), student.importFrom(exported), "reimportare non duplica");
        assertEquals(3, new ProfileStore(dir.resolve("studente")).profiles().size());
    }

    @Test
    void ilFileEsportatoHaLaVersioneDelFormatoENonPortaLUltimoServerVisto() throws IOException {
        ProfileStore store = new ProfileStore(dir);
        store.add(sampleProfiles().get(1));
        Path exported = dir.resolve("connessioni-3A.json");
        store.exportTo(exported);

        JsonNode root = new ObjectMapper().readTree(exported.toFile());
        assertEquals(ProfileStore.FORMAT_VERSION, root.get("formatVersion").asInt());
        assertEquals(1, root.get("profiles").size());
        assertFalse(root.get("profiles").get(0).has("lastServerVersion"));
    }

    @Test
    void unFileCheNonEUnElencoDiProfiliVieneRifiutatoConUnMessaggioItaliano() throws IOException {
        Path notJson = dir.resolve("appunti.json");
        Files.writeString(notJson, "questo non è JSON");
        Path newer = dir.resolve("futuro.json");
        Files.writeString(newer, "{\"formatVersion\": 99, \"profiles\": []}");
        ProfileStore store = new ProfileStore(dir);

        IOException e1 = assertThrows(IOException.class, () -> store.importFrom(notJson));
        IOException e2 = assertThrows(IOException.class, () -> store.importFrom(newer));

        assertTrue(e1.getMessage().contains("appunti.json"), e1.getMessage());
        assertTrue(e2.getMessage().contains("più recente"), e2.getMessage());
        assertNotEquals(e1.getMessage(), e2.getMessage());
    }

    @Test
    void modificaDuplicaEdElimina() throws IOException {
        ProfileStore store = new ProfileStore(dir);
        ConnectionProfile p = ConnectionProfile.create("Aula", "10.0.0.5", 3306, "studente", "", "");
        store.add(p);

        store.update(p.withDetails("Aula 3A", "10.0.0.6", 3307, "studente", "biblioteca", "nota"));
        store.add(store.byId(p.id()).duplicate(store.freeName("Aula 3A")));

        List<ConnectionProfile> reread = new ProfileStore(dir).profiles();
        assertEquals(List.of("Aula 3A", "Aula 3A (2)"), reread.stream().map(ConnectionProfile::name).toList());
        assertEquals("10.0.0.6", reread.get(1).host());
        assertNotEquals(reread.get(0).id(), reread.get(1).id());
        assertTrue(store.nameTakenByOther("aula 3a", reread.get(1).id()));
        assertFalse(store.nameTakenByOther("Aula 3A", reread.get(0).id()));

        store.remove(p.id());
        assertEquals(1, new ProfileStore(dir).profiles().size());
    }

    @Test
    void primaDiImportareSiSaQualiProfiliVerrannoSostituiti() throws IOException {
        ProfileStore teacher = new ProfileStore(dir.resolve("docente"));
        teacher.add(ConnectionProfile.create("Aula", "10.0.0.5", 3306, "studente", "", ""));
        teacher.add(ConnectionProfile.create("casa", "localhost", 3306, "root", "", ""));
        teacher.add(ConnectionProfile.create("Nuovo", "localhost", 3306, "root", "", ""));
        Path exported = dir.resolve("connessioni-3A.json");
        teacher.exportTo(exported);

        ProfileStore student = new ProfileStore(dir.resolve("studente"));
        student.add(ConnectionProfile.create("AULA", "vecchio-host", 3306, "io", "", ""));
        student.add(ConnectionProfile.create("Casa", "127.0.0.1", 3307, "io", "", ""));
        student.add(ConnectionProfile.create("Mio", "localhost", 3307, "io", "", ""));
        List<ConnectionProfile> before = student.profiles();

        assertEquals(List.of("AULA", "Casa"), student.namesReplacedBy(exported), "nomi uguali senza badare alle maiuscole");
        assertEquals(before, student.profiles(), "chiedere non cambia nulla");
        assertEquals(before, new ProfileStore(dir.resolve("studente")).profiles(), "nemmeno su disco");
        assertEquals(List.of(), new ProfileStore(dir.resolve("vuoto")).namesReplacedBy(exported));
    }

    @Test
    void seIlSalvataggioFallisceMemoriaEDiscoNonDivergono() throws IOException {
        ProfileStore store = new ProfileStore(dir);
        ConnectionProfile first = ConnectionProfile.create("Aula", "10.0.0.5", 3306, "studente", "", "");
        store.add(first);
        Path importFile = dir.resolve("da-importare.json");
        ProfileStore other = new ProfileStore(dir.resolve("altro"));
        other.add(ConnectionProfile.create("Altro", "localhost", 3306, "io", "", ""));
        other.exportTo(importFile);

        // il file dei profili diventa impossibile da sostituire: al suo posto c'è una cartella non vuota
        Files.delete(store.file());
        Files.createDirectories(store.file());
        Files.writeString(store.file().resolve("blocco.txt"), "x");
        List<ConnectionProfile> before = store.profiles();

        assertThrows(IOException.class, () -> store.add(ConnectionProfile.create("Nuovo", "h", 3306, "u", "", "")));
        assertThrows(IOException.class, () -> store.update(first.withDetails("Cambiato", "h", 1, "u", "", "")));
        assertThrows(IOException.class, () -> store.remove(first.id()));
        assertThrows(IOException.class, () -> store.importFrom(importFile));

        assertEquals(before, store.profiles(), "la memoria resta com'era: nessuna modifica «solo in memoria»");
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(f -> f.getFileName().toString().endsWith(".tmp")), "nessun temporaneo rimasto");
        }
    }

    @Test
    void unFileIllegibileSiMetteDaParteConUnNomeUnivoco() throws IOException {
        Path file = dir.resolve(ProfileStore.FILE_NAME);
        Files.writeString(file, "{ rovinato", StandardCharsets.UTF_8);

        ProfileStore.Opening opening = ProfileStore.openRecovering(dir);

        assertTrue(opening.hasProblem());
        assertTrue(opening.problem().contains("connessioni.json"), opening.problem());
        assertEquals(dir.resolve("connessioni.json.illeggibile"), opening.setAsideCopy());
        assertEquals("{ rovinato", Files.readString(opening.setAsideCopy(), StandardCharsets.UTF_8));
        assertFalse(Files.exists(file));
        assertTrue(opening.store().profiles().isEmpty());
    }

    @Test
    void unaCopiaMessaDaParteInPrecedenzaNonSiSovrascriveMai() throws IOException {
        Path file = dir.resolve(ProfileStore.FILE_NAME);
        Files.writeString(dir.resolve("connessioni.json.illeggibile"), "copia di ieri", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("connessioni.json.illeggibile-2"), "copia di stamattina", StandardCharsets.UTF_8);
        Files.writeString(file, "rovinato di nuovo", StandardCharsets.UTF_8);

        ProfileStore.Opening opening = ProfileStore.openRecovering(dir);

        assertEquals(dir.resolve("connessioni.json.illeggibile-3"), opening.setAsideCopy());
        assertEquals("rovinato di nuovo", Files.readString(opening.setAsideCopy(), StandardCharsets.UTF_8));
        assertEquals("copia di ieri", Files.readString(dir.resolve("connessioni.json.illeggibile"), StandardCharsets.UTF_8));
        assertEquals("copia di stamattina", Files.readString(dir.resolve("connessioni.json.illeggibile-2"), StandardCharsets.UTF_8));
    }

    @Test
    void seIlFileNonSiPuoSpostareLElencoVuotoViveSoloInMemoriaEIlFileNonSiTocca() throws IOException {
        Path file = dir.resolve(ProfileStore.FILE_NAME);
        Files.writeString(file, "{ rovinato", StandardCharsets.UTF_8);
        // un altro programma tiene il file aperto senza permettere di spostarlo (Windows: niente FILE_SHARE_DELETE)
        try (var lock = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.READ,
                com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)) {
            ProfileStore.Opening opening = ProfileStore.openRecovering(dir);

            assertTrue(opening.hasProblem());
            assertNull(opening.setAsideCopy(), "non è stato possibile metterlo da parte");
            assertTrue(opening.store().profiles().isEmpty());
            assertEquals("{ rovinato", Files.readString(file, StandardCharsets.UTF_8), "il file su disco resta com'era");
            try (var files = Files.list(dir)) {
                assertEquals(1, files.count(), "nessun altro file creato");
            }
        }
    }

    private static void collectKeys(JsonNode node, List<String> keys) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(keys::add);
        }
        node.forEach(child -> collectKeys(child, keys));
    }
}
