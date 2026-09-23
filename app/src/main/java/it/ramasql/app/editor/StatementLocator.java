/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.editor;

import java.util.ArrayList;
import java.util.List;

import it.ramasql.core.exec.SqlStatement;
import it.ramasql.core.exec.StatementSplitter;
import it.ramasql.core.exec.StatementSplitter.SplitStatement;

/**
 * Quali istruzioni esegue l'editor: quella al cursore, quelle della selezione, tutte. Logica pura sul testo del
 * documento (nessun componente Swing), separazione affidata a {@link StatementSplitter} (quindi {@code DELIMITER},
 * commenti e stringhe sono rispettati).
 *
 * <h2>Istruzione al cursore</h2>
 * È quella che contiene il cursore (estremi compresi). Se il cursore è fuori da ogni istruzione: se sulla sua riga,
 * prima di lui, finisce un'istruzione (cursore subito dopo il {@code ;}), è quella; altrimenti la successiva; in fondo
 * allo script, l'ultima.
 *
 * <h2>Selezione</h2>
 * Se la selezione cade tutta dentro un'unica istruzione dello script, si esegue il testo selezionato così com'è, senza
 * separarlo (così un pezzo del corpo di una procedura scritta con {@code DELIMITER //} non viene spezzato ai {@code ;}).
 * Altrimenti il testo selezionato si separa per conto suo.
 */
final class StatementLocator {

    private StatementLocator() {
    }

    /** Tutte le istruzioni del documento. */
    static List<PositionedStatement> all(String text, String origin) {
        return positioned(text, StatementSplitter.split(text), 0, origin);
    }

    /** L'istruzione al cursore, {@code null} se lo script non ne contiene. */
    static PositionedStatement atCaret(String text, int caret, String origin) {
        List<SplitStatement> parts = StatementSplitter.split(text);
        SplitStatement chosen = chooseAtCaret(text, parts, caret);
        return chosen == null ? null : positioned(text, List.of(chosen), 0, origin).getFirst();
    }

    /** Le istruzioni della selezione {@code [start, end)}. */
    static List<PositionedStatement> inSelection(String text, int start, int end, String origin) {
        int from = Math.max(0, Math.min(start, end));
        int to = Math.min(text.length(), Math.max(start, end));
        String selected = text.substring(from, to);
        for (SplitStatement s : StatementSplitter.split(text)) {
            if (s.startOffset() <= from && to <= s.endOffset()) {
                String trimmed = selected.strip();
                if (trimmed.isEmpty()) {
                    return List.of();
                }
                int lead = selected.indexOf(trimmed);
                int begin = from + lead;
                SplitStatement one = new SplitStatement(trimmed, begin, begin + trimmed.length(), 1);
                return positioned(text, List.of(one), 0, origin);
            }
        }
        return positioned(text, StatementSplitter.split(selected), from, origin);
    }

    private static SplitStatement chooseAtCaret(String text, List<SplitStatement> parts, int caret) {
        if (parts.isEmpty()) {
            return null;
        }
        SplitStatement previous = null;
        for (SplitStatement s : parts) {
            if (s.startOffset() <= caret && caret <= s.endOffset()) {
                return s;
            }
            if (s.endOffset() < caret) {
                previous = s;
            } else {
                // s comincia dopo il cursore
                if (previous != null && !containsLineBreak(text, previous.endOffset(), caret)) {
                    return previous;
                }
                return s;
            }
        }
        return previous;
    }

    private static boolean containsLineBreak(String text, int from, int to) {
        for (int i = from; i < to && i < text.length(); i++) {
            if (text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                return true;
            }
        }
        return false;
    }

    private static List<PositionedStatement> positioned(String text, List<SplitStatement> parts, int shift,
            String origin) {
        List<PositionedStatement> out = new ArrayList<>(parts.size());
        for (SplitStatement s : parts) {
            int start = s.startOffset() + shift;
            int end = s.endOffset() + shift;
            out.add(new PositionedStatement(SqlStatement.of(s.text(), origin), start, end, lineOf(text, start)));
        }
        return out;
    }

    /** Riga (da 1) della posizione {@code offset}. */
    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
