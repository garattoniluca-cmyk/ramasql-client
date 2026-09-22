/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.ramasql.qb.QbSql;

/**
 * Regressione del difetto trovato nello spike S2c (viste rilette dal server): con la ON scritta
 * «tabella_aggiunta.col = tabella_precedente.col» il parser ereditato scambiava le due tabelle del join, e un
 * {@code a LEFT JOIN b} veniva rigenerato come {@code b LEFT JOIN a} (significato diverso).
 */
@Tag("step1")
class S2cVersoDeiJoinTest {

    @ParameterizedTest
    @ValueSource(strings = {"LEFT JOIN", "RIGHT JOIN", "INNER JOIN"})
    void ilVersoDelJoinNonDipendeDallOrdineDegliOperandiDellaOn(String join) {
        String dritta = "SELECT a.cognome FROM autori a " + join + " libri_autori la ON a.id = la.id_autore";
        String rovescia = "SELECT a.cognome FROM autori a " + join + " libri_autori la ON la.id_autore = a.id";

        QbSql.Result r1 = QbSql.check(dritta);
        QbSql.Result r2 = QbSql.check(rovescia);

        assertTrue(r1.representable(), r1.reason());
        assertTrue(r2.representable(), r2.reason());
        assertEquals(QbSql.normalize(r1.regenerated()), QbSql.normalize(r2.regenerated()),
                "le due scritture sono la stessa query: stesso SQL rigenerato");
        String n = QbSql.normalize(r2.regenerated());
        assertTrue(n.indexOf("autori a") < n.indexOf("libri_autori la"),
                "autori deve restare a sinistra del join: " + r2.regenerated());
    }

    @Test
    void catenaDiJoinConOnRovesciate() {
        String sql = "SELECT a.cognome, l.titolo FROM autori a INNER JOIN libri_autori la ON la.id_autore = a.id"
                + " LEFT JOIN libri l ON l.id = la.id_libro WHERE l.anno > 1950";
        QbSql.Result r = QbSql.check(sql);
        assertTrue(r.representable(), r.reason());
        String n = QbSql.normalize(r.regenerated());
        assertTrue(n.contains("from autori a join libri_autori la on a.id=la.id_autore left join libri l on la.id_libro=l.id"),
                "FROM rigenerato: " + r.regenerated());
    }

    @Test
    void confrontoConDisuguaglianzaSpecchiata() {
        String sql = "SELECT a.cognome FROM autori a LEFT JOIN libri l ON l.anno > a.anno_nascita";
        String n = QbSql.normalize(QbSql.check(sql).regenerated());
        assertTrue(n.contains("from autori a left join libri l on a.anno_nascita<l.anno"), n);
    }
}
