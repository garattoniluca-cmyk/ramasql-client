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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import it.ramasql.core.exec.StatementSplitter.SplitStatement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T4.1 — separatore di istruzioni: 30 casi. */
@Tag("step4")
class StatementSplitterTest {

    static Stream<Arguments> casi() {
        return Stream.of(
                arguments("script vuoto", "", List.of()),
                arguments("solo spazi e a-capo", "  \n\t\r\n ", List.of()),
                arguments("solo commenti", "-- uno\n# due\n/* tre */", List.of()),
                arguments("solo separatori", ";;\n;", List.of()),
                arguments("una istruzione con ;", "SELECT 1;", List.of("SELECT 1")),
                arguments("ultima istruzione senza ;", "SELECT 1;\nSELECT 2", List.of("SELECT 1", "SELECT 2")),
                arguments("due istruzioni sulla stessa riga", "SELECT 1; SELECT 2;", List.of("SELECT 1", "SELECT 2")),
                arguments("; dentro apici singoli", "SELECT 'a;b';SELECT 2", List.of("SELECT 'a;b'", "SELECT 2")),
                arguments("; dentro virgolette doppie", "SELECT \"a;b\"; SELECT 2",
                        List.of("SELECT \"a;b\"", "SELECT 2")),
                arguments("apice con backslash", "SELECT 'l\\'ora; tarda'; SELECT 2",
                        List.of("SELECT 'l\\'ora; tarda'", "SELECT 2")),
                arguments("apice raddoppiato", "SELECT 'l''ora; tarda'; SELECT 2",
                        List.of("SELECT 'l''ora; tarda'", "SELECT 2")),
                arguments("backslash finale nella stringa", "SELECT 'c:\\\\'; SELECT 2",
                        List.of("SELECT 'c:\\\\'", "SELECT 2")),
                arguments("virgolette doppie raddoppiate", "SELECT \"dice \"\"ciao;\"\"\"; SELECT 2",
                        List.of("SELECT \"dice \"\"ciao;\"\"\"", "SELECT 2")),
                arguments("; dentro backtick", "SELECT `a;b` FROM `t;1`; SELECT 2",
                        List.of("SELECT `a;b` FROM `t;1`", "SELECT 2")),
                arguments("backtick raddoppiato", "SELECT `a``;b` FROM t; SELECT 2",
                        List.of("SELECT `a``;b` FROM t", "SELECT 2")),
                arguments("; in commento --", "SELECT 1 -- fine; non qui\n+ 2; SELECT 3",
                        List.of("SELECT 1 -- fine; non qui\n+ 2", "SELECT 3")),
                arguments("-- senza spazio non è un commento", "SELECT 5--3; SELECT 2",
                        List.of("SELECT 5--3", "SELECT 2")),
                arguments("; in commento #", "SELECT 1 # fine; non qui\n+ 2; SELECT 3",
                        List.of("SELECT 1 # fine; non qui\n+ 2", "SELECT 3")),
                arguments("; in commento /* */", "SELECT /* a; b */ 1; SELECT 2",
                        List.of("SELECT /* a; b */ 1", "SELECT 2")),
                arguments("commento /* */ su più righe", "SELECT 1\n/* riga;\n riga; */\n+ 2;",
                        List.of("SELECT 1\n/* riga;\n riga; */\n+ 2")),
                arguments("apice dentro un commento", "SELECT 1; -- l'ora\nSELECT 2; /* dell'anno */ SELECT 3;",
                        List.of("SELECT 1", "SELECT 2", "SELECT 3")),
                arguments("commenti prima e dopo: esclusi", "-- titolo\nSELECT 1; -- coda\n# fine\n",
                        List.of("SELECT 1")),
                arguments("commento in coda all'ultima istruzione senza ;", "SELECT 1\n-- fine",
                        List.of("SELECT 1")),
                arguments("commento eseguibile /*! */", "/*!40101 SET NAMES utf8mb4 */;\nSELECT 1;",
                        List.of("/*!40101 SET NAMES utf8mb4 */", "SELECT 1")),
                arguments("commento eseguibile dentro l'istruzione",
                        "CREATE TABLE t (id INT) /*!50100 ENGINE=InnoDB */; SELECT 1",
                        List.of("CREATE TABLE t (id INT) /*!50100 ENGINE=InnoDB */", "SELECT 1")),
                arguments("DELIMITER // e ritorno a ;",
                        "DELIMITER //\nCREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END//\nDELIMITER ;\nSELECT 3;",
                        List.of("CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END", "SELECT 3")),
                arguments("DELIMITER $$ minuscolo, due routine",
                        "delimiter $$\nCREATE TRIGGER a BEFORE INSERT ON t FOR EACH ROW SET NEW.x = 1;$$\n"
                                + "CREATE TRIGGER b BEFORE UPDATE ON t FOR EACH ROW SET NEW.x = 2$$\ndelimiter ;\n",
                        List.of("CREATE TRIGGER a BEFORE INSERT ON t FOR EACH ROW SET NEW.x = 1;",
                                "CREATE TRIGGER b BEFORE UPDATE ON t FOR EACH ROW SET NEW.x = 2")),
                arguments("separatore personalizzato dentro una stringa", "DELIMITER //\nSELECT 'a//b'//\nSELECT 2//",
                        List.of("SELECT 'a//b'", "SELECT 2")),
                arguments("la parola delimiter in mezzo a un'istruzione non è il comando",
                        "SELECT delimiter FROM t; SELECT 2", List.of("SELECT delimiter FROM t", "SELECT 2")),
                arguments("a-capo di Windows", "SELECT 1;\r\nSELECT\r\n  2;\r\n", List.of("SELECT 1", "SELECT\r\n  2")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("casi")
    void separa(String nome, String script, List<String> attese) {
        List<String> trovate = StatementSplitter.split(script).stream().map(SplitStatement::text).toList();
        assertEquals(attese, trovate);
    }

    @Test
    void sonoEsattamenteTrentaCasi() {
        assertEquals(30, casi().count(), "T4.1 chiede 30 casi");
    }

    @Test
    void posizioniERighe() {
        String script = "-- intestazione\nSELECT 1;\n\n  UPDATE t\n  SET a = 1;\nSELECT 3";
        List<SplitStatement> s = StatementSplitter.split(script);
        assertEquals(3, s.size());
        assertEquals(new SplitStatement("SELECT 1", 16, 24, 2), s.get(0));
        assertEquals(new SplitStatement("UPDATE t\n  SET a = 1", 29, 49, 4), s.get(1));
        assertEquals(6, s.get(2).line());
        for (SplitStatement st : s) {
            assertEquals(st.text(), script.substring(st.startOffset(), st.endOffset()));
        }
    }

    @Test
    void scriptNulloEStringaNonChiusa() {
        assertEquals(List.of(), StatementSplitter.split(null));
        assertEquals(List.of("SELECT 'aperta; e mai chiusa"),
                StatementSplitter.split("SELECT 'aperta; e mai chiusa").stream().map(SplitStatement::text).toList());
    }
}
