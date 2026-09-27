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

import java.util.Optional;

import it.ramasql.core.metadata.ViewDefinitionNormalizer;
import it.ramasql.qb.QbSql;

/**
 * Come si riapre una vista con «Modifica vista» ({@code FEASIBILITY.md} F-06, {@code DESIGN.md} §3.8): la strategia a
 * <b>tre livelli</b>.
 * <ol>
 *   <li><b>Sorgente originale</b>: la vista è nata in questo client e sul server non è cambiata; si riapre il testo
 *       scritto allora (archivio delle viste).</li>
 *   <li><b>Definizione del server normalizzata</b>: altrimenti si prende {@code VIEW_DEFINITION}, la si ripulisce
 *       ({@link ViewDefinitionNormalizer}) e, se il parser la rappresenta, si apre nel diagramma.</li>
 *   <li><b>Ripiego sul testo</b>: se nemmeno così è rappresentabile, si apre nella vista SQL, con un avviso, senza
 *       perdere nulla; la vista si può modificare e salvare come testo.</li>
 * </ol>
 *
 * @param level   1, 2 o 3
 * @param sql     il testo della SELECT con cui si apre la scheda
 * @param graphic {@code true} se il testo si disegna nel diagramma
 * @param reason  perché non si disegna (livello 3, o sorgente non rappresentabile); {@code null} altrimenti
 */
public record ViewReopening(int level, String sql, boolean graphic, String reason) {

    /**
     * @param source     il sorgente originale valido (già verificato contro la definizione attuale), se c'è
     * @param definition la definizione riletta dal server
     * @param catalog    catalogo della vista (il suo qualificatore si toglie)
     */
    public static ViewReopening decide(Optional<String> source, String definition, String catalog) {
        if (source.isPresent() && !source.get().isBlank()) {
            QbSql.Result check = QbSql.check(source.get());
            return new ViewReopening(1, source.get(), check.representable(),
                    check.representable() ? null : check.reason());
        }
        String normalized = ViewDefinitionNormalizer.normalize(definition, catalog);
        QbSql.Result check = QbSql.check(normalized);
        if (check.representable()) {
            return new ViewReopening(2, normalized, true, null);
        }
        // livello 3: il testo del server, solo senza il nome del catalogo — non la forma normalizzata, che nessuno
        // (il parser) ha verificato: salvandolo, la vista deve restare quella
        return new ViewReopening(3, ViewDefinitionNormalizer.stripCatalog(definition, catalog), false, check.reason());
    }
}
