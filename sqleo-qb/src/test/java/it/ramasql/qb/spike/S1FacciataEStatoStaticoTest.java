/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb.spike;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.qb.BasicQbHost;
import it.ramasql.qb.QbHost;
import it.ramasql.qb.QbIcon;
import it.ramasql.qb.QbRuntime;
import it.ramasql.qb.QbSql;

/** Spike S1: la facciata {@link QbHost} e lo stato {@code static} residuo del parser ereditato (rischio R-06). */
@Tag("step1")
class S1FacciataEStatoStaticoTest {

    @Test
    void hostDiBaseFornisceIconeETestiItaliani() {
        BasicQbHost host = new BasicQbHost();
        for (QbIcon id : QbIcon.values()) {
            assertNotNull(host.icon(id), "icona " + id);
            assertTrue(host.icon(id).getIconWidth() > 0, "icona vuota: " + id);
        }
        assertEquals("Grafico", host.text("querybuilder.designer", "designer"));
        assertEquals("testo originale", host.text("chiave.che.non.esiste", "testo originale"));
        assertTrue(host.joinHints("libri").isEmpty());
    }

    @Test
    void leCteDiUnaQueryNonContaminanoLaSuccessiva() {
        QbSql.check("WITH libri AS (SELECT e.id, e.nome FROM editori e) SELECT libri.nome FROM libri");

        String dopo = "SELECT titolo FROM libri";
        QbSql.Result r = QbSql.check(dopo);

        assertTrue(r.representable(), "la tabella «libri» è stata scambiata per la CTE della query precedente: " + r.regenerated());
        assertFalse(r.regenerated().toLowerCase().contains("editori"), r.regenerated());
    }

    @Test
    void checkRimetteAPostoLHostERaccoglieGliAvvisi() {
        QbHost prima = QbRuntime.host();

        QbSql.Result r = QbSql.check("SELECT a FROM t1 UNION ALL SELECT b FROM t2");

        assertSame(prima, QbRuntime.host(), "host non ripristinato");
        assertFalse(r.warnings().isEmpty(), "l'avviso su UNION ALL doveva essere raccolto");
        assertFalse(r.representable(), "UNION ALL diventa UNION: la query non deve risultare rappresentabile");
    }

    /** Testi che NON sono SELECT valide: il giro non deve lanciare eccezioni e l'esito deve essere «non rappresentabile». */
    static final String[] ILLEGGIBILI = {"", ";", "SELECT", "SELECT FROM", "FROM WHERE", "SELECT ((( FROM x",
            "DELETE FROM libri", "SELECT * FROM libri WHERE", "SELECT a FROM t JOIN u ON", "SELECT DELETE FROM libri",
            "SELECT select FROM libri", "SELECT a FROM from", "SELECT a FROM libri WHERE where = 1",
            "SELECT a b c FROM libri", "SELECT a FROM libri l JOIN", "SELECT titolo FROM libri GROUP",
            "SELECT titolo FROM libri ORDER BY", "SELECT titolo FROM libri WHERE titolo = 'aperto",
            "SELECT titolo FROM libri)", "UPDATE libri SET prezzo = 0", "DROP TABLE libri"};

    @Test
    void sqlIllegibileNonLanciaEccezioni() {
        for (String sql : ILLEGGIBILI) {
            QbSql.Result r = assertDoesNotThrow(() -> QbSql.check(sql), sql);
            assertNotNull(r, sql);
            assertFalse(r.representable(), "testo non valido dichiarato rappresentabile: «" + sql + "» -> " + r.regenerated());
            assertNotNull(r.reason(), "manca il motivo per: «" + sql + "»");
            assertFalse(QbSql.isRepresentable(sql), sql);
        }
    }

    /** I controlli di forma aggiunti a {@code check} non devono respingere SQL valido (nomi qualificati, funzioni, FOR UPDATE…). */
    @Test
    void iControlliDiFormaNonRespingonoSqlValido() {
        for (String sql : new String[] {
                "SELECT `b`.`l`.`titolo` FROM `b`.`libri` `l`",
                "SELECT biblioteca.libri.titolo FROM biblioteca.libri",
                "SELECT REPLACE(titolo, 'a', 'b'), INSERT(titolo, 1, 2, 'xx') FROM libri",
                "SELECT l.titolo AS t FROM libri AS l WHERE l.titolo NOT LIKE 'DELETE %' AND l.anno IS NOT NULL",
                "SELECT titolo FROM libri WHERE note = 'l''update (non chiusa'",
                "SELECT CASE WHEN prezzo > 10 THEN 'caro' ELSE 'ok' END AS fascia FROM libri ORDER BY prezzo DESC LIMIT 5",
                "SELECT titolo FROM libri;"}) {
            QbSql.Result r = QbSql.check(sql);
            assertTrue(r.reason() == null || !r.reason().startsWith("il testo non è una SELECT valida"), sql + " -> " + r.reason());
        }
    }
}
