/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.ScriptResult;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;
import it.ramasql.core.verify.SchemaVerifier;
import it.ramasql.core.verify.Verification;
import it.ramasql.core.verify.VerificationIssue;
import it.ramasql.core.verify.VerificationIssue.Kind;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import it.ramasql.it.step5.EditorSupport;

/**
 * T6.4 — <b>verifica dopo</b>: per ogni caso di indice (T6.1) e di chiave esterna (T6.2) — gli stessi modelli e lo
 * stesso SQL atteso dei test U {@code TableDiffIndexTest} e {@code TableDiffForeignKeyTest}, comprese le 16
 * combinazioni di azioni, la FK composta, l'autoreferenziale e le modifiche — l'SQL generato si applica davvero con
 * {@link SqlExecutor}, si rilegge la tabella con {@code MetadataReader} ({@code information_schema.STATISTICS},
 * {@code KEY_COLUMN_USAGE}, {@code REFERENTIAL_CONSTRAINTS}) e {@link SchemaVerifier} deve dichiararla «conforme».
 * Le modifiche di una FK sono due istruzioni ({@code DROP FOREIGN KEY} e poi {@code ADD}) eseguite in quest'ordine.
 * Tre casi <b>alterati ad arte</b> (FK creata sul server con un'azione diversa, indice con le colonne in un altro
 * ordine, FK ignorata da MyISAM) devono invece dare la differenza.
 */
@Tag("step6")
@Tag("it")
class T64VerificaDopoTest {

    private static final Map<ItServers, Map<String, String>> EVIDENZE = new EnumMap<>(ItServers.class);

    /** Tabelle riferite dai casi di T6.2, create in ogni catalogo di prova prima del caso. */
    static final List<String> PARENTS = List.of(
            "CREATE TABLE `editori` (`id` INT UNSIGNED NOT NULL, `nome` VARCHAR(50) NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB",
            "CREATE TABLE `libri_autori` (`id_libro` INT UNSIGNED NOT NULL, `id_autore` INT UNSIGNED NOT NULL,"
                    + " PRIMARY KEY (`id_libro`, `id_autore`)) ENGINE=InnoDB",
            "CREATE TABLE `case_editrici` (`codice` INT UNSIGNED NOT NULL, PRIMARY KEY (`codice`)) ENGINE=InnoDB",
            "CREATE TABLE `order` (`primary key` INT NOT NULL, PRIMARY KEY (`primary key`)) ENGINE=InnoDB");

    /** Segnaposto dei due cataloghi del caso «FK verso un altro catalogo», sostituiti con quelli di prova. */
    static final String CAT_A = "ramasql_test_a";
    static final String CAT_B = "ramasql_test_b";

    // ================================================================ T6.1 (come TableDiffIndexTest)

