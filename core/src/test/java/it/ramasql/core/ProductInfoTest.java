/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductInfoTest {

    @Test
    void ilTitoloContieneNomeEVersione() {
        assertEquals("RamaSQL Client", ProductInfo.NAME);
        assertTrue(ProductInfo.title().startsWith(ProductInfo.NAME + " "));
        assertEquals("dev", ProductInfo.version(), "fuori da un jar la versione vale 'dev'");
    }
}
