/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.FkAction;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.IndexKind;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.verify.VerificationIssue.Kind;
import it.ramasql.core.verify.VerificationIssue.Severity;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** ADR-011 — {@link SchemaVerifier}: il confronto tra indici/FK chiesti e riletti, senza server. */
@Tag("step6")
class SchemaVerifierTest {

    /** Richiesto: PK, UNIQUE, INDEX a due colonne, FK con azioni esplicite. */
    static final TableDef LIBRI = TableDef.of("c", "libri")
            .addColumn(ColumnDef.of("id", "INT").notNull())
            .addColumn(ColumnDef.of("isbn", "CHAR", "13").notNull())
            .addColumn(ColumnDef.of("titolo", "VARCHAR", "100").notNull())
            .addColumn(ColumnDef.of("anno", "SMALLINT"))
            .addColumn(ColumnDef.of("id_editore", "INT"))
            .withPrimaryKey("id")
            .addIndex(IndexDef.unique("uq_isbn", "isbn"))
            .addIndex(IndexDef.index("ix_titolo_anno", "titolo", "anno"))
            .addForeignKey(ForeignKeyDef.of("fk_libri_editori", "id_editore", "editori", "id")
                    .withActions(FkAction.RESTRICT, FkAction.CASCADE))
            .withEngine("InnoDB")
            .withOrdinalPositions();

    /** Come il server lo rilegge davvero: con l'indice creato da sé per la FK. */
    static final TableDef LETTO = LIBRI.addIndex(IndexDef.index("fk_libri_editori", "id_editore"));

    private static List<Kind> kinds(Verification v) {
        return v.issues().stream().map(VerificationIssue::kind).toList();
    }

    @Test
    void identicoEConforme() {
        Verification v = SchemaVerifier.verify(LIBRI, LIBRI);
        assertTrue(v.conforming());
        assertTrue(v.issues().isEmpty());
        assertTrue(v.summary().startsWith("✔ verificato sul server"), v.summary());
    }

    @Test
    void indiceImplicitoDellaFkEUnaInformazione() {
        Verification v = SchemaVerifier.verify(LIBRI, LETTO);
        assertTrue(v.conforming(), v::summary);
        assertEquals(List.of(Kind.INDEX_IMPLICIT_FOR_FK), kinds(v));
        assertEquals(Severity.INFO, v.issues().get(0).severity());
        assertTrue(v.issues().get(0).message().contains("fk_libri_editori"), v.issues().get(0).message());
    }

    @Test
    void maiuscoleEMinuscoleNonContano() {
        TableDef server = TableDef.of("C", "LIBRI").withColumns(LIBRI.columns())
                .withIndexes(List.of(IndexDef.primary("ID"), IndexDef.unique("UQ_ISBN", "Isbn"),
                        IndexDef.index("Ix_Titolo_Anno", "TITOLO", "anno")))
                .withForeignKeys(List.of(ForeignKeyDef.of("FK_LIBRI_EDITORI", "ID_EDITORE", "Editori", "ID")
                        .withActions(FkAction.RESTRICT, FkAction.CASCADE)));
        assertTrue(SchemaVerifier.verify(LIBRI, server).issues().isEmpty());
    }

    @Test
    void indiceMancanteEChiavePrimariaMancante() {
        TableDef server = LETTO.removeIndex("uq_isbn").withPrimaryKey();
        Verification v = SchemaVerifier.verify(LIBRI, server);
        assertFalse(v.conforming());
        assertEquals(List.of(Kind.INDEX_MISSING, Kind.INDEX_MISSING, Kind.INDEX_IMPLICIT_FOR_FK), kinds(v));
        assertTrue(v.blocking().get(0).message().contains("chiave primaria"), v.blocking().get(0).message());
        assertTrue(v.blocking().get(1).message().contains("uq_isbn"));
    }

    @Test
    void nomeAssegnatoDalServerAUnIndice() {
        TableDef server = LETTO.changeIndex("uq_isbn", i -> i.withName("isbn"));
        Verification v = SchemaVerifier.verify(LIBRI, server);
        assertFalse(v.conforming());
        assertEquals(Kind.INDEX_NAME_DIFFERENT, v.blocking().get(0).kind());
        assertTrue(v.blocking().get(0).message().contains("«isbn»"), v.blocking().get(0).message());
    }

