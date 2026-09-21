/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.data;

import static it.ramasql.core.data.ClipboardBlock.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ramasql.core.data.PendingChanges.PasteResult;
import it.ramasql.core.data.PendingChanges.RowKind;
import it.ramasql.core.data.PendingChanges.State;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ColumnDefault;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.core.sqlgen.DmlGenerator;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Modello a modifiche pendenti del data-entry (Step 4): nessuna scrittura finché non si conferma. */
@Tag("step4")
class PendingChangesTest {

    static final TableDef AUTORI = TableDef.of(null, "autori")
            .addColumn(ColumnDef.of("id", "INT").notNull().withAutoIncrement(true))
            .addColumn(ColumnDef.of("cognome", "VARCHAR", "50").notNull())
            .addColumn(ColumnDef.of("nome", "VARCHAR", "50"))
            .addColumn(ColumnDef.of("nascita", "DATE"))
            .addColumn(ColumnDef.of("paese", "CHAR", "2").notNull().withDefault(ColumnDefault.literal("IT")))
            .withPrimaryKey("id");

    PendingChanges griglia;

    @BeforeEach
    void carica() {
        griglia = new PendingChanges(AUTORI.columns(), List.of(
                Arrays.asList("1", "Calvino", "Italo", "1923-10-15", "IT"),
                Arrays.asList("2", "Eco", "Umberto", null, "IT"),
                Arrays.asList("3", "Woolf", "Virginia", "1882-01-25", "GB")));
    }

    @Test
    void appenaCaricataNonHaNullaInSospeso() {
        assertEquals(3, griglia.rowCount());
        assertFalse(griglia.hasPending());
        assertFalse(griglia.canConfirm());
        assertEquals("0 inserimenti · 0 modifiche · 0 eliminazioni", griglia.summary());
        assertEquals(List.of(), griglia.toRowChanges());
        assertEquals(State.SALVATA, griglia.state(0));
        assertNull(griglia.value(1, 3));
    }

    @Test
    void modificaProduceUpdateDelleSoleColonneCambiate() {
        griglia.setValue(1, 3, "1932-01-05");
        assertEquals(RowKind.MODIFIED, griglia.kind(1));
        assertEquals(State.PENDENTE, griglia.state(1));
        assertTrue(griglia.isCellModified(1, 3));
        assertFalse(griglia.isCellModified(1, 1));
        assertEquals("0 inserimenti · 1 modifica · 0 eliminazioni", griglia.summary());
        assertEquals(List.of("UPDATE `autori` SET `nascita` = '1932-01-05' WHERE `id` = 2"),
                DmlGenerator.generate(AUTORI, griglia.toRowChanges()));
    }

    @Test
    void riportareIlValoreOriginaleRitiraLaModifica() {
        griglia.setValue(0, 1, "Calvino!");
        griglia.setValue(0, 1, "Calvino");
        assertEquals(RowKind.UNCHANGED, griglia.kind(0));
        assertFalse(griglia.hasPending());
    }

    @Test
    void rigaNuovaOmetteLeCelleNonImpostateELaRigaVuotaNonConta() {
        int r = griglia.addRow();
        assertEquals(RowKind.INSERTED, griglia.kind(r));
        assertFalse(griglia.hasPending(), "la riga d'inserimento vuota non è una modifica");
        griglia.setValue(r, 1, "Morante");
        griglia.setValue(r, 2, null);
        assertTrue(griglia.isSet(r, 2));
        assertFalse(griglia.isSet(r, 0));
        assertEquals("1 inserimento · 0 modifiche · 0 eliminazioni", griglia.summary());
        assertEquals(List.of("INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Morante', NULL)"),
                DmlGenerator.generate(AUTORI, griglia.toRowChanges()));
        griglia.clearValue(r, 2);
        assertFalse(griglia.isSet(r, 2));
    }

    @Test
    void eliminazioneERipristino() {
        griglia.deleteRow(2);
        assertEquals(RowKind.DELETED, griglia.kind(2));
        assertEquals("0 inserimenti · 0 modifiche · 1 eliminazione", griglia.summary());
        assertEquals(List.of("DELETE FROM `autori` WHERE `id` = 3"),
                DmlGenerator.generate(AUTORI, griglia.toRowChanges()));
        griglia.setValue(2, 1, "ignorata");
        assertEquals("Woolf", griglia.value(2, 1), "una riga eliminata non si modifica");
        griglia.restoreRow(2);
        assertFalse(griglia.hasPending());

        int nuova = griglia.addRow();
        griglia.setValue(nuova, 1, "Provvisorio");
        griglia.deleteRow(nuova);
        assertEquals(3, griglia.rowCount(), "eliminare una riga nuova la toglie e basta");
        assertFalse(griglia.hasPending());
    }

