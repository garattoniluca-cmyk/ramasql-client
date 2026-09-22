/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.app.spike.clipboard;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copia degli appunti dell'utente presa PRIMA dei test e rimessa DOPO (anche se un test fallisce): tutti i
 * {@link DataFlavor} leggibili (testo, HTML, RTF, immagini, elenchi di file, formati nativi), non solo il testo.
 * I flussi ({@link InputStream}, {@link Reader}, buffer) si leggono subito in memoria, perché dopo che gli appunti
 * sono cambiati non sarebbero più validi. I flavor che non si lasciano leggere si contano e si saltano.
 */
final class ClipboardSnapshot implements Transferable {

    private final Map<DataFlavor, Object> data;
    private final int unreadable;

    private ClipboardSnapshot(Map<DataFlavor, Object> data, int unreadable) {
        this.data = data;
        this.unreadable = unreadable;
    }

    /** Legge ora tutto il contenuto degli appunti (con qualche tentativo se sono occupati da un altro programma). */
    static ClipboardSnapshot take(Clipboard clipboard) {
        Transferable t = null;
        for (int i = 0; i < 20 && t == null; i++) {
            try {
                t = clipboard.getContents(null);
                if (t == null) {
                    break;
                }
            } catch (IllegalStateException e) {
                pause();
            }
        }
        Map<DataFlavor, Object> data = new LinkedHashMap<>();
        int unreadable = 0;
        if (t != null) {
            for (DataFlavor f : t.getTransferDataFlavors()) {
                try {
                    data.put(f, detach(t.getTransferData(f)));
                } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
                    unreadable++;
                }
            }
        }
        return new ClipboardSnapshot(data, unreadable);
    }

    /** Rimette negli appunti il contenuto salvato (tutti i flavor); se erano vuoti, li lascia vuoti. */
    void restore(Clipboard clipboard) {
        IllegalStateException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                clipboard.setContents(this, null);
                return;
            } catch (IllegalStateException e) {
                last = e;
                pause();
            }
        }
        throw last;
    }

    int flavorCount() {
        return data.size();
    }

    int unreadableCount() {
        return unreadable;
    }

    /** Tipi MIME salvati, senza contenuto (per il messaggio di un'eventuale asserzione). */
    List<String> mimeTypes() {
        List<String> out = new ArrayList<>();
        data.keySet().forEach(f -> out.add(f.getMimeType()));
        return out;
    }

    private static Object detach(Object o) throws IOException {
        if (o instanceof InputStream in) {
            try (in) {
                return new StreamCopy(in.readAllBytes());
            }
        }
        if (o instanceof Reader r) {
            try (r) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                for (int n; (n = r.read(buf)) >= 0; ) {
                    sb.append(buf, 0, n);
                }
                return new ReaderCopy(sb.toString());
            }
        }
        if (o instanceof ByteBuffer b) {
            ByteBuffer copy = ByteBuffer.allocate(b.remaining());
            copy.put(b.duplicate()).flip();
            return new ByteBufferCopy(copy);
        }
        if (o instanceof CharBuffer c) {
            return new CharBufferCopy(c.duplicate().toString());
        }
        return o; // String, Image, List<File>, oggetti serializzati: immutabili o già staccati dagli appunti
    }

    private record StreamCopy(byte[] bytes) { }

    private record ReaderCopy(String text) { }

    private record ByteBufferCopy(ByteBuffer buffer) { }

    private record CharBufferCopy(String text) { }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return data.keySet().toArray(new DataFlavor[0]);
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return data.containsKey(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!data.containsKey(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        Object o = data.get(flavor);
        if (o instanceof StreamCopy s) {
            return new ByteArrayInputStream(s.bytes());
        }
        if (o instanceof ReaderCopy r) {
            return new StringReader(r.text());
        }
        if (o instanceof ByteBufferCopy b) {
            return b.buffer().duplicate();
        }
        if (o instanceof CharBufferCopy c) {
            return CharBuffer.wrap(c.text());
        }
        return o;
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
