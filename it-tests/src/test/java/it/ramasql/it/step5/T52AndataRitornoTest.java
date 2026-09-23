/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * T5.2 — andata e ritorno di {@link TableDiff} sui due server, per 39 casi di T5.1 (stessi modelli e stesso SQL
 * atteso dei test U di {@code TableDiffColumnsTest}): crea la tabella di partenza con {@code diff(null, originale)},
 * applica con {@link SqlExecutor} l'{@code ALTER} di {@code diff(originale, modificata)}, rilegge con
 * {@code MetadataReader} e controlla che
 * <ol>
 *   <li>la tabella di partenza riletta sia quella chiesta (il {@code CREATE} è giusto);</li>
 *   <li>partendo dalla tabella <i>riletta</i> (come fa l'editor) il generatore produca lo stesso SQL;</li>
 *   <li>la tabella riletta dopo l'{@code ALTER} sia <b>uguale</b> ({@code equals}) a quella attesa;</li>
 *   <li>{@code TableDiff.diff(riletta, attesa)} dia <b>zero istruzioni</b>.</li>
 * </ol>
 *
 * <h2>Dal modello chiesto al modello atteso (solo ciò che decide il server, regole scritte)</h2>
 * Il modello modificato dell'editor lascia alcune scelte al server; per il confronto con {@code equals} l'atteso
 * si ottiene dal modello chiesto con queste sole regole (vedi {@link #atteso}):
 * <ul>
 *   <li><b>R1</b> catalogo = quello di prova (l'SQL non è qualificato: vale il catalogo della sessione).</li>
 *   <li><b>R2</b> colonne numerate 1…n (il server le numera; nel modello le colonne nuove hanno posizione 0).</li>
 *   <li><b>R3</b> engine, charset, collation, AUTO_INCREMENT di tabella <i>non specificati</i> ({@code null}) = quelli
 *       scelti dal server (predefiniti del catalogo/server): si prendono dal riletto. Se sono specificati, si
 *       confrontano.</li>
 *   <li><b>R4</b> charset/collation di colonna uguali a quelli della tabella = {@code null} («ereditati», convenzione
 *       del lettore); charset di colonna dato senza collation = collation predefinita del server per quel charset.</li>
 *   <li><b>R5</b> colonna annullabile senza default = {@code DEFAULT NULL} (i server non li distinguono); NOT NULL con
 *       {@code DEFAULT NULL} = nessun default (il generatore non lo scrive).</li>
 *   <li><b>R6</b> default numerici confrontati per valore (il server scrive {@code 0.00} per {@code 0} su un DECIMAL).</li>
 *   <li><b>R7</b> {@code current_timestamp()}, {@code NOW()}, {@code CURRENT_TIMESTAMP(0)} = {@code CURRENT_TIMESTAMP}
 *       (forma canonica, la stessa del lettore); parentesi esterne tolte.</li>
 *   <li><b>R8</b> indici: PRIMARY per prima, poi per nome; chiavi esterne per nome (ordine del lettore).</li>
 * </ul>
 */
@Tag("step5")
@Tag("it")
class T52AndataRitornoTest {

    private static final Pattern NOW = Pattern.compile(
            "(?i)(CURRENT_TIMESTAMP|NOW|LOCALTIME|LOCALTIMESTAMP)(\\s*\\(\\s*(\\d*)\\s*\\))?");

    /** Evidenze per server, in ordine di caso. */
    private static final Map<ItServers, Map<Integer, String>> EVIDENZE = new EnumMap<>(ItServers.class);

    // ---------------------------------------------------------------- modelli (gli stessi dei test U di T5.1)

    static final TableDef LIBRI = TableDef.of(null, "libri")
            .addColumn(ColumnDef.of("id", "INT").withUnsigned(true).notNull().withAutoIncrement(true))
            .addColumn(ColumnDef.of("titolo", "VARCHAR", "100").notNull())
            .addColumn(ColumnDef.of("prezzo", "DECIMAL", "8,2"))
            .addColumn(ColumnDef.of("stato", "ENUM", "'nuovo','usato'").notNull()
                    .withDefault(ColumnDefault.literal("nuovo")))
            .addColumn(ColumnDef.of("note", "TEXT"))
            .addColumn(ColumnDef.of("creato", "DATETIME").notNull().withDefault(ColumnDefault.CURRENT_TIMESTAMP))
            .withPrimaryKey("id")
            .withEngine("InnoDB")
            .withCharset("utf8mb4", "utf8mb4_general_ci")
            .withOrdinalPositions();

    static final TableDef LIBRI_AUTORI = TableDef.of(null, "libri_autori")
            .addColumn(ColumnDef.of("id_libro", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("id_autore", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("ruolo", "VARCHAR", "30").notNull().withDefault(ColumnDefault.literal("autore")))
            .withPrimaryKey("id_libro", "id_autore")
            .withEngine("InnoDB")
            .withOrdinalPositions();

    static final TableDef SENZA_PK = TableDef.of(null, "t")
            .addColumn(ColumnDef.of("a", "INT").notNull())
            .addColumn(ColumnDef.of("b", "INT").notNull())
            .withOrdinalPositions();

    static final TableDef MYISAM = TableDef.of(null, "registro")
            .addColumn(ColumnDef.of("riga", "VARCHAR", "200"))
            .withEngine("MyISAM").withOrdinalPositions();

    static final TableDef ORDINE = TableDef.of(null, "ordine dettagli").addColumn(ColumnDef.of("order", "INT").notNull())
            .addColumn(ColumnDef.of("group by", "DATE")).withOrdinalPositions();

    /**
     * Un caso di T5.1: modelli e SQL atteso (quello dei test U).
     *
     * @param attese SQL atteso di {@code diff(originale, modificata)}, identico al test U
     */
    record Caso(String nome, TableDef originale, TableDef modificata, List<String> attese) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private static Caso caso(String nome, TableDef originale, TableDef modificata, String... attese) {
        return new Caso(nome, originale, modificata, List.of(attese));
    }

    static List<Caso> casi() {
        return List.of(
                // ------------------------------------------------------------ tabella nuova
                caso("nuova: tabella completa", null, LIBRI, """
                        CREATE TABLE `libri` (
                          `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
                          `titolo` VARCHAR(100) NOT NULL,
                          `prezzo` DECIMAL(8,2) NULL,
                          `stato` ENUM('nuovo','usato') NOT NULL DEFAULT 'nuovo',
                          `note` TEXT NULL,
                          `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"""),
                caso("nuova: nomi con spazi e parole riservate", null,
                        TableDef.of(null, "ordine dettagli")
                                .addColumn(ColumnDef.of("order", "INT").notNull())
                                .addColumn(ColumnDef.of("select", "VARCHAR", "10"))
                                .addColumn(ColumnDef.of("group by", "DATE"))
                                .withPrimaryKey("order"), """
                        CREATE TABLE `ordine dettagli` (
                          `order` INT NOT NULL,
                          `select` VARCHAR(10) NULL,
                          `group by` DATE NULL,
                          PRIMARY KEY (`order`)
                        )"""),
                caso("nuova: MyISAM, commenti con apostrofo, AUTO_INCREMENT iniziale", null,
                        TableDef.of(null, "registro")
                                .addColumn(ColumnDef.of("id", "INT").notNull().withAutoIncrement(true)
                                        .withComment("l'identificativo"))
                                .withPrimaryKey("id").withEngine("MyISAM").withAutoIncrementStart(1000L)
                                .withComment("Registro dell'aula"), """
                        CREATE TABLE `registro` (
                          `id` INT NOT NULL AUTO_INCREMENT COMMENT 'l''identificativo',
                          PRIMARY KEY (`id`)
                        ) ENGINE=MyISAM AUTO_INCREMENT=1000 COMMENT='Registro dell''aula'"""),
                caso("nuova: tutti i tipi di default", null,
                        TableDef.of(null, "eventi")
                                .addColumn(ColumnDef.of("id", "CHAR", "36").notNull()
                                        .withDefault(ColumnDefault.expression("uuid()")))
                                .addColumn(ColumnDef.of("titolo", "VARCHAR", "50").notNull()
                                        .withDefault(ColumnDefault.literal("")))
                                .addColumn(ColumnDef.of("posti", "INT").notNull().withDefault(ColumnDefault.literal("0")))
                                .addColumn(ColumnDef.of("sala", "VARCHAR", "20").withDefault(ColumnDefault.NULL_VALUE))
                                .addColumn(ColumnDef.of("creato", "DATETIME").notNull()
                                        .withDefault(ColumnDefault.expression("current_timestamp()")))
                                .addColumn(ColumnDef.of("modificato", "TIMESTAMP").notNull()
                                        .withDefault(ColumnDefault.expression("NOW()"))
                                        .withOnUpdate("current_timestamp()"))
                                .addColumn(ColumnDef.of("preciso", "DATETIME", "6").notNull()
                                        .withDefault(ColumnDefault.expression("current_timestamp(6)")))
                                .withPrimaryKey("id").withEngine("InnoDB"), """
                        CREATE TABLE `eventi` (
                          `id` CHAR(36) NOT NULL DEFAULT (uuid()),
                          `titolo` VARCHAR(50) NOT NULL DEFAULT '',
                          `posti` INT NOT NULL DEFAULT 0,
                          `sala` VARCHAR(20) NULL DEFAULT NULL,
                          `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `modificato` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          `preciso` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                          PRIMARY KEY (`id`)
                        ) ENGINE=InnoDB"""),
                caso("nuova: charset e collation di colonna", null,
                        TableDef.of(null, "codici")
                                .addColumn(ColumnDef.of("codice", "VARCHAR", "10").notNull()
                                        .withCharset("utf8mb4", "utf8mb4_bin"))
                                .addColumn(ColumnDef.of("vecchio", "VARCHAR", "10")
                                        .withCharset("latin1", "latin1_swedish_ci"))
                                .addColumn(ColumnDef.of("normale", "VARCHAR", "10")
                                        .withCharset("utf8mb4", "utf8mb4_general_ci"))
                                .withCharset("utf8mb4", "utf8mb4_general_ci"), """
                        CREATE TABLE `codici` (
                          `codice` VARCHAR(10) COLLATE utf8mb4_bin NOT NULL,
                          `vecchio` VARCHAR(10) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL,
                          `normale` VARCHAR(10) NULL
                        ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"""),

                // ------------------------------------------------------------ aggiungi / rimuovi / rinomina
                caso("aggiungi colonna in mezzo", LIBRI, LIBRI.addColumn(2, ColumnDef.of("sottotitolo", "VARCHAR", "200")),
                        "ALTER TABLE `libri` ADD COLUMN `sottotitolo` VARCHAR(200) NULL AFTER `titolo`"),
                caso("aggiungi colonna con default e commento", LIBRI,
                        LIBRI.addColumn(ColumnDef.of("copie", "INT").notNull().withDefault(ColumnDefault.literal("1"))
                                .withComment("copie possedute")),
                        "ALTER TABLE `libri` ADD COLUMN `copie` INT NOT NULL DEFAULT 1 COMMENT 'copie possedute'"),
                caso("aggiungi colonna con nome riservato dopo una con spazi", ORDINE,
                        ORDINE.addColumn(1, ColumnDef.of("key", "INT")),
                        "ALTER TABLE `ordine dettagli` ADD COLUMN `key` INT NULL AFTER `order`"),
                caso("rimuovi due colonne", LIBRI, LIBRI.removeColumn("note").removeColumn("prezzo"), """
                        ALTER TABLE `libri`
                          DROP COLUMN `prezzo`,
                          DROP COLUMN `note`"""),
                caso("rinomina colonna: CHANGE COLUMN con la definizione completa", LIBRI,
                        LIBRI.changeColumn("titolo", c -> c.withName("titolo_libro")),
                        "ALTER TABLE `libri` CHANGE COLUMN `titolo` `titolo_libro` VARCHAR(100) NOT NULL"),
                caso("rinomina verso un nome riservato con spazi", LIBRI,
                        LIBRI.changeColumn("stato", c -> c.withName("order by")),
                        "ALTER TABLE `libri` CHANGE COLUMN `stato` `order by` ENUM('nuovo','usato') NOT NULL DEFAULT 'nuovo'"),
                caso("rinomina solo maiuscole/minuscole", LIBRI, LIBRI.changeColumn("titolo", c -> c.withName("Titolo")),
                        "ALTER TABLE `libri` CHANGE COLUMN `titolo` `Titolo` VARCHAR(100) NOT NULL"),

                // ------------------------------------------------------------ tipo, lunghezza, NULL
                caso("cambio tipo INT → BIGINT", LIBRI, LIBRI.changeColumn("id", c -> c.withType("BIGINT", null)),
                        "ALTER TABLE `libri` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT"),
                caso("cambio lunghezza VARCHAR", LIBRI, LIBRI.changeColumn("titolo", c -> c.withTypeArgs("200")),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(200) NOT NULL"),
                caso("nuovo valore di un ENUM", LIBRI,
                        LIBRI.changeColumn("stato", c -> c.withTypeArgs("'nuovo','usato','d''epoca'")),
                        "ALTER TABLE `libri` MODIFY COLUMN `stato` ENUM('nuovo','usato','d''epoca') NOT NULL DEFAULT 'nuovo'"),
                caso("da NULL a NOT NULL", LIBRI, LIBRI.changeColumn("prezzo", ColumnDef::notNull),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NOT NULL"),

                // ------------------------------------------------------------ default
                caso("default stringa vuota", LIBRI, LIBRI.changeColumn("titolo", c -> c.withDefault(ColumnDefault.literal(""))),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(100) NOT NULL DEFAULT ''"),
                caso("da default letterale a DEFAULT NULL",
                        LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.literal("9.99"))),
                        LIBRI.changeColumn("prezzo", c -> c.withDefault(ColumnDefault.NULL_VALUE)),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NULL DEFAULT NULL"),
                caso("da letterale a CURRENT_TIMESTAMP",
                        LIBRI.changeColumn("creato", c -> c.withDefault(ColumnDefault.literal("2000-01-01 00:00:00"))), LIBRI,
                        "ALTER TABLE `libri` MODIFY COLUMN `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"),
                caso("aggiunta di ON UPDATE CURRENT_TIMESTAMP", LIBRI,
                        LIBRI.changeColumn("creato", c -> c.withOnUpdate("CURRENT_TIMESTAMP")),
                        "ALTER TABLE `libri` MODIFY COLUMN `creato` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"),

                // ------------------------------------------------------------ AI, UNSIGNED, commento, charset
                caso("togli AUTO_INCREMENT", LIBRI, LIBRI.changeColumn("id", c -> c.withAutoIncrement(false)),
                        "ALTER TABLE `libri` MODIFY COLUMN `id` INT UNSIGNED NOT NULL"),
                caso("togli UNSIGNED", LIBRI, LIBRI.changeColumn("id", c -> c.withUnsigned(false)),
                        "ALTER TABLE `libri` MODIFY COLUMN `id` INT NOT NULL AUTO_INCREMENT"),
                caso("metti UNSIGNED", SENZA_PK, SENZA_PK.changeColumn("b", c -> c.withUnsigned(true)),
                        "ALTER TABLE `t` MODIFY COLUMN `b` INT UNSIGNED NOT NULL"),
                caso("aggiungi commento di colonna", LIBRI,
                        LIBRI.changeColumn("prezzo", c -> c.withComment("in euro, IVA inclusa: l'importo")),
                        "ALTER TABLE `libri` MODIFY COLUMN `prezzo` DECIMAL(8,2) NULL COMMENT 'in euro, IVA inclusa: l''importo'"),
                caso("charset di colonna", LIBRI,
                        LIBRI.changeColumn("titolo", c -> c.withCharset("latin1", "latin1_swedish_ci")),
                        "ALTER TABLE `libri` MODIFY COLUMN `titolo` VARCHAR(100) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL"),

                // ------------------------------------------------------------ chiave primaria
                caso("PK semplice aggiunta", SENZA_PK, SENZA_PK.withPrimaryKey("a"),
                        "ALTER TABLE `t` ADD PRIMARY KEY (`a`)"),
                caso("PK composta aggiunta", SENZA_PK, SENZA_PK.withPrimaryKey("a", "b"),
                        "ALTER TABLE `t` ADD PRIMARY KEY (`a`, `b`)"),
                caso("PK tolta insieme all'AUTO_INCREMENT che la richiede", LIBRI,
                        LIBRI.withPrimaryKey().changeColumn("id", c -> c.withAutoIncrement(false)), """
                        ALTER TABLE `libri`
                          DROP PRIMARY KEY,
                          MODIFY COLUMN `id` INT UNSIGNED NOT NULL"""),
                caso("PK composta: ordine delle colonne cambiato", LIBRI_AUTORI,
                        LIBRI_AUTORI.withPrimaryKey("id_autore", "id_libro"), """
                        ALTER TABLE `libri_autori`
                          DROP PRIMARY KEY,
                          ADD PRIMARY KEY (`id_autore`, `id_libro`)"""),
                caso("PK da composta a semplice", LIBRI_AUTORI, LIBRI_AUTORI.withPrimaryKey("id_libro"), """
                        ALTER TABLE `libri_autori`
                          DROP PRIMARY KEY,
                          ADD PRIMARY KEY (`id_libro`)"""),
                caso("nuova colonna AUTO_INCREMENT che diventa PK", SENZA_PK,
                        SENZA_PK.addColumn(0, ColumnDef.of("id", "INT").notNull().withAutoIncrement(true))
                                .withPrimaryKey("id"), """
                        ALTER TABLE `t`
                          ADD COLUMN `id` INT NOT NULL AUTO_INCREMENT FIRST,
                          ADD PRIMARY KEY (`id`)"""),

                // ------------------------------------------------------------ opzioni di tabella
                caso("engine InnoDB → MyISAM", LIBRI, LIBRI.withEngine("MyISAM"), "ALTER TABLE `libri` ENGINE=MyISAM"),
                caso("engine MyISAM → InnoDB", MYISAM, MYISAM.withEngine("InnoDB"), "ALTER TABLE `registro` ENGINE=InnoDB"),
                // Il cambio di charset di tabella cambia il predefinito per le colonne future e non converte quelle
                // esistenti (TableDiff, come Workbench): nel modello modificato le colonne di testo esistenti
                // restano quindi esplicitamente in utf8mb4_general_ci; l'SQL è lo stesso del test U.
                caso("charset e collation di tabella", LIBRI, conColonneFissate(LIBRI)
                                .withCharset("latin1", "latin1_swedish_ci"),
                        "ALTER TABLE `libri` DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci"),
                caso("commento di tabella", LIBRI, LIBRI.withComment("Catalogo dell'istituto"),
                        "ALTER TABLE `libri` COMMENT='Catalogo dell''istituto'"),
                caso("AUTO_INCREMENT iniziale", LIBRI, LIBRI.withAutoIncrementStart(1000L),
                        "ALTER TABLE `libri` AUTO_INCREMENT=1000"),

                // ------------------------------------------------------------ rinomina tabella e combinazioni
                caso("rinomina tabella + nuova colonna: prima RENAME, poi ALTER sul nome nuovo", LIBRI,
                        LIBRI.withName("volumi").addColumn(ColumnDef.of("isbn", "CHAR", "13")),
                        "RENAME TABLE `libri` TO `volumi`",
                        "ALTER TABLE `volumi` ADD COLUMN `isbn` CHAR(13) NULL"),
                caso("combinazione: DROP, MODIFY, ADD, engine e commento in un solo ALTER", LIBRI,
                        LIBRI.removeColumn("note").changeColumn("titolo", c -> c.withTypeArgs("200"))
                                .addColumn(2, ColumnDef.of("isbn", "CHAR", "13"))
                                .withEngine("MyISAM").withComment("Catalogo"), """
                        ALTER TABLE `libri`
                          DROP COLUMN `note`,
                          MODIFY COLUMN `titolo` VARCHAR(200) NOT NULL,
                          ADD COLUMN `isbn` CHAR(13) NULL AFTER `titolo`,
                          ENGINE=MyISAM,
                          COMMENT='Catalogo'"""),
                caso("combinazione: rinomina, NULL, default e PK in un solo ALTER", SENZA_PK,
                        SENZA_PK.changeColumn("a", c -> c.withName("codice").withType("VARCHAR", "8"))
                                .changeColumn("b", c -> c.withNullable(true).withDefault(ColumnDefault.literal("5")))
                                .withPrimaryKey("codice"), """
                        ALTER TABLE `t`
                          CHANGE COLUMN `a` `codice` VARCHAR(8) NOT NULL,
                          MODIFY COLUMN `b` INT NULL DEFAULT 5,
                          ADD PRIMARY KEY (`codice`)"""));
    }

    /** Le colonne di testo con il charset/collation della tabella scritti per esteso (vedi il caso del charset). */
    private static TableDef conColonneFissate(TableDef t) {
        List<ColumnDef> cols = new ArrayList<>();
        for (ColumnDef c : t.columns()) {
            boolean testo = SqlTypes.isText(c.dataType()) || c.dataType().equals("ENUM") || c.dataType().equals("SET");
            cols.add(testo && c.charset() == null ? c.withCharset(t.charset(), t.collation()) : c);
        }
        return t.withColumns(cols);
    }

    static Stream<Arguments> casiPerServer() {
        List<Arguments> out = new ArrayList<>();
        List<Caso> casi = casi();
        for (ItServers server : ItServers.values()) {
            for (int i = 0; i < casi.size(); i++) {
                out.add(arguments(server, i + 1, casi.get(i)));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "[{0}] caso {1}: {2}")
    @MethodSource("casiPerServer")
    void andataERitorno(ItServers server, int numero, Caso caso) throws Exception {
        StringBuilder ev = new StringBuilder();
        ev.append("## Caso ").append(numero).append(" — ").append(caso.nome()).append('\n');
        try {
            eseguiCaso(server, caso, ev);
            ev.append("ESITO: OK\n");
        } catch (Throwable t) {
            ev.append("ESITO: FALLITO — ").append(t.getMessage()).append('\n');
            throw t;
        } finally {
            synchronized (EVIDENZE) {
                EVIDENZE.computeIfAbsent(server, k -> new TreeMap<>()).put(numero, ev.toString());
            }
        }
    }

    private void eseguiCaso(ItServers server, Caso caso, StringBuilder ev) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "t52"); Session session = EditorSupport.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            ServerInfo info = session.serverInfo();
            ev.append("server: ").append(info.displayName()).append('\n');
            TableDef finale;
            List<String> applicate;
            if (caso.originale() == null) {
                applicate = TableDiff.diff(null, caso.modificata(), info);
                assertEquals(caso.attese(), applicate, "SQL del generatore = SQL atteso del test U");
                ev.append("SQL applicato (CREATE):\n").append(EditorSupport.block(applicate)).append('\n');
                EditorSupport.apply(exec, "T5.2 " + caso.nome(), applicate);
                ev.append("esecuzione: OK (").append(applicate.size()).append(" istruzioni)\n");
            } else {
                List<String> create = TableDiff.diff(null, caso.originale(), info);
                ev.append("tabella di partenza:\n").append(EditorSupport.block(create)).append('\n');
                EditorSupport.apply(exec, "T5.2 partenza " + caso.nome(), create);
                TableDef partenza = EditorSupport.reread(session, cat.name(), caso.originale().name());
                assertEquals(atteso(caso.originale(), cat.name(), partenza), partenza,
                        "la tabella di partenza riletta è quella chiesta");
                ev.append("partenza riletta = chiesta: sì\n");

                applicate = TableDiff.diff(caso.originale(), caso.modificata(), info);
                assertEquals(caso.attese(), applicate, "SQL del generatore = SQL atteso del test U");
                List<String> dalRiletto = TableDiff.diff(partenza.withCatalog(null), caso.modificata(), info);
                assertEquals(applicate, dalRiletto, "partendo dal riletto (come l'editor) l'SQL è lo stesso");
                ev.append("SQL applicato (= test U, = generato dal riletto):\n")
                        .append(EditorSupport.block(applicate)).append('\n');
                EditorSupport.apply(exec, "T5.2 " + caso.nome(), applicate);
                ev.append("esecuzione: OK (").append(applicate.size()).append(" istruzioni)\n");
            }
            finale = EditorSupport.reread(session, cat.name(), caso.modificata().name());
            TableDef atteso = atteso(caso.modificata(), cat.name(), finale);
            assertEquals(atteso, finale, "tabella riletta = tabella attesa");
            ev.append("riletta = attesa (equals): sì\n");
            ev.append("riletta: ").append(descrivi(finale)).append('\n');
            List<String> residuo = TableDiff.diff(finale, atteso, info);
            assertEquals(List.of(), residuo, "diff(riletta, attesa) deve essere vuoto");
            assertFalse(residuo.iterator().hasNext());
            ev.append("diff(riletta, attesa): 0 istruzioni\n");
            assertTrue(exec.log().entries().stream().allMatch(e -> e.outcome() == SqlLog.Outcome.OK));
        }
    }

    // ---------------------------------------------------------------- atteso = chiesto + regole R1…R8

    /** Il modello atteso sul server a partire da quello chiesto, con le sole regole R1…R8 della classe. */
    static TableDef atteso(TableDef chiesto, String catalog, TableDef riletto) {
        TableDef t = chiesto.withCatalog(catalog).withOrdinalPositions();                        // R1, R2
        t = t.withEngine(t.engine() != null ? canonicalEngine(t.engine()) : riletto.engine());   // R3
        if (t.charset() == null && t.collation() == null) {
            t = t.withCharset(riletto.charset(), riletto.collation());
        } else if (t.collation() == null) {
            t = t.withCharset(t.charset(), riletto.collation());
        }
        if (t.autoIncrementStart() == null) {
            t = t.withAutoIncrementStart(riletto.autoIncrementStart());
        }
        List<ColumnDef> cols = new ArrayList<>();
        for (ColumnDef c : t.columns()) {
            ColumnDef server = riletto.column(c.name()).orElse(null);
            String charset = c.charset();
            String collation = c.collation();
            if (charset != null && collation == null && server != null) {                           // R4
                collation = server.collation() != null ? server.collation() : t.collation();
            }
            if (charset != null && charset.equalsIgnoreCase(t.charset())) {
                charset = null;
            }
            if (collation != null && collation.equalsIgnoreCase(t.collation())) {
                collation = null;
            }
            c = c.withCharset(charset, collation);
            ColumnDefault d = c.defaultValue();                                                      // R5
            if (!c.autoIncrement() && c.nullable() && d.isNone()) {
                d = ColumnDefault.NULL_VALUE;
            } else if (!c.nullable() && d.kind() == ColumnDefault.Kind.NULL) {
                d = ColumnDefault.NONE;
            }
            if (d.kind() == ColumnDefault.Kind.LITERAL && server != null                           // R6
                    && server.defaultValue().kind() == ColumnDefault.Kind.LITERAL
                    && SqlTypes.isNumeric(c.dataType()) && sameNumber(d.value(), server.defaultValue().value())) {
                d = server.defaultValue();
            }
            if (d.kind() == ColumnDefault.Kind.EXPRESSION) {                                        // R7
                d = ColumnDefault.expression(canonicalExpression(d.value()));
            }
            c = c.withDefault(d).withOnUpdate(c.onUpdate() == null ? null : canonicalExpression(c.onUpdate()));
            cols.add(c);
        }
        t = t.withColumns(cols);
        List<IndexDef> idx = new ArrayList<>(t.indexes());                                         // R8
        idx.sort(Comparator.comparing((IndexDef i) -> !i.isPrimary()).thenComparing(i -> i.name().toLowerCase(Locale.ROOT)));
        List<ForeignKeyDef> fks = new ArrayList<>(t.foreignKeys());
        fks.sort(Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));
        return t.withIndexes(idx).withForeignKeys(fks);
    }

    private static String canonicalEngine(String engine) {
        return engine.equalsIgnoreCase("innodb") ? "InnoDB" : engine.equalsIgnoreCase("myisam") ? "MyISAM" : engine;
    }

    private static boolean sameNumber(String a, String b) {
        try {
            return new BigDecimal(a.trim()).compareTo(new BigDecimal(b.trim())) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String canonicalExpression(String expression) {
        String s = expression.trim();
        while (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        Matcher m = NOW.matcher(s);
        if (m.matches()) {
            String p = m.group(3);
            return "CURRENT_TIMESTAMP" + (p != null && !p.isEmpty() && !p.equals("0") ? "(" + p + ")" : "");
        }
        return s;
    }

    static String descrivi(TableDef t) {
        StringBuilder s = new StringBuilder("`").append(t.name()).append("` ").append(t.engine()).append(' ')
                .append(t.collation()).append(t.autoIncrementStart() == null ? "" : " AI=" + t.autoIncrementStart())
                .append(t.comment().isEmpty() ? "" : " COMMENT='" + t.comment() + "'").append(" [");
        List<String> parts = new ArrayList<>();
        for (ColumnDef c : t.columns()) {
            parts.add(c.ordinalPosition() + ":" + c.name() + " " + c.fullType() + (c.unsigned() ? " UNSIGNED" : "")
                    + (c.nullable() ? " NULL" : " NOT NULL")
                    + switch (c.defaultValue().kind()) {
                        case NONE -> "";
                        case NULL -> " DEFAULT NULL";
                        case LITERAL -> " DEFAULT '" + c.defaultValue().value() + "'";
                        case EXPRESSION -> " DEFAULT " + c.defaultValue().value();
                    }
                    + (c.onUpdate() == null ? "" : " ON UPDATE " + c.onUpdate())
                    + (c.autoIncrement() ? " AI" : "")
                    + (c.collation() == null ? "" : " " + c.collation())
                    + (c.comment().isEmpty() ? "" : " '" + c.comment() + "'"));
        }
        s.append(String.join(", ", parts)).append("] ");
        List<String> idx = new ArrayList<>();
        for (IndexDef i : t.indexes()) {
            idx.add(i.kind() + " " + i.name() + i.columns());
        }
        return s.append(idx).toString();
    }

    @AfterAll
    static void scriviEvidenze() {
        synchronized (EVIDENZE) {
            for (Map.Entry<ItServers, Map<Integer, String>> e : EVIDENZE.entrySet()) {
                long ok = e.getValue().values().stream().filter(v -> v.contains("ESITO: OK")).count();
                StringBuilder out = new StringBuilder("# T5.2 — andata e ritorno di TableDiff su " + e.getKey().label()
                        + "\n# Classe: it.ramasql.it.step5.T52AndataRitornoTest (step5, it)\n"
                        + "# Per caso: SQL applicato con SqlExecutor, esito, confronto riletto/atteso e diff residuo.\n"
                        + "# Casi eseguiti: " + e.getValue().size() + ", superati: " + ok + "\n"
                        + "# Atteso = modello chiesto + sole regole su ciò che decide il server (javadoc della classe):\n"
                        + "#  R1 catalogo di prova; R2 colonne numerate 1..n; R3 engine/charset/collation/AUTO_INCREMENT\n"
                        + "#  non specificati = scelti dal server; R4 charset/collation di colonna uguali alla tabella =\n"
                        + "#  ereditati; R5 NULL senza default = DEFAULT NULL, NOT NULL con DEFAULT NULL = nessun default;\n"
                        + "#  R6 default numerici per valore; R7 NOW()/current_timestamp() = CURRENT_TIMESTAMP;\n"
                        + "#  R8 ordine di indici e FK del lettore. Nessun altro campo è escluso dal confronto (equals).\n\n");
                e.getValue().values().forEach(v -> out.append(v).append('\n'));
                TestResults.write("step5", "T5.2-" + e.getKey().name().toLowerCase(Locale.ROOT) + ".txt", out.toString());
            }
        }
    }
}
