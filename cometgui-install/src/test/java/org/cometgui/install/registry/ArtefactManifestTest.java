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

package org.cometgui.install.registry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ArtefactExecutability;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Tests for {@link ArtefactManifest}: what it refuses to hold, and what it offers a host.
 *
 * <p>Selection is the part the Tool Manager renders, so its order is asserted against a hand-typed
 * list rather than against whatever the comparator produces today, and the Rosetta 2 case is
 * asserted in both directions -- an Apple silicon host may be offered an x86-64 macOS build, and an
 * x86-64 machine may never be offered an {@code aarch64} one.
 */
class ArtefactManifestTest {

    private static final HostPlatform LINUX_X86_64 =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);
    private static final HostPlatform MACOS_X86_64 =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64);
    private static final HostPlatform MACOS_AARCH64 =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64);
    private static final HostPlatform WINDOWS_X86_64 =
            new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64);

    private static List<String> describedBy(List<ArtefactSelection> selections) {
        List<String> described = new ArrayList<>(selections.size());
        for (ArtefactSelection selection : selections) {
            described.add(
                    selection.artefact().describe()
                            + (selection.isTranslated() ? " (translated)" : " (native)"));
        }
        return described;
    }

    @Test
    @DisplayName("a schema version this build does not write cannot be held at all")
    void theSchemaVersionIsPinned() {
        List<ArtefactRecord> one =
                List.of(ManifestFixtures.namedMember(ToolName.PERCOLATOR, "3.07.1", LINUX_X86_64));

        assertAll(
                () ->
                        assertEquals(
                                "schemaVersion must be 1, which is the manifest format this build"
                                        + " reads, but was: 2",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new ArtefactManifest(2, one))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "schemaVersion must be 1, which is the manifest format this build"
                                        + " reads, but was: 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new ArtefactManifest(0, one))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a manifest with no artefacts at all is rejected")
    void anEmptyManifestIsRejected() {
        assertEquals(
                "artefacts must name at least one artefact: a manifest with no rows offers nothing"
                        + " and would be indistinguishable from one that failed to load",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ArtefactManifest(1, List.of()))
                        .getMessage());
    }

    @Test
    @DisplayName("the same tool, version and platform twice is rejected, naming both positions")
    void aDuplicateIsRejectedNamingBothPositions() {
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            for (HostPlatform platform : List.of(LINUX_X86_64, MACOS_AARCH64, WINDOWS_X86_64)) {
                List<ArtefactRecord> records =
                        List.of(
                                ManifestFixtures.namedMember(tool, "3.07.1", platform),
                                ManifestFixtures.wholeArtefact(tool, "3.06.5", LINUX_X86_64),
                                ManifestFixtures.wholeArtefact(tool, "3.07.1", platform));
                assertions.add(
                        () ->
                                assertEquals(
                                        "artefacts describes "
                                                + tool.id()
                                                + " 3.07.1 "
                                                + platform.id()
                                                + " twice, at index 0 and index 2",
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () -> new ArtefactManifest(1, records))
                                                .getMessage(),
                                        tool.id() + " on " + platform.id()));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("one download described two different ways is rejected, naming both records")
    void oneUrlMustBeDescribedOneWay() {
        ArtefactRecord linux = ManifestFixtures.wholeArtefact(ToolName.PDV, "2.7.0", LINUX_X86_64);
        ArtefactRecord windowsAgreeing =
                ManifestFixtures.wholeArtefact(ToolName.PDV, "2.7.0", WINDOWS_X86_64);
        ArtefactRecord windowsDisagreeing =
                new ArtefactRecord(
                        ToolName.PDV,
                        ToolVersion.parse("2.7.0"),
                        "rel-t",
                        WINDOWS_X86_64,
                        ArtefactKind.ZIP,
                        ManifestFixtures.URL,
                        946304,
                        ManifestFixtures.hashes(),
                        Optional.empty(),
                        Optional.of("bin/tool"),
                        true,
                        ManifestFixtures.licence(),
                        List.of(),
                        List.of(),
                        List.of(),
                        MinimumHostRequirements.none(),
                        ToolVersion.parse("0.1.0"));

        ArtefactRecord withCompanion =
                withCompanions(
                        LINUX_X86_64, ManifestFixtures.payloadCompanion("xsd", "share/a.xsd"));
        ArtefactRecord sameCompanion =
                withCompanions(
                        WINDOWS_X86_64, ManifestFixtures.payloadCompanion("xsd", "share/a.xsd"));
        ArtefactRecord withDisagreeingCompanion =
                withCompanions(
                        MACOS_X86_64,
                        new ArtefactCompanion(
                                "xsd",
                                ArtefactKind.DEB_PAYLOAD,
                                ManifestFixtures.payloadCompanion("xsd", "share/a.xsd").url(),
                                1852661,
                                ManifestFixtures.hashes(),
                                false,
                                Optional.empty(),
                                "the same .deb, described with a different length",
                                List.of(
                                        new ArchiveMember(
                                                "usr/share/xml/percolator/percolator_out.xsd",
                                                10388,
                                                ManifestFixtures.memberHashes(),
                                                "share/a.xsd"))));

        assertAll(
                () ->
                        assertEquals(
                                2,
                                new ArtefactManifest(1, List.of(withCompanion, sameCompanion))
                                        .artefacts()
                                        .size(),
                                "the Percolator XSD .deb really is fetched by the Linux and the"
                                        + " Windows records alike, so the same companion URL"
                                        + " described the same way must be accepted"),
                () ->
                        assertTrue(
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new ArtefactManifest(
                                                                1,
                                                                List.of(
                                                                        withCompanion,
                                                                        withDisagreeingCompanion)))
                                        .getMessage()
                                        .contains("companion xsd"),
                                "a companion download described two ways is caught too, and the"
                                        + " message names the companion"),
                () ->
                        assertEquals(
                                2,
                                new ArtefactManifest(1, List.of(linux, windowsAgreeing))
                                        .artefacts()
                                        .size(),
                                "PDV really is one download offered on five platforms, so the same"
                                        + " URL described the same way must be accepted"),
                () ->
                        assertTrue(
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new ArtefactManifest(
                                                                1,
                                                                List.of(linux, windowsDisagreeing)))
                                        .getMessage()
                                        .startsWith(
                                                "artefacts describes the same download two"
                                                        + " different ways: ")));
    }

    /**
     * A Percolator record on a platform, carrying the given companions.
     *
     * @param platform the artefact's platform
     * @param companions the companions to hang off it
     * @return the record
     */
    private static ArtefactRecord withCompanions(
            HostPlatform platform, ArtefactCompanion... companions) {
        return new ArtefactRecord(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                "rel-3-07-01",
                platform,
                ArtefactKind.ZIP,
                ManifestFixtures.URL,
                946303,
                ManifestFixtures.hashes(),
                Optional.of(ManifestFixtures.member()),
                Optional.empty(),
                true,
                ManifestFixtures.licence(),
                List.of(companions),
                List.of(),
                List.of(),
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    @Test
    @DisplayName("selection returns the newest version first, native before translated")
    void selectionIsOrdered() {
        ArtefactManifest manifest =
                new ArtefactManifest(
                        1,
                        List.of(
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.06.5", MACOS_X86_64),
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.09", MACOS_AARCH64),
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.07.1", MACOS_X86_64),
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.09", MACOS_X86_64)));

        assertEquals(
                List.of(
                        "percolator 3.09 macos-aarch64 (native)",
                        "percolator 3.09 macos-x86-64 (translated)",
                        "percolator 3.07.1 macos-x86-64 (translated)",
                        "percolator 3.06.5 macos-x86-64 (translated)"),
                describedBy(manifest.select(MACOS_AARCH64, ToolName.PERCOLATOR)));
    }

    @Test
    @DisplayName("Rosetta 2 translates in one direction only, and nothing else translates at all")
    void translationGoesOneWay() {
        ArtefactManifest manifest =
                new ArtefactManifest(
                        1,
                        List.of(
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.07.1", MACOS_X86_64),
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.09", MACOS_AARCH64),
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.07.1", WINDOWS_X86_64),
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.06.5", LINUX_X86_64)));

        assertAll(
                () ->
                        assertEquals(
                                List.of("percolator 3.06.5 linux-x86-64 (native)"),
                                describedBy(manifest.select(LINUX_X86_64, ToolName.PERCOLATOR)),
                                "a Linux host is never offered a macOS or Windows build"),
                () ->
                        assertEquals(
                                List.of("percolator 3.07.1 macos-x86-64 (native)"),
                                describedBy(manifest.select(MACOS_X86_64, ToolName.PERCOLATOR)),
                                "an x86-64 Mac is never offered the aarch64 build: Rosetta 2"
                                        + " translates x86-64 code on Apple silicon, not the"
                                        + " other way round"),
                () ->
                        assertEquals(
                                List.of("percolator 3.07.1 windows-x86-64 (native)"),
                                describedBy(manifest.select(WINDOWS_X86_64, ToolName.PERCOLATOR))));
    }

    @Test
    @DisplayName("selection by version compares numerically, so 3.09 and 3.09.0 are one version")
    void selectionByVersionComparesNumerically() {
        ArtefactManifest manifest =
                new ArtefactManifest(
                        1,
                        List.of(
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.07.1", LINUX_X86_64),
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.09", LINUX_X86_64)));

        assertAll(
                () ->
                        assertEquals(
                                List.of("percolator 3.09 linux-x86-64 (native)"),
                                describedBy(
                                        manifest.select(
                                                LINUX_X86_64,
                                                ToolName.PERCOLATOR,
                                                ToolVersion.parse("3.09.0")))),
                () ->
                        assertEquals(
                                List.of(),
                                describedBy(
                                        manifest.select(
                                                LINUX_X86_64,
                                                ToolName.PERCOLATOR,
                                                ToolVersion.parse("3.08")))));
    }

    @Test
    @DisplayName("selection for a tool with no runnable artefact is empty rather than an error")
    void selectionForAnAbsentToolIsEmpty() {
        ArtefactManifest manifest =
                new ArtefactManifest(
                        1,
                        List.of(
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.07.1", LINUX_X86_64)));

        assertAll(
                () -> assertTrue(manifest.select(LINUX_X86_64, ToolName.PDV).isEmpty()),
                () -> assertTrue(manifest.select(WINDOWS_X86_64, ToolName.PERCOLATOR).isEmpty()));
    }

    @Test
    @DisplayName("a selection carries how the host would run the artefact, not only that it can")
    void aSelectionCarriesItsExecutability() {
        ArtefactManifest manifest =
                new ArtefactManifest(
                        1,
                        List.of(
                                ManifestFixtures.namedMember(
                                        ToolName.PERCOLATOR, "3.07.1", MACOS_X86_64)));

        assertEquals(
                ArtefactExecutability.TRANSLATED_ROSETTA_2,
                manifest.select(MACOS_AARCH64, ToolName.PERCOLATOR).get(0).executability());
    }

    @Test
    @DisplayName("the artefact list is copied on the way in and on the way out")
    void theArtefactListIsCopied() {
        List<ArtefactRecord> mutable = new ArrayList<>();
        mutable.add(ManifestFixtures.namedMember(ToolName.PERCOLATOR, "3.07.1", LINUX_X86_64));
        ArtefactManifest manifest = new ArtefactManifest(1, mutable);
        mutable.clear();

        assertAll(
                () -> assertEquals(1, manifest.artefacts().size()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> manifest.artefacts().clear()));
    }
}
