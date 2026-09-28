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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Il file {@code .rsqlmodel}: JSON leggibile (UTF-8, rientrato), con {@code formatVersion} in testa. Un file scritto
 * da una versione più recente del formato non si apre (errore chiaro invece di un modello mezzo capito); un file che
 * non è un modello nemmeno. La scrittura passa da un file temporaneo: un errore a metà non rovina il modello salvato.
 */
public final class ModelFile {

    /** Il file non si può aprire come modello: il messaggio è in italiano. */
    public static final class ModelFileException extends Exception {
        private static final long serialVersionUID = 1L;

        public ModelFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Forma su disco: la versione del formato e il modello. */
    record Document(int formatVersion, String name, String catalog, String server, boolean wholeCatalog,
            List<ErModel.Entity> entities, List<Relationship> relationships) {
    }

    static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ModelFile() {
    }

    public static String toJson(ErModel m) {
        try {
            return MAPPER.writeValueAsString(new Document(ModelFormat.FORMAT_VERSION, m.name(), m.catalog(),
                    m.server(), m.wholeCatalog(), m.entities(), m.relationships()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ErModel fromJson(String json, String fileName) throws ModelFileException {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ModelFileException(ModelMessages.get("model.file.invalid", fileName), e);
        }
        if (root == null || !root.isObject() || !root.has("formatVersion") || !root.get("formatVersion").isInt()) {
            throw new ModelFileException(ModelMessages.get("model.file.invalid", fileName), null);
        }
        int version = root.get("formatVersion").asInt();
        if (version > ModelFormat.FORMAT_VERSION) {
            throw new ModelFileException(ModelMessages.get("model.file.newer", fileName, version,
                    ModelFormat.FORMAT_VERSION), null);
        }
        if (version < 1) {
            throw new ModelFileException(ModelMessages.get("model.file.invalid", fileName), null);
        }
        try {
            Document d = MAPPER.treeToValue(root, Document.class);
            return new ErModel(d.name(), d.catalog(), d.wholeCatalog(),
                    d.entities() == null ? List.of() : d.entities(),
                    d.relationships() == null ? List.of() : d.relationships(), d.server());
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException e) {
            throw new ModelFileException(ModelMessages.get("model.file.invalid", fileName), e);
        }
    }

    public static void write(Path file, ErModel model) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, toJson(model), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);   // se lo spostamento non riesce, niente .tmp abbandonato
        }
    }

    public static ErModel read(Path file) throws IOException, ModelFileException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString());
    }
}
