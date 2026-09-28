/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T10.2 — ordinamento: tabelle prima delle viste; viste che dipendono da viste; chiavi esterne circolari
 * (→ {@code SET FOREIGN_KEY_CHECKS=0}).
 */
@Tag("step10")
class T102DumpOrderTest {

    private static final Map<String, List<String>> BIBLIOTECA = new LinkedHashMap<>();

    static {
        BIBLIOTECA.put("prestiti", List.of("libri", "soci"));
        BIBLIOTECA.put("libri_autori", List.of("libri", "autori"));
        BIBLIOTECA.put("libri", List.of("editori"));
        BIBLIOTECA.put("soci", List.of());
        BIBLIOTECA.put("autori", List.of());
        BIBLIOTECA.put("editori", List.of());
    }

    @Test
    void tabelleRiferitePrima() {
        DumpOrder o = DumpOrder.of(BIBLIOTECA, Map.of());
        List<String> t = o.tables();
        assertEquals(6, t.size());
        assertTrue(t.indexOf("editori") < t.indexOf("libri"));
        assertTrue(t.indexOf("libri") < t.indexOf("prestiti"));
        assertTrue(t.indexOf("soci") < t.indexOf("prestiti"));
        assertTrue(t.indexOf("autori") < t.indexOf("libri_autori"));
        assertTrue(t.indexOf("libri") < t.indexOf("libri_autori"));
        assertFalse(o.circular());
        assertEquals(List.of("autori", "editori", "libri", "libri_autori", "soci", "prestiti"), t,
                "a parità di dipendenze, ordine alfabetico (dump ripetibili)");
    }

    @Test
    void vistePoiLeVisteCheUsanoAltreViste() {
        Map<String, String> views = new LinkedHashMap<>();
        views.put("v_riepilogo", "select `v`.`cognome` AS `cognome`,count(0) AS `n` from `bib`.`v_prestiti_aperti` `v` group by `v`.`cognome`");
        views.put("v_prestiti_aperti", "select `p`.`id` AS `id` from `bib`.`prestiti` `p` where `p`.`data_reso` is null");
        views.put("a_prima", "select 1 AS `x` from v_riepilogo");
        DumpOrder o = DumpOrder.of(Map.of("prestiti", List.of()), views);
        assertEquals(List.of("v_prestiti_aperti", "v_riepilogo", "a_prima"), o.views());
    }

    @Test
    void chiaviCircolariSegnalate() {
        Map<String, List<String>> refs = new LinkedHashMap<>();
        refs.put("classi", List.of("docenti"));      // coordinatore
        refs.put("docenti", List.of("classi"));      // classe di riferimento
        refs.put("materie", List.of());
        DumpOrder o = DumpOrder.of(refs, Map.of());
        assertTrue(o.circular());
        assertEquals(Set.of("classi", "docenti"), Set.copyOf(o.circularKeys()));
        assertEquals("materie", o.tables().get(0), "ciò che non è nel ciclo viene prima");
        assertEquals(3, o.tables().size());
    }

    @Test
    void riferimentoASeStessaSpegneIControlli() {
        DumpOrder o = DumpOrder.of(Map.of("dipendenti", List.of("dipendenti")), Map.of());
        assertFalse(o.circular(), "non è un ciclo fra tabelle diverse");
        assertEquals(List.of("dipendenti"), o.selfReferences());
        assertTrue(o.needsForeignKeysOff(), "un responsabile con id più alto del dipendente: senza controlli spenti il"
                + " ripristino fallirebbe con 1452");
        assertEquals(List.of("dipendenti"), o.tables());
        DumpOrder plain = DumpOrder.of(Map.of("libri", List.of("editori"), "editori", List.of()), Map.of());
        assertFalse(plain.needsForeignKeysOff());
    }

    @Test
    void conChiaviCircolariIlDumpSpegneIControlliAncheSenzaOpzione() throws Exception {
        StringWriter out = new StringWriter();
        DumpWriter w = new DumpWriter(out, DumpOptions.defaults().withDisableForeignKeys(false));
        w.header(Instant.EPOCH, "MariaDB 11.5.2", List.of("scuola"), true);
        w.footer(false);
        String text = out.toString();
        assertTrue(text.contains("SET FOREIGN_KEY_CHECKS = 0;"), text);
        assertTrue(text.contains("SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;"), text);
        assertTrue(text.contains("circolari"), "il file spiega perché");

        StringWriter noFk = new StringWriter();
        DumpWriter w2 = new DumpWriter(noFk, DumpOptions.defaults().withDisableForeignKeys(false));
        w2.header(Instant.EPOCH, "MariaDB 11.5.2", List.of("biblioteca"), false);
        w2.footer(false);
        assertFalse(noFk.toString().contains("FOREIGN_KEY_CHECKS"), "senza cicli e senza opzione non si toccano");
    }

    @Test
    void tabelleRiferiteLetteDalCreateTable() {
        String create = "CREATE TABLE `prestiti` (\n  `id` int NOT NULL,\n"
                + "  CONSTRAINT `fk_prestiti_libri` FOREIGN KEY (`id_libro`) REFERENCES `libri` (`id`),\n"
                + "  CONSTRAINT `fk_prestiti_soci` FOREIGN KEY (`id_socio`) REFERENCES `soci` (`id`) ON DELETE CASCADE\n"
                + ") ENGINE=InnoDB";
        assertEquals(List.of("libri", "soci"), DumpWriter.referencedTables(create));
    }
}
