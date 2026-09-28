/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L'analisi di uno script prima di eseguirlo: numero di istruzioni, prime istruzioni per l'anteprima, conferma
 * rafforzata anche per un {@code DROP} in fondo al file, cataloghi nominati (anche con i commenti eseguibili di
 * {@code mysqldump}), BOM saltato.
 */
@Tag("step10")
class ScriptPreviewTest {

    @TempDir
    Path dir;

    private Path write(String text) throws Exception {
        Path f = dir.resolve("s.sql");
        Files.writeString(f, text, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void distruttivaInFondoAlFileChiedeLaConfermaRafforzata() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("INSERT INTO t VALUES (").append(i).append(");\n");
        }
        sb.append("DROP TABLE t;\n");
        ScriptPreview p = ScriptPreview.scan(write(sb.toString()), "Esportazione", null);
        assertEquals(101, p.statements());
        assertEquals(ScriptPreview.FIRST, p.first().size());
        assertEquals(1, p.destructive().size());
        ConfirmationPolicy.Confirmation c = p.confirmation("Esportazione", List.of());
        assertEquals(ConfirmationPolicy.Level.STRONG, c.level(), "il DROP alla riga 101 conta come se fosse in cima");
        assertEquals("t", c.typeToConfirm());
        SqlScript shown = p.previewScript("Esportazione", List.of(SqlStatement.of("USE `x`", "Esportazione")));
        assertEquals("USE `x`", shown.statements().get(0).text());
        assertEquals(ScriptPreview.FIRST + 2, shown.size(), "l'USE, le prime 30 e il DROP della riga 101");
        assertEquals("DROP TABLE t", shown.statements().get(shown.size() - 1).text(), "il DROP in fondo si vede");
        assertTrue(shown.title().contains("101 istruzioni") && shown.title().contains("prime 30"), shown.title());
        assertEquals(1, p.destructiveCount());
    }

    @Test
    void cataloghiNominatiAncheConICommentiDiMysqldump() throws Exception {
        ScriptPreview p = ScriptPreview.scan(write("CREATE DATABASE /*!32312 IF NOT EXISTS*/ `ramasql_test_a`"
                + " /*!40100 DEFAULT CHARACTER SET utf8mb4 */;\nUSE `ramasql_test_a`;\nDROP DATABASE IF EXISTS"
                + " ramasql_test_b;\nCREATE SCHEMA `ramasql_test_c`;\nSELECT 1;\n"), "x", null);
        assertEquals(Set.of("ramasql_test_a", "ramasql_test_b", "ramasql_test_c"), p.catalogs());
        assertTrue(p.hasUse());
        assertTrue(p.onlyCatalogsStartingWith("ramasql_test_"));
        ScriptPreview q = ScriptPreview.scan(write("USE scuola;\nSELECT 1;"), "x", null);
        assertFalse(q.onlyCatalogsStartingWith("ramasql_test_"), "un catalogo dell'utente: il test non lo esegue");
    }

    @Test
    void senzaUseEControlliDelleChiavi() throws Exception {
        ScriptPreview p = ScriptPreview.scan(write("CREATE TABLE a (id INT);\nSET FOREIGN_KEY_CHECKS = 0;\n"), "x", null);
        assertFalse(p.hasUse());
        assertTrue(p.touchesForeignKeyChecks());
        assertEquals(Set.of(), p.catalogs());
        assertEquals(ConfirmationPolicy.Level.CONFIRM, p.confirmation("x", List.of()).level());
    }

    @Test
    void bomSaltato() throws Exception {
        Path f = dir.resolve("bom.sql");
        byte[] text = "SELECT 1;".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[text.length + 3];
        all[0] = (byte) 0xEF;
        all[1] = (byte) 0xBB;
        all[2] = (byte) 0xBF;
        System.arraycopy(text, 0, all, 3, text.length);
        Files.write(f, all);
        ScriptPreview p = ScriptPreview.scan(f, "x", null);
        assertEquals("SELECT 1", p.first().get(0).text());
    }
}
