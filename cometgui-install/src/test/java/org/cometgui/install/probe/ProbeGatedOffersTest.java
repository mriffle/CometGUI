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

package org.cometgui.install.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.ArtefactExecutability;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;
import org.cometgui.install.testing.Nulls;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ProbeGatedOffers}: {@code R-TOOL-06}'s last sentence, asserted on the
 * <strong>offered set</strong> rather than on an exception.
 *
 * <p>The boundary cases are the point. A host with precisely {@code GLIBC_2.34} is offered
 * Percolator 3.07.1; a host at 2.33 is not, and the refusal names both. The same at the {@code
 * GLIBCXX} floor. And a host whose C++ runtime could not be measured at all is offered the build
 * anyway, with the probe left to decide -- which is the test that fails if the advance check ever
 * starts guessing.
 */
class ProbeGatedOffersTest {

    private static final HostRuntimeVersions DEBIAN_12 = host("2.36", "3.4.30");

    private static final ProbeGatedOffers.LoadabilityCheck EVERYTHING_LOADS =
            record -> Optional.empty();

    @Test
    @DisplayName("a host with exactly GLIBC_2.34 and GLIBCXX_3.4.29 is offered 3.07.1")
    void exactlyAtBothFloorsIsOffered() throws IOException {
        ProbeGatedOffers.Decision decision =
                gate(host("2.34", "3.4.29")).decide(linuxPercolators(), EVERYTHING_LOADS);

        assertAll(
                () -> assertEquals(List.of(), decision.refused()),
                () ->
                        assertEquals(
                                List.of(
                                        "percolator 3.07.1 linux-x86-64",
                                        "percolator 3.06.5 linux-x86-64"),
                                describe(decision.offered()),
                                "a symbol-version floor says \"this version or newer\", and"
                                        + " refusing at equality would withhold the default"
                                        + " XML-capable Percolator from "
                                        + "every machine upstream built"
                                        + " it for"));
    }

    @Test
    @DisplayName(
            "one below the C library floor is not offered, and the refusal names 2.34 and 2.33")
    void oneBelowTheGlibcFloorIsRefused() throws IOException {
        ProbeGatedOffers.Decision decision =
                gate(host("2.33", "3.4.29")).decide(linuxPercolators(), EVERYTHING_LOADS);

        assertAll(
                () ->
                        assertEquals(
                                List.of("percolator 3.06.5 linux-x86-64"),
                                describe(decision.offered()),
                                "3.06.5 needs only GLIBC_2.14 and is still offered"),
                () -> assertEquals(1, decision.refused().size()),
                () ->
                        assertEquals(
                                "percolator 3.07.1 linux-x86-64",
                                decision.refused().get(0).artefact().describe()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: libc.so.6 on this host does"
                                        + " not provide a symbol version "
                                        + "this build needs. Required:"
                                        + " GLIBC_2.34. Available on this host: GLIBC_2.33."
                                        + " Alternatives: percolator 3.06.5 linux-x86-64.",
                                decision.refused().get(0).diagnostic().message()));
    }

    @Test
    @DisplayName("one above the C library floor is offered")
    void oneAboveTheGlibcFloorIsOffered() throws IOException {
        assertEquals(
                List.of(),
                gate(host("2.35", "3.4.29"))
                        .decide(linuxPercolators(), EVERYTHING_LOADS)
                        .refused());
    }

    @Test
    @DisplayName("the C++ runtime floor has the same three cases, and names GLIBCXX in its refusal")
    void theGlibcxxFloorHasTheSameBoundary() throws IOException {
        ProbeGatedOffers.Decision atTheFloor =
                gate(host("2.36", "3.4.29")).decide(linuxPercolators(), EVERYTHING_LOADS);
        ProbeGatedOffers.Decision oneBelow =
                gate(host("2.36", "3.4.28")).decide(linuxPercolators(), EVERYTHING_LOADS);
        ProbeGatedOffers.Decision oneAbove =
                gate(host("2.36", "3.4.30")).decide(linuxPercolators(), EVERYTHING_LOADS);

        assertAll(
                () -> assertEquals(List.of(), atTheFloor.refused()),
                () -> assertEquals(List.of(), oneAbove.refused()),
                () ->
                        assertEquals(
                                List.of("percolator 3.06.5 linux-x86-64"),
                                describe(oneBelow.offered()),
                                "3.06.5 needs only GLIBCXX_3.4.21"),
                () ->
                        assertEquals(
                                "This build cannot run on this host: libstdc++.so.6 on this host"
                                        + " does not provide a symbol version this build needs."
                                        + " Required: GLIBCXX_3.4.29. Available on this host:"
                                        + " GLIBCXX_3.4.28. Alternatives: percolator 3.06.5"
                                        + " linux-x86-64.",
                                oneBelow.refused().get(0).diagnostic().message()));
    }