    @Test
    void ordineDelleColonneEColonneDiverse() {
        Verification ordine = SchemaVerifier.verify(LIBRI,
                LETTO.changeIndex("ix_titolo_anno", i -> i.withColumns("anno", "titolo")));
        assertEquals(List.of(Kind.INDEX_COLUMN_ORDER), kinds(ordine).subList(0, 1));
        assertFalse(ordine.conforming());
        Verification colonne = SchemaVerifier.verify(LIBRI,
                LETTO.changeIndex("ix_titolo_anno", i -> i.withColumns("titolo")));
        assertEquals(Kind.INDEX_COLUMNS, colonne.blocking().get(0).kind());
        Verification pk = SchemaVerifier.verify(LIBRI, LETTO.withPrimaryKey("id", "isbn"));
        assertEquals(Kind.INDEX_COLUMNS, pk.blocking().get(0).kind());
    }

    @Test
    void unicitaDiversa() {
        Verification v = SchemaVerifier.verify(LIBRI, LETTO.changeIndex("uq_isbn", i -> i.withKind(IndexKind.INDEX)));
        assertEquals(List.of(Kind.INDEX_UNIQUENESS), v.blocking().stream().map(VerificationIssue::kind).toList());
        assertTrue(v.blocking().get(0).message().contains("UNIQUE"));
    }

    @Test
    void indiceNonChiestoEIndiceNonEliminato() {
        TableDef extra = LETTO.addIndex(IndexDef.index("ix_anno", "anno"));
        assertEquals(Kind.INDEX_UNEXPECTED, SchemaVerifier.verify(LIBRI, extra).blocking().get(0).kind());
        // l'utente ha chiesto di eliminare ix_anno ma il server ce l'ha ancora
        Verification v = SchemaVerifier.verify(extra, LETTO, extra);
        assertEquals(Kind.INDEX_UNEXPECTED, v.blocking().get(0).kind());
        assertTrue(v.blocking().get(0).message().contains("doveva essere eliminato"), v.blocking().get(0).message());
    }

    @Test
    void azioneDiversaEBloccante() {
        TableDef alterata = LETTO.changeForeignKey("fk_libri_editori", f -> f.withOnDelete(FkAction.CASCADE));
        Verification v = SchemaVerifier.verify(LIBRI, alterata);
        assertFalse(v.conforming());
        assertEquals(List.of(Kind.FK_ON_DELETE), v.blocking().stream().map(VerificationIssue::kind).toList());
        assertEquals("La chiave esterna «fk_libri_editori» ha un'altra azione ON DELETE: chiesta RESTRICT, sul server "
                + "CASCADE.", v.blocking().get(0).message());
        TableDef update = LETTO.changeForeignKey("fk_libri_editori", f -> f.withOnUpdate(FkAction.SET_NULL));
        assertEquals(Kind.FK_ON_UPDATE, SchemaVerifier.verify(LIBRI, update).blocking().get(0).kind());
    }

    @Test
    void restrictENoActionSonoEquivalentiSuInnoDb() {
        TableDef server = LETTO.changeForeignKey("fk_libri_editori", f -> f.withOnDelete(FkAction.NO_ACTION));
        Verification v = SchemaVerifier.verify(LIBRI, server);
        assertTrue(v.conforming(), v::summary);
        assertTrue(v.has(Kind.FK_ACTION_EQUIVALENT));
        TableDef inverso = LETTO.changeForeignKey("fk_libri_editori",
                f -> f.withActions(FkAction.NO_ACTION, FkAction.CASCADE));
        assertTrue(SchemaVerifier.verify(inverso, server.changeForeignKey("fk_libri_editori",
                f -> f.withOnDelete(FkAction.RESTRICT))).conforming());
        // NO ACTION contro CASCADE resta una differenza vera
        assertFalse(SchemaVerifier.verify(inverso, LETTO.changeForeignKey("fk_libri_editori",
                f -> f.withOnDelete(FkAction.CASCADE))).conforming());
    }

    @Test
    void tabellaEColonneRiferiteDiverse() {
        TableDef tabella = LETTO.changeForeignKey("fk_libri_editori",
                f -> f.withReference(null, "case_editrici", List.of("id")));
        assertEquals(Kind.FK_REFERENCED_TABLE, SchemaVerifier.verify(LIBRI, tabella).blocking().get(0).kind());
        TableDef colonne = LETTO.changeForeignKey("fk_libri_editori",
                f -> f.withReference(null, "editori", List.of("codice")));
        assertEquals(Kind.FK_REFERENCED_COLUMNS, SchemaVerifier.verify(LIBRI, colonne).blocking().get(0).kind());
        TableDef catalogo = LETTO.changeForeignKey("fk_libri_editori",
                f -> f.withReference("altro", "editori", List.of("id")));
        assertEquals(Kind.FK_REFERENCED_TABLE, SchemaVerifier.verify(LIBRI, catalogo).blocking().get(0).kind());
        // lo stesso catalogo scritto per esteso non è una differenza
        TableDef esplicito = LETTO.changeForeignKey("fk_libri_editori",
                f -> f.withReference("c", "editori", List.of("id")));
        assertTrue(SchemaVerifier.verify(LIBRI, esplicito).conforming());
    }

