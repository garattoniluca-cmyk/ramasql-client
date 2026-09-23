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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import it.ramasql.app.Texts;
import it.ramasql.app.navigator.NavigatorIcons;
import it.ramasql.app.theme.Pill;
import it.ramasql.app.theme.Styles;
import it.ramasql.app.theme.Tokens;
import it.ramasql.core.exec.ConfirmationPolicy;
import it.ramasql.core.exec.RiskLevel;
import it.ramasql.core.exec.SqlScript;

/**
 * Finestra «SQL che verrà eseguito» (DESIGN §2, ARCHITECTURE §4, DESIGN-SYSTEM §3.7): l'SQL <b>esatto</b> dello
 * {@link SqlScript}, in sola lettura e colorato, con il numero di istruzioni e la pillola del rischio; tre pulsanti:
 * <em>Esegui</em> (primario, a destra), <em>Annulla</em>, <em>Copia nell'editor</em> (discreto, a sinistra). Non esiste
 * un modo per saltarla.
 * <p>Con la <b>conferma rafforzata</b> ({@link ConfirmationPolicy.Level#STRONG}: {@code DROP}, {@code TRUNCATE}…)
 * compare una fascia rossa con la frase «Stai per …» e il campo «Scrivi <i>nome</i> per confermare»: il pulsante
 * (rosso, «Elimina»/«Svuota») resta disabilitato, e non è il predefinito, finché il testo non coincide. Chiudere la
 * finestra in qualunque altro modo equivale ad Annulla.
 * <p>«Copia nell'editor»: per ora copia l'SQL negli appunti (lo fa chi ha aperto la finestra); il collegamento
 * diretto all'editor SQL arriverà con l'editor.
 */
