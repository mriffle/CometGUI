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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link ArtefactCompanion}.
 *
 * <p>Two rules here are worth more than they look. The first is that a {@code BARE_EXECUTABLE}
 * companion's single member must describe the file it downloads: without it the size and the
 * digests are written twice with nothing comparing them, which is a restatement rather than a
 * check. The second is that a companion's gated capability must belong to the tool the companion
 * hangs off -- the rule that makes {@code R-TOOL-02}'s "a Comet install missing them shall not
 * advertise {@code THERMO_RAW_WINDOWS}" a fact in the manifest rather than a conditional in code.
 */
class ArtefactCompanionTest {

    private static final URI URL =
            URI.create("https://github.com/example/example/releases/download/t/payload");

    private static ArtefactCompanion bareExecutable(
            long sizeBytes, FileHashes hashes, List<ArchiveMember> members) {
        return new ArtefactCompanion(
                "comet-thermo",
                ArtefactKind.BARE_EXECUTABLE,
                URL,
                sizeBytes,
                hashes,
                true,
                Optional.of(ToolCapability.THERMO_RAW_WINDOWS),
                "Comet reads Thermo RAW on Windows only with this library beside the executable",
                members);
    }

    @Test
    @DisplayName("a payload companion keeps its parts and reports where its files go")
    void keepsItsParts() {
        ArtefactCompanion companion =
                ManifestFixtures.payloadCompanion("xsd", "share/percolator_out.xsd");

        assertAll(
                () -> assertEquals("xsd", companion.id()),
                () -> assertEquals(ArtefactKind.DEB_PAYLOAD, companion.kind()),
                () -> assertEquals(URL, companion.url()),
                () -> assertEquals(1852660, companion.sizeBytes()),
                () -> assertEquals(Optional.empty(), companion.gatesCapability()),
                () -> assertEquals(List.of("share/percolator_out.xsd"), companion.installedPaths()),
                () ->
                        assertTrue(
                                !companion.runtimePrerequisite(),
                                "the XSD pair is a provenance and validation asset, and R-TOOL-02"
                                        + " requires that distinction recorded in the registry"));
    }

