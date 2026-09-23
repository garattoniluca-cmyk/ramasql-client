/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.grid;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Il testo tabulato di un blocco di celle, pubblicato negli appunti <b>senza alterare gli a-capo dentro le celle</b>
 * (BUG-002).
 *
 * <h2>Il problema</h2>
 * Java su Windows, per <i>ogni</i> flavor di testo con charset ({@code stringFlavor}, ma anche
 * {@code text/plain;charset=utf-16} su {@code InputStream} o {@code ByteBuffer}), passa il testo da
 * {@code DataTransferer.translateTransferableString}, che trasforma ogni LF isolato in CR+LF prima di scriverlo nel
 * formato nativo {@code CF_UNICODETEXT}. Così l'a-capo dentro una cella ({@code "riga uno\nriga due"}) arriva in
 * Excel come CR+LF. Provato: il rimedio «flavor utf-16 su InputStream» <b>non basta</b> (stesso CR+LF), perché la
 * conversione dipende dal fatto che il flavor sia di tipo testo, non dalla classe che lo rappresenta.
 *
 * <h2>Il rimedio</h2>
 * Un flavor <b>non testuale</b> ({@code application/x-ramasql-unicode-text}, byte UTF-16LE con il terminatore
 * {@code \0}) collegato nella {@link SystemFlavorMap} al formato nativo {@code UNICODE TEXT}: i byte arrivano a
 * Windows così come sono. Windows ricava da solo gli altri formati di testo (ANSI, OEM). Il flavor è l'unico
 * dichiarato da {@link #getTransferDataFlavors()}: se ci fosse anche un flavor {@code text/plain}, Java lo
 * preferirebbe per lo stesso formato nativo e tornerebbe il CR+LF. {@link DataFlavor#stringFlavor} resta comunque
 * <i>leggibile</i> ({@link #isDataFlavorSupported}, {@link #getTransferData}) per chi legge dentro questo programma.
 * Fuori da Windows si dichiara semplicemente {@code stringFlavor}.
 *
 * <p>Il testo del formato HTML <b>non</b> si pubblica: Excel preferirebbe l'HTML al testo e ne interpreterebbe i
 * valori (formattazione, numeri) diversamente.
 */
public final class BlockTransferable implements Transferable, ClipboardOwner {

    /** Flavor grezzo verso il formato nativo {@code UNICODE TEXT} di Windows. */
    static final DataFlavor WINDOWS_UNICODE_TEXT = createRawFlavor();

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private final String text;

    public BlockTransferable(String text) {
        this.text = text;
    }

    private static DataFlavor createRawFlavor() {
        DataFlavor flavor = new DataFlavor("application/x-ramasql-unicode-text;class=java.io.InputStream",
                "Testo tabulato (UTF-16LE)");
        FlavorMap map = SystemFlavorMap.getDefaultFlavorMap();
        if (map instanceof SystemFlavorMap system) {
            system.addUnencodedNativeForFlavor(flavor, "UNICODE TEXT");
        }
        return flavor;
    }

    public String text() {
        return text;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return WINDOWS ? new DataFlavor[] {WINDOWS_UNICODE_TEXT} : new DataFlavor[] {DataFlavor.stringFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.stringFlavor.equals(flavor) || WINDOWS_UNICODE_TEXT.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (DataFlavor.stringFlavor.equals(flavor)) {
            return text;
        }
        if (WINDOWS_UNICODE_TEXT.equals(flavor)) {
            return new ByteArrayInputStream((text + '\0').getBytes(StandardCharsets.UTF_16LE));
        }
        throw new UnsupportedFlavorException(flavor);
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // niente da fare: il testo era una copia
    }

    // ---------------------------------------------------------------- appunti, con nuovi tentativi

    /** Mette il testo negli appunti. Gli appunti di Windows possono essere occupati per un istante: si riprova. */
    static void write(Clipboard clipboard, String text) {
        BlockTransferable contents = new BlockTransferable(text);
        IllegalStateException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                clipboard.setContents(contents, contents);
                return;
            } catch (IllegalStateException e) {
                last = e;
                pause();
            }
        }
        throw last;
    }

    /** Il testo degli appunti; {@code null} se non contengono testo. */
    static String read(Clipboard clipboard) {
        RuntimeException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return null;
                }
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            } catch (IllegalStateException e) {
                last = e;
                pause();
            } catch (UnsupportedFlavorException | IOException e) {
                return null;
            }
        }
        throw last;
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
