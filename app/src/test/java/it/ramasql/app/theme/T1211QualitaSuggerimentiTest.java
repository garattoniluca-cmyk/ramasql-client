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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.IndexKind;

/**
 * <b>T12.11</b>: la qualità dei testi dei suggerimenti (tutte le chiavi {@code *.tooltip} e {@code nav.tip.*} dei file di
 * risorse del programma e del query builder, e le
 * spiegazioni composte delle voci delle liste). Ogni testo è un trafiletto completo in italiano: abbastanza lungo,
 * senza «TODO», segnaposti rimasti o frasi inglesi, senza duplicati copiati fra chiavi diverse; le scelte con
 * conseguenze sui dati (motori, azioni delle chiavi esterne, tipi d'indice, eliminazioni…) hanno almeno due frasi.
 */
@Tag("step12")
class T1211QualitaSuggerimentiTest {

    private static final Pattern ITALIAN = Pattern.compile(
            "(?iu)(^|[\\s«(])(il|lo|la|le|gli|un|una|di|da|per|che|non|con|si|è|e|del|della|dei|delle|degli|nel|"
                    + "nella|nelle|al|alla|alle|sono|o|in|questa|questo|qui|quando|anche|più|come|ogni)([\\s,.;:»)]|$)"
                    + "|(?iu)\\b(l|dell|nell|all|sull|dall|un)'\\p{L}");
    /** Parole inglesi (minuscole o a inizio frase): le parole chiave SQL tutte maiuscole, come AND, sono ciò che si insegna. */
    private static final Pattern ENGLISH = Pattern.compile("\\b(?:[Tt]he|and|[Cc]lick|[Pp]lease|select the|is not|[Tt]his)\\b");
    private static final Pattern PLACEHOLDER_LEFT = Pattern.compile("TODO|FIXME|XXX|\\?\\?\\?|lorem", Pattern.CASE_INSENSITIVE);
    /** Chiavi in un'altra lingua per scelta (la voce «English» della lingua). */
    private static final Set<String> NOT_ITALIAN = Set.of("settings.language.en.tooltip");
    /** Scelte con conseguenze sui dati: almeno due frasi (cosa fa, cosa comporta). */
    private static final List<String> CONSEQUENCES = List.of("tableeditor.engine.", "tableeditor.fk.action.",
            "tableeditor.index.kind.", "nav.menu.drop", "nav.menu.truncate", "columns.remove", "dump.content.DATA",
            "import.option.duplicates.ignore", "charset.utf8mb3", "charset.latin1", "tableeditor.type.FLOAT",
            "tableeditor.type.DOUBLE", "tableeditor.type.ENUM", "script.target.catalog");

