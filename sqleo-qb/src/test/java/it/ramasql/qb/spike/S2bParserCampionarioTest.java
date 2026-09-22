/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import it.ramasql.qb.QbSql;

/**
 * Spike S2b (docs/ROADMAP.md, Step 1): campionario di 20 query «da aula» sullo schema «biblioteca»
 * passate al parser ereditato da SQLeo: SQL → modello → SQL.
 * Esito per query: ok / non rappresentabile; mai eccezioni non gestite; criterio: almeno 15 ok.
 * «ok» = l'SQL rigenerato dal modello equivale all'originale ({@link QbSql#normalize(String)}).
 */
@Tag("step1")
class S2bParserCampionarioTest {

    /** Una query del campionario. {@code attesoOk}: esito osservato nello spike, fissato per accorgersi delle regressioni. */
    record Caso(int n, String costrutto, String sql, boolean attesoOk) {
        @Override
        public String toString() {
            return "Q" + n + " " + costrutto;
        }
    }

    static final List<Caso> CAMPIONARIO = List.of(
            new Caso(1, "SELECT semplice", "SELECT titolo, anno FROM libri", true),
            new Caso(2, "WHERE con confronto", "SELECT titolo, prezzo FROM libri WHERE prezzo > 20", true),
            new Caso(3, "alias di colonna e di tabella",
                    "SELECT l.titolo AS titolo_libro, l.anno FROM libri l WHERE l.anno >= 2000", true),
            new Caso(4, "INNER JOIN a 2 tabelle",
                    "SELECT l.titolo, e.nome FROM libri l INNER JOIN editori e ON l.id_editore = e.id", true),
            new Caso(5, "JOIN a 3 tabelle con tabella ponte",
                    "SELECT a.cognome, l.titolo FROM autori a INNER JOIN libri_autori la ON a.id = la.id_autore"
                            + " INNER JOIN libri l ON la.id_libro = l.id", true),
            new Caso(6, "LEFT JOIN",
                    "SELECT s.cognome, p.data_prestito FROM soci s LEFT JOIN prestiti p ON s.id = p.id_socio", true),
            new Caso(7, "RIGHT JOIN",
                    "SELECT e.nome, l.titolo FROM libri l RIGHT JOIN editori e ON l.id_editore = e.id", true),
            new Caso(8, "COUNT con GROUP BY",
                    "SELECT e.nome, COUNT(l.id) AS quanti FROM editori e INNER JOIN libri l ON e.id = l.id_editore"
                            + " GROUP BY e.nome", true),
            new Caso(9, "GROUP BY con HAVING",
                    "SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio FROM libri l GROUP BY l.id_editore"
                            + " HAVING AVG(l.prezzo) > 15", true),
            new Caso(10, "ORDER BY su due colonne, DESC",
                    "SELECT titolo, anno, prezzo FROM libri ORDER BY anno DESC, titolo", true),
            new Caso(11, "LIMIT", "SELECT titolo, prezzo FROM libri ORDER BY prezzo DESC LIMIT 10", true),
            new Caso(12, "LIMIT con scostamento", "SELECT cognome, nome FROM soci ORDER BY cognome LIMIT 20, 10", true),
            new Caso(13, "DISTINCT", "SELECT DISTINCT nazionalita FROM autori", true),
            new Caso(14, "IN con elenco",
                    "SELECT cognome, nome FROM autori WHERE nazionalita IN ('Italia', 'Francia', 'Spagna')", true),
            new Caso(15, "BETWEEN", "SELECT titolo FROM libri WHERE anno BETWEEN 1990 AND 2000", true),
            new Caso(16, "LIKE", "SELECT cognome, nome FROM soci WHERE cognome LIKE 'Ro%'", true),
            new Caso(17, "IS NULL (prestiti non resi)",
                    "SELECT p.id, p.data_prestito FROM prestiti p WHERE p.data_reso IS NULL", true),
            new Caso(18, "AND, OR e parentesi, IS NOT NULL",
                    "SELECT titolo FROM libri WHERE (anno < 1950 OR anno > 2010) AND prezzo IS NOT NULL", true),
            new Caso(19, "backtick e nomi qualificati catalogo.tabella",
                    "SELECT `l`.`titolo`, `e`.`nome` FROM `biblioteca`.`libri` `l` INNER JOIN `biblioteca`.`editori` `e`"
                            + " ON `l`.`id_editore` = `e`.`id` WHERE `e`.`citta` = 'Milano'", true),
            new Caso(20, "sottoquery con IN",
                    "SELECT s.cognome, s.nome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p"
                            + " WHERE p.data_reso IS NULL)", true));

