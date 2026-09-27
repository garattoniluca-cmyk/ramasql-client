/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T7.1 e T7.10 — controlli sul modulo {@code sqleo-qb} (Step 7), sui sorgenti così come sono nel repository.
 * <ul>
 *   <li><b>T7.1</b>: nel modulo (codice e risorse) non compaiono {@code isFullVersion}, {@code Donate},
 *       {@code google-analytics}, {@code VERSION_TRACK}, né i nomi di altri DBMS <b>a parola intera</b> (così
 *       {@code addOrderByClause}, che contiene «derby», non conta; vedi la nota in {@code UPSTREAM.md}).</li>
 *   <li><b>T7.10</b> (controllo GPL): ogni file ereditato conserva l'intestazione originale (copyright e «GNU General
 *       Public License … version 2 … any later version»); ogni file ereditato che contiene una nostra modifica (un
 *       commento «RamaSQL») ha nell'intestazione la nota «Modificato per RamaSQL Client (data)» ed è citato nel
 *       paragrafo «File modificati e perché» di {@code UPSTREAM.md}; ogni file del modulo sotto {@code com/sqleo} è
 *       elencato in {@code UPSTREAM.md}; il codice nostro porta la nostra intestazione GPL-3.0-or-later.</li>
 * </ul>
 */
@Tag("step7")
class T71T710ModuloTest {

    private static final Path MODULE = Path.of(System.getProperty("user.dir"));
    private static final Path MAIN = MODULE.resolve("src").resolve("main");
    private static final String OUR_HEADER = "RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later";