    @Test
    void contatoriMistiEScarta() {
        griglia.setValue(0, 2, "I.");
        griglia.setValue(1, 2, "U.");
        griglia.deleteRow(2);
        for (String cognome : List.of("Levi", "Ginzburg", "Buzzati")) {
            griglia.setValue(griglia.addRow(), 1, cognome);
        }
        assertEquals("3 inserimenti · 2 modifiche · 1 eliminazione", griglia.summary());
        assertEquals(3, griglia.insertCount());
        assertEquals(2, griglia.updateCount());
        assertEquals(1, griglia.deleteCount());
        assertEquals(6, griglia.toRowChanges().size());

        griglia.discard();
        assertEquals(3, griglia.rowCount());
        assertFalse(griglia.hasPending());
        assertEquals("Italo", griglia.value(0, 2));
        assertEquals(RowKind.UNCHANGED, griglia.kind(2));
    }

    @Test
    void incollaOltreLUltimaRigaCreaRigheNuoveEdEUnaSolaOperazionePerAnnulla() {
        ClipboardBlock blocco = ClipboardBlock.parse("Levi\tPrimo\t1919-07-31\r\nGinzburg\tNatalia\t\r\nBuzzati\tDino\t1906-10-16\r\n");
        PasteResult esito = griglia.paste(3, 1, blocco);
        assertEquals(new PasteResult(9, 3, 0, 0), esito);
        assertEquals(6, griglia.rowCount());
        assertEquals("3 inserimenti · 0 modifiche · 0 eliminazioni", griglia.summary());
        assertFalse(griglia.isSet(4, 3), "cella vuota su riga nuova = non impostata (DEFAULT)");
        assertEquals(List.of(
                "INSERT INTO `autori` (`cognome`, `nome`, `nascita`) VALUES ('Levi', 'Primo', '1919-07-31')",
                "INSERT INTO `autori` (`cognome`, `nome`) VALUES ('Ginzburg', 'Natalia')",
                "INSERT INTO `autori` (`cognome`, `nome`, `nascita`) VALUES ('Buzzati', 'Dino', '1906-10-16')"),
                DmlGenerator.generate(AUTORI, griglia.toRowChanges()));

        assertTrue(griglia.canUndoPaste());
        assertTrue(griglia.undoLastPaste());
        assertEquals(3, griglia.rowCount());
        assertFalse(griglia.hasPending());
        assertFalse(griglia.undoLastPaste(), "un solo livello di annullamento");
    }

    @Test
    void incollaSopraCelleEsistentiProduceUpdateEAnnullaRipristinaSoloQuelle() {
        griglia.setValue(2, 4, "UK");                       // modifica a mano, precedente all'incolla
        PasteResult esito = griglia.paste(0, 1, ClipboardBlock.of(row("CALVINO", "I."), row("ECO", null)));
        assertEquals(new PasteResult(4, 0, 0, 0), esito);
        assertNull(griglia.value(1, 2), "cella vuota su riga esistente = NULL");
        assertEquals(List.of(
                "UPDATE `autori` SET `cognome` = 'CALVINO', `nome` = 'I.' WHERE `id` = 1",
                "UPDATE `autori` SET `cognome` = 'ECO', `nome` = NULL WHERE `id` = 2",
                "UPDATE `autori` SET `paese` = 'UK' WHERE `id` = 3"),
                DmlGenerator.generate(AUTORI, griglia.toRowChanges()));

        griglia.undoLastPaste();
        assertEquals("Calvino", griglia.value(0, 1));
        assertEquals("Umberto", griglia.value(1, 2));
        assertEquals("UK", griglia.value(2, 4), "la modifica a mano sopravvive all'annullamento dell'incolla");
        assertEquals("0 inserimenti · 1 modifica · 0 eliminazioni", griglia.summary());
    }

    @Test
    void incollaPiuLargoDellaTabellaScartaLEccedenzaESaltaAutoIncrement() {
        PasteResult largo = griglia.paste(0, 3, ClipboardBlock.of(row("1930-01-01", "FR", "di troppo", "anche")));
        assertEquals(new PasteResult(2, 0, 2, 0), largo);
        assertEquals("FR", griglia.value(0, 4));

        PasteResult suId = griglia.paste(1, 0, ClipboardBlock.of(row("99", "Eco!")));
        assertEquals(new PasteResult(1, 0, 0, 1), suId);
        assertEquals("2", griglia.value(1, 0), "la colonna AUTO_INCREMENT non si incolla");
        assertEquals("Eco!", griglia.value(1, 1));
    }

