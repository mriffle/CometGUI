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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.ArtefactExecutability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ManifestAlternatives}, against the <strong>shipped</strong> manifest rather than
 * a fixture. A fixture contains what the rule needs; the real file contains a tool with no Linux
 * row for one of its three managed versions, which is exactly the case this class is for.
 */
class ManifestAlternativesTest {

    /**
     * The Comet release the sibling cases give a second macOS build in {@link #withTwoMacBuilds()}.
     *
     * <p><strong>No shipped release has two.</strong> Upstream has never published an x86-64 macOS
     * Comet -- {@code comet.macos.exe} is an arm64 Mach-O (CPU type {@code 0x0100000C}) in
     * 2026.02.2 and 2026.03.0 alike -- so {@code D-011} removed the false 2026.02.2 row that made
     * the shipped manifest the fixture for this rule. The rule itself stands: it is keyed on the
     * download, and a manifest with two builds of one release is one upstream asset away.
     */
    private static final ToolVersion TWO_MACOS_BUILDS = ToolVersion.parse("2026.02.2");

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    @Test
    @DisplayName("the alternatives to the 3.09 payload are the two Linux builds that do run here")
    void theRealAlternatives() throws IOException {
        List<String> alternatives = alternatives(DEBIAN_12).forArtefact(ProbeRecords.payload309());

        assertEquals(
                List.of("percolator 3.07.1 linux-x86-64", "percolator 3.06.5 linux-x86-64"),
                alternatives,
                "newest first, which is the manifest's own offer order");
    }

    @Test
    @DisplayName("a build's own row is never offered as an alternative to itself")
    void aBuildIsNotItsOwnAlternative() throws IOException {
        ArtefactRecord percolator3071 = ProbeRecords.shippedPercolator("3.07.1");

        assertEquals(
                List.of("percolator 3.06.5 linux-x86-64"),
                alternatives(DEBIAN_12).forArtefact(percolator3071));
    }

    @Test
    @DisplayName("the SIBLING ROW of a failing build is an alternative, even sharing its version")
    void aSiblingRowOfTheSameVersionIsAnAlternative() throws IOException {
        HostPlatform appleSilicon =
                new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64);
        ArtefactRecord nativeComet = cometFor(appleSilicon, HostArchitecture.AARCH64);

        List<String> alternatives =
                new ManifestAlternatives(withTwoMacBuilds(), appleSilicon, DEBIAN_12)
                        .forArtefact(nativeComet);

