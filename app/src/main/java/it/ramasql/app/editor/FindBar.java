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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import it.ramasql.app.Texts;

/**
 * Trova e sostituisci «semplice» (Ctrl+F): una riga sopra l'editor con Trova, Sostituisci con, Successivo,
 * Sostituisci, Sostituisci tutti, Chiudi. Senza distinguere maiuscole e minuscole, ricomincia dall'inizio a fine testo;
 * niente espressioni regolari né opzioni (DESIGN §2: le opzioni avanzate non esistono).
 */
final class FindBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final RTextArea area;
    private final JTextField findField = new JTextField(18);
    private final JTextField replaceField = new JTextField(14);
    private final JLabel message = new JLabel(" ");

    FindBar(RTextArea area) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        this.area = area;
        setName("sqlEditor.findBar");
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        findField.setName("sqlEditor.find.text");
        replaceField.setName("sqlEditor.find.replace");
        JButton next = new JButton(Texts.get("editor.find.next"));
        JButton replaceOne = new JButton(Texts.get("editor.find.replaceOne"));
        JButton replaceAll = new JButton(Texts.get("editor.find.replaceAll"));
        JButton close = new JButton(Texts.get("editor.find.close"));
        next.addActionListener(e -> findNext());
        replaceOne.addActionListener(e -> replaceNext());
        replaceAll.addActionListener(e -> replaceAll());
        close.addActionListener(e -> close());
        findField.addActionListener(e -> findNext());
        replaceField.addActionListener(e -> replaceNext());
        add(new JLabel(Texts.get("editor.find.label")));
        add(findField);
        add(next);
        add(new JLabel(Texts.get("editor.find.replace")));
        add(replaceField);
        add(replaceOne);
        add(replaceAll);
        add(close);
        add(message);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeFind");
        getActionMap().put("closeFind", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        setVisible(false);
    }

    /** Mostra la barra con il testo selezionato (se è su una riga sola) e porta il cursore nel campo Trova. */
    void open() {
        String selected = area.getSelectedText();
        if (selected != null && !selected.isEmpty() && selected.indexOf('\n') < 0) {
            findField.setText(selected);
        }
        message.setText(" ");
        setVisible(true);
        revalidate();
        findField.selectAll();
        findField.requestFocusInWindow();
    }

    void close() {
        setVisible(false);
        area.requestFocusInWindow();
    }

    void setFindText(String text) {
        findField.setText(text);
    }

    void setReplaceText(String text) {
        replaceField.setText(text);
    }

    /** Seleziona la prossima occorrenza dopo il cursore; {@code false} se non ce ne sono. */
    boolean findNext() {
        SearchResult r = SearchEngine.find(area, context());
        message.setText(r.wasFound() ? " " : Texts.get("editor.find.notFound"));
        return r.wasFound();
    }

    /** Sostituisce l'occorrenza selezionata (o la prossima) e passa alla successiva. */
    boolean replaceNext() {
        SearchResult r = SearchEngine.replace(area, context());
        message.setText(r.wasFound() ? " " : Texts.get("editor.find.notFound"));
        return r.wasFound();
    }

    /** Sostituisce tutte le occorrenze; restituisce quante. */
    int replaceAll() {
        SearchResult r = SearchEngine.replaceAll(area, context());
        int count = r.getCount();
        message.setText(count == 0 ? Texts.get("editor.find.notFound") : Texts.get("editor.find.replaced", count));
        return count;
    }

    String messageText() {
        return message.getText();
    }

    private SearchContext context() {
        SearchContext c = new SearchContext(findField.getText(), false);
        c.setReplaceWith(replaceField.getText());
        c.setSearchForward(true);
        c.setSearchWrap(true);
        c.setWholeWord(false);
        c.setRegularExpression(false);
        c.setMarkAll(false);
        return c;
    }
}
