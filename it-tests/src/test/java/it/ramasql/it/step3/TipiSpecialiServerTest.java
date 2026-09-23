/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.ServerInfo;
import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.exec.SqlOrigin;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.core.metadata.ReadOnlyIndex;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * Il lettore dei metadati sui «tipi speciali» ({@code it-tests/fixtures/tipi_speciali.sql}, fixture separata da
 * {@code biblioteca}): ZEROFILL, {@code TINYINT(1)}, TIMESTAMP con {@code ON UPDATE}, ENUM, JSON, FULLTEXT, indice su
 * prefisso, indice DESC, FK con azioni omesse. Il normalizzatore non deve nascondere differenze: ciò che il server ha,
 * il lettore lo restituisce (o lo conserva, in sola lettura, fra gli elementi avanzati); un {@code ALTER} generato dal
 * client non lo perde. Poi il confronto fra i due server, con le differenze reali documentate.
 */
@Tag("step3")
@Tag("it")
class TipiSpecialiServerTest {

    static final String FIXTURE = "/fixtures/tipi_speciali.sql";

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void ilLettoreRestituisceIlDovuto(ItServers server) throws Exception {
        StringBuilder ev = new StringBuilder("Metadati dei tipi speciali su " + server.label() + "\n");
        try (TestCatalog cat = TestCatalog.create(server, "t3_tipi"); Session session = Step3Support.open(server,
                cat.name())) {
            cat.runScript(FIXTURE);
            ev.append("server: ").append(session.serverInfo().versionText()).append('\n');
            MetadataReader reader = MetadataReader.of(session);
            TableDef t = reader.table(cat.name(), "articoli").orElseThrow();

            ColumnDef codice = t.column("codice").orElseThrow();
            assertTrue(codice.zerofill(), "ZEROFILL letto");
            assertTrue(codice.unsigned());
            assertEquals("INT(6)", codice.fullType(), "con ZEROFILL la larghezza resta");
            ColumnDef attivo = t.column("attivo").orElseThrow();
            assertEquals("TINYINT(1)", attivo.fullType());
            assertEquals(ColumnDefault.literal("1"), attivo.defaultValue());
            assertFalse(attivo.zerofill());
            ColumnDef stato = t.column("stato").orElseThrow();
            assertEquals("ENUM", stato.dataType());
            assertEquals("'bozza','pubblicato','l''archivio'", stato.typeArgs());
            assertEquals(ColumnDefault.literal("bozza"), stato.defaultValue());
            ColumnDef dati = t.column("dati").orElseThrow();
            assertEquals("JSON", dati.dataType(), "JSON anche su MariaDB (LONGTEXT + CHECK json_valid)");
            assertEquals(null, dati.charset());
            ColumnDef modificato = t.column("modificato").orElseThrow();
            assertEquals("TIMESTAMP", modificato.dataType());
            assertEquals(ColumnDefault.CURRENT_TIMESTAMP, modificato.defaultValue());
            assertEquals("CURRENT_TIMESTAMP", modificato.onUpdate());
            assertFalse(modificato.nullable());

            // indici che il generatore sa riscrivere identici
            assertEquals(List.of(IndexDef.primary("id"), IndexDef.index("ix_categoria", "id_categoria"),
                    IndexDef.unique("uq_codice", "codice")), t.indexes());
            // gli altri: elementi avanzati, intatti per il generatore, visibili in sola lettura
            List<ReadOnlyIndex> ro = t.readOnlyIndexes();
            assertEquals(List.of("ft_testo", "ix_anno_desc", "ix_titolo_prefisso"),
                    ro.stream().map(ReadOnlyIndex::name).sorted().toList(), t.advancedElements().toString());
            assertEquals(Set.of(ReadOnlyIndex.Feature.FULLTEXT), byName(ro, "ft_testo").features());
            assertEquals(Set.of(ReadOnlyIndex.Feature.DESCENDING), byName(ro, "ix_anno_desc").features());
            assertEquals("anno DESC", byName(ro, "ix_anno_desc").columns());
            assertEquals(Set.of(ReadOnlyIndex.Feature.PREFIX), byName(ro, "ix_titolo_prefisso").features());
            assertEquals("titolo(20)", byName(ro, "ix_titolo_prefisso").columns());
            assertEquals(3, t.advancedElements().size(), "solo i tre indici: " + t.advancedElements());

            // FK con azioni omesse: il server riporta la sua predefinita, com'è
            ForeignKeyDef fk = t.foreignKey("fk_articoli_categoria").orElseThrow();
            FkAction omitted = server.isMariaDb() ? FkAction.RESTRICT : FkAction.NO_ACTION;
            assertEquals(omitted, fk.onDelete(), "azione ON DELETE omessa riportata da " + server.label());
            assertEquals(omitted, fk.onUpdate(), "azione ON UPDATE omessa riportata da " + server.label());

            // letto = letto: il generatore non ha niente da fare
            ServerInfo info = session.serverInfo();
            assertEquals(List.of(), TableDiff.diff(t, t, info));

            // un ALTER generato dal client (commento su «codice») conserva ZEROFILL, DESC, prefisso, FULLTEXT
            TableDef edited = t.changeColumn("codice", c -> c.withComment("codice di magazzino"));
            List<String> alter = TableDiff.diff(t, edited, info);
            assertEquals(1, alter.size());
            assertTrue(alter.get(0).contains("INT(6) UNSIGNED ZEROFILL NOT NULL"), alter.get(0));
            try (SqlExecutor exec = new SqlExecutor(session, new SqlLog(), reader)) {
                assertTrue(exec.run(it.ramasql.core.exec.SqlScript.of("alter", SqlOrigin.TABLE_EDITOR.label(), alter))
                        .completed());
            }
            TableDef after = reader.table(cat.name(), "articoli").orElseThrow();
            assertTrue(after.column("codice").orElseThrow().zerofill(), "ZEROFILL sopravvive al MODIFY");
            assertEquals("codice di magazzino", after.column("codice").orElseThrow().comment());
            assertEquals(t.readOnlyIndexes(), after.readOnlyIndexes(), "indici speciali intatti");
            assertEquals(t.advancedElements(), after.advancedElements());
            assertEquals(List.of(), TableDiff.diff(after, edited.withAdvancedElements(after.advancedElements()), info));
            assertEquals("000042", Step3Support.scalar(cat.connection(),
                    "SELECT CAST(codice AS CHAR) FROM articoli WHERE titolo LIKE 'Il nome%'"),
                    "il server mostra ancora gli zeri a sinistra");

            ev.append("colonne: ");
            t.columns().forEach(c -> ev.append(c.name()).append(' ').append(c.fullType())
                    .append(c.unsigned() ? " UNSIGNED" : "").append(c.zerofill() ? " ZEROFILL" : "")
                    .append(c.onUpdate() != null ? " ON UPDATE " + c.onUpdate() : "").append("; "));
            ev.append("\nindici modificabili: ").append(t.indexes()).append('\n');
            ev.append("indici in sola lettura (navigatore): ");
            ro.forEach(i -> ev.append(i.name()).append(' ').append(i.features()).append(" (").append(i.columns())
                    .append("); "));
            ev.append("\nelementi avanzati (testo del server): ").append(t.advancedElements()).append('\n');
            ev.append("FK con azioni omesse: ON DELETE ").append(fk.onDelete()).append(", ON UPDATE ")
                    .append(fk.onUpdate()).append('\n');
            ev.append("ALTER generato: ").append(alter.get(0).replace(cat.name(), "<catalogo>")).append('\n');
            ev.append("dopo l'ALTER: ZEROFILL ").append(after.column("codice").orElseThrow().zerofill())
                    .append(", indici speciali invariati, codice 42 letto dal server come «000042» (INT(6) ZEROFILL)\n");
        } finally {
            TestResults.write("step3", "metadati-tipi-speciali-" + server.name().toLowerCase(Locale.ROOT) + ".txt",
                    ev.toString());
        }
    }

