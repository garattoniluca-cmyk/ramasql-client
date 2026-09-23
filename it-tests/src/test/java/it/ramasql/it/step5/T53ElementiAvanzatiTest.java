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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.ramasql.core.connection.Session;
import it.ramasql.core.exec.SqlExecutor;
import it.ramasql.core.exec.SqlLog;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.TableDiff;
import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;

/**
 * T5.3 — elementi non editabili in v1 (CHECK, colonna generata, partizioni), creati via script, <b>sopravvivono
 * identici</b> quando il generatore modifica un'altra colonna.
 *
 * <p>Tabella compatibile con le partizioni su entrambi i server: niente chiavi esterne (InnoDB non le ammette su
 * tabelle partizionate) e l'unica chiave (la primaria) contiene la colonna di partizionamento. Il confronto è fatto
 * sul testo di {@code SHOW CREATE TABLE} prima e dopo, <b>tolta solo la riga della colonna modificata</b>, e su
 * {@link TableDef#advancedElements()}.
 */
@Tag("step5")
@Tag("it")
class T53ElementiAvanzatiTest {

    private static final String SCRIPT = """
            CREATE TABLE misure (
              id INT NOT NULL,
              valore DECIMAL(8,2) NOT NULL CHECK (valore >= 0),
              doppio DECIMAL(10,2) AS (valore * 2) STORED,
              meta DECIMAL(10,2) AS (valore / 2) VIRTUAL,
              nota VARCHAR(50) NULL,
              PRIMARY KEY (id),
              CONSTRAINT chk_nota CHECK (nota IS NULL OR CHAR_LENGTH(nota) > 2)
            ) ENGINE=InnoDB
            PARTITION BY HASH (id) PARTITIONS 4""";

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void gliElementiAvanzatiSopravvivono(ItServers server) throws Exception {
        StringBuilder ev = new StringBuilder("# T5.3 — elementi non editabili che sopravvivono a una modifica ("
                + server.label() + ")\n# Classe: it.ramasql.it.step5.T53ElementiAvanzatiTest (step5, it)\n\n");
        try (TestCatalog cat = TestCatalog.create(server, "t53"); Session session = EditorSupport.open(server,
                cat.name()); SqlExecutor exec = new SqlExecutor(session, new SqlLog(), null)) {
            ev.append("server: ").append(session.serverInfo().displayName()).append("\n\n");
            cat.execute(SCRIPT);
            ev.append("script di creazione (fuori dal generatore):\n").append(SCRIPT).append("\n\n");

            String prima = EditorSupport.showCreate(session, cat.name(), "misure");
            TableDef letta = EditorSupport.reread(session, cat.name(), "misure");
            ev.append("SHOW CREATE TABLE prima:\n").append(prima).append("\n\n");
            ev.append("advancedElements prima:\n  ").append(String.join("\n  ", letta.advancedElements()))
                    .append("\n\n");
            assertTrue(letta.advancedElements().stream().anyMatch(s -> s.toUpperCase(Locale.ROOT).contains("CHECK")),
                    "CHECK riconosciuto come elemento avanzato: " + letta.advancedElements());
            assertTrue(letta.advancedElements().stream().anyMatch(s -> s.toUpperCase(Locale.ROOT).contains("PARTITION")),
                    "partizioni riconosciute: " + letta.advancedElements());
            assertTrue(letta.column("doppio").orElseThrow().generated(), "colonna generata STORED");
            assertTrue(letta.column("meta").orElseThrow().generated(), "colonna generata VIRTUAL");

            // l'editor parte dal riletto e cambia solo «nota»
            TableDef modificata = letta.changeColumn("nota", c -> c.withTypeArgs("120").withComment("nota libera"));
            List<String> sql = TableDiff.diff(letta, modificata, session.serverInfo());
            ev.append("SQL generato e applicato:\n").append(EditorSupport.block(sql)).append("\n\n");
            assertEquals(1, sql.size());
            String alter = sql.get(0).toUpperCase(Locale.ROOT);
            assertTrue(alter.startsWith("ALTER TABLE") && alter.contains("MODIFY COLUMN `NOTA`"), sql.get(0));
            for (String vietato : List.of("CHECK", "PARTITION", "DOPPIO", "META", "GENERATED", "VALORE")) {
                assertFalse(alter.contains(vietato), "l'ALTER non tocca " + vietato + ": " + sql.get(0));
            }
            EditorSupport.apply(exec, "T5.3", sql);
            ev.append("esecuzione: OK\n\n");

            String dopo = EditorSupport.showCreate(session, cat.name(), "misure");
            TableDef riletta = EditorSupport.reread(session, cat.name(), "misure");
            ev.append("SHOW CREATE TABLE dopo:\n").append(dopo).append("\n\n");

            ColumnDef nota = riletta.column("nota").orElseThrow();
            assertEquals("VARCHAR(120)", nota.fullType());
            assertEquals("nota libera", nota.comment());
            List<String> restoPrima = senzaColonna(prima, "nota");
            List<String> restoDopo = senzaColonna(dopo, "nota");
            assertEquals(restoPrima, restoDopo, "SHOW CREATE TABLE identico al netto della colonna modificata");
            assertEquals(letta.advancedElements(), riletta.advancedElements(), "advancedElements identici");
            assertEquals(letta.column("doppio"), riletta.column("doppio"));
            assertEquals(letta.column("meta"), riletta.column("meta"));
            assertEquals(List.of(), TableDiff.diff(riletta, modificata,
                    session.serverInfo()), "dopo la rilettura non resta nulla da applicare");
            ev.append("confronto SHOW CREATE TABLE (tolta la riga di `nota`): ").append(restoPrima.size())
                    .append(" righe su ").append(restoPrima.size()).append(" identiche\n");
            ev.append("advancedElements identici (").append(riletta.advancedElements().size()).append("):\n  ")
                    .append(String.join("\n  ", riletta.advancedElements())).append('\n');
            ev.append("colonne generate `doppio` e `meta` identiche nel modello; `nota` = ")
                    .append(nota.fullType()).append(" COMMENT '").append(nota.comment()).append("'\n");
            ev.append("ESITO: OK\n");
        } catch (Throwable t) {
            ev.append("ESITO: FALLITO — ").append(t.getMessage()).append('\n');
            throw t;
        } finally {
            TestResults.write("step5", "T5.3-" + server.name().toLowerCase(Locale.ROOT) + ".txt", ev.toString());
        }
    }

    /** Le righe di {@code SHOW CREATE TABLE} senza quella della colonna data (e senza la virgola finale). */
    static List<String> senzaColonna(String ddl, String column) {
        List<String> out = new ArrayList<>();
        String prefix = "`" + column + "`";
        for (String line : ddl.replace("\r\n", "\n").split("\n")) {
            String l = line.strip();
            if (l.endsWith(",")) {
                l = l.substring(0, l.length() - 1);
            }
            if (!l.startsWith(prefix)) {
                out.add(l);
            }
        }
        return out;
    }
}
