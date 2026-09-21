/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModelFormatTest {

    @Test
    void estensioneEVersioneDelFormato() {
        assertEquals("rsqlmodel", ModelFormat.FILE_EXTENSION);
        assertEquals(1, ModelFormat.FORMAT_VERSION);
    }
}
