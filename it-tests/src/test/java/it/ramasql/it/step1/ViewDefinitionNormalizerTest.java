/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regole della bozza di {@link ViewDefinitionNormalizer} su testi realmente restituiti da MariaDB 11.5 e MySQL 8.0
 * (spike S2c). Test senza database: l'equivalenza sul server la verifica {@link S2cVisteRiletteTest}.
 */
@Tag("step1")
class ViewDefinitionNormalizerTest {

    private static final String CAT = "ramasql_test_x";

    @Test
    void mariaDbJoinATreTabelle() {
        String server = "select `a`.`cognome` AS `cognome`,`l`.`titolo` AS `titolo` from ((`ramasql_test_x`.`autori` `a`"
                + " join `ramasql_test_x`.`libri_autori` `la` on(`la`.`id_autore` = `a`.`id`))"
                + " join `ramasql_test_x`.`libri` `l` on(`la`.`id_libro` = `l`.`id`))";
        assertEquals("select `a`.`cognome`,`l`.`titolo` from `autori` `a` join `libri_autori` `la`"
                        + " on `la`.`id_autore` = `a`.`id` join `libri` `l` on `la`.`id_libro` = `l`.`id`",
                ViewDefinitionNormalizer.normalize(server, CAT));
    }

    @Test
    void mySqlParentesiDoppieEAliasQualificatoColCatalogo() {
        String server = "select `ramasql_test_x`.`v`.`editore` AS `editore` from `ramasql_test_x`.`v02_join2` `v`"
                + " where ((`ramasql_test_x`.`v`.`editore` like 'E%') and (`ramasql_test_x`.`v`.`anno` > 1970))";
        assertEquals("select `v`.`editore` from `v02_join2` `v` where `v`.`editore` like 'E%' and `v`.`anno` > 1970",
                ViewDefinitionNormalizer.normalize(server, CAT));
    }

    @Test
    void introducerDiCharsetEStringheIntoccate() {
        String server = "select ifnull(`s`.`email`,_utf8mb4'(`ramasql_test_x`.`x`) and (1)') AS `email`,"
                + "concat(`s`.`nome`,_latin1' ',`s`.`cognome`) AS `socio` from `ramasql_test_x`.`soci` `s`";
        assertEquals("select ifnull(`s`.`email`,'(`ramasql_test_x`.`x`) and (1)') AS `email`,"
                        + "concat(`s`.`nome`,' ',`s`.`cognome`) AS `socio` from `soci` `s`",
                ViewDefinitionNormalizer.normalize(server, CAT));
    }

    @Test
    void parentesiCheContanoRestano() {
        String server = "select `l`.`titolo` AS `t` from `ramasql_test_x`.`libri` `l`"
                + " where ((`l`.`anno` > 1980) and ((`l`.`copie` = 0) or (`l`.`prezzo` is null)))"
                + " and (not((`l`.`id` in (1,2,3)))) and (`l`.`prezzo` between 5 and 10)";
        assertEquals("select `l`.`titolo` AS `t` from `libri` `l`"
                        + " where (`l`.`anno` > 1980 and (`l`.`copie` = 0 or `l`.`prezzo` is null))"
                        + " and not(`l`.`id` in (1,2,3)) and (`l`.`prezzo` between 5 and 10)",
                ViewDefinitionNormalizer.normalize(server, CAT));
    }

    @Test
    void sottoqueryETabelleDerivateNonPerdonoLeParentesi() {
        String server = "select `l`.`titolo` AS `titolo` from `ramasql_test_x`.`libri` `l` where (`l`.`prezzo` >"
                + " (select avg(`l2`.`prezzo`) from `ramasql_test_x`.`libri` `l2`))";
        assertEquals("select `l`.`titolo` from `libri` `l` where `l`.`prezzo` > (select avg(`l2`.`prezzo`) from `libri` `l2`)",
                ViewDefinitionNormalizer.normalize(server, CAT));
    }

    @Test
    void altroCatalogoNonSiTocca() {
        String server = "select `t`.`a` AS `a` from `altro_catalogo`.`t` `t`";
        assertEquals("select `t`.`a` from `altro_catalogo`.`t` `t`", ViewDefinitionNormalizer.normalize(server, CAT));
    }
}
