/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.visual;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import it.ramasql.qb.QbSql;

/**
 * T8.2 (parte con il parser) e la regola dei tre livelli di «Modifica vista» ({@link ViewReopening}).
 * <ul>
 *   <li>Le <b>26 definizioni reali</b> rilette da MariaDB e MySQL nello spike S2c (le stesse del test del normalizzatore
 *       nel modulo {@code core}), normalizzate, passano dal parser del query builder <b>senza eccezioni</b>; quelle
 *       rappresentabili (tutte, sulla fixture e sulle viste reali, dopo la correzione di {@code BUG-005}) si aprono al
 *       livello 2, con l'SQL rigenerato equivalente.</li>
 *   <li>Livello 1 quando c'è il sorgente; livello 3, con il motivo, quando il testo non si disegna.</li>
 * </ul>
 */
@Tag("step8")
class T82ViewReopeningTest {

    record Definizione(String server, String vista, String catalogo, String testo) {
        @Override
        public String toString() {
            return server + " " + vista;
        }
    }

    private static final Pattern CATALOGO = Pattern.compile("`(ramasql_test_[a-z0-9_]+)`");

    static Stream<Definizione> definizioni() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("mvnw.cmd"))) {
            root = root.getParent();
        }
        assertNotNull(root, "radice del progetto");
        List<Definizione> out = new ArrayList<>();
        for (String server : List.of("mariadb", "mysql")) {
            Path file = root.resolve("core/src/test/resources/it/ramasql/core/metadata/viste-reali-" + server + ".tsv");
            for (String riga : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (riga.isBlank()) {
                    continue;
                }
                String[] p = riga.split("\t", 2);
                Matcher m = CATALOGO.matcher(p[1]);
                assertTrue(m.find());
                out.add(new Definizione(server, p[0], m.group(1), p[1]));
            }
        }
        assertEquals(26, out.size(), "13 definizioni reali per server");
        return out.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("definizioni")
    void definizioneRealeNormalizzataAccettataDalParser(Definizione d) {
        ViewReopening r = assertDoesNotThrow(() -> ViewReopening.decide(Optional.empty(), d.testo(), d.catalogo()));
        assertEquals(2, r.level(), "si riapre dalla definizione del server: " + r.reason());
        assertTrue(r.graphic(), "si disegna: " + r.reason());
        assertFalse(r.sql().contains(d.catalogo()));
        QbSql.Result check = QbSql.check(r.sql());
        assertTrue(check.representable());
        // la forma rigenerata dal modello si riconosce di nuovo (stabile)
        assertTrue(QbSql.check(check.regenerated()).representable(), check.regenerated());
    }

    @Test
    void livello1ConIlSorgenteOriginale() {
        String sorgente = "SELECT p.id, s.cognome FROM prestiti p INNER JOIN soci s ON p.id_socio = s.id"
                + " WHERE p.data_reso IS NULL";
        ViewReopening r = ViewReopening.decide(Optional.of(sorgente), "select `p`.`id` AS `id` from `c`.`prestiti` `p`",
                "c");
        assertEquals(1, r.level());
        assertEquals(sorgente, r.sql(), "si riapre il testo scritto dall'utente, non la riscrittura del server");
        assertTrue(r.graphic());
        assertNull(r.reason());
    }

    @Test
    void livello1ConSorgenteNonDisegnabileRestaTesto() {
        String sorgente = "SELECT titolo, ROW_NUMBER() OVER (ORDER BY prezzo) AS n FROM libri";
        ViewReopening r = ViewReopening.decide(Optional.of(sorgente), "qualunque", "c");
        assertEquals(1, r.level());
        assertEquals(sorgente, r.sql());
        assertFalse(r.graphic());
        assertNotNull(r.reason());
    }

    @Test
    void livello3QuandoNonSiDisegna() {
        String def = "select `c`.`autori`.`cognome` AS `cognome` from `c`.`autori` union all select"
                + " `c`.`soci`.`cognome` AS `cognome` from `c`.`soci`";
        ViewReopening r = ViewReopening.decide(Optional.empty(), def, "c");
        assertEquals(3, r.level());
        assertFalse(r.graphic());
        assertNotNull(r.reason());
        assertTrue(r.sql().toLowerCase().contains("union all"), "nessuna perdita: " + r.sql());
        assertFalse(r.sql().contains("`c`."), "il catalogo si toglie anche al livello 3");
    }

    @Test
    void sorgenteVuotoValeComeAssente() {
        ViewReopening r = ViewReopening.decide(Optional.of("  "), "select `c`.`t`.`a` AS `a` from `c`.`t`", "c");
        assertEquals(2, r.level());
        assertEquals("select `t`.`a` from `t`", r.sql());
    }
}