    @Test
    @DisplayName("a companion that installs nothing is rejected")
    void anEmptyMemberListIsRejected() {
        assertEquals(
                "companion members must name at least one file: a companion that installs nothing"
                        + " is a download with no reason to happen",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ArtefactCompanion(
                                                "empty",
                                                ArtefactKind.DEB_PAYLOAD,
                                                URL,
                                                1,
                                                ManifestFixtures.hashes(),
                                                false,
                                                Optional.empty(),
                                                "a note",
                                                List.of()))
                        .getMessage());
    }

    @Test
    @DisplayName(
            "a companion naming one member twice, or installing two files at one path, is rejected")
    void collidingMembersAreRejected() {
        ArchiveMember first =
                new ArchiveMember("a.xsd", 1, ManifestFixtures.memberHashes(), "share/a.xsd");
        ArchiveMember sameName =
                new ArchiveMember("a.xsd", 1, ManifestFixtures.memberHashes(), "share/b.xsd");
        ArchiveMember sameDestination =
                new ArchiveMember("b.xsd", 1, ManifestFixtures.memberHashes(), "share/a.xsd");

        assertAll(
                () ->
                        assertEquals(
                                "companion members names the member \"a.xsd\" more than once",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> payload(List.of(first, sameName)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "companion members installs two files at \"share/a.xsd\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> payload(List.of(first, sameDestination)))
                                        .getMessage()));
    }

    private static ArtefactCompanion payload(List<ArchiveMember> members) {
        return new ArtefactCompanion(
                "xsd",
                ArtefactKind.DEB_PAYLOAD,
                URL,
                1852660,
                ManifestFixtures.hashes(),
                false,
                Optional.empty(),
                "a note",
                members);
    }

    @Test
    @DisplayName("a BARE_EXECUTABLE companion must describe exactly the file it downloads")
    void aBareExecutableCompanionMustDescribeItsOwnFile() {
        FileHashes own = ManifestFixtures.hashes();
        ArchiveMember agreeing =
                new ArchiveMember("CometWrapper.dll", 4411392, own, "bin/CometWrapper.dll");
        ArchiveMember wrongSize =
                new ArchiveMember("CometWrapper.dll", 4411391, own, "bin/CometWrapper.dll");
        ArchiveMember wrongDigest =
                new ArchiveMember(
                        "CometWrapper.dll",
                        4411392,
                        ManifestFixtures.memberHashes(),
                        "bin/CometWrapper.dll");
        ArchiveMember second = new ArchiveMember("Other.dll", 4411392, own, "bin/Other.dll");

        assertAll(
                () ->
                        assertEquals(
                                4411392,
                                bareExecutable(4411392, own, List.of(agreeing)).sizeBytes(),
                                "a companion whose member agrees with it is accepted"),
                () ->
                        assertEquals(
                                "a BARE_EXECUTABLE companion's one member is the downloaded file"
                                        + " itself, so its sizeBytes and digests must equal the"
                                        + " companion's own",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        bareExecutable(
                                                                4411392, own, List.of(wrongSize)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a BARE_EXECUTABLE companion's one member is the downloaded file"
                                        + " itself, so its sizeBytes and digests must equal the"
                                        + " companion's own",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        bareExecutable(
                                                                4411392, own, List.of(wrongDigest)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a BARE_EXECUTABLE companion is a single downloaded file, so"
                                        + " companion members must name exactly one, but named 2",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        bareExecutable(
                                                                4411392,
                                                                own,
                                                                List.of(agreeing, second)))
                                        .getMessage()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = ArtefactKind.class,
            names = {"BARE_EXECUTABLE"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("every other kind may name several members, because it has a container")
    void otherKindsMayNameSeveralMembers(ArtefactKind kind) {
        ArtefactCompanion companion =
                new ArtefactCompanion(
                        "xsd",
                        kind,
                        URL,
                        1852660,
                        ManifestFixtures.hashes(),
                        false,
                        Optional.empty(),
                        "a note",
                        List.of(
                                new ArchiveMember(
                                        "a.xsd", 1, ManifestFixtures.memberHashes(), "share/a.xsd"),
                                new ArchiveMember(
                                        "b.xsd",
                                        2,
                                        ManifestFixtures.memberHashes(),
                                        "share/b.xsd")));

        assertEquals(List.of("share/a.xsd", "share/b.xsd"), companion.installedPaths());
    }

    @Test
    @DisplayName("a gated capability belonging to another tool is rejected, over every tool")
    void aGatedCapabilityOfAnotherToolIsRejected() {
        ArtefactCompanion thermo =
                bareExecutable(
                        4411392,
                        ManifestFixtures.hashes(),
                        List.of(
                                new ArchiveMember(
                                        "CometWrapper.dll",
                                        4411392,
                                        ManifestFixtures.hashes(),
                                        "bin/CometWrapper.dll")));

        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            if (tool == ToolName.COMET) {
                assertions.add(
                        () ->
                                assertEquals(
                                        thermo,
                                        thermo.requireGatesCapabilityOf(tool),
                                        "THERMO_RAW_WINDOWS is a fact about comet"));
                continue;
            }
            assertions.add(
                    () ->
                            assertEquals(
                                    "THERMO_RAW_WINDOWS is a capability of comet and cannot be"
                                            + " declared for "
                                            + tool.id(),
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> thermo.requireGatesCapabilityOf(tool))
                                            .getMessage()));
        }
        assertions.add(
                () ->
                        assertEquals(
                                ManifestFixtures.payloadCompanion("xsd", "share/a.xsd"),
                                ManifestFixtures.payloadCompanion("xsd", "share/a.xsd")
                                        .requireGatesCapabilityOf(ToolName.PDV),
                                "a companion that gates nothing is accepted by every tool"));
        assertAll(assertions);
    }

    @Test
    @DisplayName("the member list is copied on the way in and on the way out")
    void theMemberListIsCopied() {
        List<ArchiveMember> mutable = new ArrayList<>();
        mutable.add(new ArchiveMember("a.xsd", 1, ManifestFixtures.memberHashes(), "share/a.xsd"));
        ArtefactCompanion companion =
                new ArtefactCompanion(
                        "xsd",
                        ArtefactKind.DEB_PAYLOAD,
                        URL,
                        1,
                        ManifestFixtures.hashes(),
                        false,
                        Optional.empty(),
                        "a note",
                        mutable);
        mutable.clear();

        assertAll(
                () -> assertEquals(1, companion.members().size()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> companion.members().clear()));
    }
}
