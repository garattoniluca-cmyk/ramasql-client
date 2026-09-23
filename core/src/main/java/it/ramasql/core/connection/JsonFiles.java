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
            replace(temp, file, REPLACE_ATTEMPTS);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Tentativi di sostituzione del file (Windows: un antivirus o l'indicizzatore possono tenerlo aperto un attimo). */
    static final int REPLACE_ATTEMPTS = 10;

    /**
     * Sostituisce {@code file} con {@code temp}. Su Windows un altro programma (antivirus, indicizzatore, Esplora
     * risorse) può tenere aperto il file per qualche millisecondo e lo spostamento fallisce con «accesso negato»: si
     * riprova per circa un secondo prima di arrendersi, così il salvataggio non fallisce per un motivo passeggero.
     */
    static void replace(Path temp, Path file, int attempts) throws IOException {
        replace(temp, file, attempts, JsonFiles::moveReplacing);
    }

    /** Lo spostamento vero e proprio; separato perché i test possano simulare un file bloccato. */
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    static void moveReplacing(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void replace(Path temp, Path file, int attempts, Mover mover) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                mover.move(temp, file);
                return;
            } catch (java.nio.file.AccessDeniedException e) {
                if (attempt >= attempts) {
                    throw e;
                }
                try {
                    Thread.sleep(50L * attempt / 2 + 10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
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
