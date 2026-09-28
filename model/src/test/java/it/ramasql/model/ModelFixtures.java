/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.model;

import java.util.List;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;

/** La «biblioteca» come definizioni di tabelle, con e senza chiavi esterne (senza server). */
final class ModelFixtures {

    private ModelFixtures() {
    }

    private static ColumnDef id() {
        return ColumnDef.of("id", "INT").withUnsigned(true).withNullable(false).withAutoIncrement(true);
    }

    private static ColumnDef ref(String name, boolean nullable) {
        return ColumnDef.of(name, "INT").withUnsigned(true).withNullable(nullable);
    }

    static List<TableDef> biblioteca(boolean withForeignKeys) {
        String engine = withForeignKeys ? "InnoDB" : "MyISAM";
        TableDef editori = TableDef.of("bib", "editori").withEngine(engine).withColumns(List.of(id(),
                ColumnDef.of("nome", "VARCHAR", "80").withNullable(false), ColumnDef.of("citta", "VARCHAR", "60")))
                .withIndexes(List.of(IndexDef.primary("id"), IndexDef.unique("uq_editori_nome", "nome")));
        TableDef autori = TableDef.of("bib", "autori").withEngine(engine).withColumns(List.of(id(),
                ColumnDef.of("cognome", "VARCHAR", "60").withNullable(false),
                ColumnDef.of("nome", "VARCHAR", "60").withNullable(false),
                ColumnDef.of("nazionalita", "VARCHAR", "40").withNullable(false)))
                .withIndexes(List.of(IndexDef.primary("id")));
        TableDef libri = TableDef.of("bib", "libri").withEngine(engine).withColumns(List.of(id(),
                ColumnDef.of("titolo", "VARCHAR", "150").withNullable(false), ColumnDef.of("isbn", "CHAR", "13"),
                ColumnDef.of("anno", "SMALLINT").withUnsigned(true), ColumnDef.of("prezzo", "DECIMAL", "6,2"),
                ref("id_editore", true)))
                .withIndexes(List.of(IndexDef.primary("id"), IndexDef.unique("uq_libri_isbn", "isbn"),
                        IndexDef.index("ix_libri_editore", "id_editore")));
        TableDef libriAutori = TableDef.of("bib", "libri_autori").withEngine(engine).withColumns(List.of(
                ref("id_libro", false), ref("id_autore", false)))
                .withIndexes(List.of(IndexDef.primary("id_libro", "id_autore")));
        TableDef soci = TableDef.of("bib", "soci").withEngine(engine).withColumns(List.of(id(),
                ColumnDef.of("tessera", "CHAR", "8").withNullable(false),
                ColumnDef.of("cognome", "VARCHAR", "60").withNullable(false),
                ColumnDef.of("nome", "VARCHAR", "60").withNullable(false), ColumnDef.of("email", "VARCHAR", "120"),
                ColumnDef.of("nato_il", "DATE")))
                .withIndexes(List.of(IndexDef.primary("id"), IndexDef.unique("uq_soci_tessera", "tessera")));
        TableDef prestiti = TableDef.of("bib", "prestiti").withEngine(engine).withColumns(List.of(id(),
                ref("id_libro", false), ref("id_socio", false), ColumnDef.of("data_prestito", "DATE").withNullable(false),
                ColumnDef.of("data_reso", "DATE")))
                .withIndexes(List.of(IndexDef.primary("id")));
        if (withForeignKeys) {
            libri = libri.withForeignKeys(List.of(ForeignKeyDef.of("fk_libri_editori", "id_editore", "editori", "id")));
            libriAutori = libriAutori.withForeignKeys(List.of(
                    ForeignKeyDef.of("fk_libri_autori_libri", "id_libro", "libri", "id")
                            .withActions(FkAction.CASCADE, FkAction.CASCADE),
                    ForeignKeyDef.of("fk_libri_autori_autori", "id_autore", "autori", "id")
                            .withActions(FkAction.CASCADE, FkAction.CASCADE)));
            prestiti = prestiti.withForeignKeys(List.of(
                    ForeignKeyDef.of("fk_prestiti_libri", "id_libro", "libri", "id"),
                    ForeignKeyDef.of("fk_prestiti_soci", "id_socio", "soci", "id")));
        }
        return List.of(editori, autori, libri, libriAutori, soci, prestiti);
    }
}
