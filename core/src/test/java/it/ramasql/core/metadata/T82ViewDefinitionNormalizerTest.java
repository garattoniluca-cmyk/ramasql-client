/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T8.2 (parte senza server) — il normalizzatore sulle <b>definizioni reali</b> rilette da
 * {@code information_schema.VIEWS} di MariaDB 11.5 e MySQL 8.0 nello spike S2c (13 per server, 26 in tutto: le 11
 * viste della fixture e le 2 viste reali di {@code bibliotecasoft} / di {@code scuola}, file
 * {@code viste-reali-*.tsv} copiati da {@code test-results/step1/S2c-definizioni-*.md}).
 *
 * <p>Per ciascuna: nessuna eccezione; il catalogo sparisce; <b>nulla di significativo si perde</b> (stesse stringhe,
 * stessi numeri, stesse parole chiave, parentesi bilanciate); per le forme tipiche il testo atteso è scritto a mano.
 * Che il parser del query builder accetti le forme normalizzate lo verifica la parte con il parser
 * ({@code T82ViewReopeningTest}, modulo {@code app}).
 */
@Tag("step8")
class T82ViewDefinitionNormalizerTest {

    record Definizione(String server, String vista, String catalogo, String testo) {
        @Override
        public String toString() {
            return server + " " + vista;
        }
    }

    private static final Pattern CATALOGO = Pattern.compile("`(ramasql_test_[a-z0-9_]+)`");

