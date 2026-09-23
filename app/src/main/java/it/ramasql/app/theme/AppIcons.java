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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;

/**
 * <b>Unico punto d'accesso alle icone</b> ({@code docs/DESIGN-SYSTEM.md} §2). Sono SVG disegnati da noi in
 * {@code it/ramasql/app/icons/} (griglia 20 per la barra strumenti, 16 per albero, menu e griglia; tratto 1,5 px,
 * due toni: tratto {@code text.secondary}, accenti {@code accent}). Caricate con {@link FlatSVGIcon}: dimensione
 * <em>logica</em> in px al 100%, scala HiDPI e del carattere automatica, versione disabilitata grigia fatta da FlatLaf.
 * Nessuna immagine di terzi, nessun logo di marchi.
 */
public final class AppIcons {

    /** Cartella delle icone nel classpath. */
    public static final String FOLDER = "it/ramasql/app/icons/";

    // ---------------------------------------------------------------- barra strumenti (20)
    public static final String CONNECT = "connect";
    public static final String DISCONNECT = "disconnect";
    public static final String NEW_QUERY = "new-query";
    public static final String VISUAL_QUERY = "visual-query";
    public static final String NEW_TABLE = "new-table";
    public static final String NEW_VIEW = "new-view";
    public static final String IMPORT = "import";
    public static final String EXPORT = "export";
    public static final String ER_MODEL = "er-model";
    public static final String RUN = "run";
    public static final String STOP = "stop";

    // ---------------------------------------------------------------- albero (16): le collega il navigatore
    public static final String TREE_SERVER = "tree-server";
    public static final String TREE_CATALOG = "tree-catalog";
    public static final String TREE_TABLE_INNODB = "tree-table-innodb";
    public static final String TREE_TABLE_MYISAM = "tree-table-myisam";
    public static final String TREE_VIEW = "tree-view";
    public static final String TREE_COLUMN = "tree-column";
    public static final String TREE_PRIMARY_KEY = "tree-primary-key";
    public static final String TREE_INDEX = "tree-index";
    public static final String TREE_FOREIGN_KEY = "tree-foreign-key";
    public static final String TREE_ROUTINE = "tree-routine";
    public static final String TREE_TRIGGER = "tree-trigger";
    public static final String TREE_EVENT = "tree-event";

    // ---------------------------------------------------------------- griglia (16)
    public static final String GRID_CONFIRM = "grid-confirm";
    public static final String GRID_DISCARD = "grid-discard";
    public static final String GRID_EXPORT = "grid-export";
    public static final String GRID_NULL = "grid-null";
    public static final String PAGE_PREVIOUS = "page-previous";
    public static final String PAGE_NEXT = "page-next";
    public static final String VIEW_GRID = "view-grid";
    public static final String VIEW_FORM = "view-form";
    /** Glifo del tipo «data/ora» nell'intestazione della griglia (i numeri hanno «#», i testi «Aa»). */
    public static final String TYPE_DATE = "type-date";

    // ---------------------------------------------------------------- stati (16)
    public static final String STATUS_SUCCESS = "status-success";
    public static final String STATUS_WARNING = "status-warning";
    public static final String STATUS_ERROR = "status-error";
    public static final String STATUS_INFO = "status-info";

    // ---------------------------------------------------------------- menu (16)
    public static final String MENU_IMPORT_PROFILES = "menu-import-profiles";
    public static final String MENU_EXPORT_PROFILES = "menu-export-profiles";
    public static final String MENU_SETTINGS = "menu-settings";
    public static final String MENU_EXIT = "menu-exit";
    public static final String MENU_ABOUT = "menu-about";
    public static final String MENU_NEW_CONNECTION = "menu-new-connection";

    // ---------------------------------------------------------------- generiche (16)
    public static final String FILTER = "filter";
    public static final String PLUS = "plus";
    public static final String MORE = "more";
    public static final String CHEVRON_DOWN = "chevron-down";
    public static final String COPY = "copy";

    /** L'icona dell'applicazione (finestre, barra delle applicazioni). */
    public static final String APP = "app";

