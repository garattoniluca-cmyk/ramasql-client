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

import static it.ramasql.app.theme.ThemeTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * (b) Ogni SVG di {@code icons/} si carica, si disegna a scala 1, 1,5 e 2 senza eccezioni e non è vuoto; le icone
 * seguono le regole del disegno (§2: tratto 1,5 px, estremità arrotondate, solo colori dei token). Il foglio di
 * tutte le icone va in {@code test-results/step2/tema-icone.png}.
 */
@Tag("step2")
@Tag("ui")
class IconsTest {

    private static final double[] SCALES = {1.0, 1.5, 2.0};

    @BeforeEach
    void setUp() {
        ThemeTestSupport.setupTheme();
    }

    @Test
    void ogniFileSvgEElencatoInAppIconsEViceversa() throws Exception {
        TreeSet<String> onDisk = new TreeSet<>();
        try (Stream<Path> files = Files.list(iconsFolder())) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".svg"))
                    .forEach(n -> onDisk.add(n.substring(0, n.length() - 4)));
        }
        assertEquals(new TreeSet<>(AppIcons.ALL), onDisk);
        assertTrue(onDisk.size() >= 45, "set completo: " + onDisk.size());
    }

    @Test
    void ogniIconaSiDisegnaATutteLeScaleENonEVuota() {
        onEdt(() -> {
            for (String name : AppIcons.ALL) {
                int size = name.equals(AppIcons.APP) ? 32 : isToolbar(name) ? Tokens.ICON_TOOLBAR : Tokens.ICON_SMALL;
                FlatSVGIcon icon = AppIcons.get(name, size);
                assertTrue(icon.hasFound(), name + ": file non trovato");
                for (double scale : SCALES) {
                    int px = (int) Math.ceil(size * scale) + 2;
                    BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    try {
                        g.scale(scale, scale);
                        icon.paintIcon(null, g, 0, 0);
                    } finally {
                        g.dispose();
                    }
                    int painted = paintedPixels(img);
                    assertTrue(painted > px, name + " a scala " + scale + ": disegna solo " + painted + " pixel");
                }
                // versione disabilitata (FlatLaf): grigia, ma sempre visibile
                assertTrue(icon.getDisabledIcon() != null, name);
            }
        });
    }

    @Test
    void leIconeSeguonoLeRegoleDelDisegno() throws Exception {
        Pattern color = Pattern.compile("#[0-9A-Fa-f]{6}");
        List<String> allowed = Stream.of(Tokens.TEXT_SECONDARY, Tokens.ACCENT, Tokens.ACCENT_PRESSED, Tokens.DANGER,
                Tokens.SUCCESS, Tokens.WARNING, Tokens.ENGINE_MYISAM, Tokens.KEY_GOLD, Tokens.ON_ACCENT)
                .map(Tokens::hex).toList();
        try (Stream<Path> files = Files.list(iconsFolder())) {
            for (Path file : files.filter(p -> p.toString().endsWith(".svg")).toList()) {
                String svg = Files.readString(file, StandardCharsets.UTF_8);
                String name = file.getFileName().toString();
                assertFalse(svg.contains("<image") || svg.contains("xlink:href") || svg.contains("base64"),
                        name + ": solo vettori disegnati da noi, nessuna immagine incorporata");
                Matcher m = color.matcher(svg);
                while (m.find()) {
                    assertTrue(allowed.contains(m.group().toLowerCase()), name + ": colore fuori dai token " + m.group());
                }
                if (!name.equals("app.svg")) {
                    int size = Integer.parseInt(svg.replaceAll("(?s).*viewBox=\"0 0 (\\d+) \\d+\".*", "$1"));
                    assertTrue(size == 16 || size == 20, name + ": griglia 16 o 20, non " + size);
                    if (svg.contains("stroke=")) {
                        assertTrue(svg.contains("stroke-width=\"1.5\"") && svg.contains("stroke-linecap=\"round\""),
                                name + ": tratto 1,5 px con estremità arrotondate");
                    }
                }
            }
        }
    }

    @Test
    void lIconaDellApplicazioneHaTutteLeDimensioni() {
        List<Image> images = AppIcons.windowImages();
        assertEquals(List.of(16, 24, 32, 48, 64, 128, 256), images.stream().map(i -> i.getWidth(null)).toList());
        for (Image image : images) {
            assertTrue(paintedPixels((BufferedImage) image) > image.getWidth(null), "icona vuota");
        }
    }

    @Test
    void foglioDelleIcone() {
        onEdt(() -> {
            int columns = 8;
            int cell = 120;
            int rows = (AppIcons.ALL.size() + columns - 1) / columns;
            BufferedImage sheet = new BufferedImage(columns * cell, rows * cell + 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(Tokens.BG_SURFACE);
                g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                g.setColor(Tokens.TEXT_PRIMARY);
                g.setFont(Tokens.semibold(Tokens.TITLE));
                g.drawString("Icone di RamaSQL — disegnate da noi (DESIGN-SYSTEM §2): a 1× e 2×", 16, 26);
                g.setFont(Tokens.font(Tokens.CAPTION, Font.PLAIN));
                for (int i = 0; i < AppIcons.ALL.size(); i++) {
                    String name = AppIcons.ALL.get(i);
                    int x = (i % columns) * cell;
                    int y = 40 + (i / columns) * cell;
                    int size = name.equals(AppIcons.APP) ? 32 : isToolbar(name) ? 20 : 16;
                    g.setColor(i % 2 == 0 ? Tokens.BG_WINDOW : Tokens.BG_SURFACE);
                    g.fillRect(x, y, cell, cell);
                    AppIcons.get(name, size).paintIcon(null, g, x + 12, y + 16);
                    Graphics2D big = (Graphics2D) g.create();
                    big.translate(x + 50, y + 12);
                    big.scale(2, 2);
                    AppIcons.get(name, size).paintIcon(null, big, 0, 0);
                    big.dispose();
                    g.setColor(Tokens.TEXT_SECONDARY);
                    g.drawString(name, x + 8, y + cell - 14);
                }
            } finally {
                g.dispose();
            }
            ThemeTestSupport.writePng(sheet, "tema-icone.png");
        });
    }

    static boolean isToolbar(String name) {
        return List.of(AppIcons.CONNECT, AppIcons.DISCONNECT, AppIcons.NEW_QUERY, AppIcons.VISUAL_QUERY,
                AppIcons.NEW_TABLE, AppIcons.NEW_VIEW, AppIcons.IMPORT, AppIcons.EXPORT, AppIcons.ER_MODEL,
                AppIcons.RUN, AppIcons.STOP).contains(name);
    }

    private static int paintedPixels(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (new Color(img.getRGB(x, y), true).getAlpha() > 40) {
                    n++;
                }
            }
        }
        return n;
    }

    private static Path iconsFolder() throws URISyntaxException, IOException {
        return Path.of(IconsTest.class.getClassLoader().getResource(AppIcons.FOLDER).toURI());
    }
}
