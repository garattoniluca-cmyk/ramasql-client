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

import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager2;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToggleButton;

/**
 * Barre che si stringono (T12.6, revisione T12.7): quando lo spazio non basta, alcuni pulsanti con icona perdono la
 * scritta, a gradini, finché la barra ci sta; il nome resta nel suggerimento e per chi legge lo schermo. Il pulsante
 * principale (per esempio <i>Conferma</i>) non si tocca.
 */
public final class Compact {

    private Compact() {
    }

    /** Un pulsante che può mostrare solo l'icona. */
    public interface Part {
        void setCompact(boolean compact);

        boolean isCompact();
    }

    public static class Button extends JButton implements Part {
        private static final long serialVersionUID = 1L;
        private boolean compact;

        public Button(String text) {
            super(text);
        }

        public Button(String text, Icon icon) {
            super(text, icon);
        }

        @Override
        public void setCompact(boolean compact) {
            if (this.compact != compact) {
                this.compact = compact;
                getAccessibleContext().setAccessibleName(compact ? super.getText() : null);
                invalidate();
                repaint();
            }
        }

        @Override
        public boolean isCompact() {
            return compact;
        }

        @Override
        public String getText() {
            return compact ? "" : super.getText();
        }
    }

    public static class Toggle extends JToggleButton implements Part {
        private static final long serialVersionUID = 1L;
        private boolean compact;

        public Toggle(String text) {
            super(text);
        }

        @Override
        public void setCompact(boolean compact) {
            if (this.compact != compact) {
                this.compact = compact;
                getAccessibleContext().setAccessibleName(compact ? super.getText() : null);
                invalidate();
                repaint();
            }
        }

        @Override
        public boolean isCompact() {
            return compact;
        }

        @Override
        public String getText() {
            return compact ? "" : super.getText();
        }
    }

    /**
     * Da chiamare all'inizio di {@code doLayout()} della barra: i gradini si applicano in ordine (il primo gradino
     * compatta i pulsanti della prima lista, il secondo anche quelli della seconda…) finché la barra ci sta.
     */
    public static void fit(Container bar, List<List<? extends Part>> steps) {
        for (int level = 0; level <= steps.size(); level++) {
            for (int i = 0; i < steps.size(); i++) {
                for (Part p : steps.get(i)) {
                    p.setCompact(i < level);
                }
            }
            // durante la disposizione le misure in memoria non si azzerano da sole
            for (Component c : bar.getComponents()) {
                if (c instanceof Container k && k.getLayout() instanceof LayoutManager2 l) {
                    l.invalidateLayout(k);
                }
            }
            if (bar.getLayout() instanceof LayoutManager2 l) {
                l.invalidateLayout(bar);
            }
            if (bar.getLayout().minimumLayoutSize(bar).width <= bar.getWidth()) {
                return;
            }
        }
    }
}