    /**
     * Stessa fixture, due server: i {@link TableDef} coincidono in tutto tranne dove i server sono davvero diversi,
     * e queste differenze il lettore non le nasconde: le azioni di una FK scritta senza {@code ON DELETE/ON UPDATE}
     * (MariaDB riporta RESTRICT, MySQL NO ACTION: per InnoDB equivalenti, ma è ciò che il server dice) e il testo delle
     * righe avanzate di {@code SHOW CREATE TABLE}, che ciascun server scrive a modo suo.
     */
    @Test
    void iDueServerCoincidonoDoveDevono() throws Exception {
        Map<ItServers, List<TableDef>> read = new EnumMap<>(ItServers.class);
        for (ItServers server : ItServers.values()) {
            List<TestCatalog> catalogs = new ArrayList<>();
            try {
                TestCatalog c = TestCatalog.create(server, "t3_tipi_confronto");
                catalogs.add(c);
                c.runScript(FIXTURE);
                try (Session session = Step3Support.open(server, "")) {
                    read.put(server, MetadataReader.of(session).allTables(c.name()).stream()
                            .map(t -> t.withCatalog(null)).toList());
                }
            } finally {
                Step3Support.closeAll(catalogs);
            }
        }
        List<TableDef> maria = read.get(ItServers.MARIADB);
        List<TableDef> mysql = read.get(ItServers.MYSQL);
        assertEquals(List.of("articoli", "categorie"), maria.stream().map(TableDef::name).toList());
        assertEquals(maria.get(1), mysql.get(1), "categorie: identica");
        TableDef a = maria.get(0);
        TableDef b = mysql.get(0);
        assertEquals(a.columns(), b.columns(), "colonne identiche (ZEROFILL, TINYINT(1), ENUM, JSON, ON UPDATE)");
        assertEquals(a.indexes(), b.indexes());
        assertEquals(a.readOnlyIndexes().stream().map(i -> i.name() + i.features() + i.columns()).sorted().toList(),
                b.readOnlyIndexes().stream().map(i -> i.name() + i.features() + i.columns()).sorted().toList(),
                "indici in sola lettura: stessi nomi, motivi e colonne");
        ForeignKeyDef fa = a.foreignKeys().get(0);
        ForeignKeyDef fb = b.foreignKeys().get(0);
        assertEquals(FkAction.RESTRICT, fa.onDelete());
        assertEquals(FkAction.NO_ACTION, fb.onDelete());
        assertEquals(fa.withOnDelete(FkAction.NO_ACTION).withOnUpdate(FkAction.NO_ACTION), fb,
                "a parte l'azione predefinita riportata, la FK è la stessa");
        assertEquals(a.withForeignKeys(List.of()).withAdvancedElements(List.of()),
                b.withForeignKeys(List.of()).withAdvancedElements(List.of()));
        TestResults.write("step3", "metadati-tipi-speciali-confronto.txt",
                "Tipi speciali: confronto dei TableDef letti da MariaDB e MySQL\n"
                + "categorie: identica (equals)\n"
                + "articoli: colonne, indici modificabili, indici in sola lettura (nome, motivo, colonne), opzioni:"
                + " identici\n"
                + "differenza reale 1: FK senza ON DELETE/ON UPDATE → MariaDB riporta " + fa.onDelete() + "/"
                + fa.onUpdate() + ", MySQL " + fb.onDelete() + "/" + fb.onUpdate()
                + " (information_schema.REFERENTIAL_CONSTRAINTS); in InnoDB le due azioni si comportano allo stesso"
                + " modo, il lettore riporta ciò che dice il server\n"
                + "differenza reale 2: il testo delle righe avanzate di SHOW CREATE TABLE\n  MariaDB: "
                + a.advancedElements() + "\n  MySQL:   " + b.advancedElements() + "\n");
    }

    private static ReadOnlyIndex byName(List<ReadOnlyIndex> list, String name) {
        return list.stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }
}
