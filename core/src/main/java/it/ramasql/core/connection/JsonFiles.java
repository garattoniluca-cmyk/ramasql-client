/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Lettura e scrittura dei piccoli file JSON dell'utente (profili, impostazioni): UTF-8, rientrati, tolleranti. */
final class JsonFiles {

    /** Suffisso della copia messa da parte di un file che non si riesce a leggere. */
    static final String SET_ASIDE_SUFFIX = ".illeggibile";

    /** I campi sconosciuti si ignorano: un file scritto da una versione futura (o ritoccato a mano) si apre lo stesso. */
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonFiles() {
    }

    static <T> T read(Path file, Class<T> type) throws IOException {
        return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
    }

    /**
     * Scrive prima un file temporaneo, lo forza su disco e poi lo sostituisce al file vero con uno spostamento
     * atomico (se il file system non lo permette, con uno spostamento normale): un arresto improvviso o una mancanza di
     * corrente non lasciano un file a metà. Se qualcosa va storto il file vero resta com'era e il temporaneo sparisce.
     */
    static void write(Path file, Object value) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            byte[] bytes = MAPPER.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Mette da parte un file che non si riesce a leggere, con un nome <strong>mai usato</strong>
     * ({@code nome.illeggibile}, poi {@code nome.illeggibile-2}, {@code -3}…): una copia messa da parte in precedenza
     * non si sovrascrive mai.
     *
     * @return dove è finito il file
     */
    static Path setAside(Path file) throws IOException {
        String base = file.getFileName().toString() + SET_ASIDE_SUFFIX;
        for (int n = 1; n < 10_000; n++) {
            Path target = file.resolveSibling(n == 1 ? base : base + "-" + n);
            if (Files.exists(target)) {
                continue;
            }
            try {
                Files.move(file, target); // senza REPLACE_EXISTING: se nel frattempo è comparso, si prova il seguente
                return target;
            } catch (FileAlreadyExistsException raced) {
                // nome preso nel frattempo: avanti
            }
        }
        throw new IOException("Nessun nome libero per mettere da parte " + file.getFileName());
    }
}
