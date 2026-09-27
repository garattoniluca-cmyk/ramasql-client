/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.visual;

import java.util.function.Consumer;

/**
 * Chi salva una vista per la scheda «Query visiva» in modalità vista (Step 8, {@code DESIGN.md} §3.8). Nel programma
 * è {@code PipelineViewSaver}: {@code CREATE [OR REPLACE] VIEW} passa dalla pipeline «anteprima SQL» (anteprima,
 * conferma, registro) e, se il server l'accetta, il sorgente originale finisce nell'archivio delle viste.
 */
public interface ViewSaver {

    /**
     * Propone e, se l'utente conferma, esegue la creazione della vista. Va chiamato sull'EDT.
     *
     * @param orReplace {@code true} per sostituire una vista esistente (modifica), {@code false} per una vista nuova
     * @param done      chiamato sull'EDT: {@code true} se la vista ora c'è sul server come richiesto
     */
    void save(String catalog, String view, String select, boolean orReplace, Consumer<Boolean> done);
}
