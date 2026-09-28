/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.fixtures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * File di prova dell'importazione (Step 9), coerenti con la «biblioteca»: li scrive {@link #main} in
 * {@code it-tests/fixtures/import/} (e {@code ImportFixturesTest} controlla che quelli nel repository siano questi).
 * <ul>
 *   <li>{@code soci.csv} — 100 soci nuovi (tessere {@code S0000001}…, che non si sovrappongono a quelle della
 *       biblioteca), UTF-8 senza BOM, punto e virgola, date {@code gg/mm/aaaa}, accenti, apostrofi, qualche email
 *       vuota (→ NULL): si importa nella tabella {@code soci} esistente (T9.4);</li>
 *   <li>{@code libri.json} — 200 libri come elenco di oggetti piatti, con qualche chiave mancante e qualche
 *       {@code null}: si importa in una <b>tabella nuova</b> con i tipi dedotti (T9.4);</li>
 *   <li>{@code soci-errori.csv} — 100 righe, 5 sbagliate apposta (T9.7): chiave duplicata, data impossibile, testo in
 *       colonna numerica, NOT NULL vuoto, chiave esterna inesistente (si importa in {@code prestiti});</li>
 *   <li>{@code soci-excel.csv} — lo stesso elenco di soci salvato da <b>Excel italiano</b> (lo produce
 *       {@code crea-soci-excel.ps1} con Excel vero, non questo generatore).</li>
 * </ul>
 */
public final class ImportFixtures {

    public static final String DIR = "import";
    public static final String SOCI = "soci.csv";
    public static final String LIBRI = "libri.json";
    public static final String PRESTITI_ERRORI = "prestiti-errori.csv";
    public static final int SOCI_ROWS = 100;
    public static final int LIBRI_ROWS = 200;

    private static final String[] COGNOMI = {"Rossi", "Bianchi", "D'Angelo", "Esposito", "Colombo", "Romano",
        "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "De Luca", "Mancini", "Costa", "Giordano", "Rizzo",
        "Lombardi", "Moretti", "Barbieri", "Fontana", "Santoro", "Mariani", "Rinaldi", "Caruso", "Ferrara", "Galli",
        "Martini", "Leone", "Longo", "Gentile", "Martinelli", "Vitale", "Lombardo", "Serra", "Coppola", "De Santis",
        "D'Amico", "Marchetti", "Parisi", "Villa", "Conte", "Ferraro", "Fabbri", "Bianco", "Marini", "Grasso",
        "Valentini", "Messina", "Sala"};
    private static final String[] NOMI = {"Niccolò", "Anna", "Mattia", "Chiara", "Nicolò", "Beatrice", "Andrea",
        "Sofia", "Tommaso", "Aurora", "Gabriele", "Ginevra", "Riccardo", "Alice", "Edoardo", "Emma", "Lorenzo",
        "Giorgia", "Matteo", "Martina", "Francesco", "Noemi", "Alessandro", "Asia", "Leonardo"};
    private static final String[] TITOLI = {"Il sentiero", "La città", "Lettere", "Il mare", "Cronache",
        "L'ombra", "Racconti", "Il giardino", "Viaggio", "La notte", "Storie", "Il ritorno", "Sogni", "Il vento",
        "La casa"};
    private static final String[] DI = {"d'autunno", "dei nidi", "del nord", "perduta", "di pietra", "in città",
        "è finito", "di Forlì", "senza nome", "all'alba", "di carta", "per sempre", "sul fiume"};

