/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Il confronto di {@link QbSql#check} considera uguali «a = b» e «b = a» (spike S2c), ma solo per operandi semplici:
 * non deve nascondere differenze vere.
 */
@Tag("step1")
class QbSqlUguaglianzeTest {

    private static String canonico(String sql) {
        return QbSql.canonicalEqualities(QbSql.normalize(sql));
    }

    @Test
    void operandiSempliciInOrdineIndifferente() {
        assertEquals(canonico("SELECT 1 FROM a JOIN b ON b.y = a.x WHERE (b.k = 3 AND 4 <> a.z)"),
                canonico("SELECT 1 FROM a JOIN b ON a.x = b.y WHERE (3 = b.k AND a.z <> 4)"));
    }

    @Test
    void operandiCompostiNonSiToccano() {
        assertNotEquals(canonico("SELECT 1 FROM a WHERE a.x = a.y + 1"), canonico("SELECT 1 FROM a WHERE a.y = a.x + 1"));
        assertNotEquals(canonico("SELECT 1 FROM a WHERE 1 + a.x = a.y"), canonico("SELECT 1 FROM a WHERE 1 + a.y = a.x"));
        assertNotEquals(canonico("SELECT 1 FROM a WHERE f(a.x) = a.y"), canonico("SELECT 1 FROM a WHERE f(a.y) = a.x"));
    }

    @Test
    void altriOperatoriNonSiToccano() {
        assertNotEquals(canonico("SELECT 1 FROM a WHERE a.x < a.y"), canonico("SELECT 1 FROM a WHERE a.y < a.x"));
        assertNotEquals(canonico("SELECT 1 FROM a WHERE a.x >= a.y"), canonico("SELECT 1 FROM a WHERE a.y >= a.x"));
    }
}