    private static List<Path> files(Path root, String... extensions) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString();
                for (String e : extensions) {
                    if (n.endsWith(e)) {
                        return true;
                    }
                }
                return false;
            }).sorted().toList();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- T7.1

    private static final List<String> VIETATE = List.of("isFullVersion", "Donate", "google-analytics", "VERSION_TRACK");
    private static final Pattern ALTRI_DBMS = Pattern.compile("(?i)\\b(oracle|postgres|postgresql|sql\\s*server|mssql"
            + "|db2|derby|hsqldb|hsql|h2|sqlite|firebird|interbase|informix|sybase|teradata|ingres|csvjdbc|odbc"
            + "|ms\\s*access|mimer|pointbase|mckoi)\\b");

    @Test
    void t71_nessunaTracciaDiVersioneAPagamentoStatisticheOAltriDbms() throws IOException {
        List<Path> tutti = files(MAIN, ".java", ".properties", ".md");
        assertTrue(tutti.size() > 60, "sorgenti del modulo trovati: " + tutti.size());
        List<String> trovate = new ArrayList<>();
        for (Path p : tutti) {
            String t = read(p);
            for (String v : VIETATE) {
                if (t.contains(v)) {
                    trovate.add(MODULE.relativize(p) + ": " + v);
                }
            }
            Matcher m = ALTRI_DBMS.matcher(t);
            while (m.find()) {
                trovate.add(MODULE.relativize(p) + ": " + m.group());
            }
        }
        assertEquals(List.of(), trovate, "occorrenze vietate nel modulo");
        // il controllo vede davvero quello che cerca
        assertTrue(ALTRI_DBMS.matcher("usa Oracle o PostgreSQL").find());
        assertTrue(!ALTRI_DBMS.matcher("addOrderByClause").find(), "«derby» dentro una parola non conta");
    }

    // ---------------------------------------------------------------- T7.10

    /** La prima riga di commento: l'intestazione del file. */
    private static String header(String text) {
        int end = text.indexOf("*/");
        return end < 0 ? "" : text.substring(0, end);
    }

    @Test
    void t710_intestazioniNoteDiModificaEUpstream() throws IOException {
        String upstream = read(MODULE.resolve("UPSTREAM.md"));
        int modifiedStart = upstream.indexOf("## File modificati e perché");
        int modifiedEnd = upstream.indexOf("## Rimosso rispetto all'originale");
        assertTrue(modifiedStart > 0 && modifiedEnd > modifiedStart, "sezione «File modificati e perché» in UPSTREAM.md");
        String modifiedSection = upstream.substring(modifiedStart, modifiedEnd);

        List<String> problemi = new ArrayList<>();
        int ereditati = 0;
        int modificati = 0;
        int nostri = 0;
        int confrontati = 0;
        // il clone dell'originale (spikes/sqleo-upstream, escluso da git: c'è sul PC di sviluppo)
        Path clone = MODULE.getParent().resolve("spikes").resolve("sqleo-upstream").resolve("src");
        boolean conClone = Files.isDirectory(clone);
        StringBuilder evidenza = new StringBuilder("T7.10 — controllo GPL del modulo sqleo-qb\n");
        try {
        for (Path p : files(MAIN.resolve("java").resolve("com").resolve("sqleo"), ".java")) {
            String name = p.getFileName().toString();
            String text = read(p);
            String head = header(text);
            String rel = MAIN.resolve("java").resolve("com").resolve("sqleo").relativize(p).toString().replace('\\', '/');
            if (!upstream.contains(name)) {
                problemi.add(rel + ": non elencato in UPSTREAM.md");
            }
            if (head.contains(OUR_HEADER)) {
                nostri++;
                continue;
            }
            ereditati++;
            if (!head.toLowerCase(Locale.ROOT).contains("copyright") && !head.contains("@author")) {
                problemi.add(rel + ": intestazione originale senza copyright");
            }
            if (!head.contains("GNU General Public License") || !head.contains("version 2")
                    || !head.contains("any later version")) {
                problemi.add(rel + ": intestazione GPL-2.0-or-later originale non intatta");
            }
            boolean toccato = text.substring(head.length()).contains("RamaSQL");
            boolean nota = head.contains("Modificato per RamaSQL Client (20");
            if (toccato && !nota) {
                problemi.add(rel + ": modificato (commenti «RamaSQL» nel codice) ma senza nota nell'intestazione");
            }
            if (nota) {
                modificati++;
                if (!modifiedSection.contains(name)) {
                    problemi.add(rel + ": nota di modifica ma assente da «File modificati e perché»");
                }
            }
            Path originale = clone.resolve("com").resolve("sqleo").resolve(rel);
            if (conClone && Files.exists(originale)) {
                confrontati++;
                String orig = read(originale);
                List<String> intestazioneOrig = righe(header(orig));
                List<String> nostra = righe(head);
                int primaNota = -1;
                for (int k = 0; k < nostra.size(); k++) {
                    if (nostra.get(k).contains("Modificato per RamaSQL Client")) {
                        primaNota = k;
                        break;
                    }
                }
                List<String> parteOriginale = senzaCodaVuota(primaNota < 0 ? nostra : nostra.subList(0, primaNota));
                if (!parteOriginale.equals(senzaCodaVuota(intestazioneOrig))) {
                    problemi.add(rel + ": l'intestazione originale non coincide con quella di SQLeo");
                }
                if (!nota && !righe(text).equals(righe(orig))) {
                    problemi.add(rel + ": diverso dall'originale ma senza nota di modifica");
                }
            }
        }
        assertEquals(List.of(), problemi, "controllo GPL del modulo sqleo-qb");
        assertTrue(ereditati >= 45, "file ereditati controllati: " + ereditati);
        assertTrue(modificati >= 25, "file ereditati modificati (con nota): " + modificati);
        assertTrue(nostri >= 4, "file nostri nel pacchetto ereditato: " + nostri);
        evidenza.append("file ereditati controllati: ").append(ereditati)
                .append(" (intestazione originale GPL-2.0-or-later intatta)\n")
                .append("di cui modificati, con nota «Modificato per RamaSQL Client (data)» e citati in UPSTREAM.md: ")
                .append(modificati).append('\n')
                .append("file nostri nel pacchetto com.sqleo (intestazione GPL-3.0-or-later): ").append(nostri).append('\n')
                .append(conClone ? "confrontati con il clone dell'originale (spikes/sqleo-upstream): " + confrontati
                        + " file — intestazioni originali identiche; i file senza nota identici all'originale\n"
                        : "clone dell'originale assente (spikes/sqleo-upstream, fuori da git): confronto non eseguito\n")
                .append("ogni file sotto com/sqleo elencato in UPSTREAM.md: sì\nproblemi: nessuno\nEsito: SUPERATO\n");
        } catch (Throwable t) {
            evidenza.append("problemi: ").append(problemi).append("\nEsito: FALLITO - ").append(t).append('\n');
            throw t;
        } finally {
            Step7Files.write("T7.10-controllo-gpl.txt", evidenza.toString());
        }
    }

    /** Le righe del testo, senza spazi a fine riga (in un file ereditato erano stati tolti: non è una modifica). */
    private static List<String> righe(String text) {
        return text.lines().map(String::stripTrailing).toList();
    }

    private static List<String> senzaCodaVuota(List<String> righe) {
        List<String> out = new ArrayList<>(righe);
        while (!out.isEmpty() && (out.get(out.size() - 1).isBlank() || out.get(out.size() - 1).strip().equals("*"))) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
