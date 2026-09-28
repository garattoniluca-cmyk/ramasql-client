/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.app.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import it.ramasql.app.theme.RamaSqlLaf;
import it.ramasql.core.metadata.ColumnDef;
import it.ramasql.core.metadata.ForeignKeyDef;
import it.ramasql.core.metadata.IndexDef;
import it.ramasql.core.metadata.TableDef;
import it.ramasql.model.ErModel;
import it.ramasql.model.Relationship;
import it.ramasql.model.ReverseEngineer;

/**
 * Ciò che il diagramma disegna, misurato sull'immagine: la zampa di gallina dal lato «molti» (e da entrambi per N:M
 * indicativa), niente zampa dal lato «uno»; le relazioni parallele ben separate; e il modello ER che non raggiunge mai
 * il database.
 */
@Tag("step11")
class ErCanvasNotationTest {

    private static ErModel twoTables() {
        TableDef editori = TableDef.of("x", "editori").withColumns(List.of(ColumnDef.of("id", "INT").withNullable(false),
                ColumnDef.of("nome", "VARCHAR", "40"))).withIndexes(List.of(IndexDef.primary("id")));
        TableDef libri = TableDef.of("x", "libri").withColumns(List.of(ColumnDef.of("id", "INT").withNullable(false),
                ColumnDef.of("id_editore", "INT").withNullable(false))).withIndexes(List.of(IndexDef.primary("id")))
                .withForeignKeys(List.of(ForeignKeyDef.of("fk", "id_editore", "editori", "id")));
        ErModel m = ReverseEngineer.build("x", List.of(libri, editori), true);
        return m.withEntities(List.of(m.entity("libri").orElseThrow().at(40, 40),
                m.entity("editori").orElseThrow().at(400, 40)));
    }

    /** Pixel scuri sulla verticale x, fra y-6 e y+6 unità (scala 2): la zampa si apre lì, la barretta no. */
    private static int darkAt(BufferedImage img, ErCanvas c, double x, double y, double scale) {
        Rectangle2D ext = c.extent();
        int px = (int) Math.round((x - ext.getX() + ErCanvas.margin()) * scale);
        int count = 0;
        for (double dy = -5; dy <= 5; dy += 0.5) {
            int py = (int) Math.round((y + dy - ext.getY() + ErCanvas.margin()) * scale);
            Color col = new Color(img.getRGB(px, py));
            if ((col.getRed() + col.getGreen() + col.getBlue()) / 3 < 180) {
                count++;
            }
        }
        return count;
    }

    @Test
    void zampaDiGallinaDalLatoMoltiEDaEntrambiPerNm() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RamaSqlLaf.setup();
            ErCanvas c = new ErCanvas();
            ErModel m = twoTables();
            c.setModel(m);
            String id = m.physical().get(0).id();
            List<Point2D> pts = c.routePoints(id);
            Point2D child = pts.get(0);
            Point2D parent = pts.get(pts.size() - 1);
            double dirC = Math.signum(pts.get(1).getX() - child.getX());
            double dirP = Math.signum(pts.get(pts.size() - 2).getX() - parent.getX());
            BufferedImage img = c.render(2.0);
            // 1:N: al bordo della figlia la zampa (le tre linee si aprono per 12 unità), al bordo del padre no
            assertTrue(darkAt(img, c, child.getX() + dirC * 2, child.getY(), 2.0) >= 6, "zampa dal lato molti");
            assertTrue(darkAt(img, c, parent.getX() + dirP * 2, parent.getY(), 2.0) <= 3, "nessuna zampa dal lato uno");
            assertTrue(darkAt(img, c, parent.getX() + dirP * 7, parent.getY(), 2.0) >= 6, "barretta del lato uno");
            // N:M indicativa: la zampa anche dal lato del padre
            Relationship r = m.physical().get(0).withCardinality(Relationship.Cardinality.MANY_TO_MANY);
            c.setModel(m.withRelationships(List.of(r)));
            BufferedImage nm = c.render(2.0);
            assertTrue(darkAt(nm, c, parent.getX() + dirP * 2, parent.getY(), 2.0) >= 6, "zampa anche dal lato del padre");
            assertTrue(darkAt(nm, c, child.getX() + dirC * 2, child.getY(), 2.0) >= 6);
        });
    }

    @Test
    void ilModelloErNonRaggiungeMaiIlDatabase() throws IOException {
        List<String> hits = new ArrayList<>();
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("model"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "cartella del progetto");
        for (Path dir : List.of(root.resolve("app/src/main/java/it/ramasql/app/er"),
                root.resolve("model/src/main/java/it/ramasql/model"))) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(f);
                    for (String forbidden : List.of("SqlExecutor", "SqlPipeline", "java.sql.Connection", "Statement",
                            "executeQuery", "executeUpdate", "submitScript", ".propose(")) {
                        if (text.contains(forbidden)) {
                            hits.add(f.getFileName() + ": " + forbidden);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), hits, "il modello legge solo dal lettore dei metadati e non esegue SQL");
        assertFalse(hits.size() > 0);
    }
}