    /**
     * Costrutti DEBOLI del parser ereditato (segnalati dalla revisione): non contano nel criterio «almeno 15 su 20».
     * Per ognuno l'esito atteso è «non rappresentabile»: {@code check} deve accorgersi che il giro perderebbe o
     * cambierebbe del testo, così la vista grafica si disattiva e il testo dell'utente non si tocca (R-03).
     */
    static final List<Caso> DEBOLI = List.of(
            new Caso(21, "JOIN … ON a=b AND condizione",
                    "SELECT l.titolo, e.nome FROM libri l INNER JOIN editori e ON l.id_editore = e.id AND e.citta = 'Milano'",
                    false),
            new Caso(22, "JOIN … USING (…)",
                    "SELECT p.data_prestito, s.cognome FROM prestiti p INNER JOIN soci s USING (id_socio)", false),
            new Caso(23, "CROSS JOIN", "SELECT l.titolo, e.nome FROM libri l CROSS JOIN editori e", false),
            new Caso(24, "commenti --",
                    "SELECT titolo -- il titolo\nFROM libri -- tutti i libri\nWHERE anno > 2000", false),
            new Caso(25, "ORDER BY e LIMIT dentro una sottoquery",
                    "SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p"
                            + " ORDER BY p.data_prestito DESC LIMIT 5)", false),
            new Caso(26, "UNION ALL", "SELECT cognome FROM autori UNION ALL SELECT cognome FROM soci", false),
            new Caso(27, "funzione finestra ROW_NUMBER() OVER (…)",
                    "SELECT titolo, ROW_NUMBER() OVER (PARTITION BY id_editore ORDER BY prezzo DESC) AS pos FROM libri",
                    false),
            new Caso(28, "WITH RECURSIVE",
                    "WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 5) SELECT i FROM n",
                    false));

    static Stream<Caso> campionario() {
        return Stream.concat(CAMPIONARIO.stream(), DEBOLI.stream());
    }

