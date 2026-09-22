/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqleo.querybuilder.QueryModel;
import com.sqleo.querybuilder.syntax.DerivedTable;
import com.sqleo.querybuilder.syntax.QueryExpression;
import com.sqleo.querybuilder.syntax.QuerySpecification;
import com.sqleo.querybuilder.syntax.QueryTokens;
import com.sqleo.querybuilder.syntax.SubQuery;

import it.ramasql.qb.QbSql;

/**
 * Spike S2d, parte U (docs/ROADMAP.md, Step 1; docs/FEASIBILITY.md F-05bis): query nidificate.
 * Per ogni costrutto: SQL → modello → SQL senza perdita di testo, e il modello deve contenere davvero
 * nodi {@link SubQuery} / {@link DerivedTable} (non testo opaco dentro un'espressione).
 */
@Tag("step1")
class S2dQueryNidificateTest {

    enum Esito {
        /** modello con nodi di sottoquery e SQL rigenerato equivalente: si può aprire nella vista grafica */
        GRAFICO_OK("grafico ok"),
        /** il modello ha i nodi giusti ma l'SQL rigenerato è scritto in altra forma (testo originale non conservato) */
        GRAFICO_CON_RISCRITTURA("grafico con riscrittura"),
        /** si modifica solo come testo */
        SOLO_TESTO("solo testo");

        final String etichetta;

        Esito(String etichetta) {
            this.etichetta = etichetta;
        }
    }

    /**
     * {@code atteso}: esito osservato nello spike, fissato per accorgersi delle regressioni.
     * {@code riscrittura}: forma equivalente, scritta a mano, in cui il modello di SQLeo rigenera la query
     * (serve alla CTE, che SQLeo rappresenta come tabella derivata con nome); {@code null} se non prevista.
     */
    record Caso(String costrutto, String sql, int sottoqueryAttese, int derivateAttese, int livelliAttesi, Esito atteso,
                String riscrittura) {
        Caso(String costrutto, String sql, int sottoqueryAttese, int derivateAttese, int livelliAttesi, Esito atteso) {
            this(costrutto, sql, sottoqueryAttese, derivateAttese, livelliAttesi, atteso, null);
        }

        @Override
        public String toString() {
            return costrutto;
        }
    }

    static final List<Caso> CASI = List.of(
            new Caso("WHERE ... IN (SELECT)",
                    "SELECT s.cognome, s.nome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.data_reso IS NULL)",
                    1, 0, 1, Esito.GRAFICO_OK),
            new Caso("WHERE ... NOT IN (SELECT)",
                    "SELECT l.titolo FROM libri l WHERE l.id NOT IN (SELECT p.id_libro FROM prestiti p)",
                    1, 0, 1, Esito.GRAFICO_OK),
            new Caso("WHERE EXISTS (SELECT correlata)",
                    "SELECT a.cognome FROM autori a WHERE EXISTS (SELECT la.id_libro FROM libri_autori la WHERE la.id_autore = a.id)",
                    1, 0, 1, Esito.GRAFICO_OK),
            new Caso("WHERE confronto con (SELECT MAX...)",
                    "SELECT l.titolo, l.prezzo FROM libri l WHERE l.prezzo = (SELECT MAX(l2.prezzo) FROM libri l2)",
                    1, 0, 1, Esito.GRAFICO_OK),
            new Caso("sottoquery nella lista SELECT",
                    "SELECT e.nome, (SELECT COUNT(l.id) FROM libri l WHERE l.id_editore = e.id) AS quanti_libri FROM editori e",
                    1, 0, 1, Esito.GRAFICO_OK),
            new Caso("tabella derivata in FROM",
                    "SELECT t.id_editore, t.prezzo_medio FROM (SELECT l.id_editore, AVG(l.prezzo) AS prezzo_medio FROM libri l"
                            + " GROUP BY l.id_editore) t WHERE t.prezzo_medio > 20",
                    0, 1, 1, Esito.GRAFICO_OK),
            new Caso("CTE WITH (riferita con alias)",
                    "WITH recenti AS (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 2020)"
                            + " SELECT r.titolo FROM recenti r ORDER BY r.titolo",
                    0, 1, 1, Esito.SOLO_TESTO),
            new Caso("CTE WITH (riferita senza alias)",
                    "WITH recenti AS (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 2020)"
                            + " SELECT recenti.titolo FROM recenti ORDER BY recenti.titolo",
                    0, 1, 1, Esito.GRAFICO_CON_RISCRITTURA,
                    "SELECT recenti.titolo FROM (SELECT l.id, l.titolo FROM libri l WHERE l.anno >= 2020) recenti"
                            + " ORDER BY recenti.titolo"),
            new Caso("due livelli di annidamento",
                    "SELECT s.cognome FROM soci s WHERE s.id IN (SELECT p.id_socio FROM prestiti p WHERE p.id_libro IN"
                            + " (SELECT l.id FROM libri l WHERE l.anno < 1950))",
                    2, 0, 2, Esito.GRAFICO_OK));

    static Stream<Caso> casi() {
        return CASI.stream();
    }

    /** Conteggio dei nodi di sottoquery realmente presenti nel modello. */
    static final class Struttura {
        int sottoquery;
        int derivate;
        int livelli;

        void visita(QueryExpression qe, int livello) {
            for (QueryExpression e = qe; e != null; e = e.getUnion()) {
                visita(e.getQuerySpecification(), livello);
            }
        }

