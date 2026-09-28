/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

import it.ramasql.app.Texts;

/**
 * I testi dei suggerimenti delle voci delle liste ({@code ADR-020}): tutti dal file di risorse, con chiavi prevedibili
 * {@code <lista>.<voce>.tooltip}; la voce diventa il titolo in grassetto del suggerimento ({@link RamaToolTipUI}). Per
 * le liste che vengono dal server (collation, set di caratteri) la spiegazione si compone dal nome, con pezzi di testo
 * anch'essi nel file di risorse.
 */
public final class Tips {

    private static final ResourceBundle TEXTS = ResourceBundle.getBundle("it.ramasql.app.messages");

    private Tips() {
    }

    /**
     * Dà a ogni componente di una schermata ancora senza suggerimento quello della chiave {@code <nome>.tooltip} (il
     * nome del componente), se esiste: così i testi stanno nel file di risorse e il codice non si riempie di chiamate.
     */
    public static void fromNames(java.awt.Component root) {
        if (root instanceof javax.swing.JComponent j && j.getName() != null && (j.getToolTipText() == null
                || j.getToolTipText().isBlank()) && Texts.has(j.getName() + ".tooltip")) {
            j.setToolTipText(Texts.get(j.getName() + ".tooltip"));
        }
        if (root instanceof javax.swing.JMenu m) {
            for (java.awt.Component c : m.getMenuComponents()) {
                fromNames(c);
            }
        }
        if (root instanceof java.awt.Container k) {
            for (java.awt.Component c : k.getComponents()) {
                fromNames(c);
            }
        }
    }

    /**
     * I suggerimenti delle intestazioni di colonna di una tabella: {@code <nome della tabella>.column.<n>.tooltip}, con
     * {@code n} l'indice della colonna nel modello. Il disegnatore delle intestazioni resta quello del tema.
     */
    public static void headers(javax.swing.JTable table) {
        javax.swing.table.JTableHeader header = table.getTableHeader();
        if (header == null || table.getName() == null) {
            return;
        }
        javax.swing.table.TableCellRenderer inner = header.getDefaultRenderer();
        String prefix = table.getName() + ".column.";
        header.setDefaultRenderer((t, value, selected, focus, row, column) -> {
            java.awt.Component c = inner.getTableCellRendererComponent(t, value, selected, focus, row, column);
            if (c instanceof javax.swing.JComponent j && column >= 0) {
                int model = t.convertColumnIndexToModel(column);
                j.setToolTipText(Texts.has(prefix + model + ".tooltip") ? Texts.get(prefix + model + ".tooltip") : null);
            }
            return c;
        });
    }

