/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.servertest;

import static it.ramasql.app.servertest.Probe.fromEdt;
import static it.ramasql.app.servertest.Probe.onEdt;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import com.sqleo.querybuilder.QbOperations;
import com.sqleo.querybuilder.QueryBuilder;

import it.ramasql.app.theme.RamaSqlLaf;
import it.ramasql.app.visual.VisualQueryTab;
import it.ramasql.core.connection.AppData;

/**
 * La scena di T7.9, eseguita in una JVM a sé con la scala di FlatLaf voluta ({@code -Dflatlaf.uiScale}), come fa
 * Windows con lo schermo al 150%: il <b>programma vero</b>, con il suo tema, collegato al server, apre la query visiva,
 * aggiunge tre tabelle con filtri su colonne dal nome lungo (il caso di {@code BUG-011}) e prepara le maschere.
 * Poi controlla ogni etichetta e ogni pulsante del diagramma, della barra della scheda e delle maschere con il calcolo
 * di Swing ({@link SwingUtilities#layoutCompoundLabel}): se il testo che verrebbe disegnato non è quello intero, è
 * tagliato. Scrive il rapporto (una riga «TAGLIATO …» per ogni testo tagliato) e l'immagine.
 *
 * <p>Argomenti: server ({@code MARIADB}/{@code MYSQL}), cartella dei dati, file del rapporto, file dell'immagine.
 */
public final class T79Scena {

    private T79Scena() {
    }

    public static void main(String[] args) throws Exception {
        DbServer server = DbServer.valueOf(args[0]);
        System.setProperty(AppData.OVERRIDE_PROPERTY, args[1]);
        Path report = Path.of(args[2]);
        Path image = Path.of(args[3]);
        Locale.setDefault(Locale.ITALIAN);
        onEdt(RamaSqlLaf::setup);
        String catalog = DbServer.newCatalogName("resa79");
        StringBuilder out = new StringBuilder();
        int code = 1;
        try {
            server.createCatalog(catalog);
            server.loadFixture(catalog, "biblioteca.sql");
            try (ClientApp a = ClientApp.connect(server, Path.of(args[1]))) {
                // come fa Windows con lo schermo al 150%: la finestra cresce con la scala (qui la scala è di FlatLaf)
                double scala = Double.parseDouble(System.getProperty("flatlaf.uiScale", "1"));
                onEdt(() -> a.frame().setSize((int) (a.frame().getPreferredSize().width * scala),
                        (int) (a.frame().getPreferredSize().height * scala)));
                // il pannello SQL in basso resta della sua altezza (330 px a 100%), come nella finestra vera
                onEdt(() -> {
                    a.frame().validate();
                    a.frame().sqlPanelSplit().setDividerLocation(a.frame().sqlPanelSplit().getHeight()
                            - (int) (330 * scala));
                    a.frame().validate();
                });
                VisualQueryTab tab = VisualSupport.openFromToolbar(a, catalog);
                QueryBuilder qb = tab.queryBuilder();
                onEdt(() -> {
                    QbOperations.addTable(qb, "prestiti");
                    QbOperations.addTable(qb, "soci");
                    QbOperations.addTable(qb, "libri");
                    QbOperations.addWhere(qb, "prestiti", "data_reso", "IS", "NULL");
                    QbOperations.addWhere(qb, "prestiti", "data_prestito", ">", "'2020-01-01'");
                    QbOperations.addWhere(qb, "libri", "prezzo", ">", "10");
                    QbOperations.addWhere(qb, "soci", "nato_il", "<", "'2000-01-01'");
                });
                onEdt(() -> a.frame().validate());
                List<String> problemi = new ArrayList<>();
                int[] controllati = {0};
                onEdt(() -> {
                    for (javax.swing.JInternalFrame f : QbOperations.entityFrames(qb)) {
                        f.validate();
                        controlla(f, "entità " + f.getName(), problemi, controllati);
                        Dimension pref = f.getPreferredSize();
                        if (f.getWidth() < pref.width) {
                            problemi.add("STRETTA entità " + f.getName() + ": " + f.getWidth() + " < " + pref.width);
                        }
                    }
                    controlla((Container) tab.getComponent(0), "barra della scheda", problemi, controllati);
                    controlla(QbOperations.queryTree(qb), "albero della query", problemi, controllati);
                    for (JComponent mask : QbOperations.masksForRendering(qb)) {
                        // la maschera vera è una finestra grande esattamente quanto la sua dimensione preferita
                        mask.setSize(mask.getPreferredSize());
                        layoutAll(mask);
                        controlla(mask, "maschera " + mask.getName(), problemi, controllati);
                    }
                    // l'elenco delle tabelle si vede (almeno 5 righe) e il diagramma ha spazio (almeno il 40% della scheda)
                    javax.swing.JList<?> elenco = QbOperations.objectList(qb);
                    int righe = elenco.getVisibleRect().height / Math.max(1, elenco.getCellBounds(0, 0).height);
                    if (righe < 5) {
                        problemi.add("ELENCO delle tabelle: solo " + righe + " righe visibili");
                    }
                    controllati[0]++;
                    int alto = QbOperations.diagram(qb).getHeight();
                    if (alto * 100 < tab.getHeight() * 40) {
                        problemi.add("DIAGRAMMA schiacciato: " + alto + " px su " + tab.getHeight());
                    }
                    controllati[0]++;
                    // ogni riga di campo contiene la sua casella (a 150% le caselle si toccavano)
                    for (javax.swing.JInternalFrame f : QbOperations.entityFrames(qb)) {
                        for (Component campo : ((javax.swing.JComponent) f.getContentPane().getComponent(0)).getComponents()) {
                            if (campo instanceof Container riga && riga.getComponentCount() > 0
                                    && riga.getComponent(0) instanceof javax.swing.JCheckBox box) {
                                controllati[0]++;
                                if (box.getPreferredSize().height > riga.getHeight() + 1) {
                                    problemi.add("CASELLA più alta della riga in " + f.getName() + ": "
                                            + box.getPreferredSize().height + " > " + riga.getHeight());
                                }
                            }
                        }
                    }
                });
                Probe.paintWindow("step7", a.frame(), image.getFileName().toString());
                // la fascia d'avviso con un motivo lungo va a capo, non si taglia
                onEdt(() -> tab.setSql("SELECT titolo, ROW_NUMBER() OVER (ORDER BY prezzo DESC, id) AS posizione"
                        + " FROM libri ORDER BY posizione"));
                onEdt(() -> a.frame().validate());
                onEdt(() -> {
                    javax.swing.JTextArea avviso = trova(tab, "visualQuery.notice");
                    controllati[0]++;
                    if (avviso == null || avviso.getText().isBlank()) {
                        problemi.add("AVVISO assente per la funzione finestra");
                    } else {
                        layoutAll(tab);
                        if (avviso.getHeight() + 1 < avviso.getPreferredSize().height
                                || avviso.getWidth() > avviso.getParent().getWidth()) {
                            problemi.add("AVVISO tagliato: " + avviso.getWidth() + "x" + avviso.getHeight()
                                    + " per un testo che ne chiede " + avviso.getPreferredSize());
                        }
                    }
                });
                Probe.paintWindow("step7", a.frame(), image.getFileName().toString().replace(".png", "-avviso.png"));
                out.append("scalaFlatLaf=").append(System.getProperty("flatlaf.uiScale")).append('\n')
                        .append("server=").append(server.label()).append('\n')
                        .append("tabelle=").append(fromEdt(() -> QbOperations.tables(qb))).append('\n')
                        .append("testi controllati=").append(controllati[0]).append('\n');
                problemi.forEach(p -> out.append(p).append('\n'));
                out.append("problemi=").append(problemi.size()).append('\n');
                code = problemi.isEmpty() && controllati[0] > 30 ? 0 : 2;
            }
        } catch (Throwable t) {
            out.append("ERRORE ").append(t).append('\n');
        } finally {
            server.dropQuietly(catalog);
            Files.writeString(report, out.toString(), StandardCharsets.UTF_8);
        }
        System.exit(code);
    }

