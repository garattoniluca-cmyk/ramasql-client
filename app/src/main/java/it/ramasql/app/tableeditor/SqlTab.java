/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.tableeditor;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;

import it.ramasql.app.Texts;
import it.ramasql.app.theme.Tokens;

/**
 * Scheda <b>SQL</b>: l'anteprima <b>viva</b> del {@code CREATE}/{@code ALTER} che {@code TableDiff} ricava dal modello,
 * aggiornata a ogni modifica; è esattamente ciò che «Applica» passerà all'esecuzione. Sopra, il contatore delle
 * istruzioni (DESIGN-SYSTEM §3.7); senza modifiche, «Nessuna modifica».
 */
final class SqlTab extends JPanel {

    private static final long serialVersionUID = 1L;

    private final RSyntaxTextArea area = new RSyntaxTextArea();
    private final JLabel counter = new JLabel();
    private String sql = "";

    SqlTab() {
        super(new BorderLayout(0, Tokens.SPACE_8));
        setName("tableeditor.sql");
        setBackground(Tokens.BG_SURFACE);
        setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_16, Tokens.SPACE_16,
                Tokens.SPACE_16, Tokens.SPACE_16));
        area.setName("sql.preview");
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        area.setEditable(false);
        area.setHighlightCurrentLine(false);
        area.setCodeFoldingEnabled(false);
        area.setAntiAliasingEnabled(true);
        area.setBackground(Tokens.BG_SUNKEN);
        area.setSelectionColor(Tokens.SQL_SELECTION);
        area.setFont(Tokens.mono(Tokens.BODY));
        area.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_12, Tokens.SPACE_12,
                Tokens.SPACE_12, Tokens.SPACE_12));
        applyScheme(area);
        RTextScrollPane scroll = new RTextScrollPane(area, false);
        scroll.setBorder(BorderFactory.createLineBorder(Tokens.BORDER_SUBTLE));
        counter.setName("sql.counter");
        counter.setForeground(Tokens.TEXT_SECONDARY);
        add(counter, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** Colori della sintassi SQL di DESIGN-SYSTEM §1.1. */
    private static void applyScheme(RSyntaxTextArea area) {
        SyntaxScheme scheme = area.getSyntaxScheme();
        Font base = area.getFont();
        style(scheme, Token.RESERVED_WORD, Tokens.SQL_KEYWORD, base.deriveFont(Font.BOLD));
        style(scheme, Token.RESERVED_WORD_2, Tokens.SQL_KEYWORD, base.deriveFont(Font.BOLD));
        style(scheme, Token.DATA_TYPE, Tokens.SQL_KEYWORD, base.deriveFont(Font.BOLD));
        style(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, Tokens.SQL_STRING, base);
        style(scheme, Token.LITERAL_CHAR, Tokens.SQL_STRING, base);
        style(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, Tokens.SQL_NUMBER, base);
        style(scheme, Token.LITERAL_NUMBER_FLOAT, Tokens.SQL_NUMBER, base);
        style(scheme, Token.COMMENT_EOL, Tokens.SQL_COMMENT, base.deriveFont(Font.ITALIC));
        style(scheme, Token.COMMENT_MULTILINE, Tokens.SQL_COMMENT, base.deriveFont(Font.ITALIC));
        style(scheme, Token.FUNCTION, Tokens.SQL_FUNCTION, base);
        style(scheme, Token.IDENTIFIER, Tokens.SQL_IDENTIFIER, base);
        style(scheme, Token.OPERATOR, Tokens.SQL_OPERATOR, base);
        style(scheme, Token.SEPARATOR, Tokens.SQL_OPERATOR, base);
    }

    private static void style(SyntaxScheme scheme, int token, java.awt.Color color, Font font) {
        Style s = scheme.getStyle(token);
        if (s != null) {
            s.foreground = color;
            s.font = font;
        }
    }

    /** Il testo dell'anteprima: le istruzioni separate da una riga vuota, ciascuna chiusa da {@code ;}. */
    static String text(List<String> statements) {
        return statements.isEmpty() ? "" : String.join(";\n\n", statements) + ";";
    }

    /** @param complete {@code false} = il modello ha campi incompleti e l'SQL non si può ancora scrivere */
    void show(List<String> statements, boolean complete) {
        sql = text(statements);
        String shown = !complete ? Texts.get("tableeditor.sql.incomplete")
                : statements.isEmpty() ? Texts.get("tableeditor.sql.none") : sql;
        if (!shown.equals(area.getText())) {
            area.setText(shown);
            area.setCaretPosition(0);
        }
        counter.setText(statements.isEmpty() ? Texts.get("tableeditor.sql.counter.none")
                : statements.size() == 1 ? Texts.get("tableeditor.sql.counter.one")
                : Texts.get("tableeditor.sql.counter", statements.size()));
    }

    /** L'SQL dell'anteprima ({@code ""} se non ci sono modifiche). */
    String sql() {
        return sql;
    }

    String shownText() {
        return area.getText();
    }

    String counterText() {
        return counter.getText();
    }
}
