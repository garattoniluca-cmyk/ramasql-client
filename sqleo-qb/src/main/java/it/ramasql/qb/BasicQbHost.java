/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Implementazione di base della facciata: nessuna connessione, icone disegnate nel codice ({@link QbDrawnIcon}),
 * testi italiani dal file {@code it/ramasql/qb/qb_it.properties} (ripiego: testo inglese originale),
 * scala ricavata dal font predefinito del Look and Feel. L'app e i test la estendono.
 */
public class BasicQbHost implements QbHost {

    private static final float BASE_FONT_SIZE = 12f;

    private final Map<QbIcon, Icon> icons = new EnumMap<>(QbIcon.class);
    private final Properties texts = new Properties();
    private float iconFactor = Float.NaN;

    public BasicQbHost() {
        try (InputStream in = BasicQbHost.class.getResourceAsStream("qb_it.properties")) {
            if (in != null) {
                texts.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // si resta sui testi predefiniti
        }
    }

    @Override
    public Connection connection() {
        return null;
    }

    @Override
    public String catalog() {
        return null;
    }

    @Override
    public synchronized Icon icon(QbIcon id) {
        float factor = scaleFactor();
        if (factor != iconFactor) {
            icons.clear();
            iconFactor = factor;
        }
        return icons.computeIfAbsent(id, i -> new QbDrawnIcon(i, factor));
    }

    @Override
    public String text(String key, String defaultText) {
        return texts.getProperty(key, defaultText);
    }

    /** Scala = dimensione del font predefinito del Look and Feel rispetto a 12 pt (FlatLaf la adegua ai DPI). */
    @Override
    public int scale(int px) {
        return Math.round(px * scaleFactor());
    }

    /** Fattore di scala corrente (almeno 1), usato per {@link #scale(int)} e per le icone. */
    protected float scaleFactor() {
        Font f = UIManager.getFont("Label.font");
        return f == null ? 1f : Math.max(1f, f.getSize2D() / BASE_FONT_SIZE);
    }

    @Override
    public boolean option(QbOption o) {
        return o.defaultValue();
    }

    @Override
    public void alert(String message) {
        System.err.println("[sqleo-qb] " + message);
    }

    @Override
    public List<JoinHint> joinHints(String table) {
        return List.of();
    }
}
