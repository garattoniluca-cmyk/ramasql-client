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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Archivio dei sorgenti delle viste (Step 8, livello 1 della riapertura, {@code FEASIBILITY.md} F-06). */
@Tag("step8")
class ViewSourceStoreTest {

    private static final String SERVER = "127.0.0.1:3306";
    private static final String SOURCE = "SELECT p.id, s.cognome FROM prestiti p INNER JOIN soci s ON p.id_socio = s.id"
            + " WHERE p.data_reso IS NULL";
    private static final String DEFINITION = "select `p`.`id` AS `id`,`s`.`cognome` AS `cognome` from (`c`.`prestiti` `p`"
            + " join `c`.`soci` `s` on(`p`.`id_socio` = `s`.`id`)) where `p`.`data_reso` is null";

    @TempDir
    Path dir;

    @Test
    void ilSorgenteValeSoloSeLaDefinizioneSulServerNonECambiata() throws Exception {
        ViewSourceStore store = new ViewSourceStore(dir);
        store.save(new ViewSourceStore.Entry(SERVER, "c", "v_prestiti_aperti", SOURCE, DEFINITION));
        assertEquals(Optional.of(SOURCE), store.sourceFor(SERVER, "c", "v_prestiti_aperti", DEFINITION));
        assertEquals(Optional.of(SOURCE), store.sourceFor(SERVER, "c", "v_prestiti_aperti", "  " + DEFINITION + "\n"));
        // qualcuno l'ha cambiata da un altro programma: il sorgente è vecchio, non si usa
        assertEquals(Optional.empty(), store.sourceFor(SERVER, "c", "v_prestiti_aperti", DEFINITION + " limit 3"));
        assertEquals(Optional.empty(), store.sourceFor(SERVER, "c", "v_prestiti_aperti", null));
    }

    @Test
    void senzaDefinizioneNonSiConfronta() throws Exception {
        // un utente senza SHOW VIEW rilegge una definizione vuota: vuoto = vuoto non deve riaprire un sorgente vecchio
        ViewSourceStore store = new ViewSourceStore(dir);
        store.save(new ViewSourceStore.Entry(SERVER, "c", "v", SOURCE, ""));
        assertEquals(Optional.empty(), store.sourceFor(SERVER, "c", "v", ""));
        assertEquals(Optional.empty(), store.sourceFor(SERVER, "c", "v", "  "));
    }

    @Test
    void chiaveServerCatalogoVistaSenzaMaiuscole() throws Exception {
        ViewSourceStore store = new ViewSourceStore(dir);
        store.save(new ViewSourceStore.Entry(SERVER, "Biblioteca", "V_Prestiti", SOURCE, DEFINITION));
        assertTrue(store.find(SERVER, "biblioteca", "v_prestiti").isPresent());
        assertFalse(store.find("127.0.0.1:3307", "biblioteca", "v_prestiti").isPresent(), "altro server");
        assertFalse(store.find(SERVER, "altro", "v_prestiti").isPresent(), "altro catalogo");
    }

    @Test
    void salvareDiNuovoSostituisce() throws Exception {
        ViewSourceStore store = new ViewSourceStore(dir);
        store.save(new ViewSourceStore.Entry(SERVER, "c", "v", "SELECT 1", "select 1 AS `1`"));
        store.save(new ViewSourceStore.Entry(SERVER, "c", "V", "SELECT 2", "select 2 AS `2`"));
        assertEquals(1, store.entries().size());
        assertEquals(Optional.of("SELECT 2"), store.sourceFor(SERVER, "c", "v", "select 2 AS `2`"));
    }

    @Test
    void restaSuDiscoConFormatVersion() throws Exception {
        new ViewSourceStore(dir).save(new ViewSourceStore.Entry(SERVER, "c", "v", SOURCE, DEFINITION));
        String json = Files.readString(dir.resolve(ViewSourceStore.FILE_NAME), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"formatVersion\" : 1"), json);
        ViewSourceStore reopened = new ViewSourceStore(dir);
        assertEquals(Optional.of(SOURCE), reopened.sourceFor(SERVER, "c", "v", DEFINITION));
    }

    @Test
    void eliminaDimentica() throws Exception {
        ViewSourceStore store = new ViewSourceStore(dir);
        store.save(new ViewSourceStore.Entry(SERVER, "c", "v", SOURCE, DEFINITION));
        store.remove(SERVER, "c", "V");
        assertTrue(store.entries().isEmpty());
        assertTrue(new ViewSourceStore(dir).entries().isEmpty());
    }

    @Test
    void fileIlleggibileMessoDaParteSenzaEccezioni() throws Exception {
        Path file = dir.resolve(ViewSourceStore.FILE_NAME);
        Files.writeString(file, "{ non è JSON", StandardCharsets.UTF_8);
        ViewSourceStore store = new ViewSourceStore(dir);
        assertTrue(store.entries().isEmpty());
        assertTrue(Files.exists(dir.resolve(ViewSourceStore.FILE_NAME + ".illeggibile")));
        store.save(new ViewSourceStore.Entry(SERVER, "c", "v", SOURCE, DEFINITION));
        assertEquals(1, new ViewSourceStore(dir).entries().size());
    }

    @Test
    void campiSconosciutiDiUnaVersioneFuturaIgnorati() throws Exception {
        Files.writeString(dir.resolve(ViewSourceStore.FILE_NAME), "{\"formatVersion\":2,\"extra\":true,\"views\":"
                + "[{\"server\":\"" + SERVER + "\",\"catalog\":\"c\",\"view\":\"v\",\"source\":\"SELECT 1\","
                + "\"serverDefinition\":\"select 1 AS `1`\",\"layout\":{}}]}", StandardCharsets.UTF_8);
        assertEquals(Optional.of("SELECT 1"), new ViewSourceStore(dir).sourceFor(SERVER, "c", "v", "select 1 AS `1`"));
    }
}
