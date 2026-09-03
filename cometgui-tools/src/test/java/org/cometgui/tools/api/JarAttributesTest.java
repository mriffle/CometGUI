/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.tools.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading a JAR's own manifest -- against the real artefacts, not a fixture that says what is
 * convenient.
 */
class JarAttributesTest {

    @Test
    @DisplayName("the real Limelight converter JAR declares its release tag and its main class")
    void theRealConverterJar() throws IOException {
        JarAttributes attributes =
                JarAttributes.of(
                        UpstreamArtefacts.artefact("v2.8.1__cometPercolator2LimelightXML.jar"));

        assertAll(
                () ->
                        assertEquals(
                                Optional.of("v2.8.1"), attributes.value("LIMELIGHT_RELEASE_TAG")),
                () -> assertEquals(Optional.of("v2.8.1"), attributes.value("GIT-Tag-at-HEAD")),
                () ->
                        assertEquals(
                                Optional.of(
                                        "org.yeastrc.limelight.xml.comet_percolator.main"
                                                + ".MainProgram"),
                                attributes.value("Main-Class")),
                () ->
                        assertEquals(
                                Optional.empty(),
                                attributes.value("Implementation-Version"),
                                "the converter carries no Implementation-Version at all, which is"
                                        + " why its identity is established by running it rather"
                                        + " than by reading this manifest"));
    }

    @Test
    @DisplayName("attribute names are matched exactly, never case-folded")
    void namesAreMatchedExactly() throws IOException {
        JarAttributes attributes =
                JarAttributes.of(
                        UpstreamArtefacts.artefact("v2.8.1__cometPercolator2LimelightXML.jar"));

        assertAll(
                () -> assertEquals(Optional.empty(), attributes.value("main-class")),
                () -> assertEquals(Optional.empty(), attributes.value("MAIN-CLASS")),
                () -> assertTrue(attributes.value("Main-Class").isPresent()),
                () -> assertTrue(attributes.all().containsKey("Manifest-Version")));
    }

    @Test
    @DisplayName("a file that is not a JAR is refused, not read as an empty attribute set")
    void aFileThatIsNotAJar(@TempDir Path directory) throws IOException {
        Path notAJar = Files.writeString(directory.resolve("plain.txt"), "hello");

        assertEquals(
                notAJar
                        + " cannot be read as a JAR: zip END header not found"
                        + " (java.util.zip.ZipException)",
                assertThrows(IOException.class, () -> JarAttributes.of(notAJar)).getMessage(),
                "the JDK's own refusal is \"zip END header not found\" and names no file at all,"
                        + " which tells a reader what went wrong and not what it went wrong on");
    }

    @Test
    @DisplayName(
            "a ZIP with no manifest is refused by name: that is not the same as saying nothing")
    void aZipWithNoManifest(@TempDir Path directory) throws IOException {
        Path zip = directory.resolve("empty.jar");
        try (OutputStream out = Files.newOutputStream(zip);
                ZipOutputStream archive = new ZipOutputStream(out)) {
            archive.putNextEntry(new ZipEntry("something.txt"));
            archive.write("content".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }

        assertEquals(
                zip
                        + " is a ZIP container with no META-INF/MANIFEST.MF, so it is not a JAR"
                        + " this product can identify",
                assertThrows(IOException.class, () -> JarAttributes.of(zip)).getMessage());
    }

    @Test
    @DisplayName("a path that is not a file at all is refused before anything is opened")
    void aMissingFile(@TempDir Path directory) {
        Path missing = directory.resolve("gone.jar");

        assertEquals(
                missing + " is not a regular file, so it cannot be read as a JAR",
                assertThrows(IOException.class, () -> JarAttributes.of(missing)).getMessage());
    }

    @Test
    @DisplayName("a blank attribute value is empty, because a blank value is not a value")
    void aBlankValueIsEmpty(@TempDir Path directory) throws IOException {
        Path jar = directory.resolve("blank.jar");
        try (OutputStream out = Files.newOutputStream(jar);
                ZipOutputStream archive = new ZipOutputStream(out)) {
            archive.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            archive.write(
                    "Manifest-Version: 1.0\r\nImplementation-Title:  \r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }

        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                JarAttributes.of(jar).value("Implementation-Title")),
                () -> assertEquals(Optional.empty(), JarAttributes.of(jar).value("Absent-Name")),
                () ->
                        assertEquals(
                                "name",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> JarAttributes.of(jar).value(null))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "jar",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> JarAttributes.of(null))
                                        .getMessage()));
    }
}
