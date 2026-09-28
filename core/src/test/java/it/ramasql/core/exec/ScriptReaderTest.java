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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Il lettore di script in streaming divide come {@link StatementSplitter} (quello dell'editor SQL), anche quando le
 * istruzioni cadono a cavallo dei blocchi letti dal file, e tiene il numero di riga.
 */
@Tag("step10")
class ScriptReaderTest {

    private static List<ScriptReader.Statement> read(Reader in) throws Exception {
        List<ScriptReader.Statement> out = new ArrayList<>();
        try (ScriptReader r = new ScriptReader(in)) {
            ScriptReader.Statement s;
            while ((s = r.next()) != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** Un lettore che consegna pochi caratteri alla volta: le istruzioni si spezzano fra un blocco e l'altro. */
    private static Reader dribble(String text, int step) {
        return new Reader() {
            int pos;

            @Override
            public int read(char[] cbuf, int off, int len) {
                if (pos >= text.length()) {
                    return -1;
                }
                int n = Math.min(Math.min(len, step), text.length() - pos);
                text.getChars(pos, pos + n, cbuf, off);
                pos += n;
                return n;
            }

            @Override
            public void close() {
            }
        };
    }

    private static final String[] SCRIPTS = {
        "SELECT 1; SELECT 2;",
        "-- commento\nCREATE TABLE t (a INT); -- dopo\n# cancelletto\nINSERT INTO t VALUES (1);\n",
        "INSERT INTO t VALUES ('a;b', \"c;d\", `e;f`, 'l''ora', 'x\\'y');\nSELECT 3",
        "/* blocco ; */ SELECT /* dentro */ 4; /*!40101 SET NAMES utf8mb4 */;\n/*M!999999\\- sandbox */\nSELECT 5;",
        "DELIMITER $$\nCREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END$$\nDELIMITER ;\nCALL p();",
        "SELECT 5--3;\nSELECT '-- non commento';",
        "  \n\n  ;;; SELECT 6 ;;\n",
        "SELECT 'riga1\nriga2';\nSELECT `a\n`;",
        "",
        "-- solo commenti\n/* niente */",
    };

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void comeLoSplitterDellEditorAncheABlocchiDiUnCarattere(int index) throws Exception {
        String script = SCRIPTS[index];
        List<StatementSplitter.SplitStatement> expected = StatementSplitter.split(script);
        for (int step : new int[] {1, 2, 3, 7, 64 * 1024}) {
            List<ScriptReader.Statement> got = read(dribble(script, step));
            assertEquals(expected.stream().map(StatementSplitter.SplitStatement::text).toList(),
                    got.stream().map(ScriptReader.Statement::text).toList(), "blocchi da " + step + ": " + script);
            assertEquals(expected.stream().map(s -> (long) s.line()).toList(),
                    got.stream().map(ScriptReader.Statement::line).toList(), "righe, blocchi da " + step);
        }
    }

    @Test
    void scriptGrandeConMemoriaLimitata() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            sb.append("INSERT INTO t VALUES (").append(i).append(", 'testo; con punto e virgola');\n");
        }
        try (ScriptReader r = new ScriptReader(new StringReader(sb.toString()))) {
            long n = 0;
            ScriptReader.Statement s;
            while ((s = r.next()) != null) {
                assertEquals(n, s.index());
                assertEquals(n + 1, s.line());
                n++;
            }
            assertEquals(20_000, n);
            assertEquals(sb.length(), r.charsConsumed());
        }
    }

    @Test
    void ultimaIstruzioneSenzaPuntoEVirgola() throws Exception {
        List<ScriptReader.Statement> s = read(new StringReader("SELECT 1;\nSELECT 2"));
        assertEquals("SELECT 2", s.get(1).text());
        assertEquals(2, s.get(1).line());
    }

    @Test
    void fileVuoto() throws Exception {
        try (ScriptReader r = new ScriptReader(new StringReader(""))) {
            assertNull(r.next());
        }
    }

    @Test
    void dumpDiMysqldump() throws Exception {
        String dump = "/*M!999999\\- enable the sandbox mode */ \n-- MariaDB dump 10.19\n"
                + "/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;\n"
                + "DROP TABLE IF EXISTS `t`;\nCREATE TABLE `t` (\n  `a` int(11) DEFAULT NULL\n);\n"
                + "LOCK TABLES `t` WRITE;\n/*!40000 ALTER TABLE `t` DISABLE KEYS */;\n"
                + "INSERT INTO `t` VALUES (1),(2);\n/*!40000 ALTER TABLE `t` ENABLE KEYS */;\nUNLOCK TABLES;\n";
        List<ScriptReader.Statement> s = read(new StringReader(dump));
        assertEquals(StatementSplitter.split(dump).size(), s.size());
        assertTrue(s.get(0).text().contains("SET @OLD_CHARACTER_SET_CLIENT"));
        assertEquals("UNLOCK TABLES", s.get(s.size() - 1).text());
    }
}
