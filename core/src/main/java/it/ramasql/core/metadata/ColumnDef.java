/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.metadata;

import java.util.Locale;
import java.util.Objects;

/**
 * Colonna di una tabella (record immutabile).
 *
 * <p><b>Identità della colonna:</b> {@code ordinalPosition} è la posizione letta dal server (1…n) e vale
 * come identità nel confronto tra originale e modificato: una colonna che conserva la posizione ma cambia
 * nome è una <i>rinomina</i>. Le colonne nuove, non ancora sul server, hanno posizione {@code 0}
 * (vedi {@link #asNew()}); l'ordine effettivo è quello della lista in {@link TableDef#columns()}.
 *
 * @param name            nome
 * @param dataType        tipo senza argomenti, es. {@code VARCHAR}, {@code INT}, {@code ENUM} (maiuscole)
 * @param typeArgs        lunghezza o valori tra le parentesi: {@code "100"}, {@code "10,2"}, {@code "'a','b'"}; {@code null} se assenti
 * @param unsigned        {@code UNSIGNED}
 * @param nullable        ammette NULL
 * @param defaultValue    default (mai {@code null}: si usa {@link ColumnDefault#NONE})
 * @param autoIncrement   {@code AUTO_INCREMENT}
 * @param comment         commento ({@code ""} se assente)
 * @param charset         charset della colonna, {@code null} = quello della tabella
 * @param collation       collation della colonna, {@code null} = quella della tabella
 * @param ordinalPosition posizione sul server (1…n), {@code 0} per una colonna nuova
 * @param onUpdate        espressione di {@code ON UPDATE} (in pratica {@code CURRENT_TIMESTAMP}), {@code null} se assente
 * @param generated       colonna generata: elemento avanzato, conservato e mai toccato dai generatori
 */
public record ColumnDef(
        String name,
        String dataType,
        String typeArgs,
        boolean unsigned,
        boolean nullable,
        ColumnDefault defaultValue,
        boolean autoIncrement,
        String comment,
        String charset,
        String collation,
        int ordinalPosition,
        String onUpdate,
        boolean generated) {

    public ColumnDef {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dataType, "dataType");
        dataType = dataType.trim().toUpperCase(Locale.ROOT);
        typeArgs = typeArgs == null || typeArgs.isBlank() ? null : typeArgs.trim();
        defaultValue = defaultValue == null ? ColumnDefault.NONE : defaultValue;
        comment = comment == null ? "" : comment;
        charset = blankToNull(charset);
        collation = blankToNull(collation);
        onUpdate = blankToNull(onUpdate);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Colonna nuova, annullabile, senza default: {@code of("titolo", "VARCHAR", "100")}. */
    public static ColumnDef of(String name, String dataType, String typeArgs) {
        return new ColumnDef(name, dataType, typeArgs, false, true, ColumnDefault.NONE, false, "", null, null, 0, null,
                false);
    }

    /** Colonna nuova senza argomenti di tipo: {@code of("nato_il", "DATE")}. */
    public static ColumnDef of(String name, String dataType) {
        return of(name, dataType, null);
    }

    /** Tipo completo com'è scritto nell'SQL, senza UNSIGNED: {@code VARCHAR(100)}, {@code DATE}. */
    public String fullType() {
        return typeArgs == null ? dataType : dataType + "(" + typeArgs + ")";
    }

    public ColumnDef withName(String v) {
        return new ColumnDef(v, dataType, typeArgs, unsigned, nullable, defaultValue, autoIncrement, comment, charset,
                collation, ordinalPosition, onUpdate, generated);
    }

    /** Cambia tipo e argomenti insieme: {@code withType("DECIMAL", "10,2")}. */
    public ColumnDef withType(String newDataType, String newTypeArgs) {
        return new ColumnDef(name, newDataType, newTypeArgs, unsigned, nullable, defaultValue, autoIncrement, comment,
                charset, collation, ordinalPosition, onUpdate, generated);
    }

    public ColumnDef withTypeArgs(String v) {
        return withType(dataType, v);
    }

    public ColumnDef withUnsigned(boolean v) {
        return new ColumnDef(name, dataType, typeArgs, v, nullable, defaultValue, autoIncrement, comment, charset,
                collation, ordinalPosition, onUpdate, generated);
    }

    public ColumnDef withNullable(boolean v) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, v, defaultValue, autoIncrement, comment, charset,
                collation, ordinalPosition, onUpdate, generated);
    }

    /** Scorciatoia per {@code withNullable(false)}. */
    public ColumnDef notNull() {
        return withNullable(false);
    }

    public ColumnDef withDefault(ColumnDefault v) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, nullable, v, autoIncrement, comment, charset,
                collation, ordinalPosition, onUpdate, generated);
    }

    public ColumnDef withAutoIncrement(boolean v) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, nullable, defaultValue, v, comment, charset,
                collation, ordinalPosition, onUpdate, generated);
    }

    public ColumnDef withComment(String v) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, nullable, defaultValue, autoIncrement, v, charset,
                collation, ordinalPosition, onUpdate, generated);
    }

    public ColumnDef withCharset(String newCharset, String newCollation) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, nullable, defaultValue, autoIncrement, comment,
                newCharset, newCollation, ordinalPosition, onUpdate, generated);
    }

    public ColumnDef withOrdinalPosition(int v) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, nullable, defaultValue, autoIncrement, comment,
                charset, collation, v, onUpdate, generated);
    }

    /** La stessa definizione come colonna nuova (posizione 0): utile per «duplica colonna». */
    public ColumnDef asNew() {
        return withOrdinalPosition(0);
    }

    public ColumnDef withOnUpdate(String v) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, nullable, defaultValue, autoIncrement, comment,
                charset, collation, ordinalPosition, v, generated);
    }

    public ColumnDef withGenerated(boolean v) {
        return new ColumnDef(name, dataType, typeArgs, unsigned, nullable, defaultValue, autoIncrement, comment,
                charset, collation, ordinalPosition, onUpdate, v);
    }
}
