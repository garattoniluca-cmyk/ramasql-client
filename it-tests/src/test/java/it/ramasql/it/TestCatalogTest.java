/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Garanzie dell'infrastruttura dei test d'integrazione: solo cataloghi {@code ramasql_test_*}, sempre distrutti. */
@Tag("step1")
class TestCatalogTest {

    @Test
    void rifiutaINomiSenzaIlPrefissoDedicato() {
        assertEquals("ramasql_test_x_1", TestCatalog.requireTestName("ramasql_test_x_1"));
        for (String vietato : new String[] {null, "", "bibliotecasoft", "scuola", "mysql", "ramasql_test_",
            "Ramasql_test_x", "ramasql_test_x`; DROP DATABASE scuola; --", "xramasql_test_a", "ramasql_test_a b"}) {
            assertThrows(IllegalArgumentException.class, () -> TestCatalog.requireTestName(vietato), String.valueOf(vietato));
        }
    }

    @Test
    void divideLoScriptInIstruzioni() {
        List<String> sql = TestCatalog.splitStatements(
                "-- commento\r\nCREATE TABLE a (\r\n  id INT -- non a inizio riga\r\n);\r\n\r\nINSERT INTO a VALUES (1);\nSELECT 'x;y' FROM a");
        assertEquals(3, sql.size());
        assertTrue(sql.get(0).startsWith("CREATE TABLE a"));
        assertEquals("INSERT INTO a VALUES (1)", sql.get(1));
        assertEquals("SELECT 'x;y' FROM a", sql.get(2));
    }

    @Tag("it")
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void creaUnCatalogoUnivocoELoDistruggeAncheSeIlTestFallisce(ItServers server) throws SQLException {
        String nome;
        try (TestCatalog cat = TestCatalog.create(server, "Infra Catalogo!")) {
            nome = cat.name();
            assertTrue(nome.startsWith("ramasql_test_infra_catalogo__"), nome);
            assertEquals(nome, cat.connection().getCatalog());
            assertTrue(esiste(cat.connection(), nome));
            assertEquals("utf8mb4", charset(cat.connection(), nome));
            try (TestCatalog altro = TestCatalog.create(server, "Infra Catalogo!")) {
                assertFalse(nome.equals(altro.name()), "due cataloghi con la stessa etichetta hanno nomi diversi");
            }
        }
        RuntimeException simulata = assertThrows(IllegalStateException.class, () -> {
            try (TestCatalog cat = TestCatalog.create(server, "infra_fallito")) {
                cat.connection().close(); // anche con la connessione principale rotta
                throw new IllegalStateException(cat.name());
            }
        });
        try (Connection con = server.connect()) {
            assertFalse(esiste(con, nome), "catalogo non distrutto: " + nome);
            assertFalse(esiste(con, simulata.getMessage()), "catalogo non distrutto dopo il fallimento: " + simulata.getMessage());
        }
    }

    private static boolean esiste(Connection con, String nome) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    private static String charset(Connection con, String nome) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
