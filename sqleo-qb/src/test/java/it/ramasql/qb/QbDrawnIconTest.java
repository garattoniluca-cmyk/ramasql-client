/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Icone disegnate nel codice (2026-09-22): si disegnano a ogni scala, restano nel loro riquadro, sono diverse tra loro. */
@Tag("step1")
class QbDrawnIconTest {

    private static final int MARGINE = 4;

    /** Disegna l'icona dentro un'immagine più grande (sfondo trasparente), spostata di {@link #MARGINE}. */
    private static BufferedImage disegna(Icon icon) {
        BufferedImage img = new BufferedImage(icon.getIconWidth() + 2 * MARGINE, icon.getIconHeight() + 2 * MARGINE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            icon.paintIcon(null, g, MARGINE, MARGINE);
        } finally {
            g.dispose();
        }
        return img;
    }

    @ParameterizedTest(name = "scala {0}")
    @ValueSource(floats = {1f, 1.5f, 2f})
    void ogniIconaSiDisegnaAllaScalaERestaNelSuoRiquadro(float scala) {
        int lato = Math.round(16 * scala);
        for (QbIcon id : QbIcon.values()) {
            QbDrawnIcon icon = new QbDrawnIcon(id, scala);
            assertEquals(lato, icon.getIconWidth(), id + ": larghezza");
            assertEquals(lato, icon.getIconHeight(), id + ": altezza");

            BufferedImage img = disegna(icon);
            int dentro = 0;
            int fuori = 0;
            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    boolean coperto = (img.getRGB(x, y) >>> 24) > 0x20;
                    boolean nelRiquadro = x >= MARGINE && y >= MARGINE && x < MARGINE + lato && y < MARGINE + lato;
                    if (coperto && nelRiquadro) {
                        dentro++;
                    } else if (coperto) {
                        fuori++;
                    }
                }
            }
            assertTrue(dentro >= lato * lato / 8, id + " a scala " + scala + ": quasi vuota (" + dentro + " pixel)");
            assertEquals(0, fuori, id + " a scala " + scala + ": disegna fuori dal suo riquadro");
        }
    }

    @Test
    void iDisegniSonoDistintiPerSignificato() {
        Set<String> firme = new HashSet<>();
        // stesso disegno voluto: DIAG_OBJECT e QB_TABLE come DIAG_TABLE, DIAG_QUERY come QB_QUERY
        Set<QbIcon> stessoDisegno = Set.of(QbIcon.DIAG_OBJECT, QbIcon.QB_TABLE, QbIcon.DIAG_QUERY);
        for (QbIcon id : QbIcon.values()) {
            if (stessoDisegno.contains(id)) {
                continue;
            }
            BufferedImage img = disegna(new QbDrawnIcon(id, 1f));
            StringBuilder firma = new StringBuilder();
            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    firma.append(Integer.toHexString(img.getRGB(x, y))).append(',');
                }
            }
            assertTrue(firme.add(firma.toString()), id + " ha lo stesso disegno di un'altra icona");
        }
        assertEquals(13, firme.size(), "13 disegni diversi (uno per ciascuna delle vecchie PNG)");
    }

    @Test
    void lHostDiBaseScalaLeIconeColFontDelLookAndFeel() {
        Font prima = UIManager.getFont("Label.font");
        try {
            UIManager.put("Label.font", new FontUIResource("Dialog", Font.PLAIN, 12));
            BasicQbHost host = new BasicQbHost();
            Icon a100 = host.icon(QbIcon.QB_WHERE);
            assertInstanceOf(QbDrawnIcon.class, a100);
            assertEquals(16, a100.getIconWidth());

            UIManager.put("Label.font", new FontUIResource("Dialog", Font.PLAIN, 24));
            Icon a200 = host.icon(QbIcon.QB_WHERE);
            assertEquals(32, a200.getIconWidth(), "l'icona deve seguire la scala corrente");
            assertEquals(host.scale(16), a200.getIconHeight());
        } finally {
            UIManager.put("Label.font", prima);
        }
    }
}
