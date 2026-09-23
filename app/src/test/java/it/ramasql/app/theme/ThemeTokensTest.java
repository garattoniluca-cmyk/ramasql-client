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

import static it.ramasql.app.theme.ThemeTestSupport.fromEdt;
import static it.ramasql.app.theme.ThemeTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatLaf;

/**
 * (a) Il tema RamaSQL si installa e le chiavi FlatLaf principali hanno i valori dei {@link Tokens}
 * ({@code DESIGN-SYSTEM.md} §1): i due posti in cui vivono i colori non possono divergere.
 */
@Tag("step2")
@Tag("ui")
class ThemeTokensTest {

    @BeforeEach
    void setUp() {
        ThemeTestSupport.setupTheme();
    }

    @AfterEach
    void restoreFont() {
        onEdt(() -> {
            UIManager.put("defaultFont", null);
            FlatLaf.updateUI();
        });
    }

    @Test
    void ilTemaSiInstallaAlPostoDiFlatLightLaf() {
        onEdt(() -> {
            assertInstanceOf(RamaSqlLaf.class, UIManager.getLookAndFeel());
            assertEquals(RamaSqlLaf.NAME, UIManager.getLookAndFeel().getName());
        });
    }

    @Test
    void iColoriDelleChiaviPrincipaliSonoQuelliDeiToken() {
        Map<String, Color> expected = new LinkedHashMap<>();
        expected.put("Panel.background", Tokens.BG_WINDOW);
        expected.put("Table.background", Tokens.BG_SURFACE);
        expected.put("TextField.background", Tokens.BG_SURFACE);
        expected.put("TableHeader.background", Tokens.BG_SUNKEN);
        expected.put("Separator.foreground", Tokens.BORDER_SUBTLE);
        expected.put("Table.gridColor", Tokens.BORDER_SUBTLE);
        expected.put("Component.borderColor", Tokens.BORDER_DEFAULT);
        expected.put("Label.foreground", Tokens.TEXT_PRIMARY);
        expected.put("TableHeader.foreground", Tokens.TEXT_SECONDARY);
        expected.put("Label.disabledForeground", Tokens.TEXT_TERTIARY);
        expected.put("Component.accentColor", Tokens.ACCENT);
        expected.put("Component.focusedBorderColor", Tokens.ACCENT);
        expected.put("Button.default.background", Tokens.ACCENT);
        expected.put("Button.default.foreground", Tokens.ON_ACCENT);
        expected.put("Button.default.hoverBackground", Tokens.ACCENT_HOVER);
        expected.put("Button.default.pressedBackground", Tokens.ACCENT_PRESSED);
        expected.put("Table.selectionBackground", Tokens.ACCENT_TINT);
        expected.put("Tree.selectionBackground", Tokens.ACCENT_TINT);
        expected.put("MenuItem.selectionBackground", Tokens.ACCENT_TINT);
        expected.put("MenuItem.selectionForeground", Tokens.TEXT_PRIMARY);
        expected.put("MenuItem.acceleratorForeground", Tokens.TEXT_SECONDARY);
        expected.put("MenuBar.background", Tokens.BG_WINDOW);
        expected.put("TitlePane.background", Tokens.BG_WINDOW);
        expected.put("ToolBar.background", Tokens.BG_WINDOW);
        expected.put("PopupMenu.borderColor", Tokens.BORDER_SUBTLE);
        expected.put("TabbedPane.underlineColor", Tokens.ACCENT);
        expected.put("ToolTip.background", Tokens.TEXT_PRIMARY);
        expected.put("Component.error.focusedBorderColor", Tokens.DANGER);
        expected.put("Component.warning.focusedBorderColor", Tokens.WARNING);
        expected.put("ProgressBar.foreground", Tokens.ACCENT);
        expected.put("TextField.selectionBackground", Tokens.SQL_SELECTION);
        onEdt(() -> expected.forEach((key, color) -> {
            Color actual = UIManager.getColor(key);
            assertEquals(Tokens.hex(color), actual == null ? "null" : Tokens.hex(actual), key);
        }));
    }