    static List<Definizione> definizioni() {
        List<Definizione> out = new ArrayList<>();
        for (String server : List.of("mariadb", "mysql")) {
            String risorsa = "viste-reali-" + server + ".tsv";
            try (InputStream in = T82ViewDefinitionNormalizerTest.class.getResourceAsStream(risorsa)) {
                String tutto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String riga : tutto.split("\\R")) {
                    if (riga.isBlank()) {
                        continue;
                    }
                    String[] p = riga.split("\t", 2);
                    Matcher m = CATALOGO.matcher(p[1]);
                    assertTrue(m.find(), "catalogo non trovato in " + p[0]);
                    out.add(new Definizione(server, p[0], m.group(1), p[1]));
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        return out;
    }

    static Stream<Definizione> tutte() {
        return definizioni().stream();
    }

    @Test
    void campionarioDiAlmeno15DefinizioniRealiDaiDueServer() {
        List<Definizione> d = definizioni();
        assertEquals(26, d.size());
        assertEquals(13, d.stream().filter(x -> x.server().equals("mysql")).count());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tutte")
    void normalizzataSenzaPerditeESenzaCatalogo(Definizione d) {
        String n = ViewDefinitionNormalizer.normalize(d.testo(), d.catalogo());
        assertFalse(n.isBlank());
        assertFalse(n.contains(d.catalogo()), "resta il catalogo: " + n);
        assertEquals(letterali(d.testo()), letterali(n), "stringhe e numeri devono restare tutti");
        assertEquals(paroleChiave(d.testo()), paroleChiave(n), "parole chiave perse o aggiunte");
        assertTrue(bilanciate(n), "parentesi non bilanciate: " + n);
        assertFalse(n.contains("_utf8mb4'") || n.contains("_latin1'"), "introducer rimasto: " + n);
        // idempotente: normalizzare due volte non cambia più nulla
        assertEquals(n, ViewDefinitionNormalizer.normalize(n, d.catalogo()));
    }

    // ---------------------------------------------------------------- forme attese scritte a mano

    private static String reale(String server, String vista) {
        Definizione d = definizioni().stream().filter(x -> x.server().equals(server) && x.vista().equals(vista))
                .findFirst().orElseThrow();
        return ViewDefinitionNormalizer.normalize(d.testo(), d.catalogo());
    }

    @Test
    void semplice() {
        assertEquals("select `libri`.`titolo`,`libri`.`anno`,`libri`.`prezzo` from `libri` where `libri`.`anno` >= 1980",
                reale("mariadb", "v01_semplice"));
        assertEquals(reale("mariadb", "v01_semplice"), reale("mysql", "v01_semplice"));
    }

    @Test
    void joinConParentesiDoppieDiMysql() {
        String atteso = "select `l`.`titolo`,`e`.`nome` AS `editore` from `libri` `l` join `editori` `e`"
                + " on `l`.`id_editore` = `e`.`id`";
        assertEquals(atteso, reale("mariadb", "v02_join2"));
        assertEquals(atteso, reale("mysql", "v02_join2"));
    }

    @Test
    void joinATreTabelleAnnidatiASinistra() {
        String atteso = "select `a`.`cognome`,`a`.`nome`,`l`.`titolo` from `autori` `a` join `libri_autori` `la`"
                + " on `la`.`id_autore` = `a`.`id` join `libri` `l` on `la`.`id_libro` = `l`.`id`";
        assertEquals(atteso, reale("mariadb", "v03_join3"));
        assertEquals(atteso, reale("mysql", "v03_join3"));
    }

    @Test
    void aggregataConHaving() {
        String atteso = "select `e`.`nome` AS `editore`,count(`l`.`id`) AS `n_libri`,avg(`l`.`prezzo`) AS"
                + " `prezzo_medio` from `editori` `e` join `libri` `l` on `l`.`id_editore` = `e`.`id` group by"
                + " `e`.`nome` having count(`l`.`id`) >= 2";
        assertEquals(atteso, reale("mariadb", "v05_aggregata"));
        assertEquals(atteso, reale("mysql", "v05_aggregata"));
    }

    @Test
    void sottoquery() {
        String atteso = "select `l`.`titolo`,`l`.`prezzo` from `libri` `l` where `l`.`prezzo` >"
                + " (select avg(`l2`.`prezzo`) from `libri` `l2`)";
        assertEquals(atteso, reale("mariadb", "v08_sottoquery"));
        assertEquals(atteso, reale("mysql", "v08_sottoquery"));
    }

    @Test
    void vistaSuVistaConAliasQualificatoDaMysql() {
        // MySQL scrive `cat`.`v`.`editore`: l'alias della vista qualificato con il catalogo (tre parti)
        String atteso = "select `v`.`editore`,`v`.`titolo` from `v02_join2` `v` where `v`.`editore` like 'E%'";
        assertEquals(atteso, reale("mariadb", "v09_vista_su_vista"));
        assertEquals(atteso, reale("mysql", "v09_vista_su_vista"));
    }

    // ---------------------------------------------------------------- limiti della bozza (BUG-010)

    @Test
    void aliasDiTabellaUgualeAlNomeDelCatalogoNonSiTocca() {
        String def = "select `scuola`.`nome` AS `nome` from `scuola`.`alunni` `scuola` where (`scuola`.`id` > 3)";
        assertEquals("select `scuola`.`nome` from `alunni` `scuola` where `scuola`.`id` > 3",
                ViewDefinitionNormalizer.normalize(def, "scuola"));
    }

    @Test
    void parentesiAritmeticheDiUnElementoDellaSelect() {
        String def = "select (`l`.`prezzo` * 1.22) AS `lordo`,round((`l`.`prezzo` * 1.22),2) AS `r`"
                + " from `c`.`libri` `l` where ((`l`.`prezzo` + 1) > 10)";
        assertEquals("select `l`.`prezzo` * 1.22 AS `lordo`,round((`l`.`prezzo` * 1.22),2) AS `r` from `libri` `l`"
                + " where (`l`.`prezzo` + 1) > 10", ViewDefinitionNormalizer.normalize(def, "c"));
    }

    @Test
    void joinAnnidatoADestraRestaComEra() {
        // a LEFT JOIN (b JOIN c) ON …: togliere le parentesi cambierebbe il risultato
        String def = "select `a`.`x` AS `x` from (`c`.`a` left join (`c`.`b` join `c`.`d` on((`b`.`id` = `d`.`id`)))"
                + " on((`a`.`id` = `b`.`id`)))";
        String n = ViewDefinitionNormalizer.normalize(def, "c");
        assertTrue(n.contains("left join (`b` join `d` on `b`.`id` = `d`.`id`)"), n);
        assertTrue(bilanciate(n));
    }

    @Test
    void introducerDiCharsetEStringheIntatte() {
        String def = "select `c`.`t`.`a` AS `a` from `c`.`t` where `c`.`t`.`b` = _utf8mb4'x (`c`.`t`) y'"
                + " and `c`.`t`.`d` in (_latin1'1',_utf8mb4'2')";
        assertEquals("select `t`.`a` from `t` where `t`.`b` = 'x (`c`.`t`) y' and `t`.`d` in ('1','2')",
                ViewDefinitionNormalizer.normalize(def, "c"));
    }

    @Test
    void predicatiTraAndOrENot() {
        String def = "select `t`.`a` AS `a` from `c`.`t` where ((`t`.`a` is not null) and ((`t`.`b` = 1) or (not"
                + " (`t`.`c` = 2))))";
        assertEquals("select `t`.`a` from `t` where `t`.`a` is not null and (`t`.`b` = 1 or not (`t`.`c` = 2))",
                ViewDefinitionNormalizer.normalize(def, "c"));
    }

    @Test
    void introducerBinarioResta() {
        // _binary cambia il confronto (a byte, non per collation): toglierlo cambierebbe il significato
        String def = "select `t`.`a` AS `a` from `c`.`t` where `t`.`b` = _binary'Abc' and `t`.`d` = _utf8mb4'x'";
        assertEquals("select `t`.`a` from `t` where `t`.`b` = _binary'Abc' and `t`.`d` = 'x'",
                ViewDefinitionNormalizer.normalize(def, "c"));
    }

    @Test
    void condizioniAnnidateCaseInNotERowConstructor() {
        String def = "select (case when (`t`.`a` > 1) then 'alto' else 'basso' end) AS `livello` from `c`.`t`"
                + " where (((`t`.`a` = 1) or (`t`.`b` = 2)) and (not((`t`.`c` in (1,2,3)))) and"
                + " ((`t`.`d`,`t`.`e`) = (1,2)))";
        String n = ViewDefinitionNormalizer.normalize(def, "c");
        // le parentesi che raggruppano l'OR restano (AND lega più di OR); NOT(…) e il row constructor restano
        assertTrue(n.contains("(`t`.`a` = 1 or `t`.`b` = 2) and"), n);
        assertTrue(n.contains("not((`t`.`c` in (1,2,3)))") || n.contains("not(`t`.`c` in (1,2,3))"), n);
        assertTrue(n.contains("(`t`.`d`,`t`.`e`) = (1,2)"), n);
        assertTrue(n.contains("case when"), n);
        assertTrue(bilanciate(n), n);
        assertEquals(paroleChiave(def), paroleChiave(n));
        assertEquals(letterali(def), letterali(n));
    }

    @Test
    void maiEccezioni() {
        for (String s : new String[] {null, "", ")))(((", "select `", "select 'aperta", "`c`.", "(select",
                "select ( from where (", "\u0000"}) {
            String n = ViewDefinitionNormalizer.normalize(s, "c");
            assertTrue(n != null);
        }
        assertEquals("", ViewDefinitionNormalizer.normalize(null, "c"));
    }

    @Test
    void senzaCatalogoNonSiTogliePrefisso() {
        String def = "select `c`.`t`.`a` AS `a` from `c`.`t`";
        assertEquals("select `c`.`t`.`a` from `c`.`t`", ViewDefinitionNormalizer.normalize(def, null));
        assertEquals("select `t`.`a` AS `a` from `t`", ViewDefinitionNormalizer.stripCatalog(def, "c"));
    }

    // ---------------------------------------------------------------- aiutanti

    private static final Pattern LETTERALE = Pattern.compile("'(?:[^'\\\\]|\\\\.|'')*'|\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern PAROLA = Pattern.compile("`[^`]*`|'(?:[^'\\\\]|\\\\.|'')*'|[A-Za-z_]+");
    private static final List<String> CHIAVI = List.of("select", "distinct", "from", "join", "left", "right", "on",
            "where", "and", "or", "not", "group", "by", "having", "order", "limit", "desc", "is", "null", "like",
            "in", "exists", "case", "when", "then", "else", "end", "count", "sum", "avg", "min", "max", "union");

    private static List<String> letterali(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = LETTERALE.matcher(s.replaceAll("`[^`]*`", "``"));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static List<String> paroleChiave(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = PAROLA.matcher(s);
        while (m.find()) {
            String w = m.group().toLowerCase(Locale.ROOT);
            if (CHIAVI.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private static boolean bilanciate(String s) {
        int depth = 0;
        String senzaTesti = s.replaceAll("'(?:[^'\\\\]|\\\\.|'')*'", "''").replaceAll("`[^`]*`", "``");
        for (char c : senzaTesti.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }
}
