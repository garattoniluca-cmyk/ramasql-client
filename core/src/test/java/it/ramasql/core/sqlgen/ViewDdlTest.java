/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.exec.SqlScript;

/** T8.1 — generatore delle viste: {@code CREATE VIEW}, {@code CREATE OR REPLACE VIEW}, {@code DROP VIEW}, backtick. */
@Tag("step8")
class ViewDdlTest {

    private static final String SELECT = "SELECT p.id, s.cognome FROM prestiti p INNER JOIN soci s ON p.id_socio = s.id"
            + " WHERE p.data_reso IS NULL";

    @Test
    void creaVista() {
        assertEquals("CREATE VIEW `biblioteca`.`v_prestiti_aperti` AS\n" + SELECT,
                ViewDdl.createView("biblioteca", "v_prestiti_aperti", SELECT, false));
    }

    @Test
    void creaOSostituisciVista() {
        assertEquals("CREATE OR REPLACE VIEW `biblioteca`.`v_prestiti_aperti` AS\n" + SELECT,
                ViewDdl.createView("biblioteca", "v_prestiti_aperti", SELECT, true));
    }

    @Test
    void eliminaVista() {
        assertEquals("DROP VIEW `biblioteca`.`v_prestiti_aperti`", ViewDdl.dropView("biblioteca", "v_prestiti_aperti"));
        // lo stesso SQL dell'eliminazione dal navigatore (Step 3): una sola regola
        assertEquals(ObjectDdl.dropView("biblioteca", "v_prestiti_aperti"),
                ViewDdl.dropView("biblioteca", "v_prestiti_aperti"));
    }

    @Test
    void nomiConSpaziParoleRiservateEBacktick() {
        assertEquals("CREATE VIEW `scuola 2`.`order` AS\nSELECT 1", ViewDdl.createView("scuola 2", "order", "SELECT 1", false));
        assertEquals("CREATE VIEW `c`.`vista ``strana``` AS\nSELECT 1",
                ViewDdl.createView("c", "vista `strana`", "SELECT 1", false));
        assertEquals("DROP VIEW `a``b`.`select`", ViewDdl.dropView("a`b", "select"));
    }

    @Test
    void senzaCatalogoIlNomeNonEQualificato() {
        assertEquals("CREATE VIEW `v` AS\nSELECT 1", ViewDdl.createView(null, "v", "SELECT 1", false));
        assertEquals("DROP VIEW `v`", ViewDdl.dropView(" ", "v"));
    }

    @Test
    void puntoEVirgolaESpaziFinaliTolti() {
        assertEquals("CREATE VIEW `c`.`v` AS\nSELECT 1", ViewDdl.createView("c", "v", "  SELECT 1 ;; \n", false));
    }

    @Test
    void testoSuPiuRigheConservato() {
        String multi = "SELECT\n\tl.titolo\nFROM\n\tlibri l";
        assertEquals("CREATE VIEW `c`.`v` AS\n" + multi, ViewDdl.createView("c", "v", multi, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"select 1", "SELECT\n1", "WITH x AS (SELECT 1) SELECT * FROM x",
            "(SELECT 1) UNION (SELECT 2)"})
    void inizioAmmesso(String select) {
        assertTrue(ViewDdl.createView("c", "v", select, false).endsWith(select.strip()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ";", "DELETE FROM libri", "selectx FROM t", "UPDATE t SET a=1",
            "DROP TABLE libri"})
    void soloUnaSelect(String notSelect) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ViewDdl.createView("c", "v", notSelect, false));
        assertEquals(it.ramasql.core.CoreMessages.get("sqlgen.view.notSelect"), e.getMessage());
    }

    @Test
    void nomeObbligatorio() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ViewDdl.createView("c", " ", "SELECT 1", false));
        assertEquals("Manca il nome della vista.", e.getMessage());
    }

    @Test
    void scriptPerLaPipeline() {
        SqlScript s = ViewDdl.createScript("biblioteca", "v_prestiti_aperti", SELECT, true);
        assertEquals("Sostituisci la vista «v_prestiti_aperti»", s.title());
        assertEquals(SqlOrigin.QUERY_BUILDER.label(), s.origin());
        // prima il catalogo: i nomi non qualificati della SELECT si risolvono nel catalogo corrente della sessione
        assertEquals(2, s.statements().size());
        assertEquals("USE `biblioteca`", s.statements().get(0).text());
        assertEquals("CREATE OR REPLACE VIEW `biblioteca`.`v_prestiti_aperti` AS\n" + SELECT,
                s.statements().get(1).text());
        assertEquals(1, ViewDdl.createScript(null, "v", "SELECT 1", false).statements().size(),
                "senza catalogo nessun USE");
        assertEquals("Crea la vista «v»", ViewDdl.createScript("c", "v", "SELECT 1", false).title());
        // creare o sostituire una vista cambia il catalogo ma non distrugge dati: conferma normale, non rafforzata
        assertEquals(RiskLevel.MODIFIES, s.risk());
        assertEquals(ConfirmationPolicy.Level.CONFIRM, ConfirmationPolicy.evaluate(s).level());
        assertFalse(ViewDdl.createScript("c", "v", "SELECT 1", false).risk() == RiskLevel.DESTRUCTIVE);
        assertTrue(s.text().endsWith(";"));
    }
}
