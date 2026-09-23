/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.pipeline;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.text.Segment;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMaker;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import it.ramasql.app.theme.Tokens;

/**
 * L'SQL mostrato in <b>sola lettura</b> (anteprima, scheda Anteprima, «Mostra SQL di creazione», righe del Registro),
 * colorato con la sintassi di {@code DESIGN-SYSTEM.md} §1.1: parole chiave in grassetto d'accento, stringhe, numeri,
 * commenti in corsivo, funzioni. Stesso analizzatore dell'editor SQL (RSyntaxTextArea).
 */
public final class SqlText {

    private SqlText() {
    }

    /** Un riquadro di sola lettura su fondo {@code bg.sunken}, con il testo dato e il cursore all'inizio. */
    public static RSyntaxTextArea readOnly(String text, int rows, int columns) {
        RSyntaxTextArea area = new RSyntaxTextArea(rows, columns);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        area.setEditable(false);
        area.setHighlightCurrentLine(false);
        area.setCodeFoldingEnabled(false);
        area.setAntiAliasingEnabled(true);
        area.setBracketMatchingEnabled(false);
        area.setFont(Tokens.mono(Tokens.BODY));
        area.setBackground(Tokens.BG_SUNKEN);
        area.setForeground(Tokens.SQL_IDENTIFIER);
        area.setSelectionColor(Tokens.SQL_SELECTION);
        area.setCaretColor(Tokens.BG_SUNKEN);   // sola lettura: il cursore non si vede
        area.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_12),
                Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_12)));
        applyScheme(area);
        set(area, text);
        return area;
    }

    /** I colori della sintassi SQL del sistema visivo. */
    public static void applyScheme(RSyntaxTextArea area) {
        Font plain = area.getFont();
        Font bold = plain.deriveFont(Font.BOLD);
        Font italic = plain.deriveFont(Font.ITALIC);
        SyntaxScheme scheme = area.getSyntaxScheme();
        for (int type = 0; type < TokenTypes.DEFAULT_NUM_TOKEN_TYPES; type++) {
            Style s = scheme.getStyle(type);
            if (s != null) {
                s.foreground = colorFor(type);
                s.font = fontFor(type, plain, bold, italic);
                s.background = null;
                s.underline = false;
            }
        }
        area.setSyntaxScheme(scheme);
    }

    /** Sostituisce il testo e torna all'inizio. */
    public static void set(RSyntaxTextArea area, String text) {
        area.setText(text == null ? "" : text);
        area.setCaretPosition(0);
    }

    /** Una riga di SQL colorata in HTML (per le celle del Registro); il carattere è quello del componente. */
    public static String toHtml(String line) {
        TokenMaker maker = TokenMakerFactory.getDefaultInstance().getTokenMaker(SyntaxConstants.SYNTAX_STYLE_SQL);
        char[] chars = line.toCharArray();
        Token t = maker.getTokenList(new Segment(chars, 0, chars.length), TokenTypes.NULL, 0);
        StringBuilder sb = new StringBuilder("<html><nobr>");
        while (t != null && t.isPaintable()) {
            String lexeme = t.getLexeme();
            int type = t.getType();
            String escaped = lexeme.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace(" ", "&nbsp;");
            if (type == TokenTypes.WHITESPACE) {
                sb.append(escaped);
            } else {
                boolean bold = isKeyword(type);
                sb.append("<font color='").append(Tokens.hex(colorFor(type))).append("'>");
                sb.append(bold ? "<b>" : "").append(escaped).append(bold ? "</b>" : "");
                sb.append("</font>");
            }
            t = t.getNextToken();
        }
        return sb.append("</nobr></html>").toString();
    }

    private static boolean isKeyword(int type) {
        return type == TokenTypes.RESERVED_WORD || type == TokenTypes.RESERVED_WORD_2;
    }

    private static Color colorFor(int type) {
        return switch (type) {
            case TokenTypes.RESERVED_WORD, TokenTypes.RESERVED_WORD_2, TokenTypes.DATA_TYPE,
                    TokenTypes.LITERAL_BOOLEAN -> Tokens.SQL_KEYWORD;
            case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, TokenTypes.LITERAL_CHAR -> Tokens.SQL_STRING;
            case TokenTypes.LITERAL_NUMBER_DECIMAL_INT, TokenTypes.LITERAL_NUMBER_FLOAT,
                    TokenTypes.LITERAL_NUMBER_HEXADECIMAL -> Tokens.SQL_NUMBER;
            case TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE, TokenTypes.COMMENT_DOCUMENTATION,
                    TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP -> Tokens.SQL_COMMENT;
            case TokenTypes.FUNCTION -> Tokens.SQL_FUNCTION;
            case TokenTypes.OPERATOR, TokenTypes.SEPARATOR -> Tokens.SQL_OPERATOR;
            default -> Tokens.SQL_IDENTIFIER;
        };
    }

    private static Font fontFor(int type, Font plain, Font bold, Font italic) {
        if (isKeyword(type)) {
            return bold;
        }
        return switch (type) {
            case TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE, TokenTypes.COMMENT_DOCUMENTATION -> italic;
            default -> plain;
        };
    }
}
