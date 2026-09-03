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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link StagedToolProbe}, the three stages composed, run against the real binaries.
 *
 * <p>What each case is really checking is the <strong>order</strong>: a failure must be reported as
 * the stage it happened at, and a stage must not be reached when an earlier one refused. The
 * advance case proves the strongest form of that -- the process runner fails the test if it is
 * entered at all, so a build refused on a declared floor is provably never launched.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the composition is exercised against the real Linux binaries in this phase's"
                        + " artefact mirror")
class StagedToolProbeTest {

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final Set<ToolCapability> PROBED_CAPABILITIES =
            Set.of(ToolCapability.XML_OUTPUT, ToolCapability.PSM_TSV_OUTPUT);

    /** Records what the capability stage was handed, so the seam's contract can be asserted. */
    private static final class RecordingProber implements CapabilityProber {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Set<ToolCapability> probe(
                ToolName tool, ToolVersion version, HostPlatform platform, Path executable) {
            calls.add(tool.id() + " " + version.text() + " " + platform.id() + " " + executable);
            return PROBED_CAPABILITIES;
        }
    }

    @Test
    @DisplayName(
            "the real 3.07.1 passes all three stages, and the capability stage gets the"
                    + " PROBED version")
    void theRealBinaryPassesEveryStage(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        Path executable =
                StagedBinaries.stage(
                        StagedBinaries.percolator3071(), staged, record.executablePath());
        RecordingProber prober = new RecordingProber();

        Set<ToolCapability> capabilities = probe(prober).probe(record, staged);

        assertAll(
                () -> assertEquals(PROBED_CAPABILITIES, capabilities),
                () ->
                        assertEquals(
                                List.of("percolator 3.07.1 linux-x86-64 " + executable),
                                prober.calls,
                                "the capability stage is handed the version the binary reported,"
                                        + " not the one the manifest claimed"));
    }

    @Test
    @DisplayName("a build refused on a declared floor is never launched at all")
    void anAdvanceRefusalNeverLaunchesAnything(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.payload309();
        StagedBinaries.stage(StagedBinaries.payload309(), staged, record.executablePath());
        ProcessRunner neverEntered =
                (command, listener) -> {
                    throw new AssertionError(
                            "the advance check named an unmet floor, so nothing should have been"
                                    + " launched, but this was: "
                                    + command);
                };
        RecordingProber prober = new RecordingProber();

        ProbeFailedException refused =
                assertThrows(
                        ProbeFailedException.class,
                        () -> probe(neverEntered, DEBIAN_12, prober).probe(record, staged));

        assertAll(
                () -> assertEquals(ProbeFailureKind.MISSING_SYMBOL_VERSION, refused.kind()),
                () ->
                        assertEquals(
                                org.cometgui.domain.tools.ProbeStage.LOADABILITY, refused.stage()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: libc.so.6 on this host does"
                                        + " not provide a symbol version "
                                        + "this build needs. Required:"
                                        + " GLIBC_2.38. Available on this host: GLIBC_2.36."
                                        + " Alternatives: percolator "
                                        + "3.07.1 linux-x86-64; percolator"
                                        + " 3.06.5 linux-x86-64.",
                                refused.getMessage()),
                () ->
                        assertEquals(
                                List.of(),
                                prober.calls,
                                "and the capability stage is not" + " reached either"));
    }

    @Test
    @DisplayName("with no floor declared the binary is run, and the LOADER refuses it")
    void aLoaderRefusalIsReportedAsOne(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.payload309WithNoDeclaredFloors();
        StagedBinaries.stage(StagedBinaries.payload309(), staged, record.executablePath());
        RecordingProber prober = new RecordingProber();

        ProbeFailedException refused =
                assertThrows(ProbeFailedException.class, () -> probe(prober).probe(record, staged));

        assertAll(
                () -> assertEquals(ProbeFailureKind.MISSING_SHARED_OBJECT, refused.kind()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: the dynamic loader could not"
                                        + " find the shared library libboost_filesystem.so.1.83.0."
                                        + " Required: not named by the "
                                        + "loader. Available on this host:"
                                        + " none found. Alternatives: "
                                        + "percolator 3.07.1 linux-x86-64;"
                                        + " percolator 3.06.5 linux-x86-64.",
                                refused.getMessage()),
                () ->
                        assertEquals(
                                List.of(),
                                prober.calls,
                                "a build that will not start has not been shown to lack a"
                                        + " capability; the capability stage is never reached"));
    }

