/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * <b>T12.3</b>: nessun testo dell'interfaccia scritto nel codice. Si cercano, nei sorgenti del programma (app, core,
 * model e tutto il query builder, anche il codice ereditato da SQLeo), le stringhe letterali con delle lettere passate
 * ai metodi che mostrano testo all'utente (etichette, pulsanti, voci di menu anche come {@code super("…")} e
 * {@code putValue(NAME, "…")}, suggerimenti, titoli, finestre di messaggio, linguette, bordi con titolo, fasce di
 * avviso): devono essere zero. I testi stanno nei file di risorse, predisposti per l'inglese. Non contano le parole
 * chiave SQL tutte maiuscole (SUBQUERY, AND…: sono ciò che si insegna) né le chiavi dei testi (come
 * {@code condition.edit}), e nemmeno i testi predefiniti passati a {@code I18n.getString}.
 */
@Tag("step12")
class T123TestiNelCodiceTest {

    /** Chiamate che mostrano il loro argomento testuale all'utente. */
    private static final Pattern UI_CALL = Pattern.compile(
            "(?:\\.setText|\\.setToolTipText|\\.setTitle|\\.setBorderTitle|\\.addTab|\\.insertTab|\\.setTitleAt"
                    + "|\\.setToolTipTextAt|showMessageDialog|showConfirmDialog|showInputDialog|showOptionDialog"
                    + "|createTitledBorder|new\\s+J(?:Label|Button|MenuItem|Menu|CheckBox|RadioButton|ToggleButton"
                    + "|CheckBoxMenuItem|RadioButtonMenuItem)|banner\\.set|Banner\\.Line|\\.message\\(PipelineView"
                    + "\\.MessageKind\\.\\w+|\\.setName\\b|\\.alert|\\bsuper|putValue\\s*\\(\\s*(?:Action\\.)?NAME\\s*,)"
                    + "\\s*\\(?(?:[^;\"]*?,\\s*)?\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** Una parola chiave SQL (tutta maiuscola) o una chiave dei testi ({@code condition.edit}): non è un testo da tradurre. */
    private static final Pattern NOT_TEXT = Pattern.compile("[A-Z][A-Z _]*|[a-z]+(?: [a-z]+)*(?:\\.[a-z]+)+");
    /** Un testo per le persone: almeno due lettere di fila (non «+», «−», «%», «…» o simboli). */
    private static final Pattern WORDS = Pattern.compile("\\p{L}{2,}");

    static List<String> hits(Path root) throws IOException {
        List<String> out = new ArrayList<>();
        List<Path> dirs = List.of(root.resolve("app/src/main/java"), root.resolve("core/src/main/java"),
                root.resolve("model/src/main/java"), root.resolve("sqleo-qb/src/main/java"));
        for (Path dir : dirs) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(f);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.strip().startsWith("*") || line.strip().startsWith("//")) {
                            continue;
                        }
                        Matcher m = UI_CALL.matcher(line);
                        while (m.find()) {
                            String literal = m.group(1);
                            // setName("…") dà un nome interno (per i test e i suggerimenti), non un testo mostrato
                            if (m.group().contains("setName")) {
                                continue;
                            }
                            // nomi di stile del tema (Styles.text(…, "heading", …)) e marcatori HTML non sono testi
                            if (m.group().contains("Styles.") || line.contains("Styles.text(")
                                    && literal.matches("[a-z][A-Za-z]*")) {
                                continue;
                            }
                            String plain = literal.replaceAll("<[^>]*>", " ").strip();
                            if (NOT_TEXT.matcher(plain).matches()) {
                                continue;
                            }
                            if (WORDS.matcher(plain).find()) {
                                out.add(root.relativize(f) + ":" + (i + 1) + ": \"" + literal + "\"");
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    @Test
    void nessunTestoDellInterfacciaNelCodice() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("sqleo-qb"))) {
            root = root.getParent();
        }
        List<String> found = hits(root);
        Files.createDirectories(root.resolve("test-results/step12"));
        Files.writeString(root.resolve("test-results/step12/T12.3-testi-nel-codice.txt"), "T12.3 — testi dell'interfaccia scritti nel codice"
                + " (app, core, model, tutto il query builder): " + found.size() + "\n" + String.join("\n", found)
                + "\nEsito: " + (found.isEmpty() ? "OK" : "FALLITO") + "\n");
        assertEquals(List.of(), found);
    }

    @Test
    void loScannerTrovaUnTestoScrittoNelCodice() {
        Matcher m = UI_CALL.matcher("        label.setText(\"Salva il file\");");
        assertTrue(m.find() && WORDS.matcher(m.group(1)).find(), "il controllo non è cieco");
        Matcher t = UI_CALL.matcher("        b.setToolTipText(Texts.get(\"x.tooltip\"));");
        assertTrue(!t.find(), "i testi dalle risorse passano");
        Matcher d = UI_CALL.matcher("        JOptionPane.showMessageDialog(owner, \"Errore grave\", t, 0);");
        assertTrue(d.find(), "anche il secondo argomento");
        Matcher sup = UI_CALL.matcher("            super(\"select\");");
        assertTrue(sup.find() && !NOT_TEXT.matcher(sup.group(1)).matches(), "anche il testo di una voce di menu");
        Matcher lbl = UI_CALL.matcher("        JLabel lbl = new JLabel(\"identifier:\");");
        assertTrue(lbl.find(), "anche un'etichetta del codice di SQLeo");
        assertTrue(NOT_TEXT.matcher("SUBQUERY").matches() && NOT_TEXT.matcher("condition.edit").matches());
    }
}
