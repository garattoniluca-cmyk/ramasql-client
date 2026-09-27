/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqleo.querybuilder.QbOperations;

import it.ramasql.app.editor.PositionedStatement;
import it.ramasql.app.editor.SqlRunner;
import it.ramasql.app.visual.VisualQueryTab;

/**
 * Step 7, vista SQL della query visiva: <b>il testo scritto a mano non si perde mai</b> (difetto B3 della revisione:
 * {@code SELECT NOW()} passando alla Grafica diventava un testo vuoto). Per ogni testo che il diagramma non sa
 * disegnare — nessuna tabella, più istruzioni, commenti, funzione finestra, testo rotto — la scheda resta sulla vista
 * SQL, il testo è identico e c'è l'avviso con il motivo in parole semplici. Senza server (la scheda non esegue nulla).
 * In più: la maschera della condizione offre la sottoquery anche con gli operatori di confronto (T7.7b).
 */
@Tag("step7")
@Tag("ui")
class T76TestoMaiPersoTest {

    @BeforeAll
    static void setup() {
        Probe.setup();
    }

    private static VisualQueryTab scheda() {
        FakeWorkspacePrompts p = new FakeWorkspacePrompts();
        SqlRunner nessuno = new SqlRunner() {
            @Override
            public void run(List<PositionedStatement> statements, String origin, Listener listener) {
                listener.done(true);
            }

            @Override
            public void cancel() {
            }
        };
        return fromEdt(() -> new VisualQueryTab("Query visiva di prova", "biblioteca", null, nessuno, null,
                p.editorPrompts(), p.gridPrompts(), 100, m -> { }, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT NOW()",
        "SELECT 1 + 1;",
        "SELECT DATABASE()",
        "SELECT titolo FROM libri; SELECT nome FROM editori",
        "-- i libri recenti\nSELECT titolo FROM libri WHERE anno > 2000",
        "SELECT titolo /* nome */ FROM libri",
        "SELECT titolo, ROW_NUMBER() OVER (ORDER BY prezzo) AS n FROM libri",
        "WITH r AS (SELECT titolo FROM libri) SELECT * FROM r",
        "SELECT titolo FROM libri UNION ALL SELECT nome FROM editori",
        "SELECT titolo FROM libri WHERE (anno > 2000",
        "SELECT FROM libri"})
    void ilTestoRestaIntatto(String sql) {
        VisualQueryTab tab = scheda();
        boolean disegnata = fromEdt(() -> tab.setSql(sql));
        assertFalse(disegnata, "non si disegna: " + sql);
        assertFalse(fromEdt(tab::isGraphicShown), "resta la vista SQL");
        assertEquals(sql, fromEdt(() -> tab.editor().getText()), "testo identico");
        String avviso = fromEdt(tab::noticeText);
        assertTrue(avviso.startsWith("Questa query non si può mostrare nel diagramma: "), avviso);
        assertFalse(avviso.contains("java.") || avviso.contains("«"), "motivo in parole semplici: " + avviso);
        // tornare alla Grafica di nuovo non cambia nulla
        assertFalse(fromEdt(tab::showGraphic));
        assertEquals(sql, fromEdt(() -> tab.editor().getText()));
    }

    @Test
    void motiviInParoleSemplici() {
        VisualQueryTab tab = scheda();
        onEdt(() -> tab.setSql("SELECT NOW()"));
        assertTrue(fromEdt(tab::noticeText).contains("non usa nessuna tabella"), fromEdt(tab::noticeText));
        onEdt(() -> tab.setSql("SELECT titolo, ROW_NUMBER() OVER (ORDER BY prezzo) AS n FROM libri"));
        assertTrue(fromEdt(tab::noticeText).contains("funzione finestra (OVER)"), fromEdt(tab::noticeText));
        onEdt(() -> tab.setSql("SELECT titolo FROM libri UNION ALL SELECT nome FROM editori"));
        assertTrue(fromEdt(tab::noticeText).contains("UNION"), fromEdt(tab::noticeText));
        onEdt(() -> tab.setSql("-- nota\nSELECT titolo FROM libri"));
        assertTrue(fromEdt(tab::noticeText).contains("commenti"), fromEdt(tab::noticeText));
    }

    @Test
    void laMascheraDellaCondizioneAmmetteLaSottoqueryConIConfronti() {
        VisualQueryTab tab = scheda();
        for (String op : List.of("=", "<", ">", "<=", ">=", "<>", "IN", "NOT IN", "EXISTS", "NOT EXISTS")) {
            assertTrue(fromEdt(() -> QbOperations.conditionMaskAllowsSubquery(tab.queryBuilder(), op)), op);
        }
        for (String op : List.of("LIKE", "IS", "BETWEEN")) {
            assertFalse(fromEdt(() -> QbOperations.conditionMaskAllowsSubquery(tab.queryBuilder(), op)), op);
        }
    }
}