    /** I suggerimenti del programma e del query builder: le chiavi {@code *.tooltip} e quelle del navigatore. */
    static Map<String, String> tooltips() throws IOException {
        Map<String, String> out = new TreeMap<>();
        for (String res : List.of("/it/ramasql/app/messages.properties", "/it/ramasql/qb/qb_it.properties")) {
            Properties p = new Properties();
            try (InputStream in = T1211QualitaSuggerimentiTest.class.getResourceAsStream(res)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            for (String k : p.stringPropertyNames()) {
                if (k.endsWith(".tooltip") || k.startsWith("nav.tip.")) {
                    out.put(k, p.getProperty(k));
                }
            }
        }
        return out;
    }

    static int sentences(String text) {
        String t = text.replaceAll("\\([^)]*\\)", "").replaceAll("\\d+[.,]\\d+", "0").strip();
        int n = 0;
        for (String part : t.split("(?<=[.!?:])\\s+(?=[A-ZÀ-Ý«])")) {
            if (part.strip().length() > 3) {
                n++;
            }
        }
        return n;
    }

    @Test
    void ogniSuggerimentoEUnTrafilettoCompletoInItaliano() throws IOException {
        Map<String, String> tips = tooltips();
        // anche le spiegazioni composte dai dati (voci delle liste costruite dal server)
        Map<String, String> all = new LinkedHashMap<>(tips);
        for (FkAction a : FkAction.values()) {
            all.put("[voce] azione " + a.sql(), Tips.of("tableeditor.fk.action").apply(a.sql()));
        }
        for (IndexKind k : IndexKind.values()) {
            all.put("[voce] indice " + k, Tips.item("tableeditor.index.kind", k, k.name()));
        }
        for (String c : List.of("utf8mb4_uca1400_ai_ci", "utf8mb4_0900_as_cs", "latin1_swedish_ci", "utf8mb4_bin",
                "utf8mb4_general_ci", "utf8mb4_unicode_ci")) {
            all.put("[voce] collation " + c, Tips.collation(c));
        }
        for (String c : List.of("utf8mb4", "cp1250", "")) {
            all.put("[voce] charset " + c, Tips.charset(c));
        }
        List<String> problems = new ArrayList<>();
        Map<String, String> seen = new TreeMap<>();
        for (Map.Entry<String, String> e : all.entrySet()) {
            String key = e.getKey();
            String text = e.getValue() == null ? "" : e.getValue().replaceAll("^\\*\\*[^*]*\\*\\*\\s*", "");
            if (text.length() < 30) {
                problems.add(key + ": troppo corto «" + text + "»");
            }
            if (PLACEHOLDER_LEFT.matcher(text).find()) {
                problems.add(key + ": segnaposto rimasto «" + text + "»");
            }
            if (!NOT_ITALIAN.contains(key) && !ITALIAN.matcher(text).find()) {
                problems.add(key + ": non sembra italiano «" + text + "»");
            }
            if (!NOT_ITALIAN.contains(key) && ENGLISH.matcher(text.replaceAll("«[^»]*»|'[^']*'", "")).find()) {
                problems.add(key + ": parole inglesi «" + text + "»");
            }
            // le voci composte ripetono per costruzione il testo della loro chiave: il doppione si cerca fra chiavi
            String previous = key.startsWith("[voce]") ? null : seen.putIfAbsent(text.toLowerCase(Locale.ROOT), key);
            if (previous != null) {
                problems.add(key + ": stesso testo di " + previous);
            }
            boolean consequences = CONSEQUENCES.stream().anyMatch(key::startsWith)
                    || key.startsWith("[voce] azione") || key.startsWith("[voce] indice");
            if ((consequences || text.contains("Attenzione")) && sentences(text) < 2) {
                problems.add(key + ": scelta con conseguenze in una frase sola «" + text + "»");
            }
        }
        StringBuilder ev = new StringBuilder("T12.11 — qualità dei suggerimenti: " + all.size() + " testi controllati ("
                + tips.size() + " chiavi dei suggerimenti del programma e del query builder e " + (all.size() - tips.size())
                + " spiegazioni composte delle voci)\nProblemi: " + problems.size() + "\n");
        problems.forEach(p -> ev.append("  ").append(p).append('\n'));
        ev.append("Esito: ").append(problems.isEmpty() ? "OK" : "FALLITO").append('\n');
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
        while (!java.nio.file.Files.isDirectory(root.resolve("sqleo-qb"))) {
            root = root.getParent();
        }
        java.nio.file.Files.createDirectories(root.resolve("test-results/step12"));
        java.nio.file.Files.writeString(root.resolve("test-results/step12/T12.11-qualita.txt"), ev.toString(),
                StandardCharsets.UTF_8);
        assertEquals(List.of(), problems);
        assertTrue(all.size() > 250, "testi controllati: " + all.size());
    }

    @Test
    void ilContatoreDiFrasiNonSiLasciaIngannare() {
        assertEquals(1, sentences("Una sola frase, con virgole e 3,14 numeri."));
        assertEquals(2, sentences("Prima frase. Seconda frase con INT(11) e altro."));
        assertEquals(2, sentences("Cosa fa. Attenzione: cosa comporta."));
    }
}