        assertAll(
                () ->
                        assertFalse(
                                alternatives.contains(nativeComet.describe()),
                                "the failing row itself is never its own alternative: "
                                        + alternatives),
                () ->
                        assertTrue(
                                alternatives.size() > sameRelease(alternatives, nativeComet).size(),
                                "and the other Comet releases that run here are named too: "
                                        + alternatives));
        assertEquals(
                List.of("comet 2026.02.2 macos-x86-64"),
                sameRelease(alternatives, nativeComet),
                "where a release has TWO macOS builds, the x86-64 one runs on Apple silicon under"
                        + " Rosetta 2, so "
                        + "a native build that will not load has"
                        + " somewhere to send the user -- and R-PLAT-03 requires it to be named."
                        + " Excluding the failing build by VERSION rather than by row takes the"
                        + " sibling with it and tells the scientist there is nothing else");
    }

    @Test
    @DisplayName(
            "where one release has a native and a translated build here, the native one is offered"
                    + " first")
    void theNativeBuildOfAReleaseIsOfferedFirst() throws IOException {
        /*
         * D-004's ordering, graded on the synthetic sibling: no shipped release has two builds for
         * one host since D-011 removed the false 2026.02.2 x86-64 macOS Comet row, so the shipped
         * manifest can no longer show this order, and a rule nobody can watch fail is not graded.
         */
        HostPlatform appleSilicon =
                new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64);
        List<ArtefactSelection> release =
                withTwoMacBuilds().select(appleSilicon, ToolName.COMET, TWO_MACOS_BUILDS);

        assertAll(
                () -> assertEquals(2, release.size(), "both builds are offered, not collapsed"),
                () ->
                        assertEquals(
                                ArtefactExecutability.NATIVE,
                                release.get(0).executability(),
                                "the native build must be first, not merely present"),
                () ->
                        assertEquals(
                                ArtefactExecutability.TRANSLATED_ROSETTA_2,
                                release.get(1).executability()));
    }

    @Test
    @DisplayName("and the translated row's own alternative is the native one, in the other order")
    void theSiblingRelationHoldsBothWays() throws IOException {
        HostPlatform appleSilicon =
                new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64);
        ArtefactRecord translatedComet = cometFor(appleSilicon, HostArchitecture.X86_64);

        assertEquals(
                List.of("comet 2026.02.2 macos-aarch64"),
                sameRelease(
                        new ManifestAlternatives(withTwoMacBuilds(), appleSilicon, DEBIAN_12)
                                .forArtefact(translatedComet),
                        translatedComet),
                "the rule is about which ROW failed, not which of the two is preferred");
    }

    @Test
    @DisplayName("a build this host provably cannot run is not offered as somewhere to go instead")
    void aBuildThatWouldFailTheSameWayIsNotOffered() throws IOException {
        HostRuntimeVersions ancient =
                new HostRuntimeVersions(
                        Optional.of(GlibcVersion.parse("2.20")),
                        Optional.of(GlibcVersion.parse("3.4.21")));

        assertEquals(
                List.of("percolator 3.06.5 linux-x86-64"),
                alternatives(ancient).forArtefact(ProbeRecords.payload309()),
                "3.07.1 needs GLIBC_2.34 and this host has 2.20, so pointing a user at it would"
                        + " be pointing them at the same failure");
    }

    @Test
    @DisplayName("a floor that could not be measured does not remove an alternative")
    void anUnmeasuredFloorLeavesTheAlternativeStanding() throws IOException {
        assertEquals(
                List.of("percolator 3.07.1 linux-x86-64", "percolator 3.06.5 linux-x86-64"),
                alternatives(HostRuntimeVersions.unknown()).forArtefact(ProbeRecords.payload309()),
                "not knowing is not a reason to withhold a build; R-PLAT-02 makes the probe the"
                        + " authority");
    }

    @Test
    @DisplayName(
            "a tool with no build for this host has no alternatives, and says so as an empty list")
    void aToolWithNoBuildHere() throws IOException {
        HostPlatform unsupported =
                new HostPlatform(
                        org.cometgui.domain.tools.HostOperatingSystem.WINDOWS,
                        org.cometgui.domain.tools.HostArchitecture.AARCH64);

        assertEquals(
                List.of(),
                new ManifestAlternatives(ProbeRecords.shipped(), unsupported, DEBIAN_12)
                        .forArtefact(ProbeRecords.payload309()));
    }

    @Test
    @DisplayName("it rejects a null argument by name")
    void nullArgumentsAreRejectedByName() throws IOException {
        ManifestAlternatives source = alternatives(DEBIAN_12);
        assertAll(
                () ->
                        assertEquals(
                                "manifest",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ManifestAlternatives(
                                                                Nulls.of(ArtefactManifest.class),
                                                                ProbeRecords.LINUX_X86_64,
                                                                DEBIAN_12))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ManifestAlternatives(
                                                                ProbeRecords.shipped(),
                                                                Nulls.of(HostPlatform.class),
                                                                DEBIAN_12))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "versions",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ManifestAlternatives(
                                                                ProbeRecords.shipped(),
                                                                ProbeRecords.LINUX_X86_64,
                                                                Nulls.of(
                                                                        HostRuntimeVersions.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "record",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        source.forArtefact(
                                                                Nulls.of(ArtefactRecord.class)))
                                        .getMessage()));
    }

    /**
     * The shipped Comet row built for one architecture, as selected on a given host.
     *
     * @param host the machine
     * @param builtFor the architecture the row is built for
     * @return the record
     * @throws IOException if the manifest cannot be read
     */
    private static ArtefactRecord cometFor(HostPlatform host, HostArchitecture builtFor)
            throws IOException {
        return withTwoMacBuilds().select(host, ToolName.COMET).stream()
                .map(selection -> selection.artefact())
                .filter(record -> record.version().equals(TWO_MACOS_BUILDS))
                .filter(record -> record.platform().architecture() == builtFor)
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the shipped manifest offers no "
                                                + builtFor.id()
                                                + " Comet build on "
                                                + host.id()
                                                + "; this case exists because it offers two"));
    }

    /*
     * The alternatives that are the failing build's own release.  The defect these sibling cases
     * exist for is the exclusion keyed on the VERSION, which removes the same-release sibling and
     * leaves the other releases standing; since the manifest names 2026.03.0 as well, "nothing is
     * left" is no longer what that defect looks like in the whole list, but it still is in this
     * slice of it, which is where the sibling relation lives.
     */
    private static List<String> sameRelease(List<String> alternatives, ArtefactRecord failing) {
        String release = failing.tool().id() + " " + failing.version().text() + " ";
        return alternatives.stream().filter(described -> described.startsWith(release)).toList();
    }

    /**
     * The shipped manifest plus one synthetic x86-64 macOS build of {@link #TWO_MACOS_BUILDS}: the
     * shipped 2026.02.2 {@code macos-aarch64} row with another platform and another download.
     *
     * <p>The URL is invented and says so, and nothing outside this test sees the row. The shipped
     * manifest has no same-release sibling on any host (see {@link #TWO_MACOS_BUILDS}), and a rule
     * graded only where the data happens to exercise it stops being graded when the data changes.
     *
     * @return the manifest
     * @throws IOException if the shipped manifest cannot be read
     */
    private static ArtefactManifest withTwoMacBuilds() throws IOException {
        ArtefactManifest shipped = ProbeRecords.shipped();
        ArtefactRecord arm =
                shipped.artefacts().stream()
                        .filter(record -> record.tool() == ToolName.COMET)
                        .filter(record -> record.version().equals(TWO_MACOS_BUILDS))
                        .filter(
                                record ->
                                        record.platform()
                                                .equals(
                                                        new HostPlatform(
                                                                HostOperatingSystem.MACOS,
                                                                HostArchitecture.AARCH64)))
                        .findFirst()
                        .orElseThrow();
        ArtefactRecord intel =
                new ArtefactRecord(
                        arm.tool(),
                        arm.version(),
                        arm.releaseTag(),
                        new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64),
                        arm.kind(),
                        URI.create(
                                "https://example.invalid/synthetic-test-row/comet.x86_64.macos.exe"),
                        arm.sizeBytes(),
                        arm.hashes(),
                        arm.member(),
                        arm.expectedExecutablePath(),
                        arm.executable(),
                        arm.licence(),
                        arm.companions(),
                        arm.capabilities(),
                        arm.advisories(),
                        arm.minimumHostRequirements(),
                        arm.minimumCometGuiVersion());
        List<ArtefactRecord> records = new ArrayList<>(shipped.artefacts());
        records.add(intel);
        return new ArtefactManifest(shipped.schemaVersion(), records);
    }

    private static ManifestAlternatives alternatives(HostRuntimeVersions versions)
            throws IOException {
        return new ManifestAlternatives(
                ProbeRecords.shipped(), ProbeRecords.LINUX_X86_64, versions);
    }
}
