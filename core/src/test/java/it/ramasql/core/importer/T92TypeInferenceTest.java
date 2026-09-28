/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.importer.ValueParsing.DateOrder;
import it.ramasql.core.importer.ValueParsing.DecimalStyle;

/**
 * T9.2 — deduzione dei tipi: interi, decimali con la virgola e con il punto, date {@code gg/mm/aaaa} e ISO, valori
 * logici, testo lungo, colonna tutta vuota, zeri iniziali (CAP, telefono → testo). Tipo proposto atteso per ogni
 * colonna.
 */
@Tag("step9")
class T92TypeInferenceTest {

    private static TypeInference.Inferred infer(Object... values) {
        ColumnProfile p = new ColumnProfile("c");
        Arrays.stream(values).forEach(p::add);
        return TypeInference.infer(p);
    }

    private static String type(Object... values) {
        return infer(values).column().fullType();
    }

    @Test
    void interi() {
        assertEquals("INT", type("1", "42", "-7", "+3"));
        TypeInference.Inferred i = infer("3", "1998");
        assertTrue(i.explanation().contains("3") && i.explanation().contains("1998"), i.explanation());
    }

    @Test
    void interiTroppoGrandiPerIntSonoBigint() {
        assertEquals("BIGINT", type("1", "3000000000"));
    }

    @Test
    void interiOltreBigintSonoTesto() {
        assertEquals("VARCHAR(20)", type("12345678901234567890", "1"));
    }

    @Test
    void decimaliConLaVirgola() {
        TypeInference.Inferred i = infer("12,50", "8", "1.234,5", "0,99");
        assertEquals("DECIMAL(6,2)", i.column().fullType());
        assertEquals(DecimalStyle.COMMA, i.decimals());
    }

    @Test
    void decimaliConIlPunto() {
        TypeInference.Inferred i = infer("12.50", "8", "123.456");
        assertEquals("DECIMAL(6,3)", i.column().fullType());
        assertEquals(DecimalStyle.DOT, i.decimals());
    }

    @Test
    void numeriConEsponenteSonoDouble() {
        assertEquals("DOUBLE", type("1.5E3", "2"));
    }

    @Test
    void dateItaliane() {
        TypeInference.Inferred i = infer("12/03/2001", "1/1/1999", "31/12/1985");
        assertEquals("DATE", i.column().fullType());
        assertEquals(DateOrder.DMY, i.dates());
    }

    @Test
    void dateIso() {
        TypeInference.Inferred i = infer("2001-03-12", "1999-01-01");
        assertEquals("DATE", i.column().fullType());
        assertEquals(DateOrder.YMD, i.dates());
    }

    @Test
    void dateAmericaneSeIlSecondoNumeroSuperaDodici() {
        TypeInference.Inferred i = infer("03/12/2001", "12/31/1999");
        assertEquals("DATE", i.column().fullType());
        assertEquals(DateOrder.MDY, i.dates());
    }

    @Test
    void dataInesistenteRendeLaColonnaTesto() {
        assertEquals("CHAR(10)", type("12/03/2001", "31/02/2001"));
    }

    @Test
    void dateConOra() {
        assertEquals("DATETIME", type("12/03/2001 08:30", "2001-03-13 17:05:10"));
    }

    @Test
    void soloOre() {
        assertEquals("TIME", type("08:30", "17:05:10"));
    }

    @Test
    void valoriLogici() {
        assertEquals("TINYINT(1)", type("vero", "falso", "VERO"));
        assertEquals("TINYINT(1)", type("sì", "no"));
        assertEquals("TINYINT(1)", type("true", "false"));
        assertEquals("TINYINT(1)", type(Boolean.TRUE, Boolean.FALSE), "valori logici JSON");
    }

    @Test
    void testoCortoEVarcharArrotondato() {
        assertEquals("VARCHAR(20)", type("Rossi", "Bianchi Verdi"));
        assertEquals("VARCHAR(255)", type("x".repeat(201), "y"));
    }

    @Test
    void testoLungoEText() {
        assertEquals("TEXT", type("x".repeat(300), "breve"));
        assertEquals("MEDIUMTEXT", type("x".repeat(20_000)));
    }

    @Test
    void soloCifreSenzaZeriDavantiRestanoNumeri() {
        // un ISBN di sole cifre è un intero che sta in BIGINT: il tipo proposto si può cambiare al passo 3
        assertEquals("BIGINT", type("9788804668237", "9788845292613"));
    }

