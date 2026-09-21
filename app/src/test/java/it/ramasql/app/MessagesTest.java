/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void iTestiDellInterfacciaSonoNelFileDiRisorse() {
        ResourceBundle texts = ResourceBundle.getBundle("it.ramasql.app.messages");
        assertFalse(texts.getString("welcome").isBlank());
    }

    @Test
    void lIconaHaTutteLeDimensioni() {
        assertEquals(7, AppIcon.images().size());
    }
}
