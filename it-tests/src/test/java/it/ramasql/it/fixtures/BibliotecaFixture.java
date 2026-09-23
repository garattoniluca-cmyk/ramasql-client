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
import java.util.function.IntFunction;

import it.ramasql.core.sqlgen.SqlLiterals;
import it.ramasql.it.TestResults;

/**
 * Generatore <b>deterministico</b> dello schema di prova canonico (ROADMAP, «Schema di prova canonico»): produce
 * {@code it-tests/fixtures/biblioteca.sql} (InnoDB, con le chiavi esterne) e {@code biblioteca_myisam.sql} (stesse
 * tabelle e dati in MyISAM, <b>senza</b> chiavi esterne). I due script sono salvati nel repository; il test
 * {@code BibliotecaFixtureTest} controlla che coincidano con ciò che questo generatore produce.
 * Rigenerarli: eseguire {@link #main} dalla radice del progetto.
 *
 * <p>Dati: 20 editori, 50 autori, 200 libri, 100 soci, 500 prestiti, con accenti, apostrofi, emoji e NULL. Gli
 * {@code id} sono scritti esplicitamente: dopo il caricamento ogni {@code AUTO_INCREMENT} vale max(id) + 1 su
 * entrambi i server. Script senza {@code CREATE DATABASE}/{@code USE}: si caricano dentro un catalogo di test.
 * Regole di {@code TestCatalog.runScript}: «;» a fine riga chiude l'istruzione, «--» a inizio riga è un commento.
 */
public final class BibliotecaFixture {

    public static final String INNODB_FILE = "biblioteca.sql";
    public static final String MYISAM_FILE = "biblioteca_myisam.sql";

    public static final int EDITORI = 20;
    public static final int AUTORI = 50;
    public static final int LIBRI = 200;
    public static final int SOCI = 100;
    public static final int PRESTITI = 500;