        private void visita(QuerySpecification qs, int livello) {
            for (QueryTokens._Expression e : qs.getSelectList()) {
                nodo(e, livello);
            }
            for (QueryTokens._TableReference t : qs.getFromClause()) {
                nodo(t, livello);
            }
            for (QueryTokens.Condition c : qs.getWhereClause()) {
                nodo(c.getLeft(), livello);
                nodo(c.getRight(), livello);
            }
            for (QueryTokens.Condition c : qs.getHavingClause()) {
                nodo(c.getLeft(), livello);
                nodo(c.getRight(), livello);
            }
        }

        private void nodo(Object token, int livello) {
            if (token instanceof DerivedTable dt) {
                derivate++;
                livelli = Math.max(livelli, livello + 1);
                visita(dt, livello + 1);
            } else if (token instanceof SubQuery sq) {
                sottoquery++;
                livelli = Math.max(livelli, livello + 1);
                visita(sq, livello + 1);
            }
        }
    }

    record Valutazione(Esito esito, Struttura struttura, QbSql.Result risultato) {
    }

    static Valutazione valuta(Caso caso) {
        QbSql.Result r = QbSql.check(caso.sql());
        Struttura s = new Struttura();
        QueryModel model = r.model();
        if (model != null) {
            s.visita(model.getQueryExpression(), 0);
        }
        boolean nodiGiusti = model != null && s.sottoquery == caso.sottoqueryAttese()
                && s.derivate == caso.derivateAttese() && s.livelli == caso.livelliAttesi();
        Esito esito;
        if (nodiGiusti && r.representable()) {
            esito = Esito.GRAFICO_OK;
        } else if (nodiGiusti && r.warnings().isEmpty() && caso.riscrittura() != null
                && QbSql.normalize(caso.riscrittura()).equals(QbSql.normalize(r.regenerated()))) {
            esito = Esito.GRAFICO_CON_RISCRITTURA;
        } else {
            esito = Esito.SOLO_TESTO;
        }
        return new Valutazione(esito, s, r);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casi")
    void ogniCostruttoHaUnEsitoSenzaEccezioni(Caso caso) {
        Valutazione v = assertDoesNotThrow(() -> valuta(caso));

        assertNotNull(v.risultato().model(), "il parser non ha prodotto un modello: " + v.risultato().reason());
        assertEquals(caso.atteso(), v.esito(),
                "esito diverso da quello fissato nello spike. Nodi SubQuery=" + v.struttura().sottoquery
                        + " DerivedTable=" + v.struttura().derivate + " livelli=" + v.struttura().livelli
                        + "; rigenerato: " + v.risultato().regenerated()
                        + "; avvisi: " + v.risultato().warnings() + "; motivo: " + v.risultato().reason());
        if (v.esito() != Esito.SOLO_TESTO) {
            // i nodi di sottoquery ci sono davvero (non testo opaco) e il secondo giro è stabile
            assertEquals(caso.sottoqueryAttese(), v.struttura().sottoquery, "nodi SubQuery");
            assertEquals(caso.derivateAttese(), v.struttura().derivate, "nodi DerivedTable");
            assertEquals(caso.livelliAttesi(), v.struttura().livelli, "livelli di annidamento");
            QbSql.Result secondoGiro = QbSql.check(v.risultato().regenerated());
            assertTrue(secondoGiro.representable(), "secondo giro non stabile: " + secondoGiro.reason());
        }
    }

    @Test
    void tabellaDegliEsitiPerCostrutto() throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# S2d (parte U) - query nidificate: SQL -> modello -> SQL\n\n");
        md.append("Generato da `").append(getClass().getName()).append("`.\n\n");
        md.append("- **grafico ok**: il modello contiene i nodi `SubQuery`/`DerivedTable` attesi e l'SQL rigenerato equivale all'originale;\n");
        md.append("- **grafico con riscrittura**: nodi giusti e nessun pezzo di testo perso, ma l'SQL rigenerato ha un'altra forma;\n");
        md.append("- **solo testo**: da modificare nell'editor SQL.\n\n");
        md.append("| Costrutto | Esito | Nodi SubQuery | Nodi DerivedTable | Livelli | SQL originale | SQL rigenerato | Avvisi / motivo |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");

        int graficoOk = 0;
        for (Caso caso : CASI) {
            Valutazione v = valuta(caso);
            if (v.esito() == Esito.GRAFICO_OK) {
                graficoOk++;
            }
            QbSql.Result r = v.risultato();
            String note = String.join("; ", r.warnings())
                    + (r.reason() == null ? "" : (r.warnings().isEmpty() ? "" : "; ") + r.reason());
            md.append("| ").append(SpikeFiles.cell(caso.costrutto()))
                    .append(" | ").append(v.esito().etichetta)
                    .append(" | ").append(v.struttura().sottoquery)
                    .append(" | ").append(v.struttura().derivate)
                    .append(" | ").append(v.struttura().livelli)
                    .append(" | `").append(SpikeFiles.cell(caso.sql())).append('`')
                    .append(" | ").append(r.regenerated() == null ? "-" : "`" + SpikeFiles.cell(r.regenerated()) + "`")
                    .append(" | ").append(SpikeFiles.cell(note))
                    .append(" |\n");
        }
        md.append("\n**Totale: ").append(graficoOk).append(" «grafico ok» su ").append(CASI.size()).append(".**\n");
        SpikeFiles.write("S2d-esiti.md", md.toString());

        assertEquals(9, CASI.size(), "gli otto costrutti di S2d (la CTE in due varianti)");
        // sottoquery in WHERE, nella lista SELECT e tabella derivata sono il cuore di F-05bis: almeno questi devono reggere
        assertTrue(graficoOk >= 6, "solo " + graficoOk + " costrutti su 9 apribili nella vista grafica");
    }
}