    private ImportFixtures() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "it-tests/fixtures").resolve(DIR);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(SOCI), soci(), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(LIBRI), libri(), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(PRESTITI_ERRORI), prestitiConErrori(), StandardCharsets.UTF_8);
    }

    /** Un socio: tessera, cognome, nome, email, nato il (gg/mm/aaaa). */
    public record Socio(String tessera, String cognome, String nome, String email, LocalDate natoIl) {
    }

    public static List<Socio> sociList() {
        List<Socio> out = new ArrayList<>();
        for (int i = 1; i <= SOCI_ROWS; i++) {
            String cognome = COGNOMI[(i * 7) % COGNOMI.length];
            String nome = NOMI[(i * 11) % NOMI.length];
            String email = i % 9 == 0 ? null : (nome + "." + cognome).toLowerCase(Locale.ROOT)
                    .replace("'", "").replace(" ", "").replace("ò", "o") + i + "@esempio.it";
            LocalDate nato = LocalDate.of(1950 + (i * 3) % 55, 1 + (i * 5) % 12, 1 + (i * 13) % 28);
            out.add(new Socio(String.format("S%07d", i), cognome, nome, email, nato));
        }
        return out;
    }

    static String soci() {
        StringBuilder sb = new StringBuilder("tessera;cognome;nome;email;nato_il\n");
        for (Socio s : sociList()) {
            sb.append(s.tessera()).append(';').append(s.cognome()).append(';').append(s.nome()).append(';')
                    .append(s.email() == null ? "" : s.email()).append(';').append(italian(s.natoIl())).append('\n');
        }
        return sb.toString();
    }

    public static String italian(LocalDate d) {
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    static String libri() {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 1; i <= LIBRI_ROWS; i++) {
            String titolo = TITOLI[i % TITOLI.length] + " " + DI[(i * 5) % DI.length] + (i > 100 ? " II" : "");
            sb.append("  {\"id\": ").append(i)
                    .append(", \"titolo\": \"").append(titolo.replace("\"", "\\\"")).append('"')
                    .append(", \"isbn\": \"").append(String.format("97888%08d", 10_000 + i * 37)).append('"');
            if (i % 10 != 0) {
                sb.append(", \"anno\": ").append(1950 + (i * 7) % 75);
            } else {
                sb.append(", \"anno\": null");
            }
            sb.append(", \"prezzo\": ").append(String.format(Locale.ROOT, "%d.%02d", 5 + (i * 3) % 40, (i * 17) % 100))
                    .append(", \"disponibile\": ").append(i % 4 != 0);
            if (i % 3 == 0) {
                sb.append(", \"note\": \"").append(i % 6 == 0 ? "edizione rilegata — con dedica ✍" : "copia usata")
                        .append('"');
            }
            sb.append('}').append(i < LIBRI_ROWS ? ",\n" : "\n");
        }
        return sb.append("]\n").toString();
    }

    /**
     * 100 prestiti (per la tabella {@code prestiti} della biblioteca: libri 1-200, soci 1-100), di cui 5 sbagliati
     * alle righe 11, 23, 37, 58, 90 del file (la riga 1 è l'intestazione).
     */
    static String prestitiConErrori() {
        StringBuilder sb = new StringBuilder("id;id_libro;id_socio;data_prestito;data_reso\n");
        for (int i = 1; i <= 100; i++) {
            int line = i + 1;
            String id = String.valueOf(1000 + i);
            String libro = String.valueOf(1 + (i * 7) % 200);
            String socio = String.valueOf(1 + (i * 3) % 100);
            String prestito = italian(LocalDate.of(2025, 1 + i % 12, 1 + i % 28));
            String reso = i % 4 == 0 ? "" : italian(LocalDate.of(2025, 1 + i % 12, 1 + i % 28).plusDays(14));
            switch (line) {
                case 11 -> id = "1002";                  // chiave duplicata (la riga 3 ha già 1002)
                case 23 -> prestito = "31/02/2025";      // data impossibile
                case 37 -> libro = "trentasette";        // testo in colonna numerica
                case 58 -> socio = "";                   // NOT NULL vuoto
                case 90 -> libro = "9999";               // chiave esterna inesistente
                default -> {
                }
            }
            sb.append(id).append(';').append(libro).append(';').append(socio).append(';').append(prestito).append(';')
                    .append(reso).append('\n');
        }
        return sb.toString();
    }

    /** Le righe sbagliate di {@link #prestitiConErrori()}. */
    public static final List<Long> ERROR_LINES = List.of(11L, 23L, 37L, 58L, 90L);
}
