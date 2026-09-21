/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ItConfigTest {

    @Test
    void iTestUsanoSoloCataloghiConIlPrefissoDedicato() {
        assertEquals("ramasql_test_", ItConfig.TEST_CATALOG_PREFIX);
    }
}
