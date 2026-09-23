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

/**
 * Chi mostra i metadati (il navigatore) ascolta le invalidazioni della cache per ricaricarsi da solo.
 * Le notifiche arrivano sul thread che ha invalidato (di solito quello dell'esecutore SQL, mai l'EDT):
 * un'interfaccia Swing le riporta sull'EDT con {@code SwingUtilities.invokeLater}.
 */
@FunctionalInterface
public interface MetadataListener {

    /**
     * La cache è stata invalidata.
     *
     * @param catalog catalogo invalidato; {@code null} = tutto (anche l'elenco dei cataloghi)
     * @param table   tabella invalidata; {@code null} = l'intero catalogo
     */
    void metadataInvalidated(String catalog, String table);
}