    /** I suggerimenti delle linguette: {@code <prefisso>.<n>.tooltip}, con {@code n} la posizione della linguetta. */
    public static void tabs(javax.swing.JTabbedPane tabs, String prefix) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (Texts.has(prefix + "." + i + ".tooltip")) {
                tabs.setToolTipTextAt(i, Texts.get(prefix + "." + i + ".tooltip"));
            }
        }
    }

    /** La chiave della voce: {@code SET NULL} → {@code <lista>.SET_NULL.tooltip}, vuota → {@code default}. */
    public static String key(String list, Object value) {
        String v = value == null ? "" : value.toString().strip();
        String norm = v.isEmpty() ? "default" : v.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        return list + "." + norm + ".tooltip";
    }

    /** C'è un testo scritto per la voce. */
    public static boolean has(String list, Object value) {
        return TEXTS.containsKey(key(list, value));
    }

    /** Titolo e trafiletto della voce ({@code title} è ciò che la lista mostra). */
    public static String item(String list, Object value, String title) {
        return titled(title, Texts.get(key(list, value)));
    }

    /** Un suggerimento con titolo in grassetto (vedi {@link RamaToolTipUI}). */
    public static String titled(String title, String text) {
        return title == null || title.isBlank() ? text : "**" + title + "**\n" + text;
    }

    /** Le voci di una lista con i testi {@code <lista>.<voce>.tooltip}; la voce stessa è il titolo. */
    public static Function<Object, String> of(String list) {
        return v -> item(list, v, v == null ? "" : v.toString().isEmpty() ? Texts.get(list + ".default") : v.toString());
    }

    /** Le voci di una lista con testi mostrati diversi dai valori: {@code values[i]} dà la chiave di {@code shown[i]}. */
    public static Function<Object, String> of(String list, List<String> shown, List<String> values) {
        return v -> {
            int i = shown.indexOf(String.valueOf(v));
            return i < 0 ? null : item(list, values.get(i), shown.get(i));
        };
    }

    /** Un tipo SQL, anche con gli argomenti: {@code VARCHAR(50)} usa il testo di {@code VARCHAR} e dice la lunghezza. */
    public static String type(Object value) {
        String v = value == null ? "" : value.toString().strip().toUpperCase(Locale.ROOT);
        String base = v.replaceAll("\\(.*$", "").strip();
        String key = key("tableeditor.type", base);
        if (!TEXTS.containsKey(key)) {
            return null;
        }
        String text = Texts.get(key);
        int open = v.indexOf('(');
        if (open > 0 && v.endsWith(")")) {
            text = text + "\n" + Texts.get("tableeditor.type.args", v.substring(open + 1, v.length() - 1));
        }
        return titled(value.toString(), text);
    }

    /** Una colonna di una tabella (le liste di colonne dell'editor di tabelle): tipo, obbligatoria, chiave. */
    public static String column(it.ramasql.core.metadata.ColumnDef c, boolean primaryKey) {
        String type = c.dataType() + (c.typeArgs() == null ? "" : "(" + c.typeArgs() + ")");
        return titled(c.name(), Texts.get("tableeditor.column.item.tooltip", c.name(), type,
                Texts.get(c.nullable() ? "tableeditor.column.item.nullable" : "tableeditor.column.item.notNull"),
                primaryKey ? Texts.get("tableeditor.column.item.key") : ""));
    }

    /** Un set di caratteri: il testo scritto se c'è, altrimenti una spiegazione generale con il nome. */
    public static String charset(Object value) {
        String v = value == null ? "" : value.toString().strip();
        String key = key("charset", v.isEmpty() ? "" : v.toLowerCase(Locale.ROOT));
        String text = TEXTS.containsKey(key) ? Texts.get(key) : Texts.get("charset.other.tooltip", v);
        return titled(v.isEmpty() ? Texts.get("charset.default") : v, text);
    }

    /**
     * Una collation, spiegata dal nome: il set di caratteri, le regole (Unicode, generali, binarie, di una lingua),
     * maiuscole e minuscole ({@code _ci}/{@code _cs}), accenti ({@code _ai}/{@code _as}).
     */
    public static String collation(Object value) {
        String v = value == null ? "" : value.toString().strip().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return titled(Texts.get("collation.default"), Texts.get("collation.default.tooltip"));
        }
        List<String> parts = new ArrayList<>();
        int u = v.indexOf('_');
        String cs = u < 0 ? v : v.substring(0, u);
        parts.add(Texts.get("collation.part.charset", cs));
        if (v.endsWith("_bin")) {
            parts.add(Texts.get("collation.part.bin"));
        } else {
            if (v.contains("uca1400") || v.contains("0900") || v.contains("unicode_520")) {
                parts.add(Texts.get("collation.part.uca.recent"));
            } else if (v.contains("unicode")) {
                parts.add(Texts.get("collation.part.uca"));
            } else if (v.contains("general")) {
                parts.add(Texts.get("collation.part.general"));
            } else if (v.contains("swedish") || v.contains("italian") || v.contains("german") || v.contains("spanish")
                    || v.matches(".*_[a-z]{2}_0900.*")) {
                parts.add(Texts.get("collation.part.language"));
            }
            if (v.contains("_ai")) {
                parts.add(Texts.get("collation.part.ai"));
            } else if (v.contains("_as")) {
                parts.add(Texts.get("collation.part.as"));
            }
            if (v.endsWith("_ci")) {
                parts.add(Texts.get("collation.part.ci"));
            } else if (v.endsWith("_cs")) {
                parts.add(Texts.get("collation.part.cs"));
            }
        }
        if (v.contains("nopad") || v.contains("0900")) {
            parts.add(Texts.get("collation.part.nopad"));
        }
        return titled(value.toString(), String.join(" ", parts));
    }
}