public final class PreviewDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Scelta dell'utente. */
    public enum Decision { EXECUTE, COPY, CANCEL }

    private final transient ConfirmationPolicy.Confirmation confirmation;
    private final transient SqlScript script;
    private final RSyntaxTextArea sql;
    private final JButton execute = new JButton();
    private final JButton copy = new JButton(Texts.get("preview.copy"));
    private final JButton cancel = new JButton(Texts.get("dialog.cancel"));
    private final JTextField typed = new JTextField(22);
    private Decision decision = Decision.CANCEL;

    public PreviewDialog(Window owner, SqlScript script, ConfirmationPolicy.Confirmation confirmation) {
        super(owner, Texts.get("preview.title"), ModalityType.APPLICATION_MODAL);
        this.confirmation = confirmation;
        this.script = script;
        setName("preview.dialog");
        int pad = Tokens.px(Tokens.SPACE_24);

        // ---- intestazione: operazione, poi «SQL che verrà eseguito · N istruzioni» e la pillola del rischio
        JLabel title = new JLabel(script.title().isEmpty() ? Texts.get("preview.title") : script.title());
        title.setName("preview.operation");
        title.setFont(Tokens.font(Tokens.HEADING, Font.BOLD));
        title.setForeground(Tokens.TEXT_PRIMARY);
        JLabel subtitle = new JLabel(Texts.get(script.size() == 1 ? "preview.subtitle.one" : "preview.subtitle.many",
                script.size(), script.origin()));
        subtitle.setForeground(Tokens.TEXT_SECONDARY);
        Pill risk = riskPill(script.risk());
        JPanel subtitleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        subtitleRow.setOpaque(false);
        subtitleRow.add(subtitle);
        subtitleRow.add(Box.createHorizontalStrut(Tokens.px(Tokens.SPACE_8)));
        subtitleRow.add(risk);
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBorder(BorderFactory.createEmptyBorder(pad, pad, Tokens.px(Tokens.SPACE_16), pad));
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitleRow.setAlignmentX(LEFT_ALIGNMENT);
        head.add(title);
        head.add(Box.createVerticalStrut(Tokens.px(Tokens.SPACE_4)));
        head.add(subtitleRow);

        // ---- il codice
        // alto quanto serve (da 3 a 14 righe): una sola istruzione non lascia un riquadro vuoto
        // e le righe lunghe vanno a capo: niente barra orizzontale da inseguire
        int lines = 1;
        for (String line : script.text().split("\n", -1)) {
            lines += Math.max(1, (line.length() + 63) / 64);
        }
        lines = Math.max(3, Math.min(14, lines));
        sql = SqlText.readOnly(script.text(), lines, 64);
        sql.setLineWrap(true);
        sql.setName("preview.sql");
        JScrollPane scroll = new JScrollPane(sql);
        scroll.setBorder(BorderFactory.createLineBorder(Tokens.BORDER_SUBTLE));
        if (lines < 14) {   // il riquadro è già alto quanto il testo: niente barra finta
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        }
        java.awt.Insets in = sql.getInsets();
        scroll.setPreferredSize(new Dimension(Tokens.px(620),
                lines * sql.getLineHeight() + in.top + in.bottom + Tokens.px(4)));
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, pad, 0, pad));
        center.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(isStrong() ? strongPanel() : notePanel(), BorderLayout.CENTER);
        south.add(buttons(), BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Tokens.BG_SURFACE);
        content.add(head, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().registerKeyboardAction(e -> choose(Decision.CANCEL),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // Invio non deve mai confermare un'operazione distruttiva
        getRootPane().setDefaultButton(isStrong() ? null : execute);
        updateExecute();
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private static Pill riskPill(RiskLevel risk) {
        Pill pill = switch (risk) {
            case SAFE -> new Pill(Texts.get("preview.risk.safe"), Tokens.TEXT_SECONDARY, Tokens.BG_SUNKEN);
            case MODIFIES -> new Pill(Texts.get("preview.risk.modifies"), Tokens.WARNING, Tokens.WARNING_TINT);
            case DESTRUCTIVE -> new Pill(Texts.get("preview.risk.destructive"), Tokens.DANGER, Tokens.DANGER_TINT);
        };
        pill.setName("preview.risk");
        return pill;
    }

    /** Conferma normale: una riga di spiegazione sotto il codice. */
    private JComponent notePanel() {
        JLabel note = new JLabel(confirmation.message().isEmpty() ? " " : confirmation.message());
        note.setName("preview.message");
        note.setForeground(Tokens.TEXT_SECONDARY);
        int pad = Tokens.px(Tokens.SPACE_24);
        note.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), pad, 0, pad));
        return note;
    }

    /** Conferma rafforzata: fascia rossa con la frase chiara e il campo in cui riscrivere il nome. */
    private JComponent strongPanel() {
        String name = confirmation.typeToConfirm();
        JLabel sentence = new JLabel("<html>" + Texts.get("preview.strong.sentence", html(action()),
                html(name)) + "</html>", NavigatorIcons.WARNING, JLabel.LEADING);
        sentence.setName("preview.message");
        sentence.setIconTextGap(Tokens.px(Tokens.SPACE_8));
        sentence.setForeground(Tokens.TEXT_PRIMARY);
        sentence.setAlignmentX(LEFT_ALIGNMENT);

        typed.setName("preview.typeToConfirm");
        typed.putClientProperty("JTextField.placeholderText", name);
        typed.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateExecute();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateExecute();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateExecute();
            }
        });
        JLabel label = new JLabel("<html>" + Texts.get("preview.typeToConfirm.label", html(name)) + "</html>");
        label.setLabelFor(typed);
        label.setForeground(Tokens.TEXT_SECONDARY);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), 0, 0, 0));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Tokens.px(Tokens.SPACE_8)));
        row.add(label);
        row.add(typed);

        JPanel band = new JPanel();
        band.setName("preview.strong");
        band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
        band.setBackground(Tokens.DANGER_TINT);
        band.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, Tokens.px(3), 0, 0, Tokens.DANGER),
                BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_16),
                        Tokens.px(Tokens.SPACE_12), Tokens.px(Tokens.SPACE_16))));
        band.add(sentence);
        band.add(row);
        int pad = Tokens.px(Tokens.SPACE_24);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(Tokens.px(Tokens.SPACE_16), pad, 0, pad));
        wrap.add(band);
        return wrap;
    }

    /**
     * «eliminare la tabella libri», «svuotare la tabella prestiti»…: dalla prima istruzione distruttiva dello script,
     * riconosciuta dal core anche con commenti iniziali, {@code DROP TEMPORARY TABLE} e {@code SET STATEMENT … FOR}.
     */
    String action() {
        return switch (ConfirmationPolicy.actionOf(script)) {
            case DROP_TABLE -> Texts.get("preview.strong.dropTable");
            case DROP_VIEW -> Texts.get("preview.strong.dropView");
            case DROP_CATALOG -> Texts.get("preview.strong.dropCatalog");
            case TRUNCATE -> Texts.get("preview.strong.truncate");
            case OTHER -> Texts.get("preview.strong.other");
        };
    }

    /** Testo del pulsante che esegue: verbo d'azione (§4). */
    private String executeLabel() {
        if (!isStrong()) {
            return Texts.get("preview.execute");
        }
        String a = action();
        if (a.equals(Texts.get("preview.strong.truncate"))) {
            return Texts.get("preview.execute.truncate");
        }
        if (a.equals(Texts.get("preview.strong.other"))) {
            return Texts.get("preview.execute");
        }
        return Texts.get("preview.execute.drop");
    }

    private JComponent buttons() {
        execute.setText(executeLabel());
        execute.setName("preview.execute");
        copy.setName("preview.copy");
        cancel.setName("preview.cancel");
        copy.setToolTipText(Texts.get("preview.copy.tooltip"));
        copy.putClientProperty("JButton.buttonType", "borderless");
        copy.setForeground(Tokens.ACCENT);
        Styles.primary(execute, isStrong() ? Tokens.DANGER : Tokens.ACCENT);
        execute.addActionListener(e -> {
            if (confirmation.accepts(typed.getText())) {
                choose(Decision.EXECUTE);
            }
        });
        copy.addActionListener(e -> choose(Decision.COPY));
        cancel.addActionListener(e -> choose(Decision.CANCEL));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(copy);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Tokens.px(Tokens.SPACE_8), 0));
        right.setOpaque(false);
        right.add(cancel);
        right.add(execute);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        int pad = Tokens.px(Tokens.SPACE_24);
        bar.setBorder(BorderFactory.createEmptyBorder(pad, pad - Tokens.px(Tokens.SPACE_8), pad, pad));
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean isStrong() {
        return confirmation.level() == ConfirmationPolicy.Level.STRONG;
    }

    private void updateExecute() {
        execute.setEnabled(confirmation.accepts(typed.getText()));
    }

    private void choose(Decision chosen) {
        decision = chosen;
        dispose();
    }

    /** Mostra la finestra (modale) e restituisce la scelta; chiusura con Esc o con la X = {@link Decision#CANCEL}. */
    public Decision showModal() {
        decision = Decision.CANCEL;
        setVisible(true);
        return decision;
    }

    /** La scelta fatta (Annulla finché non si preme altro). */
    public Decision decision() {
        return decision;
    }

    // ------------------------------------------------------------------ accesso per i test

    public String sqlText() {
        return sql.getText();
    }

    public JButton executeButton() {
        return execute;
    }

    public JButton copyButton() {
        return copy;
    }

    public JButton cancelButton() {
        return cancel;
    }

    /** Il campo della conferma rafforzata (presente nella finestra solo con {@link ConfirmationPolicy.Level#STRONG}). */
    public JTextField confirmationField() {
        return typed;
    }

    public boolean requiresTypedConfirmation() {
        return isStrong();
    }

    public ConfirmationPolicy.Confirmation confirmation() {
        return confirmation;
    }
}