    static final TableDef SOCI = TableDef.of(null, "soci")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("tessera", "VARCHAR", "10").notNull())
            .addColumn(ColumnDef.of("cognome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("email", "VARCHAR", "100"))
            .withEngine("InnoDB")
            .withOrdinalPositions();

    static final TableDef CON_INDICI = SOCI.withPrimaryKey("id")
            .addIndex(IndexDef.unique("uq_tessera", "tessera"))
            .addIndex(IndexDef.index("ix_nome", "cognome", "nome"));

    // ================================================================ T6.2 (come TableDiffForeignKeyTest)

    static final TableDef LIBRI = TableDef.of(null, "libri")
            .addColumn(ColumnDef.of("id", "INT").withUnsigned(true).notNull().withAutoIncrement(true))
            .addColumn(ColumnDef.of("titolo", "VARCHAR", "100").notNull())
            .addColumn(ColumnDef.of("id_editore", "INT").withUnsigned(true))
            .withPrimaryKey("id")
            .withEngine("InnoDB")
            .withOrdinalPositions();

    static final ForeignKeyDef FK_EDITORI = ForeignKeyDef.of("fk_libri_editori", "id_editore", "editori", "id");
    static final TableDef LIBRI_CON_FK = LIBRI.addForeignKey(FK_EDITORI);

    static final TableDef CITAZIONI = TableDef.of(null, "citazioni")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("id_libro", "INT").withUnsigned(true).notNull())
            .addColumn(ColumnDef.of("id_autore", "INT").withUnsigned(true).notNull())
            .withPrimaryKey("id").withEngine("InnoDB").withOrdinalPositions();

    static final TableDef CATEGORIE = TableDef.of(null, "categorie")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("id_padre", "INT"))
            .withPrimaryKey("id").withEngine("InnoDB").withOrdinalPositions();

    static final ForeignKeyDef FK_PADRE =
            ForeignKeyDef.of("fk_categorie_padre", "id_padre", "categorie", "id").withOnDelete(FkAction.CASCADE);

    record Caso(String id, String nome, TableDef originale, TableDef modificata, List<String> attese) {
        @Override
        public String toString() {
            return id + " " + nome;
        }
    }

    private static Caso caso(String id, String nome, TableDef originale, TableDef modificata, String... attese) {
        return new Caso(id, nome, originale, modificata, List.of(attese));
    }

    static List<Caso> casiIndici() {
        return List.of(
                caso("I01", "crea INDEX", SOCI, SOCI.addIndex(IndexDef.index("ix_cognome", "cognome")),
                        "ALTER TABLE `soci` ADD INDEX `ix_cognome` (`cognome`)"),
                caso("I02", "crea UNIQUE", SOCI, SOCI.addIndex(IndexDef.unique("uq_email", "email")),
                        "ALTER TABLE `soci` ADD UNIQUE INDEX `uq_email` (`email`)"),
                caso("I03", "crea PRIMARY", SOCI, SOCI.withPrimaryKey("id"), "ALTER TABLE `soci` ADD PRIMARY KEY (`id`)"),
                caso("I04", "crea INDEX multi-colonna: l'ordine è quello dato", SOCI,
                        SOCI.addIndex(IndexDef.index("ix_nome", "nome", "cognome")),
                        "ALTER TABLE `soci` ADD INDEX `ix_nome` (`nome`, `cognome`)"),
                caso("I05", "crea più indici in un solo ALTER", SOCI,
                        SOCI.withPrimaryKey("id").addIndex(IndexDef.unique("uq_tessera", "tessera"))
                                .addIndex(IndexDef.index("order by", "cognome", "nome")), """
                        ALTER TABLE `soci`
                          ADD PRIMARY KEY (`id`),
                          ADD UNIQUE INDEX `uq_tessera` (`tessera`),
                          ADD INDEX `order by` (`cognome`, `nome`)"""),
                caso("I06", "elimina INDEX", CON_INDICI, CON_INDICI.removeIndex("ix_nome"),
                        "ALTER TABLE `soci` DROP INDEX `ix_nome`"),
                caso("I07", "elimina UNIQUE", CON_INDICI, CON_INDICI.removeIndex("uq_tessera"),
                        "ALTER TABLE `soci` DROP INDEX `uq_tessera`"),
                caso("I08", "elimina PRIMARY", CON_INDICI, CON_INDICI.withPrimaryKey(),
                        "ALTER TABLE `soci` DROP PRIMARY KEY"),
                caso("I09", "rinomina INDEX", CON_INDICI, CON_INDICI.changeIndex("ix_nome", i -> i.withName("ix_anagrafica")),
                        "ALTER TABLE `soci` RENAME INDEX `ix_nome` TO `ix_anagrafica`"),
                caso("I10", "rinomina UNIQUE", CON_INDICI,
                        CON_INDICI.changeIndex("uq_tessera", i -> i.withName("tessera unica")),
                        "ALTER TABLE `soci` RENAME INDEX `uq_tessera` TO `tessera unica`"),
                caso("I11", "modifica colonne di un indice: DROP + ADD nello stesso ALTER", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withColumns("cognome", "nome", "email")), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD INDEX `ix_nome` (`cognome`, `nome`, `email`)"""),
                caso("I12", "cambia solo l'ordine delle colonne: è una modifica", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withColumns("nome", "cognome")), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD INDEX `ix_nome` (`nome`, `cognome`)"""),
                caso("I13", "da INDEX a UNIQUE: DROP + ADD nello stesso ALTER", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withKind(IndexKind.UNIQUE)), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD UNIQUE INDEX `ix_nome` (`cognome`, `nome`)"""),
                caso("I14", "rinomina e modifica insieme: non è una rinomina", CON_INDICI,
                        CON_INDICI.removeIndex("ix_nome").addIndex(IndexDef.index("ix_cognome", "cognome")), """
                        ALTER TABLE `soci`
                          DROP INDEX `ix_nome`,
                          ADD INDEX `ix_cognome` (`cognome`)"""),
                caso("I15", "cambia la PRIMARY", CON_INDICI, CON_INDICI.withPrimaryKey("id", "tessera"), """
                        ALTER TABLE `soci`
                          DROP PRIMARY KEY,
                          ADD PRIMARY KEY (`id`, `tessera`)"""),
                caso("I16", "rinomina di colonna indicizzata: l'indice non si tocca", CON_INDICI,
                        CON_INDICI.changeColumn("cognome", c -> c.withName("famiglia"))
                                .changeIndex("ix_nome", i -> i.withColumns("famiglia", "nome")),
                        "ALTER TABLE `soci` CHANGE COLUMN `cognome` `famiglia` VARCHAR(50) NOT NULL"),
                caso("I17", "nuova colonna con il suo indice: ADD COLUMN prima di ADD INDEX", CON_INDICI,
                        CON_INDICI.addColumn(ColumnDef.of("citta", "VARCHAR", "40"))
                                .addIndex(IndexDef.index("ix_citta", "citta")), """
                        ALTER TABLE `soci`
                          ADD COLUMN `citta` VARCHAR(40) NULL,
                          ADD INDEX `ix_citta` (`citta`)"""),
                caso("I18", "elimina colonna e il suo indice: DROP INDEX prima di DROP COLUMN", CON_INDICI,
                        CON_INDICI.removeIndex("uq_tessera").removeColumn("tessera"), """
                        ALTER TABLE `soci`
                          DROP INDEX `uq_tessera`,
                          DROP COLUMN `tessera`"""),
                caso("I19", "nessuna modifica: nomi di colonna con maiuscole diverse", CON_INDICI,
                        CON_INDICI.changeIndex("ix_nome", i -> i.withColumns("COGNOME", "Nome"))),
                caso("I20", "tabella nuova con indici", null, CON_INDICI, """
                        CREATE TABLE `soci` (
                          `id` INT NOT NULL,
                          `tessera` VARCHAR(10) NOT NULL,
                          `cognome` VARCHAR(50) NOT NULL,
                          `nome` VARCHAR(50) NOT NULL,
                          `email` VARCHAR(100) NULL,
                          PRIMARY KEY (`id`),
                          UNIQUE INDEX `uq_tessera` (`tessera`),
                          INDEX `ix_nome` (`cognome`, `nome`)
                        ) ENGINE=InnoDB"""));
    }

    static List<Caso> casiChiaviEsterne() {
        List<Caso> casi = new ArrayList<>();
        int n = 1;
        for (FkAction onDelete : FkAction.values()) {
            for (FkAction onUpdate : FkAction.values()) {
                casi.add(caso(String.format("F%02d", n++), "crea FK: ON DELETE " + onDelete.sql() + " ON UPDATE "
                                + onUpdate.sql(), LIBRI, LIBRI.addForeignKey(FK_EDITORI.withActions(onDelete, onUpdate)),
                        "ALTER TABLE `libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                                + "REFERENCES `editori` (`id`) ON DELETE " + onDelete.sql() + " ON UPDATE "
                                + onUpdate.sql()));
            }
        }
        casi.add(caso("F17", "FK composta", CITAZIONI,
                CITAZIONI.addForeignKey(ForeignKeyDef.of("fk_citazioni_libri_autori", List.of("id_libro", "id_autore"),
                        "libri_autori", List.of("id_libro", "id_autore")).withOnDelete(FkAction.CASCADE)),
                "ALTER TABLE `citazioni` ADD CONSTRAINT `fk_citazioni_libri_autori` FOREIGN KEY (`id_libro`, `id_autore`) "
                        + "REFERENCES `libri_autori` (`id_libro`, `id_autore`) ON DELETE CASCADE ON UPDATE RESTRICT"));
        casi.add(caso("F18", "elimina FK", LIBRI_CON_FK, LIBRI, "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`"));
        casi.add(caso("F19", "modifica azione: prima DROP FOREIGN KEY, poi ADD CONSTRAINT, in due istruzioni",
                LIBRI_CON_FK, LIBRI_CON_FK.changeForeignKey("fk_libri_editori", f -> f.withOnDelete(FkAction.SET_NULL)),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `editori` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT"));
        casi.add(caso("F20", "modifica tabella riferita: DROP poi ADD", LIBRI_CON_FK,
                LIBRI_CON_FK.changeForeignKey("fk_libri_editori",
                        f -> f.withReference(null, "case_editrici", List.of("codice"))),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `case_editrici` (`codice`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("F21", "rinomina FK: DROP poi ADD con il nome nuovo", LIBRI_CON_FK,
                LIBRI_CON_FK.changeForeignKey("fk_libri_editori", f -> f.withName("fk editore")),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk editore` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("F22", "FK autoreferenziale", CATEGORIE, CATEGORIE.addForeignKey(FK_PADRE),
                "ALTER TABLE `categorie` ADD CONSTRAINT `fk_categorie_padre` FOREIGN KEY (`id_padre`) "
                        + "REFERENCES `categorie` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT"));
        casi.add(caso("F23", "FK autoreferenziale già nel CREATE TABLE", null, CATEGORIE.addForeignKey(FK_PADRE), """
                CREATE TABLE `categorie` (
                  `id` INT NOT NULL,
                  `nome` VARCHAR(50) NOT NULL,
                  `id_padre` INT NULL,
                  PRIMARY KEY (`id`),
                  CONSTRAINT `fk_categorie_padre` FOREIGN KEY (`id_padre`) REFERENCES `categorie` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
                ) ENGINE=InnoDB"""));
        casi.add(caso("F24", "FK autoreferenziale + rinomina della tabella: basta il RENAME",
                CATEGORIE.addForeignKey(FK_PADRE), CATEGORIE.addForeignKey(FK_PADRE).withName("generi"),
                "RENAME TABLE `categorie` TO `generi`"));
        casi.add(caso("F25", "nuova colonna, indice e FK in un solo ALTER, nell'ordine giusto", CITAZIONI,
                CITAZIONI.addColumn(ColumnDef.of("id_editore", "INT").withUnsigned(true))
                        .addIndex(IndexDef.index("ix_editore", "id_editore"))
                        .addForeignKey(FK_EDITORI.withName("fk_citazioni_editori")), """
                ALTER TABLE `citazioni`
                  ADD COLUMN `id_editore` INT UNSIGNED NULL,
                  ADD INDEX `ix_editore` (`id_editore`),
                  ADD CONSTRAINT `fk_citazioni_editori` FOREIGN KEY (`id_editore`) REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT"""));
        casi.add(caso("F26", "elimina FK e la sua colonna: prima l'ALTER della FK, poi quello della colonna",
                LIBRI_CON_FK, LIBRI.removeColumn("id_editore"),
                "ALTER TABLE `libri` DROP FOREIGN KEY `fk_libri_editori`",
                "ALTER TABLE `libri` DROP COLUMN `id_editore`"));
        casi.add(caso("F27", "due FK eliminate e una creata", LIBRI_CON_FK.addForeignKey(FK_EDITORI.withName("fk_doppia")),
                LIBRI.addForeignKey(FK_EDITORI.withName("fk_nuova").withOnUpdate(FkAction.CASCADE)), """
                ALTER TABLE `libri`
                  DROP FOREIGN KEY `fk_libri_editori`,
                  DROP FOREIGN KEY `fk_doppia`""",
                "ALTER TABLE `libri` ADD CONSTRAINT `fk_nuova` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `editori` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE"));
        casi.add(caso("F28", "FK senza nome: lo assegna il server", LIBRI, LIBRI.addForeignKey(FK_EDITORI.withName(null)),
                "ALTER TABLE `libri` ADD FOREIGN KEY (`id_editore`) REFERENCES `editori` (`id`) "
                        + "ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("F29", "FK verso un altro catalogo: tabella riferita qualificata", LIBRI.withCatalog(CAT_A),
                LIBRI.withCatalog(CAT_A).addForeignKey(FK_EDITORI.withReference(CAT_B, "editori", List.of("id"))),
                "ALTER TABLE `ramasql_test_a`.`libri` ADD CONSTRAINT `fk_libri_editori` FOREIGN KEY (`id_editore`) "
                        + "REFERENCES `ramasql_test_b`.`editori` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("F30", "nomi con spazi e parole riservate",
                TableDef.of(null, "ordine dettagli").addColumn(ColumnDef.of("order", "INT")).withOrdinalPositions(),
                TableDef.of(null, "ordine dettagli").addColumn(ColumnDef.of("order", "INT")).withOrdinalPositions()
                        .addForeignKey(ForeignKeyDef.of("fk order", "order", "order", "primary key")),
                "ALTER TABLE `ordine dettagli` ADD CONSTRAINT `fk order` FOREIGN KEY (`order`) "
                        + "REFERENCES `order` (`primary key`) ON DELETE RESTRICT ON UPDATE RESTRICT"));
        casi.add(caso("F31", "nessuna modifica → zero istruzioni", LIBRI_CON_FK, LIBRI_CON_FK));
        casi.add(caso("F32", "rinomina della colonna della FK: la FK non si tocca", LIBRI_CON_FK,
                LIBRI.changeColumn("id_editore", c -> c.withName("editore"))
                        .addForeignKey(FK_EDITORI.withColumns(List.of("editore"), List.of("id"))),
                "ALTER TABLE `libri` CHANGE COLUMN `id_editore` `editore` INT UNSIGNED NULL"));
        return casi;
    }

    static Stream<Arguments> casiPerServer() {
        List<Arguments> out = new ArrayList<>();
        List<Caso> tutti = new ArrayList<>(casiIndici());
        tutti.addAll(casiChiaviEsterne());
        for (ItServers server : ItServers.values()) {
            for (Caso c : tutti) {
                out.add(arguments(server, c));
            }
        }
        return out.stream();
    }

    // ================================================================ casi del generatore: «conforme»

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("casiPerServer")
    void applicatoERilettoEConforme(ItServers server, Caso caso) throws Exception {
        StringBuilder ev = new StringBuilder("## " + caso.id() + " — " + caso.nome() + "\n");
        // il catalogo riferito (solo F29) si apre per primo e quindi si distrugge per ultimo, dopo quello con la FK
        try (TestCatalog altro = caso.id().equals("F29") ? TestCatalog.create(server, "t64b") : null;
                TestCatalog cat = TestCatalog.create(server, "t64"); Session session = EditorSupport.open(server, cat.name());
                SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            ServerInfo info = session.serverInfo();
            cat.execute(PARENTS.toArray(String[]::new));
            if (altro != null) {
                altro.execute(PARENTS.toArray(String[]::new));
            }
            String catB = altro == null ? CAT_B : altro.name();
            TableDef originale = cataloghi(caso.originale(), cat.name(), catB);
            TableDef modificata = cataloghi(caso.modificata(), cat.name(), catB);
            List<String> attese = caso.attese().stream()
                    .map(s -> s.replace(CAT_A, cat.name()).replace(CAT_B, catB)).toList();

            if (originale != null) {
                List<String> create = TableDiff.diff(null, originale, info);
                ev.append("tabella di partenza:\n").append(EditorSupport.block(create)).append('\n');
                EditorSupport.apply(exec, "T6.4 partenza", create);
            }
            List<String> sql = TableDiff.diff(originale, modificata, info);
            assertEquals(attese, sql, "SQL del generatore = SQL atteso del test U");
            ev.append("SQL applicato (= test U):\n").append(EditorSupport.block(sql)).append('\n');
            int prima = exec.log().entries().size();
            if (!sql.isEmpty()) {
                ScriptResult r = EditorSupport.apply(exec, "T6.4 " + caso.id(), sql);
                assertEquals(sql.size(), r.results().size());
            }
            List<SqlLog.Entry> eseguite = exec.log().entries().subList(prima, exec.log().entries().size());
            assertEquals(sql, eseguite.stream().map(SqlLog.Entry::sql).toList(), "eseguite tutte, nell'ordine dato");
            assertTrue(eseguite.stream().allMatch(e -> e.outcome() == SqlLog.Outcome.OK));
            ev.append("esecuzione: ").append(eseguite.size()).append(" istruzioni OK, nell'ordine\n");
            if (sql.size() == 2 && sql.get(0).contains("DROP FOREIGN KEY") && sql.get(1).contains("ADD ")) {
                ev.append("modifica di FK: DROP FOREIGN KEY eseguita prima di ADD CONSTRAINT (log #")
                        .append(eseguite.get(0).sequence()).append(" < #").append(eseguite.get(1).sequence())
                        .append(")\n");
                assertTrue(eseguite.get(0).sequence() < eseguite.get(1).sequence());
            }

            TableDef riletta = EditorSupport.reread(session, cat.name(), modificata.name());
            Verification v = SchemaVerifier.verify(originale == null ? null : originale.withCatalog(cat.name()),
                    modificata.withCatalog(cat.name()), riletta);
            ev.append("riletti dal server: indici ").append(indici(riletta)).append("; FK ").append(fks(riletta))
                    .append('\n');
            ev.append("verifica: ").append(v.summary().replace("\n", "\n    ")).append('\n');
            assertTrue(v.conforming(), v::summary);
            // le azioni chieste sono riportate dal server esattamente come scritte (RESTRICT ≠ NO ACTION nel testo)
            for (ForeignKeyDef f : modificata.foreignKeys()) {
                ForeignKeyDef letta = f.name() == null ? riletta.foreignKeys().get(0)
                        : riletta.foreignKey(f.name()).orElseThrow();
                assertEquals(f.onDelete(), letta.onDelete(), "ON DELETE riportato come scritto");
                assertEquals(f.onUpdate(), letta.onUpdate(), "ON UPDATE riportato come scritto");
            }
            assertFalse(v.has(Kind.FK_ACTION_EQUIVALENT), "azioni identiche, nessuna equivalenza necessaria");
            ev.append("ESITO: conforme\n");
        } catch (Throwable t) {
            ev.append("ESITO: FALLITO — ").append(t.getMessage()).append('\n');
            throw t;
        } finally {
            evidenza(server, caso.id(), ev.toString());
        }
    }

    // ================================================================ casi alterati ad arte: la differenza si vede

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void alteratoAdArteAzioneDiversa(ItServers server) throws Exception {
        StringBuilder ev = new StringBuilder("## X1 — alterato ad arte: FK creata sul server con ON DELETE CASCADE, "
                + "chiesta RESTRICT\n");
        try (TestCatalog cat = TestCatalog.create(server, "t64x1"); Session session = EditorSupport.open(server,
                cat.name())) {
            cat.execute(PARENTS.toArray(String[]::new));
            TableDef chiesta = LIBRI_CON_FK.withCatalog(cat.name());   // ON DELETE RESTRICT ON UPDATE RESTRICT
            String sql = "CREATE TABLE `libri` (`id` INT UNSIGNED NOT NULL AUTO_INCREMENT, `titolo` VARCHAR(100) NOT NULL,"
                    + " `id_editore` INT UNSIGNED NULL, PRIMARY KEY (`id`), CONSTRAINT `fk_libri_editori` FOREIGN KEY"
                    + " (`id_editore`) REFERENCES `editori` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT) ENGINE=InnoDB";
            cat.execute(sql);
            ev.append("creata fuori dal client:\n    ").append(sql).append(";\n");
            Verification v = SchemaVerifier.verify(chiesta, EditorSupport.reread(session, cat.name(), "libri"));
            ev.append("verifica: ").append(v.summary().replace("\n", "\n    ")).append('\n');
            assertFalse(v.conforming());
            assertEquals(List.of(Kind.FK_ON_DELETE), v.blocking().stream().map(VerificationIssue::kind).toList());
            assertTrue(v.blocking().get(0).message().contains("chiesta RESTRICT, sul server CASCADE"),
                    v.blocking().get(0).message());
            ev.append("ESITO: differenza dichiarata\n");
        } catch (Throwable t) {
            ev.append("ESITO: FALLITO — ").append(t.getMessage()).append('\n');
            throw t;
        } finally {
            evidenza(server, "X1", ev.toString());
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void alteratoAdArteOrdineDelleColonne(ItServers server) throws Exception {
        StringBuilder ev = new StringBuilder("## X2 — alterato ad arte: indice creato sul server con le colonne in "
                + "un altro ordine\n");
        try (TestCatalog cat = TestCatalog.create(server, "t64x2"); Session session = EditorSupport.open(server,
                cat.name())) {
            TableDef chiesta = CON_INDICI.withCatalog(cat.name());   // ix_nome (cognome, nome)
            String sql = "CREATE TABLE `soci` (`id` INT NOT NULL, `tessera` VARCHAR(10) NOT NULL, `cognome` VARCHAR(50)"
                    + " NOT NULL, `nome` VARCHAR(50) NOT NULL, `email` VARCHAR(100) NULL, PRIMARY KEY (`id`),"
                    + " UNIQUE INDEX `uq_tessera` (`tessera`), INDEX `ix_nome` (`nome`, `cognome`)) ENGINE=InnoDB";
            cat.execute(sql);
            ev.append("creata fuori dal client:\n    ").append(sql).append(";\n");
            Verification v = SchemaVerifier.verify(chiesta, EditorSupport.reread(session, cat.name(), "soci"));
            ev.append("verifica: ").append(v.summary().replace("\n", "\n    ")).append('\n');
            assertFalse(v.conforming());
            assertEquals(List.of(Kind.INDEX_COLUMN_ORDER), v.blocking().stream().map(VerificationIssue::kind).toList());
            ev.append("ESITO: differenza dichiarata\n");
        } catch (Throwable t) {
            ev.append("ESITO: FALLITO — ").append(t.getMessage()).append('\n');
            throw t;
        } finally {
            evidenza(server, "X2", ev.toString());
        }
    }

    /**
     * La FK del generatore applicata a una tabella MyISAM: il server accetta l'istruzione ma non crea il vincolo
     * (ADR-011, «FK ignorate in silenzio»). Solo la verifica dopo lo rivela.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void alteratoAdArteMyIsamIgnoraLaFk(ItServers server) throws Exception {
        StringBuilder ev = new StringBuilder("## X3 — FK del generatore su una tabella MyISAM\n");
        try (TestCatalog cat = TestCatalog.create(server, "t64x3"); Session session = EditorSupport.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            cat.execute(PARENTS.toArray(String[]::new));
            TableDef myisam = LIBRI.withEngine("MyISAM");
            TableDef chiesta = myisam.addForeignKey(FK_EDITORI);
            List<String> sql = new ArrayList<>(TableDiff.diff(null, myisam, session.serverInfo()));
            sql.addAll(TableDiff.diff(myisam, chiesta, session.serverInfo()));
            ev.append("SQL applicato:\n").append(EditorSupport.block(sql)).append('\n');
            ScriptResult r = EditorSupport.apply(exec, "T6.4 X3", sql);
            ev.append("esecuzione: ").append(r.results().size()).append(" istruzioni OK (il server non protesta)\n");
            Verification v = SchemaVerifier.verify(chiesta.withCatalog(cat.name()),
                    EditorSupport.reread(session, cat.name(), "libri"));
            ev.append("verifica: ").append(v.summary().replace("\n", "\n    ")).append('\n');
            assertFalse(v.conforming());
            assertEquals(List.of(Kind.FK_MISSING), v.blocking().stream().map(VerificationIssue::kind).toList());
            assertTrue(v.blocking().get(0).message().contains("MyISAM"), v.blocking().get(0).message());
            ev.append("ESITO: differenza dichiarata\n");
        } catch (Throwable t) {
            ev.append("ESITO: FALLITO — ").append(t.getMessage()).append('\n');
            throw t;
        } finally {
            evidenza(server, "X3", ev.toString());
        }
    }

    // ================================================================ attrezzi

    /** Sostituisce i segnaposto dei cataloghi del caso F29 con i cataloghi di prova. */
    private static TableDef cataloghi(TableDef t, String catA, String catB) {
        if (t == null) {
            return null;
        }
        TableDef out = CAT_A.equals(t.catalog()) ? t.withCatalog(catA) : t;
        List<ForeignKeyDef> fks = new ArrayList<>();
        for (ForeignKeyDef f : out.foreignKeys()) {
            fks.add(CAT_B.equals(f.refCatalog()) ? f.withReference(catB, f.refTable(), f.refColumns()) : f);
        }
        return out.withForeignKeys(fks);
    }

    private static String indici(TableDef t) {
        List<String> out = new ArrayList<>();
        for (IndexDef i : t.indexes()) {
            out.add(i.kind() + " `" + i.name() + "` " + i.columns());
        }
        return out.toString();
    }

    private static String fks(TableDef t) {
        List<String> out = new ArrayList<>();
        for (ForeignKeyDef f : t.foreignKeys()) {
            out.add("`" + f.name() + "` " + f.columns() + " → " + (f.refCatalog() == null ? "" : f.refCatalog() + ".")
                    + f.refTable() + f.refColumns() + " ON DELETE " + f.onDelete().sql() + " ON UPDATE "
                    + f.onUpdate().sql());
        }
        return out.toString();
    }

    private static void evidenza(ItServers server, String id, String text) {
        synchronized (EVIDENZE) {
            EVIDENZE.computeIfAbsent(server, k -> new TreeMap<>()).put(id, text);
        }
    }

    @AfterAll
    static void scriviEvidenze() {
        synchronized (EVIDENZE) {
            for (Map.Entry<ItServers, Map<String, String>> e : EVIDENZE.entrySet()) {
                long conformi = e.getValue().values().stream().filter(v -> v.contains("ESITO: conforme")).count();
                long differenze = e.getValue().values().stream().filter(v -> v.contains("ESITO: differenza")).count();
                StringBuilder out = new StringBuilder("# T6.4 — verifica dopo l'applicazione su " + e.getKey().label()
                        + "\n# Classe: it.ramasql.it.step6.T64VerificaDopoTest (step6, it)\n"
                        + "# I01–I20 = casi di T6.1, F01–F32 = casi di T6.2 (F01–F16: le 16 combinazioni di azioni),"
                        + " X1–X3 = casi alterati ad arte.\n"
                        + "# Casi: " + e.getValue().size() + " — conformi: " + conformi
                        + " — alterati con differenza dichiarata: " + differenze + "\n\n");
                e.getValue().values().forEach(v -> out.append(v).append('\n'));
                TestResults.write("step6", "T6.4-" + e.getKey().name().toLowerCase(Locale.ROOT) + ".txt", out.toString());
            }
        }
    }
}
