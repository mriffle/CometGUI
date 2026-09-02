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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ManifestAlternatives}, against the <strong>shipped</strong> manifest rather than
 * a fixture. A fixture contains what the rule needs; the real file contains a tool with no Linux
 * row for one of its three managed versions, which is exactly the case this class is for.
 */
class ManifestAlternativesTest {

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

    private static ManifestAlternatives alternatives(HostRuntimeVersions versions)
            throws IOException {
        return new ManifestAlternatives(
                ProbeRecords.shipped(), ProbeRecords.LINUX_X86_64, versions);
    }
}