    private static final String TABLE_OPTIONS = " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    private static final String[] EDITORI_NOMI = {"Einaudi", "Mondadori", "Feltrinelli", "Adelphi", "Laterza",
        "Il Mulino", "Sellerio", "Bompiani", "Garzanti", "Zanichelli", "Rizzoli", "L'Orma", "Neri Pozza", "Marsilio",
        "Guanda", "Longanesi", "E/O", "Minimum Fax", "Iperborea", "Città Nuova"};
    private static final String[] CITTA = {"Torino", "Milano", "Roma", "Bologna", "Palermo", "Firenze", "Forlì",
        "Napoli", "Venezia", "L'Aquila"};
    private static final String[] AUTORI_COGNOMI = {"Manzoni", "D'Annunzio", "Pirandello", "Verga", "Levi",
        "Calvino", "Morante", "Ginzburg", "Pavese", "Sciascia", "Deledda", "Eco", "Tabucchi", "Buzzati", "Fenoglio",
        "Márquez", "Pérez-Reverte", "Dostoevskij", "Tolstoj", "Čechov", "Hugo", "Flaubert", "Proust", "Camus",
        "Ōe", "Murakami", "Kafka", "Mann", "Böll", "Grass", "Woolf", "Austen", "Dickens", "O'Connor", "Joyce",
        "Borges", "Cortázar", "Saramago", "Pessoa", "Neruda", "Achebe", "Adichie", "Lessing", "Morrison", "Atwood",
        "Munro", "Ishiguro", "Pamuk", "Szymborska", "Ferrante"};
    private static final String[] AUTORI_NOMI = {"Alessandro", "Gabriele", "Luigi", "Giovanni", "Primo", "Italo",
        "Elsa", "Natalia", "Cesare", "Leonardo", "Grazia", "Umberto", "Antonio", "Dino", "Beppe", "Gabriel",
        "Arturo", "Fëdor", "Lev", "Anton", "Victor", "Gustave", "Marcel", "Albert", "Kenzaburō", "Haruki", "Franz",
        "Thomas", "Heinrich", "Günter", "Virginia", "Jane", "Charles", "Flannery", "James", "Jorge Luis", "Julio",
        "José", "Fernando", "Pablo", "Chinua", "Chimamanda", "Doris", "Toni", "Margaret", "Alice", "Kazuo", "Orhan",
        "Wisława", "Elena"};
    private static final String[] NAZIONALITA = {"italiana", "italiana", "italiana", "italiana", "italiana",
        "italiana", "italiana", "italiana", "italiana", "italiana", "italiana", "italiana", "italiana", "italiana",
        "italiana", "colombiana", "spagnola", "russa", "russa", "russa", "francese", "francese", "francese",
        "francese", "giapponese", "giapponese", "ceca", "tedesca", "tedesca", "tedesca", "inglese", "inglese",
        "inglese", "statunitense", "irlandese", "argentina", "argentina", "portoghese", "portoghese", "cilena",
        "nigeriana", "nigeriana", "britannica", "statunitense", "canadese", "canadese", "britannica", "turca",
        "polacca", "italiana"};
    private static final String[] TITOLI_A = {"Il giardino", "La casa", "L'isola", "Il viaggio", "La notte",
        "Il silenzio", "La memoria", "L'ultimo inverno", "Il segreto", "La città", "Il fiume", "L'ombra",
        "La lettera", "Il ritorno", "La luna", "Il gatto 🐱", "La stanza", "Il mare 🌊", "La strada", "L'attesa"};
    private static final String[] TITOLI_B = {"dei ricordi", "di Anna", "sul lago", "senza nome", "d'autunno",
        "degli specchi", "delle voci", "tra le nuvole", "di sabbia", "all'alba"};
    private static final String[] SOCI_COGNOMI = {"Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano",
        "Colombo", "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "De Luca", "Mancini", "Costa", "Giordano",
        "Rizzo", "Lombardi", "Moretti", "D'Amico", "Dell'Acqua", "Fabbri", "Barbieri", "Nicolò"};
    private static final String[] SOCI_NOMI = {"Giulia", "Francesco", "Sofia", "Alessandro", "Aurora", "Lorenzo",
        "Ginevra", "Mattia", "Alice", "Leonardo", "Emma", "Riccardo", "Giorgia", "Tommaso", "Beatrice", "Niccolò",
        "Zoë 😀", "Andrea", "Chiara", "Gabriele"};

    private BibliotecaFixture() {
    }

    /** Scrive i due script nella cartella {@code it-tests/fixtures} del progetto. */
    public static void main(String[] args) throws IOException {
        Path dir = TestResults.projectRoot().resolve("it-tests").resolve("fixtures");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(INNODB_FILE), script(true), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(MYISAM_FILE), script(false), StandardCharsets.UTF_8);
    }

    /** Il testo dello script: InnoDB con chiavi esterne ({@code true}) o MyISAM senza ({@code false}). */
    public static String script(boolean innodb) {
        String engine = innodb ? "InnoDB" : "MyISAM";
        StringBuilder sql = new StringBuilder();
        sql.append("-- RamaSQL Client - schema di prova canonico «biblioteca»")
                .append(innodb ? " (InnoDB, con chiavi esterne)" : " in variante MyISAM, SENZA chiavi esterne")
                .append(".\n")
                .append("-- GENERATO da it-tests/src/test/java/it/ramasql/it/fixtures/BibliotecaFixture.java: non modificare a mano.\n")
                .append("-- Nessun CREATE DATABASE / USE: si carica dentro un catalogo di test (ramasql_test_*).\n\n");

        sql.append("CREATE TABLE editori (\n")
                .append("  id INT UNSIGNED NOT NULL AUTO_INCREMENT,\n")
                .append("  nome VARCHAR(80) NOT NULL,\n")
                .append("  citta VARCHAR(60) NULL,\n")
                .append("  PRIMARY KEY (id),\n")
                .append("  UNIQUE KEY uq_editori_nome (nome)\n")
                .append(") ENGINE=").append(engine).append(TABLE_OPTIONS).append(";\n\n");

        sql.append("CREATE TABLE autori (\n")
                .append("  id INT UNSIGNED NOT NULL AUTO_INCREMENT,\n")
                .append("  cognome VARCHAR(60) NOT NULL,\n")
                .append("  nome VARCHAR(60) NOT NULL,\n")
                .append("  nazionalita VARCHAR(40) NOT NULL DEFAULT 'italiana',\n")
                .append("  PRIMARY KEY (id),\n")
                .append("  KEY ix_autori_cognome (cognome, nome)\n")
                .append(") ENGINE=").append(engine).append(TABLE_OPTIONS).append(";\n\n");

        sql.append("CREATE TABLE libri (\n")
                .append("  id INT UNSIGNED NOT NULL AUTO_INCREMENT,\n")
                .append("  titolo VARCHAR(150) NOT NULL,\n")
                .append("  isbn CHAR(13) NULL,\n")
                .append("  anno SMALLINT UNSIGNED NULL,\n")
                .append("  prezzo DECIMAL(6,2) NOT NULL DEFAULT 0.00,\n")
                .append("  id_editore INT UNSIGNED NULL,\n")
                .append("  PRIMARY KEY (id),\n")
                .append("  UNIQUE KEY uq_libri_isbn (isbn),\n")
                .append("  KEY ix_libri_editore (id_editore)");
        if (innodb) {
            sql.append(",\n  CONSTRAINT fk_libri_editori FOREIGN KEY (id_editore) REFERENCES editori (id)"
                    + " ON DELETE RESTRICT ON UPDATE RESTRICT");
        }
        sql.append("\n) ENGINE=").append(engine).append(TABLE_OPTIONS).append(" COMMENT='Catalogo dei libri';\n\n");

        sql.append("CREATE TABLE libri_autori (\n")
                .append("  id_libro INT UNSIGNED NOT NULL,\n")
                .append("  id_autore INT UNSIGNED NOT NULL,\n")
                .append("  PRIMARY KEY (id_libro, id_autore),\n")
                .append("  KEY ix_libri_autori_autore (id_autore)");
        if (innodb) {
            sql.append(",\n  CONSTRAINT fk_libri_autori_libri FOREIGN KEY (id_libro) REFERENCES libri (id)"
                    + " ON DELETE CASCADE ON UPDATE CASCADE")
                    .append(",\n  CONSTRAINT fk_libri_autori_autori FOREIGN KEY (id_autore) REFERENCES autori (id)"
                            + " ON DELETE CASCADE ON UPDATE CASCADE");
        }
        sql.append("\n) ENGINE=").append(engine).append(TABLE_OPTIONS).append(";\n\n");

        sql.append("CREATE TABLE soci (\n")
                .append("  id INT UNSIGNED NOT NULL AUTO_INCREMENT,\n")
                .append("  tessera CHAR(8) NOT NULL COMMENT 'numero della tessera (T e 7 cifre)',\n")
                .append("  cognome VARCHAR(60) NOT NULL,\n")
                .append("  nome VARCHAR(60) NOT NULL,\n")
                .append("  email VARCHAR(120) NULL,\n")
                .append("  nato_il DATE NULL,\n")
                .append("  PRIMARY KEY (id),\n")
                .append("  UNIQUE KEY uq_soci_tessera (tessera)\n")
                .append(") ENGINE=").append(engine).append(TABLE_OPTIONS).append(";\n\n");

        sql.append("CREATE TABLE prestiti (\n")
                .append("  id INT UNSIGNED NOT NULL AUTO_INCREMENT,\n")
                .append("  id_libro INT UNSIGNED NOT NULL,\n")
                .append("  id_socio INT UNSIGNED NOT NULL,\n")
                .append("  data_prestito DATE NOT NULL,\n")
                .append("  data_reso DATE NULL DEFAULT NULL,\n")
                .append("  PRIMARY KEY (id),\n")
                .append("  KEY ix_prestiti_libro (id_libro),\n")
                .append("  KEY ix_prestiti_socio (id_socio)");
        if (innodb) {
            sql.append(",\n  CONSTRAINT fk_prestiti_libri FOREIGN KEY (id_libro) REFERENCES libri (id)"
                    + " ON DELETE RESTRICT ON UPDATE RESTRICT")
                    .append(",\n  CONSTRAINT fk_prestiti_soci FOREIGN KEY (id_socio) REFERENCES soci (id)"
                            + " ON DELETE RESTRICT ON UPDATE RESTRICT");
        }
        sql.append("\n) ENGINE=").append(engine).append(TABLE_OPTIONS).append(";\n\n");

        sql.append("CREATE VIEW v_prestiti_aperti AS\n")
                .append("  SELECT p.id, s.cognome, s.nome, l.titolo, p.data_prestito\n")
                .append("  FROM prestiti p\n")
                .append("  JOIN soci s ON s.id = p.id_socio\n")
                .append("  JOIN libri l ON l.id = p.id_libro\n")
                .append("  WHERE p.data_reso IS NULL;\n\n");
        sql.append("CREATE VIEW v_libri_editori AS\n")
                .append("  SELECT l.id, l.titolo, l.anno, e.nome AS editore\n")
                .append("  FROM libri l\n")
                .append("  LEFT JOIN editori e ON e.id = l.id_editore;\n\n");

        insert(sql, "editori", "id, nome, citta", EDITORI, i -> List.of(
                String.valueOf(i),
                SqlLiterals.string(EDITORI_NOMI[i - 1]),
                i % 7 == 0 ? "NULL" : SqlLiterals.string(CITTA[(i * 3) % CITTA.length])));

        insert(sql, "autori", "id, cognome, nome, nazionalita", AUTORI, i -> List.of(
                String.valueOf(i),
                SqlLiterals.string(AUTORI_COGNOMI[i - 1]),
                SqlLiterals.string(AUTORI_NOMI[i - 1]),
                SqlLiterals.string(NAZIONALITA[i - 1])));

        insert(sql, "libri", "id, titolo, isbn, anno, prezzo, id_editore", LIBRI, i -> List.of(
                String.valueOf(i),
                SqlLiterals.string(TITOLI_A[(i - 1) % TITOLI_A.length] + " " + TITOLI_B[((i - 1) / TITOLI_A.length)
                        % TITOLI_B.length] + (i > TITOLI_A.length * TITOLI_B.length ? " (vol. 2)" : "")),
                i % 11 == 0 ? "NULL" : SqlLiterals.string(String.format(Locale.ROOT, "978%010d", 8800000000L + i * 7919L)),
                i % 13 == 0 ? "NULL" : String.valueOf(1850 + (i * 37) % 175),
                String.format(Locale.ROOT, "%d.%02d", 5 + (i * 17) % 40, (i * 29) % 100),
                i % 9 == 0 ? "NULL" : String.valueOf(1 + (i * 3) % EDITORI)));

        List<List<String>> pairs = new ArrayList<>();
        for (int libro = 1; libro <= LIBRI; libro++) {
            int first = 1 + (libro * 7) % AUTORI;
            pairs.add(List.of(String.valueOf(libro), String.valueOf(first)));
            int second = 1 + (libro * 13) % AUTORI;
            if (libro % 5 == 0 && second != first) {
                pairs.add(List.of(String.valueOf(libro), String.valueOf(second)));
            }
        }
        insert(sql, "libri_autori", "id_libro, id_autore", pairs.size(), i -> pairs.get(i - 1));

        insert(sql, "soci", "id, tessera, cognome, nome, email, nato_il", SOCI, i -> {
            String cognome = SOCI_COGNOMI[(i * 7) % SOCI_COGNOMI.length];
            String nome = SOCI_NOMI[(i * 3) % SOCI_NOMI.length];
            String email = i % 6 == 0 ? null : (nome.split(" ")[0] + "." + cognome).toLowerCase(Locale.ROOT)
                    .replace("'", "").replace(" ", "").replace("ò", "o").replace("ë", "e") + i + "@esempio.it";
            LocalDate nato = LocalDate.of(1950, 1, 1).plusDays((i * 211L) % 20000);
            return List.of(
                    String.valueOf(i),
                    SqlLiterals.string(String.format(Locale.ROOT, "T%07d", 1000 + i * 37)),
                    SqlLiterals.string(cognome),
                    SqlLiterals.string(nome),
                    SqlLiterals.string(email),
                    i % 10 == 0 ? "NULL" : SqlLiterals.date(nato));
        });

        insert(sql, "prestiti", "id, id_libro, id_socio, data_prestito, data_reso", PRESTITI, k -> {
            LocalDate preso = LocalDate.of(2025, 1, 1).plusDays((k * 3L) % 600);
            return List.of(
                    String.valueOf(k),
                    String.valueOf(1 + (k * 37) % LIBRI),
                    String.valueOf(1 + (k * 53) % SOCI),
                    SqlLiterals.date(preso),
                    k % 7 == 0 ? "NULL" : SqlLiterals.date(preso.plusDays(1 + k % 30)));
        });
        return sql.toString();
    }

    /** Righe di {@code libri_autori} generate (per i controlli dei test). */
    public static int libriAutoriRows() {
        int n = 0;
        for (int libro = 1; libro <= LIBRI; libro++) {
            n += libro % 5 == 0 && (1 + (libro * 13) % AUTORI) != (1 + (libro * 7) % AUTORI) ? 2 : 1;
        }
        return n;
    }

    private static void insert(StringBuilder sql, String table, String columns, int rows,
            IntFunction<List<String>> row) {
        sql.append("INSERT INTO ").append(table).append(" (").append(columns).append(") VALUES\n");
        for (int i = 1; i <= rows; i++) {
            sql.append("  (").append(String.join(", ", row.apply(i))).append(')')
                    .append(i == rows ? ";\n\n" : ",\n");
        }
    }
}