    @Test
    void misureERaggiSonoQuelliDelSistema() {
        onEdt(() -> {
            // FlatLaf vuole il diametro dell'arco: raggio 6 -> 12
            assertEquals(Tokens.RADIUS_CONTROL * 2, UIManager.getInt("Component.arc"));
            assertEquals(Tokens.RADIUS_CONTROL * 2, UIManager.getInt("Button.arc"));
            assertEquals(Tokens.RADIUS_CONTROL * 2, UIManager.getInt("TextComponent.arc"));
            assertEquals(Tokens.RADIUS_TREE_SELECTION * 2, UIManager.getInt("Tree.selectionArc"));
            assertEquals(Tokens.RADIUS_CONTROL * 2, UIManager.getInt("MenuItem.selectionArc"));
            assertEquals(Tokens.ROW_HEIGHT, UIManager.getInt("Table.rowHeight"));
            assertEquals(Tokens.TREE_ROW_HEIGHT, UIManager.getInt("Tree.rowHeight"));
            assertEquals(2, UIManager.getInt("Component.focusWidth"), "anello di focus 2 px (§5)");
            assertEquals("underlined", UIManager.getString("TabbedPane.tabType"));
            assertEquals(2, UIManager.getInt("TabbedPane.tabSelectionHeight"));
            assertEquals(999, UIManager.getInt("ScrollBar.thumbArc"), "barra di scorrimento arrotondata");
            assertEquals(10, UIManager.getInt("ScrollBar.width"), "barra di scorrimento sottile");
            assertTrue(UIManager.getBoolean("TitlePane.menuBarEmbedded"), "menu nella barra del titolo");
            assertTrue(UIManager.getBoolean("Popup.dropShadowPainted"), "ombra dei menu");
            assertEquals(10, UIManager.getInt("PopupMenu.borderCornerRadius"), "popup arrotondati");
        });
    }

    @Test
    void ilCarattereESegoeEAlCorpoDelSistema() {
        onEdt(() -> {
            Font base = UIManager.getFont("defaultFont");
            assertEquals(Tokens.BODY, base.getSize());
            if (System.getProperty("os.name", "").startsWith("Windows")) {
                assertTrue(base.getFamily().startsWith("Segoe UI"), base.getFamily());
            }
            Font mono = Tokens.mono(Tokens.BODY);
            assertTrue(mono.getFamily().equals(Tokens.MONO_FONT) || mono.getFamily().equals(Tokens.MONO_FALLBACK)
                    || mono.getFamily().equalsIgnoreCase(Font.MONOSPACED), mono.getFamily());
        });
    }

    @Test
    void laDimensioneDelleImpostazioniScalaTuttaLaScalaTipografica() {
        int[] at13 = fromEdt(ThemeTokensTest::scale);
        assertEquals(Tokens.BODY, at13[0]);
        assertEquals(Tokens.DISPLAY, at13[1], "display 24");
        assertEquals(Tokens.HEADING, at13[2], "heading 18");
        assertEquals(Tokens.TITLE, at13[3], "title 15");
        assertEquals(Tokens.CAPTION, at13[4], "caption 11");
        // come fanno le impostazioni (SettingsController.applyFont) per il proiettore
        onEdt(() -> {
            UIManager.put("defaultFont", UIManager.getFont("defaultFont").deriveFont(26f));
            FlatLaf.updateUI();
        });
        int[] at26 = fromEdt(ThemeTokensTest::scale);
        assertEquals(26, at26[0]);
        for (int i = 1; i < at13.length; i++) {
            assertEquals(at13[i] * 2, at26[i], 1, "la voce " + i + " della scala raddoppia con il carattere");
        }
        assertEquals(Tokens.CAPTION * 2, fromEdt(() -> Tokens.font(Tokens.CAPTION, Font.PLAIN).getSize()), 1);
    }

    /** corpo, display, heading, title, caption: dai caratteri degli stili, come li vede un'etichetta. */
    private static int[] scale() {
        int[] sizes = new int[5];
        String[] classes = {null, "display", "heading", "title", "caption"};
        for (int i = 0; i < classes.length; i++) {
            JLabel label = new JLabel("Aa");
            if (classes[i] != null) {
                label.putClientProperty("FlatLaf.styleClass", classes[i]);
            }
            label.updateUI();
            sizes[i] = label.getFont().getSize();
        }
        return sizes;
    }
}