    /**
     * Per i costrutti deboli non basta «non rappresentabile»: il motivo deve essere la differenza di testo (o un
     * errore del parser gestito), e il testo originale deve restare disponibile intatto a chi chiama: {@code check}
     * non modifica né restituisce una versione «aggiustata» da usare al suo posto.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("deboli")
    void iCostruttiDeboliSonoIntercettatiSenzaPerditaDiTesto(Caso caso) {
        String originale = caso.sql();
        QbSql.Result esito = assertDoesNotThrow(() -> QbSql.check(caso.sql()));

        assertFalse(esito.representable(), "costrutto debole dichiarato rappresentabile: " + esito.regenerated());
        assertNotNull(esito.reason(), "manca il motivo");
        assertEquals(originale, caso.sql(), "il testo originale non deve cambiare");
        if (esito.regenerated() != null) {
            // la prova che il controllo serve: il rigenerato è DAVVERO diverso (qualcosa si perderebbe o cambierebbe)
            assertNotEquals(QbSql.normalize(originale), QbSql.normalize(esito.regenerated()),
                    "il rigenerato equivale all'originale: allora perché non è rappresentabile? " + esito.reason());
        }
        assertFalse(QbSql.isRepresentable(caso.sql()));
    }

    static Stream<Caso> deboli() {
        return DEBOLI.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("campionario")
    void ogniQueryHaUnEsitoSenzaEccezioni(Caso caso) {
        QbSql.Result esito = assertDoesNotThrow(() -> QbSql.check(caso.sql()));

        assertNotNull(esito, "esito mancante");
        if (esito.representable()) {
            assertNotNull(esito.model(), "rappresentabile ma senza modello");
            // il giro completo deve essere stabile: l'SQL rigenerato, riletto, rigenera se stesso
            QbSql.Result secondoGiro = QbSql.check(esito.regenerated());
            assertTrue(secondoGiro.representable(), "secondo giro non stabile: " + secondoGiro.reason());
        } else {
            assertNotNull(esito.reason(), "non rappresentabile ma senza motivo");
        }
        assertEquals(caso.attesoOk(), esito.representable(),
                "esito diverso da quello fissato nello spike; rigenerato: " + esito.regenerated()
                        + " - motivo: " + esito.reason());
    }

    @Test
    void almenoQuindiciSuVentiETabellaDegliEsiti() throws Exception {
        assertEquals(20, CAMPIONARIO.size(), "il campionario deve avere 20 query");

        StringBuilder md = new StringBuilder();
        md.append("# S2b - SQL -> modello -> SQL: campionario di 20 query «da aula» (schema biblioteca)\n\n");
        md.append("Generato da `").append(getClass().getName()).append("`. ")
                .append("«ok» = il parser produce un modello e l'SQL rigenerato equivale all'originale ")
                .append("(confronto normalizzato: spazi, maiuscole, backtick, AS/INNER/OUTER/ASC facoltativi).\n\n");
        md.append("| # | Costrutto | Esito | SQL originale | SQL rigenerato | Avvisi del parser / motivo |\n");
        md.append("|---|---|---|---|---|---|\n");

        int ok = 0;
        int erroriDelParser = 0;
        for (Caso caso : CAMPIONARIO) {
            QbSql.Result esito = QbSql.check(caso.sql());
            if (esito.representable()) {
                ok++;
            }
            if (esito.model() == null) {
                erroriDelParser++;
            }
            String note = String.join("; ", esito.warnings())
                    + (esito.reason() == null ? "" : (esito.warnings().isEmpty() ? "" : "; ") + esito.reason());
            md.append("| ").append(caso.n())
                    .append(" | ").append(SpikeFiles.cell(caso.costrutto()))
                    .append(" | ").append(esito.representable() ? "ok" : "non rappresentabile")
                    .append(" | `").append(SpikeFiles.cell(caso.sql())).append('`')
                    .append(" | ").append(esito.regenerated() == null ? "-" : "`" + SpikeFiles.cell(esito.regenerated()) + "`")
                    .append(" | ").append(SpikeFiles.cell(note))
                    .append(" |\n");
        }
        md.append("\n## Costrutti deboli (fuori dal criterio): devono risultare «non rappresentabile»\n\n");
        md.append("| # | Costrutto | Esito | SQL originale | SQL rigenerato (che andrebbe perso/cambiato) | Avvisi del parser / motivo |\n");
        md.append("|---|---|---|---|---|---|\n");
        int deboliIntercettati = 0;
        for (Caso caso : DEBOLI) {
            QbSql.Result esito = QbSql.check(caso.sql());
            if (!esito.representable()) {
                deboliIntercettati++;
            }
            String note = String.join("; ", esito.warnings())
                    + (esito.reason() == null ? "" : (esito.warnings().isEmpty() ? "" : "; ") + esito.reason());
            md.append("| ").append(caso.n())
                    .append(" | ").append(SpikeFiles.cell(caso.costrutto()))
                    .append(" | ").append(esito.representable() ? "ok (INATTESO)" : "non rappresentabile (intercettato)")
                    .append(" | `").append(SpikeFiles.cell(caso.sql().replace('\n', ' '))).append('`')
                    .append(" | ").append(esito.regenerated() == null ? "-" : "`" + SpikeFiles.cell(esito.regenerated()) + "`")
                    .append(" | ").append(SpikeFiles.cell(note))
                    .append(" |\n");
        }
        md.append("\nCostrutti deboli intercettati: ").append(deboliIntercettati).append(" su ").append(DEBOLI.size())
                .append(". Per questi la vista grafica si disattiva e il testo resta quello dell'utente.\n");
        md.append("\n**Totale: ").append(ok).append(" ok su ").append(CAMPIONARIO.size())
                .append("** (criterio: almeno 15). Query su cui il parser ha lanciato un'eccezione (gestita): ")
                .append(erroriDelParser).append(".\n");
        SpikeFiles.write("S2b-esiti.md", md.toString());

        assertEquals(DEBOLI.size(), deboliIntercettati, "un costrutto debole non è stato intercettato");
        assertTrue(ok >= 15, "solo " + ok + " query su 20 sono rappresentabili (criterio: almeno 15)");
    }
}
