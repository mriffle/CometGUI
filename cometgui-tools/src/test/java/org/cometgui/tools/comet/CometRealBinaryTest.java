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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comet's capability set, established by running the real {@code comet.linux.exe}.
 *
 * <p>The binary is the one the artefact manifest pins, taken from the gitignored mirror and checked
 * against the manifest's own SHA-256 before it is run. The two parameter counts asserted below --
 * 96 from {@code -p} and 118 from {@code -q} -- are the numbers phase 00 recorded as the difference
 * {@code R-PARAM-01} is about, reproduced here on 2026-09-03.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "comet.linux.exe is a Linux ELF binary; the Windows and macOS Comet builds have"
                        + " never been executed anywhere in this project")
class CometRealBinaryTest {

    /**
     * One linux-x86-64 Comet release the manifest ships, typed out: this module cannot read the
     * manifest, so the digest and the two counts are a second statement of what the manifest and
     * the parameter fixtures say, measured from the bytes.
     *
     * @param file the mirror's name for the binary
     * @param sha256 the manifest's SHA-256 for it
     * @param version the release
     * @param defaultCount how many parameters {@code -p} declares
     * @param completeCount how many parameters {@code -q} declares
     */
    record Release(
            String file, String sha256, String version, int defaultCount, int completeCount) {

        @Override
        public String toString() {
            return "Comet " + version;
        }
    }

