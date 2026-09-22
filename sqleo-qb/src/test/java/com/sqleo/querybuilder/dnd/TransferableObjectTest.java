/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package com.sqleo.querybuilder.dnd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.Arrays;
import java.util.List;

import javax.swing.JList;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.sqleo.querybuilder.beans.Entity;
import com.sqleo.querybuilder.beans.EntityField;
import com.sqleo.querybuilder.syntax.QueryTokens;

/** {@link TransferableObject}, riscritto da zero il 2026-09-22: i flavor che si aspettano i bersagli del trascinamento. */
@Tag("step1")
class TransferableObjectTest {

    /** Gli stessi flavor costruiti da {@code EntityDropTargetListener} e {@code RelationDropTargetListener}. */
    private static final DataFlavor ENTITY = new DataFlavor(Entity.class, Entity.class.getName());
    private static final DataFlavor ENTITY_FIELD = new DataFlavor(EntityField.class, EntityField.class.getName());

    @Test
    void unEntitaSiRiconosceESiRestituisceTalQuale() throws Exception {
        Entity libri = new Entity("biblioteca", "libri");
        TransferableObject t = new TransferableObject(libri);

        List<DataFlavor> flavors = Arrays.asList(t.getTransferDataFlavors());
        assertTrue(flavors.contains(ENTITY), "flavor atteso dal bersaglio delle tabelle: " + flavors);
        assertTrue(t.isDataFlavorSupported(ENTITY));
        assertSame(libri, t.getTransferData(ENTITY));
        for (DataFlavor f : flavors) {
            assertSame(libri, t.getTransferData(f), f.toString());
        }
        assertFalse(t.isDataFlavorSupported(ENTITY_FIELD));
        assertSame(libri, t.getObject());
    }

    @Test
    void unCampoSiRiconosceESiRestituisceTalQuale() throws Exception {
        EntityField campo = new EntityField();
        campo.setTable(new QueryTokens.Table("biblioteca", "libri"));
        campo.setFieldName("titolo");
        TransferableObject t = new TransferableObject(campo);

        assertTrue(Arrays.asList(t.getTransferDataFlavors()).contains(ENTITY_FIELD));
        assertSame(campo, t.getTransferData(ENTITY_FIELD));
        assertFalse(t.isDataFlavorSupported(ENTITY));
    }

    @Test
    void flavorNonSupportatoLanciaUnsupportedFlavorException() {
        TransferableObject t = new TransferableObject(new Entity(null, "libri"));
        assertFalse(t.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertFalse(t.isDataFlavorSupported(null));
        assertThrows(UnsupportedFlavorException.class, () -> t.getTransferData(DataFlavor.stringFlavor));
        assertThrows(UnsupportedFlavorException.class, () -> t.getTransferData(ENTITY_FIELD));
    }

    @Test
    void senzaOggettoNessunFlavor() {
        TransferableObject t = new TransferableObject(null);
        assertEquals(0, t.getTransferDataFlavors().length);
        assertThrows(UnsupportedFlavorException.class, () -> t.getTransferData(ENTITY));
    }

    @Test
    void laCopiaDeiFlavorNonAlteraIlTransferable() {
        TransferableObject t = new TransferableObject(new Entity(null, "libri"));
        t.getTransferDataFlavors()[0] = DataFlavor.stringFlavor;
        assertTrue(t.isDataFlavorSupported(ENTITY));
        assertFalse(t.isDataFlavorSupported(DataFlavor.stringFlavor));
    }

    /** Come fa la lista degli oggetti quando si comincia a trascinare una tabella verso il diagramma. */
    @Test
    void ilGestoreDellaListaProduceCioCheIlDiagrammaAccetta() throws Exception {
        Entity autori = new Entity("biblioteca", "autori");
        JList<Entity> lista = new JList<>(new Entity[] {new Entity("biblioteca", "libri"), autori});
        lista.setSelectedIndex(1);

        Transferable t = new EntityTransferHandler().createTransferable(lista);

        assertInstanceOf(TransferableObject.class, t);
        assertTrue(Arrays.asList(t.getTransferDataFlavors()).contains(ENTITY));
        Entity trascinata = (Entity) t.getTransferData(ENTITY);
        assertSame(autori, trascinata);
        assertEquals("autori", trascinata.getEntityName());
    }
}