    /** Tutte le icone, per i test (ognuna deve caricarsi e disegnare qualcosa). */
    public static final List<String> ALL = List.of(CONNECT, DISCONNECT, NEW_QUERY, VISUAL_QUERY, NEW_TABLE, NEW_VIEW,
            IMPORT, EXPORT, ER_MODEL, RUN, STOP, TREE_SERVER, TREE_CATALOG, TREE_TABLE_INNODB, TREE_TABLE_MYISAM,
            TREE_VIEW, TREE_COLUMN, TREE_PRIMARY_KEY, TREE_INDEX, TREE_FOREIGN_KEY, TREE_ROUTINE, TREE_TRIGGER,
            TREE_EVENT, GRID_CONFIRM, GRID_DISCARD, GRID_EXPORT, GRID_NULL, PAGE_PREVIOUS, PAGE_NEXT, VIEW_GRID,
            VIEW_FORM, TYPE_DATE, STATUS_SUCCESS, STATUS_WARNING, STATUS_ERROR, STATUS_INFO, MENU_IMPORT_PROFILES,
            MENU_EXPORT_PROFILES, MENU_SETTINGS, MENU_EXIT, MENU_ABOUT, MENU_NEW_CONNECTION, FILTER, PLUS, MORE,
            CHEVRON_DOWN, COPY, APP);

    private AppIcons() {
    }

    /** L'icona {@code name} alla dimensione logica {@code size} (px al 100%, scalata da FlatLaf). */
    public static FlatSVGIcon get(String name, int size) {
        return new FlatSVGIcon(FOLDER + name + ".svg", size, size, AppIcons.class.getClassLoader());
    }

    /** Icona della barra strumenti (20). */
    public static FlatSVGIcon toolbar(String name) {
        return get(name, Tokens.ICON_TOOLBAR);
    }

    /** Icona piccola (16): albero, menu, griglia, stati. */
    public static FlatSVGIcon small(String name) {
        return get(name, Tokens.ICON_SMALL);
    }

    /**
     * La stessa icona ridipinta per stare <em>su un fondo pieno</em> (pulsante primario d'accento o di pericolo):
     * tratto, accento e pericolo diventano {@code ON_ACCENT} (bianco).
     */
    public static FlatSVGIcon onAccent(FlatSVGIcon icon) {
        FlatSVGIcon.ColorFilter filter = new FlatSVGIcon.ColorFilter();
        for (Color c : new Color[] {Tokens.TEXT_SECONDARY, Tokens.ACCENT, Tokens.DANGER}) {
            filter.add(c, Tokens.ON_ACCENT);
        }
        FlatSVGIcon copy = icon.derive(1f);
        copy.setColorFilter(filter);
        return copy;
    }

    /** La stessa icona tutta in un colore (es. {@code danger} per un'azione distruttiva). */
    public static FlatSVGIcon tinted(FlatSVGIcon icon, Color color) {
        FlatSVGIcon copy = icon.derive(1f);
        copy.setColorFilter(new FlatSVGIcon.ColorFilter(c -> new Color(color.getRed(), color.getGreen(),
                color.getBlue(), c.getAlpha())));
        return copy;
    }

    // ---------------------------------------------------------------- icone dell'albero, con nomi chiari

    public static FlatSVGIcon treeServer() {
        return small(TREE_SERVER);
    }

    public static FlatSVGIcon treeCatalog() {
        return small(TREE_CATALOG);
    }

    /** Tabella: InnoDB in blu, MyISAM in viola con la «M» (distinguibile anche senza colori). */
    public static FlatSVGIcon treeTable(boolean myIsam) {
        return small(myIsam ? TREE_TABLE_MYISAM : TREE_TABLE_INNODB);
    }

    public static FlatSVGIcon treeView() {
        return small(TREE_VIEW);
    }

    public static FlatSVGIcon treeColumn() {
        return small(TREE_COLUMN);
    }

    /** Colonna della chiave primaria: chiave oro. */
    public static FlatSVGIcon treePrimaryKey() {
        return small(TREE_PRIMARY_KEY);
    }

    public static FlatSVGIcon treeIndex() {
        return small(TREE_INDEX);
    }

    public static FlatSVGIcon treeForeignKey() {
        return small(TREE_FOREIGN_KEY);
    }

    public static FlatSVGIcon treeRoutine() {
        return small(TREE_ROUTINE);
    }

    public static FlatSVGIcon treeTrigger() {
        return small(TREE_TRIGGER);
    }

    public static FlatSVGIcon treeEvent() {
        return small(TREE_EVENT);
    }

    /** Le immagini dell'icona dell'applicazione a tutte le dimensioni utili a Windows (16…256 px). */
    public static List<Image> windowImages() {
        List<Image> images = new ArrayList<>();
        for (int size : new int[] {16, 24, 32, 48, 64, 128, 256}) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                // l'icona scala da sé con l'interfaccia: qui serve la misura esatta in pixel
                float userScale = UIScale.getUserScaleFactor();
                g.scale(1 / userScale, 1 / userScale);
                get(APP, size).paintIcon(null, g, 0, 0);
            } finally {
                g.dispose();
            }
            images.add(image);
        }
        return images;
    }
}
