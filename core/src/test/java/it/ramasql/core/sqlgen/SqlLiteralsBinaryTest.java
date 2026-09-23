/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.sqlgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.core.data.RowChange;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.SqlTypes;
import it.ramasql.core.metadata.TableDef;

/**
 * Colonne <b>binarie</b> (BINARY, VARBINARY, i BLOB, BIT): il valore è una sequenza di byte, e la griglia lo mostra
 * come {@code 0x48656C6C6F}. Scriverlo fra apici salverebbe il <i>testo</i> «0x48656C6C6F» al posto dei 5 byte, e in
 * una condizione {@code WHERE} su una chiave binaria (una PK {@code BINARY(16)} con un UUID è il caso normale) non
 * troverebbe nessuna riga: il server risponderebbe <b>OK con zero righe</b> e il client direbbe «salvata» senza aver
 * cambiato niente. Qui si controlla che il letterale sia {@code X'…'} da tutte e tre le strade: valore, cella e
 * condizione.
 */
@Tag("step4")
class SqlLiteralsBinaryTest {

    private static ColumnDef binaria(String name, String type, String args) {
        return ColumnDef.of(name, type, args).notNull();
    }

    @Test
    void iTipiBinariSonoRiconosciuti() {
        for (String t : List.of("BINARY", "VARBINARY", "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB", "BIT")) {
            assertTrue(SqlTypes.isBinary(t), t);
            assertTrue(SqlTypes.isBinary(t.toLowerCase(java.util.Locale.ROOT)), t);
        }
        for (String t : List.of("VARCHAR", "CHAR", "TEXT", "INT", "DATE", "ENUM")) {
            assertFalse(SqlTypes.isBinary(t), t);
        }
    }

    @Test
    void ilTestoDellaCellaBinariaDiventaUnLetteraleEsadecimale() {
        ColumnDef dati = binaria("dati", "VARBINARY", "64");
        assertEquals("X'48656C6C6F'", SqlLiterals.forColumn("0x48656C6C6F", dati));
        assertEquals("X'48656C6C6F'", SqlLiterals.forColumn("0x48656c6c6f", dati), "minuscole ammesse");
        assertEquals("X'48656C6C6F'", SqlLiterals.forColumn("X'48656C6C6F'", dati), "anche la forma X'…'");
        assertEquals("X''", SqlLiterals.forColumn("0x", dati), "vettore vuoto");
        assertEquals("NULL", SqlLiterals.forColumn(null, dati));
    }

    @Test
    void unTestoNonEsadecimaleRestaUnaStringaEIlServerLoRifiuta() {
        ColumnDef dati = binaria("dati", "BLOB", null);
        // non si inventano byte: si passa il testo al server, che risponderà con il suo errore
        assertEquals("'ciao'", SqlLiterals.forColumn("ciao", dati));
        assertEquals("'0xZZ'", SqlLiterals.forColumn("0xZZ", dati), "cifre non esadecimali");
        assertEquals("'0x123'", SqlLiterals.forColumn("0x123", dati), "numero dispari di cifre");
    }

    @Test
    void laCondizioneSuUnaChiaveBinariaUsaIlLetteraleEsadecimale() {
        TableDef t = new TableDef("biblioteca", "sessioni", "InnoDB", null, null, "", null,
                List.of(binaria("id", "BINARY", "16"), ColumnDef.of("nome", "VARCHAR", "40")),
                List.of(IndexDef.primary("id")), List.of(), List.of());
        String where = DmlGenerator.where(t, Map.of("id", "0x0A1B2C3D4E5F60718293A4B5C6D7E8F9", "nome", "prova"));
        assertTrue(where.contains("X'0A1B2C3D4E5F60718293A4B5C6D7E8F9'"),
                "la condizione deve usare il letterale binario, non una stringa: " + where);
        assertFalse(where.contains("'0x"), "nessun apice attorno a «0x…»: " + where);

        String update = DmlGenerator.update(t, RowChange.update(1L,
                Map.of("id", "0x0A1B2C3D4E5F60718293A4B5C6D7E8F9", "nome", "prova"), Map.of("nome", "cambiato")));
        assertTrue(update.contains("WHERE `id` = X'0A1B2C3D4E5F60718293A4B5C6D7E8F9'"), update);

        String delete = DmlGenerator.delete(t, RowChange.delete(1L,
                Map.of("id", "0x0A1B2C3D4E5F60718293A4B5C6D7E8F9", "nome", "prova")));
        assertTrue(delete.contains("WHERE `id` = X'0A1B2C3D4E5F60718293A4B5C6D7E8F9'"), delete);
    }
}
