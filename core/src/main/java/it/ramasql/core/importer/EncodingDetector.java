/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.importer;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Riconosce la codifica di un file di testo dai suoi primi byte, come serve in aula: i CSV salvati da Excel
 * italiano sono in <b>Windows-1252</b> («CSV (delimitato dal separatore di elenco)») oppure in <b>UTF-8 con BOM</b>
 * («CSV UTF-8»); quelli di LibreOffice, di Fogli Google e i JSON sono di solito in UTF-8 senza BOM.
 *
 * <ol>
 *   <li>BOM {@code EF BB BF} → UTF-8; {@code FF FE} → UTF-16LE; {@code FE FF} → UTF-16BE;</li>
 *   <li>senza BOM: se i byte sono UTF-8 valido → UTF-8 (un file solo ASCII è UTF-8 e Windows-1252 insieme: si
 *       sceglie UTF-8);</li>
 *   <li>altrimenti → Windows-1252 (il «ANSI» di Windows in italiano: accenti su un byte).</li>
 * </ol>
 */
public final class EncodingDetector {

    /** Windows-1252, la codifica «ANSI» di Windows in italiano. */
    public static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /**
     * @param charset   codifica
     * @param bomLength byte del BOM all'inizio del file (0 se non c'è): il lettore li salta
     */
    public record Detected(Charset charset, int bomLength) {
    }

    private EncodingDetector() {
    }

    /**
     * @param head   i primi byte del file (bastano poche decine di kB)
     * @param length quanti byte di {@code head} sono validi
     * @param whole  {@code head} contiene tutto il file: una sequenza UTF-8 tagliata alla fine è un errore vero, non
     *               un effetto del taglio
     */
    public static Detected detect(byte[] head, int length, boolean whole) {
        int bom = bomLength(head, length);
        if (bom == 3) {
            return new Detected(StandardCharsets.UTF_8, 3);
        }
        if (bom == 2) {
            return new Detected((head[0] & 0xFF) == 0xFF ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE, 2);
        }
        return new Detected(isUtf8(head, length, whole) ? StandardCharsets.UTF_8 : WINDOWS_1252, 0);
    }

    /** Lunghezza del BOM riconosciuto all'inizio dei byte (0, 2 o 3). */
    public static int bomLength(byte[] head, int length) {
        if (length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            return 3;
        }
        if (length >= 2 && (((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE)
                || ((head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF))) {
            return 2;
        }
        return 0;
    }

    /** Il BOM che corrisponde alla codifica, se i byte cominciano proprio con quello (0 altrimenti). */
    public static int bomLength(byte[] head, int length, Charset charset) {
        int bom = bomLength(head, length);
        if (bom == 3 && charset.equals(StandardCharsets.UTF_8)) {
            return 3;
        }
        if (bom == 2 && (charset.equals(StandardCharsets.UTF_16LE) || charset.equals(StandardCharsets.UTF_16BE)
                || charset.equals(StandardCharsets.UTF_16))) {
            return 2;
        }
        return 0;
    }

    private static boolean isUtf8(byte[] head, int length, boolean whole) {
        int end = length;
        if (!whole) {
            // l'ultima sequenza può essere tagliata a metà dal limite della lettura: la si ignora
            int i = length - 1;
            int back = 0;
            while (i >= 0 && back < 4 && (head[i] & 0xC0) == 0x80) {
                i--;
                back++;
            }
            if (i >= 0 && (head[i] & 0x80) != 0) {
                int needed = (head[i] & 0xE0) == 0xC0 ? 2 : (head[i] & 0xF0) == 0xE0 ? 3 : (head[i] & 0xF8) == 0xF0 ? 4 : 1;
                if (needed > back + 1) {
                    end = i;
                }
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(head, 0, end));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