    @Test
    void tessereDiLunghezzaFissaSonoChar() {
        assertEquals("CHAR(8)", type("T0000001", "T0000002", "T0000003"));
    }

    @Test
    void colonnaTuttaVuota() {
        TypeInference.Inferred i = infer("", null, " ");
        assertEquals("VARCHAR(255)", i.column().fullType());
        assertTrue(i.column().nullable());
    }

    @Test
    void zeriInizialiCapETelefonoSonoTesto() {
        assertEquals("CHAR(5)", type("00144", "47521", "20121"), "CAP");
        TypeInference.Inferred tel = infer("0541123456", "3331234567");
        assertTrue(tel.column().dataType().equals("CHAR") || tel.column().dataType().equals("VARCHAR"),
                tel.column().fullType());
        assertTrue(tel.explanation().contains("zero"), tel.explanation());
    }

    @Test
    void annullabileSoloSeCiSonoVuoti() {
        assertFalse(infer("1", "2").column().nullable());
        assertTrue(infer("1", "", "2").column().nullable());
    }

    @Test
    void misteNumeriETestoSonoTesto() {
        assertEquals("VARCHAR(10)", type("12", "dodici"));
    }

    @Test
    void numeriJson() {
        assertEquals("INT", type(1L, 200L));
        assertEquals("DECIMAL(5,2)", type(new java.math.BigDecimal("12.5"), new java.math.BigDecimal("199.99")));
    }

    @Test
    void oggettoAnnidatoJsonETesto() {
        assertEquals("TEXT", type(new SourceRow.JsonText("{\"a\":1}"), "x"));
    }

    private static TypeInference.Inferred named(String name, Object... values) {
        ColumnProfile p = new ColumnProfile(name);
        Arrays.stream(values).forEach(p::add);
        return TypeInference.infer(p);
    }

    @Test
    void telefonoSenzaZeroMaConIlNomeETesto() {
        TypeInference.Inferred tel = named("telefono", "3331234567", "3479876543");
        assertEquals("CHAR(10)", tel.column().fullType());
        assertTrue(tel.explanation().contains("codice"), tel.explanation());
        assertEquals("CHAR(13)", named("isbn", "9788804668237", "9788845292613").column().fullType());
        assertEquals("CHAR(5)", named("cap", "47121", "40100").column().fullType());
        assertEquals("INT", named("capitolo", "1", "12").column().fullType(), "«capitolo» non è un CAP");
        assertEquals("INT", named("anno", "1999", "2005").column().fullType());
    }

    @Test
    void segnoPiuETelefonoInternazionale() {
        TypeInference.Inferred t = named("contatto", "+393331234567", "+390541123456");
        assertEquals("CHAR(13)", t.column().fullType());
        assertTrue(t.explanation().contains("+"), t.explanation());
    }

    @Test
    void colonnaIdConZeroNonDiventaChiaveAutomatica() {
        ColumnProfile p = new ColumnProfile("id");
        Arrays.asList("0", "1", "2").forEach(p::add);
        assertFalse(p.uniqueIntegers(), "uno 0 in AUTO_INCREMENT farebbe generare un numero nuovo");
    }

    @Test
    void colonnaIdGrandeMaCrescenteEUnica() {
        ColumnProfile p = new ColumnProfile("id");
        for (long i = 0; i < 1000; i++) {
            p.add(String.valueOf(5_000_000_000L + i));
        }
        assertTrue(p.uniqueIntegers(), "fuori dalla mappa di bit ma sempre crescente");
        ColumnProfile q = new ColumnProfile("id");
        Arrays.asList("5000000002", "5000000001").forEach(q::add);
        assertFalse(q.uniqueIntegers(), "fuori dalla mappa e non crescente: non si sa, non si propone");
    }

    @Test
    void colonnaIdConInteriUniciPuoEssereChiave() {
        ColumnProfile p = new ColumnProfile("id");
        Arrays.asList("1", "2", "3").forEach(p::add);
        assertTrue(p.uniqueIntegers());
        ColumnProfile d = new ColumnProfile("id");
        Arrays.asList("1", "2", "2").forEach(d::add);
        assertFalse(d.uniqueIntegers());
    }
}