    private static javax.swing.JTextArea trova(Container c, String name) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JTextArea t && name.equals(k.getName())) {
                return t;
            }
            if (k instanceof Container cc) {
                javax.swing.JTextArea f = trova(cc, name);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static void layoutAll(Component c) {
        if (c instanceof Container k) {
            k.doLayout();
            for (Component child : k.getComponents()) {
                layoutAll(child);
            }
        }
    }

    /** Ogni etichetta e pulsante visibili con testo: il testo che Swing disegnerebbe deve essere quello intero. */
    private static void controlla(Component c, String dove, List<String> problemi, int[] n) {
        if (!c.isVisible()) {
            return;
        }
        String text = c instanceof JLabel l ? l.getText() : c instanceof AbstractButton b ? b.getText() : null;
        if (text != null && !text.isBlank() && !text.startsWith("<html>")) {
            JComponent j = (JComponent) c;
            n[0]++;
            FontMetrics fm = j.getFontMetrics(j.getFont());
            Insets in = j.getInsets();
            Rectangle view = new Rectangle(in.left, in.top, j.getWidth() - in.left - in.right,
                    j.getHeight() - in.top - in.bottom);
            Icon icon = c instanceof JLabel l ? l.getIcon() : ((AbstractButton) c).getIcon();
            int gap = c instanceof JLabel l ? l.getIconTextGap() : ((AbstractButton) c).getIconTextGap();
            int hAlign = c instanceof JLabel l ? l.getHorizontalAlignment() : ((AbstractButton) c).getHorizontalAlignment();
            int hPos = c instanceof JLabel l ? l.getHorizontalTextPosition()
                    : ((AbstractButton) c).getHorizontalTextPosition();
            int vAlign = c instanceof JLabel l ? l.getVerticalAlignment() : ((AbstractButton) c).getVerticalAlignment();
            int vPos = c instanceof JLabel l ? l.getVerticalTextPosition() : ((AbstractButton) c).getVerticalTextPosition();
            if (c instanceof javax.swing.JCheckBox || c instanceof javax.swing.JRadioButton) {
                Icon box = javax.swing.UIManager.getIcon(c instanceof javax.swing.JCheckBox ? "CheckBox.icon"
                        : "RadioButton.icon");
                icon = box;
            }
            String shown = SwingUtilities.layoutCompoundLabel(j, fm, text, icon, vAlign, hAlign, vPos, hPos, view,
                    new Rectangle(), new Rectangle(), text.isEmpty() || icon == null ? 0 : gap);
            if (!shown.equals(text)) {
                problemi.add("TAGLIATO in " + dove + ": «" + text + "» diventa «" + shown + "» (larghezza " + j.getWidth()
                        + ", serve " + (fm.stringWidth(text) + (icon == null ? 0 : icon.getIconWidth() + gap)
                        + in.left + in.right) + ")");
            }
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                controlla(child, dove, problemi, n);
            }
        }
    }
}
