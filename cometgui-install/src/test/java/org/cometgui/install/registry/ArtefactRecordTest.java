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
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Tests for {@link ArtefactRecord}.
 *
 * <p>The extraction-mode rule is graded over every tool, platform and kind, because it does not
 * depend on any of them: a record that declared both modes would have two answers to one question
 * whether it were Comet on Windows or PDV on Apple silicon, and a rule quietly restricted to one of
 * them would pass a test that only ever built the other.
 */
class ArtefactRecordTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final List<HostPlatform> PLATFORMS =
            List.of(
                    new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64),
                    new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.AARCH64),
                    new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64),
                    new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64),
                    new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64));

    private static ArtefactRecord record(
            ToolName tool,
            HostPlatform platform,
            ArtefactKind kind,
            Optional<ArchiveMember> member,
            Optional<String> expectedExecutablePath,
            List<ArtefactCompanion> companions,
            List<DeclaredCapability> capabilities,
            List<ToolAdvisory> advisories) {
        return new ArtefactRecord(
                tool,
                ToolVersion.parse("3.07.1"),
                "rel-3-07-01",
                platform,
                kind,
                ManifestFixtures.URL,
                946303,
                ManifestFixtures.hashes(),
                member,
                expectedExecutablePath,
                true,
                ManifestFixtures.licence(),
                companions,
                capabilities,
                advisories,
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    @Test
    @DisplayName("a named-member record answers the member's install path as its executable path")
    void namedMemberModeAnswersTheMembersPath() {
        ArtefactRecord record = ManifestFixtures.namedMember(ToolName.PERCOLATOR, "3.07.1", LINUX);

        assertAll(
                () -> assertTrue(record.isSingleMemberExtraction()),
                () -> assertEquals("bin/percolator", record.executablePath()),
                () -> assertEquals("percolator 3.07.1 linux-x86-64", record.describe()),
                () -> assertEquals(List.of("bin/percolator"), record.installedPaths()));
    }

    @Test
    @DisplayName("a whole-artefact record answers its expected executable path")
    void wholeArtefactModeAnswersItsDeclaredPath() {
        ArtefactRecord record = ManifestFixtures.wholeArtefact(ToolName.PDV, "2.7.0", LINUX);

        assertAll(
                () -> assertTrue(!record.isSingleMemberExtraction()),
                () -> assertEquals("bin/tool", record.executablePath()),
                () -> assertEquals("pdv 2.7.0 linux-x86-64", record.describe()));
    }

    /** How a rejection of a record declaring both extraction modes begins. */
    private static final String BOTH_MODES =
            "a record declares one extraction mode, and this one declares both";

    /** How a rejection of a record declaring neither extraction mode begins. */
    private static final String NEITHER_MODE =
            "a record declares one extraction mode, and this one declares neither";

    /**
     * The message a record with the given extraction-mode fields is rejected with.
     *
     * @param tool the tool
     * @param platform the artefact's platform
     * @param kind the artefact kind
     * @param member the named member, or empty
     * @param path the whole-artefact executable path, or empty
     * @return the rejection message
     */
    private static String modeRejection(
            ToolName tool,
            HostPlatform platform,
            ArtefactKind kind,
            Optional<ArchiveMember> member,
            Optional<String> path) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                record(
                                        tool, platform, kind, member, path, List.of(), List.of(),
                                        List.of()))
                .getMessage();
    }

    @Test
    @DisplayName("declaring both extraction modes is rejected, for every tool, platform and kind")
    void bothExtractionModesAreRejected() {
        assertAll(gradedOverEveryShape(true, BOTH_MODES));
    }

    @Test
    @DisplayName("declaring neither extraction mode is rejected, for every tool, platform and kind")
    void neitherExtractionModeIsRejected() {
        assertAll(gradedOverEveryShape(false, NEITHER_MODE));
    }

    /**
     * The same extraction-mode rejection, asserted for every tool, platform and artefact kind.
     *
     * @param both whether to declare both modes rather than neither
     * @param beginning how the rejection message must begin
     * @return one assertion per shape
     */
    private static List<Executable> gradedOverEveryShape(boolean both, String beginning) {
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            for (HostPlatform platform : PLATFORMS) {
                for (ArtefactKind kind : ArtefactKind.values()) {
                    String where = tool.id() + " " + platform.id() + " " + kind.id();
                    Optional<ArchiveMember> member =
                            both ? Optional.of(ManifestFixtures.member()) : Optional.empty();
                    Optional<String> path = both ? Optional.of("bin/x") : Optional.empty();
                    assertions.add(
                            () -> {
                                String message = modeRejection(tool, platform, kind, member, path);
                                assertTrue(message.startsWith(beginning), where + ": " + message);
                            });
                }
            }
        }
        return assertions;
    }

    @Test
    @DisplayName("a capability of another tool is rejected, over every tool and every capability")
    void aCapabilityOfAnotherToolIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            for (ToolCapability capability : ToolCapability.values()) {
                DeclaredCapability declared =
                        new DeclaredCapability(
                                capability,
                                CapabilityEvidence.UNVERIFIED,
                                "not run anywhere in this project");
                if (capability.belongsTo(tool)) {
                    assertions.add(
                            () ->
                                    assertEquals(
                                            List.of(declared),
                                            record(
                                                            tool,
                                                            LINUX,
                                                            ArtefactKind.ZIP,
                                                            Optional.of(ManifestFixtures.member()),
                                                            Optional.empty(),
                                                            List.of(),
                                                            List.of(declared),
                                                            List.of())
                                                    .capabilities()));
                    continue;
                }
                assertions.add(
                        () ->
                                assertEquals(
                                        capability.id()
                                                + " is a capability of "
                                                + capability.tool().id()
                                                + " and cannot be declared for "
                                                + tool.id(),
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () ->
                                                                record(
                                                                        tool,
                                                                        LINUX,
                                                                        ArtefactKind.ZIP,
                                                                        Optional.of(
                                                                                ManifestFixtures
                                                                                        .member()),
                                                                        Optional.empty(),
                                                                        List.of(),
                                                                        List.of(declared),
                                                                        List.of()))
                                                .getMessage()));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("a capability, an advisory or a companion named twice is rejected")
    void repeatedEntriesAreRejected() {
        DeclaredCapability xml =
                new DeclaredCapability(
                        ToolCapability.XML_OUTPUT,
                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                        "run on linux-x86-64 by phase 00");
        ToolAdvisory advisory = new ToolAdvisory("percolator.a", "a caveat");
        ArtefactCompanion companion =
                ManifestFixtures.payloadCompanion("xsd", "share/percolator_out.xsd");
        ArtefactCompanion sameId =
                ManifestFixtures.payloadCompanion("xsd", "share/percolator_in.xsd");

        assertAll(
                () ->
                        assertEquals(
                                "capabilities declares XML_OUTPUT more than once",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        record(
                                                                ToolName.PERCOLATOR,
                                                                LINUX,
                                                                ArtefactKind.ZIP,
                                                                Optional.of(
                                                                        ManifestFixtures.member()),
                                                                Optional.empty(),
                                                                List.of(),
                                                                List.of(xml, xml),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "advisories declares \"percolator.a\" more than once",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        record(
                                                                ToolName.PERCOLATOR,
                                                                LINUX,
                                                                ArtefactKind.ZIP,
                                                                Optional.of(
                                                                        ManifestFixtures.member()),
                                                                Optional.empty(),
                                                                List.of(),
                                                                List.of(),
                                                                List.of(advisory, advisory)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "companions names the companion \"xsd\" more than once",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        record(
                                                                ToolName.PERCOLATOR,
                                                                LINUX,
                                                                ArtefactKind.ZIP,
                                                                Optional.of(
                                                                        ManifestFixtures.member()),
                                                                Optional.empty(),
                                                                List.of(companion, sameId),
                                                                List.of(),
                                                                List.of()))
                                        .getMessage()));
    }

    @Test
    @DisplayName("two files installed at one path are rejected, executable and companion alike")
    void collidingInstallPathsAreRejected() {
        ArtefactCompanion overTheBinary =
                ManifestFixtures.payloadCompanion("xsd", "bin/percolator");
        ArtefactCompanion first = ManifestFixtures.payloadCompanion("a", "share/x");
        ArtefactCompanion second = ManifestFixtures.payloadCompanion("b", "share/x");

        assertAll(
                () ->
                        assertEquals(
                                "two files would be installed at \"bin/percolator\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        record(
                                                                ToolName.PERCOLATOR,
                                                                LINUX,
                                                                ArtefactKind.ZIP,
                                                                Optional.of(
                                                                        ManifestFixtures.member()),
                                                                Optional.empty(),
                                                                List.of(overTheBinary),
                                                                List.of(),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "two files would be installed at \"share/x\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        record(
                                                                ToolName.PERCOLATOR,
                                                                LINUX,
                                                                ArtefactKind.ZIP,
                                                                Optional.of(
                                                                        ManifestFixtures.member()),
                                                                Optional.empty(),
                                                                List.of(first, second),
                                                                List.of(),
                                                                List.of()))
                                        .getMessage()));
    }

    @Test
    @DisplayName("installedPaths lists the executable first and then every companion's files")
    void installedPathsListsEverything() {
        ArtefactRecord record =
                record(
                        ToolName.PERCOLATOR,
                        LINUX,
                        ArtefactKind.ZIP,
                        Optional.of(ManifestFixtures.member()),
                        Optional.empty(),
                        List.of(
                                ManifestFixtures.payloadCompanion("a", "share/a.xsd"),
                                ManifestFixtures.payloadCompanion("b", "share/b.xsd")),
                        List.of(),
                        List.of());

        assertEquals(
                List.of("bin/percolator", "share/a.xsd", "share/b.xsd"), record.installedPaths());
    }

    @Test
    @DisplayName("the capabilities, advisories and companions given are the ones the record keeps")
    void theListsGivenAreTheListsKept() {
        DeclaredCapability xml =
                new DeclaredCapability(
                        ToolCapability.XML_OUTPUT,
                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                        "run on linux-x86-64 by phase 00");
        DeclaredCapability decoys =
                new DeclaredCapability(
                        ToolCapability.XML_DECOY_OUTPUT,
                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                        "run on linux-x86-64 by phase 05 unit 0");
        ToolAdvisory regressor =
                new ToolAdvisory("percolator.pep-regressor", "the older PEP regressor");
        ToolAdvisory pepAboveOne =
                new ToolAdvisory("percolator.pep-above-one", "a PEP above 1.0 can appear");
        ArtefactCompanion schemas = ManifestFixtures.payloadCompanion("xsd", "share/a.xsd");

        ArtefactRecord record =
                record(
                        ToolName.PERCOLATOR,
                        LINUX,
                        ArtefactKind.ZIP,
                        Optional.of(ManifestFixtures.member()),
                        Optional.empty(),
                        List.of(schemas),
                        List.of(xml, decoys),
                        List.of(regressor, pepAboveOne));

        assertAll(
                () -> assertEquals(List.of(xml, decoys), record.capabilities()),
                () -> assertEquals(List.of(regressor, pepAboveOne), record.advisories()),
                () -> assertEquals(List.of(schemas), record.companions()),
                () ->
                        assertEquals(
                                List.of("percolator.pep-regressor", "percolator.pep-above-one"),
                                List.of(
                                        record.advisories().get(0).id(),
                                        record.advisories().get(1).id()),
                                "R-PERC-11 requires these shown at selection time, so an empty list"
                                        + " would be a caveat the user never sees"));
    }

    @Test
    @DisplayName("a whole-artefact record whose companion lands on its executable is rejected")
    void aWholeArtefactCollisionIsRejected() {
        assertEquals(
                "two files would be installed at \"bin/tool\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        record(
                                                ToolName.PDV,
                                                LINUX,
                                                ArtefactKind.ZIP,
                                                Optional.empty(),
                                                Optional.of("bin/tool"),
                                                List.of(
                                                        ManifestFixtures.payloadCompanion(
                                                                "x", "bin/tool")),
                                                List.of(),
                                                List.of()))
                        .getMessage());
    }

    @Test
    @DisplayName("a whole-artefact path that escapes the install directory is rejected")
    void anEscapingWholeArtefactPathIsRejected() {
        assertEquals(
                "expectedExecutablePath must be a relative path inside the install directory, with"
                        + " no empty, \".\" or \"..\" segment and no backslash, but was:"
                        + " \"../tool\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        record(
                                                ToolName.PDV,
                                                LINUX,
                                                ArtefactKind.ZIP,
                                                Optional.empty(),
                                                Optional.of("../tool"),
                                                List.of(),
                                                List.of(),
                                                List.of()))
                        .getMessage());
    }

    @Test
    @DisplayName("the three lists are copied on the way in and on the way out")
    void theListsAreCopied() {
        List<ArtefactCompanion> mutable = new ArrayList<>();
        mutable.add(ManifestFixtures.payloadCompanion("a", "share/a.xsd"));
        ArtefactRecord record =
                record(
                        ToolName.PERCOLATOR,
                        LINUX,
                        ArtefactKind.ZIP,
                        Optional.of(ManifestFixtures.member()),
                        Optional.empty(),
                        mutable,
                        List.of(),
                        List.of());
        mutable.clear();

        assertAll(
                () -> assertEquals(1, record.companions().size()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> record.companions().clear()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> record.capabilities().clear()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> record.advisories().clear()));
    }
}
