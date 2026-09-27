/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.sqleo.querybuilder.QueryModel;
import com.sqleo.querybuilder.syntax.QuerySpecification;
import com.sqleo.querybuilder.syntax.QueryTokens;

/**
 * T7.2, BUG-005: ordine dei join nella riscrittura del modello ({@code SQLFormatter.sort}, reso stabile il 2026-09-27).
 *
 * <p>Un ordine già valido (ogni join si aggancia a una tabella dichiarata prima) resta quello scritto, anche con join
 * misti INNER/LEFT; un ordine non valido, come quello che nasce nel diagramma quando si disegnano le relazioni «a
 * pezzi», viene ancora riordinato in modo che ogni join si agganci ai precedenti (comportamento originale da non
 * perdere).
 */
@Tag("step7")
class T72OrdineDeiJoinTest {

    @Test
    void joinMistiInOrdineValidoRestanoComeScritti() {
        String sql = "SELECT l.titolo, a.cognome, p.data_prestito, s.cognome FROM libri l"
                + " INNER JOIN libri_autori la ON l.id = la.id_libro LEFT JOIN prestiti p ON l.id = p.id_libro"
                + " INNER JOIN autori a ON la.id_autore = a.id LEFT JOIN soci s ON p.id_socio = s.id";
        QbSql.Result r = QbSql.check(sql);
        assertTrue(r.representable(), r.reason());
        String rigenerato = r.regenerated();
        int la = rigenerato.indexOf("`libri_autori`");
        int p = rigenerato.indexOf("`prestiti`");
        int a = rigenerato.indexOf("`autori` a");
        int s = rigenerato.indexOf("`soci`");
        assertTrue(la < p && p < a && a < s, "ordine dei join cambiato: " + rigenerato);
    }

    @Test
    void relazioniDisegnateInOrdineSparsoSiRiordinanoAgganciate() {
        QueryTokens.Table p = tabella("prestiti", "p");
        QueryTokens.Table s = tabella("soci", "s");
        QueryTokens.Table l = tabella("libri", "l");
        QueryTokens.Table e = tabella("editori", "e");
        QueryModel qm = new QueryModel();
        QuerySpecification qs = qm.getQueryExpression().getQuerySpecification();
        qs.addSelectList(new QueryTokens.Column(p, "id"));
        // relazioni aggiunte nell'ordine: p-s, poi l-e (non si aggancia ancora a nulla), poi p-l
        qs.addFromClause(new QueryTokens.Join(QueryTokens.Join.INNER, col(p, "id_socio"), "=", col(s, "id")));
        qs.addFromClause(new QueryTokens.Join(QueryTokens.Join.INNER, col(l, "id_editore"), "=", col(e, "id")));
        qs.addFromClause(new QueryTokens.Join(QueryTokens.Join.LEFT_OUTER, col(p, "id_libro"), "=", col(l, "id")));

        String sql = qm.toString(false);

        assertTrue(!sql.substring(sql.indexOf(" FROM ")).contains(","), "FROM spezzato da una virgola: " + sql);
        assertTrue(sql.indexOf("`libri`") < sql.indexOf("`editori`"), "editori prima di libri: " + sql);
        assertEquals("SELECT p.`id` FROM `prestiti` p INNER JOIN `soci` s  ON p.`id_socio` = s.`id`"
                + " LEFT OUTER JOIN `libri` l  ON p.`id_libro` = l.`id` INNER JOIN `editori` e  ON l.`id_editore` = e.`id`",
                sql);
        QbSql.Result riletto = QbSql.check(sql);
        assertTrue(riletto.representable(), "la riscrittura non si rilegge: " + riletto.reason());
    }

    @Test
    void secondaCondizioneFraLeStesseTabelleSegueIlSuoJoin() {
        QueryTokens.Table l = tabella("libri", "l");
        QueryTokens.Table e = tabella("editori", "e");
        QueryTokens.Table p = tabella("prestiti", "p");
        QueryModel qm = new QueryModel();
        QuerySpecification qs = qm.getQueryExpression().getQuerySpecification();
        qs.addSelectList(new QueryTokens.Column(l, "titolo"));
        qs.addFromClause(new QueryTokens.Join(QueryTokens.Join.INNER, col(l, "id_editore"), "=", col(e, "id")));
        qs.addFromClause(new QueryTokens.Join(QueryTokens.Join.LEFT_OUTER, col(l, "id"), "=", col(p, "id_libro")));
        // seconda relazione fra libri ed editori disegnata dopo: deve finire nella ON di editori, non in quella di prestiti
        qs.addFromClause(new QueryTokens.Join(QueryTokens.Join.INNER, col(l, "anno"), "=", col(e, "id")));

        String sql = qm.toString(false);

        int onEditori = sql.indexOf("ON l.`id_editore`");
        int secondaCondizione = sql.indexOf("l.`anno` = e.`id`");
        int prestiti = sql.indexOf("`prestiti`");
        assertTrue(onEditori < secondaCondizione && secondaCondizione < prestiti,
                "la seconda condizione non è nella ON di editori: " + sql);
    }

    private static QueryTokens.Table tabella(String nome, String alias) {
        QueryTokens.Table t = new QueryTokens.Table(null, nome);
        t.setAlias(alias);
        return t;
    }

    private static QueryTokens.Column col(QueryTokens.Table t, String nome) {
        return new QueryTokens.Column(t, nome);
    }
}
