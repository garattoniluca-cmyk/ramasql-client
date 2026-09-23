/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.it.TestResults;

/** Le fixture salvate nel repository sono quelle del generatore, con i volumi e i casi difficili richiesti. */
@Tag("step3")
class BibliotecaFixtureTest {

    private static String committed(String file) throws Exception {
        return Files.readString(TestResults.projectRoot().resolve("it-tests").resolve("fixtures").resolve(file),
                StandardCharsets.UTF_8);
    }

    @Test
    void iFileSalvatiCoincidonoConIlGeneratore() throws Exception {
        assertEquals(BibliotecaFixture.script(true), committed(BibliotecaFixture.INNODB_FILE),
                "rigenerare con BibliotecaFixture.main");
        assertEquals(BibliotecaFixture.script(false), committed(BibliotecaFixture.MYISAM_FILE),
                "rigenerare con BibliotecaFixture.main");
    }

    @Test
    void volumiECasiDifficili() throws Exception {
        String sql = committed(BibliotecaFixture.INNODB_FILE);
        assertEquals(BibliotecaFixture.EDITORI, rowsOf(sql, "editori"));
        assertEquals(BibliotecaFixture.AUTORI, rowsOf(sql, "autori"));
        assertEquals(BibliotecaFixture.LIBRI, rowsOf(sql, "libri"));
        assertEquals(BibliotecaFixture.SOCI, rowsOf(sql, "soci"));
        assertEquals(BibliotecaFixture.PRESTITI, rowsOf(sql, "prestiti"));
        assertEquals(BibliotecaFixture.libriAutoriRows(), rowsOf(sql, "libri_autori"));
        assertTrue(sql.contains("'D''Annunzio'"), "apostrofi");
        assertTrue(sql.contains("Città") && sql.contains("Čechov") && sql.contains("Kenzaburō"), "accenti");
        assertTrue(sql.contains("🐱") && sql.contains("😀"), "emoji");
        assertTrue(sql.contains(", NULL"), "NULL");
        assertFalse(Pattern.compile("(?mi)^\\s*(CREATE\\s+DATABASE|CREATE\\s+SCHEMA|USE\\s)").matcher(sql).find(),
                "niente catalogo nello script");
        assertEquals(5, count(sql, "FOREIGN KEY"));   // libri→editori, libri_autori→libri/autori, prestiti→libri/soci
        assertEquals(2, count(sql, "CREATE VIEW"));
    }

    @Test
    void laVarianteMyIsamNonHaChiaviEsterne() throws Exception {
        String sql = committed(BibliotecaFixture.MYISAM_FILE);
        assertEquals(0, count(sql, "FOREIGN KEY"));
        assertEquals(0, count(sql, "InnoDB"));
        assertEquals(6, count(sql, "ENGINE=MyISAM"));
        assertEquals(BibliotecaFixture.PRESTITI, rowsOf(sql, "prestiti"));
    }

    /** Righe del blocco {@code INSERT INTO <tabella> (…) VALUES} (una per riga di testo). */
    private static int rowsOf(String sql, String table) {
        Matcher m = Pattern.compile("INSERT INTO " + table + " \\([^)]*\\) VALUES\\n((?:  \\(.*\\)[,;]\\n)+)")
                .matcher(sql);
        assertTrue(m.find(), "INSERT di " + table);
        return (int) m.group(1).lines().count();
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }
}
