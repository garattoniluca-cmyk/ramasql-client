/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.sql.Connection;

/**
 * Facciata tra l'applicazione e il query builder derivato da SQLeo (docs/ARCHITECTURE.md §5):
 * sostituisce ogni riferimento del codice ereditato ad Application, Preferences e finestre MDI.
 * Segnaposto dello Step 0: il contratto completo nasce dallo spike S1.
 */
public interface QbHost {

    Connection connection();

    String catalog();

    String text(String key, String defaultText);

    void alert(String message);
}
