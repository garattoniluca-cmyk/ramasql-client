/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * (c) <b>Nessun colore fisso fuori da {@code theme/}</b> ({@code DESIGN-SYSTEM.md} §1): nei sorgenti di
 * {@code app/src/main} si cercano {@code new Color(}, {@code Color.decode(}, le costanti {@code Color.WHITE}… e i
 * letterali {@code 0xRRGGBB} / {@code "#RRGGBB"} (commenti esclusi), e nei testi {@code #RRGGBB}. Ammessi solo in
 * {@code it/ramasql/app/theme/}.
 * <p>Severo per i pacchetti della shell e del tema (radice di {@code it.ramasql.app}, {@code connection},
 * {@code settings}, {@code grid}, {@code tableeditor}). I pacchetti ancora in lavorazione in parallelo
 * ({@code navigator}, {@code pipeline}, {@code sqlpanel}, {@code workspace}, {@code editor}) per ora sono solo
 * <em>elencati</em> in {@code test-results/step2/colori-fuori-tema.txt}, senza far fallire il test.
 */
@Tag("step2")
@Tag("ui")
class NoFixedColorsTest {

    /** Pacchetti in cui un colore fisso è un errore. */
    private static final List<String> STRICT = List.of("", "connection", "settings", "grid", "tableeditor");
    /** Pacchetti per ora solo elencati. */
    private static final List<String> LISTED = List.of("navigator", "pipeline", "sqlpanel", "workspace", "editor");

    private static final Pattern FIXED = Pattern.compile(
            "new\\s+(java\\.awt\\.)?Color\\s*\\(|Color\\.decode\\s*\\(|\\bColor\\.(WHITE|BLACK|RED|GREEN|BLUE|YELLOW|"
                    + "ORANGE|PINK|GRAY|GREY|LIGHT_GRAY|DARK_GRAY|CYAN|MAGENTA|white|black|red|green|blue|yellow|"
                    + "orange|pink|gray|lightGray|darkGray|cyan|magenta)\\b|\\b0x[0-9A-Fa-f]{6}\\b|\"#[0-9A-Fa-f]{6}\"");
    private static final Pattern HEX_IN_TEXT = Pattern.compile("#[0-9A-Fa-f]{6}\\b");

    @Test
    void nessunColoreFissoFuoriDalTema() throws IOException {
        Path root = ThemeTestSupport.projectRoot().resolve("app/src/main/java/it/ramasql/app");
        List<String> strictHits = new ArrayList<>();
        List<String> listedHits = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String pkg = packageOf(root, file);
                if (pkg.equals("theme")) {
                    continue;
                }
                scanned++;
                List<String> hits = hits(file, root);
                if (LISTED.contains(pkg)) {
                    listedHits.addAll(hits);
                } else {
                    strictHits.addAll(hits);
                }
            }
        }
        // i testi dell'interfaccia: niente colori scritti nell'HTML delle etichette
        Path messages = ThemeTestSupport.projectRoot().resolve("app/src/main/resources/it/ramasql/app/messages.properties");
        List<String> lines = Files.readAllLines(messages, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith("#") && HEX_IN_TEXT.matcher(line).find()) {
                strictHits.add("messages.properties:" + (i + 1) + ": " + line.strip());
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("Colori fissi fuori da it/ramasql/app/theme/ (DESIGN-SYSTEM §1) — scansione di ").append(scanned)
                .append(" sorgenti di app/src/main + messages.properties\n\n");
        report.append("Pacchetti della shell e del tema (severi: devono essere zero): ").append(strictHits.size()).append('\n');
        strictHits.forEach(h -> report.append("  ").append(h).append('\n'));
        report.append("\nPacchetti in lavorazione in parallelo (").append(String.join(", ", LISTED))
                .append("), solo elencati: ").append(listedHits.size()).append('\n');
        listedHits.forEach(h -> report.append("  ").append(h).append('\n'));
        report.append("\nDa sostituire con le costanti di it.ramasql.app.theme.Tokens (o con le chiavi del tema FlatLaf).\n");
        ThemeTestSupport.writeText("colori-fuori-tema.txt", report.toString());

        assertTrue(scanned > 30, "sorgenti trovati: " + scanned);
        assertTrue(strictHits.isEmpty(), "colori fissi fuori dal tema:\n" + String.join("\n", strictHits));
    }

    private static String packageOf(Path root, Path file) {
        Path rel = root.relativize(file.getParent());
        return rel.getNameCount() == 0 || rel.toString().isEmpty() ? "" : rel.getName(0).toString();
    }

    private static List<String> hits(Path file, Path root) throws IOException {
        String source = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        List<String> out = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = FIXED.matcher(lines[i]);
            if (m.find()) {
                out.add(root.relativize(file).toString().replace('\\', '/') + ":" + (i + 1) + ": " + lines[i].strip());
            }
        }
        return out;
    }

    /** Toglie i commenti ({@code //} e blocchi) lasciando le righe al loro posto; le stringhe restano. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        boolean inString = false;
        boolean inChar = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (inString || inChar) {
                out.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    out.append(next);
                    i += 2;
                    continue;
                }
                if ((inString && c == '"') || (inChar && c == '\'')) {
                    inString = false;
                    inChar = false;
                }
                i++;
            } else if (c == '/' && next == '/') {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < s.length() && !(s.charAt(i) == '*' && i + 1 < s.length() && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '\'') {
                    inChar = true;
                }
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