    @Test
    @DisplayName("a binary that is not the pinned version is refused, naming both versions")
    void aVersionTheManifestDidNotPinIsRefused(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        StagedBinaries.stage(StagedBinaries.percolator3065(), staged, record.executablePath());

        ProbeFailedException refused =
                assertThrows(
                        ProbeFailedException.class,
                        () -> probe(new RecordingProber()).probe(record, staged));

        assertAll(
                () -> assertEquals(ProbeFailureKind.UNPARSEABLE_VERSION, refused.kind()),
                () -> assertEquals(org.cometgui.domain.tools.ProbeStage.IDENTITY, refused.stage()),
                () ->
                        assertEquals(
                                "percolator 3.07.1 linux-x86-64 reports itself as version 3.06.5,"
                                        + " and the manifest pins 3.07.1; "
                                        + "a cache entry recorded under"
                                        + " a version the binary disagrees "
                                        + "with cannot be reproduced",
                                refused.getMessage()));
    }

    @Test
    @DisplayName("a build printing no recognisable banner is UNPARSEABLE_VERSION, not a guess")
    void anUnparseableBannerIsAnIdentityFailure(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        Path executable = staged.resolve(record.executablePath());
        Files.createDirectories(java.util.Objects.requireNonNull(executable.getParent(), "parent"));
        Files.writeString(executable, "#!/bin/sh\necho 'a tool, but not one we know'\n");
        assertTrue(executable.toFile().setExecutable(true, true), "could not set the bit");

        ProbeFailedException refused =
                assertThrows(
                        ProbeFailedException.class,
                        () -> probe(new RecordingProber()).probe(record, staged));

        assertEquals(
                "percolator 3.07.1 linux-x86-64 printed no version this build recognises: 1"
                        + " line(s) of output were searched, standard error first",
                refused.getMessage());
    }

    @Test
    @DisplayName("a diagnostic with no loader text names the executable, from the manifest's path")
    void theSubjectIsTheInstalledFileName(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        Path executable = staged.resolve(record.executablePath());
        Files.createDirectories(java.util.Objects.requireNonNull(executable.getParent(), "parent"));
        Files.writeString(executable, "#!/bin/sh\nexit 0\n");
        assertTrue(executable.toFile().setExecutable(false, false), "could not clear the bit");

        ProbeFailedException refused =
                assertThrows(
                        ProbeFailedException.class,
                        () -> probe(new RecordingProber()).probe(record, staged));

        assertAll(
                () -> assertEquals(ProbeFailureKind.NOT_EXECUTABLE, refused.kind()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: percolator is not executable"
                                        + " on this host. Required: not "
                                        + "named by the loader. Available"
                                        + " on this host: none found. "
                                        + "Alternatives: percolator 3.06.5"
                                        + " linux-x86-64.",
                                refused.getMessage(),
                                "the manifest installs it at \"bin/percolator\" and the sentence"
                                        + " names the file, not the path it is under"));
    }

    @Test
    @DisplayName("a tool this unit has no banner for fails by name rather than skipping the stage")
    void aToolWithNoBannerFailsByName(@TempDir Path staged) throws IOException {
        ArtefactRecord pdv =
                ProbeRecords.shipped()
                        .select(ProbeRecords.LINUX_X86_64, ToolName.PDV)
                        .get(0)
                        .artefact();

        IOException refused =
                assertThrows(
                        IOException.class, () -> probe(new RecordingProber()).probe(pdv, staged));

        assertEquals(
                pdv.describe()
                        + " cannot be probed: no version banner is configured for pdv, and this"
                        + " unit ships only the two it has watched a tool print. A Java artefact's"
                        + " identity needs a JVM launch, which belongs with the tool adapters;"
                        + " supply a banner for it rather than letting the identity stage be"
                        + " skipped.",
                refused.getMessage());
    }

    @Test
    @DisplayName(
            "a staged entry that vanished fails by name, and the two ways it can vanish differ")
    void aStagedEntryWhoseExecutableIsGone(@TempDir Path root) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        StagedToolProbe probe = probe(new RecordingProber());
        Path wholeInstallGone = Files.createDirectories(root.resolve("emptied"));
        Path onlyTheFileGone = root.resolve("half");
        Files.createDirectories(onlyTheFileGone.resolve("bin"));

