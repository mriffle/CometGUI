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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PDV's identity, read from the real PDV 2.7.0 JAR inside the real upstream ZIP.
 *
 * <p>The JAR is taken out of {@code PDV-2.7.0.zip} in the gitignored mirror at the member path the
 * artefact manifest names, so what is read here is the file the installer would install.
 */
class PdvJarIdentityTest {

    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";
    private static final String PDV_MEMBER = "PDV-2.7.0/PDV-2.7.0.jar";

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static Path realPdvJar(Path directory) throws IOException {
        return UpstreamArtefacts.member(
                PDV_ZIP, PDV_MEMBER, directory.resolve("PDV-2.7.0").resolve("PDV-2.7.0.jar"));
    }

    private static Path jarWith(Path directory, String name, String manifest) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar);
                ZipOutputStream archive = new ZipOutputStream(out)) {
            archive.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            archive.write(manifest.getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return jar;
    }

    @Test
    @DisplayName("the real PDV JAR identifies itself as 2.7.0, from its own manifest")
    void theRealJar(@TempDir Path directory) throws IOException {
        Path jar = realPdvJar(directory);

        ToolVersion version = new PdvJarIdentity().identify(ToolName.PDV, LINUX, jar);

        assertAll(
                () -> assertEquals(ToolVersion.parse("2.7.0"), version),
                () ->
                        assertEquals(
                                "2.7.0",
                                version.text(),
                                "the text is what upstream calls the release, which is what a"
                                        + " scientist can look up"),
                () -> assertEquals(1343276L, Files.size(jar)));
    }

    @Test
    @DisplayName("a JAR that is not PDV is refused, quoting both titles")
    void aJarThatIsNotPdv(@TempDir Path directory) throws IOException {
        Path jar =
                jarWith(
                        directory,
                        "other.jar",
                        "Manifest-Version: 1.0\r\n"
                                + "Implementation-Title: SomethingElse\r\n"
                                + "Implementation-Version: 2.7.0\r\n\r\n");

        assertEquals(
                jar
                        + " is not PDV: its manifest declares Implementation-Title as"
                        + " \"SomethingElse\" and PDV's declares \"PDV\"",
                assertThrows(
                                IOException.class,
                                () -> new PdvJarIdentity().identify(ToolName.PDV, LINUX, jar))
                        .getMessage());
    }

    @Test
    @DisplayName("a JAR with no title at all is refused the same way, quoting the empty value")
    void aJarWithNoTitle(@TempDir Path directory) throws IOException {
        Path jar =
                jarWith(
                        directory,
                        "untitled.jar",
                        "Manifest-Version: 1.0\r\nImplementation-Version: 2.7.0\r\n\r\n");

        assertEquals(
                jar
                        + " is not PDV: its manifest declares Implementation-Title as \"\" and"
                        + " PDV's declares \"PDV\"",
                assertThrows(
                                IOException.class,
                                () -> new PdvJarIdentity().identify(ToolName.PDV, LINUX, jar))
                        .getMessage());
    }

    @Test
    @DisplayName(
            "PDV with no declared version is refused, and the refusal says why nothing is left")
    void aJarWithNoVersion(@TempDir Path directory) throws IOException {
        Path jar =
                jarWith(
                        directory,
                        "no-version.jar",
                        "Manifest-Version: 1.0\r\nImplementation-Title: PDV\r\n\r\n");

        assertEquals(
                jar
                        + " declares no Implementation-Version in its manifest, and PDV prints no"
                        + " version at all, so there is nothing left to identify it by",
                assertThrows(
                                IOException.class,
                                () -> new PdvJarIdentity().identify(ToolName.PDV, LINUX, jar))
                        .getMessage());
    }

    @Test
    @DisplayName("a declared version that is not a version is refused rather than half-read")
    void aVersionThatIsNotOne(@TempDir Path directory) throws IOException {
        Path jar =
                jarWith(
                        directory,
                        "odd-version.jar",
                        "Manifest-Version: 1.0\r\n"
                                + "Implementation-Title: PDV\r\n"
                                + "Implementation-Version: 2.7.0-SNAPSHOT\r\n\r\n");

        assertEquals(
                jar
                        + " declares Implementation-Version as \"2.7.0-SNAPSHOT\", which is not a"
                        + " version this product accepts: not a recognised tool version:"
                        + " \"2.7.0-SNAPSHOT\" (expected two to four numeric components, such as"
                        + " 3.09, 3.07.1 or 2026.02.2)",
                assertThrows(
                                IOException.class,
                                () -> new PdvJarIdentity().identify(ToolName.PDV, LINUX, jar))
                        .getMessage());
    }

    @Test
    @DisplayName("a file that is not a JAR is refused, naming the file")
    void aFileThatIsNotAJar(@TempDir Path directory) throws IOException {
        Path notAJar = Files.writeString(directory.resolve("plain.txt"), "hello");

        assertEquals(
                notAJar
                        + " cannot be read as a JAR: zip END header not found"
                        + " (java.util.zip.ZipException)",
                assertThrows(
                                IOException.class,
                                () -> new PdvJarIdentity().identify(ToolName.PDV, LINUX, notAJar))
                        .getMessage());
    }

    @Test
    @DisplayName("another tool is refused, and every argument is required")
    void refusalsAndNulls(@TempDir Path directory) throws IOException {
        Path jar = realPdvJar(directory);
        PdvJarIdentity identity = new PdvJarIdentity();

        assertAll(
                () ->
                        assertEquals(
                                "this reads PDV's identity and was asked about limelight-converter",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        identity.identify(
                                                                ToolName.LIMELIGHT_CONVERTER,
                                                                LINUX,
                                                                jar))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "tool",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> identity.identify(null, LINUX, jar))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "platform",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> identity.identify(ToolName.PDV, null, jar))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "jar",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> identity.identify(ToolName.PDV, LINUX, null))
                                        .getMessage()));
    }
}
