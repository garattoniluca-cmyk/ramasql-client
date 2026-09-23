/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T4.6: tabella delle spiegazioni in italiano e lettura della posizione dai messaggi del server. */
@Tag("step4")
@Tag("ui")
class ErrorExplainerTest {

    @ParameterizedTest
    @ValueSource(ints = {1064, 1146, 1054, 1062, 1451, 1452, 1045, 1044, 1049, 1050, 1136})
    void codiciFrequentiHannoUnaSpiegazioneInItaliano(int code) {
        Optional<String> e = ErrorExplainer.explain(code);
        assertTrue(e.isPresent(), "manca la spiegazione di " + code);
        assertFalse(e.get().isBlank());
    }

    @Test
    void codiceSconosciutoNonHaSpiegazione() {
        assertTrue(ErrorExplainer.explain(4242).isEmpty());
    }

    @Test
    void posizioneDalMessaggioMariaDbConTestoSuPiuRigheEApici() {
        var loc = ErrorExplainer.locate("You have an error in your SQL syntax; check the manual that corresponds to "
                + "your MariaDB server version for the right syntax to use near 'FORM libri\nWHERE nome = 'x'' at line 12");
        assertEquals(12, loc.orElseThrow().line());
        assertEquals("FORM libri\nWHERE nome = 'x'", loc.get().near());
    }

    @Test
    void posizioneAllaFineDellIstruzione() {
        var loc = ErrorExplainer.locate("... for the right syntax to use near '' at line 1");
        assertEquals(1, loc.orElseThrow().line());
        assertEquals("", loc.get().near());
    }

    @Test
    void messaggioSenzaPosizione() {
        assertTrue(ErrorExplainer.locate("Table 'biblioteca.libro' doesn't exist").isEmpty());
        assertTrue(ErrorExplainer.locate(null).isEmpty());
    }

    @Test
    void descrizioneConMessaggioSpiegazioneERiga() {
        String d = ErrorExplainer.describe(new SqlError(1062, "23000", "Duplicate entry '7' for key 'tessera'"), 3, 9);
        assertEquals("Istruzione 3 — errore 1062 (23000): Duplicate entry '7' for key 'tessera'\n"
                + "In parole semplici: Valore duplicato: esiste già una riga con lo stesso valore nella chiave primaria "
                + "o in un indice UNIQUE.\n"
                + "Il punto dell'errore è evidenziato nell'editor, alla riga 9.", d);
    }
}
