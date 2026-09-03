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

package org.cometgui.tools.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.ScriptedRunner;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Limelight converter's identity, established by actually starting the real JAR.
 *
 * <p>{@link #theRealJarPrintsItsVersion} launches {@code java -jar} on the pinned v2.8.1 artefact
 * from the gitignored mirror, through the real process service, and reads the line it prints. The
 * refusals are graded with a scripted runner, because a JAR that answers {@code --version} with the
 * wrong thing is not an artefact anyone publishes.
 */
class LimelightConverterIdentityTest {

    private static final String CONVERTER_FILE = "v2.8.1__cometPercolator2LimelightXML.jar";

    /** The manifest's SHA-256 for the converter JAR, hand-typed. */
    private static final String CONVERTER_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static Path stage(Path directory) throws IOException {
        Path jar = directory.resolve("cometPercolator2LimelightXML.jar");
        Files.copy(
                UpstreamArtefacts.artefact(CONVERTER_FILE),
                jar,
                StandardCopyOption.REPLACE_EXISTING);
        assertEquals(
                CONVERTER_SHA256,
                UpstreamArtefacts.sha256(jar),
                "the staged converter is not the bytes the manifest pins");
        return jar;
    }

    private static LimelightConverterIdentity realIdentity() throws IOException {
        return new LimelightConverterIdentity(
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120)),
                JavaRuntime.ofThisApplication());
    }

    private static LimelightConverterIdentity scripted(ScriptedRunner runner) throws IOException {
        return new LimelightConverterIdentity(
                new ToolRunner(runner, Duration.ofSeconds(5)), JavaRuntime.ofThisApplication());
    }

    @Test
    @DisplayName("the real converter JAR is started and prints v2.8.1 on standard output")
    void theRealJarPrintsItsVersion(@TempDir Path directory) throws IOException {
        Path jar = stage(directory);

        ToolVersion version = realIdentity().identify(ToolName.LIMELIGHT_CONVERTER, LINUX, jar);

        assertEquals(ToolVersion.parse("2.8.1"), version);
    }

    @Test
    @DisplayName("the command is java -jar <jar> --version, run in the JAR's own directory")
    void theCommand(@TempDir Path directory) throws IOException {
        Path jar = stage(directory);
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(
                                0, List.of(), List.of("cometPercolator2LimelightXML.jar v2.8.1"));

        scripted(runner).identify(ToolName.LIMELIGHT_CONVERTER, LINUX, jar);

        ToolCommand command = runner.commands().get(0);
        assertAll(
                () ->
                        assertEquals(
                                JavaRuntime.ofThisApplication().launcher().toString(),
                                command.argv().get(0)),
                () -> assertEquals("-jar", command.argv().get(1)),
                () -> assertEquals(jar.toString(), command.argv().get(2)),
                () -> assertEquals("--version", command.argv().get(3)),
                () -> assertEquals(4, command.argv().size()),
                () -> assertEquals(directory, command.workingDirectory()),
                () ->
                        assertEquals(
                                List.of("--version"),
                                LimelightConverterIdentity.VERSION_ARGUMENTS));
    }

    @Test
    @DisplayName(
            "the version line is read from standard error too, because every other tool uses it")
    void bothStreamsAreSearched(@TempDir Path directory) throws IOException {
        Path jar = stage(directory);
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(
                                0, List.of("cometPercolator2LimelightXML.jar v2.8.1"), List.of());

        assertEquals(
                ToolVersion.parse("2.8.1"),
                scripted(runner).identify(ToolName.LIMELIGHT_CONVERTER, LINUX, jar));
    }

    @Test
    @DisplayName("a non-zero exit is a refusal that quotes what the program said")
    void aNonZeroExit(@TempDir Path directory) throws IOException {
        Path jar = stage(directory);
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(1, List.of("Error: Unable to access jarfile"), List.of());

        assertEquals(
                "the Limelight converter at "
                        + jar
                        + " answered --version with exit 1 saying: Error: Unable to access jarfile",
                assertThrows(
                                IOException.class,
                                () ->
                                        scripted(runner)
                                                .identify(ToolName.LIMELIGHT_CONVERTER, LINUX, jar))
                        .getMessage());
    }

    @Test
    @DisplayName("a JAR that starts and says something else is not this converter")
    void aJarThatSaysSomethingElse(@TempDir Path directory) throws IOException {
        Path jar = stage(directory);
        ScriptedRunner runner =
                new ScriptedRunner().thenPrints(0, List.of(), List.of("some other tool v9.9.9"));

        assertEquals(
                "the JAR at "
                        + jar
                        + " started and exited 0 but printed no line matching"
                        + " \"cometPercolator2LimelightXML\\.jar v(\\d{1,4}\\.\\d{1,4}"
                        + "(?:\\.\\d{1,4})?)\", so it is not the Limelight converter this product"
                        + " installs. It printed: some other tool v9.9.9",
                assertThrows(
                                IOException.class,
                                () ->
                                        scripted(runner)
                                                .identify(ToolName.LIMELIGHT_CONVERTER, LINUX, jar))
                        .getMessage());
    }

    @Test
    @DisplayName("a run that never answers is a refusal naming the timeout")
    void aRunThatNeverAnswers(@TempDir Path directory) throws IOException {
        Path jar = stage(directory);
        LimelightConverterIdentity identity =
                new LimelightConverterIdentity(
                        new ToolRunner(
                                new ScriptedRunner().thenNeverFinishes(), Duration.ofMillis(50)),
                        JavaRuntime.ofThisApplication());

        assertEquals(
                "the Limelight converter at "
                        + jar
                        + " did not answer --version within PT0.05S, so it has not been identified",
                assertThrows(
                                IOException.class,
                                () -> identity.identify(ToolName.LIMELIGHT_CONVERTER, LINUX, jar))
                        .getMessage());
    }

    @Test
    @DisplayName("another tool is refused, and every argument is required")
    void refusalsAndNulls(@TempDir Path directory) throws IOException {
        Path jar = stage(directory);
        LimelightConverterIdentity identity = scripted(new ScriptedRunner());

        assertAll(
                () ->
                        assertEquals(
                                "this reads the Limelight converter's identity and was asked about"
                                        + " pdv",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> identity.identify(ToolName.PDV, LINUX, jar))
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
                                                () ->
                                                        identity.identify(
                                                                ToolName.LIMELIGHT_CONVERTER,
                                                                null,
                                                                jar))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "jar",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        identity.identify(
                                                                ToolName.LIMELIGHT_CONVERTER,
                                                                LINUX,
                                                                null))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "runner",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LimelightConverterIdentity(
                                                                null,
                                                                JavaRuntime.ofThisApplication()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "java",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LimelightConverterIdentity(
                                                                new ToolRunner(
                                                                        new ScriptedRunner(),
                                                                        Duration.ofSeconds(1)),
                                                                null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("PDV cannot be identified the same way, and this is why: it needs a display")
    void pdvCannotBeIdentifiedByRunningIt(@TempDir Path directory) throws IOException {
        Path pdv =
                UpstreamArtefacts.member(
                        "v2.7.0__PDV-2.7.0.zip",
                        "PDV-2.7.0/PDV-2.7.0.jar",
                        directory.resolve("PDV-2.7.0.jar"));
        ToolRunner runner =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120));

        var outcome =
                runner.run(
                        JavaRuntime.ofThisApplication()
                                .jarCommand(pdv, List.of("--version"), java.util.Map.of()));

        assertAll(
                () -> assertEquals(1, outcome.exitCode().orElseThrow()),
                () ->
                        assertTrue(
                                outcome.errorFirst().stream()
                                        .anyMatch(
                                                line ->
                                                        line.contains("java.awt.HeadlessException")
                                                                || line.contains(
                                                                        "NoClassDefFoundError")),
                                "PDV builds a JFrame before it reads its first argument, so on a"
                                        + " machine with no display no argument can make it print"
                                        + " a version: "
                                        + outcome.joinedOutput()),
                () ->
                        assertTrue(
                                outcome.errorFirst().stream()
                                        .noneMatch(line -> line.contains("2.7.0")),
                                "and nothing it printed carries its version: "
                                        + outcome.joinedOutput()));
    }
}
