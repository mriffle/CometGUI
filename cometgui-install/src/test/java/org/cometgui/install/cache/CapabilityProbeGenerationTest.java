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

package org.cometgui.install.cache;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Phase 09 unit 1's older-marker rule: a completion marker whose capabilities were recorded by an
 * earlier generation of the capability probe is not believed.
 *
 * <p>The case it exists for is concrete. Before phase 09, the Percolator probe established {@code
 * XML_OUTPUT} and {@code XML_DECOY_OUTPUT} and nothing else, and wrote exactly that into the
 * marker. Believing such a marker would make an installed 3.07.1 offer no tab-separated output, no
 * weights and no seed -- capabilities it has and the current probe establishes. The rule, tested
 * both ways here: an older marker is {@link InstallationState#CAPABILITIES_FROM_AN_EARLIER_PROBE},
 * not installed, and the next install re-runs the probe; a current marker is installed and the
 * probe is not run again. Nothing in the rule names a tool or a version.
 */
class CapabilityProbeGenerationTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final FileHashes HASHES =
            new FileHashes(
                    "0b77b68fd859639d7421f1c5e006ade5",
                    "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c");

    /** The line the current writer emits, hand-typed; removing it makes a pre-phase-09 marker. */
    private static final String GENERATION_LINE = "  \"capabilityProbeGeneration\": 3,\n";

    @TempDir private Path temporary;

    private static InstallationMarker marker(int generation) {
        return new InstallationMarker(
                InstallationMarker.SCHEMA_VERSION,
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                LINUX,
                "rel-3-07-01",
                URI.create("https://example.invalid/percolator-noxml-ubuntu-portable.zip"),
                946303,
                HASHES,
                "2026-09-02T11:22:33.444Z",
                "bin/percolator",
                1,
                List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                generation,
                List.of(new RecordedFile("bin/percolator", 2538632, HASHES)));
    }

    /* What a marker written before phase 09 looks like: the same document, without the field. */
    private static String withoutTheGeneration(String document) {
        String older = document.replace(GENERATION_LINE, "");
        if (older.equals(document)) {
            throw new AssertionError(
                    "the marker no longer carries the line this test removes: " + document);
        }
        return older;
    }

    @Test
    @DisplayName("the current generation is 3, and a new marker writes it")
    void theCurrentGenerationIsWritten() {
        String json = marker(InstallationMarker.CAPABILITY_PROBE_GENERATION).toJson();

        assertAll(
                () -> assertEquals(3, InstallationMarker.CAPABILITY_PROBE_GENERATION),
                () -> assertTrue(json.contains(GENERATION_LINE), json),
                () ->
                        assertTrue(
                                json.indexOf("\"capabilities\"") < json.indexOf(GENERATION_LINE)
                                        && json.indexOf(GENERATION_LINE)
                                                < json.indexOf("\"files\""),
                                "beside the capabilities it qualifies: " + json),
                () ->
                        assertEquals(
                                3,
                                InstallationMarker.parse(json).capabilityProbeGeneration(),
                                "and reads back"));
    }

    @Test
    @DisplayName(
            "a marker with NO generation is one written before the field existed: generation 1")
    void aMarkerWithNoGenerationIsGenerationOne() {
        String older = withoutTheGeneration(marker(3).toJson());

        InstallationMarker read = InstallationMarker.parse(older);

        assertAll(
                () -> assertEquals(1, read.capabilityProbeGeneration()),
                () -> assertTrue(read.capabilitiesFromAnEarlierProbe()),
                () ->
                        assertEquals(
                                List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                                read.capabilities(),
                                "what it recorded is still read, so a reader can see what the"
                                        + " older probe said"));
    }

    @Test
    @DisplayName(
            "generations below the current one are earlier; the current and later ones are not")
    void whichGenerationsAreEarlier() {
        assertAll(
                () -> assertTrue(marker(1).capabilitiesFromAnEarlierProbe()),
                () ->
                        assertTrue(
                                marker(2).capabilitiesFromAnEarlierProbe(),
                                "phase 09's probe did not establish NO_ANALYTICS_OPTION (D-013)"),
                () -> assertFalse(marker(3).capabilitiesFromAnEarlierProbe()),
                () ->
                        assertFalse(
                                marker(4).capabilitiesFromAnEarlierProbe(),
                                "a later CometGUI's probe establishes at least as much; what it"
                                        + " recorded is no less believable"));
    }

    @ParameterizedTest(name = "[{index}] generation {0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("a generation that is not positive is refused, naming the field and the value")
    void aGenerationThatIsNotPositiveIsRefused(int generation) {
        String document =
                marker(3)
                        .toJson()
                        .replace(
                                GENERATION_LINE,
                                "  \"capabilityProbeGeneration\": " + generation + ",\n");

        assertAll(
                () ->
                        assertEquals(
                                "capabilityProbeGeneration must be positive, but was: "
                                        + generation,
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> marker(generation))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the completion marker is not a valid one:"
                                        + " capabilityProbeGeneration must be positive, but was: "
                                        + generation,
                                assertThrows(
                                                MarkerFormatException.class,
                                                () -> InstallationMarker.parse(document))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a generation too large for any CometGUI to have written is refused")
    void anOverflowingGenerationIsRefused() {
        String document =
                marker(3)
                        .toJson()
                        .replace(GENERATION_LINE, "  \"capabilityProbeGeneration\": 4294967299,\n");

        assertEquals(
                "the completion marker is not a valid one: the completion marker's"
                        + " \"capabilityProbeGeneration\" is 4294967299, which no CometGUI has"
                        + " written",
                assertThrows(MarkerFormatException.class, () -> InstallationMarker.parse(document))
                        .getMessage(),
                "4294967299 cast to int is 3, the current generation: a marker that said that"
                        + " would otherwise be believed");
    }

    @Test
    @DisplayName("the largest generation an int can carry is read as itself, not refused")
    void theLargestGenerationIsRead() {
        String document =
                marker(3)
                        .toJson()
                        .replace(GENERATION_LINE, "  \"capabilityProbeGeneration\": 2147483647,\n");

        InstallationMarker read = InstallationMarker.parse(document);

        assertAll(
                () -> assertEquals(2147483647, read.capabilityProbeGeneration()),
                () -> assertFalse(read.capabilitiesFromAnEarlierProbe()));
    }

    @Test
    @DisplayName("a generation that is not a number is refused by the reader's own type check")
    void aGenerationOfTheWrongTypeIsRefused() {
        String document =
                marker(3)
                        .toJson()
                        .replace(GENERATION_LINE, "  \"capabilityProbeGeneration\": \"3\",\n");

        assertEquals(
                "the completion marker is not a valid one: the completion marker's"
                        + " \"capabilityProbeGeneration\" is a number, and this one is a"
                        + " JsonString",
                assertThrows(MarkerFormatException.class, () -> InstallationMarker.parse(document))
                        .getMessage());
    }

    @Test
    @DisplayName("the state needs a marker, because it is only reached by reading one")
    void theStateCarriesItsMarker() {
        Path directory = temporary.resolve("entry");
        InstallationState older = InstallationState.CAPABILITIES_FROM_AN_EARLIER_PROBE;

        assertAll(
                () ->
                        assertEquals(
                                "CAPABILITIES_FROM_AN_EARLIER_PROBE is only reached by reading the"
                                        + " marker, so one must be supplied",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new InstallationCheck(
                                                                older,
                                                                directory,
                                                                "older",
                                                                Optional.empty()))
                                        .getMessage()),
                () -> assertFalse(older.installed()));
    }

    // ------------------------------------------------------------- the cache and installer --

    private static void downgrade(InstallHarness harness, ArtefactRecord record)
            throws IOException {
        Path marker = harness.markerOf(record);
        Files.writeString(
                marker,
                withoutTheGeneration(Files.readString(marker, StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    /* What a marker phase 09's probe wrote looks like: generation 2, field present. */
    private static void downgradeToPhase09(InstallHarness harness, ArtefactRecord record)
            throws IOException {
        Path marker = harness.markerOf(record);
        String current = Files.readString(marker, StandardCharsets.UTF_8);
        String phase09 = current.replace(GENERATION_LINE, "  \"capabilityProbeGeneration\": 2,\n");
        if (phase09.equals(current)) {
            throw new AssertionError("the marker carries no generation line to change: " + current);
        }
        Files.writeString(marker, phase09, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("PHASE 09 MARKER (generation 2): re-probed, and gains NO_ANALYTICS_OPTION (D-013)")
    void aPhase09MarkerIsReprobed() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        Path root = temporary.resolve("cache");
        InstallHarness earlier =
                new InstallHarness(
                        root,
                        RecordingProbe.confirming(
                                ToolCapability.PSM_TSV_OUTPUT, ToolCapability.WEIGHTS_OUTPUT),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(earlier.fetcher(), record, fixture);
        earlier.install(record);
        downgradeToPhase09(earlier, record);
        InstallationCheck before = earlier.verify(record);
        InstallHarness current =
                new InstallHarness(
                        root,
                        RecordingProbe.confirming(
                                ToolCapability.PSM_TSV_OUTPUT,
                                ToolCapability.WEIGHTS_OUTPUT,
                                ToolCapability.NO_ANALYTICS_OPTION),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(current.fetcher(), record, fixture);

        Installation reprobed = current.install(record);

        assertAll(
                () ->
                        assertEquals(
                                InstallationState.CAPABILITIES_FROM_AN_EARLIER_PROBE,
                                before.state(),
                                before::detail),
                () ->
                        assertTrue(
                                before.detail()
                                        .contains(
                                                "capability probe generation 2, and this CometGUI"
                                                        + " runs generation 3"),
                                before::detail),
                () -> assertFalse(reprobed.alreadyInstalled(), "the phase 09 entry was not reused"),
                () -> assertEquals(1, current.probe().callCount(), "the probe ran again, once"),
                () ->
                        assertEquals(
                                List.of(
                                        ToolCapability.PSM_TSV_OUTPUT,
                                        ToolCapability.WEIGHTS_OUTPUT,
                                        ToolCapability.NO_ANALYTICS_OPTION),
                                reprobed.capabilities()),
                () -> assertEquals(3, reprobed.marker().capabilityProbeGeneration()),
                () -> assertEquals(InstallationState.INSTALLED, current.verify(record).state()));
    }

    @Test
    @DisplayName("OLD MARKER: the cache reports it as needing a re-probe, and not as installed")
    void anOlderMarkerIsNotInstalled() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        harness.install(record);
        downgrade(harness, record);

        InstallationCheck check = harness.verify(record);

        Path directory = harness.directoryOf(record);
        assertAll(
                () ->
                        assertEquals(
                                InstallationState.CAPABILITIES_FROM_AN_EARLIER_PROBE,
                                check.state()),
                () -> assertFalse(check.installed()),
                () -> assertEquals(1, check.requireMarker().capabilityProbeGeneration()),
                () ->
                        assertEquals(
                                "the marker in "
                                        + directory
                                        + " records capabilities from capability probe generation"
                                        + " 1, and this CometGUI runs generation 3; the entry must"
                                        + " be probed again before its capabilities are believed"
                                        + " (R-TOOL-07)",
                                check.detail()));
    }

    @Test
    @DisplayName("a corrupted entry is reported as corrupted, whatever generation its marker is")
    void aChecksumMismatchIsReportedFirst() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        harness.install(record);
        downgrade(harness, record);
        Path binary = harness.directoryOf(record).resolve(CacheFixtures.INSTALLED_BINARY);
        byte[] original = Files.readAllBytes(binary);
        byte[] swapped = original.clone();
        swapped[swapped.length - 1] ^= 1;
        Files.write(binary, swapped);

        assertEquals(
                InstallationState.CHECKSUM_MISMATCH,
                harness.verify(record).state(),
                "a swapped executable is the R-TOOL-04 failure, and saying \"re-probe it\" would"
                        + " hide that the bytes changed");
    }

    @Test
    @DisplayName("OLD MARKER: the next install runs the probe again and records what it says now")
    void anOlderMarkerIsReprobedByTheNextInstall() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        Path root = temporary.resolve("cache");
        InstallHarness earlier =
                new InstallHarness(
                        root,
                        RecordingProbe.confirming(
                                ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(earlier.fetcher(), record, fixture);
        earlier.install(record);
        downgrade(earlier, record);
        InstallHarness current =
                new InstallHarness(
                        root,
                        RecordingProbe.confirming(
                                ToolCapability.XML_OUTPUT,
                                ToolCapability.XML_DECOY_OUTPUT,
                                ToolCapability.PSM_TSV_OUTPUT,
                                ToolCapability.WEIGHTS_OUTPUT),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(current.fetcher(), record, fixture);

        Installation reprobed = current.install(record);

        InstallationCheck after = current.verify(record);
        assertAll(
                () -> assertFalse(reprobed.alreadyInstalled(), "the older entry was not reused"),
                () -> assertEquals(1, current.probe().callCount(), "the probe ran again, once"),
                () ->
                        assertEquals(
                                List.of(
                                        ToolCapability.XML_OUTPUT,
                                        ToolCapability.XML_DECOY_OUTPUT,
                                        ToolCapability.PSM_TSV_OUTPUT,
                                        ToolCapability.WEIGHTS_OUTPUT),
                                reprobed.capabilities(),
                                "the probe wins (R-TOOL-07): what it says now is what is recorded"),
                () -> assertEquals(3, reprobed.marker().capabilityProbeGeneration()),
                () -> assertEquals(InstallationState.INSTALLED, after.state(), after::detail));
    }

    @Test
    @DisplayName("NEW MARKER: the entry is installed and the probe is not run again")
    void aCurrentMarkerIsBelieved() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        Path root = temporary.resolve("cache");
        InstallHarness first =
                new InstallHarness(
                        root,
                        RecordingProbe.confirming(ToolCapability.XML_OUTPUT),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(first.fetcher(), record, fixture);
        first.install(record);
        InstallHarness second =
                new InstallHarness(
                        root,
                        RecordingProbe.confirming(
                                ToolCapability.XML_OUTPUT, ToolCapability.PSM_TSV_OUTPUT),
                        HostOperatingSystem.LINUX);

        Installation again = second.install(record);

        assertAll(
                () -> assertEquals(InstallationState.INSTALLED, second.verify(record).state()),
                () -> assertTrue(again.alreadyInstalled()),
                () -> assertEquals(0, second.probe().callCount(), "nothing was probed again"),
                () ->
                        assertEquals(
                                List.of(ToolCapability.XML_OUTPUT),
                                again.capabilities(),
                                "the current marker's record stands until the checksum changes"));
    }
}
