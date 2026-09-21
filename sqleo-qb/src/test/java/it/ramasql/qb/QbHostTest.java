/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QbHostTest {

    @Test
    void laFacciataEUnInterfaccia() {
        assertTrue(QbHost.class.isInterface());
    }
}
