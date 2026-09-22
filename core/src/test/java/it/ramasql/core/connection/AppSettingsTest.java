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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Impostazioni (quattro voci), cartella dei dati e regole fisse della sessione, senza server. */
@Tag("step2")
class AppSettingsTest {

    @TempDir
    Path dir;

    @Test
    void leImpostazioniSonoEsattamenteQuattro() {
        assertEquals(4, AppSettings.class.getRecordComponents().length);
    }

    @Test
    void iValoriPredefinitiSonoQuelliDelDesign() {
        AppSettings s = AppSettings.load(dir);
        assertEquals("it", s.language());
        assertEquals(1000, s.rowLimit());
        assertEquals(AppSettings.DEFAULT_FONT_SIZE, s.fontSize());
        assertTrue(Files.isDirectory(Path.of(s.workDirectory())), s.workDirectory());
    }

    @Test
    void leImpostazioniSalvateSiRileggonoIdentiche() throws IOException {
        AppSettings s = new AppSettings("en", 18, 250, dir.toString());
        s.save(dir);
        assertEquals(s, AppSettings.load(dir));
        assertTrue(Files.exists(dir.resolve("impostazioni.json")));
    }

    @Test
    void iValoriFuoriIntervalloSiCorreggonoEUnFileRottoNonBloccaLAvvio() throws IOException {
        assertEquals(AppSettings.MAX_FONT_SIZE, new AppSettings("it", 500, 10, "x").fontSize());
        assertEquals(AppSettings.MIN_FONT_SIZE, new AppSettings("it", 3, 10, "x").fontSize());
        Files.writeString(dir.resolve(AppSettings.FILE_NAME), "{ rotto");
        assertEquals(AppSettings.defaults(), AppSettings.load(dir));
    }

    @Test
    void laCartellaDeiDatiSiPuoSostituireConLaProprietaDiSistema() {
        String before = System.getProperty(AppData.OVERRIDE_PROPERTY);
        try {
            System.setProperty(AppData.OVERRIDE_PROPERTY, dir.toString());
            assertEquals(dir, AppData.directory());
            System.clearProperty(AppData.OVERRIDE_PROPERTY);
            assertTrue(AppData.directory().endsWith("RamaSQL"));
        } finally {
            if (before != null) {
                System.setProperty(AppData.OVERRIDE_PROPERTY, before);
            }
        }
    }

    @Test
    void lIndirizzoJdbcNonPortaMaiUtenteOPassword() {
        ConnectionProfile p = ConnectionProfile.create("x", "db.example", 3307, "studente", "biblioteca", "");
        assertEquals("jdbc:mariadb://db.example:3307/biblioteca", Session.jdbcUrl(p, p.defaultCatalog()));
        assertEquals("jdbc:mariadb://[::1]:3306/",
                Session.jdbcUrl(ConnectionProfile.create("y", "::1", 3306, "u", "", ""), ""));
    }

    @Test
    void iParametriDelDriverSonoQuelliDecisi() {
        ConnectionProfile p = ConnectionProfile.create("x", "db.example", 3306, "studente", "", "");
        Properties props = Session.driverProperties(p, "abc".toCharArray());
        assertEquals("true", props.getProperty("autocommit"), "sempre autocommit: nessuna gestione delle transazioni");
        assertTrue(Integer.parseInt(props.getProperty("connectTimeout")) <= 10_000);
        assertEquals("true", props.getProperty("allowPublicKeyRetrieval"));
        assertEquals("false", props.getProperty("tinyInt1isBit"));
    }

    /**
     * Il profilo scritto su disco ha <strong>esattamente</strong> queste chiavi: se un giorno qualcuno aggiunge un
     * campo (per esempio una password) questo test fallisce e obbliga a pensarci.
     */
    @Test
    void ilProfiloSuDiscoHaSoloLeChiaviPreviste() throws IOException {
        ConnectionProfile p = ConnectionProfile.create("x", "db.example", 3307, "studente", "biblioteca", "nota")
                .withLastServer(ServerInfo.parse("11.5.2-MariaDB"));
        Set<String> keys = new TreeSet<>();
        JsonFiles.MAPPER.readTree(JsonFiles.MAPPER.writeValueAsString(p)).fieldNames().forEachRemaining(keys::add);

        assertEquals(new TreeSet<>(Set.of("id", "name", "host", "port", "user", "defaultCatalog", "note",
                "lastServerKind", "lastServerVersion")), keys);
        assertEquals("studente@db.example:3307", p.address());
    }

    @Test
    void impostazioniRovinate_valoriPredefinitiAvvisoECopiaMessaDaParteConNomeUnivoco() throws IOException {
        Path file = dir.resolve(AppSettings.FILE_NAME);
        Files.writeString(dir.resolve(AppSettings.FILE_NAME + ".illeggibile"), "copia precedente");
        Files.writeString(file, "{ \"fontSize\": rotto");

        AppSettings.Loading loading = AppSettings.loadRecovering(dir);

        assertEquals(AppSettings.defaults(), loading.settings());
        assertTrue(loading.hasProblem());
        assertTrue(loading.problem().contains("impostazioni.json") && loading.problem().contains("rovinato"), loading.problem());
        assertEquals(dir.resolve("impostazioni.json.illeggibile-2"), loading.setAsideCopy());
        assertEquals("{ \"fontSize\": rotto", Files.readString(loading.setAsideCopy()));
        assertEquals("copia precedente", Files.readString(dir.resolve("impostazioni.json.illeggibile")),
                "la copia di una volta precedente non si sovrascrive");

        // salvare dopo non cancella più nulla: il contenuto rovinato è al sicuro nella copia
        loading.settings().withFontSize(20).save(dir);
        assertEquals(20, AppSettings.load(dir).fontSize());
        assertEquals("{ \"fontSize\": rotto", Files.readString(loading.setAsideCopy()));
    }

    @Test
    void impostazioniBuone_nessunAvvisoENessunaCopia() throws IOException {
        new AppSettings("it", 15, 500, dir.toString()).save(dir);
        AppSettings.Loading loading = AppSettings.loadRecovering(dir);
        assertFalse(loading.hasProblem());
        assertEquals(15, loading.settings().fontSize());
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }
}
