/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

/**
 * {@code BUG-026} — il titolo della versione portabile diceva «RamaSQL Client dev»: {@link ProductInfo} legge la
 * versione dal manifest del <b>proprio</b> jar ({@code ramasql-core}), che non aveva {@code Implementation-Version}.
 * Qui si dimostra (1) il meccanismo: la stessa classe caricata da un jar con quella voce restituisce la versione vera,
 * da un jar senza restituisce «dev», e (2) la correzione: il pom padre chiede le voci di manifest a
 * {@code maven-jar-plugin} per <b>tutti</b> i moduli.
 */
@Tag("step12")
class Bug026VersioneNelManifestTest {

    @TempDir
    Path dir;

    /** Un jar con dentro solo {@code ProductInfo.class} e il manifest dato. */
    private Path jarWithProductInfo(String name, String implementationVersion) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (implementationVersion != null) {
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "RamaSQL :: core");
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, implementationVersion);
        }
        Path jar = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream jos = new JarOutputStream(out, manifest);
                InputStream clazz = ProductInfo.class.getResourceAsStream("ProductInfo.class")) {
            assertNotNull(clazz, "ProductInfo.class tra le classi compilate");
            jos.putNextEntry(new JarEntry("it/ramasql/core/ProductInfo.class"));
            clazz.transferTo(jos);
            jos.closeEntry();
        }
        return jar;
    }

    /** Il titolo che {@link ProductInfo} darebbe se caricata da quel jar (caricatore isolato, niente classi di test). */
    private static String titleFrom(Path jar) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> info = Class.forName("it.ramasql.core.ProductInfo", true, loader);
            assertEquals(loader, info.getClassLoader(), "la classe viene proprio dal jar di prova");
            return (String) info.getMethod("title").invoke(null);
        }
    }

    @Test
    void conLaVoceNelManifestIlTitoloHaLaVersioneVera() throws Exception {
        assertEquals("RamaSQL Client 0.1.0", titleFrom(jarWithProductInfo("con-versione.jar", "0.1.0")));
    }

    @Test
    void senzaLaVoceNelManifestIlTitoloDiceDev() throws Exception {
        // era il caso di ramasql-core nella versione portabile
        assertEquals("RamaSQL Client dev", titleFrom(jarWithProductInfo("senza-versione.jar", null)));
    }

    @Test
    void ilPomPadreMetteLeVociDiManifestInTuttiIModuli() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("mvnw.cmd"))) {
            root = root.getParent();
        }
        assertNotNull(root, "radice del progetto");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        Document pom = f.newDocumentBuilder().parse(root.resolve("pom.xml").toFile());
        String value = (String) XPathFactory.newInstance().newXPath().evaluate(
                "/*[local-name()='project']/*[local-name()='build']/*[local-name()='pluginManagement']"
                        + "/*[local-name()='plugins']/*[local-name()='plugin'][*[local-name()='artifactId']"
                        + "='maven-jar-plugin']/*[local-name()='configuration']/*[local-name()='archive']"
                        + "/*[local-name()='manifest']/*[local-name()='addDefaultImplementationEntries']",
                pom, XPathConstants.STRING);
        assertEquals("true", value.strip(),
                "maven-jar-plugin nel pom padre: addDefaultImplementationEntries per tutti i moduli (core compreso)");
    }
}