    @Test
    void unValoreSuUnaSelezioneLaRiempieTutta() {
        PasteResult esito = griglia.fill(0, 2, 4, 4, "FR");
        assertEquals(new PasteResult(3, 0, 0, 0), esito);
        assertEquals(3, griglia.updateCount());
        griglia.undoLastPaste();
        assertFalse(griglia.hasPending());
    }

    @Test
    void leCelleNonValideBloccanoLaConferma() {
        griglia.paste(3, 1, ClipboardBlock.of(row("Levi", "Primo", "31/07/1919", "ITA")));
        assertTrue(griglia.hasPending());
        assertFalse(griglia.canConfirm());
        assertEquals(2, griglia.invalidCells().size());
        assertTrue(griglia.validationError(3, 3).orElseThrow().contains("Data non valida"));
        assertTrue(griglia.validationError(3, 4).orElseThrow().contains("al massimo 2 caratteri"));

        griglia.setValue(3, 3, "1919-07-31");
        griglia.setValue(3, 4, "IT");
        assertTrue(griglia.canConfirm());
    }

    @Test
    void rigaNuovaSenzaUnCampoObbligatorioENullSuNotNull() {
        int r = griglia.addRow();
        griglia.setValue(r, 2, "Senza cognome");
        assertTrue(griglia.validationError(r, 1).orElseThrow().contains("«cognome» non ammette NULL"));
        assertTrue(griglia.validationError(r, 0).isEmpty(), "AUTO_INCREMENT lasciato vuoto va bene");
        assertTrue(griglia.validationError(r, 4).isEmpty(), "ha un DEFAULT");

        griglia.setValue(r, 1, "Deledda");
        griglia.setValue(r, 4, null);
        assertTrue(griglia.validationError(r, 4).isPresent(), "NULL esplicito su NOT NULL, anche se ha un DEFAULT");
        griglia.setValue(0, 1, null);
        assertTrue(griglia.validationError(0, 1).isPresent());
        assertTrue(griglia.validationError(0, 2).isEmpty(), "le celle non toccate non si validano");
    }

    @Test
    void esecuzioneRigaPerRigaSalvataInErrorePendente() {
        long primo = griglia.rowId(griglia.addRow());
        long secondo = griglia.rowId(griglia.addRow());
        long terzo = griglia.rowId(griglia.addRow());
        griglia.setValue(griglia.indexOf(primo), 1, "Levi");
        griglia.setValue(griglia.indexOf(secondo), 1, "Eco");
        griglia.setValue(griglia.indexOf(terzo), 1, "Buzzati");

        griglia.markSaved(primo, Map.of("id", "4", "paese", "IT"));
        griglia.markError(secondo, "Errore 1062: valore duplicato");

        assertEquals(State.SALVATA, griglia.state(griglia.indexOf(primo)));
        assertEquals("4", griglia.value(griglia.indexOf(primo), 0), "l'id AUTO_INCREMENT compare in griglia");
        assertEquals(RowKind.UNCHANGED, griglia.kind(griglia.indexOf(primo)));
        assertEquals(State.IN_ERRORE, griglia.state(griglia.indexOf(secondo)));
        assertEquals("Errore 1062: valore duplicato", griglia.errorMessage(griglia.indexOf(secondo)).orElseThrow());
        assertEquals(State.PENDENTE, griglia.state(griglia.indexOf(terzo)));
        assertEquals("2 inserimenti · 0 modifiche · 0 eliminazioni", griglia.summary());
        assertEquals(2, griglia.toRowChanges().size(), "la riga in errore resta da eseguire");

        griglia.setValue(griglia.indexOf(secondo), 1, "Eco bis");
        assertEquals(State.PENDENTE, griglia.state(griglia.indexOf(secondo)), "correggere la riga toglie l'errore");

        griglia.discard();
        assertEquals(4, griglia.rowCount(), "Scarta conserva ciò che è già stato scritto sul server");
        assertEquals("Levi", griglia.value(3, 1));
    }

    @Test
    void updateEDeleteSalvati() {
        griglia.setValue(0, 2, "I.");
        griglia.deleteRow(1);
        List<RowChange> modifiche = griglia.toRowChanges();
        for (RowChange m : modifiche) {
            griglia.markSaved(m.rowId());
        }
        assertEquals(2, griglia.rowCount(), "la riga eliminata sparisce");
        assertFalse(griglia.hasPending());
        assertEquals("I.", griglia.value(0, 2));
        griglia.discard();
        assertEquals("I.", griglia.value(0, 2));
        assertEquals("Woolf", griglia.value(1, 1));
    }

    @Test
    void copiaDiUnBloccoConNull() {
        assertEquals("Eco\tUmberto\t\r\nWoolf\tVirginia\t1882-01-25\r\n", griglia.copy(1, 2, 1, 3).toText());
    }
}