    /**
     * Both releases the manifest installs on linux-x86-64. 2026.03.0's {@code -p} no longer writes
     * {@code index_search_type}, which is why its count is one lower; {@code -q} is 118 in both.
     *
     * @return the releases
     */
    static Stream<Release> releases() {
        return Stream.of(
                new Release(
                        "v2026.02.2__comet.linux.exe",
                        "af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e",
                        "2026.02.2",
                        96,
                        118),
                new Release(
                        "v2026.03.0__comet.linux.exe",
                        "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed",
                        "2026.03.0",
                        95,
                        118));
    }

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);
    private static final HostPlatform WINDOWS =
            new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64);

    private static ToolRunner runner() {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(60));
    }

    private static Path directoryOf(Path file) {
        return Objects.requireNonNull(
                file.getParent(), "the staged binary is written into a directory");
    }

    private static Path stage(Release release, Path directory) throws IOException {
        Path binary =
                UpstreamArtefacts.executableCopy(
                        release.file(), directory.resolve("bin").resolve("comet"));
        assertEquals(
                release.sha256(),
                UpstreamArtefacts.sha256(binary),
                "the staged Comet is not the bytes the manifest pins");
        return binary;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName("the real binary is observed to write pepXML, write PIN and answer -q")
    void theRealCapabilitySet(Release release, @TempDir Path directory) throws IOException {
        Path binary = stage(release, directory);

        Set<ToolCapability> observed =
                new CometCapabilityProbe(runner(), List.of())
                        .probe(ToolName.COMET, ToolVersion.parse(release.version()), LINUX, binary);

        assertEquals(
                Set.of(
                        ToolCapability.PEPXML_OUTPUT,
                        ToolCapability.PIN_OUTPUT,
                        ToolCapability.COMPLETE_PARAMS_QUERY),
                observed,
                "exactly the three capabilities the manifest claims for this row, and no more:"
                        + " R-TOOL-08 makes an unprobed capability an absent one");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName("a real Linux Comet never advertises THERMO_RAW_WINDOWS, gate or no gate")
    void theRealBinaryHasNoThermoCapability(Release release, @TempDir Path directory)
            throws IOException {
        Path binary = stage(release, directory);
        for (String companion : CometCompanionGates.thermoRawWindows().fileNames()) {
            Files.writeString(directoryOf(binary).resolve(companion), "MZ");
        }

        Set<ToolCapability> observed =
                new CometCapabilityProbe(runner(), List.of(CometCompanionGates.thermoRawWindows()))
                        .probe(ToolName.COMET, ToolVersion.parse(release.version()), LINUX, binary);

        assertFalse(
                observed.contains(ToolCapability.THERMO_RAW_WINDOWS),
                "the three libraries are right there beside it and this is still a Linux build:"
                        + " "
                        + observed);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName(
            "the same real binary, told it is on Windows with the libraries, does advertise it")
    void theCompanionRuleGrantsIt(Release release, @TempDir Path directory) throws IOException {
        Path binary = stage(release, directory);
        for (String companion : CometCompanionGates.thermoRawWindows().fileNames()) {
            Files.writeString(directoryOf(binary).resolve(companion), "MZ");
        }

        Set<ToolCapability> observed =
                new CometCapabilityProbe(runner(), List.of(CometCompanionGates.thermoRawWindows()))
                        .probe(
                                ToolName.COMET,
                                ToolVersion.parse(release.version()),
                                WINDOWS,
                                binary);

        assertAll(
                () -> assertTrue(observed.contains(ToolCapability.THERMO_RAW_WINDOWS)),
                () -> assertTrue(observed.contains(ToolCapability.PEPXML_OUTPUT)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName(
            "and with the libraries taken away it stops, which is the half that makes it a rule")
    void theCompanionRuleWithholdsIt(Release release, @TempDir Path directory) throws IOException {
        Path binary = stage(release, directory);

        Set<ToolCapability> observed =
                new CometCapabilityProbe(runner(), List.of(CometCompanionGates.thermoRawWindows()))
                        .probe(
                                ToolName.COMET,
                                ToolVersion.parse(release.version()),
                                WINDOWS,
                                binary);

        assertFalse(observed.contains(ToolCapability.THERMO_RAW_WINDOWS), observed.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName("-p declares fewer parameters than -q's 118, which is why -q is a capability")
    void theTwoParameterCounts(Release release, @TempDir Path directory) throws IOException {
        Path binary = stage(release, directory);
        Path defaults = Files.createDirectories(directory.resolve("defaults"));
        Path complete = Files.createDirectories(directory.resolve("complete"));

        ToolRunOutcome defaultRun =
                runner().run(new ToolCommand(List.of(binary.toString(), "-p"), defaults, Map.of()));
        ToolRunOutcome completeRun =
                runner().run(new ToolCommand(List.of(binary.toString(), "-q"), complete, Map.of()));
        Set<String> declaredByDefault =
                CometParameterDeclarations.readFrom(
                        defaults.resolve(CometCapabilityProbe.WRITTEN_PARAMETERS_FILE));
        Set<String> declaredByComplete =
                CometParameterDeclarations.readFrom(
                        complete.resolve(CometCapabilityProbe.WRITTEN_PARAMETERS_FILE));

        assertAll(
                () -> assertTrue(defaultRun.exitedZero()),
                () -> assertTrue(completeRun.exitedZero()),
                () -> assertEquals(release.defaultCount(), declaredByDefault.size()),
                () -> assertEquals(release.completeCount(), declaredByComplete.size()),
                () ->
                        assertTrue(
                                declaredByComplete.containsAll(declaredByDefault),
                                "the complete set has to be a superset, or the difference is not"
                                        + " what the capability says it is"),
                () ->
                        assertTrue(
                                declaredByComplete.contains(CometCapabilityProbe.PEPXML_PARAMETER)),
                () -> assertTrue(declaredByComplete.contains(CometCapabilityProbe.PIN_PARAMETER)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName("the banner arrives on standard output for -q, so both streams have to be read")
    void theBannerStream(Release release, @TempDir Path directory) throws IOException {
        Path binary = stage(release, directory);
        Path workspace = Files.createDirectories(directory.resolve("run"));

        ToolRunOutcome outcome =
                runner().run(
                                new ToolCommand(
                                        List.of(binary.toString(), "-q"), workspace, Map.of()));

        assertAll(
                () -> assertTrue(CometBanner.isPresentIn(outcome.standardOutput())),
                () ->
                        assertFalse(
                                CometBanner.isPresentIn(outcome.standardError()),
                                "for -q it is standard OUTPUT that carries it, which is the"
                                        + " opposite of Percolator and of comet -h"));
    }
}