    @Test
    void fkMancanteSuMyIsamSpiegaLEngine() {
        TableDef myisam = LIBRI.withForeignKeys(List.of()).withEngine("MyISAM")
                .addIndex(IndexDef.index("fk_libri_editori", "id_editore"));
        Verification v = SchemaVerifier.verify(LIBRI.withEngine("MyISAM"), myisam);
        assertFalse(v.conforming());
        assertEquals(List.of(Kind.FK_MISSING), v.blocking().stream().map(VerificationIssue::kind).toList());
        assertTrue(v.has(Kind.INDEX_IMPLICIT_FOR_FK), "l'indice creato per la FK ignorata è solo un'informazione");
        assertTrue(v.blocking().get(0).message().contains("MyISAM"), v.blocking().get(0).message());
    }

    @Test
    void fkSenzaNomeENomeDiverso() {
        TableDef chiesta = LIBRI.withForeignKeys(List.of(LIBRI.foreignKeys().get(0).withName(null)));
        TableDef server = LETTO.changeForeignKey("fk_libri_editori", f -> f.withName("libri_ibfk_1"))
                .changeIndex("fk_libri_editori", i -> i.withName("id_editore"));
        Verification v = SchemaVerifier.verify(chiesta, server);
        assertTrue(v.conforming(), v::summary);
        assertTrue(v.has(Kind.FK_NAME_ASSIGNED));
        assertTrue(v.informational().get(v.informational().size() - 1).message().contains("libri_ibfk_1"));

        Verification nome = SchemaVerifier.verify(LIBRI, server);
        assertFalse(nome.conforming());
        assertEquals(Kind.FK_NAME_DIFFERENT, nome.blocking().get(0).kind());
    }

    @Test
    void fkNonChiestaENonEliminata() {
        TableDef senza = LIBRI.withForeignKeys(List.of());
        Verification v = SchemaVerifier.verify(senza, LETTO);
        assertEquals(Kind.FK_UNEXPECTED, v.blocking().get(0).kind());
        Verification nonEliminata = SchemaVerifier.verify(LETTO, LETTO.withForeignKeys(List.of()), LETTO);
        assertEquals(List.of(Kind.FK_UNEXPECTED), nonEliminata.blocking().stream().map(VerificationIssue::kind).toList());
        assertTrue(nonEliminata.blocking().get(0).message().contains("doveva essere eliminata"));
    }

    @Test
    void indiceRimastoDopoLEliminazioneDellaFk() {
        TableDef server = LETTO.withForeignKeys(List.of());
        Verification v = SchemaVerifier.verify(LIBRI, LIBRI.withForeignKeys(List.of()), server);
        assertTrue(v.conforming(), v::summary);
        assertEquals(List.of(Kind.INDEX_LEFT_BY_DROPPED_FK), kinds(v));
    }

    @Test
    void fkAutoreferenzialeDiUnaTabellaRinominata() {
        TableDef categorie = TableDef.of("c", "categorie")
                .addColumn(ColumnDef.of("id", "INT").notNull()).addColumn(ColumnDef.of("id_padre", "INT"))
                .withPrimaryKey("id")
                .addForeignKey(ForeignKeyDef.of("fk_padre", "id_padre", "categorie", "id"));
        TableDef rinominata = categorie.withName("generi");
        TableDef server = rinominata.changeForeignKey("fk_padre", f -> f.withReference(null, "generi", List.of("id")))
                .addIndex(IndexDef.index("fk_padre", "id_padre"));
        assertTrue(SchemaVerifier.verify(categorie, rinominata, server).conforming());
        assertFalse(SchemaVerifier.verify(rinominata, server).conforming(), "senza l'originale non si può sapere");
    }

    @Test
    void tabellaAssente() {
        Verification v = SchemaVerifier.verify(LIBRI, null);
        assertEquals(List.of(Kind.TABLE_MISSING), kinds(v));
        assertFalse(v.conforming());
        assertTrue(v.summary().contains("✘"), v.summary());
    }
}
