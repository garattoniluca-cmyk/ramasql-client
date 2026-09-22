/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 *
 * Riscritta da zero il 2026-09-22 per sostituire un file GPL-2.0-only ereditato da SQLeo; scritta a partire
 * dall'uso nel resto del modulo, senza consultare l'originale.
 */
package com.sqleo.querybuilder.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.List;

/**
 * Oggetto trascinato all'interno della stessa JVM (dalla lista degli oggetti al diagramma, da un campo a un altro
 * campo per creare un join).
 *
 * <p>I bersagli del trascinamento ({@link EntityDropTargetListener}, {@link RelationDropTargetListener}) riconoscono
 * il contenuto con il flavor {@code new DataFlavor(Classe.class, Classe.class.getName())} della classe attesa
 * ({@code Entity}, {@code EntityField}). Qui si offrono, per la classe dell'oggetto e per ciascuna delle sue
 * superclassi (esclusa {@code Object}), quel flavor e il flavor «oggetto locale alla JVM»; per tutti
 * {@link #getTransferData} restituisce l'oggetto stesso, senza copie né serializzazione.
 */
public class TransferableObject implements Transferable {

    private final Object object;
    private final DataFlavor[] flavors;

    /** @param object oggetto trascinato; con {@code null} non si offre alcun flavor. */
    public TransferableObject(Object object) {
        this.object = object;
        this.flavors = flavorsFor(object);
    }

    private static DataFlavor[] flavorsFor(Object object) {
        List<DataFlavor> list = new ArrayList<>();
        if (object != null) {
            for (Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                list.add(new DataFlavor(c, c.getName()));
                list.add(localFlavor(c));
            }
        }
        return list.toArray(new DataFlavor[0]);
    }

    private static DataFlavor localFlavor(Class<?> c) {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + c.getName() + "\"",
                    c.getName(), c.getClassLoader());
        } catch (ClassNotFoundException e) {
            // la classe è quella dell'oggetto in mano: non può mancare
            throw new IllegalStateException(e);
        }
    }

    /** L'oggetto trascinato (può essere {@code null}). */
    public Object getObject() {
        return object;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return flavors.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if (flavor == null) {
            return false;
        }
        for (DataFlavor f : flavors) {
            if (f.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return object;
    }
}
