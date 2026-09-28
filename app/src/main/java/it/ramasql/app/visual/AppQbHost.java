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

import java.awt.Color;
import java.sql.Connection;
import java.util.function.Consumer;

import it.ramasql.app.theme.Tokens;
import it.ramasql.core.metadata.MetadataReader;
import it.ramasql.qb.BasicQbHost;
import it.ramasql.qb.QbColor;
import it.ramasql.qb.QbMetadata;
import it.ramasql.qb.QbRuntime;

/**
 * La facciata del query builder ereditato da SQLeo vista dal programma ({@code ARCHITECTURE.md} §5).
 * <ul>
 *   <li><b>Nessuna connessione JDBC</b>: il query builder non esegue nulla e non legge metadati da sé; tabelle, colonne
 *       e chiavi esterne arrivano dal canale dei metadati del client ({@link MetadataQbMetadata}, {@code BUG-016}).</li>
 *   <li>Catalogo: quello della scheda «Query visiva».</li>
 *   <li>Colori: i token di {@code DESIGN-SYSTEM.md} ({@link Tokens}), una sola tavolozza per tutta l'app.</li>
 *   <li>Avvisi: nel pannello Messaggi, non in finestre.</li>
 * </ul>
 * Un'istanza per scheda (catalogo e lettore sono della scheda); {@link #installGlobal()} imposta per il processo la
 * facciata «senza scheda» che serve alle parti statiche del codice ereditato (icone, colori, testi, scala).
 */
public class AppQbHost extends BasicQbHost {

    private final MetadataReader reader;
    private final String catalog;
    private final Consumer<String> alerts;

    /**
     * @param reader  canale dei metadati della sessione; {@code null} = diagramma senza colonne
     * @param catalog catalogo della scheda
     * @param alerts  dove finiscono gli avvisi del query builder; {@code null} = ignorati
     */
    public AppQbHost(MetadataReader reader, String catalog, Consumer<String> alerts) {
        this.reader = reader;
        this.catalog = catalog;
        this.alerts = alerts;
    }

    /** La facciata di processo (colori e testi del programma, nessun metadato). Idempotente. */
    public static void installGlobal() {
        if (!(QbRuntime.processHost() instanceof AppQbHost)) {
            QbRuntime.setHost(new AppQbHost(null, null, null));
        }
    }

    @Override
    public Connection connection() {
        return null;
    }

    @Override
    public String catalog() {
        return catalog;
    }

    @Override
    public QbMetadata metadata() {
        return reader == null ? null : new MetadataQbMetadata(reader, catalog);
    }

    @Override
    public void alert(String message) {
        if (alerts != null && message != null && !message.isBlank()) {
            alerts.accept(message);
        }
    }

    @Override
    public Color color(QbColor c) {
        return switch (c) {
            case CANVAS, FIELD -> Tokens.BG_SURFACE;
            case FIELD_JOINED -> Tokens.ACCENT_TINT;
            case FIELD_JOIN_START -> Tokens.WARNING_TINT;
            case MISSING -> Tokens.DANGER;
            case LINE -> Tokens.TEXT_TERTIARY;
            case LINE_HIGHLIGHT, JOIN_INNER -> Tokens.ACCENT;
            case JOIN_OUTER, JOIN_ALL_ROWS -> Tokens.WARNING;
            case TEXT_SECONDARY -> Tokens.TEXT_SECONDARY;
            case BORDER -> Tokens.BORDER_DEFAULT;
            case HEADER -> Tokens.BG_SUNKEN;
        };
    }

    /** Le voci delle liste del query builder si spiegano come quelle del programma (T12.10, {@code ComboTips}). */
    @Override
    @SuppressWarnings("unchecked")
    public void explainItems(javax.swing.JComboBox<?> combo, java.util.function.Function<Object, String> tip) {
        it.ramasql.app.theme.ComboTips.install((javax.swing.JComboBox<Object>) combo, tip::apply);
    }
}
