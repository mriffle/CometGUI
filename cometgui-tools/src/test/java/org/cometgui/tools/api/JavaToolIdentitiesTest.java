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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.limelight.LimelightConverterIdentity;
import org.cometgui.tools.pdv.PdvJarIdentity;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.Nulls;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Routing a JAR to the adapter that knows how to ask it, against both real artefacts.
 *
 * <p>This is the object the composition root hands to {@code
 * org.cometgui.install.probe.StagedToolProbe} as its {@code JavaArtefactIdentity}, so what is
 * checked here is that both of this product's JAR tools really are answerable through one method
 * reference -- and that the two native tools are refused rather than silently routed somewhere.
 */
class JavaToolIdentitiesTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static JavaToolIdentities identities() throws IOException {
        return JavaToolIdentities.usingThisApplicationsRuntime(
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120)));
    }

    @Test
    @DisplayName("both real JAR tools are identified through one router")
    void bothRealJars(@TempDir Path directory) throws IOException {
        Path pdv =
                UpstreamArtefacts.member(
                        "v2.7.0__PDV-2.7.0.zip",
                        "PDV-2.7.0/PDV-2.7.0.jar",
                        directory.resolve("pdv").resolve("PDV-2.7.0.jar"));
        Path converterDirectory = Files.createDirectories(directory.resolve("converter"));
        Path converter = converterDirectory.resolve("converter.jar");
        Files.copy(
                UpstreamArtefacts.artefact("v2.8.1__cometPercolator2LimelightXML.jar"),
                converter,
                StandardCopyOption.REPLACE_EXISTING);
        JavaToolIdentities router = identities();

        assertAll(
                () ->
                        assertEquals(
                                ToolVersion.parse("2.7.0"),
                                router.identify(ToolName.PDV, LINUX, pdv)),
                () ->
                        assertEquals(
                                ToolVersion.parse("2.8.1"),
                                router.identify(ToolName.LIMELIGHT_CONVERTER, LINUX, converter)));
    }

    @Test
    @DisplayName("the two native tools are refused: their identity is the banner they print")
    void theNativeToolsAreRefused(@TempDir Path directory) throws IOException {
        Path anything = Files.writeString(directory.resolve("x.jar"), "PK");
        JavaToolIdentities router = identities();

        assertAll(
                () ->
                        assertEquals(
                                "comet is a native executable, not a JAR: its identity is the"
                                        + " version banner it prints, and asking for it here would"
                                        + " skip the loadability stage that runs it",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        router.identify(
                                                                ToolName.COMET, LINUX, anything))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "percolator is a native executable, not a JAR: its identity is the"
                                        + " version banner it prints, and asking for it here would"
                                        + " skip the loadability stage that runs it",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        router.identify(
                                                                ToolName.PERCOLATOR,
                                                                LINUX,
                                                                anything))
                                        .getMessage()));
    }

    @Test
    @DisplayName("every argument is required, of the router and of its construction")
    void everyArgumentIsRequired(@TempDir Path directory) throws IOException {
        Path anything = Files.writeString(directory.resolve("x.jar"), "PK");
        JavaToolIdentities router = identities();
        ToolRunner runner =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(1));
        LimelightConverterIdentity converter =
                new LimelightConverterIdentity(runner, JavaRuntime.ofThisApplication());

        assertAll(
                () ->
                        assertEquals(
                                "tool",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        router.identify(
                                                                Nulls.of(ToolName.class),
                                                                LINUX,
                                                                anything))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "platform",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        router.identify(
                                                                ToolName.PDV,
                                                                Nulls.of(HostPlatform.class),
                                                                anything))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "jar",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        router.identify(
                                                                ToolName.PDV,
                                                                LINUX,
                                                                Nulls.of(Path.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "pdv",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new JavaToolIdentities(
                                                                Nulls.of(PdvJarIdentity.class),
                                                                converter))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "converter",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new JavaToolIdentities(
                                                                new PdvJarIdentity(),
                                                                Nulls.of(
                                                                        LimelightConverterIdentity
                                                                                .class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "runner",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        JavaToolIdentities
                                                                .usingThisApplicationsRuntime(
                                                                        Nulls.of(ToolRunner.class)))
                                        .getMessage()));
    }
}
