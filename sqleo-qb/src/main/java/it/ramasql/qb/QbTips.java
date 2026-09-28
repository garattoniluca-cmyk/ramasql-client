/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import com.sqleo.common.util.I18n;

/**
 * I suggerimenti dei menu del query builder (T12.9): ogni voce il cui testo viene da una chiave del file dei testi
 * (per esempio {@code querybuilder.menu.addCondition}) riceve il suggerimento della chiave {@code <chiave>.tooltip}.
 * Si chiama appena il menu è costruito, così la voce ha il suo suggerimento prima di essere mostrata.
 */
public final class QbTips {

    private QbTips() {
    }

    /** Dà il suggerimento alle voci del menu (anche dei sottomenu) che non ne hanno uno. */
    public static void explain(Container menu) {
        Component[] items = menu instanceof JMenu m ? m.getMenuComponents() : menu.getComponents();
        for (Component c : items) {
            if (c instanceof JMenu sub) {
                explain(sub);
            }
            if (c instanceof JMenuItem item) {
                tip(item, item.getText());
            }
        }
    }

    /** Dà al componente il suggerimento della chiave del testo dato, se il componente non ne ha uno. */
    public static void tip(JComponent c, String text) {
        if (c.getToolTipText() != null && !c.getToolTipText().isBlank()) {
            return;
        }
        String key = I18n.keyOf(text);
        if (key != null) {
            String tip = QbRuntime.host().text(key + ".tooltip", null);
            if (tip != null && !tip.isBlank()) {
                c.setToolTipText(tip);
            }
        }
    }

    private static final java.util.Map<String, String> OPERATORS = java.util.Map.ofEntries(
            java.util.Map.entry("=", "eq"), java.util.Map.entry("<", "lt"), java.util.Map.entry(">", "gt"),
            java.util.Map.entry("<=", "le"), java.util.Map.entry(">=", "ge"), java.util.Map.entry("<>", "ne"),
            java.util.Map.entry("!=", "ne2"), java.util.Map.entry("LIKE", "like"),
            java.util.Map.entry("NOT LIKE", "notlike"), java.util.Map.entry("IS", "is"),
            java.util.Map.entry("IS NOT", "isnot"), java.util.Map.entry("IN", "in"), java.util.Map.entry("NOT IN", "notin"),
            java.util.Map.entry("EXISTS", "exists"), java.util.Map.entry("NOT EXISTS", "notexists"),
            java.util.Map.entry("BETWEEN", "between"), java.util.Map.entry("NOT BETWEEN", "notbetween"),
            java.util.Map.entry("AND", "and"), java.util.Map.entry("OR", "or"));

    /** Le spiegazioni delle voci di una lista di operatori (o di AND/OR), dal file dei testi. */
    public static void explainOperators(javax.swing.JComboBox<?> combo) {
        QbRuntime.host().explainItems(combo, v -> {
            String key = v == null ? null : OPERATORS.get(v.toString().trim().toUpperCase(java.util.Locale.ROOT));
            return key == null ? null : QbRuntime.host().text("querybuilder.operator." + key + ".tooltip", null);
        });
    }

    /** Il suggerimento della chiave, o il testo predefinito. */
    public static String text(String key, String defaultText) {
        return QbRuntime.host().text(key, defaultText);
    }
}
