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
import static it.ramasql.app.grid.GridTestSupport.visible;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BUG-002 con gli appunti <b>di sistema</b> (l'unico test della griglia che li usa; il loro contenuto è salvato prima e
 * rimesso dopo, in tutti i formati). Ciò che «arriva realmente» agli altri programmi si legge dal formato nativo
 * {@code CF_UNICODETEXT} con un processo PowerShell separato ({@code System.Windows.Forms.Clipboard.GetText()}): niente
 * controllo del desktop.
 *
 * <p>Si registra anche, come confronto, cosa producono {@code StringSelection} e il flavor
 * {@code text/plain;charset=utf-16} su {@code InputStream} (il «rimedio noto» di BUG-002): lo si scrive nell'evidenza
 * senza asserirlo, perché è comportamento del JDK e non del client.
 */
@Tag("step4")
@Tag("ui")
class Bug002SystemClipboardTest {

    private static Clipboard system;
    private static SystemClipboardSnapshot userClipboard;

    @TempDir
    Path tmp;

    @BeforeAll
    static void saveUserClipboard() {
        GridTestSupport.setupLookAndFeel();
        system = Toolkit.getDefaultToolkit().getSystemClipboard();
        userClipboard = SystemClipboardSnapshot.take(system);
    }

    @AfterAll
    static void restoreUserClipboard() {
        userClipboard.restore(system);
    }

    private String nativeText(Path tmp) throws Exception {
        Path out = tmp.resolve("letto-" + System.nanoTime() + ".txt");
        run(tmp, "Read", out);
        return Files.readString(out, StandardCharsets.UTF_8);
    }

    private void nativeSetText(Path tmp, String text) throws Exception {
        Path in = tmp.resolve("da-scrivere.txt");
        Files.writeString(in, text, StandardCharsets.UTF_8);
        run(tmp, "Write", in);
    }

    private static void run(Path tmp, String mode, Path file) throws Exception {
        Path ps1 = tmp.resolve("appunti.ps1");
        Files.writeString(ps1, """
                param([string]$Mode, [string]$Target)
                $ErrorActionPreference = 'Stop'
                Add-Type -AssemblyName System.Windows.Forms
                for ($i = 0; $i -lt 20; $i++) {
                  try {
                    if ($Mode -eq 'Read') {
                      $t = [System.Windows.Forms.Clipboard]::GetText()
                      [IO.File]::WriteAllText($Target, $t, (New-Object System.Text.UTF8Encoding $false))
                    } else {
                      $t = [IO.File]::ReadAllText($Target, [Text.Encoding]::UTF8)
                      [System.Windows.Forms.Clipboard]::SetText($t)
                    }
                    exit 0
                  } catch { Start-Sleep -Milliseconds 100 }
                }
                exit 1
                """, StandardCharsets.UTF_8);
        Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-ExecutionPolicy",
                "Bypass", "-File", ps1.toString(), "-Mode", mode, "-Target", file.toString())
                .redirectErrorStream(true).redirectOutput(tmp.resolve("ps.log").toFile()).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "PowerShell non ha risposto");
        assertEquals(0, p.exitValue(), "PowerShell: " + Files.readString(tmp.resolve("ps.log")));
    }

    private static void publish(Transferable t) throws Exception {
        for (int i = 0; ; i++) {
            try {
                system.setContents(t, null);
                return;
            } catch (IllegalStateException e) {
                if (i > 20) {
                    throw e;
                }
                Thread.sleep(50);
            }
        }
    }

    @Test
    void aCapoInCellaArrivaAgliAltriProgrammiComeSoloLf() throws Exception {
        DataGrid grid = Fixtures.grid(Fixtures.soci(), Fixtures.sociRows(), 1000, new FakeGridPrompts());
        // nessun setClipboard: la griglia usa gli appunti di sistema, come nel programma

        onEdt(() -> grid.selectBlock(3, 2, 3, 3));      // «Dario Neri» | «riga uno\nriga due»
        action(grid, DataGrid.ACTION_COPY);
        String arrivato = nativeText(tmp);
        assertEquals("Dario Neri\t\"riga uno\nriga due\"\r\n", arrivato,
                "CF_UNICODETEXT: LF da solo dentro la cella, CR+LF solo a fine riga");

        // confronto (non asserito): cosa farebbe Java con i flavor di testo
        String viaStringSelection;
        publish(new StringSelection("Dario Neri\t\"riga uno\nriga due\"\r\n"));
        viaStringSelection = nativeText(tmp);
        DataFlavor utf16 = new DataFlavor("text/plain;charset=utf-16;class=java.io.InputStream");
        publish(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {utf16};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return utf16.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return new ByteArrayInputStream("Dario Neri\t\"riga uno\nriga due\"\r\n".getBytes(StandardCharsets.UTF_16));
            }
        });
        String viaUtf16Stream = nativeText(tmp);

        // da un ALTRO programma (come Excel: CR+LF tra le righe, LF dentro la cella tra virgolette) → Ctrl+V
        nativeSetText(tmp, "Zeta\tBianchi\t\"prima\nseconda\"\r\nOmega\tNeri\t\r\n");
        onEdt(() -> grid.selectBlock(12, 1, 12, 1));
        action(grid, DataGrid.ACTION_PASTE);
        assertEquals(List.of(List.of("Zeta", "Bianchi", "prima\nseconda"), java.util.Arrays.asList("Omega", "Neri", null)),
                GridTestSupport.cells(grid, 12, 13, 1, 3));
        assertEquals(2, (int) fromEdt(() -> grid.model().pending().insertCount()));

        GridTestSupport.writeText("BUG-002-appunti-di-sistema.txt", "Testo che gli altri programmi leggono da "
                + "CF_UNICODETEXT (PowerShell, Clipboard.GetText), <TAB> <CR> <LF> resi visibili.\n\n"
                + "Griglia (BlockTransferable, flavor grezzo → UNICODE TEXT):\n" + visible(arrivato)
                + "\nStringSelection (confronto, JDK):\n" + visible(viaStringSelection)
                + "\ntext/plain;charset=utf-16 su InputStream (confronto, «rimedio noto»):\n" + visible(viaUtf16Stream)
                + "\nEsito: solo il flavor grezzo lascia il LF da solo dentro la cella.\n\n"
                + "Da un altro programma (PowerShell SetText, testo in stile Excel) → Ctrl+V sulla riga d'inserimento: "
                + "celle " + GridTestSupport.cells(grid, 12, 13, 1, 3) + "\n"
                + "Gli appunti dell'utente sono stati salvati prima del test (" + userClipboard.flavorCount()
                + " formati) e rimessi dopo.\n");
    }
}
