/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.sql.Connection;
import java.util.List;

import javax.swing.Icon;

/**
 * Facciata tra l'applicazione e il query builder derivato da SQLeo (docs/ARCHITECTURE.md §5):
 * sostituisce ogni riferimento del codice ereditato ad Application, Preferences, finestre interne di SQLeo,
 * ConnectionAssistant e ConnectionHandler.
 *
 * <p>Contratto nato dallo spike S1. Volutamente piccola: tutto ciò che il codice ereditato
 * chiedeva all'applicazione SQLeo passa da qui. Un'implementazione con valori predefiniti
 * (icone e testi inclusi nel modulo) è {@link BasicQbHost}.
 */
public interface QbHost {

    /** Connessione JDBC della sessione; può essere {@code null} (QB senza metadati). */
    Connection connection();

    /** Catalogo (database) predefinito della sessione; può essere {@code null}. */
    String catalog();

    /** Icona per l'elemento indicato; mai {@code null}. */
    Icon icon(QbIcon id);

    /** Testo d'interfaccia: {@code key} è la chiave ereditata da SQLeo, {@code defaultText} il testo inglese originale. */
    String text(String key, String defaultText);

    /** Converte pixel «a 100%» in pixel alla scala corrente (HiDPI). */
    int scale(int px);

    /** Opzioni di comportamento che in SQLeo stavano nelle preferenze. */
    boolean option(QbOption o);

    /** Avviso all'utente (in SQLeo: il metodo alert della classe Application). Non deve lanciare eccezioni. */
    void alert(String message);

    /** Suggerimenti di join per una tabella: FK reali + relazioni logiche del modello ER. Mai {@code null}. */
    List<JoinHint> joinHints(String table);

    /**
     * Metadati per il diagramma (tabelle, colonne, chiavi esterne). Predefinito: letti dalla {@link #connection()}
     * con {@link JdbcQbMetadata}; {@code null} se non c'è connessione (diagramma senza colonne). Il programma lo
     * ridefinisce con il suo canale dei metadati, così il query builder non interroga il server da sé ({@code BUG-016}).
     */
    default QbMetadata metadata() {
        Connection c = connection();
        return c == null ? null : new com.sqleo.querybuilder.JdbcQbMetadata(c, catalog());
    }

    /** Colore del diagramma; predefinito: il token di {@code DESIGN-SYSTEM.md}. */
    /**
     * Le spiegazioni delle voci di una lista a discesa del query builder (T12.10): il programma le mostra accanto alla
     * lista aperta, come in tutte le sue liste. Per impostazione predefinita non fa nulla.
     */
    default void explainItems(javax.swing.JComboBox<?> combo, java.util.function.Function<Object, String> tip) {
    }

    default java.awt.Color color(QbColor c) {
        return c.defaultColor();
    }
}
