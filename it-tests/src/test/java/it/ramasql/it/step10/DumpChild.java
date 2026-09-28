/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.it.step10;

import java.nio.file.Path;
import java.util.List;

import it.ramasql.core.dump.DumpOptions;
import it.ramasql.core.dump.Dumper;
import it.ramasql.core.exec.ScriptFileResult;
import it.ramasql.it.ItServers;

/**
 * Dump o ripristino con il codice del client in un processo a parte, lanciato dai test con poca memoria
 * ({@code -Xmx}): se il client tenesse in memoria il risultato di una tabella o il file, il processo finirebbe senza
 * memoria. Stampa una riga {@code OK …} con i conteggi e il picco di memoria usata, ed esce con 0.
 *
 * <pre>DumpChild dump|restore MARIADB|MYSQL catalogo file [righePerInsert]</pre>
 */
public final class DumpChild {

    private DumpChild() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        ItServers server = ItServers.valueOf(args[1]);
        String catalog = args[2];
        Path file = Path.of(args[3]);
        int rowsPerInsert = args.length > 4 ? Integer.parseInt(args[4]) : 100;
        try (DumpSupport.Client c = DumpSupport.Client.of(server)) {
            if (mode.equals("dump")) {
                List<Dumper.Item> items = c.whole(catalog);
                Dumper.Result r = c.dump(items, DumpOptions.defaults().withRowsPerInsert(rowsPerInsert), file);
                System.out.println("OK rows=" + r.rows() + " statements=" + r.statements() + " log=" + c.log().size()
                        + " peakMb=" + peakMb());
            } else {
                ScriptFileResult r = c.restore(file, catalog);
                System.out.println("OK executed=" + r.executed() + " completed=" + r.completed() + " failures="
                        + r.failureCount() + " log=" + c.log().size() + " peakMb=" + peakMb());
            }
        }
        System.exit(0);
    }

    /** Il picco dell'heap usato (somma dei picchi delle aree dell'heap). */
    static long peakMb() {
        long peak = 0;
        for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) {
                peak += pool.getPeakUsage().getUsed();
            }
        }
        return peak / (1024 * 1024);
    }
}