    @Test
    @DisplayName("a C++ floor that could not be measured is OFFERED, and the probe decides")
    void anUnmeasuredFloorDoesNotWithholdTheOffer() throws IOException {
        HostRuntimeVersions noCxxRuntime =
                new HostRuntimeVersions(Optional.of(GlibcVersion.parse("2.36")), Optional.empty());

        ProbeGatedOffers.Decision loads =
                gate(noCxxRuntime).decide(linuxPercolators(), EVERYTHING_LOADS);
        ProbeGatedOffers.Decision doesNotLoad =
                gate(noCxxRuntime)
                        .decide(
                                linuxPercolators(),
                                record ->
                                        Optional.of(
                                                new LoaderDiagnostic(
                                                        ProbeFailureKind.MISSING_SYMBOL_VERSION,
                                                        "libstdc++.so.6",
                                                        Optional.of("GLIBCXX_3.4.29"),
                                                        Optional.empty(),
                                                        List.of())));

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "percolator 3.07.1 linux-x86-64",
                                        "percolator 3.06.5 linux-x86-64"),
                                describe(loads.offered()),
                                "both declare a GLIBCXX floor this host could not be measured"
                                        + " against; absence of information is never a refusal, and"
                                        + " a check that started guessing would fail here"),
                () ->
                        assertEquals(
                                List.of(),
                                describe(doesNotLoad.offered()),
                                "and it is never an approval either: the same unmeasured host"
                                        + " offers nothing once the binary has been run and"
                                        + " refused"));
    }

    @Test
    @DisplayName("a host about which nothing at all is known still offers everything to the probe")
    void anEntirelyUnknownHostOffersEverything() throws IOException {
        assertEquals(
                List.of("percolator 3.07.1 linux-x86-64", "percolator 3.06.5 linux-x86-64"),
                describe(
                        gate(HostRuntimeVersions.unknown())
                                .decide(linuxPercolators(), EVERYTHING_LOADS)
                                .offered()));
    }

    @Test
    @EnabledOnOs(
            value = OS.LINUX,
            disabledReason = "this case runs the real Linux binaries from the artefact mirror")
    @DisplayName("a real binary that fails loadability is absent from the offered set")
    void aBinaryThatFailsLoadabilityIsNotOffered(@TempDir Path root) throws IOException {
        ArtefactRecord working = ProbeRecords.shippedPercolator("3.07.1");
        ArtefactRecord broken = ProbeRecords.payload309WithNoDeclaredFloors();
        Map<ArtefactRecord, Path> staged = new HashMap<>();
        staged.put(
                working,
                stage(
                        root.resolve("working"),
                        StagedBinaries.percolator3071(),
                        working.executablePath()));
        staged.put(
                broken,
                stage(
                        root.resolve("broken"),
                        StagedBinaries.payload309(),
                        broken.executablePath()));
        StagedToolProbe probe = realProbe();

        ProbeGatedOffers.Decision decision =
                gate(DEBIAN_12)
                        .decide(
                                List.of(offer(working), offer(broken)),
                                record -> probe.loadabilityOf(record, staged.get(record)));

        assertAll(
                () ->
                        assertEquals(
                                List.of("percolator 3.07.1 linux-x86-64"),
                                describe(decision.offered()),
                                "the 3.09 payload declared no floor here, so nothing refused it in"
                                        + " advance: it was RUN, and it did not start"),
                () -> assertEquals(1, decision.refused().size()),
                () ->
                        assertEquals(
                                ProbeFailureKind.MISSING_SHARED_OBJECT,
                                decision.refused().get(0).diagnostic().kind()),
                () ->
                        assertTrue(
                                decision.refused()
                                        .get(0)
                                        .diagnostic()
                                        .message()
                                        .contains("libboost_filesystem.so.1.83.0"),
                                decision.refused().get(0).diagnostic().message()));
    }

    @Test
    @DisplayName("a decision copies both of its lists")
    void theDecisionCopiesItsLists() {
        List<ArtefactRecord> offered = new ArrayList<>();
        List<ProbeGatedOffers.Refusal> refused = new ArrayList<>();
        ProbeGatedOffers.Decision decision = new ProbeGatedOffers.Decision(offered, refused);

        offered.add(ProbeRecords.payload309());

        assertAll(
                () -> assertEquals(List.of(), decision.offered()),
                () -> assertEquals(List.of(), decision.refused()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> decision.offered().add(ProbeRecords.payload309())),
                () ->
                        assertEquals(
                                "offered",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeGatedOffers.Decision(
                                                                Nulls.of(List.class), List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "refused",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeGatedOffers.Decision(
                                                                List.of(), Nulls.of(List.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "artefact",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeGatedOffers.Refusal(
                                                                Nulls.of(ArtefactRecord.class),
                                                                new LoaderDiagnostic(
                                                                        ProbeFailureKind.TIMED_OUT,
                                                                        "x",
                                                                        Optional.empty(),
                                                                        Optional.empty(),
                                                                        List.of())))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "diagnostic",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeGatedOffers.Refusal(
                                                                ProbeRecords.payload309(),
                                                                Nulls.of(LoaderDiagnostic.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the gate rejects a null argument by name")
    void nullArgumentsAreRejectedByName() throws IOException {
        ProbeGatedOffers gate = gate(DEBIAN_12);
        assertAll(
                () ->
                        assertEquals(
                                "versions",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeGatedOffers(
                                                                Nulls.of(HostRuntimeVersions.class),
                                                                record -> List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "alternatives",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeGatedOffers(
                                                                DEBIAN_12,
                                                                Nulls.of(
                                                                        java.util.function.Function
                                                                                .class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "candidates",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        gate.decide(
                                                                Nulls.of(List.class),
                                                                EVERYTHING_LOADS))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "check",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        gate.decide(
                                                                List.of(),
                                                                Nulls.of(
                                                                        ProbeGatedOffers
                                                                                .LoadabilityCheck
                                                                                .class)))
                                        .getMessage()));
    }

    private static ProbeGatedOffers gate(HostRuntimeVersions versions) throws IOException {
        return new ProbeGatedOffers(
                versions,
                new ManifestAlternatives(
                                ProbeRecords.shipped(), ProbeRecords.LINUX_X86_64, versions)
                        ::forArtefact);
    }

    private static List<ArtefactSelection> linuxPercolators() throws IOException {
        return ProbeRecords.shipped().select(ProbeRecords.LINUX_X86_64, ToolName.PERCOLATOR);
    }

    private static ArtefactSelection offer(ArtefactRecord record) {
        return new ArtefactSelection(record, ArtefactExecutability.NATIVE);
    }

    private static List<String> describe(List<ArtefactRecord> records) {
        return records.stream().map(ArtefactRecord::describe).toList();
    }

    private static HostRuntimeVersions host(String glibc, String glibcxx) {
        return new HostRuntimeVersions(
                Optional.of(GlibcVersion.parse(glibc)), Optional.of(GlibcVersion.parse(glibcxx)));
    }

    private static Path stage(Path directory, Path binary, String installedPath)
            throws IOException {
        java.nio.file.Files.createDirectories(directory);
        StagedBinaries.stage(binary, directory, installedPath);
        return directory;
    }

    private static StagedToolProbe realProbe() {
        return new StagedToolProbe(
                new LoadabilityProbe(
                        new ProcessService(Clock.systemUTC()),
                        new LoaderOutputClassifier(ProbeRecords.LINUX_X86_64, DEBIAN_12),
                        ProbeRecords.LINUX_X86_64,
                        Duration.ofSeconds(5)),
                DEBIAN_12,
                VersionBanner.observedOnThisProject(),
                (tool, version, platform, executable) -> java.util.Set.<ToolCapability>of(),
                record -> List.of(),
                Map.of());
    }
}