        assertAll(
                () ->
                        assertTrue(
                                assertThrows(
                                                IOException.class,
                                                () -> probe.probe(record, wholeInstallGone))
                                        .getMessage()
                                        .endsWith(
                                                "has no directory to run in, so it cannot be"
                                                        + " probed"),
                                "the whole install is gone, so not even the directory the binary"
                                        + " would run in is there"),
                () ->
                        assertTrue(
                                assertThrows(
                                                IOException.class,
                                                () -> probe.probe(record, onlyTheFileGone))
                                        .getMessage()
                                        .endsWith("is not a regular file, so it cannot be probed"),
                                "the directory survived and the payload did not, which is a"
                                        + " different sentence and a different thing to go looking"
                                        + " for"),
                () ->
                        assertTrue(
                                assertThrows(
                                                IOException.class,
                                                () -> probe.loadabilityOf(record, wholeInstallGone))
                                        .getMessage()
                                        .endsWith(
                                                "has no directory to run in, so it cannot be"
                                                        + " probed"),
                                "and the offered-set entry point fails the same way, which is what"
                                        + " ProbeGatedOffers turns into one refusal"));
    }

    @Test
    @DisplayName("a capability probe that FAILED never becomes a capability set that is empty")
    void aFailedCapabilityProbeIsNotAnEmptyCapabilitySet(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        StagedBinaries.stage(StagedBinaries.percolator3071(), staged, record.executablePath());
        CapabilityProber unableToRun =
                (tool, version, platform, executable) -> {
                    throw new IOException("the synthetic PIN could not be written");
                };

        IOException propagated =
                assertThrows(IOException.class, () -> probe(unableToRun).probe(record, staged));

        assertEquals(
                "the synthetic PIN could not be written",
                propagated.getMessage(),
                "R-TOOL-08 says a capability is absent without positive evidence, and an EMPTY SET"
                        + " is positive evidence of absence -- "
                        + "\"this Percolator cannot write XML\" --"
                        + " produced by a probe that never got an "
                        + "answer. That is the exact confusion"
                        + " ProbeStage exists to prevent, so the failure propagates and the install"
                        + " stops");
    }

    @Test
    @DisplayName(
            "an empty capability set from a probe that RAN is a legitimate answer, not a"
                    + " failure")
    void anEmptyCapabilitySetFromAProbeThatRanIsAccepted(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        StagedBinaries.stage(StagedBinaries.percolator3071(), staged, record.executablePath());

        assertEquals(
                Set.of(),
                probe((tool, version, platform, executable) -> Set.of()).probe(record, staged),
                "\"it ran and found nothing\" and \"it could not run\" are different answers,"
                        + " and only one of them is a failure");
    }

    @Test
    @DisplayName("loadabilityOf answers the loadability question alone, for the offered-set gate")
    void loadabilityAlone(@TempDir Path staged) throws IOException {
        ArtefactRecord working = ProbeRecords.shippedPercolator("3.07.1");
        StagedBinaries.stage(StagedBinaries.percolator3065(), staged, working.executablePath());

        assertEquals(
                Optional.empty(),
                probe(new RecordingProber()).loadabilityOf(working, staged),
                "the 3.06.5 binary starts, which is all this method asks; it is the WRONG version"
                        + " for this record, and that is the identity stage's business");
    }

    @Test
    @DisplayName("the probe rejects a null argument by name")
    void nullArgumentsAreRejectedByName(@TempDir Path staged) throws IOException {
        StagedToolProbe probe = probe(new RecordingProber());
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        assertAll(
                () ->
                        assertEquals(
                                "record",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                Nulls.of(ArtefactRecord.class),
                                                                staged))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "stagedDirectory",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> probe.probe(record, Nulls.of(Path.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "record",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.loadabilityOf(
                                                                Nulls.of(ArtefactRecord.class),
                                                                staged))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "stagedDirectory",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.loadabilityOf(
                                                                record, Nulls.of(Path.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "loadability",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new StagedToolProbe(
                                                                Nulls.of(LoadabilityProbe.class),
                                                                DEBIAN_12,
                                                                Map.of(),
                                                                new RecordingProber(),
                                                                anyRecord -> List.of(),
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "banners",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new StagedToolProbe(
                                                                loadabilityProbe(
                                                                        new ProcessService(
                                                                                Clock.systemUTC()),
                                                                        DEBIAN_12),
                                                                DEBIAN_12,
                                                                Nulls.of(Map.class),
                                                                new RecordingProber(),
                                                                anyRecord -> List.of(),
                                                                Map.of()))
                                        .getMessage()));
    }

    private StagedToolProbe probe(CapabilityProber capabilities) throws IOException {
        return probe(new ProcessService(Clock.systemUTC()), DEBIAN_12, capabilities);
    }

    private StagedToolProbe probe(
            ProcessRunner processes, HostRuntimeVersions versions, CapabilityProber capabilities)
            throws IOException {
        return new StagedToolProbe(
                loadabilityProbe(processes, versions),
                versions,
                VersionBanner.observedOnThisProject(),
                capabilities,
                new ManifestAlternatives(
                                ProbeRecords.shipped(), ProbeRecords.LINUX_X86_64, versions)
                        ::forArtefact,
                Map.of());
    }

    private static LoadabilityProbe loadabilityProbe(
            ProcessRunner processes, HostRuntimeVersions versions) {
        return new LoadabilityProbe(
                processes,
                new LoaderOutputClassifier(ProbeRecords.LINUX_X86_64, versions),
                ProbeRecords.LINUX_X86_64,
                Duration.ofSeconds(5));
    }
}
