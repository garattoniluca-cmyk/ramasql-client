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

import static it.ramasql.app.grid.GridTestSupport.action;
import static it.ramasql.app.grid.GridTestSupport.fromEdt;
import static it.ramasql.app.grid.GridTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T4.22 (parte componente): «Esporta CSV…» del contenuto caricato di {@code soci}, riletto dal disco byte per byte:
 * UTF-8 con BOM, {@code ;}, intestazioni, virgolette solo dove servono, decimali con la virgola, NULL ≠ stringa vuota,
 * accenti ed emoji intatti, righe marcate da eliminare escluse. L'apertura in Excel resta un controllo manuale.
 */
@Tag("step4")
@Tag("ui")
class T422ExportCsvTest {

    @TempDir
    Path tmp;

    @BeforeAll
    static void lookAndFeel() {
        GridTestSupport.setupLookAndFeel();
    }

    @Test
    void esportaCsvPerLExcelItaliano() throws Exception {
        FakeGridPrompts prompts = new FakeGridPrompts();
        prompts.csvFile = tmp.resolve("soci.csv");
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, prompts);
        GridTestSupport.type(grid, 0, 2, "Rossi; Anna");          // modifica in sospeso: esportata com'è mostrata
        onEdt(() -> grid.selectRows(11, 11));
        action(grid, DataGrid.ACTION_DELETE_ROWS);                 // Paolo Bassi, da eliminare: escluso
        onEdt(grid::exportCsv);                                    // pulsante «Esporta CSV…»

        assertEquals("csv: soci.csv", prompts.shown.get(0), "nome proposto");
        byte[] bytes = Files.readAllBytes(prompts.csvFile);
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(bytes, 3), "BOM UTF-8");
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\r\n"));
        List<String> lines = Arrays.asList(text.split("\r\n"));
        assertEquals(12, lines.size(), "intestazione + 11 righe (la 12ª è da eliminare)");
        assertEquals("id;tessera;nome;email;nato_il;quota;punti", lines.get(0));
        assertEquals("1;T101;\"Rossi; Anna\";anna.rossi@scuola.it;2008-01-10;10,50;0", lines.get(1));
        assertEquals("3;T103;\"Carla\tVerdi\";carla.verdi@scuola.it;2008-03-12;12,50;20", lines.get(3));
        assertEquals("4;T104;Dario Neri;\"riga uno\nriga due\";;13,50;30", lines.get(4), "a-capo in cella, NULL vuoto");
        assertEquals("5;T105;\"Elena \"\"Leni\"\" Galli\";;2008-05-14;14,50;40", lines.get(5));
        assertEquals("6;T106;Fabio Conti;\"\";2008-06-15;15,50;50", lines.get(6), "stringa vuota ≠ NULL");
        assertEquals("7;T107;Giulia Testà 😀;giulia.testa@scuola.it;2008-07-16;16,50;60", lines.get(7));
        assertEquals("Esportato in «soci.csv».", fromEdt(grid::notice));

        // l'esportazione non tocca le modifiche in sospeso
        assertEquals("0 inserimenti · 1 modifica · 1 eliminazione in sospeso", fromEdt(grid::counterText));
        Path copy = GridTestSupport.resultsDir().resolve("T4.22-soci.csv");
        Files.write(copy, bytes);
        GridTestSupport.writeText("T4.22-esporta-csv.txt", "File riletto dal disco (" + bytes.length + " byte), "
                + "copia in test-results/step4/T4.22-soci.csv.\nPrimi 3 byte: EF BB BF (BOM UTF-8). Separatore «;», "
                + "decimali con la virgola, righe CR+LF.\nContenuto (<TAB> <CR> <LF> resi visibili):\n"
                + GridTestSupport.visible(text) + "\nDa fare a mano: aprire il file con un doppio clic nell'Excel "
                + "italiano e controllare colonne e accenti (T4.22).\n");
    }
}
